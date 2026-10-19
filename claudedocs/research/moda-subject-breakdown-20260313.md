# G01 Subject Breakdown: LabBlocks vs Spalmone

## Executive Summary

**TOTAL ENTITIES COMPARISON:**
- **Spalmone (CSV)**: 952 individual 1-hour lessons
- **Our Database**: 189 LabBlocks + 594 Lessons = **783 entities**
- **Difference**: **169 fewer scheduling decisions** (-18%)

**Key Finding**: Even combining LabBlocks + Lessons, we have fewer entities than spalmone's individual lessons because:
1. LabBlocks represent 2-3 hours as 1 entity (reduces decision count)
2. Some subjects have incomplete coverage in our database

## Detailed Subject-by-Subject Comparison

| Subject | Spalmone | LabBlock (2-3h) | Lesson (1h) | Total DB | Diff |
|---------|----------|-----------------|-------------|----------|------|
| **Confezione** | 130 | 71 blocks = 142h | 0 | 71 | **-59** |
| **Modello** | 77 | 38 blocks = 76h | 1 | 39 | **-38** |
| **Tecnologia tessile** | 51 (28+23) | 18 blocks = 54h | 1 | 19 | **-32** |
| **Informatica** | 49 | 25 blocks = 50h | 0 | 25 | **-24** |
| **Moulage** | 48 | 24 blocks = 48h | 2 | 26 | **-22** |
| **Taglio metalli** | 21 | 13 blocks = 26h | 1 | 14 | **-7** |
| **Italiano** | 90 | 0 | 90 | 90 | 0 |
| **Matematica** | 80 | 0 | 80 | 80 | 0 |
| **Inglese** | 69 | 0 | 70 | 70 | +1 |
| **Scienze** | 49 | 0 | 50 | 50 | +1 |
| **Fisica** | 43 | 0 | 45 | 45 | +2 |
| **Economia** | 30 | 0 | 30 | 30 | 0 |
| **Educazione civica** | 29 | 0 | 30 | 30 | +1 |
| **Storia della moda** | 25 | 0 | 25 | 25 | 0 |
| **Orientamento** | 20 | 0 | 20 | 20 | 0 |
| **Mercatistica** | 20 | 0 | 30 | 30 | +10 |
| **Storia** | 20 | 0 | 20 | 20 | 0 |
| **Scienze sociali** | 10 | 0 | 10 | 10 | 0 |
| **Arredamento** | 10 | 0 | 10 | 10 | 0 |
| **Sicurezza** | 4+4=8 | 0 | 4+4=8 | 8 | 0 |
| **Minor subjects** | ~28 | 0 | ~28 | ~28 | 0 |

### Key Observations

1. **LabBlock subjects have significantly fewer entities**:
   - Confezione: 71 LabBlocks vs 130 lessons = **45% fewer decisions**
   - Modello: 39 entities vs 77 lessons = **49% fewer decisions**
   - Tecnologia tessile: 19 entities vs 51 lessons = **63% fewer decisions**

2. **Non-lab subjects match closely**:
   - Italiano, Matematica, Inglese, Scienze all match within 1-2 hours
   - These use Lesson entities (1 hour = 1 decision)

3. **Hours match, entities don't**:
   - Confezione: 142h (correct) but only 71 scheduling decisions vs 130
   - A 2-hour LabBlock = 1 decision, but occupies 2 timeslots
   - This reduces the solver's ability to fill days densely

## Compaction Impact Analysis

### Spalmone Approach (Individual Lessons)
To fill 6 slots on a Wednesday for G01, spalmone uses:
```
08:00 Confezione (T16)     - 1 decision
09:00 Confezione (T16)     - 1 decision
10:00 Modello (T17)      - 1 decision
11:00 Italiano (T14) - 1 decision
12:00 Informatica (T06) - 1 decision
13:00 Inglese (T21)     - 1 decision
```
= **6 scheduling decisions** to fill 6 slots

### Our Approach (LabBlocks + Lessons)
```
08:00-10:00 Confezione (T16) - 2h LabBlock (1 decision)
10:00-12:00 Modello (T17) - 2h LabBlock (1 decision)
12:00-13:00 ??? (only 1 slot left, can't fit 2h LabBlock)
```
= **2 scheduling decisions** for 4 slots, 2 slots empty

### Why This Hurts Day Clustering

1. **Reduced granularity**: 189 LabBlocks + 594 Lessons = 783 total entities
   vs 952 individual lessons = 169 fewer placement decisions

2. **Inflexible placement**: A 2h LabBlock MUST be placed contiguously
   - Can't split across lunch breaks
   - Can't adjust to fill odd-sized gaps

3. **Day-filling difficulty**: With LabBlocks:
   - 6 LabBlocks = 12 slots filled (if 2h each)
   - But 4 LabBlocks = 8 slots with 4 decisions
   - Remaining 4 slots need perfect coordination

## Recommendations

### Option 1: Convert LabBlocks to Individual Lessons (RECOMMENDED)

Match spalmone's approach:
- Replace LabBlocks with individual 1-hour Lessons
- Keep `isLab=true` flag for room tag filtering
- Result: ~950 scheduling decisions instead of 783

**Benefits**:
- Matches proven human planner approach
- 22% more placement decisions = better compaction
- More flexible day filling

**Implementation**:
```java
// In DataGenerator.generateCoursesFromCSV():
// For lab courses, create individual Lessons, not LabBlocks
Course course = new Course();
course.isLab = true;  // Keep for room filtering
course.labBlockSize = 2;  // Optional metadata
// Generate totalHours individual Lessons
```

### Option 2: Keep LabBlocks, Accept Lower Compaction

- Document that LabBlock model = ~25% worse compaction
- Adjust M20 weight expectations
- Explain to stakeholders

### Option 3: Hybrid Approach

- Keep LabBlocks for subjects that truly need contiguous blocks
- Use individual Lessons for most lab work
- Requires curriculum analysis to determine which subjects need contiguity

## Validation Data Sources

- **Database queries**: LabBlock and Lesson tables for G01
- **Spalmone CSV**: Column 34 (G01) from scripts/data/spalmone.csv
- **Parsed JSON**: /tmp/spalmone-parsed.json with 11,136 total lessons
