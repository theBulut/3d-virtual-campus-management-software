package de.tudarmstadt.campus.admin.game.web.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Everything the Unity client needs to build the campus, in one response.
 * <p>
 * One request instead of three: over WebGL every round trip is noticeable, and the three parts belong to
 * the same picture anyway.
 * <p>
 * What is inside depends on the caller's permissions (docs/DECISIONS.md D-42). A player sees published
 * content; whoever additionally holds the {@code _ALL} permissions sees drafts and submissions as well,
 * and only then is {@code status} filled at all. The filtering happens in the service — no query
 * parameter can widen it.
 */
public record ScenePayload(
        List<ScenePoi> pois,
        List<SceneBuilding> buildings,
        List<SceneConsultation> consultations) {

    /** A point in the scene. {@code null} status means the object is published. */
    public record ScenePoi(
            Long id,
            String nameDe,
            String nameEn,
            String descriptionDe,
            String descriptionEn,
            String category,
            String buildingCode,
            Vector3 position,
            String status) {
    }

    public record SceneBuilding(
            Long id,
            String code,
            String nameDe,
            String nameEn,
            String modelRef,
            Vector3 position,
            double rotationY,
            Boolean published) {
    }

    public record SceneConsultation(
            Long id,
            String titleDe,
            String titleEn,
            String descriptionDe,
            String organisation,
            String buildingCode,
            String room,
            String contactEmail,
            List<SceneSlot> slots,
            Boolean published) {
    }

    public record SceneSlot(Short dayOfWeek, LocalTime startTime, LocalTime endTime, String room) {
    }

    /** Matches Unity's Vector3, so the client can deserialise it without a custom converter. */
    public record Vector3(double x, double y, double z) {
    }
}
