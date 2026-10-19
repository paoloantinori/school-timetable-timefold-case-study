---
id: m-3
title: "Solver Quick Wins - config-level experiments"
---

## Description

Cheap, high-potential experiments on the existing pipeline - config and small code changes only, each A/B-tested against the m-2 baseline via the standardized protocol. Rationale: claudedocs/research/critical-assessment-and-roadmap-20260712.md section 3.

Tasks: TASK-323 (Timefold 2.3.0 upgrade), TASK-324 (RuinRecreate ruined-count sweep), TASK-325 (acceptor sweep: tabu vs Late Acceptance vs SA), TASK-326 (termination policy + long-run curve), TASK-327 (constraint-stream throughput).

Recommended order: 323 first (everything after runs on the upgraded solver), then 324/325 (highest expected leverage on the stuck-solver symptom), then 326, 327.

**Exit criteria:** every variant has experiment-log.md rows with a keep/revert verdict; solverConfig.xml reflects only reproducibly-better winners; the score-vs-time curve says whether termination truncates improvement.
