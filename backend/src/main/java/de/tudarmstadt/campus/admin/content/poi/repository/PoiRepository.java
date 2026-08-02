package de.tudarmstadt.campus.admin.content.poi.repository;

import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoiRepository extends JpaRepository<Poi, Long> {

    Page<Poi> findByStatus(ContentStatus status, Pageable pageable);

    /** Backs {@code GET /api/public/pois}, which exposes published POIs only. */
    List<Poi> findByStatusOrderByNameDeAsc(ContentStatus status);

    /** Deleting a building that still has POIs must be rejected with 409 (spec section 5.4). */
    long countByBuildingId(Long buildingId);

    long countByAssignedToId(Long userId);
}
