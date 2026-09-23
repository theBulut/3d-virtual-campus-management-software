#!/usr/bin/env bash
# Restores the starting state of the study. Run before every session.
#
#   ./scripts/eval/eval-reset.sh              reset
#   ./scripts/eval/eval-reset.sh --backup     write a dump of the current state first
#
# Three steps, because a database reset alone is not enough:
#
#   1. Data      — the baseline SQL, deterministic delete-then-insert.
#   2. Sessions  — the study instance's Redis, so no token, token version or rate limit from the last
#                  session survives into the next one.
#   3. Audit     — the T8 entry, produced by a real API call. It cannot be written as SQL: the task asks
#                  a participant to read that entry, and a hand-written row would be fabricated evidence.
#
# Refuses to touch anything but the campus-eval project (see eval-env.sh). The development database is
# never a possible target.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/eval-env.sh"

EVAL_PASSWORD="${EVAL_PASSWORD:-eval-2026-campus}"
AUDIT_OFFER_TITLE="Audit-Testberatung"
AUDIT_ACTOR="eval_leitung"

eval_assert_target
eval_wait_for_api 60 || eval_die "Die API unter $EVAL_API antwortet nicht. Zuerst ./scripts/eval/eval-up.sh"

echo "Ziel: Projekt $EVAL_PROJECT, Datenbank $EVAL_DB ($EVAL_DB_CONTAINER)"

if [ "${1:-}" = "--backup" ]; then
    backup="$EVAL_DATA_DIR/backups/campus_eval_$(date +%Y%m%d-%H%M%S).sql"
    mkdir -p "$(dirname "$backup")"
    docker exec "$EVAL_DB_CONTAINER" pg_dump -U "$EVAL_DB_USER" -d "$EVAL_DB" > "$backup"
    echo "Sicherung: $backup"
fi

# --- 1. Data ---------------------------------------------------------------------------------------
echo -n "Datenbestand … "
eval_psql -q < "$EVAL_BASELINE_SQL"
echo "hergestellt."

# --- 2. Sessions -----------------------------------------------------------------------------------
# Only this project's Redis. Blacklisted tokens and rate limit counters from the previous session would
# otherwise decide who may log in during the next one.
echo -n "Sitzungen …    "
"${EVAL_COMPOSE[@]}" exec -T redis redis-cli FLUSHALL >/dev/null
echo "geleert."

# --- 3. Audit entry for T8 -------------------------------------------------------------------------
# One successful change to a weekly slot of the audit offer. CONSULTATION_UPDATED is the existing action
# code for slot changes as well, and since docs/DECISIONS.md D-49 the entry carries the id of the offer
# rather than the id of the slot — which is what makes it findable by the identifier on the task card.
echo -n "Audit-Beleg …  "
token="$(eval_token_for "$AUDIT_ACTOR" "$EVAL_PASSWORD")" \
    || eval_die "Anmeldung als $AUDIT_ACTOR fehlgeschlagen. Kennwort prüfen (EVAL_PASSWORD)."

# Values reach Python through the environment, not through string splicing: a quote in a title would
# otherwise end up as code.
read -r offer_id slot_id <<<"$(curl -sf "$EVAL_API/consultations" -H "Authorization: Bearer $token" |
    EVAL_TITLE="$AUDIT_OFFER_TITLE" python3 -c '
import os, sys, json
offers = json.load(sys.stdin)
match = [o for o in offers if o["titleDe"] == os.environ["EVAL_TITLE"]]
if not match:
    sys.exit("Angebot nicht gefunden")
offer = match[0]
if not offer["events"]:
    sys.exit("Angebot hat keine Sprechzeit")
print(offer["id"], offer["events"][0]["id"])')"

curl -sf -X PUT "$EVAL_API/consultations/events/$slot_id" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d '{"dayOfWeek":3,"startTime":"09:00:00","endTime":"11:00:00"}' >/dev/null \
    || eval_die "Die Sprechzeit konnte nicht geändert werden; ohne sie fehlt der T8-Beleg."

# Read the entry back rather than assuming it: the timestamp the participant reads comes from the
# application, and the reference has to be the value the interface actually shows.
entry="$(curl -sf "$EVAL_API/audit?resourceType=CONSULTATION&size=50" -H "Authorization: Bearer $token" |
    EVAL_OFFER_ID="$offer_id" python3 -c '
import os, sys, json
page = json.load(sys.stdin)
rows = [r for r in page["content"]
        if r["resourceId"] == os.environ["EVAL_OFFER_ID"]
        and r["action"] == "CONSULTATION_UPDATED" and r["success"]]
if len(rows) != 1:
    sys.exit("Erwartet genau ein passendes Ereignis, gefunden %d" % len(rows))
r = rows[0]
print("\t".join([str(r["id"]), r["actorUsername"], r["createdAt"], str(r["success"])]))')" \
    || eval_die "Der Audit-Beleg ist nicht eindeutig auffindbar."
echo "erzeugt."

IFS=$'\t' read -r audit_id audit_actor audit_time audit_ok <<<"$entry"

# --- Verification ----------------------------------------------------------------------------------
echo
eval_psql -q -t -A -F' | ' -c "
SELECT 'Konten',        string_agg(username, ', ' ORDER BY username) FROM admin_user
UNION ALL SELECT 'POIs', string_agg(name_de || ' [' || status || ']', ', ' ORDER BY name_de) FROM poi
UNION ALL SELECT 'Angebote', string_agg(title_de, ', ' ORDER BY title_de) FROM consultation
UNION ALL SELECT 'Sprechzeiten', count(*)::text FROM consultation_event
UNION ALL SELECT 'Gebäude', string_agg(code || ' ' || street, ', ' ORDER BY code) FROM building
UNION ALL SELECT 'Auditzeilen', count(*)::text FROM audit_log;"

cat <<EOF

Sollreferenz für T8 — in Aufgabenblatt und Sollprotokoll übernehmen:
  Angebots-Kennung : $offer_id
  Ausführendes Konto: $audit_actor
  Zeitstempel (UTC) : $audit_time
  Ergebnis          : $( [ "$audit_ok" = "True" ] && echo "erfolgreich" || echo "PRÜFEN — nicht erfolgreich" )
  Audit-Zeilen-ID   : $audit_id

Der angezeigte Zeitstempel in der Oberfläche folgt der Zeitzone des Browsers; beim Abgleich der
Teilnehmerantwort die lokale Darstellung verwenden, nicht diesen UTC-Wert.

Ausgangszustand hergestellt. Browser: abmelden, Sitzungsdaten löschen, $EVAL_APP neu laden.
EOF
