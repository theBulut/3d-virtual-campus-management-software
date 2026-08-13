package de.tudarmstadt.campus.admin.game.repository;

import de.tudarmstadt.campus.admin.game.domain.GameState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateRepository extends JpaRepository<GameState, Long> {
}
