# Architektur- und Implementierungsspezifikation
## Administrationsinfrastruktur für den 3D Campus Explorer der TU Darmstadt

**Repository:** `https://github.com/theBulut/3d-virtual-campus-management-software`
**Stack (bereits vorhanden):** Spring Boot 3 / Java 21 / Maven, PostgreSQL, Redis, React (Vite) + SCSS, Docker Compose
**Status des Repos:** Skelett mit ungeschützter User-CRUD-API und Default-Admin. Auth/RBAC/Content fehlen vollständig.

> **Hinweis an das ausführende Modell (Claude Code):** Dieses Dokument ist die verbindliche Spezifikation.
> Prosa ist deutsch, **alle Bezeichner, Klassen, Tabellen, Spalten, Endpunkte und Enum-Werte sind englisch bzw.
> wie hier wörtlich angegeben**. Rollennamen sind bewusst deutsch (`PROJEKTLEITER`, …), weil sie so in der
> Bachelorarbeit dokumentiert sind — sie dürfen nicht umbenannt werden.
> Wenn etwas in dieser Spec nicht definiert ist, gilt: **einfachste Lösung wählen, die die Anforderung erfüllt**,
> und die Entscheidung in `docs/DECISIONS.md` mit Begründung festhalten.

---

## 0. Ausgangslage und Leitentscheidungen

Die folgenden Entscheidungen leiten sich direkt aus der Rückmeldung des Prüfers (Dr.-Ing. Stefan Göbel, E-Mail
vom 01.07.2026) ab und sind für den Prototyp bindend.

| # | Entscheidung | Herkunft / Begründung |
|---|---|---|
| E-1 | **Rollen sind hard-coded.** Die sechs Rollen werden beim Systemstart aus einem Java-Katalog in die Datenbank geseedet. Es gibt **keine UI zum Erstellen eigener Rollen**. | Stefan: *"'hard coded Rollenkonzept' auch erstmal genug … individuelle Rollenerstellung in den Ausblick der Arbeit"* |
| E-2 | **Berechtigungen sind technisch fixiert** und als eigenständige Entität modelliert (`permission`, `role_permission`). Rollen sind Bündel von Berechtigungen. Die Zuordnung ist in der DB abgelegt, aber im Prototyp nur lesbar. | Stefan: *"klingt gut"* zum permission-basierten Modell; Thesis Kap. 2.4 fordert Nutzer/Rollen/Berechtigungen als getrennte Elemente. Dadurch ist der Ausblick (individuelle Rollen) eine reine UI-/Endpunkt-Ergänzung, kein Redesign. |
| E-3 | **Admins weisen Rollen zu und entziehen sie.** Das ist die zentrale, im Prototyp voll umgesetzte RBAC-Verwaltungsfunktion. Auch `PROJEKTLEITER` darf Rollen vergeben — aber nur aus einer eingeschränkten, datenbankgestützten Teilmenge. | Stefan: *"Benutzerverwaltung: Projektleitung/Eileen richtet Accounts ein … ggf. aus Systemadmin-Sicht (wir)"* |
| E-4 | **Freigabe-Workflow für Content** (Entwurf → Prüfung → veröffentlicht). Erstellen und Freigeben sind getrennte Berechtigungen. | Stefan: *"ggf. Qualitätskontrolle/Check/Freischalten von Content durch Projektleitung"* |
| E-5 | **Keine Unity-Anbindung.** Es gibt lediglich eine öffentliche, lesende Endpunktgruppe `/api/public/**`, die ein Unity-Client später konsumieren *könnte*. Kein Unity-Code, keine Unity-Tests. | Stefan auf die Frage nach Unity-Anbindung: *"ja"* (begrenzte Datenverwaltung reicht) |
| E-6 | **Begrenzter Content-Umfang:** POIs, Gebäude, Beratungszeiten, Medien-Metadaten. Kein vollwertiges CMS. | Thesis Kap. 2.4: *"ohne ein vollständiges Content-Management-System vorauszusetzen"* |
| E-7 | **Durchsetzung ausschließlich serverseitig.** Frontend-Guards dienen nur der Usability. Jeder geschützte Endpunkt trägt eine `@PreAuthorize`-Annotation; ein Test weist nach, dass kein Endpunkt ungeschützt ist. | Thesis Kap. 2.4 und 3.3.3 |

---

## 1. Rollenmodell

### 1.1 Rollenkatalog

Sechs Rollen, exakt wie in Kapitel 3.3.1 der Arbeit benannt. Die Spalte *Projektkontext* ordnet jede Rolle den
von Stefan genannten realen Personengruppen zu — diese Zuordnung ist der inhaltliche Kern der Antwort auf
seine Frage nach den Rollen.

| Rolle (technisch) | Anzeigename | Projektkontext (real) | Kernaufgabe im System |
|---|---|---|---|
| `ADMIN` | Systemadministration | AG Serious Games / technischer Systembetrieb ("wir", Stefan) | Vollzugriff. Legt Accounts an, vergibt und entzieht **alle** Rollen, sieht Audit-Log, Systemkonfiguration. |
| `PROJEKTLEITER` | Projektleitung | Projektleitung / Eileen | Richtet Accounts für Mitarbeitende und Verwaltungspersonal ein, vergibt eingeschränkte Rollen, **gibt Inhalte frei** (Qualitätskontrolle), pflegt und löscht Inhalte, exportiert Daten. |
| `PROJEKTMITARBEITER` | Projektmitarbeit | Story++-Team, studentische Hilfskräfte, beitragende Studierende | Speist Content ein: erstellt und bearbeitet **eigene bzw. zugewiesene** POIs, lädt Medien hoch, reicht Inhalte zur Prüfung ein. **Kann nicht selbst veröffentlichen.** |
| `PERSONAL` | Verwaltungspersonal | Fachgebiete und Verwaltungspersonal der TU | Pflegt Beratungszeiten der eigenen Einrichtung und das eigene Profil. Keine Berührung mit dem RBAC-System. |
| `MAINTENANCE_DEV` | Betrieb / Entwicklung | Technisches Betriebs- und Entwicklungspersonal | Liest Health-Status, Systeminfos und Audit-Log. **Kein** Zugriff auf Nutzerverwaltung und Inhalte (Least Privilege — bewusst als Negativbeispiel in der Matrix). |
| `EXTERNE_PERSON` | Öffentlicher Zugriff | Besucher, Studierende ohne Account, künftiger Unity-Client | Nur lesender Zugriff auf **veröffentlichte** Inhalte über `/api/public/**`. Kein Benutzerkonto. |

