#!/usr/bin/env python3
"""Chain-feasibility KPI - TASK-451 depth-2 / depth-3 horizontal recombination gate.

CONTEXT (see claudedocs/spalmone-2026-27/task447-results/spikes/SPIKES-RAW.md,
section TASK-451): a gap survives construction+compaction+repair when the slot is
contended - every teacher of the group that day is already busy elsewhere in that
slot. No single swap/cange move can represent the coordinated multi-group shift
needed to escape (every intermediate state is worse or illegal), so local search
stops in a local optimum. The G11 Friday slot-0 case is the measured instance,
but the mechanism is slot-agnostic (maintainer).

This script MEASURES (read-only, dump-derived, no golden, no solver) how many of
those HARD gaps could be closed by a chain of depth 2 and depth 3 - the gate maintainer
set before any prototype. depth-2 was already measured in the prior session
(0/15 finale, 8/73 construction); this script REPRODUCES that as a calibration
check, then extends to depth-3.

DEFINITIONS (mirroring the prior session's TASK-451 methodology, so the depth-2
column reproduces 0/15 and 8/73 - the calibration gate):
  slot      = hour - 8 (hours 8..15 -> slots 0..7), same as every KPI in the family.
  GAP       = empty slot in [expected_start, max_occupied] for (group, date) -
              i.e. late-start ([expected_start, min_occupied-1]) UNION internal
              holes (strictly between min and max occupied). EXCLUDES the tail
              (slots > max_occupied): an early finish is legal, not a gap.
              expected_start = 1 only on the 2026-09-14 ramp Monday, else 0
              (same convention as hole-topology-kpi.py / day-start-kpi.py).
  trivially-fillable = a GAP slot where >=1 of the group's day-teachers is FREE
              in that slot (has no lesson there at all). Upper bound - does not
              check the moved lesson's own source slot.
  HARD      = a GAP slot where EVERY day-teacher is busy in that slot (the
              contention case). GAP = trivially-fillable + HARD (clean split).

DEPTH-2 (the prior session's "catena corretta + departure-safe", CORREZIONE 1):
  G1 has a HARD gap at slot s. A teacher T1 of G1 (placed with G1 that day) is
  busy at slot s with another group G2 (G2(T1)@s, unpinned). The chain: move
  G2(T1) from s to a legal destination, place a G1(T1) lesson at s.
  departure-safe = moving G2(T1) off slot s does NOT open a gap in G2. The ONLY
  gap-neutral removal is from G2's TAIL (max_occupied): an early finish is legal.
  Removing from G2's day-start or an internal slot opens a late-start / internal
  hole. So departure-safe <=> s == max_occupied(G2, date). At slot 0 (the earliest
  slot) this is structurally impossible - slot 0 is always a day-start, never a
  tail - which is why the G11 slot-0 case is 0/15.
  survive-2 = exists T1 (G1 day-teacher busy at s with G2, G2(T1)@s unpinned) AND
              departure-safe (s == G2 tail). [Calibration: 0 finale / 8 construction.]

DEPTH-3 (this script's new measurement - the residual variant after depth-2
collapsed to 0): the trap is that moving G2(T1) off slot s opens a G2 hole at s
(s is G2's day-start). depth-3 ESCAPES if that opened hole is itself fillable by
  ONE more legal move, with every move gap-neutral:
    1. G2(T2): G2's tail slot -> slot s   [T2 free at s; refills G2's day-start,
                                            G2 day stays contiguous - tail removal
                                            is an early finish]
    2. G2(T1): slot s -> G2's tail slot   [T1 free at the tail; the swap closes
                                            the G2 day-shape: occupied set unchanged,
                                            just teachers swapped at s and tail]
    3. G1(T1): G1's tail slot -> slot s   [T1 now free at s after step 2; fills
                                            G1's gap; G1 tail removal = early finish]
  Net: G1's gap at s CLOSED (tail shortened), G2 day-shape UNCHANGED (swap),
  T1 ends at G2-tail + G1@s (two different slots, no teacher conflict), T2 at G2@s.
  "G3 che resta contigua" in the mandate = the donor group whose tail-lesson moves
  (G2 here); its day stays contiguous because only the tail is lifted.
  survive-3 (SWAP, strict, fully gap-neutral) = exists T1, T2 with:
    - T1 in day_teachers(G1), busy at s with G2 (!= G1), G2(T1)@s unpinned
    - G1(T1) at G1's tail (G1 has a lesson by T1 at max_occupied(G1), unpinned)
    - T2 = G2's tail teacher (G2 lesson at max_occupied(G2) by T2), T2 != T1,
      G2(T2)@tail unpinned, T2 FREE at s
    - T1 FREE at G2's tail slot (so G2(T1) can land there)
  survive-3 (EXTEND, looser upper bound) = same but T1 lands at a slot beyond
    G2's tail (G2 day extends by one) instead of swapping into the tail; needs T1
    free at that open slot. Flags H11/max-hours as NOT validated (same scope as the
    depth-2 upper bound, which also did not validate room/H11/quota).

LIMITS (honest, same as depth-2's "limite superiore"): validates teacher-availability
+ mobile(unpinned) + departure-safe(gap-neutrality). Does NOT validate room conflict
(H2/H-M2), H11 max daily hours, M15 same-subject adjacency, or daily quota. So any
non-zero count is an UPPER BOUND on truly-legal chains, not a proof they're legal.
Pinned lessons are immobile (the mandate's non-negotiable); at construction every
lesson is unpinned (verified: 0 pinned in the construction dump).

Usage:
  python3 scripts/analyze/chain-feasibility-kpi.py <lessons-dump> [--detail] [--depth3]
  <lessons-dump> = pipe-format group|date|hour|subject|teacher|pinned (construction
    dump target/construction-lessons.txt OR a final export dump).
"""
import argparse
import os
import sys
from collections import defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import KPI_EXCLUDED_PSEUDO  # noqa: E402

