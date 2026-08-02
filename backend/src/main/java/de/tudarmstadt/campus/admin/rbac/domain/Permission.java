package de.tudarmstadt.campus.admin.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single permission such as {@code POI_PUBLISH}. Enforced as a Spring Security authority, never as a
 * {@code ROLE_} prefixed role (spec section 1.2).
 */
@Entity
@Table(name = "permission")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 30)
    private String resource;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    public Permission(String code, String resource, String action, String description) {
        this.code = code;
        this.resource = resource;
        this.action = action;
        this.description = description;
    }
}
