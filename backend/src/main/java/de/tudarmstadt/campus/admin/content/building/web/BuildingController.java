package de.tudarmstadt.campus.admin.content.building.web;

import de.tudarmstadt.campus.admin.content.building.service.BuildingService;
import de.tudarmstadt.campus.admin.content.building.web.dto.BuildingRequest;
import de.tudarmstadt.campus.admin.content.building.web.dto.BuildingResponse;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@Tag(name = "Gebäude", description = "Campusgebäude als Bezugspunkt für POIs und Beratungszeiten")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BUILDING_READ_ALL')")
    @Operation(summary = "Alle Gebäude")
    public List<BuildingResponse> findAll() {
        return buildingService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BUILDING_READ_ALL')")
    @Operation(summary = "Gebäude lesen")
    public BuildingResponse findById(@PathVariable long id) {
        return buildingService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUILDING_CREATE')")
    @Operation(summary = "Gebäude anlegen")
    public BuildingResponse create(@AuthenticationPrincipal CampusUserDetails principal,
                                   @Valid @RequestBody BuildingRequest request) {
        return buildingService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BUILDING_UPDATE')")
    @Operation(summary = "Gebäude ändern")
    public BuildingResponse update(@PathVariable long id, @Valid @RequestBody BuildingRequest request) {
        return buildingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BUILDING_DELETE')")
    @Operation(summary = "Gebäude löschen",
            description = "Antwortet mit 409, solange dem Gebäude noch POIs zugeordnet sind.")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        buildingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
