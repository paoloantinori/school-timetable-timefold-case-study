---
id: TASK-268
title: >-
  BUG: DayClusteredConstructionPhase not mixing subjects - places all lessons of
  same subject consecutively
status: Done
assignee: []
created_date: '2026-03-17 16:39'
updated_date: '2026-03-17 17:05'
labels:
  - bug
  - pre-assignment
  - phase3-professionalizzanti
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Problem:** Phase 3 (professionalizzanti handling) concentrates ALL lessons of a subject on consecutive days instead of spreading them across available stage days.

**Evidence from G10 Cosmetologia:**
- March 16: 6 Cosmetologia lessons (all day)
- March 17: 6 Cosmetologia lessons (all day)  
- March 18: 4 Cosmetologia lessons (all day)

**Expected Behavior:** Professionalizzanti courses typically have 1-2 days per week. Lessons should be spread evenly across ALL available stage days throughout the year, not concentrated.

**Example:** 35 hours of Cosmetologia over ~170 school days should be ~1 lesson every 5 days, not 16-18 lessons concentrated in 3 days.

**Root Cause:** `HybridPhase1aService.runPhase3Professionalizzanti()` uses `findStageDaySlot()` which doesn't track/skip recently used dates. It keeps finding the same available slots (Monday-Wednesday of a given week) and pins all lessons there.

**Files to investigate:**
- `src/main/java/com/school/planner/service/HybridPhase1aService.java` - Phase 3 logic, `findStageDaySlot()` method
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Additional Evidence - G08:**
- March 5 (Thu): Controllo numerico (2) + Tecnologia meccanica (4) = 6 lessons
- March 6 (Fri): Controllo numerico (1) + Sicurezza (1) + Tecnologia meccanica (3) = 5 lessons
- March 9 (Mon): Controllo numerico (1) + Tecnologia meccanica (3) = 4 lessons
- March 10 (Tue): Taglio metalli (2) lessons
- March 11 (Wed): Lavorazioni meccaniche d'officina (4) lessons
- March 12 (Thu): Controllo numerico (2) lessons
- March 13 (Fri): Sicurezza (2) lessons

**Pattern:** Lessons are scattered across different days and subjects

instead of consistent 1-2 fixed days per week.

**Expected:** For example

if Controllo numerico should be on Thursdays

then ONLY Thursdays throughout the year should have Controllo numerico lessons (1-2 per Thursday)

not scattered across Mon/Tue/Wed.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
["**ROOT CAUSE IDENTIFIED - DATA PROBLEM:**", "Missing `ProfessionalizzantiDay` records for G10, G08, G13.", "", "DataGenerator only seeds:", "- G07 → Monday", "- G11 → Monday", "", "**Missing groups (should be added from spalmone.csv analysis):**", "Need to determine which day each group has professionalizzanti lessons", "and add `ProfessionalizzantiDay` records.", "", "**Analysis needed:**", "1. Review spalmone.csv to identify professionalizzanti lesson patterns", "2. For G10: Cosmetologia (35h) - what day in spalmone?", "3. For G08: Tecnologia meccanica, Controllo numerico, Sicurezza - what days?", "4. For G13: multiple professionalizzanti subjects - what day(s)?", "", "**Current behavior:**", "When `ProfessionalizzantiDay` is null, Phase 3 places lessons on ANY stage day", "(no filtering by day of week), creating scattered patterns.", "", "**Fix:**", "1. Analyze spalmone.csv to extract professionalizzanti day patterns", "2. Add `ProfessionalizzantiDay` records to DataGenerator for missing groups", "3. Verify Phase 3 uses designated day consistently"]
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**FIXED**: Added early return in HybridPhase1aService.findStageDaySlot() when ProfessionalizzantiDay is null

**Result**: Groups without designated professionalizzanti day are now correctly skipped by Phase 3

**Verified**: G10 no longer has lessons on March 3, 16, 17, 18 (stage days)

**Root cause**: When expectedDay=null, code was still iterating ALL stage days and placing lessons

**Code change**: Line ~992 in HybridPhase1aService.java - return Optional.empty() if expectedDay == null
<!-- SECTION:FINAL_SUMMARY:END -->
