---
id: TASK-188
title: Solver Progression Dashboard - Phase-by-Phase Runtime Statistics
status: Done
assignee: []
created_date: '2026-03-07 06:05'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - frontend
  - backend
  - solver
  - monitoring
dependencies: []
priority: medium
ordinal: 94000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview

Add a dedicated solver progression/reflection section to the web application that shows phase-by-phase solver statistics similar to what's available in logs - phase start/end times, duration, score progression, move counts.

## Requirements

**When visible**: Live during solving (real-time updates)
**Persistence**: In-memory + JSON export (no DB storage needed)
**Detail Level**: Phase summary only (like the log report)

## Backend Implementation

### 1. PhaseProgressionService.java
Create a new service to track phase progression history:

```java
@ApplicationScoped
public class PhaseProgressionService {
    
    public static class PhaseRecord {
        public int phaseNumber;
        public String phaseName; // "CH-LabBlocks", "CH-Lessons", "LS-Compactness", etc.
        public Instant startTime;
        public Instant endTime;
        public String startScore;
        public String endScore;
        public long timeSpentMillis;
        public boolean isComplete;
    }
    
    private List<PhaseRecord> history = new CopyOnWriteArrayList<>();
    
    // Hook into BestSolutionChangedEvent to infer phase transitions
    public void onBestSolutionChanged(HardMediumSoftScore score, long timeSpentMillis);
    
    public List<PhaseRecord> getProgression();
    public void reset(); // Called on solve start/stop
    public String exportJson(); // For download
}
```

### 2. PhaseProgressionResource.java
New REST endpoint:
- `GET /api/phase-progression` - Returns phase history
- `GET /api/phase-progression/export` - Returns JSON file download

### 3. Integration with ScheduleService
Wire PhaseProgressionService into the existing solver lifecycle:
- Call `reset()` when solver starts
- Call `onBestSolutionChanged()` in the best solution event consumer

## Frontend Implementation

### 1. ProgressionPanel.tsx
New modal component showing:
- Phase cards with: number, name, duration, score range
- Current phase highlighted (if solving)
- Timeline visualization of phases
- Export JSON button

### 2. Integration with SolverControls
Add a "Progression" button next to "Analyze" that opens the modal.

### 3. API Client
Add to `scheduleApi.ts`:
```typescript
getPhaseProgression: () => apiClient.get('/phase-progression'),
exportPhaseProgression: () => apiClient.get('/phase-progression/export', { responseType: 'blob' }),
```

## Phase Detection Logic

Infer phases from score patterns:

| Score Pattern | Inferred Phase |
|--------------|----------------|
| `0hard/*` with very few entities placed | CH Phase 0 (LabBlocks) |
| `negative hard/*` with many entities | CH Phase 1 (Lessons) |
| `hard → 0` with medium fluctuating | LS Phase 2 (Compactness) |
| `hard=0`, `medium → better` | LS Phase 3 (Conflict Resolution) |
| `hard=0`, `medium stable`, `soft → better` | LS Phase 4 (Room Refinement) |

## Current State

- `PhaseTrackingListener.java` exists but only stores current phase (not history)
- `setPhase()` is never called during solving - it's a placeholder
- `SolverPhasesPage.tsx` is static documentation, not runtime data

## Files to Modify

**New files:**
- `src/main/java/com/school/planner/service/PhaseProgressionService.java`
- `src/main/java/com/school/planner/rest/PhaseProgressionResource.java`
- `src/main/webui/src/components/ProgressionPanel.tsx`

**Modify:**
- `src/main/java/com/school/planner/service/ScheduleService.java` - wire in progression service
- `src/main/webui/src/components/SolverControls.tsx` - add Progression button
- `src/main/webui/src/api/scheduleApi.ts` - add API methods
<!-- SECTION:DESCRIPTION:END -->
