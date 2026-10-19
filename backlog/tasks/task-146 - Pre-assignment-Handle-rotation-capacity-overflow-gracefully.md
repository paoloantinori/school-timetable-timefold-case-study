---
id: TASK-146
title: 'Pre-assignment: Handle rotation capacity overflow gracefully'
status: Done
assignee:
  - Claude
created_date: '2026-03-02 20:13'
updated_date: '2026-03-03 21:00'
labels:
  - claude-planned
  - pre-assignment
  - rotation
  - capacity
dependencies: []
priority: high
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Pre-assignment Phase 1 (Acconciatura) fails with "CAPACITY_EXCEEDED" because it assumes ALL of a teacher's hours must happen on their rotation day.

### Evidence from spalmone.csv

T26 teaches across ALL days, not just Wednesday:
- Monday: 88h (Progettazione visiva, Stile visivo)
- Tuesday: 149h (Stile visivo)
- Wednesday: 144h (rotation day - only 144h, not all hours)
- Thursday: 30h (Rappresentazione visiva)
- Friday: 89h (Stile visivo, Rappresentazione visiva)

**Total: 500h across all days, but only 144h on the rotation day (Wednesday)**

### Current Bug

Pre-assignment tries to place ALL 64 blocks (384h) on 38 Wednesdays → IMPOSSIBLE

## Solution Options

### Option A: Split Subjects in DataGenerator (Long-term Correctness)
Split "Servizio tecnico e stilistico (B)" into:
- "Stile visivo" (lab component, rotation-constrained) → ~144h
- "Progettazione visiva" (non-lab, solver handles) → ~46h
- "Rappresentazione visiva" (non-lab, solver handles) → ~70h

**Pros:** Accurate to real school data
**Cons:** Requires data verification and user permission

### Option B: Mark Only Lab Blocks for Rotation
Change how LabBlocks are generated - only true lab sessions get rotation constraint.

**Pros:** Minimal data changes
**Cons:** May not match real subject structure

### Option C: Relax Rotation Constraint for Overflow (CHOSEN)
Modify pre-assignment to be **best-effort**:
- Place as many blocks as possible on the rotation day
- Leave remaining blocks UNASSIGNED (not pinned, not failed)
- Let the solver place overflow blocks on other days

**Pros:** No data changes needed, matches real-world behavior
**Cons:** Doesn't fix underlying data accuracy

## Implementation (Option C)

Modify `GreedyRotationAssigner` or `HybridPhase1aService` to:
1. Try to place blocks on the rotation day
2. If capacity exceeded, leave remaining blocks UNASSIGNED
3. The solver will place them on other days

**Files:**
- `src/main/java/com/school/planner/service/GreedyRotationAssigner.java`
- `src/main/java/com/school/planner/service/HybridPhase1aService.java`

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Pre-assignment completes successfully even when capacity < blocks
- [ ] #2 Rotation day blocks are pinned correctly
- [ ] #3 Overflow blocks remain unassigned for solver
- [ ] #4 Solver can place overflow blocks on other days
- [ ] #5 No capacity-related ERROR status from pre-assignment
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by LabRotation removal (commit e71bc85). Overflow was caused by T26 having ALL 4 groups on WEDNESDAY — those entries were removed. CSP now discovers valid days with no overflow. Pre-assignment achieves 100% pinning (1135/1135).
<!-- SECTION:FINAL_SUMMARY:END -->
