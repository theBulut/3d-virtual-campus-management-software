package de.tudarmstadt.campus.admin.content.consultation;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario S-13: administrative staff maintain their own consultation hours and nothing else (FA-13).
 */
class ConsultationIT extends AbstractContentIT {

    private static final String OFFER = """
            {"titleDe":"Studienberatung Maschinenbau","titleEn":"Mechanical Engineering Advice",
             "descriptionDe":"Fragen zum Studienverlauf.","organisation":"Fachgebiet Maschinenbau",
             "room":"B211","contactEmail":"beratung.mb@tu-darmstadt.de","published":true}""";

    /** S-13, first half: creating and maintaining an own offer works. */
    @Test
    void staffMaintainTheirOwnConsultationHours() throws Exception {
        AdminUser staff = account("s13_personal", RoleCode.PERSONAL);

        long id = createOffer(staff);

        String slot = mockMvc.perform(post("/api/consultations/" + id + "/events").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek":2,"startTime":"10:00:00","endTime":"12:00:00",
                                 "note":"Ohne Anmeldung"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("10:00:00"))
                .andReturn().getResponse().getContentAsString();
        long slotId = ((Number) JsonPath.read(slot, "$.id")).longValue();

        mockMvc.perform(put("/api/consultations/events/" + slotId).with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"startTime\":\"10:00:00\",\"endTime\":\"13:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").value("13:00:00"));

        mockMvc.perform(put("/api/consultations/" + id).with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)));

        mockMvc.perform(delete("/api/consultations/events/" + slotId).with(as(staff)))
                .andExpect(status().isNoContent());
    }

    /** S-13, second half: the same account gets nowhere near a POI. */
    @Test
    void staffCannotTouchPois() throws Exception {
        AdminUser staff = account("s13b_personal", RoleCode.PERSONAL);
        AdminUser worker = account("s13b_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        String poi = mockMvc.perform(post("/api/pois").with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Werkstatt","category":"LAB",
                                 "positionX":0.0,"positionY":0.0,"positionZ":0.0}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long poiId = ((Number) JsonPath.read(poi, "$.id")).longValue();

        mockMvc.perform(put("/api/pois/" + poiId).with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Werkstatt","category":"LAB",
                                 "positionX":0.0,"positionY":0.0,"positionZ":0.0}"""))
                .andExpect(status().isForbidden());

        // PERSONAL holds POI_READ_PUBLISHED, not POI_READ_ALL — the administrative list stays closed.
        mockMvc.perform(get("/api/pois").with(as(staff)))
                .andExpect(status().isForbidden());
    }

    /**
     * D-34: releasing a consultation offer needs CONSULTATION_UPDATE_ANY. Without it the flag in the
     * request is ignored rather than rejected, so staff can save their entry and the project lead decides
     * when it goes public.
     */
    @Test
    void staffCannotPublishTheirOwnOffer() throws Exception {
        AdminUser staff = account("s13c_personal", RoleCode.PERSONAL);
        AdminUser lead = account("s13c_leitung", RoleCode.PROJEKTLEITER);
        long id = createOffer(staff);

        mockMvc.perform(put("/api/consultations/" + id).with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(put("/api/consultations/" + id).with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                // Reassignment is part of the same wider permission and did not lose the owner.
                .andExpect(jsonPath("$.responsibleUsername").value("s13c_personal"));
    }

    /** Ownership again: another staff account is not responsible and is refused. */
    @Test
    void staffCannotEditSomebodyElsesOffer() throws Exception {
        AdminUser owner = account("s13d_owner", RoleCode.PERSONAL);
        AdminUser other = account("s13d_other", RoleCode.PERSONAL);
        long id = createOffer(owner);

        mockMvc.perform(put("/api/consultations/" + id).with(as(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/consultations/" + id + "/events").with(as(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":3,\"startTime\":\"09:00:00\",\"endTime\":\"10:00:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSlotThatEndsBeforeItStartsIsRefused() throws Exception {
        AdminUser staff = account("s13e_personal", RoleCode.PERSONAL);
        long id = createOffer(staff);

        mockMvc.perform(post("/api/consultations/" + id + "/events").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":3,\"startTime\":\"14:00:00\",\"endTime\":\"12:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    /** The creator becomes the responsible account, whatever the request says. */
    private long createOffer(AdminUser staff) throws Exception {
        String created = mockMvc.perform(post("/api/consultations").with(as(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibleUsername").value(staff.getUsername()))
                .andExpect(jsonPath("$.published").value(false))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }
}
