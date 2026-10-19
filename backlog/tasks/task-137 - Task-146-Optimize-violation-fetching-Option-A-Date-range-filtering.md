---
id: TASK-137
title: 'Task-146: Optimize violation fetching - Option A: Date range filtering'
status: Done
assignee:
  - claude
created_date: '2026-02-28 15:31'
updated_date: '2026-03-09 19:50'
labels:
  - performance
  - optimization
  - violations
  - backend
  - frontend
dependencies: []
priority: medium
ordinal: 110000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Explore optimizing the lesson violations API by adding date range filtering instead of fetching all 8000+ lessons.

## Problem
Current implementation fetches violations for all lessons in the schedule (~8000+), even when only ~150 cells are visible in the week view.

## Proposed Solution
Add `startDate` and `endDate` query parameters to `/api/schedule/analysis/lessons` endpoint. Backend filters by timeslot date range, frontend requests only for visible week.

## Implementation Tasks
- [ ] Add `startDate`/`endDate` params to backend endpoint
- [ ] Add SQL WHERE clause to filter by timeslot date
- [ ] Update `useLessonViolations` hook to accept date range
- [ ] Pass current week's date range from WeekViewGrid/TimelineView
- [ ] For TimelineView, consider larger window (e.g., 2 weeks buffer)

## Pros
- Simple to implement
- Single request per view change
- Efficient SQL filtering

## Cons
- Still fetches some invisible cells (within date range but off-screen)
- TimelineView may need larger windows for smooth scrolling
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Notes

### Backend Changes

**ScheduleResource.java** (line ~326):
- Add `@QueryParam("startDate") LocalDate startDate` and `@QueryParam("endDate") LocalDate endDate`
- Use defaults (1900-01-01 to 2100-12-31) if params not provided for backward compatibility

**ScheduleService.java** (line ~331):
- Change method signature to accept `LocalDate startDate, LocalDate endDate`
- Use Panache queries to filter by timeslot date:
  - Lessons: `Lesson.find("timeslot.startDateTime >= ?1 and timeslot.startDateTime <= ?2", ...)`
  - LabBlocks: `LabBlock.find("startTimeslot.startDateTime >= ?1 and startTimeslot.startDateTime <= ?2", ...)`
- Convert LocalDate to LocalDateTime with `atStartOfDay()` and `atTime(LocalTime.MAX)`

### Frontend Changes

**scheduleApi.ts**:
- Add `DateRange` interface with `start` and `end` string fields
- Update `analyzeLessonViolations` to accept optional `dateRange` param
- Build query string with `startDate` and `endDate` params

**useLessonViolations.ts**:
- Change signature to accept options object with `solverStatus`, `enabled`, `dateRange`
- Track previous date range with `useRef` to detect changes
- Refetch when date range changes OR solver stops

**ScheduleTimeline.tsx**:
- Compute `violationDateRange` from `days` array using `format(date, 'yyyy-MM-dd')`
- For week view: use first and last day of current week
- For timeline view: currently returns `undefined` (fetches all) - could be optimized later with larger window

## Verification (2026-03-01)

**Status: ✅ FULLY IMPLEMENTED**

All implementation tasks verified complete:

- Backend: `ScheduleResource.java:115-116` accepts startDate/endDate query params with defaults (1900-01-01 to 2100-12-31)

- Service: `ScheduleService.java` filters by timeslot date range

- Frontend API: `scheduleApi.ts` has `DateRange` interface and `analyzeLessonViolations(dateRange?)`

- Hook: `useLessonViolations.ts` accepts `dateRange` in options, refetches on range change

Implementation includes backward compatibility (works without date range) and smart refetching (triggers when solver stops OR date range changes).
<!-- SECTION:NOTES:END -->
