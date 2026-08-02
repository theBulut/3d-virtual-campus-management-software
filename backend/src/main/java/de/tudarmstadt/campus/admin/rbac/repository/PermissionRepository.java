package de.tudarmstadt.campus.admin.rbac.repository;

import de.tudarmstadt.campus.admin.rbac.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    /** Ordered for {@code GET /api/permissions}, which groups the catalogue by resource. */
    List<Permission> findAllByOrderByResourceAscCodeAsc();
}
