package de.tudarmstadt.campus.admin.rbac.domain;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Assignment of a role to a user. Carries who granted it and when, which the audit log needs when a role
 * is revoked again (spec section 1.4).
 * <p>
 * Implements {@link Persistable} because the identifier is assigned rather than generated: without it
 * {@code save()} would see a non-null id, take the row for an existing one and call {@code merge()},
 * which fails on a fresh assignment.
 */
@Entity
@Table(name = "user_role")
@Getter
@Setter
@NoArgsConstructor
public class UserRole implements Persistable<UserRoleId> {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AdminUser user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    /** Null once the granting account has been deleted (ON DELETE SET NULL, D-9). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AdminUser assignedBy;

    @Transient
    private boolean newAssignment = true;

    public UserRole(AdminUser user, Role role, AdminUser assignedBy) {
        this.id = new UserRoleId(user.getId(), role.getId());
        this.user = user;
        this.role = role;
        this.assignedBy = assignedBy;
    }

    @Override
    public boolean isNew() {
        return newAssignment;
    }

    @PostLoad
    @PostPersist
    void markAsStored() {
        this.newAssignment = false;
    }
}
