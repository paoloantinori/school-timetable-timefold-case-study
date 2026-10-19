---
id: TASK-133
title: Update solver-configurations.md with pillar and LA research findings
status: Done
assignee: []
created_date: '2026-02-27 10:29'
updated_date: '2026-08-04 02:00'
labels:
  - documentation
  - solver
  - claude-planned
dependencies: []
priority: low
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Update the solver configurations documentation with findings from the recent research tasks:

1. **Pillar moves (task-096):**
   - Document that maxSubPillarSize=5 is appropriate for 7,000-8,700 entities
   - Document recommendation to add room pillar moves
   - Note that LabBlock pillar moves are low priority

2. **Late Acceptance (task-097):**
   - Document that current lateAcceptanceSize=200 may be undersized
   - Document recommendation to add LA to Phase 4
   - Document recommended benchmark values [100, 200, 400, 800, 1600]

3. **Value Range Provider (task-031):**
   - Document that LabBlock already has comprehensive VRP optimization
   - Note that entity-based room VRP for Lesson is being implemented (TASK-153)

**File to update:** `claudedocs/solver-configurations.md`
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-04 02:00
---
solver-configurations.md refreshed (read-only). Block Entity/LabBlock 7-fasi TF1.31 -> Lesson-singola 5-fasi TF2.3.0. Adds pillar (experimental/unproven, TASK-332 To Do) + LA (one M-LA, H-D1 gone). DoD skeptic caught: doc said 'Fase 1 assegna stanze' but code NULLs rooms (TASK-446 Alt-B); Fase 4 sole assignment; deferred-room TASK-183 current. Corrected. Commit fbc3cc1b.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
