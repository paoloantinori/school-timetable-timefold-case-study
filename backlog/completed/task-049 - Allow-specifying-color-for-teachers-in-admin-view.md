---
id: task-049
title: Allow specifying color for teachers in admin view
status: Done
assignee: []
created_date: '2026-02-20 20:23'
updated_date: '2026-02-21 12:02'
labels:
  - ui
  - claude-planned
  - enhancement
dependencies:
  - task-048
priority: low
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement
Human planners at the school use Excel spreadsheets where rows/columns are colored by teacher, not by subject. This is their mental model for reading schedules. Currently, the app only supports subject-based coloring. Adding per-teacher colors allows matching the Excel workflow and potentially offering a "color by teacher" view mode in the timeline.

## Current Behavior
- Timeline colors are solely based on subject via `getColor(subject)` 
- No teacher color field exists in the `Teacher` entity
- Users cannot customize teacher appearance

## Business Context
From the task description: "this is how the current excel using in human planning are coloured" - teacher-based coloring is the existing mental model. Supporting this reduces friction when transitioning from Excel to the digital tool.

## Design Consideration
Should teacher color override subject color, or should there be a toggle/setting? 
- Option A: Add a "Color by" toggle (Subject/Teacher) in the timeline toolbar
- Option B: Teacher color always takes precedence when set
- Recommend Option A for flexibility
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given I am on the Teachers Admin page, When I create or edit a Teacher, Then I see a color picker input field for the teacher color
- [x] #2 Given I set a color for a teacher (e.g., T16 = #ec4899), When I save the teacher, Then the color is persisted to the database
- [x] #3 Given a teacher has a saved color, When I view the schedule timeline in teacher view mode, Then lessons taught by that teacher use the saved color
- [x] #4 Given a teacher has no saved color, When I view the schedule timeline, Then the system falls back to the subject-based color
- [x] #5 Given I am editing a teacher with an existing color, When the edit form loads, Then the color picker shows the currently saved color
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Phase 1: Backend - Add Color Field
**File**: `src/main/java/com/school/planner/domain/Teacher.java`
```java
@Column(length = 7)
public String color; // Nullable hex color like "#ec4899"
```

### Phase 2: Frontend Types
**File**: `src/main/webui/src/types/domain.ts`
```typescript
export interface Teacher {
  // ... existing fields
  color?: string | null;  // Hex color for teacher display
}
```

### Phase 3: Admin UI Color Picker
**File**: `src/main/webui/src/components/admin/TeachersAdmin.tsx`
- Add state: `const [color, setColor] = useState('#3b82f6');`
- Add color picker input in form (after nickname field)
- Include `color` in `handleSave` payload
- Display color swatch in table (new column)
- Set `color` when editing existing teacher

### Phase 4: Timeline Color Mode Toggle
**File**: `src/main/webui/src/components/ScheduleTimeline.tsx`
- Add state: `const [colorByMode, setColorByMode] = useState<'subject' | 'teacher'>('subject');`
- Add toggle buttons to toolbar
- Create `getTeacherColor()` function
- Modify lesson cell rendering to use appropriate color

### Phase 5: Alternative Implementation (simpler)
Instead of toggle, show both colors:
- Subject color as background
- Teacher color as 4px left border

### Files to Modify
1. `src/main/java/com/school/planner/domain/Teacher.java`
2. `src/main/webui/src/types/domain.ts`
3. `src/main/webui/src/components/admin/TeachersAdmin.tsx`
4. `src/main/webui/src/components/ScheduleTimeline.tsx`
5. `src/main/webui/src/i18n/locales/en/schedule.json` (add "colorBy" labels)
6. `src/main/webui/src/i18n/locales/it/schedule.json` (add "colorBy" labels)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Backend field + admin UI + toggle |
| Risk | 2 | Similar to task-048 |
| Testing | 3 | Color mode testing |
| Dependencies | 2 | Follows task-048 pattern |
| **Total** | **3** | 1 day (after task-048) |

---

## Priority: **LOW** (Follows task-048)

Requires color-by toggle decision. Implement with task-048 for consistency.

## Dependencies
- task-048 (similar pattern, can be done together)
<!-- SECTION:NOTES:END -->
