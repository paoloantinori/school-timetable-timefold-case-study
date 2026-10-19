---
id: TASK-368
title: >-
  Availability revisions from 'Vincoli in parole' + choice-day constraints
  (T11/T05/T04)
status: Done
assignee: []
created_date: '2026-07-17 09:02'
updated_date: '2026-07-18 00:14'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies: []
priority: high
ordinal: 192000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the school's 2026-07-17 docx (claudedocs/spalmone-2026-27/vincoli-in-parole.md) revises three teachers' availability vs our transcription from the old sheet, and introduces CHOICE-DAY semantics our additive rule model cannot express: T11 MAY start at 08:00 on one solver-chosen day/week; T05 MUST have one fully free day among Mon-Thu (solver-chosen).

WHAT:
1. Data revisions in availability_rules.csv (+ claudedocs copy + byte-sync): T04 8-14 Mon-Thu (currently blocks slot 5 per the old 8-13 note) + Friday off stays; T05 8-14 Mon-Thu only (drop the old Tue-afternoon availability; Friday off stays); T11 unchanged (9-14) pending item 2.
2. Choice-day model: a new constraint pair is needed because the current expansion is per-day static: (a) T11: at most ONE day per week may use slot 0 (soft/medium reward or a disjunctive hard? recommend MEDIUM 'at most one 8:00 start day per week' — penalize the 2nd+ day using slot 0); (b) T05: at least ONE of Mon-Thu each week with zero lessons (MEDIUM penalty when all four days have lessons). Both are weekly-quantified constraints over the teacher's lessons — implementable as constraint streams grouped by (teacher, ISO week). Keep the static slot rules as the base (T11 slot 0 currently blocked daily -> UNBLOCK slot 0 in his rules and let the new constraint cap its use).
3. Update availability-not-yet-modeled.md: the T11 one-day-8am and T05 light-day clauses move from NOT-YET-MODELED to modeled; T05's 'MAX un'ora one light day' old reading is superseded by the docx's 'one day fully free'.
4. ConstraintVerifier tests for both weekly constraints; golden-audit note: measure how the golden solution scores on them (informational).
Ref: claudedocs/spalmone-2026-27/vincoli-in-parole.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 CSV revisions applied with byte-sync green; expansion reflects the docx windows
- [x] #2 Weekly choice-day constraints implemented with tests; T11 slot 0 unblocked statically and capped by constraint
- [x] #3 NOT-YET-MODELED registry updated; golden measurement recorded
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Docx availability revisions + choice-day constraints shipped. CSVs: T04 to 14:00 Mon-Thu, T05 uniform Mon-Thu afternoon blocks, T11 slot-0 unblocked (27->23 rows). M24 'at most one early-start day/week' (Teacher.maxEarlyStartDaysPerWeek, T11=1 — golden violates 15 weeks/52 slot-0 lessons, correcting the brief's zero claim; calibrated guard pin -16/15) and M25 'required free day/week' (requiredFreeDaysPerWeek, T05=2 because the docx literal 1 is vacuous with Friday already blocked; pin -27/27). Per-teacher fields follow the maxDailyHours pattern end-to-end (CSV+extractor preservation+null-keeps/0-sentinel CRUD). WeekFields.ISO locale bug caught in-agent. Full DoD in-agent (simplify + code-review high) + orchestrator post-merge verification 544/544 + typecheck exit 0 (domain.ts merge conflict union-resolved). Known gap documented: ImportService teacher UPSERT omits the per-teacher override columns (pre-existing).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
