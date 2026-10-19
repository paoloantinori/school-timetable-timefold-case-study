#!/usr/bin/env python3
"""
TASK-472 repeatable report for the "max-N ore consecutive della stessa materia"
(block-size) family of written constraints. The pilot instance is T16's
"3h-non-6" rule (tab "Vincoli del golden" / docx): a subject's daily run of
consecutive same-subject slots should stay at or below N hours, and the golden
violates it (the bucket-B/C measure counted "4 lunedì 6h + 6x5h").

maintainer's reading key (TASK-472): for every WRITTEN constraint the golden violates,
classify it H-true vs M-high-value (almost-always-respected could be an H the
human planner could not solve, or a high-value M), with a repeatable report of
the violating occurrences and an attempted explanation of each.

A block is a maximal run of ADJACENT (consecutive-hour) same-subject lessons in
a (group, date). This script tallies blocks longer than --max (default 3, the
T16 cap), clusters them, attempts an explanation, and proposes a tier. It is
input-agnostic (golden or any run dump) so the same lens scores a run.

Input (default the golden): seed/lessons.csv (golden) OR a run dump lessons.txt
(group|date|hour|subject|teacher|pinned). Golden-only traps (Saturdays, placeholder
teachers Fasullo/Fasutto, Stage off-grid) are applied only to the golden input,
per CLAUDE.md's golden-traps protocol.

Output: total blocks, violating blocks (>max h), residual (sum of excess hours),
rate, clustering (weekday / group / subject / month), per-block detail with an
attempted explanation ('capacity-driven', 'deep-block', or 'inspiegata'), and a
tier proposal. Read-only; prints to stdout. Use --subject to focus one subject
(e.g. the T16 rule), --max to set the cap.
"""
import argparse
import csv
import os
import sys
from datetime import datetime
from collections import Counter, defaultdict

# Reuse the authoritative placeholder-teacher trap (single source with the M-TR report).
sys.path.insert(0, os.path.dirname(__file__))
from theory_adjacent_residual import PLACEHOLDER_TEACHERS  # noqa: E402

GOLDEN_DEFAULT = "src/main/resources/seed/lessons.csv"


