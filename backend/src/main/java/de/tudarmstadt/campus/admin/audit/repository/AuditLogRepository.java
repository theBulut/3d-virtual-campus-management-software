package de.tudarmstadt.campus.admin.audit.repository;

import de.tudarmstadt.campus.admin.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);

    /**
     * Filtered view of {@code GET /api/audit}. {@code resourceTypes} is null for holders of AUDIT_READ
     * and restricted to the content resources for holders of AUDIT_READ_CONTENT only (spec section 5.5).
     */
    @Query("""
            select a from AuditLog a
            where (:actorId is null or a.actor.id = :actorId)
              and (:action is null or a.action = :action)
              and (:resourceTypes is null or a.resourceType in :resourceTypes)
              and (cast(:from as timestamp) is null or a.createdAt >= :from)
              and (cast(:to as timestamp) is null or a.createdAt <= :to)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
                          @Param("action") String action,
                          @Param("resourceTypes") Collection<String> resourceTypes,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
