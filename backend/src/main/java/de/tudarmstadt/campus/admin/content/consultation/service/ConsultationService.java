package de.tudarmstadt.campus.admin.content.consultation.service;

import de.tudarmstadt.campus.admin.audit.AuditContext;
import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.consultation.domain.Consultation;
import de.tudarmstadt.campus.admin.content.consultation.domain.ConsultationEvent;
import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationEventRepository;
import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationRepository;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationEventRequest;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationEventResponse;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationRequest;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationResponse;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Consultation offers and their slots (spec section 5.4, FA-13).
 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultations;
    private final ConsultationEventRepository events;
    private final BuildingRepository buildings;
    private final AdminUserRepository adminUsers;

    public ConsultationService(ConsultationRepository consultations, ConsultationEventRepository events,
                               BuildingRepository buildings, AdminUserRepository adminUsers) {
        this.consultations = consultations;
        this.events = events;
        this.buildings = buildings;
        this.adminUsers = adminUsers;
    }

    @Transactional(readOnly = true)
    public List<ConsultationResponse> findAll() {
        return consultations.findAll().stream()
                .sorted(Comparator.comparing(Consultation::getTitleDe))
                .map(ConsultationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConsultationResponse findById(long id) {
        return toResponse(load(id));
    }

    /**
     * @param mayPublish true when the caller holds CONSULTATION_UPDATE_ANY; only then is
     *                   {@code published} taken from the request, and only then may the offer be
     *                   assigned to somebody else (D-34)
     */
    @Audited(action = "CONSULTATION_CREATED", resourceType = "CONSULTATION")
    @Transactional
    public ConsultationResponse create(long actorId, ConsultationRequest request, boolean mayPublish) {
        Consultation consultation = new Consultation(request.titleDe().trim(),
                request.organisation().trim());
        apply(consultation, request, actorId, mayPublish);
        consultation.setCreatedBy(adminUsers.findById(actorId).orElse(null));
        Consultation saved = consultations.save(consultation);

        AuditContext.resourceId(saved.getId());
        AuditContext.after("titleDe", saved.getTitleDe());
        AuditContext.after("published", saved.isPublished());
        return toResponse(saved);
    }

    @Audited(action = "CONSULTATION_UPDATED", resourceType = "CONSULTATION", resourceId = "#id")
    @Transactional
    public ConsultationResponse update(long id, long actorId, ConsultationRequest request,
                                       boolean mayPublish) {
        Consultation consultation = load(id);
        AuditContext.before("titleDe", consultation.getTitleDe());
        AuditContext.before("published", consultation.isPublished());

        consultation.setTitleDe(request.titleDe().trim());
        consultation.setOrganisation(request.organisation().trim());
        apply(consultation, request, actorId, mayPublish);
        Consultation saved = consultations.save(consultation);

        AuditContext.after("titleDe", saved.getTitleDe());
        AuditContext.after("published", saved.isPublished());
        return toResponse(saved);
    }

    @Audited(action = "CONSULTATION_DELETED", resourceType = "CONSULTATION", resourceId = "#id")
    @Transactional
    public void delete(long id) {
        Consultation consultation = load(id);
        AuditContext.before("titleDe", consultation.getTitleDe());
        // The slots go with it (ON DELETE CASCADE plus orphanRemoval).
        consultations.delete(consultation);
    }

    @Audited(action = "CONSULTATION_UPDATED", resourceType = "CONSULTATION", resourceId = "#id")
    @Transactional
    public ConsultationEventResponse addEvent(long id, ConsultationEventRequest request) {
        Consultation consultation = load(id);
        assertTimeOrder(request);

        ConsultationEvent event = new ConsultationEvent(request.dayOfWeek(), request.startTime(),
                request.endTime());
        applyEvent(event, request);
        consultation.addEvent(event);
        // Saved through the slot repository rather than by cascade: saving the offer would go through
        // merge and put the identity on a copy, leaving this instance — and with it the response —
        // without an id. The client needs that id to edit or remove the slot again.
        ConsultationEvent saved = events.saveAndFlush(event);

        AuditContext.after("addedEvent", request.startTime() + "–" + request.endTime());
        return toResponse(saved);
    }

    @Audited(action = "CONSULTATION_UPDATED", resourceType = "CONSULTATION", resourceId = "#eventId")
    @Transactional
    public ConsultationEventResponse updateEvent(long eventId, ConsultationEventRequest request) {
        ConsultationEvent event = loadEvent(eventId);
        assertTimeOrder(request);

        AuditContext.before("slot", event.getStartTime() + "–" + event.getEndTime());
        event.setDayOfWeek(request.dayOfWeek());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        applyEvent(event, request);
        ConsultationEvent saved = events.save(event);

        AuditContext.after("slot", saved.getStartTime() + "–" + saved.getEndTime());
        return toResponse(saved);
    }

    @Audited(action = "CONSULTATION_UPDATED", resourceType = "CONSULTATION", resourceId = "#eventId")
    @Transactional
    public void deleteEvent(long eventId) {
        ConsultationEvent event = loadEvent(eventId);
        AuditContext.before("removedSlot", event.getStartTime() + "–" + event.getEndTime());
        events.delete(event);
    }

    private void apply(Consultation consultation, ConsultationRequest request, long actorId,
                       boolean mayPublish) {
        consultation.setTitleEn(trimToNull(request.titleEn()));
        consultation.setDescriptionDe(trimToNull(request.descriptionDe()));
        consultation.setDescriptionEn(trimToNull(request.descriptionEn()));
        consultation.setRoom(trimToNull(request.room()));
        consultation.setContactEmail(trimToNull(request.contactEmail()));
        consultation.setBuilding(resolveBuilding(request.buildingId()));

        if (mayPublish) {
            consultation.setPublished(request.published());
        }
        if (mayPublish && request.responsibleUserId() != null) {
            consultation.setResponsibleUser(resolveResponsible(request.responsibleUserId()));
        } else if (consultation.getResponsibleUser() == null) {
            // Whoever creates it owns it; nobody without the wider permission reassigns an offer. An
            // update that names no account leaves the responsible person alone — releasing an entry
            // must not quietly take it away from the department that maintains it.
            consultation.setResponsibleUser(adminUsers.findById(actorId).orElse(null));
        }
    }

    private static void applyEvent(ConsultationEvent event, ConsultationEventRequest request) {
        event.setValidFrom(request.validFrom());
        event.setValidTo(request.validTo());
        event.setRoomOverride(trimToNull(request.roomOverride()));
        event.setNote(trimToNull(request.note()));
    }

    /** Checked here as well so the caller gets a field error rather than a constraint violation. */
    private static void assertTimeOrder(ConsultationEventRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BadRequestException("INVALID_TIME_RANGE",
                    "Die Endzeit muss nach der Startzeit liegen.");
        }
    }

    private AdminUser resolveResponsible(long userId) {
        return adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Das angegebene Konto wurde nicht gefunden."));
    }

    private Building resolveBuilding(Long buildingId) {
        if (buildingId == null) {
            return null;
        }
        return buildings.findById(buildingId)
                .orElseThrow(() -> new NotFoundException("BUILDING_NOT_FOUND",
                        "Das Gebäude wurde nicht gefunden."));
    }

    private Consultation load(long id) {
        return consultations.findById(id)
                .orElseThrow(() -> new NotFoundException("CONSULTATION_NOT_FOUND",
                        "Das Beratungsangebot wurde nicht gefunden."));
    }

    private ConsultationEvent loadEvent(long eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("CONSULTATION_EVENT_NOT_FOUND",
                        "Der Termin wurde nicht gefunden."));
    }

    static ConsultationResponse toResponse(Consultation consultation) {
        Building building = consultation.getBuilding();
        AdminUser responsible = consultation.getResponsibleUser();
        return new ConsultationResponse(consultation.getId(), consultation.getTitleDe(),
                consultation.getTitleEn(), consultation.getDescriptionDe(),
                consultation.getDescriptionEn(), consultation.getOrganisation(),
                building == null ? null : building.getId(),
                building == null ? null : building.getCode(),
                consultation.getRoom(), consultation.getContactEmail(),
                responsible == null ? null : responsible.getId(),
                responsible == null ? null : responsible.getUsername(),
                consultation.isPublished(),
                consultation.getEvents().stream()
                        .sorted(Comparator.comparing(ConsultationEvent::getStartTime))
                        .map(ConsultationService::toResponse).toList(),
                consultation.getCreatedAt(), consultation.getUpdatedAt());
    }

    static ConsultationEventResponse toResponse(ConsultationEvent event) {
        return new ConsultationEventResponse(event.getId(), event.getDayOfWeek(), event.getStartTime(),
                event.getEndTime(), event.getValidFrom(), event.getValidTo(), event.getRoomOverride(),
                event.getNote());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
