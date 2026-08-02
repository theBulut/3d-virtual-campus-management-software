package de.tudarmstadt.campus.admin.content.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A single slot of a consultation offer: either weekly ({@link #dayOfWeek} set) or a one-off appointment
 * ({@code dayOfWeek} null, bounded by {@link #validFrom} and {@link #validTo}).
 */
@Entity
@Table(name = "consultation_event")
@Getter
@Setter
@NoArgsConstructor
public class ConsultationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    /** 1 (Monday) to 7 (Sunday), null for a one-off appointment. */
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "room_override", length = 50)
    private String roomOverride;

    @Column(columnDefinition = "text")
    private String note;

    public ConsultationEvent(Short dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
