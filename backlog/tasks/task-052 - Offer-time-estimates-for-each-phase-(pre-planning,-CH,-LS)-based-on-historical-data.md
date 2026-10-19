---
id: task-052
title: >-
  Offer time estimates for each phase (pre-planning, CH, LS) based on historical
  data
status: To Do
assignee: []
created_date: '2026-02-20 20:27'
updated_date: '2026-02-21 07:34'
labels:
  - ui
  - claude-planned
dependencies:
  - task-040
  - task-050
priority: low
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Users want to know approximately how long each scheduling phase will take:
1. **Pre-planning** (pre-assignment): Acconciatura + Labs + Professionalizzanti
2. **Construction Heuristic (CH)**: Initial placement
3. **Local Search (LS)**: Optimization phase

Currently, users have no indication of expected duration. They must wait and observe, which makes planning difficult.

## Two Approaches

### Option A: Static Estimates
Simple fixed values based on typical dataset sizes:
- Pre-planning: 10-30 seconds (depends on number of rotations)
- CH: 2-5 minutes (depends on entity count)
- LS: User-configured via `unimprovedSecondsSpentLimit`

### Option B: Historical Data (Preferred)
Store timing history from previous runs and compute averages:
- More accurate over time
- Accounts for dataset size variations
- Self-improving as more runs occur

## Proposed Solution
Implement Option B with Option A as fallback. Store timing records in localStorage (or backend database) with phase duration and entity count. Display estimates based on comparable historical runs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [GIVEN] The user is on the solver controls page [WHEN] The page loads [THEN] Estimated times for each phase are displayed: Pre-planning, Construction Heuristic, Local Search
- [ ] #2 [GIVEN] Historical data exists from previous solver runs [WHEN] The time estimates are calculated [THEN] They are based on actual historical averages, not static values
- [ ] #3 [GIVEN] No historical data exists [WHEN] Time estimates are displayed [THEN] Default fallback values are shown with a 'no history' indicator
- [ ] #4 [GIVEN] The user clicks 'Solve' [WHEN] The solver starts [THEN] A live progress indicator shows elapsed vs estimated time for the current phase
- [ ] #5 [GIVEN] The current phase exceeds estimated time [WHEN] The elapsed time passes the estimate [THEN] The UI shows an 'exceeding estimate' indicator
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Phase 1: Data Collection (Backend)

1. **Create TimingRecord entity** (optional - can use localStorage first):
   ```java
   @Entity
   public class SolverTimingRecord extends PanacheEntity {
       public Instant timestamp;
       public String profile;  // DB profile name
       public int lessonCount;
       public int labBlockCount;
       public int preAssignSeconds;
       public int chSeconds;
       public int lsSeconds;
       public int finalHardScore;
       public int finalMediumScore;
       public int finalSoftScore;
   }
   ```

2. **ScheduleService.java** - Record timing on solver completion:
   ```java
   // In finalBestSolutionConsumer callback
   SolverTimingRecord record = new SolverTimingRecord();
   record.timestamp = Instant.now();
   record.lessonCount = schedule.getLessons().size();
   record.labBlockCount = schedule.getLabBlocks().size();
   record.preAssignSeconds = preAssignDuration;
   record.chSeconds = chDuration;
   record.lsSeconds = lsDuration;
   // ... scores
   record.persist();
   ```

3. **New REST endpoint** for historical averages:
   ```java
   @GET
   @Path("/api/schedule/timing-estimates")
   public TimingEstimates getTimingEstimates() {
       // Query recent records, compute averages by entity count bucket
   }
   ```

### Phase 2: Frontend Display

4. **SolverControls.tsx** - Add estimate display:
   - Fetch timing estimates on mount
   - Show "Estimated: X-Y min" for each phase
   - During execution, show progress bar: `elapsed / estimated`

5. **UI Layout** (in elapsed section):
   ```tsx
   <span style={styles.estimate}>
     Est: Pre {preEstimate}s | CH {chEstimate}s | LS {lsEstimate}s
   </span>
   {isRunning && (
     <span style={styles.progress}>
       {phaseName}: {elapsed}s / ~{estimated}s
       {elapsed > estimated && ' (exceeding estimate)'}
     </span>
   )}
   ```

### Phase 3: Simple localStorage Fallback (MVP)

If database persistence is too complex for initial implementation, use localStorage:

6. **localStorage format**:
   ```typescript
   interface TimingHistory {
     records: {
       timestamp: string;
       entityCount: number;
       preAssignSeconds: number;
       chSeconds: number;
       lsSeconds: number;
     }[];
   }
   ```

7. **Estimation logic**:
   ```typescript
   function getEstimate(currentCount: number, records: TimingRecord[]): Estimate {
     // Find records with similar entity counts (±10%)
     // Return average of their times
     // If <3 similar records, use static fallback
   }
   ```

### Static Fallback Values
Based on benchmark data (~7000-8700 entities):
- Pre-planning: 15-30 seconds
- CH: 150-230 seconds (varies by branch/architecture)
- LS: User-configured (default from solverConfig.xml)

### Files to Modify
**Backend (optional for MVP):**
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/domain/SolverTimingRecord.java` (new)
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/service/ScheduleService.java`
- `/home/user/data/repo/personal/planner/src/main/java/com/school/planner/rest/ScheduleResource.java`

**Frontend:**
- `/home/user/data/repo/personal/planner/src/main/webui/src/components/SolverControls.tsx`
- `/home/user/data/repo/personal/planner/src/main/webui/src/api/scheduleApi.ts`
- `/home/user/data/repo/personal/planner/src/main/webui/src/types/domain.ts`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/en/schedule.json`
- `/home/user/data/repo/personal/planner/src/main/webui/src/i18n/locales/it/schedule.json`

### Incremental Implementation
1. **v1**: Static estimates based on known benchmark data
2. **v2**: localStorage-based history (frontend only)
3. **v3**: Backend persistence with proper entity-based estimates

### Testing
1. Run solver multiple times with same dataset
2. Verify estimates converge toward actual times
3. Test with different entity counts to verify scaling
4. Test fallback when no history exists
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Data collection + display + estimation logic |
| Risk | 3 | Historical data management |
| Testing | 3 | Accuracy verification |
| Dependencies | 3 | Depends on task-040, task-050 |
| **Total** | **3** | 1-2 days (after dependencies) |

---

## Priority: **LOW** (Future Enhancement)

Advanced feature requiring data collection infrastructure. Defer to future iteration.
<!-- SECTION:NOTES:END -->
