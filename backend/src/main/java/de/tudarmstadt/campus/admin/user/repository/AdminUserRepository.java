package de.tudarmstadt.campus.admin.user.repository;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    /**
     * Backs {@code GET /api/users} with the filters of spec section 5.2. Every parameter is optional;
     * {@code null} means "do not restrict on this".
     * <p>
     * The casts are not decoration: an untyped {@code null} parameter reaches PostgreSQL as {@code bytea},
     * and {@code lower(bytea)} does not exist — without them every call without a {@code q} ends in a 500.
     */
    @Query("""
            select u from AdminUser u
            where (cast(:query as string) is null
                   or lower(u.username) like lower(concat('%', cast(:query as string), '%'))
                   or lower(u.email) like lower(concat('%', cast(:query as string), '%'))
                   or lower(u.firstName) like lower(concat('%', cast(:query as string), '%'))
                   or lower(u.lastName) like lower(concat('%', cast(:query as string), '%')))
              and (cast(:active as boolean) is null or u.active = :active)
              and (cast(:roleName as string) is null
                   or exists (select 1 from UserRole ur
                              where ur.user = u and ur.role.name = :roleName))
            """)
    Page<AdminUser> search(@Param("query") String query,
                           @Param("roleName") String roleName,
                           @Param("active") Boolean active,
                           Pageable pageable);

    Optional<AdminUser> findByUsername(String username);

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    /**
     * The login accepts either identifier. Students who register themselves remember their mail address,
     * not the username they picked once — and both columns are unique, so the result stays unambiguous.
     */
    @Query("""
            select u from AdminUser u
            where lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier)
            """)
    Optional<AdminUser> findByUsernameOrEmail(@Param("identifier") String identifier);

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
