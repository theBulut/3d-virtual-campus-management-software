package de.tudarmstadt.campus.admin.game;

import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The progress of a player (FA-25). Two properties matter: a new account starts a new game, and a state
 * belongs to exactly one account and is unreachable from any other.
 */
class GameStateIT extends AbstractContentIT {

    private static final String SAVE = """
            {"position":{"x":12.5,"y":0.0,"z":34.2},"visited":["S1|03","S2|02"],"minutesPlayed":17}""";

    /** A freshly registered account has nothing stored — the client then starts from the beginning. */
    @Test
    void aNewAccountHasNoState() throws Exception {
        AdminUser player = account("state_new", RoleCode.EXTERNE_PERSON);

        mockMvc.perform(get("/api/game/state").with(as(player)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aStateSurvivesAndIsReplacedOnTheNextSave() throws Exception {
        AdminUser player = account("state_roundtrip", RoleCode.EXTERNE_PERSON);

        mockMvc.perform(put("/api/game/state").with(as(player))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isNoContent());
        flush();

        mockMvc.perform(get("/api/game/state").with(as(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesPlayed").value(17))
                .andExpect(jsonPath("$.visited[0]").value("S1|03"));

        mockMvc.perform(put("/api/game/state").with(as(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutesPlayed\":42}"))
                .andExpect(status().isNoContent());
        flush();

        // Replaced, not merged: the client owns the document as a whole.
        mockMvc.perform(get("/api/game/state").with(as(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesPlayed").value(42))
                .andExpect(jsonPath("$.visited").doesNotExist());
    }

    /**
     * The account comes from the token, so there is no identifier a client could point at somebody else.
     * Two accounts, two states, no way from one to the other.
     */
    @Test
    void everyAccountSeesOnlyItsOwnState() throws Exception {
        AdminUser first = account("state_first", RoleCode.EXTERNE_PERSON);
        AdminUser second = account("state_second", RoleCode.EXTERNE_PERSON);

        mockMvc.perform(put("/api/game/state").with(as(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"first\"}"))
                .andExpect(status().isNoContent());
        flush();

        mockMvc.perform(get("/api/game/state").with(as(second)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/game/state").with(as(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"second\"}"))
                .andExpect(status().isNoContent());
        flush();

        mockMvc.perform(get("/api/game/state").with(as(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("first"));
    }

    /** Every role that may play may save — including an administration playing its own campus. */
    @Test
    void anAdministrationHasAGameStateToo() throws Exception {
        AdminUser admin = account("state_admin", RoleCode.ADMIN);

        mockMvc.perform(put("/api/game/state").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isNoContent());
        flush();

        mockMvc.perform(get("/api/game/state").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesPlayed").value(17));
    }

    @Test
    void aBrokenDocumentIsRefusedWithAReadableError() throws Exception {
        AdminUser player = account("state_broken", RoleCode.EXTERNE_PERSON);

        mockMvc.perform(put("/api/game/state").with(as(player))
                        .contentType(MediaType.APPLICATION_JSON).content("kein json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAME_STATE_MALFORMED"));
    }

    @Test
    void anOversizedDocumentIsRefused() throws Exception {
        AdminUser player = account("state_large", RoleCode.EXTERNE_PERSON);
        String tooLarge = "{\"padding\":\"" + "x".repeat(65 * 1024) + "\"}";

        mockMvc.perform(put("/api/game/state").with(as(player))
                        .contentType(MediaType.APPLICATION_JSON).content(tooLarge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAME_STATE_TOO_LARGE"));
    }

    @Test
    void withoutASessionThereIsNoState() throws Exception {
        mockMvc.perform(get("/api/game/state")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/game/state")
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isUnauthorized());
    }
}
