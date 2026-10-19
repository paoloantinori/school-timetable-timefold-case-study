# M17 Lab Recurring Pattern: Modeling Approaches Research

**Date**: 2026-03-08
**Research Depth**: Exhaustive
**Status**: Findings and Recommendations

---

## Executive Summary

The current `expectedLabDaysPerWeek()` function in M17/M17.2 constraints uses a **hardcoded 38-week divisor**, which assumes all lab courses are distributed evenly across the entire school year. This is **inherently incorrect** for:

1. **Rotation-based courses** that run in concentrated periods (10-18 weeks)
2. **Half-year courses** that only run in specific trimesters
3. **Professional stage periods** where groups are partially absent

**Impact**: Courses that should be classified as "multi-day" are incorrectly classified as "single-day", causing the M17 constraint to apply wrong logic.

---

## Part 1: Current Implementation Analysis

### 1.1 The Flawed Function

```java
static int expectedLabDaysPerWeek(Course course) {
    if (course == null || !course.isLab || course.labBlockSize <= 0) return 1;
    int blockCount = (int) Math.ceil((double) course.totalHours / course.labBlockSize);
    return Math.max(1, (int) Math.round((double) blockCount / 38.0));
}
```

**Problem**: `blockCount / 38.0` assumes uniform weekly distribution across 38 weeks.

**Why 38 weeks?** This is the approximate Italian school year length (September → June, excluding holidays).

### 1.2 Data Analysis Results

Querying actual course data reveals the issue:

| Subject | Group | Blocks | 38-week divisor | expectedDays (current) | If 12-week period | If 10-week period |
|---------|-------|--------|-----------------|------------------------|-------------------|-------------------|
| Lavorazioni meccaniche | 1G03 | 75 | 1.97 → **2** | 1.97 | 6.25 | 7.50 |
| Confezione | G05 | 71 | 1.87 → **2** | 1.87 | 5.92 | 7.10 |
| Confezione | G09 | 60 | 1.58 → **2** | 1.58 | 5.00 | 6.00 |
| Informatica | G07 | 25 | 0.66 → **1** ⚠️ | 0.66 | 2.08 | 2.50 |
| Informatica | G02 | 25 | 0.66 → **1** ⚠️ | 0.66 | 2.08 | 2.50 |
| Modello | G09 | 25 | 0.66 → **1** ⚠️ | 0.66 | 2.08 | 2.50 |

**Key Finding**: Many courses with 20-30 blocks are classified as "single-day" (expectedDays = 1) but would actually require 2+ days/week if concentrated in a shorter period.

### 1.3 LabRotation Period Data

Actual `LabRotation` periods in the database:

| Teacher | Group | Period Start | Period End | Weeks |
|---------|-------|--------------|------------|-------|
| T28 | G02 | 2025-09-10 | 2026-06-10 | **39** |
| T28 | G06 | 2025-09-10 | 2026-06-10 | **39** |
| T28 | G10 | 2025-09-10 | 2026-06-10 | **39** |
| T28 | G12 | 2026-02-01 | 2026-06-10 | **18** |

**Critical Issue**: Current `LabRotation` data shows **39-week periods** (essentially full-year), not the expected trimester rotations (10-12 weeks). This may be placeholder data.

---

## Part 2: Root Cause Analysis

### 2.1 The Misalignment

```
Course: 25 blocks (50h total, 2h blocks)
├── Current computation: 25 / 38 = 0.66 → rounds to 1 (single-day)
├── Actual period (if 12 weeks): 25 / 12 = 2.08 → should be 2 (multi-day)
└── Constraint applies: M17 (pairwise) instead of M17.2 (groupBy)
    → Wrong constraint logic for actual distribution
```

### 2.2 Why Period-Awareness Exists But Fails

The constraint **IS** period-aware in its **comparison** logic:

```java
private boolean sameRotationPeriod(LabBlock a, LabBlock b) {
    if (a.labRotation == null && b.labRotation == null) return true;
    if (a.labRotation == null || b.labRotation == null) return false;
    return a.labRotation.id.equals(b.labRotation.id);
}
```

