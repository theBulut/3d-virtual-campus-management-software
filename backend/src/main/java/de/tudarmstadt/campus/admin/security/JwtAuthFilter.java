package de.tudarmstadt.campus.admin.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a bearer token into an authenticated {@code SecurityContext} (spec section 4.3).
 * <p>
 * A request without a usable token simply continues unauthenticated; whether that is a problem is
 * decided by the endpoint, and the entry point then produces the 401. The principal is rebuilt from the
 * claims alone, so an authorised request costs no database round trip.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** Signals a token that was valid but has been revoked or superseded (INV-6). */
    static final String STALE_TOKEN_ATTRIBUTE = "campus.token.stale";

    /**
     * The claims of the accepted access token. Logout needs the {@code jti} and the expiry to revoke
     * exactly this token, and re-parsing it in the controller would be wasteful.
     */
    public static final String CLAIMS_ATTRIBUTE = "campus.token.claims";

    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;
    private final TokenVersionService tokenVersions;

    public JwtAuthFilter(JwtService jwtService, TokenBlacklistService blacklist,
                         TokenVersionService tokenVersions) {
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.tokenVersions = tokenVersions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = bearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TokenClaims claims = jwtService.parse(token);

            // A refresh token must never open a protected endpoint.
            if (!claims.isAccessToken()) {
                log.debug("Rejected a {} token used as an access token", claims.type());
                filterChain.doFilter(request, response);
                return;
            }

            if (blacklist.isBlacklisted(claims.jti())) {
                request.setAttribute(STALE_TOKEN_ATTRIBUTE, Boolean.TRUE);
                filterChain.doFilter(request, response);
                return;
            }

            // Role change, deactivation or password change since the token was issued.
            if (claims.version() != tokenVersions.currentAccessVersion(claims.userId())) {
                request.setAttribute(STALE_TOKEN_ATTRIBUTE, Boolean.TRUE);
                filterChain.doFilter(request, response);
                return;
            }

            authenticate(request, claims);
        } catch (JwtException ex) {
            log.debug("Discarded an unusable token: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, TokenClaims claims) {
        request.setAttribute(CLAIMS_ATTRIBUTE, claims);

        CampusUserDetails principal = new CampusUserDetails(
                claims.userId(), claims.username(), null, true,
                claims.roles(), claims.permissions());

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
