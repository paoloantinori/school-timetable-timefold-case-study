#!/usr/bin/env python3
"""TASK-637: residual decomposition of a delivered run capture.

The distilled mechanical half of the zero-hard loop (maintainer, 21/09: "in
automatico tu analizzi i risultati, identifichi se e quali valga la pena di
cercare di migliorare"). Input: a capture dir (lessons.txt + score.txt, the
capture-run.sh shape). Output: the residual's family decomposition, the exact
cells (scope-correct - the lessons of 20/09 are baked in), the cure-registry
lookup, and a worth-improving ranking.

Scope rules learned the hard way (each was a wrong first attempt):
  - grid dates only (a date with NO timeslot is out of scope: weekend sheet
    rows manufacture phantom cells);
  - modality S days are stage-only (no ordinary quota obligation);
  - G04 / G08 / G14 are out of the H32/H34 scope (their target is the
    pool-cap form-b, not the sheet quota);
  - dump lines may or may not carry a leading space (both forms in the wild).

Usage: residual_decomposition.py <capture-dir> [--registry <path>]
Exit 0 always (a report, not a gate: the sanity gate is separate).
"""
import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SEED_DIR = REPO / "src/main/resources/seed"
H32_H34_OUT_OF_SCOPE = {"G04", "G08", "G14"}


def load_quota():
    """Declared ORDINARY quota per (group, date): grid dates, A/L days only.

    For FCQuota groups (the calendario_fc.csv rows: 1/2/3 FC + G13) the
    fine-grained aula/lab sheet is AUTHORITATIVE and calendario_class_days is a
    coarse duplicate of the same group (the 21/09 lesson: max() over both
    manufactured phantom H32 cells for G13 Dec 21-22, sheet 6 vs fc 5, golden
    exact at 5). For everyone else class_days is the only source.
    """
    import csv
    FC_groups = set()
    fine = {}
    with open(SEED_DIR / "calendario_fc.csv") as fh:
        for row in csv.DictReader(fh):
            FC_groups.add(row["group"].strip())
            q = int(row["aula_hours"] or 0) + int(row["lab_hours"] or 0)
            if q > 0:
                fine[(row["group"].strip(), row["date"])] = q
    quota = {}
    with open(SEED_DIR / "calendario_class_days.csv") as fh:
        for row in csv.DictReader(fh):
            if row["modality"].strip().upper() in ("A", "L") and row["group"].strip() not in FC_groups:
                quota[(row["group"].strip(), row["date"])] = int(row["hours"] or 0)
    quota.update(fine)
    return quota


def load_lessons(path: Path):
    lessons = []
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if not line or line == "SET" or "|" not in line:
                continue
            p = line.split("|")
            if len(p) >= 5:
                lessons.append((p[0].strip(), p[1], p[2], p[3], p[4]))
    return lessons