**Wichtig zur Umsetzung von `EXTERNE_PERSON`:** Diese Rolle wird **keinem Benutzer zugewiesen**. Sie existiert
als Zeile in der Tabelle `role` (Dokumentationszweck, Vollständigkeit der Matrix), technisch wird sie durch
`permitAll()` auf der Endpunktgruppe `/api/public/**` in der `SecurityFilterChain` realisiert. Das ist in
`docs/DECISIONS.md` und in Kapitel 4 der Arbeit explizit so zu beschreiben.

### 1.2 Berechtigungskatalog

Berechtigungen folgen dem Schema `RESOURCE_ACTION` und werden als Spring-Security-**Authorities** (nicht als
`ROLE_`-Präfix-Rollen) durchgesetzt. Java-Repräsentation: `enum PermissionCode`.

**Nutzer- und Rechteverwaltung**

| Code | Bedeutung |
|---|---|
| `USER_READ` | Nutzerliste und -details lesen |
| `USER_CREATE` | Neues Nutzerkonto anlegen |
| `USER_UPDATE` | Stammdaten eines fremden Kontos ändern |
| `USER_DELETE` | Konto löschen |
| `USER_ACTIVATE` | Konto sperren/entsperren (`is_active`) |
| `USER_PASSWORD_RESET` | Passwort eines fremden Kontos zurücksetzen |
| `ROLE_READ` | Rollen und deren Berechtigungen lesen |
| `ROLE_ASSIGN` | Rollen zuweisen/entziehen (eingeschränkt durch `role_grant`, s. 1.4) |
| `ROLE_MANAGE` | Rollen anlegen/ändern/löschen — **im Prototyp nur an `ADMIN` vergeben und von keinem Endpunkt genutzt (Ausblick)** |
| `PROFILE_UPDATE_OWN` | Eigenes Profil und Passwort ändern |

**Points of Interest**

| Code | Bedeutung |
|---|---|
| `POI_READ_PUBLISHED` | Nur veröffentlichte POIs lesen |
| `POI_READ_ALL` | Alle POIs inkl. Entwürfe lesen |
| `POI_CREATE` | POI anlegen (entsteht immer im Status `DRAFT`) |
| `POI_UPDATE_OWN` | Nur selbst erstellte oder zugewiesene POIs ändern |
| `POI_UPDATE_ANY` | Beliebige POIs ändern |
| `POI_DELETE` | POI löschen |
| `POI_SUBMIT_REVIEW` | Eigenen POI zur Prüfung einreichen (`DRAFT` → `IN_REVIEW`) |
| `POI_PUBLISH` | Freigeben, zurückweisen, archivieren |
| `POI_ASSIGN` | Bearbeiter (`assigned_to`) eines POI setzen |

**Gebäude**

| Code | Bedeutung |
|---|---|
| `BUILDING_READ_PUBLIC` | Öffentliche Gebäudedaten lesen |
| `BUILDING_READ_ALL` | Alle Gebäudedaten lesen |
| `BUILDING_CREATE` / `BUILDING_UPDATE` / `BUILDING_DELETE` | Schreibende Operationen |

**Beratungszeiten**

| Code | Bedeutung |
|---|---|
| `CONSULTATION_READ_PUBLIC` | Öffentliche Beratungszeiten lesen |
| `CONSULTATION_READ_ALL` | Alle Beratungszeiten lesen |
| `CONSULTATION_CREATE` | Beratungsangebot anlegen |
| `CONSULTATION_UPDATE_OWN` | Nur eigene Beratungsangebote ändern (Ownership über `responsible_user_id`) |
| `CONSULTATION_UPDATE_ANY` | Beliebige Beratungsangebote ändern |
| `CONSULTATION_DELETE` | Löschen |

**Medien und System**

| Code | Bedeutung |
|---|---|
| `MEDIA_UPLOAD` / `MEDIA_DELETE` | Medien-Assets verwalten |
| `AUDIT_READ` | Vollständiges Audit-Log lesen |
| `AUDIT_READ_CONTENT` | Audit-Log **gefiltert auf Content-Ressourcen** (POI, BUILDING, CONSULTATION, MEDIA) lesen |
| `SYSTEM_HEALTH_READ` | Health-/Systeminformationen lesen |
| `SYSTEM_CONFIG` | Systemkonfiguration ändern |
| `DATA_EXPORT` | Datenexport (CSV/JSON) |

### 1.3 Berechtigungsmatrix

`X` = Berechtigung vorhanden. Diese Tabelle ist die **normative Quelle** für den Seed in
`V4__seed_rbac.sql` bzw. `RoleCatalog.java` — beides muss identisch sein und wird durch einen Test verglichen.

| Berechtigung | ADMIN | PROJEKT&#8203;LEITER | PROJEKT&#8203;MITARBEITER | PERSONAL | MAINTE&#8203;NANCE_DEV | EXTERNE&#8203;_PERSON |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| USER_READ | X | X | | | | |
| USER_CREATE | X | X | | | | |
| USER_UPDATE | X | X¹ | | | | |
| USER_DELETE | X | | | | | |
| USER_ACTIVATE | X | X¹ | | | | |
| USER_PASSWORD_RESET | X | | | | | |
| ROLE_READ | X | X | | | X | |
| ROLE_ASSIGN | X | X² | | | | |
| ROLE_MANAGE | X | | | | | |
| PROFILE_UPDATE_OWN | X | X | X | X | X | |
| POI_READ_PUBLISHED | X | X | X | X | | X |
| POI_READ_ALL | X | X | X | | | |
| POI_CREATE | X | X | X | | | |
| POI_UPDATE_OWN | X | X | X | | | |
| POI_UPDATE_ANY | X | X | | | | |
| POI_DELETE | X | X | | | | |
| POI_SUBMIT_REVIEW | X | X | X | | | |
| POI_PUBLISH | X | X | | | | |
| POI_ASSIGN | X | X | | | | |
| BUILDING_READ_PUBLIC | X | X | X | X | | X |
| BUILDING_READ_ALL | X | X | X | X | | |
| BUILDING_CREATE | X | X | | | | |
| BUILDING_UPDATE | X | X | | | | |
| BUILDING_DELETE | X | X | | | | |
| CONSULTATION_READ_PUBLIC | X | X | X | X | | X |
| CONSULTATION_READ_ALL | X | X | X | X | | |
| CONSULTATION_CREATE | X | X | | X | | |
| CONSULTATION_UPDATE_OWN | X | X | | X | | |
| CONSULTATION_UPDATE_ANY | X | X | | | | |
| CONSULTATION_DELETE | X | X | | | | |
| MEDIA_UPLOAD | X | X | X | | | |
| MEDIA_DELETE | X | X | | | | |
| AUDIT_READ | X | | | | X | |
| AUDIT_READ_CONTENT | X | X | | | X | |
| SYSTEM_HEALTH_READ | X | | | | X | |
| SYSTEM_CONFIG | X | | | | | |
| DATA_EXPORT | X | X | | | | |

