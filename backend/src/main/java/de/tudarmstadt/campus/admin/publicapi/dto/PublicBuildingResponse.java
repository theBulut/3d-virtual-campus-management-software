package de.tudarmstadt.campus.admin.publicapi.dto;

/** A published building for anonymous consumers (spec section 5.5). */
public record PublicBuildingResponse(
        Long id,
        String code,
        String nameDe,
        String nameEn,
        String street,
        String postalCode,
        String city,
        Double latitude,
        Double longitude,
        String modelRef) {
}
