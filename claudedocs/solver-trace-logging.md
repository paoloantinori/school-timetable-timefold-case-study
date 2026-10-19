# Solver TRACE Logging

## Overview

TRACE logging provides detailed insight into Timefold solver behavior:
- Constraint evaluation counts and timing
- Move selector performance
- Phase transitions and step behavior
- Score calculation breakdown
- Shadow variable updates

**WARNING:** TRACE logging has significant performance overhead (~10-20% slower solving). Only enable during debugging, never in production.

## How to Enable

### Option 1: Environment Variable (Recommended)

```bash
# Enable TRACE logging for this session
SOLVER_TRACE=true ./planner.sh start <profile>

# Or export for all commands
export SOLVER_TRACE=true
./planner.sh start <profile>
```

### Option 2: Direct Quarkus Property

Edit `src/main/resources/application.properties`:
```properties
quarkus.log.category."ai.timefold.solver".level=TRACE
```

**Note:** This permanently enables TRACE. Not recommended for commits.

## What You'll See

### Normal Logging (INFO level)
```
2026-03-16 15:50:00 INFO  Solver started
2026-03-16 15:50:05 INFO  Construction Heuristic phase complete
```

### TRACE Logging Enabled
```
2026-03-16 15:50:00 INFO  Solving started
2026-03-16 15:50:00 TRACE Solving started: solver config
2026-03-16 15:50:00 TRACE Construction Heuristic phase (0) started
2026-03-16 15:50:00 TRACE   Step index (0), time spent (0ms), score (0hard/-0medium/-0soft)
2026-03-16 15:50:00 TRACE   Phase (0), step (1), time spent (5ms), score (-120hard/-500medium/-800soft)
2026-03-16 15:50:00 TRACE   Phase (0), step (2), time spent (3ms), score (-110hard/-450medium/-750soft)
...
2026-03-16 15:50:05 TRACE Construction Heuristic phase (0) ended
2026-03-16 15:50:05 TRACE Local Search phase (1) started
...
```

## Interpreting TRACE Output

### Step Log Format
```
TRACE   Phase (PHASE_ID), step (STEP), time spent (MS), score (HARD/MEDIUM/SOFT)
```

- **Phase ID**: Which solver phase (0=CH, 1=LS Compactness, 2=LS Conflict, etc.)
- **Step**: Incremental step counter within phase
- **Time spent**: Milliseconds for this step
- **Score**: Current best score (negative is better for constraints)

### Key Patterns

**Fast improvement (good):**
```
TRACE   Step (0), time (5ms), score (-100hard/-400soft)
TRACE   Step (1), time (6ms), score (-500hard/-800soft)  ← Big jump
```

**Stuck or slow (investigate):**
```
TRACE   Step (100), time (5ms), score (-1000hard/-1200soft)
TRACE   Step (101), time (5ms), score (-1000hard/-1200soft)  ← No change
TRACE   Step (102), time (5ms), score (-1000hard/-1200soft)  ← Stuck!
```

**Slow step (investigate constraint):**
```
TRACE   Step (50), time (500ms), score (-1500hard/-2000soft)  ← Very slow!
```

## Performance Impact

| Logging Level | Relative Speed | Memory Usage |
|--------------|---------------|--------------|
| INFO (default) | 100% (baseline) | Low |
| DEBUG | ~95% | Medium |
| TRACE | ~80-90% | High |

## Configuration

### In application.properties
```properties
# Default: INFO level (fast, minimal output)
quarkus.log.category."ai.timefold.solver".level=INFO

# Dev mode: Override via environment variable
%dev.quarkus.log.category."ai.timefold.solver".level=${SOLVER_TRACE:INFO}
```

### Per-Package Logging (Advanced)
For even more detailed logging, trace specific subsystems:
```properties
# Trace only constraint evaluation
quarkus.log.category."ai.timefold.solver.constraint.streams".level=TRACE

# Trace move selector
quarkus.log.category."ai.timefold.solver.localsearch".level=TRACE

# Trace construction heuristic
quarkus.log.category."ai.timefold.solver.constructionheuristic".level=TRACE
```

## When to Use TRACE Logging

✅ **Use TRACE when:**
- Debugging why solver is stuck
- Investigating slow step performance
- Understanding constraint evaluation order
- Verifying phase transitions work correctly
- Developing new constraints

❌ **Do NOT use TRACE when:**
- Running production solves
- Benchmarking (inflates results)
- Normal development (INFO is sufficient)
- Pre-assignment (no solver involved)

## Troubleshooting

### Too Much Output
Log to file instead of console:
```bash
SOLVER_TRACE=true ./planner.sh start <profile> 2>&1 | tee solver-trace.log
```

### Find Slow Constraints
```bash
# Capture TRACE output
SOLVER_TRACE=true ./planner.sh start <profile> 2>&1 | tee solver-trace.log

# Find slow steps (>100ms)
grep "time spent ([1-9][0-9][0-9]ms" solver-trace.log
```

## See Also

- [Timefold Logging Documentation](https://timefold.ai/docs/timefold-solver/latest/logging)
- [Solver Configuration Guide](../solver-configurations.md)
- TASK-234 - Original task description
