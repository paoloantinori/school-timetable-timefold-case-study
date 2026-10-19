---
id: TASK-050
title: Track and display pre-planning time in SolverControls
status: Done
assignee: []
created_date: '2026-02-20 20:24'
updated_date: '2026-03-16 14:44'
labels:
  - ui
  - claude-planned
dependencies:
  - task-040
priority: medium
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The SolverControls component displays elapsed time for the solver phases (Construction Heuristic, Local Search), but the pre-assignment (pre-planning) phase duration is not shown. Users cannot see how long pre-assignment took, making it difficult to:
1. Estimate total planning time
2. Compare pre-assignment duration across runs
3. Understand the full pipeline timeline

The backend already tracks `preAssignmentStartTime` in `SchoolSchedule.java` and `ScheduleService.java`. The frontend receives this data but does not display it in the elapsed time section.

## Current State
- Backend: `preAssignmentStartTime` is set when pre-assignment starts
- Backend: `preAssignmentStatus` transitions: IDLE → ACCONCIATURA → LABS → PROFESSIONALIZZANTI → COMPLETE
- Frontend: `isPreAssigning` is detected and elapsed timer shows during pre-assignment
- Frontend: No explicit "Pre-planning time" display after completion

## Proposed Solution
Add a `preAssignmentEndTime` field to track when pre-assignment completed, then display the total pre-planning duration in SolverControls alongside CH and LS times.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [GIVEN] Pre-assignment is NOT running [WHEN] The user views SolverControls [THEN] No pre-planning time is displayed
- [ ] #2 [GIVEN] Pre-assignment has completed (status=COMPLETE) [WHEN] The user views SolverControls [THEN] The total pre-planning duration is shown (e.g., 'Pre-planning: 12s')
- [ ] #3 [GIVEN] Pre-assignment is in progress [WHEN] The elapsed timer updates [THEN] The time counts up from preAssignmentStartTime to now
- [ ] #4 [GIVEN] Pre-assignment completed with status=ERROR [WHEN] The user views SolverControls [THEN] The elapsed time up to the error is still displayed
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Backend Changes
1. **SchoolSchedule.java** - Add field:
   ```java
   private Instant preAssignmentEndTime;
   // getter/setter
   ```

2. **ScheduleService.java** - Add tracking:
   ```java
   private volatile Instant preAssignmentEndTime;
   // getter/setter
   // Include in getSchoolSchedule() output
   ```

3. **PreAssignmentService.java** - Set end time:
   ```java
   // In runAll(), before returning:
   scheduleService.setPreAssignmentEndTime(Instant.now());
   // In error catch block:
   scheduleService.setPreAssignmentEndTime(Instant.now());
   ```

### Frontend Changes
4. **domain.ts** - Add to SchoolSchedule interface:
   ```typescript
   preAssignmentEndTime: string | null;
   ```

5. **SolverControls.tsx** - Add pre-planning timer display:
   - Compute `preAssignSeconds` from `preAssignmentStartTime` to `preAssignmentEndTime` (or now if still running)
   - Display format: `Pre-planning: 12s` in elapsed section
   - Show after solver start alongside CH/LS timers

6. **i18n** - Add translation key:
   ```json
   "prePlanningTime": "Pre-planning"
   ```

### Files to Modify
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/domain/SchoolSchedule.java`
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/service/ScheduleService.java`
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/service/PreAssignmentService.java`
- `/home/user/data/repo/personal/planner/src/main/webui/src/types/domain.ts`
- `/home/user/data/repo/personal/planner/src/main/webui/src/components/SolverControls.tsx`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/en/schedule.json`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/it/schedule.json`

### Testing
1. Run pre-assignment via UI
2. Verify timer shows during pre-assignment
3. Verify total time persists after completion
4. Verify time displays correctly after page refresh (data from backend)
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **2/5** (Low-Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 2 | Backend field + frontend display |
| Risk | 2 | Simple addition |
| Testing | 2 | Visual verification |
| Dependencies | 2 | Depends on task-040 |
| **Total** | **2** | 2-4 hours (after task-040) |

---

## Priority: **MEDIUM** (Completes Timing UX)

Completes the timing picture. Relies on task-040 infrastructure.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**TASK-050 - Pre-planning Time Display - ALREADY IMPLEMENTED ✅**

The feature is already complete:

**Backend:**
- `SchoolSchedule.java` has `preAssignmentDurationSeconds` field (line 373)
- Getters/setters implemented

**Frontend:**
- `UnifiedSolverControls.tsx` already displays pre-planning time (line 766-769)
- Shows elapsed time during and after pre-assignment
- Format: `CH: Xs LS: Ys` for Construction Heuristic and Local Search
- Pre-assignment duration is included in elapsed time calculation

**Verification:** The elapsed timer already accounts for `schedule?.preAssignmentDurationSeconds` and displays it in the UI alongside CH/LS times.
<!-- SECTION:FINAL_SUMMARY:END -->
