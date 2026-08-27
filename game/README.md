# Unity-Anbindung

Die Brücke zwischen der Verwaltung und dem 3D-Campus. Sie hängt an keiner Spiellogik, nur an der API,
und wird als Ordner in ein bestehendes Unity-Projekt eingespielt.

Die Spielumgebung ist der 3D-Campus der AG Serious Games —
`serious-games-darmstadt/3d-virtual-campus`, Branch **`FECP_Bulut`**, Szene `Assets/Scenes/Web.unity`.
Dieses Projekt liegt **nicht** im Repository (rund 4 GB, fremdes Team, siehe `docs/DECISIONS.md` D-45);
hier steht nur, was beide Seiten verbindet.

| Datei | Aufgabe |
|---|---|
| `Campus.asmdef` · `Editor/Campus.Editor.asmdef` | eigene Assemblies für die Brücke — siehe unten |
| `Scripts/WebBridge.cs` | Token und API-Adresse von der Webseite; muss auf einem GameObject namens **`WebBridge`** liegen |
| `Plugins/WebBridge.jslib` | die Browserseite derselben Brücke |
| `Scripts/SceneModel.cs` | die Datenklassen zu `GET /api/game/scene` |
| `Scripts/SceneLoader.cs` | lädt die Szene und setzt sie in die Welt |
| `Scripts/CampusBuildingRegistry.cs` | findet die Gebäude der Szene über ihren Schlüssel |
| `Scripts/PoiMarker.cs` | kennt die Daten eines Punktes und formuliert den Infotext |
| `Scripts/CampusUi.cs` | eigener Overlay-Canvas: Infofeld und Redaktions-Abzeichen |
| `Scripts/CampusInteraction.cs` | Klick bzw. `E` auf einen Punkt, `Esc` schließt |
| `Scripts/CampusInput.cs` | Freiflug-Kamera — nur für die Sandkastenszene |
| `Scripts/InfoPanel.cs` | das Textfeld der Sandkastenszene |
| `Scripts/GameStateClient.cs` | lädt und speichert den Spielstand des Kontos |
| `Editor/CampusSceneInjector.cs` | fügt die Anbindung in eine **bestehende** Szene ein |
| `Editor/CampusSceneBuilder.cs` | erzeugt eine Sandkastenszene aus dem Nichts |
| `Editor/CampusBuild.cs` | baut nach `frontend/public/game` mit den richtigen Einstellungen |
| `Editor/CampusAccountMenu.cs` | Editor-Konto umschalten (`demo_studi` / `demo_leitung` / `demo_admin`) |

## Einmal einrichten

```bash
# 1. Unity 6000.3.8f1 mit WebGL-Modul über Unity Hub installieren.
#    Dieselbe Version wie das FEC-Projekt: ein neuerer Editor zieht es beim Öffnen hoch —
#    alle Assets neu importiert und ein Diff, um den niemand gebeten hat.

# 2. Das FEC-Projekt neben dieses Repository klonen (rund 4 GB):
git clone --depth 1 --branch FECP_Bulut \
    git@github.com:serious-games-darmstadt/3d-virtual-campus.git ~/projects/fec-campus

# 3. Die Brücke einspielen:
./game/install-bridge.sh ~/projects/fec-campus

# 4. Unity Hub → Add project from disk → ~/projects/fec-campus
```

Schritt 3 kopiert `game/campus-bridge/` nach `<projekt>/Assets/Campus/` und schreibt
`<projekt>/CampusBuild.json` mit dem absoluten Pfad auf `frontend/public/game`. Nach jeder Änderung an
der Brücke einfach erneut aufrufen — das Skript ist dafür gemacht.

## In Unity

1. `Assets/Scenes/Web.unity` öffnen.
2. **Campus → Anbindung in aktuelle Szene einfügen**.

Das legt `WebBridge`, `CampusUI`, `CampusRoot` (mit `SceneLoader`, `CampusBuildingRegistry` und
`CampusInteraction`) und `GameState` an, verdrahtet die Felder und speichert. Ein zweiter Aufruf meldet
„nichts zu tun"; vorhandene Objekte werden übernommen, nichts wird überschrieben.

