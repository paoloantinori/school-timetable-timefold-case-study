#!/usr/bin/env python3
"""TASK-339: measure hard violations involving PINNED lessons, to test whether pinning
(pre-assigned authoritative + mid-solve promoted) causes residual hard the solver cannot close.

WHY. Pinned lessons are fixed (the solver cannot move them). If a pinned lesson sits inside a hard
violation, that violation is at least partly pin-locked: the solver cannot resolve it by moving the
pinned lesson. The ex-ante prediction is that pinned lessons are 0-violating by construction (canPin
validates authoritative pre-assignment), with the open risk that PROMOTED pins (template cells
promoted to pinned mid-solve) escape that gate.

WHAT IT MEASURES. Reads a run's export.json.gz (lessons carry `pinned` + `promotedPin` + timeslot +
room + teacher + group) and recomputes the hard constraints that can be "pin-caused":
  - H29 room-null: an assigned (timeslot set) lesson with room=null. THE load-bearing check: is that
    lesson pinned (pin-caused / pin-locked) or unpinned (solver-fixable by the room phases)?
  - H1 teacher conflict / H3 group conflict: two+ real-teacher lessons at the same (teacher/group,
    date, slot). A pin in a conflict pair is pin-caused. (Absent on the current bad-seed residuals,
    computed for completeness.)
  - H17 vertically-compact (gaps): the "buco interno" between a group-day's first and last occupied
    slot. H17 is break-level pinned-EXEMPT (a gap with BOTH flanks pinned is excused), so a SCORED gap
    has at least one unpinned flank by construction -> not pin-LOCKED. We still report whether a
    pinned lesson borders a scored gap (the pin contributes to the gap shape even if not locking it).
H2 room conflict is omitted (the co-presence exemption is golden-derived and out of scope for this
standalone check). H0 (unassigned), H7 (teacher daily hours) and H11 (group daily hours) are also out
of scope: an H0 lesson has no timeslot so it cannot be pin-caused; H7/H11 are not among the measured
residuals. NOTE: H2/H7/H11 carry NO !pinned filter in the solver, so a pinned lesson CAN participate
in them on a hypothetical run where they fire; on the measured runs they fire 0 hard (analysis.json
confirms only H17/H29 fire), so the "0 pin-caused hard" verdict holds here for ALL hard families.

This is READ-ONLY: it measures, it does not change canPin() or anything else.

Usage:
  python3 scripts/analyze/pinned-hard-impact.py <export.json.gz> [<export.json.gz> ...]
"""
import gzip
import json
import sys
from collections import defaultdict

from shared import KPI_EXCLUDED_PSEUDO, SLOT_START_TIMES


def is_pseudo_teacher(t):
    """A teacher is pseudo (accounting, not a real conflict) if flagged pseudo OR named in the
    KPI_EXCLUDED_PSEUDO set (Stage/Esame/Fasullo; Fasullo may lack the pseudo flag on golden-imported
    rows). Used by both H1 exclusion and the H29 !pseudo filter so the two stay consistent."""
    return bool(t.get("pseudo")) or t.get("name") in KPI_EXCLUDED_PSEUDO


def slot_index(timeslot):
    """Map a timeslot's start time to its 0-based slot index (0=08:00 .. 7=15:30). None if off-grid."""
    hhmm = timeslot["startDateTime"][11:16]  # "2026-09-15T08:00:00" -> "08:00"
    return SLOT_START_TIMES.index(hhmm) if hhmm in SLOT_START_TIMES else None


def load_lessons(path):
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return json.load(fh)["lessons"]


def pin_label(lesson):
    """Concise pin status of a lesson for reporting."""
    if not lesson.get("pinned"):
        return "unpinned"
    return "promoted" if lesson.get("promotedPin") else "authoritative"


def is_authoritative_pin(lesson):
    """An authoritative pin (pre-assigned by the school, validated by canPin), as opposed to a
    promoted pin (template cell promoted to pinned mid-solve). H17's break-level exemption keys off
    AUTHORITATIVE pins (mirrors Java's isAuthoritativePin = pinned && !promotedPin). On current
    exports promotedPin is always false (the staged cycle releases promoted pins before export), so
    this equals a plain pinned check; the stricter form is robust to a future mid-cycle export."""
    return bool(lesson.get("pinned")) and not lesson.get("promotedPin")


