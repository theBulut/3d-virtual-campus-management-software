package de.tudarmstadt.campus.admin.publicapi.service;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.consultation.domain.Consultation;
import de.tudarmstadt.campus.admin.content.consultation.domain.ConsultationEvent;
import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationRepository;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.publicapi.dto.PublicBuildingResponse;
import de.tudarmstadt.campus.admin.publicapi.dto.PublicConsultationResponse;
import de.tudarmstadt.campus.admin.publicapi.dto.PublicPoiResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * The read-only view for anonymous consumers (spec section 5.5, FA-17).
 * <p>
 * The filter lives here, not in the controller: whether something is published is a property of the
 * content, and no query parameter may widen it.
 */
@Service
@Transactional(readOnly = true)
public class PublicContentService {

    private final PoiRepository pois;
    private final BuildingRepository buildings;
    private final ConsultationRepository consultations;

    public PublicContentService(PoiRepository pois, BuildingRepository buildings,
                                ConsultationRepository consultations) {
        this.pois = pois;
        this.buildings = buildings;
        this.consultations = consultations;
    }

    public List<PublicPoiResponse> publishedPois() {
        return pois.findByStatusOrderByNameDeAsc(ContentStatus.PUBLISHED).stream()
                .map(PublicContentService::toPublic)
                .toList();
    }

    public List<PublicBuildingResponse> publishedBuildings() {
        return buildings.findByPublishedTrueOrderByCodeAsc().stream()
                .map(PublicContentService::toPublic)
                .toList();
    }

    public List<PublicConsultationResponse> publishedConsultations() {
        return consultations.findByPublishedTrueOrderByTitleDeAsc().stream()
                .map(PublicContentService::toPublic)
                .toList();
    }

    private static PublicPoiResponse toPublic(Poi poi) {
        Building building = poi.getBuilding();
        return new PublicPoiResponse(poi.getId(), poi.getNameDe(), poi.getNameEn(),
                poi.getDescriptionDe(), poi.getDescriptionEn(), poi.getCategory().name(),
                building == null ? null : building.getCode(),
                poi.getPositionX(), poi.getPositionY(), poi.getPositionZ());
    }

    private static PublicBuildingResponse toPublic(Building building) {
        return new PublicBuildingResponse(building.getId(), building.getCode(), building.getNameDe(),
                building.getNameEn(), building.getStreet(), building.getPostalCode(), building.getCity(),
                building.getLatitude(), building.getLongitude(), building.getModelRef());
    }

    private static PublicConsultationResponse toPublic(Consultation consultation) {
        Building building = consultation.getBuilding();
        return new PublicConsultationResponse(consultation.getId(), consultation.getTitleDe(),
                consultation.getTitleEn(), consultation.getDescriptionDe(),
                consultation.getDescriptionEn(), consultation.getOrganisation(),
                building == null ? null : building.getCode(),
                consultation.getRoom(), consultation.getContactEmail(),
                consultation.getEvents().stream()
                        .sorted(Comparator.comparing(ConsultationEvent::getStartTime))
                        .map(event -> new PublicConsultationResponse.PublicSlot(event.getDayOfWeek(),
                                event.getStartTime(), event.getEndTime(), event.getRoomOverride()))
                        .toList());
    }
}
