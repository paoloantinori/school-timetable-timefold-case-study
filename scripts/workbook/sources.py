"""Data sources for the school-year input workbook generator (TASK-445).

Two kinds of source, kept strictly apart:

1. **Live CSV reads.** Anagrafica that already lives as seed CSV
   (``src/main/resources/seed/*.csv``) is READ AT RUNTIME by the functions in
   :mod:`csv_load`. A regenerated workbook always reflects the current CSV, so
   a school edit to a seed file flows through without touching this code.

2. **Code-sourced snapshots.** Facts that still live as Java constants (no CSV
   yet) are held here as Python literals with a ``# SRC:`` provenance comment
   pointing at the exact ``file:line`` they were transcribed from on
   2026-07-29. They are ``origine = sistema`` in the workbook and are the thing
   later tappe replace with a live read once the consuming code is data-driven
   (audit clusters C2/C3/C6/C7). The byte-identity gate
   (:mod:`test_materie_byte_identity`) cross-checks the workbook back against
   :mod:`csv_load` for the taxonomy and against these snapshots for the timing
   columns, so a stale transcription fails loudly.

The ban on guessing: every value below is transcribed from code I read this
turn, never invented. If a value is "optional / unknown" it is left EMPTY, not
zero, per the proposal's empty-cell convention (section 2).
"""

from __future__ import annotations

import csv
import datetime
import sys
from functools import lru_cache
from pathlib import Path

# scripts/analyze/shared.py already mirrors several Java constants (slot times,
# school-year end, subject normalization, pseudo-teacher set) and is the
# established home for them across the KPI/derive scripts. Import instead of
# re-transcribing: some carry Java-side drift guards (e.g. PSEUDO_TEACHERS is
# cross-checked by DataGeneratorSeedTest), so one hand-pinned copy stays
# authoritative and a second copy here cannot drift silently.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "analyze"))
from shared import (  # noqa: E402  (import after sys.path tweak above)
    SCHOOL_YEAR_END,
    SLOT_START_TIMES,
    SUBJECT_CASE_NORMALIZATION,
    PSEUDO_TEACHERS,
    WEEKDAY_ORDER,
)

# --- repo layout -----------------------------------------------------------
# This module lives at scripts/workbook/, the seed CSVs at src/main/resources/seed/.
SEED_DIR = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "seed"


def normalize_subject(raw: str) -> str:
    """Mirror of SubjectNormalization.normalize (case-duplicate map only).

    Thin local wrapper over the shared dict so callers keep a stable name; the
    authoritative map is shared.SUBJECT_CASE_NORMALIZATION.
    """
    return SUBJECT_CASE_NORMALIZATION.get(raw, raw)


# Pseudo-teacher rows in incarichi.csv that are NOT real Course assignments:
# they model Stage/Esame/RA/total-hours and must be excluded from 12_Incarichi
# (Stage -> 16_Stage, Esame/RA -> tipo, Totale ore corso -> 10_Classi.oreAnnueAttese).
# Alias of shared.PSEUDO_TEACHERS (SRC: DataGenerator.PSEUDO_COURSE_TEACHERS,
# DataGenerator.java:173, cross-checked by DataGeneratorSeedTest).
PSEUDO_COURSE_TEACHERS = PSEUDO_TEACHERS

# origine vocabulary: the single source for the origine column value + the
# yellow-tint contract (generate_workbook._style_body tints rows whose origine
# cell == ORIGINE_SISTEMA). Hoisted here so availability_compress and
# generate_workbook share one literal and cannot drift out of sync.
ORIGINE_SISTEMA = "sistema"
ORIGINE_SCUOLA = "scuola"


# --- live CSV reads --------------------------------------------------------

def _read_seed(name: str, expected_cols: int) -> list[list[str]]:
    """Read a seed CSV, fail-loud on a column-count mismatch (SeedCsv philosophy)."""
    path = SEED_DIR / name
    if not path.exists():
        raise FileNotFoundError(f"seed CSV not found: {path}")
    with path.open(newline="", encoding="utf-8") as fh:
        rows = list(csv.reader(fh))
    if not rows:
        raise ValueError(f"empty seed CSV: {path}")
    header, body = rows[0], rows[1:]
    if len(header) != expected_cols:
        raise ValueError(f"{name}: expected {expected_cols} columns, header has {len(header)}: {header}")
    for i, row in enumerate(body, start=2):
        # A wrong-width row (short OR long) is a real data bug, not something to
        # pad or shift silently. `<` would let an extra cell (e.g. an unquoted
        # comma splitting a field) through with shifted columns.
        if len(row) != expected_cols:
            raise ValueError(f"{name} line {i}: {len(row)} cells, expected {expected_cols}: {row!r}")
    return body


