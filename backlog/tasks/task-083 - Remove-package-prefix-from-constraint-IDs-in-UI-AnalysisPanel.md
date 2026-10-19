---
id: TASK-083
title: Remove package prefix from constraint IDs in UI (AnalysisPanel)
status: Done
assignee: []
created_date: '2026-02-23 13:54'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - claude-planned
dependencies: []
priority: high
ordinal: 28750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The AnalysisPanel displays constraint IDs with the full Java package prefix (e.g., `com.school.planner.domain/H1.1: Teacher conflict (L-L)`) which is internal technical detail that adds no value to users.

**Problem**:
```json
{
  "constraint": "com.school.planner.domain/H1.1: Teacher conflict (L-L)",
  ...
}
```

**Expected**:
```json
{
  "constraint": "H1.1: Teacher conflict (L-L)",
  ...
}
```

**Root Cause**: In `ScheduleResource.java`, `ref.constraintId()` returns the full constraint ID including package prefix from Timefold Solver's `ConstraintRef`.

**Solution Options**:
1. **Backend (preferred)**: Strip package in `ScheduleResource.getScoreAnalysis()` before returning JSON
2. **Frontend**: Strip package in `AnalysisPanel.tsx` when displaying

Backend is preferred because:
- Clean API response for all consumers
- Single point of change
- Consistent with other constraint ID displays (docs page uses just "H1", "M3", etc.)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 AnalysisPanel shows constraint IDs without package prefix (e.g., 'H1.1: Teacher conflict (L-L)' not 'com.school.planner.domain/H1.1: ...')
- [x] #2 API response from /api/schedule/analysis returns clean constraint IDs
- [x] #3 No other user-facing screens show the package prefix
- [x] #4 Backend change: extract just the constraint name from ConstraintRef
- [x] #5 All constraint IDs remain sortable and filterable in AnalysisPanel
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Approach

### Option 1: Backend Fix (Recommended)

Modify `ScheduleResource.java` to strip the package prefix:

```java
// Current:
entry.put("constraint", ref.constraintId());

// Fixed:
String fullName = ref.constraintId();
String constraintName = fullName.contains("/") 
    ? fullName.substring(fullName.indexOf("/") + 1) 
    : fullName;
entry.put("constraint", constraintName);
```

Or use Timefold's API if available:
```java
// Check if ConstraintRef has packageName() and constraintName() methods
entry.put("constraint", ref.constraintName()); // or ref.justification()
```

### Option 2: Frontend Fix (Fallback)

Modify `AnalysisPanel.tsx` to strip package when displaying:

```tsx
// Helper function
const stripPackage = (constraintId: string) => {
  const idx = constraintId.indexOf('/');
  return idx >= 0 ? constraintId.substring(idx + 1) : constraintId;
};

// Usage in table
<span title={c.constraint}>
  {stripPackage(c.constraint)}
</span>
```

### Files to Modify

**Backend (Option 1 - Preferred)**:
- `src/main/java/com/school/planner/rest/ScheduleResource.java` — strip package in `getScoreAnalysis()`

**Frontend (Option 2)**:
- `src/main/webui/src/components/AnalysisPanel.tsx` — strip package in display

### Verification

1. Run app, trigger solver
2. Click "Analyze" button to open AnalysisPanel
3. Verify constraint IDs show as `H1.1: Teacher conflict (L-L)` without package prefix

Backend fix applied in ScheduleResource.java:72-76. Strips package prefix from ConstraintRef.constraintId() before returning in API response.

Propagation skipped per user request - change only on feature/cartesian-block-entity branch.
<!-- SECTION:NOTES:END -->
