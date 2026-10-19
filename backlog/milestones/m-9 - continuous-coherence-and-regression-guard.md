---
id: m-9
title: "Continuous Coherence and Regression Guard"
---
## Description

Continuous coherence: the user must notice IMMEDIATELY when (a) a data edit makes the current solution illegal, or (b) assigned and scheduled hours diverge (a number moved on one side only). Replicates and automates the Excel Classi reconciliation sheet and adds a mutation-triggered legality watchdog plus a permanent golden-solution regression suite against constraint drift.

Tasks: TASK-353 (reconciliation endpoint + dashboard), TASK-354 (legality watchdog), TASK-355 (golden regression test suite).

**Exit criteria:** editing a holiday onto a scheduled date surfaces the new violations in the UI without solving; any hour mismatch per (teacher, subject, group) is visible with a status flag; constraint drift breaks CI loudly.

**DoD OBBLIGATORIA per ogni task**: /simplify + /code-review high (verifica Opus, fix dei confermati) + verifica finale con exit code dall'orchestratore. Nessun task è Done senza.
