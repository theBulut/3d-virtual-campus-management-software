package de.tudarmstadt.campus.admin.content.consultation.web.dto;

import java.time.Instant;
import java.util.List;

public record ConsultationResponse(
        Long id,
        String titleDe,
        String titleEn,
        String descriptionDe,
        String descriptionEn,
        String organisation,
        Long buildingId,
        String buildingCode,
        String room,
        String contactEmail,
        Long responsibleUserId,
        String responsibleUsername,
        boolean published,
        List<ConsultationEventResponse> events,
        Instant createdAt,
        Instant updatedAt) {
}
