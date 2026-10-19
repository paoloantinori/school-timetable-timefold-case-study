#!/usr/bin/env python3
"""Compress teacher availability into the concise two-level model (TASK-445 Tappa 3).

The seed ``availability_rules.csv`` (42 rows) is a flat list of per-(teacher, day)
slot closures, mostly declaring the COMPLEMENT of the real rule ("closed Mon/Tue/
Wed/Fri" to mean "only Thursday"). The proposal (section 3.6) reorganizes it into:

- ``06_DocentiDisponibilita`` - one row per concise recurring rule
  (SOLO_GIORNI / MAI_GIORNI / SOLO_FASCIA / FINESTRA) plus the weekly-count rules
  (MAX_INIZI_PRESTO / MIN_GIORNI_LIBERI) that today live as teachers.csv columns.
- ``07_DocentiEccezioni`` - the rare DATED exceptions (a visit, a week of leave).

The correctness gate (:mod:`test_availability_roundtrip`) is the bidirectional
equivalence maintainer defined: ``compress`` then ``expand`` must reproduce the exact
same set of (teacher, weekday, closed-slots, forza) closures for the recurring
rows. A single differing closure fails the gate with exit != 0. A recurring
closure that fits none of the concise forms is NOT forced into a lying rule and
is NOT silently moved to 07 (07's schema is dated; a recurring closure has no
date): it is a vocabulary gap, and the gate fails loud so the 06 vocabulary gets
extended. (In 2026/27 every recurring closure fits a concise form.)

Slot model (from 02_Campanella): 8 slots, mattino = 0-5 (08:00-14:00), pomeriggio
= 6-7 (14:30-16:30). A VACATION row closes its [minSlot, maxSlot]; PREFERENCE is
the same structure with forza = PREFERENZA instead of VINCOLO.
"""

from __future__ import annotations

from collections import defaultdict

import sources as S

# Weekday axes reused from the shared constants: shared.WEEKDAY_ORDER (English)
# via sources, and sources.CALENDAR_WEEKDAYS (Italian). The EN->IT map is their zip.
WEEKDAYS_EN = S.WEEKDAY_ORDER
WEEKDAYS_IT = S.CALENDAR_WEEKDAYS
DAY_EN_TO_IT = dict(zip(WEEKDAYS_EN, WEEKDAYS_IT))
DAY_ORDER = {d: i for i, d in enumerate(WEEKDAYS_IT)}  # chronological, for human-facing day lists
DAY_IT_WORD = {"LUN": "lunedi", "MAR": "martedi", "MER": "mercoledi",
               "GIO": "giovedi", "VEN": "venerdi"}

# Slot model DERIVED from 02_Campanella (sources.CAMPANELLA_SLOTS), not re-hardcoded:
# the mattino/pomeriggio boundary is the lunch break (audit bug 4), so it must flow
# from the campanella. Compress and expand share these sets, so a stale literal here
# is exactly the one drift the round-trip gate cannot catch.
ALL_SLOTS = frozenset(s[0] for s in S.CAMPANELLA_SLOTS)
MATTINO = frozenset(s[0] for s in S.CAMPANELLA_SLOTS if s[3] == "mattino")
POMERIGGIO = frozenset(s[0] for s in S.CAMPANELLA_SLOTS if s[3] == "pomeriggio")

FORZA_VINCOLO = "VINCOLO"
FORZA_PREFERENZA = "PREFERENZA"
# Exhaustive over AvailabilityType {VACATION, SICK, PREFERENCE}: a future value must
# fail loud, not silently land in the soft tier. SRC: domain/AvailabilityType.java.
TYPE_TO_FORZA = {"VACATION": FORZA_VINCOLO, "SICK": FORZA_VINCOLO, "PREFERENCE": FORZA_PREFERENZA}
# Dated-exception tipo (proposal 3.7): hard absences -> ASSENTE, soft -> PREFERISCE_LIBERO.
TYPE_TO_TIPO_07 = {"VACATION": "ASSENTE", "SICK": "ASSENTE", "PREFERENCE": "PREFERISCE_LIBERO"}

ORIGINE_SISTEMA = S.ORIGINE_SISTEMA


# --- load + classify -------------------------------------------------------

def _load_raw() -> list[dict]:
    """availability_rules.csv rows (strict seed read via sources.load_availability_rules,
    which enforces the column-count guard)."""
    return S.load_availability_rules()


def _is_dated(row: dict) -> bool:
    return bool((row.get("effectiveFrom") or "").strip() or (row.get("effectiveTo") or "").strip())


def _closed_slots(row: dict) -> frozenset:
    return frozenset(range(int(row["minSlot"]), int(row["maxSlot"]) + 1))