BUT: The `expectedLabDaysPerWeek()` calculation **does NOT use** `LabRotation.periodStart` or `periodEnd`. It always divides by 38.

**The disconnect**: Constraint knows *which* blocks to compare (period-aware grouping), but doesn't know *what the expected pattern should be* (period-agnostic divisor).

---

## Part 3: Modeling Approaches

### Approach 1: Period-Aware Divisor (Computation Fix)

**Idea**: Use actual `LabRotation` period duration instead of hardcoded 38.

```java
static int expectedLabDaysPerWeek(Course course, LabRotation rotation) {
    if (course == null || !course.isLab || course.labBlockSize <= 0) return 1;
    int blockCount = (int) Math.ceil((double) course.totalHours / course.labBlockSize);

    // Use actual rotation period if available
    double weeksInPeriod = 38.0; // default
    if (rotation != null && rotation.periodStart != null && rotation.periodEnd != null) {
        weeksInPeriod = ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd);
    }

    return Math.max(1, (int) Math.round((double) blockCount / weeksInPeriod));
}
```

**Challenge**: M17's `forEachUniquePair` doesn't provide access to `LabRotation` in the filter context.

**Workaround**: Pre-compute `expectedDays` and store on `Course` or `LabBlock`.

#### 1A: Store on Course (Requires data model change)

```java
// Course.java
@Transient
public int expectedLabDaysPerWeek; // computed by BacktrackingPreAssigner
```

**Pros**:
- Clean separation of computation and constraint
- Easy to debug and verify

**Cons**:
- Requires `Course` data model change
- Must be kept in sync with `LabRotation` periods
- `@Transient` means it's not persisted (must recompute on load)

#### 1B: Store on LabBlock (Smaller scope)

```java
// LabBlock.java
@Transient
public int expectedLabDaysPerWeek;
```

**Pros**:
- More granular (different blocks could have different expectations if periods vary)
- Smaller scope change

**Cons**:
- Redundant storage (same value for all blocks of same course+rotation)
- Still requires synchronization

---

### Approach 2: Constraint Refactoring (New Formulation)

**Idea**: Instead of pre-computing `expectedDays`, reformulate the constraint to be inherently period-aware.

#### 2A: Direct Period-Grouping (groupBy with period)

```java
Constraint labRecurringPatternPeriodAware(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(LabBlock.class)
        .filter(b -> b.startTimeslot != null && b.course != null && b.labRotation != null)
        .groupBy(b -> b.labRotation.id,  // Group by rotation period
                 b -> b.course.id,
                 b -> b.startTimeslot.getDate().getDayOfWeek(),
                 count())
        .filter((rotationId, courseId, dayOfWeek, count) -> {
            // Get the rotation period
            LabRotation rotation = LabRotation.findById(rotationId);
            double weeksInPeriod = weeksBetween(rotation.periodStart, rotation.periodEnd);

            // Count distinct days for this (rotation, course)
            long distinctDays = /* need to count distinct days separately */;

            // Expected blocks per week
            double blocksPerWeek = (double) count / weeksInPeriod;

            // If blocksPerWeek > 1, should only use 1 day
            // If blocksPerWeek < 1, use multiple days
            return /* complex logic */;
        })
        .penalize(HardMediumSoftScore.ONE_MEDIUM)
        .asConstraint("M17: Period-aware lab pattern");
}
```

**Problem**: This approach gets **very complex** because we need to:
1. Count distinct days per (rotation, course)
2. Count total blocks per (rotation, course)
3. Calculate expected days dynamically
4. Compare actual vs expected

**The quadruple-nested groupBy becomes computationally expensive** and harder to reason about.

#### 2B: Two-Constraint Approach (Split by Pattern)

Instead of trying to handle both cases in one constraint, split based on observed pattern:

