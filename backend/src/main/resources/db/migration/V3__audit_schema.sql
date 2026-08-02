-- Audit log for every write and for denied access (spec section 2.3 and 4.6, FA-15).
--
-- actor_id uses ON DELETE SET NULL and actor_username is denormalised on purpose, so an entry survives
-- the deletion of the account that caused it (docs/DECISIONS.md D-9).

CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_id       BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    actor_username VARCHAR(64),
    action         VARCHAR(60)  NOT NULL,       -- see the catalogue in spec section 4.6
    resource_type  VARCHAR(40)  NOT NULL,       -- USER, ROLE, POI, BUILDING, CONSULTATION, MEDIA, AUTH
    resource_id    VARCHAR(64),
    before_state   JSONB,
    after_state    JSONB,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(255),
    success        BOOLEAN NOT NULL DEFAULT TRUE,
    error_code     VARCHAR(60),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_id);
