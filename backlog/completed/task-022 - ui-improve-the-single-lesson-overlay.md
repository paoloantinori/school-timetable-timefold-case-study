---
id: task-022
title: ui - improve the single lesson overlay
status: Done
assignee: []
created_date: '2026-02-18 06:11'
updated_date: '2026-02-18 20:31'
labels:
  - ui
  - enhancement
  - navigation
dependencies: []
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

When clicking on a single lesson in the calendar, show enhanced stats about the lesson:
- Decorate teacher name with same color used in calendar
- Show total hours for that class in that student group
- Remove seconds from timestamp
- Add clickable links for filtered views

---

## Redesign Plan

### Original Implementation
- **Commit**: `8723a8e` (Add stage day highlighting and copyable pre-assignment report)
- **Files affected**: `LessonTooltip.tsx`
- **Current state**: Basic modal with plain text. No color coding, no total hours, timestamps include seconds, no navigation links.

### Design Direction
- **Aesthetic**: Editorial Card - a mini "profile card" for the lesson with visual hierarchy and actionable links
- **Key differentiator**: The teacher name becomes a visual anchor (colored badge), and each field is a potential navigation action
- **Typography**: Bold subject title, lighter metadata, clear visual hierarchy
- **Color**: Use the subject color for teacher badge, subtle borders for sections
- **Motion**: Smooth fade-in (150ms), scale-up entrance animation, hover effects on clickable items

### Implementation Steps

1. **Add subject color to teacher name**
   ```tsx
   // Pass color from parent via lesson.course.subject lookup
   const teacherBadge = (
     <span style={{
       background: subjectColor,
       color: '#fff',
       padding: '0.2rem 0.5rem',
       borderRadius: '4px',
       fontWeight: 600,
       fontSize: '0.85rem',
     }}>
       {lesson.course?.teacher?.name}
     </span>
   );
   ```

2. **Add total hours calculation**
   - Requires new API endpoint or computed value from schedule data
   - Count all lessons for same course.studentGroup
   - Display as "Total hours: X of Y" (assigned vs required)

3. **Format timestamp without seconds**
   ```tsx
   const formatDateTime = (isoStr: string): string => {
     const d = new Date(isoStr);
     return d.toLocaleString('it-IT', {
       day: '2-digit',
       month: '2-digit',
       year: 'numeric',
       hour: '2-digit',
       minute: '2-digit',
     });
   };
   ```

4. **Add clickable navigation links**
   - Teacher name → filter by teacher view
   - Student group name → filter by student group view  
   - Room name → filter by room view
   - Date → navigate to that week in calendar
   
   ```tsx
   const NavLink = ({ label, value, onClick }) => (
     <button
       onClick={onClick}
       style={{
         background: 'none',
         border: 'none',
         color: 'var(--action-text)',
         cursor: 'pointer',
         padding: 0,
         textDecoration: 'underline',
         fontSize: 'inherit',
       }}
     >
       {value}
     </button>
   );
   ```

5. **Redesigned card layout**
   ```tsx
   <div style={styles.card}>
     {/* Header with colored subject */}
     <div style={{
       background: subjectColor,
       color: '#fff',
       padding: '0.75rem 1rem',
       margin: '-1.25rem -1.25rem 1rem',
       borderRadius: '8px 8px 0 0',
     }}>
       <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>
         {lesson.course?.subject}
       </div>
     </div>
     
     {/* Navigation links as primary actions */}
     <div style={styles.navSection}>
       <Field label="Teacher" onClick={() => navigate('teacher', teacherId)}>
         <ColoredBadge color={subjectColor}>{teacherName}</ColoredBadge>
       </Field>
       <Field label="Class" onClick={() => navigate('group', groupId)}>
         <ClickableText>{groupName}</ClickableText>
       </Field>
       <Field label="Room" onClick={() => navigate('room', roomId)}>
         <ClickableText>{roomName}</ClickableText>
       </Field>
     </div>
     
     {/* Metadata */}
     <div style={styles.metaSection}>
       <Field label="Date">{formattedDateTime}</Field>
       <Field label="Total Hours">{totalHours}</Field>
       <Field label="Status">
         <PinBadge pinned={lesson.pinned} />
       </Field>
     </div>
     
     {/* Actions */}
     <div style={styles.actions}>
       <PinButton onClick={handleTogglePin} pinned={lesson.pinned} />
       <CloseButton onClick={onClose} />
     </div>
   </div>
   ```

6. **Add animation on open**
   ```css
   @keyframes tooltipEnter {
     from { opacity: 0; transform: scale(0.95) translateY(-10px); }
     to { opacity: 1; transform: scale(1) translateY(0); }
   }
   ```

### Testing Checklist
- [ ] Teacher badge shows subject color
- [ ] Total hours displays correctly
- [ ] Timestamp has no seconds
- [ ] Clicking teacher navigates to teacher view
- [ ] Clicking group navigates to group view
- [ ] Clicking room navigates to room view
- [ ] Clicking date navigates to that week
- [ ] Animation is smooth
- [ ] Card is accessible (focus management)

### Files to Modify
- `src/main/webui/src/components/LessonTooltip.tsx`
- `src/main/webui/src/components/ScheduleTimeline.tsx` (pass navigation callbacks)
- `src/main/webui/src/api/scheduleApi.ts` (add endpoint for total hours if needed)
<!-- SECTION:DESCRIPTION:END -->
