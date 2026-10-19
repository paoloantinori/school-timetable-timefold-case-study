#!/usr/bin/env python3
"""Tag-capacity excess KPI (TASK-455 / scarcity-aware spec, maintainer 2026-07-30).

Measures, on a run's export.json.gz, whether assigned lessons overflow the
physical capacity of each lab ROOM TAG per (date, hour) cell, and how the
overflow (and the H29 room-null lessons) concentrate by (tag, weekday).

This is the read-only diagnosis the scarcity-aware spec (sec 2.2-2.3) says the
ANNUAL tag measure cannot give: annually nothing is scarce, but per (tag,
weekday) single-room labs saturate. It is the target finder for the TASK-455
capacity constraint.

Definitions (all verified against the 30e export keys):
  - Cell = (date, startDateTime). timeslot has NO slotIndex in export.json
    (the historical aggregation artifact); hour-of-start is the canonical key.
  - Required tag = lesson.course.requiredRoomTags (runtime field set at course
    generation, zero drift vs re-deriving subject->tag).
  - Capacity(tag) = #rooms with that tag AND room.assignable (cap>0; AULA
    POLIVALENTE cap-0 excluded, TASK-456).
  - H29 population = placed (timeslot!=null) AND room==null AND not pinned AND
    not pseudo-teacher. Discovered on the data to reproduce exactly the score's
    H29 match count (30e: 169); do NOT assume the filter.

Two views are printed:
  (A) DIRECT: the H29 lessons bucketed by (required tag, weekday) -> is the
      room-null deficit concentrated (TASK-455 has a target) or diffuse?
  (B) CAPACITY MODEL: demand = ALL placed lessons requiring the tag in the cell
      (pinned included: they physically occupy a slot) vs capacity; the excess
      bucketed by (tag, weekday) -> what TASK-455 would actually penalize.

Causality check: of the H29 lessons, how many sit in a cell where their own tag
still had spare capacity. Expected ~0 (room-null is a contention effect, not a
missing-room-when-free effect) - reproduces spec sec 2.3 at the H29 count.

Usage:
  python3 scripts/analyze/tag-capacity-excess-kpi.py <export.json.gz>

Prints a compact SUMMARY line first (parseable for A/B), then the two tables.
"""
import gzip
import json
import sys
from collections import Counter, defaultdict
from datetime import date as _date

WD = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
WD3 = [w[:3].upper() for w in WD]


