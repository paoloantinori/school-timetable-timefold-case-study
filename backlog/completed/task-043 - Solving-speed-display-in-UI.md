---
id: task-043
title: Solving speed display in UI
status: Done
assignee: []
created_date: '2026-02-19 20:14'
updated_date: '2026-02-21 10:31'
labels:
  - claude-planned
  - ui
  - solver
dependencies: []
priority: medium
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Users cannot see the solving speed (score changes per second or per minute) during solver execution. This makes it hard to understand how much progress to expect after a specific moment, and whether the solver is making meaningful improvements or has stalled.

## User Value

- Understand solver progress at a glance
- Predict how long to wait for meaningful improvements
- Detect when solver has stalled (no progress)
- Compare solver performance across runs

## Current State

The `SolverControls.tsx` component already displays:
- Elapsed time
- CH time and LS time (construction heuristic vs local search)
- Current score

But it does NOT show the rate of score improvement over time.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given the solver is running, when at least 10 seconds have elapsed, then a 'speed' metric is displayed showing score change per minute (e.g., '+2.5k hard/min')
- [x] #2 Given the solver is running in local search phase, when the speed is displayed, then it shows hard score improvement rate (since hard score is the primary concern)
- [x] #3 Given the solver has made no score improvement for 60+ seconds, when the speed metric is displayed, then it shows a warning indicator (e.g., 'stalled' or '0/min')
- [x] #4 Given the solver is in construction heuristic phase, when speed would be displayed, then show 'CH phase...' or similar instead (speed is not meaningful during CH)
- [x] #5 Given the solver stops, when the final speed is displayed, then it shows the average speed over the entire LS phase
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Files to Modify
- `src/main/webui/src/components/SolverControls.tsx` (primary)
- `src/main/webui/src/i18n/locales/en/schedule.json` (add i18n keys)
- `src/main/webui/src/i18n/locales/it/schedule.json` (add i18n keys)

### Approach

1. **Track score history** - Store an array of `{timestamp, hardScore, softScore}` samples during solving (sample every 2-5 seconds to avoid noise)

2. **Calculate speed** - Compute slope over recent samples (last 30-60 seconds) using linear regression or simple delta:
   - Speed = (currentHardScore - oldestSampleHardScore) / elapsedMinutes
   - Format as '+X.Xk hard/min' or '-X.Xk hard/min'

3. **Display in UI** - Add new `<span>` element next to elapsed time:
   ```
   Speed: +2.5k hard/min | Elapsed: 3m 42s | CH: 48s | LS: 2m 54s
   ```

4. **Handle edge cases**:
   - CH phase: Display 'CH phase...' (no speed calculation)
   - First 10 seconds: Display 'calculating...'
   - No improvement for 60s: Display 'stalled (0/min)' in warning color
   - Solver stopped: Show average speed over entire LS phase

5. **Color coding**:
   - Positive speed (improving): Green text
   - Near-zero speed (0-500/min): Gray/muted text
   - Negative speed (worsening): Red text (should not happen in normal operation)

### Data Structure

```typescript
interface ScoreSample {
  timestamp: number;      // epoch ms
  hardScore: number;      // parsed from score string
  softScore: number;
}

// In component state
const [scoreHistory, setScoreHistory] = useState<ScoreSample[]>([]);
```

### Score Parsing

Parse score format like '0hard/-12345678soft/-987654321medium' to extract numbers:
```typescript
const parseScore = (scoreStr: string) => {
  const hardMatch = scoreStr.match(/(-?\d+)hard/);
  const softMatch = scoreStr.match(/(-?\d+)soft/);
  return {
    hard: hardMatch ? parseInt(hardMatch[1]) : 0,
    soft: softMatch ? parseInt(softMatch[1]) : 0,
  };
};
```

### Sampling Logic

Add to the existing polling `useEffect`:
```typescript
useEffect(() => {
  if (isSolving && isInLS && schedule?.score) {
    const parsed = parseScore(String(schedule.score));
    setScoreHistory(prev => {
      const newHistory = [...prev, { timestamp: Date.now(), ...parsed }];
      // Keep only last 30 samples (~60 seconds at 2s polling)
      return newHistory.slice(-30);
    });
  }
}, [schedule?.score, isSolving, isInLS]);
```

### Risks

| Risk | Mitigation |
|------|------------|
| Score parsing complexity | Score format is stable; write unit tests for parser |
| Noisy speed readings | Use 30+ second window for smoothing |
| Memory usage from history array | Cap at 30-50 samples |
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **2/5** (Low-Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 2 | Frontend only, score parsing |
| Risk | 2 | Display only |
| Testing | 2 | Visual verification |
| Dependencies | 1 | None |
| **Total** | **2** | 2-4 hours |

---

## Priority: **MEDIUM** (UX Enhancement)

Understanding solver progress is key UX improvement. Standalone frontend work.
<!-- SECTION:NOTES:END -->
