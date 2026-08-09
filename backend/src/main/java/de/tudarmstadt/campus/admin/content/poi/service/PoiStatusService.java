package de.tudarmstadt.campus.admin.content.poi.service;

import de.tudarmstadt.campus.admin.common.exception.BadRequestException;
import de.tudarmstadt.campus.admin.common.exception.UnprocessableContentException;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The review workflow of spec section 4.5 as an explicit state machine.
 * <pre>
 *   (—) → DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED
 *           ↑         │
 *           └─────────┘   reject, review note mandatory
 * </pre>
 * Keeping the allowed transitions in one table rather than scattered {@code if} statements is what makes
 * the workflow testable cell by cell — and what makes "PROJEKTMITARBEITER contributes, PROJEKTLEITER
 * releases" (FA-10, FA-11) a property of the model rather than of the controller.
 */
@Service
public class PoiStatusService {

    private static final Map<ContentStatus, Set<ContentStatus>> ALLOWED = allowedTransitions();

    private static Map<ContentStatus, Set<ContentStatus>> allowedTransitions() {
        Map<ContentStatus, Set<ContentStatus>> transitions = new EnumMap<>(ContentStatus.class);
        transitions.put(ContentStatus.DRAFT, EnumSet.of(ContentStatus.IN_REVIEW));
        // From review either forward to published or back to the author for corrections.
        transitions.put(ContentStatus.IN_REVIEW, EnumSet.of(ContentStatus.PUBLISHED, ContentStatus.DRAFT));
        transitions.put(ContentStatus.PUBLISHED, EnumSet.of(ContentStatus.ARCHIVED));
        // Archived is final in the prototype; reviving content is not part of the scope (E-6).
        transitions.put(ContentStatus.ARCHIVED, EnumSet.noneOf(ContentStatus.class));
        return transitions;
    }

    public static boolean isAllowed(ContentStatus from, ContentStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    /** {@code DRAFT → IN_REVIEW}, triggered by the author (POI_SUBMIT_REVIEW). */
    public void submitForReview(Poi poi) {
        transition(poi, ContentStatus.IN_REVIEW);
    }

    /** {@code IN_REVIEW → PUBLISHED}; records who released it and when. */
    public void publish(Poi poi, AdminUser actor) {
        transition(poi, ContentStatus.PUBLISHED);
        poi.setPublishedAt(Instant.now());
        poi.setPublishedBy(actor);
        poi.setReviewNote(null);
    }

    /**
     * {@code IN_REVIEW → DRAFT}. The note is mandatory: a rejection without a reason gives the author
     * nothing to work with (spec section 5.4).
     */
    public void reject(Poi poi, String reviewNote) {
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED",
                    "Eine Zurückweisung benötigt eine Begründung.");
        }
        transition(poi, ContentStatus.DRAFT);
        poi.setReviewNote(reviewNote.trim());
    }

    /** {@code PUBLISHED → ARCHIVED}. */
    public void archive(Poi poi) {
        transition(poi, ContentStatus.ARCHIVED);
    }

    private void transition(Poi poi, ContentStatus target) {
        ContentStatus current = poi.getStatus();
        if (!isAllowed(current, target)) {
            throw new UnprocessableContentException("INVALID_STATUS_TRANSITION",
                    "Der Übergang von " + current + " nach " + target + " ist nicht zulässig.");
        }
        poi.setStatus(target);
    }
}
