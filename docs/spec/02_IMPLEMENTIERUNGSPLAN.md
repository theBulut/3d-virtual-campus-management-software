# Implementierungsplan, Anforderungskatalog und Evaluationsszenarien

Ergänzung zu `01_ARCHITEKTUR_SPEC.md`. Dieses Dokument liefert (a) den Anforderungskatalog, den Stefan im
weiteren Vorgehen fordert, (b) die Phasenplanung für die Umsetzung mit Claude Code und (c) die Szenarien für
den anforderungs- und szenariobasierten Funktionstest in Kapitel 5.

---

## 1. Anforderungskatalog

Die IDs sind in Kapitel 3, 4 und 5 der Arbeit durchgängig zu referenzieren (Traceability, siehe Abschnitt 4).

### 1.1 Funktionale Anforderungen

| ID    | Anforderung                                                                                                    | Priorität | Quelle                                                             |
| ----- | -------------------------------------------------------------------------------------------------------------- | --------- | ------------------------------------------------------------------ |
| FA-01 | Nutzer authentifizieren sich mit Benutzername und Passwort und erhalten ein zeitlich begrenztes Zugriffstoken. | Muss      | Kap. 2.3, 3.2.2                                                    |
| FA-02 | Passwörter werden ausschließlich als kryptografischer Hash gespeichert.                                        | Muss      | Kap. 3.2.2                                                         |
| FA-03 | Ein explizites Logout macht das ausgestellte Token sofort ungültig.                                            | Muss      | Kap. 3.2.2 (Redis-Blacklist)                                       |
| FA-04 | Das System stellt sechs vordefinierte Rollen bereit.                                                           | Muss      | Kap. 3.3.1, Prüfer-Mail (hard-coded)                               |
| FA-05 | Berechtigungen sind als eigenständige Objekte modelliert und Rollen zugeordnet.                                | Muss      | Kap. 2.4, Prüfer-Mail                                              |
| FA-06 | Nutzer können einer oder mehreren Rollen zugeordnet werden.                                                    | Muss      | Kap. 2.4 (Core RBAC)                                               |
| FA-07 | Berechtigte Nutzer können anderen Nutzern Rollen **zuweisen und entziehen**.                                   | Muss      | Prüfer-Mail (Benutzerverwaltung durch Projektleitung/Eileen)       |
| FA-08 | Die Menge vergebbarer Rollen ist abhängig von der Rolle des Vergebenden.                                       | Muss      | Prüfer-Mail (eingeschränkte Benutzerverwaltung)                    |
| FA-09 | Berechtigte Nutzer können Konten anlegen, bearbeiten, sperren und löschen.                                     | Muss      | Prüfer-Mail                                                        |
| FA-10 | Inhalte (POIs) durchlaufen einen Freigabe-Workflow: Entwurf → Prüfung → veröffentlicht.                        | Muss      | Prüfer-Mail (Qualitätskontrolle/Freischalten durch Projektleitung) |
| FA-11 | Erstellen und Freigeben von Inhalten sind unterschiedliche Berechtigungen.                                     | Muss      | Prüfer-Mail                                                        |
| FA-12 | Nutzer mit eingeschränkten Rechten dürfen nur eigene bzw. zugewiesene Inhalte bearbeiten.                      | Muss      | Kap. 3.3.1 (PROJEKTMITARBEITER)                                    |
| FA-13 | Beratungszeiten können durch Verwaltungspersonal gepflegt werden.                                              | Muss      | Kap. 3.3.1 (PERSONAL)                                              |
| FA-14 | Jede geschützte Operation wird **serverseitig** autorisiert; kein Endpunkt ist unbeabsichtigt offen.           | Muss      | Kap. 2.4, 3.3.3                                                    |
| FA-15 | Alle schreibenden Operationen und abgewiesene Zugriffe werden revisionssicher protokolliert.                   | Muss      | Kap. 3.2.3                                                         |
| FA-16 | Die Administrationsoberfläche blendet Funktionen ohne Berechtigung aus.                                        | Muss      | Kap. 3.2.4                                                         |
| FA-17 | Veröffentlichte Inhalte sind ohne Authentifizierung über eine öffentliche Schnittstelle abrufbar.              | Muss      | Kap. 3.4.2 (Vorbereitung Unity)                                    |
| FA-18 | Die REST-Schnittstelle ist vollständig über OpenAPI dokumentiert.                                              | Muss      | Kap. 3.2.1                                                         |
| FA-19 | Rechteänderungen werden ohne Neustart und ohne Abwarten der Token-Laufzeit wirksam.                            | Soll      | abgeleitet aus FA-07                                               |
| FA-20 | Die Berechtigungsmatrix ist zur Laufzeit über die API abrufbar.                                                | Soll      | Nachweisbarkeit für Kap. 4/5                                       |
| FA-21 | Medien können hochgeladen und einem POI zugeordnet werden.                                                     | Soll      | Kap. 3.3.1                                                         |
| FA-22 | Inhaltsdaten können exportiert werden (CSV).                                                                   | Kann      | Kap. 3.3.1 (PROJEKTLEITER)                                         |

