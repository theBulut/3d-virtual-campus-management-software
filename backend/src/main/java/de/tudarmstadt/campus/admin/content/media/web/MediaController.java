package de.tudarmstadt.campus.admin.content.media.web;

import de.tudarmstadt.campus.admin.content.media.service.MediaService;
import de.tudarmstadt.campus.admin.content.media.web.dto.MediaAssetResponse;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Medien", description = "Bild-Uploads und ihre Zuordnung zu POIs")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    @Operation(summary = "Bild hochladen",
            description = "Höchstens 5 MB, ausschließlich PNG, JPEG und WebP. Optional direkt einem POI "
                    + "zugeordnet.")
    public MediaAssetResponse upload(@AuthenticationPrincipal CampusUserDetails principal,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) Long poiId) {
        return mediaService.upload(principal.getUserId(), file, poiId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bild abrufen", description = "Liefert die Datei selbst.")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        MediaService.StoredFile file = mediaService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                // inline: the interface shows previews, it does not force a download.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    @GetMapping("/{id}/metadata")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Metadaten eines Bildes")
    public MediaAssetResponse metadata(@PathVariable long id) {
        return mediaService.findById(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bilder eines POI")
    public List<MediaAssetResponse> findByPoi(@RequestParam long poiId) {
        return mediaService.findByPoi(poiId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MEDIA_DELETE')")
    @Operation(summary = "Bild löschen")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
