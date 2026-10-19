---
id: task-014
title: dilute hours over the year
status: Done
assignee: []
created_date: '2026-02-17 16:38'
updated_date: '2026-02-17 20:10'
labels: []
dependencies: []
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
every subject should try to spread their hours over the year, to avoid the kids to completely lose touch with a subject.
ideally just 1 hour a week, more if there are more hours than weeks
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 S9 constraint added to SchoolScheduleConstraintProvider
- [ ] #2 Groups lessons by (studentGroup, subject, week)
- [ ] #3 Penalizes >1 lesson of same subject per week
- [ ] #4 Soft weight 10 (doesn't override hard constraints)
- [ ] #5 Pinned lessons excluded
- [ ] #6 Tests pass
- [ ] #7 Propagated to all 10 branches
<!-- AC:END -->
