package de.tudarmstadt.campus.admin.export.service;

import de.tudarmstadt.campus.admin.audit.Audited;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CSV export of the POI inventory (spec section 5.5, FA-22).
 * <p>
 * Written by hand rather than with a CSV library: one format, ten columns, and no new dependency for it
 * (CLAUDE.md). Quoting follows RFC 4180 — every field is quoted and inner quotes are doubled, so a
 * semicolon or a line break inside a description cannot break the file.
 */
@Service
public class PoiExportService {

    private static final String SEPARATOR = ";";
    private static final String NEWLINE = "\r\n";

    private static final List<String> HEADERS = List.of(
            "id", "name_de", "name_en", "kategorie", "gebaeude", "status",
            "position_x", "position_y", "position_z", "veroeffentlicht_am");

    private final PoiRepository pois;

    public PoiExportService(PoiRepository pois) {
        this.pois = pois;
    }

    @Audited(action = "DATA_EXPORTED", resourceType = "POI")
    @Transactional(readOnly = true)
    public String exportPoisAsCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(SEPARATOR, HEADERS.stream().map(PoiExportService::quote).toList()))
                .append(NEWLINE);

        for (Poi poi : pois.findAll()) {
            csv.append(String.join(SEPARATOR, List.of(
                            quote(poi.getId()),
                            quote(poi.getNameDe()),
                            quote(poi.getNameEn()),
                            quote(poi.getCategory()),
                            quote(poi.getBuilding() == null ? null : poi.getBuilding().getCode()),
                            quote(poi.getStatus()),
                            quote(poi.getPositionX()),
                            quote(poi.getPositionY()),
                            quote(poi.getPositionZ()),
                            quote(poi.getPublishedAt()))))
                    .append(NEWLINE);
        }
        return csv.toString();
    }

    private static String quote(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
