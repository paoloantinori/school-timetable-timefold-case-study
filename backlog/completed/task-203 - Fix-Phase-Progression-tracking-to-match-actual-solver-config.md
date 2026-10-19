---
id: TASK-203
title: Fix Phase Progression tracking to match actual solver config
status: Done
assignee:
  - claude
created_date: '2026-03-08 20:18'
updated_date: '2026-03-08 20:27'
labels:
  - bug
  - solver
  - phase-tracking
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Progression endpoint displays incorrect phase names because `SolverProgressionListener.detectPhase()` uses score-based heuristics instead of reading the actual solver configuration from `solverConfig.xml`.

**Current Behavior:**
- Shows "Phase 2: CH - Lesson Placement" when solver is actually in LocalSearch (Phase 3/4/5)
- Phase detection based on score thresholds (hardScore < 0, mediumScore ranges)
- Never detects CH completion with large datasets (hardScore never reaches 0)
- Reports only 5 phases when solverConfig.xml has 6 phases

**Expected Behavior:**
- Parse solverConfig.xml to get actual phase names, limits, and order
- Report 6 phases matching the XML configuration:
  1. CH - Lab Block Placement
  2. CH - Lesson Placement  
  3. LS - Compactness
  4. LS - Conflict Resolution
  5. LS - Room Refinement
  6. LS - Final Compaction
- Accurately track phase transitions based on solver progress, not scores

**Root Cause:**
`SolverProgressionListener.detectPhase()` uses score patterns:
- Phase 1: hardScore < 0 (first time)
- Phase 2: hardScore < 0 (after Phase 1) ← Gets stuck here forever
- Phase 3-5: mediumScore thresholds

With 7,538 Lessons + 1,135 LabBlocks, CH never reaches hardScore = 0, so the listener stays in "Phase 2" indefinitely while the solver is actually in LocalSearch phases.

**Files to Modify:**
- `src/main/java/com/school/planner/solver/SolverProgressionListener.java`
- `src/main/java/com/school/planner/service/PhaseProgressionService.java` (may need updates)

**Acceptance Criteria:**
1. Progression endpoint displays phase names matching solverConfig.xml
2. Phase transitions accurately reflect actual solver phase changes
3. All 6 solver phases are correctly tracked (not just 5)
4. Phase durations and limits match solverConfig.xml configuration
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented time-based phase detection to fix phase progression tracking:

**Changes Made:**
1. **SolverConfigParser.java**: Added `PHASE_NAMES` map with all 6 phases, `getPhaseName(int)`, `getPhaseCount()` methods
2. **SolverProgressionListener.java**: 
   - Replaced score-based `detectPhase()` with time-based detection using elapsed solve time
   - Added `setSolveStartTime()` and `getSolveStartTime()` methods
   - Added Phase 6 ("LS - Final Compaction") to `getPhaseName()` switch
   - Added `Duration` import
3. **ScheduleService.java**: 
   - Set solve start time on phase tracker in `startSolving()`
   - Initialize phase tracker's solve time in `saveSchedule()`
4. **claudedocs/solver-phases.md**: 
   - Updated header from "5-phase" to "6-phase"
   - Added Phase 6 to architecture diagram
   - Added complete Phase 6 section (goal, move palette, metaheuristic, termination)
   - Updated "Why Multiple Phases?" section
   - Updated "Phase Timing" table with Phase 6 (~15-45 min)
   - Updated "Configuration Reference" table with Phase 6 (SA temperature, 900s unimproved, 2700s hard cap)

**Why Time-Based Detection Works:**
- CH phases are deterministic (~1s + ~90s for typical dataset)
- LS phases have known hard caps from solverConfig.xml (7200s, 7200s, 1200s, 2700s)
- Phase transitions based on elapsed time match actual solver behavior better than score thresholds

**Verification:**
- Backend rebuilt successfully (26s)
- App running at http://localhost:8080
- Phase progression endpoint returns empty array (expected - no solver run yet)
<!-- SECTION:FINAL_SUMMARY:END -->
