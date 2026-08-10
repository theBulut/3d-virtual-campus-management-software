-- Demo data for the dev profile: one account per role plus enough content to walk through the review
-- workflow and the public interface.
--
-- A repeatable migration in its own Flyway location. Only the dev profile adds classpath:db/demo, so
-- these accounts never reach docker or prod (docs/DECISIONS.md D-8). Repeatable migrations run after all
-- versioned ones and re-run whenever this file changes, which is why every statement is idempotent.
--
-- All demo accounts share the password "demo-passwort". That a known hash sits in the repository is
-- acceptable precisely because this file is never loaded outside dev.

INSERT INTO admin_user (username, email, password_hash, first_name, last_name, organisation, is_active)
VALUES
    ('demo_admin', 'demo.admin@tu-darmstadt.de',
     '$2a$12$cK8Sm9AtFiH2dvQ1iA4UE.rbSUzDkjt7MnLBxmjbgW7kaSlPPqQ5K',
     'Demo', 'Systemadministration', 'AG Serious Games', TRUE),
    ('demo_leitung', 'demo.leitung@tu-darmstadt.de',
     '$2a$12$cK8Sm9AtFiH2dvQ1iA4UE.rbSUzDkjt7MnLBxmjbgW7kaSlPPqQ5K',
     'Demo', 'Projektleitung', 'Projektleitung 3D Campus', TRUE),
    ('demo_mitarbeit', 'demo.mitarbeit@tu-darmstadt.de',
     '$2a$12$cK8Sm9AtFiH2dvQ1iA4UE.rbSUzDkjt7MnLBxmjbgW7kaSlPPqQ5K',
     'Demo', 'Projektmitarbeit', 'Story++', TRUE),
    ('demo_personal', 'demo.personal@tu-darmstadt.de',
     '$2a$12$cK8Sm9AtFiH2dvQ1iA4UE.rbSUzDkjt7MnLBxmjbgW7kaSlPPqQ5K',
     'Demo', 'Verwaltungspersonal', 'Fachgebiet Informatik', TRUE),
    ('demo_devops', 'demo.devops@tu-darmstadt.de',
     '$2a$12$cK8Sm9AtFiH2dvQ1iA4UE.rbSUzDkjt7MnLBxmjbgW7kaSlPPqQ5K',
     'Demo', 'Betrieb', 'Technischer Betrieb', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
    ('demo_admin', 'ADMIN'),
    ('demo_leitung', 'PROJEKTLEITER'),
    ('demo_mitarbeit', 'PROJEKTMITARBEITER'),
    ('demo_personal', 'PERSONAL'),
    ('demo_devops', 'MAINTENANCE_DEV')
) AS m(username, role_name)
JOIN admin_user u ON u.username = m.username
JOIN role r ON r.name = m.role_name
ON CONFLICT DO NOTHING;

INSERT INTO building (code, name_de, name_en, street, postal_code, city, latitude, longitude, model_ref, is_published)
VALUES
    ('S1|03', 'Altes Hauptgebäude', 'Old Main Building', 'Hochschulstraße 1', '64289', 'Darmstadt', 49.8759, 8.6567, 'models/s1_03.glb', TRUE),
    ('S2|02', 'Piloty-Gebäude', 'Piloty Building', 'Hochschulstraße 10', '64289', 'Darmstadt', 49.8776, 8.6540, 'models/s2_02.glb', TRUE),
    ('S1|01', 'Karo 5', 'Karo 5', 'Karolinenplatz 5', '64289', 'Darmstadt', 49.8748, 8.6559, 'models/s1_01.glb', TRUE),
    ('S3|21', 'Hörsaalgebäude', 'Lecture Hall Building', 'Franziska-Braun-Straße 7', '64287', 'Darmstadt', 49.8863, 8.6580, 'models/s3_21.glb', TRUE),
    ('L4|01', 'Lichtwiese Mensa', 'Lichtwiese Canteen', 'Alarich-Weiss-Straße 1', '64287', 'Darmstadt', 49.8628, 8.6813, 'models/l4_01.glb', FALSE)
ON CONFLICT (code) DO NOTHING;