### 1.2 Nicht-funktionale Anforderungen

| ID | Anforderung | Messkriterium |
|---|---|---|
| NFA-01 | Zustandslose Authentifizierung | Kein HTTP-Session-State; horizontale Skalierung möglich |
| NFA-02 | Schichtentrennung Controller/Service/Repository | ArchUnit-Test grün |
| NFA-03 | Reproduzierbares Deployment | `docker compose up --build` startet alle vier Dienste ohne manuelle Schritte |
| NFA-04 | Reproduzierbares Schema | Flyway-Migrationen, `ddl-auto=validate` |
| NFA-05 | Antwortzeit administrativer Leseoperationen | < 300 ms bei 1000 Datensätzen (lokal gemessen) |
| NFA-06 | Testabdeckung | Backend ≥ 70 %, Pakete `security`/`rbac` ≥ 90 % |
| NFA-07 | Keine Preisgabe interner Details in Fehlermeldungen | Kein Stacktrace im Response-Body |
| NFA-08 | Erweiterbarkeit des Rollenmodells | Neue Rolle = ein Katalog-Eintrag + eine Migrationszeile, kein Eingriff in Controller |
| NFA-09 | Mehrsprachigkeit der Inhalte | Alle nutzersichtbaren Inhaltsfelder in DE und EN |
| NFA-10 | Nachvollziehbarkeit | Audit-Eintrag mit Vorher-/Nachher-Zustand für jede schreibende Operation |

---

## 2. Phasenplan für die Umsetzung

Jede Phase ist ein abgeschlossener, testbarer Schritt und sollte als **eigener Commit** enden. Die Reihenfolge
ist bindend: Sicherheit vor Fachlichkeit, damit keine ungeschützten Endpunkte entstehen.

### Phase 0 — Fundament

**Ziel:** Repo-Skelett auf die Zielarchitektur bringen.

- Paketstruktur nach Abschnitt 3 der Spec anlegen; bestehende ungeschützte User-CRUD entfernen bzw. migrieren.
- Abhängigkeiten ergänzen: `spring-boot-starter-security`, `spring-boot-starter-validation`,
  `io.jsonwebtoken:jjwt-api/impl/jackson` (0.12.x), `flyway-core`, `flyway-database-postgresql`,
  `org.testcontainers:postgresql/junit-jupiter`, `spring-security-test`, `com.tngtech.archunit:archunit-junit5`,
  `jacoco-maven-plugin`.
- `application.yml` + Profile `dev`/`docker`/`test`, `AppProperties`, `.env.example`.
- `GlobalExceptionHandler`, `ApiError`, `PageResponse`.

**Abnahme:** `./mvnw test` grün, `docker compose up --build` startet, `/api/health` antwortet.

### Phase 1 — Datenmodell und Migrationen

- `V1`–`V3` schreiben (Abschnitt 2 der Spec), JPA-Entities dazu, `ddl-auto=validate`.
- Repositories mit den benötigten Query-Methoden.

**Abnahme:** Integrationstest mit Testcontainers startet den Kontext gegen ein frisches Postgres, Flyway läuft
fehlerfrei durch, alle Entities validieren.

### Phase 2 — RBAC-Katalog und Seeding

