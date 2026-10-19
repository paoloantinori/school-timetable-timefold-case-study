#!/usr/bin/env python3
"""System-side validation controls for ``99_Validazione`` (TASK-445, Tappa 5).

The workbook's ``99_Validazione`` sheet is the *anticipo* of the defence: it
shows the school, in the moment they compile, the contradictions that would
break the solve. The proposal (section 4, nota di metodo) is explicit that the
SAME rules must also run *system-side at import*, because an Excel can always
arrive with formulas broken or replaced by static values. This module is that
system-side mirror.

Design rules (inherited from the Tappa 3/4 gate-blind-hole lessons):

1. **Controls read from the SEED sources** (``sources.py`` loaders and the
   constants transcribed from Java with provenance), never from the generated
   workbook. The workbook is not the defence; the seed is the input the school
   owns. Slot/weekday sets are reused from ``availability_compress`` (the one
   place the mattino/pomeriggio boundary lives) instead of re-derived, so a
   future change to the fascia derivation cannot desync this mirror.
2. A control returns a list of :class:`Finding` (the problems it caught); an
   empty list means that control is OK. The gate exits non-zero if any
   ``ERRORE`` finding survives. ``AVVISO`` findings are printed but never block.
3. **No silent OK.** A control that cannot run because its source is not wired
   yet is registered as DEFERRED with the reason it needs, so the gate printout
   shows it instead of hiding a hole behind a green tick.

Levels mirror the sheet: ``ERRORE`` (red, blocks delivery), ``AVVISO`` (yellow).
DEFERRED is a registry status (control not yet wired), surfaced as a Finding so
the summary lists it; a future Tappa 5b may lift it to its own type when more
controls are implemented and the registry is keyed by cid.
"""

from __future__ import annotations

import collections
import datetime
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import sources as S
import availability_compress as AC
import workbook_schema as WS

ERRORE = "ERRORE"
AVVISO = "AVVISO"
DEFERRED = "DEFERRED"  # control not yet wired; printed, never blocks, never OK


@dataclass(frozen=True)
class Finding:
    """One problem a control caught. ``level`` is ERRORE / AVVISO / DEFERRED."""

    cid: str
    level: str
    message: str


# The cids the 99_Validazione sheet defines (V1..V23 plus the V4 sub-controls),
# in sheet order. The summary denominator anchors here (a function list would
# undercount: V4 and V4b are emitted by one function over one population).
SHEET_CIDS = [
    "V1", "V2", "V3", "V4", "V4b", "V4c", "V5", "V6", "V7", "V8", "V9", "V10",
    "V11", "V12", "V13", "V14", "V15", "V16", "V17", "V18", "V19", "V20",
    "V21", "V22", "V23",
    # System-side structural checks (req 1/2/4): read the COMPILED workbook, not
    # the seed. V25/V26 land in their own commits.
    "V24",
    "V25",
    "V26",
]


def _real_incarichi() -> list[dict]:
    """Incarichi rows that are real Course assignments: a real teacher.

    PSEUDO_COURSE_TEACHERS already includes 'Totale ore corso' (it aliases
    shared.PSEUDO_TEACHERS), so the one membership test covers Stage/Esame/RA/
    Totale-ore-corso rows that are not teacher assignments."""
    return [r for r in S.load_incarichi() if r["teacher"] not in S.PSEUDO_COURSE_TEACHERS]


def _known_subjects() -> frozenset[str]:
    """The 08_Materie canonical names, plus the 09_Alias canonical forms they
    normalize to. An incarichi subject is 'mapped' iff its normalized form is a
    canonical subject (the same membership rule V5 and V10 share)."""
    canonical = {s["subject"] for s in S.load_subjects()}
    aliases_canonical = set(S.SUBJECT_CASE_NORMALIZATION.values())
    return canonical | aliases_canonical


def _weekday_occurrences() -> dict[str, int]:
    """Real occurrences of each Italian weekday (LUN..VEN) among the in-grid
    school days: net of closures, Saturdays excluded. This is the denominator
    V4 needs instead of nominal weeks (proposal: 36 real Thursdays, not 38)."""
    counts = collections.Counter(d.weekday() for d in S.school_year_dates())
    # AC.DAY_EN_TO_IT is the single EN->IT weekday map (zip of WEEKDAY_ORDER
    # over CALENDAR_WEEKDAYS); reuse it instead of rebuilding a third copy.
    return {AC.DAY_EN_TO_IT[en]: counts.get(i, 0) for i, en in enumerate(S.WEEKDAY_ORDER)}


# --- teacher capacity (the V4 chain, proposal section 'Il controllo di capacita') ---

# 05_Docenti.maxOreGiorno default when teachers.csv leaves it blank. SRC: the
# solver's Teacher.maxDailyHours default (CLAUDE.md H7 'default 6').
_DEFAULT_MAX_DAILY_HOURS = 6


def _max_daily_hours(teacher_short: str) -> int:
    rec = next((t for t in S.load_teachers() if t["short"] == teacher_short), None)
    # `is not None`, not truthiness: a maxDailyHours of 0 means "cannot teach"
    # (capacity 0); a truthiness test would silently turn it into the default 6.
    if rec and rec["maxDailyHours"] is not None:
        return rec["maxDailyHours"]
    return _DEFAULT_MAX_DAILY_HOURS


