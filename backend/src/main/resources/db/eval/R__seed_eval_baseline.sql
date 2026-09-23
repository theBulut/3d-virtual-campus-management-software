-- Baseline of the usability study (eval/09_AUFGABEN_UND_AUSGANGSDATEN.md, tasks T1-T9).
--
-- Loaded only by the `eval` profile, which is the only one adding classpath:db/eval. These accounts can
-- therefore never reach a development or production database.
--
-- This file is the single definition of the starting state, and it is used twice:
--
--   Flyway  — on the first build of a fresh study database, as a repeatable migration.
--   psql    — before every session, through scripts/eval/eval-reset.sh.
--
-- That is why it deletes before it inserts. A seed built from ON CONFLICT DO NOTHING, as the demo seed
-- is, cannot undo what a participant changed: it would leave the account created in T1, the roles swapped
-- in T2 and the opening hours edited in T7 exactly as the previous session left them. Deterministic
-- delete-then-insert is the only form that restores the same starting state every time.
--
-- The study database is owned entirely by this file. Deleting all accounts is therefore correct here and
-- would be destructive anywhere else — scripts/eval/eval-reset.sh refuses to run against any database
-- other than campus_eval for exactly that reason.
--
-- Not touched: role, permission, role_permission, role_grant. The permission model is the object under
-- study and stays exactly as the versioned migrations define it.
--
-- One thing this file cannot produce: the audit entry for T8. An audit record is written by the
-- application, not by SQL, and inventing one would be a fabricated piece of evidence in a study that
-- asks participants to read it. eval-reset.sh creates it through a real API call afterwards.

BEGIN;

-- ---------------------------------------------------------------------------------------------------
-- Clear. Order follows the foreign keys: content before the accounts that own it, buildings last.
-- ---------------------------------------------------------------------------------------------------

-- The study starts from an empty log so the T8 entry is reliably within the 50 rows the audit page
-- loads — there is no pagination. Synthetic test records only; no research notes live in this database.
DELETE FROM audit_log;

-- consultation_event goes with it (ON DELETE CASCADE), user_role goes with the account.
DELETE FROM consultation;
DELETE FROM poi;
DELETE FROM admin_user;
DELETE FROM building;

-- Identifiers have to be the same in every session. The task card for T8 names a concrete offer id, and
-- without this the sequences would keep counting: the id printed on the sheet would be wrong from the
-- second session onwards. Restarting them is safe here only because this database holds nothing but the
-- records below.
ALTER SEQUENCE admin_user_id_seq RESTART WITH 1;
ALTER SEQUENCE building_id_seq RESTART WITH 1;
ALTER SEQUENCE poi_id_seq RESTART WITH 1;
ALTER SEQUENCE consultation_id_seq RESTART WITH 1;
ALTER SEQUENCE consultation_event_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_log_id_seq RESTART WITH 1;

-- ---------------------------------------------------------------------------------------------------
-- Accounts. Password for all of them is documented outside the repository, in the session guide.
-- must_change_password stays FALSE: changing a password is not one of the nine tasks, and an enforced
-- change would put every participant in front of a form the study does not measure.
-- ---------------------------------------------------------------------------------------------------

INSERT INTO admin_user (username, email, password_hash, first_name, last_name, organisation,
                        is_active, must_change_password)
VALUES
    ('eval_admin', 'eval_admin@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Eva', 'Admin', 'Evaluationsteam', TRUE, FALSE),
    ('eval_leitung', 'eval_leitung@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Lea', 'Leitung', 'Evaluationsteam', TRUE, FALSE),
    ('eval_mitarbeit', 'eval_mitarbeit@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Mario', 'Mitarbeit', 'Evaluationsteam', TRUE, FALSE),
    ('eval_personal', 'eval_personal@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Petra', 'Personal', 'Evaluationsteam', TRUE, FALSE),
    ('eval_betrieb', 'eval_betrieb@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Bernd', 'Betrieb', 'Evaluationsteam', TRUE, FALSE),
    -- The target of T2. Holds PROJEKTMITARBEITER and nothing else; the task turns that into PERSONAL.
    ('eval_wechsel', 'eval_wechsel@example.org',
     '$2a$12$RvTNKQMx6rViGtHjxIAkEOOmkRKj9HR3gzMezOn7SO3EqqPrSPbaC',
     'Wanda', 'Wechsel', 'Evaluationsteam', TRUE, FALSE);

-- eval_neuzugang is absent on purpose: T1 asks the participant to create it.

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
    ('eval_admin', 'ADMIN'),
    ('eval_leitung', 'PROJEKTLEITER'),
    ('eval_mitarbeit', 'PROJEKTMITARBEITER'),
    ('eval_personal', 'PERSONAL'),
    ('eval_betrieb', 'MAINTENANCE_DEV'),
    ('eval_wechsel', 'PROJEKTMITARBEITER')
) AS d(username, role_name)
JOIN admin_user u ON u.username = d.username
JOIN role r ON r.name = d.role_name;

