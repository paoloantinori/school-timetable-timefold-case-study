# Solver Debugging: Constraint Toggling

## Debug Mode

To test CH phase behavior with **zero constraints** (pure Construction Heuristic placement):

### Via System Property (Runtime)

```bash
java -Dsolver.constraints.debug.disabled=true -jar target/quarkus-app/quarkus-run.jar
```

### Via Environment Variable

```bash
export SOLVER__CONSTRAINTS__DEBUG_DISABLED=true
./planner.sh prod dev
```

### Via REST API

```bash
# Enable debug mode (disable all constraints)
./scripts/api/api.sh get /api/schedule/constraints/debug-mode?enabled=true

# Disable debug mode (restore normal constraints)
./scripts/api/api.sh get /api/schedule/constraints/debug-mode?enabled=false

# Check current mode
./scripts/api/api.sh get /api/schedule/constraints/debug-mode | jq
```

### Via application.properties

```properties
solver.constraints.debug.disabled=true
```

## What Debug Mode Does

When `debugConstraintsDisabled=true`:

- **ALL constraints are disabled** (zero constraints returned)
- No conflict detection (H1/H2/H3), no placement constraints (H4-H6, H7, etc.), no optimization (M/S)
- CH phase places entities with **zero score influence**
- The `ASCENDING` timeslot sorter naturally prefers early timeslots
- Resulting schedule **will have conflicts** (teacher in two places, room double-booked, etc.) - this is **expected and acceptable** for CH behavior verification
- Score will be **exactly 0hard/0medium/0soft** throughout

## Expected CH Behavior (Debug Mode)

With **zero constraints**:

1. **Phase 1 CH** (LabBlocks): Places blocks with zero consideration for:
   - Room conflicts (multiple blocks in same room)
   - Teacher conflicts (teacher in multiple places)
   - Student group conflicts

2. **Phase 2 CH** (Lessons): Assigns lessons to timeslots based solely on:
   - `DECREASING_DIFFICULTY` entity ordering (lessons with requiredRoomTags first)
   - `ASCENDING` timeslot sorter (earlier timeslots first)
   - **No penalty for ANY placement**

### Hypothesis

With the `ASCENDING` sorter on timeslots, CH should preferentially assign lessons to earlier dates (September → October → November...), creating a visible clustering pattern at the start of the school year.

### How to Verify

Run solver in debug mode, stop after CH Phase 2, check if lesson dates are heavily skewed toward September/October.

## Verification Steps

### 1. Test System Property Toggle

```bash
# Kill any running app
./planner.sh kill-app

# Start with debug mode enabled via system property
java -Dsolver.constraints.debug.disabled=true -jar target/quarkus-app/quarkus-run.jar > /tmp/planner-debug.log 2>&1 &

# Wait for app to be ready
./planner.sh wait

# Check logs for confirmation
grep "defineConstraints called in DEBUG mode" /tmp/planner-debug.log
```

### 2. Test API Toggle

```bash
# Enable via API
./scripts/api/api.sh get /api/schedule/constraints/debug-mode?enabled=true

# Verify
./scripts/api/api.sh get /api/schedule/constraints/debug-mode | jq

# Run solve and observe CH behavior
./scripts/api/api.sh solve
```

### 3. Verify CH Assigns Early Slots

After CH phase completes in debug mode:

```bash
# Check score is exactly zero
./scripts/api/api.sh score | jq
# Expected: {"hardScore":0,"mediumScore":0,"softScore":0}

# Get distribution of lesson dates
./scripts/api/api.sh get /api/schedule | jq -r '
  .lessons[] | select(.timeslot != null) | .timeslot.date
' | sort | uniq -c | sort -rn
```

**Expected output (if CH prefers early slots)**:
```
  1500 2025-09-10
  1200 2025-09-11
   900 2025-09-12
    ...
     10 2026-06-30
```

### 4. Compare Scores

| Mode | Expected Constraints | Score Behavior |
|------|---------------------|----------------|
| Normal | All constraints | High hard/medium/soft penalties (negative scores) |
| Debug | **ZERO constraints** | **Exactly 0hard/0medium/0soft** throughout |
| Pre-assignment | Base constraints only | Hard constraints active (negative hard score) |

**Key indicator**: In debug mode, score should remain at **exactly zero** after CH completes. Any non-zero score indicates the feature flag isn't working.

## Constraint Mode Priority

The `defineConstraints()` method checks modes in this order:

1. **Debug mode** (highest priority) - returns ZERO constraints
2. **Pre-assignment mode** - returns base constraints only
3. **Normal mode** (default) - returns all constraints

This means if debug mode is enabled, it overrides pre-assignment mode.

## Use Cases

### Pure CH Testing
Use debug mode to verify that the Construction Heuristic's `ASCENDING` timeslot sorter actually prefers early dates when unconstrained.

### Baseline Performance
Measure raw CH performance (time, entity placement rate) without constraint scoring overhead.

### Solver Debugging
Isolate constraint-related issues by temporarily disabling all constraints.

## Safety Notes

- **Debug mode produces invalid schedules** - do not use for production
- **Score is meaningless** in debug mode - all violations have zero weight
- **Conflicts are expected** - teachers double-booked, rooms double-booked
- **Only for testing/analysis** - disable immediately after verification

## Future Extensibility

The `debugConstraintsDisabled` flag can be evolved into:

- **Enum-based mode**: `FULL`, `DEBUG_ALL_OFF`, `DEBUG_MINIMAL` (H1/H2/H3 only), `DEBUG_HARD_ONLY`
- **Constraint group flags**: `disableHardConstraints`, `disableMediumConstraints`, `disableSoftConstraints`
- **Individual constraint toggles**: Constraint ID map for selective disabling

## Related Documentation

- `claudedocs/solver-phases.md` - 5-phase solver pipeline
- `claudedocs/rest-api.md` - REST API endpoints (including debug mode endpoints)
- `src/main/resources/solverConfig.xml` - Solver configuration
