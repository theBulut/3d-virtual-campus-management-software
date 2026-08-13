package de.tudarmstadt.campus.admin.game.service;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.consultation.domain.Consultation;
import de.tudarmstadt.campus.admin.content.consultation.domain.ConsultationEvent;
import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationRepository;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload.SceneBuilding;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload.SceneConsultation;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload.SceneSlot;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload.ScenePoi;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload.Vector3;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the scene for one caller (FA-24).
 * <p>
 * This is where the permission model becomes visible in the product itself: the same URL answers
 * differently depending on who asks. A registered student receives published content; a project lead
 * receives the drafts and submissions as well, so the campus can be inspected before it is released
 * (docs/DECISIONS.md D-42).
 * <p>
 * The decision is taken from the caller's authorities, never from a request parameter — a client cannot
 * ask for more than its permissions allow.
 */
@Service
@Transactional(readOnly = true)
public class GameSceneService {

    private final PoiRepository pois;
    private final BuildingRepository buildings;
    private final ConsultationRepository consultations;

    public GameSceneService(PoiRepository pois, BuildingRepository buildings,
                            ConsultationRepository consultations) {
        this.pois = pois;
        this.buildings = buildings;
        this.consultations = consultations;
    }

    public ScenePayload buildFor(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean allPois = authorities.contains(PermissionCode.POI_READ_ALL.name());
        boolean allBuildings = authorities.contains(PermissionCode.BUILDING_READ_ALL.name());
        boolean allConsultations = authorities.contains(PermissionCode.CONSULTATION_READ_ALL.name());

        return new ScenePayload(
                scenePois(allPois),
                sceneBuildings(allBuildings),
                sceneConsultations(allConsultations));
    }

    private List<ScenePoi> scenePois(boolean includeUnpublished) {
        List<Poi> found = includeUnpublished
                ? pois.findAll()
                : pois.findByStatusOrderByNameDeAsc(ContentStatus.PUBLISHED);

        return found.stream()
                .sorted(Comparator.comparing(Poi::getNameDe))
                .map(poi -> toScene(poi, includeUnpublished))
                .toList();
    }

    private List<SceneBuilding> sceneBuildings(boolean includeUnpublished) {
        List<Building> found = includeUnpublished
                ? buildings.findAll()
                : buildings.findByPublishedTrueOrderByCodeAsc();

        return found.stream()
                .sorted(Comparator.comparing(Building::getCode))
                .map(building -> toScene(building, includeUnpublished))
                .toList();
    }

    private List<SceneConsultation> sceneConsultations(boolean includeUnpublished) {
        List<Consultation> found = includeUnpublished
                ? consultations.findAll()
                : consultations.findByPublishedTrueOrderByTitleDeAsc();

        return found.stream()
                .sorted(Comparator.comparing(Consultation::getTitleDe))
                .map(consultation -> toScene(consultation, includeUnpublished))
                .toList();
    }

    /**
     * The status is only part of the payload for callers who may see more than published content. For
     * everyone else it would carry no information — everything they receive is published — and the
     * client has no business branching on it.
     */
    private static ScenePoi toScene(Poi poi, boolean withStatus) {
        Building building = poi.getBuilding();
        return new ScenePoi(poi.getId(), poi.getNameDe(), poi.getNameEn(),
                poi.getDescriptionDe(), poi.getDescriptionEn(), poi.getCategory().name(),
                building == null ? null : building.getCode(),
                new Vector3(poi.getPositionX(), poi.getPositionY(), poi.getPositionZ()),
                withStatus ? poi.getStatus().name() : null);
    }

    private static SceneBuilding toScene(Building building, boolean withPublishedFlag) {
        return new SceneBuilding(building.getId(), building.getCode(), building.getNameDe(),
                building.getNameEn(), building.getModelRef(),
                new Vector3(building.getPositionX(), building.getPositionY(), building.getPositionZ()),
                building.getRotationY(),
                withPublishedFlag ? building.isPublished() : null);
    }

    private static SceneConsultation toScene(Consultation consultation, boolean withPublishedFlag) {
        Building building = consultation.getBuilding();
        return new SceneConsultation(consultation.getId(), consultation.getTitleDe(),
                consultation.getTitleEn(), consultation.getDescriptionDe(),
                consultation.getOrganisation(),
                building == null ? null : building.getCode(),
                consultation.getRoom(), consultation.getContactEmail(),
                consultation.getEvents().stream()
                        .sorted(Comparator.comparing(ConsultationEvent::getStartTime))
                        .map(event -> new SceneSlot(event.getDayOfWeek(), event.getStartTime(),
                                event.getEndTime(),
                                event.getRoomOverride() == null
                                        ? consultation.getRoom() : event.getRoomOverride()))
                        .toList(),
                withPublishedFlag ? consultation.isPublished() : null);
    }
}
