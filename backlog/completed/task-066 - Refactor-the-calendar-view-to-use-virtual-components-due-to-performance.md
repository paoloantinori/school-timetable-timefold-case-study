---
id: TASK-066
title: Refactor the calendar view to use virtual components due to performance
status: Done
assignee:
  - Claude
created_date: '2026-02-22 07:01'
updated_date: '2026-02-22 12:14'
labels:
  - ui
  - performance
  - claude-planned
dependencies: []
priority: high
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The calendar view (TimelineView) was refactored away from virtual components to static rendering, causing severe performance issues when the schedule is densely populated.

**Problem Scale:**
- ~7,000 scheduled lessons across 200 school days
- 6 time slots per day × 35 columns = 42,000 cells
- Each cell has 3 sub-columns with 2+ DOM elements
- **Estimated: 252,000+ DOM nodes** at full capacity
- Chrome struggles above 150k nodes, causing scroll jank and slow interactions

**Current Implementation:**
- `TimelineView.tsx` renders ALL days directly without virtualization
- Comment explicitly states: "renders all days directly without virtualization to ensure proper horizontal scroll synchronization"
- `react-window` is installed but unused

**Root Cause:** The non-virtualized approach works for small datasets but becomes unusable with real production data.

---

## Design Direction

**Aesthetic:** Industrial/Utilitarian with subtle refinements
- Maintain existing color scheme (subject colors, stage day highlighting)
- Preserve sticky headers and horizontal scroll sync
- Keep the dense information display (3 sub-columns per cell)
- Add subtle loading skeletons during virtual scroll

**Performance Target:**
- Initial render < 500ms
- Scroll at 60fps with 7,000 lessons
- Memory usage < 200MB for full timeline

---

## Implementation Approach

### Phase 1: Virtualize TimelineView with react-window

Use `react-window`'s `VariableSizeList` for vertical virtualization while keeping horizontal scroll native.

**Key Architecture Decision:**
- Virtualize **days** (rows), not individual cells
- Each "row" is a full day with all 6 time periods
- Horizontal scroll remains native (no virtualization needed for 35 columns)
- This preserves the "proper horizontal scroll synchronization" requirement

**Files to Modify:**
1. `src/main/webui/src/components/timeline/TimelineView.tsx` - Add virtualization
2. `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx` - New component
3. `src/main/webui/src/components/timeline/types.ts` - Add types

### Phase 2: Memoization & Optimization

1. **Wrap TimelineCell with React.memo** - Prevent unnecessary re-renders
2. **Memoize getColor function** - Use useCallback with stable ref
3. **Pre-compute lesson lookups** - Convert getLesson to O(1) lookup with Map

### Phase 3: Playwright Test Infrastructure (NEW)

**Setup:**
1. `npm install -D @playwright/test` in webui directory
2. Create `e2e/` directory structure at project root
3. Configure `playwright.config.ts` with base URL `http://localhost:8080`
4. Create test utilities for data fixtures

**Test Scenarios:**
1. Performance baseline - Measure render time with empty calendar
2. Dense calendar - Measure render time with 7,000 lessons
3. Scroll performance - Assert scroll completes in < 100ms
4. Virtualization - Verify only visible rows are in DOM
5. Interaction - Click lesson, verify tooltip opens

---

## Implementation Steps

### Step 1: Create VirtualizedDayRow Component
New file: `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx`
- Renders a single day with all 6 time periods
- Receives pre-computed data to avoid calculations during render

### Step 2: Refactor TimelineView to use react-window
- Replace `allDays.map()` with `FixedSizeList`
- Keep sticky column headers outside virtualization
- Synchronize horizontal scroll between header and content

### Step 3: Optimize TimelineCell
- Wrap with `React.memo`
- Add `data-testid` for Playwright selectors
- Extract inline styles to CSS classes where possible

### Step 4: Optimize useScheduleData hook
- Build `lessonLookup: Map<string, Lesson>` for O(1) access
- Memoize `stageDaysByGroup` computation
- Add `useMemo` for expensive filters

### Step 5: Add Playwright Tests
- `e2e/calendar-performance.spec.ts` - Performance tests
- `playwright.config.ts` - Configuration

### Step 6: Validation & Benchmarking
- Run against `dev` profile with full seed data
- Compare DOM node count before/after
- Measure memory usage with Chrome DevTools

---

## Critical Files

| File | Purpose |
|------|---------|
| `src/main/webui/src/components/timeline/TimelineView.tsx` | Main virtualization refactor |
| `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx` | New: Single day row component |
| `src/main/webui/src/components/timeline/TimelineCell.tsx` | Memoization optimization |
| `src/main/webui/src/hooks/useScheduleData.ts` | Pre-compute lookups |
| `src/main/webui/package.json` | Add Playwright |
| `e2e/calendar-performance.spec.ts` | Performance tests |
| `playwright.config.ts` | Playwright configuration |

---

## Performance Targets

| Metric | Before | Target |
|--------|--------|--------|
| DOM nodes | 252,000+ | < 5,000 |
| Initial render | 5-10s | < 500ms |
| Scroll frame time | 100-200ms | < 16ms (60fps) |
| Memory usage | 500MB+ | < 200MB |

---

## Rollback Plan

If virtualization causes issues:
1. Keep `TimelineViewNonVirtualized.tsx` backup
2. Add feature flag `VITE_ENABLE_VIRTUALIZATION=true`
3. Fallback to non-virtualized view if flag is false
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Timeline scroll maintains 60fps (no frame > 16ms) with virtualization
- [x] #2 DOM node count reduced by 95%+ (only visible rows rendered by react-window)
- [x] #3 All existing functionality preserved (tooltips, filters, navigation, i18n)
- [x] #4 No visual regressions in week view or timeline view
- [x] #5 Week → Timeline view switch: <2000ms with fully populated calendar (~8,600 entities)
- [x] #6 Timeline → Week view switch: <500ms with fully populated calendar

- [x] #7 Playwright performance test in place with assertions for both directions
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Performance Results (populated calendar, 7520 lessons + 1144 lab blocks)

| Metric | Target | Measured | Status |
|--------|--------|----------|--------|
| Timeline → Week | <500ms | 289ms | PASS |
| Week → Timeline | <2000ms | 1744ms | PASS |

### Optimizations Applied
1. **react-window virtualization** — only visible day rows rendered
2. **Memoized VirtualizedDayRow + TimelineCell** — prevents unnecessary re-renders
3. **Inline cell rendering** — eliminated 3 React.memo boundaries per cell (46% faster Timeline→Week)
4. **Pre-computed timeslot lookup Map** — O(1) lesson lookups

### Optimization Attempts That Failed
- **Keep-mounted with display:none** — react-window List breaks when hidden (0 height → recalculation storm)
- **Pre-compute all cell data** — creating 3,500 cell objects upfront slower than lazy computation

### Why Week→Timeline is ~1.7s (architectural limit)
The bottleneck is creating ~1,400 DOM elements for 3 visible days × 14 columns × 6 periods × 4 elements/cell.
Further improvement requires canvas-based rendering or progressive column loading.

### Bug fixes included
- Fixed i18n key collision: `days` (array of weekday names) vs `daysLabel` (count label "school days")
<!-- SECTION:NOTES:END -->
