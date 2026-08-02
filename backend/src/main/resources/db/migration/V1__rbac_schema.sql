-- RBAC core: accounts, roles, permissions and the two relations between them.
-- Specification: docs/spec/01_ARCHITEKTUR_SPEC.md section 2.1.
--
-- Every reference back to admin_user carries ON DELETE SET NULL. The specification omits the clause,
-- which makes DELETE /api/users/{id} fail with a foreign key violation for any account that ever granted
-- a role or created content. See docs/DECISIONS.md (D-9).

CREATE TABLE admin_user (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(64)  NOT NULL UNIQUE,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    organisation         VARCHAR(150),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Invalidates access tokens: role change, deactivation, password change (spec section 4.2).
    token_version        INTEGER      NOT NULL DEFAULT 0,
    -- Invalidates refresh tokens: password change, password reset, deactivation. Separate from
    -- token_version so a role change does not force a new login (docs/DECISIONS.md D-3).
    refresh_version      INTEGER      NOT NULL DEFAULT 0,
    -- Forced password change on first login outside dev (spec section 7.1).
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT REFERENCES admin_user(id) ON DELETE SET NULL
);

CREATE TABLE role (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(50)  NOT NULL UNIQUE,   -- ADMIN, PROJEKTLEITER, ...
    display_name  VARCHAR(100) NOT NULL,
    description   TEXT         NOT NULL,
    is_system     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_assignable BOOLEAN      NOT NULL DEFAULT TRUE,  -- FALSE for EXTERNE_PERSON (INV-4)
    sort_order    INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(60) NOT NULL UNIQUE,    -- POI_PUBLISH, ...
    resource    VARCHAR(30) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    description TEXT        NOT NULL,
    -- PROFILE and DATA extend the vocabulary of the specification, which lists no resource for
    -- PROFILE_UPDATE_OWN and DATA_EXPORT (docs/DECISIONS.md D-14).
    CONSTRAINT permission_resource_known CHECK (resource IN
        ('USER', 'ROLE', 'PROFILE', 'POI', 'BUILDING', 'CONSULTATION', 'MEDIA', 'AUDIT', 'SYSTEM', 'DATA'))
);

CREATE TABLE user_role (
    user_id     BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES role(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);
-- The primary key only serves lookups by user. Counting the holders of a role (INV-1) goes the other way.
CREATE INDEX idx_user_role_role ON user_role(role_id);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Which role may grant which role (spec section 1.4). EXTERNE_PERSON never appears here.
CREATE TABLE role_grant (
    granter_role_id   BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    grantable_role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (granter_role_id, grantable_role_id)
);
