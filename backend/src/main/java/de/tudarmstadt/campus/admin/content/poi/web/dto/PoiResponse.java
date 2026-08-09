package de.tudarmstadt.campus.admin.content.poi.web.dto;

import java.time.Instant;

/**
 * A POI in the administration interface — including the workflow fields the public API never shows
 * (spec section 5.4).
 */
public record PoiResponse(
        Long id,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        String category,
        Long buildingId,
        String buildingCode,
        double positionX,
        double positionY,
        double positionZ,
        String status,
        boolean published,
        Long assignedToId,
        String assignedToUsername,
        String reviewNote,
        Instant publishedAt,
        String publishedByUsername,
        Long createdById,
        String createdByUsername,
        Instant createdAt,
        Instant updatedAt) {
}
