#!/usr/bin/env python3
"""
TASK-472 repeatable report for the "no 2 ore vicine della stessa materia teorica"
rule (the M-TR / TASK-468 candidate, tab "Vincoli DA ANALIZZARE").

maintainer's reading key (TASK-472): for every WRITTEN constraint the golden violates,
classify it H-true vs M-high-value (a rule that is almost-always respected but not
always could be an H the human planner could not solve, OR a high-value M), with a
repeatable report of the violating occurrences and an attempted explanation of each.

This is the PILOT: the framework (input-agnostic report + per-occurrence
explanation + tier proposal) instantiated on the already-measured no-2h-teoria case
(TASK-468 AC#1: ~432 day-cells on the golden). The theory classification (lab vs
practical-exempt vs theory) is IMPORTED from theory_adjacent_residual.py, which
mirrors the M-TR constraint's single source (SubjectNormalization.isTheoryForRule +
the DB's Course.isLab), so the report sees exactly what the runtime scores.

Input (default the golden): seed/lessons.csv (golden) OR a run dump lessons.txt
(group|date|hour|subject|teacher|pinned). Golden-only traps (Saturdays, placeholder
teachers Fasullo/Fasutto, Stage off-grid) are applied only to the golden input, per
CLAUDE.md's golden-traps protocol.

Output: total violating day-cells + residual (sum of excess hours = the M-TR medium
penalty units), rate, clustering (weekday / group / subject / month), and per-cell
detail with an attempted explanation ('capacity-driven', 'deep-block', or
'inspiegata'). Read-only; prints to stdout.
"""
import argparse
import csv
import os
import sys
from datetime import datetime
from collections import Counter, defaultdict

# Reuse the authoritative theory classification (single source with the M-TR constraint).
sys.path.insert(0, os.path.dirname(__file__))
from theory_adjacent_residual import is_theory, PLACEHOLDER_TEACHERS  # noqa: E402

GOLDEN_DEFAULT = "src/main/resources/seed/lessons.csv"
MODE = "stretta"  # match M-TR (excludes practical-exempt: disegno/informatica/RA etc.)


def load_rows(path):
    """Yield (date, weekday, group, subject, teacher) for placed real lessons.

    Detects golden CSV by its header and applies the golden-trap filters (Saturday,
    placeholder teacher, Stage); a run lessons.txt dump is already Mon-Fri and has
    no placeholder teachers, so only Stage is dropped there for like-for-like.
    """
    with open(path) as fh:
        first = fh.readline()
    if first.startswith("date,day,slot,closed"):
        yield from _load_golden(path)
    else:
        yield from _load_lessons_txt(path)


def _load_golden(path):
    with open(path, newline="") as fh:
        for r in csv.DictReader(fh):
            teacher = r["teacher"].strip()
            subject = r["subject"].strip()
            if teacher in PLACEHOLDER_TEACHERS or subject == "Stage":
                continue
            date = r["date"].strip()
            wd = datetime.strptime(date, "%Y-%m-%d").strftime("%a")
            if wd == "Sat":
                continue
            yield (date, wd, r["group"].strip(), subject, teacher)


def _load_lessons_txt(path):
    with open(path) as fh:
        for line in fh:
            parts = line.strip().split("|")
            if len(parts) < 5 or parts[0] in ("SET", "group"):
                continue
            group, date, _hour, subject, teacher = parts[:5]
            if subject == "Stage":
                continue
            wd = datetime.strptime(date, "%Y-%m-%d").strftime("%a")
            yield (date, wd, group, subject, teacher)


