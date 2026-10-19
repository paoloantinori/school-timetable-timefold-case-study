# Fix: Background Pipe Chains Antipattern (2026-03-17)

## Problem

**Symptom**: Accumulating orphaned background processes that hang for 13+ hours.

**Root Cause**: Using `run_in_background=true` with commands containing pipes creates orphaned process chains.

### The Bad Pattern

```bash
# This was run with run_in_background=true:
./scripts/api/api.sh raw "/api/schedule/analysis/lessons?constraint=H17" 2>&1 | \
python3 -c "import sys, json; d=json.load(sys.stdin); print(f'✓ H17: {len(d)} violations')" && \
./scripts/api/api.sh raw "/api/schedule/analysis/lessons?constraint=H18" 2>&1 | \
python3 -c "import sys, json; d=json.load(sys.stdin); print(f'✓ H18: {len(d)} violations')"
```

**Result**: 4 orphaned processes hanging for 13+ hours.

### Why It Hangs

1. **Pipe buffering**: The downstream python3 waits for stdin EOF
2. **No timeout propagation**: API timeout (30s) doesn't kill the python3
3. **Parent abandonment**: Backgrounding means parent can't cleanup children
4. **Zombie accumulation**: Process repeats, creating more orphans

## Solution

### 1. Created Wrapper Script

**File**: `scripts/api/constraint-violations.sh`

```bash
# GOOD: Single script, no background pipes
./scripts/api/constraint-violations.sh H17 H18

# Or check all constraints
./scripts/api/constraint-violations.sh --all
```

**Features**:
- Health check before execution (fail fast)
- Server-side filtering (single API call per constraint)
- Built-in error handling
- No pipe chains

### 2. Created Protection Hook

**File**: `.claude/hooks/check-background-pipes.sh`

Automatically blocks dangerous patterns:
- Background commands with pipes (`|`)
- Background commands with chains (`&&`, `||`)

**Hook activates before** any Bash tool execution.

### 3. Documentation

**Files**:
- `.claude/rules/no-background-pipes.md` - Rule documentation
- This file - Fix summary

## Safe Patterns

| ✅ Safe | ❌ Unsafe |
|---------|----------|
| Synchronous pipes | Background pipes |
| `cmd \| python3` | `cmd \| python3 &` |
| Wrapper scripts | Direct background chains |
| Explicit timeouts | Implicit orphaning |
| `timeout 60 cmd` | `cmd &` (long-running) |

## Prevention

### For Claude (Bash tool)

The hook `.claude/hooks/check-background-pipes.sh` now blocks:
- `run_in_background=true` + pipes
- `run_in_background=true` + chains

### For Developers

```bash
# Check for orphaned processes
ps aux | grep -E "(bash|python3)" | \
  awk '$10 ~ /[0-9]+:[0-9]+/ && $10 !~ /:0[0-9]/ {print}'

# Clean them up (if any exist)
pkill -9 -f "api.sh.*python3"
```

## Lessons Learned

1. **Backgrounding is not async**: `&` doesn't make things async, it abandons them
2. **Pipes require parenting**: The downstream process needs its parent to clean up
3. **Timeouts don't propagate**: API timeout doesn't kill the pipe chain
4. **Wrapper scripts win**: For any multi-step operation, create a script

## Related Issues

- Fix needed: Review all `scripts/benchmark/*.sh` for similar patterns
- Fix needed: Consider adding PID tracking to `./planner.sh run`
- Fix needed: Add process cleanup to benchmark scripts

## Files Changed

| File | Purpose |
|------|---------|
| `scripts/api/constraint-violations.sh` | New safe wrapper |
| `.claude/hooks/check-background-pipes.sh` | Prevention hook |
| `.claude/rules/no-background-pipes.md` | Rule documentation |
| `claudedocs/antipattern-fix-background-pipes-20260317.md` | This file |
