---
id: TASK-366
title: >-
  Golden-fidelity fixes: H4 must ignore synthetic Stage lessons; import the 2
  FC RA rows
status: Done
assignee: []
created_date: '2026-07-17 00:36'
updated_date: '2026-07-27 04:48'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies:
  - TASK-347
priority: high
ordinal: 190000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: TASK-347 root-caused two golden-import artifacts: (1) H4 stage blackout fires 694 times on the synthetic Stage course's OWN pinned lessons (nonProfLessonStagePairs does not exclude the Stage pseudo course — a lesson representing the stage itself cannot violate its own blackout); (2) the 31 unimported G08 'RA' rows punch mid-day holes that fabricate 22 H17 vertical-compactness violations. The golden reference must score clean on artifacts before m-8 recalibration lands (TASK-355's regression suite will pin these numbers).

WHAT: 1) H4: exclude lessons whose course is the synthetic Stage course (or whose teacher is pseudo) from nonProfLessonStagePairs; constraint-verifier test with a pinned Stage lesson inside its own group's stage window -> no H4. 2) RA rows: extend GoldenScheduleImportService to import the G08 'RA' rows by creating per-teacher micro-courses (teacher, 'Recuperi ed Approfondimenti', G08, hours=row count) — pending school answer Q9 these carry a clear provenance note; the G08 RA accounting course's 40h assigned then reconciles against these scheduled hours in TASK-353's dashboard. Update the dry-run test categories (imported should rise by 31, unmatchedNoCourse to 0) and re-run the golden audit numbers for H4/H17 (expect 12 genuine H4 rientro exceptions and 1 genuine H17 T01 case to remain — document them).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 H4 = 12 (genuine rientro cases only) and H17 = 1 (T01) on the golden import, verified live
- [x] #2 RA rows imported (unmatchedNoCourse=0); dry-run test updated; reconciliation note for TASK-353
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-18 live cycle: AC1 verified against the running app — fresh profile + golden import, analysis shows H4=-12/12 (genuine rientri), H17=0 (per TASK-350's pinned-break exemption, superseding the -1 prediction), H7=0. All other hard constraints zero. Import categories match the guard exactly (10,527 imported incl. RA micro-courses, 311 stageOnClosure, 0 unmatched).

SUPERATO/IMPLEMENTATO (2026-07-27): entrambi i punti risultano gia' a bordo - H4 esclude le lezioni Stage sintetiche via il filtro pseudo-teacher di nonProfLessonStagePairs (documentato in CLAUDE.md, pin golden H4 -12 calibrato proprio da TASK-366) e le RA G08 sono i 'real-teacher RA micro-courses, task-366' citati nella sezione M21. Archiviato come completato di fatto.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + combined code-review high with TASK-348 (fixes D1-D8 incl. coverage-based RA pool dedup and micro-course totalHours refresh). H4 ignores pseudo-teacher lessons (null-teacher lessons still subject — asymmetry documented and test-locked); 31 RA rows imported as per-teacher micro-courses; dry-run categories updated (unmatchedNoCourse=0). AC1 (live re-audit asserting H4=12, H17=1) pending the next live app cycle — expectations recorded inline in golden-audit.md. Tests 512/512.
<!-- SECTION:FINAL_SUMMARY:END -->
