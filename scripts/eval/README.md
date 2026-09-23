# Evaluationsumgebung

Isolierte Instanz für die Usability-Studie der Bachelorarbeit. Läuft neben der Entwicklungsumgebung,
auf eigenen Ports, mit eigener Datenbank und eigenem Redis.

Die inhaltlichen Unterlagen — Datenmanifest, Sitzungsanleitung, Zugangsdaten, Durchlaufnachweis —
liegen außerhalb dieses Repositories unter `~/Studium/Bachelorthesis/eval/vorbereitung/`.
Zugangsdaten und Forschungsdaten gehören nicht in Git.

## Befehle

```bash
./scripts/eval/eval-up.sh [--build]   # Umgebung starten
./scripts/eval/eval-reset.sh          # Ausgangszustand herstellen — vor jeder Sitzung
./scripts/eval/eval-selftest.sh       # alle neun Aufgaben ausführen und zurücksetzen
python3 scripts/eval/test_eval_run.py # Prüffälle der Auswertungslogik
```

Während einer Sitzung:

```bash
./scripts/eval/eval-run start --participant <CODE>
./scripts/eval/eval-run checkpoint --task T1
./scripts/eval/eval-run report --participant <CODE> --observations <CSV>
```

## Adressen

| | Studie | Entwicklung |
|---|---|---|
| Anwendung | 3100 | 3000 |
| API | 8090 | 8080 |
| PostgreSQL | 5442 | 5432 |
| Redis | 6389 | 6379 |
| Compose-Projekt | `campus-eval` | `3d-virtual-campus-management-software` |
| Datenbank | `campus_eval` | `campus` |
| Profile | `docker,eval` | `docker` (plus `demo` mit Overlay) |

## Sicherungen gegen das falsche Ziel

`eval-reset.sh` löscht in seiner Zieldatenbank alle Konten und Inhalte. Bevor irgendetwas passiert,
prüft `eval-env.sh` drei Dinge: dass ein Datenbank-Container im Projekt `campus-eval` läuft, dass
dieser Container das Projektlabel `campus-eval` trägt, und dass die Datenbank `campus_eval` darin
existiert. Schlägt eine davon fehl, bricht das Skript ab, ohne etwas zu verändern.

`docker-compose.eval.yml` setzt `name: campus-eval` in der Datei selbst statt sich auf `-p` zu
verlassen — ein vergessenes Flag würde sonst die Entwicklungscontainer mit diesen Einstellungen neu
erzeugen.

Jede `ports`-Liste des Overlays trägt `!override`. Ohne das Tag hängt Compose die Listen aneinander
statt sie zu ersetzen, und beide Umgebungen streiten sich um dieselben Ports.

## Datenbestand

Der Ausgangszustand steht einmal, in
`backend/src/main/resources/db/eval/R__seed_eval_baseline.sql`. Flyway führt ihn beim ersten Bau aus,
`eval-reset.sh` vor jeder Sitzung — dieselbe Datei, damit die beiden Wege nicht auseinanderlaufen
können. Er löscht, bevor er einfügt: Ein Seed aus `ON CONFLICT DO NOTHING` kann nicht rückgängig
machen, was eine teilnehmende Person geändert hat.

Die Sequenzen werden dabei zurückgesetzt, damit die Kennungen in jeder Sitzung dieselben sind. Das
Aufgabenblatt für T8 nennt eine konkrete Angebots-Kennung und muss nur einmal gedruckt werden.

Den Audit-Eintrag für T8 erzeugt `eval-reset.sh` über einen echten API-Aufruf, nicht per SQL. Die
Aufgabe verlangt von einer Person, genau diesen Eintrag zu lesen; eine von Hand geschriebene Zeile
wäre ein erfundener Beleg.
