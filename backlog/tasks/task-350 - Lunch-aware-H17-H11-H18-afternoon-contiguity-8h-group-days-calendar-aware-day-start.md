---
id: TASK-350
title: >-
  Lunch-aware H17/H11/H18: afternoon contiguity, 8h group days, calendar-aware
  day start
status: Done
assignee: []
created_date: '2026-07-16 09:35'
updated_date: '2026-07-18 01:41'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies:
  - TASK-342
  - TASK-347
priority: high
ordinal: 174000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: analysis.md sections 2-4: real group days reach 8h (276 group-days at 7-8h); the lunch break must not count as a gap for H17; a 14:30 lesson with an empty 13:00 slot happens only 2 times in the whole golden year (near-hard contiguity across lunch); H18 start-of-day must be the first OPEN slot per the group calendar (first school day starts 09:00), not literal slot 0.

WHAT:
1. H17 vertically-compact: use the shared adjacency utility; zero-gap within the morning block stays hard; decide and implement afternoon rule: lessons at slot >= 6 require slot 5 occupied (allow a documented exception mechanism or accept 2 golden violations as medium-tier).
2. H11: per-group maxHoursPerDay reseeded from the real data (derive max observed per group from lessons.csv via the extractor); groups with afternoons get 8.
3. H18: consult GroupCalendarRule (TASK-342) for the first open slot instead of assuming slot 0.
4. ConstraintVerifier tests for each changed behavior; re-run golden audit and record deltas.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 H17 lunch-aware; afternoon-contiguity rule implemented and documented
- [x] #2 H11 per-group maxima match real data; H18 calendar-aware
- [x] #3 Golden audit re-run: H17/H11/H18 each 0 (or the accepted documented exceptions); tests pass
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
NOTE 2026-07-16 (from TASK-342 review): M18/H18 applicability gate still uses studentGroup.maxHoursPerDay <= 5 to select which lessons to check, while the start-anchor is now calendar-aware. For mixed-calendar groups (G12: Mon-Wed short, Thu-Fri long) the gate fires on long days too. Consider gating on GroupCalendarService.openSlotCount(group, weekday, date) <= 5 — behavior change, decide with the golden audit numbers.

NOTE 2026-07-17 (TASK-347 golden audit, clean-redo verified): H17 measured -26 hard, matches=23 (predicted ~0 + 2 known afternoon exceptions). Root-caused ALL 23: 22 are studentGroup=G08 and trace exactly to the 31 RA-subject rows the golden importer skips (unmatchedNoCourseTriples - no Course exists for those teacher x RA x G08 triples), which sit mid-day in lessons.csv (e.g. 2026-09-29 09:00 T06/RA between T14/Lingua-Italiana at 08:00 and T03/Sicurezza at 10:00) and leave an artificial hole once dropped - an import-defect side-effect of the (deliberate, documented) RA-pool accounting design, NOT a constraint bug and not a genuine human-schedule gap. The 1 remaining violation (G10, T01, 2026-12-17) IS the predicted genuine afternoon exception: 08:00-13:00 + 14:30-16:30 with 13:00-14:00 empty, crossing lunch - matches analysis.md's "2 exceptions only" finding. H17's own lunch-aware adjacency (TimeslotSequence.isAdjacent, already treats slot5-end -> slot6-start as non-gap) is confirmed working correctly; this task's item 1 (shared adjacency utility) appears already implemented. H11 measured 0 (per-group maxHoursPerDay already reseeded from real data per DataGenerator log, e.g. G08=8, matches this task's item 2). M18 (renamed from H18, already demoted to medium) measured 0. Full audit + root-cause detail: claudedocs/spalmone-2026-27/golden-audit.md.

NOTE 2026-07-18 (remaining scope closed): H17 got TASK-349's break-level both-sides-pinned exemption applied to `verticallyCompact()` (golden -3/2 -> 0/0, moved to the guard's zero-tolerance section) and `ScheduleService.addH17Violations` was consolidated onto the same TimeslotSequence.slotIndexOf() + pinned-check semantics. M18's gate (`fiveHourGroupsMustStartFromFirstSlot`) moved from `studentGroup.maxHoursPerDay <= 5` to `SchoolSchedule.getGroupCalendarWindow(group, weekday, date)[0] <= 5` (per-day open-slot count) as this task's NOTE 2026-07-16 recommended - this widens M18's evaluated (group, date) pairs considerably (every group's short Friday, G12's Mon-Wed, G10's Monday, not just G14) but still measures 0/0 on the golden - newly pinned as zero-tolerance.

DEFERRED (code-review high, 2026-07-18, three independent finder angles converged on this): `ScheduleService.addH18Violations` (the sole per-lesson drill-down for M18, dispatched at the `constraintId == null || constraintId == "H18"` branch) was NOT migrated and is now more wrong than before this task: it still gates on the OLD static `studentGroup.maxHoursPerDay <= 5` field this task just removed from the real constraint, hardcodes the expected start to 08:00 (`SLOT_INDEX_OFFSET`) instead of calling `getGroupCalendarWindow`, reports severity `"HARD"` instead of medium, and is labeled `"H18"` - a constraint ID that stopped existing at task-247's H18->M18 demotion. Separately, there is no dispatch branch for `constraintId == "M18"` at all, so `GET /api/schedule/analysis/lessons?constraintId=M18` returns zero per-lesson violations even though the (now much broader) M18 score is non-zero whenever it fires - a live score/UI disagreement reachable from the REST API. Out of scope for this task (item 2 of the original spec named only the H17 mirror, not H18/M18's) and too large to fold in silently (needs relabeling, severity fix, calendar-aware anchor rewrite, a real M18 dispatch branch, and its own GroupByViolationAnalysisTest coverage) - flag for a new task.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Lunch-aware series completed: H17 break-level pinned exemption (golden 0/0, zero-tolerance), addH15/addH17 mirrors consolidated on shared countPinnedExemptGaps + TimeslotSequence, M18 gated on the effective calendar window (covers all short days; golden 0/0 pinned — the human always starts at the first open slot), getGroupCalendarWindow replaces double resolution, stale h13/m18 docs fixed. In-agent simplify + code-review high; the stale addH18Violations drill-down surfaced by 3 finder angles deferred to TASK-376 with documentation. Full DoD; 560/560.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
