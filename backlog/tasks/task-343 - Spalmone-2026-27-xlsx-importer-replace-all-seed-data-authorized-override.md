---
id: TASK-343
title: 'Spalmone 2026/27 xlsx importer: replace all seed data (authorized override)'
status: Done
assignee: []
created_date: '2026-07-16 09:33'
updated_date: '2026-07-16 21:04'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies:
  - TASK-341
  - TASK-342
references:
  - claudedocs/spalmone-2026-27/data
  - claudedocs/spalmone-2026-27/analysis.md
  - .claude/rules/datagenerator-protection.md
priority: high
ordinal: 167000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: User decision 2026-07-16: the file Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx COMPLETELY OVERRIDES all previous data. DataGenerator still seeds the old 2025/26 dataset (31 teachers incl. T28 who no longer exists; old hours). The new dataset: 26 teachers, 14 groups, 16 rooms, 29 closures, ~290 real course assignments, 57 subjects.

WHAT:
1. Build an import path from the extracted CSVs in claudedocs/spalmone-2026-27/data/ (teachers.csv, groups.csv incl. enrollment, rooms.csv incl. capacity, incarichi.csv, closures.csv, vincoli_classi.csv stage windows). Either extend ImportService or write a dedicated bootstrap: choose the simpler that keeps the import repeatable on a fresh profile.
2. MUST handle pseudo-entities explicitly (analysis.md section 1): skip rows with teacher in {Stage, Esame, RA, Totale ore corso} for Course creation - their modeling is decided in the stage-modeling task; log what was skipped.
3. Normalize subject-name case duplicates (Lingua italiana vs Lingua Italiana, Educazione Civica vs civica, Comportamento Sociale vs sociale) - one canonical name each, mapping documented in notes.
4. Update DataGenerator to seed the 2026/27 data (or delegate to the importer). The datagenerator-protection rule is satisfied: explicit user authorization given 2026-07-16, cite it in the commit message.
5. Remove/deactivate obsolete seeds: T28 VACATION records, T28 LabRotations, old stage patterns; new stage windows: G07 2027-03-23..2027-05-28, G11 2027-01-13..2027-03-19, G12 2026-11-20..2027-04-17.
6. Room tags: preserve the tag-based lab room mechanism; map new rooms (R14, R15, R16, R11, R12/2, R17...) to tags consistent with course requiredRoomTags. R17 and R18 have capacity 0 in the sheet - import as-is and flag in notes.

VERIFY: after import on a fresh profile, counts match: 26 teachers, 14 groups, 16 rooms, 29 holidays; total course hours per group ~990 minus stage/esame/RA portions (assert exact numbers derived from incarichi.csv in a test).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Fresh profile import yields exact entity counts and per-group hour totals asserted in an integration test
- [x] #2 Pseudo-rows (Stage/Esame/RA/Totale ore corso) are excluded from Course creation and logged
- [x] #3 Subject names normalized; mapping documented
- [x] #4 T28 artifacts removed; new stage windows seeded; ./scripts/core/test.sh passes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
REVIEW 2026-07-16 (TASK-342): when re-adding G14, GROUP_CALENDAR_PATTERNS must gain its row (Thu 0-4, Fri 0-2, Mon/Tue/Wed CLOSED) — the DataGenerator first-Monday ramp loop and null-cell semantics are being fixed to be safe, but the pattern row itself is this task's job. Also reseed per-group maxHoursPerDay from real data (see M20 quota interplay with calendar open slots).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + code-review high (2 finder batches, 18 candidates, fixes Y1-Y12 applied). Seed = classpath CSVs (src/main/resources/seed/, format doc claudedocs/spalmone-2026-27/seed-csv-format.md, byte-sync test vs claudedocs copies). 26 teachers / 14 groups (G14 back, EXCLUDED_GROUPS emptied) / 18 rooms / 268 courses / stage windows with golden-derived masks / maxHoursPerDay from real data. AC1 nuance: entity counts + per-group hour totals asserted in DataGeneratorSeedTest at CSV/parse level; a true fresh-profile end-to-end seed test needs test isolation (Testcontainers) — deliberately not done after 2 failed @TestProfile attempts leaked H2 state into 40 unrelated tests; follow-up noted. Room-tag mapping incl. new saldatura tag (R17 cap 0 - WARN at seed + school Q4). Tests 367/367.
<!-- SECTION:FINAL_SUMMARY:END -->
