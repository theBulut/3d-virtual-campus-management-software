package de.tudarmstadt.campus.admin.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The technical evidence for FA-14: no endpoint is unintentionally open.
 * <p>
 * Reflects over every {@code @RestController} method and fails when one is neither annotated with
 * {@code @PreAuthorize} nor listed in the allowlist below. The allowlist is the complete set of
 * deliberately public endpoints from spec section 4.4 — adding to it is a conscious act that shows up
 * in the diff.
 */
class EndpointSecurityTest {

    private static final String BASE_PACKAGE = "de.tudarmstadt.campus.admin";

    /**
     * Endpoints that must remain reachable without authentication (spec sections 4.3 and 4.4).
     * Swagger is served by springdoc and has no controller of ours, so it does not appear here.
     */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/auth/login",
            // Registration has to be reachable without an account — that is its whole purpose. The
            // protection here is not authorisation but a rate limit and the fixed role it hands out.
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/health",
            "/api/public/**");

    @Test
    void everyEndpointIsEitherProtectedOrDeliberatelyPublic() {
        List<String> unprotected = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                boolean annotated = AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                        || AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
                if (annotated) {
                    continue;
                }
                String path = pathOf(controller, method);
                if (!isAllowlisted(path)) {
                    unprotected.add(controller.getSimpleName() + "." + method.getName() + " -> " + path);
                }
            }
        }

        assertThat(unprotected)
                .as("every endpoint needs @PreAuthorize or an entry in the allowlist")
                .isEmpty();
    }

    /**
     * Guards the allowlist itself: an entry that no longer matches any endpoint is stale and would
     * silently widen the next endpoint that happens to land on that path.
     */
    @Test
    void theAllowlistContainsNoStaleEntries() {
        List<String> mappedPaths = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (isEndpoint(method)) {
                    mappedPaths.add(pathOf(controller, method));
                }
            }
        }

        // /api/public/** is reserved for phase 6 and has no controller yet.
        List<String> stale = PUBLIC_ENDPOINTS.stream()
                .filter(entry -> !entry.endsWith("/**"))
                .filter(entry -> !mappedPaths.contains(entry))
                .toList();

        assertThat(stale).as("allowlist entries without a matching endpoint").isEmpty();
    }

    @Test
    void atLeastOneControllerWasScanned() {
        // Protects against the whole test passing because the scan silently found nothing.
        assertThat(restControllers()).isNotEmpty();
    }

    private static List<Class<?>> restControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                controllers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Scanned controller cannot be loaded", ex);
            }
        }
        return controllers;
    }

    private static boolean isEndpoint(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && !method.isSynthetic()
                && AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static String pathOf(Class<?> controller, Method method) {
        RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);

        String base = firstPath(typeMapping);
        String suffix = firstPath(methodMapping);
        String combined = (base + suffix).replaceAll("//+", "/");
        return combined.isEmpty() ? "/" : combined;
    }

    private static String firstPath(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0 || mapping.path()[0].isEmpty()) {
            return "";
        }
        return mapping.path()[0];
    }

    private static boolean isAllowlisted(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(entry -> entry.endsWith("/**")
                ? path.startsWith(entry.substring(0, entry.length() - 2))
                : entry.equals(path));
    }
}
