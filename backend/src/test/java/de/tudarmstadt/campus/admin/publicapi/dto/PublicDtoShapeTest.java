package de.tudarmstadt.campus.admin.publicapi.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data minimisation as a rule instead of a habit (spec section 5.5, NFA-07).
 * <p>
 * {@code PublicApiIT} checks what the endpoints return today; this test checks the shape of the records
 * themselves, so a field added later fails here even if no test happens to call that endpoint. It needs
 * no container and runs in the Docker-free path.
 */
class PublicDtoShapeTest {

    /** Everything that belongs to the internal workflow or to the accounts behind an entry. */
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "status", "review", "publishedat", "publishedby", "assigned", "createdby", "createdat",
            "updatedat", "responsible", "actor", "password", "token", "username");

    @ParameterizedTest
    @ValueSource(classes = {PublicPoiResponse.class, PublicBuildingResponse.class,
            PublicConsultationResponse.class, PublicConsultationResponse.PublicSlot.class})
    void noPublicDtoExposesAnInternalField(Class<?> dto) {
        assertThat(dto.isRecord()).as("%s must be a record", dto.getSimpleName()).isTrue();

        for (RecordComponent component : dto.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertThat(FORBIDDEN_FRAGMENTS)
                    .as("field %s.%s", dto.getSimpleName(), component.getName())
                    .noneMatch(name::contains);
        }
    }

    /**
     * The contact address is the one deliberate exception to "no personal data": a consultation offer
     * without one is useless, and it is an institutional address, not a private one. The account that
     * maintains the entry stays invisible.
     */
    @Test
    void theConsultationViewShowsAContactButNotTheAccountBehindIt() {
        List<String> fields = Arrays.stream(PublicConsultationResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields).contains("contactEmail");
        assertThat(fields).doesNotContain("responsibleUserId", "responsibleUsername",
                "createdAt", "updatedAt");
    }
}
