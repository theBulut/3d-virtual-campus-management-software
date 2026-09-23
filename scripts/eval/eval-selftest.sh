#!/usr/bin/env bash
# Proof that the prepared environment carries all nine tasks, and that the reset restores it.
#
#   ./scripts/eval/eval-selftest.sh
#
# Performs T1-T7, T9 through the API as the role each task prescribes, takes a snapshot after every one,
# produces a report, then resets and checks the starting state again. T8 changes no data; it is covered
# by the reference the reset prints.
#
# Deliberately through the API and not through SQL: the point is to show that the prepared accounts
# really hold the permissions the tasks need. A direct database write would prove nothing about that —
# it would bypass exactly the authorisation the study relies on.
#
# This is a technical check of the preparation. It says nothing about usability, and nothing about the
# application being free of defects.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/eval-env.sh"

EVAL_PASSWORD="${EVAL_PASSWORD:-eval-2026-campus}"
CODE="SELFTEST"
RUN="$(dirname "${BASH_SOURCE[0]}")/eval-run"

eval_assert_target
eval_wait_for_api 60 || eval_die "Die API antwortet nicht."

pass=0
fail=0
check() {
    if [ "$2" = "$3" ]; then
        printf '  ok   %s\n' "$1"; pass=$((pass + 1))
    else
        printf '  FEHL %s — erwartet %s, erhalten %s\n' "$1" "$3" "$2"; fail=$((fail + 1))
    fi
}

api() {  # api <token> <method> <path> [body]
    local token="$1" method="$2" path="$3" body="${4:-}"
    if [ -n "$body" ]; then
        curl -sf -X "$method" "$EVAL_API$path" -H "Authorization: Bearer $token" \
            -H 'Content-Type: application/json' -d "$body"
    else
        curl -sf -X "$method" "$EVAL_API$path" -H "Authorization: Bearer $token"
    fi
}

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }

echo "== Ausgangszustand herstellen =="
"$(dirname "${BASH_SOURCE[0]}")/eval-reset.sh" >/dev/null
"$RUN" start --participant "$CODE" --force

echo
echo "== Anmeldung aller vorbereiteten Konten =="
for account in eval_admin eval_leitung eval_mitarbeit eval_personal eval_betrieb eval_wechsel; do
    if token="$(eval_token_for "$account" "$EVAL_PASSWORD" 2>/dev/null)" && [ -n "$token" ]; then
        printf '  ok   %s\n' "$account"; pass=$((pass + 1))
    else
        printf '  FEHL %s kann sich nicht anmelden\n' "$account"; fail=$((fail + 1))
    fi
done

LEITUNG="$(eval_token_for eval_leitung "$EVAL_PASSWORD")"
MITARBEIT="$(eval_token_for eval_mitarbeit "$EVAL_PASSWORD")"
PERSONAL="$(eval_token_for eval_personal "$EVAL_PASSWORD")"
ADMIN="$(eval_token_for eval_admin "$EVAL_PASSWORD")"
BETRIEB="$(eval_token_for eval_betrieb "$EVAL_PASSWORD")"

echo
echo "== T1 Konto anlegen (PROJEKTLEITER) =="
created="$(api "$LEITUNG" POST /users '{"username":"eval_neuzugang","email":"eval_neuzugang@example.org",
    "firstName":"Test","lastName":"Neuzugang","organisation":"Evaluationsteam",
    "roles":["PROJEKTMITARBEITER"]}')"
check "Konto angelegt" "$(printf '%s' "$created" | jget "['user']['username']")" "eval_neuzugang"
"$RUN" checkpoint --task T1 --participant "$CODE" --force >/dev/null

echo
echo "== T2 Rolle umstellen (PROJEKTLEITER) =="
wechsel_id="$(api "$LEITUNG" GET "/users?search=eval_wechsel" | jget "['content'][0]['id']")"
api "$LEITUNG" POST "/users/$wechsel_id/roles" '{"roleName":"PERSONAL"}' >/dev/null
api "$LEITUNG" DELETE "/users/$wechsel_id/roles/PROJEKTMITARBEITER" >/dev/null
check "Rollenmenge" "$(api "$LEITUNG" GET "/users/$wechsel_id/roles" | jget "")" "['PERSONAL']"
"$RUN" checkpoint --task T2 --participant "$CODE" --force >/dev/null

echo
echo "== T3 POI anlegen und einreichen (PROJEKTMITARBEITER) =="
building_id="$(api "$MITARBEIT" GET /buildings | python3 -c '
import sys, json
print([b["id"] for b in json.load(sys.stdin) if b["code"] == "EVAL|01"][0])')"
poi="$(api "$MITARBEIT" POST /pois "{\"nameDe\":\"Infopunkt Evaluation\",
    \"nameEn\":\"Evaluation information point\",
    \"descriptionDe\":\"Informationen zum Beratungsangebot im Raum 212.\",
    \"descriptionEn\":\"Information about the advice service in room 212.\",
    \"category\":\"SERVICE\",\"buildingId\":$building_id,
    \"positionX\":0.0,\"positionY\":0.0,\"positionZ\":0.0}")"
