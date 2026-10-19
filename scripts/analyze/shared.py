#!/usr/bin/env python3
"""
Shared constants and utilities for spalmone analysis scripts.
"""
import argparse
import csv
import os
import re
from dataclasses import dataclass
from datetime import date

# Common paths: every scripts/analyze/*.py script that reads the 2026/27
# seed data lives in this same directory, one level below scripts/ and two
# below the repo root - shared here instead of each script recomputing an
# identical SCRIPT_DIR/PROJECT_ROOT/DATA_DIR block.
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

# --- Parametric school-year config (TASK-444) -------------------------------
# Defaults reproduce the 2026/27 behavior exactly. Override per-invocation with
# the --year/--ramp/--year-end/--data-dir/--lessons-path/--closures-path CLI
# flags (see add_kpi_args) or the PLANNER_KPI_* env vars, so the KPI scripts run
# without source edits when the school year rolls over (solo-input directive,
# TASK-441): point --year at the new year and drop closures.csv + lessons.csv in
# the new data-dir.
YEAR = os.environ.get("PLANNER_KPI_YEAR", "2026/27")
YEAR_SLUG = YEAR.replace("/", "-")  # "2026/27" -> "2026-27" (matches spalmone-2026-27)


def data_dir_for_year(year):
    """The claudedocs/spalmone-<slug>/data dir for a school year ('2026/27' ->
    spalmone-2026-27/data). Single derivation used by both the module-level
    DATA_DIR (env-driven) and resolve_config() (CLI-driven), so the layout
    cannot drift between the two paths."""
    return os.path.join(PROJECT_ROOT, 'claudedocs', f'spalmone-{year.replace("/", "-")}', 'data')


def parse_score(text):
    """'score: -121hard/-964300medium/17548soft' or '-121hard/-964300medium/17548soft' -> (h,m,s).

    The single owner of the score-string format for analysis scripts (score.txt is
    written by api.sh 'score', scripts/api/api.sh). Lifted from
    btimeslot-bench-summary.py so the fifth private copy of this regex is never
    born (reuse review 29/08).
    """
    import re
    m = re.search(r"(-?\d+)hard/(-?\d+)medium/(-?\d+)soft", text or "")
    if not m:
        return None
    return tuple(int(x) for x in m.groups())


_data_dir_env = os.environ.get("PLANNER_KPI_DATA_DIR")
DATA_DIR = _data_dir_env or data_dir_for_year(YEAR)

LESSONS_CSV = os.environ.get("PLANNER_KPI_LESSONS_PATH") or os.path.join(DATA_DIR, 'lessons.csv')
INCARICHI_CSV = os.path.join(DATA_DIR, 'incarichi.csv')
AVAILABILITY_RULES_CSV = os.path.join(DATA_DIR, 'availability_rules.csv')
CLOSURES_CSV = os.environ.get("PLANNER_KPI_CLOSURES_PATH") or os.path.join(DATA_DIR, 'closures.csv')

# Mirrors com.school.planner.solver.TimeslotSequence.SLOT_START_TIMES - that
# Java array is the single source of truth; keep this list in lockstep by
# hand if it changes.
SLOT_START_TIMES = ["08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:30", "15:30"]

# School-year date bounds + first-day ramp. Mirror DataGenerator on the app side;
# hand-pinned here (same convention as SLOT_START_TIMES above). Overridable via
# env/CLI (TASK-444) for the year-rollover case. SCHOOL_YEAR_END mirrors
# TimeslotSequence.SCHOOL_YEAR_END (TASK-373 code review H2/H4).
SCHOOL_YEAR_START = date.fromisoformat(os.environ.get("PLANNER_KPI_YEAR_START", "2026-09-14"))
SCHOOL_YEAR_END = date.fromisoformat(os.environ.get("PLANNER_KPI_YEAR_END", "2027-06-10"))
RAMP_DATE = date.fromisoformat(os.environ.get("PLANNER_KPI_RAMP", "2026-09-14"))

WEEKDAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]

# lessons.csv's `day` column spells weekdays in Italian; RecurringAvailabilityRule
# (and availability_rules.csv) use the Java DayOfWeek enum name.
ITALIAN_DAY_TO_ENUM = {
    "Lunedì": "MONDAY", "Martedì": "TUESDAY", "Mercoledì": "WEDNESDAY",
    "Giovedì": "THURSDAY", "Venerdì": "FRIDAY",
}


