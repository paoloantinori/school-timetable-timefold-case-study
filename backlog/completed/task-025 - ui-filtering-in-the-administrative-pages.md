---
id: task-025
title: ui - filtering in the administrative pages
status: Done
assignee: []
created_date: '2026-02-18 06:16'
updated_date: '2026-02-18 21:36'
labels:
  - ui
  - enhancement
  - admin
dependencies: []
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Add filtering capability to every page in the administrative section for easier display of content.

---

## Redesign Plan

### Original Implementation
- **Commit**: Not yet implemented
- **Files affected**: `AdminLayout.tsx`, all admin page components
- **Current state**: No filtering on any admin page. All records shown in one long table.

### Design Direction
- **Aesthetic**: Command Center Toolbar - a consistent, always-present filter bar that feels like a search interface
- **Key differentiator**: Live filtering as you type, with a "filtered count" badge that updates in real-time
- **Typography**: Search icon + placeholder text, clean input styling
- **Color**: Subtle input background, accent color on focus, badge matches theme
- **Motion**: 150ms transition on focus, smooth row fade when filtered out

### Implementation Steps

1. **Create reusable FilterBar component**
   ```tsx
   // src/main/webui/src/components/admin/FilterBar.tsx
   interface FilterBarProps {
     placeholder?: string;
     value: string;
     onChange: (value: string) => void;
     totalCount: number;
     filteredCount: number;
   }
   
   export function FilterBar({ placeholder, value, onChange, totalCount, filteredCount }: FilterBarProps) {
     return (
       <div style={styles.container}>
         <div style={styles.inputWrapper}>
           <SearchIcon />
           <input
             type="text"
             placeholder={placeholder || 'Filter...'}
             value={value}
             onChange={(e) => onChange(e.target.value)}
             style={styles.input}
           />
           {value && (
             <button onClick={() => onChange('')} style={styles.clearBtn}>
               ×
             </button>
           )}
         </div>
         <span style={styles.count}>
           {filteredCount === totalCount 
             ? `${totalCount} items`
             : `${filteredCount} of ${totalCount}`}
         </span>
       </div>
     );
   }
   ```

2. **Add to each admin page**
   ```tsx
   // In TeachersPage, StudentGroupsPage, etc.
   const [filter, setFilter] = useState('');
   
   const filteredTeachers = teachers.filter(t => 
     t.name.toLowerCase().includes(filter.toLowerCase())
   );
   
   return (
     <>
       <FilterBar
         placeholder="Filter teachers..."
         value={filter}
         onChange={setFilter}
         totalCount={teachers.length}
         filteredCount={filteredTeachers.length}
       />
       <table>
         {/* filteredTeachers.map(...) */}
       </table>
     </>
   );
   ```

3. **Page-specific filter fields**

   | Page | Filter Fields |
   |------|--------------|
   | Teachers | name |
   | Student Groups | name, profile (FB, MODA, etc.) |
   | Rooms | name, capacity, tags |
   | Courses | subject, teacher name, student group |
   | Holidays | date, description |
   | Stage Patterns | student group, dates |
   | Teacher Availability | teacher name, type |

4. **Add multi-field filtering for Courses page**
   ```tsx
   const filteredCourses = courses.filter(c => 
     c.subject?.toLowerCase().includes(filter.toLowerCase()) ||
     c.teacher?.name?.toLowerCase().includes(filter.toLowerCase()) ||
     c.studentGroup?.name?.toLowerCase().includes(filter.toLowerCase())
   );
   ```

5. **Style the filter bar**
   ```tsx
   const styles = {
     container: {
       display: 'flex',
       alignItems: 'center',
       gap: '1rem',
       marginBottom: '1rem',
     },
     inputWrapper: {
       display: 'flex',
       alignItems: 'center',
       background: 'var(--surface-alt)',
       border: '1px solid var(--input-border)',
       borderRadius: '6px',
       padding: '0.4rem 0.75rem',
       flex: 1,
       maxWidth: '300px',
     },
     input: {
       border: 'none',
       background: 'transparent',
       flex: 1,
       fontSize: '0.9rem',
       outline: 'none',
     },
     clearBtn: {
       background: 'none',
       border: 'none',
       color: 'var(--text-muted)',
       cursor: 'pointer',
       fontSize: '1rem',
       padding: 0,
     },
     count: {
       fontSize: '0.8rem',
       color: 'var(--text-muted)',
     },
   };
   ```

6. **Add keyboard shortcut**
   - `/` or `Ctrl+F` focuses the filter input
   - `Escape` clears the filter

### Testing Checklist
- [ ] Filter bar appears on all admin pages
- [ ] Live filtering works as you type
- [ ] Clear button works
- [ ] Count badge updates correctly
- [ ] Multi-field filtering works on Courses page
- [ ] Keyboard shortcut focuses input
- [ ] Escape clears filter
- [ ] Empty state shows when no results

### Files to Modify
- `src/main/webui/src/components/admin/FilterBar.tsx` (new)
- `src/main/webui/src/components/admin/AdminLayout.tsx`
- `src/main/webui/src/components/admin/TeachersPage.tsx`
- `src/main/webui/src/components/admin/StudentGroupsPage.tsx`
- `src/main/webui/src/components/admin/RoomsPage.tsx`
- `src/main/webui/src/components/admin/CoursesPage.tsx`
- `src/main/webui/src/components/admin/HolidaysPage.tsx`
- `src/main/webui/src/components/admin/StagePatternsPage.tsx`
- `src/main/webui/src/components/admin/TeacherAvailabilityPage.tsx`
<!-- SECTION:DESCRIPTION:END -->