poi_id="$(printf '%s' "$poi" | jget "['id']")"
check "POI Status" "$(api "$MITARBEIT" POST "/pois/$poi_id/submit" | jget "['status']")" "IN_REVIEW"
"$RUN" checkpoint --task T3 --participant "$CODE" --force >/dev/null

echo
echo "== T4 Zurückweisen mit Begründung (PROJEKTLEITER) =="
pruefung_id="$(api "$LEITUNG" GET "/pois?size=50" | python3 -c '
import sys, json
print([p["id"] for p in json.load(sys.stdin)["content"] if p["nameDe"] == "Beratungspunkt Prüfung"][0])')"
# One line: a line break inside a JSON string is not valid JSON and the request comes back as 400.
rejected="$(api "$LEITUNG" POST "/pois/$pruefung_id/reject" '{"reviewNote":"Die englische Beschreibung nennt Raum 214. Richtig ist Raum 212 wie in der deutschen Fassung."}')"
check "POI Status" "$(printf '%s' "$rejected" | jget "['status']")" "DRAFT"
"$RUN" checkpoint --task T4 --participant "$CODE" --force >/dev/null

echo
echo "== T5 Korrektur und erneutes Einreichen (PROJEKTMITARBEITER) =="
korrektur_id="$(api "$MITARBEIT" GET "/pois?size=50" | python3 -c '
import sys, json
print([p["id"] for p in json.load(sys.stdin)["content"] if p["nameDe"] == "Beratungspunkt Korrektur"][0])')"
api "$MITARBEIT" PUT "/pois/$korrektur_id" "{\"nameDe\":\"Beratungspunkt Korrektur\",
    \"nameEn\":\"Advice point correction\",\"descriptionDe\":\"Beratung im Raum 212.\",
    \"descriptionEn\":\"Advice in room 212.\",\"category\":\"SERVICE\",\"buildingId\":$building_id,
    \"positionX\":0.0,\"positionY\":0.0,\"positionZ\":0.0}" >/dev/null
check "POI Status" "$(api "$MITARBEIT" POST "/pois/$korrektur_id/submit" | jget "['status']")" "IN_REVIEW"
"$RUN" checkpoint --task T5 --participant "$CODE" --force >/dev/null

echo
echo "== T6 Freigeben (PROJEKTLEITER) =="
freigabe_id="$(api "$LEITUNG" GET "/pois?size=50" | python3 -c '
import sys, json
print([p["id"] for p in json.load(sys.stdin)["content"] if p["nameDe"] == "Beratungspunkt Freigabe"][0])')"
check "POI Status" "$(api "$LEITUNG" POST "/pois/$freigabe_id/publish" | jget "['status']")" "PUBLISHED"
"$RUN" checkpoint --task T6 --participant "$CODE" --force >/dev/null

echo
echo "== T7 Sprechzeiten pflegen (PERSONAL) =="
offer="$(api "$PERSONAL" GET /consultations | python3 -c '
import sys, json
o = [c for c in json.load(sys.stdin) if c["titleDe"] == "Studienberatung Evaluation"][0]
tue = [e["id"] for e in o["events"] if e["dayOfWeek"] == 2][0]
thu = [e["id"] for e in o["events"] if e["dayOfWeek"] == 4][0]
print(o["id"], tue, thu)')"
read -r offer_id tue_id thu_id <<<"$offer"
api "$PERSONAL" PUT "/consultations/events/$tue_id" \
    '{"dayOfWeek":2,"startTime":"11:00:00","endTime":"13:00:00"}' >/dev/null
api "$PERSONAL" DELETE "/consultations/events/$thu_id" >/dev/null
api "$PERSONAL" POST "/consultations/$offer_id/events" \
    '{"dayOfWeek":5,"startTime":"09:00:00","endTime":"10:00:00"}' >/dev/null
check "Wochenplan" "$(api "$PERSONAL" GET "/consultations/$offer_id" | python3 -c '
import sys, json
print(sorted((e["dayOfWeek"], e["startTime"][:5], e["endTime"][:5])
             for e in json.load(sys.stdin)["events"]))')" \
    "[(2, '11:00', '13:00'), (5, '09:00', '10:00')]"
"$RUN" checkpoint --task T7 --participant "$CODE" --force >/dev/null

echo
echo "== T9 Gebäudestammdaten (ADMIN) =="
eval2="$(api "$ADMIN" GET /buildings | python3 -c '
import sys, json
b = [x for x in json.load(sys.stdin) if x["code"] == "EVAL|02"][0]
print(json.dumps(b))')"
eval2_id="$(printf '%s' "$eval2" | jget "['id']")"
api "$ADMIN" PUT "/buildings/$eval2_id" "$(printf '%s' "$eval2" | python3 -c '
import sys, json
b = json.load(sys.stdin)
b["nameEn"] = "Evaluation advisory centre"
b["street"] = "Testweg 12"
print(json.dumps(b))')" >/dev/null
check "Gebäude" "$(api "$ADMIN" GET "/buildings/$eval2_id" | jget "['nameEn'] + ' / ' + d['street']")" \
    "Evaluation advisory centre / Testweg 12"
