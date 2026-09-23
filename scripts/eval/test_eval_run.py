#!/usr/bin/env python3
"""Acceptance cases for the grading logic of eval-run.

    python3 scripts/eval/test_eval_run.py

Synthetic snapshots only — no database, no session data. What is checked is the step from criteria and
observation to a result code, because that is where a mistake would quietly turn into a wrong success
figure in the thesis.

Every case from the handover document, section "Prüffälle und Abnahme des Werkzeugs".
"""

import importlib.util
import json
import sys
from pathlib import Path

spec = importlib.util.spec_from_loader(
    "eval_run", importlib.machinery.SourceFileLoader("eval_run", str(Path(__file__).with_name("eval-run"))))
er = importlib.util.module_from_spec(spec)
spec.loader.exec_module(er)

failures = []


def expect(name, actual, wanted):
    if actual == wanted:
        print(f"  ok   {name}")
    else:
        print(f"  FEHL {name} — erwartet {wanted!r}, erhalten {actual!r}")
        failures.append(name)


# ---------------------------------------------------------------------------------------------------
# Fixtures. The baseline mirrors R__seed_eval_baseline.sql; the variants below change one thing each.
# ---------------------------------------------------------------------------------------------------

def base_snapshot():
    return {
        "taken_at": "2026-09-23T08:00:00+00:00",
        "accounts": [
            {"username": "eval_wechsel", "email": "eval_wechsel@example.org", "first_name": "Wanda",
             "last_name": "Wechsel", "organisation": "Evaluationsteam", "is_active": True,
             "must_change_password": False, "roles": ["PROJEKTMITARBEITER"]},
        ],
        "buildings": [
            {"code": "EVAL|02", "name_de": "Beratungsgebäude Evaluation",
             "name_en": "Evaluation advice building", "street": "Testweg 1", "postal_code": "64289",
             "city": "Darmstadt", "latitude": 49.8731, "longitude": 8.6520,
             "model_ref": "eval_building_02", "is_published": True},
        ],
        "pois": [
            {"name_de": "Beratungspunkt Freigabe", "name_en": "Advice point approval",
             "description_de": "Beratung im Raum 212.", "description_en": "Advice in room 212.",
             "category": "SERVICE", "building_code": "EVAL|01", "position_x": 0, "position_y": 0,
             "position_z": 0, "status": "IN_REVIEW", "review_note": None,
             "owner": "eval_mitarbeit", "assigned_to": None},
        ],
        "consultations": [
            {"title_de": "Studienberatung Evaluation", "title_en": "Evaluation study advice",
             "organisation": "Evaluationsteam", "room": "212",
             "contact_email": "eval_beratung@example.org", "is_published": True,
             "building_code": "EVAL|01", "responsible": "eval_personal",
             "events": [{"day_of_week": 2, "start_time": "10:00", "end_time": "12:00",
                         "valid_from": None, "valid_to": None, "note": None},
                        {"day_of_week": 4, "start_time": "14:00", "end_time": "16:00",
                         "valid_from": None, "valid_to": None, "note": None}]},
        ],
        "audit": [],
    }


def with_t7_done(order_reversed=False):
    """Target state of T7. The reversed variant carries the same slots in the other row order."""
    snap = base_snapshot()
    events = [{"day_of_week": 2, "start_time": "11:00", "end_time": "13:00",
               "valid_from": None, "valid_to": None, "note": None},
              {"day_of_week": 5, "start_time": "09:00", "end_time": "10:00",
               "valid_from": None, "valid_to": None, "note": None}]
    snap["consultations"][0]["events"] = list(reversed(events)) if order_reversed else events
    return snap


def observation(**fields):
    row = {"begonnen": "ja", "endgrund": "fertig", "hoechste_hilfe": "H0", "uebernahme": "nein",
           "stoerung": "nein", "manuelle_pruefung": "", "manuelles_ergebnis": "", "begruendung": ""}
    row.update(fields)
    return row


def grade_task(task, before, after, obs, meta=None, evidence_missing=False):
    checks = (er.t8(before, after, meta or {}, obs) if task == "T8"
              else er.CRITERIA[task](before, after, meta or {}))
    return er.grade(task, er.state_of(checks), obs, evidence_missing)[0]


# ---------------------------------------------------------------------------------------------------

print("Zustandsbewertung")

base = base_snapshot()

expect("T7 vollständig erreicht → U",
       grade_task("T7", base, with_t7_done(), observation()), "U")

expect("T7 gleiche Termine in anderer Reihenfolge → U",
       grade_task("T7", base, with_t7_done(order_reversed=True), observation()), "U")

partial = with_t7_done()
partial["consultations"][0]["events"] = partial["consultations"][0]["events"][:1]
expect("T7 Teilziel fehlt → F", grade_task("T7", base, partial, observation()), "F")

expect("T7 erreicht nach H3 → A",
       grade_task("T7", base, with_t7_done(), observation(hoechste_hilfe="H3")), "A")

expect("T7 Sollzustand nach H4 → F",
       grade_task("T7", base, with_t7_done(), observation(hoechste_hilfe="H4")), "F")

expect("T7 Sollzustand nach Übernahme → F",
       grade_task("T7", base, with_t7_done(), observation(uebernahme="ja")), "F")

extra = with_t7_done()
extra["consultations"][0]["room"] = "999"
expect("T7 zusätzliche unerlaubte Sachänderung → F",
       grade_task("T7", base, extra, observation()), "F")

print("\nÜbersteuerung durch Protokoll")

expect("Störung schlägt passenden Datenzustand → X",
       grade_task("T7", base, with_t7_done(), observation(stoerung="ja")), "X")

expect("Nicht begonnen → N",
       grade_task("T7", base, with_t7_done(), observation(begonnen="nein")), "N")

