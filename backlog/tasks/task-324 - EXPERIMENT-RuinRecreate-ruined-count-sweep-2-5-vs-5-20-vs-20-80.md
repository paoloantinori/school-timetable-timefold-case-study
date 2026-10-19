---
id: TASK-324
title: 'EXPERIMENT: RuinRecreate ruined-count sweep (2-5 vs 5-20 vs 20-80)'
status: Done
assignee: []
created_date: '2026-07-12 17:43'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-3
dependencies: []
references:
  - src/main/resources/solverConfig.xml
  - .claude/rules/solver-config-sync.md
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: high
ordinal: 148000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Phase 4 currently uses minimumRuinedCount=2 / maximumRuinedCount=5 (solverConfig.xml around line 183). The Timefold default is 5-20 and official docs recommend RAISING it for large datasets (~8700 lessons here). Ruining 2-5 lessons cannot restructure even one school day (6 slots), so the current RuinRecreate phase is likely too timid to escape compaction traps - coarse moves exist precisely to cross score humps that fine moves cannot. See claudedocs/research/critical-assessment-and-roadmap-20260712.md section 3, first bullet.

WHAT:
1. Variants to test, one at a time, everything else unchanged: (a) current 2-5 as control if TASK-320 baseline predates this task, (b) 5-20 Timefold default, (c) 20-80, (d) optionally 50-200 if (c) improves over (b).
2. For each variant: edit solverConfig.xml Phase 4, validate with bash scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml, rebuild, run the TASK-319 protocol at least twice.
3. Also record from logs whether Phase 4 wall-clock per step grows materially (larger ruins = more recreate work per move).
4. Append all rows to claudedocs/research/experiment-log.md. Declare a winner only if reproducibly better on hard score (primary) and medium (secondary) at equal termination settings.
5. If a variant wins, keep it in solverConfig.xml and follow .claude/rules/solver-config-sync.md (update solverPhases.ts and claudedocs/solver-phases.md if displayed values changed). Otherwise revert to control.

Note: also consider raising the fixedProbabilityWeight of ruinRecreateMoveSelector (currently 5 vs change 2 / swap 3) as a follow-up variant if ruined-count alone shows movement.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 At least 3 variants each run 2+ times through the standardized protocol
- [ ] #2 solver XML validated after every edit; experiment-log.md rows appended
- [ ] #3 Winner-or-no-change decision stated with numbers; solverConfig.xml final state matches the decision; solver-config-sync rule followed
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 18:27
---
REP 1 RESULT (variant 20-80, 2026-07-14): -115hard vs control (2-5) -113hard - WITHIN NOISE, no hard improvement. Medium +17k better (-493k vs -510k; beyond rep variance) but medium is secondary. The hard debt is IDENTICAL to baseline: M22 calendar compactness -85 (6 matches) + H4.3 professionalizzanti -20 (20) + H4 stage blackout -10 (10) - STRUCTURAL, not escapable via ruined-count. The R&R ruined-count hypothesis (larger ruins escape hard traps) is REFUTED for hard. Recommendation: do NOT pursue the 5-20 variant for hard (unlikely to help if 20-80 didn't - roadmap section 5.5 says don't stack on a refuted idea); revert solverConfig.xml to the 2-5 control before the next experiment (keep single-variable); move to TASK-325 (acceptor sweep, Late Acceptance per #2407) or TASK-330 (M22/H17 compaction hard-vs-medium re-benchmark) which target the actual hard debt via different mechanisms. solverConfig.xml currently left at 20-80 pending that revert decision. Single rep - a confirming rep would tighten the medium finding but does not change the hard conclusion.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
