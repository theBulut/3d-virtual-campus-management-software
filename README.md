
# 3D Virtual Campus Management Software

Administrationsinfrastruktur für den 3D Campus Explorer der TU Darmstadt — Bachelorarbeit, Mehmet Bulut.

Rollenbasierte Benutzer- und Rechteverwaltung mit Freigabe-Workflow für Inhalte, revisionssicherem
Audit-Log und einer öffentlichen Leseschnittstelle als Vorbereitung für einen Unity-Client.

Verbindliche Spezifikation:

- [docs/spec/01_ARCHITEKTUR_SPEC.md](docs/spec/01_ARCHITEKTUR_SPEC.md) — Rollenmodell, Datenmodell,
  Security, REST-API, Frontend
- [docs/spec/02_IMPLEMENTIERUNGSPLAN.md](docs/spec/02_IMPLEMENTIERUNGSPLAN.md) — Anforderungskatalog,
  Phasenplan, Evaluationsszenarien
- [docs/DECISIONS.md](docs/DECISIONS.md) — Entwurfsentscheidungen und Abweichungen von der Spec

## Tech-Stack

- **Backend**: Spring Boot 4.1 (Java 21, Maven), Spring Security 7, Spring Data JPA, Flyway,
  springdoc-openapi
- **Datenbank**: PostgreSQL 17 — Schema ausschließlich über Flyway, `ddl-auto=validate`
- **Cache**: Redis 7 — JWT-Blacklist und Token-Version-Cache
- **Frontend**: React (Vite), SCSS, Jest + Testing Library
- **Tests**: JUnit 5, Testcontainers, ArchUnit, JaCoCo
- **Betrieb**: Docker Compose (frontend, backend, db, redis)

## Umsetzungsstand

Der Phasenplan steht in `docs/spec/02_IMPLEMENTIERUNGSPLAN.md`, Abschnitt 2.

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Fundament: Paketstruktur, Abhängigkeiten, Profile, Fehlerformat | ✅ |
| 1 | Datenmodell und Flyway-Migrationen | ✅ |
| 2 | RBAC-Katalog und Seeding | ✅ |
| 3 | Authentifizierung (JWT, Refresh, Logout) | ✅ |
| 4 | Autorisierung, Nutzer- und Rollenverwaltung | ✅ |
| 5 | Audit-Log | ✅ |
| 6 | Content: POI, Gebäude, Beratungszeiten, Medien | ✅ |
| 7 | Frontend | offen |
| 8 | Härtung, Dokumentation, Evaluation | offen |

Beim Start sind die sechs Rollen, 37 Berechtigungen und die Vergaberegeln bereits in der Datenbank, und
ein initialer Administrator existiert (Standard `admin`/`admin`, siehe `.env.example`).

Unter `/api/auth/**` liegen Anmeldung, Token-Erneuerung mit Rotation, Abmeldung und das eigene Profil.
Ohne Token antwortet jeder Pfad außer `/api/health` und `/api/auth/login|refresh` mit `401`.

Abgemeldet wird auf zwei Wegen (siehe `docs/DECISIONS.md` D-24):

| Endpunkt | Wirkung |
|---|---|
| `POST /api/auth/logout` | beendet die aktuelle Sitzung; Refresh-Token optional im Rumpf |
| `POST /api/auth/logout-all` | beendet alle Sitzungen des Kontos, auch auf anderen Geräten |

Dazu kommen die Verwaltungsendpunkte aus Phase 4 unter `/api/users`, `/api/roles` und `/api/permissions`
— jeder mit `@PreAuthorize` auf einem Berechtigungscode. `GET /api/roles/matrix` liefert die vollständige
Berechtigungsmatrix als JSON und ist zugleich die Quelle für die Abbildung in Kapitel 4 der Arbeit.

Jede schreibende Operation und jeder abgewiesene Zugriff landen im Audit-Log (`GET /api/audit`).
`AUDIT_READ` sieht alles, `AUDIT_READ_CONTENT` ausschließlich Einträge zu POI, Gebäude, Beratungszeiten
und Medien. Passwörter, Hashes und Tokens werden beim Schreiben maskiert und stehen nie im Log.

### Inhalte und Freigabe-Workflow

POIs unter `/api/pois` durchlaufen vier Zustände. Jeder Übergang hat einen eigenen Endpunkt, eine eigene
Berechtigung und eine eigene Audit-Aktion — ein gewöhnliches `PUT` kann nichts veröffentlichen:

```
DRAFT ──submit──▶ IN_REVIEW ──publish──▶ PUBLISHED ──archive──▶ ARCHIVED
  ▲                   │
  └────reject─────────┘   (Begründung ist Pflicht)
```

