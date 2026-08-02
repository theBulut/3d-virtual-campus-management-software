package de.tudarmstadt.campus.admin.content.poi.repository;

import de.tudarmstadt.campus.admin.content.building.domain.Building;
import de.tudarmstadt.campus.admin.content.building.repository.BuildingRepository;
import de.tudarmstadt.campus.admin.content.poi.domain.ContentStatus;
import de.tudarmstadt.campus.admin.content.poi.domain.Poi;
import de.tudarmstadt.campus.admin.support.AbstractIntegrationTest;
import de.tudarmstadt.campus.admin.support.TestEntities;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PoiRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PoiRepository pois;

    @Autowired
    private BuildingRepository buildings;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newPoisStartAsDraftAndAreNotPublished() {
        Poi saved = pois.save(TestEntities.poi("Neuer POI"));
        entityManager.flush();
        entityManager.clear();

        Poi reloaded = pois.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(reloaded.isPublished()).isFalse();
    }

    /**
     * {@code is_published} is a generated column: the database derives it from {@code status}, so the two
     * can never drift apart. Hibernate re-reads it after every write.
     */
    @Test
    void isPublishedFollowsTheStatusColumn() {
        Poi poi = pois.save(TestEntities.poi("Statuswechsel"));
        entityManager.flush();

        poi.setStatus(ContentStatus.IN_REVIEW);
        pois.save(poi);
        entityManager.flush();
        entityManager.clear();
        assertThat(pois.findById(poi.getId()).orElseThrow().isPublished()).isFalse();

        Poi inReview = pois.findById(poi.getId()).orElseThrow();
        inReview.setStatus(ContentStatus.PUBLISHED);
        pois.save(inReview);
        entityManager.flush();
        entityManager.clear();
        assertThat(pois.findById(poi.getId()).orElseThrow().isPublished()).isTrue();

        Poi published = pois.findById(poi.getId()).orElseThrow();
        published.setStatus(ContentStatus.ARCHIVED);
        pois.save(published);
        entityManager.flush();
        entityManager.clear();
        assertThat(pois.findById(poi.getId()).orElseThrow().isPublished()).isFalse();
    }

    @Test
    void theDatabaseRejectsAnUnknownStatus() {
        Poi poi = pois.save(TestEntities.poi("Ungültiger Status"));
        entityManager.flush();

        // Bypasses the enum on purpose: the CHECK constraint has to hold on its own (D-2).
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update poi set status = 'NOT_A_STATUS' where id = ?", poi.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publicListingReturnsPublishedPoisOnly() {
        Poi draft = TestEntities.poi("Entwurf");
        Poi published = TestEntities.poi("Veröffentlicht");
        published.setStatus(ContentStatus.PUBLISHED);
        pois.save(draft);
        pois.save(published);
        entityManager.flush();
        entityManager.clear();

        assertThat(pois.findByStatusOrderByNameDeAsc(ContentStatus.PUBLISHED))
                .extracting(Poi::getNameDe)
                .containsExactly("Veröffentlicht");
    }

    /** Deleting a building that still carries POIs has to be rejected with 409 (spec section 5.4). */
    @Test
    void countsPoisPerBuilding() {
        Building building = buildings.save(new Building("S1|01", "Hauptgebäude"));
        Poi poi = TestEntities.poi("POI im Gebäude");
        poi.setBuilding(building);
        pois.save(poi);
        entityManager.flush();

        assertThat(pois.countByBuildingId(building.getId())).isEqualTo(1);
    }

    @Test
    void theDatabaseKeepsABuildingThatStillHasPois() {
        Building building = buildings.save(new Building("S1|02", "Belegtes Gebäude"));
        Poi poi = TestEntities.poi("Hängender POI");
        poi.setBuilding(building);
        pois.save(poi);
        entityManager.flush();

        // No ON DELETE clause here on purpose: the service turns this into a 409 rather than
        // silently orphaning the POI.
        assertThatThrownBy(() -> jdbcTemplate.update("delete from building where id = ?", building.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