Boden, Kamera und Steuerung kommen nicht dazu — die hat die Szene. Der Spieler läuft mit dem
`WorldCharacterController` des FEC-Projekts; die Brücke liest genau einen Klick und eine Taste.

Für ein leeres Projekt gibt es weiterhin **Campus → Szene erzeugen**: eine Sandkastenszene mit Boden,
Freiflug-Kamera und Platzhalterquadern.

## Eigene Assemblies

Die Brücke bringt zwei Assembly Definitions mit: `Campus` für die Laufzeit und `Campus.Editor` für die
Menüpunkte. Ohne sie fallen die Skripte in die Assembly des Gastprojekts — im FEC-Campus ist das
`AssemblyDef.asmdef` auf `Assets/`, und das referenziert das Input-System nicht. Ergebnis wäre
`error CS0234: 'InputSystem' does not exist in 'UnityEngine'`, und weil ein Compilerfehler *alle*
Skripte des Projekts blockiert, erschiene nicht einmal das `Campus`-Menü.

Mit eigenen Assemblies hängt die Brücke an nichts, was das Gastprojekt zufällig eingetragen hat. Sie
verlangt dafür zwei Pakete, die beide Projekte ohnehin haben:

- `com.unity.inputsystem` — für `CampusInput` und `CampusInteraction`
- `com.unity.ugui` (Assembly `UnityEngine.UI`) — für `CampusUi` und `InfoPanel`

Fehlt eines davon im Zielprojekt, meldet Unity die Referenz als unauflösbar; dann in `Campus.asmdef` die
betreffende Zeile streichen und das zugehörige Skript entfernen.

## Wie Daten und Szene zusammenfinden

**Gebäude** werden gesucht, nicht erzeugt. Beide Seiten werden auf Buchstaben und Ziffern reduziert:
`S1|03` aus der Datenbank findet das Objekt `S103` in der Szene (D-46). Beim Laden steht in der Konsole,
welche Schlüssel gebunden wurden:

```
Gebäude gebunden: S101, S103, S120, S202, S306 · nicht in der Szene gefunden: —
```

Ein Schlüssel, den die Szene nicht kennt, ist kein Fehler: für ihn gilt weiter
`building.position_x/y/z` — in der Sandkastenszene ist das der Normalfall.

**POIs** stehen über dem Gebäude, das im Formular gewählt wurde; `position_x/y/z` ist dann ein Versatz
in Metern (D-47). Ohne Gebäude sind es Weltkoordinaten. Deshalb sind die Werte in den Demo-Daten klein:
sie fächern die Punkte eines Hauses auf, sie verorten sie nicht.

**Beratungsangebote** haben keine eigene Position. Sie erscheinen im Infotext jedes Punktes, der im
selben Gebäude steht.

## Im Editor testen

Ohne Browser gibt es keine Brücke, deshalb meldet sich `WebBridge` im Editor **selbst an**. Voreingestellt
ist `demo_leitung` / `demo-passwort` gegen `http://localhost:8080/api`; alle drei Werte stehen als Felder
auf dem Objekt **`WebBridge`**. Es genügt also, das Backend zu starten und Play zu drücken:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d db redis backend
```

Zum Rollenvergleich **Campus → Editor-Konto** umschalten:

| Konto | Szene |
|---|---|
| `demo_leitung` | 12 Punkte, 5 Gebäude, 7 Punkte orange (Entwürfe und Eingereichtes), Abzeichen „Redaktionsansicht" |
| `demo_studi` | 5 Punkte, 4 Gebäude, keine orangen, kein Abzeichen |

Genau dieser Unterschied ist der Kern der Sache — dieselbe Szene, dieselbe URL, zwei Rollen.

Das Feld **Editor Token** ist nur für den Sonderfall gedacht, ein bestimmtes Token vorzugeben. Steht dort
etwas, wird es benutzt und **nicht** angemeldet — ein abgelaufenes Token darin ist die häufigste Ursache
für ein hartnäckiges 401. Im Zweifel leeren.

Die Anmeldedaten stehen hinter `#if UNITY_EDITOR` und landen nie in einem Build.

