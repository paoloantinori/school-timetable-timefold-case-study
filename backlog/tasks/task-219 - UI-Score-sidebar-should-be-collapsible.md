---
id: TASK-219
title: 'UI: Score sidebar should be collapsible'
status: Done
assignee: []
created_date: '2026-03-09 18:40'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - feature
  - sidebar
  - ux
dependencies: []
priority: low
ordinal: 15750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The ScheduleSidebar (right side panel showing constraint scores and legend) is always visible and takes up screen space. It should be collapsible.

**Current Behavior:**
- Sidebar is always expanded
- No way to hide it
- Takes up valuable screen space

**Expected Behavior:**
- Collapse/expand button (toggle) to hide/show sidebar
- Collapsed state should show minimal indicator or icon
- State should persist across page reloads (localStorage)

**Files to Investigate:**
- `src/main/webui/src/components/ScheduleSidebar.tsx`
- Similar patterns: `ScheduleFilterBar.tsx` (if it has collapse functionality)

**Implementation Notes:**
- Use localStorage to persist collapsed state
- Add a toggle button (chevron/icon) in the sidebar header
<!-- SECTION:DESCRIPTION:END -->
