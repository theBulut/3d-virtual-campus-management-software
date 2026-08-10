package de.tudarmstadt.campus.admin.content.building;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Buildings: the reference data POIs and consultation offers hang off. No workflow, but two guards that
 * matter — a unique key and no orphaned POIs (spec section 5.4).
 */
class BuildingIT extends AbstractContentIT {

    @Test
    void aProjectLeadCreatesReadsAndUpdatesABuilding() throws Exception {
        AdminUser lead = account("bld_leitung", RoleCode.PROJEKTLEITER);

        long id = create(lead, "B1|01", "Testgebäude");

        mockMvc.perform(get("/api/buildings/" + id).with(as(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("B1|01"))
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(put("/api/buildings/" + id).with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"B1|01","nameDe":"Testgebäude","nameEn":"Test Building",
                                 "street":"Hochschulstraße 1","postalCode":"64289","city":"Darmstadt",
                                 "latitude":49.8759,"longitude":8.6567,"published":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Test Building"))
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(delete("/api/buildings/" + id).with(as(lead)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aDuplicateBuildingKeyIsRefused() throws Exception {
        AdminUser lead = account("bld_dup_leitung", RoleCode.PROJEKTLEITER);
        create(lead, "B2|02", "Erstes Gebäude");

        mockMvc.perform(post("/api/buildings").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B2|02\",\"nameDe\":\"Zweites Gebäude\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILDING_CODE_ALREADY_USED"));
    }

    /** A POI without its building would lose its place on the map, so the deletion is refused. */
    @Test
    void aBuildingWithPoisCannotBeDeleted() throws Exception {
        AdminUser lead = account("bld_ref_leitung", RoleCode.PROJEKTLEITER);
        long buildingId = create(lead, "B3|03", "Gebäude mit POIs");

        mockMvc.perform(post("/api/pois").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Hörsaal","category":"LECTURE_HALL","buildingId":%d,
                                 "positionX":0.0,"positionY":0.0,"positionZ":0.0}"""
                                .formatted(buildingId)))
                .andExpect(status().isCreated());
        flush();

        mockMvc.perform(delete("/api/buildings/" + buildingId).with(as(lead)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILDING_HAS_POIS"));
    }

    /** Contributors read the reference data but do not maintain it. */
    @Test
    void aContributorCanReadButNotChangeBuildings() throws Exception {
        AdminUser lead = account("bld_ro_leitung", RoleCode.PROJEKTLEITER);
        AdminUser worker = account("bld_ro_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        long id = create(lead, "B4|04", "Nur lesen");

        mockMvc.perform(get("/api/buildings").with(as(worker)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/buildings/" + id).with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B4|04\",\"nameDe\":\"Umbenannt\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/buildings/" + id).with(as(worker)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownBuildingIs404() throws Exception {
        AdminUser lead = account("bld_404_leitung", RoleCode.PROJEKTLEITER);

        mockMvc.perform(get("/api/buildings/999999").with(as(lead)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"));
    }

    private long create(AdminUser lead, String code, String nameDe) throws Exception {
        String created = mockMvc.perform(post("/api/buildings").with(as(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\",\"nameDe\":\"%s\"}".formatted(code, nameDe)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }
}
