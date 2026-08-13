package de.tudarmstadt.campus.admin.game.service;

import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.NotFoundException;
import de.tudarmstadt.campus.admin.game.domain.GameState;
import de.tudarmstadt.campus.admin.game.repository.GameStateRepository;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Reads and writes the progress of the calling account (FA-25).
 * <p>
 * Always the caller's own state: the account id comes from the token, never from the path, so there is
 * no identifier a client could tamper with. That is why the endpoints need no permission of their own —
 * like the profile, a game state is data about oneself.
 */
@Service
public class GameStateService {

    private static final Logger log = LoggerFactory.getLogger(GameStateService.class);

    /**
     * A save game is a handful of positions and flags. The limit is generous for that and small enough
     * that a client cannot use the account as free storage.
     */
    private static final int MAX_LENGTH = 64 * 1024;

    /** Only used to reject malformed documents; the content itself stays untouched. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GameStateRepository gameStates;
    private final AdminUserRepository adminUsers;

    public GameStateService(GameStateRepository gameStates, AdminUserRepository adminUsers) {
        this.gameStates = gameStates;
        this.adminUsers = adminUsers;
    }

    /** Empty for an account that has never played — the client then starts a new game. */
    @Transactional(readOnly = true)
    public Optional<String> find(long userId) {
        return gameStates.findById(userId).map(GameState::getState);
    }

    @Transactional
    public void save(long userId, String state) {
        assertUsable(state);

        GameState existing = gameStates.findById(userId).orElse(null);
        if (existing != null) {
            existing.setState(state);
            gameStates.save(existing);
            return;
        }

        AdminUser user = adminUsers.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Das Konto wurde nicht gefunden."));
        gameStates.save(new GameState(user, state));
        log.info("First game state stored for '{}'", user.getUsername());
    }

    /**
     * The document is opaque, but it has to be valid JSON: the column is JSONB, and PostgreSQL would
     * answer a broken document with a constraint violation and therefore a 500. Better a readable 400.
     */
    private void assertUsable(String state) {
        if (state == null || state.isBlank()) {
            throw new BadRequestException("GAME_STATE_REQUIRED", "Der Spielstand darf nicht leer sein.");
        }
        if (state.length() > MAX_LENGTH) {
            throw new BadRequestException("GAME_STATE_TOO_LARGE",
                    "Der Spielstand überschreitet die zulässige Größe von 64 KB.");
        }
        try {
            objectMapper.readTree(state);
        } catch (JacksonException ex) {
            throw new BadRequestException("GAME_STATE_MALFORMED",
                    "Der Spielstand ist kein gültiges JSON-Dokument.");
        }
    }
}
