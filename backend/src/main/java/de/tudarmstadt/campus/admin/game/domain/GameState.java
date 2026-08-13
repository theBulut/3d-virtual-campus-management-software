package de.tudarmstadt.campus.admin.game.domain;

import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * The progress of one account in the 3D campus.
 * <p>
 * The document is stored as it arrives and handed back unchanged — the backend never looks inside it.
 * That is deliberate: the format belongs to the Unity client and will change with the game, while this
 * side would otherwise need a migration for every new field (docs/DECISIONS.md D-41). JSONB rather than
 * text so a later evaluation can still query into it.
 */
@Entity
@Table(name = "game_state")
@Getter
@Setter
@NoArgsConstructor
public class GameState {

    /** Shared with the account: one state per player, never two. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private AdminUser user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String state;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameState(AdminUser user, String state) {
        this.user = user;
        this.state = state;
    }
}
