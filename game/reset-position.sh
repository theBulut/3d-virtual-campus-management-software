#!/usr/bin/env bash
# Clears the saved position of one account's game state.
#
# Why this exists: GameStateClient teleports the player to the stored position on load and autosaves
# every 30 seconds. Fall out of the world once and the bad position is persisted — every later start
# drops you back into the void. There is no DELETE endpoint (the API has only GET and PUT on
# /api/game/state), so the fix is to write the state back without a position key.
#
# Leaving the key out rather than sending null is deliberate: Unity's JsonUtility maps an absent key
# to the field's default, which for a class field is null, and SceneLoader then skips the teleport.
# The player spawns wherever the scene puts them.
#
# Usage:  ./game/reset-position.sh demo_leitung [passwort] [api-basis]
set -euo pipefail

USER="${1:?Benutzername fehlt — z. B. ./game/reset-position.sh demo_leitung}"
PASS="${2:-demo-passwort}"
API="${3:-http://localhost:8080/api}"

token=$(curl -sf -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" |
    python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

before=$(curl -sf "$API/game/state" -H "Authorization: Bearer $token")
echo "vorher:  ${before:-<kein Spielstand>}"

# Keep everything except the position, so minutes played and visited buildings survive the reset.
payload=$(printf '%s' "${before:-\{\}}" | python3 -c '
import json, sys
try:
    state = json.load(sys.stdin)
except Exception:
    state = {}
state.pop("position", None)
state.setdefault("visitedBuildings", [])
state.setdefault("minutesPlayed", 0)
state.setdefault("savedAt", "")
print(json.dumps(state))')

curl -sf -X PUT "$API/game/state" -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "$payload" >/dev/null

echo "nachher: $(curl -sf "$API/game/state" -H "Authorization: Bearer $token")"
echo
echo "Fertig. Spiel neu laden — der Spieler startet am Spawn-Punkt der Szene."
echo "Wichtig: Das Spiel muss beim Zurücksetzen geschlossen sein, sonst überschreibt"
echo "der Autosave (alle 30 s) die Korrektur wieder."
