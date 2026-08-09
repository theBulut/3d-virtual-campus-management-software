package de.tudarmstadt.campus.admin.audit.service;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import de.tudarmstadt.campus.admin.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an audit entry in its own transaction.
 * <p>
 * {@code REQUIRES_NEW} is what makes a denied operation auditable at all: the business transaction rolls
 * back, the entry stays (spec section 4.6). It also keeps a failing audit insert from taking the business
 * transaction with it — {@code AuditService} catches around this call.
 */
@Component
public class AuditWriter {

    private final AuditLogRepository auditLogs;

    AuditWriter(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /**
     * Public on purpose: with proxy based AOP, {@code @Transactional} is only honoured on public
     * methods. A package-private method here would silently run in the caller's transaction and take the
     * entry down with it on rollback — the one thing this class exists to prevent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        auditLogs.save(entry);
    }
}
