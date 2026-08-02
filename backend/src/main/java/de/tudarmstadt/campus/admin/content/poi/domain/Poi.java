package de.tudarmstadt.campus.admin.content.poi.domain;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * A point of interest, the content type that runs through the review workflow (spec section 4.5).
 */
@Entity
@Table(name = "poi")
@Getter
@Setter
@NoArgsConstructor
public class Poi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_de", nullable = false, length = 200)
    private String nameDe;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "description_de", columnDefinition = "text")
    private String descriptionDe;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PoiCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    @Column(name = "position_z", nullable = false)
    private double positionZ;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status = ContentStatus.DRAFT;

    /**
     * Generated column, derived from {@link #status} by the database. Read only on the Java side;
     * Hibernate re-reads it after every insert and update.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "is_published", insertable = false, updatable = false)
    private boolean published;

    /** Editor responsible for this POI; counts as ownership alongside {@link #createdBy}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private AdminUser assignedTo;

    /** Reason given when a submission is rejected; mandatory on the reject transition. */
    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private AdminUser publishedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AdminUser createdBy;

    public Poi(String nameDe, PoiCategory category, double positionX, double positionY, double positionZ) {
        this.nameDe = nameDe;
        this.category = category;
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
    }
}
