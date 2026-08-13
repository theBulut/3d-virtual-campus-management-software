-- Self-registration, playable accounts and the game state.
--
-- Turns EXTERNE_PERSON from a documentation-only row into a role real accounts carry. Until now the
-- specification said in INV-4 that the role is never assigned and is realised through permitAll on
-- /api/public/**. With international students registering themselves and their progress bound to their
-- account, that no longer holds — see docs/DECISIONS.md D-40.
--
-- Existing migrations are never edited (CLAUDE.md), so V4 keeps its original content and this file
-- corrects the two rows that carry the old rule.

-- 1. The role becomes assignable. Without this, RoleAssignmentService refuses it with
--    ROLE_NOT_ASSIGNABLE and no account could ever hold it.
UPDATE role
SET is_assignable = TRUE,
    description   = 'Spielt den 3D-Campus und liest veröffentlichte Inhalte. Wird bei der '
                    || 'Selbstregistrierung vergeben und bleibt beim Hochstufen erhalten.'
WHERE name = 'EXTERNE_PERSON';

-- 2. The role enters the grant sets of ADMIN and PROJEKTLEITER.
--
--    This is not cosmetic: assertCanManage demands that *all* roles of a target account lie inside the
--    caller's grant set. As long as EXTERNE_PERSON was in nobody's set, a self-registered account was
--    unreachable for every administrator — promoting it would have failed with TARGET_OUT_OF_SCOPE
--    before the first role could be added.
INSERT INTO role_grant (granter_role_id, grantable_role_id)
SELECT g.id, t.id
FROM role g
JOIN role t ON t.name = 'EXTERNE_PERSON'
WHERE g.name IN ('ADMIN', 'PROJEKTLEITER')
ON CONFLICT DO NOTHING;

-- 3. Scene coordinates for buildings.
--
--    Buildings carried geographic coordinates only; the 3D scene needs a position in its own coordinate
--    system. Deriving one from latitude and longitude would need the real anchor and scale of the Unity
--    scene, which is not known yet — an explicit, editable value is the honest solution. Default 0 keeps
--    existing rows valid under ddl-auto=validate.
ALTER TABLE building
    ADD COLUMN position_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN position_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN position_z DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN rotation_y DOUBLE PRECISION NOT NULL DEFAULT 0;

COMMENT ON COLUMN building.position_x IS 'Position in der Unity-Szene, nicht geografisch';
COMMENT ON COLUMN building.rotation_y IS 'Drehung um die Hochachse in Grad';

-- 4. One game state per account.
--
--    The document itself is opaque to the backend: Unity owns its format, the server stores and returns
--    it (docs/DECISIONS.md D-41). JSONB rather than text so a later evaluation can query inside it
--    without a migration. ON DELETE CASCADE because a game state without its account is meaningless —
--    unlike the audit trail, which deliberately survives with actor_id set to NULL.
CREATE TABLE game_state (
    user_id    BIGINT PRIMARY KEY REFERENCES admin_user(id) ON DELETE CASCADE,
    state      JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE game_state IS 'Spielstand je Konto; Format wird vom Unity-Client bestimmt';