@lru_cache(maxsize=1)
def load_subjects() -> list[dict]:
    """subjects.csv -> list of {subject, roomTags(list), professionalizzanti(bool)}.

    The single source of truth for the 08_Materie taxonomy flags (TASK-437).
    """
    out = []
    for row in _read_seed("subjects.csv", 3):
        subject = row[0].strip()
        tag_cell = row[1].strip()
        prof = _strict_bool(row[2].strip(), f"professionalizzanti for {subject}")
        tags = [t for t in (s.strip() for s in tag_cell.split(";")) if t] if tag_cell else []
        out.append({"subject": subject, "roomTags": tags, "professionalizzanti": prof})
    return out


@lru_cache(maxsize=1)
def load_rooms() -> list[dict]:
    """rooms.csv -> list of {room, capacity(int)}."""
    out = []
    for row in _read_seed("rooms.csv", 2):
        out.append({"room": row[0].strip(), "capacity": int(row[1])})
    return out


@lru_cache(maxsize=1)
def load_groups() -> list[dict]:
    """groups.csv -> list of {group, courseCode, enrolled(int)}."""
    out = []
    for row in _read_seed("groups.csv", 3):
        out.append({"group": row[0].strip(), "courseCode": row[1].strip(), "enrolled": int(row[2])})
    return out


@lru_cache(maxsize=1)
def load_teachers() -> list[dict]:
    """teachers.csv -> list of {short, fullname, email, maxDailyHours, ...}."""
    out = []
    for row in _read_seed("teachers.csv", 6):
        out.append({
            "short": row[0].strip(),
            "fullname": row[1].strip(),
            "email": row[2].strip(),
            "maxDailyHours": _opt_int(row[3]),
            "maxEarlyStartDaysPerWeek": _opt_int(row[4]),
            "requiredFreeDaysPerWeek": _opt_int(row[5]),
        })
    return out


@lru_cache(maxsize=1)
def load_incarichi() -> list[dict]:
    """incarichi.csv -> all rows {teacher, subject, group, hours(float)} (incl. pseudo)."""
    out = []
    for row in _read_seed("incarichi.csv", 4):
        out.append({
            "teacher": row[0].strip(),
            "subject": row[1].strip(),
            "group": row[2].strip(),
            "hours": _opt_float(row[3]),
        })
    return out


@lru_cache(maxsize=1)
def load_availability_rules() -> list[dict]:
    """availability_rules.csv -> rows {teacher, dayOfWeek, minSlot, maxSlot, type,
    effectiveFrom, effectiveTo, note}. Uses the strict _read_seed column-count
    guard so a stray comma in a note cannot silently shift columns."""
    out = []
    for row in _read_seed("availability_rules.csv", 8):
        out.append({
            "teacher": row[0].strip(),
            "dayOfWeek": row[1].strip(),
            "minSlot": int(row[2]),
            "maxSlot": int(row[3]),
            "type": row[4].strip(),
            "effectiveFrom": row[5].strip(),
            "effectiveTo": row[6].strip(),
            "note": row[7].strip(),
        })
    return out


# The 3 "quota groups" seeded from calendario_fc.csv's finer aula/lab/stage split
# (DataGenerator.FC_QUOTA_GROUP_NAMES, DataGenerator.java:906). Authoritative
# literal; PIANO_GIORNI_FINE_GROUPS below aliases it (pianoGiorniFine = SI tracks
# the same set), so the two distinct code concepts share one literal and cannot drift.
FC_QUOTA_GROUP_NAMES = {"G04", "G08", "G13"}


@lru_cache(maxsize=1)
def load_calendario_FC() -> list[dict]:
    """calendario_fc.csv -> rows {group, date, aulaHours, labHours, stageHours}.

    The school's authoritative "DEF FC TUTTI" sheet for G04/G08/G13 (990h
    each): one row per (group, date) with the aula/lab/stage split. Strict
    column-count guard via _read_seed (5 columns)."""
    out = []
    for row in _read_seed("calendario_fc.csv", 5):
        out.append({
            "group": row[0].strip(),
            "date": row[1].strip(),
            "aulaHours": int(row[2]),
            "labHours": int(row[3]),
            "stageHours": int(row[4]),
        })
    return out