def load_rows(path):
    """Yield (group, date, slot_hour, subject, teacher, weekday) for placed real lessons.

    Detects golden CSV by its header and applies the golden-trap filters (Saturday,
    placeholder teacher, Stage); a run lessons.txt dump is already Mon-Fri and has no
    placeholder teachers, so only Stage is dropped there for like-for-like.
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
            hour = int(r["slot"].strip().split(":")[0])  # "09:00 10:00" -> 9
            yield (r["group"].strip(), date, hour, subject, teacher, wd)


def _load_lessons_txt(path):
    with open(path) as fh:
        for line in fh:
            parts = line.strip().split("|")
            if len(parts) < 5 or parts[0] in ("SET", "group"):
                continue
            group, date, hour, subject, teacher = parts[:5]
            if subject == "Stage":
                continue
            wd = datetime.strptime(date, "%Y-%m-%d").strftime("%a")
            yield (group.strip(), date.strip(), int(hour), subject.strip(), teacher.strip(), wd)


def detect_blocks(rows):
    """Maximal runs of adjacent (consecutive-hour) same-subject lessons per (group, date).

    Returns list of (group, date, subject, length_hours, weekday, teacher).
    """
    by_day = defaultdict(list)
    for group, date, hour, subject, teacher, wd in rows:
        by_day[(group, date)].append((hour, subject, teacher, wd))

    blocks = []
    for (group, date), lessons in by_day.items():
        lessons.sort()
        run_subj = None
        run_len = 0
        run_teacher = None
        run_wd = None
        prev_hour = None
        for hour, subject, teacher, wd in lessons:
            same_run = subject == run_subj and prev_hour is not None and hour == prev_hour + 1
            if same_run:
                run_len += 1
            else:
                if run_len > 0:
                    blocks.append((group, date, run_subj, run_len, run_wd, run_teacher))
                run_subj, run_len, run_teacher, run_wd = subject, 1, teacher, wd
            prev_hour = hour
        if run_len > 0:
            blocks.append((group, date, run_subj, run_len, run_wd, run_teacher))
    return blocks


def explain(length, subj_total_block_hours, subj_active_days):
    """Attempted explanation for an over-cap block.

    capacity-driven: the subject averages >cap hours per active day, so long blocks
      are structural (the budget does not fit shorter daily runs).
    deep-block: a single day carries cap+3h or more (an intensive block).
    inspiegata: a sporadic over-cap block the budget does not force.
    """
    avg = subj_total_block_hours / subj_active_days if subj_active_days else 0
    if avg > 3:
        return f"capacity-driven (subject avg {avg:.1f}h over {subj_active_days} active days)"
    if length >= 6:
        return f"deep-block ({length}h same day)"
    return "inspiegata (sporadic over-cap block, budget does not force it)"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", default=GOLDEN_DEFAULT, help=f"golden CSV or run lessons.txt (default {GOLDEN_DEFAULT})")
    ap.add_argument("--max", dest="cap", type=int, default=3, help="max hours per same-subject block (default 3, T16)")
    ap.add_argument("--subject", default=None, help="restrict to one subject (e.g. the T16 rule)")
    ap.add_argument("--top", type=int, default=12, help="top-N cutoffs for clustering tables (default 12)")
    args = ap.parse_args()

    rows = list(load_rows(args.input))
    if args.subject:
        rows = [r for r in rows if r[3] == args.subject]

    blocks = detect_blocks(rows)
    # Per-subject totals for the explanation heuristic (hours delivered on active days).
    subj_hours = defaultdict(int)
    subj_days = defaultdict(set)
    for group, date, subject, length, wd, teacher in blocks:
        subj_hours[subject] += length
        subj_days[subject].add(date)

    violating = [b for b in blocks if b[3] > args.cap]
    residual = sum(b[3] - args.cap for b in violating)
    rate = len(violating) / len(blocks) if blocks else 0.0

    subj_label = f" [{args.subject}]" if args.subject else ""
    print("=" * 74)
    print(f"TASK-472 report: block-size > {args.cap}h{subj_label} on {args.input}")
    print("=" * 74)
    print(f"same-subject blocks (adjacent hours, placed, real): {len(blocks)}")
    print(f"violating blocks (>{args.cap}h): {len(violating)}  ({rate:.1%} of blocks)")
    print(f"residual (sum of excess hours over {args.cap}): {residual}")
    print()

    def cluster(keyfn, label):
        c = Counter(keyfn(b) for b in violating)
        total = sum(c.values())
        top = c.most_common(args.top)
        share = sum(v for _, v in top[:3]) / total if total else 0
        print(f"  --- violating blocks by {label} (top {len(top)}, top-3 share {share:.0%}) ---")
        for name, n in top:
            print(f"    {str(name):40} {n}")
        print()

    print("CLUSTERING")
    cluster(lambda b: b[4], "weekday")
    cluster(lambda b: b[0], "group")
    cluster(lambda b: b[2], "subject")
    cluster(lambda b: datetime.strptime(b[1], "%Y-%m-%d").strftime("%Y-%m"), "month")

    expl = Counter()
    for group, date, subject, length, wd, teacher in violating:
        expl[explain(length, subj_hours[subject], len(subj_days[subject]))] += 1
    print("EXPLANATION MIX")
    for reason, n in expl.most_common():
        print(f"  {n:4}  {reason}")
    print()

    print(f"PER-OCCURRENCE DETAIL ({len(violating)} blocks, sorted by length desc)")
    print(f"  {'group':8} {'subject':34} {'date':12} {'h':>2} {'wd':3} {'teacher':18} explanation")
    for group, date, subject, length, wd, teacher in sorted(violating, key=lambda b: (-b[3], b[0])):
        reason = explain(length, subj_hours[subject], len(subj_days[subject]))
        print(f"  {group:8} {subject:34} {date:12} {length:>2} {wd:3} {teacher:18} {reason}")
    print()

    cap_share = sum(n for r, n in expl.items() if r.startswith("capacity-driven"))
    print("TIER PROPOSAL (TASK-472 reading key, golden-first)")
    print(f"  - rate: {rate:.1%} of same-subject blocks violate ({len(violating)}/{len(blocks)}); "
          f"capacity-driven = {cap_share}/{len(violating)}.")
    if rate == 0:
        print(f"  - verdict: golden violates it 0 times -> safe to model HARD (golden-clean).")
    else:
        print(f"  - verdict: golden violates it {len(violating)} times -> a BLANKET HARD is forbidden")
        print("    (would make the golden infeasible; golden-first). It is M, or hard-with-exceptions.")
        if cap_share > len(violating) * 0.5:
            print("  - tier: M (capacity-driven dominates: high-hour subjects must run long; little leverage).")
        elif len(violating) <= 30:
            print("  - tier: HARD-WITH-ENUMERATED-EXCEPTIONS candidate (few violations; if structural, model")
            print("          the pattern hard and enumerate the residual per pattern-exhaustion).")
        else:
            print("  - tier: M-HIGH-VALUE (violations spread/sporadic; the rule has leverage at a real medium).")
    print()
    print("Repeatable: re-run with --input <run lessons.txt> [--subject S] [--max N] to score a run the same way.")


if __name__ == "__main__":
    main()
