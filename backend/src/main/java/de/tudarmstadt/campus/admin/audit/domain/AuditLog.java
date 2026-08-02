package de.tudarmstadt.campus.admin.audit.domain;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One audit entry (spec section 4.6). Written for every write operation and for denied access.
 * <p>
 * {@link #beforeState} and {@link #afterState} hold the JSON document itself rather than a mapped object
 * graph, so the audit trail does not change shape when an entity does — and so that
 * {@code AuditService} keeps control over which fields are masked ({@code password_hash} and tokens
 * never appear).
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null once the account has been deleted; {@link #actorUsername} survives it (D-9). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AdminUser actor;

    @Column(name = "actor_username", length = 64)
    private String actorUsername;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private String afterState;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(nullable = false)
    private boolean success = true;

    /** Set when {@link #success} is false, for example {@code ROLE_NOT_GRANTABLE}. */
    @Column(name = "error_code", length = 60)
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(String action, String resourceType, String resourceId) {
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
