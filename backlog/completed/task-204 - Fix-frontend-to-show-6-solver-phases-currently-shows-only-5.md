---
id: TASK-204
title: Fix frontend to show 6 solver phases (currently shows only 5)
status: Done
assignee: []
created_date: '2026-03-08 20:57'
updated_date: '2026-03-08 20:59'
labels:
  - bug
  - frontend
  - solver
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The frontend Solver Phases page shows only 5 phases while solverConfig.xml has 6. The Phase 6 (Final Compaction with Simulated Annealing) is missing from:
- src/main/webui/src/data/solverPhases.ts
- src/main/webui/src/i18n/locales/en/phases.json
- src/main/webui/src/i18n/locales/it/phases.json
- src/main/webui/src/components/SolverPhasesPage.tsx (comparison table)

claudedocs/solver-phases.md correctly describes 6 phases.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed frontend to show all 6 solver phases matching solverConfig.xml:

**Changes Made:**

1. **solverPhases.ts**: Added Phase 6 (Final Compaction) with:
   - Timeslot-only moves (Change + Swap)
   - Simulated Annealing temperature: 0hard/1000medium/0soft
   - Termination: 900s unimproved, 2700s hard cap
   - Added `simulatedAnnealingTemperature` to MetaheuristicInfo interface

2. **en/phases.json**: 
   - Changed "5-phase pipeline" → "6-phase pipeline"
   - Added phase6 translations (name, goal, description)
   - Added compactionValues to comparison table
   - Updated whyMultiphase reasons for 6 phases
   - Added simulatedAnnealing label

3. **it/phases.json**: 
   - Changed "pipeline a 5 fasi" → "pipeline a 6 fasi"
   - Added phase6 Italian translations
   - Added compactionValues to comparison table
   - Updated reasons for 6 phases

4. **SolverPhasesPage.tsx**:
   - Updated comparison table to show 4 LS phases (3, 4, 5, 6)
   - Added SA temperature display in metaheuristic section

**Also Fixed Termination Limit Discrepancies:**
- Phase 4: 3600s → 1800s unimproved (matches solverConfig.xml 30 min)
- Phase 5: 600s → 300s unimproved (matches solverConfig.xml 5 min)
- Both now include hardCapSeconds matching XML (7200s, 1200s)

**User Action Required:** Hard-refresh browser (Ctrl+Shift+R) to clear cached JS bundle.
<!-- SECTION:FINAL_SUMMARY:END -->