```java
// M17a: Same-day preference (single-day courses)
Constraint labRecurringPatternSingleDay(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(LabBlock.class,
            Joiners.equal(b -> b.course != null ? b.course.id : -1L),
            Joiners.equal(b -> b.labRotation != null ? b.labRotation.id : -1L))
        .filter((a, b) -> a.startTimeslot != null && b.startTimeslot != null)
        .filter((a, b) -> {
            // Check if actual distribution is single-day
            LabRotation rot = a.labRotation;
            double weeks = (rot != null) ?
                weeksBetween(rot.periodStart, rot.periodEnd) : 38.0;
            int blockCount = /* count blocks for this course+rotation */;
            double blocksPerWeek = blockCount / weeks;
            return blocksPerWeek <= 1.0; // Single-day pattern
        })
        .filter((a, b) -> a.startTimeslot.getDate().getDayOfWeek()
                       != b.startTimeslot.getDate().getDayOfWeek())
        .penalize(HardMediumSoftScore.ONE_MEDIUM)
        .asConstraint("M17a: Same-day pattern");
}

// M17b: Multi-day limit (multi-day courses)
Constraint labRecurringPatternMultiDay(ConstraintFactory constraintFactory) {
    // Similar, but use groupBy to count distinct days
    // Only apply when blocksPerWeek > 1.0
}
```

**Problem**: Still requires computing `blocksPerWeek` dynamically, which is expensive in a filter.

---

### Approach 3: Data Model Enhancement (Expected Days Field)

**Idea**: Add `expectedLabDaysPerWeek` as a **persistent field** computed by the pre-assigner.

```java
// Course.java
@Column(name = "expected_lab_days_per_week")
public int expectedLabDaysPerWeek = 1; // default

// BacktrackingPreAssigner.java
void assignExpectedDays(Course course, LabRotation rotation) {
    int blockCount = (int) Math.ceil(course.totalHours / (double) course.labBlockSize);
    double weeks = (rotation != null) ?
        ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd) : 38.0;
    course.expectedLabDaysPerWeek = Math.max(1, (int) Math.round(blockCount / weeks));
    course.persist();
}
```

**Constraint becomes trivial**:

```java
Constraint labRecurringPattern(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(LabBlock.class,
            Joiners.equal(b -> b.course != null ? b.course.id : -1L))
        .filter((a, b) -> a.course != null && a.course.expectedLabDaysPerWeek == 1)
        .filter((a, b) -> a.labRotation != null && a.labRotation.equals(b.labRotation))
        .filter((a, b) -> a.startTimeslot != null && b.startTimeslot != null
                && a.startTimeslot.getDate().getDayOfWeek()
                   != b.startTimeslot.getDate().getDayOfWeek())
        .penalize(HardMediumSoftScore.ONE_MEDIUM)
        .asConstraint("M17: Same-day pattern");
}
```

**Pros**:
- Simple, fast constraint (just field access)
- Computed once, stored, reused
- Easy to verify and debug

**Cons**:
- Requires database schema change
- Must be updated when `LabRotation` periods change
- Migration required for existing data

---

### Approach 4: Hybrid (Pre-Computation + Constraint)

**Idea**: Store computed `expectedDays` on `LabBlock` as a **transient field**, populated during solution initialization.

```java
// LabBlock.java
@Transient
public int expectedLabDaysPerWeek;

// SchoolSchedule.java (solution class)
@ProblemFactCollectionProperty
protected List<LabBlock> labBlocks;

// After loading from DB, compute expectedDays
void computeExpectedLabDays() {
    for (LabBlock block : labBlocks) {
        if (block.labRotation != null) {
            double weeks = ChronoUnit.WEEKS.between(
                block.labRotation.periodStart,
                block.labRotation.periodEnd);
            int blockCount = (int) Math.ceil(
                block.course.totalHours / (double) block.course.labBlockSize);
            block.expectedLabDaysPerWeek = Math.max(1,
                (int) Math.round(blockCount / weeks));
        } else {
            // Fallback to 38 weeks
            block.expectedLabDaysPerWeek = expectedLabDaysPerWeek(block.course);
        }
    }
}
```

**Pros**:
- No database schema change
- Computed fresh each solution load
- Fast constraint evaluation

**Cons**:
- Must be called after every solution load
- Transient field not persisted (must recompute if solver clones)

---

### Approach 5: Remove expectedDays entirely (Simplification)

**Idea**: What if we **don't distinguish** between single-day and multi-day courses? What if M17 is simply:

> "All blocks of the same course (within a rotation period) should prefer the same day of week"

```java
Constraint labRecurringPatternSimplified(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(LabBlock.class,
            Joiners.equal(b -> b.course != null ? b.course.id : -1L),
            Joiners.equal(b -> b.labRotation != null ? b.labRotation.id : -1L))
        .filter((a, b) -> a.startTimeslot != null && b.startTimeslot != null)
        .penalize(HardMediumSoftScore.ONE_MEDIUM,
            (a, b) -> a.startTimeslot.getDate().getDayOfWeek()
                     != b.startTimeslot.getDate().getDayOfWeek()
                ? 1 : 0)
        .asConstraint("M17: Prefer same day");
}
```

**Pros**:
- Extremely simple
- No `expectedDays` computation needed
- Works for both single-day and multi-day courses

**Cons**:
- **Incorrect for true multi-day courses**: If a course genuinely NEEDS 2 days/week (e.g., 40 blocks in 12 weeks = 3.3 blocks/week), this penalty would discourage the correct multi-day pattern.

**But wait**: Is this actually a problem? Let's think about it...

For a course with 40 blocks over 12 weeks:
- **Ideal**: 2 days/week × 2 blocks/day = 4 blocks/week × 12 weeks = 48 blocks (close to 40)
- **Current M17**: Would penalize using 2 different days, even though that's correct!

**Conclusion**: We NEED to distinguish single-day vs multi-day somehow. The `expectedDays` computation is essential.

---

## Part 4: Recommended Approach

### Recommendation: Approach 4 (Hybrid Pre-Computation)

**Rationale**:

1. **No schema change required** → Lower deployment risk
2. **Computed fresh each time** → Always reflects current `LabRotation` periods
3. **Fast constraint evaluation** → Just field access, no complex computation
4. **Clear separation of concerns** → Pre-assigner computes, constraint enforces

### Implementation Plan

#### Step 1: Add transient field to LabBlock

```java
// LabBlock.java
@Transient
public int expectedLabDaysPerWeek;
```

#### Step 2: Populate during solution initialization

```java
// SchoolSchedule.java
@Solution
public class SchoolSchedule {
    // ...

    /**
     * Called by Timefold after solution is loaded from DB.
     * Computes expectedLabDaysPerWeek for all LabBlocks.
     */
    @CustomPhaseSolverListener(phase = SolverPhase.PHASE_0, order = 0)
    public void computeExpectedLabDays() {
        Map<Course, Map<LabRotation, List<LabBlock>>> blocksByCourseRotation =
            labBlocks.stream()
                .collect(Collectors.groupingBy(
                    b -> b.course,
                    Collectors.groupingBy(b -> b.labRotation)));

        for (var entry : blocksByCourseRotation.entrySet()) {
            Course course = entry.getKey();
            for (var rotationEntry : entry.getValue().entrySet()) {
                LabRotation rotation = rotationEntry.getKey();
                List<LabBlock> blocks = rotationEntry.getValue();

                double weeks = 38.0; // default
                if (rotation != null && rotation.periodStart != null && rotation.periodEnd != null) {
                    weeks = ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd);
                }

                int expectedDays = Math.max(1, (int) Math.round(
                    (double) blocks.size() / weeks));

                blocks.forEach(b -> b.expectedLabDaysPerWeek = expectedDays);
            }
        }
    }
}
```

**Actually**: Timefold doesn't have `@CustomPhaseSolverListener` for initialization. We need a different approach...

#### Alternative: Lazy computation in constraint