BASE_HOUR = 8
SLOTS_PER_DAY = 8
RAMP_DATE = '2026-09-14'


def build_all(path):
    """Parse the pipe dump into three structures. KEY: a slot is a GAP only if no
    lesson at all fills it - Stage/Esame stubs DO occupy their slot (students are
    busy off-campus, you cannot place a real lesson there), so they count toward
    `occupied_all` for gap detection. But Stage/Esame/Fasullo are pseudo - they are
    NOT real teachers and are never move candidates - so they are excluded from
    `placed`/`busy`/day_teachers. This is what makes G08 2027-04-01 (a morning of
    pinned Stage + afternoon Matematica) read as gap-free, while G11 ven slot-0
    (genuinely empty) stays a gap.

      occupied_all[(group,date)] = set of slots filled by ANY lesson (incl Stage)
      placed[(group,date)]       = list of (slot, teacher, subject, pinned) REAL only
      busy[(date,slot)]          = {real_teacher: group} (who teaches whom)"""
    occupied_all = defaultdict(set)
    placed = defaultdict(list)
    busy = defaultdict(dict)
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line == 'SET' or '|' not in line:
                continue
            p = [x.strip() for x in line.split('|')]
            if len(p) != 6:
                continue
            g, d, hh, s, t, pn = p
            slot = int(float(hh)) - BASE_HOUR
            if not (0 <= slot < SLOTS_PER_DAY):
                continue
            occupied_all[(g, d)].add(slot)         # Stage fills the slot too
            if s in KPI_EXCLUDED_PSEUDO or t in KPI_EXCLUDED_PSEUDO:
                continue                            # pseudo = not a real teacher/move candidate
            pinned = (pn.lower() == 'true')
            placed[(g, d)].append((slot, t, s, pinned))
            busy[(d, slot)][t] = g                  # last writer wins; one place/slot
    return occupied_all, placed, busy