def analyze(capture_dir: Path, registry_path: Path):
    lessons_file = capture_dir / "lessons.txt"
    if not lessons_file.exists():
        # nearmiss/ subdirectory or run/ shape: try one level down
        for alt in capture_dir.glob("*/lessons.txt"):
            lessons_file = alt
            break
    if not lessons_file.exists():
        print(f"ERR no lessons.txt under {capture_dir}")
        return 1
    lessons = load_lessons(lessons_file)
    grid_dates = {l[1] for l in lessons}
    quota = load_quota()

    placed = defaultdict(int)          # (group, date) -> ordinary hours
    teacher_day = defaultdict(int)     # (teacher, date) -> hours
    for g, d, _slot, _subject, teacher in lessons:
        if teacher != "Stage":
            placed[(g, d)] += 1
            teacher_day[(teacher, d)] += 1

    report = {"capture": str(capture_dir), "lessons": len(lessons), "families": {}}

    # H34: declared ordinary day, in scope, zero ordinary placed
    h34 = [(g, d, q) for (g, d), q in sorted(quota.items())
           if q > 0 and placed[(g, d)] == 0 and d in grid_dates
           and g not in H32_H34_OUT_OF_SCOPE]
    if h34:
        report["families"]["H34"] = {
            "cells": [{"group": g, "date": d, "quota": q} for g, d, q in h34],
            "note": "empty declared day(s). CURE: full fill or nothing (partial = H32 poison)."
                    " Check golden practice + course templateness (TASK-636 chain)."}

    # H32: day with lessons but below quota
    h32 = [(g, d, q, placed[(g, d)]) for (g, d), q in sorted(quota.items())
           if q > 0 and 0 < placed[(g, d)] < q and d in grid_dates
           and g not in H32_H34_OUT_OF_SCOPE]
    if h32:
        report["families"]["H32"] = {
            "cells": [{"group": g, "date": d, "quota": q, "placed": p}
                      for g, d, q, p in h32],
            "note": "under-quota day(s): QuotaFillRepair/EDF territory. CANDIDATES:"
                    " the live H32 may exempt some (sheet contradictions, scope"
                    " subtleties): cross-check analysis.json before chasing."}

    # H0: staged? boxed lessons are absent from lessons.txt entirely - the
    # score.txt hard tells the depth; report hard score for context.
    score_file = capture_dir / "score.txt"
    if score_file.exists():
        for line in score_file.read_text().splitlines():
            if line.startswith("score:"):
                report["deliveredScore"] = line.strip()

    # H7 candidates: teacher-day over 6 (cap heuristic: most caps are 6; the
    # authoritative cap per teacher lives in the DB, this is the report proxy)
    h7 = [(t, d, n) for (t, d), n in sorted(teacher_day.items()) if n > 6]
    if h7:
        report["families"]["H7-candidates"] = {
            "cells": [{"teacher": t, "date": d, "hours": n} for t, d, n in h7],
            "note": "teacher-day > 6h (proxy: authoritative caps may exempt 8h teachers)."}

    # registry lookup + worth-improving ranking
    ranking = []
    if registry_path.exists():
        registry = json.loads(registry_path.read_text())
        for fam, info in registry["families"].items():
            present = fam in report["families"] or (
                fam == "H7" and "H7-candidates" in report["families"])
            if present:
                ranking.append({
                    "family": fam,
                    "status": info["status"],
                    "task": info["task"],
                    "cause": info["cause"],
                    "worth": "YES" if "IN-COSTRUZIONE" in info["status"] or "PARZIALE" in info["status"] else "already cured - verify on this state",
                })
    report["worthImproving"] = ranking
    print(json.dumps(report, indent=2, ensure_ascii=False))

    # TASK-637 (maintainer: "aggancia la produzione e lettura del dataviz al flusso"):
    # the analysis emits its own consumption layer: a day-anatomy HTML per
    # residual cell and the pacing profile of the touched groups, next to the
    # JSON. The zero-hard-loop skill's phase 1 REQUIRES opening them before any
    # verdict. Sibling scripts invoked as subprocesses (best-effort decoration:
    # a failure here never breaks the report).
    import subprocess
    viz_dir = Path(__file__).resolve().parent
    out_dir = capture_dir
    cells = (report["families"].get("H34", {}).get("cells", [])
             + report["families"].get("H32", {}).get("cells", []))
    touched_groups = sorted({c["group"] for c in cells})
    for cell in cells:
        out = out_dir / f"day-anatomy-{cell['group']}-{cell['date']}.html"
        subprocess.run([sys.executable, str(viz_dir / "day_anatomy_viz.py"),
                        str(lessons_file), "--group", cell["group"],
                        "--date", cell["date"], "--out", str(out)],
                       capture_output=True)
        if out.exists():
            report.setdefault("viz", []).append(str(out))
            print(f"viz: {out}")
    if touched_groups:
        pacing = out_dir / "course-pacing.html"
        # no --group filter: the full all-groups view renders every group
        # ordered by divergence (touched groups are a subset, in context)
        subprocess.run([sys.executable, str(viz_dir / "course_pacing_viz.py"),
                        str(lessons_file), "--out", str(pacing)],
                       capture_output=True)
        if pacing.exists():
            report.setdefault("viz", []).append(str(pacing))
            print(f"viz: {pacing} (touched groups: {', '.join(touched_groups)})")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("capture_dir")
    ap.add_argument("--registry", default=str(
        REPO / "claudedocs/spalmone-2026-27/cure-registry.json"))
    args = ap.parse_args()
    return analyze(Path(args.capture_dir), Path(args.registry))


if __name__ == "__main__":
    sys.exit(main())
