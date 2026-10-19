# Auto-Tune Experiment

**Trigger:** User says "run experiment", "autotune experiment", "tune parameters", or requests to run solver tuning experiments.

## Overview

This agent executes solver configuration experiments, running benchmarks with different parameter combinations and collecting results for analysis.

## Usage

```bash
# Run random configuration search
/autotune-experiment random --trials 20

# Run grid search over specific parameters
/autotune-experiment grid --parameters entityTabuSize,acceptedCountLimit

# Run Latin hypercube sampling
/autotune-experiment lhs --samples 30

# Resume existing study
/autotune-experiment resume --study studies/tabu-tuning-2025-03-17.db
```

## Commands

### Random Search
Generate N random valid configurations and benchmark each:
```bash
/autotune-experiment random --trials 50 [--seed 42]
```

### Grid Search
Exhaustively test all combinations of specified parameters:
```bash
/autotune-experiment grid --parameters entityTabuSize,lateAcceptanceSize,acceptedCountLimit
```

### Latin Hypercube
Stratified sampling for space-filling coverage:
```bash
/autotune-experiment lhs --samples 20 [--seed 123]
```

### Genetic Operations
Mutate/crossover existing configurations:
```bash
/autotune-experiment mutate --config configs/best.json --rate 0.2
/autotune-experiment crossover --parent1 configs/a.json --parent2 configs/b.json
```

## Workflow

1. **Setup:**
   - Create `ExperimentDesign` with dataset splits
   - Initialize `TrialLogger` for this experiment
   - Set resource limits (timeout, memory)

2. **Generate Configurations:**
   - Use `ConfigSpaceGenerator` based on command type
   - Validate each config with `SolverConfigSchema.validate()`
   - Skip invalid configurations

3. **Run Benchmarks:**
   - For each configuration:
     - Render XML using `SolverConfigTemplate`
     - Execute benchmark via `BenchmarkExecutor`
     - Collect metrics via `MetricsCollector`
     - Log trial via `TrialLogger`

4. **Collect Results:**
   - Aggregate all trial results
   - Calculate summary statistics
   - Save to `target/autotune/experiments/<timestamp>/`

## Output

- **Trial log:** `target/autotune/experiments/<timestamp>/trials.jsonl`
- **Summary:** `target/autotune/experiments/<timestamp>/summary.json`
- **Best config:** `target/autotune/experiments/<timestamp>/best.xml`

## Example

```json
{
  "experimentId": "exp-20250317-120000",
  "type": "random",
  "trials": 50,
  "results": {
    "bestHardScore": -950,
    "bestSoftScore": -48000000,
    "avgTime": 145,
    "trialsCompleted": 50,
    "trialsFailed": 0
  }
}
```

## Related Skills

- `/improve-solver` - Full optimization pipeline
- `/autotune-analyze` - Analyze experiment results
