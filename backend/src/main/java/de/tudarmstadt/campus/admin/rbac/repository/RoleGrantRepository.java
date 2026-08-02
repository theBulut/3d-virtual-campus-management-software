package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.domain.RoleGrant;
import de.tudarmstadt.campus.admin.rbac.domain.RoleGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoleGrantRepository extends JpaRepository<RoleGrant, RoleGrantId> {

    /**
     * The effective grant set of a user: the union of what all of their roles may hand out
     * (spec section 1.4). Drives {@code GET /api/users/me/grantable-roles} and the
     * {@code ROLE_NOT_GRANTABLE} check.
     */
    @Query("""
            select distinct rg.grantableRole.name
            from RoleGrant rg
            where rg.granterRole.name in :granterRoleNames
            order by rg.grantableRole.name
            """)
    List<String> findGrantableRoleNames(@Param("granterRoleNames") Collection<String> granterRoleNames);

    @Query("""
            select case when count(rg) > 0 then true else false end
            from RoleGrant rg
            where rg.granterRole.name in :granterRoleNames and rg.grantableRole.name = :targetRoleName
            """)
    boolean canGrant(@Param("granterRoleNames") Collection<String> granterRoleNames,
                     @Param("targetRoleName") String targetRoleName);
}
