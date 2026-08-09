package de.tudarmstadt.campus.admin.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose outcome belongs in the audit log (spec section 4.6, FA-15).
 * <p>
 * {@code AuditAspect} writes one entry per invocation, successful or not. What it cannot know — the state
 * before and after the change — the method itself contributes through {@link AuditContext}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** One of the actions of the catalogue in spec section 4.6, for example {@code ROLE_ASSIGNED}. */
    String action();

    /** {@code USER}, {@code ROLE}, {@code POI}, {@code BUILDING}, {@code CONSULTATION}, … */
    String resourceType();

    /**
     * SpEL over the method arguments that yields the affected record, for example
     * {@code "#targetUserId"}. Empty when the action has no single resource.
     */
    String resourceId() default "";
}
