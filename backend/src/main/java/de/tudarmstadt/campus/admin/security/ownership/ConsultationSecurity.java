package de.tudarmstadt.campus.admin.security.ownership;

import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationEventRepository;
import de.tudarmstadt.campus.admin.content.consultation.repository.ConsultationRepository;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership check behind {@code CONSULTATION_UPDATE_OWN} (spec sections 1.2 and 4.4, FA-13).
 * <p>
 * Ownership runs through {@code responsible_user_id} — the field the specification names for exactly this
 * purpose. Unlike a POI there is no status to consider: a consultation offer has no review workflow.
 */
@Component("consultationSecurity")
public class ConsultationSecurity {

    private final ConsultationRepository consultations;
    private final ConsultationEventRepository events;

    public ConsultationSecurity(ConsultationRepository consultations,
                                ConsultationEventRepository events) {
        this.consultations = consultations;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public boolean isResponsible(Long consultationId, Authentication authentication) {
        if (consultationId == null
                || !(authentication.getPrincipal() instanceof CampusUserDetails principal)) {
            return false;
        }
        return consultations.existsByIdAndResponsibleUserId(consultationId, principal.getUserId());
    }

    /**
     * Same rule one level down: a slot belongs to whoever is responsible for its offer, so the nested
     * endpoints need no rule of their own.
     */
    @Transactional(readOnly = true)
    public boolean isResponsibleForEvent(Long eventId, Authentication authentication) {
        if (eventId == null) {
            return false;
        }
        return events.findById(eventId)
                .map(event -> isResponsible(event.getConsultation().getId(), authentication))
                .orElse(false);
    }
}
