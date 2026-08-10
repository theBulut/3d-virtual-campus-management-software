package de.tudarmstadt.campus.admin.export.web;

import de.tudarmstadt.campus.admin.export.service.PoiExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/export")
@Tag(name = "Export", description = "Datenexport für Auswertungen")
public class ExportController {

    private final PoiExportService exportService;

    public ExportController(PoiExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * The {@code .csv} suffix in the last path segment is harmless on Spring Boot 4 — suffix pattern
     * matching is off — but the content type has to be set explicitly.
     */
    @GetMapping(value = "/pois.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    @Operation(summary = "POIs als CSV exportieren")
    public ResponseEntity<byte[]> exportPois() {
        byte[] csv = exportService.exportPoisAsCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pois.csv\"")
                .body(csv);
    }
}
