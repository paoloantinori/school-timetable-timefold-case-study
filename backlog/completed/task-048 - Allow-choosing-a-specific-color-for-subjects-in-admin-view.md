---
id: task-048
title: Allow choosing a specific color for subjects in admin view
status: Done
assignee: []
created_date: '2026-02-20 20:22'
updated_date: '2026-02-21 11:45'
labels:
  - ui
  - claude-planned
  - enhancement
dependencies: []
priority: low
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement
Currently, subject colors in the schedule timeline are auto-generated using a hash-based assignment from a fixed 15-color palette (`COLORS` array in `ScheduleTimeline.tsx`). This means the same subject may get different colors across sessions or when subjects are added/removed. Users (human planners) want to assign specific colors to subjects and have them persist consistently.

## Current Behavior
- Colors are generated client-side via `getColor(subject)` function (line 539-547)
- Uses `subjectColorMap.current` (a `useRef<Map>`) to store subject→color mapping
- Assignment is deterministic within a session but not persisted
- No database field exists for storing subject color

## Business Context
Human planners use Excel spreadsheets with fixed subject colors. Matching these colors in the digital tool improves usability and reduces cognitive load when reviewing schedules.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given I am on the Courses Admin page, When I create or edit a Course, Then I see a color picker input field for the subject color
- [x] #2 Given I set a color for a subject (e.g., Matematica = #3b82f6), When I save the course, Then the color is persisted to the database
- [x] #3 Given a course has a saved subject color, When I view the schedule timeline, Then lessons for that course use the saved color instead of auto-generated color
- [x] #4 Given a course has no saved color, When I view the schedule timeline, Then the system falls back to auto-generating a color from the COLORS palette
- [x] #5 Given I am editing a course with an existing color, When the edit form loads, Then the color picker shows the currently saved color
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Phase 1: Backend - Add Color Field
**File**: `src/main/java/com/school/planner/domain/Course.java`
```java
@Column(length = 7)
public String color; // Nullable hex color like "#3b82f6"
```

### Phase 2: Frontend Types
**File**: `src/main/webui/src/types/domain.ts`
```typescript
export interface Course {
  // ... existing fields
  color?: string | null;  // Hex color for subject display
}
```

### Phase 3: Admin UI Color Picker
**File**: `src/main/webui/src/components/admin/CoursesAdmin.tsx`
- Add state: `const [color, setColor] = useState('#3b82f6');`
- Add color picker input in form (after `isLab` checkbox)
- Include `color` in `handleSave` payload
- Set `color` when editing existing course

### Phase 4: Timeline Integration
**File**: `src/main/webui/src/components/ScheduleTimeline.tsx`
- Modify `getColor()` function:
```typescript
const getColor = (course: Course | null): string => {
  if (course?.color) return course.color;
  const subject = course?.subject || 'unknown';
  // ... existing auto-generation logic
};
```

### Phase 5: Testing
- Manual test: create course with color, verify in timeline
- Test fallback: course without color uses auto-generated
- Test edit: change color, verify update persists

### Files to Modify
1. `src/main/java/com/school/planner/domain/Course.java`
2. `src/main/webui/src/types/domain.ts`
3. `src/main/webui/src/components/admin/CoursesAdmin.tsx`
4. `src/main/webui/src/components/ScheduleTimeline.tsx`
5. `src/main/webui/src/components/LessonTooltip.tsx` (if it uses colors)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Backend field + admin UI + timeline |
| Risk | 2 | Additive feature |
| Testing | 3 | Admin + timeline testing |
| Dependencies | 1 | None |
| **Total** | **3** | 1 day |

---

## Priority: **LOW** (Excel Parity)

Color customization for Excel parity. Admin feature, not timeline-critical.
<!-- SECTION:NOTES:END -->