def _teacher_open_days_and_slots() -> dict[str, dict[str, int]]:
    """Per teacher -> {weekday_it: available_slot_count} for the days the
    teacher is actually open, derived from the compressed 06 rules.

    The chain (proposal): SOLO_GIORNI lists the OPEN days; MAI_GIORNI lists the
    CLOSED days; SOLO_FASCIA/FINESTRA reduce the slots of the days they name.
    A teacher with NO rule is open every weekday with the full 8 slots (this is
    the bug that cost a first draft: an unruled teacher is fully available, not
    capacity-0). Only VINCOLO rules constrain capacity; PREFERENZA is soft.
    """
    # Default: every known teacher open all weekdays with the full slot set.
    teachers = {t["short"] for t in S.load_teachers()} | {r["teacher"] for r in _real_incarichi()}
    slots = {t: {d: set(AC.ALL_SLOTS) for d in S.CALENDAR_WEEKDAYS} for t in teachers}

    rules, _noncomp = AC.compress()
    for r in rules:
        if r["forza"] != AC.FORZA_VINCOLO or r["docente"] not in slots:
            continue
        teacher = r["docente"]
        tipo = r["tipo"]
        giorni = [g for g in r["giorni"].split(";") if g] if r["giorni"] else []
        if tipo == "SOLO_GIORNI":
            open_set = set(giorni)
            for d in S.CALENDAR_WEEKDAYS:
                if d not in open_set:
                    slots[teacher].pop(d, None)  # closed
        elif tipo == "MAI_GIORNI":
            for d in giorni:
                slots[teacher].pop(d, None)  # closed
        elif tipo in ("SOLO_FASCIA", "FINESTRA"):
            # Both name days and intersect them with a kept-slot set; only the
            # keep computation differs. SOLO_FASCIA names the fascia the teacher
            # IS available in (so keep = that fascia) - this is the INVERSE of
            # AC.expand(), which computes the CLOSED slots; do not copy its
            # inverted expression (a first draft did and turned a 6-slot mattino
            # into a 2-slot pomeriggio, fabricating false over-capacity errors).
            if tipo == "SOLO_FASCIA":
                keep = AC.MATTINO if r["fascia"] == "mattino" else AC.POMERIGGIO
            else:
                keep = set(range(int(r["slotMin"]), int(r["slotMax"]) + 1))
            for d in giorni:
                if d in slots[teacher]:
                    slots[teacher][d] &= keep
        # MAX_INIZI_PRESTO / MIN_GIORNI_LIBERI are weekly counts, not day
        # closures: they do not change the open-day/slot map.
    return {t: {d: len(s) for d, s in days.items() if s} for t, days in slots.items()}


def _teacher_capacity_hours(open_map: dict[str, dict[str, int]]) -> dict[str, int]:
    """Per teacher -> annual capacity in hours = sum over open weekdays of
    real_occurrences(weekday) * min(maxOreGiorno, available_slots)."""
    occ = _weekday_occurrences()
    cap: dict[str, int] = {}
    for teacher, dayslots in open_map.items():
        mdh = _max_daily_hours(teacher)
        total = 0
        for wd, nslots in dayslots.items():
            total += occ.get(wd, 0) * min(mdh, nslots)
        cap[teacher] = total
    return cap


def _assigned_hours_by_teacher() -> dict[str, float]:
    out = collections.defaultdict(float)
    for r in _real_incarichi():
        out[r["teacher"]] += r["hours"] or 0.0
    return out


# --- the controls ----------------------------------------------------------
# Each returns list[Finding]. The acceptance test cases (proposal + handoff):
#   V1  G14 120 attese vs 115 assegnate, residuo 5
#   V3  G04 stage in-grid senza riga monte-ore (16_Stage)
#   V4b T01 231h / 288h = 80.2% (solo giovedi, AVVISO)


def v1_monte_ore_classe() -> list[Finding]:
    """V1 - Monte-ore classe: SUM(incarichi ore reali) + 16_Stage.oreTotaliAttese
    deve uguagliare 10_Classi.oreAnnueAttese (proposal formula). Lo stage conta
    qui perche' non e' un incarico-docente; Esame/RA (GAP-1) ancora non sono
    righe 12, quindi i loro residui (es. G11 +24h = Esame 24h) sono attesi
    finche' la scuola non li compila come tipo=esame|pool."""
    attese = S.ore_annue_attese()
    assigned = collections.defaultdict(float)
    for r in _real_incarichi():
        assigned[r["group"]] += r["hours"] or 0.0
    stage = collections.defaultdict(int)
    for _classe, _i, _f, _g, _r, ore in S.STAGE_PATTERNS:
        stage[_classe] += ore
    findings = []
    for group, expected in sorted(attese.items()):
        got = assigned.get(group, 0.0) + stage.get(group, 0)
        if abs(got - expected) > 0.5:
            findings.append(Finding(
                "V1", ERRORE,
                f"{group}: incarichi+stage {got:g}h contro oreAnnueAttese {expected:g}h "
                f"(residuo {expected - got:+g}h)",
            ))
    return findings


def v4_capacita_docente() -> list[Finding]:
    """V4 - Capacita' docente annua: ore assegnate non oltre la capacita'
    derivata dalle regole concise di 06. V4b emette AVVISO oltre l'80%."""
    cap = _teacher_capacity_hours(_teacher_open_days_and_slots())
    assigned = _assigned_hours_by_teacher()
    findings = []
    for teacher in sorted(set(cap) | set(assigned)):
        capacity = cap.get(teacher, 0)
        hours = assigned.get(teacher, 0.0)
        if capacity <= 0:
            if hours > 0:
                findings.append(Finding(
                    "V4", ERRORE,
                    f"{teacher}: {hours:g}h assegnate ma capacita' 0 (nessun giorno aperto)",
                ))
            continue
        if hours > capacity + 0.5:
            findings.append(Finding(
                "V4", ERRORE,
                f"{teacher}: {hours:g}h assegnate oltre capacita' {capacity}h "
                f"({hours / capacity:.0%})",
            ))
        elif hours / capacity > 0.80:
            findings.append(Finding(
                "V4b", AVVISO,
                f"{teacher}: saturazione {hours / capacity:.1%} ({hours:g}h / {capacity}h)",
            ))
    return findings