@lru_cache(maxsize=1)
def load_calendario_class_days() -> list[dict]:
    """calendario_class_days.csv -> rows {group, date, hours, modality}.

    The per-class per-date expected-hours sheet for ALL 14 groups, but with
    exactly ONE non-zero modality per date (A=aula, L=lab, S=stage). The 3 FC
    quota groups appear here too but are SKIPPED by the loader (already seeded
    from the finer calendario_fc.csv split); this builder honours that skip.
    Strict 4-column guard."""
    out = []
    for row in _read_seed("calendario_class_days.csv", 4):
        out.append({
            "group": row[0].strip(),
            "date": row[1].strip(),
            "hours": _opt_float(row[2]),
            "modality": row[3].strip(),
        })
    return out


@lru_cache(maxsize=1)
def closure_dates() -> frozenset:
    """The set of closure dates (03_Chiusure), as ISO date objects. A closure
    date generates no Timeslot, so any quota row on it is off-grid."""
    return frozenset(datetime.date.fromisoformat(d) for d, _desc, _tipo in CLOSURES)


@lru_cache(maxsize=1)
def school_year_dates() -> list:
    """Every Mon-Fri date in [dataInizio, dataFine] that is NOT a closure, as
    ISO date objects. These are the 'in-grid school days': the universe over
    which 15_PianoGiorniGenerato is expanded. Saturdays and closures are
    off-grid (no Timeslot) and excluded. Cached: callers set()-copy or iterate,
    never mutate."""
    start = datetime.date.fromisoformat(ANNO_DATA_INIZIO)
    end = datetime.date.fromisoformat(ANNO_DATA_FINE)
    closed = closure_dates()
    out = []
    d = start
    while d <= end:
        if d.weekday() < 5 and d not in closed:
            out.append(d)
        d += datetime.timedelta(days=1)
    return out


@lru_cache(maxsize=1)
def _known_group_names() -> frozenset:
    """The set of valid group names (groups.csv), for the findGroupByName skip both
    DataGenerator loaders perform (a CSV row whose group is not in the DB is skipped)."""
    return frozenset(g["group"] for g in load_groups())


def _round_half_up(value: float) -> int:
    """Round half UP, matching Java ``Math.round(double)`` (DataGenerator.java:955),
    NOT Python ``round`` (banker's rounding). For a future .5 hours value Python
    ``round`` would diverge by 1h from what the solver seeds."""
    return int(value + 0.5) if value >= 0 else -int(-value + 0.5)


