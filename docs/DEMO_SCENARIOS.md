# Manuelle Testszenarien

Zehn Abläufe, die den Stand der Anwendung von Hand prüfen. Jedes Szenario nennt am Ende, welche
Anforderung oder welches Evaluationsszenario aus `spec/02_IMPLEMENTIERUNGSPLAN.md` es belegt.

Alle Angaben beziehen sich auf den Containerbetrieb mit Demo-Daten. Die Daten selbst sind in
[DEMO_DATA.md](DEMO_DATA.md) beschrieben.

## Starten

```bash
docker compose down -v                                                    # sauberer Anfangszustand
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

- Oberfläche: http://localhost:3000
- API und Swagger: http://localhost:8080/api/health · http://localhost:8080/swagger-ui.html
- Konten: `demo_admin`, `demo_leitung`, `demo_mitarbeit`, `demo_personal`, `demo_devops`, `demo_neu`,
  `demo_gesperrt` — Passwort überall `demo-passwort`

Stoppen mit `docker compose down`, zurücksetzen mit `docker compose down -v`.

Für die Gegenproben in einem Terminal einmal diese Hilfsfunktion setzen:

```bash
tok() { curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"${2:-demo-passwort}\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])'; }
```

Die Gegenproben sind der wichtigere Teil: Sie zeigen, dass die Oberfläche nichts *verhindert*, sondern nur
nichts anbietet, was der Server ohnehin ablehnt.

---

## 1 · Menü je Rolle

Nacheinander als alle Konten anmelden und die Sidebar sowie die Zahl rechts oben vergleichen.

| Konto | erwartet |
|---|---|
| `demo_admin` | 37 Berechtigungen, alle Menüpunkte |
| `demo_leitung` | 31, mit Freigabe-Warteschlange |
| `demo_mitarbeit` | 11, POIs **ohne** Freigabe-Warteschlange, keine Nutzerverwaltung |
| `demo_personal` | 8, **nur** Dashboard |
| `demo_devops` | 5, nur Rollen & Rechte und Audit-Log |
| `demo_gesperrt` | Anmeldung scheitert mit „Konto ist gesperrt" |

**Belegt:** S-17, FA-16

## 2 · Gesperrte Route direkt aufrufen

Als `demo_mitarbeit` angemeldet `localhost:3000/users` in die Adresszeile tippen. Danach:

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/users -H "Authorization: Bearer $(tok demo_mitarbeit)"
```

**Erwartet:** 403-Seite mit „Benötigt wird: USER_READ", curl antwortet `403`.
**Belegt:** S-18, S-03, D-38

## 3 · Konto anlegen und Vergabemenge

Als `demo_leitung`: *Nutzerverwaltung → Konto anlegen*. Die Rollenauswahl ansehen, `test.mitarbeit` mit
`PROJEKTMITARBEITER` anlegen, das Initialpasswort notieren. Gegenprobe:

```bash
curl -s -X POST localhost:8080/api/users/1/roles -H "Authorization: Bearer $(tok demo_leitung)" \
  -H 'Content-Type: application/json' -d '{"roleName":"ADMIN"}'
```

**Erwartet:** Auswahl enthält nur `PERSONAL` und `PROJEKTMITARBEITER`; Initialpasswort erscheint genau
einmal; curl liefert `403 ROLE_NOT_GRANTABLE`.
**Belegt:** S-05, S-06, FA-07, FA-08

## 4 · Erstanmeldung mit Initialpasswort

Privates Browserfenster, anmelden als `demo_neu`. Dann `localhost:3000/pois` direkt aufrufen. Danach
*Profil → Passwort ändern*, zuerst mit einem zu kurzen Passwort, dann mit einem gültigen.

**Erwartet:** gelbes Banner, Sidebar zeigt nur das Dashboard, Dashboard nennt **eine** Berechtigung;
`/pois` endet auf der 403-Seite; kurzes Passwort erzeugt einen Feldfehler; nach der Änderung wirst du
abgemeldet und siehst nach erneuter Anmeldung 11 Berechtigungen und die POIs.
**Belegt:** D-21, D-20, FA-19

## 5 · POI anlegen und einreichen

Als `demo_mitarbeit`: *POIs → POI anlegen*, speichern, dann *Zur Prüfung einreichen*.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/pois/<id>/publish \
  -H "Authorization: Bearer $(tok demo_mitarbeit)"
```

**Erwartet:** Status *Entwurf*, dann *In Prüfung*; *Freigeben* ist nicht vorhanden; curl liefert `403`.
**Belegt:** S-09, S-10, FA-10, FA-11

## 6 · Zurückweisen, korrigieren, freigeben

Als `demo_leitung`: *Freigabe-Warteschlange* → Eintrag öffnen → *Zurückweisen* mit Begründung. Als
`demo_mitarbeit` die Begründung lesen, etwas ändern, erneut einreichen. Als `demo_leitung` freigeben.
Zuletzt ohne Anmeldung:

```bash
curl -s localhost:8080/api/public/pois | python3 -m json.tool | grep nameDe
```

**Erwartet:** `IN_REVIEW → DRAFT → IN_REVIEW → PUBLISHED`; die Begründung steht beim Autor und ist nach
der Freigabe verschwunden; die öffentliche Liste enthält den POI, aber keine Entwürfe.
**Belegt:** S-11, S-15, FA-17

## 7 · Fremden Entwurf ändern

Als `demo_neu` (nach Szenario 4) *POIs* öffnen und „Cafeteria Piloty" wählen — ein **Entwurf** von
`demo_mitarbeit`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X PUT localhost:8080/api/pois/<id> \
  -H "Authorization: Bearer $(tok demo_neu <neues-passwort>)" -H 'Content-Type: application/json' \
  -d '{"nameDe":"Gekapert","category":"OTHER","positionX":0,"positionY":0,"positionZ":0}'
```

**Erwartet:** *Speichern* deaktiviert mit Erklärung, kein Einreichen-Knopf, curl liefert `403`. Es
scheitert am Eigentum, nicht am Status.
**Belegt:** S-12, FA-12

## 8 · Konto sperren bei laufender Sitzung

`demo_personal` in einem zweiten Fenster angemeldet lassen. Als `demo_admin` das Konto sperren, dann im
zweiten Fenster klicken. Anschließend das eigene Konto von `demo_admin` öffnen.

**Erwartet:** sofortiger Rauswurf, erneuter Login mit `ACCOUNT_DISABLED`; beim eigenen Konto fehlen der
Sperren-Knopf und das × an den Rollen-Chips. Gegenprobe per curl auf die eigene ID:
`409 SELF_MODIFICATION_FORBIDDEN`.
**Belegt:** S-08, INV-2

## 9 · Audit-Log

Als `demo_devops` das Audit-Log öffnen, nach `ROLE_ASSIGNED` filtern, Filter wieder leeren.

```bash
curl -s "localhost:8080/api/audit?size=200" -H "Authorization: Bearer $(tok demo_admin)" | grep -ic 'demo-passwort\|passwordHash'
curl -s "localhost:8080/api/audit?resourceType=USER" -H "Authorization: Bearer $(tok demo_leitung)"
```

**Erwartet:** die Aktionen der Szenarien 3 bis 8 stehen drin, abgewiesene Versuche rot mit ihrem Code
(`ROLE_NOT_GRANTABLE`, `SELF_MODIFICATION_FORBIDDEN`, `ACCESS_DENIED`); der erste curl gibt `0`, weil
Passwörter maskiert werden; der zweite gibt `totalElements: 0`, weil `AUDIT_READ_CONTENT` keine
Nutzer-Einträge sieht.
**Belegt:** FA-15, S-14

## 10 · Token-Erneuerung

Als `demo_leitung` angemeldet: Entwicklerwerkzeuge → *Application → Local Storage* → `campus.accessToken`
löschen. **Nicht** neu laden, sondern in der Sidebar auf *Rollen & Rechte* klicken.

**Erwartet:** die Seite lädt; im Netzwerk-Tab stehen drei Aufrufe — `roles/matrix` 401,
`auth/refresh` 200, `roles/matrix` 200 — und in Local Storage liegt ein neues Token-Paar. Ein zweites
Mal mit dem alten Refresh-Token endet auf `401`, weil bei jeder Erneuerung rotiert wird.
**Belegt:** FA-02, FA-03
