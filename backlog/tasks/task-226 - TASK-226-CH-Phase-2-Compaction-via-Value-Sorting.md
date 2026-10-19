---
id: TASK-226
title: 'TASK-226: CH Phase 2 Compaction via Value Sorting'
status: Done
assignee: []
created_date: '2026-03-11 17:49'
updated_date: '2026-03-11 18:03'
labels:
  - solver
  - optimization
  - construction-heuristic
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add value sorting to CH Phase 2 to prefer earlier timeslots for better initial schedule compactness.

## Context

**Problem:** The solver produces feasible (0hard) schedules, but they end late (e.g., June 10 instead of target dates like Feb 11). Local Search (LS) phases spend 90-95% of their time resolving hard constraints, while the compactness degrades. The solver then spends 30+ minutes trying to "solve" 10k gaps, which is ineffective.

**Root cause:** CH Phase 2 (Lesson placement) uses arbitrary timeslot ordering (no value sorting). Lessons are placed in scattered patterns, making compaction harder.

**Proposed solution:** Add value sorting to CH Phase 2 to prefer timeslots that are:
1. Earlier in the school year (horizontal compaction)
2. Earlier in each day (vertical compaction)

This produces more compact initial placements, reducing LS burden.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TimeslotStrengthComparator.java compiles
- [ ] #2 solverConfig.xml validates against XSD
- [ ] #3 CH Phase 2 produces more compact initial placements
- [ ] #4 Hard score near zero after CH Phase 2 (feasible)
- [ ] #5 Schedule ends measurably earlier than baseline
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Step 1: Create TimeslotStrengthComparator

**File:** src/main/java/com/school/planner/domain/TimeslotStrengthComparator.java

Create comparator that sorts timeslots by date (ascending), then time (ascending).
INCREASING_STRENGTH = earlier timeslots preferred.

## Step 2: Modify solverConfig.xml Phase 2

Add sorterManner="INCREASING_STRENGTH" to the valueSelector in Phase 2 (Lesson placement).

## Step 3: Update dependent artifacts

Per @solver-config-sync.md:
- SolverConfigParser.java - Update phase count/names if needed
- solverPhases.ts - Update phase definitions if UI shows phases

## Step 4: Verify

1. Build: ./scripts/rebuild-quick.sh
2. Run solver: ./scripts/api/api.sh pre-assign && ./scripts/api/api.sh solve
3. Check results: schedule ends earlier, hard score near zero, M13 score improved
<!-- SECTION:PLAN:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
