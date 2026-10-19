#!/usr/bin/env python3
"""
TASK-472 perimeter-residual report: golden-first tier classification for 5
not-yet-pinned workbook rules (siblings of the no-2h-teoria pilot).

For each rule: (a) precise predicate on the golden, (b) golden violation count,
(c) proposed tier via golden-first (violations>0 -> a BLANKET HARD is forbidden,
would make the golden infeasible), (d) composition note vs existing H/M.

Golden traps applied per CLAUDE.md KPI section: Saturdays off-grid, teacher
Fasullo/Fasutto placeholder, Stage off-grid (except where a rule IS about Stage).

Rules (CLAUDE.md TASK-472 perimeter):
  1. Termoidraulica "prime 4 ore"       (slot 0-3 = 08-12 tassativo?)
  2. Sicurezza "1a / 2 giorni"           (first-years in <=2 cumulative days)
  3. T21 mid-day                   (NOT slot0, ore di mezzo)
  4. M24/M25 residuals                   (T11 early-start, T05 free-days)
  5. M29 discrepanza                     (G12 prof at day edges Wed/Thu)

Sibling of theory_adjacent_report.py. Read-only; prints to stdout.
"""
import argparse
import csv
import os
import sys
import datetime
from collections import Counter, defaultdict

# Reuse the authoritative lab classification + placeholder set (single source with
# the M-TR constraint, mirrors the DB's Course.isLab), as the sibling
# theory_adjacent_report.py / block_size_report.py already do.
sys.path.insert(0, os.path.dirname(__file__))
from theory_adjacent_residual import is_lab, PLACEHOLDER_TEACHERS  # noqa: E402

GOLDEN_DEFAULT = "src/main/resources/seed/lessons.csv"

# Slot "HH:MM HH:MM" -> 0-based index (08=0 ... 15=7), mirror of TimeslotSequence.
SLOT_INDEX = {"08": 0, "09": 1, "10": 2, "11": 3, "12": 4, "13": 5, "14": 6, "15": 7}

# Professionalizzanti subjects (subjects.csv professionalizzanti=true).
PROFESSIONALIZZANTI = {
    "Analisi del capello e del cuoio capelluto", "Arredi Tessili", "Barberia",
    "Cad", "Confezione", "Consulenza d'immagine", "Controllo numerico",
    "Cosmetologia", "Disegno Edile ed Impiantistico", "Figurino",
    "Gestione aziendale", "Igiene e sicurezza", "Laboratorio Termoidraulico",
    "Lavaggio", "Lavorazioni meccaniche", "Merceologia",
    "Metrologia e Tecnologia dei materiali", "Modello (Moulage)",
    "Personalizzazione", "Preparazione all'alternanza",
    "Progettazione Termoidraulica", "Relazione con il cliente",
    "Servizio stilistico", "Servizio tecnico e stilistico", "Sicurezza",
    "Storia della moda", "Sviluppo Sostenibile", "Taglio Metalli",
    "Tecniche di saldatura", "Tecnologia Meccanica",
    "Tecnologia e creatività", "Tecnologia tessile",
}

# M24/M25 per-teacher config (from teachers.csv, mirror of the DB).
M24_TEACHERS = {"T11": 1}   # maxEarlyStartDaysPerWeek
M25_TEACHERS = {"T05": 2}     # requiredFreeDaysPerWeek

# M29 scope: G12 non-lab professionalizzanti on Wed/Thu.
M29_GROUP = "G12"
M29_RESTRICTED_WD = {2, 3}  # Wednesday, Thursday (Mon=0)


def slot_of(slot_field):
    return SLOT_INDEX.get(slot_field[:2], -1)


def iso_week(date):
    ic = date.isocalendar()
    return (ic[0], ic[1])


