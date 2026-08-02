package de.tudarmstadt.campus.admin.content.consultation.repository;

import de.tudarmstadt.campus.admin.content.consultation.domain.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    /** Ownership check for CONSULTATION_UPDATE_OWN (spec section 4.4). */
    boolean existsByIdAndResponsibleUserId(Long id, Long userId);

    List<Consultation> findByResponsibleUserId(Long userId);

    /** Backs {@code GET /api/public/consultations}. */
    List<Consultation> findByPublishedTrueOrderByTitleDeAsc();
}
