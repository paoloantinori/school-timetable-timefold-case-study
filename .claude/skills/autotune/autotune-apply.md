# Auto-Tune Apply

**Trigger:** User says "apply recommendation", "apply config", "use tuned config", or requests to apply a saved autotune recommendation.

## Overview

This agent applies validated solver configuration recommendations to the project, with proper backup, validation, and rollback capabilities.

## Usage

```bash
# Apply most recent recommendation
/autotune-apply

# Apply specific recommendation
/autotune-apply --recommendation target/autotune/recommendations/rec-20250317-120000.json

# Apply configuration from file
/autotune-apply --config target/autotune/best-config.xml

# Preview changes without applying
/autotune-apply --preview --recommendation rec-123

# Validate before applying
/autotune-apply --validate --recommendation rec-123
```

## Workflow

### 1. Load Recommendation
- Read recommendation JSON from `target/autotune/recommendations/`
- Extract recommended configuration parameters
- Check validation status (should already be validated)

### 2. Safety Checks
- **Backup:** Create `solverConfig.xml.backup` if not exists
- **Validation:** Verify config with `SolverConfigSchema.validate()`
- **Diff:** Show changes compared to current configuration

### 3. Apply Configuration
- **Option A:** Apply to main config (`src/test/resources/solverConfig.xml`)
- **Option B:** Create new solver config variant (e.g., `solverConfig-tuned.xml`)
- **Option C:** Update benchmark config only (keep existing configs)

### 4. Validate Application
- Rebuild project to ensure XML is valid
- Run quick smoke test benchmark
- Verify solver starts without errors

### 5. Rollback on Failure
If application fails:
- Restore from `solverConfig.xml.backup`
- Report error details
- Keep backup for analysis

## Pre-Apply Preview

```
Recommended Configuration:
=========================
entityTabuSize: 11 (was: 5)
lateAcceptanceSize: 300 (was: 200)
cacheType: STEP (was: PHASE)
acceptedCountLimit: 2 (was: 1)

Expected Improvements:
====================
Hard score: +20% (from -1,185 to -950)
Soft score: +8% (from -52M to -48M)
Time: +5% (from 155s to 162s)

Statistical Evidence:
==================
Wilcoxon p-value: 0.003
Cohen's d: 0.82 (large)
Validation: PASSED on holdout set

Apply this configuration? [Y/n/q] _
```

## Post-Apply Actions

After successful application:

1. **Update related files** (if needed):
   - `claudedocs/solver-phases.md`
   - `claudedocs/solver-configurations.md`

2. **Run validation benchmark:**
   ```bash
   mvn test -Dtest=SchoolScheduleBenchmark
   ```

3. **Create git commit** (if approved):
   ```
   git add src/test/resources/solverConfig.xml
   git commit -m "feat(autotune): Apply optimized solver configuration

   - entityTabuSize: 5 → 11
   - lateAcceptanceSize: 200 → 300
   - cacheType: PHASE → STEP
   - Hard score: +20% improvement
   - Soft score: +8% improvement
   - Validated on holdout set (p=0.003, d=0.82)

   Based on autotune study exp-20250317-120000 (50 trials)
   ```

4. **Track in memory:**
   - Save recommendation ID to project memory
   - Note performance improvement for future reference

## Options

### --preview
Show diff without applying:
```
Current → Recommended
entityTabuSize: 5 → 11
lateAcceptanceSize: 200 → 300
cacheType: PHASE → STEP
```

### --validate
Run validation on holdout set before applying:
- Re-run benchmarks on test dataset
- Confirm no regression
- Check statistical significance still holds

### --config
Apply configuration from XML file directly:
- Bypasses recommendation system
- User takes responsibility for validation

### --backup
Specify custom backup location:
```bash
/autotune-apply --backup backups/solverConfig-before-tuning.xml
```

## Error Handling

| Error | Action |
|-------|--------|
| Invalid XML | Abort, restore backup, show parse error |
| Validation fails | Show errors, ask to continue anyway |
| Benchmark crashes | Rollback, save crash log for analysis |
| No improvement | Warn, but allow user to proceed |

## Example Session

```
User: /autotune-apply

Agent: Found recommendation: rec-20250317-143000

Configuration Changes:
----------------------
- entityTabuSize: 5 → 11
- lateAcceptanceSize: 200 → 300
- cacheType: PHASE → STEP
- acceptedCountLimit: 1 → 2

Expected Results:
----------------
Hard score: +20% (-1,185 → -950)
Soft score: +8% (-52M → -48M)
Time: +5% (155s → 162s)

Validation: ✓ PASSED (p=0.003, d=0.82 on holdout)

Apply to solverConfig.xml? [Y/n/preview/diff] Y

[Creating backup: solverConfig.xml.backup]
[Updating solverConfig.xml...]
[Validating XML...]
[Running smoke test...]
[SUCCESS]

Configuration applied successfully!

Next steps:
1. Run full benchmark: mvn test -Dtest=SchoolScheduleBenchmark
2. Test with real data: ./planner.sh start test
3. Commit changes when satisfied

Git commit these changes? [Y/n] Y

[Git commit created: feat(autotune): Apply optimized solver configuration...]

Done! Your solver is now using the optimized configuration.
```

## Safety Features

1. **Always backup** before modifying `solverConfig.xml`
2. **XML validation** before writing
3. **Rollback capability** - one command restore
4. **User approval** required for git commits
5. **Detailed logging** of all changes

## Rollback Command

If you need to undo applied changes:

```bash
# Restore from backup
cp src/test/resources/solverConfig.xml.backup src/test/resources/solverConfig.xml

# Or use this skill
/autotune-apply --rollback
```

## Related Skills

- `/improve-solver` - Full pipeline with automatic application
- `/autotune-analyze` - Analyze before applying
