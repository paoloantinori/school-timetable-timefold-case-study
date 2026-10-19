---
id: TASK-126
title: Log detailed failure analysis in hybrid Phase 1a
status: Done
assignee:
  - '@claude'
created_date: '2026-02-26 15:36'
updated_date: '2026-03-09 19:50'
labels:
  - pre-assignment
  - phase1a
  - logging
  - observability
dependencies: []
priority: medium
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The `GreedyRotationAssigner` generates detailed failure analysis including `CAPACITY_EXCEEDED` and `INSUFFICIENT_CAPACITY` patterns, but these are not logged at ERROR level. The `HybridPhase1aService` only sets a terse error message like "Phase 1 (Acconciatura) failed" which appears in the API response.

## Problem

When Phase 1a fails, operators see:
```
{"error":"Phase 1 (Acconciatura) failed"}
```

But the actual root cause (e.g., "Teacher 14 requires 11 blocks but only 9 Thursday slots available") is only visible by checking server logs at DEBUG level.

## Solution

Add ERROR-level logging of the `FailureAnalysis` in `HybridPhase1aService.runAsync()`:

```java
if (!phase1Result.success) {
    LOG.errorf("[HybridPhase1a] Pipeline failed at Phase 1: %s", 
        phase1Result.failureAnalysis.suggestion);
    // Also log conflict patterns
    for (ConflictPattern p : phase1Result.failureAnalysis.conflictPatterns) {
        LOG.errorf("  - %s: %s", p.type, p.description);
    }
    ...
}
```

## Files to Modify

- `service/HybridPhase1aService.java` - Add ERROR logging of FailureAnalysis
- Optionally: include failure analysis in the API response's `error` field

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 When Phase 1a fails, ERROR log includes the suggestion text (e.g., "DATA DISCREPANCY DETECTED...")
- [ ] #2 Conflict patterns are logged (e.g., "CAPACITY_EXCEEDED: Teacher 14 requires 11 blocks...")
- [ ] #3 API response error field remains concise (or optionally includes more detail)
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Added ERROR-level logging of `FailureAnalysis.toFormattedString()` at three locations in `HybridPhase1aService.java`:

1. **runPhase1()** - logs detailed analysis when Phase 1 (Acconciatura) fails
2. **runPhase2ForRoomTag()** - logs detailed analysis when Phase 2 (labs) fails  
3. **buildFailureReport()** - logs failure summary with analysis

### Commit
- `b370b7c feat(logging): add detailed failure analysis logging in HybridPhase1a (task-126)`

### Acceptance Criteria
- ✅ #1 - ERROR log includes suggestion text
- ✅ #2 - Conflict patterns logged with type and description
- ✅ #3 - API response remains concise (only logs get detail)
<!-- SECTION:NOTES:END -->
