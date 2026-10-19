# Pre-assignment CSP limits (DayAssignmentCsp is a dead end for the general case)

> 📌 **Session memory** (~March 2026, architectural finding). Verify whether Phase 2 CSP still exists in current code — this conclusion was reached when `DayAssignmentCsp` (two-level CSP) was still in the pipeline.

## Rule: don't generalize pre-assignment beyond deterministic patterns

Significant effort went into building `DayAssignmentCsp` (two-level CSP) for Phase 2 lab pre-assignment. It works for acconciatura (few groups, single room) but fails for informatica (14 groups, 2 rooms) due to fundamental architectural issues:

- Two-level decomposition (days → slots) loses timeslot-level constraint information
- Level 2 greedy processing is order-dependent and non-deterministic (83–98.5% variance)
- Cross-phase cascading failures (termoidraulica pins saturate days for informatica)
- Each fix patches a symptom, not the root cause

## How to apply

Pre-assignment should ONLY be used for truly deterministic patterns:
1. LabRotations with fixed teacher+group+day+slot (Phase 1 acconciatura)
2. Professionalizzanti on stage days (Phase 3, deterministic placement)
3. Everything else should be left to Timefold's solver

## Recommended path forward

1. Benchmark the solver **without** Phase 2 pre-assignment — measure if Timefold can handle it directly
2. If fast enough (< 5 min): remove Phase 2 pre-assignment entirely
3. If too slow: use a short Timefold CH run as pre-assignment instead of a hand-rolled CSP
4. The DayAssignmentCsp approach is a dead end for the general case

## Root cause of the informatica problem

T06 teaches ALL 14 informatica groups with only 2 rooms. At any timeslot she can teach 1 group (teacher bottleneck), so the other room is wasted. The CSP's day-level assignment can't capture this timeslot-level scarcity, and the greedy Level 2 processing order determines which groups win the room lottery.
