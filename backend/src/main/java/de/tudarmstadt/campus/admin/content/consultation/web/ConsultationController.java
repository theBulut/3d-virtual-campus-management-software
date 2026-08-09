package de.tudarmstadt.campus.admin.content.consultation.web;

import de.tudarmstadt.campus.admin.content.consultation.service.ConsultationService;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationEventRequest;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationEventResponse;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationRequest;
import de.tudarmstadt.campus.admin.content.consultation.web.dto.ConsultationResponse;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultation offers (spec section 5.4). The slot endpoints are nested and carry the same ownership
 * rule as their offer, so PERSONAL maintains its own opening hours and nothing else (FA-13).
 */
@RestController
@RequestMapping("/api/consultations")
@Tag(name = "Beratungszeiten", description = "Beratungsangebote der Fachgebiete und Einrichtungen")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONSULTATION_READ_ALL')")
    @Operation(summary = "Alle Beratungsangebote")
    public List<ConsultationResponse> findAll() {
        return consultationService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONSULTATION_READ_ALL')")
    @Operation(summary = "Beratungsangebot lesen")
    public ConsultationResponse findById(@PathVariable long id) {
        return consultationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONSULTATION_CREATE')")
    @Operation(summary = "Beratungsangebot anlegen",
            description = "Ohne CONSULTATION_UPDATE_ANY wird der Aufrufer als zuständig eingetragen und "
                    + "das Angebot bleibt unveröffentlicht.")
    public ConsultationResponse create(@AuthenticationPrincipal CampusUserDetails principal,
                                       Authentication authentication,
                                       @Valid @RequestBody ConsultationRequest request) {
        return consultationService.create(principal.getUserId(), request, mayPublish(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONSULTATION_UPDATE_ANY') or (hasAuthority('CONSULTATION_UPDATE_OWN') "
            + "and @consultationSecurity.isResponsible(#id, authentication))")
    @Operation(summary = "Beratungsangebot ändern")
    public ConsultationResponse update(@AuthenticationPrincipal CampusUserDetails principal,
                                       Authentication authentication,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ConsultationRequest request) {
        return consultationService.update(id, principal.getUserId(), request, mayPublish(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONSULTATION_DELETE')")
    @Operation(summary = "Beratungsangebot löschen", description = "Die Termine werden mitgelöscht.")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        consultationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONSULTATION_UPDATE_ANY') or (hasAuthority('CONSULTATION_UPDATE_OWN') "
            + "and @consultationSecurity.isResponsible(#id, authentication))")
    @Operation(summary = "Termin hinzufügen")
    public ConsultationEventResponse addEvent(@PathVariable Long id,
                                              @Valid @RequestBody ConsultationEventRequest request) {
        return consultationService.addEvent(id, request);
    }

    @PutMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('CONSULTATION_UPDATE_ANY') or (hasAuthority('CONSULTATION_UPDATE_OWN') "
            + "and @consultationSecurity.isResponsibleForEvent(#eventId, authentication))")
    @Operation(summary = "Termin ändern")
    public ConsultationEventResponse updateEvent(@PathVariable Long eventId,
                                                 @Valid @RequestBody ConsultationEventRequest request) {
        return consultationService.updateEvent(eventId, request);
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('CONSULTATION_UPDATE_ANY') or (hasAuthority('CONSULTATION_UPDATE_OWN') "
            + "and @consultationSecurity.isResponsibleForEvent(#eventId, authentication))")
    @Operation(summary = "Termin löschen")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        consultationService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    /** Publishing and reassigning stay with the wider permission (docs/DECISIONS.md D-34). */
    private static boolean mayPublish(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PermissionCode.CONSULTATION_UPDATE_ANY.name()::equals);
    }
}