Steuerung im FEC-Campus: die des Projekts (WASD, Maus). Klick oder `E` auf einen Marker öffnet sein
Infofeld, `Esc` schließt es.

## WebGL-Build

**Campus → WebGL-Build erzeugen**. Ausgabeordner ist der aus `CampusBuild.json`; fehlt die Datei, wird
einmal gefragt und die Antwort gemerkt.

Ohne Editor, etwa für ein Protokoll:

```bash
/Applications/Unity/Hub/Editor/6000.3.8f1/Unity.app/Contents/MacOS/Unity \
  -batchmode -quit -nographics \
  -projectPath ~/projects/fec-campus \
  -executeMethod Campus.Editor.CampusBuild.BuildFromCommandLine
```

Der erste Lauf wechselt die Plattform auf WebGL und dauert je nach Rechner zwanzig Minuten bis eine
Stunde. Ist das WebGL-Modul nicht installiert, im Unity Hub unter *Installs → Add modules* nachrüsten.

Gesetzt werden dabei drei Dinge, die im Dialog leicht untergehen:

- *Compression Format* auf `Disabled` — komprimiert wird von nginx, das setzt den passenden
  `Content-Encoding`-Header gleich mit (D-48). Vorkomprimierte `.br`-Dateien ohne Header waren schon
  einmal die Ursache dafür, dass gar nichts lud.
- *Data Caching* an — bei dieser Größe wäre der zweite Start sonst so lang wie der erste.
- `build-info.json` mit den tatsächlichen Dateinamen. Die Webseite liest sie von dort, statt sie zu
  raten; Unity hat die Namensregel zwischen Versionen schon geändert.

Danach genügt:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml restart frontend
```

Kein `--build`: der Ordner ist in den Container eingehängt (D-48).

Der Build selbst ist **nicht versioniert** (`frontend/public/game/*` steht in `.gitignore`). Solange
keiner vorliegt, zeigt `/play` die Szenendaten als Liste — die Datenkette ist damit auch ohne Unity
vorführbar.

## Was das FEC-Projekt vorgibt

Nachgesehen im Branch `FECP_Bulut` (Stand `a4427b5`):

- **Unity 6000.3.8f1**, URP 17.3.0, Input System 1.18.0, `activeInputHandler: 2` (altes *und* neues
  Input-System). Die Brücke ist über `#if ENABLE_INPUT_SYSTEM` auf beide Pfade vorbereitet.
- **WebGL ist bereits eingerichtet**: `Web.unity` steht als Szene 0 in den Build Settings,
  `webGLCompressionFormat` auf `Disabled`.
- Das **ArcGIS-SDK ist raus** (im README des Projekts als „discontinued" vermerkt, kein Paket im
  `manifest.json`). `PostBuildProcessor.cs` sucht noch nach `.slpk`-Dateien, protokolliert ihr Fehlen
  und baut weiter.
- Die 39 Gebäude tragen den Tag `EnergyGameBuilding` und die Namen `S101` … `S401`; ihre Klarnamen
  stehen in `Assets/Resources/CSVFiles/Buildings_Overview.csv`.
- Der Spieler trägt den Tag `Player`. Darüber findet `GameStateClient` ihn, ohne verdrahtet zu werden.

## Das Sandkastenprojekt

`game/My project/` ist das von Unity Hub angelegte Testprojekt für die Brücke. Es steht in
`.gitignore` (D-45) und wird von demselben Skript versorgt:

```bash
./game/install-bridge.sh "game/My project"
```

Danach **Campus → Szene erzeugen** und Play. Nützlich, um eine Änderung an der Brücke zu prüfen, ohne
den ganzen Campus zu laden.
