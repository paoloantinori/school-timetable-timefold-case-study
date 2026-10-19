# Timefold Benchmarking Guide

## Overview

This project includes a lightweight benchmarking framework for systematic performance testing of Timefold solver configurations.

**★ Insight ─────────────────────────────────────**
The benchmarking framework provides:
- **A/B Testing**: Compare solver configurations (baseline vs. fast vs. quality)
- **Regression Detection**: Track performance over time with baseline comparisons
- **Fast Feedback**: Minimal test dataset (12 lessons) completes in <2 minutes
**─────────────────────────────────────────────────**

## Quick Start

### Run Benchmarks

```bash
# Fast mode (30s) - CH only
./scripts/benchmark/run-benchmark.sh --fast

# Baseline mode (30s) - main solverConfig.xml
./scripts/benchmark/run-benchmark.sh

# Quality mode (2min) - full pipeline
./scripts/benchmark/run-benchmark.sh --full
```

### Output

```
=== Running Timefold Benchmark ===
Mode: fast
Run time: 30 seconds
Runs: 1
Config: benchmark/solverConfig-fast.xml

Run 1/1...
  Score: 0

=== Benchmark Results ===
Best:   0
Worst:  0
Average: 0
```

## Benchmark Configurations

### Baseline (default)

**Purpose:** Regression baseline with main configuration

- **Duration:** 30 seconds
- **Config:** `solverConfig.xml` (5-phase pipeline)
- **Phases:** Day-clustered CH → Fallback CH → Compactness LS → Conflict LS → Room LS
- **Test:** `SchoolScheduleBenchmark#baselineBenchmark`

### Fast

**Purpose:** Quick validation (CH only, no LS)

- **Duration:** 30 seconds
- **Config:** `benchmark/solverConfig-fast.xml`
- **Phases:** Day-clustered CH → Fallback CH (no local search)
- **Test:** `SchoolScheduleBenchmark#fastBenchmark`
- **Trade-off:** Lower solution quality, faster execution

### Quality

**Purpose:** Thorough optimization testing

- **Duration:** 2 minutes
- **Config:** `benchmark/solverConfig-quality.xml`
- **Phases:** Complete 5-phase pipeline with shorter termination
- **Test:** `SchoolScheduleBenchmark#qualityBenchmark`
- **Target:** Near-production solution quality

## Test Data

The benchmark uses a **minimal feasible dataset** to ensure fast, reproducible results:

| Entity | Count | Description |
|--------|-------|-------------|
| Lessons | 12 | 4 per teacher, 6 per group |
| Teachers | 3 | Teacher A, B, C |
| Student Groups | 2 | 1A (20), 1B (20) |
| Rooms | 3 | Room 1, 2, 3 (30 capacity each) |
| Timeslots | 6 | Mon/Tue, 8-10am (3 slots per day) |

**Utilization:** ~67% (12 lessons / 18 total slots)

**Design:** Low conflict potential (different teachers/groups per lesson)

## Direct Maven Execution

Run benchmarks directly via Maven:

```bash
# Baseline
mvn test -Dtest=SchoolScheduleBenchmark#baselineBenchmark

# Fast
mvn test -Dtest=SchoolScheduleBenchmark#fastBenchmark

# Quality
mvn test -Dtest=SchoolScheduleBenchmark#qualityBenchmark

# Custom duration (60 seconds)
mvn test -Dtest=SchoolScheduleBenchmark#baselineBenchmark \
  -Dbenchmark.seconds=60

# Multiple runs (for statistics)
mvn test -Dtest=SchoolScheduleBenchmark#baselineBenchmark \
  -Dbenchmark.runs=5
```

## Interpreting Results

### Score Format

Timefold uses **HardMediumSoftScore**:

```
Best:   0hard/-50medium/-500soft
Worst:  0hard/-55medium/-520soft
Average: 0hard/-52medium/-510soft
```

- **Hard Score:** Must be ≥ 0 (negative = infeasible)
- **Medium Score:** Compaction violations (lower is better)
- **Soft Score:** Preference violations (lower is better)

### What to Look For

| Metric | Good | Bad |
|--------|------|-----|
| Hard Score | 0 | < 0 (infeasible) |
| Best vs Worst | Close (<5% diff) | Large gap (unstable) |
| Run-to-run variance | Low | High (random seed issues) |

