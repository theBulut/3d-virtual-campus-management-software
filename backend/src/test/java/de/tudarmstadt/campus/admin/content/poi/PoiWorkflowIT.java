package de.tudarmstadt.campus.admin.content.poi;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance criterion of phase 6: the release workflow walked end to end through the HTTP layer,
 * plus the two refusals that make the separation of duties visible — scenarios S-09 to S-12.
 */
class PoiWorkflowIT extends AbstractContentIT {

    private static final String NEW_POI = """
            {"nameDe":"Lernraum S1|03 23","nameEn":"Study Room S1|03 23",
             "descriptionDe":"Ruhiger Arbeitsplatz.","category":"LIBRARY",
             "positionX":1.5,"positionY":0.0,"positionZ":12.0}""";

    /** S-09: a contributor creates a POI and submits it — DRAFT becomes IN_REVIEW. */
    @Test
    void aContributorCreatesAPoiAndSubmitsIt() throws Exception {
        AdminUser worker = account("s09_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        long poiId = createDraft(worker);

        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.published").value(false));
    }

    /** S-10: the same account may not release its own work — POI_PUBLISH is not part of the role. */
    @Test
    void aContributorCannotPublishItsOwnPoi() throws Exception {
        AdminUser worker = account("s10_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        long poiId = createDraft(worker);
        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(worker)))
                .andExpect(status().isForbidden());

        // The POI stayed where it was; a refused call must not move the workflow forward.
        mockMvc.perform(get("/api/pois/" + poiId).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    /**
     * S-11: the full loop — submit, reject with a reason, correct, submit again, release. The state after
     * each step is read back over the API, not from the entity.
     */
    @Test
    void theReviewLoopRunsFromDraftToPublished() throws Exception {
        AdminUser worker = account("s11_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("s11_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(worker);

        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(post("/api/pois/" + poiId + "/reject").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNote\":\"Bitte die Öffnungszeiten ergänzen.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reviewNote").value("Bitte die Öffnungszeiten ergänzen."));

        // The author can still edit — that is why canEdit allows DRAFT and IN_REVIEW.
        mockMvc.perform(put("/api/pois/" + poiId).with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Lernraum S1|03 23","nameEn":"Study Room S1|03 23",
                                 "descriptionDe":"Ruhiger Arbeitsplatz, 8 bis 20 Uhr geöffnet.",
                                 "category":"LIBRARY","positionX":1.5,"positionY":0.0,"positionZ":12.0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedByUsername").value("s11_leitung"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                // The note of the earlier rejection is gone; it no longer describes this content.
                .andExpect(jsonPath("$.reviewNote").doesNotExist());
    }

    /** S-12: POI_UPDATE_OWN is ownership, not a licence for everything a role can read. */
    @Test
    void aContributorCannotChangeSomebodyElsesPoi() throws Exception {
        AdminUser author = account("s12_autor", RoleCode.PROJEKTMITARBEITER);
        AdminUser other = account("s12_fremd", RoleCode.PROJEKTMITARBEITER);
        long poiId = createDraft(author);

        mockMvc.perform(put("/api/pois/" + poiId).with(as(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_POI))
                .andExpect(status().isForbidden());

        // Reading stays open — POI_READ_ALL is a different permission from POI_UPDATE_OWN.
        mockMvc.perform(get("/api/pois/" + poiId).with(as(other)))
                .andExpect(status().isOk());
    }

    /** Being assigned counts as ownership, so a handover actually hands over the editing right. */
    @Test
    void anAssignedContributorMayEdit() throws Exception {
        AdminUser author = account("assign_autor", RoleCode.PROJEKTMITARBEITER);
        AdminUser helper = account("assign_helfer", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("assign_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(author);

        mockMvc.perform(put("/api/pois/" + poiId).with(as(helper))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POI))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/pois/" + poiId + "/assignee").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + helper.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUsername").value("assign_helfer"));

        mockMvc.perform(put("/api/pois/" + poiId).with(as(helper))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POI))
                .andExpect(status().isOk());
    }

    /** Once released, the contributing role is locked out — editing again would bypass the review. */
    @Test
    void publishedContentIsClosedToTheContributor() throws Exception {
        AdminUser worker = account("pub_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("pub_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(worker);
        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)));
        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(lead)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/pois/" + poiId).with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POI))
                .andExpect(status().isForbidden());

        // The project lead holds POI_UPDATE_ANY and is not bound by the ownership rule.
        mockMvc.perform(put("/api/pois/" + poiId).with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POI))
                .andExpect(status().isOk());
    }

    /** The state machine, seen through HTTP: an illegal jump is 422, not 500 and not a silent success. */
    @Test
    void publishingADraftIsRefusedWith422() throws Exception {
        AdminUser worker = account("jump_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("jump_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(worker);

        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(lead)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void aRejectionWithoutAReasonIsRefused() throws Exception {
        AdminUser worker = account("note_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("note_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(worker);
        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pois/" + poiId + "/reject").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNote\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    /** Archiving is the one way out of PUBLISHED, and it is final. */
    @Test
    void publishedContentCanBeArchivedButNotRevived() throws Exception {
        AdminUser worker = account("arch_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("arch_leitung", RoleCode.PROJEKTLEITER);
        long poiId = createDraft(worker);
        mockMvc.perform(post("/api/pois/" + poiId + "/submit").with(as(worker)));
        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(lead)));

        mockMvc.perform(post("/api/pois/" + poiId + "/archive").with(as(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(post("/api/pois/" + poiId + "/publish").with(as(lead)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    /** The status filter of the review queue: what the project lead has to look at. */
    @Test
    void theListCanBeFilteredByStatus() throws Exception {
        AdminUser worker = account("filter_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("filter_leitung", RoleCode.PROJEKTLEITER);
        long submitted = createDraft(worker);
        createDraft(worker);
        mockMvc.perform(post("/api/pois/" + submitted + "/submit").with(as(worker)))
                .andExpect(status().isOk());
        flush();

        mockMvc.perform(get("/api/pois").param("status", "IN_REVIEW").with(as(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + submitted + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.status != 'IN_REVIEW')]").isEmpty());
    }

    private long createDraft(AdminUser author) throws Exception {
        String created = mockMvc.perform(post("/api/pois").with(as(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_POI))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdByUsername").value(author.getUsername()))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }
}
