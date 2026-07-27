# Projektkontext

Administrationsinfrastruktur für den 3D Campus Explorer der TU Darmstadt (Bachelorarbeit, Mehmet Bulut).
Verbindliche Spezifikation:

- `docs/spec/01_ARCHITEKTUR_SPEC.md` — Rollenmodell, Datenmodell, Security, REST-API, Frontend
- `docs/spec/02_IMPLEMENTIERUNGSPLAN.md` — Anforderungskatalog, Phasenplan, Evaluationsszenarien

Vor jeder Änderung die relevanten Abschnitte der Spec lesen. Die Spec hat Vorrang vor Konventionen.

## Stack

Spring Boot **4.1** (Java 21, Maven), PostgreSQL 17, Redis 7, React (Vite) + SCSS, Docker Compose.

Die Spec sagt „Spring Boot 3", das Repo läuft auf 4.1.0 — siehe `docs/DECISIONS.md` D-1. Zwei
Konsequenzen, die beim Nachschlagen von Beispielcode immer wieder auffallen:

- Autokonfigurationen liegen in Technologie-Modulen (`spring-boot-flyway`, …), nicht mehr in
  `spring-boot-autoconfigure`. Die Bibliothek allein genügt nicht.
- Test-Annotationen sind umgezogen: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
  statt `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`.

## Feste Regeln

- Rollennamen sind unveränderlich: `ADMIN`, `PROJEKTLEITER`, `PROJEKTMITARBEITER`, `PERSONAL`,
  `MAINTENANCE_DEV`, `EXTERNE_PERSON`. Sie sind so in der Bachelorarbeit dokumentiert.
- Autorisierung ausschließlich über Authorities (Permission-Codes wie `POI_PUBLISH`), **nie** über
  `hasRole('PROJEKTLEITER')`. Rollen sind nur Bündel von Berechtigungen.
- Jede Controller-Methode trägt eine `@PreAuthorize`-Annotation. Ausnahmen ausschließlich über die Allowlist in
  `EndpointSecurityTest` (`/api/auth/login`, `/api/auth/refresh`, `/api/public/**`, `/api/health`, Swagger).
- Schichten: Controller → Service → Repository. Controller injizieren keine Repositories.
- Entities verlassen nie die Service-Schicht; nach außen ausschließlich DTOs.
- Schemaänderungen nur über **neue** Flyway-Migrationen. Bestehende Migrationen werden nie geändert.
  `spring.jpa.hibernate.ddl-auto=validate`.
- Sprache: Code, Bezeichner, Kommentare und Commit-Messages englisch. Fehlermeldungen für Endnutzer und
  UI-Texte deutsch.
- Keine neuen Abhängigkeiten ohne Eintrag mit Begründung in `docs/DECISIONS.md`.
- Keine Secrets im Repo. `JWT_SECRET` kommt aus der Umgebung; Fail-Fast bei Default-Secret außerhalb von `dev`.
  Im `docker`-Profil wird ohne gesetztes `JWT_SECRET` ein zufälliges Secret erzeugt (D-5), damit
  `docker compose up` ohne manuelle Schritte startet, ohne dass ein bekanntes Secret im Repo liegt.

## Befehle

```bash
cd backend && ./mvnw test                      # Backend-Tests (Docker nötig, Testcontainers)
cd backend && ./mvnw test -DexcludedGroups=it  # nur Unit- und Slice-Tests, ohne Docker
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm test                        # Frontend-Tests
cd frontend && npm run dev                     # Frontend lokal
docker compose up --build                      # Gesamtsystem
```

Integrationstests heißen `*IT` und tragen `@Tag("it")`; Basisklasse ist `AbstractIntegrationTest`.

- Frontend: http://localhost:3000 · API: http://localhost:8080/api/health · Swagger: http://localhost:8080/swagger-ui.html

## Definition of Done je Phase

1. Alle Tests grün (`./mvnw test`, `npm test`).
2. `docker compose up --build` startet ohne manuelle Schritte.
3. Abnahmekriterien der Phase aus `02_IMPLEMENTIERUNGSPLAN.md` durch Tests belegt.
4. Neue Entwurfsentscheidungen in `docs/DECISIONS.md` dokumentiert.
5. Ein Commit pro Phase, Message-Format: `feat(phase-N): <kurzbeschreibung>`.

## Arbeitsweise

Immer nur die aktuell beauftragte Phase umsetzen. Bei fehlenden oder widersprüchlichen Angaben anhalten,
offene Punkte auflisten und Lösungsvorschläge machen — nicht raten.