## Troubleshooting

### Static Pollution Errors

**Symptom:** Tests fail with unexpected constraint violations

**Fix:** The `@AfterEach` hook clears static state:

```java
@AfterEach
void tearDown() {
    SchoolSchedule.clearStaticState();
}
```

### Slow Benchmarks

**Target:** <30 seconds for fast/baseline, <2 min for quality

**Diagnosis:**
```bash
# Check if TerminationSpentLimit is working
grep -A 5 "termination" src/main/resources/benchmark/solverConfig-*.xml
```

**Fixes:**
- Verify `secondsSpentLimit` is set correctly
- Check for infinite loops in custom phases
- Reduce `benchmark.seconds` system property

### Infeasible Solutions

**Symptom:** Hard score < 0

**Diagnosis:** Check constraint violations in solver logs

**Fixes:**
- Add more timeslots/rooms to `createProblem()`
- Reduce lesson count
- Review constraint definitions

## CI/CD Integration

### GitHub Actions

```yaml
name: Benchmark

on:
  pull_request:
    paths:
      - 'src/main/java/**'
      - 'src/main/resources/**.xml'

jobs:
  benchmark:
    runs-on: ubuntu-latest
    timeout-minutes: 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run benchmark
        run: ./scripts/benchmark/run-benchmark.sh --fast
```

### Regression Detection

To implement automated regression detection:

1. **Save baseline:**
   ```bash
   ./scripts/benchmark/run-benchmark.sh --fast > baseline.txt
   # Extract and save best score to claudedocs/benchmark/baseline.json
   ```

2. **Compare in CI:**
   ```bash
   ./scripts/benchmark/run-benchmark.sh --fast > current.txt
   # Compare scores against baseline
   ```

3. **Fail on regression:**
   ```bash
   if (( current_score > baseline_score * 1.10 )); then
     echo "Regression detected!"
     exit 1
   fi
   ```

## Advanced Usage

### Custom Problem Datasets

Modify `createProblem()` for specific scenarios:

```java
// High utilization (90%)
private SchoolSchedule createHighUtilizationProblem() {
    // Add more lessons, fewer timeslots
    return createProblem(16); // 16 lessons for 18 slots
}

// Many teachers
private SchoolSchedule createManyTeacherProblem() {
    // Add more teachers, spread lessons across them
    // ...
}
```

### Benchmarking Specific Constraints

Create a custom constraint provider:

```java
// In test/resources: benchmark-constraints.xml
<constraintProviderClass>com.school.planner.benchmark.BenchmarkConstraintProvider</constraintProviderClass>

// Create BenchmarkConstraintProvider with subset of constraints
public class BenchmarkConstraintProvider extends SchoolScheduleConstraintProvider {
    @Override
    public Constraint defineConstraints(ConstraintFactory factory) {
        // Only enable specific constraints for this benchmark
        return Stream.of(
            teacherConflict(factory),
            roomConflict(factory)
        ).collect(ConstraintComposer::compose);
    }
}
```

## Architecture

### File Structure

```
src/test/java/com/school/planner/benchmark/
└── SchoolScheduleBenchmark.java    # JUnit benchmark tests

src/main/resources/benchmark/
├── solverConfig-fast.xml            # Fast config (CH only)
└── solverConfig-quality.xml         # Quality config (full pipeline)

scripts/benchmark/
├── run-benchmark.sh                # Benchmark runner script
└── check-regression.py             # Regression detection (placeholder)

claudedocs/
└── benchmarking.md                 # This file
```

### Static State Management

**Critical:** `SchoolSchedule.clearStaticState()` must be called between runs

```java
@BeforeEach
void setUp() {
    SchoolSchedule.clearStaticState();
}

@AfterEach
void tearDown() {
    SchoolSchedule.clearStaticState();
}
```

**Why:** Timefold reuses the same JVM process. Static fields retain values between runs.

## References

- **Timefold Docs:** https://timefold.ai/docs/
- **Project Constraints:** `claudedocs/solver-phases.md`
- **Solver Config:** `src/main/resources/solverConfig.xml`
- **Task:** TASK-233 (benchmark framework implementation)

---

**Last Updated:** 2026-03-16
**Status:** ✅ Implemented
