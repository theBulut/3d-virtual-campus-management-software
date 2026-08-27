# Demo-Daten

Diese Daten werden **ausschließlich** angelegt, wenn das Spring-Profil `dev` oder `demo` aktiv ist. Sie
liegen als wiederholbare Flyway-Migration `R__seed_demo_data.sql` in der eigenen Location
`classpath:db/demo`, die nur diese beiden Profile auf die Flyway-Liste setzen. Ein Start ohne sie führt
die Datei nicht aus — die Demo-Daten fehlen dann nicht nur in der Oberfläche, sie existieren gar nicht
(`DECISIONS.md` D-8 und D-39).

**Alle Demo-Konten haben das Passwort `demo-passwort`.** Der zugehörige BCrypt-Hash steht offen im
Repository. Das ist vertretbar, weil die Datei außerhalb einer Vorführung nie geladen wird; bei aktivem
`demo`-Profil schreibt `DemoProfileWarning` beim Start eine Warnung ins Log.

Starten mit Demo-Daten:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Starten ohne (das Verhalten der ausgelieferten Anwendung):

```bash
docker compose up -d --build
```

## Konten

| Benutzername | Rolle | Besonderheit | Wofür in der Vorführung |
|---|---|---|---|
| `demo_admin` | ADMIN | 37 Berechtigungen | vollständige Matrix, Konten sperren, Audit-Log |
| `demo_leitung` | PROJEKTLEITER | 31 Berechtigungen | Konten anlegen, Rollen vergeben, Inhalte freigeben |
| `demo_mitarbeit` | PROJEKTMITARBEITER | 11 Berechtigungen | Inhalte erstellen und einreichen, **nicht** freigeben |
| `demo_personal` | PERSONAL | 8 Berechtigungen | nur eigene Beratungszeiten; Menü zeigt kein POI |
| `demo_devops` | MAINTENANCE_DEV | 5 Berechtigungen | Least-Privilege-Gegenbeispiel: Audit, aber keine Nutzerdaten |
| `demo_neu` | PROJEKTMITARBEITER | `must_change_password = true` | Token enthält nur `PROFILE_UPDATE_OWN`, Menü ist leer bis auf das Profil (D-21) |
| `demo_gesperrt` | PERSONAL | `is_active = false` | Anmeldung scheitert mit `ACCOUNT_DISABLED` |
| `demo_studi` | EXTERNE_PERSON | hat einen Spielstand | registrierte Studentin: sieht nur freigegebene Inhalte, kommt nicht in die Verwaltung |

Dazu kommt der beim ersten Start erzeugte Administrator aus `.env` (Standard `admin`/`admin`), sofern noch
kein aktives ADMIN-Konto existiert. Mit Demo-Daten existiert `demo_admin` bereits, das Konto wird dann
nicht angelegt.

## Gebäude (5)

| Schlüssel | Name | Objekt in der Szene | veröffentlicht |
|---|---|---|---|
| `S1\|03` | Altes Hauptgebäude | `S103` | ja |
| `S2\|02` | Robert-Piloty-Gebäude | `S202` | ja |
| `S1\|01` | Universitätszentrum (Karo 5) | `S101` | ja |
| `S1\|20` | Universitäts- und Landesbibliothek | `S120` | ja |
| `S3\|06` | Hans-Busch-Institut (ETIT) | `S306` | nein — belegt, dass die Filterung greift |

Alle fünf sind echte Häuser des Campus Stadtmitte und stehen als fertiges Modell in der FEC-Szene. Der
Schlüssel ist die Verbindung: `S1|03` wird auf Buchstaben und Ziffern reduziert und findet so `S103`
(`docs/DECISIONS.md` D-46). Namen und Adressen folgen denen, die das FEC-Projekt in
`Assets/Resources/CSVFiles/Buildings_Overview.csv` führt.

Die Spalten `position_x/y/z` und `rotation_y` der Gebäude sind damit im FEC-Campus ohne Wirkung — dort
steht das Haus, wo sein Modell steht. Sie greifen nur in der Sandkastenszene, die keine Gebäude hat.

