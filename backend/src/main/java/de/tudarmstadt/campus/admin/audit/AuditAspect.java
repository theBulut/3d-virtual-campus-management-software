package de.tudarmstadt.campus.admin.audit;

import de.tudarmstadt.campus.admin.audit.service.AuditService;
import de.tudarmstadt.campus.admin.common.exception.ApiException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;


/**
 * Writes one audit entry per {@link Audited} method, whether it succeeded or not (spec section 4.6).
 * <p>
 * Runs at the outermost order on purpose, so it wraps the transaction advice: on success the business
 * transaction has already committed when the entry is written, and on failure it has already rolled back
 * — the entry then documents an attempt that changed nothing, which is exactly what scenario S-06 needs.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * The annotation is read from the signature instead of being bound by the pointcut. Binding it
     * (`@annotation(audited)`) fails once a second proxy sits in front of the method — the transaction
     * advice here — with "JoinPointMatch was NOT bound in invocation".
     */
    @Around("@annotation(de.tudarmstadt.campus.admin.audit.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Audited audited = auditedAnnotation(joinPoint);
        AuditContext.clear();
        try {
            Object result = joinPoint.proceed();
            auditService.record(action(audited), audited.resourceType(),
                    resourceId(joinPoint, audited), true, null,
                    AuditContext.drainBefore(), AuditContext.drainAfter());
            return result;
        } catch (ApiException ex) {
            // A rejected operation is the interesting half of the audit trail.
            auditService.record(action(audited), audited.resourceType(),
                    resourceId(joinPoint, audited), false, ex.getCode(), null, null);
            throw ex;
        } catch (Throwable ex) {
            auditService.record(action(audited), audited.resourceType(),
                    resourceId(joinPoint, audited), false, "INTERNAL_ERROR", null, null);
            throw ex;
        } finally {
            // Threads are pooled; nothing may survive into the next request.
            AuditContext.clear();
        }
    }

    private static Audited auditedAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Audited audited = AnnotatedElementUtils.findMergedAnnotation(signature.getMethod(), Audited.class);
        if (audited != null) {
            return audited;
        }
        // The signature may report the interface method; fall back to the implementation.
        Method target = ClassUtils.getMostSpecificMethod(signature.getMethod(),
                AopProxyUtils.ultimateTargetClass(joinPoint.getTarget()));
        return AnnotatedElementUtils.findMergedAnnotation(target, Audited.class);
    }

    /** A refinement recorded by the method wins over the static value of the annotation. */
    private static String action(Audited audited) {
        String refined = AuditContext.drainAction();
        return refined == null ? audited.action() : refined;
    }

    private String resourceId(ProceedingJoinPoint joinPoint, Audited audited) {
        String refined = AuditContext.drainResourceId();
        return refined == null ? resolveResourceId(joinPoint, audited) : refined;
    }

    private String resolveResourceId(ProceedingJoinPoint joinPoint, Audited audited) {
        if (audited.resourceId().isBlank()) {
            return null;
        }
        try {
            String[] parameterNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            EvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
            Object value = expressionParser.parseExpression(audited.resourceId()).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException ex) {
            log.warn("Could not resolve the resource id expression '{}'", audited.resourceId(), ex);
            return null;
        }
    }
}
