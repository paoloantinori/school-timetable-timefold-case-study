---
id: TASK-138
title: 'Task-147: Optimize violation fetching - Option B: On-demand by lesson ID'
status: To Do
assignee: []
created_date: '2026-02-28 15:31'
updated_date: '2026-07-25 05:55'
labels:
  - performance
  - optimization
  - violations
  - backend
  - frontend
dependencies: []
priority: medium
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Explore optimizing the lesson violations API by fetching only for specific lesson IDs that are currently visible.

## Problem
Current implementation fetches violations for all lessons in the schedule (~8000+), even when only ~150 cells are visible in the week view.

## Proposed Solution
Frontend sends visible lesson IDs to the API. Backend only returns violations for those specific lessons.

## Implementation Tasks
- [ ] Change endpoint to POST or use query param for IDs
- [ ] Frontend tracks which lessons are currently rendered
- [ ] Send comma-separated list of visible lesson IDs
- [ ] Backend filters by `lesson.id IN (...)`
- [ ] Update hook to collect visible IDs from cells

## Pros
- Most precise - only fetches exactly what's visible
- Smallest possible payload

## Cons
- More network requests (one per scroll/viewport change)
- Need to track which lessons are rendered
- URL length limits for many IDs (use POST body)
- More complex state management
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Giro pulizia 2026-07-25: SCOPE RIDOTTO - il problema principale e' stato risolto per altra via (filtro per date-range su /analysis/lessons); resta solo il gap della vista timeline che fa fetch-all (ScheduleTimeline.tsx:185 'could optimize later'). Ri-titolare su quello se si riprende.
<!-- SECTION:NOTES:END -->
