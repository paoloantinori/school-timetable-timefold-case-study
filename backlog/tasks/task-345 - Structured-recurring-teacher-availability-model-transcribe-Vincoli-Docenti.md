---
id: TASK-345
title: Structured recurring teacher availability model + transcribe Vincoli Docenti
status: Done
assignee: []
created_date: '2026-07-16 09:34'
updated_date: '2026-07-17 00:00'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies:
  - TASK-341
  - TASK-343
references:
  - claudedocs/spalmone-2026-27/analysis.md
priority: high
ordinal: 169000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: TeacherAvailability today stores absolute datetime VACATION/PREFERRED rows. The real constraints (analysis.md section 5) are weekly-recurring and richer: T04 8-13 + Friday free; T05 Mon/Wed/Thu 8-14 with at most a light day on one of them, Tue also afternoon, Fri free; T11 9-14 always; T16 max 3 consecutive R16 hours and 2 mornings free from 11:00; T10 Thursday morning free; T12 equipe-day rules + dated absences.

WHAT:
1. Extend the availability model with recurring rules: {teacher, dayOfWeek, slotRange, type (UNAVAILABLE/PREFERRED), effectiveFrom/To} alongside existing dated records. A generator can expand rules to dated records if that keeps H5 unchanged - prefer the expansion approach (matches how T28 was handled) unless it explodes row counts unreasonably.
2. Transcribe every entry of the Vincoli Docenti sheet (full text in analysis.md section 5 and the xlsx) into rules/records. Items that are NOT expressible (T16 max-3-consecutive-in-R16 = block-size cap per subject; T05 at-most-one-light-day = weekly pattern constraint) get documented as NOT-YET-MODELED with a proposed constraint sketch each - do not silently drop them.
3. Seed via the importer path from TASK-343.
4. Anything ambiguous goes to the school-questions task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Recurring availability rules exist and expand into H5-visible records; unit test covers expansion incl. slot ranges
- [x] #2 Every Vincoli Docenti row is either transcribed or listed as NOT-YET-MODELED with a proposal - zero silently dropped
- [x] #3 ./scripts/core/test.sh passes
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented (worktree, merged) + code-review high (9 candidates, fixes R1-R8). RecurringAvailabilityRule + AvailabilityExpansionService (expansion to dated H5/S3 rows, sourceRuleId delete-and-regenerate, lunch-spanning verified vs H5 overlap matching); 24 rules transcribed from Vincoli Docenti (availability_rules.csv, byte-sync tested); validated CRUD (400 on slot bounds/overlap/date-incoherence/unknown teacher) with re-expansion; NOT-YET-MODELED registry (zero clauses dropped, incl. T03/T13 from the raw sheet) with constraint sketches; Q10-Q15 added for the school. UI editor = TASK-359 (annotated). Tests 446/446.
<!-- SECTION:FINAL_SUMMARY:END -->
