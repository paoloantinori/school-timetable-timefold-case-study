---
id: task-021
title: ui - offer the ability to hide full student groups in the ui
status: Done
assignee: []
created_date: '2026-02-18 06:06'
updated_date: '2026-02-18 21:35'
labels:
  - ui
  - enhancement
dependencies: []
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Offer the ability to hide full student groups in the UI and remember the preference storing the info client side.

---

## Redesign Plan

### Original Implementation
- **Commit**: `8723a8e` (Add stage day highlighting and copyable pre-assignment report)
- **Files affected**: `ScheduleTimeline.tsx`
- **Current state**: Basic checkbox exists in toolbar, but:
  - No localStorage persistence
  - No visual feedback when groups are hidden
  - Groups are not actually being filtered out

### Design Direction
- **Aesthetic**: Refined Utility with Subtle Elegance
- **Key differentiator**: Smooth fade animation when groups disappear, with a floating "hidden count" badge that shows how many groups are filtered
- **Typography**: Use the existing system fonts but add a small badge counter
- **Color**: Match the existing palette, add a subtle teal accent for the filter badge
- **Motion**: 200ms fade-out animation for hidden columns, 150ms scale animation on badge

### Implementation Steps

1. **Add state persistence with localStorage**
   - Create a custom hook `useHiddenGroups()` that:
     - Stores Set of hidden group IDs in localStorage
     - Provides `toggleGroup(id)`, `hideGroup(id)`, `showGroup(id)`, `showAll()`
     - Persists on every change

2. **Filter columns in ScheduleTimeline**
   - Use the hidden groups set to filter `columns` before rendering
   - Animate column removal with CSS transition (opacity + width)

3. **Enhanced toggle UI in toolbar**
   ```tsx
   <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
     <input type="checkbox" id="hide-completed" ... />
     <label htmlFor="hide-completed">Hide completed groups</label>
     {hiddenCount > 0 && (
       <span style={{
         background: '#14b8a6',
         color: '#fff',
         padding: '0.1rem 0.4rem',
         borderRadius: '10px',
         fontSize: '0.7rem',
         fontWeight: 600,
       }}>
         {hiddenCount} hidden
       </span>
     )}
     {hiddenCount > 0 && (
       <button onClick={showAll} style={{ fontSize: '0.7rem' }}>Show all</button>
     )}
   </div>
   ```

4. **Add per-group hide button in column headers**
   - Small "×" button appears on hover over each column header
   - Clicking hides that specific group
   - Hidden groups are remembered across sessions

5. **Visual feedback**
   - Hidden columns fade out with `transition: opacity 0.2s, width 0.2s`
   - Hidden group names appear in a collapsible "Hidden Groups" panel

### Testing Checklist
- [ ] Hide a group, refresh page - group remains hidden
- [ ] Click "Show all" - all groups reappear
- [ ] Hide multiple groups - badge counter updates
- [ ] Column headers show hide button on hover
- [ ] Animations are smooth (no jank)

### Files to Modify
- `src/main/webui/src/components/ScheduleTimeline.tsx`
- `src/main/webui/src/hooks/useHiddenGroups.ts` (new)
<!-- SECTION:DESCRIPTION:END -->
