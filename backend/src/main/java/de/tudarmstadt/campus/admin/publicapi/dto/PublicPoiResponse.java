package de.tudarmstadt.campus.admin.publicapi.dto;

/**
 * A published POI for anonymous consumers, above all a future Unity client (spec section 5.5, FA-17).
 * <p>
 * Deliberately minimal: no status, no author, no assignee, no review note, no timestamps. Data
 * minimisation is not a side effect here but the point — {@code PublicApiIT} fails if a field with any
 * of those names ever appears.
 */
public record PublicPoiResponse(
        Long id,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        String category,
        String buildingCode,
        double positionX,
        double positionY,
        double positionZ) {
}
