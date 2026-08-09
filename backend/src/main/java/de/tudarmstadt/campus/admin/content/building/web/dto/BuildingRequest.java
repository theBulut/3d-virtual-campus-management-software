package de.tudarmstadt.campus.admin.content.building.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or update a building. {@code published} is part of the payload because buildings have no review
 * workflow — only POIs do (spec section 4.5).
 */
public record BuildingRequest(
        @NotBlank(message = "Gebäudeschlüssel darf nicht leer sein")
        @Size(max = 20, message = "Gebäudeschlüssel darf höchstens 20 Zeichen lang sein")
        String code,

        @NotBlank(message = "Deutscher Name darf nicht leer sein")
        @Size(max = 200, message = "Deutscher Name darf höchstens 200 Zeichen lang sein")
        String nameDe,

        @Size(max = 200, message = "Englischer Name darf höchstens 200 Zeichen lang sein")
        String nameEn,

        @Size(max = 200, message = "Straße darf höchstens 200 Zeichen lang sein")
        String street,

        @Size(max = 10, message = "Postleitzahl darf höchstens 10 Zeichen lang sein")
        String postalCode,

        @Size(max = 100, message = "Ort darf höchstens 100 Zeichen lang sein")
        String city,

        @DecimalMin(value = "-90.0", message = "Breitengrad muss zwischen -90 und 90 liegen")
        @DecimalMax(value = "90.0", message = "Breitengrad muss zwischen -90 und 90 liegen")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Längengrad muss zwischen -180 und 180 liegen")
        @DecimalMax(value = "180.0", message = "Längengrad muss zwischen -180 und 180 liegen")
        Double longitude,

        @Size(max = 255, message = "Modellreferenz darf höchstens 255 Zeichen lang sein")
        String modelRef,

        boolean published) {
}