- `PermissionCode`-Enum, `RoleCode`-Enum, `RoleCatalog` mit der Matrix aus Abschnitt 1.3 als Java-Konstante.
- `V4__seed_rbac.sql` mit identischem Inhalt (Rollen, Berechtigungen, `role_permission`, `role_grant`).
- `AppInitializerService`: legt den Initial-Admin an, sofern kein aktiver `ADMIN` existiert.
- Konsistenztest `RoleCatalogConsistencyTest`: Java-Katalog == DB-Seed.

**Abnahme:** Nach dem Start enthält die DB 6 Rollen, den vollständigen Berechtigungskatalog und genau die
Zuordnungen der Matrix; der Konsistenztest ist grün.

### Phase 3 — Authentifizierung

- `JwtService`, `JwtAuthFilter`, `TokenBlacklistService`, `TokenVersionService`, `CampusUserDetails(Service)`,
  `SecurityConfig`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`.
- `AuthController`: `login`, `refresh` (mit Rotation), `logout`, `me`, `me` (PUT), `me/password`.

**Abnahme:** Integrationstest: Login liefert Tokens → geschützter Endpunkt mit Token = 200, ohne Token = 401,
nach Logout = 401. Refresh-Token wird als Access-Token abgelehnt.

### Phase 4 — Autorisierung, Nutzer- und Rollenverwaltung *(Kernstück der Arbeit)*

- `UserService`, `RoleAssignmentService` inkl. aller Invarianten INV-1…INV-6.
- `UserController`, `RoleController` mit vollständigen `@PreAuthorize`-Annotationen.
- `GET /api/roles/matrix`, `GET /api/users/me/grantable-roles`.
- `EndpointSecurityTest` (Reflection über alle Controller).

**Abnahme:** Für jede Zelle der Berechtigungsmatrix existiert ein Test, der den Zugriff mit passender und mit
fehlender Berechtigung prüft (parametrisierter Test über `RoleCatalog`). Alle Invarianten haben einen eigenen Test.

### Phase 5 — Audit-Log

- `AuditService`, `@Audited`, `AuditAspect`, `AuditController` mit gefilterter Sicht für `AUDIT_READ_CONTENT`.
- `ACCESS_DENIED`-Protokollierung im `RestAccessDeniedHandler`, `LOGIN_FAILED` im `AuthService`.

**Abnahme:** Rollenzuweisung erzeugt einen `ROLE_ASSIGNED`-Eintrag mit korrektem `before`/`after`; ein
abgewiesener Zugriff erzeugt `ACCESS_DENIED`; `password_hash` erscheint in keinem Eintrag.

### Phase 6 — Content: POI, Gebäude, Beratungszeiten, Medien

- Entities/Services/Controller je Ressource, `PoiStatusService` mit dem Statusautomaten,
  `PoiSecurity`/`ConsultationSecurity` für Ownership.
- `PublicContentController` mit reduzierten DTOs.
- `V5__seed_demo_data.sql`: 5 Gebäude, 12 POIs in verschiedenen Status, 4 Beratungsangebote, je ein Demo-Nutzer
  pro Rolle (`demo_admin`, `demo_leitung`, `demo_mitarbeit`, `demo_personal`, `demo_devops`).

**Abnahme:** Der vollständige Freigabe-Workflow ist per Integrationstest durchlaufen; `PROJEKTMITARBEITER` kann
einen fremden POI nachweislich nicht ändern; `/api/public/pois` liefert ausschließlich veröffentlichte POIs.

### Phase 7 — Frontend

- `AuthContext`, `client.js` mit Refresh-Retry, `ProtectedRoute`, `RequirePermission`, `Can`.
- `AppShell` mit permission-gefilterter Sidebar, alle Seiten aus Abschnitt 6 der Spec.
- `RoleAssignPanel` und `PermissionMatrix` als zentrale Demonstrationskomponenten.

**Abnahme:** Anmeldung mit jedem Demo-Nutzer zeigt ein unterschiedliches Menü; ein direkter Aufruf einer
gesperrten Route zeigt die 403-Seite; die Rollenzuweisung funktioniert Ende-zu-Ende.

### Phase 8 — Härtung, Dokumentation, Evaluation

- Rate Limiting für `/api/auth/login` (z. B. 10 Versuche / 15 min pro Benutzername, Zähler in Redis).
- Swagger-Beschreibungen und Beispiele für alle Endpunkte.
- `docs/DECISIONS.md`, `docs/ROLE_MODEL.md`, `docs/EVALUATION.md`, README aktualisieren.
- JaCoCo-Report erzeugen, Screenshots für Kapitel 4 aufnehmen.

---

## 3. Übergabe an Claude Code

### 3.1 Vorbereitung im Repo

Beide Dokumente nach `docs/spec/` legen und eine `CLAUDE.md` im Wurzelverzeichnis anlegen:

```markdown
# Projektkontext