expect("Fehlende Hilfeangabe → OFFEN",
       grade_task("T7", base, with_t7_done(), observation(hoechste_hilfe="")), "OFFEN")

expect("Fehlender Snapshot → OFFEN",
       grade_task("T7", base, base, observation(), evidence_missing=True), "OFFEN")

expect("Manuelles Ergebnis ohne Begründung → OFFEN",
       grade_task("T7", base, with_t7_done(), observation(manuelles_ergebnis="F")), "OFFEN")

expect("Manuelles Ergebnis mit Begründung wird übernommen",
       grade_task("T7", base, with_t7_done(),
                  observation(manuelles_ergebnis="F", begruendung="falscher Datensatz bearbeitet")), "F")

print("\nManuelle Sachprüfung (T4: Begründung und Protokoll)")

t4_base = base_snapshot()
t4_base["pois"][0].update({"name_de": "Beratungspunkt Prüfung", "status": "IN_REVIEW",
                           "review_note": None})
t4_done = json.loads(json.dumps(t4_base))
t4_done["pois"][0].update({"status": "DRAFT", "review_note": "Englisch nennt 214, richtig ist 212."})

expect("T4 mit offener Sachprüfung → OFFEN",
       grade_task("T4", t4_base, t4_done, observation()), "OFFEN")

expect("T4 mit bestätigter Sachprüfung → U",
       grade_task("T4", t4_base, t4_done, observation(manuelle_pruefung="erfuellt")), "U")

expect("T4 mit verworfener Sachprüfung (unklare Begründung) → F",
       grade_task("T4", t4_base, t4_done, observation(manuelle_pruefung="nicht_erfuellt")), "F")

t4_unrejected = json.loads(json.dumps(t4_base))
t4_unrejected["pois"][0]["status"] = "PUBLISHED"
expect("T4 veröffentlicht statt zurückgewiesen → F",
       grade_task("T4", t4_base, t4_unrejected, observation(manuelle_pruefung="erfuellt")), "F")

print("\nT8: mündliche Antwort gegen die Referenz")

meta = {"t8_reference": {"resource_id": "2", "actor": "eval_leitung",
                         "created_at": "2026-09-23T08:00:00Z", "audit_id": 2}}
expect("T8 richtige Antwort → U",
       grade_task("T8", base, base,
                  observation(manuelle_pruefung="erfuellt", t8_antwort_konto="eval_leitung",
                              t8_antwort_ergebnis="erfolgreich", t8_antwort_zeit="lokal geprüft"),
                  meta), "U")

expect("T8 falsches Konto genannt → F",
       grade_task("T8", base, base,
                  observation(manuelle_pruefung="erfuellt", t8_antwort_konto="eval_admin",
                              t8_antwort_ergebnis="erfolgreich", t8_antwort_zeit="lokal geprüft"),
                  meta), "F")

expect("T8 ohne erfasste Antwort → F",
       grade_task("T8", base, base, observation(manuelle_pruefung="erfuellt"), meta), "F")

t8_changed = with_t7_done()
expect("T8 hat Daten verändert → F",
       grade_task("T8", base, t8_changed,
                  observation(manuelle_pruefung="erfuellt", t8_antwort_konto="eval_leitung",
                              t8_antwort_ergebnis="erfolgreich", t8_antwort_zeit="lokal geprüft"),
                  meta), "F")

print("\nKennzahlen")


def rates(counts):
    judgeable = counts["U"] + counts["A"] + counts["F"]
    if judgeable == 0:
        return "nicht berechenbar"
    return (round(counts["U"] / judgeable * 100, 1),
            round((counts["U"] + counts["A"]) / judgeable * 100, 1))


expect("Nenner 0 → nicht berechenbar",
       rates({"U": 0, "A": 0, "F": 0, "X": 5, "N": 4}), "nicht berechenbar")
expect("6 U, 1 A, 2 F", rates({"U": 6, "A": 1, "F": 2}), (66.7, 77.8))
expect("X und N zählen nicht in den Nenner", rates({"U": 4, "A": 0, "F": 0, "X": 3, "N": 2}),
       (100.0, 100.0))

print("\nBerichtstext")

summary = {"studiencode": "TEST", "kriterien_version": "1.0.0", "commit": "abc1234",
           "ausgangszustand": "2026-09-23T08:00:00+00:00", "erstellt": "2026-09-23T09:00:00+00:00",
           "zaehlungen": {"U": 3, "A": 1, "F": 1, "X": 0, "N": 0, "OFFEN": 1},
           "n_beurteilbar": 5, "quote_ohne_hilfe": 60.0, "quote_mit_hilfe": 80.0,
           "vorlaeufig": True, "aufgaben": []}
text = er.render_markdown(summary)
expect("Offene Fälle werden als vorläufig gekennzeichnet", "Vorläufig" in text, True)
expect("Kein fertiger Ergebnissatz solange etwas offen ist", "Satz zur Übernahme" in text, False)

summary["vorlaeufig"] = False
summary["zaehlungen"]["OFFEN"] = 0
expect("Ergebnissatz erst ohne offene Fälle", "Satz zur Übernahme" in er.render_markdown(summary), True)

summary["n_beurteilbar"] = 0
summary["zaehlungen"] = {"U": 0, "A": 0, "F": 0, "X": 9, "N": 0, "OFFEN": 0}
expect("Nenner 0 ohne Prozentzahl im Bericht",
       "nicht berechenbar" in er.render_markdown(summary), True)

print()
if failures:
    print(f"{len(failures)} Prüfungen fehlgeschlagen: {', '.join(failures)}")
    sys.exit(1)
print("Alle Prüffälle bestanden.")
