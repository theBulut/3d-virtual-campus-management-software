-- Seeds the six roles, the permission catalogue and both relations between them.
-- Specification: docs/spec/01_ARCHITEKTUR_SPEC.md sections 1.1 to 1.4.
--
-- This file and de.tudarmstadt.campus.admin.rbac.RoleCatalog must stay identical; the comparison runs
-- in RoleCatalogConsistencyIT. Roles are hard-coded (decision E-1) and marked is_system, so they can be
-- neither renamed nor deleted (INV-5).

INSERT INTO role (name, display_name, description, is_system, is_assignable, sort_order) VALUES
    ('ADMIN', 'Systemadministration',
     'Vollzugriff. Legt Konten an, vergibt und entzieht alle Rollen, sieht das Audit-Log und die Systemkonfiguration.',
     TRUE, TRUE, 1),
    ('PROJEKTLEITER', 'Projektleitung',
     'Richtet Konten für Mitarbeitende und Verwaltungspersonal ein, vergibt eingeschränkte Rollen, gibt Inhalte frei, pflegt und löscht Inhalte und exportiert Daten.',
     TRUE, TRUE, 2),
    ('PROJEKTMITARBEITER', 'Projektmitarbeit',
     'Speist Inhalte ein: erstellt und bearbeitet eigene beziehungsweise zugewiesene POIs, lädt Medien hoch und reicht Inhalte zur Prüfung ein. Kann nicht selbst veröffentlichen.',
     TRUE, TRUE, 3),
    ('PERSONAL', 'Verwaltungspersonal',
     'Pflegt die Beratungszeiten der eigenen Einrichtung und das eigene Profil. Keine Berührung mit der Rechteverwaltung.',
     TRUE, TRUE, 4),
    ('MAINTENANCE_DEV', 'Betrieb / Entwicklung',
     'Liest Health-Status, Systeminformationen und Audit-Log. Kein Zugriff auf Nutzerverwaltung und Inhalte.',
     TRUE, TRUE, 5),
    -- Never assigned to a user; realised by permitAll on /api/public/** (INV-4).
    ('EXTERNE_PERSON', 'Öffentlicher Zugriff',
     'Nur lesender Zugriff auf veröffentlichte Inhalte über die öffentliche Schnittstelle. Kein Benutzerkonto.',
     TRUE, FALSE, 6);

INSERT INTO permission (code, resource, action, description) VALUES
    ('USER_READ', 'USER', 'READ', 'Nutzerliste und -details lesen'),
    ('USER_CREATE', 'USER', 'CREATE', 'Neues Nutzerkonto anlegen'),
    ('USER_UPDATE', 'USER', 'UPDATE', 'Stammdaten eines fremden Kontos ändern'),
    ('USER_DELETE', 'USER', 'DELETE', 'Konto löschen'),
    ('USER_ACTIVATE', 'USER', 'ACTIVATE', 'Konto sperren oder entsperren'),
    ('USER_PASSWORD_RESET', 'USER', 'PASSWORD_RESET', 'Passwort eines fremden Kontos zurücksetzen'),
    ('ROLE_READ', 'ROLE', 'READ', 'Rollen und deren Berechtigungen lesen'),
    ('ROLE_ASSIGN', 'ROLE', 'ASSIGN', 'Rollen zuweisen und entziehen'),
    ('ROLE_MANAGE', 'ROLE', 'MANAGE', 'Rollen anlegen, ändern und löschen'),
    ('PROFILE_UPDATE_OWN', 'PROFILE', 'UPDATE_OWN', 'Eigenes Profil und Passwort ändern'),

    ('POI_READ_PUBLISHED', 'POI', 'READ_PUBLISHED', 'Nur veröffentlichte POIs lesen'),
    ('POI_READ_ALL', 'POI', 'READ_ALL', 'Alle POIs inklusive Entwürfe lesen'),
    ('POI_CREATE', 'POI', 'CREATE', 'POI anlegen; entsteht immer im Status Entwurf'),
    ('POI_UPDATE_OWN', 'POI', 'UPDATE_OWN', 'Nur selbst erstellte oder zugewiesene POIs ändern'),
    ('POI_UPDATE_ANY', 'POI', 'UPDATE_ANY', 'Beliebige POIs ändern'),
    ('POI_DELETE', 'POI', 'DELETE', 'POI löschen'),
    ('POI_SUBMIT_REVIEW', 'POI', 'SUBMIT_REVIEW', 'Eigenen POI zur Prüfung einreichen'),
    ('POI_PUBLISH', 'POI', 'PUBLISH', 'Freigeben, zurückweisen und archivieren'),
    ('POI_ASSIGN', 'POI', 'ASSIGN', 'Bearbeiter eines POI setzen'),

    ('BUILDING_READ_PUBLIC', 'BUILDING', 'READ_PUBLIC', 'Öffentliche Gebäudedaten lesen'),
    ('BUILDING_READ_ALL', 'BUILDING', 'READ_ALL', 'Alle Gebäudedaten lesen'),
    ('BUILDING_CREATE', 'BUILDING', 'CREATE', 'Gebäude anlegen'),
    ('BUILDING_UPDATE', 'BUILDING', 'UPDATE', 'Gebäude ändern'),
    ('BUILDING_DELETE', 'BUILDING', 'DELETE', 'Gebäude löschen'),

    ('CONSULTATION_READ_PUBLIC', 'CONSULTATION', 'READ_PUBLIC', 'Öffentliche Beratungszeiten lesen'),
    ('CONSULTATION_READ_ALL', 'CONSULTATION', 'READ_ALL', 'Alle Beratungszeiten lesen'),
    ('CONSULTATION_CREATE', 'CONSULTATION', 'CREATE', 'Beratungsangebot anlegen'),
    ('CONSULTATION_UPDATE_OWN', 'CONSULTATION', 'UPDATE_OWN', 'Nur eigene Beratungsangebote ändern'),
    ('CONSULTATION_UPDATE_ANY', 'CONSULTATION', 'UPDATE_ANY', 'Beliebige Beratungsangebote ändern'),
    ('CONSULTATION_DELETE', 'CONSULTATION', 'DELETE', 'Beratungsangebot löschen'),

    ('MEDIA_UPLOAD', 'MEDIA', 'UPLOAD', 'Medien hochladen'),
    ('MEDIA_DELETE', 'MEDIA', 'DELETE', 'Medien löschen'),
    ('AUDIT_READ', 'AUDIT', 'READ', 'Vollständiges Audit-Log lesen'),
    ('AUDIT_READ_CONTENT', 'AUDIT', 'READ_CONTENT', 'Audit-Log gefiltert auf Inhaltsressourcen lesen'),
    ('SYSTEM_HEALTH_READ', 'SYSTEM', 'HEALTH_READ', 'Health- und Systeminformationen lesen'),
    ('SYSTEM_CONFIG', 'SYSTEM', 'CONFIG', 'Systemkonfiguration ändern'),
    ('DATA_EXPORT', 'DATA', 'EXPORT', 'Datenexport als CSV oder JSON');

-- ADMIN holds every permission: the matrix in section 1.3 marks the whole column.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.name = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_ACTIVATE',
    'ROLE_READ', 'ROLE_ASSIGN', 'PROFILE_UPDATE_OWN',
    'POI_READ_PUBLISHED', 'POI_READ_ALL', 'POI_CREATE', 'POI_UPDATE_OWN', 'POI_UPDATE_ANY',
    'POI_DELETE', 'POI_SUBMIT_REVIEW', 'POI_PUBLISH', 'POI_ASSIGN',
    'BUILDING_READ_PUBLIC', 'BUILDING_READ_ALL', 'BUILDING_CREATE', 'BUILDING_UPDATE', 'BUILDING_DELETE',
    'CONSULTATION_READ_PUBLIC', 'CONSULTATION_READ_ALL', 'CONSULTATION_CREATE',
    'CONSULTATION_UPDATE_OWN', 'CONSULTATION_UPDATE_ANY', 'CONSULTATION_DELETE',
    'MEDIA_UPLOAD', 'MEDIA_DELETE', 'AUDIT_READ_CONTENT', 'DATA_EXPORT'
) WHERE r.name = 'PROJEKTLEITER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'PROFILE_UPDATE_OWN',
    'POI_READ_PUBLISHED', 'POI_READ_ALL', 'POI_CREATE', 'POI_UPDATE_OWN', 'POI_SUBMIT_REVIEW',
    'BUILDING_READ_PUBLIC', 'BUILDING_READ_ALL',
    'CONSULTATION_READ_PUBLIC', 'CONSULTATION_READ_ALL',
    'MEDIA_UPLOAD'
) WHERE r.name = 'PROJEKTMITARBEITER';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'PROFILE_UPDATE_OWN',
    'POI_READ_PUBLISHED',
    'BUILDING_READ_PUBLIC', 'BUILDING_READ_ALL',
    'CONSULTATION_READ_PUBLIC', 'CONSULTATION_READ_ALL', 'CONSULTATION_CREATE',
    'CONSULTATION_UPDATE_OWN'
) WHERE r.name = 'PERSONAL';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'ROLE_READ', 'PROFILE_UPDATE_OWN',
    'AUDIT_READ', 'AUDIT_READ_CONTENT', 'SYSTEM_HEALTH_READ'
) WHERE r.name = 'MAINTENANCE_DEV';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'POI_READ_PUBLISHED', 'BUILDING_READ_PUBLIC', 'CONSULTATION_READ_PUBLIC'
) WHERE r.name = 'EXTERNE_PERSON';

-- Grant rules of section 1.4. EXTERNE_PERSON appears on neither side.
INSERT INTO role_grant (granter_role_id, grantable_role_id)
SELECT g.id, t.id FROM role g JOIN role t ON t.name IN (
    'ADMIN', 'PROJEKTLEITER', 'PROJEKTMITARBEITER', 'PERSONAL', 'MAINTENANCE_DEV'
) WHERE g.name = 'ADMIN';

INSERT INTO role_grant (granter_role_id, grantable_role_id)
SELECT g.id, t.id FROM role g JOIN role t ON t.name IN (
    'PROJEKTMITARBEITER', 'PERSONAL'
) WHERE g.name = 'PROJEKTLEITER';
