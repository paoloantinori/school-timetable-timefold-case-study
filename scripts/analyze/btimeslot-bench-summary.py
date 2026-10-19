#!/usr/bin/env python3
"""Per-arm summary for the B-TIMESLOT-1 metaheuristic benchmark (notte2, 2026-07-31).

Reads a results directory containing one subdir per arm (as produced by
capture-btimeslot-arm.sh) and prints a comparison table of the metrics that decide which
phase-2 acceptor wins. Read-only, safe to run any time (also mid-bench on the arms done so far).

Per arm it extracts:
  - B-TIMESLOT-1 end: the FIRST phase-2 LS run's timeslot-tier hard (= -2 * box, H0 weight 2),
    box count, and medium. This is the primary metaheuristic-quality metric (the acceptor's
    result from the promoted construction, before repairs/rooms).
  - B-TIMESLOT iteration count + final box (how fast the staged cycle cleared the residual).
  - Final score (after B-ROOM) hard/medium.
  - Full-day rate, internal holes, unpinned recurrence (KPI context; an arm that wins on score
    by shredding KPIs loses - laptop-RUNBOOK verdict criteria).

Usage: python3 scripts/analyze/btimeslot-bench-summary.py claudedocs/spalmone-2026-27/bench-btimeslot1
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import parse_score_string  # noqa: E402 (dual-format: legacy + BendableScore bracket)


def parse_score(text):
    """'score: -121hard/-964300medium/17548soft' or '[-121/0]hard/[-964300/17548]soft' -> (h,m,s)."""
    return parse_score_string(text)


def parse_btimeslot_stages(path):
    """From btimeslot-stages.txt: (iter1_tt_hard, iter1_box, iter1_medium, n_iters, final_box)."""
    if not path.exists():
        return None
    iters = []
    for line in path.read_text(errors="replace").splitlines():
        m = re.search(r"stage B-TIMESLOT #(\d+) ended.*timeslot-tier hard=(-?\d+), box=(\d+)", line)
        if m:
            iters.append((int(m.group(2)), int(m.group(3))))
            sm = re.search(r"score=(-?\d+)hard/(-?\d+)medium", line)
            # store medium too by re-scanning the same line
        if iters:
            sm = re.search(r"score=(-?\d+)hard/(-?\d+)medium/(-?\d+)soft", line)
            if sm and iters:
                # attach medium to the last-seen iteration
                h, med, s = int(sm.group(1)), int(sm.group(2)), int(sm.group(3))
                last = iters[-1]
                iters[-1] = (last[0], last[1], med)
    if not iters:
        return None
    first = iters[0]
    first_medium = first[2] if len(first) > 2 else None
    return (first[0], first[1], first_medium, len(iters), iters[-1][1])


def parse_full_day(path):
    if not path.exists():
        return None
    m = re.search(r"FULL-DAY RATE:\s*(\d+)/(\d+)\s*=\s*([\d.]+)%", path.read_text(errors="replace"))
    return f"{m.group(3)}%" if m else None


def parse_holes(path):
    if not path.exists():
        return None
    m = re.search(r"BUCO-INTERNO:\s*(\d+) giorni", path.read_text(errors="replace"))
    return f"{m.group(1)}d" if m else None


def parse_recurrence(path):
    if not path.exists():
        return None
    txt = path.read_text(errors="replace")
    # prefer the unpinned/overall line; take the first percentage that looks like a recurrence
    m = re.search(r"(\d{2,3}[\.,]?\d*)\s*%", txt)
    return f"{m.group(1)}%" if m else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("results_dir")
    args = ap.parse_args()
    root = Path(args.results_dir)
    if not root.is_dir():
        sys.exit(f"not a dir: {root}")

    arm_dirs = sorted(d for d in root.iterdir() if d.is_dir() and d.name.startswith("arm-"))
    if not arm_dirs:
        sys.exit(f"no arm-* subdirs in {root}")

    rows = []
    for d in arm_dirs:
        score = parse_score((d / "score.txt").read_text(errors="replace") if (d / "score.txt").exists() else "")
        bt = parse_btimeslot_stages(d / "btimeslot-stages.txt")
        fd = parse_full_day(d / "kpi-fill.txt")
        holes = parse_holes(d / "kpi-holes.txt")
        rec = parse_recurrence(d / "kpi-resemblance.txt")
        rows.append((d.name, bt, score, fd, holes, rec))

    # Header
    print(f"{'arm':<18} {'BT1 ttH':>8} {'BT1 box':>8} {'BT1 med':>10} {'iters':>6} {'final H':>9} {'final M':>10} {'fullday':>8} {'holes':>6} {'rec%':>7}")
    print("-" * 100)
    for name, bt, score, fd, holes, rec in rows:
        bt1_h = f"{bt[0]}" if bt else "?"
        bt1_box = f"{bt[1]}" if bt else "?"
        bt1_med = f"{bt[2]:,}" if bt and bt[2] is not None else "?"
        iters = f"{bt[3]}" if bt else "?"
        fh = f"{score[0]:,}" if score else "?"
        fm = f"{score[1]:,}" if score else "?"
        print(f"{name:<18} {bt1_h:>8} {bt1_box:>8} {bt1_med:>10} {iters:>6} {fh:>9} {fm:>10} {fd or '?':>8} {holes or '?':>6} {rec or '?':>7}")


if __name__ == "__main__":
    main()