@lru_cache(maxsize=1)
def daily_activity_quota() -> list[dict]:
    """The ground-truth per-(group, date) expected-hours table the solver seeds,
    reproducing BOTH DataGenerator loaders exactly (so the byte-identity gate of
    Tappa 4 has one canonical source, not two hand-mirrored copies).

    - generateDailyActivityQuotas (DataGenerator.java:873): calendario_fc.csv
      rows with (aula+lab+stage) > 0 AND date inside the Timeslot horizon ->
      FCQuota = true. (Zero-hour rows carry no information; beyond-horizon rows
      have no Timeslot.)
    - generateExpectedDailyHours (DataGenerator.java:939): calendario_class_days
      .csv rows for the OTHER 11 groups (FC quota groups skipped), hours > 0,
      modality A->aula / L->lab / S->stage, FCQuota = false. Since TASK-462 a
      date may span two rows of DIFFERENT modality (e.g. G14 A+L same day):
      they aggregate into one [aula, lab, stage] entity; only a duplicate
      (group, date, modality) row fails loud (DataGenerator.java:961).

    Each returned row: {group, date(date), aula, lab, stage, FCQuota(bool)}.
    Rows are NOT filtered by weekday or closure: the loader persists quota on
    Saturdays and closure dates too (off-grid stage hours); the caller classifies
    in-grid vs off-grid via the date. Faithful to BOTH loader skips: a CSV row whose
    group is not in groups.csv is skipped (DataGenerator findGroupByName == null ->
    continue), and hours use round-half-up like Java Math.round (not Python banker's
    rounding, which would diverge by 1h on a future .5 value). Unknown modality FAILS
    LOUD here (ValueError) where Java silently persists a (0,0,0) no-op row: stricter
    on purpose, so a bad modality letter is caught at build time. NOTE: the
    DataGenerator javadoc (513 rows, G04 stage 0) is STALE - calendario_fc.csv was
    regenerated 2026-07-28; the current CSV seeds 515 fc rows and G04 now carries
    an in-grid stage period (224h seeded; 240h raw of which 16h are off-grid)."""
    start = datetime.date.fromisoformat(ANNO_DATA_INIZIO)
    end = datetime.date.fromisoformat(ANNO_DATA_FINE)
    known = _known_group_names()
    out: list[dict] = []
    # fc.csv -> 3 quota groups, FCQuota = true
    for r in load_calendario_FC():
        if r["group"] not in known:
            continue  # DataGenerator findGroupByName == null -> continue
        d = datetime.date.fromisoformat(r["date"])
        if r["aulaHours"] + r["labHours"] + r["stageHours"] <= 0:
            continue
        if d < start or d > end:
            continue
        out.append({"group": r["group"], "date": d, "aula": r["aulaHours"],
                    "lab": r["labHours"], "stage": r["stageHours"], "FCQuota": True})
    # class_days.csv -> other 11 groups. Since TASK-462 a date may span two CSV
    # rows (e.g. G14 2027-03-11: A=2 + L=3), so rows AGGREGATE here per
    # (group, date) into [aula, lab, stage]; only a duplicate (group, date,
    # modality) row stays fatal. Mirrors DataGenerator.generateExpectedDailyHours
    # (DataGenerator.java:940) byte-for-byte: the hours<=0 skip and the
    # (g,d,modality) dup-check precede the out-of-horizon skip; aggregation
    # follows; unknown groups are dropped at emit (findGroupByName == null ->
    # continue at persist, DataGenerator.java:984).
    by_group_date: dict = {}  # (g,d) -> [aula, lab, stage], insertion-ordered (py3.7+)
    seen_group_date_modality = set()
    for r in load_calendario_class_days():
        g = r["group"]
        if g in FC_QUOTA_GROUP_NAMES:
            continue  # already seeded above from the finer fc split
        d = datetime.date.fromisoformat(r["date"])
        hours = _round_half_up(r["hours"]) if r["hours"] is not None else 0
        if hours <= 0:
            continue
        modality = r["modality"]
        gdm = (g, d, modality)
        if gdm in seen_group_date_modality:
            raise ValueError(f"duplicate (group, date, modality) row in calendario_class_days.csv "
                             f"for {g} {d} {modality}")
        seen_group_date_modality.add(gdm)
        if d < start or d > end:
            continue  # out-of-horizon: counted for the dup-check, NOT aggregated
        split = by_group_date.setdefault((g, d), [0, 0, 0])
        if modality == "A":
            split[0] += hours
        elif modality == "L":
            split[1] += hours
        elif modality == "S":
            split[2] += hours
        else:
            raise ValueError(f"unknown modality {modality!r} for {g} {d} in calendario_class_days.csv "
                             f"(Java fails loud too, DataGenerator.java:978)")
    for (g, d), split in by_group_date.items():
        if g not in known:
            continue  # DataGenerator findGroupByName == null -> skip at persist
        out.append({"group": g, "date": d, "aula": split[0], "lab": split[1],
                    "stage": split[2], "FCQuota": False})
    return out


def _strict_bool(cell: str, ctx: str) -> bool:
    if cell.lower() == "true":
        return True
    if cell.lower() == "false":
        return False
    raise ValueError(f"malformed boolean {cell!r} ({ctx}) in seed CSV - expected 'true'/'false'")


def _opt_int(cell: str):
    cell = cell.strip()
    return int(cell) if cell else None


def _opt_float(cell: str):
    cell = cell.strip()
    return float(cell) if cell else None


# --- code-sourced snapshots (provenance in the comment above each) ---------

