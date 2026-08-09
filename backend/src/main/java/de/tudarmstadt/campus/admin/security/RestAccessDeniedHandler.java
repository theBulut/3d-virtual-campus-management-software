package de.tudarmstadt.campus.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Produces the 403 for denials raised inside the filter chain (spec section 4.3).
 * <p>
 * Denials from {@code @PreAuthorize} never reach this handler — those are thrown inside the dispatcher
 * and handled by {@code GlobalExceptionHandler}, which is also where phase 5 writes the
 * {@code ACCESS_DENIED} audit entry (docs/DECISIONS.md D-10).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public RestAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
