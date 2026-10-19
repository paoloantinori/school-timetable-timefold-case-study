---
id: TASK-272
title: 'BUG: G09 lessons assigned on stage day (Tue 10/03)'
status: Done
assignee: []
created_date: '2026-03-18 13:29'
updated_date: '2026-07-15 19:32'
labels:
  - bug
  - stage-blackout
  - H4
  - professionalizzanti
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Issue:** Lessons for G09 are being assigned on Tuesday March 10, 2026, which is during their stage period (March 5 - April 29). According to spalmone data, G09 should have no lessons during this period.

**Root Cause:** 
The H4 constraint allows professionalizzanti courses during stage periods, but this should be a **per-group administrator setting**, not a hardcoded rule. For G09, professionalizzanti should be blocked during stage.

**Details:**
- Stage period for G09: 2026-03-05 to 2026-04-29 (Monday-Friday)
- Lessons found on 2026-03-10 (Tuesday):
  - Confezione (T16) - professionalizzanti=true
  - Modello (T17) - professionalizzanti=true
- dayOfWeekMask values: 0,1,2,3,4 (ordinals for Monday-Friday)

**Solution:**
Add a per-group configuration field (e.g., `allowProfessionalizzantiDuringStage`) to control whether professionalizzanti courses can be scheduled during stage periods. Default should be `false` (block them), and G09 should have this setting disabled.

**Implementation:**
1. Add `allowProfessionalizzantiDuringStage` boolean field to StudentGroup entity
2. Update H4 constraint to check this setting: block if `!allowProfessionalizzantiDuringStage`
3. DataGenerator should set this to `false` for all groups by default
4. For G09 specifically, ensure it's `false`
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): allowProfessionalizzantiDuringStage=false for G09 (DataGenerator:197 NO_PROFESSIONALIZZANTI_DURING_STAGE set). H4 (BaseConstraintProvider:72-78) checks the flag. Plus H4.3 generalized (2026-07-15) enforces prof day-of-week.
<!-- SECTION:FINAL_SUMMARY:END -->
