# Timefold Bug Report: ValueRangeCache IdentityHashSet Contradiction

**Date**: 2026-03-06  
**Version**: Timefold Solver Community Edition 1.31.0  
**Severity**: High (blocks solver initialization)  
**Type**: Design contradiction between deduplication and validation

## Summary

`ValueRangeCache` uses `IdentityHashSet` (`==`) for containment checks on user-defined types, but value range deduplication (`HashedValueRange`) uses `.equals()` to match ranges. When entity instances differ by identity but match by `.equals()`, validation fails with `IllegalStateException: The value has been assigned to the entity, but it is outside of the related value range.`

This blocks legitimate solver scenarios where:
- Pre-assignment persists entities with values from one JPA transaction
- Solver loads entities with values from another transaction  
- Both have logically equal values (same `id`) but different object instances

## Minimal Reproducible Case

```java
@Entity
public class PlanningEntity extends PanacheEntityBase {
    @PlanningId
    public Long id;
    
    @PlanningVariable(valueRangeProviderRefs = {"rooms"})
    public Room room;
}

@Entity  
public class Room extends PanacheEntityBase {
    public Long id;
    public String name;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room)) return false;
        return id != null && id.equals(((Room) o).id);
    }
    
    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}

@PlanningSolution
public class Solution {
    @EntityId
    private Long id = 1L;
    
    @PlanningEntityCollectionProperty
    private List<PlanningEntity> entities = new ArrayList<>();
    
    @ValueRangeProvider(id = "rooms")
    private List<Room> rooms = new ArrayList<>();
    
    // ... getters and setters
}

// ============================================
// Bug reproduction scenario:
// ============================================

// Step 1: Create solution with pinned entity
Solution solution1 = new Solution();
Room roomFromDB = new Room(); roomFromDB.id = 1L; roomFromDB.name = "Room A";
solution1.rooms.add(roomFromDB);

PlanningEntity entity = new PlanningEntity(); entity.id = 1L;
entity.room = roomFromDB; // Assigned from DB-loaded room
entity.pinned = true;
solution1.entities.add(entity);

// Step 2: Simulate JPA transaction boundary (persistence)
// After persist(), we load a new Solution instance
Solution solution2 = new Solution();

// Load canonical room list from static cache (same DB, new instances)
Room canonicalRoom = new Room(); canonicalRoom.id = 1L; canonicalRoom.name = "Room A";
solution2.rooms.add(canonicalRoom);

// Load entity from DB (has room reference from when it was persisted)
PlanningEntity loadedEntity = new PlanningEntity(); loadedEntity.id = 1L;
loadedEntity.room = roomFromDB; // Same Room by equals(), different object by ==
loadedEntity.pinned = true;
solution2.entities.add(loadedEntity);

// Step 3: Start solver
// Timefold validates: loadedEntity.room is in solution2.rooms value range?
// ValueRangeCache contains canonicalRoom (IdentityHashSet)
// loadedEntity.room == canonicalRoom? FALSE (different objects)
// loadedEntity.room.equals(canonicalRoom)? TRUE (same id)
// Result: IllegalStateException - value outside value range
solverManager.solveBuilder()
    .withProblemId(1L)
    .withProblemFinder(id -> solution2)
    .run(); // THROWS IllegalStateException
```

## Expected Behavior

Validation should use `.equals()` for containment checks, not `==`. If `entity.room.equals(roomInList)` returns `true`, the value should be considered in range.

## Actual Behavior

```java
// ValueRangeCache.java (Timefold 1.31.0)
public enum Builder {
    FOR_USER_VALUES {  // Used for Room, Timeslot (user-defined types)
        public <Value_> ValueRangeCache<Value_> buildCache(int size) {
            return new ValueRangeCache<>(size,
                CollectionUtils.newIdentityHashSet(size), false);  // ← PROBLEM: uses ==
        }
    }
}

// During solver startup, AbstractScoreDirector asserts:
public void assertValueRangeForBasicVariables(Object value) {
    if (!valueRangeCache.contains(value)) {  // ← Uses IdentityHashSet.contains()
        throw new IllegalStateException("...value is outside of the related value range...");
    }
}
```

## Root Cause Analysis

### The Chain of Causation

