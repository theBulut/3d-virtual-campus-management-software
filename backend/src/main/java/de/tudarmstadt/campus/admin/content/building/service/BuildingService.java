package de.tudarmstadt.campus.admin.content.building.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.building.web.dto.BuildingRequest;
import de.tudarmstadt.campus.admin.content.building.web.dto.BuildingResponse;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Buildings (spec section 5.4). No review workflow here — a building is either published or it is not.
 */
@Service
public class BuildingService {

    private final BuildingRepository buildings;
    private final PoiRepository pois;
    private final AdminUserRepository adminUsers;

    public BuildingService(BuildingRepository buildings, PoiRepository pois,
                           AdminUserRepository adminUsers) {
        this.buildings = buildings;
        this.pois = pois;
        this.adminUsers = adminUsers;
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> findAll() {
        return buildings.findAll().stream()
                .sorted(Comparator.comparing(Building::getCode))
                .map(BuildingService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildingResponse findById(long id) {
        return toResponse(load(id));
    }

    @Audited(action = "BUILDING_CREATED", resourceType = "BUILDING")
    @Transactional
    public BuildingResponse create(long actorId, BuildingRequest request) {
        if (buildings.existsByCode(request.code())) {
            throw new ConflictException("BUILDING_CODE_ALREADY_USED",
                    "Der Gebäudeschlüssel " + request.code() + " ist bereits vergeben.");
        }

        Building building = new Building(request.code().trim(), request.nameDe().trim());
        apply(building, request);
        building.setCreatedBy(adminUsers.findById(actorId).orElse(null));
        Building saved = buildings.save(building);

        AuditContext.resourceId(saved.getId());
        AuditContext.after("code", saved.getCode());
        AuditContext.after("nameDe", saved.getNameDe());
        AuditContext.after("published", saved.isPublished());
        return toResponse(saved);
    }

    @Audited(action = "BUILDING_UPDATED", resourceType = "BUILDING", resourceId = "#id")
    @Transactional
    public BuildingResponse update(long id, BuildingRequest request) {
        Building building = load(id);
        if (!building.getCode().equals(request.code()) && buildings.existsByCode(request.code())) {
            throw new ConflictException("BUILDING_CODE_ALREADY_USED",
                    "Der Gebäudeschlüssel " + request.code() + " ist bereits vergeben.");
        }

        AuditContext.before("code", building.getCode());
        AuditContext.before("nameDe", building.getNameDe());
        AuditContext.before("published", building.isPublished());

        building.setCode(request.code().trim());
        building.setNameDe(request.nameDe().trim());
        apply(building, request);
        Building saved = buildings.save(building);

        AuditContext.after("code", saved.getCode());
        AuditContext.after("nameDe", saved.getNameDe());
        AuditContext.after("published", saved.isPublished());
        return toResponse(saved);
    }

    /**
     * Deleting a building that still carries POIs is refused with 409 rather than orphaning them
     * (spec section 5.4). The database would refuse it too — this is the readable half of that guard.
     */
    @Audited(action = "BUILDING_DELETED", resourceType = "BUILDING", resourceId = "#id")
    @Transactional
    public void delete(long id) {
        Building building = load(id);
        long referencing = pois.countByBuildingId(id);
        if (referencing > 0) {
            throw new ConflictException("BUILDING_HAS_POIS",
                    "Dem Gebäude sind noch " + referencing + " POIs zugeordnet.");
        }

        AuditContext.before("code", building.getCode());
        buildings.delete(building);
    }

    private static void apply(Building building, BuildingRequest request) {
        building.setNameEn(trimToNull(request.nameEn()));
        building.setStreet(trimToNull(request.street()));
        building.setPostalCode(trimToNull(request.postalCode()));
        building.setCity(trimToNull(request.city()));
        building.setLatitude(request.latitude());
        building.setLongitude(request.longitude());
        building.setModelRef(trimToNull(request.modelRef()));
        building.setPositionX(request.positionX());
        building.setPositionY(request.positionY());
        building.setPositionZ(request.positionZ());
        building.setRotationY(request.rotationY());
        building.setPublished(request.published());
    }

    private Building load(long id) {
        return buildings.findById(id)
                .orElseThrow(() -> new NotFoundException("BUILDING_NOT_FOUND",
                        "Das Gebäude wurde nicht gefunden."));
    }

    static BuildingResponse toResponse(Building building) {
        return new BuildingResponse(building.getId(), building.getCode(), building.getNameDe(),
                building.getNameEn(), building.getStreet(), building.getPostalCode(), building.getCity(),
                building.getLatitude(), building.getLongitude(), building.getModelRef(),
                building.getPositionX(), building.getPositionY(), building.getPositionZ(),
                building.getRotationY(),
                building.isPublished(), building.getCreatedAt(), building.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
