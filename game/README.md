# Unity-Anbindung

Die Brücke zwischen der Verwaltung und dem 3D-Campus: sieben C#-Skripte und ein JavaScript-Plugin. Sie
hängen an keiner Spiellogik, nur an der API, und lassen sich als Ordner in das bestehende FEC-Projekt
übernehmen.

Das Unity-Projekt liegt unter `My project/` (von Unity Hub so angelegt), die Anbindung darin unter
`My project/Assets/Campus/`.

| Datei | Aufgabe |
|---|---|
| `Scripts/WebBridge.cs` | Token und API-Adresse von der Webseite; muss auf einem GameObject namens **`WebBridge`** liegen |
| `Plugins/WebBridge.jslib` | die Browserseite derselben Brücke |
| `Scripts/SceneModel.cs` | die Datenklassen zu `GET /api/game/scene` |
| `Scripts/SceneLoader.cs` | lädt die Szene und baut sie auf |
| `Scripts/PoiMarker.cs` | kennt die Daten eines Punktes und formuliert den Infotext |
| `Scripts/CampusInput.cs` | Kamera bewegen und Punkte anklicken (neues Input System) |
| `Scripts/InfoPanel.cs` | das Textfeld dafür |
| `Scripts/GameStateClient.cs` | lädt und speichert den Spielstand des Kontos |
| `Editor/CampusSceneBuilder.cs` | erzeugt die komplette Szene auf Knopfdruck |
| `Editor/CampusBuild.cs` | baut nach `frontend/public/game` mit den richtigen Einstellungen |

## Szene erzeugen

In Unity: **Campus → Szene erzeugen**.

Das legt `Assets/Campus/Scenes/CampusScene.unity` an, mit Boden, Kamera samt Steuerung, `WebBridge`,
`Campus` (der `SceneLoader`), `GameState` und dem Info-Panel — alle Felder verdrahtet, die Materialien
als URP-Material erzeugt. Die Szene wird zugleich als einzige in die Build Settings eingetragen.

Von Hand ist daran nichts mehr zu tun; ein zweiter Aufruf erzeugt dieselbe Szene erneut.

## Im Editor testen

Ohne Browser gibt es keine Brücke, deshalb meldet sich `WebBridge` im Editor **selbst an**. Voreingestellt
ist `demo_leitung` / `demo-passwort` gegen `http://localhost:8080/api`; alle drei Werte stehen als Felder
auf dem Objekt **`WebBridge`**. Es genügt also, das Backend zu starten und Play zu drücken.

Zum Rollenvergleich einfach **Editor Username** ändern:

| Konto | Szene |
|---|---|
| `demo_leitung` | 12 Würfel, 5 Gebäude, 7 davon orange (Entwürfe und Eingereichtes) |
| `demo_studi` | 5 Würfel, 4 Gebäude, keine orangen |

Genau dieser Unterschied ist der Kern der Sache — dieselbe Szene, dieselbe URL, zwei Rollen.

Das Feld **Editor Token** ist nur für den Sonderfall gedacht, ein bestimmtes Token vorzugeben. Steht dort
etwas, wird es benutzt und **nicht** angemeldet — ein abgelaufenes Token darin ist die häufigste Ursache
für ein hartnäckiges 401. Im Zweifel leeren.

Die Anmeldedaten stehen hinter `#if UNITY_EDITOR` und landen nie in einem Build.

Steuerung: WASD bewegen, rechte Maustaste umsehen, Q/E Höhe, Klick auf einen Würfel öffnet sein Infofeld.

## WebGL-Build

In Unity: **Campus → WebGL-Build erzeugen**.

Das setzt *Compression Format* auf `Disabled` (sonst liefert nginx `.br`-Dateien ohne passenden
`Content-Encoding`-Header aus und nichts lädt), baut nach `frontend/public/game` und schreibt dort eine
`build-info.json` mit den tatsächlichen Dateinamen. Die Webseite liest diese Datei — damit ist die
Einbettung unabhängig davon, wie Unity seine Dateien benennt.

Beim ersten Mal fragt Unity nach dem Plattformwechsel; das dauert einige Minuten. Ist das WebGL-Modul
nicht installiert, im Unity Hub unter *Installs → Add modules* nachinstallieren.

Danach `npm run dev` beziehungsweise `docker compose … up --build`. Solange kein Build vorhanden ist,
zeigt `/play` die Szenendaten als Liste; die Datenkette ist also auch ohne Unity prüfbar.

## Was dieses Projekt vorgibt

Beim Anlegen über Unity Hub sind zwei Voreinstellungen gesetzt, an denen fremder Beispielcode
üblicherweise scheitert:

- **URP** (Universal Render Pipeline) — Materialien brauchen den Shader
  `Universal Render Pipeline/Lit`, sonst erscheint alles magenta. `CampusSceneBuilder` erzeugt sie
  entsprechend.
- **Nur das neue Input System** (`activeInputHandler: 1`) — `Input.GetAxis` und `OnMouseUpAsButton`
  werfen zur Laufzeit. `CampusInput` benutzt `Keyboard.current` und `Mouse.current`, hält über `#if`
  aber auch den alten Pfad offen, falls das FEC-Projekt darauf steht.

## Übernahme in das FEC-Projekt

Den Ordner `Assets/Campus/` hinüberkopieren und dort **Campus → Szene erzeugen** aufrufen — oder in einer
bestehenden Szene die vier Objekte selbst anlegen (`WebBridge`, `SceneLoader`, `GameStateClient`,
`InfoPanel`).

Die vorhandenen Gebäude bleiben Prefabs und werden über `model_ref` zugeordnet: im `SceneLoader` unter
*Gebäudemodelle* je Eintrag den Wert aus der Datenbank (etwa `models/s1_03.glb`) mit dem passenden Prefab
verbinden. Ohne Eintrag entsteht ein Platzhalterquader, das Spiel läuft also auch bei unvollständiger
Zuordnung.

Offen, bis das FEC-Repository zugänglich ist: Unity-Version, ob dort ein WebGL-Build konfiguriert ist,
und in welchem Koordinatensystem die Szene liegt — davon hängt ab, welche Werte in
`building.position_x/y/z` gehören.

## Zwei Aufräumarbeiten (optional)

1. **Eigenes Git-Repository im Projekt.** Unity Hub hat `My project/.git` mit einem Initial-Commit
   angelegt. Solange das existiert, nimmt das Hauptrepository den Ordner nicht auf. Zum Einbinden:

   ```bash
   rm -rf "game/My project/.git"
   ```

   Die mitgelieferte `.gitignore` bleibt und hält `Library/`, `Temp/` und `Logs/` weiterhin draußen.

2. **Ordnername.** `My project` enthält ein Leerzeichen. Umbenennen geht — Unity **vorher schließen**:

   ```bash
   mv "game/My project" game/unity
   ```

   Danach im Unity Hub *Add project from disk* auf den neuen Pfad zeigen. Der Build-Pfad in
   `CampusBuild.cs` ist relativ und bleibt gültig.