1. **ValueRangeState deduplicates ranges using `equals()`**:
   ```java
   // ValueRangeState.java (v1.31.0)
   private Entity_ findEntityMatch(Entity_ entity, CountableValueRange<Value_> valueRange) {
       HashedValueRange<Value_> hashedValueRange = HashedValueRange.of(valueRange);
       Entity_ fromEntity = valueRangeDeduplicationCache.get(hashedValueRange);
       // HashedValueRange.equals() uses ListValueRange.equals() → Room.equals()
       // If two entities have rooms with same id, their ranges are "equal"
   }
   ```

2. **Deduplication redirects entity B to use entity A's value range**:
   ```java
   if (fromEntity == null) {
       valueRangeDeduplicationCache.put(hashedValueRange, entity);
       return null;
   }
   return fromEntity;  // Redirect B → A's cached ValueRange
   ```

3. **ValueRangeCache uses `IdentityHashSet` for containment**:
   ```java
   // Entity A's ValueRangeCache was built with IdentityHashSet
   // It contains Entity A's Room instance
   Entity A: assigned room = Room@123
   ValueRangeCache: [Room@123]
   
   // Entity B is redirected to A's range, but has different Room instance
   Entity B: assigned room = Room@456 (same id as @123, different object)
   ValueRangeCache.contains(Room@456) → FALSE (IdentityHashSet uses ==)
   ```

4. **Validation fails**:
   ```java
   // AbstractScoreDirector.java
   if (!valueRangeCache.contains(entity.room)) {  // Room@456 not in [Room@123]
       throw new IllegalStateException("...value is outside of the related value range...");
   }
   ```

### Contradiction

- **Deduplication**: Two value ranges are "equal" if `List.equals()` → `Room.equals()` → `room.id.equals()`
- **Validation**: A value is "in range" if `IdentityHashSet.contains()` → `room1 == room2` (same object)

These two criteria are **contradictory** for user-defined types where `.equals()` compares identity but `==` checks object reference.

## Affected Versions

- **Confirmed**: Timefold Solver Community Edition 1.31.0
- **Code path**: `ValueRangeCache.Builder.FOR_USER_VALUES` (used for non-immutable user types)
- **Immunity**: `FOR_TRUSTED_VALUES` types (String, Integer, LocalDate, etc.) use `HashSet` with `.equals()`

## Workaround

```java
// Normalize all planning entity value references to canonical instances before solving
Map<Long, Room> roomById = rooms.stream().collect(toMap(r -> r.id, r -> r));
for (PlanningEntity entity : entities) {
    if (entity.room != null) {
        entity.room = roomById.getOrDefault(entity.room.id, entity.room);
    }
}

// Clear VRP caches to force recomputation
for (PlanningEntity entity : entities) {
    entity.cachedAvailableRooms = null;  // If using cached VRP
}
```

## Suggested Fix

### Option 1: Use HashSet for user types (simple)

```java
// ValueRangeCache.java
FOR_USER_VALUES {
    public <Value_> ValueRangeCache<Value_> buildCache(int size) {
        // Use HashSet instead of IdentityHashSet for user types
        return new ValueRangeCache<>(size,
            CollectionUtils.newHashSet(size), false);
    }
}
```

**Rationale**: 
- Javadoc says `FOR_TRUSTED_VALUES` is for types where "we can trust that equals() means same object"
- User-defined types (like JPA entities) implement `equals()` correctly (by ID for entities)
- If we can't trust user's `.equals()` implementation, that's a bug in their code, not our responsibility
- The current design contradicts itself: deduplication trusts `.equals()`, but validation doesn't

**Risk**: 
- If users implement `.equals()` incorrectly (e.g., mutable fields), cache may have duplicate entries
- This is acceptable - it's a user error, not a framework bug

### Option 2: Skip identity check for deduplicated ranges (targeted)

```java
// AbstractScoreDirector.java
private void assertValueRangeForBasicVariables(Object value) {
    // If value range was deduplicated (shared between entities), skip identity check
    // The deduplication already verified equality via equals()
    if (valueRangeState.isSharedValueRange() && valueRange instanceof ListValueRange) {
        ListValueRange<?> listRange = (ListValueRange<?>) valueRange;
        if (listRange.contains(value)) {  // Use List.contains() which uses equals()
            return;  // Valid
        }
    }
    
    // Original logic for non-shared ranges
    if (!valueRangeCache.contains(value)) {
        throw new IllegalStateException(...);
    }
}
```

