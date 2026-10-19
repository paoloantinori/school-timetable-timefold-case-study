---
id: TASK-379
title: >-
  Golden-first recalibration: demote H21/H22, extend Orario-wins to MODA shift
  courses
status: Done
assignee: []
created_date: '2026-07-18 10:30'
updated_date: '2026-07-18 11:14'
labels:
  - claude-planned
dependencies: []
ordinal: 203000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User directive 2026-07-18: the school considers the golden timetable VALID; our rules must be compatible with it and never stricter than the practice it embodies, regardless of verbal notes (docx/Q17) — discrepancies get reported to the school but do not block progress. Run-3 audit of rules stricter than golden practice: (1) H21 afternoons stage-only (hard + VRP filter) vs 575 real golden afternoon lessons — the capacity relief valve the school itself used; demote to medium preference (M28), REMOVE the VRP afternoon exclusion in Lesson.getAvailableTimeslots. (2) H22 professionalizzanti edge-of-day (hard, -14 in run 3) vs golden's own {0,4}/{2,3} placements; demote to medium. (3) Seed demand exceeds golden: G01 Confezione 180 vs 169, Moulage 143 vs 128; G05 Confezione 176 vs 166, Moulage 159 vs 150 — apply Orario-wins (TASK-371 precedent) to these shift courses so every group's demand fits its calendar. H15/H17 stay pinned-exempt hard: run 3 shows the solver satisfies them (0/-1) — not binding. Question for referente added to domande-brevi.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H21 renamed M28, medium weight, pseudo-exemption kept; VRP afternoon filter removed from Lesson.getAvailableTimeslots
- [ ] #2 H22 demoted to medium ONE_MEDIUM per violating day, same day-level pinned exemption
- [ ] #3 incarichi.csv (classpath + claudedocs copy, byte-sync test green) reduced to golden hours for the 4 MODA shift courses; fresh-profile demand = 990/group for 1-G05
- [ ] #4 Guard, CLAUDE.md tables, constraints.ts, i18n cards updated; test.sh exit 0
- [ ] #5 Run 4 launched on VERIFIED fresh JAR
- [ ] #6 DoD: /simplify + /code-review high
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DONE 2026-07-18. Implemented: H21→M28 (medium 100/lesson, VRP afternoon exclusion removed from Lesson.getAvailableTimeslots), H22→M29 (ONE_MEDIUM/day), CalendarioImportService 'Orario wins' cap (per-course totalHours budget, date-ordered visit, reservation rollback on skip, dry-run predicts force-run) + modaSkippedBeyondCourseHours report field (real data: 293 planned/866 pins/17 capped rows), frontend afternoon unlock (types.ts/LessonTooltip/e2e inverted), guard M28/M29 pins, CLAUDE.md/docs/i18n updated, Q19 for referente in domande-brevi.md. DoD: /simplify applied (map collapse, pinned-tally reuse, visit-order line-number preservation; skipped: fixture dedup, extractor-side reconciliation→TASK-364); /code-review high 8 findings (4 fixed incl. phantom-reservation batch blocker + dry-run budget bug, 3 skipped documented, 1 no-change); test.sh 605/605 exit 0, tc 0, build 0. NOT done here: run 4 (next step).
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