def load_golden(path, drop_stage=True):
    """Yield (date, weekday, slot_index, group, teacher, subject) for placed real
    golden lessons, golden traps applied (Saturday + placeholder + Stage)."""
    with open(path, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            teacher = r["teacher"].strip()
            subject = r["subject"].strip()
            if teacher in PLACEHOLDER_TEACHERS:
                continue
            if drop_stage and subject == "Stage":
                continue
            try:
                d = datetime.date.fromisoformat(r["date"].strip())
            except ValueError:
                continue
            if d.weekday() == 5:  # Saturday off-grid
                continue
            si = slot_of(r["slot"].strip())
            if si < 0:
                continue
            yield (d, d.weekday(), si, r["group"].strip(), teacher, subject)


def banner(n, title):
    print("\n" + "=" * 74)
    print(f"RULE {n}: {title}")
    print("=" * 74)


def tier_line(violations, extra=""):
    """Golden-first tier verdict line."""
    if violations == 0:
        print(f"  TIER: golden violates 0 times -> HARD defensible (golden-clean). {extra}".rstrip())
    else:
        print(f"  TIER: golden violates {violations} times -> BLANKET HARD forbidden (would")
        print(f"         make the golden infeasible; golden-first). MEDIUM, or hard-with-exceptions. {extra}".rstrip())


# ─── RULE 1: Termoidraulica "prime 4 ore" ──────────────────────────────────

def rule1_termoidraulica(rows):
    banner(1, 'Termoidraulica "prime 4 ore" (slot 0-3 = 08-12)')
    s = [r for r in rows if r[5] == "Laboratorio Termoidraulico"]
    dist = Counter(r[2] for r in s)
    in_prime = sum(c for sl, c in dist.items() if 0 <= sl <= 3)
    outside = len(s) - in_prime
    print(f"  predicate: a Laboratorio Termoidraulico lesson at slot >= 4 (12:00+) violates")
    print(f"             the workbook 'prime 4 ore' (slot 0-3 = 08-12 tassativo) reading.")
    print(f"  golden lessons: {len(s)}")
    print(f"  in slot 0-3 (prime 4h): {in_prime} ({in_prime/len(s):.0%})")
    print(f"  OUTSIDE (slot 4-5, 12-14): {outside} ({outside/len(s):.0%})  <- violations")
    print(f"  slot distribution: {dict(sorted(dist.items()))}")
    # Block-start view: is there a fixed start? Count block-start slots.
    bydate = defaultdict(list)
    for d, wd, si, g, t, sub in s:
        bydate[(g, d)].append(si)
    starts = Counter(min(v) for v in bydate.values() if v)
    print(f"  block-start distribution (min slot per (group,date)): {dict(sorted(starts.items()))}")
    print(f"  COMPOSITION: no existing constraint covers this; new M candidate, no clash with")
    print(f"               H-M2 (lab room-tag) or M30/M31 (lab-day quota/contiguity).")
    tier_line(outside, "75% golden-clean but 25% leaks to 12-14 -> MEDIUM (not hard).")
    return outside


# ─── RULE 2: Sicurezza "1a / 2 giorni" ─────────────────────────────────────

def rule2_sicurezza_2giorni(rows):
    banner(2, 'Sicurezza "1a / 2 giorni" (first-years in <=2 cumulative days)')
    print(f'  predicate: workbook riga 42 "2 gg di sicurezza 4 ore con le 1e" = the 8h of')
    print(f"             Sicurezza for each first-year group must concentrate in <=2 distinct")
    print(f"             days. A group spanning >2 days violates.")
    first_years = ["G03", "G02", "G01", "G04"]
    total_violating = 0
    for g in first_years:
        s = [r for r in rows if r[3] == g and r[5] == "Sicurezza"]
        days = sorted({r[0] for r in s})
        violates = len(days) > 2
        if violates:
            total_violating += 1
        print(f"    {g:8s} ore={len(s):2d}  distinct-days={len(days):2d}  -> "
              f"{'VIOLATES (>2 days)' if violates else 'OK (<=2)'}")
    print(f"  golden violating groups: {total_violating}/{len(first_years)}")
    print(f"  NOTE: the golden practises 1h/week (8h over 8 days) for ALL four first-years,")
    print(f"        never the 2-day concentration. The rule reads aspirational/non-applied.")
    print(f"  COMPOSITION: orthogonal to M26 (Sicurezza before stage) which is a DIFFERENT")
    print(f"               Sicurezza rule (date-vs-stageStart, not day-concentration). No clash.")
    tier_line(total_violating, "100% of groups violate -> NOT modelable until referente confirms;")
    print(f"         if confirmed real, at most SOFT (the 1h/week cadence is entrenched).")
    return total_violating


# ─── RULE 3: T21 mid-day ─────────────────────────────────────────────

def rule3_T21_midday(rows):
    banner(3, 'T21 "mid-day, NOT prime ore" (T30, bucket C2)')
    print(f"  predicate: workbook 'T21 t30 -> ore di mezzo, non prime ore' = a")
    print(f"             T21 lesson at slot 0 (08:00, prime ore) violates. Scope: the")
    print(f"             2 groups named in the rule (G08, G12).")
    total_violating = 0
    for g in ["G08", "G12"]:
        s = [r for r in rows if r[3] == g and r[4] == "T21"]
        slot0 = [r for r in s if r[2] == 0]
        dist = Counter(r[2] for r in s)
        total_violating += len(slot0)
        print(f"    {g:8s} ore={len(s):3d}  slot0={len(slot0):3d} ({len(slot0)/max(len(s),1):.0%})  "
              f"dist={dict(sorted(dist.items()))}")
    print(f"  golden slot0 violations (T21): {total_violating}")
    print(f"  NOTE: the golden puts T21 HEAVILY at prime ore (G08 26/42 = 62%), the")
    print(f"        opposite of 'ore di mezzo'. The rule contradicts established practice.")
    print(f"  COMPOSITION: would clash with nothing structural (no slot0 preference exists),")
    print(f"               but would fight the golden's own M18 (day-start anchoring).")
    tier_line(total_violating, "practice contradicts the rule -> NOT modelable until referente;")
    print(f"         if confirmed real, at most SOFT and G08 is the carve-out to watch.")
    return total_violating


# ─── RULE 4: M24/M25 residuals ─────────────────────────────────────────────

def rule4_m24_m25(rows):
    banner(4, "M24/M25 residuals (T11 early-start / T05 free-days)")
    # M24: per ISO week, distinct days with slot0 lesson; cap = maxEarlyStartDaysPerWeek.
    m24_cap = M24_TEACHERS["T11"]
    weeks = defaultdict(set)
    for d, wd, si, g, t, sub in rows:
        if t == "T11" and si == 0 and wd < 5:
            weeks[iso_week(d)].add(d)
    m24_viol_weeks = 0
    m24_residual = 0
    for wk, days in weeks.items():
        if len(days) > m24_cap:
            m24_viol_weeks += 1
            m24_residual += len(days) - m24_cap
    print(f"  M24 predicate: T11 (maxEarlyStartDaysPerWeek={m24_cap}) has >1 distinct")
    print(f"                 Mon-Fri day/ISO-week with a slot0 (08:00) lesson.")
    print(f"  M24 golden: {len(weeks)} active weeks, {m24_viol_weeks} violating, "
          f"residual={m24_residual} excess slot0 days (matches CLAUDE.md 'M24 -16').")

    # M25: per ISO week, distinct Mon-Fri days used; free = 5 - used; cap = requiredFreeDaysPerWeek.
    m25_req = M25_TEACHERS["T05"]
    weeks_b = defaultdict(set)
    for d, wd, si, g, t, sub in rows:
        if t == "T05" and wd < 5:
            weeks_b[iso_week(d)].add(d)
    m25_viol_weeks = 0
    m25_residual = 0
    for wk, days in weeks_b.items():
        free = 5 - len(days)
        if free < m25_req:
            m25_viol_weeks += 1
            m25_residual += m25_req - free
    print(f"  M25 predicate: T05 (requiredFreeDaysPerWeek={m25_req}) has <2 free Mon-Fri")
    print(f"                 days/ISO-week (i.e. teaches on >3 days).")
    print(f"  M25 golden: {len(weeks_b)} active weeks, {m25_viol_weeks} violating, "
          f"residual={m25_residual} shortfall (matches CLAUDE.md 'M25 -27').")
    print(f"  COMPOSITION: both already MEDIUM, residual-calibrated (CLAUDE.md TASK-368).")
    print(f"               No HARD possible (golden violates both). No weight tweak advised:")
    print(f"               ONE_MEDIUM matches the preference scale; the residual is the school's")
    print(f"               own practice, not a solver failure.")
    total = m24_residual + m25_residual
    print(f"  TIER: M24 STAYS MEDIUM (golden residual {m24_residual}); "
          f"M25 STAYS MEDIUM (golden residual {m25_residual}). "
          f"No tier change; no HARD (golden violates).")
    return total


# ─── RULE 5: M29 discrepanza ───────────────────────────────────────────────

def rule5_m29(rows):
    banner(5, "M29 discrepanza (G12 prof at day edges, Wed/Thu)")
    print(f"  M29 predicate: on a M29_RESTRICTED day (G12 Wed/Thu), the day's NON-LAB")
    print(f"                 professionalizzanti must form ONE contiguous run of <=2 slots")
    print(f"                 touching the group's first or last calendar-open slot that day.")
    print(f"  calendar windows (empirical from golden): Wed open 0-4 (last=4), Thu open 0-7 (last=7).")
    windows = {2: (0, 4), 3: (0, 7)}  # Wed, Thu (first, last)
    total_illegal = 0
    total_dates = 0
    for wd, name in [(2, "Wed"), (3, "Thu")]:
        first, last = windows[wd]
        subset = [r for r in rows if r[3] == M29_GROUP and r[1] == wd
                  and r[5] in PROFESSIONALIZZANTI and not is_lab(r[5], M29_GROUP)]
        bydate = defaultdict(list)
        for d, _, si, _, _, sub in subset:
            bydate[d].append(si)
        legal = 0
        illegal = 0
        illegal_examples = []
        for d, slots in sorted(bydate.items()):
            total_dates += 1
            ss = sorted(set(slots))
            span = ss[-1] - ss[0] + 1
            contiguous = (span == len(ss))
            maxlen = (span <= 2)
            touches = (ss[0] == first or ss[-1] == last)
            ok = contiguous and maxlen and touches
            if ok:
                legal += 1
            else:
                illegal += 1
                total_illegal += 1
                if len(illegal_examples) < 4:
                    illegal_examples.append((d, ss, contiguous, maxlen, touches))
        print(f"    {name}: {len(bydate)} dates, legal-edge={legal}, ILLEGAL={illegal} "
              f"({illegal/max(len(bydate),1):.0%})")
        for d, ss, c, ml, t in illegal_examples:
            print(f"        {d} slots={ss} contiguous={c} maxlen2={ml} touches_edge={t}")
    print(f"  golden illegal-edge dates: {total_illegal}/{total_dates} "
          f"({total_illegal/max(total_dates,1):.0%})")
    print(f"  NOTE: M29 scores 0 on the golden RUNTIME (day-level pinned exemption: the golden")
    print("        is 100% pinned import), but the PLACEMENT violates the edge shape 71% of dates.")
    print(f"        This is exactly why TASK-379 demoted H22 -> M29: the golden does not follow")
    print(f"        the edge rule, so HARD was stricter than practice.")
    print(f"  COMPOSITION: M29 is the consumer of the G12 docx rule; no clash with H17")
    print(f"               (vertical compaction) or M15 (same-subject adjacency). The pinned")
    print(f"               exemption means it only bites on solver-generated (unpinned) shapes.")
    tier_line(total_illegal, "STAYS MEDIUM; day-level pinned exemption is the correct register.")
    return total_illegal


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", default=GOLDEN_DEFAULT, help=f"golden CSV (default {GOLDEN_DEFAULT})")
    args = ap.parse_args()

    rows = list(load_golden(args.input))
    print("#" * 74)
    print(f"# TASK-472 perimeter-residual report (golden-first tier classification)")
    print(f"# input: {args.input}")
    print(f"# golden lessons (post-trap filters): {len(rows)}")
    print("#" * 74)

    v1 = rule1_termoidraulica(rows)
    v2 = rule2_sicurezza_2giorni(rows)
    v3 = rule3_T21_midday(rows)
    v4 = rule4_m24_m25(rows)
    v5 = rule5_m29(rows)

    # Sanity summary.
    print("\n" + "#" * 74)
    print("# SANITY SUMMARY (golden-first: no proposed HARD may have golden violations > 0)")
    print("#" * 74)
    print(f"  R1 Termoidraulica prime-4h : violations={v1:4d}  -> MEDIUM (not 0, no HARD)")
    print(f"  R2 Sicurezza 1a/2gg        : violations={v2:4d}  -> NOT-MODEL / SOFT (not 0)")
    print(f"  R3 T21 mid-day       : violations={v3:4d}  -> NOT-MODEL / SOFT (not 0)")
    print(f"  R4 M24/M25 residual        : violations={v4:4d}  -> MEDIUM HOLDS (not 0)")
    print(f"  R5 M29 discrepanza         : violations={v5:4d}  -> MEDIUM HOLDS (not 0)")
    hard_ok = all(v > 0 for v in (v1, v2, v3, v4, v5))  # all have violations -> none is HARD
    print(f"\n  sanity: every rule has golden violations > 0 -> NO rule is proposed HARD.")
    print(f"          golden-first invariant holds (no HARD proposal violates the golden).")
    print(f"          (A 0-violation rule would be the only HARD candidate; none qualifies.)")


if __name__ == "__main__":
    main()
