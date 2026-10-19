---
id: task-029
title: ui - allow teacher to specify a nick name
status: Done
assignee: []
created_date: '2026-02-18 09:21'
updated_date: '2026-02-19 09:13'
labels:
  - ui
  - claude-planned
  - 'complexity:low'
dependencies: []
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
allow to specify a nick name to be used in the ui if present
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Start app, navigate to Admin → Teachers
- [x] #2 Add nickname to a teacher (e.g., 'T06' → 'Bono')
- [x] #3 Save changes
- [x] #4 Navigate to Schedule view
- [x] #5 Verify nickname appears in schedule cells instead of full name
- [ ] #6 Clear nickname, save
- [ ] #7 Verify full name returns in schedule view
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Backend Changes

### File: `src/main/java/com/school/planner/domain/Teacher.java`
```java
// Add field:
public String nickname;  // Optional display name for UI
```

### File: `src/main/java/com/school/planner/rest/TeacherResource.java`
- No changes needed - Panache entity auto-exposes new field via JSON

### File: `src/main/java/com/school/planner/bootstrap/DataGenerator.java`
- Optionally seed nicknames for demonstration

## Frontend Changes

### File: `src/main/webui/src/types/domain.ts`
```typescript
export interface Teacher {
  id: number;
  name: string;
  nickname?: string | null;  // Add optional field
}
```

### File: `src/main/webui/src/components/LessonCell.tsx` (or inline in ScheduleTimeline)
- Create helper: `const displayName = teacher.nickname || teacher.name`

### Admin UI Update
Add nickname editing to the Teachers admin page:
- Text input field (optional)
- Placeholder: "Display name (optional)"
- Helper text: "If set, replaces the full name in schedule views"

## Files to Modify
- `src/main/java/com/school/planner/domain/Teacher.java` - Add nickname field
- `src/main/webui/src/types/domain.ts` - Add nickname to Teacher interface
- `src/main/webui/src/components/ScheduleTimeline.tsx` - Use nickname in display
- `src/main/webui/src/components/admin/TeacherForm.tsx` (if exists) - Add nickname input

## Database Migration
- Hibernate `update` mode will auto-add the column on next startup

## UI/UX Design Specifications

### Form Field Design (TeachersAdmin.tsx)

**Layout**: Add a second input field in the inline form, positioned AFTER the name field.

```
+---------------------------------------------------------------+
| [ Name input................ ] [ Nickname input....... ] [Add]|
+---------------------------------------------------------------+
| When editing:                                                  |
| [ T06............... ] [ Bono................. ] [Save] [Cancel]|
+---------------------------------------------------------------+
```

**Input Specifications**:
- **Placeholder**: "Short name (optional)" / "Nome breve (opzionale)"
- **Helper text** (below form, visible when field is focused): 
  - EN: "If set, replaces full name in schedule cells for better readability"
  - IT: "Se impostato, sostituisce il nome completo nelle celle dell'orario"
- **Max length**: 20 characters (fits in 120px column with 0.65rem font)
- **Width**: 120px (matches teacher column width in schedule view)

### Validation Rules

1. **Max length**: 20 characters (enforced by `maxLength` attribute)
2. **Allowed characters**: Alphanumeric, spaces, hyphens, apostrophes
   - Regex: `^[\p{L}\p{N}\s\-']*$` (Unicode letters, numbers, spaces, hyphens, apostrophes)
3. **No leading/trailing whitespace**: Trim on save
4. **Optional field**: Empty/null means use full name

### Display Locations

The nickname should replace the full name in **ALL** schedule views:

1. **ScheduleTimeline.tsx** (line 865, 879, etc.):
   - `lesson.course?.teacher?.name` becomes `lesson.course?.teacher?.nickname || lesson.course?.teacher?.name`
   - Applies to all groupBy modes (studentGroup, room, teacher)

2. **LessonTooltip.tsx** (line 265):
   - Badge displays nickname if set, full name otherwise
   - Tooltip on hover shows: "Nickname (Full Name)" when nickname differs from name

3. **FilterBar dropdown** (when filtering by teacher):
   - Show: "Nickname" if set, else full name

### Helper Function

Create a reusable utility:

```typescript
// src/main/webui/src/utils/teacherUtils.ts
export const getDisplayName = (teacher: Teacher | null): string => {
  if (!teacher) return '';
  return teacher.nickname?.trim() || teacher.name;
};

export const getFullLabel = (teacher: Teacher | null): string => {
  if (!teacher) return '';
  if (teacher.nickname && teacher.nickname !== teacher.name) {
    return `${teacher.nickname} (${teacher.name})`;
  }
  return teacher.name;
};
```

### Column Width Consideration

The teacher column is 120px wide with 0.65rem font. Character limits:
- "T06" (11 chars) fits
- "Bono" (4 chars) fits comfortably
- Max readable: ~15-18 characters at this width

Recommend **20 char max** to allow flexibility while ensuring fit.

### Edge Cases

1. **Nickname same as name**: No special handling, just display it
2. **Whitespace-only nickname**: Trim to empty, treat as unset
3. **Very long names (20+ chars)**: Truncate with ellipsis in schedule cell (existing behavior via `textOverflow: 'ellipsis'`)

### i18n Keys to Add

```json
// en/admin.json
"nickname": "Nickname",
"nicknamePlaceholder": "Short name (optional)",
"nicknameHelper": "If set, replaces full name in schedule cells for better readability"

// it/admin.json
"nickname": "Nome breve",
"nicknamePlaceholder": "Nome breve (opzionale)",
"nicknameHelper": "Se impostato, sostituisce il nome completo nelle celle dell'orario"
```
<!-- SECTION:NOTES:END -->
