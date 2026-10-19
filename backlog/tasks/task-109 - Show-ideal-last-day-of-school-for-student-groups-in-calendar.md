---
id: TASK-109
title: Show ideal last day of school for student groups in calendar
status: Done
assignee: []
created_date: '2026-02-24 05:25'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - enhancement
  - student-groups
dependencies: []
priority: medium
ordinal: 38750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Human planners need to see when each student group should ideally finish their curriculum to plan the school year effectively. Currently, there's no visual indicator showing the projected completion date based on scheduled hours vs. required hours.

## Business Context

Each student group has a set number of course hours to complete. The ideal last day is calculated by:
1. Taking the total required hours for the group (sum of all Course.totalHours)
2. Accounting for available school days (excluding holidays and weekends)
3. Subtracting stage/internship days (from StagePattern)
4. Computing when all hours would be scheduled if evenly distributed

## Proposed Solution

Add a visual marker in the calendar timeline view showing the "ideal last day" for each student group. This could be:
- A vertical line indicator at the calculated date
- A badge/label showing the date in the group's row
- A tooltip showing the calculation breakdown

## Data Requirements

- Course.totalHours per student group
- SchoolHoliday dates
- StagePattern periods (already available as `stageDaysByGroup`)
- School year boundaries (SCHOOL_YEAR_START, SCHOOL_YEAR_END)

## Algorithm Sketch

```
idealEndDate = computeIdealEndDate(groupId):
  totalHours = sum(course.totalHours for group's courses)
  availableDays = allSchoolDays - holidays - weekends
  availableDays = availableDays - stageDaysForGroup(groupId)
  
  # 6 slots per day, need to find day where cumulative slots >= totalHours
  cumulative = 0
  for day in sorted(availableDays):
    cumulative += 6  # max slots per day
    if cumulative >= totalHours:
      return day
  return SCHOOL_YEAR_END
```

## Implementation Notes

- Add computation in `useScheduleData.ts` hook (similar to `stageDaysByGroup`)
- Display in `WeekViewGrid` or `TimelineView` components
- Consider showing both "ideal" and "projected" (based on current scheduling progress) dates
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each student group shows its ideal last school day in the calendar view
- [ ] #2 Calculation correctly accounts for holidays, weekends, and stage days
- [ ] #3 Visual indicator is visible but does not obstruct schedule viewing
- [ ] #4 Hovering/clicking the indicator shows calculation breakdown (total hours, excluded days)
- [ ] #5 Works in both Week view and Timeline view modes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
- Add computation in `useScheduleData.ts` hook (similar to `stageDaysByGroup`)
- Display in `WeekViewGrid` or `TimelineView` components
- Consider showing both "ideal" and "projected" (based on current scheduling progress) dates
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:NOTES:END -->
