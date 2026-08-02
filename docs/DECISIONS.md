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

## D-13 — HTTP 422 für unzulässige Statusübergänge

**Kontext.** Spec Abschnitt 4.5 nennt `409 INVALID_STATUS_TRANSITION`, Abschnitt 4.7 nennt „`422`
unzulässiger Statusübergang". Widerspruch.

**Entscheidung.** `422` mit Code `INVALID_STATUS_TRANSITION`. Abschnitt 4.5 ist entsprechend zu
korrigieren.

**Begründung.** Abschnitt 4.7 ist der normative Fehlercode-Katalog und reserviert 422 genau dafür; 409
bleibt für Invarianten (`LAST_ADMIN_PROTECTED` und Verwandte). Umgesetzt wird das in Phase 6.

**Abweichung von der Spec:** ja (Abschnitt 4.5).
