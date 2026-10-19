---
id: task-051
title: Show blocking error modal when pre-assignment produces hard violations
status: Done
assignee:
  - claude
created_date: '2026-02-20 20:25'
updated_date: '2026-02-21 15:19'
labels:
  - ui
  - claude-planned
dependencies:
  - task-040
priority: medium
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

When pre-assignment completes with hard constraint violations (non-zero hard score), the problem is likely infeasible and running the solver will waste time. Currently:
1. The backend's `PreAssignmentService.runAll()` runs `validatePreAssignments()` which checks H1/H2/H3 conflicts
2. It also runs `analyzeSolverConstraints()` to get the full hard score
3. This validation info is returned in the API response but is only shown as a text summary

The user needs a **blocking error dialog** that:
- Clearly indicates the schedule may be infeasible
- Lists which hard constraints are violated
- Shows the constraint IDs (H1, H2, H3, etc.) and match counts
- Prevents confusion about why solver produces poor results

## Current State
- Backend: `ValidationResult` includes `solverAnalysis` with `hardScore` and `hardConstraints` list
- Frontend: Pre-assignment result is formatted by `formatPreAssignResult()` but only shows text summary
- Frontend: No modal for hard violations, just inline text

## Proposed Solution
Add a modal dialog that appears when pre-assignment result contains `validation.solverAnalysis.hardScore < 0`. The modal should explain the problem and list violating constraints.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [GIVEN] Pre-assignment completes with hard score violations [WHEN] The API returns the validation result [THEN] A modal dialog appears showing which hard constraints are violated (H1, H2, H3, etc.) with match counts
- [x] #2 [GIVEN] Pre-assignment completes with 0 hard score [WHEN] The user views the result [THEN] No blocking error is shown, only the normal success summary
- [x] #3 [GIVEN] A blocking error modal is displayed [WHEN] The user clicks 'Details' [THEN] The full list of violated constraint IDs and their scores is shown
- [x] #4 [GIVEN] A blocking error modal is displayed [WHEN] The user clicks 'Dismiss' [THEN] The modal closes and the user can proceed (the schedule may still be unfeasible)
- [x] #5 [GIVEN] Pre-assignment fails with status=ERROR [WHEN] The error occurs [THEN] A blocking modal shows the exception message from preAssignmentError
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Frontend Changes Only (backend already provides data)

1. **domain.ts** - Ensure types match backend response:
   ```typescript
   export interface SolverConstraintAnalysis {
     hardScore: number;
     hardConstraints: ConstraintSummary[];
   }
   
   export interface ConstraintSummary {
     constraintId: string;
     score: number;
     matchCount: number;
   }
   
   export interface ValidationResult {
     conflictFree: boolean;
     violations: ConflictViolation[];
     solverAnalysis: SolverConstraintAnalysis | null;
   }
   ```

2. **SolverControls.tsx** - Add blocking error modal:
   - Add state: `showBlockingError: boolean`, `blockingErrorInfo: ValidationResult | null`
   - After pre-assignment completes, check `result.validation?.solverAnalysis?.hardScore < 0`
   - If true, set `showBlockingError = true` and store validation info
   - Render modal with:
     - Title: "Pre-assignment Completed with Hard Constraint Violations"
     - Explanation: "The pre-assigned schedule has constraint violations that may make it impossible for the solver to find a feasible solution."
     - List of violated constraints with IDs and match counts
     - "Details" expandable section showing all violations
     - "Dismiss" button to close

3. **Modal Design** (inline styles matching existing modals):
   ```tsx
   // Red/warning themed modal
   <div style={styles.modalOverlay}>
     <div style={{...styles.modalContent, borderLeft: '4px solid #ef4444'}}>
       <h3>⚠️ Hard Constraint Violations</h3>
       <p>The schedule has violations that may make it infeasible:</p>
       <ul>
         {hardConstraints.map(c => (
           <li key={c.constraintId}>
             <strong>{c.constraintId}</strong>: {c.matchCount} violations (score: {c.score})
           </li>
         ))}
       </ul>
       <button onClick={() => setShowBlockingError(false)}>Dismiss</button>
     </div>
   </div>
   ```

4. **i18n** - Add translation keys:
   ```json
   "blockingErrorTitle": "Pre-assignment Completed with Violations",
   "blockingErrorDesc": "The pre-assigned schedule has constraint violations that may make it impossible for the solver to find a feasible solution.",
   "blockingErrorHint": "Review the constraint violations below. You may need to adjust LabRotation records, teacher availability, or stage patterns.",
   "dismiss": "Dismiss"
   ```

### Files to Modify
- `/home/user/data/repo/personal/planner/src/main/webui/src/types/domain.ts`
- `/home/user/data/repo/personal/planner/src/main/webui/src/components/SolverControls.tsx`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/en/schedule.json`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/it/schedule.json`

### Edge Cases
- Handle case where `validation` is null (dry-run path)
- Handle case where `solverAnalysis` is null (analysis failed)
- Handle `preAssignmentError` separately (exception case, not validation failure)

### Testing
1. Create a scenario with intentional conflicts (e.g., overlapping teacher assignments)
2. Run pre-assignment
3. Verify modal appears with correct constraint list
4. Verify modal dismisses and doesn't block future operations
5. Verify successful pre-assignment (0 hard score) does not show modal
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **2/5** (Low-Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 2 | Frontend modal + logic |
| Risk | 2 | No backend changes |
| Testing | 2 | Edge case testing |
| Dependencies | 2 | Uses validation data from task-040 |
| **Total** | **2** | 2-4 hours (after task-040) |

---

## Priority: **MEDIUM** (User Clarity)

Prevents user confusion when pre-assignment produces infeasible schedules.
<!-- SECTION:NOTES:END -->
