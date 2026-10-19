# Timefold VRP Identity Bug Fix

**Date**: 2026-03-06
**Timefold Version**: 1.31.0 (patched)
**Type**: Bug fix via patching upstream dependency

## Quick Fix

```bash
./scripts/ensure-timefold-patch.sh
```

This script automatically clones Timefold, applies the patch, builds, and installs to your local Maven repo.

---

## ★ Insight ─────────────────────────────────────

**Why patching beats working around**: Spent 6-8 hours trying complex workarounds (normalization, caching, re-loading) - all failed. The root cause was a design contradiction in Timefold itself. Patching the source took 30 minutes and fixed it completely.

**The key insight**: Value range deduplication uses `.equals()` to identify equivalent ranges, but validation uses `==` (IdentityHashSet). When your planning entities have `.equals()`-but-not-`==` values (common with JPA), this contradiction blocks legitimate scenarios.

─────────────────────────────────────────────────

## Problem Description

### What Happens

**Symptom**: Solver crashes with:
```
IllegalStateException: The value (Room@456) has been assigned to the entity (Lesson@789),
but it is outside of the related value range [Room@123, Room@234, ...].
```

**When it occurs**:
- At solver startup (Construction Heuristic → Local Search transition)
- Only when using **filtering value range providers** (e.g., `@FilteringValueRangeProvider`)
- Only when using **pre-assignment** with JPA entities
- The crash happens because:
  1. Pre-assignment persists `Lesson` with `Room` instance from DB query #1
  2. Solver loads schedule and gets `Room` instance from DB query #2
  3. Both have the same `id`, but are different Java objects (`!=`)

### Why It Happens