¹ `PROJEKTLEITER` darf fremde Konten nur bearbeiten/sperren, wenn der Zielnutzer **ausschließlich** Rollen aus
seiner Vergabemenge besitzt (siehe 1.4). Ein `PROJEKTLEITER` kann also weder einen `ADMIN` noch einen
`MAINTENANCE_DEV` verändern. Prüfung in `UserService.assertCanManage(actor, target)`.

² Eingeschränkt auf die Rollen `PROJEKTMITARBEITER` und `PERSONAL`.

### 1.4 Rollenvergabe: Wer darf welche Rolle vergeben?

Dies ist der Mechanismus hinter der Anforderung *"Admin kann Rollen zuweisen/entziehen"* — datengetrieben statt
hart im Code, damit die Regel dokumentierbar und testbar ist.

Tabelle `role_grant (granter_role_id, grantable_role_id)`:

| Vergebende Rolle | darf vergeben/entziehen |
|---|---|
| `ADMIN` | `ADMIN`, `PROJEKTLEITER`, `PROJEKTMITARBEITER`, `PERSONAL`, `MAINTENANCE_DEV` |
| `PROJEKTLEITER` | `PROJEKTMITARBEITER`, `PERSONAL` |
| alle übrigen | — |

`EXTERNE_PERSON` ist grundsätzlich nicht vergebbar (nicht in der Tabelle enthalten). Die effektive Vergabemenge
eines Nutzers ist die Vereinigung der Vergabemengen aller seiner Rollen.

**Ablauf einer Rollenzuweisung** (`POST /api/users/{id}/roles`):

1. `JwtAuthFilter` authentifiziert; `@PreAuthorize("hasAuthority('ROLE_ASSIGN')")` prüft die Grundberechtigung.
2. `RoleAssignmentService.assign(actorId, targetUserId, roleName)`:
   1. Zielnutzer laden → `404`, wenn nicht vorhanden.
   2. Rolle laden → `404`, wenn nicht vorhanden.
   3. `grantableRoles(actor).contains(roleName)` → sonst `403` mit Fehlercode `ROLE_NOT_GRANTABLE`.
   4. `assertCanManage(actor, target)` (siehe Fußnote ¹) → sonst `403` `TARGET_OUT_OF_SCOPE`.
   5. Rolle bereits vorhanden → `409` `ROLE_ALREADY_ASSIGNED` (idempotenzfreundlich dokumentieren).
   6. `user_role`-Zeile schreiben mit `assigned_at = now()`, `assigned_by = actorId`.
   7. `user.token_version++` → **alle bestehenden Tokens des Zielnutzers werden ungültig** (siehe 4.2).
   8. Audit-Eintrag `ROLE_ASSIGNED` mit `before`/`after` der Rollenliste.
3. Antwort `200` mit dem aktualisierten `UserDto` inkl. neuer Rollenliste.

**Ablauf eines Entzugs** (`DELETE /api/users/{id}/roles/{roleName}`): analog, zusätzlich die Invarianten aus 1.5.

### 1.5 Invarianten (müssen als Tests existieren)

| ID | Invariante | Fehlerverhalten |
|---|---|---|
| INV-1 | Es muss jederzeit mindestens **ein aktives Konto mit Rolle `ADMIN`** existieren. | `409 LAST_ADMIN_PROTECTED` beim Entzug, Löschen oder Sperren |
| INV-2 | Ein Nutzer kann sich **die eigene `ADMIN`-Rolle nicht selbst entziehen** und sich nicht selbst löschen oder sperren. | `409 SELF_MODIFICATION_FORBIDDEN` |
| INV-3 | Jeder Nutzer besitzt **mindestens eine Rolle**. Der Entzug der letzten Rolle ist unzulässig. | `409 LAST_ROLE_PROTECTED` |
| INV-4 | `EXTERNE_PERSON` kann keinem Nutzer zugewiesen werden. | `400 ROLE_NOT_ASSIGNABLE` |
| INV-5 | System-Rollen (`is_system = true`, gilt für alle sechs) können nicht gelöscht oder umbenannt werden. | `403 SYSTEM_ROLE_IMMUTABLE` |
| INV-6 | Nach Rollenänderung, Sperrung oder Passwortänderung sind vorherige Access-Tokens ungültig. | `401 TOKEN_STALE` |

---

## 2. Datenmodell

PostgreSQL, Migrationen mit **Flyway** (`backend/src/main/resources/db/migration`).
`spring.jpa.hibernate.ddl-auto=validate` — kein Auto-DDL, damit das Schema reproduzierbar und in der Arbeit
zitierfähig ist.

Migrationsdateien:

```
V1__rbac_schema.sql        -- admin_user, role, permission, user_role, role_permission, role_grant
V2__content_schema.sql     -- building, poi, consultation, consultation_event, media_asset
V3__audit_schema.sql       -- audit_log
V4__seed_rbac.sql          -- Rollen, Berechtigungen, Zuordnungen, Vergaberegeln
V5__seed_demo_data.sql     -- Demo-Nutzer und Beispielinhalte (nur Profil "demo")
```

### 2.1 RBAC-Tabellen

```sql
CREATE TABLE admin_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    organisation    VARCHAR(150),               -- Fachgebiet/Einrichtung, relevant für PERSONAL
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    token_version   INTEGER      NOT NULL DEFAULT 0,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES admin_user(id)
);

CREATE TABLE role (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,   -- ADMIN, PROJEKTLEITER, ...
    display_name VARCHAR(100) NOT NULL,
    description TEXT         NOT NULL,
    is_system   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_assignable BOOLEAN    NOT NULL DEFAULT TRUE,  -- FALSE für EXTERNE_PERSON
    sort_order  INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(60) NOT NULL UNIQUE,    -- POI_PUBLISH, ...
    resource    VARCHAR(30) NOT NULL,           -- USER, ROLE, POI, BUILDING, CONSULTATION, MEDIA, AUDIT, SYSTEM
    action      VARCHAR(30) NOT NULL,           -- READ, CREATE, UPDATE, DELETE, PUBLISH, ASSIGN, ...
    description TEXT        NOT NULL
);

CREATE TABLE user_role (
    user_id     BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES role(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by BIGINT REFERENCES admin_user(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE role_grant (                        -- welche Rolle darf welche Rolle vergeben
    granter_role_id   BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    grantable_role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (granter_role_id, grantable_role_id)
);
```

