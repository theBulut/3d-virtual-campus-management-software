package de.tudarmstadt.campus.admin.rbac.domain;

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
import org.springframework.data.domain.Persistable;

/**
 * Which role may grant which role (spec section 1.4). Keeping the rule in a table rather than in code
 * makes it documentable and testable, and turns the effective grant set of a user into the union over
 * all of their roles.
 * <p>
 * Implements {@link Persistable} for the same reason as {@link UserRole}: the identifier is assigned,
 * so {@code save()} would otherwise merge instead of persist.
 */
@Entity
@Table(name = "role_grant")
@Getter
@Setter
@NoArgsConstructor
public class RoleGrant implements Persistable<RoleGrantId> {

    @EmbeddedId
    private RoleGrantId id;

    @MapsId("granterRoleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granter_role_id")
    private Role granterRole;

    @MapsId("grantableRoleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grantable_role_id")
    private Role grantableRole;

    @Transient
    private boolean newGrant = true;

    public RoleGrant(Role granterRole, Role grantableRole) {
        this.id = new RoleGrantId(granterRole.getId(), grantableRole.getId());
        this.granterRole = granterRole;
        this.grantableRole = grantableRole;
    }

    @Override
    public boolean isNew() {
        return newGrant;
    }

    @PostLoad
    @PostPersist
    void markAsStored() {
        this.newGrant = false;
    }
}