def measure(lessons):
    assigned = [l for l in lessons if l.get("timeslot")]

    # --- H29: assigned lesson with room=null. H29's actual filter (assignedLessonMustHaveRoom) is
    # timeslot!=null && room==null && !pinned && !teacher.pseudo: PINNED and pseudo lessons are
    # EXEMPT by construction, so a scored H29 is by definition an UNPINNED, non-pseudo lesson.
    # We report both the RAW room-null set (context: the export carries room=null for many pinned
    # labs) and the SCORED H29 set (!pinned && !pseudo) that matches the run's H29 count.
    raw_room_null = [l for l in assigned if l.get("room") is None]
    h29 = [l for l in raw_room_null if not l.get("pinned")
           and not is_pseudo_teacher(l.get("teacher") or {})]

    # --- H1 teacher conflict (real teachers only): >1 lesson same (teacher, date, slot) ---
    by_teacher = defaultdict(list)
    for l in assigned:
        t = l.get("teacher") or {}
        if is_pseudo_teacher(t):
            continue
        si = slot_index(l["timeslot"])
        if si is None:
            continue
        by_teacher[(t.get("id"), l["timeslot"]["date"], si)].append(l)
    h1 = [grp for grp in by_teacher.values() if len(grp) > 1]

    # --- H3 group conflict: >1 lesson same (group, date, slot) ---
    by_group = defaultdict(list)
    for l in assigned:
        g = l.get("studentGroup") or {}
        si = slot_index(l["timeslot"])
        if si is None:
            continue
        by_group[(g.get("id"), l["timeslot"]["date"], si)].append(l)
    h3 = [grp for grp in by_group.values() if len(grp) > 1]

    # --- H17 gaps: per (group, date), holes between first and last occupied slot ---
    by_group_day = defaultdict(dict)
    for l in assigned:
        g = (l.get("studentGroup") or {}).get("id")
        si = slot_index(l["timeslot"])
        if si is None:
            continue
        by_group_day[(g, l["timeslot"]["date"])][si] = l
    scored_gaps = []  # (group_id, date, hole_slot, lower, upper)
    for (g, date), slotmap in by_group_day.items():
        if len(slotmap) < 2:
            continue
        slots = sorted(slotmap)
        for hole in range(slots[0] + 1, slots[-1]):
            if hole in slotmap:
                continue
            lower = max(s for s in slots if s < hole)
            upper = min(s for s in slots if s > hole)
            lower_l = slotmap[lower]
            upper_l = slotmap[upper]
            # H17 break-level exemption fires only when BOTH flanks are AUTHORITATIVE pins
            # (pinned && !promotedPin), matching Java's isAuthoritativePin. A promoted pin does NOT
            # exempt a gap.
            if is_authoritative_pin(lower_l) and is_authoritative_pin(upper_l):
                continue  # break-level pinned-exempt: this gap does not score
            scored_gaps.append((g, date, hole, lower_l, upper_l))

    return h29, raw_room_null, h1, h3, scored_gaps


def report_group(label, groups):
    """groups: list of lesson-lists (H1/H3 conflict clusters). Report pinned involvement."""
    n = len(groups)
    any_pinned = sum(1 for grp in groups if any(l.get("pinned") for l in grp))
    all_pinned = sum(1 for grp in groups if all(l.get("pinned") for l in grp))
    has_promoted = sum(1 for grp in groups if any(l.get("promotedPin") for l in grp))
    print(f"  {label}: {n} cluster(s) | >=1 pinned in {any_pinned} | all-pinned {all_pinned} | promoted-involving {has_promoted}")


def main():
    paths = sys.argv[1:]
    if not paths:
        print("usage: pinned-hard-impact.py <export.json.gz> [...]", file=sys.stderr)
        return 1
    for path in paths:
        lessons = load_lessons(path)
        n_assigned = sum(1 for l in lessons if l.get("timeslot"))
        n_pinned = sum(1 for l in lessons if l.get("pinned"))
        n_promoted = sum(1 for l in lessons if l.get("promotedPin"))
        h29, raw_room_null, h1, h3, gaps = measure(lessons)
        print(f"# {path}")
        print(f"# assigned {n_assigned}, pinned {n_pinned} (of which promoted {n_promoted})\n")

        # H29: H29 exempts pinned+pseudo by construction, so scored H29 is UNPINNED by definition.
        raw_pinned = sum(1 for l in raw_room_null if l.get("pinned"))
        print(f"  H29 room-null: RAW {len(raw_room_null)} (of which pinned {raw_pinned}; the export "
              f"carries room=null for many pinned labs) | SCORED {len(h29)} (matches the run's H29; "
              f"these are !pinned && !pseudo by the H29 filter)")
        for l in h29[:5]:
            g = (l.get("studentGroup") or {}).get("name", "?")
            t = (l.get("teacher") or {}).get("name", "?")
            print(f"    -> id={l['id']} group={g} teacher={t} subject={l.get('subject')} "
                  f"slot={l['timeslot']['date']} {l['timeslot']['startDateTime'][11:16]} PIN={pin_label(l)}")
        if len(h29) > 5:
            print(f"    ... ({len(h29) - 5} more)")
        print(f"    => H29 is NEVER pin-caused: pinned (authoritative + promoted) are exempt by the "
              f"!pinned filter; the {len(h29)} residual(s) are unpinned (solver room-phase failure).")

        report_group("H1 teacher-conflict", h1)
        report_group("H3 group-conflict", h3)

        # H17 scored gaps
        gap_any_pinned = sum(1 for (_, _, _, lo, up) in gaps if lo.get("pinned") or up.get("pinned"))
        gap_promoted = sum(1 for (_, _, _, lo, up) in gaps if lo.get("promotedPin") or up.get("promotedPin"))
        print(f"  H17 scored gaps: {len(gaps)} | a pinned lesson borders {gap_any_pinned} | promoted borders {gap_promoted}")
        print(f"    (scored gaps have >=1 unpinned flank by the pinned-exempt rule; pinned-bordering means the pin contributes to the gap shape, not that it locks it)\n")
        if n_promoted == 0:
            print(f"  NOTE: promoted=0 in this export (all {n_pinned} pinned are authoritative); the")
            print(f"        H1/H3/H17 promoted-involvement counts are trivially 0 (promoted pins untested).\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
