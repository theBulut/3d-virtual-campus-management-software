package de.tudarmstadt.campus.admin.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class RoleGrantId implements Serializable {

    @Column(name = "granter_role_id")
    private Long granterRoleId;

    @Column(name = "grantable_role_id")
    private Long grantableRoleId;

    public RoleGrantId(Long granterRoleId, Long grantableRoleId) {
        this.granterRoleId = granterRoleId;
        this.grantableRoleId = grantableRoleId;
    }
}
