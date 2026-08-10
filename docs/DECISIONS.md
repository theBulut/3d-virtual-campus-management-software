# Entwurfsentscheidungen

Kurzform-ADRs. Jede Entscheidung nennt Kontext, Entscheidung und Begründung. Abweichungen von
`docs/spec/01_ARCHITEKTUR_SPEC.md` sind als solche markiert — die Spec hat Vorrang, jede Abweichung
braucht einen Eintrag hier.

---

## D-1 — Spring Boot 4.1.0 statt Spring Boot 3

**Kontext.** Die Spec nennt im Kopf „Spring Boot 3 (bereits vorhanden)". Das Skelett lief tatsächlich schon
auf Spring Boot 4.1.0 (Spring Framework 7.0.8, Spring Security 7.1.0, Hibernate 7.4.1, Jackson 3.1.4,
Flyway 12.4.0, Testcontainers 2.0.5).

**Entscheidung.** Bei 4.1.0 bleiben. Spec-Kopf und Kapitel 3.2 der Arbeit sind auf „Spring Boot 4.1" zu
korrigieren.

**Begründung.** Ein Downgrade hätte den funktionierenden Skelett-Build ohne fachlichen Gewinn angefasst.
Die Folgekosten sind bekannt und in D-4, D-6 und D-7 abgearbeitet.

**Abweichung von der Spec:** ja (Versionsangabe im Kopf).

---

## D-2 — `poi.status` als `VARCHAR(20)` mit `CHECK` statt PostgreSQL-Enum

