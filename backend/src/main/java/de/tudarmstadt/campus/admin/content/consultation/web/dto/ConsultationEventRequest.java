package de.tudarmstadt.campus.admin.content.consultation.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A slot. The database additionally enforces {@code end_time > start_time}; the service checks it first
 * so the caller gets a readable error instead of a constraint violation.
 */
public record ConsultationEventRequest(
        @Min(value = 1, message = "Wochentag muss zwischen 1 und 7 liegen")
        @Max(value = 7, message = "Wochentag muss zwischen 1 und 7 liegen")
        Short dayOfWeek,

        @NotNull(message = "Startzeit muss angegeben werden") LocalTime startTime,
        @NotNull(message = "Endzeit muss angegeben werden") LocalTime endTime,

        LocalDate validFrom,
        LocalDate validTo,

        @Size(max = 50, message = "Abweichender Raum darf höchstens 50 Zeichen lang sein")
        String roomOverride,

        String note) {
}
