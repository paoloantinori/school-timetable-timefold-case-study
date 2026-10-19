---
id: TASK-265
title: 'Auto-Tuning Agent System - Phase 3: Optuna Integration'
status: Done
assignee: []
created_date: '2026-03-17 05:28'
updated_date: '2026-03-17 08:03'
labels:
  - autotune
  - optuna
  - optimization
dependencies: []
documentation:
  - 'https://optuna.readthedocs.io/'
  - >-
    https://optuna.readthedocs.io/en/stable/tutorial/20_recipes/002_multi_objective.html
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Integrate with Optuna optimization engine for automated parameter search.

**Tasks:**
1. Create `OptunaBridge.java` - CLI wrapper to communicate with Optuna studies
2. Create `ObjectiveFunction.java` - Wrapper that runs benchmark and returns metrics
3. Create `StudyManager.java` - Create/resume Optuna studies with persistence
4. Create `scripts/autotune/optuna-study.py` - Python Optuna study script
5. Create `scripts/autotune/run-trial.sh` - Shell wrapper for single trial execution

**Acceptance Criteria:**
- [ ] Optuna study runs N trials with multi-objective optimization (hard/soft/time)
- [ ] ObjectiveFunction executes benchmarks and returns JSON metrics
- [ ] Studies persist to `target/autotune/studies/` for resume capability
- [ ] Parallel trial execution supported (up to CPU count)

**Key Files:**
- `src/main/java/com/school/planner/autotune/optuna/OptunaBridge.java`
- `src/main/java/com/school/planner/autotune/optuna/ObjectiveFunction.java`
- `scripts/autotune/optuna-study.py`
- `scripts/autotune/run-trial.sh`

**Dependencies:** Phase 2 (Experiment Infrastructure)
<!-- SECTION:DESCRIPTION:END -->
