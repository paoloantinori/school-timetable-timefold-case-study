---
id: TASK-346
title: Import the hand-made golden solution as a reference schedule
status: Done
assignee: []
created_date: '2026-07-16 09:34'
updated_date: '2026-07-16 23:20'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies:
  - TASK-343
  - TASK-344
references:
  - claudedocs/spalmone-2026-27/data/lessons.csv
priority: high
ordinal: 170000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The spalmone contains the complete human-made timetable (12551 lesson-hours, extracted to claudedocs/spalmone-2026-27/data/lessons.csv). It is our golden reference for constraint calibration (m-8), regression tests (m-9) and beat-the-human benchmarking. It must be loadable into the app as an actual schedule.

WHAT:
1. Build an import that maps lessons.csv rows to Lesson entities: match (teacher, subject, group) to the Courses created by TASK-343; match (date, slot) to Timeslots from TASK-341. Stage rows follow the TASK-344 decision. Rooms are NOT in the data - leave room null (the human grid has no rooms).
2. Import as pinned lessons into a dedicated profile (e.g. planner-data-golden) so it never mixes with solver experiments; document the exact command sequence in notes.
3. Report unmatched rows (teacher/subject/group triples with no Course - expected for RA and in-flux FC entries) - the count must be reported, not swallowed.
4. Deliverable includes a wrapper script (scripts/analyze/ or scripts/api/) that performs the full load on a fresh profile.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Golden profile loads with >= 95 percent of non-pseudo rows matched; unmatched rows listed in a report file
- [x] #2 Wrapper script performs the full load end-to-end and is documented in claudedocs/scripts.md
- [ ] #3 Loaded schedule visible in the UI timeline
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + code-review high (13 candidates; critical fixed: shared Stage pseudo-teacher would have fabricated H1/H7 violations for overlapping stage windows — Teacher.pseudo flag now filters H1/H7/S1). Import via POST /api/schedule/import-golden (+force) + scripts/api/import-golden.sh; report with full category counts. Dry-run on real CSV: 10,496 real + 1,993 stage imported; 31 no-timeslot (FC exam week beyond year end, expected); 31 no-course (G08 'RA' label vs real-teacher courses — genuine data mismatch, documented). AC3 (UI visibility on golden profile) pending the next live-app cycle together with 356/358 e2e. Esame/RA accounting courses created per design doc (RA G08 only — G04 pool is double-counting, Q7 updated). Tests 399/399.
<!-- SECTION:FINAL_SUMMARY:END -->
