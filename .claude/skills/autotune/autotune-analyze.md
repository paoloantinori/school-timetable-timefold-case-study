# Auto-Tune Analyze

**Trigger:** User says "analyze results", "analyze experiment", "compare configs", or requests analysis of autotune experiment results.

## Overview

This agent analyzes autotune experiment results, performing statistical analysis to identify significant improvements and generate actionable recommendations.

## Usage

```bash
# Analyze most recent experiment
/autotune-analyze

# Analyze specific experiment
/autotune-analyze --experiment target/autotune/experiments/exp-20250317-120000

# Compare two configurations
/autotune-analyze --compare configs/baseline.xml configs/tuned.xml

# Generate detailed report
/autotune-analyze --report --format html

# Show Pareto front
/autotune-analyze --pareto
```

## Analysis Workflow

### 1. Load Results
- Read trial log from `target/autotune/experiments/<experiment>/trials.jsonl`
- Parse all trial results into structured data
- Load baseline configuration for comparison

### 2. Statistical Analysis
For each configuration vs baseline:
- **Wilcoxon signed-rank test:** Statistical significance (p < 0.05)
- **Cohen's d:** Effect size (practical significance)
  - d < 0.2: negligible
  - 0.2 ≤ d < 0.5: small
  - 0.5 ≤ d < 0.8: medium
  - d ≥ 0.8: large

### 3. Multi-Objective Ranking
- Calculate scores for hard score, soft score, time
- Apply weights (hard=1.0, soft=0.5, time=0.2)
- Rank by composite score
- Identify Pareto-optimal configurations

### 4. Validation Check
- If holdout data available, validate top configs:
  - Check for overfitting (training >> holdout)
  - Cross-validation with k-fold (k=5)
  - Calculate confidence intervals (bootstrap)

### 5. Generate Report
Using `RecommendationGenerator`:
- **Summary:** Top recommendation with confidence level
- **Key Findings:** Statistical evidence, effect sizes
- **Trade-offs:** Pareto front analysis
- **Implementation Steps:** How to apply changes
- **Alternatives:** Other Pareto-optimal options

## Output

### Console Summary
```
Analysis of exp-20250317-120000 (50 trials)

Top Configuration:
- entityTabuSize: 11
- lateAcceptanceSize: 300
- cacheType: STEP
- Hard score: -950 (baseline: -1,185, +20%)
- Soft score: -48M (baseline: -52M, +8%)
- Time: 162s (baseline: 155s, +5%)

Statistical Significance:
- Wilcoxon p-value: 0.003 ✓ (< 0.05)
- Cohen's d: 0.82 (large effect)
- Confidence: HIGH - Strong evidence

Pareto Front (3 configs):
1. [tabu=11, late=300] - Best hard score
2. [tabu=7, late=500] - Best soft score
3. [tabu=5, late=200] - Fastest

Recommendation: Apply config #1 for best constraint satisfaction.
```

### HTML Report
- Full statistical analysis
- Score distribution plots
- Pareto front visualization
- Configuration comparison table

## Example Commands

```bash
# Quick analysis of latest experiment
/aututne-analyze

# Detailed report with visualizations
/autotune-analyze --report --format html --output reports/

# Compare specific configurations
/autotune-analyze --compare configs/baseline.xml configs/proposal-a.xml configs/proposal-b.xml

# Show only Pareto front
/autotune-analyze --pareto --verbose

# Check for overfitting
/autotune-analyze --check-overfitting --experiment exp-123
```

## Related Skills

- `/improve-solver` - Full optimization pipeline with analysis
- `/autotune-experiment` - Run experiments to analyze