def load(path):
    op = gzip.open if path.endswith((".gz", ".gzip")) else open
    with op(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def capacity_by_tag(rooms):
    rbt = defaultdict(list)
    for r in rooms:
        if not r.get("assignable"):
            continue
        for t in r.get("tags") or []:
            rbt[t].append(r["name"])
    return {t: len(v) for t, v in rbt.items()}, rbt


def is_pinned(l):
    return bool(l.get("pinned")) or bool(l.get("promotedPin"))


def is_pseudo(l):
    return bool((l.get("teacher") or {}).get("pseudo"))


def tag_of(l):
    tags = l["course"].get("requiredRoomTags") or []
    return tags[0] if tags else None


def cell_key(l):
    ts = l["timeslot"]
    return (ts["date"], ts["startDateTime"])


def wd_of(l):
    return l["timeslot"]["dayOfWeek"]


def wd_of_date(d):
    return WD[_date.fromisoformat(d).weekday()]


def run(path):
    data = load(path)
    rooms, lessons = data["rooms"], data["lessons"]
    cap_by_tag, rooms_by_tag = capacity_by_tag(rooms)

    placed = [l for l in lessons if l.get("timeslot") is not None]
    prn = [l for l in placed if l.get("room") is None]
    h29 = [l for l in prn if not is_pinned(l) and not is_pseudo(l)]

    print("ASSIGNABLE ROOMS PER TAG:")
    for t in sorted(cap_by_tag):
        print(f"  {t:18s} cap={cap_by_tag[t]}  rooms={rooms_by_tag[t]}")
    print(f"\nplaced={len(placed)} placed-room-null={len(prn)} H29(not-pinned,not-pseudo)={len(h29)}")

    # (A) direct bucket of H29 by (tag, weekday)
    tw = defaultdict(Counter)
    for l in h29:
        tw[tag_of(l)][wd_of(l)] += 1

    # (B) capacity model: demand over ALL placed, excess by (tag, weekday)
    demand = defaultdict(Counter)
    for l in placed:
        t = tag_of(l)
        if t is None:
            continue
        demand[t][cell_key(l)] += 1
    ex_cells = defaultdict(Counter)
    ex_hours = defaultdict(Counter)
    worst = {}
    tot_ex = 0
    tot_cells = 0
    for t, cells in demand.items():
        cap = cap_by_tag.get(t, 0)
        for (d, _start), cnt in cells.items():
            ex = cnt - cap
            if ex > 0:
                w = wd_of_date(d)
                ex_cells[t][w] += 1
                ex_hours[t][w] += ex
                tot_ex += ex
                tot_cells += 1
                if ex > worst.get(t, (0, None))[0]:
                    worst[t] = (ex, (d, _start))

    # causality
    spare = contended = 0
    for l in h29:
        t = tag_of(l)
        if t is None:
            continue
        cap = cap_by_tag.get(t, 0)
        cnt = demand[t].get(cell_key(l), 0)
        if cnt <= cap:
            spare += 1
        else:
            contended += 1

    # compact SUMMARY (parseable: key=value pairs)
    top_tag = max(tw, key=lambda t: sum(tw[t].values())) if tw else None
    top_val = sum(tw[top_tag].values()) if top_tag else 0
    print(f"\nSUMMARY h29={len(h29)} excess_total={tot_ex} overcap_cells={tot_cells} "
          f"top_tag={top_tag} top_tag_h29={top_val} causality_spare={spare} causality_contended={contended}")

    def print_table(title, mat, value_of):
        print(f"\n{'=' * 82}\n{title}\n{'=' * 82}")
        print(f"{'tag':18s}  " + "  ".join(f"{w:>7s}" for w in WD3) + f"  {'TOT':>5s}")
        for t in sorted(mat, key=lambda x: str(x)):
            row = [value_of(mat[t], w) for w in WD]
            print(f"{str(t):18s}  " + "  ".join(f"{v:>7d}" for v in row) + f"  {sum(row):>5d}")
        col_tot = [sum(value_of(mat[t], w) for t in mat) for w in WD]
        print(f"{'TOT':18s}  " + "  ".join(f"{v:>7d}" for v in col_tot) + f"  {sum(col_tot):>5d}")

    print_table("(A) DIRECT: H29 room-null lessons by (required tag, weekday)",
                tw, lambda c, w: c.get(w, 0))
    print(f"(acconciatura 144 / termoidraulica 24 / meccanica 1 were the 30e reference)\n")

    print(f"\n{'=' * 82}\n(B) CAPACITY MODEL: excess (demand-capacity) by (tag, weekday)\n{'=' * 82}")
    print(f"total over-capacity cells={tot_cells}  total excess lesson-hours={tot_ex}")
    print(f"{'tag':18s} {'cap':>3s}  " + "  ".join(f"{w:>7s}" for w in WD3) +
          f"  {'cells':>5s} {'excess':>6s}  worst-cell")
    for t in sorted(demand):
        cap = cap_by_tag.get(t, 0)
        cw = [ex_cells[t].get(w, 0) for w in WD]
        hw = [ex_hours[t].get(w, 0) for w in WD]
        w = worst.get(t, (0, None))
        wc = f"{w[1][0]} {w[1][1][11:16]} x{w[0]}" if w[1] else "-"
        print(f"{t:18s} {cap:>3d}  " + "  ".join(f"{v:>7d}" for v in cw) +
              f"  {sum(cw):>5d} {sum(hw):>6d}  {wc}")

    print(f"\nCAUSALITY: H29 in spare-capacity cell={spare} / in over-capacity cell={contended} "
          f"(spare~0 => room-null is 100% contention)")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: tag-capacity-excess-kpi.py <export.json[.gz]>", file=sys.stderr)
        sys.exit(2)
    run(sys.argv[1])