# 01_Anno. SRC: TimeslotSequence.SCHOOL_YEAR_START/END (TimeslotSequence.java:28-29).
ANNO_ETICHETTA = "2026/27"
ANNO_DATA_INIZIO = "2026-09-14"
ANNO_DATA_FINE = SCHOOL_YEAR_END.isoformat()
ANNO_GIORNI_SETTIMANA = "LUN;MAR;MER;GIO;VEN"
# oreAnnueStandard: the 990h that appears on every group's "Totale ore corso"
# row (G12 is 1000, G14 is 120 - carried per-class in 10_Classi, left blank
# here as a year-level reference). settimaneDidattiche left BLANK: optional, the
# proposal computes it from the dates minus closures rather than hardcoding 38.
ANNO_ORE_ANNUE_STANDARD = 990

# 02_Campanella. SRC: TimeslotSequence.SLOT_START_TIMES (via shared.py) +
# LUNCH_BREAK_END = 14:30 (TimeslotSequence.java:60-61). Each slot is 1h; the
# first slot at or after the lunch-break end (14:30) is pomeriggio, the derived
# afternoon boundary the proposal wants instead of the literal 6 (audit bug 4).
# LUNCH_BREAK_END stays a documented literal here: shared.py does not export it.
LUNCH_BREAK_END = "14:30"


def _slot_end(start: str) -> str:
    """Each campanella slot is one hour."""
    h, m = (int(x) for x in start.split(":"))
    end = h * 60 + m + 60
    return f"{end // 60:02d}:{end % 60:02d}"


CAMPANELLA_SLOTS = [
    (i, start, _slot_end(start), "pomeriggio" if start >= LUNCH_BREAK_END else "mattino")
    for i, start in enumerate(SLOT_START_TIMES)
]

# 03_Chiusure. SRC: DataGenerator.buildClosures2026_27 (DataGenerator.java:134-153).
# (date, descrizione, tipo) - tipo inferred from the description label, as the
# proposal 3.3 asks the school to confirm.
CLOSURES = [
    ("2026-12-07", "Ponte Immacolata Concezione", "ponte"),
    ("2026-12-08", "Immacolata Concezione", "festivo"),
    ("2026-12-23", "Vacanze di Natale", "vacanza"),
    ("2026-12-24", "Vacanze di Natale", "vacanza"),
    ("2026-12-25", "Vacanze di Natale", "vacanza"),
    ("2026-12-26", "Vacanze di Natale", "vacanza"),
    ("2026-12-27", "Vacanze di Natale", "vacanza"),
    ("2026-12-28", "Vacanze di Natale", "vacanza"),
    ("2026-12-29", "Vacanze di Natale", "vacanza"),
    ("2026-12-30", "Vacanze di Natale", "vacanza"),
    ("2026-12-31", "Vacanze di Natale", "vacanza"),
    ("2027-01-01", "Vacanze di Natale", "vacanza"),
    ("2027-01-02", "Vacanze di Natale", "vacanza"),
    ("2027-01-03", "Vacanze di Natale", "vacanza"),
    ("2027-01-04", "Vacanze di Natale", "vacanza"),
    ("2027-01-05", "Vacanze di Natale", "vacanza"),
    ("2027-01-06", "Vacanze di Natale", "vacanza"),
    ("2027-01-22", "Chiusura da golden/fogli (era typo 2026)", "chiusura"),
    ("2027-02-08", "Carnevale", "vacanza"),
    ("2027-02-09", "Carnevale", "vacanza"),
    ("2027-03-25", "Vacanze di Pasqua", "vacanza"),
    ("2027-03-26", "Vacanze di Pasqua", "vacanza"),
    ("2027-03-27", "Vacanze di Pasqua", "vacanza"),
    ("2027-03-28", "Vacanze di Pasqua", "vacanza"),
    ("2027-03-29", "Vacanze di Pasqua", "vacanza"),
    ("2027-03-30", "Vacanze di Pasqua", "vacanza"),
    ("2027-05-31", "Ponte Festa della Repubblica", "ponte"),
    ("2027-06-01", "Ponte Festa della Repubblica", "ponte"),
    ("2027-06-02", "Festa della Repubblica", "festivo"),
]

# 04_GiorniSpeciali: the first-day ramp. SRC: DataGenerator.generateGroupCalendarRules
# first-day override (DataGenerator.java:798-834). 2026-09-14 is the only day
# where every group with an open Monday is confined to slots 1-4 (09:00-13:00),
# start-an-hour-late; G14 excluded (its Monday is closed).
GIORNI_SPECIALI_DATE = "2026-09-14"
GIORNI_SPECIALI_MOTIVO = "Primo giorno, ingresso posticipato 09:00-13:00"

