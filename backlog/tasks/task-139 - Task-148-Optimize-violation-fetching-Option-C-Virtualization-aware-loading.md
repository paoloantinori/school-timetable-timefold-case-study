---
id: TASK-139
title: 'Task-148: Optimize violation fetching - Option C: Virtualization-aware loading'
status: To Do
assignee: []
created_date: '2026-02-28 15:31'
updated_date: '2026-03-03 07:00'
labels:
  - performance
  - optimization
  - violations
  - frontend
  - virtualization
dependencies: []
priority: low
ordinal: 62000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Explore optimizing the lesson violations API by implementing virtualization-aware loading that tracks which days are rendered and requests violations only for those date ranges.

## Problem
Current implementation fetches violations for all lessons in the schedule (~8000+), even when only ~150 cells are visible in the week view.

## Proposed Solution
Hook tracks which days are rendered in TimelineView (via react-window's `onItemsRendered` callback). Requests violations only for those date ranges. Auto-updates as user scrolls.

## Implementation Tasks
- [ ] Use react-window's `onItemsRendered` to track visible days
- [ ] Hook maintains Set of currently visible date ranges
- [ ] Debounce requests to avoid spamming on fast scroll
- [ ] Cache already-fetched date ranges
- [ ] Invalidate cache when solver stops
- [ ] For WeekViewGrid, use current week's days

## Pros
- Efficient for TimelineView's infinite scroll
- Only fetches what user can see
- Good UX - no waiting for full load

## Cons
- Most complex implementation
- Need to manage cache invalidation
- Multiple smaller requests vs one large
- More state to track
<!-- SECTION:DESCRIPTION:END -->