### 2.2 Content-Tabellen

```sql
CREATE TABLE building (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,   -- z.B. S1|03
    name_de      VARCHAR(200) NOT NULL,
    name_en      VARCHAR(200),
    street       VARCHAR(200),
    postal_code  VARCHAR(10),
    city         VARCHAR(100),
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    model_ref    VARCHAR(255),                   -- Referenz auf 3D-Modell in der Unity-Szene
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT REFERENCES admin_user(id)
);

CREATE TYPE content_status AS ENUM ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED');

CREATE TABLE poi (
    id              BIGSERIAL PRIMARY KEY,
    name_de         VARCHAR(200) NOT NULL,
    name_en         VARCHAR(200),
    description_de  TEXT,
    description_en  TEXT,
    category        VARCHAR(50)  NOT NULL,       -- LECTURE_HALL, LIBRARY, CAFETERIA, SERVICE, LAB, OTHER
    building_id     BIGINT REFERENCES building(id),
    position_x      DOUBLE PRECISION NOT NULL,
    position_y      DOUBLE PRECISION NOT NULL,
    position_z      DOUBLE PRECISION NOT NULL,
    status          content_status NOT NULL DEFAULT 'DRAFT',
    is_published    BOOLEAN GENERATED ALWAYS AS (status = 'PUBLISHED') STORED,
    assigned_to     BIGINT REFERENCES admin_user(id),   -- zuständiger Bearbeiter
    review_note     TEXT,                                -- Begründung bei Zurückweisung
    published_at    TIMESTAMPTZ,
    published_by    BIGINT REFERENCES admin_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES admin_user(id)
);
CREATE INDEX idx_poi_status ON poi(status);
CREATE INDEX idx_poi_building ON poi(building_id);

CREATE TABLE consultation (
    id                   BIGSERIAL PRIMARY KEY,
    title_de             VARCHAR(200) NOT NULL,
    title_en             VARCHAR(200),
    description_de       TEXT,
    organisation         VARCHAR(150) NOT NULL,  -- Fachgebiet / Einrichtung
    building_id          BIGINT REFERENCES building(id),
    room                 VARCHAR(50),
    contact_email        VARCHAR(255),
    responsible_user_id  BIGINT REFERENCES admin_user(id),   -- Ownership für PERSONAL
    is_published         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           BIGINT REFERENCES admin_user(id)
);

CREATE TABLE consultation_event (
    id              BIGSERIAL PRIMARY KEY,
    consultation_id BIGINT NOT NULL REFERENCES consultation(id) ON DELETE CASCADE,
    day_of_week     SMALLINT,          -- 1..7, NULL bei Einzeltermin
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    valid_from      DATE,
    valid_to        DATE,
    room_override   VARCHAR(50),
    note            TEXT,
    CHECK (end_time > start_time)
);

CREATE TABLE media_asset (
    id            BIGSERIAL PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,      -- lokales Volume, kein S3
    poi_id        BIGINT REFERENCES poi(id) ON DELETE SET NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by   BIGINT REFERENCES admin_user(id)
);
```

### 2.3 Audit-Tabelle

```sql
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_id      BIGINT REFERENCES admin_user(id),
    actor_username VARCHAR(64),                -- denormalisiert, überlebt Löschung des Kontos
    action        VARCHAR(60)  NOT NULL,       -- siehe Aktionskatalog 4.4
    resource_type VARCHAR(40)  NOT NULL,       -- USER, ROLE, POI, BUILDING, CONSULTATION, MEDIA, AUTH
    resource_id   VARCHAR(64),
    before_state  JSONB,
    after_state   JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(255),
    success       BOOLEAN NOT NULL DEFAULT TRUE,
    error_code    VARCHAR(60),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_id);
```

Damit sind es **zwölf Tabellen** — Kapitel 3.2.3 der Arbeit spricht von zehn und ist entsprechend auf
`role_grant` und `media_asset` zu erweitern.

---

## 3. Backend-Paketstruktur

Basispaket: `de.tudarmstadt.campus.admin`

```
de.tudarmstadt.campus.admin
├── CampusAdminApplication.java
├── config
│   ├── SecurityConfig.java            # SecurityFilterChain, PasswordEncoder, CORS, MethodSecurity
│   ├── OpenApiConfig.java             # springdoc, BearerAuth-SecurityScheme
│   ├── RedisConfig.java               # RedisTemplate<String,String>
│   ├── JacksonConfig.java             # JavaTimeModule, ISO-8601
│   └── AppProperties.java             # @ConfigurationProperties("campus")
├── security
│   ├── JwtService.java                # create/parse/validate, Claims
│   ├── JwtAuthFilter.java             # OncePerRequestFilter
│   ├── TokenBlacklistService.java     # Redis
│   ├── TokenVersionService.java       # Redis-Cache über admin_user.token_version
│   ├── CampusUserDetails.java         # implements UserDetails, enthält userId + Authorities
│   ├── CampusUserDetailsService.java
│   ├── RestAuthenticationEntryPoint.java   # 401 als ApiError-JSON
│   ├── RestAccessDeniedHandler.java        # 403 als ApiError-JSON
│   └── ownership
│       ├── PoiSecurity.java           # @Component("poiSecurity")
│       └── ConsultationSecurity.java  # @Component("consultationSecurity")
├── rbac
│   ├── RoleCatalog.java               # Enum RoleCode + statische Matrix (Single Source of Truth)
│   ├── PermissionCode.java            # Enum
│   ├── domain/{Role,Permission,UserRole}.java
│   ├── repository/{RoleRepository,PermissionRepository,RoleGrantRepository}.java
│   ├── service/{RoleService,RoleAssignmentService}.java
│   └── web/{RoleController, dto/*}
├── user
│   ├── domain/AdminUser.java
│   ├── repository/AdminUserRepository.java
│   ├── service/{UserService, PasswordService}.java
│   └── web/{UserController, AuthController, dto/*}
├── content
│   ├── poi/{domain,repository,service,web}
│   ├── building/{domain,repository,service,web}
│   ├── consultation/{domain,repository,service,web}
│   └── media/{domain,repository,service,web}
├── publicapi
│   └── PublicContentController.java   # /api/public/**, permitAll
├── audit
│   ├── domain/AuditLog.java
│   ├── repository/AuditLogRepository.java
│   ├── service/AuditService.java
│   ├── Audited.java                   # Annotation
│   ├── AuditAspect.java               # AOP um @Audited
│   └── web/AuditController.java
├── system
│   └── web/SystemController.java      # /api/health (public), /api/system/info
└── common
    ├── exception/{ApiException, NotFoundException, ConflictException, ForbiddenException}
    ├── exception/GlobalExceptionHandler.java
    ├── dto/{ApiError, PageResponse}
    └── mapper/  (ModelMapper-Konfiguration)
```