**Kontext.** Spec Abschnitt 2.2 legt `CREATE TYPE content_status AS ENUM (...)` an. Der Typ funktioniert
(gegen PostgreSQL 17 verifiziert), aber die Abbildung eines native Enums auf JPA bei
`ddl-auto=validate` ist die fragilste Stelle im Schema: sie braucht `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
und stimmt nur dann mit Hibernates Erwartung überein, wenn der Typname exakt passt.

**Entscheidung.** `status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_REVIEW',
'PUBLISHED','ARCHIVED'))` plus `@Enumerated(EnumType.STRING)`.

**Begründung.** Der Constraint bleibt in der Datenbank und ist damit für die Arbeit genauso zitierbar wie
ein Enum, ohne Typrisiko bei `validate`. Die Spec-Regel „einfachste Lösung, die die Anforderung erfüllt"
zeigt in dieselbe Richtung. Die Generated Column `is_published` funktioniert über `VARCHAR` genauso
(verifiziert).

**Abweichung von der Spec:** ja (Abschnitt 2.2).

---

## D-3 — Zweiter Zähler `admin_user.refresh_version`

**Kontext.** Spec Abschnitt 1.4 erhöht `token_version` bei jeder Rollenänderung; Abschnitt 4.1 legt den
`ver`-Claim aber auch in den Refresh-Token. Damit wäre nach einem Rollenwechsel auch der Refresh-Token
stale und Szenario S-08 („nach Refresh reduzierte Berechtigungen") nicht erfüllbar.

**Entscheidung.** `admin_user` erhält zusätzlich `refresh_version`. `token_version` steigt bei
Rollenänderung, Sperrung und Passwortwechsel und invalidiert Access-Tokens; `refresh_version` steigt nur
bei Passwortwechsel, Passwort-Reset und Sperrung und invalidiert Refresh-Tokens.

**Begründung.** S-08 läuft wie beschrieben, und ein gestohlener Refresh-Token stirbt trotzdem beim
Passwortwechsel. Die Alternative (Refresh prüft `ver` nicht) hätte genau diese Lücke geöffnet.

**Abweichung von der Spec:** ja (Abschnitt 2.1 und 4.2, je eine Spalte und ein Claim mehr).

---

## D-4 — `jackson-databind` 2.x explizit als Abhängigkeit

**Kontext.** JJWT steht bei 0.12.6 (aktuellste Version) und ist gegen Jackson 2
(`com.fasterxml.jackson.*`) gebaut. Spring Boot 4 liefert per Starter Jackson 3 (`tools.jackson.*`) aus,
verwaltet aber beide BOMs.

**Entscheidung.** `com.fasterxml.jackson.core:jackson-databind` ohne Versionsangabe aufnehmen (wird über
Boots `jackson-2-bom` auf 2.21.4 gemanagt), Scope `runtime`.

**Begründung.** `jjwt-jackson` findet damit seinen Serializer, ohne dass die HTTP-Serialisierung auf
Jackson 2 wechselt. Ein Test in `GlobalExceptionHandlerTest` sichert ab, dass Zeitstempel weiterhin als
ISO-8601 und nicht als Epoch-Zahl serialisiert werden. Alternative wäre `jjwt-gson` gewesen — das hätte
Gson als weitere Abhängigkeit gebracht, ohne Vorteil.

---

## D-5 — Generiertes JWT-Secret im docker-Profil statt Pflicht-Variable

**Kontext.** Drei Anforderungen stehen gegeneinander: `JWT_SECRET` kommt aus der Umgebung und es liegen
keine Secrets im Repo (CLAUDE.md), Fail-Fast beim Default-Secret außerhalb `dev` (Spec 4.1), und
`docker compose up --build` startet ohne manuelle Schritte (NFA-03).

**Entscheidung.** `application-docker.yml` setzt
`campus.jwt.secret: ${JWT_SECRET:${random.value}${random.value}}`. Ohne gesetztes `JWT_SECRET` entsteht
beim Start ein zufälliges 64-Zeichen-Secret; `JwtSecretValidator` protokolliert dann eine Warnung, dass
Tokens einen Neustart nicht überleben. `.env` ist optional (`env_file: required: false`).

**Begründung.** Alle drei Anforderungen bleiben erfüllt: kein Secret im Repo, kein bekanntes
Default-Secret außerhalb `dev`, kein manueller Schritt. Ein im Compose-File hinterlegtes Default-Secret
hätte die erste Regel gebrochen, eine Pflicht-Variable die dritte.

---

## D-6 — Testcontainers als einziger Datenbankpfad, H2 entfällt

**Kontext.** Das Skelett testete gegen H2 im PostgreSQL-Modus mit `ddl-auto=create-drop`.

**Entscheidung.** H2 entfernt. Integrationstests laufen über Testcontainers gegen PostgreSQL 17 und
Redis 7 (`AbstractIntegrationTest`). Diese Tests tragen `@Tag("it")` und heißen `*IT`; die
Surefire-Konfiguration nimmt das Muster `**/*IT.java` auf, weil es sonst nur Failsafe kennt. Ohne
Docker-Daemon: `./mvnw test -DexcludedGroups=it`.

**Begründung.** Die Migrationen sind PostgreSQL-spezifisch (`BIGSERIAL`, `JSONB`, `TIMESTAMPTZ`, Generated
Columns). Ein H2-Pfad hätte ein anderes Schema getestet als das ausgelieferte, und `create-drop`
widerspricht Flyway plus `validate` (NFA-04).

Die Container starten in einem statischen Initialisierer statt über `@Testcontainers`/`@Container`: JUnit
würde sie nach jeder Testklasse stoppen, während Spring den Context über Klassen hinweg cacht. Das
Singleton-Muster hält beides synchron und spart den Neustart pro Testklasse.

---

## D-7 — `spring-boot-flyway` als Abhängigkeit

**Kontext.** Der Phase-0-Abhängigkeitsliste der Spec folgend waren nur `flyway-core` und
`flyway-database-postgresql` eingetragen. Flyway lief damit nicht — kein einziger Log-Eintrag, keine
`flyway_schema_history`.

**Entscheidung.** `org.springframework.boot:spring-boot-flyway` ergänzen.

**Begründung.** Spring Boot 4 hat die Autokonfigurationen in Technologie-Module ausgelagert;
`FlywayAutoConfiguration` steckt nicht mehr in `spring-boot-autoconfigure`. `flyway-core` allein bringt
nur die Bibliothek, nicht die Spring-Integration. Betrifft dieselbe Modul-Aufteilung wie die Test-
Annotationen (`org.springframework.boot.webmvc.test.autoconfigure` statt
`org.springframework.boot.test.autoconfigure.web.servlet`).

---

## D-8 — Demo-Daten als repeatable Migration in eigener Location

**Kontext.** Spec Abschnitt 2 schreibt `V5__seed_demo_data.sql` „nur Profil demo" vor, Abschnitt 7.1 kennt
`CAMPUS_SEED_DEMO`. Flyway wendet jede Migration in seinen Locations aber unabhängig vom Spring-Profil an.

**Entscheidung.** Demo-Seed wird `R__seed_demo_data.sql` in `classpath:db/demo` (Phase 6). Nur das
`dev`-Profil nimmt die Location auf (`spring.flyway.locations`).

**Begründung.** Repeatable Migrationen laufen nach allen versionierten und kollidieren nicht mit späteren
`V6+`; ein `V5` in einer nur-dev-Location hätte Out-of-order-Konflikte erzeugt. Weil die Location außerhalb
`dev` nie geladen wird, sind die fest verdrahteten BCrypt-Hashes der Demo-Konten vertretbar.

**Abweichung von der Spec:** ja (Dateiname und Ablageort).

---

## D-9 — `ON DELETE SET NULL` auf allen Rückverweisen nach `admin_user`

**Kontext.** Die DDL in Spec Abschnitt 2 lässt die `ON DELETE`-Klausel auf `user_role.assigned_by`,
`admin_user.created_by`, `poi.created_by/published_by/assigned_to`, `building.created_by`,
`consultation.created_by/responsible_user_id`, `media_asset.uploaded_by` und `audit_log.actor_id` weg.
Gegen PostgreSQL 17 verifiziert: `DELETE` auf einem Konto, das je eine Rolle vergeben oder Inhalte erzeugt
hat, scheitert mit SQLState 23503.

**Entscheidung.** `ON DELETE SET NULL` auf allen diesen Verweisen. `ON DELETE CASCADE` bleibt auf
`user_role.user_id`, `role_permission`, `role_grant` und `consultation_event.consultation_id`.

**Begründung.** `DELETE /api/users/{id}` (Spec 5.2) wäre sonst für praktisch jedes Konto ein 500er.
`audit_log.actor_username` ist laut Spec genau für diesen Fall denormalisiert. Nachweis in Phase 1 durch
`UserDeletionConstraintIT`.

**Abweichung von der Spec:** ja (Abschnitt 2.1–2.3), als Fehlerkorrektur.

---

## D-10 — `AccessDeniedException` wird im `GlobalExceptionHandler` behandelt

**Kontext.** Spec Abschnitt 4.3 sieht einen `RestAccessDeniedHandler` für 403-Antworten vor. Seit Spring
Security 6.3 wirft `@PreAuthorize` eine `AuthorizationDeniedException` innerhalb des DispatcherServlets;
ein `@ControllerAdvice` fängt sie ab, bevor der `ExceptionTranslationFilter` sie sieht.

**Entscheidung.** `GlobalExceptionHandler` behandelt `AccessDeniedException` explizit (403, Code
`ACCESS_DENIED`). Der `RestAccessDeniedHandler` bleibt für Denials auf Filterebene. Der
`ACCESS_DENIED`-Auditeintrag für Method-Security entsteht in Phase 5 an dieser Stelle, nicht im Handler.

**Begründung.** Ohne diesen Handler hätte der `Exception`-Catch-all jeden 403 in einen 500 verwandelt und
S-03, S-06 und S-16 gebrochen. Zusätzlich mappt der Catch-all Spring-MVC-eigene Exceptions über ihr
`ErrorResponse`-Interface auf den korrekten Status, statt sie als 500 zu maskieren.

---

## D-11 — ModelMapper entfällt

**Kontext.** Das Skelett nutzte ModelMapper für Entity-zu-DTO-Abbildung.

**Entscheidung.** Abhängigkeit entfernt, Mapping wird handgeschrieben.

**Begründung.** Die DTOs der Spec sind keine Feld-für-Feld-Kopien (Rollen als `List<String>`,
Permission-Aggregation, maskierte Felder), ModelMappers implizites Matching funktioniert nicht mit
Java-`record`s, und stille Fehlzuordnungen in DTOs, die Berechtigungen transportieren, sind ein
Sicherheitsrisiko.

---

## D-12 — Actuator entfällt

**Kontext.** Das Skelett hatte `spring-boot-starter-actuator` mit `health,info` exponiert. Die Spec nennt
Actuator nirgends und definiert `/api/health` und `/api/system/info` selbst.

**Entscheidung.** Abhängigkeit entfernt.

**Begründung.** `/actuator/**` wäre eine authentifizierte Endpunktfläche, die `EndpointSecurityTest`
(reflektiert über `@RestController`) nie sieht. Ohne Actuator bleibt die Aussage „kein Endpunkt ist
unbeabsichtigt offen" (FA-14) ohne Sonderfall prüfbar.

---

## D-14 — Ressourcen-Vokabular um `PROFILE` und `DATA` erweitert

**Kontext.** Spec Abschnitt 2.1 nennt als `permission.resource` die Werte `USER, ROLE, POI, BUILDING,
CONSULTATION, MEDIA, AUDIT, SYSTEM`. `PROFILE_UPDATE_OWN` und `DATA_EXPORT` passen in keinen davon.

**Entscheidung.** `PROFILE` und `DATA` ergänzen; das Vokabular steht als `CHECK`-Constraint
`permission_resource_known` in `V1`.

**Begründung.** `GET /api/permissions` gruppiert nach `resource` — ohne die beiden Werte hätten zwei
Berechtigungen keine sinnvolle Gruppe. Der Constraint macht das Vokabular in der Datenbank nachweisbar,
statt es nur als Kommentar zu führen.

**Abweichung von der Spec:** ja (Abschnitt 2.1).

---

## D-15 — `UserRole` und `RoleGrant` implementieren `Persistable`

**Kontext.** Beide Entities haben einen zusammengesetzten, **zugewiesenen** Schlüssel (`@EmbeddedId`).
Spring Data entscheidet in `SimpleJpaRepository.save()` anhand von `isNew()`, ob `persist()` oder
`merge()` läuft, und hält eine nicht-null ID für eine bestehende Zeile. Eine frische Rollenzuweisung
lief damit in `merge()` und scheiterte mit `TransientPropertyValueException`.

**Entscheidung.** Beide implementieren `Persistable<…>` mit einem `@Transient`-Flag, das
`@PostLoad`/`@PostPersist` zurücksetzt.

**Begründung.** Betrifft nicht nur Tests: `RoleAssignmentService` in Phase 4 legt `user_role`-Zeilen über
`save()` an. Die Alternative wäre gewesen, überall `EntityManager.persist()` zu verwenden und die
Repository-Abstraktion zu umgehen.

---

## D-16 — `poi.building_id` bleibt ohne `ON DELETE`-Klausel

**Kontext.** D-9 setzt `ON DELETE SET NULL` auf alle Verweise nach `admin_user`. Für `building_id` in
`poi` und `consultation` gilt das bewusst **nicht**.

**Entscheidung.** Keine `ON DELETE`-Klausel, also `NO ACTION`.

**Begründung.** Spec Abschnitt 5.4 verlangt beim Löschen eines Gebäudes mit referenzierenden POIs eine
`409`-Antwort. Ein `SET NULL` würde die POIs still verwaisen lassen, ein `CASCADE` sie mitlöschen —
beides widerspricht der Anforderung. Der Service prüft über `PoiRepository.countByBuildingId` vor und
die Datenbank ist die zweite Verteidigungslinie; `PoiRepositoryIT` belegt beides.

---

## D-17 — `RoleCode` als eigene Datei neben `RoleCatalog`

**Kontext.** Spec Abschnitt 3 listet im Paketbaum `RoleCatalog.java` mit dem Kommentar „Enum RoleCode +
statische Matrix"; Abschnitt 2 des Implementierungsplans nennt `PermissionCode`-Enum, `RoleCode`-Enum und
`RoleCatalog` als drei Artefakte.

**Entscheidung.** Drei Dateien: `PermissionCode.java`, `RoleCode.java`, `RoleCatalog.java`.

**Begründung.** Ein verschachteltes `RoleCatalog.RoleCode` würde an jeder Aufrufstelle mitgeschleppt,
obwohl der Rollenname das meistgenutzte Symbol im ganzen Projekt ist. Inhaltlich ändert sich nichts: das
Paket `rbac` enthält dieselben drei Konzepte wie in der Spec.

---

## D-18 — Konsistenztest heißt `RoleCatalogConsistencyIT`

**Kontext.** Der Implementierungsplan nennt den Test `RoleCatalogConsistencyTest`. Er vergleicht den
Java-Katalog mit dem Datenbank-Seed und braucht dafür zwingend eine Datenbank.

**Entscheidung.** Der Test heißt `RoleCatalogConsistencyIT` und trägt `@Tag("it")`.

**Begründung.** Die Namenskonvention aus D-6 unterscheidet Docker-freie Tests (`*Test`) von solchen mit
Testcontainers (`*IT`). Ein `*Test`, der ohne Docker scheitert, würde `./mvnw test -DexcludedGroups=it`
unbrauchbar machen. Die reine Katalogprüfung ohne Datenbank liegt zusätzlich in `RoleCatalogTest`.

---

## D-19 — Logout nimmt das Refresh-Token optional im Rumpf entgegen

**Kontext.** Spec Abschnitt 5.1 verlangt beim Logout das Blacklisten von Access- **und** Refresh-`jti`.
Die `jti` des Refresh-Tokens steht aber nicht im Access-Token, und der Server führt keine Liste
ausgegebener Tokens.

**Entscheidung.** `POST /api/auth/logout` akzeptiert einen optionalen Rumpf `{refreshToken}`. Das
Access-Token wird immer entwertet, das Refresh-Token nur, wenn es mitgeschickt wird und demselben Konto
gehört.

**Begründung.** Ohne den Rumpf wäre die Anforderung nicht erfüllbar. Der Fremdbesitz-Check verhindert,
dass jemand mit einem gültigen Access-Token fremde Refresh-Tokens entwertet. Ein Logout ohne
mitgeschicktes Refresh-Token protokolliert eine Debug-Meldung; die Oberfläche schickt es immer mit.

---

## D-24 — Zwei Abmeldefunktionen: diese Sitzung und alle Sitzungen

**Kontext.** Das Abmelden nach D-19 ist Best-Effort: der Server kann nicht erzwingen, dass der Client
sein Refresh-Token zurückgibt. Wer es weglässt, hinterlässt ein bis zu sieben Tage gültiges
Refresh-Token — also genau in dem Fall problematisch, den ein Logout schließen soll.

**Entscheidung.** Zwei getrennte Endpunkte:

| Endpunkt | Wirkung | Mechanismus |
|---|---|---|
| `POST /api/auth/logout` | beendet **diese** Sitzung | Redis-Blacklist auf die `jti` (Spec 4.2) |
| `POST /api/auth/logout-all` | beendet **alle** Sitzungen des Kontos | `token_version++` und `refresh_version++` |

`logout-all` braucht keinen Rumpf: die Identität kommt aus dem signierten Token, und die Zähler wirken
auf jedes ausgegebene Token, auch auf solche, deren `jti` der Server nie gesehen hat.

**Begründung.** Die Blacklist kann nur widerrufen, was der Client vorlegt — für „von überall abmelden"
ist sie das falsche Werkzeug. Die Versionszähler aus D-3 leisten genau das, ohne Mitwirkung des Clients.
Beides als *ein* Endpunkt mit Schalter zu bauen wäre schlechter gewesen: die beiden Fälle haben
unterschiedliche Nebenwirkungen (ein Gerät gegen alle Geräte), und getrennte Pfade sind in Swagger und
im `EndpointSecurityTest` eindeutig.

Damit erweitert sich die Liste der Ereignisse aus D-3, die die Zähler erhöhen, um „Abmelden von überall".
`TokenVersionService.invalidate` muss dabei mitlaufen, sonst würde der Fünf-Minuten-Cache die alten
Werte weiterliefern.

**Abweichung von der Spec:** ja, `logout-all` ist in Abschnitt 5.1 nicht vorgesehen (Ergänzung).

---

## D-25 — Konten bekommen ein erzeugtes Passwort, keines aus dem Request

**Kontext.** Spec Abschnitt 5.2 beschreibt bei `POST /api/users` nur `roles[]` und schweigt zum Passwort.

**Entscheidung.** Der Server erzeugt ein temporäres Passwort, gibt es einmalig in der Antwort zurück und
setzt `must_change_password`. Der Request enthält kein Passwortfeld.

**Begründung.** Dasselbe Verfahren wie beim Passwort-Reset, also ein Weg statt zwei. Ein Passwort im
Request-Rumpf würde in Logs und Browser-Verlauf landen, und der Prototyp verschickt bewusst keine Mails
(Spec Abschnitt 8). Zusammen mit D-21 heißt das: das neue Konto kann sich anmelden und ausschließlich
sein Passwort ändern.

---

## D-26 — INV-2 verbietet jede Selbstbearbeitung, nicht nur die eigene ADMIN-Rolle

**Kontext.** INV-2 nennt wörtlich nur „die eigene `ADMIN`-Rolle nicht selbst entziehen" und sagt nichts
über andere eigene Rollen.

**Entscheidung.** Jede Änderung an den eigenen Rollen sowie Selbstlöschung und Selbstsperrung sind
verboten (`409 SELF_MODIFICATION_FORBIDDEN`).

**Begründung.** Die schärfere Regel ist einfacher zu formulieren, zu testen und zu erklären als eine, die
nach Rollenname unterscheidet. Ein Anwendungsfall für „sich selbst eine Rolle entziehen" existiert nicht;
die Rechteverwaltung ist eine Fremdfunktion.

---

## D-27 — `roles[]` ist beim Anlegen eines Kontos Pflicht

**Kontext.** INV-3 verlangt mindestens eine Rolle pro Konto, Spec Abschnitt 5.2 lässt offen, ob `roles[]`
beim Anlegen angegeben werden muss.

**Entscheidung.** `@NotEmpty`; ohne Rolle antwortet der Endpunkt mit `400` und `fieldErrors.roles`.

**Begründung.** Sonst entstünde ein Konto, das INV-3 sofort verletzt und das wegen
`assertCanManage` niemand mehr bearbeiten könnte — ein rollenloses Konto liegt außerhalb jeder
Vergabemenge.

---

## D-28 — `PATCH /api/users/{id}/status` nimmt `{"active": …}`

**Kontext.** Spec Abschnitt 5.2 nennt den Endpunkt, aber keinen Rumpf.

**Entscheidung.** `{"active": true|false}`, Pflichtfeld. Sperren erhöht beide Versionszähler und beendet
damit alle Sitzungen des Kontos.

**Begründung.** Ein eigener Endpunkt statt eines Feldes in `PUT /{id}`: Sperren ist eine
sicherheitsrelevante Einzelaktion mit eigenen Invarianten (INV-1, INV-2) und eigener Berechtigung
(`USER_ACTIVATE`), und ein normales Stammdaten-Update soll niemanden versehentlich aussperren.

---

## D-29 — INV-1 ist über die API nicht erreichbar und bleibt trotzdem

**Kontext.** Beim Schreiben der Invariantentests zeigte sich: `LAST_ADMIN_PROTECTED` kann durch keine
API-Aufruffolge ausgelöst werden. Wer eine ADMIN-Rolle entziehen, ein Konto löschen oder sperren darf,
muss selbst `ADMIN` sein und ist damit ein zweiter aktiver Administrator. Der Ein-Administrator-Fall
wiederum wird bereits von INV-2 abgefangen, weil niemand sich selbst bearbeiten darf.

**Entscheidung.** Die Prüfung bleibt und wird direkt auf der Wächtermethode
`assertAnotherActiveAdminRemains` getestet, statt über eine konstruierte Aufruffolge.

**Begründung.** Ehrlicher Test statt Schein-Nachweis. Die Prüfung ist zweite Verteidigungslinie für den
Fall, dass INV-2 gelockert wird oder eine spätere Sammeloperation daran vorbeigeht. Für Kapitel 5 der
Arbeit ist das Zusammenspiel der beiden Invarianten das eigentlich interessante Ergebnis.

---

## D-30 — DTOs kennen keine Entities

**Kontext.** `UserResponse.of(AdminUser)` und `PermissionResponse.from(Permission)` waren als statische
Fabrikmethoden im Paket `web/dto` bequem, erzeugten aber eine Abhängigkeit von `web` nach `domain` — die
ArchUnit-Regel zu Spec Abschnitt 3 schlug an.

**Entscheidung.** DTOs sind reine Records. Die Übersetzung Entity zu DTO liegt in der Service-Schicht.

**Begründung.** „Entities verlassen nie die Service-Schicht" ist am klarsten durchgesetzt, wenn keine
Klasse der Web-Schicht eine Entity überhaupt sehen kann. Bei der Gelegenheit fiel ein Fehlalarm derselben
Regel auf: `..domain..` matchte auch `org.springframework.data.domain.Pageable`. Alle ArchUnit-Muster
sind jetzt auf `de.tudarmstadt.campus.admin` verankert.

---

## D-31 — Eigener ObjectMapper für das Audit-Log

**Kontext.** Boot 4 liefert Jackson 3 (`tools.jackson`), für JJWT liegt zusätzlich Jackson 2 im Classpath
(D-4). Welcher `ObjectMapper` injiziert würde, ist damit nicht auf einen Blick klar.

**Entscheidung.** `AuditService` erzeugt seinen eigenen `tools.jackson.databind.ObjectMapper`.

**Begründung.** Die Audit-Darstellung muss stabil bleiben, auch wenn die HTTP-Serialisierung später über
`spring.jackson.*` umkonfiguriert wird — ein Audit-Log, dessen Format sich mit einer API-Einstellung
ändert, wäre schlecht auswertbar. Nebeneffekt: die Mehrdeutigkeit entfällt.

---

## D-32 — `AuditContext` ergänzt den Aspect um fachliches Wissen

**Kontext.** Spec Abschnitt 4.6 verlangt `@Audited` plus `AuditAspect`, gleichzeitig aber `before_state`
und `after_state`. Ein generischer Aspect kann Akteur, Aktion und Erfolg erfassen — nicht aber, dass eine
Rollenliste von `[PERSONAL]` auf `[PERSONAL, PROJEKTMITARBEITER]` gewechselt ist.

**Entscheidung.** Ein `ThreadLocal`-basierter `AuditContext`, in den die auditierte Methode Vorher- und
Nachher-Zustand, eine verfeinerte Aktion und die Ressourcen-ID schreiben kann. Der Aspect leert ihn um
jeden Aufruf herum.

**Begründung.** Ein Mechanismus statt zweier. Die Verfeinerung der Aktion löst zwei reale Fälle: Sperren
und Entsperren teilen sich eine Methode, sind im Katalog aber `USER_DEACTIVATED` und `USER_ACTIVATED`;
und die ID eines neu angelegten Kontos existiert erst nach dem Insert, kann also nicht aus dem
SpEL-Ausdruck der Annotation kommen.

---

## D-33 — Der Aspect umschließt die Transaktion

**Kontext.** Ein Audit-Eintrag muss auch dann bestehen bleiben, wenn die Geschäftstransaktion
zurückgerollt wird — sonst wäre eine abgewiesene Rollenvergabe (S-06) unsichtbar.

**Entscheidung.** `AuditAspect` läuft mit `@Order(HIGHEST_PRECEDENCE)`, also außerhalb der
Transaktionsberatung, und `AuditWriter.write` trägt `REQUIRES_NEW`.

**Begründung.** Bei Erfolg ist die Geschäftstransaktion beim Schreiben bereits committet, bei einem
Fehler bereits zurückgerollt — der Eintrag dokumentiert dann einen Versuch, der nichts verändert hat.

Zwei Fallstricke, die dabei auftraten und in Tests festgehalten sind:

- `@Transactional` wirkt bei Proxy-basiertem AOP **nur auf public Methoden**. `AuditWriter.write` war
  zuerst package-private; `REQUIRES_NEW` wäre wirkungslos geblieben und der Eintrag mit der
  Geschäftstransaktion verschwunden.
- `@Around("@annotation(audited)")` mit gebundenem Parameter scheitert, sobald ein zweiter Proxy
  (die Transaktionsberatung) davor sitzt: „Required to bind 2 arguments … JoinPointMatch was NOT bound".
  Die Annotation wird deshalb aus der Signatur gelesen statt gebunden.

---

## D-20 — Mindestlänge 12 Zeichen für über die API gesetzte Passwörter

**Kontext.** Die Spec definiert keine Passwortrichtlinie, setzt den Initial-Admin aber auf `admin`.

**Entscheidung.** Über die API gewählte Passwörter brauchen mindestens 12 Zeichen (Bean Validation auf
`ChangePasswordRequest`). Das geseedete Standardpasswort ist ausgenommen, erzwingt aber eine Änderung
(siehe D-21).

**Begründung.** Ohne jede Richtlinie stünde in einer Arbeit über Zugriffskontrolle ein Endpunkt, der
`a` als Passwort akzeptiert. Zwölf Zeichen sind eine bewusste, begründbare Untergrenze und keine
komplexen Zeichenklassenregeln, die erwiesenermaßen wenig bringen.

---

## D-21 — Erzwungener Passwortwechsel über eingeschränkte Tokens

**Kontext.** Spec Abschnitt 7.1 verlangt, dass das Standardpasswort außerhalb von `dev` „beim ersten
Login eine Passwortänderung erzwingt". E-7 verlangt zugleich, dass jede Durchsetzung serverseitig
passiert — ein Hinweis, den nur das Frontend auswertet, wäre zu wenig.

**Entscheidung.** Ist `must_change_password` gesetzt, enthält das ausgestellte Access-Token nur noch die
Berechtigung `PROFILE_UPDATE_OWN`. Rollen bleiben im Token, damit die Oberfläche den Kontext anzeigen
kann. Nach erfolgreichem Wechsel entfällt das Flag und der nächste Token trägt wieder alle
Berechtigungen.

**Begründung.** Das Konto kann damit ausschließlich das eigene Profil und Passwort ändern; jeder andere
Endpunkt scheitert an `@PreAuthorize`, ohne Sonderfall in der Filterkette. Im laufenden docker-Profil
verifiziert: `roles: [ADMIN]`, `perms: [PROFILE_UPDATE_OWN]`.

---

## D-22 — `RedisConfig` entfällt zugunsten von `StringRedisTemplate`

**Kontext.** Spec Abschnitt 3 listet `RedisConfig.java` mit einem `RedisTemplate<String,String>`. Spring
Boot konfiguriert mit `StringRedisTemplate` bereits genau diesen Typ; beide Beans nebeneinander machten
die Injektion mehrdeutig und der Kontext startete nicht.

**Entscheidung.** `RedisConfig` entfernt, `TokenBlacklistService` und `TokenVersionService` injizieren
`StringRedisTemplate`.

**Begründung.** Eine eigene Konfigurationsklasse, die ein vorhandenes Framework-Bean dupliziert, ist kein
Gewinn. Die Spec-Anforderung ist erfüllt — nur eben durch Boot statt durch eigenen Code.

---

## D-23 — Zwei ArchUnit-Regeln präzisiert

**Kontext.** Zwei selbst gesetzte Regeln aus Phase 0 kollidierten mit der Paketstruktur der Spec:

- *Repositories nur aus `..service..`*: `CampusUserDetailsService` und `TokenVersionService` liegen laut
  Spec Abschnitt 3 im Paket `security` und müssen Konten lesen.
- *Services hängen nicht von `..web..` ab*: Services geben DTOs zurück, und die liegen laut Spec unter
  `web/dto`.

**Entscheidung.** Erste Regel erlaubt zusätzlich `..security..`; zweite Regel verbietet jetzt konkret
Abhängigkeiten auf Klassen mit Namensendung `Controller`.

**Begründung.** Beide Regeln waren strenger formuliert als die Spec verlangt (dort: „Controller
injizieren keine Repositories", „Entities verlassen nie die Service-Schicht"). Die eigentliche Aussage
bleibt erhalten und wird weiter geprüft; korrigiert wurde meine Verschärfung, nicht die Spec.

---

## D-13 — HTTP 422 für unzulässige Statusübergänge

**Kontext.** Spec Abschnitt 4.5 nennt `409 INVALID_STATUS_TRANSITION`, Abschnitt 4.7 nennt „`422`
unzulässiger Statusübergang". Widerspruch.

**Entscheidung.** `422` mit Code `INVALID_STATUS_TRANSITION`. Abschnitt 4.5 ist entsprechend zu
korrigieren.

**Begründung.** Abschnitt 4.7 ist der normative Fehlercode-Katalog und reserviert 422 genau dafür; 409
bleibt für Invarianten (`LAST_ADMIN_PROTECTED` und Verwandte). Umgesetzt wird das in Phase 6.

**Abweichung von der Spec:** ja (Abschnitt 4.5).

## D-34 — Beratungszeiten veröffentlicht nur `CONSULTATION_UPDATE_ANY`

**Kontext.** `consultation.is_published` hat in der Spec keinen Eigentümer. `PERSONAL` hält
`CONSULTATION_UPDATE_OWN` und könnte damit eigene Einträge selbst veröffentlichen — POIs durchlaufen
einen Freigabe-Workflow (E-4), Beratungszeiten hätten dann gar keine Qualitätskontrolle.

**Entscheidung.** `published` wird nur übernommen, wenn der Aufrufer `CONSULTATION_UPDATE_ANY` hält;
dieselbe Berechtigung erlaubt als einzige, ein Angebot einer anderen Person zuzuordnen. Ohne sie wird das
Feld stillschweigend ignoriert statt die Anfrage abzuweisen. Wer ein Angebot anlegt, wird zuständig; ein
späteres Update ohne `responsibleUserId` lässt die Zuständigkeit unverändert.

**Begründung.** `PERSONAL` pflegt Inhalte, die Projektleitung gibt frei — dieselbe Trennung wie beim POI,
nur ohne eigenen Statusautomaten, weil ein Beratungsangebot keine vier Zustände braucht. Das Ignorieren
statt Abweisen hält den Frontend-Fall einfach: dieselbe Maske kann das Feld anzeigen (deaktiviert) und
denselben Body senden. Dass die Freigabe nicht heimlich die Zuständigkeit übernimmt, ist der zweite Teil:
sonst hätte jede Veröffentlichung den Eintrag dem Fachgebiet entzogen, das ihn pflegt.

---

## D-35 — Fehlende Primitive im Request-Body sind `false`, kein Fehler

**Kontext.** Jackson 3 hat `FAIL_ON_NULL_FOR_PRIMITIVES` standardmäßig aktiviert (Jackson 2: aus). Ein
`POST /api/buildings` mit `{"code":"S1|03","nameDe":"…"}` — ohne das optionale `published` — endete
deshalb in einer `HttpMessageNotReadableException`. Die kennzeichnet Spring nicht als `ErrorResponse`,
also fiel sie in den Catch-all des `GlobalExceptionHandler`: **HTTP 500 für einen gültigen Request.**
Gefunden von `BuildingIT`, nicht von der Berechtigungsmatrix — die prüft Endpunkte mit vollständigem Body.

**Entscheidung.** Zwei Änderungen: `spring.jackson.deserialization.fail-on-null-for-primitives: false`,
und `HttpMessageNotReadableException` wird explizit auf `400 MALFORMED_REQUEST` abgebildet.

**Begründung.** Ein fehlendes Flag bedeutet in JSON `false`; welche Felder Pflicht sind, sagt Bean
Validation, nicht der Unterschied zwischen `boolean` und `Boolean`. Und ein Client, der unlesbares JSON
schickt, bekommt 400 — 500 hätte einen Serverfehler behauptet, wo keiner vorliegt (NFA-07). Der Code
`MALFORMED_REQUEST` ergänzt den Katalog aus Abschnitt 4.7.

**Abweichung von der Spec:** Ergänzung (Fehlercode `MALFORMED_REQUEST` in Abschnitt 4.7).

---

## D-36 — Zwei JPA-Fallstricke, die nur die Antwort betreffen

**Kontext.** Zwei Tests aus Phase 6 schlugen mit korrekten Datenbankzuständen, aber falschen Antworten
fehl — beides Fälle, in denen der Zustand im Speicher und der in der Datenbank auseinanderlaufen.

**Entscheidung.**

- `PoiService` schreibt Statusübergänge mit `saveAndFlush`. `is_published` ist eine Generated Column
  (`@Generated(INSERT, UPDATE)`); Hibernate liest sie erst nach dem tatsächlichen Update neu. Ohne Flush
  meldete die Antwort auf `POST /api/pois/{id}/publish` `status: PUBLISHED` bei `published: false`.
- `ConsultationService.addEvent` speichert den neuen Termin über das Termin-Repository, nicht per Kaskade
  über das Angebot. Das Angebot ist bereits persistent, `save` geht also in `merge`, und die Kaskade
  vergibt die ID an eine **Kopie** des Termins — das zurückgegebene Objekt blieb ohne ID, das Feld fiel
  wegen `default-property-inclusion: non_null` ganz aus der Antwort, und der Client konnte den gerade
  angelegten Termin nicht ansprechen.

**Begründung.** Beide Male war die Persistenz korrekt und nur die Rückgabe falsch — die Art Fehler, die
ein Test, der nach dem Aufruf frisch aus der Datenbank liest, nie sieht. Die Tests prüfen deshalb die
HTTP-Antwort, nicht den Repository-Zustand.
