#!/usr/bin/env python3
"""Per-boxed-lesson placement-conflict feasibility — the TASK-398 A/B discriminator (partial).

For each lesson left in the box (null timeslot, unpinned) at FINAL solve, count how many slot
cells are free of H1 (teacher) / H3 (group) / H7 (teacher daily cap) / H11 (group daily cap)
conflicts given the final placements.

PARTIAL DISCRIMINATOR (read carefully):
- This checks ONLY the placement-conflict constraints (H1/H3/H7/H11) — the dominant, fully
  data-derivable ones. It does NOT include the VRP gate (calendar H20 / quota H23-H24 / teacher
  availability H5 / holiday H6 / stage H4,H25), which lives in Java Lesson.getAvailableTimeslots
  and can't be faithfully replicated here without re-implementing those filters (a wrong replica
  would give a wrong verdict).
- A boxed lesson with 0 placement-conflict-free cells is DEFINITELY stuck (strong "B": the
  placements themselves deadlock it) — relaxing recurrence / rearranging blocks is the lever.
- A boxed lesson with >0 free cells is NECESSARY-but-not-SUFFICIENT for placeable: those cells
  might still be VRP-illegal (a holiday, the teacher unavailable, a closed calendar slot). >0
  leans "A" (an LS-coverage miss — a slot exists, LS didn't take it) but needs the Java VRP
  check to confirm. So >0 is reported as "lean A (pending VRP)", not a verdict.
- The full VRP-filtered discriminator is a Fedora-side Java follow-up (a test or endpoint calling
  getAvailableTimeslots); this script is the achievable laptop-collection version.

Inputs (pipe-delimited, one per line; header-ish lines starting with non-data are skipped):
  lessons.txt : group|date|hour|subject|teacher|pinned   (the PLACED lessons, JOIN timeslot)
  boxed.txt   : group|teacher|subject|grpMaxHrs|tchMaxHrs (the BOXED lessons: NOT pinned, timeslot NULL)
  grid.txt    : date|hour                                 (every Timeslot cell, e.g. 1368)

Usage: box-feasibility.py <lessons.txt> <boxed.txt> <grid.txt>
"""
import collections
import sys


def load_cells(path):
    """group|date|hour|... -> occupancy maps."""
    teacher_occ = collections.defaultdict(set)   # teacher -> set of (date,hour)
    group_occ = collections.defaultdict(set)     # group   -> set of (date,hour)
    teacher_daily = collections.defaultdict(lambda: collections.Counter())  # teacher -> {date: count}
    group_daily = collections.defaultdict(lambda: collections.Counter())    # group   -> {date: count}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or "|" not in line or line.startswith(("SET", "group", "date")):
                continue
            p = line.split("|")
            if len(p) < 5:
                continue
            grp, date, hour, _subj, teacher = p[0], p[1], p[2], p[3], p[4]
            cell = (date, hour)
            if teacher:
                teacher_occ[teacher].add(cell)
                teacher_daily[teacher][date] += 1
            if grp:
                group_occ[grp].add(cell)
                group_daily[grp][date] += 1
    return teacher_occ, group_occ, teacher_daily, group_daily


def load_grid(path):
    cells = set()
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or "|" not in line or line.startswith(("SET", "date")):
                continue
            p = line.split("|")
            if len(p) >= 2:
                cells.add((p[0], p[1]))
    return cells


def load_boxed(path):
    boxed = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or "|" not in line or line.startswith(("SET", "group")):
                continue
            p = line.split("|")
            if len(p) >= 5:
                grp, teacher, subj = p[0], p[1], p[2]
                try:
                    grp_cap = int(p[3])
                except ValueError:
                    grp_cap = 0
                try:
                    tch_cap = int(p[4])
                except ValueError:
                    tch_cap = 0
                boxed.append((grp, teacher, subj, grp_cap, tch_cap))
    return boxed


def main():
    if len(sys.argv) < 4:
        print("Usage: box-feasibility.py <lessons.txt> <boxed.txt> <grid.txt>")
        sys.exit(2)
    teacher_occ, group_occ, teacher_daily, group_daily = load_cells(sys.argv[1])
    boxed = load_boxed(sys.argv[2])
    grid = load_grid(sys.argv[3])

    print(f"Box feasibility (H1/H3/H7/H11 placement-conflict only; VRP NOT included).")
    print(f"Grid cells: {len(grid)} | boxed lessons: {len(boxed)}")
    print(f"Per boxed lesson — free cells (teacher free AND group free AND caps OK):")
    by_group = collections.Counter()
    by_teacher = collections.Counter()
    zero_free = 0
    for grp, teacher, subj, grp_cap, tch_cap in boxed:
        by_group[grp] += 1
        if teacher:
            by_teacher[teacher] += 1
        tocc = teacher_occ.get(teacher, set())
        gocc = group_occ.get(grp, set())
        td = teacher_daily.get(teacher, {})
        gd = group_daily.get(grp, {})
        free = 0
        for (date, hour) in grid:
            if (date, hour) in tocc or (date, hour) in gocc:
                continue
            if tch_cap > 0 and td.get(date, 0) >= tch_cap:
                continue
            if grp_cap > 0 and gd.get(date, 0) >= grp_cap:
                continue
            free += 1
        if free == 0:
            zero_free += 1
        verdict = "DEFINITELY STUCK (strong B)" if free == 0 else f"lean A (pending VRP) — {free} cells"
        print(f"  {grp:8} | {teacher:14} | {subj:36} | grpCap={grp_cap} tchCap={tch_cap} -> {verdict}")
    print()
    print("Box composition by group: " + ", ".join(f"{g}:{n}" for g, n in by_group.most_common()))
    print("Box composition by teacher: " + ", ".join(f"{t}:{n}" for t, n in by_teacher.most_common()))
    print(f"SUMMARY: {zero_free}/{len(boxed)} boxed lessons have 0 placement-conflict-free cells "
          f"(definitely stuck); {len(boxed)-zero_free}/{len(boxed)} have free cells (lean A, pending VRP).")
    print("NOTE: a full A-vs-B verdict needs the Java getAvailableTimeslots VRP check (Fedora follow-up).")


if __name__ == "__main__":
    main()
