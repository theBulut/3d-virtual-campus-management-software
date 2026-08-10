package de.tudarmstadt.campus.admin.content.media;

import com.jayway.jsonpath.JsonPath;
import de.tudarmstadt.campus.admin.rbac.RoleCode;
import de.tudarmstadt.campus.admin.support.AbstractContentIT;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploads (spec section 5.4): the size and type limits, and the fact that a client-supplied file name is
 * data rather than a path.
 */
class MediaIT extends AbstractContentIT {

    /** A one-pixel PNG is enough — nothing here decodes the image. */
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void aContributorUploadsAnImageAndReadsItBack() throws Exception {
        AdminUser worker = account("media_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        String uploaded = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "grundriss.png", "image/png", PNG_BYTES))
                        .with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("grundriss.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.uploadedByUsername").value("media_mitarbeit"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(uploaded, "$.id")).longValue();

        mockMvc.perform(get("/api/media/" + id).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("inline")))
                .andExpect(content().bytes(PNG_BYTES));

        mockMvc.perform(get("/api/media/" + id + "/metadata").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeBytes").value(PNG_BYTES.length));
    }

    @Test
    void anUploadCanBeAttachedToAPoi() throws Exception {
        AdminUser worker = account("media_poi_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        String poi = mockMvc.perform(post("/api/pois").with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameDe":"Hörsaal mit Bild","category":"LECTURE_HALL",
                                 "positionX":0.0,"positionY":0.0,"positionZ":0.0}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long poiId = ((Number) JsonPath.read(poi, "$.id")).longValue();

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "innen.jpg", "image/jpeg", PNG_BYTES))
                        .param("poiId", String.valueOf(poiId))
                        .with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poiId").value((int) poiId));
        flush();

        mockMvc.perform(get("/api/media").param("poiId", String.valueOf(poiId)).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("innen.jpg"));
    }

    @Test
    void aPdfIsRefused() throws Exception {
        AdminUser worker = account("media_typ_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf",
                                "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                        .with(as(worker)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anImageAboveFiveMegabytesIsRefused() throws Exception {
        AdminUser worker = account("media_size_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "riesig.png", "image/png", tooLarge))
                        .with(as(worker)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    /** The stored name is a UUID; a path in the client's file name must not survive into storage. */
    @Test
    void aPathInTheFileNameIsStrippedOff() throws Exception {
        AdminUser worker = account("media_pfad_mitarbeit", RoleCode.PROJEKTMITARBEITER);

        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "../../etc/passwd.png", "image/png",
                                PNG_BYTES))
                        .with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("passwd.png"));
    }

    /** MEDIA_DELETE belongs to the project lead, not to the contributing role. */
    @Test
    void aContributorCannotDeleteAnUpload() throws Exception {
        AdminUser worker = account("media_del_mitarbeit", RoleCode.PROJEKTMITARBEITER);
        AdminUser lead = account("media_del_leitung", RoleCode.PROJEKTLEITER);

        String uploaded = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "bild.webp", "image/webp", PNG_BYTES))
                        .with(as(worker)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(uploaded, "$.id")).longValue();

        mockMvc.perform(delete("/api/media/" + id).with(as(worker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/media/" + id).with(as(lead)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/media/" + id + "/metadata").with(as(lead)))
                .andExpect(status().isNotFound());
    }
}
