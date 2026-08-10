package de.tudarmstadt.campus.admin.content.poi.service;

import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.UnprocessableContentException;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.domain.PoiCategory;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every cell of the state machine in spec section 4.5 — all sixteen combinations of source and target
 * state, not only the four allowed ones. Runs without Spring or a database.
 */
class PoiStatusServiceTest {

    private final PoiStatusService statusService = new PoiStatusService();

    /** The four transitions the specification allows; everything else must be refused. */
    private static final Set<List<ContentStatus>> ALLOWED = Set.of(
            List.of(ContentStatus.DRAFT, ContentStatus.IN_REVIEW),
            List.of(ContentStatus.IN_REVIEW, ContentStatus.PUBLISHED),
            List.of(ContentStatus.IN_REVIEW, ContentStatus.DRAFT),
            List.of(ContentStatus.PUBLISHED, ContentStatus.ARCHIVED));

    static Stream<Arguments> allTransitions() {
        List<Arguments> cells = new ArrayList<>();
        for (ContentStatus from : ContentStatus.values()) {
            for (ContentStatus to : ContentStatus.values()) {
                cells.add(Arguments.of(from, to));
            }
        }
        return cells.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    void onlyTheDocumentedTransitionsAreAllowed(ContentStatus from, ContentStatus to) {
        boolean expected = ALLOWED.contains(List.of(from, to));

        assertThat(PoiStatusService.isAllowed(from, to))
                .as("%s to %s", from, to)
                .isEqualTo(expected);
    }

    @Test
    void submittingMovesADraftIntoReview() {
        Poi poi = poi(ContentStatus.DRAFT);

        statusService.submitForReview(poi);

        assertThat(poi.getStatus()).isEqualTo(ContentStatus.IN_REVIEW);
    }

    /** Scenario S-09 in reverse: a POI already under review cannot be submitted again. */
    @Test
    void submittingTwiceIsRefused() {
        Poi poi = poi(ContentStatus.IN_REVIEW);

        assertThatThrownBy(() -> statusService.submitForReview(poi))
                .isInstanceOf(UnprocessableContentException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
    }

    @Test
    void publishingRecordsWhoReleasedItAndWhen() {
        Poi poi = poi(ContentStatus.IN_REVIEW);
        poi.setReviewNote("Bitte Öffnungszeiten ergänzen.");
        AdminUser leitung = new AdminUser();
        leitung.setUsername("demo_leitung");

        statusService.publish(poi, leitung);

        assertThat(poi.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(poi.getPublishedBy()).isSameAs(leitung);
        assertThat(poi.getPublishedAt()).isNotNull();
        // The old rejection note would be misleading on published content.
        assertThat(poi.getReviewNote()).isNull();
    }

    @Test
    void aDraftCannotBePublishedDirectly() {
        Poi poi = poi(ContentStatus.DRAFT);

        assertThatThrownBy(() -> statusService.publish(poi, null))
                .isInstanceOf(UnprocessableContentException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
        assertThat(poi.getStatus()).as("a refused transition changes nothing")
                .isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void rejectingSendsItBackToTheAuthorWithAReason() {
        Poi poi = poi(ContentStatus.IN_REVIEW);

        statusService.reject(poi, "  Koordinaten stimmen nicht.  ");

        assertThat(poi.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(poi.getReviewNote()).isEqualTo("Koordinaten stimmen nicht.");
    }

    @Test
    void rejectingWithoutAReasonIsRefused() {
        Poi poi = poi(ContentStatus.IN_REVIEW);

        assertThatThrownBy(() -> statusService.reject(poi, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_NOTE_REQUIRED");
        assertThat(poi.getStatus()).isEqualTo(ContentStatus.IN_REVIEW);
    }

    @Test
    void archivingIsOnlyPossibleFromPublished() {
        assertThatCode(() -> statusService.archive(poi(ContentStatus.PUBLISHED)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> statusService.archive(poi(ContentStatus.DRAFT)))
                .isInstanceOf(UnprocessableContentException.class);
    }

    /** Archived is final in the prototype — reviving content is out of scope (E-6). */
    @Test
    void archivedContentHasNoWayBack() {
        for (ContentStatus target : ContentStatus.values()) {
            assertThat(PoiStatusService.isAllowed(ContentStatus.ARCHIVED, target))
                    .as("ARCHIVED to %s", target)
                    .isFalse();
        }
    }

    private static Poi poi(ContentStatus status) {
        Poi poi = new Poi("Testeintrag", PoiCategory.OTHER, 1.0, 2.0, 3.0);
        poi.setStatus(status);
        return poi;
    }
}
