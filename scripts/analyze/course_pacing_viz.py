#!/usr/bin/env python3
"""Course pacing viz: golden vs run hour distribution over the months.

The 636-class finder (maintainer 21/09: "ragiona sui dataviz"). The question it
answers: is a course's hour distribution over the year golden-shaped? The
G06/Servizio tecnico case (golden: 10 bursts by Feb; run: spread through
June, root of the -1 H34) took hours of ad-hoc awk tables; this makes it a
one-second look. Values ON the elements (bars + numbers), no hover-only data.

Usage: course_pacing_viz.py <run-lessons.txt> [--golden seed/lessons.csv]
       [--group "G06"] [--out out.html] [--top N]
Without --group: one section per group, the N courses with the largest
golden-vs-run month divergence first (the finding order, not alphabetical).
"""
import argparse
import csv
import html
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MONTHS = ["2026-09", "2026-10", "2026-11", "2026-12",
          "2027-01", "2027-02", "2027-03", "2027-04", "2027-05", "2027-06"]


def month_of(date: str) -> str:
    return date[:7]


def load_golden(path: Path):
    dist = defaultdict(lambda: defaultdict(int))  # (group, subject) -> month -> h
    with open(path) as fh:
        for row in csv.DictReader(fh):
            if row["teacher"] == "Stage":
                continue
            dist[(row["group"], row["subject"])][month_of(row["date"])] += 1
    return dist


def load_run(path: Path):
    dist = defaultdict(lambda: defaultdict(int))
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if not line or line == "SET" or "|" not in line:
                continue
            p = line.split("|")
            if len(p) < 5 or p[4] == "Stage":
                continue
            dist[(p[0].strip(), p[3])][month_of(p[1])] += 1
    return dist


def divergence(golden_months, run_months):
    """Total absolute month-by-month difference (the pacing-shape distance)."""
    return sum(abs(golden_months.get(m, 0) - run_months.get(m, 0)) for m in MONTHS)


def bars(gm, rm):
    gmax = max(list(gm.values()) + list(rm.values()) + [1])
    cells = []
    for m in MONTHS:
        g, r = gm.get(m, 0), rm.get(m, 0)
        same = "" if g != r else " SAME"
        cells.append(
            f'<div style="display:inline-block;width:9%;vertical-align:top;'
            f'margin:1px;font-size:10px;text-align:center">'
            f'<div style="background:#888;color:#fff;height:{max(2, int(60*g/gmax))}px">'
            f'{g}</div>'
            f'<div style="background:#4a90d9;color:#fff;height:{max(2, int(60*r/gmax))}px">'
            f'{r}</div>'
            f'<div style="border-top:1px solid #ccc">{m[2:]}{same}</div></div>')
    return "".join(cells)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("run_lessons")
    ap.add_argument("--golden", default=str(REPO / "src/main/resources/seed/lessons.csv"))
    ap.add_argument("--group")
    ap.add_argument("--out", default="course-pacing.html")
    ap.add_argument("--top", type=int, default=8)
    args = ap.parse_args()

    golden = load_golden(Path(args.golden))
    run = load_run(Path(args.run_lessons))
    keys = set(golden) | set(run)
    if args.group:
        keys = {k for k in keys if k[0] == args.group}

    by_group = defaultdict(list)
    for k in keys:
        d = divergence(golden.get(k, {}), run.get(k, {}))
        if d > 0:
            by_group[k[0]].append((d, k))
    for g in by_group:
        by_group[g].sort(reverse=True)

    parts = [f"<html><head><meta charset='utf-8'><title>course pacing</title></head><body>"
             f"<h3>Course pacing, golden vs run: {html.escape(args.run_lessons)}</h3>"
             f"<p style='font-size:11px'>grey = golden hours/month, blue = run hours/month."
             f" Courses ordered by total month-divergence (worst pacing first).</p>"]
    for g in sorted(by_group, key=lambda g: -sum(d for d, _ in by_group[g][:args.top])):
        parts.append(f"<h4>{html.escape(g)}</h4>")
        for d, k in by_group[g][:args.top]:
            label = f"{html.escape(k[1])} <span style='color:#999'>(div {d})</span>"
            parts.append(f"<div style='margin:6px 0'><div style='font-size:12px'>{label}</div>"
                         f"<div>{bars(golden.get(k, {}), run.get(k, {}))}</div></div>")
    parts.append("</body></html>")
    Path(args.out).write_text("".join(parts), encoding="utf-8")
    print(f"OK wrote {args.out} ({sum(len(v) for v in by_group.values())} courses, "
          f"{len(by_group)} groups)")


if __name__ == "__main__":
    main()
