# Improve Solver

**Trigger:** User says "improve solver", "tune solver", "optimize solver", or similar requests to automatically improve the Timefold solver configuration.

## Overview

This agent orchestrates the full autotune pipeline to automatically discover and apply better solver configurations through systematic experimentation.

## Workflow

### 1. Analysis Phase (Current State Assessment)
- Load current solver configuration from `src/test/resources/solverConfig.xml`
- Run quick baseline benchmark (5-10 trials) on current configuration
- Identify key metrics: hard score, soft score, execution time
- Check recent solver runs for performance bottlenecks

### 2. Design Phase (Experiment Planning)
- Select target datasets for tuning:
  - Use `DatasetCatalog` to get available datasets
  - Create train/validation split (70%/30%) using `DatasetSplitter`
- Define optimization objectives:
  - Primary: minimize hard score (fewest constraint violations)
  - Secondary: minimize soft score (best solution quality)
  - Tertiary: minimize execution time
- Configure search space using `SolverConfigSchema`:
  - Termination: spentLimit (30-600s), unimprovedLimit (10-120s)
  - Acceptor: entityTabuSize (3-13), lateAcceptanceSize (100-1000)
  - Forager: acceptedCountLimit (1-10)
  - Cache: JUST_IN_TIME, STEP, PHASE, SOLVER
  - Selection: ORIGINAL, SORTED, PROBABILISTIC

### 3. Optimization Phase (Parameter Search)
- Initialize Optuna study with multi-objective optimization
- Run N trials (default: 50, user-configurable with `--trials N`)
- Each trial:
  1. Generate configuration using `ConfigSpaceGenerator`
  2. Render solver config XML using `SolverConfigTemplate`
  3. Execute benchmark via `BenchmarkExecutor`
  4. Collect metrics via `MetricsCollector`
  5. Log trial data via `TrialLogger`
- Apply early stopping if no improvement after 10 trials
- Monitor progress, log intermediate results every 5 trials

### 4. Validation Phase (Holdout Testing)
- Select top 3 configurations from Pareto front
- Validate each on holdout dataset (unseen during tuning)
- Run statistical tests:
  - Wilcoxon signed-rank for significance (p < 0.05)
  - Cohen's d for effect size (practical significance)
- Check for overfitting (training vs holdout performance gap)
- Cross-validation with k-fold (k=5) for stability assessment

### 5. Recommendation Phase (Results Synthesis)
- Generate recommendation report using `RecommendationGenerator`:
  - Top configuration with confidence level
  - Key findings with statistical evidence
  - Trade-offs between objectives
  - Implementation steps
- Display Pareto front visualization
- Create comparison report of all tested configurations

### 6. Implementation Phase (Apply Changes)
- **IMPORTANT:** Ask user for approval before modifying `solverConfig.xml`
- If approved:
  1. Backup current `solverConfig.xml` to `solverConfig.xml.backup`
  2. Apply recommended configuration
  3. Run full validation benchmark
  4. Verify improvement on holdout set
  5. Commit changes with detailed message
- If rejected:
  - Save recommendation to `target/autotune/recommendations/`
  - Provide diff showing proposed changes

## Usage Examples

```bash
# Basic usage (50 trials, default settings)
/improve-solver

# Specify number of trials
/improve-solver --trials 100

# Use specific dataset
/improve-solver --dataset medium

# Focus on single objective (speed over quality)
/improve-solver --objective time

# Quick exploration (10 trials for testing)
/improve-solver --trials 10 --quick

# Resume previous study
/improve-solver --resume studies/tabu-tuning-2025-03-17
```

## Output Artifacts

- **Study data:** `target/autotune/studies/<timestamp>-study.db`
- **Trial logs:** `target/autotune/trials/<timestamp>-trials.jsonl`
- **Recommendation report:** `target/autotune/reports/<timestamp>-recommendation.html`
- **Backup config:** `src/test/resources/solverConfig.xml.backup` (if applied)

## Error Handling

- **Benchmark failures:** Log error, continue with next trial, flag in report
- **Configuration errors:** Validate with `SolverConfigSchema.validate()` before running
- **Resource exhaustion:** Detect CPU/memory limits, pause trials, warn user
- **Statistical issues:** If no significant improvement found, report "no conclusive evidence"

## User Interaction Points

1. **Start:** Confirm experiment design (datasets, trials, objectives)
2. **Mid-experiment:** Show progress after every 10 trials, ask if continue
3. **Pre-apply:** Show recommendation summary, ask for approval to apply
4. **Post-apply:** Confirm improvement, offer to commit changes

## Dependencies

- `SolverConfigSchema` - Parameter space definition
- `ConfigSpaceGenerator` - Configuration generation
- `SolverConfigTemplate` - XML rendering
- `BenchmarkExecutor` - Benchmark execution
- `MetricsCollector` - Result parsing
- `TrialLogger` - Metadata logging
- `ConfigValidator` - Holdout validation
- `StatisticalTests` - Significance testing
- `ConfigurationRanker` - Multi-objective ranking
- `RecommendationGenerator` - Report generation

## Success Criteria

1. User says "improve solver" → agents handle full pipeline automatically
2. Recommendations are statistically validated (p < 0.05, confidence intervals)
3. Pareto front shows trade-offs between hard/soft/time objectives
4. User approval required before modifying any files
5. Full workflow completes in reasonable time (50 trials ≈ 2-4 hours)

## Example Session

```
User: improve solver

Agent: I'll help you automatically discover a better solver configuration.

Analysis Phase:
- Current config: baseline (tabu=5, lateAcceptance=200, spent=300s)
- Baseline metrics: hard=-1,185, soft=-52.1M, time=155s

Design Phase:
- Datasets: medium (train=70%, validation=30%)
- Objectives: minimize hard score, soft score, time (multi-objective)
- Search space: 11 parameters, ~10^6 combinations
- Trials: 50 (estimated time: 2-3 hours)

Proceed? [Y/n] Y

Optimization Phase:
[████████████████████████████████████████] 50/50 trials (2h 15m)
Best so far: tabu=11, lateAcceptance=300 → hard=-950, soft=-48M

Validation Phase:
- Top 3 configs validated on holdout set
- Config #3: p=0.003 (significant), Cohen's d=0.82 (large effect)

Recommendation Phase:
RECOMMENDED: tabuSize=11, lateAcceptanceSize=300, cacheType=STEP
- Hard score: +19% improvement (p=0.003)
- Soft score: +8% improvement
- Time: +5% slower (acceptable trade-off)

Apply this configuration to solverConfig.xml? [Y/n] Y

Implementation Phase:
- Backup created: solverConfig.xml.backup
- Configuration applied
- Validation benchmark: CONFIRMED improvement

Commit changes? [Y/n] Y
[git commit with detailed message]

Done! Your solver is now optimized.
```

## Related Skills

- `/autotune-experiment` - Run individual experiments
- `/autotune-analyze` - Analyze existing results
- `/autotune-apply` - Apply saved recommendation