def v5_materie_non_mappate() -> list[Finding]:
    """V5 - Materie non mappate: ogni materia di un incarico reale deve essere
    in 08_Materie o normalizzare a un canonical (09 alias)."""
    known = _known_subjects()
    unknown = collections.defaultdict(int)
    for r in _real_incarichi():
        norm = S.normalize_subject(r["subject"])
        if norm not in known:
            unknown[r["subject"]] += 1
    return [
        Finding("V5", ERRORE, f"materia non mappata '{sub}' ({n} righe incarichi)")
        for sub, n in sorted(unknown.items())
    ]


def v6_date_fuori_anno() -> list[Finding]:
    """V6 - Date fuori anno: ogni data di 03 (chiusure), 04 (speciali),
    07 (eccezioni), 16 (stage inizio/fine) dentro [dataInizio, dataFine].
    Cattura il typo 2026-01-22 per 2027-01-22 (gia' corretto nel seed)."""
    start = datetime.date.fromisoformat(S.ANNO_DATA_INIZIO)
    end = datetime.date.fromisoformat(S.ANNO_DATA_FINE)
    sources_dates: list[tuple[str, str]] = []
    for d, _desc, _tipo in S.CLOSURES:
        sources_dates.append(("03_Chiusure", d))
    sources_dates.append(("04_GiorniSpeciali", S.GIORNI_SPECIALI_DATE))
    for r in S.load_availability_rules():
        if (r["effectiveFrom"] or "").strip():
            sources_dates.append(("07_Eccezioni", r["effectiveFrom"]))
        if (r["effectiveTo"] or "").strip():
            sources_dates.append(("07_Eccezioni", r["effectiveTo"]))
    for _classe, inizio, fine, _g, _r, _h in S.STAGE_PATTERNS:
        sources_dates.append(("16_Stage", inizio))
        sources_dates.append(("16_Stage", fine))
    findings = []
    for src, ds in sources_dates:
        try:
            d = datetime.date.fromisoformat(ds)
        except ValueError:
            findings.append(Finding("V6", ERRORE, f"{src}: data non parseabile '{ds}'"))
            continue
        if d < start or d > end:
            findings.append(Finding(
                "V6", ERRORE, f"{src}: {ds} fuori anno [{S.ANNO_DATA_INIZIO}..{S.ANNO_DATA_FINE}]",
            ))
    return findings


def v10_nomi_non_risolti() -> list[Finding]:
    """V10 - Nomi non risolti: ogni riferimento incrociato degli incarichi
    (docente, classe, materia) esiste nell'anagrafica (05/10/08)."""
    teachers = {t["short"] for t in S.load_teachers()}
    groups = {g["group"] for g in S.load_groups()}
    known_subjects = _known_subjects()
    bad_t, bad_g, bad_s = set(), set(), set()
    for r in _real_incarichi():
        if r["teacher"] not in teachers:
            bad_t.add(r["teacher"])
        if r["group"] not in groups:
            bad_g.add(r["group"])
        if S.normalize_subject(r["subject"]) not in known_subjects:
            bad_s.add(r["subject"])
    findings = []
    for t in sorted(bad_t):
        findings.append(Finding("V10", ERRORE, f"docente non in anagrafica: '{t}'"))
    for g in sorted(bad_g):
        findings.append(Finding("V10", ERRORE, f"classe non in anagrafica: '{g}'"))
    for s in sorted(bad_s):
        findings.append(Finding("V10", ERRORE, f"materia non in anagrafica: '{s}'"))
    return findings


def v11_chiavi_duplicate() -> list[Finding]:
    """V11 - Chiavi duplicate: (docente, materia, classe) in 12_Incarichi.
    Oggi sommate con un warning che nessuno legge."""
    seen = collections.Counter(
        (r["teacher"], r["subject"], r["group"]) for r in _real_incarichi()
    )
    return [
        Finding("V11", ERRORE, f"chiave duplicata (docente,materia,classe) = {key} ({n} volte)")
        for key, n in sorted(seen.items()) if n > 1
    ]


def v19_slot_contigui() -> list[Finding]:
    """V19 - Slot contigui: 02_Campanella numerata da 0 senza salti, orari
    crescenti."""
    slots = S.CAMPANELLA_SLOTS
    findings = []
    for i, (idx, start, _end, _fascia) in enumerate(slots):
        if idx != i:
            findings.append(Finding("V19", ERRORE, f"slot {i}: indice {idx} atteso {i}"))
    starts = [s[1] for s in slots]
    if starts != sorted(starts):
        findings.append(Finding("V19", ERRORE, "orari campanella non crescenti"))
    return findings


def v20_alias_canonici_disgiunti() -> list[Finding]:
    """V20 - Alias e canonici disgiunti: nessuna 09!grafia coincide con una
    08!materia (ciclo di normalizzazione)."""
    canonical = {s["subject"] for s in S.load_subjects()}
    grafie = set(S.SUBJECT_CASE_NORMALIZATION.keys())
    overlap = sorted(grafie & canonical)
    return [
        Finding("V20", ERRORE, f"grafia alias == materia canonica: '{g}'")
        for g in overlap
    ]


def v22_regole_non_contraddittorie() -> list[Finding]:
    """V22 - Regole non contraddittorie. Due controlli:
    - coesistenza SOLO_GIORNI + MAI_GIORNI per lo stesso docente: GUARDIA
      strutturale. AC.compress emette al piu' uno dei due per (docente,forza),
      percio' su questo mirror derivato non puo' mai scattare; resta come rete
      di sicurezza. La contraddizione vera si vedrebbe su un 06 compilato a
      mano dalla scuola, non sul mirror derivato da availability_rules.
    - disponibilita' risultante vuota (tutti i giorni chiusi): LIVE."""
    rules, _noncomp = AC.compress()
    open_map = _teacher_open_days_and_slots()  # hoist: deterministic, one build
    by_teacher = collections.defaultdict(list)
    for r in rules:
        if r["forza"] == AC.FORZA_VINCOLO:
            by_teacher[r["docente"]].append(r)
    findings = []
    for teacher, trules in sorted(by_teacher.items()):
        tipos = {r["tipo"] for r in trules}
        if {"SOLO_GIORNI", "MAI_GIORNI"} <= tipos:
            findings.append(Finding(
                "V22", ERRORE,
                f"{teacher}: convivono SOLO_GIORNI e MAI_GIORNI (contraddizione)",
            ))
        # resulting availability empty = every weekday fully closed
        if not open_map.get(teacher, {}):
            findings.append(Finding(
                "V22", ERRORE,
                f"{teacher}: nessun giorno aperto risultante (disponibilita' azzerata)",
            ))
    return findings


