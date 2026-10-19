#!/usr/bin/env python3
"""TASK-542: convert a staged-cycle boundary checkpoint (grid of lesson placements) into the
lessons.txt dump format (`group|date|hour|subject|teacher|pinned`) the KPI battery and the
TASK-535 sanity gate (solution-sanity-check.py) consume. Joins the checkpoint's placement ids
against the live DB (read-only via scripts/db/db-query.sh) for the descriptive columns.

Usage: checkpoint-to-dump.py <checkpoint.json> [> lessons.txt]
"""
import json
import subprocess
import sys
from datetime import datetime

DB = ["./scripts/db/db-query.sh"]


def q(sql: str) -> str:
    out = subprocess.run(DB + [sql], capture_output=True, text=True, check=True)
    return out.stdout


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    ckpt = json.load(open(sys.argv[1]))
    ids = [str(p["lessonId"]) for p in ckpt["placements"]]
    # one join query: placement columns come from the CHECKPOINT (the DB may have moved on),
    # descriptive columns from the DB (they are inputs, invariant across the cycle)
    rows = q(
        "SELECT l.id, sg.name, c.subject, t.name, ts.startdatetime FROM lesson l "
        "JOIN course c ON c.id = l.course_id "
        "JOIN studentgroup sg ON sg.id = c.studentgroup_id "
        "JOIN teacher t ON t.id = c.teacher_id "
        "LEFT JOIN timeslot ts ON ts.id = l.timeslot_id "
        f"WHERE l.id IN ({','.join(ids)})"
    )
    desc = {}
    for line in rows.splitlines():
        parts = [p.strip() for p in line.split("|")]
        if len(parts) != 5 or not parts[0].isdigit():
            continue
        lid, group, subject, teacher, start = parts
        hour = ""
        date = ""
        if start:
            try:
                dt = datetime.fromisoformat(start)
                date = dt.strftime("%Y-%m-%d")
                hour = str(dt.hour)
            except ValueError:
                pass
        desc[int(lid)] = (group, date, hour, subject, teacher)

    pinned = {p["lessonId"] for p in ckpt["placements"] if p["pinned"]}
    out = []
    for p in ckpt["placements"]:
        if p["timeslotId"] is None:
            continue  # boxed: not on the grid, invisible to the sanity battery
        lid = p["lessonId"]
        group, date, hour, subject, teacher = desc.get(lid, ("?", "?", "?", "?", "?"))
        out.append(f"{group}|{date}|{hour}|{subject}|{teacher}|"
                   f"{'P' if lid in pinned else ''}")
    print("\n".join(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
