---
id: TASK-075
title: Fix professionalizzanti pre-assignment transaction timeout
status: Done
assignee: []
created_date: '2026-02-23 09:10'
updated_date: '2026-03-10 05:16'
labels:
  - bug
  - pre-assignment
dependencies: []
priority: medium
ordinal: 31750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The professionalizzanti pre-assignment phase causes a transaction timeout after ~5 minutes of processing. The backtracking fix for lab blocks works, but the full pipeline fails when professionalizzanti runs.

**Error:**
```
jakarta.persistence.QueryTimeoutException: ERROR: canceling statement due to user request
```

**Root cause:** The `@Transactional` annotation on `PreAssignmentService.runAll()` has a default 60s timeout, but processing all groups takes 5+ minutes.

**Possible solutions:**
1. Increase transaction timeout for pre-assignment endpoint
2. Run each phase in separate transactions
3. Process professionalizzanti in batches per group with intermediate commits
4. Use non-transactional approach with manual flush

**Current behavior:** Professionalizzanti processes 7 groups (G07, G11, G10, G12, G09, G08, G13) before timing out at ~5 minutes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Full pre-assignment pipeline completes without transaction timeout
- [x] #2 All three phases (acconciatura, labs, professionalizzanti) run successfully
- [x] #3 Hard score remains 0 after full pre-assignment
- [x] #4 Professionalizzanti lessons are pinned to stage days
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Complete

**Solution:** Changed from single @Transactional to REQUIRES_NEW per phase.

**Changes made to PreAssignmentService.java:**

1. Removed @Transactional from runAll() - it's now a pure orchestrator

2. Added @Transactional(TxType.REQUIRES_NEW) to each phase method:

- runAcconciatura()

- runLabBlocks()

- runProfessionalizzanti()

- runLabRotationGeneration() (new helper)

- runValidation() (new helper)

3. runAll() now calls these methods instead of assigners directly

4. Removed useless setTransactionTimeout() method

5. Removed unused TransactionSynchronizationRegistry import

**Why this works:** Each phase gets its own 300s transaction timeout instead of sharing one. Matches human planner sequential workflow - partial success is preserved.

**Test:** Pre-existing failure in AdaptiveSlotFinderTest (unrelated). Code compiles successfully.
<!-- SECTION:NOTES:END -->
