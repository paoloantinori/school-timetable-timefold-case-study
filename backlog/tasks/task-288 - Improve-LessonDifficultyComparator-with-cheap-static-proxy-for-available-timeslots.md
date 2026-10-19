---
id: TASK-288
title: >-
  Improve LessonDifficultyComparator with cheap static proxy for
  available-timeslots
status: Done
assignee:
  - claude
created_date: '2026-03-22 05:18'
updated_date: '2026-03-22 06:39'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace the current static LessonDifficultyComparator with a better static proxy that correlates with available timeslots without actually counting them. This is the cheaper alternative to full dynamic counting.

**Approach**: Compute a difficulty score from static entity properties that correlate with timeslot scarcity:
- Teacher vacation/unavailability count (more unavailable = fewer slots = harder)
- Required room tags count (already used, but could weight more heavily)
- Group total hours density (larger group = more competition for slots)
- Stage pattern presence (groups with stage dates have reduced horizon = harder)
- Whether the lesson is a lab/pinned lesson (pinned = already placed = skip)

**Trade-offs**:
- Much cheaper than dynamic counting (O(lessons) at solve start, no timeslot iteration)
- Less accurate than dynamic counting (proxy, not exact)
- Good enough to significantly improve CH ordering over current comparator
- Still only affects CH initial solution; LS then improves from there

**Current state**: LessonDifficultyComparator sorts by requiredRoomTags.size() then course.totalHours — very coarse, ignores teacher availability and group density entirely.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Replaced the 2-signal LessonDifficultyComparator (roomTags.size + totalHours) with a 5-level cascade using O(1) entity properties:

1. **pinned** (last) — already placed, trivially easy
2. **teacher.external** (first) — T28 has ~30 days vs ~200 for internal teachers
3. **professionalizzanti** (first) — stage blackout removes weeks from horizon
4. **requiredRoomTags.size()** (existing signal) — more tags = fewer rooms
5. **maxHoursPerDay inverted** — lower limit = fewer daily slots = harder

Dropped signals from original task: `idealEndDateVrpBuffered` (transient, risky during cloning), `isLab` (perfectly correlated with roomTags.size() > 0), `group total hours density` (requires cross-entity iteration, not O(1)).

Smoke test: CH phases ran without errors, score progressed normally (-222hard → -221hard in ~1 min of LS). Build succeeded, no other files changed.
<!-- SECTION:FINAL_SUMMARY:END -->
