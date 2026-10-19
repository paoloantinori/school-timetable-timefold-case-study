---
id: task-024
title: ui - sidebar
status: Done
assignee: []
created_date: '2026-02-18 06:15'
updated_date: '2026-02-21 08:34'
labels:
  - ui
  - claude-planned
  - 'complexity:high'
  - playwright-tests
dependencies: []
priority: medium
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The current sidebar only shows unassigned lessons, which provides limited value to users. Users need visibility into the solver's progress and constraint violations to understand the schedule quality and take corrective actions. A redesigned sidebar should show real-time statistics, violation summaries, and provide quick access to common actions.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Stats tab displays Hard/Medium/Soft scores with color-coded cards and violation counts
- [ ] #2 #2 Assignment progress bar accurately shows X/Y lessons assigned with percentage
- [ ] #3 #3 Top 5 hard constraint violations are listed with constraint ID and match count, matching the Analysis modal
- [ ] #4 #4 Unassigned tab shows all unassigned lessons grouped by subject with counts
- [ ] #5 #5 Quick action buttons (Solve, Stop, Reset, Pre-assign, Analyze) execute their respective operations
- [ ] #6 #6 Tab selection persists during the session when switching between tabs

- [ ] #7 #7 Performance: sidebar renders without lag when displaying 8000+ lessons
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Playwright Validation Tests

### Prerequisites
- App running at http://localhost:8080
- Database profile with seed data (e.g., `./planner.sh start dev`)
- Fresh schedule state (run reset if needed)

### Test 1: Navigation and Tab Switching

```
# Navigate to app
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")
mcp__playwright__browser_snapshot()

# Click Stats tab
mcp__playwright__browser_click(ref: "tab with name 'Stats'")
mcp__playwright__browser_snapshot()
# Verify: Stats tab content visible (score cards, progress bar)

# Click Unassigned tab
mcp__playwright__browser_click(ref: "tab with name 'Unassigned'")
mcp__playwright__browser_snapshot()
# Verify: Unassigned lessons list visible

# Click Actions tab
mcp__playwright__browser_click(ref: "tab with name 'Actions'")
mcp__playwright__browser_snapshot()
# Verify: Action buttons visible (Solve, Stop, Reset, Pre-assign)
```

### Test 2: Score Cards Display After Solving

```
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")

# Click Solve button
mcp__playwright__browser_click(ref: "button with name 'Solve'")
mcp__playwright__browser_wait_for(time: 5)

# Switch to Stats tab
mcp__playwright__browser_click(ref: "tab with name 'Stats'")
mcp__playwright__browser_snapshot()
# Verify: Hard/Medium/Soft score cards display values
# Verify: Cards have correct color coding (red=hard, amber=medium, green=soft)
# Verify: Violation counts are non-negative integers

mcp__playwright__browser_wait_for(time: 10)
mcp__playwright__browser_snapshot()
# Verify: Score values update (may improve or change)
```

### Test 3: Progress Bar Updates

```
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")

# Reset to ensure unassigned state
mcp__playwright__browser_click(ref: "tab with name 'Actions'")
mcp__playwright__browser_click(ref: "button with name 'Reset'")

# Check initial progress
mcp__playwright__browser_click(ref: "tab with name 'Stats'")
mcp__playwright__browser_snapshot()
# Verify: Progress bar shows low percentage (mostly unassigned)
# Verify: Label shows "X/Y lessons assigned" where X < Y

# Start solving
mcp__playwright__browser_click(ref: "tab with name 'Actions'")
mcp__playwright__browser_click(ref: "button with name 'Solve'")
mcp__playwright__browser_wait_for(time: 15)

# Check progress after solving
mcp__playwright__browser_click(ref: "tab with name 'Stats'")
mcp__playwright__browser_snapshot()
# Verify: Progress bar shows higher percentage
```

### Test 4: Top Violations List

```
# Prerequisite: Run solver long enough to generate violations
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")
mcp__playwright__browser_click(ref: "button with name 'Solve'")
mcp__playwright__browser_wait_for(time: 10)

# Navigate to Stats tab
mcp__playwright__browser_click(ref: "tab with name 'Stats'")
mcp__playwright__browser_snapshot()
# Verify: "Top Violations" section visible
# Verify: List shows constraint IDs (H1, H2, H3, etc.)
# Verify: Each item has violation count in parentheses
# Verify: Items sorted by count (descending)

# Click on a violation to open Analysis modal
mcp__playwright__browser_click(ref: "list item containing 'H1'")
mcp__playwright__browser_snapshot()
# Verify: Analysis modal opens
# Verify: Modal shows same constraint details
```

### Test 5: Action Buttons Functional

```
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")
mcp__playwright__browser_click(ref: "tab with name 'Actions'")

# Test Solve button
mcp__playwright__browser_click(ref: "button with name 'Solve'")
mcp__playwright__browser_wait_for(time: 2)
mcp__playwright__browser_snapshot()
# Verify: Button state changes to "Stop" or shows solving indicator

# Test Stop button
mcp__playwright__browser_click(ref: "button with name 'Stop'")
mcp__playwright__browser_wait_for(time: 2)
mcp__playwright__browser_snapshot()
# Verify: Solving stops, button reverts to "Solve"

# Test Reset button
mcp__playwright__browser_click(ref: "button with name 'Reset'")
mcp__playwright__browser_wait_for(time: 2)
mcp__playwright__browser_snapshot()
# Verify: Schedule resets, assignments cleared

# Test Pre-assign button
mcp__playwright__browser_click(ref: "button with name 'Pre-assign'")
mcp__playwright__browser_wait_for(time: 5)
mcp__playwright__browser_snapshot()
# Verify: Pre-assignment runs, some lessons become pinned
```

### Test 6: Tab State Persistence

```
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")

# Switch to Unassigned tab
mcp__playwright__browser_click(ref: "tab with name 'Unassigned'")
mcp__playwright__browser_snapshot()
# Verify: Unassigned tab is active

# Trigger an action (e.g., Solve)
mcp__playwright__browser_click(ref: "button with name 'Solve'")
mcp__playwright__browser_wait_for(time: 5)
mcp__playwright__browser_snapshot()
# Verify: Tab remains on Unassigned (state persists during solve)

# Stop solving
mcp__playwright__browser_click(ref: "button with name 'Stop'")
mcp__playwright__browser_wait_for(time: 2)

# Refresh page
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_wait_for(text: "Schedule")
mcp__playwright__browser_snapshot()
# Verify: Tab returns to default (Stats) on fresh page load
```

### Validation Checklist

After running all tests, verify:

- [ ] All three tabs are clickable and show correct content
- [ ] Score cards display Hard/Medium/Soft scores with correct colors
- [ ] Progress bar accurately reflects assignment state
- [ ] Top violations list matches Analysis modal data
- [ ] Solve button starts solver and changes to Stop
- [ ] Stop button terminates solver
- [ ] Reset button clears all assignments
- [ ] Pre-assign button runs pre-assignment pipeline
- [ ] Tab selection persists during solve operations
- [ ] Page refresh resets tab to default
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **4/5** (High)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 4 | New component, 3 tabs, API integration |
| Risk | 3 | Replaces existing sidebar |
| Testing | 4 | Full UI testing |
| Dependencies | 2 | Soft dep on task-026 for violation data |
| **Total** | **4** | 3-5 days |

---

## Priority: **MEDIUM** (High User Value)

Sidebar redesign transforms user understanding of solver state. Complexity: HIGH.
<!-- SECTION:NOTES:END -->