def v23_docenti_esterni_dichiarati() -> list[Finding]:
    """V23 - Docenti esterni dichiarati: ogni docente esterno (05!esterno=SI)
    ha almeno una regola in 06 (altrimenti verrebbe trattato come sempre
    disponibile). Una regola 06 e' una chiusura (compress) O un conteggio
    settimanale (weekly_count_rules: MAX_INIZI_PRESTO / MIN_GIORNI_LIBERI)."""
    ruled = {r["docente"] for r in AC.compress()[0]} | \
        {r["docente"] for r in AC.weekly_count_rules()}
    return [
        Finding("V23", AVVISO, f"docente esterno senza regole in 06: '{t}'")
        for t in sorted(S.DOCENTI_ESTERNI) if t not in ruled
    ]


def v3_stage_tripla_coerenza() -> list[Finding]:
    """V3 - Stage tripla coerenza (proposal): lo stage in piano
    (daily_activity_quota) deve conciliare con 16_Stage (STAGE_PATTERNS):
    - NESSUNA riga 16_Stage ma stage nel piano -> ERRORE (stage non dichiarato;
      e' il caso G04/G08/G09/G10/G13).
    - riga 16_Stage presente -> oreTotaliAttese deve == stage in-grid + off-grid
      (off-grid = oreFuoriGriglia, dichiarate in GAP-2). Mismatch -> ERRORE.

    Formula della conciliazione (interpretazione verificata sui 3 gruppi
    dichiarati, che la confermano): oreTotaliAttese == in-grid + off-grid.
    G12 112+183=295 e G11 287+8=295 tornano; G07 300+0 vs 295 e' un +5
    reale. Semantica DECISA nel task TASK-445 (nota Tappa 5b)."""
    q = S.daily_activity_quota()
    school_days = set(S.school_year_dates())
    stage_ingrid = collections.defaultdict(int)
    stage_offgrid = collections.defaultdict(int)
    for r in q:
        if r["stage"] > 0:
            (stage_ingrid if r["date"] in school_days else stage_offgrid)[r["group"]] += r["stage"]
    monte = collections.defaultdict(int)
    for c, _i, _f, _g, _r, ore in S.STAGE_PATTERNS:
        monte[c] += ore
    findings = []
    for g in sorted(set(stage_ingrid) | set(stage_offgrid) | set(monte)):
        ingrid = stage_ingrid.get(g, 0)
        offgrid = stage_offgrid.get(g, 0)
        m = monte.get(g, 0)
        if ingrid + offgrid == 0 and m == 0:
            continue
        if m == 0:
            findings.append(Finding(
                "V3", ERRORE,
                f"{g}: {ingrid}h stage in-grid (+{offgrid}h off-grid) nel piano ma "
                f"nessuna riga 16_Stage (dichiarare lo stage; off-grid = oreFuoriGriglia)",
            ))
        else:
            total = ingrid + offgrid
            if abs(total - m) > 0.5:
                findings.append(Finding(
                    "V3", ERRORE,
                    f"{g}: stage in piano {ingrid}h in-grid + {offgrid}h off-grid = {total}h "
                    f"non concilia con 16_Stage.oreTotaliAttese {m}h (delta {total - m:+})",
                ))
    return findings


def v13_capienza_aula() -> list[Finding]:
    """V13 - Capienza aula: per ogni materia con tagAula, la stanza piu' capiente
    con quel tag ha capienza >= iscritti della classe che la usa. AVVISO se sotto
    (lab capacity-starved, non ERRORE: la scuola puo' sdoppiare il gruppo)."""
    room_cap = {r["room"]: r["capacity"] for r in S.load_rooms()}
    tag_maxcap: dict[str, int] = {}
    for rm, tag in S.ROOM_TAG_MAP.items():
        cap = room_cap.get(rm, 0)
        if tag not in tag_maxcap or cap > tag_maxcap[tag]:
            tag_maxcap[tag] = cap
    subj_tags = {s["subject"]: s["roomTags"] for s in S.load_subjects()}
    enrolled = {g["group"]: g["enrolled"] for g in S.load_groups()}
    class_tags = collections.defaultdict(set)
    for r in _real_incarichi():
        for tag in subj_tags.get(r["subject"], []):
            class_tags[r["group"]].add(tag)
    findings = []
    for tag in sorted(tag_maxcap):
        cap = tag_maxcap[tag]
        if cap <= 0:
            continue
        using = [enrolled[g] for g, tags in class_tags.items() if tag in tags]
        maxenr = max(using, default=0)
        if maxenr > cap:
            findings.append(Finding(
                "V13", AVVISO,
                f"tag '{tag}': stanza max capienza {cap} < iscritti {maxenr} di una classe che la usa",
            ))
    return findings


def v18_sigle_univoche() -> list[Finding]:
    """V18 - Sigle univoche: 05!sigla senza duplicati fra i docenti."""
    shorts = [t["short"] for t in S.load_teachers()]
    dupes = sorted({s for s in shorts if shorts.count(s) > 1})
    return [Finding("V18", ERRORE, f"sigla docente duplicata: '{d}'") for d in dupes]