def explain(hours, subj_total_hours, subj_active_days):
    """Attempted explanation for a 2+ h same-day same-subject theory cell.

    capacity-driven: the subject averages >1.5 h on each of its active days, so
      2 h days are structural (the budget does not fit 1 h/day).
    deep-block: a single day carries 3+ h (an intensive block, not a 2h split).
    inspiegata: a sporadic 2h repeat the budget does not force.
    """
    avg = subj_total_hours / subj_active_days if subj_active_days else 0
    if avg > 1.5:
        return f"capacity-driven (subject avg {avg:.1f}h over {subj_active_days} active days)"
    if hours >= 3:
        return f"deep-block ({hours}h same day)"
    return "inspiegata (sporadic 2h, budget does not force it)"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", default=GOLDEN_DEFAULT, help=f"golden CSV or run lessons.txt (default {GOLDEN_DEFAULT})")
    ap.add_argument("--top", type=int, default=12, help="top-N cutoffs for clustering tables (default 12)")
    args = ap.parse_args()

    rows = list(load_rows(args.input))

    # (group, subject) totals + active days, theory-only, for the explanation heuristic.
    subj_hours = defaultdict(int)
    subj_days = defaultdict(set)
    for date, wd, group, subject, teacher in rows:
        if not is_theory(subject, group, MODE):
            continue
        subj_hours[(group, subject)] += 1
        subj_days[(group, subject)].add(date)

    # Cells: (group, subject, date) -> hours, theory only.
    cells = defaultdict(int)
    cell_meta = {}
    for date, wd, group, subject, teacher in rows:
        if not is_theory(subject, group, MODE):
            continue
        cells[(group, subject, date)] += 1
        cell_meta.setdefault((group, subject, date), (wd, teacher))

    violating = {k: h for k, h in cells.items() if h > 1}
    residual = sum(h - 1 for h in violating.values())  # M-TR medium penalty units
    theory_day_cells = len(cells)  # distinct (group, subject, date) theory cells overall
    rate = len(violating) / theory_day_cells if theory_day_cells else 0.0

    print("=" * 74)
    print(f"TASK-472 report: no-2h-teoria (M-TR, mode={MODE}) on {args.input}")
    print("=" * 74)
    print(f"theory lessons (placed, real): {sum(cells.values())}")
    print(f"distinct theory day-cells (group,subject,date): {theory_day_cells}")
    print(f"violating day-cells (2+ h same theory): {len(violating)}  ({rate:.1%} of theory day-cells)")
    print(f"residual (sum of excess hours = M-TR penalty units): {residual}")
    print()

    # Clustering.
    def cluster(keyfn, label):
        c = Counter(keyfn(k) for k in violating)
        total = sum(c.values())
        top = c.most_common(args.top)
        share = sum(v for _, v in top[:3]) / total if total else 0
        print(f"  --- violating cells by {label} (top {len(top)}, top-3 share {share:.0%}) ---")
        for name, n in top:
            print(f"    {str(name):40} {n}")
        print()

    print("CLUSTERING")
    cluster(lambda k: cell_meta[k][0], "weekday")
    cluster(lambda k: k[0], "group")
    cluster(lambda k: k[1], "subject")
    cluster(lambda k: datetime.strptime(k[2], "%Y-%m-%d").strftime("%Y-%m"), "month")

    # Explanation mix.
    expl = Counter()
    for (group, subject, date), hours in violating.items():
        expl[explain(hours, subj_hours[(group, subject)], len(subj_days[(group, subject)]))] += 1
    print("EXPLANATION MIX")
    for reason, n in expl.most_common():
        print(f"  {n:4}  {reason}")
    print()

    # Per-cell detail (occurrences).
    print(f"PER-OCCURRENCE DETAIL ({len(violating)} cells, sorted by excess hours desc)")
    print(f"  {'group':8} {'subject':38} {'date':12} {'h':>2} {'wd':3} explanation")
    for (group, subject, date), hours in sorted(violating.items(), key=lambda kv: (-kv[1], kv[0])):
        wd = cell_meta[(group, subject, date)][0]
        reason = explain(hours, subj_hours[(group, subject)], len(subj_days[(group, subject)]))
        print(f"  {group:8} {subject:38} {date:12} {hours:>2} {wd:3} {reason}")
    print()

    # Tier proposal (golden-first): a blanket HARD forbids any rule the golden violates,
    # because it would make the golden itself infeasible. So rate>0 -> M (blanket) or
    # hard-with-enumerated-exceptions (only if violations are few + structural).
    cap_share = sum(n for r, n in expl.items() if r.startswith("capacity-driven"))
    print("TIER PROPOSAL (TASK-472 reading key, golden-first)")
    print(f"  - rate: {rate:.1%} of theory day-cells violate ({len(violating)}/{theory_day_cells}); "
          f"capacity-driven = {cap_share}/{len(violating)}.")
    if rate == 0:
        print("  - verdict: golden violates it 0 times -> safe to model HARD (golden-clean).")
    else:
        print(f"  - verdict: golden violates it {len(violating)} times -> a BLANKET HARD is forbidden")
        print("    (would make the golden infeasible; golden-first). It is M, or hard-with-exceptions.")
        if cap_share > len(violating) * 0.5:
            print("  - tier: M (capacity-driven dominates: high-hour subjects must repeat, little leverage;")
            print("          keep medium-low, the TASK-468 weight 1 is the right register).")
        elif len(violating) <= 50:
            print("  - tier: HARD-WITH-ENUMERATED-EXCEPTIONS candidate (few violations; if structural, model")
            print("          the pattern hard and enumerate the residual per pattern-exhaustion).")
        else:
            print("  - tier: M-HIGH-VALUE (violations spread/sporadic, not capacity-forced; the rule has")
            print("          leverage to push them out at a real medium weight).")
    print()
    print("Repeatable: re-run with --input <run lessons.txt> to score a run the same way.")


if __name__ == "__main__":
    main()
