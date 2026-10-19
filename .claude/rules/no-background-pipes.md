# Rule: No Background Pipe Chains

## Problem

Using `run_in_background=true` with commands containing pipes creates **orphaned processes** that hang forever:

```bash
# BAD: Orphaned pipe chain - will hang forever
./scripts/api/api.sh raw "/api/path" | python3 -c "..."
```

**Why it hangs:**
1. The downstream process (python3) waits for stdin
2. If upstream is slow or blocks, the pipe backs up
3. Backgrounding means the parent can't clean up the children
4. Processes accumulate over time

## Solution

### Option 1: Use wrapper scripts (Preferred)

Create a dedicated script that handles the entire operation:

```bash
# GOOD: Script with built-in timeout and cleanup
./scripts/api/constraint-violations.sh H17 H18
```

### Option 2: Synchronous execution only

Never background commands with pipes. Run them synchronously:

```bash
# GOOD: Synchronous - will timeout properly
./scripts/api/api.sh raw "/api/path" | python3 -c "..."
```

### Option 3: Use subshells with explicit timeouts

If you must background, use a subshell with its own timeout:

```bash
# RISKY: Only if absolutely necessary
(timeout 60 bash -c './scripts/api/api.sh raw "/api/path" | python3 -c "..."') &
```

## Patterns to Avoid

| Pattern | Problem | Fix |
|---------|---------|-----|
| `cmd \| python3 &` | Orphaned pipe | Use wrapper script |
| `cmd1 && cmd2 &` | Chained background | Run synchronously |
| `long-running &` | No cleanup | Use proper service/daemon |
| `mvn quarkus:run &` | Parent hangs | Use `./planner.sh run &` |

## Safe Background Operations

Only background these types of operations:

1. **Service startup** (with PID tracking):
   ```bash
   ./planner.sh run > /tmp/planner.log 2>&1 &
   echo $! > /tmp/planner.pid
   ```

2. **Fire-and-forget tasks** (no pipes, no chains):
   ```bash
   ./scripts/cleanup-temp-files.sh &
   ```

3. **With explicit timeout** (subshell pattern):
   ```bash
   (timeout 300 ./long-task.sh) &
   ```

## Detection

Check for orphaned processes:
```bash
# Find bash/python processes older than 1 hour
ps aux | grep -E "(bash|python3)" | awk '$10 ~ /[0-9]+:[0-9]+/ && $10 !~ /:0[0-9]/ {print}'

# Find zombie pipes
lsof | grep -v "txt" | grep "PIPE"
```

## Enforce in Code

When using Bash tool with background:
- ❌ Never use pipes (`|`) in backgrounded commands
- ❌ Never use chains (`&&`, `||`) in backgrounded commands
- ✅ Use wrapper scripts instead
- ✅ Add explicit timeouts to any background operation
