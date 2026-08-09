package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.domain.UserRole;
import de.tudarmstadt.campus.admin.rbac.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select ur from UserRole ur join fetch ur.role where ur.user.id = :userId")
    List<UserRole> findByUserId(@Param("userId") Long userId);

    Optional<UserRole> findByUserIdAndRoleName(Long userId, String roleName);

    boolean existsByUserIdAndRoleName(Long userId, String roleName);

    long countByUserId(Long userId);

    /** Number of accounts holding a role, shown next to each role in the interface. */
    long countByRoleName(String roleName);

    void deleteByUserIdAndRoleName(Long userId, String roleName);
}
