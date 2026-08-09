package de.tudarmstadt.campus.admin.user.service;

import de.tudarmstadt.campus.admin.common.exception.ConflictException;
import de.tudarmstadt.campus.admin.common.exception.ForbiddenException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.common.exception.UnauthorizedException;
import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.security.JwtService;
import de.tudarmstadt.campus.admin.security.TokenBlacklistService;
import de.tudarmstadt.campus.admin.security.TokenClaims;
import de.tudarmstadt.campus.admin.security.TokenVersionService;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import de.tudarmstadt.campus.admin.user.web.dto.CurrentUserResponse;
import de.tudarmstadt.campus.admin.user.web.dto.TokenResponse;
import de.tudarmstadt.campus.admin.user.web.dto.UpdateProfileRequest;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Login, token rotation, logout and self service for the caller's own account (spec section 5.1).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;
    private final TokenVersionService tokenVersions;
    private final PasswordService passwordService;

    public AuthService(AdminUserRepository adminUsers, RoleRepository roles, JwtService jwtService,
                       TokenBlacklistService blacklist, TokenVersionService tokenVersions,
                       PasswordService passwordService) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.tokenVersions = tokenVersions;
        this.passwordService = passwordService;
    }

    @Transactional
    public TokenResponse login(String username, String rawPassword) {
        AdminUser user = adminUsers.findByUsername(username).orElse(null);

        // Same answer for an unknown account and a wrong password, so the endpoint cannot be used to
        // find out which usernames exist.
        if (user == null || !passwordService.matches(rawPassword, user.getPasswordHash())) {
            log.debug("Failed login attempt for '{}'", username);
            throw new UnauthorizedException("INVALID_CREDENTIALS",
                    "Benutzername oder Passwort ist falsch.");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_DISABLED",
                    "Dieses Konto ist gesperrt. Bitte wenden Sie sich an die Administration.");
        }

        user.setLastLoginAt(Instant.now());
        adminUsers.save(user);

        return issueTokens(user);
    }

    /**
     * Rotation: the presented refresh token is revoked and a new pair is issued. Roles and permissions
     * are rebuilt from the database, which is how a permission change takes effect without a new login
     * (FA-19, scenario S-08).
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        TokenClaims claims = parseOrReject(refreshToken);

        if (!claims.isRefreshToken()) {
            throw new UnauthorizedException("INVALID_TOKEN_TYPE",
                    "Für diesen Vorgang wird ein Refresh-Token benötigt.");
        }
        if (blacklist.isBlacklisted(claims.jti())) {
            throw new UnauthorizedException("TOKEN_REVOKED",
                    "Dieses Token wurde bereits verwendet oder widerrufen.");
        }

        AdminUser user = adminUsers.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("TOKEN_STALE",
                        "Ihre Sitzung ist nicht mehr gültig. Bitte melden Sie sich erneut an."));

        // Only a password change, reset or deactivation invalidates refresh tokens (D-3).
        if (claims.version() != user.getRefreshVersion()) {
            throw new UnauthorizedException("TOKEN_STALE",
                    "Ihre Sitzung ist nicht mehr gültig. Bitte melden Sie sich erneut an.");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_DISABLED",
                    "Dieses Konto ist gesperrt. Bitte wenden Sie sich an die Administration.");
        }

        blacklist.blacklist(claims.jti(), claims.expiresAt());
        return issueTokens(user);
    }

    /**
     * Revokes the access token and, when supplied, the refresh token as well. Without the refresh token
     * in the body its {@code jti} is unknown and the session could be continued (D-19).
     */
    public void logout(TokenClaims accessClaims, String refreshToken) {
        blacklist.blacklist(accessClaims.jti(), accessClaims.expiresAt());

        if (refreshToken == null || refreshToken.isBlank()) {
            log.debug("Logout of '{}' without a refresh token; it stays valid until it expires",
                    accessClaims.username());
            return;
        }
        try {
            TokenClaims refreshClaims = jwtService.parse(refreshToken);
            // Only ever revoke your own token.
            if (refreshClaims.isRefreshToken() && refreshClaims.userId() == accessClaims.userId()) {
                blacklist.blacklist(refreshClaims.jti(), refreshClaims.expiresAt());
            }
        } catch (JwtException ex) {
            log.debug("Ignored an unusable refresh token during logout: {}", ex.getMessage());
        }
    }

    /**
     * Ends every session of the account, on this device and on all others.
     * <p>
     * Unlike {@link #logout} this does not rely on the client handing back its tokens: raising both
     * counters makes every outstanding access and refresh token stale at once, no matter where it is
     * (spec section 4.2, docs/DECISIONS.md D-24). The blacklist is not involved — it can only revoke
     * tokens whose {@code jti} the server has seen.
     */
    @Transactional
    public void logoutEverywhere(long userId) {
        AdminUser user = loadUser(userId);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setRefreshVersion(user.getRefreshVersion() + 1);
        adminUsers.save(user);

        // Without this the filter would keep answering from the five minute cache.
        tokenVersions.invalidate(userId);
        log.info("Ended all sessions of '{}'", user.getUsername());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(long userId) {
        return toResponse(loadUser(userId));
    }

    @Transactional
    public CurrentUserResponse updateOwnProfile(long userId, UpdateProfileRequest request) {
        AdminUser user = loadUser(userId);

        if (adminUsers.existsByEmailIgnoreCaseAndIdNot(request.email(), userId)) {
            throw new ConflictException("EMAIL_ALREADY_USED",
                    "Diese E-Mail-Adresse wird bereits von einem anderen Konto verwendet.");
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim());
        user.setOrganisation(request.organisation() == null ? null : request.organisation().trim());
        return toResponse(adminUsers.save(user));
    }

    /**
     * Changing the password ends every session: both counters go up, so neither the current access token
     * nor any outstanding refresh token survives (D-3).
     */
    @Transactional
    public void changeOwnPassword(long userId, String currentPassword, String newPassword) {
        AdminUser user = loadUser(userId);

        if (!passwordService.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Das aktuelle Passwort ist falsch.");
        }
        if (passwordService.matches(newPassword, user.getPasswordHash())) {
            throw new ConflictException("PASSWORD_UNCHANGED",
                    "Das neue Passwort muss sich vom bisherigen unterscheiden.");
        }

        user.setPasswordHash(passwordService.hash(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setRefreshVersion(user.getRefreshVersion() + 1);
        user.setMustChangePassword(false);
        adminUsers.save(user);
        tokenVersions.invalidate(userId);
    }

    private TokenResponse issueTokens(AdminUser user) {
        List<String> roleNames = roles.findRoleNamesByUserId(user.getId());
        List<String> permissions = effectivePermissions(user);

        String accessToken = jwtService.createAccessToken(
                user.getId(), user.getUsername(), user.getTokenVersion(), roleNames, permissions);
        String refreshToken = jwtService.createRefreshToken(
                user.getId(), user.getUsername(), user.getRefreshVersion());

        return new TokenResponse(accessToken, refreshToken,
                jwtService.accessTokenLifetime().toSeconds(), toResponse(user));
    }

    /**
     * An account that has to change its password gets a token that can do nothing else. That makes the
     * forced change of spec section 7.1 a server side rule rather than a hint the frontend could skip
     * (docs/DECISIONS.md D-21).
     */
    private List<String> effectivePermissions(AdminUser user) {
        List<String> permissions = roles.findPermissionCodesByUserId(user.getId());
        if (!user.isMustChangePassword()) {
            return permissions;
        }
        return permissions.stream()
                .filter(code -> code.equals(PermissionCode.PROFILE_UPDATE_OWN.name()))
                .toList();
    }

    private AdminUser loadUser(long userId) {
        return adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Das Konto wurde nicht gefunden."));
    }

    private TokenClaims parseOrReject(String token) {
        try {
            return jwtService.parse(token);
        } catch (JwtException ex) {
            throw new UnauthorizedException("INVALID_TOKEN",
                    "Das Token ist ungültig oder abgelaufen. Bitte melden Sie sich erneut an.");
        }
    }

    private CurrentUserResponse toResponse(AdminUser user) {
        return new CurrentUserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getOrganisation(),
                user.isActive(), user.isMustChangePassword(),
                roles.findRoleNamesByUserId(user.getId()),
                roles.findPermissionCodesByUserId(user.getId()));
    }
}