# 05_Docenti: esterno flag. SRC: the external teacher is T01 (T28,
# Thursday only) - DataGenerator creates VACATION records for every non-Thursday
# weekday; the proposal 3.5 note. maxOreGiorno comes from teachers.csv
# maxDailyHours (T01=8); others blank = default 6.
DOCENTI_ESTERNI = {"T01"}

# 10_Classi per-group facts. SRC: DataGenerator constants.
# MAX_HOURS_PER_DAY (DataGenerator.java:207-222).
MAX_HOURS_PER_DAY = {
    "G01": 6, "G02": 6, "G03": 6, "G04": 8,
    "G05": 6, "G06": 8, "G07": 8, "G08": 8,
    "G09": 8, "G10": 8, "G11": 8, "G12": 8,
    "G13": 8, "G14": 5,
}
# NO_PROFESSIONALIZZANTI_DURING_STAGE (DataGenerator.java:182-185) ->
# profDuranteStage = NO for these, SI otherwise.
NO_PROF_DURING_STAGE = {"G08", "G09"}
# FC_QUOTA_GROUP_NAMES (DataGenerator.java:906) -> pianoGiorniFine = SI
# (the groups whose day-plan has the aula/lab/stage split). Alias of the
# authoritative FC_QUOTA_GROUP_NAMES literal above (same set, distinct concept).
PIANO_GIORNI_FINE_GROUPS = FC_QUOTA_GROUP_NAMES
# G14 exemption from day-fill (isG14, SchoolScheduleConstraintProvider:1840)
# -> esenteRiempimento = SI for G14 only.
ESENTE_RIEMPIMENTO_GROUPS = {"G14"}
# M5_TARGET_GROUPS (BaseConstraintProvider.java:74-78) -> tesinaDopoStage = SI
# (Informatica-after-stage preference: MODA + FA).
TESINA_DOPO_STAGE_GROUPS = {
    "G01", "G05", "G09", "G02", "G06", "G10", "G12",
}

# 16_Stage. SRC: DataGenerator.generateStagePatterns (DataGenerator.java:704-742).
# (classe, inizio, fine, giorniStage, giorniRientro, oreTotaliAttese)
# oreGiorno left BLANK: not carried in the StagePattern constant; the school
# confirms. oreTotaliAttese is the proposal's documented figure per class.
#
# G04 added req5 (TASK-445 Tappa 6): its stage was already SEEDED in the piano
# (calendario_fc.csv, daily_activity_quota) but had NO 16_Stage row, so V3 fired
# "nessuna riga 16_Stage" and V1 showed a phantom +240h residuo. Measured from the
# seed (not the stale '240h/30 date' in older notes): 28 stage dates over
# 2027-04-26..2027-06-10, all weekdays (no return weekday), 208h in-grid + 16h
# off-grid (2 Ponte-Repubblica closures) = 224h total. oreTotaliAttese = 224
# reconciles V3 exactly (in-grid + off-grid == declared); V1 then shows the REAL
# +16h gap (990 attese vs 750 incarichi + 224 stage = 974) instead of the phantom
# +240h. The stage becomes a declared INPUT and the extract->import chain is
# idempotent (the row declares exactly what the piano contains).
STAGE_PATTERNS = [
    ("G11", "2027-01-13", "2027-03-19", "MAR;MER;GIO;VEN", "LUN", 295),
    ("G07", "2027-03-23", "2027-05-28", "MAR;MER;GIO;VEN", "LUN", 295),
    ("G12", "2026-11-20", "2027-04-17", "GIO;VEN", "LUN;MAR;MER", 295),
    ("G04", "2027-04-26", "2027-06-10", "LUN;MAR;MER;GIO;VEN", "", 224),
]

# 17_Aule room tags. SRC: DataGenerator.ROOM_TAG_MAP (DataGenerator.java:191-199).
ROOM_TAG_MAP = {
    "R14": "termoidraulica",
    "R15": "meccanica",
    "R11": "acconciatura",
    "R12": "informatica",
    "R13": "informatica",
    "R16": "moda",
    "R17": "saldatura",
}