def gap_slots(occupied, date_iso):
    """[expected_start, max_occupied] minus occupied = late-start + internal holes."""
    if not occupied:
        return None
    lo, hi = min(occupied), max(occupied)
    start = 1 if date_iso == RAMP_DATE else 0
    return [s for s in range(start, hi + 1) if s not in occupied]


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons_dump')
    ap.add_argument('--detail', action='store_true',
                    help='print every HARD gap with its depth-2/depth-3 verdict')
    ap.add_argument('--depth3', action='store_true',
                    help='run the depth-3 measurement (else depth-2 calibration only)')
    args = ap.parse_args()

    occupied_all, placed, busy = build_all(args.lessons_dump)

    gap_total = trivial = 0
    hard = []          # list of dicts
    survive2 = 0

    for (g, d), lessons in sorted(placed.items()):
        occupied = occupied_all[(g, d)]           # incl Stage: a Stage slot is not a gap
        gaps = gap_slots(occupied, d)
        if not gaps:
            continue
        day_teachers = {t for _, t, *_ in lessons}   # REAL teachers only
        max_o = max(occupied)
        for s in gaps:
            gap_total += 1
            free_at_s = [t for t in day_teachers if t not in busy.get((d, s), {})]
            if free_at_s:
                trivial += 1
                continue
            # HARD gap
            entry = {'group': g, 'date': d, 'slot': s, 'max_occ': max_o,
                     'day_teachers': sorted(day_teachers)}
            hard.append(entry)
            # depth-2 survive: T1 busy at s with G2, G2(T1)@s unpinned, s == G2 tail
            d2 = False
            for t1 in day_teachers:
                g2 = busy.get((d, s), {}).get(t1)
                if g2 is None or g2 == g:
                    continue
                if pinned_of(placed, g2, d, s, t1):
                    continue
                g2_occ = occupied_all.get((g2, d), set())
                if g2_occ and s == max(g2_occ):
                    d2 = True
                    break
            entry['survive2'] = d2
            if d2:
                survive2 += 1

    print(f'input: {args.lessons_dump}')
    print(f'GAP totali: {gap_total} | trivially-fillable: {trivial} | HARD: {len(hard)}')
    print(f'depth-2 survive (departure-safe, calibration): {survive2}/{len(hard)}')
    print('  [calibration target: finale 0/15, costruzione 8/73]')

    if not args.depth3:
        if args.detail:
            print('\nHARD gaps (detail):')
            for e in hard:
                print(f"  {e['group']:8s} {e['date']} slot{e['slot']} "
                      f"(day max slot {e['max_occ']}) survive2={e['survive2']}")
        return

    # ── depth-3 ──
    survive3_swap = 0
    survive3_extend = 0
    cases = []
    for e in hard:
        g1, d, s = e['group'], e['date'], e['slot']
        # G1's tail = the last REAL lesson slot that day (the gap-neutral source must be a real,
        # movable lesson). e['max_occ'] is max(occupied_all) which can be a pinned Stage stub
        # (afternoon off-campus); using it would point g1_tail at a slot with no real teacher,
        # skipping every T1. The real-tail is the correct source. For pure-teaching days (no
        # Stage) real == occupied_all, so the G11 ven result is unchanged.
        g1_real_slots = {sl for sl, *_ in placed[(g1, d)]}
        g1_tail = max(g1_real_slots) if g1_real_slots else e['max_occ']
        g1_tail_teachers = {t for sl, t, *_ in placed[(g1, d)] if sl == g1_tail}
        busy_s = busy.get((d, s), {})                # who teaches whom at the gap slot (fixed for this gap)
        swap_ok = False
        extend_ok = False
        witnesses = []
        for t1 in e['day_teachers']:
            g2 = busy_s.get(t1)
            if g2 is None or g2 == g1:
                continue
            if pinned_of(placed, g2, d, s, t1):
                continue
            # G1(T1) must sit at G1's tail (gap-neutral source)
            if t1 not in g1_tail_teachers:
                continue
            if pinned_of(placed, g1, d, g1_tail, t1):
                continue
            g2_occ = occupied_all.get((g2, d), set())
            if not g2_occ:
                continue
            g2_tail = max(g2_occ)
            # T2 = G2's tail teacher (real), free at s
            for sl2, t2, sub2, pn2 in placed.get((g2, d), []):
                if sl2 != g2_tail or t2 == t1:
                    continue
                if pn2:
                    continue
                if t2 in busy_s:
                    continue  # T2 not free at s
                # SWAP: T1 lands at G2 tail (must be free there)
                if t1 not in busy.get((d, g2_tail), {}):
                    swap_ok = True
                    witnesses.append(('SWAP', t1, g2, t2, g2_tail))
                # EXTEND: T1 lands at the slot CONTIGUOUS beyond G2 tail (g2_tail+1), so G2's
                # day extends by one with no internal hole. (Earlier this loop scanned all slots
                # beyond the tail and could pick a non-contiguous dest, opening a hole in G2 -
                # contradicting the gap-neutral premise. g2_tail = max(g2_occ) already guarantees
                # nothing beyond it is occupied, so the old `dest in g2_occ` guard was dead code.)
                dest = g2_tail + 1
                if dest < SLOTS_PER_DAY and t1 not in busy.get((d, dest), {}):
                    extend_ok = True
                    witnesses.append(('EXTEND', t1, g2, t2, dest))
                if swap_ok:
                    break
            if swap_ok:
                break
        e['survive3_swap'] = swap_ok
        e['survive3_extend'] = extend_ok
        if swap_ok:
            survive3_swap += 1
        if extend_ok:
            survive3_extend += 1
        if witnesses:
            cases.append((e, witnesses))

    print(f'\ndepth-3 SWAP (strict, gap-neutral): {survive3_swap}/{len(hard)}')
    print(f'depth-3 EXTEND (looser upper bound, H11 unvalidated): {survive3_extend}/{len(hard)}')
    if cases:
        print('\nDEPTH-3 CASES (group, date, slot, witness):')
        for e, ws in cases:
            for kind, t1, g2, t2, slot in ws:
                print(f"  {e['group']:8s} {e['date']} slot{e['slot']} "
                      f"[{kind}] T1={t1} (G1 tail, freed at s via G2 swap) "
                      f"G2={g2} T2={t2} (G2 tail->{kind.lower()}-slot {slot})")


def pinned_of(placed, g, d, slot, teacher):
    for sl, t, s, pn in placed.get((g, d), []):
        if sl == slot and t == teacher:
            return pn
    return False


if __name__ == '__main__':
    main()