```java
// Cache computed values per (course, rotation) pair
static final Map<Long, Map<Long, Integer>> expectedDaysCache = new HashMap<>();

static int getExpectedLabDaysPerWeek(LabBlock block) {
    if (block.expectedLabDaysPerWeek > 0) {
        return block.expectedLabDaysPerWeek; // Cached
    }

    Course course = block.course;
    LabRotation rotation = block.labRotation;
    long cacheKey = course.id;
    long rotationKey = (rotation != null) ? rotation.id : -1L;

    expectedDaysCache
        .computeIfAbsent(cacheKey, k -> new HashMap<>())
        .computeIfAbsent(rotationKey, k -> {
            int blockCount = (int) Math.ceil(
                course.totalHours / (double) course.labBlockSize);

            double weeks = 38.0;
            if (rotation != null && rotation.periodStart != null && rotation.periodEnd != null) {
                weeks = ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd);
            }

            int result = Math.max(1, (int) Math.round(blockCount / weeks));

            // Cache on block for faster access
            block.expectedLabDaysPerWeek = result;
            return result;
        });

    return block.expectedLabDaysPerWeek;
}
```

**Problem**: Static cache doesn't work well with Timefold's solution cloning...

#### Final Approach: Pre-compute in BacktrackingPreAssigner

Since `BacktrackingPreAssigner` already assigns `labRotation` to blocks, also compute `expectedLabDaysPerWeek` there:

```java
// BacktrackingPreAssigner.java
private void assignExpectedDays(List<LabBlock> blocks, LabRotation rotation) {
    double weeks = 38.0;
    if (rotation != null && rotation.periodStart != null && rotation.periodEnd != null) {
        weeks = ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd);
    }

    Map<Course, List<LabBlock>> byCourse = blocks.stream()
        .collect(Collectors.groupingBy(b -> b.course));

    for (var entry : byCourse.entrySet()) {
        Course course = entry.getKey();
        List<LabBlock> courseBlocks = entry.getValue();

        int blockCount = courseBlocks.size();
        int expectedDays = Math.max(1, (int) Math.round((double) blockCount / weeks));

        courseBlocks.forEach(b -> b.expectedLabDaysPerWeek = expectedDays);
        courseBlocks.forEach(b -> b.persist()); // Save to DB
    }
}
```

**Wait**: `expectedLabDaysPerWeek` is `@Transient`, so `persist()` won't save it. It would need to be `@Column` (persistent field) for this to work.

### Revised Recommendation: Approach 3 (Persistent Field)

**Actually**, given that:
1. The value is deterministic (computed from course + rotation)
2. It only changes when `LabRotation` periods change (rare)
3. We need it persisted across solver runs

**Go with Approach 3** — make it a persistent field.

---

## Part 5: Implementation Details (Approach 3)

### 5.1 Database Migration

```sql
-- Add column to course table
ALTER TABLE course ADD COLUMN expected_lab_days_per_week INT DEFAULT 1;

-- Compute initial values for lab courses
UPDATE course c
SET expected_lab_days_per_week = GREATEST(1,
    ROUND(CEIL(c.totalhours::numeric / NULLIF(c.labblocksize, 0)) / 38.0))
WHERE c.islab = true;
```

### 5.2 Data Model Change

```java
// Course.java
@Column(name = "expected_lab_days_per_week")
public int expectedLabDaysPerWeek = 1; // default
```

### 5.3 Computation Logic

```java
// BacktrackingPreAssigner.java (or new ExpectedDaysCalculator)

public static void computeAndPersistExpectedDays(Course course, LabRotation rotation) {
    int blockCount = (int) Math.ceil(
        (double) course.totalHours / course.labBlockSize);

    double weeks = 38.0; // default
    if (rotation != null && rotation.periodStart != null && rotation.periodEnd != null) {
        weeks = ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd);
    }

    int expectedDays = Math.max(1, (int) Math.round(blockCount / weeks));

    if (course.expectedLabDaysPerWeek != expectedDays) {
        course.expectedLabDaysPerWeek = expectedDays;
        course.persist();
    }
}
```

### 5.4 Constraint Simplification