def v21_token_struttura_coerenti() -> list[Finding]:
    """V21 - Token e struttura coerenti: il token 06!regola deve essere coerente
    con le colonne strutturate (tipo/giorni/fascia). System-side legge l'output di
    AC.compress, dove regola e colonne sono generate insieme: l'invariante vale
    per costruzione e il controllo la verifica (cattura un 06 compilato a mano
    incoerente)."""
    rules, _noncomp = AC.compress()
    findings = []
    for r in rules:
        tipo, regola = r["tipo"], r["regola"]
        ok = (
            (tipo == "SOLO_GIORNI" and regola.startswith("solo ")) or
            (tipo == "MAI_GIORNI" and regola.startswith("mai ")) or
            (tipo == "SOLO_FASCIA" and regola.startswith("solo ")) or
            (tipo == "FINESTRA" and regola.startswith("finestra ")) or
            tipo in ("MAX_INIZI_PRESTO", "MIN_GIORNI_LIBERI")
        )
        if not ok:
            findings.append(Finding(
                "V21", ERRORE,
                f"{r['docente']}: token '{regola}' incoerente con tipo {tipo}",
            ))
    return findings


# Nota da accodare ai finding G14 (View A ratificata da maintainer, proposta 5.3.3):
# il foglio flagga l'incoerenza e nomina l'esenzione del motore, cosi' chi legge
# capisce perche' l'orario esiste comunque e non pensi a un bug. La voce resta
# rossa finche' il gruppo non e' sanato in input o l'esenzione ratificata.
_G14_EXEMPT = (" [il motore esenta G14 dal fill: isG14, "
                 "SchoolScheduleConstraintProvider.java:1839; sanare in input o ratificare l'esenzione]")


def _calendar_ampiezza(group: str, weekday_idx: int):
    """Slot count of the group's calendar window for a weekday (0=Mon..4=Fri),
    or None if the weekday is closed. SRC: sources.GROUP_CALENDAR_PATTERNS."""
    for row in S.GROUP_CALENDAR_PATTERNS:
        if row[0] == group:
            cell = row[1 + weekday_idx]
            if cell is None:
                return None
            a, b = cell.split("-")
            return int(b) - int(a) + 1
    return None


def v2_incarichi_contro_piano_giorni() -> list[Finding]:
    """V2 - Incarichi contro piano-giorni: SUM(incarichi ore reali per classe)
    deve uguagliare SUM(oreAula+oreLab) del piano generato. G14 e' flaggato
    (View A): il suo piano e' un template 990h non autoritativo vs 115h reali."""
    inc = collections.defaultdict(float)
    for r in _real_incarichi():
        inc[r["group"]] += r["hours"] or 0.0
    aulalab = collections.defaultdict(int)
    for r in S.daily_activity_quota():
        aulalab[r["group"]] += r["aula"] + r["lab"]
    findings = []
    for g in sorted(set(inc) | set(aulalab)):
        d = inc.get(g, 0) - aulalab.get(g, 0)
        if abs(d) > 0.5:
            suffix = _G14_EXEMPT if g == "G14" else ""
            findings.append(Finding(
                "V2", ERRORE,
                f"{g}: incarichi {inc.get(g, 0):g}h vs piano aula+lab {aulalab.get(g, 0)}h "
                f"(delta {d:+.0f}h){suffix}",
            ))
    return findings


def _v_plan_window_violations() -> tuple[dict, dict, dict]:
    """(V7/V8/V9 share one pass) -> (over_finetra, on_closed_weekday, over_max_ore)."""
    school_days = set(S.school_year_dates())
    v7 = collections.defaultdict(int)  # ore > finestra 11
    v8 = collections.defaultdict(int)  # ore > 0 su weekday chiuso
    v9 = collections.defaultdict(int)  # ore > maxOreGiorno 10
    for r in S.daily_activity_quota():
        if r["date"] not in school_days:
            continue
        g = r["group"]
        ore = r["aula"] + r["lab"] + r["stage"]
        wd = r["date"].weekday()
        amp = _calendar_ampiezza(g, wd)
        if amp is None:
            if ore > 0:
                v8[g] += 1
        elif ore > amp:
            v7[g] += 1
        if ore > S.MAX_HOURS_PER_DAY.get(g, 6):
            v9[g] += 1
    return v7, v8, v9


def v7_ore_contro_finestra() -> list[Finding]:
    """V7 - Ore contro finestra: per (classe, giorno) oreAula+oreLab+oreStage non
    oltre l'ampiezza della finestra 11 di quel weekday. G14 e' flaggato (View A)."""
    v7, _v8, _v9 = _v_plan_window_violations()
    return [
        Finding("V7", ERRORE,
                f"{g}: {n} giorni con ore oltre la finestra 11{_G14_EXEMPT if g == 'G14' else ''}")
        for g, n in sorted(v7.items()) if n
    ]


def v8_giorno_chiuso_con_ore() -> list[Finding]:
    """V8 - Giorno chiuso con ore: riga di 15 con ore > 0 su un weekday chiuso
    (aperto=NO in 11). G14 e' flaggato (View A: ore del template 990h sui suoi
    Mon/Tue/Wed chiusi)."""
    _v7, v8, _v9 = _v_plan_window_violations()
    return [
        Finding("V8", ERRORE,
                f"{g}: {n} giorni con ore su weekday chiuso (aperto=NO in 11)"
                f"{_G14_EXEMPT if g == 'G14' else ''}")
        for g, n in sorted(v8.items()) if n
    ]


def v9_cap_giornaliero_classe() -> list[Finding]:
    """V9 - Cap giornaliero classe: ore per (classe, data) non oltre maxOreGiorno
    di 10. G14 e' flaggato (View A: template 6h/giorno vs suo cap 5h)."""
    _v7, _v8, v9 = _v_plan_window_violations()
    return [
        Finding("V9", ERRORE,
                f"{g}: {n} giorni con ore oltre maxOreGiorno {S.MAX_HOURS_PER_DAY.get(g, 6)}"
                f"{_G14_EXEMPT if g == 'G14' else ''}")
        for g, n in sorted(v9.items()) if n
    ]