-- Twelve POIs across all four states, so the review queue and the public interface both have content.
INSERT INTO poi (name_de, name_en, description_de, description_en, category, building_id,
                 position_x, position_y, position_z, status, review_note, published_at, published_by, created_by, assigned_to)
SELECT d.name_de, d.name_en, d.description_de, d.description_en, d.category,
       (SELECT id FROM building WHERE code = d.building_code),
       d.x, d.y, d.z, d.status, d.review_note,
       CASE WHEN d.status = 'PUBLISHED' THEN now() ELSE NULL END,
       CASE WHEN d.status = 'PUBLISHED' THEN (SELECT id FROM admin_user WHERE username = 'demo_leitung') ELSE NULL END,
       (SELECT id FROM admin_user WHERE username = d.created_by),
       (SELECT id FROM admin_user WHERE username = d.assigned_to)
FROM (VALUES
    ('Audimax', 'Audimax', 'Größter Hörsaal der Universität mit 800 Plätzen.', 'The largest lecture hall with 800 seats.', 'LECTURE_HALL', 'S1|03', 12.5, 0.0, 34.2, 'PUBLISHED', NULL, 'demo_mitarbeit', 'demo_mitarbeit'),
    ('Universitäts- und Landesbibliothek', 'University Library', 'Zentrale Bibliothek mit Lesesälen und Gruppenarbeitsräumen.', 'Central library with reading rooms.', 'LIBRARY', 'S1|01', 4.0, 0.0, 18.7, 'PUBLISHED', NULL, 'demo_mitarbeit', NULL),
    ('Mensa Stadtmitte', 'City Centre Canteen', 'Mittagsverpflegung von 11:30 bis 14:30 Uhr.', 'Lunch from 11:30 to 14:30.', 'CAFETERIA', 'S1|03', -8.3, 0.0, 22.1, 'PUBLISHED', NULL, 'demo_mitarbeit', NULL),
    ('Studierendensekretariat', 'Registrar Office', 'Anlaufstelle für Immatrikulation und Bescheinigungen.', 'Enrolment and certificates.', 'SERVICE', 'S1|01', 2.0, 3.0, 9.4, 'PUBLISHED', NULL, 'demo_leitung', NULL),
    ('Rechnerpool Piloty', 'Piloty Computer Lab', 'Öffentlich zugänglicher Rechnerpool.', 'Publicly accessible computer lab.', 'LAB', 'S2|02', 15.2, 6.0, 41.0, 'PUBLISHED', NULL, 'demo_mitarbeit', 'demo_mitarbeit'),
    ('Hörsaal S3|21 001', 'Lecture Hall S3|21 001', 'Hörsaal mit 250 Plätzen.', 'Lecture hall with 250 seats.', 'LECTURE_HALL', 'S3|21', 0.0, 0.0, 12.0, 'IN_REVIEW', NULL, 'demo_mitarbeit', 'demo_mitarbeit'),
    ('Fachschaft Informatik', 'Computer Science Student Council', 'Beratung durch Studierende für Studierende.', 'Peer advice for students.', 'SERVICE', 'S2|02', 7.7, 3.0, 15.5, 'IN_REVIEW', NULL, 'demo_mitarbeit', NULL),
    ('Cafeteria Piloty', 'Piloty Cafeteria', 'Kaffee und Snacks zwischen den Vorlesungen.', 'Coffee and snacks between lectures.', 'CAFETERIA', 'S2|02', -3.1, 0.0, 8.8, 'DRAFT', NULL, 'demo_mitarbeit', 'demo_mitarbeit'),
    ('Lernzentrum Lichtwiese', 'Lichtwiese Learning Centre', 'Ruhige Arbeitsplätze, rund um die Uhr geöffnet.', 'Quiet workspaces, open around the clock.', 'LIBRARY', 'L4|01', 20.0, 0.0, 5.0, 'DRAFT', NULL, 'demo_mitarbeit', NULL),
    ('Fahrradwerkstatt', 'Bicycle Workshop', 'Selbsthilfewerkstatt des AStA.', 'Self-service workshop run by the student union.', 'OTHER', 'S1|03', -14.0, 0.0, 3.2, 'DRAFT', 'Bitte Öffnungszeiten und Kontakt ergänzen.', 'demo_mitarbeit', 'demo_mitarbeit'),
    ('Sprachenzentrum', 'Language Centre', 'Kursangebot in zwölf Sprachen.', 'Courses in twelve languages.', 'SERVICE', 'S1|01', 9.0, 6.0, 27.3, 'ARCHIVED', NULL, 'demo_leitung', NULL),
    ('Alter Serverraum', 'Former Server Room', 'Nicht mehr in Betrieb, Eintrag archiviert.', 'No longer in operation.', 'OTHER', 'S2|02', 30.0, -3.0, 50.0, 'ARCHIVED', NULL, 'demo_leitung', NULL)
) AS d(name_de, name_en, description_de, description_en, category, building_code, x, y, z, status, review_note, created_by, assigned_to)
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE poi.name_de = d.name_de);

