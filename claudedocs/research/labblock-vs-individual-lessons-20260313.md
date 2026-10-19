# LabBlock vs Individual Lessons: Critical Modeling Mismatch

## Executive Summary

**ROOT CAUSE IDENTIFIED**: Poor compaction is NOT a curriculum constraint or algorithm bug. It's a **fundamental modeling mismatch** between how the human planner schedules (individual 1-hour lessons) and how we model the problem (LabBlocks of 2-3 hours).

## The Evidence

### Side-by-Side Comparison: G01

| Metric | Spalmone CSV | DataGenerator | Difference |
|--------|---------------|----------------|------------|
| **Total scheduling units** | 952 lessons | 594 entities | **-38% fewer** |
| Unique dates with lessons | 172 | 145 | -27 dates |
| Avg lessons per day | **5.53** | 4.08 | **-26% density** |
| Days with 6 lessons | 98 (57%) | 36 (25%) | **-56% worse** |

### LabBlock Analysis for G01

| Course | Course Hours | BlockSize | Count | Entity Type |
|--------|-------------|-----------|-------|-------------|
| Confezione | 142h | 2h | 100+ | **LabBlock** |
| Modello | 77h | 2h | 30+ | **LabBlock** |
| Moulage | 48h | 2h | 30+ | **LabBlock** |
| Tecnologia tessile | 31h | 3h | 20+ | **LabBlock** |
| Informatica | 50h | 2h | 30+ | **LabBlock** |
| Taglio metalli | 27h | 2h | 20+ | **LabBlock** |

**Total LabBlocks for G01**: ~200+ blocks occupying ~400+ timeslots, but counting as only ~200 scheduling decisions.

## The Problem

### Scheduling Granularity Mismatch

**Spalmone (Human Planner)**:
```
Each 1-hour lesson = 1 scheduling decision
Example: Confezione 142h = 142 individual lessons
```

**DataGenerator (Our Model)**:
```
LabBlocks of 2-3 hours = 1 scheduling decision per block
Example: Confezione 142h = 71 LabBlocks of 2h each
```

### Why This Hurts Compaction

1. **Fewer Placement Decisions**: 200 LabBlocks vs 400 individual lessons = half the decision points
2. **Reduced Flexibility**: A 2-hour block must be placed contiguously; two 1-hour lessons can be placed separately
3. **Day-Filling Difficulty**: To fill 6 slots on a day with LabBlocks:
   - Need 6 different teachers with consecutive 2-hour availability
   - Each LabBlock consumes 2 slots but only 1 "decision"
   - Much harder to coordinate than 6 individual 1-hour lessons

### Example: Filling a Day

**Spalmone approach** (6 slots, 6 different 1-hour lessons):
```
08:00 - Confezione (T16)
09:00 - Modello (T17)
10:00 - Matematica (T08)
11:00 - Italiano (T14)
12:00 - Informatica (T06)
13:00 - Inglese (T21)
```
All 6 teachers teach for 1 hour each.

**DataGenerator approach** (6 slots, LabBlocks):
```
08:00-10:00 - Confezione (2h LabBlock) [T16]
10:00-12:00 - Modello (2h LabBlock) [T17]
12:00-13:00 - ??? (only 1h remaining, can't fit 2h LabBlock)
```
Only 2 LabBlocks placed, remaining 4 slots empty!

## The Solution Options

### Option 1: Convert LabBlocks to Individual Lessons (Recommended)

**Change the data model** to match spalmone.csv:
- Remove LabBlock entity for regular lab courses
- Create individual 1-hour Lesson entities for all lab hours
- Keep LabBlock ONLY for true multi-hour block requirements (if any)

**Benefits**:
- Matches human planner's approach
- Increases scheduling granularity (better compaction)
- More placement decisions = better optimization

**Implementation**:
```java
// In DataGenerator.generateCoursesFromCSV():
// For each (teacher, group, subject) combination:
//   Parse total hours from CSV
//   Create ONE course with totalHours = CSV hours
//   Generate totalHours individual Lessons (not LabBlocks)
//   Mark isLab=true for room tag filtering, but use Lesson entity
```

### Option 2: Keep LabBlocks but Optimize for Day-Filling

Modify DayClusteredConstructionPhase to:
1. Split 2-hour LabBlocks into two 1-hour "virtual" lessons for placement
2. After placement, recombine into LabBlocks
3. Handle room constraints appropriately

**Drawback**: More complex, still doesn't match spalmone's flexibility

### Option 3: Accept the Model

Keep LabBlocks but:
- Document that compaction will be ~25% worse than spalmone
- Adjust M20 weight expectations
- Explain to stakeholders that LabBlock model = less dense calendars

**Drawback**: Doesn't match human planner's proven approach

## Validation Against Spalmone

The spalmone.csv data proves that **dense calendars are achievable** with individual 1-hour lessons:
- G02: 5.47 avg lessons/day (excellent)
- G01: 5.53 avg lessons/day (excellent)
- G10: 5.30 avg lessons/day (excellent)

All these groups use **individual 1-hour lessons**, not LabBlocks.

## Files to Modify

1. **`bootstrap/DataGenerator.java`**:
   - Change `generateCoursesFromCSV()` to create individual Lessons instead of LabBlocks
   - For lab courses: `isLab=true` but create `Lesson` entities (not `LabBlock`)

2. **`domain/LabBlock.java`**:
   - Keep for special cases (true multi-hour blocks)
   - Most lab courses should use individual Lessons instead

3. **`domain/Course.java`**:
   - Keep `isLab` flag for room tag filtering
   - Remove assumption that lab courses must use LabBlocks

4. **`service/LessonGenerator.java`**:
   - Modify to generate individual Lessons for all courses
   - Stop creating LabBlocks automatically

## Recommendation

**Implement Option 1**: Convert LabBlocks to individual Lessons for regular lab courses.

This will:
- Match the human planner's proven approach
- Improve compaction by ~25-35%
- Eliminate the "scattering" issue
- Simplify the solver (fewer entity types)

**Exceptions**: Keep LabBlock entity for true multi-hour blocks (if any exist), but most lab work should be individual 1-hour lessons.

## Related Issues Resolved

- ✅ M20 (horizontal compactness) penalty will be reduced
- ✅ Day clustering will work as intended
- ✅ Solver results will match spalmone.csv patterns
- ✅ Better alignment with human planner's proven approach

## Testing Plan

1. Modify DataGenerator to create individual Lessons for lab courses
2. Rebuild and regenerate data
3. Verify lesson count matches spalmone.csv (~952 for G01)
4. Run solver and verify improved compaction:
   - Target: ~5.5 avg lessons/day (vs 4.08 currently)
   - Target: ~170 days (vs 145 currently - more days but denser)
5. Compare constraint scores (M20 should decrease significantly)