def v16_copertura_piano_giorni() -> list[Finding]:
    """V16 - Copertura piano-giorni: ogni giorno scolastico aperto (weekday aperto
    in 11) ha una riga in 15. I giorni mancanti sono AVVISO (probabile no-session:
    aggiungere riga 0,0,0 con motivo), distingue 'non in sessione' da 'riga
    dimenticata'."""
    school_days = set(S.school_year_dates())
    covered = collections.defaultdict(set)
    for r in S.daily_activity_quota():
        if r["date"] in school_days:
            covered[r["group"]].add(r["date"])
    findings = []
    for row in S.GROUP_CALENDAR_PATTERNS:
        g = row[0]
        missing = [d for d in school_days if d not in covered[g] and _calendar_ampiezza(g, d.weekday()) is not None]
        if missing:
            findings.append(Finding(
                "V16", AVVISO,
                f"{g}: {len(missing)} giorni scolastici aperti senza riga in 15 "
                f"(probabile no-session: aggiungere riga 0,0,0 con motivo)",
            ))
    return findings


def v15_stage_e_professionalizzanti() -> list[Finding]:
    """V15 - Stage e professionalizzanti (AVVISO). Per i gruppi profDuringStage=NO
    (NO_PROF_DURING_STAGE), le ore-professionalizzanti devono poter stare fuori
    finestra stage. INTERPRETAZIONE PRE-SOLVE (dichiarata nel task TASK-445 #5):
    ore-prof non devono superare la capacita' grezza dei giorni non-stage
    ((giorni-scolastici - stage-days) x maxOreGiorno). PERMISSIVO per natura:
    'cadono SOLO in stage' e' proprieta' dello schedule, non dell'input, quindi
    cattura solo il caso patologico; sul seed 2026/27 dà 0 (G08 306h/1088h,
    G09 326h/1072h). E' l'unica ricostruzione possibile senza lo schedule."""
    school_days = set(S.school_year_dates())
    stage_days = collections.defaultdict(set)
    for r in S.daily_activity_quota():
        if r["stage"] > 0 and r["date"] in school_days:
            stage_days[r["group"]].add(r["date"])
    total_sd = len(school_days)
    prof_subj = {s["subject"] for s in S.load_subjects() if s["professionalizzanti"]}
    oreprof = collections.defaultdict(float)
    for r in _real_incarichi():
        # normalize_subject: incarichi puo' avere grafie minuscole (es.
        # 'Sviluppo sostenibile') vs subjects canonico ('Sviluppo Sostenibile').
        # Match esatto perderebbe 10h di G08 (stesso pattern di V5/V10).
        if S.normalize_subject(r["subject"]) in prof_subj:
            oreprof[r["group"]] += r["hours"] or 0.0
    findings = []
    for g in sorted(S.NO_PROF_DURING_STAGE):
        nonstage = total_sd - len(stage_days.get(g, ()))
        cap = nonstage * S.MAX_HOURS_PER_DAY.get(g, 6)
        op = oreprof.get(g, 0)
        if op > cap:
            findings.append(Finding(
                "V15", AVVISO,
                f"{g} (profDuranteStage=NO): {op:.0f}h professionalizzanti > capacita' {cap}h "
                f"dei {nonstage} giorni non-stage (alcune cadrebbero in stage)",
            ))
    return findings


# --- controls that need a source not wired yet (declared, not hidden) -------
# One factory over a (cid -> reason) dict collapses what was 13 near-identical
# stub functions. A control moves out of here when its source is wired.
_DEFERRED: dict[str, str] = {
    "V4c": "l'intento pieno (concentrazione domanda per (docente,materia,giorno)) "
           "serve il giorno-per-materia di 12/15; la forma single-day e' ridondante con V4",
    "V12": "serve la nozione di blocco per materia (12!ore/blocco)",
    "V14": "serve 06_Rotazioni (lab-fallback); tabella vuota nel 2026/27",
    "V17": "servono 13/14 pattern+eccezioni (Tappa 4 compress)",
}


def _deferred(cid: str) -> Callable[[], list[Finding]]:
    return lambda: [Finding(cid, DEFERRED, _DEFERRED[cid])]


# --- workbook-structural controls (req 1/2/4) ------------------------------
# These read the COMPILED workbook (.xlsx), not the seed. The seed controls above
# verify the school's DATA; these verify the school's ARTIFACT - the spreadsheet
# that arrives at import: class references canonical (V24), totals kept as
# formulas not static literals (V25), numeric columns kept numeric (V26). That is
# where a typo like 'G14 2' or 'rosy' in an hours cell lands, and where a
# hand-typed number replaces a =SUM. Each takes the openpyxl workbook (None when
# the workbook was not generated, in which case it DEFERs loud, never silent OK).

DEFAULT_WORKBOOK = Path(__file__).resolve().parent / "output" / "AnnoScolastico_2026-27.xlsx"

# Reported by every structural control when the workbook is not on disk, so a
# missing artifact is visible in the summary instead of hidden behind a tick.
_WB_MISSING = ("workbook .xlsx non trovato: generarlo con generate_workbook.py "
               "(i controlli V24+ leggono il workbook compilato, non il seed)")


def _load_workbook(path=None):
    """Load the compiled workbook for the structural controls.

    Returns ``(wb, path)``; ``(None, path)`` when the file is absent so the caller
    DEFERs loud. openpyxl is imported lazily so seed-only runs (V1-V23) carry no
    hard dependency on it. ``data_only=False`` keeps formulas visible (=SUM), not
    their cached values, so V25 can tell a real formula from a hand-typed literal.
    """
    from openpyxl import load_workbook
    p = Path(path) if path else DEFAULT_WORKBOOK
    if not p.exists():
        return None, p
    return load_workbook(p, data_only=False), p


