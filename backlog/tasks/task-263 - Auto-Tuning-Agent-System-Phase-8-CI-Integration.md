---
id: TASK-263
title: 'Auto-Tuning Agent System - Phase 8: CI Integration'
status: Done
assignee: []
created_date: '2026-03-17 05:29'
updated_date: '2026-03-17 08:02'
labels:
  - autotune
  - ci
  - regression
dependencies: []
documentation:
  - 'https://docs.github.com/en/actions'
  - >-
    https://docs.timefold.ai/timefold-solver/latest/quarkus/quarkus-integration/benchmarking
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Integrate auto-tuning with CI pipeline for regression detection and continuous benchmarking.

**Tasks:**
1. Create `scripts/autotune/regression-check.sh` - CI regression detection script
2. Create `.github/workflows/benchmark-regression.yml` - GitHub Actions workflow
3. Create `BaselineRegistry.java` - Track baseline performance over time
4. Create `RegressionDetector.java` - Alert on significant score degradation

**Acceptance Criteria:**
- [ ] CI runs fast benchmark (30s) on every PR
- [ ] Regression detector alerts if score degrades >10% from baseline
- [ ] BaselineRegistry stores historical scores in `target/autotune/baselines/`
- [ ] GitHub workflow posts benchmark summary as PR comment
- [ ] Failed benchmarks block merge (configurable)

**Key Files:**
- `.github/workflows/benchmark-regression.yml`
- `scripts/autotune/regression-check.sh`
- `src/main/java/com/school/planner/autotune/BaselineRegistry.java`

**Dependencies:** Phase 2 (Experiment Infrastructure)
<!-- SECTION:DESCRIPTION:END -->
