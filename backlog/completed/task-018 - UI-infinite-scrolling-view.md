---
id: task-018
title: UI - infinite scrolling view
status: Done
assignee: []
created_date: '2026-02-18 05:56'
updated_date: '2026-02-18 21:36'
labels:
  - ui
  - enhancement
  - feature
dependencies: []
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Offer the ability to switch from 1 week at a time view to an infinite scrolling view (within the boundary of the active calendar).

---

## Redesign Plan

### Original Implementation
- **Commit**: Not yet implemented
- **Files affected**: `ScheduleTimeline.tsx`
- **Current state**: Week-by-week navigation with Prev/Today/Next buttons. No infinite scroll option.

### Design Direction
- **Aesthetic**: Timeline River - a continuous flow of time that scrolls naturally, like a calendar app
- **Key differentiator**: The view mode toggle is prominent, and infinite scroll feels like "unfurling" the schedule
- **Typography**: Sticky date headers that follow as you scroll
- **Color**: Same palette, but with subtle gradient separators between weeks
- **Motion**: Smooth virtualized scrolling with lazy-loaded rows, week separators fade in

### Technical Approach

Since the calendar can span ~9 months (Sep-Jun) with ~39 weeks × 5 days × 6 periods × ~15 groups = ~17,550 rows, we need **virtual scrolling** for performance.

### Implementation Steps

1. **Add view mode state**
   ```tsx
   type ViewMode = 'week' | 'infinite';
   const [viewMode, setViewMode] = useState<ViewMode>('week');
   ```

2. **Add view mode toggle in toolbar**
   ```tsx
   <div style={styles.viewModeToggle}>
     <button 
       style={viewMode === 'week' ? styles.activeToggle : {}}
       onClick={() => setViewMode('week')}
     >
       Week View
     </button>
     <button 
       style={viewMode === 'infinite' ? styles.activeToggle : {}}
       onClick={() => setViewMode('infinite')}
     >
       Timeline View
     </button>
   </div>
   ```

3. **Install react-window for virtualization**
   ```bash
   npm install react-window
   ```

4. **Create VirtualizedTimeline component**
   ```tsx
   import { FixedSizeList as List } from 'react-window';
   
   // Each "row" is one day's worth of time periods
   const Row = ({ index, style }) => {
     const day = allDays[index];
     return (
       <div style={style}>
         <DayHeader day={day} />
         {timePeriods.map(period => (
           <PeriodRow key={period.startTime} day={day} period={period} />
         ))}
       </div>
     );
   };
   
   <List
     height={600}
     itemCount={allDays.length}
     itemSize={200} // Height per day
     width="100%"
   >
     {Row}
   </List>
   ```

5. **Compute all days in school year**
   ```tsx
   const allDays = useMemo(() => {
     const start = yearRange?.start ?? SCHOOL_YEAR_START;
     const end = yearRange?.end ?? SCHOOL_YEAR_END;
     const days: Date[] = [];
     
     let current = start;
     while (current <= end) {
       const dayOfWeek = current.getDay();
       // Only weekdays (Mon-Fri)
       if (dayOfWeek >= 1 && dayOfWeek <= 5) {
         days.push(new Date(current));
       }
       current.setDate(current.getDate() + 1);
     }
     return days;
   }, [yearRange]);
   ```

6. **Add week separator visual**
   ```tsx
   const isMonday = (date: Date) => date.getDay() === 1;
   
   // In Row component:
   {isMonday(day) && (
     <div style={{
       background: 'linear-gradient(90deg, var(--border-light), transparent)',
       height: '2px',
       marginBottom: '0.5rem',
     }} />
   )}
   ```

7. **Add "Jump to date" feature**
   ```tsx
   <input
     type="date"
     onChange={(e) => {
       const targetDate = parseISO(e.target.value);
       const index = allDays.findIndex(d => 
         format(d, 'yyyy-MM-dd') === format(targetDate, 'yyyy-MM-dd')
       );
       if (index >= 0) listRef.current?.scrollToItem(index);
     }}
   />
   ```

8. **Persist view mode preference**
   ```tsx
   useEffect(() => {
     localStorage.setItem('scheduleViewMode', viewMode);
   }, [viewMode]);
   
   const [viewMode, setViewMode] = useState<ViewMode>(
     () => (localStorage.getItem('scheduleViewMode') as ViewMode) || 'week'
   );
   ```

9. **Add "Today" button for infinite view**
   ```tsx
   <button onClick={() => {
     const today = new Date();
     const index = allDays.findIndex(d => 
       format(d, 'yyyy-MM-dd') === format(today, 'yyyy-MM-dd')
     );
     if (index >= 0) listRef.current?.scrollToItem(index, 'center');
   }}>
     Jump to Today
   </button>
   ```

### Testing Checklist
- [ ] Toggle switches between week and infinite view
- [ ] Infinite view shows all days in school year
- [ ] Scrolling is smooth (no jank)
- [ ] Week separators are visible
- [ ] Jump to date works
- [ ] Jump to Today works
- [ ] View mode persists across refresh
- [ ] Holidays are still shown
- [ ] Stage day highlighting still works
- [ ] Performance is acceptable with large datasets

### Files to Modify
- `src/main/webui/src/components/ScheduleTimeline.tsx`
- `src/main/webui/src/components/VirtualizedTimeline.tsx` (new)
- `src/main/webui/package.json` (add react-window)

### Dependencies
- `react-window` - for virtual scrolling performance
<!-- SECTION:DESCRIPTION:END -->
