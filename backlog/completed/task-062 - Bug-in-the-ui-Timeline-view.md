---
id: task-062
title: Bug in the ui Timeline view
status: Done
assignee: []
created_date: '2026-02-21 09:46'
updated_date: '2026-02-21 15:28'
labels: []
dependencies: []
priority: high
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
![image](/home/user/Pictures/Screenshots/Screenshot From 2026-02-21 10-44-05.png)

as you can see there are 2 horizontal scroll bars, and a couple of empty columns on the left
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Fix Applied

- Removed react-window virtualization that created nested scroll containers

- Created single unified scroll container for header + content

- Made time column sticky on left during horizontal scroll

- Removed listRef prop from TimelineViewProps

- Removed useListRef import and listRef variable from ScheduleTimeline

Commit: d4f8f18
<!-- SECTION:NOTES:END -->
