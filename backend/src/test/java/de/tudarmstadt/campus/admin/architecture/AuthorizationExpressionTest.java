package de.tudarmstadt.campus.admin.architecture;

import de.tudarmstadt.campus.admin.rbac.PermissionCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the central rule of the role model: authorisation runs on permission authorities, never on
 * role names (spec section 1.2, CLAUDE.md).
 * <p>
 * The rule needs a mechanical guard because three permission codes start with {@code ROLE_}
 * ({@code ROLE_READ}, {@code ROLE_ASSIGN}, {@code ROLE_MANAGE}) and role names are additionally exposed
 * as {@code ROLE_}-prefixed authorities. Without this test a {@code hasRole('ASSIGN')} would look
 * plausible and quietly change the meaning of an endpoint.
 */
class AuthorizationExpressionTest {

    private static final String BASE_PACKAGE = "de.tudarmstadt.campus.admin";

    /** Matches the string literals inside hasAuthority(...) and hasAnyAuthority(...). */
    private static final Pattern AUTHORITY_LITERAL = Pattern.compile("has(?:Any)?Authority\\(\\s*'([^']+)'");

    @Test
    void noEndpointAuthorisesByRoleName() {
        List<String> offenders = new ArrayList<>();

        forEachPreAuthorize((method, expression) -> {
            if (expression.contains("hasRole(") || expression.contains("hasAnyRole(")) {
                offenders.add(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                        + " -> " + expression);
            }
        });

        assertThat(offenders)
                .as("@PreAuthorize must use permission authorities, not role names")
                .isEmpty();
    }

    @Test
    void everyAuthorityUsedInAnEndpointExistsInTheCatalogue() {
        List<String> known = Arrays.stream(PermissionCode.values()).map(Enum::name).toList();
        List<String> unknown = new ArrayList<>();

        forEachPreAuthorize((method, expression) -> {
            Matcher matcher = AUTHORITY_LITERAL.matcher(expression);
            while (matcher.find()) {
                String authority = matcher.group(1);
                if (!known.contains(authority)) {
                    unknown.add(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                            + " -> " + authority);
                }
            }
        });

        assertThat(unknown)
                .as("a typo in an authority silently locks an endpoint for everyone")
                .isEmpty();
    }

    private void forEachPreAuthorize(ExpressionVisitor visitor) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = resolve(definition.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation != null) {
                    visitor.visit(method, annotation.value());
                }
            }
        }
    }

    private static Class<?> resolve(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Scanned controller cannot be loaded: " + className, ex);
        }
    }

    @FunctionalInterface
    private interface ExpressionVisitor {
        void visit(Method method, String expression);
    }
}
