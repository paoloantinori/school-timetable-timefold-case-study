# Timefold Benchmark Progress Monitoring Research

**Date:** 2026-03-18
**Research Depth:** Exhaustive
**Query:** Timefold benchmark progress monitoring, SolverEventListener GitHub examples, custom benchmark runners, best practices

## Executive Summary

Timefold benchmarks provide progress monitoring through **built-in stdout logging** of phase completion messages. The benchmark XSD configuration does NOT support `solverEventListenerClass`, which means custom listeners must be implemented through alternative approaches: log parsing, Micrometer metrics, or custom benchmark runners.

## Key Findings

### 1. Built-in Progress Logging (Available)

Timefold benchmarks automatically log phase completion messages to stdout:

```
Construction Heuristic phase (0) ended: time spent (324560), best score (-50hard/-605323medium/302898soft)
Local Search phase (1) ended: time spent (900002), best score (-10hard/-40915medium/292622soft)
Solving ended: time spent (900002), best score (-10hard/-40915medium/292622soft)
```

**This is the standard, supported way to monitor benchmark progress.**

### 2. XSD Limitation Confirmed

The benchmark XSD schema does NOT include `<solverEventListenerClass>`:

```xml
<!-- NOT supported in benchmark configs -->
<solver>
  <solverEventListenerClass>com.example.MyListener</solverEventListenerClass>
</solver>
```

This is a **known limitation** of the benchmark framework. The XSD only supports:
- `<solutionClass>`
- `<entityClass>`
- `<scoreDirectorFactory>`
- `<termination>`
- `<constructionHeuristic>`
- `<localSearch>`

### 3. GitHub Research Results

**Repositories searched:**
- `TimefoldAI/timefold-solver-benchmarks` (Enterprise license required)
- `TimefoldAI/timefold-quickstarts`
- `TimefoldAI/timefold-solver`

**Finding:** No public examples of custom benchmark progress monitoring beyond the standard logging approach. All examples rely on the built-in phase completion messages.

## Recommended Approaches

### Option 1: Log Parsing (Recommended for this project)

Parse the Quarkus log file for phase completion messages:

**Pros:**
- No code changes required
- Works with standard Timefold benchmark configuration
- Real-time monitoring via `tail -f`

**Cons:**
- Log file format dependent
- Requires file system access

**Implementation:** `scripts/benchmark/benchmark-progress-log.sh` (created)

### Option 2: Micrometer Metrics (Production-ready)

Timefold supports Micrometer metrics for monitoring:

**Pros:**
- Industry-standard approach
- Integrates with Prometheus, Grafana
- No log parsing required

**Cons:**
- Requires additional infrastructure
- More complex setup

**Configuration:**
```xml
<solver>
  <metric>solvertime</metric>
  <metric>scorecalculationspeed</metric>
</solver>
```

### Option 3: Custom Benchmark Runner (Advanced)

Create a custom Java benchmark runner that uses `solveAndListen()`:

**Pros:**
- Full control over progress monitoring
- Can implement custom event handling

**Cons:**
- Loses the benefit of `PlannerBenchmarkFactory`
- Requires significant custom code
- Not using the benchmark framework as intended

**Example pattern:**
```java
SolverManager<Solution_, ID> solverManager = SolverManager.create(...);
SolverJob<Solution_, ID> job = solverManager.solve(1, problem);
job.getFinalBestScore(); // Blocking
```

## Benchmark Output Format

### Phase Completion Messages

| Component | Example | Description |
|-----------|---------|-------------|
| Phase name | `Construction Heuristic phase (0)` | CH always phase 0, LS is phase 1+ |
| Time spent | `time spent (324560)` | Milliseconds |
| Best score | `best score (-50hard/-605323medium/302898soft)` | HardMediumSoftScore format |

### Solver Completion Messages

```
Solving ended: time spent (900002), best score (-10hard/-40915medium/292622soft)
```

Indicates the solver completed (either by timeout or no progress).

## Implementation in This Project

### Created: `scripts/benchmark/benchmark-progress-log.sh`

A bash script that:
1. Polls `target/quarkus.log` every 2 seconds
2. Parses phase completion messages with regex
3. Formats output with colors and timing information
4. Tracks solver completion counts

**Usage:**
```bash
./scripts/benchmark/benchmark-progress-log.sh
```

**Sample output:**
```
[PHASE COMPLETE] Phase 0 (Construction Heuristic) | Time: 324s | Score: -50hard/-605323medium/302898soft
[PHASE COMPLETE] Phase 1 (Local Search) | Time: 900s | Score: -10hard/-40915medium/292622soft
[SOLVER COMPLETE] Solver #1 | Total time: 900s | Final score: -10hard/-40915medium/292622soft
```

### Regex Patterns Used

```bash
# Phase completion
([[^]]+) phase \(([0-9]+)\) ended: time spent \(([0-9]+)\), best score \(([^)]+)\)

# Solver completion
Solving ended.*time spent \(([0-9]+)\), best score \(([^)]+)\)
```

## Conclusions

1. **SolverEventListener in benchmarks is NOT supported** by the XSD schema
2. **Built-in logging is the intended monitoring mechanism** for benchmarks
3. **Log parsing is the most practical approach** for real-time progress monitoring
4. **No custom GitHub examples exist** because the standard logging is sufficient for most use cases
5. **For advanced monitoring**, consider Micrometer metrics or a custom runner outside the benchmark framework

## References

- Timefold Solver XSD: `https://timefold.ai/xsd/benchmark`
- Timefold Solver XSD: `https://timefold.ai/xsd/solver`
- Timefold Quickstarts: `https://github.com/TimefoldAI/timefold-quickstarts`
- Timefold Benchmarks (Enterprise): `https://github.com/TimefoldAI/timefold-solver-benchmarks`
- Micrometer Integration: Timefold documentation on metrics

## Files Modified

- `scripts/benchmark/benchmark-progress-log.sh` (created)
- `src/test/java/com/school/planner/benchmark/LocalSearchComparisonBenchmark.java` (existing)
- `src/main/resources/solverBenchmarkConfigTest.xml` (existing)
