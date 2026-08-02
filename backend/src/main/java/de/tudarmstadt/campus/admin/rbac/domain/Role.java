package de.tudarmstadt.campus.admin.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One of the six fixed roles (spec section 1.1). Roles are bundles of permissions; the names are part of
 * the thesis and must not change.
 * <p>
 * {@code role_grant} — which role may hand out which role — is mapped by {@link RoleGrant} rather than by
 * an association here, so the table has exactly one writable mapping.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** True for all six roles; system roles cannot be deleted or renamed (INV-5). */
    @Column(name = "is_system", nullable = false)
    private boolean system = true;

    /** False for EXTERNE_PERSON, which is never assigned to a user (INV-4). */
    @Column(name = "is_assignable", nullable = false)
    private boolean assignable = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    public Role(String name, String displayName, String description, boolean assignable, int sortOrder) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.assignable = assignable;
        this.sortOrder = sortOrder;
    }
}
