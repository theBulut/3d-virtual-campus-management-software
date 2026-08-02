-- Content: buildings, points of interest, consultation hours and media metadata.
-- Specification: docs/spec/01_ARCHITEKTUR_SPEC.md section 2.2.
--
-- poi.status is VARCHAR with a CHECK constraint rather than a PostgreSQL enum type
-- (docs/DECISIONS.md D-2). References to admin_user use ON DELETE SET NULL (D-9); references to
-- building deliberately do not, because deleting a building that still has POIs must be rejected
-- with 409 (spec section 5.4).

CREATE TABLE building (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,   -- e.g. S1|03
    name_de      VARCHAR(200) NOT NULL,
    name_en      VARCHAR(200),
    street       VARCHAR(200),
    postal_code  VARCHAR(10),
    city         VARCHAR(100),
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    model_ref    VARCHAR(255),                   -- reference to the 3D model in the Unity scene
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT REFERENCES admin_user(id) ON DELETE SET NULL
);

CREATE TABLE poi (
    id             BIGSERIAL PRIMARY KEY,
    name_de        VARCHAR(200) NOT NULL,
    name_en        VARCHAR(200),
    description_de TEXT,
    description_en TEXT,
    category       VARCHAR(50)  NOT NULL,
    building_id    BIGINT REFERENCES building(id),
    position_x     DOUBLE PRECISION NOT NULL,
    position_y     DOUBLE PRECISION NOT NULL,
    position_z     DOUBLE PRECISION NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- Derived, so status stays the single source of truth for the review workflow.
    is_published   BOOLEAN GENERATED ALWAYS AS (status = 'PUBLISHED') STORED,
    assigned_to    BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    review_note    TEXT,                         -- reason given when a submission is rejected
    published_at   TIMESTAMPTZ,
    published_by   BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    CONSTRAINT poi_status_known CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT poi_category_known CHECK (category IN
        ('LECTURE_HALL', 'LIBRARY', 'CAFETERIA', 'SERVICE', 'LAB', 'OTHER'))
);
CREATE INDEX idx_poi_status ON poi(status);
CREATE INDEX idx_poi_building ON poi(building_id);

CREATE TABLE consultation (
    id                  BIGSERIAL PRIMARY KEY,
    title_de            VARCHAR(200) NOT NULL,
    title_en            VARCHAR(200),
    description_de      TEXT,
    -- Not in the specification's DDL, but NFA-09 requires user facing content in German and English.
    description_en      TEXT,
    organisation        VARCHAR(150) NOT NULL,   -- department or institution
    building_id         BIGINT REFERENCES building(id),
    room                VARCHAR(50),
    contact_email       VARCHAR(255),
    responsible_user_id BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,  -- ownership for PERSONAL
    is_published        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT REFERENCES admin_user(id) ON DELETE SET NULL
);
CREATE INDEX idx_consultation_responsible ON consultation(responsible_user_id);

CREATE TABLE consultation_event (
    id              BIGSERIAL PRIMARY KEY,
    consultation_id BIGINT NOT NULL REFERENCES consultation(id) ON DELETE CASCADE,
    day_of_week     SMALLINT,          -- 1..7, NULL for a one-off appointment
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    valid_from      DATE,
    valid_to        DATE,
    room_override   VARCHAR(50),
    note            TEXT,
    CONSTRAINT consultation_event_time_order CHECK (end_time > start_time),
    CONSTRAINT consultation_event_day_range CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7)
);
CREATE INDEX idx_consultation_event_consultation ON consultation_event(consultation_id);

CREATE TABLE media_asset (
    id           BIGSERIAL PRIMARY KEY,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,      -- local volume, no object storage
    poi_id       BIGINT REFERENCES poi(id) ON DELETE SET NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by  BIGINT REFERENCES admin_user(id) ON DELETE SET NULL
);
CREATE INDEX idx_media_asset_poi ON media_asset(poi_id);