| Aktion | Endpunkt | Berechtigung |
|---|---|---|
| Einreichen | `POST /api/pois/{id}/submit` | `POI_SUBMIT_REVIEW` + Eigentum |
| Freigeben | `POST /api/pois/{id}/publish` | `POI_PUBLISH` |
| Zurückweisen | `POST /api/pois/{id}/reject` | `POI_PUBLISH` |
| Archivieren | `POST /api/pois/{id}/archive` | `POI_PUBLISH` |
| Bearbeiter setzen | `PATCH /api/pois/{id}/assignee` | `POI_ASSIGN` |

`PROJEKTMITARBEITER` erstellt und reicht ein, veröffentlichen kann nur die Projektleitung. `POI_UPDATE_OWN`
greift nur bei eigenen oder zugewiesenen POIs und nur solange sie nicht veröffentlicht sind; jeder andere
Übergang antwortet mit `422 INVALID_STATUS_TRANSITION`. Dazu kommen Gebäude (`/api/buildings`),
Beratungszeiten mit ihren Terminen (`/api/consultations`), Bild-Uploads (`/api/media`, höchstens 5 MB,
PNG/JPEG/WebP) und der CSV-Export (`GET /api/export/pois.csv`).

Ohne Anmeldung erreichbar ist ausschließlich `/api/public/**` — veröffentlichte POIs, Gebäude und
Beratungszeiten, in eigenen, bewusst schmalen DTOs ohne Status, Autor, Bearbeiter oder Zeitstempel.

Zwei Regeln gelten über die Berechtigung hinaus: eine Rolle kann nur vergeben werden, wenn sie in der
Vergabemenge des Aufrufers liegt (`role_grant`), und ein fremdes Konto nur bearbeitet werden, wenn **alle**
seine Rollen in dieser Menge liegen. Eine Projektleitung erreicht damit weder ein ADMIN- noch ein
MAINTENANCE_DEV-Konto.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

Solange das Standardpasswort gesetzt ist, enthält das Access-Token außerhalb des `dev`-Profils nur die
Berechtigung `PROFILE_UPDATE_OWN` — das Konto kann also ausschließlich sein Passwort ändern
(`POST /api/auth/me/password`, mindestens 12 Zeichen). Danach trägt der nächste Token wieder alle
Berechtigungen der Rolle.

## Gesamtsystem starten

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html

Es sind keine Vorbereitungsschritte nötig. Ohne `JWT_SECRET` erzeugt das Backend beim Start ein
zufälliges Secret — praktisch für lokale Läufe, aber jeder Neustart entwertet alle ausgegebenen Tokens.
Für stabile Sitzungen `.env.example` nach `.env` kopieren und `JWT_SECRET` setzen.

> **Beim Wechsel von einem älteren Stand:** das Skelett hatte `ddl-auto=update` und hat die Tabellen
> `users` und `admins` angelegt. Flyway bricht auf einem nicht-leeren Schema ohne History-Tabelle ab. Vor
> dem ersten Start deshalb das Volume verwerfen:
>
> ```bash
> docker compose down -v
> ```
>
> `spring.flyway.baseline-on-migrate` ist bewusst **nicht** gesetzt — das würde `V1` überspringen.

## Lokale Entwicklung

### Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Erwartet PostgreSQL (`campus`, `postgres`/`postgres`) und Redis auf localhost, oder Überschreibung über
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
`SPRING_DATA_REDIS_HOST`. Das `dev`-Profil lädt zusätzlich die Demo-Migrationen aus `db/demo`: fünf
Gebäude, zwölf POIs über alle vier Zustände, vier Beratungsangebote und ein Konto je Rolle. Alle
Demo-Konten haben das Passwort `demo-passwort`; die Daten werden außerhalb von `dev` nie geladen
(`docs/DECISIONS.md` D-8).

| Konto | Rolle |
|---|---|
| `demo_admin` | ADMIN |
| `demo_leitung` | PROJEKTLEITER |
| `demo_mitarbeit` | PROJEKTMITARBEITER |
| `demo_personal` | PERSONAL |
| `demo_devops` | MAINTENANCE_DEV |

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Der Vite-Dev-Server proxyt `/api` auf `http://localhost:8080`.

## Tests

```bash
cd backend && ./mvnw test     # benötigt einen laufenden Docker-Daemon (Testcontainers)
cd frontend && npm test
```

Integrationstests heißen `*IT` und tragen `@Tag("it")`. Ohne Docker:

```bash
cd backend && ./mvnw test -DexcludedGroups=it
```

JaCoCo-Report nach einem Testlauf: `backend/target/site/jacoco/index.html`.

## Konfiguration

Alle Variablen mit Bedeutung und Default in [.env.example](.env.example). Profile: `dev` (lokal, inklusive
Demo-Daten), `docker` (Compose), `test` (Testcontainers).
