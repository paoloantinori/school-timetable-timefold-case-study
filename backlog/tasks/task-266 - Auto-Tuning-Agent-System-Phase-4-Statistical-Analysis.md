---
id: TASK-266
title: 'Auto-Tuning Agent System - Phase 4: Statistical Analysis'
status: Done
assignee: []
created_date: '2026-03-17 05:28'
updated_date: '2026-03-17 08:03'
labels:
  - autotune
  - statistics
  - analysis
dependencies: []
documentation:
  - 'https://docs.scipy.org/doc/scipy/reference/stats.html'
  - 'https://arxiv.org/html/2303.00890v3'
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement statistical analysis for robust solver configuration comparison.

**Tasks:**
1. Create `StatisticalTests.java` - Implement Wilcoxon signed-rank, Friedman test
2. Create `ParetoFrontAnalyzer.java` - Identify non-dominated configurations
3. Create `EffectSizeCalculator.java` - Compute Cohen's d for practical significance
4. Create `ConfidenceIntervals.java` - Bootstrap CI for score distributions
5. Create `scripts/autotune/analyze-results.py` - Python statistical analysis script

**Acceptance Criteria:**
- [ ] Wilcoxon signed-rank test for pairwise config comparison (p < 0.05)
- [ ] Friedman test with Nemenyi post-hoc for multiple configs
- [ ] Pareto front visualization with matplotlib (3D: hard/soft/time)
- [ ] Cohen's d effect size calculation (small: 0.2, medium: 0.5, large: 0.8)
- [ ] Bootstrap confidence intervals (95%) for score distributions

**Key Files:**
- `src/main/java/com/school/planner/autotune/stats/StatisticalTests.java`
- `src/main/java/com/school/planner/autotune/stats/ParetoFrontAnalyzer.java`
- `src/main/java/com/school/planner/autotune/stats/EffectSizeCalculator.java`
- `scripts/autotune/analyze-results.py`

**Dependencies:** Phase 3 (Optuna Integration)
<!-- SECTION:DESCRIPTION:END -->
