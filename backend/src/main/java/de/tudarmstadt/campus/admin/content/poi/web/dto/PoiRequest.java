package de.tudarmstadt.campus.admin.content.poi.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or update a POI. Neither status nor assignee are part of the payload — both have their own
 * endpoints and their own permissions, so an ordinary edit can never release content or hand it on.
 */
public record PoiRequest(
        @NotBlank(message = "Deutscher Name darf nicht leer sein")
        @Size(max = 200, message = "Deutscher Name darf höchstens 200 Zeichen lang sein")
        String nameDe,

        @Size(max = 200, message = "Englischer Name darf höchstens 200 Zeichen lang sein")
        String nameEn,

        String descriptionDe,

        String descriptionEn,

        @NotNull(message = "Kategorie muss angegeben werden")
        String category,

        Long buildingId,

        @NotNull(message = "X-Koordinate muss angegeben werden") Double positionX,
        @NotNull(message = "Y-Koordinate muss angegeben werden") Double positionY,
        @NotNull(message = "Z-Koordinate muss angegeben werden") Double positionZ) {
}
