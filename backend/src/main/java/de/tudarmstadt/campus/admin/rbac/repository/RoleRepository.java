package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findAllByOrderBySortOrderAsc();

    /** Loads a role together with its permissions, avoiding a second query per role. */
    @Query("select r from Role r left join fetch r.permissions where r.name = :name")
    Optional<Role> findByNameWithPermissions(@Param("name") String name);

    @Query("select distinct r from Role r left join fetch r.permissions order by r.sortOrder")
    List<Role> findAllWithPermissions();

    /** Permission codes held by a user, the basis for the {@code perms} claim (spec section 4.1). */
    @Query("""
            select distinct p.code
            from UserRole ur join ur.role r join r.permissions p
            where ur.user.id = :userId
            order by p.code
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Query("""
            select r.name
            from UserRole ur join ur.role r
            where ur.user.id = :userId
            order by r.sortOrder
            """)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