Timefold 1.31.0 introduced **value range deduplication** (PR #1900) to save memory. When two entities have "equal" value ranges (by `.equals()`), Timefold redirects entity B to use entity A's cached range.

But for user-defined types (JPA entities), Timefold uses `IdentityHashSet` (`==`) for containment checks. So:

```
Entity A: room = Room@123 (canonical room list)
Entity B: room = Room@456 (same id as @123, but different object)

Value range deduplication: "Ranges are equal by .equals(), redirect B → A's cache"
Value range cache: [Room@123]
Validation: Room@456 in [Room@123]? → IdentityHashSet.contains() → FALSE → CRASH
```

---

## Root Cause Analysis

### The Contradiction

| Phase | Method | Comparison | Result |
|-------|--------|------------|--------|
| Deduplication | `HashedValueRange.equals()` | `Room.equals()` → `room.id.equals()` | ✅ Equal |
| Validation | `ValueRangeCache.contains()` | `IdentityHashSet.contains()` → `room1 == room2` | ❌ Not equal |

**The bug**: Timefold trusts `.equals()` for deduplication but doesn't trust it for validation.

### Source Code (Timefold 1.31.0)

**File**: `ValueRangeCache.java` (lines 109-116)

```java
public enum Builder {
    FOR_USER_VALUES {  // Used for Room, Timeslot (user-defined types)
        public <Value_> ValueRangeCache<Value_> buildCache(int size) {
            return new ValueRangeCache<>(size,
                CollectionUtils.newIdentityHashSet(size), false);  // ← PROBLEM
        }
    }
}
```

The fix: Change `newIdentityHashSet(size)` to `newHashSet(size)`.

---

## Patch Details

### One-Line Change (2 locations)

**File**: `ValueRangeCache.java` (Timefold 1.31.0)

```diff
     public <Value_> ValueRangeCache<Value_> buildCache(int size) {
-        return new ValueRangeCache<>(size, CollectionUtils.newIdentityHashSet(size), false);
+        return new ValueRangeCache<>(size, CollectionUtils.newHashSet(size), false);
     }
```

### Why This Works

- JPA entities correctly implement `.equals()` by ID field
- If two `Room` instances have the same `id`, they ARE the same room
- `HashSet` uses `.equals()`, so `Room@456` is found in `[Room@123]` (same id)

### Risk Assessment

**What if users implement `.equals()` incorrectly?**
- Cache may have duplicate entries (e.g., two `Room` objects with same ID but different names)
- This is a **user error**, not a framework bug
- The current design is paternalistic - penalizing correct implementations to protect against incorrect ones

**Timefold's Javadoc says**:
> "For types where we can trust that equals() means that if two objects are equal, they are the same object. It is not guaranteed to be the case for user-defined types."

**Our position**: This is wrong. Well-designed JPA entities (like Panache entities) DO have correct `.equals()` implementations. The framework should trust them.

---

## Build Instructions

### Step 1: Clone Timefold Source

```bash
cd /tmp
git clone https://github.com/TimefoldAI/timefold-solver.git
cd timefold-solver
git checkout v1.31.0
```

### Step 2: Apply Patch

```bash
# Edit core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java
# Line 109 and 114: Change newIdentityHashSet(size) to newHashSet(size)

# Or use sed:
sed -i 's/newIdentityHashSet(size)/newHashSet(size)/g' \
  core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java
```

### Step 3: Build and Install

```bash
# Build the core module only (faster)
cd core
mvn clean install -DskipTests

# Output: ~/.m2/repository/ai/timefold/solver/timefold-solver-core/1.31.0/timefold-solver-core-1.31.0.jar
```

### Step 4: Verify Installation

```bash
# Check the JAR was installed
ls -lh ~/.m2/repository/ai/timefold/solver/timefold-solver-core/1.31.0/

# Verify in your project
mvn dependency:tree | grep timefold-solver-core
```

### Step 5: Rebuild Your Project

```bash
# Maven will pick up the patched JAR from local repo
mvn clean package
```

---

## Workarounds Considered (All Failed)

### Attempt 1: Normalize to Canonical Instances

**Approach**: Before solving, replace all entity Room/Timeslot references with canonical instances from the value range list.

**Why it failed**:
- Filtering VRPs recompute the range on each access
- No single "canonical list" exists - it's filtered per entity
- Pre-assignment caches the "before filter" range, so canonical instances don't match

### Attempt 2: Override `equals()` to Use Identity

**Approach**: Make `Room.equals()` use `this == other` instead of `id.equals()`.

**Why it failed**:
- Breaks Hibernate dirty checking (requires `.equals()` by ID)
- Breaks `HashSet` deduplication everywhere else in the app
- Violates JPA entity best practices

### Attempt 3: Disable Value Range Deduplication

**Approach**: Find a config option to disable VRP deduplication.

**Why it failed**:
- No such config option exists in Timefold 1.31.0
- Deduplication is hardcoded in `ValueRangeState`

### Attempt 4: Use `@PlanningId` with Manual Caching

**Approach**: Cache filtered ranges per entity and invalidate carefully.

**Why it failed**:
- Extremely complex (3-layer fix)
- 6-8 hours of development time
- Still hit the identity check bug at phase transitions

---

## Maintenance

### When Upgrading Timefold

1. **Try the vanilla version first**
   ```bash
   # Update pom.xml to new version
   mvn clean package
   # Test if the bug still exists
   ```

2. **If bug still exists, re-apply patch**
   ```bash
   cd /tmp/timefold-solver
   git fetch
   git checkout v<NEW_VERSION>
   sed -i 's/newIdentityHashSet(size)/newHashSet(size)/g' \
     core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java
   cd core && mvn clean install -DskipTests
   ```

3. **Update this document**
   - Change "Timefold Version" at top
   - Add note about new version in "Maintenance" section

### Tracking

- **Bug report filed**: `claudedocs/timefold-bug-report-value-range-identity-bug.md`
- **Upstream issue**: [Timefold GitHub Issue](https://github.com/TimefoldAI/timefold-solver/issues) (to be filed)
- **Original research**: See session transcript `485f42d6-9cb3-4f19-81c7-d27592cc92ef.jsonl`

---

## References

- **Bug Report**: `claudedocs/timefold-bug-report-value-range-identity-bug.md` - Detailed analysis with minimal reproducible case
- **Upgrade Guide**: `claudedocs/timefold-upgrade-guide.md` - Step-by-step upgrade instructions
- **Source**: [Timefold Solver v1.31.0 on GitHub](https://github.com/TimefoldAI/timefold-solver/tree/v1.31.0)
- **PR #1900**: [Improve memory use of entity value ranges](https://github.com/TimefoldAI/timefold-solver/pull/1900) - Introduced the deduplication feature

---

## Status

✅ **FIXED IN UPSTREAM** - PR #2111 "refactor: value range cleanup" (merged Feb 13, 2026)
🔗 **Upstream fix**: https://github.com/TimefoldAI/timefold-solver/commit/2f99a1d72b
🐛 **Issue tracked**: https://github.com/TimefoldAI/timefold-solver/issues/2172
📦 **1.31.0 status**: Bug still present (released Feb 11, 2 days before fix merged)
⏱️ **Time to patch**: 30 minutes (vs 6-8 hours on failed workarounds)

### Timeline

| Date | Event |
|------|-------|
| Feb 11, 2026 | Timefold 1.31.0 released (WITH bug) |
| Feb 13, 2026 | PR #2111 merged (fixes bug) |
| Mar 6, 2026 | Issue #2172 created, discovered upstream fix |

### Options Going Forward

1. **Wait for next release** (likely 1.32.0) - unknown timeline
2. **Continue using patched 1.31.0** - working solution
3. **Request 1.31.1 backport** - ask Timefold team for bugfix release
