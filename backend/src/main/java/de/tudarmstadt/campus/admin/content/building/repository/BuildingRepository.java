package de.tudarmstadt.campus.admin.content.building.repository;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByCode(String code);

    boolean existsByCode(String code);

    /** Backs {@code GET /api/public/buildings}, which only ever exposes published records. */
    List<Building> findByPublishedTrueOrderByCodeAsc();
}