# --- the closure map (teacher, weekday, forza) -> frozenset(closed) --------

def recurring_closures() -> dict:
    """Closures from NON-dated rows, keyed (teacher, day_it, forza)."""
    out = {}
    for row in _load_raw():
        if _is_dated(row):
            continue
        day = DAY_EN_TO_IT[row["dayOfWeek"]]
        forza = TYPE_TO_FORZA[row["type"]]
        key = (row["teacher"].strip(), day, forza)
        out[key] = out.get(key, frozenset()) | _closed_slots(row)
    return out


# --- disponibile classification -> concise form ----------------------------

def _classify_disponibile(disp: list[int]):
    """Available slots (sorted) -> (form, fascia, slotMin, slotMax) or None.

    SOLO_FASCIA mattino/pomeriggio are the readable special cases; FINESTRA is the
    general single-window form; anything non-contiguous is not compressible.
    """
    if disp == sorted(MATTINO):
        return ("SOLO_FASCIA", "mattino", None, None)
    if disp == sorted(POMERIGGIO):
        return ("SOLO_FASCIA", "pomeriggio", None, None)
    if disp and disp == list(range(disp[0], disp[-1] + 1)):  # contiguous
        return ("FINESTRA", None, disp[0], disp[-1])
    return None


def _giorni_list(giorni: list[str]) -> str:
    """Italian day list, uniform across all rule types:
    'lunedi', 'lunedi e martedi', 'lunedi, martedi, mercoledi'."""
    words = [DAY_IT_WORD[d] for d in giorni]
    if len(words) <= 1:
        return "".join(words)
    if len(words) == 2:
        return f"{words[0]} e {words[1]}"
    return ", ".join(words)


def _giorni_phrase(tipo: str, giorni: list[str], fascia, valore) -> str:
    """Italian 'regola' token (sugar) from the structured columns."""
    days = _giorni_list(giorni)
    if tipo == "SOLO_GIORNI":
        return f"solo {days}"
    if tipo == "MAI_GIORNI":
        return f"mai {days}"
    if tipo == "SOLO_FASCIA":
        return f"solo {fascia} {days}"
    if tipo == "FINESTRA":
        return f"finestra {days}"
    if tipo == "MAX_INIZI_PRESTO":
        return f"max {valore} giorno con inizio presto/settimana"
    if tipo == "MIN_GIORNI_LIBERI":
        return f"almeno {valore} giorni liberi/settimana"
    return ""


def _rule(teacher, tipo, giorni, forza, fascia="", slot_min=None, slot_max=None, valore=None, note=""):
    return {
        "docente": teacher, "regola": _giorni_phrase(tipo, giorni, fascia, valore),
        "tipo": tipo, "giorni": ";".join(giorni), "fascia": fascia or "",
        "slotMin": slot_min if slot_min is not None else "", "slotMax": slot_max if slot_max is not None else "",
        "valore": valore if valore is not None else "", "forza": forza,
        "validoDa": "", "validoA": "", "origine": ORIGINE_SISTEMA, "note": note,
    }


# --- compress --------------------------------------------------------------

def compress() -> tuple[list[dict], list[tuple]]:
    """Recurring closures -> (rules_06, noncompressible_vocabulary_gaps).

    noncompressible is a list of (teacher, day_it, forza, closed) for recurring
    closures that fit no concise form (none in 2026/27). They are NOT moved to 07
    (07's schema is dated); the round-trip gate fails loud on them so the 06
    vocabulary gets extended rather than the closure silently lost.
    """
    closed = recurring_closures()
    by_tf = defaultdict(dict)  # (teacher, forza) -> {day_it: frozenset}
    for (teacher, day, forza), c in closed.items():
        by_tf[(teacher, forza)][day] = c

    rules: list[dict] = []
    noncomp: list[tuple] = []
    for (teacher, forza), daymap in by_tf.items():
        full = sorted((d for d, c in daymap.items() if c == ALL_SLOTS), key=DAY_ORDER.get)
        partial = {d: c for d, c in daymap.items() if c != ALL_SLOTS}
        open_days = [d for d in WEEKDAYS_IT if d not in daymap]

        # FULL-day closures. SOLO_GIORNI is a complement rule ("only these days
        # open") and is HONEST only when no partial day exists for this teacher/forza
        # (else it would fully close a day that should stay partially closed); that
        # honesty guard is also asserted per-rule by the round-trip gate. MAI_GIORNI
        # ("closed these days") is always honest. Among honest choices, the brevity
        # heuristic picks the form that lists fewer days.
        if full:
            if not partial and open_days and len(open_days) <= len(full):
                rules.append(_rule(teacher, "SOLO_GIORNI", open_days, forza, fascia="giornata"))
            else:
                rules.append(_rule(teacher, "MAI_GIORNI", full, forza, fascia="giornata"))

        # PARTIAL closures: group identical closed-sets, classify disponibile.
        by_closed = defaultdict(list)
        for day, c in partial.items():
            by_closed[c].append(day)
        for cset, days in by_closed.items():
            disp = sorted(ALL_SLOTS - set(cset))
            form = _classify_disponibile(disp)
            if form is None:
                for day in sorted(days):
                    noncomp.append((teacher, day, forza, frozenset(cset)))
            else:
                tipo, fascia, smin, smax = form
                rules.append(_rule(teacher, tipo, sorted(days, key=DAY_ORDER.get), forza, fascia=fascia,
                                   slot_min=smin, slot_max=smax))

    return rules, noncomp


