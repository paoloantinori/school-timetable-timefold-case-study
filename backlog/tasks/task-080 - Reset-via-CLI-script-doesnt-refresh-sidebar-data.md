---
id: TASK-080
title: Reset via CLI script doesn't refresh sidebar data
status: Done
assignee: []
created_date: '2026-02-23 11:09'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: medium
ordinal: 132000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

When reset is invoked externally via `scripts/api.sh pre-assign-reset`, the backend clears pinned entities, but the frontend sidebar continues showing stale data because there's no polling trigger for external changes.

## Current Architecture

### Polling Mechanism (ScheduleTimeline.tsx lines 93-113)
```typescript
const isSolving = schedule?.solverStatus === 'SOLVING_ACTIVE';
const isPreAssigning = schedule?.preAssignmentStatus &&
  !['IDLE', 'COMPLETE', 'ERROR'].includes(schedule.preAssignmentStatus);
const shouldPoll = isSolving || isPreAssigning || solvingRequested;
```

Polling ONLY happens during solver/pre-assignment activity. External CLI operations have no UI notification.

### Sidebar Data Flow
```
ScheduleTimeline (fetches schedule)
        ↓ schedule prop
ScheduleSidebar (displays unassigned)
```

The sidebar has no independent refresh capability — it relies entirely on parent re-fetching.

## Solution Design

### Option A: Manual Refresh Button (Recommended for v1)

Add a refresh button to the sidebar that manually triggers `fetchSchedule()`.

**Placement Options:**
1. **Tab bar** — Small icon button next to tab labels
2. **Stats tab** — Below the progress bar
3. **Unassigned tab header** — Inline with "Unassigned" title

**Recommended: Tab bar** — Visible from all tabs, clear affordance.

### Option B: Timer-based Polling (Future Enhancement)

Add background polling when idle:
```typescript
// Poll every 30 seconds when solver is idle
useEffect(() => {
  if (isSolving || isPreAssigning) return;
  const interval = setInterval(fetchSchedule, 30000);
  return () => clearInterval(interval);
}, [isSolving, isPreAssigning]);
```

**Pros:** Automatic, no user action needed
**Cons:** Unnecessary API calls, wasted bandwidth

### Option C: WebSocket/SSE (Future Enhancement)

Backend pushes updates via WebSocket. Frontend listens and refreshes on change.

**Pros:** Immediate, efficient
**Cons:** Significant backend changes required

## Recommended Approach

**Option A (Manual Refresh Button)** as immediate fix.

## Component Design

### UI Placement
Add refresh button to sidebar tab bar:

```
┌─────────────────────────────────────┐
│ [Stats] [Unassigned] [Actions] [↻] │  ← Refresh button
├─────────────────────────────────────┤
│ ... content ...                     │
└─────────────────────────────────────┘
```

### Props Update

Add `onRefresh` prop to `ScheduleSidebar`:

```typescript
interface Props {
  schedule: SchoolSchedule;
  onSolve: () => void;
  onStop: () => void;
  onReset: () => void;
  onPreAssign: () => void;
  onAnalyze: () => void;
  onRefresh: () => void;  // NEW
  analysis: ConstraintAnalysis | null;
}
```

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/webui/src/components/ScheduleSidebar.tsx` | Add onRefresh prop, refresh button in tab bar |
| `src/main/webui/src/components/ScheduleTimeline.tsx` | Pass fetchSchedule as onRefresh prop |
| `src/main/webui/src/i18n/locales/en/schedule.json` | Add 'refresh' translation key |
| `src/main/webui/src/i18n/locales/it/schedule.json` | Add 'refresh' translation key |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sidebar includes a visible refresh button in the tab bar that triggers fetchSchedule()
- [ ] #2 Refresh button uses circular arrow icon consistent with UI patterns
- [ ] #3 Clicking refresh updates sidebar to show current unassigned lesson counts
- [ ] #4 Reset via api.sh pre-assign-reset followed by manual refresh shows correct cleared state
- [ ] #5 Refresh button is disabled (grayed out) during active solving or pre-assignment
- [ ] #6 Refresh button has tooltip explaining its function (i18n key: 'refresh')
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Steps

### Step 1: Update ScheduleSidebar Props (ScheduleSidebar.tsx)

Add `onRefresh` to interface:
```typescript
interface Props {
  schedule: SchoolSchedule;
  onSolve: () => void;
  onStop: () => void;
  onReset: () => void;
  onPreAssign: () => void;
  onAnalyze: () => void;
  onRefresh: () => void;  // Add this
  analysis: ConstraintAnalysis | null;
}
```

### Step 2: Add Refresh Button to Tab Bar

Replace tab navigation (line ~207):
```tsx
{/* Tab Navigation */}
<div className="flex border-b border-border">
  {(['stats', 'unassigned', 'actions'] as Tab[]).map((tab) => (
    <button ...>
      {t(`sidebar.${tab}`)}
    </button>
  ))}
  {/* Refresh button */}
  <button
    onClick={onRefresh}
    disabled={isSolving}
    className="ml-auto px-3 py-2 text-muted hover:text-primary transition-colors disabled:opacity-50"
    title={t('refresh')}
  >
    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} 
        d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  </button>
</div>
```

### Step 3: Update ScheduleTimeline.tsx

Pass `fetchSchedule` as `onRefresh`:
```tsx
<ScheduleSidebar
  schedule={schedule}
  onSolve={handleSolve}
  onStop={handleStop}
  onReset={handleReset}
  onPreAssign={handlePreAssign}
  onAnalyze={handleAnalyze}
  onRefresh={fetchSchedule}  // Add this
  analysis={sidebarAnalysis}
/>
```

### Step 4: Add Translation Keys

**en/schedule.json:**
```json
{
  "refresh": "Refresh data",
  ...
}
```

**it/schedule.json:**
```json
{
  "refresh": "Aggiorna dati",
  ...
}
```

## Testing

1. Open app with populated schedule
2. Note the unassigned count in sidebar
3. Run `./scripts/api/api.sh pre-assign-reset` externally
4. Click refresh button in sidebar
5. Verify unassigned count updates
6. Verify refresh button is disabled during solving
<!-- SECTION:NOTES:END -->
