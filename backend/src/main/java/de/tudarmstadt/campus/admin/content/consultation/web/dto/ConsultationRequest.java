package de.tudarmstadt.campus.admin.content.consultation.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or update a consultation offer.
 * <p>
 * {@code published} is only honoured for callers holding CONSULTATION_UPDATE_ANY. PERSONAL maintains the
 * content of its own entries but does not release them — otherwise the quality gate of decision E-4 would
 * exist for POIs and be missing here (docs/DECISIONS.md D-34).
 */
public record ConsultationRequest(
        @NotBlank(message = "Deutscher Titel darf nicht leer sein")
        @Size(max = 200, message = "Deutscher Titel darf höchstens 200 Zeichen lang sein")
        String titleDe,

        @Size(max = 200, message = "Englischer Titel darf höchstens 200 Zeichen lang sein")
        String titleEn,

        String descriptionDe,
        String descriptionEn,

        @NotBlank(message = "Einrichtung darf nicht leer sein")
        @Size(max = 150, message = "Einrichtung darf höchstens 150 Zeichen lang sein")
        String organisation,

        Long buildingId,

        @Size(max = 50, message = "Raum darf höchstens 50 Zeichen lang sein")
        String room,

        @Email(message = "Kontakt-E-Mail muss eine gültige Adresse sein")
        @Size(max = 255, message = "Kontakt-E-Mail darf höchstens 255 Zeichen lang sein")
        String contactEmail,

        Long responsibleUserId,

        boolean published) {
}
