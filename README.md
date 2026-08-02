
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
| 2 | RBAC-Katalog und Seeding | offen |
| 3 | Authentifizierung (JWT, Refresh, Logout) | offen |
| 4 | Autorisierung, Nutzer- und Rollenverwaltung | offen |
| 5 | Audit-Log | offen |
| 6 | Content: POI, Gebäude, Beratungszeiten, Medien | offen |
| 7 | Frontend | offen |
| 8 | Härtung, Dokumentation, Evaluation | offen |

Aktuell erreichbar ist ausschließlich `GET /api/health`; jeder andere Pfad antwortet mit `401`, solange
Phase 3 die Authentifizierung nicht bereitstellt.

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
`SPRING_DATA_REDIS_HOST`. Das `dev`-Profil lädt zusätzlich die Demo-Migrationen aus `db/demo`.

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
