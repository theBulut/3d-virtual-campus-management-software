package de.tudarmstadt.campus.admin.user.repository;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Number of active accounts holding the given role. INV-1 uses this to keep at least one active
     * ADMIN in the system.
     */
    @Query("""
            select count(ur)
            from UserRole ur
            where ur.role.name = :roleName and ur.user.active = true
            """)
    long countActiveUsersWithRole(@Param("roleName") String roleName);

    /**
     * Same as above but ignoring one account — needed to answer "would this deletion, deactivation or
     * revocation remove the last administrator?" before performing it.
     */
    @Query("""
            select count(ur)
            from UserRole ur
            where ur.role.name = :roleName and ur.user.active = true and ur.user.id <> :excludedUserId
            """)
    long countActiveUsersWithRoleExcluding(@Param("roleName") String roleName,
                                           @Param("excludedUserId") Long excludedUserId);
}
