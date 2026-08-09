package de.tudarmstadt.campus.admin.content.poi.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.domain.PoiCategory;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.content.poi.web.dto.PoiRequest;
import de.tudarmstadt.campus.admin.content.poi.web.dto.PoiResponse;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Points of interest and their review workflow (spec sections 4.5 and 5.4).
 * <p>
 * Status changes never happen through {@link #update}: each transition has its own method, its own
 * permission and its own audit action. That is what keeps creating and releasing content apart (FA-11).
 */
@Service
public class PoiService {

    private static final Logger log = LoggerFactory.getLogger(PoiService.class);

    private final PoiRepository pois;
    private final BuildingRepository buildings;
    private final AdminUserRepository adminUsers;
    private final PoiStatusService statusService;

    public PoiService(PoiRepository pois, BuildingRepository buildings,
                      AdminUserRepository adminUsers, PoiStatusService statusService) {
        this.pois = pois;
        this.buildings = buildings;
        this.adminUsers = adminUsers;
        this.statusService = statusService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PoiResponse> search(String status, String category, Long buildingId,
                                            Long assignedTo, String query, Pageable pageable) {
        return PageResponse.from(
                pois.search(parseStatus(status), parseCategory(category), buildingId, assignedTo,
                        emptyToNull(query), pageable),
                PoiService::toResponse);
    }

    @Transactional(readOnly = true)
    public PoiResponse findById(long id) {
        return toResponse(loadWithDetails(id));
    }

    /** A POI always starts as a draft — the status is never part of the request (spec section 5.4). */
    @Audited(action = "POI_CREATED", resourceType = "POI")
    @Transactional
    public PoiResponse create(long actorId, PoiRequest request) {
        Poi poi = new Poi(request.nameDe().trim(), parseRequiredCategory(request.category()),
                request.positionX(), request.positionY(), request.positionZ());
        apply(poi, request);
        poi.setStatus(ContentStatus.DRAFT);
        poi.setCreatedBy(adminUsers.findById(actorId).orElse(null));
        Poi saved = pois.save(poi);

        AuditContext.resourceId(saved.getId());
        AuditContext.after("nameDe", saved.getNameDe());
        AuditContext.after("status", saved.getStatus().name());
        return toResponse(saved);
    }

    @Audited(action = "POI_UPDATED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse update(long id, PoiRequest request) {
        Poi poi = load(id);
        AuditContext.before("nameDe", poi.getNameDe());
        AuditContext.before("category", poi.getCategory().name());

        poi.setNameDe(request.nameDe().trim());
        poi.setCategory(parseRequiredCategory(request.category()));
        poi.setPositionX(request.positionX());
        poi.setPositionY(request.positionY());
        poi.setPositionZ(request.positionZ());
        apply(poi, request);
        Poi saved = pois.save(poi);

        AuditContext.after("nameDe", saved.getNameDe());
        AuditContext.after("category", saved.getCategory().name());
        return toResponse(saved);
    }

    @Audited(action = "POI_DELETED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public void delete(long id) {
        Poi poi = load(id);
        AuditContext.before("nameDe", poi.getNameDe());
        AuditContext.before("status", poi.getStatus().name());
        pois.delete(poi);
    }

    @Audited(action = "POI_SUBMITTED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse submitForReview(long id) {
        return applyTransition(id, poi -> statusService.submitForReview(poi));
    }

    @Audited(action = "POI_PUBLISHED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse publish(long actorId, long id) {
        AdminUser actor = adminUsers.findById(actorId).orElse(null);
        return applyTransition(id, poi -> statusService.publish(poi, actor));
    }

    @Audited(action = "POI_REJECTED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse reject(long id, String reviewNote) {
        return applyTransition(id, poi -> statusService.reject(poi, reviewNote));
    }

    @Audited(action = "POI_ARCHIVED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse archive(long id) {
        return applyTransition(id, poi -> statusService.archive(poi));
    }

    /** Handing a POI to an editor; {@code null} clears the assignment. */
    @Audited(action = "POI_ASSIGNED", resourceType = "POI", resourceId = "#id")
    @Transactional
    public PoiResponse assign(long id, Long userId) {
        Poi poi = load(id);
        AuditContext.before("assignedTo", username(poi.getAssignedTo()));

        AdminUser assignee = userId == null ? null : adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Das Konto wurde nicht gefunden."));
        poi.setAssignedTo(assignee);
        Poi saved = pois.save(poi);

        AuditContext.after("assignedTo", username(saved.getAssignedTo()));
        return toResponse(saved);
    }

    private PoiResponse applyTransition(long id, Consumer<Poi> transition) {
        Poi poi = load(id);
        AuditContext.before("status", poi.getStatus().name());
        transition.accept(poi);
        // saveAndFlush, not save: is_published is a generated column, and Hibernate only re-reads it
        // once the update has actually gone to the database. Without the flush the response would
        // report status PUBLISHED and published false.
        Poi saved = pois.saveAndFlush(poi);
        AuditContext.after("status", saved.getStatus().name());
        log.info("POI {} moved to {}", saved.getId(), saved.getStatus());
        return toResponse(saved);
    }

    private void apply(Poi poi, PoiRequest request) {
        poi.setNameEn(trimToNull(request.nameEn()));
        poi.setDescriptionDe(trimToNull(request.descriptionDe()));
        poi.setDescriptionEn(trimToNull(request.descriptionEn()));
        poi.setBuilding(resolveBuilding(request.buildingId()));
    }

    private Building resolveBuilding(Long buildingId) {
        if (buildingId == null) {
            return null;
        }
        return buildings.findById(buildingId)
                .orElseThrow(() -> new NotFoundException("BUILDING_NOT_FOUND",
                        "Das Gebäude wurde nicht gefunden."));
    }

    private Poi load(long id) {
        return pois.findById(id)
                .orElseThrow(() -> new NotFoundException("POI_NOT_FOUND", "Der POI wurde nicht gefunden."));
    }

    private Poi loadWithDetails(long id) {
        return pois.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("POI_NOT_FOUND", "Der POI wurde nicht gefunden."));
    }

    private static PoiCategory parseRequiredCategory(String value) {
        PoiCategory category = parseCategory(value);
        if (category == null) {
            throw new BadRequestException("INVALID_CATEGORY",
                    "Unbekannte Kategorie. Erlaubt sind: " + Arrays.toString(PoiCategory.values()));
        }
        return category;
    }

    private static PoiCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PoiCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_CATEGORY",
                    "Unbekannte Kategorie. Erlaubt sind: " + Arrays.toString(PoiCategory.values()));
        }
    }

    private static ContentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ContentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_STATUS",
                    "Unbekannter Status. Erlaubt sind: " + Arrays.toString(ContentStatus.values()));
        }
    }

    static PoiResponse toResponse(Poi poi) {
        Building building = poi.getBuilding();
        return new PoiResponse(poi.getId(), poi.getNameDe(), poi.getNameEn(),
                poi.getDescriptionDe(), poi.getDescriptionEn(), poi.getCategory().name(),
                building == null ? null : building.getId(),
                building == null ? null : building.getCode(),
                poi.getPositionX(), poi.getPositionY(), poi.getPositionZ(),
                poi.getStatus().name(), poi.isPublished(),
                poi.getAssignedTo() == null ? null : poi.getAssignedTo().getId(),
                username(poi.getAssignedTo()),
                poi.getReviewNote(), poi.getPublishedAt(), username(poi.getPublishedBy()),
                poi.getCreatedBy() == null ? null : poi.getCreatedBy().getId(),
                username(poi.getCreatedBy()),
                poi.getCreatedAt(), poi.getUpdatedAt());
    }

    private static String username(AdminUser user) {
        return user == null ? null : user.getUsername();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
