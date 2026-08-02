package de.tudarmstadt.campus.admin.content.poi.domain;

/**
 * States of the review workflow (spec section 4.5). Stored as VARCHAR with a CHECK constraint rather
 * than a PostgreSQL enum type (docs/DECISIONS.md D-2).
 */
public enum ContentStatus {

    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    ARCHIVED
}
