---
id: TASK-352
title: >-
  School open-questions list from the 2026/27 spalmone (USER/SCHOOL DECISION
  REQUIRED)
status: In Progress
assignee: []
created_date: '2026-07-16 09:37'
updated_date: '2026-07-17 09:51'
labels:
  - claude-planned
  - spalmone-2027
  - user-decision
milestone: m-7
dependencies: []
priority: medium
ordinal: 176000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: several items in the new data cannot be resolved by code and need the school (or the user relaying to the school). Collect them in ONE place instead of scattering per-task; tasks funnel their open questions here.

Initial list (from analysis.md):
1. Doppio co-teaching: the grid cannot represent two teachers on one class-slot. Does doppio still exist in 2026/27? Which courses? (H-D1 currently seeded from old data.)
2. Jory appears in the G12 professionalizzanti note (T20/Jory/T19/T26) but is absent from Docenti and the timetable - who covers those hours?
3. FC DUALE residual deltas: T03 vs T22 swap on Laboratorio Termoidraulico (100 vs 135), Taglio Metalli 60 assigned vs 20 scheduled, T15 Cad/Lavorazioni/Controllo numerico large gaps, T13 Orientamento G12 10h never scheduled - is the incarichi side or the timetable side authoritative for these?
4. Rooms: R17 and R18 have capacity 0; the timetable assigns no rooms at all - which courses are locked to which rooms in practice?
5. Vincoli Docenti row with teacher name literally ?????? - who is it?
6. Esame days beyond year end (FC to 2027-06-15) - inside or outside the scheduling scope?

WHAT: keep this task open as the collector; when TASK-344/345/347 add questions, append here. Deliver as a short Italian-language question sheet the user can forward to the school. Mark answers as they arrive and fan out follow-up tasks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Question sheet in Italian exists under claudedocs/spalmone-2026-27/ and covers at least the 6 items above
- [ ] #2 Each answered question produces either a data change task or a documented no-op
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-17 second round: referente answered nearly everything (archive in domande-per-la-scuola.md). Key outcomes: Q1 no doppio for now (H-D1 dormant, zero seeds — correct); Q2 Jory=T01 (explains her 8h Thursdays); Q3 Orario authoritative -> TASK-371; Q4 rooms DEPRIORITIZED (skip R17/POLIVALENTE, don't invest in room features for now); Q6 exams out of scope; Q7 apprendistato GIORNI FISSI, G04 RA pool = stale incarichi row; Q8 explained (24 stage rows on Easter closures -> TASK-373); Q9 RA pool stays manual; Q17 afternoons STAGE-ONLY -> TASK-372; Q18 whitelist final -> TASK-365/349. STILL OPEN: Q11-Q14, Q16 (G14) — new brief sheet regenerated with only these 5.
<!-- SECTION:NOTES:END -->