**Rationale**:
- More conservative - only changes behavior for deduplicated ranges
- Preserves identity check for non-deduplicated cases
- Clear intent: if we trust `.equals()` enough to deduplicate, trust it enough to validate

## Impact

This bug affects:
- Any application using **pre-assignment** + **JPA entities** + **filtering VRPs**
- Scenarios where entities are persisted in one transaction and loaded in another
- Common in: school scheduling, employee rostering, resource allocation

The workaround is complex (3-layer fix) and impacts performance (normalization overhead).

## Related Code

- **Timefold 1.31.0**: 
  - `ValueRangeCache.java` (lines 28-46)
  - `ValueRangeState.java` (lines 70-84)
  - `ListValueRange.java` (equals() implementation)
  - `AbstractScoreDirector.java` (assertValueRangeForBasicVariables())

- **Javadoc Context**:
  ```java
  /**
   * For types where we can trust that {@code equals()} means that if two objects 
   * are equal, they are the same object. It is not guaranteed to be the case for 
   * user-defined types, which is when {@link Builder#FOR_USER_VALUES} should be 
   * used instead.
   */
  FOR_TRUSTED_VALUES
  ```

**Problem**: This Javadoc is wrong. User-defined types (like JPA entities) **DO** have correct `.equals()` implementations (by ID). The framework should trust them.

## Test Case

```java
@Test
public void testValueRangeDeduplicationWithIdentityMismatch() {
    // Create two rooms with same ID but different instances
    Room room1 = new Room(1L, "Room A");
    Room room2 = new Room(1L, "Room A");  // Same ID, different object
    
    assertTrue(room1.equals(room2));  // equals() works
    assertFalse(room1 == room2);      // == fails
    
    // Create two entities with different room instances
    Solution solution = new Solution();
    solution.rooms = List.of(room1);  // Canonical room list
    
    PlanningEntity entity1 = new PlanningEntity(1L, room1, true);
    PlanningEntity entity2 = new PlanningEntity(2L, room2, true);
    solution.entities = List.of(entity1, entity2);
    
    // Solver startup should NOT throw IllegalStateException
    // Both entities have room with id=1, which is in the rooms list
    SolverFactory<Solution> solverFactory = SolverFactory.create(new SolverConfig()
        .withSolutionClass(Solution.class)
        .withEntityClasses(PlanningEntity.class));
    
    Solver<Solution> solver = solverFactory.buildSolver();
    solver.solve(solution);  // Should succeed, but throws with 1.31.0
}
```

## References

- **Source**: [Timefold Solver v1.31.0 on GitHub](https://github.com/TimefoldAI/timefold-solver/tree/v1.31.0)
- **PR #1900**: [Improve memory use of entity value ranges](https://github.com/TimefoldAI/timefold-solver/pull/1900) (introduced deduplication)
- **JPA Entities**: Standard pattern for equals/hashCode by ID field
- **Reproducer**: Complete minimal project available

## Priority

**High** - Blocks legitimate use cases (pre-assignment + JPA entities) and requires complex workaround. The design contradiction is a fundamental issue that affects multiple users.

## Recommendation

Accept **Option 1** (use HashSet for user types). The rationale in the Javadoc is paternalistic - if users implement `.equals()` incorrectly, that's their bug to fix. The framework should not penalize correct implementations to protect against incorrect ones.

Alternative: Accept **Option 2** as a more conservative fix, then phase out identity checks in a future major version (2.0+).

---

## Update (2026-03-06): Fix Already Exists in Upstream!

After filing issue #2172, we discovered that this bug was already fixed in:

**PR #2111: "refactor: value range cleanup"** (merged Feb 13, 2026)
- Commit: https://github.com/TimefoldAI/timefold-solver/commit/2f99a1d72b
- Key change: Removed `Builder.FOR_USER_VALUES` / `Builder.FOR_TRUSTED_VALUES` distinction
- Now always uses `CollectionUtils.newHashSet(size)` which trusts `.equals()`

The fix was merged **2 days after** the 1.31.0 release, so 1.31.0 still contains the bug.

**Issue**: https://github.com/TimefoldAI/timefold-solver/issues/2172
**Comment**: https://github.com/TimefoldAI/timefold-solver/issues/2172#issuecomment-4011598068