def _col_index(ws, header: str):
    """1-based column index of ``header`` in row 1, or None if the sheet has no
    such column. Header-NAME driven so a column reorder cannot retarget a check
    (the same robustness _style_body uses for the origine tint)."""
    for col in range(1, ws.max_column + 1):
        if str(ws.cell(row=1, column=col).value).strip() == header:
            return col
    return None


def v24_classi_canoniche(wb) -> list[Finding]:
    """V24 - Classi canoniche (req1, 'un posto autoritativo per fatto'): il foglio
    10_Classi e' l'anagrafica autoritativa delle classi del workbook. Ogni valore
    'classe' nei fogli che lo referenziano (04/11/12/13/14/15/16/20) deve essere
    ivi definito, e 10_Classi non ammette righe duplicate. Cattura refusi ('G14
    2' vs 'G14') e righe fantasma.

    L'anagrafica autoritativa e' il 10_Classi del WORKBOOK stesso, NON il seed di
    sistema: il workbook e' l'input per un NUOVO anno, quindi una nuova cohorte
    che la scuola aggiunge coerentemente (in 10_Classi e ovunque la referenzi) e'
    legittima, non un errore. Il seed e' la verita' del sistema, non del workbook
    che la scuola sta compilando."""
    if wb is None:
        return [Finding("V24", DEFERRED, _WB_MISSING)]
    findings = []
    if "10_Classi" not in wb.sheetnames:
        return [Finding("V24", ERRORE,
                        "10_Classi mancante: foglio anagrafico delle classi assente")]
    ws10 = wb["10_Classi"]
    col10 = _col_index(ws10, "classe")
    if col10 is None:
        return [Finding("V24", ERRORE, "10_Classi senza colonna 'classe'")]
    # Canonical set = the workbook's own 10_Classi (one authoritative place).
    canonical = set()
    dup_10 = collections.Counter()
    for row in range(2, ws10.max_row + 1):
        val = ws10.cell(row=row, column=col10).value
        if val is None or str(val).strip() == "":
            continue
        name = str(val).strip()
        canonical.add(name)
        dup_10[name] += 1
    # Every 'classe' in the referencing sheets must be defined in 10_Classi.
    unknown = collections.Counter()   # (title, name) -> count
    for title in WS.CLASS_SHEETS:
        if title == "10_Classi" or title not in wb.sheetnames:
            continue
        ws = wb[title]
        col = _col_index(ws, "classe")
        if col is None:
            continue
        for row in range(2, ws.max_row + 1):
            val = ws.cell(row=row, column=col).value
            if val is None or str(val).strip() == "":
                continue
            name = str(val).strip()
            if name not in canonical:
                unknown[(title, name)] += 1
    findings = [
        Finding("V24", ERRORE, f"{t}: classe '{n}' non definita in 10_Classi ({c} righe)")
        for (t, n), c in sorted(unknown.items())
    ]
    findings += [
        Finding("V24", ERRORE, f"10_Classi: classe '{n}' duplicata ({c} righe)")
        for n, c in sorted(dup_10.items()) if c > 1
    ]
    return findings


def _is_formula(val) -> bool:
    """True when the cell holds a formula (Excel stores it as a string starting
    with '='). data_only=False keeps the formula text visible for this check."""
    return isinstance(val, str) and val.startswith("=")


def v25_totali_formula(wb) -> list[Finding]:
    """V25 - Totali a formula (req2): il totale ore di 12_Incarichi e le celle del
    CALCOLO TOTALE di 99_Validazione devono essere FORMULE (=SUM/SUMIF), non
    literal. Una scuola che sostituisce il totale con un numero digitato a mano
    per mascherare uno scostamento (il 9x G14 sarebbe cosi' emerso) e' colta
    qui. I totali si trovano per etichetta (TOTAL_ROW_LABEL / CALCOLO_TOTALE_MARKER),
    non per indice di riga, cosi' un riordino del foglio non sfasa il check.

    Entrambi i totali sono richiesti da req2: la loro ASSENZA e' essa stessa un
    ERRORE (template manomesso o invecchiato), non un silent-pass. Onora la regola
    no-silent-OK: un controllo che non puo' girare perche' la struttura e' cambiata
    lo segnala, non tace."""
    if wb is None:
        return [Finding("V25", DEFERRED, _WB_MISSING)]
    findings = []
    # 12_Incarichi: the 'Totale ORE' row's ore cell must be a formula.
    if "12_Incarichi" in wb.sheetnames:
        ws = wb["12_Incarichi"]
        doc_col = _col_index(ws, "docente")
        ore_col = _col_index(ws, "ore")
        total_row = None
        if doc_col and ore_col:
            for row in range(2, ws.max_row + 1):
                if str(ws.cell(row=row, column=doc_col).value).strip() == WS.TOTAL_ROW_LABEL:
                    total_row = row
                    break
        if total_row is None:
            findings.append(Finding("V25", ERRORE,
                f"12_Incarichi: riga totale '{WS.TOTAL_ROW_LABEL}' mancante (req2: richiesta)"))
        else:
            val = ws.cell(row=total_row, column=ore_col).value
            if not _is_formula(val):
                findings.append(Finding("V25", ERRORE,
                    f"12_Incarichi: il totale '{WS.TOTAL_ROW_LABEL}' non e' una formula "
                    f"(valore {val!r}); una scuola potrebbe averlo sostituito con un literal"))
    # 99_Validazione: every CALCOLO TOTALE group row, formula columns 2-5.
    if "99_Validazione" in wb.sheetnames:
        ws = wb["99_Validazione"]
        in_section = False
        saw_group_row = False
        for row in range(1, ws.max_row + 1):
            a = str(ws.cell(row=row, column=1).value or "").strip()
            if a == WS.CALCOLO_TOTALE_MARKER:
                in_section = True
                continue
            if not in_section or a == "" or a == "classe":
                continue
            saw_group_row = True
            for col in (2, 3, 4, 5):
                val = ws.cell(row=row, column=col).value
                if not _is_formula(val):
                    findings.append(Finding("V25", ERRORE,
                        f"99_Validazione CALCOLO TOTALE: cella per '{a}' (col {col}) non e' "
                        f"una formula (valore {val!r})"))
        if not in_section:
            findings.append(Finding("V25", ERRORE,
                f"99_Validazione: sezione {WS.CALCOLO_TOTALE_MARKER} mancante (req2: richiesta)"))
        elif not saw_group_row:
            findings.append(Finding("V25", ERRORE,
                f"99_Validazione: sezione {WS.CALCOLO_TOTALE_MARKER} vuota (nessuna riga per-classe)"))
    return findings


