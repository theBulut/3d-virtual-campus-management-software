package de.tudarmstadt.campus.admin.content.building.web.dto;

import java.time.Instant;

/** A building as the administration interface sees it (spec section 5.4). */
public record BuildingResponse(
        Long id,
        String code,
        String nameDe,
        String nameEn,
        String street,
        String postalCode,
        String city,
        Double latitude,
        Double longitude,
        String modelRef,
        double positionX,
        double positionY,
        double positionZ,
        double rotationY,
        boolean published,
        Instant createdAt,
        Instant updatedAt) {
}
