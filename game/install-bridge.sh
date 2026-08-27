#!/usr/bin/env bash
#
# Copies the administration bridge into a Unity project and tells it where to build to.
#
# The FEC campus is a foreign repository of several gigabytes and stays a separate checkout
# (docs/DECISIONS.md D-45). What belongs to this thesis is the folder game/campus-bridge — eleven C#
# files, a JavaScript plugin and three materials. This script is the seam between the two.
#
#   ./game/install-bridge.sh ~/projects/fec-campus
#
# It is safe to run again after every change to the bridge; that is the intended way to update it.

set -euo pipefail

EXPECTED_UNITY="6000.3.8f1"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bridge="$repo_root/game/campus-bridge"
output="$repo_root/frontend/public/game"

if [[ $# -ne 1 ]]; then
    cat >&2 <<USAGE
Aufruf: $0 <pfad-zum-unity-projekt>

Der Pfad zeigt auf den Ordner, in dem 'Assets' und 'ProjectSettings' liegen — also auf das, was im
Unity Hub als Projekt eingetragen ist. Für das FEC-Projekt etwa:

    git clone --depth 1 --branch FECP_Bulut \\
        git@github.com:serious-games-darmstadt/3d-virtual-campus.git ~/projects/fec-campus
    $0 ~/projects/fec-campus
USAGE
    exit 64
fi

project="$(cd "$1" 2>/dev/null && pwd)" || {
    echo "Es gibt keinen Ordner '$1'." >&2
    exit 66
}

if [[ ! -d "$project/Assets" || ! -d "$project/ProjectSettings" ]]; then
    echo "'$project' sieht nicht nach einem Unity-Projekt aus: Assets/ oder ProjectSettings/ fehlt." >&2
    echo "Gemeint ist der Ordner, den der Unity Hub als Projekt führt." >&2
    exit 66
fi

if [[ ! -d "$bridge" ]]; then
    echo "Die Brücke fehlt: $bridge" >&2
    exit 70
fi

version_file="$project/ProjectSettings/ProjectVersion.txt"
if [[ -f "$version_file" ]]; then
    version="$(awk '/m_EditorVersion:/ {print $2}' "$version_file")"
    if [[ "$version" != "$EXPECTED_UNITY" ]]; then
        # Not an error: the bridge itself is plain C# and compiles on any Unity 6. But opening the FEC
        # project in a newer editor upgrades it — every asset reimported, and a diff nobody asked for
        # in a repository that belongs to somebody else.
        echo "Hinweis: Das Projekt steht auf Unity $version, erwartet war $EXPECTED_UNITY."
        echo "         Ein neuerer Editor zieht das fremde Projekt beim Öffnen hoch."
        echo
    fi
fi

# --delete räumt Skripte weg, die es in der Brücke nicht mehr gibt; Scenes/ bleibt ausgenommen, weil
# 'Campus → Szene erzeugen' dort etwas ablegt, das nicht aus diesem Repository stammt.
rsync -a --delete --exclude 'Scenes/' --exclude 'Scenes.meta' \
    "$bridge/" "$project/Assets/Campus/"

# Der Ausgabeordner muss absolut sein: die Brücke liegt jetzt in einem fremden Checkout, der irgendwo
# auf der Platte steht (siehe CampusBuild.cs). Die Datei liegt neben Assets/, damit Unity sie nicht
# als Asset importiert.
mkdir -p "$output"
cat > "$project/CampusBuild.json" <<JSON
{
    "outputPath": "$output"
}
JSON

files=$(find "$project/Assets/Campus" -name '*.cs' | wc -l | tr -d ' ')

cat <<DONE
Brücke eingespielt: $project/Assets/Campus  ($files C#-Dateien)
Build-Ziel:         $output

Weiter in Unity:
  1. Projekt öffnen (Unity Hub → Add project from disk, falls noch nicht eingetragen)
  2. Die Szene öffnen, die gebaut werden soll — im FEC-Projekt Assets/Scenes/Web.unity
  3. Campus → Anbindung in aktuelle Szene einfügen
  4. Play drücken zum Prüfen (die Brücke meldet sich selbst als demo_leitung an,
     Backend muss laufen), oder direkt: Campus → WebGL-Build erzeugen

Danach genügt:
  docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --force-recreate frontend
DONE
