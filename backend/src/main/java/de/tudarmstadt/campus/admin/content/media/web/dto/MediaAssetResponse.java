package de.tudarmstadt.campus.admin.content.media.web.dto;

import java.time.Instant;

/**
 * Metadata of an upload. The storage path stays inside the service layer — it is an implementation
 * detail of where the file happens to live, not something a client should learn.
 */
public record MediaAssetResponse(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        Long poiId,
        Instant uploadedAt,
        String uploadedByUsername) {
}
