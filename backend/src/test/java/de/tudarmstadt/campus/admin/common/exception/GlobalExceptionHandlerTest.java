package de.tudarmstadt.campus.admin.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import de.tudarmstadt.campus.admin.audit.service.AuditService;
import de.tudarmstadt.campus.admin.config.SecurityConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the error format of spec section 4.7 and NFA-07 (no internal details in responses).
 */
// This slice is about the error format alone. Security is excluded on purpose: @WebMvcTest picks up
// SecurityConfig and every Filter bean, which would drag the whole JWT infrastructure into a test that
// never authenticates anything. The security paths have their own coverage in AuthIntegrationIT.
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.ThrowingController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "de\\.tudarmstadt\\.campus\\.admin\\.security\\..*")})
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * The handler writes the ACCESS_DENIED audit entry (D-10); this slice is about the error format, so
     * the write is stubbed out. That it really happens is covered by {@code AuditTrailIT}.
     */
    @MockitoBean
    private AuditService auditService;

    @Test
    void mapsApiExceptionToItsOwnStatusAndCode() throws Exception {
        mockMvc.perform(get("/__test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"))
                .andExpect(jsonPath("$.message").value("Der letzte Administrator kann nicht entfernt werden."))
                .andExpect(jsonPath("$.path").value("/__test/conflict"));
    }

    @Test
    void mapsNotFoundException() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void reportsValidationErrorsPerField() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    /**
     * The exception that {@code @PreAuthorize} raises must become a 403, not a 500. A
     * {@code @ControllerAdvice} sees it before Spring Security's {@code ExceptionTranslationFilter}, so
     * the handler for it has to exist.
     */
    @Test
    void mapsAuthorizationDeniedToForbidden() throws Exception {
        mockMvc.perform(get("/__test/authorization-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * Guards against the catch-all swallowing Spring MVC's own exceptions, which already carry a status.
     */
    @Test
    void keepsTheStatusOfSpringMvcExceptions() throws Exception {
        mockMvc.perform(get("/__test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/__test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void hidesInternalDetailsOfUnexpectedErrors() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Es ist ein unerwarteter Fehler aufgetreten."))
                .andExpect(content().string(not(containsString("connection string"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void omitsFieldErrorsWhenThereAreNone() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    /**
     * Spring Boot 4 ships Jackson 3 while jackson-databind 2 is on the classpath for JJWT. This asserts
     * that timestamps are still serialised as ISO-8601 and not as epoch numbers (spec section 5).
     */
    @Test
    void serialisesTimestampAsIso8601() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(jsonPath("$.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")));
    }

    @RestController
    @RequestMapping("/__test")
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("USER_NOT_FOUND", "Der Nutzer wurde nicht gefunden.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("LAST_ADMIN_PROTECTED",
                    "Der letzte Administrator kann nicht entfernt werden.");
        }

        @GetMapping("/authorization-denied")
        void authorizationDenied() {
            throw new AuthorizationDeniedException("denied by @PreAuthorize");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("connection string postgres://secret@db/campus");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload payload) {
            // Body is rejected before the method runs.
        }

        record Payload(@NotBlank String username) {
        }
    }
}
