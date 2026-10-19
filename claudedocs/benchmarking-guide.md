# Timefold Benchmarking Guide

## Overview

This project uses the **Timefold Solver Benchmarking Framework** to systematically compare solver performance across different configurations, code changes, and constraint modifications.

## Quick Start

### Run a Quick Smoke Test (Fast Mode)
```bash
./scripts/benchmark/run-benchmark.sh --fast
```
- **Duration:** ~30 seconds per solver configuration
- **Use case:** CI smoke tests, quick performance checks
- **Output:** `target/benchmark/index.html`

### Run a Quality Benchmark (Thorough)
```bash
./scripts/benchmark/run-benchmark.sh --full
```
- **Duration:** ~2 minutes per solver configuration
- **Use case:** Solver tuning, thorough performance analysis
- **Output:** `target/benchmark/index.html`

### Run with Regression Detection (CI Mode)
```bash
./scripts/benchmark/run-benchmark.sh --fast --ci
```
- Runs fast benchmark + regression detection
- Fails if scores deviate >10% from baseline

## Benchmark Configurations

### 1. Fast Configuration (30s)
For quick smoke tests and CI pipelines.
- Time limit: 30 seconds
- Unimproved limit: 10 seconds
- Use when: Rapid feedback needed

### 2. Quality Configuration (2min)
For thorough solver tuning and optimization.
- Time limit: 2 minutes
- Unimproved limit: 30 seconds
- Use when: Evaluating solver changes

### 3. Baseline Configuration (5min)
For production baseline establishment.
- Time limit: 5 minutes (Phase 3)
- Full production solver configuration
- Use when: Establishing performance baselines

## Understanding the HTML Report

### Summary Statistics

| Statistic | Description |
|-----------|-------------|
| **Best Score Summary** | Score achieved per solver config (primary comparison metric) |
| **Best Score Distribution** | Reliability of solver (lower variance = more consistent) |
| **Score Calculation Speed** | Constraints per second (affects solver performance) |
| **Move Evaluation Speed** | Moves evaluated per second (affects solver efficiency) |

### Solver Ranking

The report automatically ranks solver configurations:
- **Rank 0** (Favorite) - Best overall performance, recommended for production
- Rankings are based on **Total Score** by default

### Key Metrics

| Metric | Good | Bad |
|--------|------|-----|
| **Hard Score** | Closer to 0 (e.g., -10) | More negative (e.g., -1000) |
| **Soft Score** | Higher (less negative) | More negative |
| **Score Speed** | Higher (>100k/sec) | Lower (<50k/sec) |

## Statistical Benchmarking

Each solver configuration runs **3 times** by default to provide:
- **Best score** - Best outcome across runs
- **Worst score** - Worst outcome across runs
- **Average score** - Mean performance
- **Standard deviation** - Score consistency

Lower standard deviation = more reliable solver.

## Parallel Benchmarking

Benchmarks run in parallel on multiple CPU cores (`parallelBenchmarkCount=AUTO`):
- Faster overall benchmark execution
- Leave some processors unused for garbage collection
- Results may vary slightly due to CPU contention

## Creating Custom Benchmarks

### Method 1: Modify benchmarkConfig.xml

Add a new `<solverBenchmark>` section:

```xml
<solverBenchmark>
  <name>My Custom Config</name>
  <solver>
    <!-- Your solver configuration here -->
    <termination>
      <secondsSpentLimit>60</secondsSpentLimit>
    </termination>
    <!-- Phases, moves, acceptors... -->
  </solver>
  <problemBenchmarks>
    <solutionFileIOClass>com.school.planner.persistence.SchoolScheduleSolutionFileIO</solutionFileIOClass>
  </problemBenchmarks>
</solverBenchmark>
```

### Method 2: Use Freemarker Templates (Matrix Benchmarking)

Test multiple parameter combinations:

```xml
<#list [5, 7, 11, 13] as entityTabuSize>
  <#list [500, 1000, 2000] as acceptedCountLimit>
    <solverBenchmark>
      <name>Tabu Size ${entityTabuSize} - Limit ${acceptedCountLimit}</name>
      <solver>
        <localSearch>
          <acceptor>
            <entityTabuSize>${entityTabuSize}</entityTabuSize>
            <lateAcceptanceSize>${acceptedCountLimit}</lateAcceptanceSize>
          </acceptor>
        </localSearch>
      </solver>
      <!-- ... -->
    </solverBenchmark>
  </#list>
</#list>
```