def iter_open_lessons_rows(path=LESSONS_CSV):
    """Yields lessons.csv DictReader rows, skipping closed=='True' rows (not
    real scheduled lessons). Shared by every script that needs to count
    golden lesson-hours, so the "closed" exclusion rule lives in one place."""
    with open(path, newline='', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['closed'] == 'True':
                continue
            yield row


# Off-campus markers - these are NOT lessons, they represent periods when
# students are away from school (internships, exams, company visits)
# These should be skipped during parsing, not imported as lessons
OFF_CAMPUS_TEACHERS = {
    'azienda', 'azienda\r',    # Company visits (stage/internship)
    'esame', 'esame\r',        # Exams (not scheduled lessons)
    'Stage', 'Stage\r',        # Stage/internship (off-campus, capitalized)
    'stage', 'stage\r',        # Stage/internship (off-campus, lowercase)
    '',                         # Empty teacher names
}

# Mirrors com.school.planner.util.SubjectNormalization (case-duplicate
# subject spellings in incarichi.csv/lessons.csv) - that Java class is the
# single source of truth; keep this dict in lockstep by hand if it changes.
SUBJECT_CASE_NORMALIZATION = {
    "Lingua italiana": "Lingua Italiana",
    "Educazione civica": "Educazione Civica",
    "Comportamento sociale": "Comportamento Sociale",
    "Sviluppo sostenibile": "Sviluppo Sostenibile",
}

# Mirrors com.school.planner.bootstrap.DataGenerator.PSEUDO_COURSE_TEACHERS
# (incarichi.csv teacher names that are NOT real Course assignments) - that
# Java constant is the single source of truth. A cross-language runtime check
# isn't feasible, so this side is pinned by hand: if you change this set,
# also update DataGenerator.PSEUDO_COURSE_TEACHERS and the drift-guard test
# DataGeneratorSeedTest#pseudoCourseTeachersMatchesDocumentedSet (TASK-374
# code review E4), which hard-lists the same four values on the Java side.
PSEUDO_TEACHERS = {"Stage", "Esame", "RA", "Totale ore corso"}

# Subject/teacher exclusion set for the cadence/sharpness/hole-topology KPI
# family (cadence-kpi.py, fingerprint-sharpness-kpi.py, hole-topology-kpi.py,
# golden-vs-run-viz.py). DELIBERATELY DIFFERENT from PSEUDO_TEACHERS above:
# PSEUDO_TEACHERS also lists 'RA' and 'Totale ore corso' (incarichi.csv
# pseudo COURSE teachers), but RA micro-course hours are real taught lessons
# on the run side (real teacher names in the pipe dump) and must stay
# countable on the golden side too, or the two sides would not be comparable
# (see cadence-kpi.py's own docstring for the full rationale). This set only
# excludes Stage/Esame (pseudo-teacher accounting, not real lessons) and
# Fasullo (the golden CSV's placeholder rows).
KPI_EXCLUDED_PSEUDO = frozenset({"Stage", "Esame", "Fasullo"})


def real_slots(day_lessons, base_hour=8, slots_per_day=8):
    """Canonical slot indices (0..slots_per_day-1, computed as hour-base_hour)
    occupied by REAL lessons on ONE (group, date) day, given an iterable of
    (hour, subject, teacher) triples. Rows whose subject or teacher is in
    KPI_EXCLUDED_PSEUDO (Stage/Esame/Fasullo) are dropped - the same
    exclusion hole-topology-kpi.py's build_occupied() applies row-by-row,
    factored out here because golden-vs-run-viz.py needs it three times (hole
    marker, late-start marker, day-start modes) on already-grouped day lists.
    Slots outside the day schema are ignored, like every KPI in this family."""
    return {h - base_hour for h, s, t in day_lessons
            if s not in KPI_EXCLUDED_PSEUDO and t not in KPI_EXCLUDED_PSEUDO
            and 0 <= h - base_hour < slots_per_day}


def hole_slots(occupied_slots):
    """Given a set/iterable of canonical slot indices (0-7, hour-8) occupied
    by real lessons on one (group, date) day, returns the sorted list of
    canonical slot indices strictly within [min(occupied), max(occupied)]
    that are NOT occupied - the BUCO-INTERNO shape shared by
    hole-topology-kpi.py's day classifier and golden-vs-run-viz.py's
    season-view hole marker. Empty list if there are fewer than 2 occupied
    slots (no "between" range to test)."""
    slots = set(occupied_slots)
    if len(slots) < 2:
        return []
    lo, hi = min(slots), max(slots)
    return [s for s in range(lo, hi + 1) if s not in slots]


SCORE_LEGACY_RE = re.compile(r"(-?\d+)hard/(-?\d+)medium/(-?\d+)soft")
SCORE_BENDABLE_RE = re.compile(r"\[(-?\d+)/(-?\d+)\]hard/\[(-?\d+)/(-?\d+)\]soft")


def parse_hard_vector(text, last_match_wins=True):
    """Parses the FULL hard vector (hard, quasi) from a score string or a
    multi-line score.txt body, in either format, as "H/Q" (TASK-579.01: the
    delivery gate compares BOTH coordinates; parse_score_string deliberately
    drops quasi).

    Bendable "[-13/0]hard/[-1245173/-16017]soft" -> "-13/0"; legacy plain
    "-5hard/-1000medium/17548soft" -> "-5/0" (a single-coordinate score has an
    implicit zero quasi tier). last_match_wins=True scans line by line and
    keeps the LAST match (a score.txt with an init line above the final score
    must compare the final); set False for single strings where any match is
    the match. Returns None if no line matches - the caller decides whether
    that is missing evidence.
    """
    if not text:
        return None
    vec = None
    for line in (text.splitlines() if last_match_wins else [text]):
        b = SCORE_BENDABLE_RE.search(line)
        if b:
            vec = f"{b.group(1)}/{b.group(2)}"
            continue
        m = re.search(r"(-?\d+)hard/", line)
        if m:
            vec = f"{m.group(1)}/0"
    return vec


def parse_score_string(text):
    """Parses a score string in either format into (hard, medium, soft).

    Legacy (pre-BendableScore): "-121hard/-964300medium/17548soft".
    Bracket (BendableScore branch, TASK-538 B): "[-121/0]hard/[-964300/17548]soft"
    (hard level 0, quasi level 1, medium = soft[0], soft = soft[1]; the quasi level
    is NOT part of the return value - callers that need it read the raw string).

    Returns None if neither pattern matches. This is the single parser every
    score-reading script should use; ad-hoc copies drift (see badseed-frequency
    vs btimeslot-bench-summary pre-unification)."""
    if not text:
        return None
    b = SCORE_BENDABLE_RE.search(text)
    if b:
        return int(b.group(1)), int(b.group(3)), int(b.group(4))
    m = SCORE_LEGACY_RE.search(text)
    if m:
        return tuple(int(x) for x in m.groups())
    return None


def parse_lessons_csv(path, include_hour=True, strip_fields=False):
    """Parses the golden CSV format (date,day,slot,closed,group,teacher,
    subject) into (group, date, [hour,] subject, teacher) tuples. closed==
    'True' rows are skipped. include_hour controls whether the parsed hour
    (int, from the slot column's leading digits) is included in each output
    tuple - cadence-kpi.py doesn't need it. strip_fields controls whether
    subject/teacher are .strip()-ed: hole-topology-kpi.py needs this,
    cadence-kpi.py/fingerprint-sharpness-kpi.py's golden-CSV parsing does
    not - a genuine behavioral difference between the original copies,
    preserved here as a parameter rather than silently picked one way."""
    rows = []
    with open(path, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            if r['closed'] == 'True':
                continue
            group = r['group']
            d = date.fromisoformat(r['date'])
            subject = r['subject'].strip() if strip_fields else r['subject']
            teacher = r['teacher'].strip() if strip_fields else r['teacher']
            if include_hour:
                h = int(r['slot'].split(':')[0])
                rows.append((group, d, h, subject, teacher))
            else:
                rows.append((group, d, subject, teacher))
    return rows


def parse_lessons_pipe(path, include_hour=True):
    """Parses a group|date|hour|subject|teacher|pinned lessons dump into
    (group, date, [hour,] subject, teacher) tuples. A leading 'SET' line and
    blank/malformed lines are ignored. include_hour controls whether the
    parsed hour is included in the output tuple - cadence-kpi.py doesn't
    need it."""
    rows = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line == 'SET' or '|' not in line:
                continue
            p = [x.strip() for x in line.split('|')]
            if len(p) != 6:
                continue
            g, d, hh, s, t, _pn = p
            if include_hour:
                rows.append((g, date.fromisoformat(d), int(float(hh)), s, t))
            else:
                rows.append((g, date.fromisoformat(d), s, t))
    return rows


def load_lesson_rows(path, include_hour=True, strip_fields=False):
    """Auto-detects golden-CSV vs pipe-dump format from the first line and
    dispatches to parse_lessons_csv/parse_lessons_pipe. See those functions'
    docstrings for include_hour/strip_fields semantics (strip_fields only
    affects the CSV path, since the pipe path always strips every field)."""
    with open(path, encoding='utf-8') as f:
        first = f.readline()
    if first.startswith('date,day,slot,closed,group,teacher,subject'):
        return parse_lessons_csv(path, include_hour=include_hour, strip_fields=strip_fields)
    return parse_lessons_pipe(path, include_hour=include_hour)


def load_closures(path=CLOSURES_CSV):
    """School-closure dates from a file of one ISO date per line. Blank lines and
    '#' comments are ignored; an optional ',description' column is allowed (only
    the first comma-field is parsed).

    This is the date-only closure set for the KPI scripts, replacing the inline
    copy that used to live in golden-vs-run-viz.py (TASK-444). It is NOT the
    only representation: workbook/sources.py keeps a richer
    (date, description, type) list for workbook generation, and
    DataGenerator.CLOSURES_2026_27 is the app authority; the three must stay in
    lockstep (closures.csv is itself a projection, extracted from the workbook
    by spalmone2027-extract.py). Returns a set of date."""
    closures = set()
    with open(path, newline='', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            iso = line.split(',', 1)[0].strip()
            try:
                closures.add(date.fromisoformat(iso))
            except ValueError:
                # header line (e.g. "date") or free text: skip silently
                continue
    return closures


@dataclass
class KpiConfig:
    """Resolved parametric config for a KPI run (TASK-444). Built by
    resolve_config() from CLI args (add_kpi_args); defaults reproduce shared.py's
    module-level constants. Fields closures_* hold the path only; call
    load_closures(cfg.closures_path) if a script needs the set (golden-vs-run-viz
    does; the day-start/daily-fill family does not)."""
    year: str
    data_dir: str
    lessons_path: str
    incarichi_path: str
    closures_path: str
    year_start: date
    year_end: date
    ramp: date


def add_kpi_args(parser):
    """Add the common parametric KPI flags to an argparse parser, with defaults
    that reproduce today's 2026/27 behavior. --year derives --data-dir (and thus
    the lessons/incarichi/closures paths) unless those are given explicitly.
    Scripts that only need a subset can still call this; unused flags are harmless."""
    parser.add_argument('--year', default=YEAR,
                        help=f'school year, e.g. 2026/27 (default {YEAR}); derives the data-dir slug')
    parser.add_argument('--data-dir', default=None,
                        help='data dir holding lessons.csv/incarichi.csv/closures.csv '
                             f'(default derived from --year: {DATA_DIR})')
    parser.add_argument('--lessons-path', default=None,
                        help='golden lessons CSV (default <data-dir>/lessons.csv)')
    parser.add_argument('--closures-path', default=None,
                        help='closures file, one ISO date per line (default <data-dir>/closures.csv)')
    parser.add_argument('--ramp', default=RAMP_DATE.isoformat(),
                        help=f'ramp / first school day, ISO (default {RAMP_DATE.isoformat()})')
    parser.add_argument('--year-start', default=SCHOOL_YEAR_START.isoformat(),
                        help=f'school year start, ISO (default {SCHOOL_YEAR_START.isoformat()})')
    parser.add_argument('--year-end', default=SCHOOL_YEAR_END.isoformat(),
                        help=f'school year end, ISO (default {SCHOOL_YEAR_END.isoformat()})')


def resolve_config(args) -> KpiConfig:
    """Resolve a KpiConfig from parsed args (after add_kpi_args). Fills None
    overrides from --year-derived defaults, matching shared.py's module-level
    constants so the no-flags invocation reproduces today's behavior exactly."""
    data_dir = args.data_dir or data_dir_for_year(args.year)
    return KpiConfig(
        year=args.year,
        data_dir=data_dir,
        lessons_path=args.lessons_path or os.path.join(data_dir, 'lessons.csv'),
        incarichi_path=os.path.join(data_dir, 'incarichi.csv'),
        closures_path=args.closures_path or os.path.join(data_dir, 'closures.csv'),
        year_start=date.fromisoformat(args.year_start),
        year_end=date.fromisoformat(args.year_end),
        ramp=date.fromisoformat(args.ramp),
    )


# ── Analysis anchors (lessons.md famiglia 3: silent zeros lied nine times) ──

PLACED_TOTAL_ANCHOR = 12510
"""The seed's plannable lesson count. Any census/diff script whose placed total
does not reconcile with this (or with an explicit per-group golden total) is
reading the wrong thing: FAIL LOUDLY, never report the zero."""


def reconcile_placed_total(count, what="placed"):
    """Raise unless a placed-lesson count reconciles with the seed anchor.
    Ad-hoc analyses import this instead of trusting their own parsing."""
    if count != PLACED_TOTAL_ANCHOR:
        raise SystemExit(
            f"ANCHOR-MISMATCH: {what}={count} but the seed anchor is {PLACED_TOTAL_ANCHOR}: "
            "the artifact being read is not what you think (lessons.md famiglia 3)")