**Schichtregel (Kap. 3.2.1 der Arbeit, muss testbar eingehalten werden):** Controller → Service → Repository.
Controller dürfen **keine** Repositories injizieren; Entities verlassen niemals die Service-Schicht (immer DTOs).
Diese Regel mit einem ArchUnit-Test absichern (`ArchitectureTest.java`).

---

## 4. Sicherheitsarchitektur

### 4.1 Authentifizierung

- Passwort-Hashing: `BCryptPasswordEncoder` (Stärke 12).
- Login `POST /api/auth/login` → Access-Token (**15 Minuten**) + Refresh-Token (**7 Tage**).
- Signatur: HS256, Secret aus `campus.jwt.secret` (Umgebungsvariable `JWT_SECRET`, min. 32 Zeichen).
  Beim Start Fail-Fast, wenn das Secret dem Default entspricht und Profil ≠ `dev`.

**Access-Token-Claims**

```json
{
  "sub": "mbulut",
  "uid": 42,
  "typ": "access",
  "ver": 3,
  "roles": ["PROJEKTLEITER"],
  "perms": ["USER_READ","USER_CREATE","POI_PUBLISH", "..."],
  "jti": "0f1a...",
  "iat": 1767225600,
  "exp": 1767226500
}
```

**Refresh-Token-Claims:** nur `sub`, `uid`, `typ:"refresh"`, `ver`, `jti`, `iat`, `exp`.

**Begründung für `perms` im Token** (in Kapitel 4 der Arbeit dokumentieren): die Berechtigungsmenge ist klein
(< 40 Einträge), dadurch bleibt die Autorisierung vollständig zustandslos und ohne DB-Zugriff pro Request. Der
Preis — verzögerte Wirksamkeit von Rechteänderungen — wird durch den `ver`-Claim aufgehoben.

### 4.2 Token-Invalidierung (drei Mechanismen)

| Mechanismus | Zweck | Umsetzung |
|---|---|---|
| Redis-Blacklist | Explizites Logout | Key `jwt:bl:{jti}`, Wert `"1"`, TTL = Restlaufzeit des Tokens |
| `ver`-Claim | Rollenänderung, Sperrung, Passwortwechsel | `JwtAuthFilter` vergleicht `ver` mit `admin_user.token_version` (Redis-Cache `user:ver:{uid}`, TTL 5 min, Invalidierung beim Schreiben) → bei Abweichung `401 TOKEN_STALE` |
| Kurze Lebensdauer | Grundabsicherung | 15 min Access-Token |

### 4.3 Filterkette

`SecurityConfig`:

```java
http
  .csrf(csrf -> csrf.disable())                      // zustandslose API, kein Cookie-Auth
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .cors(withDefaults())
  .exceptionHandling(e -> e
      .authenticationEntryPoint(restAuthenticationEntryPoint)
      .accessDeniedHandler(restAccessDeniedHandler))
  .authorizeHttpRequests(a -> a
      .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
      .requestMatchers("/api/public/**", "/api/health").permitAll()
      .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
      .anyRequest().authenticated())
  .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

`@EnableMethodSecurity(prePostEnabled = true)` auf `SecurityConfig`.

`JwtAuthFilter.doFilterInternal`:
1. `Authorization: Bearer <token>` extrahieren; fehlt der Header → Kette fortsetzen (Endpunkt entscheidet).
2. Signatur und Ablauf prüfen → ungültig: Kette ohne Authentication fortsetzen (führt zu 401).
3. `typ == "access"` prüfen — Refresh-Tokens dürfen **nie** als Access-Token gelten.
4. Blacklist prüfen (`jwt:bl:{jti}`).
5. `ver` gegen `TokenVersionService.current(uid)` prüfen.
6. `CampusUserDetails` aus den Claims bauen (kein DB-Zugriff), Authorities = `perms` + `ROLE_<rolename>`.
7. `UsernamePasswordAuthenticationToken` in den `SecurityContext` setzen.

### 4.4 Autorisierung auf Methodenebene

Grundmuster — **jede** Controller-Methode ist annotiert:

```java
@PreAuthorize("hasAuthority('POI_CREATE')")
@PreAuthorize("hasAuthority('POI_UPDATE_ANY') or (hasAuthority('POI_UPDATE_OWN') and @poiSecurity.canEdit(#id, authentication))")
@PreAuthorize("hasAuthority('CONSULTATION_UPDATE_ANY') or (hasAuthority('CONSULTATION_UPDATE_OWN') and @consultationSecurity.isResponsible(#id, authentication))")
@PreAuthorize("isAuthenticated()")   // nur für /api/auth/me, /api/auth/logout, Profil
```

`PoiSecurity.canEdit(Long poiId, Authentication auth)`: `true`, wenn `poi.created_by == uid` **oder**
`poi.assigned_to == uid`, **und** `poi.status` in (`DRAFT`, `IN_REVIEW`) — veröffentlichte Inhalte sind für
`POI_UPDATE_OWN`-Inhaber gesperrt.

**Testpflicht:** `EndpointSecurityTest` reflektiert über alle `@RestController`-Methoden und schlägt fehl, wenn
eine öffentlich erreichbare Methode weder `@PreAuthorize` trägt noch in einer expliziten Allowlist
(`/api/auth/login`, `/api/auth/refresh`, `/api/public/**`, `/api/health`) steht. Das ist der technische Nachweis
für Anforderung FA-14 und ein sehr gut zitierbares Ergebnis für Kapitel 5.

### 4.5 Content-Statusautomat

```
        POI_CREATE                POI_SUBMIT_REVIEW           POI_PUBLISH
  ( — ) ──────────► DRAFT ──────────────────────► IN_REVIEW ──────────────► PUBLISHED
                      ▲                               │                          │
                      └───────────────────────────────┘                          │
                            POI_PUBLISH (reject, review_note pflicht)            │
                      ◄──────────────────────────────────────────────────────────┘
                                       POI_PUBLISH (archive) ──► ARCHIVED
```

Erlaubte Übergänge in `PoiStatusService.transition(poi, target, actor)`; jeder unerlaubte Übergang →
`409 INVALID_STATUS_TRANSITION`. Beim Wechsel nach `PUBLISHED` werden `published_at` und `published_by` gesetzt.

Das ist die technische Abbildung von Stefans *"Qualitätskontrolle/Check/Freischalten von Content durch
Projektleitung"*: `PROJEKTMITARBEITER` erstellt und reicht ein, `PROJEKTLEITER` gibt frei oder weist zurück.

### 4.6 Audit-Log

Protokolliert werden **alle schreibenden Operationen** sowie sicherheitsrelevante Leseereignisse.

Aktionskatalog: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `TOKEN_REFRESHED`, `USER_CREATED`, `USER_UPDATED`,
`USER_DELETED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `PASSWORD_CHANGED`, `PASSWORD_RESET`, `ROLE_ASSIGNED`,
`ROLE_REVOKED`, `ACCESS_DENIED`, `POI_CREATED`, `POI_UPDATED`, `POI_DELETED`, `POI_SUBMITTED`, `POI_PUBLISHED`,
`POI_REJECTED`, `POI_ARCHIVED`, `POI_ASSIGNED`, `BUILDING_*`, `CONSULTATION_*`, `MEDIA_UPLOADED`, `MEDIA_DELETED`.

Umsetzung: Annotation `@Audited(action = "...", resourceType = "...")` auf Service-Methoden, ausgewertet durch
`AuditAspect` (Spring AOP). `before_state`/`after_state` als JSONB, **ohne** `password_hash` und ohne Tokens
(Maskierung über eine Feld-Blockliste in `AuditService`). Zusätzlich schreibt `RestAccessDeniedHandler` einen
`ACCESS_DENIED`-Eintrag — damit lassen sich in der Evaluation abgewiesene Zugriffe belegen.

Ein fehlgeschlagenes Audit-Schreiben darf die Geschäftstransaktion **nicht** abbrechen (`REQUIRES_NEW`,
Fehler nur loggen).

### 4.7 Fehlerformat

Einheitlich über `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-07-27T10:15:30Z",
  "status": 403,
  "error": "Forbidden",
  "code": "ROLE_NOT_GRANTABLE",
  "message": "Die Rolle ADMIN darf von dieser Rolle nicht vergeben werden.",
  "path": "/api/users/7/roles",
  "fieldErrors": { "roleName": "..." }
}
```

Statuscodes: `400` Validierung, `401` fehlende/ungültige Authentifizierung, `403` fehlende Berechtigung,
`404` unbekannte Ressource, `409` Konflikt/Invariante, `422` unzulässiger Statusübergang, `500` unerwartet.
**Nie** Stacktraces an den Client.

---

## 5. REST-API

Alle Antworten JSON, Zeitangaben ISO-8601 UTC. Listen sind paginiert:
`?page=0&size=20&sort=lastName,asc`, Antwortformat `PageResponse<T>` (`content`, `page`, `size`,
`totalElements`, `totalPages`).

### 5.1 Authentifizierung — `/api/auth`

| Methode | Pfad | Autorisierung | Beschreibung |
|---|---|---|---|
| POST | `/login` | permitAll | `{username, password}` → `{accessToken, refreshToken, expiresIn, user}`. Bei inaktivem Konto `403 ACCOUNT_DISABLED`. Fehlversuche werden auditiert. |
| POST | `/refresh` | permitAll | `{refreshToken}` → neues Token-Paar (Rotation: altes Refresh-Token wird geblacklistet) |
| POST | `/logout` | `isAuthenticated()` | Access- und Refresh-`jti` blacklisten |
| GET | `/me` | `isAuthenticated()` | Aktueller Nutzer inkl. `roles` und `permissions` — Grundlage für das Frontend-Menü |
| PUT | `/me` | `hasAuthority('PROFILE_UPDATE_OWN')` | Eigene Stammdaten ändern |
| POST | `/me/password` | `hasAuthority('PROFILE_UPDATE_OWN')` | `{currentPassword, newPassword}`, erhöht `token_version` |

### 5.2 Nutzerverwaltung — `/api/users`

| Methode | Pfad | Autorisierung |
|---|---|---|
| GET | `` | `hasAuthority('USER_READ')` — Filter `q`, `role`, `active` |
| GET | `/{id}` | `hasAuthority('USER_READ')` |
| POST | `` | `hasAuthority('USER_CREATE')` — Body enthält `roles[]`, jede Rolle wird gegen die Vergabemenge geprüft |
| PUT | `/{id}` | `hasAuthority('USER_UPDATE')` + `assertCanManage` |
| PATCH | `/{id}/status` | `hasAuthority('USER_ACTIVATE')` + INV-1, INV-2 |
| DELETE | `/{id}` | `hasAuthority('USER_DELETE')` + INV-1, INV-2 |
| POST | `/{id}/password-reset` | `hasAuthority('USER_PASSWORD_RESET')` — erzeugt temporäres Passwort, gibt es einmalig zurück |
| GET | `/{id}/roles` | `hasAuthority('USER_READ')` |
| POST | `/{id}/roles` | `hasAuthority('ROLE_ASSIGN')` — Body `{roleName}` |
| DELETE | `/{id}/roles/{roleName}` | `hasAuthority('ROLE_ASSIGN')` + INV-1..3 |
| GET | `/me/grantable-roles` | `hasAuthority('ROLE_ASSIGN')` — Rollen, die der Aufrufer vergeben darf (steuert das Dropdown im Frontend) |

### 5.3 Rollen und Berechtigungen — `/api/roles`, `/api/permissions`

| Methode | Pfad | Autorisierung | Beschreibung |
|---|---|---|---|
| GET | `/api/roles` | `hasAuthority('ROLE_READ')` | Alle Rollen inkl. Anzahl zugeordneter Nutzer |
| GET | `/api/roles/{name}` | `hasAuthority('ROLE_READ')` | Rolle inkl. vollständiger Berechtigungsliste |
| GET | `/api/roles/matrix` | `hasAuthority('ROLE_READ')` | Vollständige Berechtigungsmatrix als JSON — rendert die Matrix-Ansicht im Frontend und liefert **die Abbildung für Kapitel 4 der Arbeit** |
| GET | `/api/permissions` | `hasAuthority('ROLE_READ')` | Berechtigungskatalog, gruppiert nach `resource` |
| POST/PUT/DELETE | `/api/roles/**` | — | **Nicht implementiert.** In `OpenApiConfig` als geplante Erweiterung dokumentieren (Ausblick, E-1). |

### 5.4 Content

**POIs — `/api/pois`**

| Methode | Pfad | Autorisierung |
|---|---|---|
| GET | `` | `hasAuthority('POI_READ_ALL')` — Filter `status`, `category`, `buildingId`, `assignedTo`, `q` |
| GET | `/{id}` | `hasAuthority('POI_READ_ALL')` |
| POST | `` | `hasAuthority('POI_CREATE')` — Status immer `DRAFT`, `created_by` = Aufrufer |
| PUT | `/{id}` | `POI_UPDATE_ANY` oder (`POI_UPDATE_OWN` und `@poiSecurity.canEdit`) |
| DELETE | `/{id}` | `hasAuthority('POI_DELETE')` |
| POST | `/{id}/submit` | `POI_SUBMIT_REVIEW` und `@poiSecurity.canEdit` |
| POST | `/{id}/publish` | `hasAuthority('POI_PUBLISH')` |
| POST | `/{id}/reject` | `hasAuthority('POI_PUBLISH')` — Body `{reviewNote}` (Pflichtfeld) |
| POST | `/{id}/archive` | `hasAuthority('POI_PUBLISH')` |
| PATCH | `/{id}/assignee` | `hasAuthority('POI_ASSIGN')` — Body `{userId}` |

**Gebäude — `/api/buildings`:** GET/GET{id} (`BUILDING_READ_ALL`), POST (`BUILDING_CREATE`),
PUT (`BUILDING_UPDATE`), DELETE (`BUILDING_DELETE`, `409` bei referenzierenden POIs).

**Beratungszeiten — `/api/consultations`:** GET/GET{id} (`CONSULTATION_READ_ALL`),
POST (`CONSULTATION_CREATE`, `responsible_user_id` = Aufrufer, sofern nicht `CONSULTATION_UPDATE_ANY`),
PUT (`ANY` oder `OWN` + `@consultationSecurity.isResponsible`), DELETE (`CONSULTATION_DELETE`),
verschachtelt `POST|PUT|DELETE /{id}/events/**` mit derselben Regel.

**Medien — `/api/media`:** `POST` (multipart, `MEDIA_UPLOAD`, max. 5 MB, nur `image/png|jpeg|webp`),
`DELETE /{id}` (`MEDIA_DELETE`), `GET /{id}` (authentifiziert). Speicherung in einem Docker-Volume unter
`/data/media`, Dateiname wird auf UUID normalisiert.

### 5.5 Audit, System, Public, Export

| Methode | Pfad | Autorisierung | Beschreibung |
|---|---|---|---|
| GET | `/api/audit` | `AUDIT_READ` oder `AUDIT_READ_CONTENT` | Filter `actorId`, `action`, `resourceType`, `from`, `to`. Mit nur `AUDIT_READ_CONTENT` erzwingt der Service `resourceType IN (POI, BUILDING, CONSULTATION, MEDIA)` |
| GET | `/api/audit/{id}` | wie oben | Detail inkl. `before`/`after` |
| GET | `/api/health` | permitAll | Liveness — `{status, timestamp}` |
| GET | `/api/system/info` | `SYSTEM_HEALTH_READ` | Version, Uptime, DB- und Redis-Status, Anzahl aktiver Nutzer |
| GET | `/api/public/pois` | permitAll | nur `status = PUBLISHED`, reduziertes DTO (keine `created_by`, keine internen Felder) |
| GET | `/api/public/buildings` | permitAll | nur `is_published = true` |
| GET | `/api/public/consultations` | permitAll | nur `is_published = true` |
| GET | `/api/export/pois.csv` | `DATA_EXPORT` | CSV-Export |

**Public-DTO-Regel:** Die `publicapi`-Controller verwenden eigene, schlanke DTOs. Ein Test stellt sicher, dass
dort keine Nutzer- oder Statusfelder serialisiert werden (Datenminimierung, gut zitierbar in Kapitel 4).

---

## 6. Frontend (React + Vite + SCSS)

```
frontend/src
├── main.jsx, App.jsx
├── api
│   ├── client.js            # fetch-Wrapper: Bearer-Header, 401→Refresh-Retry (einmalig), Fehler→ApiError
│   ├── auth.js, users.js, roles.js, pois.js, buildings.js, consultations.js, audit.js, media.js
├── auth
│   ├── AuthContext.jsx      # {user, roles, permissions, login, logout, hasPermission, hasRole}
│   ├── ProtectedRoute.jsx   # Redirect auf /login, wenn nicht authentifiziert
│   ├── RequirePermission.jsx# Route-Guard: rendert 403-Seite ohne passende Permission
│   └── Can.jsx              # <Can perm="POI_PUBLISH">…</Can> — Sichtbarkeitssteuerung
├── components
│   ├── layout/{AppShell, Sidebar, Topbar}.jsx
│   ├── ui/{DataTable, Modal, ConfirmDialog, Toast, StatusBadge, Pagination, FormField}.jsx
│   └── rbac/{RoleChip, RoleAssignPanel, PermissionMatrix}.jsx
├── pages
│   ├── LoginPage.jsx
│   ├── DashboardPage.jsx
│   ├── users/{UserListPage, UserFormPage, UserDetailPage}.jsx
│   ├── roles/{RoleListPage, RoleDetailPage, PermissionMatrixPage}.jsx
│   ├── pois/{PoiListPage, PoiEditorPage, ReviewQueuePage}.jsx
│   ├── buildings/{BuildingListPage, BuildingFormPage}.jsx
│   ├── consultations/{ConsultationListPage, ConsultationFormPage}.jsx
│   ├── audit/AuditLogPage.jsx
│   ├── system/SystemInfoPage.jsx
│   └── errors/{ForbiddenPage, NotFoundPage}.jsx
├── styles/  (SCSS: _variables, _mixins, main.scss, komponentennahe Partials)
└── test/    (Jest + Testing Library)
```

**Navigationsregeln** (`Sidebar` filtert anhand `permissions` aus `/api/auth/me`):

| Menüpunkt | Sichtbar bei |
|---|---|
| Dashboard | immer |
| Nutzerverwaltung | `USER_READ` |
| Rollen & Rechte | `ROLE_READ` |
| POIs | `POI_READ_ALL` |
| Freigabe-Warteschlange | `POI_PUBLISH` |
| Gebäude | `BUILDING_READ_ALL` |
| Beratungszeiten | `CONSULTATION_READ_ALL` |
| Audit-Log | `AUDIT_READ` oder `AUDIT_READ_CONTENT` |
| System | `SYSTEM_HEALTH_READ` |

**Rollenzuweisung im UI** (`RoleAssignPanel` auf `UserDetailPage`): zeigt die aktuellen Rollen als entfernbare
Chips und ein Dropdown, das ausschließlich aus `GET /api/users/me/grantable-roles` befüllt wird. Aktionen laufen
über `POST`/`DELETE .../roles`, Fehlercodes (`LAST_ADMIN_PROTECTED`, `SELF_MODIFICATION_FORBIDDEN`, …) werden als
verständliche deutsche Toast-Meldungen angezeigt. Nach jeder Änderung wird die Nutzerdetailseite neu geladen.

**Wichtig:** Frontend-Guards sind reine Usability-Maßnahmen. In der Arbeit (und im Code-Kommentar über
`Can.jsx`) ist explizit zu vermerken, dass die Durchsetzung serverseitig erfolgt.

**Content-Editor** (`PoiEditorPage`): formularbasierter Dialog mit `name_de/en`, `description_de/en`,
Kategorie-Select, Gebäude-Dropdown (aus `/api/buildings`), drei Koordinatenfeldern, Medien-Upload und
Statusanzeige. Aktionsleiste kontextsensitiv: *Speichern* (bei Bearbeitungsrecht), *Zur Prüfung einreichen*
(bei `POI_SUBMIT_REVIEW` und Status `DRAFT`), *Freigeben* / *Zurückweisen* (bei `POI_PUBLISH` und Status
`IN_REVIEW`). Clientseitige Validierung, serverseitige Validierung über Bean Validation.

---

## 7. Konfiguration, Betrieb, Qualität

### 7.1 Profile und Variablen

`application.yml` (Basis) + `application-dev.yml`, `application-docker.yml`, `application-test.yml`.

| Variable | Default (dev) | Bedeutung |
|---|---|---|
| `JWT_SECRET` | dev-Dummy | HS256-Secret, ≥ 32 Zeichen |
| `CAMPUS_JWT_ACCESS_TTL` | `PT15M` | Access-Token-Laufzeit |
| `CAMPUS_JWT_REFRESH_TTL` | `P7D` | Refresh-Token-Laufzeit |
| `CAMPUS_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL` | `admin` / `admin` / `admin@localhost` | Initialer Admin |
| `CAMPUS_SEED_DEMO` | `true` in dev, `false` sonst | Demo-Daten laden |
| `CAMPUS_MEDIA_PATH` | `./data/media` | Ablage der Uploads |
| `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_HOST` | s. Repo | bestehend |

Beim Start protokolliert `AppInitializerService` eine **Warnung**, wenn der Initial-Admin noch das
Standardpasswort besitzt; im Profil `docker`/`prod` erzwingt er beim ersten Login eine Passwortänderung
(`must_change_password`-Flag auf `admin_user` ergänzen).

### 7.2 Docker Compose

Vier Dienste wie bisher (`frontend:3000`, `backend:8080`, `db`, `redis`), ergänzt um:
Healthchecks für `db` und `redis`, `depends_on: condition: service_healthy` beim Backend, benanntes Volume
`media_data:/data/media`, `.env.example` mit allen Variablen aus 7.1.

### 7.3 Teststrategie

| Ebene | Werkzeug | Umfang |
|---|---|---|
| Unit | JUnit 5, Mockito | `RoleAssignmentService` (alle Invarianten), `PoiStatusService` (alle Übergänge), `JwtService` |
| Slice | `@WebMvcTest` + `spring-security-test` | Autorisierung je Endpunkt mit `@WithMockUser(authorities = …)` |
| Integration | `@SpringBootTest` + **Testcontainers** (PostgreSQL + Redis) | Login → Rollenzuweisung → Token-Invalidierung → Zugriffsversuch; Freigabe-Workflow |
| Architektur | ArchUnit | Schichtregel, keine Entity in Controllern |
| Sicherheit | eigener Reflection-Test | `EndpointSecurityTest` (siehe 4.4) |
| Konsistenz | eigener Test | `RoleCatalog` (Java) == Seed (`V4__seed_rbac.sql`) == `GET /api/roles/matrix` |
| Frontend | Jest + Testing Library | `AuthContext`, `Can`, `RoleAssignPanel`, `PoiEditorPage` |

Zielabdeckung: Backend ≥ 70 % Zeilen, Sicherheits- und RBAC-Pakete ≥ 90 %. JaCoCo-Report in `target/site/jacoco`
— die Zahlen sind für Kapitel 5 verwertbar.

### 7.4 Dokumentationsartefakte im Repo

```
docs/
├── DECISIONS.md          # Architekturentscheidungen mit Begründung (ADR-Kurzform)
├── ROLE_MODEL.md         # Rollenkatalog + Berechtigungsmatrix (aus /api/roles/matrix generierbar)
├── API.md                # Kurzübersicht; Details via Swagger UI
└── EVALUATION.md         # Szenarien und Testprotokolle (Kapitel 5)
```

---

## 8. Bewusste Abgrenzung (nicht implementieren)

- Keine Unity-Integration, kein 3D-Rendering im Admin Panel.
- Keine UI und keine Endpunkte zum Anlegen eigener Rollen (**Ausblick**, siehe 9).
- Kein Single Sign-On / kein TU-ID-Login (im Ausblick als realistische Erweiterung nennen).
- Keine Mehrmandantenfähigkeit, keine Attribut-basierte Zugriffskontrolle (ABAC).
- Keine E-Mail-Zustellung (Passwort-Reset gibt das temporäre Passwort direkt in der Antwort zurück — im
  Prototyp akzeptabel, in der Arbeit als bewusste Vereinfachung kennzeichnen).
- Kein Rich-Text-Editor, keine Bildbearbeitung, keine Versionierung von Inhalten.

## 9. Vorbereitete Erweiterungspunkte (für den Ausblick, Kapitel 6.3)

Die Architektur ist so gewählt, dass die im Ausblick genannten Erweiterungen **ohne Redesign** möglich sind —
dieser Absatz ist wörtlich für Kapitel 6.3 verwertbar:

1. **Individuelle Rollenerstellung:** Da Berechtigungen bereits als eigene Entität mit `role_permission`-Relation
   vorliegen und die Autorisierung über Authorities (nicht über Rollennamen) erfolgt, genügen ein CRUD-Endpunkt
   auf `/api/roles` (Berechtigung `ROLE_MANAGE` existiert bereits), das Entfernen des `is_system`-Schutzes für
   neue Rollen und ein Matrix-Editor im Frontend. Kein Eingriff in die Durchsetzungslogik nötig.
2. **Rollenhierarchien** (RBAC1 nach Sandhu): Selbstreferenzierende Tabelle `role_hierarchy` und transitive
   Auflösung beim Token-Aufbau.
3. **Unity-Anbindung:** `/api/public/**` ist bereits der stabile Vertrag; ergänzend ein versioniertes
   `/api/public/v1/scene`-DTO.
4. **SSO über TU-ID (SAML/OIDC):** `CampusUserDetailsService` und `JwtService` sind die einzigen Berührungspunkte.
5. **Feingranulare Zuständigkeiten nach Einrichtung:** Das Feld `admin_user.organisation` und
   `consultation.organisation` sind bereits vorhanden und ermöglichen eine spätere Erweiterung um
   organisationsbezogene Sichtbarkeit (Schritt in Richtung ABAC).
