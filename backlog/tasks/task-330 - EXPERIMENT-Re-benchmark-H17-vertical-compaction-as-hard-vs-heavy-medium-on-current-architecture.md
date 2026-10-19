---
id: TASK-330
title: >-
  EXPERIMENT: Re-benchmark H17 vertical compaction as hard vs heavy-medium on
  current architecture
status: In Progress
assignee: []
created_date: '2026-07-12 17:46'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-4
dependencies: []
references:
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
  - claudedocs/research/semantically-hard-soft-constraints-2026-03-07.md
priority: high
ordinal: 154000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: H17 (student schedules must have zero vertical gaps) was promoted from medium M13 to HARD in TASK-227 because medium was not strong enough. But the projects own benchmark (claudedocs/research/semantically-hard-soft-constraints-2026-03-07.md) had shown hard-tier compaction produced a 40 percent WORSE overall hard score and 76s slower CH - hard compaction freezes the search landscape. That trade-off was measured on a dead branch and NEVER re-tested after LabBlock removal, the 6-phase pipeline, RuinRecreate and Timefold 2.x. H17 being hard is plausibly the single biggest source of score-trap rigidity today. Absorbs TASK-161 (re-benchmark H13/H17). See roadmap doc sections 2 (item 2) and 3.

WHAT:
1. Variants: (a) control = H17 hard weight 1 per gap-slot (current); (b) H17 as MEDIUM with heavy weight (e.g. 100000 per gap-slot - must dominate all other medium contributors, check M20 magnitudes from TASK-320 first); (c) H17 medium with moderate weight 10000.
2. Implementation: change the penalize call in the H17 constraint (SchoolScheduleConstraintProvider) from ONE_HARD-based to ofMedium-based per variant. Check first whether Timefold 2.x ConstraintWeightOverrides can express a tier change without code edits (probably not - tier is in code, weight is overridable); if overrides only change magnitude, do variant switching via a code flag consistent with the existing debugConstraintsDisabled/compactionOnlyMode pattern.
3. IMPORTANT analysis rule: when H17 is medium, the raw hard score is not comparable across variants. Compare (i) conflict-hard subscore = sum of H0,H1,H2,H3,H5,H6,H7,H11 violations, (ii) total H17 gap count regardless of tier, (iii) avg lessons/day per group, (iv) M20 sparse days. The compare script from TASK-319 gives per-constraint breakdowns - use those, not the headline score.
4. Two protocol runs per variant; append to experiment-log.md.
5. Decision: if a medium-tier H17 achieves equal-or-fewer H17 gaps AND fewer conflict-hard violations, demote H17 and update CLAUDE.md constraint table + ConstraintVerifier tests; otherwise keep hard and close the question permanently with numbers.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Three variants run twice each through the standardized protocol
- [ ] #2 Comparison done on conflict-hard subscore, H17 gap count, avg lessons/day and M20 - not headline score
- [ ] #3 experiment-log.md updated; tier decision applied consistently (constraint code, tests, CLAUDE.md table) or control kept
- [ ] #4 ConstraintVerifier tests pass in the final state
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 18:57
---
REFOCUS 2026-07-14 (in progress): The baseline (TASK-320/323) and the TASK-324 negative result show the hard debt is M22 Calendar compactness (-85, 6 matches) + H4.3 (-20) + H4 (-10). H17 (Vertical compaction) contributes 0 to the current hard score. So this task's original target (H17 hard-vs-medium) is MOOT; the real target is M22. Running the hard-vs-heavy-medium re-benchmark on M22 instead. (If relaxing M22 to heavy-medium removes the -85 from hard, the solver gains substantial freedom - the most targeted hard lever identified so far.)
---

created: 2026-07-14 20:33
---
RESULT 2026-07-14 (variant: M22 HARD->MEDIUM weight 1000, 1 rep): hard-excl-M22 = -20 vs control -25 (M22 changed tier, headline hard not comparable). The entire small gain is H4 Stage blackout ELIMINATED (-10 -> 0; H4 is M22-coupled). H4.3 Professionalizzanti UNCHANGED at -20 (20 matches) - structural, independent of M22 tier. conflict-hard 0 (no new conflicts). Medium regressed ~112k (-622k vs -510k; M22 now -92k medium for 92 gaps). VERDICT: MARGINAL, not worth it -> revert M22 to HARD. KEY FINDING: the remaining hard debt is H4.3 (professionalizzanti single-day during stage), structural. This resolves the compaction-tier question (M22-relaxation doesn't help H4.3) and, combined with TASK-324 (R&R refuted), points away from search-tuning and toward the professionalizzanti constraint/rotation (TASK-279). M22-medium Java change left uncommitted pending revert. Task's question (compaction hard-vs-medium) is now ANSWERED (keep hard); formal close pending the revert + user direction.
---
<!-- COMMENTS:END -->
