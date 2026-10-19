#!/usr/bin/env python3
"""Day anatomy viz: one (group, date) under the lens, golden vs run side by side.

The recurring table I built by hand twice in two days (G13 2027-06-10 for the
erosion diagnosis, G06 2027-06-09 for the H34 near miss) becomes one command.
Each slot row shows the RUN composition and the GOLDEN composition; empty
declared slots are highlighted (the H34/H17 anatomy). Optional phase columns
(the filmstrip: pass the staged-checkpoints dir and the day renders per phase
dump, the 631 erosion lens).

Usage: day_anatomy_viz.py <run-lessons.txt> --group "G06" --date 2027-06-09
       [--golden seed/lessons.csv] [--phases staged-checkpoints/attempt1] [--out out.html]
"""
import argparse
import csv
import html
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SLOTS = [str(h) for h in range(8, 16)]


def load_run_day(path: Path, group: str, date: str):
    day = {}
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if not line or "|" not in line:
                continue
            p = line.split("|")
            if len(p) >= 5 and p[0].strip() == group and p[1] == date:
                day[p[2]] = (p[4], p[3])
    return day


def load_golden_day(path: Path, group: str, date: str):
    day = {}
    with open(path) as fh:
        for row in csv.DictReader(fh):
            if row["group"] == group and row["date"] == date:
                slot = row["slot"][:2]  # "08:00 09:00" -> "08"
                day[str(int(slot))] = (row["teacher"], row["subject"])
    return day


def cell(entry):
    if entry is None:
        return ("<td style='background:#f2dede;color:#a94442;padding:2px 6px;"
                "font-size:11px'>EMPTY</td>")
    teacher, subject = entry
    return (f"<td style='padding:2px 6px;font-size:11px'>{html.escape(teacher)}"
            f"<br><span style='color:#666'>{html.escape(subject)}</span></td>")


def render(group, date, columns):
    heads = "".join(f"<th style='font-size:11px'>{html.escape(h)}</th>"
                    for h, _ in columns)
    rows = []
    for s in SLOTS:
        tds = "".join(cell(day.get(s)) for _, day in columns)
        rows.append(f"<tr><td style='font-weight:bold;font-size:11px'>{s}</td>{tds}</tr>")
    return (f"<html><head><meta charset='utf-8'></head><body>"
            f"<h3>Day anatomy: {html.escape(group)} {date}</h3>"
            f"<table border='1' cellpadding='2' cellspacing='0'>"
            f"<tr><th>slot</th>{heads}</tr>{''.join(rows)}</table>"
            f"<p style='font-size:11px'>EMPTY cells highlighted: the H34 (whole-day) and"
            f" H17 (internal gap) anatomy reads at a glance.</p></body></html>")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("run_lessons")
    ap.add_argument("--group", required=True)
    ap.add_argument("--date", required=True)
    ap.add_argument("--golden", default=str(REPO / "src/main/resources/seed/lessons.csv"))
    ap.add_argument("--phases", help="staged-checkpoints dir: renders one column per phase dump")
    ap.add_argument("--out", default="day-anatomy.html")
    args = ap.parse_args()

    columns = [("golden", load_golden_day(Path(args.golden), args.group, args.date)),
               ("run", load_run_day(Path(args.run_lessons), args.group, args.date))]
    if args.phases:
        pdir = Path(args.phases)
        for dump in sorted(pdir.glob("POST_*.json")):
            # phase dumps are solver-internal JSON: teacher/subject per slot is
            # not trivially extractable without the reader; declared limitation:
            # the filmstrip needs the lessons.txt form. Skipped with a note.
            print(f"NOTE phase {dump.name}: JSON form not supported yet (filmstrip TODO)")
    Path(args.out).write_text(render(args.group, args.date, columns), encoding="utf-8")
    print(f"OK wrote {args.out}")


if __name__ == "__main__":
    main()
