package de.tudarmstadt.campus.admin.content.consultation.domain;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A consultation offer of a department, maintained by the PERSONAL role. Ownership runs through
 * {@link #responsibleUser} (spec section 1.2, {@code CONSULTATION_UPDATE_OWN}).
 */
@Entity
@Table(name = "consultation")
@Getter
@Setter
@NoArgsConstructor
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title_de", nullable = false, length = 200)
    private String titleDe;

    @Column(name = "title_en", length = 200)
    private String titleEn;

    @Column(name = "description_de", columnDefinition = "text")
    private String descriptionDe;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    /** Department or institution. */
    @Column(nullable = false, length = 150)
    private String organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(length = 50)
    private String room;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private AdminUser responsibleUser;

    /** Writable only with CONSULTATION_UPDATE_ANY, so PERSONAL cannot publish its own entries. */
    @Column(name = "is_published", nullable = false)
    private boolean published;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AdminUser createdBy;

    @OneToMany(mappedBy = "consultation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConsultationEvent> events = new ArrayList<>();

    public Consultation(String titleDe, String organisation) {
        this.titleDe = titleDe;
        this.organisation = organisation;
    }

    public void addEvent(ConsultationEvent event) {
        events.add(event);
        event.setConsultation(this);
    }
}