Administrationsinfrastruktur für den 3D Campus Explorer der TU Darmstadt (Bachelorarbeit).
Verbindliche Spezifikation: `docs/spec/01_ARCHITEKTUR_SPEC.md` und `docs/spec/02_IMPLEMENTIERUNGSPLAN.md`.
Vor jeder Änderung die relevanten Abschnitte lesen.

## Feste Regeln
- Rollennamen sind unveränderlich: ADMIN, PROJEKTLEITER, PROJEKTMITARBEITER, PERSONAL, MAINTENANCE_DEV, EXTERNE_PERSON.
- Autorisierung ausschließlich über Authorities (Permission-Codes), nicht über hasRole(...) mit Rollennamen.
  Ausnahme: keine.
- Jede Controller-Methode trägt @PreAuthorize; Ausnahmen nur in der Allowlist von EndpointSecurityTest.
- Schichten: Controller -> Service -> Repository. Controller injizieren keine Repositories.
- Entities verlassen nie die Service-Schicht; nach außen nur DTOs.
- Schemaänderungen ausschließlich über neue Flyway-Migrationen, bestehende Migrationen nie ändern.
- Sprache: Code, Bezeichner und Commit-Messages englisch; Log- und Fehlermeldungen für Endnutzer deutsch.
- Keine neuen Abhängigkeiten ohne Eintrag in docs/DECISIONS.md.

## Befehle
- Backend: `cd backend && ./mvnw test` | `./mvnw spring-boot:run`
- Frontend: `cd frontend && npm test` | `npm run dev`
- Gesamt: `docker compose up --build`

