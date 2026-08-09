package de.tudarmstadt.campus.admin.content.poi.repository;

import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.domain.PoiCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    Page<Poi> findByStatus(ContentStatus status, Pageable pageable);

    /**
     * Backs {@code GET /api/pois} with the filters of spec section 5.4. The casts keep an untyped null
     * parameter from reaching PostgreSQL as {@code bytea}, which once broke the unfiltered user search.
     */
    @Query("""
            select p from Poi p
            where (cast(:status as string) is null or p.status = :status)
              and (cast(:category as string) is null or p.category = :category)
              and (cast(:buildingId as long) is null or p.building.id = :buildingId)
              and (cast(:assignedTo as long) is null or p.assignedTo.id = :assignedTo)
              and (cast(:query as string) is null
                   or lower(p.nameDe) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.nameEn) like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<Poi> search(@Param("status") ContentStatus status,
                     @Param("category") PoiCategory category,
                     @Param("buildingId") Long buildingId,
                     @Param("assignedTo") Long assignedTo,
                     @Param("query") String query,
                     Pageable pageable);

    /** Loads a POI with the associations the response needs, avoiding a query per field. */
    @Query("""
            select p from Poi p
            left join fetch p.building
            left join fetch p.createdBy
            left join fetch p.assignedTo
            left join fetch p.publishedBy
            where p.id = :id
            """)
    Optional<Poi> findByIdWithDetails(@Param("id") Long id);

    /** Backs {@code GET /api/public/pois}, which exposes published POIs only. */
    List<Poi> findByStatusOrderByNameDeAsc(ContentStatus status);

    /** Deleting a building that still has POIs must be rejected with 409 (spec section 5.4). */
    long countByBuildingId(Long buildingId);

    long countByAssignedToId(Long userId);
}
