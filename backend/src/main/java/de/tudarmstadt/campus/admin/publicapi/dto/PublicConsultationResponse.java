package de.tudarmstadt.campus.admin.publicapi.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * A published consultation offer for anonymous consumers. The responsible account stays internal — a
 * visitor sees opening hours and a contact address, not who maintains the entry.
 */
public record PublicConsultationResponse(
        Long id,
        String titleDe,
        String titleEn,
        String descriptionDe,
        String descriptionEn,
        String organisation,
        String buildingCode,
        String room,
        String contactEmail,
        List<PublicSlot> slots) {

    public record PublicSlot(Short dayOfWeek, LocalTime startTime, LocalTime endTime, String roomOverride) {
    }
}
