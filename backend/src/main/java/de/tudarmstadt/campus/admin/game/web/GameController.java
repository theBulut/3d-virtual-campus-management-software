package de.tudarmstadt.campus.admin.game.web;

import de.tudarmstadt.campus.admin.game.service.GameSceneService;
import de.tudarmstadt.campus.admin.game.service.GameStateService;
import de.tudarmstadt.campus.admin.game.web.dto.ScenePayload;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the Unity client talks to (spec section 5.6).
 * <p>
 * Two endpoints, two different ideas of authorisation. The scene is content and therefore guarded by a
 * content permission — {@code POI_READ_PUBLISHED}, which every playing role holds and
 * {@code MAINTENANCE_DEV} deliberately does not. The game state is data about oneself and only needs a
 * session; the account it belongs to is taken from the token.
 */
@RestController
@RequestMapping("/api/game")
@Tag(name = "Spiel", description = "Szene und Spielstand für den Unity-Client")
public class GameController {

    private final GameSceneService sceneService;
    private final GameStateService stateService;

    public GameController(GameSceneService sceneService, GameStateService stateService) {
        this.sceneService = sceneService;
        this.stateService = stateService;
    }

    @GetMapping("/scene")
    @PreAuthorize("hasAuthority('POI_READ_PUBLISHED')")
    @Operation(summary = "Szene laden",
            description = "POIs, Gebäude und Beratungszeiten in einem Aufruf. Der Umfang hängt von den "
                    + "Berechtigungen ab: mit POI_READ_ALL, BUILDING_READ_ALL beziehungsweise "
                    + "CONSULTATION_READ_ALL sind auch unveröffentlichte Inhalte enthalten.")
    public ScenePayload scene(Authentication authentication) {
        return sceneService.buildFor(authentication);
    }

    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Eigenen Spielstand laden",
            description = "Antwortet mit 204, solange noch nie gespeichert wurde — der Client beginnt "
                    + "dann ein neues Spiel.")
    public ResponseEntity<String> loadState(@AuthenticationPrincipal CampusUserDetails principal) {
        return stateService.find(principal.getUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * The body is passed through as a string on purpose: the format belongs to the game, and parsing it
     * into a model here would mean a migration for every new field (D-41).
     */
    @PutMapping(value = "/state", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Eigenen Spielstand speichern",
            description = "Legt an oder ersetzt. Beliebiges JSON-Dokument bis 64 KB.")
    public ResponseEntity<Void> saveState(@AuthenticationPrincipal CampusUserDetails principal,
                                          @RequestBody String state) {
        stateService.save(principal.getUserId(), state);
        return ResponseEntity.noContent().build();
    }
}