-- ---------------------------------------------------------------------------------------------------
-- Buildings. EVAL|01 carries the content of T3-T6; EVAL|02 is the separate record T9 edits, so a
-- mistake there cannot damage the POI tasks.
-- ---------------------------------------------------------------------------------------------------

INSERT INTO building (code, name_de, name_en, street, postal_code, city,
                      latitude, longitude, model_ref, is_published,
                      position_x, position_y, position_z, rotation_y)
VALUES
    ('EVAL|01', 'Evaluationsgebäude', 'Evaluation building',
     'Evaluationsweg 3', '64289', 'Darmstadt',
     49.8728, 8.6512, 'eval_building_01', TRUE, 0, 0, 0, 0),
    ('EVAL|02', 'Beratungsgebäude Evaluation', 'Evaluation advice building',
     'Testweg 1', '64289', 'Darmstadt',
     49.8731, 8.6520, 'eval_building_02', TRUE, 0, 0, 0, 0);

-- ---------------------------------------------------------------------------------------------------
-- Points of interest. Three separate records for T4, T5 and T6, telling the same editorial story: a
-- failure in one task must not block the next one. The German description says room 212 and the English
-- one says 214 wherever the task is about spotting that difference.
--
-- "Infopunkt Evaluation" is absent: T3 asks the participant to create it.
-- ---------------------------------------------------------------------------------------------------

INSERT INTO poi (name_de, name_en, description_de, description_en, category, building_id,
                 position_x, position_y, position_z, status, review_note, created_by)
SELECT d.name_de, d.name_en, d.description_de, d.description_en, 'SERVICE',
       (SELECT id FROM building WHERE code = 'EVAL|01'),
       0, 0, 0, d.status, d.review_note,
       (SELECT id FROM admin_user WHERE username = 'eval_mitarbeit')
FROM (VALUES
    -- T4: awaiting review, with the discrepancy the project lead is meant to find and reject.
    ('Beratungspunkt Prüfung', 'Advice point review',
     'Beratung im Raum 212.', 'Advice in room 214.',
     'IN_REVIEW', NULL),
    -- T5: already rejected, with the feedback the contributor is meant to act on.
    ('Beratungspunkt Korrektur', 'Advice point correction',
     'Beratung im Raum 212.', 'Advice in room 214.',
     'DRAFT', 'Bitte in der englischen Beschreibung Raum 214 durch Raum 212 ersetzen.'),
    -- T6: correct in both languages, so the right decision is to publish it.
    ('Beratungspunkt Freigabe', 'Advice point approval',
     'Beratung im Raum 212.', 'Advice in room 212.',
     'IN_REVIEW', NULL)
) AS d(name_de, name_en, description_de, description_en, status, review_note);

-- ---------------------------------------------------------------------------------------------------
-- Consultation offers.
-- ---------------------------------------------------------------------------------------------------

INSERT INTO consultation (title_de, title_en, description_de, description_en, organisation,
                          building_id, room, contact_email, responsible_user_id, is_published, created_by)
VALUES
    -- T7. Responsibility hangs on responsible_user_id, not on the organisation name: without it
    -- eval_personal holds CONSULTATION_UPDATE_OWN but is not the owner, and every save would be a 403.
    ('Studienberatung Evaluation', 'Evaluation study advice',
     'Beratung zu Studienverlauf und Prüfungen.', 'Advice on curriculum and examinations.',
     'Evaluationsteam',
     (SELECT id FROM building WHERE code = 'EVAL|01'), '212', 'eval_beratung@example.org',
     (SELECT id FROM admin_user WHERE username = 'eval_personal'), TRUE,
     (SELECT id FROM admin_user WHERE username = 'eval_personal')),
    -- T8. A separate offer, so the audit entry the participant looks for cannot be confused with
    -- anything T7 produced. eval-reset.sh changes one of its slots to create that entry.
    ('Audit-Testberatung', 'Audit test advice',
     'Angebot für die Nachvollziehbarkeit im Protokoll.', 'Offer used for audit traceability.',
     'Evaluationsteam',
     (SELECT id FROM building WHERE code = 'EVAL|02'), '101', 'eval_audit@example.org',
     (SELECT id FROM admin_user WHERE username = 'eval_leitung'), TRUE,
     (SELECT id FROM admin_user WHERE username = 'eval_leitung'));

INSERT INTO consultation_event (consultation_id, day_of_week, start_time, end_time, note)
SELECT c.id, d.day_of_week, d.start_time::time, d.end_time::time, d.note
FROM (VALUES
    -- T7 target state: Tuesday becomes 11-13, Thursday goes, Friday 09-10 is added.
    ('Studienberatung Evaluation', 2, '10:00', '12:00', NULL),
    ('Studienberatung Evaluation', 4, '14:00', '16:00', NULL),
    -- The slot eval-reset.sh edits through the API to produce the T8 audit entry.
    ('Audit-Testberatung', 3, '09:00', '10:00', NULL)
) AS d(title_de, day_of_week, start_time, end_time, note)
JOIN consultation c ON c.title_de = d.title_de;

COMMIT;