# --- expand (the inverse) --------------------------------------------------

def expand(rules: list[dict]) -> dict:
    """Rules -> closures keyed (teacher, day_it, forza) -> frozenset(closed).

    Weekly-count rules (MAX_INIZI_PRESTO / MIN_GIORNI_LIBERI) expand to nothing:
    they map to Teacher fields, not RecurringAvailabilityRule closures."""
    out = {}
    for r in rules:
        teacher = r["docente"]
        forza = r["forza"]
        giorni = [d for d in r["giorni"].split(";") if d] if r["giorni"] else []
        tipo = r["tipo"]
        # Each branch resolves to (target days, closed-slot frozenset); the
        # application is identical, so it is unified once at the bottom.
        if tipo == "SOLO_GIORNI":
            listed = set(giorni)
            target = [d for d in WEEKDAYS_IT if d not in listed]
            closed = ALL_SLOTS
        elif tipo == "MAI_GIORNI":
            target, closed = giorni, ALL_SLOTS
        elif tipo == "SOLO_FASCIA":
            target = giorni
            closed = MATTINO if r["fascia"] == "pomeriggio" else POMERIGGIO
        elif tipo == "FINESTRA":
            a, b = int(r["slotMin"]), int(r["slotMax"])
            target = giorni
            closed = frozenset(range(0, a)) | frozenset(range(b + 1, max(ALL_SLOTS) + 1))
        else:
            continue  # count rules (MAX_INIZI_PRESTO/MIN_GIORNI_LIBERI): no closure
        for day in target:
            out[(teacher, day, forza)] = closed
    return out


# --- weekly-count rules (from teachers.csv, -> 06, not part of the closure RT) -

def weekly_count_rules() -> list[dict]:
    """MAX_INIZI_PRESTO / MIN_GIORNI_LIBERI rows from teachers.csv columns.

    These migrate to 06 as rows (proposal 3.6); they feed Teacher fields
    (maxEarlyStartDaysPerWeek / requiredFreeDaysPerWeek), not closures, so they
    are outside the compress/expand closure round-trip."""
    rules = []
    for t in S.load_teachers():
        if t["maxEarlyStartDaysPerWeek"] not in ("", None):
            rules.append(_rule(t["short"], "MAX_INIZI_PRESTO", [], FORZA_VINCOLO,
                               valore=t["maxEarlyStartDaysPerWeek"]))
        if t["requiredFreeDaysPerWeek"] not in ("", None):
            rules.append(_rule(t["short"], "MIN_GIORNI_LIBERI", [], FORZA_VINCOLO,
                               valore=t["requiredFreeDaysPerWeek"]))
    return rules


# --- dated exceptions -> 07 ------------------------------------------------

def exceptions_07() -> list[dict]:
    """Dated rows -> 07_DocentiEccezioni (docente, dataDa, dataA, slotMin, slotMax,
    tipo, motivo, origine, note)."""
    out = []
    for row in _load_raw():
        if not _is_dated(row):
            continue
        if row["type"] not in TYPE_TO_TIPO_07:
            raise ValueError(f"tipo AvailabilityType non gestito per il foglio 07: "
                             f"{row['type']!r} (riga {row})")
        out.append({
            "docente": row["teacher"], "dataDa": row["effectiveFrom"] or row["effectiveTo"],
            "dataA": row["effectiveTo"] or row["effectiveFrom"],
            "slotMin": row["minSlot"], "slotMax": row["maxSlot"],
            "tipo": TYPE_TO_TIPO_07[row["type"]], "motivo": _motivo_from_note(row["note"]),
            "origine": ORIGINE_SISTEMA, "note": row["note"],
        })
    return out


def _motivo_from_note(note: str) -> str:
    note = note.lower()
    if "visita medica" in note:
        return "Visita medica"
    if "formazione" in note:
        return "Formazione"
    return "Assenza datata"