# 11_ClassiCalendario. SRC: DataGenerator.GROUP_CALENDAR_PATTERNS
# (DataGenerator.java:772-788). Each cell is "min-max" or None (closed).
# (group, MON, TUE, WED, THU, FRI)
GROUP_CALENDAR_PATTERNS = [
    ("G03", "0-5", "0-5", "0-5", "0-5", "0-4"),
    ("G07", "0-5", "0-7", "0-7", "0-7", "0-7"),
    ("G11", "0-5", "0-7", "0-7", "0-7", "0-7"),
    ("G02", "0-5", "0-5", "0-5", "0-5", "0-4"),
    ("G06", "0-5", "0-5", "0-7", "0-5", "0-4"),
    ("G10", "0-4", "0-7", "0-7", "0-7", "0-7"),
    ("G12", "0-4", "0-4", "0-4", "0-7", "0-6"),
    ("G01", "0-5", "0-5", "0-5", "0-5", "0-4"),
    ("G05", "0-5", "0-5", "0-5", "0-5", "0-4"),
    ("G09", "0-7", "0-7", "0-7", "0-7", "0-7"),
    ("G04", "0-7", "0-7", "0-7", "0-7", "0-7"),
    ("G08", "0-7", "0-7", "0-7", "0-7", "0-7"),
    ("G13", "0-5", "0-7", "0-7", "0-7", "0-7"),
    ("G14", None, None, None, "0-4", "0-2"),
]
CALENDAR_WEEKDAYS = ["LUN", "MAR", "MER", "GIO", "VEN"]


def first_day_ramp_groups() -> list[str]:
    """Groups eligible for the first-day slot-1..4 ramp: those whose Monday
    calendar cell is non-null, i.e. they have any lessons on a Monday at all.

    Mirrors DataGenerator.generateGroupCalendarRules rampEligibleGroups
    (DataGenerator.java:823-826: ``if (row[1] != null) rampEligibleGroups.add``)
    rather than special-casing a group name, so the workbook tracks the solver's
    actual GroupCalendarRule seed if a group's Monday opens or closes."""
    return [row[0] for row in GROUP_CALENDAR_PATTERNS if row[1] is not None]


# 20_GiorniProfessionalizzanti. SRC: M29_RESTRICTED_DAYS_BY_GROUP
# (SchoolScheduleConstraintProvider.java:787) - G12 reserved Wed+Thu.
PROFESSIONALIZZANTI_DAYS = [("G12", "MER"), ("G12", "GIO")]

# --- 08_Materie timing columns (solver-side constants, audit C1 step 2) ----
# These are still Java constants (not yet data-driven, per SubjectRegistry
# javadoc). They become 08_Materie columns; the gate checks the workbook back
# against these snapshots.

# primaDelloStage = SI for the M26 "all hours before stage" subject.
# SRC: BaseConstraintProvider.SICUREZZA_SUBJECT (BaseConstraintProvider.java:84).
PRIMA_DELLO_STAGE_SUBJECTS = {"Sicurezza"}

# settimanaInizioMax = 3 for M27 T20 "start early" subjects.
# SRC: T20_EARLY_SUBJECTS / T20_EARLY_MAX_START_WEEK (SSCP.java:1132-1133).
T20_EARLY_SUBJECTS = {"Merceologia", "Arredi Tessili"}
T20_EARLY_MAX_WEEK = 3

# settimanaInizioMin = 4 for M27 T20 "start late" subjects.
# SRC: T20_LATE_SUBJECTS / T20_LATE_MIN_START_WEEK (SSCP.java:1134-1135).
T20_LATE_SUBJECTS = {"Tecnologia tessile", "Storia della moda"}
T20_LATE_MIN_WEEK = 4

# capOreGiorno = 0 for M21-exempt subjects (no daily cap, the old whitelist).
# SRC: M21_EXEMPT_SUBJECTS (SSCP.java:87-88).
M21_EXEMPT_SUBJECTS = {"Competenze Digitali", "Disegno", "Disegno Edile ed Impiantistico"}


def ore_annue_attese() -> dict[str, float]:
    """10_Classi.oreAnnueAttese per group, from the 'Totale ore corso' pseudo rows.

    Replaces the pseudo-teacher summary row (proposal 5.1). SRC: incarichi.csv
    rows where teacher == 'Totale ore corso'.
    """
    out = {}
    for row in load_incarichi():
        if row["teacher"] == "Totale ore corso" and row["group"]:
            out[row["group"]] = row["hours"]
    return out
