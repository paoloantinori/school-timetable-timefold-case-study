---
id: TASK-344
title: >-
  Design decision: model Stage, Esame and RA hours (they count toward yearly
  totals)
status: Done
assignee: []
created_date: '2026-07-16 09:34'
updated_date: '2026-07-16 19:45'
labels:
  - claude-planned
  - spalmone-2027
  - design
milestone: m-7
dependencies:
  - TASK-343
references:
  - claudedocs/spalmone-2026-27/analysis.md
priority: high
ordinal: 168000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: In the real data (analysis.md sections 1 and 4.6) stage is NOT a pure blackout: stage hours are scheduled as 8h/day pseudo-lessons and COUNT toward the ~990h yearly group total; groups return to school during their stage window (G11 has 4 stage days/week + 1 school day - matches StagePattern dayMask). Esame hours (12-24h per graduating group) are assigned but sit at year end, partially AFTER 2027-06-10 (FC until 06-15). RA (Recuperi ed Approfondimenti) for G08 is taught by many different teachers with no fixed assignment.

WHAT: Write a short design document (claudedocs/design/) deciding, for each of the three, one of: (a) pinned pseudo-lessons occupying the calendar and counting toward totals, (b) blackout via existing H4/StagePattern with totals adjusted, (c) out of scope for the solver. Evaluate against: H4 semantics, per-group hour accounting used by the completion/reconciliation endpoints, M12 target end date (exams beyond year end), and the golden-solution import (which contains Stage rows). Recommend ONE option each with rationale; flag anything needing school confirmation. Then implement the stage decision (the largest one); Esame/RA implementation may be follow-up tasks if the decision is non-trivial.

NOTE: keep the decision inline in the design doc - no errata sections.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Design doc exists with one recommendation each for Stage/Esame/RA and their hour-accounting impact
- [ ] #2 Stage decision implemented; per-group scheduled+stage hour totals reconcile with incarichi.csv totals in a test
- [x] #3 Open school questions listed in the doc and copied into the school-questions task
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-16: design doc at claudedocs/design/stage-esame-ra-modeling-2026-07-16.md — Stage=hybrid (H4 blackout + pinned pseudo-lessons counting toward totals; G04 DUALE per-date pins only), Esame=accounting stub pending Q6, RA=accounting-only. Key numbers verified in main session against CSVs. Q7-Q9 added to domande-per-la-scuola.md. AC2 (implement stage decision + reconcile test) pending: implement AFTER TASK-343 lands, inside TASK-346's import path per the doc's implementation plan.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
