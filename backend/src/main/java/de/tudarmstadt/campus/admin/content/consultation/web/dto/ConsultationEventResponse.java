package de.tudarmstadt.campus.admin.content.consultation.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** One slot of a consultation offer. {@code dayOfWeek} null means a one-off appointment. */
public record ConsultationEventResponse(
        Long id,
        Short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate validFrom,
        LocalDate validTo,
        String roomOverride,
        String note) {
}