Build with:
```java
PlannerBenchmarkFactory.createFromFreemarkerXmlResource("benchmarkConfig.xml");
```

## Regression Detection

### Automated CI Checks

The `check-regression.py` script compares benchmark results against a baseline:

```bash
./scripts/benchmark/run-benchmark.sh --fast --ci
```

**Exit codes:**
- `0` - No regression detected
- `1` - Regression detected (score degraded >10%)

### Manual Comparison

1. Run benchmark before code changes:
   ```bash
   ./scripts/benchmark/run-benchmark.sh --fast
   cp -r target/benchmark target/benchmark-before
   ```

2. Make code changes

3. Run benchmark after changes:
   ```bash
   ./scripts/benchmark/run-benchmark.sh --fast
   cp -r target/benchmark target/benchmark-after
   ```

4. Compare reports manually in browser

## Benchmark Data Sets

### Built-in Datasets

The `SchoolScheduleBenchmark` class creates two datasets:

| Dataset | Size | Use Case |
|---------|------|----------|
| `smallDataset` | 2 lessons, 2 days, 2 slots/day | Fast smoke tests |
| `mediumDataset` | 10 lessons, 3 days, 6 slots/day | Quality benchmarks |

### Custom Datasets

To use production-scale data:

1. Export from database via API:
   ```bash
   ./scripts/api/api.sh export > benchmark-dataset.json
   ```

2. Add to `benchmarkConfig.xml`:
   ```xml
   <inputSolutionFile>benchmark-dataset.json</inputSolutionFile>
   ```

3. Run benchmark:
   ```bash
   mvn test -Dtest=SchoolScheduleBenchmark
   ```

## Programmatic Benchmark Execution

### In Java Code

```java
// Create benchmark factory from XML
PlannerBenchmarkFactory factory = PlannerBenchmarkFactory.createFromXmlResource(
    "benchmarkConfig.xml");

// Build benchmark with custom datasets
SchoolSchedule dataset = createDataset();
PlannerBenchmark benchmark = factory.buildPlannerBenchmark(dataset);

// Run and show report
benchmark.benchmarkAndShowReportInBrowser();
```

### With Custom Configuration

```java
// Create config programmatically
PlannerBenchmarkConfig config = new PlannerBenchmarkConfig();
config.setBenchmarkDirectory(new File("target/benchmark"));
config.setParallelBenchmarkCount(ParallelBenchmarkCount.AUTO);

// Build and run
PlannerBenchmarkFactory factory = PlannerBenchmarkFactory.create(config);
PlannerBenchmark benchmark = factory.buildPlannerBenchmark();
benchmark.benchmarkAndShowReportInBrowser();
```

## Common Issues

### Issue: Benchmark hangs or runs forever
**Cause:** Solver termination not configured properly
**Fix:** Ensure `<termination>` with `secondsSpentLimit` is set in solver config

### Issue: OutOfMemoryError during benchmark
**Cause:** Dataset too large or parallel benchmark count too high
**Fix:**
- Reduce dataset size
- Lower `parallelBenchmarkCount` to 1 or 2
- Increase JVM heap: `export MAVEN_OPTS="-Xmx4g"`

### Issue: Scores are all zeros
**Cause:** Constraints not firing or entity initialization failed
**Fix:** Check constraint provider configuration and verify entities are initialized

### Issue: Large variance in scores
**Cause:** Random seed or non-deterministic solver behavior
**Fix:** Increase `subSingleCount` for more statistical runs

## Best Practices

1. **Always establish a baseline** before making solver changes
2. **Use fast mode for CI**, quality mode for local development
3. **Run 3+ times** (subSingleCount) for statistical reliability
4. **Compare best AND average scores** - best can be lucky, average reflects typical performance
5. **Monitor score calculation speed** - sudden drops may indicate constraint bugs
6. **Keep benchmark reports** for historical comparison
7. **Use matrix benchmarking** when tuning solver parameters

## Performance Guidelines

| Use Case | Duration | Parallel | Runs |
|----------|----------|----------|------|
| CI Smoke Test | 30s | AUTO | 1 |
| Local Development | 2min | AUTO | 3 |
| Production Baseline | 5min | AUTO | 3 |
| Solver Tuning | 2-5min | AUTO | 5+ |

## Further Reading

- [Timefold Benchmarking Documentation](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking)
- [Solver Configuration Guide](claudedocs/solver-phases.md)
- [Constraint Best Practices](CLAUDE.md#canonical-constraint-ids)
