package de.tudarmstadt.campus.admin.content.building.domain;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "building")
@Getter
@Setter
@NoArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Campus building code, for example {@code S1|03}. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name_de", nullable = false, length = 200)
    private String nameDe;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(length = 200)
    private String street;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(length = 100)
    private String city;

    private Double latitude;

    private Double longitude;

    /** Reference to the 3D model in the Unity scene. */
    @Column(name = "model_ref", length = 255)
    private String modelRef;

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

    public Building(String code, String nameDe) {
        this.code = code;
        this.nameDe = nameDe;
    }
}
