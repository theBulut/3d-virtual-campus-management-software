package de.tudarmstadt.campus.admin.publicapi.web;

import de.tudarmstadt.campus.admin.publicapi.dto.PublicBuildingResponse;
import de.tudarmstadt.campus.admin.publicapi.dto.PublicConsultationResponse;
import de.tudarmstadt.campus.admin.publicapi.dto.PublicPoiResponse;
import de.tudarmstadt.campus.admin.publicapi.service.PublicContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The public read interface (spec section 5.5, FA-17) — and the technical realisation of the role
 * {@code EXTERNE_PERSON}, which is never assigned to anybody: these paths are {@code permitAll} in the
 * filter chain (spec section 1.1).
 * <p>
 * The methods carry no {@code @PreAuthorize}; they are covered by the allowlist in
 * {@code EndpointSecurityTest}. Their responses use their own slim DTOs, so no internal field can reach
 * an anonymous caller by accident.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Öffentliche Inhalte",
        description = "Veröffentlichte Inhalte ohne Anmeldung, vorbereitet für einen Unity-Client")
public class PublicContentController {

    private final PublicContentService publicContent;

    public PublicContentController(PublicContentService publicContent) {
        this.publicContent = publicContent;
    }

    @GetMapping("/pois")
    @SecurityRequirements
    @Operation(summary = "Veröffentlichte POIs",
            description = "Ausschließlich POIs im Status PUBLISHED, ohne interne Felder.")
    public List<PublicPoiResponse> pois() {
        return publicContent.publishedPois();
    }

    @GetMapping("/buildings")
    @SecurityRequirements
    @Operation(summary = "Veröffentlichte Gebäude")
    public List<PublicBuildingResponse> buildings() {
        return publicContent.publishedBuildings();
    }

    @GetMapping("/consultations")
    @SecurityRequirements
    @Operation(summary = "Veröffentlichte Beratungszeiten")
    public List<PublicConsultationResponse> consultations() {
        return publicContent.publishedConsultations();
    }
}
