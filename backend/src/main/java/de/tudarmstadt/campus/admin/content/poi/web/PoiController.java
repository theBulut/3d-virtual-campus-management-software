package de.tudarmstadt.campus.admin.content.poi.web;

import de.tudarmstadt.campus.admin.common.dto.PageResponse;
import de.tudarmstadt.campus.admin.content.poi.service.PoiService;
import de.tudarmstadt.campus.admin.content.poi.web.dto.AssigneeRequest;
import de.tudarmstadt.campus.admin.content.poi.web.dto.PoiRequest;
import de.tudarmstadt.campus.admin.content.poi.web.dto.PoiResponse;
import de.tudarmstadt.campus.admin.content.poi.web.dto.RejectPoiRequest;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Points of interest including the review workflow (spec sections 4.5 and 5.4).
 * <p>
 * Two expressions carry the whole separation of duties: editing is either
 * {@code POI_UPDATE_ANY} or {@code POI_UPDATE_OWN} plus ownership, and releasing is {@code POI_PUBLISH}
 * — a permission the contributing role does not hold (FA-11, FA-12).
 */
@RestController
@RequestMapping("/api/pois")
@Tag(name = "Points of Interest", description = "Inhalte mit Freigabe-Workflow")
public class PoiController {

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('POI_READ_ALL')")
    @Operation(summary = "POIs auflisten",
            description = "Filter: status, category, buildingId, assignedTo, q.")
    public PageResponse<PoiResponse> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) Long buildingId,
                                          @RequestParam(required = false) Long assignedTo,
                                          @RequestParam(required = false) String q,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return poiService.search(status, category, buildingId, assignedTo, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('POI_READ_ALL')")
    @Operation(summary = "POI lesen")
    public PoiResponse findById(@PathVariable long id) {
        return poiService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POI_CREATE')")
    @Operation(summary = "POI anlegen", description = "Entsteht immer im Status DRAFT.")
    public PoiResponse create(@AuthenticationPrincipal CampusUserDetails principal,
                              @Valid @RequestBody PoiRequest request) {
        return poiService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('POI_UPDATE_ANY') "
            + "or (hasAuthority('POI_UPDATE_OWN') and @poiSecurity.canEdit(#id, authentication))")
    @Operation(summary = "POI ändern",
            description = "Mit POI_UPDATE_OWN nur eigene oder zugewiesene POIs, und nur solange sie "
                    + "nicht veröffentlicht sind.")
    public PoiResponse update(@PathVariable Long id, @Valid @RequestBody PoiRequest request) {
        return poiService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('POI_DELETE')")
    @Operation(summary = "POI löschen")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        poiService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('POI_SUBMIT_REVIEW') and @poiSecurity.canEdit(#id, authentication)")
    @Operation(summary = "Zur Prüfung einreichen", description = "DRAFT wird zu IN_REVIEW.")
    public PoiResponse submit(@PathVariable Long id) {
        return poiService.submitForReview(id);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('POI_PUBLISH')")
    @Operation(summary = "Freigeben", description = "IN_REVIEW wird zu PUBLISHED.")
    public PoiResponse publish(@AuthenticationPrincipal CampusUserDetails principal,
                               @PathVariable long id) {
        return poiService.publish(principal.getUserId(), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('POI_PUBLISH')")
    @Operation(summary = "Zurückweisen",
            description = "IN_REVIEW wird zu DRAFT. Die Begründung ist Pflicht.")
    public PoiResponse reject(@PathVariable long id, @Valid @RequestBody RejectPoiRequest request) {
        return poiService.reject(id, request.reviewNote());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('POI_PUBLISH')")
    @Operation(summary = "Archivieren", description = "PUBLISHED wird zu ARCHIVED.")
    public PoiResponse archive(@PathVariable long id) {
        return poiService.archive(id);
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('POI_ASSIGN')")
    @Operation(summary = "Bearbeiter setzen", description = "userId = null entfernt die Zuweisung.")
    public PoiResponse assign(@PathVariable long id, @RequestBody AssigneeRequest request) {
        return poiService.assign(id, request.userId());
    }
}
