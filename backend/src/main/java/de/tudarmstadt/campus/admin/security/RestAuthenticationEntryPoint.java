package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Produces the 401 for unauthenticated requests as an {@code ApiError} (spec section 4.7).
 * <p>
 * Delegates to the {@code HandlerExceptionResolver} instead of writing JSON itself, so the error format
 * lives in {@code GlobalExceptionHandler} alone and cannot drift between the two paths.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public RestAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        boolean stale = Boolean.TRUE.equals(request.getAttribute(JwtAuthFilter.STALE_TOKEN_ATTRIBUTE));

        // A token that was signed correctly but has been revoked or superseded deserves its own code:
        // the client can react by refreshing instead of asking for the password again (INV-6).
        UnauthorizedException failure = stale
                ? new UnauthorizedException("TOKEN_STALE",
                        "Ihre Sitzung ist nicht mehr gültig. Bitte melden Sie sich erneut an.")
                : new UnauthorizedException("UNAUTHENTICATED", "Bitte melden Sie sich an.");

        resolver.resolveException(request, response, null, failure);
    }
}
