---
id: TASK-264
title: 'Auto-Tuning Agent System - Phase 2: Experiment Infrastructure'
status: Done
assignee: []
created_date: '2026-03-17 05:28'
updated_date: '2026-03-17 08:03'
labels:
  - autotune
  - experiment
  - infrastructure
dependencies: []
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking
  - 'https://automl.github.io/SMAC3/v2.0.1/'
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the experiment execution infrastructure with proper isolation, logging, and metrics collection.

**Tasks:**
1. Create `ExperimentDesign.java` - Implement train/validation/test split for datasets
2. Create `BenchmarkExecutor.java` - Run benchmarks with proper isolation and resource limits
3. Create `TrialLogger.java` - Record all trial metadata (seeds, timestamps, scores)
4. Create `MetricsCollector.java` - Extract PAR10, time-to-feasible, time-to-best from benchmark reports

**Acceptance Criteria:**
- [ ] ExperimentDesign creates stratified splits (70% train, 15% validation, 15% test)
- [ ] BenchmarkExecutor runs benchmarks in isolation with resource limits
- [ ] TrialLogger records all metadata to JSON for reproducibility
- [ ] MetricsCollector extracts key metrics from benchmark HTML reports

**Key Files:**
- `src/main/java/com/school/planner/autotune/ExperimentDesign.java`
- `src/main/java/com/school/planner/autotune/BenchmarkExecutor.java`
- `src/main/java/com/school/planner/autotune/TrialLogger.java`
- `src/main/java/com/school/planner/autotune/MetricsCollector.java`

**Dependencies:** Phase 1 (Configuration Space Definition)
<!-- SECTION:DESCRIPTION:END -->