INSERT INTO consultation (title_de, title_en, description_de, description_en, organisation, building_id,
                          room, contact_email, responsible_user_id, is_published, created_by)
SELECT d.title_de, d.title_en, d.description_de, d.description_en, d.organisation,
       (SELECT id FROM building WHERE code = d.building_code),
       d.room, d.contact_email,
       (SELECT id FROM admin_user WHERE username = 'demo_personal'),
       d.is_published,
       (SELECT id FROM admin_user WHERE username = 'demo_personal')
FROM (VALUES
    ('Studienberatung Informatik', 'Computer Science Study Advice', 'Fragen zu Studienverlauf und Prüfungsordnung.', 'Questions on curriculum and examination regulations.', 'Fachgebiet Informatik', 'S2|02', 'B302', 'studienberatung.inf@tu-darmstadt.de', TRUE),
    ('Sprechstunde Prüfungsamt', 'Examination Office Consultation', 'Anmeldung und Rücktritt von Prüfungen.', 'Registering and withdrawing from exams.', 'Prüfungsamt', 'S1|01', '14', 'pruefungsamt@tu-darmstadt.de', TRUE),
    ('Beratung für internationale Studierende', 'International Student Advice', 'Visum, Wohnen und Anerkennung von Leistungen.', 'Visa, housing and credit transfer.', 'International Office', 'S1|03', '212', 'international@tu-darmstadt.de', TRUE),
    ('Psychosoziale Beratung', 'Psychosocial Counselling', 'Vertrauliche Beratung in Belastungssituationen.', 'Confidential counselling.', 'Studierendenwerk', 'S1|01', '8', 'beratung@stwda.de', FALSE)
) AS d(title_de, title_en, description_de, description_en, organisation, building_code, room, contact_email, is_published)
WHERE NOT EXISTS (SELECT 1 FROM consultation WHERE consultation.title_de = d.title_de);

INSERT INTO consultation_event (consultation_id, day_of_week, start_time, end_time, room_override, note)
SELECT c.id, d.day_of_week, d.start_time::time, d.end_time::time, d.room_override, d.note
FROM (VALUES
    ('Studienberatung Informatik', 2, '10:00', '12:00', NULL, 'Ohne Anmeldung'),
    ('Studienberatung Informatik', 4, '14:00', '16:00', NULL, NULL),
    ('Sprechstunde Prüfungsamt', 1, '09:00', '11:30', NULL, NULL),
    ('Sprechstunde Prüfungsamt', 3, '09:00', '11:30', NULL, NULL),
    ('Beratung für internationale Studierende', 3, '13:00', '16:00', 'S1|03 214', 'Beratung auch auf Englisch'),
    ('Psychosoziale Beratung', 5, '10:00', '13:00', NULL, 'Termin nach Vereinbarung')
) AS d(title_de, day_of_week, start_time, end_time, room_override, note)
JOIN consultation c ON c.title_de = d.title_de
WHERE NOT EXISTS (
    SELECT 1 FROM consultation_event e
    WHERE e.consultation_id = c.id AND e.day_of_week = d.day_of_week
      AND e.start_time = d.start_time::time);
