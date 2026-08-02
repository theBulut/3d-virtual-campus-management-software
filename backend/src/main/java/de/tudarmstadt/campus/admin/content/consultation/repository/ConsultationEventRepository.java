package de.tudarmstadt.campus.admin.content.consultation.repository;

import de.tudarmstadt.campus.admin.content.consultation.domain.ConsultationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationEventRepository extends JpaRepository<ConsultationEvent, Long> {

    List<ConsultationEvent> findByConsultationIdOrderByDayOfWeekAscStartTimeAsc(Long consultationId);
}