"$RUN" checkpoint --task T9 --participant "$CODE" --force >/dev/null

echo
echo "== T8 Audit lesen (MAINTENANCE_DEV), verändert nichts =="
visible="$(api "$BETRIEB" GET "/audit?size=50" | python3 -c '
import sys, json
page = json.load(sys.stdin)
rows = [r for r in page["content"]
        if r["resourceType"] == "CONSULTATION" and r["action"] == "CONSULTATION_UPDATED"
        and r["success"] and r["actorUsername"] == "eval_leitung"]
print(len(rows), rows[0]["resourceId"] if rows else "-")')"
read -r audit_count audit_resource <<<"$visible"
check "T8-Beleg in den sichtbaren 50 Zeilen" "$audit_count" "1"
check "Beleg trägt die Angebotskennung" "$audit_resource" "2"
"$RUN" checkpoint --task T8 --participant "$CODE" --force >/dev/null

echo
echo "== Bericht =="
observations="$EVAL_DATA_DIR/$CODE/beobachtung.csv"
mkdir -p "$(dirname "$observations")"
cat > "$observations" <<'CSV'
task,begonnen,endgrund,hoechste_hilfe,uebernahme,stoerung,manuelle_pruefung,t8_antwort_konto,t8_antwort_zeit,t8_antwort_ergebnis,manuelles_ergebnis,begruendung,dauer_s,bedienfehler,kritisch,post_task
T1,ja,fertig,H0,nein,nein,,,,,,,120,0,0,
T2,ja,fertig,H0,nein,nein,,,,,,,90,0,0,
T3,ja,fertig,H0,nein,nein,erfuellt,,,,,,240,0,0,
T4,ja,fertig,H2,nein,nein,erfuellt,,,,,,200,0,0,
T5,ja,fertig,H0,nein,nein,,,,,,,180,0,0,
T6,ja,fertig,H0,nein,nein,,,,,,,110,0,0,
T7,ja,fertig,H0,nein,nein,,,,,,,300,0,0,
T9,ja,fertig,H0,nein,nein,,,,,,,150,0,0,
T8,ja,fertig,H0,nein,nein,erfuellt,eval_leitung,lokal geprüft,erfolgreich,,,170,0,0,
CSV
"$RUN" report --participant "$CODE" --observations "$observations" | sed -n '1,30p'

echo
echo "== Reset und erneute Prüfung des Ausgangszustands =="
"$(dirname "${BASH_SOURCE[0]}")/eval-reset.sh" >/dev/null

check "Konten" "$(eval_psql -t -A -c "SELECT count(*) FROM admin_user")" "6"
check "eval_neuzugang entfernt" \
    "$(eval_psql -t -A -c "SELECT count(*) FROM admin_user WHERE username='eval_neuzugang'")" "0"
check "eval_wechsel wieder PROJEKTMITARBEITER" \
    "$(eval_psql -t -A -c "SELECT r.name FROM user_role ur JOIN role r ON r.id=ur.role_id
        JOIN admin_user u ON u.id=ur.user_id WHERE u.username='eval_wechsel'")" "PROJEKTMITARBEITER"
check "Infopunkt entfernt" \
    "$(eval_psql -t -A -c "SELECT count(*) FROM poi WHERE name_de='Infopunkt Evaluation'")" "0"
check "POI-Status zurückgestellt" \
    "$(eval_psql -t -A -c "SELECT string_agg(status, ',' ORDER BY name_de) FROM poi")" \
    "IN_REVIEW,DRAFT,IN_REVIEW"
check "Rückmeldung wieder am Korrekturfall" \
    "$(eval_psql -t -A -c "SELECT count(*) FROM poi WHERE name_de='Beratungspunkt Korrektur'
        AND review_note IS NOT NULL")" "1"
check "Sprechzeiten zurückgestellt" \
    "$(eval_psql -t -A -c "SELECT string_agg(day_of_week || ':' || to_char(start_time,'HH24MI'), ','
        ORDER BY day_of_week) FROM consultation_event e JOIN consultation c ON c.id=e.consultation_id
        WHERE c.title_de='Studienberatung Evaluation'")" "2:1000,4:1400"
check "Gebäudeadresse zurückgestellt" \
    "$(eval_psql -t -A -c "SELECT name_en || ' / ' || street FROM building WHERE code='EVAL|02'")" \
    "Evaluation advice building / Testweg 1"
check "T8-Kennung unverändert" \
    "$(eval_psql -t -A -c "SELECT id FROM consultation WHERE title_de='Audit-Testberatung'")" "2"

echo
echo "Ergebnis: $pass Prüfungen bestanden, $fail fehlgeschlagen."
[ "$fail" -eq 0 ] || exit 1