## POIs (12, über alle vier Zustände)

| Name | Gebäude | Status | Eigentum | Rolle in der Vorführung |
|---|---|---|---|---|
| Audimax | S1\|01 | PUBLISHED | demo_mitarbeit | erscheint in `/api/public/pois` |
| Universitäts- und Landesbibliothek | S1\|20 | PUBLISHED | demo_mitarbeit | |
| Cafeteria Karo 5 | S1\|01 | PUBLISHED | demo_mitarbeit | |
| Studierendensekretariat | S1\|03 | PUBLISHED | demo_leitung | veröffentlicht **und** fremd |
| Rechnerpool Piloty | S2\|02 | PUBLISHED | demo_mitarbeit | eigener, aber veröffentlicht: nicht mehr änderbar |
| Hörsaal S1\|03 23 | S1\|03 | IN_REVIEW | demo_mitarbeit | liegt in der Freigabe-Warteschlange |
| Fachschaft Informatik | S2\|02 | IN_REVIEW | demo_mitarbeit | zweiter Eintrag zum Zurückweisen |
| Cafeteria Piloty | S2\|02 | DRAFT | demo_mitarbeit | **fremder Entwurf** für den Eigentumstest |
| Lernzentrum ULB | S1\|20 | DRAFT | demo_mitarbeit | |
| Fahrradwerkstatt | S1\|03 | DRAFT | demo_mitarbeit | trägt eine Zurückweisungsbegründung |
| Sprachenzentrum | S1\|03 | ARCHIVED | demo_leitung | archiviert ist endgültig |
| Alter Serverraum | S2\|02 | ARCHIVED | demo_leitung | |

Damit sind alle vier Zustände besetzt, und `/api/public/pois` liefert genau die fünf veröffentlichten.

`position_x/y/z` liegt bei allen zwölf zwischen −8 und 8: Die Werte sind ein Versatz in Metern vom
Ankerpunkt über dem Dach des jeweiligen Gebäudes, kein Ort (D-47). Sie fächern die Punkte eines Hauses
auf, damit sie sich nicht überdecken.

## Beratungszeiten (4 Angebote, 6 Termine)

| Titel | Einrichtung | veröffentlicht | Termine |
|---|---|---|---|
| Studienberatung Informatik | Fachgebiet Informatik | ja | Di 10–12, Do 14–16 |
| Sprechstunde Prüfungsamt | Prüfungsamt | ja | Mo 9–11:30, Mi 9–11:30 |
| Beratung für internationale Studierende | International Office | ja | Mi 13–16 (abweichender Raum) |
| Psychosoziale Beratung | Studierendenwerk | nein | Fr 10–13 |

Zuständig ist überall `demo_personal`. Das unveröffentlichte Angebot belegt, dass `PERSONAL` Inhalte
pflegt, die Freigabe aber `CONSULTATION_UPDATE_ANY` verlangt (D-34).

## Spielstand

`demo_studi` hat einen gespeicherten Spielstand (Position, zwei besuchte Gebäude, 23 Minuten Spielzeit).
Damit lässt sich „weiterspielen, wo du aufgehört hast" zeigen, ohne vorher den Campus abzulaufen. Alle
anderen Konten haben keinen — `GET /api/game/state` antwortet dort mit `204`, und das Spiel beginnt von
vorn.

## Was **nicht** geseedet wird

Keine Medien-Dateien (die Uploads landen im Volume `media_data`, nicht in der Datenbank) und keine
Audit-Einträge. Das Audit-Log ist beim Start leer und füllt sich während der Vorführung — das ist der
bessere Nachweis als vorgefertigte Zeilen.

## Zurücksetzen

Die Demo-Daten sind idempotent: ein Neustart legt nichts doppelt an, ändert aber auch nichts zurück, was
während einer Vorführung verändert wurde. Für einen sauberen Anfangszustand die Datenbank verwerfen:

```bash
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```
