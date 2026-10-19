#!/usr/bin/env python3
"""TASK-469 M-CPS confirmation KPI (NO new solve).

Independently computes what the M-CPS constraint WOULD score on an existing run's
lessons.txt, to confirm the constraint measures what it should: the count of post-stage
curriculum hours per perimeter group (G10 / G08 / G11), vs the golden baseline
(G10=6, G08=5, G11=29) and the 6h target.

This mirrors curriculumPostStage's logic (perimeter (group,subject) normalized +
date > stage-end, group by group, count lessons; deficit x full-weight 100, excess x
weak-weight 1, target 6) in pure Python, so it is an independent cross-check. It does
NOT enable the flag or run the solver. Reusable for the AC#4 A/B.

Usage: python3 scripts/analyze/curriculum-poststage-kpi.py <lessons.txt> [lessons.txt ...]
Format expected: ' GROUP|date|hour|subject|teacher|pinned' (leading space, as capture-run writes).
"""
import sys
from collections import defaultdict

# Perimeter: final-year cohort -> its curriculum subject set (T11), in the CANONICAL
# (normalized) Title-Case form that lessons carry at runtime (SubjectNormalization.normalize:
# "Comportamento sociale" -> "Comportamento Sociale", "Educazione civica" -> "Educazione Civica").
PERIMETER = {
    "G10":  {"Comportamento Sociale"},
    "G08":  {"Competenze di cittadinanza", "Educazione Civica"},
    "G11": {"Personalizzazione"},
}
# Stage-end per group = INPUT-derived (max DailyActivityQuota.stageHours>0 date), the SAME source
# the constraint uses (SchoolSchedule.lastStageDateFor). Verified 2026-08-01 from seed CSVs
# (calendario_class_days.csv modality S / calendario_fc.csv, \r stripped): G10 2027-04-23,
# G08 2027-04-26, G11 2027-03-18.
STAGE_END = {
    "G10":  "2027-04-23",
    "G08":  "2027-04-26",
    "G11": "2027-03-18",
}
BASELINE = {"G10": 6, "G08": 5, "G11": 29}
TARGET = 6


def measure(path):
    counts = defaultdict(int)
    also_total = defaultdict(int)  # incl. pre-stage, for context (should match seed course hours)
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line == "SET" or "|" not in line:
                continue
            parts = line.split("|")
            if len(parts) < 5:
                continue
            group, date, _hour, subject = parts[0], parts[1], parts[2], parts[3]
            subs = PERIMETER.get(group)
            if not subs or subject not in subs:
                continue
            also_total[group] += 1
            if date > STAGE_END[group]:  # ISO date string compare == chronological for YYYY-MM-DD
                counts[group] += 1
    return counts, also_total


def main(paths):
    for path in paths:
        counts, total = measure(path)
        print(f"\n=== {path} ===")
        print(f"{'group':8} {'post-stage':>10} {'baseline':>8} {'total-curr':>10} {'vs target 6':>14}")
        for g in PERIMETER:
            c = counts.get(g, 0)
            diff = c - TARGET
            tag = "at target" if diff == 0 else (f"deficit {-diff}" if diff < 0 else f"excess +{diff}")
            print(f"{g:8} {c:>10} {BASELINE[g]:>8} {total.get(g, 0):>10} {tag:>14}")
        score = 0
        for g in PERIMETER:
            c = counts.get(g, 0)
            if c < TARGET:
                score += (TARGET - c) * 100
            elif c > TARGET:
                score += (c - TARGET) * 1
        print(f"M-CPS would score (full=100/weak=1): -{score} medium across the 3 perimeter groups")


if __name__ == "__main__":
    main(sys.argv[1:])