## Definition of Done je Phase
Tests grün, `docker compose up --build` läuft, betroffene Doku aktualisiert, ein Commit pro Phase.
```

### 3.2 Startprompt

> Lies `docs/spec/01_ARCHITEKTUR_SPEC.md` und `docs/spec/02_IMPLEMENTIERUNGSPLAN.md` vollständig.
> Implementiere **ausschließlich Phase 0 und Phase 1** aus dem Implementierungsplan.
> Halte dich exakt an Paketstruktur, Tabellen- und Spaltennamen. Schreibe die Tests der jeweiligen
> Abnahmekriterien mit. Wenn eine Angabe fehlt oder widersprüchlich ist, halte an, liste die offenen Punkte auf
> und schlage jeweils eine Lösung vor, statt zu raten. Am Ende: Zusammenfassung der erstellten Dateien und
> Ergebnis von `./mvnw test`.

Danach je Phase ein Folgeprompt nach demselben Muster. **Nicht mehrere Phasen auf einmal beauftragen** — die
Kontextmenge wird sonst zu groß und die Testqualität sinkt spürbar.

### 3.3 Prüfschritt nach jeder Phase

1. `./mvnw test` und `npm test` lokal ausführen.
2. `git diff --stat` ansehen: Wurden nur die Dateien der Phase angefasst?
3. Stichprobe: Hat jeder neue Controller-Endpunkt eine `@PreAuthorize`-Annotation?
4. Neue Entscheidungen in `docs/DECISIONS.md` nachtragen — daraus entsteht später Kapitel 4.3
   ("Entwurfsentscheidungen") fast von selbst.

---

## 4. Evaluationsszenarien (Kapitel 5)

Stefan hat den anforderungs- und szenariobasierten Funktionstest als ausreichend bestätigt. Die folgenden
Szenarien decken den Anforderungskatalog vollständig ab; jedes ist als automatisierter Integrationstest **und**
als manuell durchführbares Protokoll (`docs/EVALUATION.md`) umzusetzen.

| ID | Szenario | Rolle | Erwartetes Ergebnis | Anforderungen |
|---|---|---|---|---|
| S-01 | Anmeldung mit gültigen Zugangsdaten | alle | 200, Token-Paar, `me` liefert korrekte Rollen und Berechtigungen | FA-01, FA-04 |
| S-02 | Anmeldung mit falschem Passwort und mit gesperrtem Konto | — | 401 bzw. 403 `ACCOUNT_DISABLED`, `LOGIN_FAILED` im Audit-Log | FA-01, FA-15 |
| S-03 | Zugriff auf `/api/users` ohne Token | — | 401, kein Datenleck im Fehlerobjekt | FA-14, NFA-07 |
| S-04 | Logout, danach erneuter Zugriff mit demselben Token | PROJEKTLEITER | 401, Token in Redis-Blacklist | FA-03 |
| S-05 | Projektleitung legt ein Konto an und weist die Rolle `PROJEKTMITARBEITER` zu | PROJEKTLEITER | 201/200, Rolle wirksam, Audit-Einträge `USER_CREATED` + `ROLE_ASSIGNED` | FA-07, FA-09, FA-15 |
| S-06 | Projektleitung versucht, die Rolle `ADMIN` zu vergeben | PROJEKTLEITER | 403 `ROLE_NOT_GRANTABLE`, `ACCESS_DENIED` im Audit-Log | FA-08 |
| S-07 | Entzug der letzten `ADMIN`-Rolle im System | ADMIN | 409 `LAST_ADMIN_PROTECTED` | INV-1 |
| S-08 | Rollenentzug bei angemeldetem Nutzer, danach Zugriff mit altem Token | ADMIN → PROJEKTMITARBEITER | 401 `TOKEN_STALE`; nach Refresh reduzierte Berechtigungen | FA-19 |
| S-09 | Projektmitarbeiter erstellt POI und reicht ihn zur Prüfung ein | PROJEKTMITARBEITER | Status `DRAFT` → `IN_REVIEW` | FA-10, FA-11 |
| S-10 | Projektmitarbeiter versucht, seinen POI selbst zu veröffentlichen | PROJEKTMITARBEITER | 403 (fehlende `POI_PUBLISH`) | FA-11 |
| S-11 | Projektleitung weist den POI mit Begründung zurück, Bearbeiter korrigiert, Projektleitung gibt frei | PROJEKTLEITER + PROJEKTMITARBEITER | `IN_REVIEW` → `DRAFT` → `IN_REVIEW` → `PUBLISHED`, `published_by` gesetzt | FA-10, FA-11 |
| S-12 | Projektmitarbeiter versucht, einen fremden POI zu ändern | PROJEKTMITARBEITER | 403 (Ownership-Prüfung) | FA-12 |
| S-13 | Verwaltungspersonal pflegt eigene Beratungszeiten und versucht, einen POI zu ändern | PERSONAL | Beratungszeit 200, POI-Änderung 403 | FA-13 |
| S-14 | Betriebspersonal ruft Health und Audit-Log ab, versucht Nutzerliste abzurufen | MAINTENANCE_DEV | 200 / 200 / 403 | FA-14, Least Privilege |
| S-15 | Anonymer Abruf von `/api/public/pois` | EXTERNE_PERSON | 200, ausschließlich veröffentlichte POIs, keine internen Felder | FA-17 |
| S-16 | Anonymer Abruf von `/api/pois` | EXTERNE_PERSON | 401 | FA-14 |
| S-17 | Anmeldung als jede der fünf Rollen im Frontend | alle | Menü und Aktionsschaltflächen entsprechen exakt der Berechtigungsmatrix | FA-16 |
| S-18 | Direkter Aufruf einer gesperrten Frontend-Route über die URL | PERSONAL | 403-Seite; der zugehörige API-Aufruf wird ebenfalls mit 403 abgewiesen | FA-14, FA-16 |
| S-19 | Automatisierte Prüfung aller Endpunkte auf Autorisierungsannotation | — | `EndpointSecurityTest` grün | FA-14 |
| S-20 | Abruf der Berechtigungsmatrix über `GET /api/roles/matrix` | ADMIN | Antwort entspricht Tabelle 1.3 der Spec | FA-20, NFA-08 |

**Ergänzender qualitativer Teil (optional, Aufwand ca. 1 Woche):** 3–5 Personen aus dem Projektumfeld
(Projektleitung, ein bis zwei Content-Beitragende, eine Person aus dem Verwaltungspersonal) bearbeiten je zwei
rollentypische Aufgaben im Prototyp (Account anlegen + Rolle zuweisen; POI erstellen + einreichen; Inhalt
freigeben). Erhebung: kurze Aufgabenprotokolle (Erfolg/Abbruch, Dauer) plus SUS-Fragebogen (10 Items) und drei
offene Fragen. Das beantwortet Stefans *"it depends"* mit einem konkreten, realistischen Umfang — vorher mit
Katharina abstimmen.

**Zeitplanung Evaluation:** ca. 1 Woche für die szenariobasierten Funktionstests inkl. Protokollierung, plus
1 Woche, falls der qualitative Teil mit Probanden durchgeführt wird.

---

## 5. Traceability-Matrix (Auszug — in Kapitel 5 vollständig führen)

| Anforderung | Umsetzung (Code) | Nachweis (Test/Szenario) |
|---|---|---|
| FA-01 | `AuthController.login`, `JwtService` | S-01, `AuthIntegrationTest` |
| FA-03 | `TokenBlacklistService`, `JwtAuthFilter` | S-04 |
| FA-04/FA-05 | `RoleCatalog`, `V4__seed_rbac.sql` | `RoleCatalogConsistencyTest`, S-20 |
| FA-07/FA-08 | `RoleAssignmentService`, `role_grant` | S-05, S-06, `RoleAssignmentServiceTest` |
| FA-10/FA-11 | `PoiStatusService`, `POI_PUBLISH` | S-09 – S-11 |
| FA-12 | `PoiSecurity.canEdit` | S-12 |
| FA-14 | `@PreAuthorize`, `SecurityConfig` | S-03, S-16, S-19 |
| FA-15 | `AuditAspect`, `RestAccessDeniedHandler` | S-02, S-05, S-06 |
| FA-16 | `Can`, `RequirePermission`, Sidebar-Filter | S-17, S-18 |
| FA-17 | `PublicContentController` | S-15 |
| FA-19 | `token_version`, `TokenVersionService` | S-08 |

---

## 6. Rückwirkungen auf die Ausarbeitung

Damit Text und Implementierung übereinstimmen, sind folgende Stellen anzupassen:

1. **Kap. 3.2.3:** statt „zehn Kerntabellen“ → zwölf; `role_grant` und `media_asset` ergänzen; `poi.status`
   (Freigabe-Workflow) neben `is_published` beschreiben.
2. **Kap. 3.3.1:** Rollenbeschreibungen um den Projektkontext ergänzen (wer ist real gemeint: Projektleitung/
   Eileen, Story++, Fachgebiete, Systembetrieb) — das beantwortet Stefans Frage sichtbar im Text.
   Für `PROJEKTLEITER` die *eingeschränkte* Benutzerverwaltung präzisieren (Vergabemenge).
3. **Kap. 3.3.2:** Matrix durch die Version aus Abschnitt 1.3 der Spec ersetzen; die Abbildungen 3.3/3.4 in
   einheitlicher Größe und lesbarer Auflösung neu erzeugen (Stefans zweiter Kritikpunkt: verpixelte Abbildungen).
4. **Kap. 3.3.3:** ergänzen, dass die Durchsetzung über **Authorities (Berechtigungen)** und nicht über
   Rollennamen erfolgt, sowie den `token_version`-Mechanismus beschreiben.
5. **Kap. 3.2 allgemein:** Stefans erster Kritikpunkt — Detailarchitektur (Referenzarchitektur, Filterketten,
   Klassendiagramme) nach **Kapitel 4** verschieben. In Kapitel 3 bleibt ein grobes Blockdiagramm mit
   Datenbank, Backend, Frontend mit Editor und öffentlicher Schnittstelle.
6. **Kap. 3.4.1:** Zugriff auf den Editor an die Berechtigungen `POI_CREATE`/`POI_UPDATE_*` binden statt an
   Rollennamen; den Freigabeschritt ergänzen.
7. **Kap. 6.3 (Ausblick):** Abschnitt 9 der Spec übernehmen — insbesondere die individuelle Rollenerstellung,
   die Stefan explizit dort verortet sehen möchte.
