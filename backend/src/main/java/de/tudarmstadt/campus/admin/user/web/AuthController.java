package de.tudarmstadt.campus.admin.user.web;

import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import de.tudarmstadt.campus.admin.security.JwtAuthFilter;
import de.tudarmstadt.campus.admin.security.TokenClaims;
import de.tudarmstadt.campus.admin.user.service.AuthService;
import de.tudarmstadt.campus.admin.user.web.dto.ChangePasswordRequest;
import de.tudarmstadt.campus.admin.user.web.dto.CurrentUserResponse;
import de.tudarmstadt.campus.admin.user.web.dto.LoginRequest;
import de.tudarmstadt.campus.admin.user.web.dto.LogoutRequest;
import de.tudarmstadt.campus.admin.user.web.dto.RefreshRequest;
import de.tudarmstadt.campus.admin.user.web.dto.TokenResponse;
import de.tudarmstadt.campus.admin.user.web.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentifizierung", description = "Anmeldung, Token-Erneuerung und eigenes Profil")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** One of the four deliberately unauthenticated endpoints; covered by the allowlist. */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Anmelden",
            description = "Liefert ein Access- und ein Refresh-Token. Gesperrte Konten erhalten 403.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Token erneuern",
            description = "Rotiert das Token-Paar: das vorgelegte Refresh-Token wird dabei entwertet. "
                    + "Rollen und Berechtigungen werden neu aus der Datenbank aufgebaut.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Abmelden",
            description = "Entwertet das Access-Token sofort. Das Refresh-Token wird nur entwertet, "
                    + "wenn es im Rumpf mitgeschickt wird.")
    public ResponseEntity<Void> logout(
            @RequestAttribute(JwtAuthFilter.CLAIMS_ATTRIBUTE) TokenClaims claims,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(claims, request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Eigenes Konto",
            description = "Enthält Rollen und Berechtigungen und steuert damit das Menü der Oberfläche.")
    public CurrentUserResponse me(@AuthenticationPrincipal CampusUserDetails principal) {
        return authService.currentUser(principal.getUserId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('PROFILE_UPDATE_OWN')")
    @Operation(summary = "Eigene Stammdaten ändern")
    public CurrentUserResponse updateProfile(@AuthenticationPrincipal CampusUserDetails principal,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateOwnProfile(principal.getUserId(), request);
    }

    @PostMapping("/me/password")
    @PreAuthorize("hasAuthority('PROFILE_UPDATE_OWN')")
    @Operation(summary = "Eigenes Passwort ändern",
            description = "Beendet alle Sitzungen: bestehende Access- und Refresh-Tokens werden ungültig.")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CampusUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changeOwnPassword(principal.getUserId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