def v26_colonne_numeriche(wb) -> list[Finding]:
    """V26 - Colonne numeriche (req4): le colonne numeriche del workbook devono
    contenere solo numeri (non testo) quando non vuote. Cattura 'rosy' o 'colloqui
    t14' finiti in una colonna ore/St/La: il difetto reale del 30/07.
    Le formule sono accettate (computano un numero, non sono testo digitato); le
    celle vuote sono accettate (vuoto = non applicabile). L'altra meta' di req4 -
    le note hanno una colonna dedicata ('note', ultima in ogni foglio di dati) -
    e' garantita dal template e da questo check, che respinge il testo fuori
    dalla colonna note."""
    if wb is None:
        return [Finding("V26", DEFERRED, _WB_MISSING)]
    findings = []
    for title, cols in WS.NUMERIC_COLUMNS.items():
        if title not in wb.sheetnames:
            continue
        ws = wb[title]
        header_col = {str(ws.cell(row=1, column=c).value).strip(): c
                      for c in range(1, ws.max_column + 1)}
        for name in cols:
            col = header_col.get(name)
            if col is None:
                continue
            bad = collections.Counter()
            for row in range(2, ws.max_row + 1):
                val = ws.cell(row=row, column=col).value
                if val is None or str(val).strip() == "" or _is_formula(val):
                    continue
                # bool is an int subclass but never a valid hours/slot value.
                if isinstance(val, bool) or not isinstance(val, (int, float)):
                    bad[val] += 1
            for val, n in sorted(bad.items(), key=lambda kv: str(kv[0])):
                findings.append(Finding("V26", ERRORE,
                    f"{title} colonna '{name}': valore non numerico {val!r} ({n} celle; "
                    f"le note vanno nella colonna note)"))
    return findings


# --- registry + runner -----------------------------------------------------
# Ordered V1..V23 so the printout reads like the sheet. V4 + V4b are emitted by
# one function (v4_capacita_docente) over the same teacher population, two
# levels; a future cid-keyed registry can split them with a shared helper.
CONTROLS: list[Callable[[], list[Finding]]] = [
    v1_monte_ore_classe,
    v2_incarichi_contro_piano_giorni,
    v3_stage_tripla_coerenza,
    v4_capacita_docente,           # covers V4 + V4b (same population, two levels)
    _deferred("V4c"),
    v5_materie_non_mappate,
    v6_date_fuori_anno,
    v7_ore_contro_finestra,
    v8_giorno_chiuso_con_ore,
    v9_cap_giornaliero_classe,
    v10_nomi_non_risolti,
    v11_chiavi_duplicate,
    _deferred("V12"),
    v13_capienza_aula,
    _deferred("V14"),
    v15_stage_e_professionalizzanti,
    v16_copertura_piano_giorni,
    _deferred("V17"),
    v18_sigle_univoche,
    v19_slot_contigui,
    v20_alias_canonici_disgiunti,
    v21_token_struttura_coerenti,
    v22_regole_non_contraddittorie,
    v23_docenti_esterni_dichiarati,
]

# Workbook-structural controls (req 1/2/4): take the openpyxl workbook. Separate
# registry from the seed controls because they read a different source (the
# compiled artifact, not the seed) and run_all threads the workbook only here.
# V25/V26 are appended in their own commits.
WORKBOOK_CONTROLS: list[Callable[[object], list[Finding]]] = [
    v24_classi_canoniche,
    v25_totali_formula,
    v26_colonne_numeriche,
]


def run_all(workbook_path=None) -> list[Finding]:
    """Run every control. Seed controls (V1-V23) read ``sources``; the structural
    controls (V24+) read the compiled workbook at ``workbook_path`` (default: the
    generated output workbook). When the workbook is absent the structural
    controls report DEFERRED, never a silent pass."""
    wb, _used = _load_workbook(workbook_path)
    findings: list[Finding] = []
    for fn in CONTROLS:
        findings.extend(fn())
    for fn in WORKBOOK_CONTROLS:
        findings.extend(fn(wb))
    return findings


def summary(findings: list[Finding]) -> str:
    erre = [f for f in findings if f.level == ERRORE]
    avv = [f for f in findings if f.level == AVVISO]
    defer = [f for f in findings if f.level == DEFERRED]
    lines = [
        f"99_Validazione (system-side): {len(erre)} ERRORE, {len(avv)} AVVISO, "
        f"{len(defer)} DEFERRED su {len(SHEET_CIDS)} controlli",
    ]
    for f in erre:
        lines.append(f"  [ERRORE] {f.cid}: {f.message}")
    for f in avv:
        lines.append(f"  [AVVISO] {f.cid}: {f.message}")
    if defer:
        lines.append("  controlli DEFERRED (non ancora cablati, non bloccano):")
        for f in defer:
            lines.append(f"    {f.cid}: {f.message}")
    return "\n".join(lines)


if __name__ == "__main__":
    f = run_all()
    print(summary(f))
    import sys
    sys.exit(1 if any(x.level == ERRORE for x in f) else 0)
