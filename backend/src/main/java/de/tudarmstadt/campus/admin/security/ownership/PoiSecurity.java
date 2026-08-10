package de.tudarmstadt.campus.admin.security.ownership;

import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.content.poi.repository.PoiRepository;
import de.tudarmstadt.campus.admin.security.CampusUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ownership check behind {@code POI_UPDATE_OWN} (spec section 4.4, FA-12).
 * <p>
 * Referenced from {@code @PreAuthorize} as {@code @poiSecurity.canEdit(#id, authentication)}, so the
 * decision happens before the method runs and cannot be forgotten inside it.
 */
@Component("poiSecurity")
public class PoiSecurity {

    /**
     * Published content is closed to holders of POI_UPDATE_OWN: changing it again would bypass the
     * release step. IN_REVIEW stays open so a rejected POI can be corrected — the state machine, not
     * this check, refuses a second submission.
     */
    private static final Set<ContentStatus> EDITABLE_STATES =
            EnumSet.of(ContentStatus.DRAFT, ContentStatus.IN_REVIEW);

    private final PoiRepository pois;

    public PoiSecurity(PoiRepository pois) {
        this.pois = pois;
    }

    @Transactional(readOnly = true)
    public boolean canEdit(Long poiId, Authentication authentication) {
        if (poiId == null || !(authentication.getPrincipal() instanceof CampusUserDetails principal)) {
            return false;
        }
        return pois.findById(poiId).map(poi -> isOwner(poi, principal.getUserId())
                && EDITABLE_STATES.contains(poi.getStatus())).orElse(false);
    }

    /** Created it or was assigned to it — both count as ownership (spec section 1.2). */
    private static boolean isOwner(Poi poi, long userId) {
        return (poi.getCreatedBy() != null && poi.getCreatedBy().getId() == userId)
                || (poi.getAssignedTo() != null && poi.getAssignedTo().getId() == userId);
    }
}
