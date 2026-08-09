package de.tudarmstadt.campus.admin.content.media.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.config.AppProperties;
import de.tudarmstadt.campus.admin.content.media.domain.MediaAsset;
import de.tudarmstadt.campus.admin.content.media.repository.MediaAssetRepository;
import de.tudarmstadt.campus.admin.content.media.web.dto.MediaAssetResponse;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uploads and their metadata (spec section 5.4).
 * <p>
 * The bytes go to a local volume, the database keeps only the path — the prototype has no object storage
 * (E-6). Stored file names are UUIDs: the original name is data, not a path, and must never influence
 * where something lands on disk.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** Spec section 5.4: images only. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp");

    private static final Set<String> ALLOWED_TYPE_NAMES = ALLOWED_TYPES.keySet();

    /** 5 MB, as in spec section 5.4. Enforced here as well, not only by the servlet container. */
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final MediaAssetRepository mediaAssets;
    private final PoiRepository pois;
    private final AdminUserRepository adminUsers;
    private final Path storageRoot;

    public MediaService(MediaAssetRepository mediaAssets, PoiRepository pois,
                        AdminUserRepository adminUsers, AppProperties properties) {
        this.mediaAssets = mediaAssets;
        this.pois = pois;
        this.adminUsers = adminUsers;
        this.storageRoot = Path.of(properties.mediaPath()).toAbsolutePath().normalize();
    }

    @Audited(action = "MEDIA_UPLOADED", resourceType = "MEDIA")
    @Transactional
    public MediaAssetResponse upload(long actorId, MultipartFile file, Long poiId) {
        assertAcceptable(file);

        String extension = ALLOWED_TYPES.get(file.getContentType());
        String storedName = UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storedName);

        try {
            Files.createDirectories(storageRoot);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store the upload", ex);
        }

        MediaAsset asset = new MediaAsset(originalName(file), file.getContentType(), file.getSize(),
                target.toString());
        asset.setUploadedBy(adminUsers.findById(actorId).orElse(null));
        if (poiId != null) {
            asset.setPoi(pois.findById(poiId)
                    .orElseThrow(() -> new NotFoundException("POI_NOT_FOUND",
                            "Der POI wurde nicht gefunden.")));
        }
        MediaAsset saved = mediaAssets.save(asset);

        AuditContext.resourceId(saved.getId());
        AuditContext.after("filename", saved.getFilename());
        AuditContext.after("contentType", saved.getContentType());
        AuditContext.after("sizeBytes", saved.getSizeBytes());
        log.info("Stored upload {} as {}", saved.getFilename(), storedName);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse findById(long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> findByPoi(long poiId) {
        return mediaAssets.findByPoiId(poiId).stream().map(MediaService::toResponse).toList();
    }

    /** Returns the bytes for {@code GET /api/media/{id}}. */
    @Transactional(readOnly = true)
    public StoredFile download(long id) {
        MediaAsset asset = load(id);
        Path path = Path.of(asset.getStoragePath());
        if (!Files.exists(path)) {
            throw new NotFoundException("MEDIA_FILE_MISSING",
                    "Die Datei ist nicht mehr auf dem Server vorhanden.");
        }
        try {
            return new StoredFile(asset.getFilename(), asset.getContentType(), Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the stored file", ex);
        }
    }

    @Audited(action = "MEDIA_DELETED", resourceType = "MEDIA", resourceId = "#id")
    @Transactional
    public void delete(long id) {
        MediaAsset asset = load(id);
        AuditContext.before("filename", asset.getFilename());

        mediaAssets.delete(asset);
        try {
            Files.deleteIfExists(Path.of(asset.getStoragePath()));
        } catch (IOException ex) {
            // The row is gone; a leftover file is a housekeeping problem, not a failed request.
            log.warn("Deleted the metadata of {} but could not remove the file", asset.getFilename(), ex);
        }
    }

    private static void assertAcceptable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "Es wurde keine Datei übermittelt.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE",
                    "Die Datei überschreitet die zulässige Größe von 5 MB.");
        }
        if (file.getContentType() == null || !ALLOWED_TYPE_NAMES.contains(file.getContentType())) {
            throw new BadRequestException("UNSUPPORTED_MEDIA_TYPE",
                    "Erlaubt sind ausschließlich PNG-, JPEG- und WebP-Bilder.");
        }
    }

    /** Keeps only the file name; a client-supplied path must never reach the storage layer. */
    private static String originalName(MultipartFile file) {
        String submitted = file.getOriginalFilename();
        if (submitted == null || submitted.isBlank()) {
            return "upload";
        }
        String name = Path.of(submitted).getFileName().toString();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private MediaAsset load(long id) {
        return mediaAssets.findById(id)
                .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND",
                        "Das Medium wurde nicht gefunden."));
    }

    private static MediaAssetResponse toResponse(MediaAsset asset) {
        return new MediaAssetResponse(asset.getId(), asset.getFilename(), asset.getContentType(),
                asset.getSizeBytes(), asset.getPoi() == null ? null : asset.getPoi().getId(),
                asset.getUploadedAt(),
                asset.getUploadedBy() == null ? null : asset.getUploadedBy().getUsername());
    }

    /** The bytes plus what the response needs to serve them. */
    public record StoredFile(String filename, String contentType, byte[] content) {
    }
}