```java
// M17: For single-day courses, all blocks should be on the same day of week
Constraint labRecurringPattern(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(LabBlock.class,
                    Joiners.equal(b -> b.course != null ? b.course.id : -1L))
            .filter((a, b) -> a.startTimeslot != null && b.startTimeslot != null
                    && a.course != null
                    && a.course.expectedLabDaysPerWeek == 1  // Simple field access!
                    && sameRotationPeriod(a, b)
                    && a.startTimeslot.getDate().getDayOfWeek()
                       != b.startTimeslot.getDate().getDayOfWeek())
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("M17: Lab recurring pattern");
}

// M17.2: For multi-day courses, limit distinct days to expected count
Constraint labRecurringPatternMultiDay(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(LabBlock.class)
            .filter(b -> b.startTimeslot != null && b.course != null
                    && b.course.expectedLabDaysPerWeek > 1)  // Simple field access!
            .groupBy(b -> new CompositeRotationKey(
                            b.course.id,
                            b.labRotation != null ? b.labRotation.id : -1L),
                    b -> b.course.expectedLabDaysPerWeek,  // Simple field access!
                    countDistinct(b -> b.startTimeslot.getDate().getDayOfWeek()),
                    count())
            .filter((compositeKey, expectedDays, distinctDays, blockCount) ->
                    distinctDays > expectedDays)
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                    (compositeKey, expectedDays, distinctDays, blockCount) ->
                            (distinctDays - expectedDays) * blockCount)
            .asConstraint("M17.2: Lab recurring pattern (multi-day)");
}
```

---

## Part 6: Edge Cases and Considerations

### 6.1 Courses Without LabRotation

**Scenario**: Regular lab courses that don't use `LabRotation` (e.g., `Lavorazioni meccaniche d'officina`).

**Handling**: Use 38 weeks as default divisor.

```java
double weeks = (rotation != null && rotation.periodStart != null && rotation.periodEnd != null)
    ? ChronoUnit.WEEKS.between(rotation.periodStart, rotation.periodEnd)
    : 38.0;
```

### 6.2 Period Duration Rounding

**Scenario**: A period is 10.5 weeks (73 days).

**Handling**: Use `Math.round()` to get whole weeks, or use fractional weeks for precision.

```java
// Option A: Round to whole weeks (simpler)
long weeks = ChronoUnit.WEEKS.between(start, end);

// Option B: Fractional weeks (more accurate)
double weeks = (double) ChronoUnit.DAYS.between(start, end) / 7.0;
```

**Recommendation**: Use fractional weeks for precision, then round final `expectedDays`.

### 6.3 LabRotation Period Changes

**Scenario**: School updates rotation periods (e.g., trimester boundaries shift).

**Handling**: Recompute `expectedLabDaysPerWeek` when periods change.

```java
// In LabRotationResource PUT/POST endpoint
@PUT
@Path("/{id}")
public Response updateLabRotation(@PathParam("id") Long id, LabRotation updated) {
    LabRotation existing = LabRotation.findById(id);
    existing.periodStart = updated.periodStart;
    existing.periodEnd = updated.periodEnd;
    existing.persist();

    // Recompute expectedDays for affected courses
    recomputeExpectedDaysForRotation(existing);

    return Response.ok(existing).build();
}
```

### 6.4 Pre-Assignment vs Solver

**Question**: Should `expectedLabDaysPerWeek` be computed during pre-assignment or solver initialization?

**Answer**: During **pre-assignment**, because:
1. Pre-assigner already knows which `LabRotation` each block belongs to
2. Value is needed before solver starts
3. Avoids recomputation during solver runs

---

## Part 7: Alternative Consideration

### Question: Do We Need M17.2 at All?

**Observation**: Looking at the data, **very few courses** are truly "multi-day" (>1 block/week):

| Course | Blocks | Period (estimated) | Blocks/Week | Classification |
|--------|--------|-------------------|-------------|----------------|
| Lavorazioni meccaniche | 75 | 38 weeks | ~2 | **Multi-day** |
| Confezione | 71 | 38 weeks | ~1.9 | **Multi-day** |
| Most others | 10-30 | 10-38 weeks | <1 | **Single-day** |

**Hypothesis**: What if we **remove M17.2 entirely** and only keep M17 (same-day preference)?

**Pros**:
- Simpler constraint set
- Multi-day courses still get "prefer same day" guidance (just not strict)
- Let the solver decide based on other constraints

**Cons**:
- No explicit limit on distinct days for truly multi-day courses
- Solver might scatter blocks across 4+ days for a 2-day/week course

**Recommendation**: **Keep M17.2** but implement it properly with period-aware `expectedDays`.

---

## Part 8: Summary and Next Steps

### 8.1 Problem Summary

| Aspect | Current State | Issue |
|--------|---------------|-------|
| Computation | `blockCount / 38.0` | Assumes 38-week uniform distribution |
| Period grouping | ✅ Implemented (via `labRotation`) | Works correctly |
| Expected days | ❌ Not period-aware | Uses wrong divisor for short periods |
| Result | Misclassification | Single-day vs multi-day logic applies incorrectly |

### 8.2 Recommended Solution

**Approach 3**: Add persistent `expectedLabDaysPerWeek` field to `Course`.

| Step | Action | Files |
|------|--------|-------|
| 1 | Add column to `course` table | SQL migration |
| 2 | Add field to `Course.java` | Data model |
| 3 | Compute during pre-assignment | `BacktrackingPreAssigner.java` |
| 4 | Update M17/M17.2 to use field | `BaseConstraintProvider.java` |
| 5 | Add recalculation trigger | `LabRotationResource.java` |
| 6 | Update CLAUDE.md | Documentation |
| 7 | Add tests | `ExpectedDaysCalculatorTest.java` |

### 8.3 Confidence Level

**High confidence** — This approach:
- Addresses the root cause (wrong divisor)
- Minimal code changes (mostly adding a field)
- Maintains existing period-aware logic
- Clear migration path

### 8.4 Open Questions

1. **Should we use fractional weeks for precision?** → Yes, for accurate `expectedDays` calculation
2. **What if `LabRotation.periodStart` is null?** → Use 38 weeks as fallback
3. **Do we need to recalculate when importing schedules?** → Yes, during import

---

## Appendix: Code Snippets

### A.1: Expected Days Calculator

```java
public class ExpectedDaysCalculator {

    private static final double DEFAULT_WEEKS = 38.0;

    public static int computeExpectedDays(Course course, LabRotation rotation) {
        if (course == null || !course.isLab || course.labBlockSize <= 0) {
            return 1;
        }

        int blockCount = (int) Math.ceil(
            (double) course.totalHours / course.labBlockSize);

        double weeks = getWeeksInPeriod(rotation);

        return Math.max(1, (int) Math.round((double) blockCount / weeks));
    }

    private static double getWeeksInPeriod(LabRotation rotation) {
        if (rotation == null || rotation.periodStart == null || rotation.periodEnd == null) {
            return DEFAULT_WEEKS;
        }

        long days = ChronoUnit.DAYS.between(rotation.periodStart, rotation.periodEnd);
        return (double) days / 7.0;
    }

    public static void recomputeForCourse(Course course) {
        List<LabRotation> rotations = LabRotation.list(
            "studentgroup_id = ?1", course.studentgroup.id);

        if (rotations.isEmpty()) {
            course.expectedLabDaysPerWeek = computeExpectedDays(course, null);
        } else {
            // For each rotation, compute separately
            // (For now, use the maximum)
            int maxExpected = rotations.stream()
                .mapToInt(r -> computeExpectedDays(course, r))
                .max()
                .orElse(computeExpectedDays(course, null));

            course.expectedLabDaysPerWeek = maxExpected;
        }

        course.persist();
    }
}
```

### A.2: Test Cases

```java
@QuarkusTest
public class ExpectedDaysCalculatorTest {

    @Test
    public void testSingleDayCourse() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2026, 6, 15); // ~38 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 25 blocks / 38 weeks = 0.66 → rounds to 1
        assertEquals(1, expected);
    }

    @Test
    public void testMultiDayCourse_ShortPeriod() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2025, 11, 15); // ~10 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 25 blocks / 10 weeks = 2.5 → rounds to 3 (or 2.5 rounds to 3)
        // Actually: Math.round(25/10) = Math.round(2.5) = 3
        assertEquals(3, expected);
    }

    @Test
    public void testFullYearCourse() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 300;
        course.labBlockSize = 4; // 75 blocks

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2026, 6, 15); // ~38 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 75 blocks / 38 weeks = 1.97 → rounds to 2
        assertEquals(2, expected);
    }
}
```

---

**End of Research Document**
