---
id: task-027
title: 'ui - docs - logic, heuristics and constraints in the site'
status: Done
assignee: []
created_date: '2026-02-18 09:03'
updated_date: '2026-02-18 21:36'
labels:
  - ui
  - docs
  - feature
dependencies: []
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Add a page with all information related to the resolution logic, heuristics, and constraints expressed in human-readable way (avoiding computer science advanced knowledge) so human planners can confirm requirements are properly converted. This must always be up to date with the deployed version.

---

## Redesign Plan

### Original Implementation
- **Commit**: Not yet implemented
- **Files affected**: None (new page needed)
- **Current state**: No documentation page exists. Constraint info only in `CLAUDE.md` and `SchoolScheduleConstraintProvider.java`.

### Design Direction
- **Aesthetic**: Educational Handbook - clear, friendly documentation that reads like a teacher's guide, not a technical spec
- **Key differentiator**: Each constraint has a "plain English" explanation with visual examples, and constraints are grouped by "What we're trying to achieve"
- **Typography**: Friendly body text (system serif or clean sans), clear headings, code snippets in monospace
- **Color**: 
  - Hard constraints: Soft red accent
  - Medium constraints: Warm amber
  - Soft constraints: Calm blue
  - Each section has a subtle colored left border
- **Motion**: Smooth section expansion, scroll-triggered reveals

### Content Structure

```
📚 How the Schedule is Built
├── 🎯 The Big Picture
│   └── Overview of the 5-step pipeline
├── 📅 Step 1: Stage Calendar
│   └── Which days groups are away
├── ✂️ Step 2: Acconciatura Rotation
│   └── How the 3 teachers rotate
├── 🔧 Step 3: Lab Blocks
│   └── Fixed weekly lab patterns
├── 📋 Step 4: Professionalizzanti
│   └── Classes during stage periods
└── 🧩 The Constraints (What Can and Can't Happen)
    ├── 🔴 Hard Rules (Must Never Break)
    │   ├── H1: One teacher, one class at a time
    │   ├── H2: One room, one class at a time
    │   └── ...
    ├── 🟡 Medium Preferences (Strongly Encouraged)
    │   └── ...
    └── 🔵 Soft Goals (Nice to Have)
        └── ...
```

### Implementation Steps

1. **Create Docs page route**
   ```tsx
   // In App.tsx
   <Route path="/docs" element={<DocsPage />} />
   ```

2. **Create DocsPage component**
   ```tsx
   // src/main/webui/src/components/DocsPage.tsx
   export default function DocsPage() {
     return (
       <div style={styles.container}>
         <header>
           <h1>How the Schedule is Built</h1>
           <p>A guide for human planners</p>
         </header>
         
         <nav style={styles.toc}>
           <TOCSection title="The Big Picture" id="overview" />
           <TOCSection title="Step 1: Stage Calendar" id="step1" />
           {/* ... */}
         </nav>
         
         <main>
           <OverviewSection />
           <Step1Section />
           {/* ... */}
           <ConstraintsSection />
         </main>
       </div>
     );
   }
   ```

3. **Create ConstraintCard component**
   ```tsx
   interface ConstraintCardProps {
     id: string;        // "H1"
     name: string;      // "Teacher Conflict"
     type: 'hard' | 'medium' | 'soft';
     humanRead: string; // "A teacher cannot be in two places at once"
     example?: string;  // "If Mario teaches Matematica at 9:00..."
   }
   
   function ConstraintCard({ id, name, type, humanRead, example }: ConstraintCardProps) {
     const typeColors = {
       hard: { bg: '#fef2f2', border: '#ef4444', badge: 'Must Never Happen' },
       medium: { bg: '#fffbeb', border: '#f59e0b', badge: 'Strongly Avoided' },
       soft: { bg: '#eff6ff', border: '#3b82f6', badge: 'Nice to Have' },
     };
     
     return (
       <div style={{
         background: typeColors[type].bg,
         borderLeft: `4px solid ${typeColors[type].border}`,
         padding: '1rem 1.25rem',
         borderRadius: '0 8px 8px 0',
         marginBottom: '1rem',
       }}>
         <header style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
           <code style={styles.constraintId}>{id}</code>
           <h4 style={{ margin: 0 }}>{name}</h4>
           <span style={styles.typeBadge}>{typeColors[type].badge}</span>
         </header>
         <p style={styles.humanRead}>{humanRead}</p>
         {example && (
           <div style={styles.example}>
             <strong>Example:</strong> {example}
           </div>
         )}
       </div>
     );
   }
   ```

4. **Constraint content data file**
   ```tsx
   // src/main/webui/src/data/constraints.ts
   export const constraints = [
     {
       id: 'H1',
       name: 'Teacher Conflict',
       type: 'hard',
       humanRead: 'A teacher cannot teach two different classes at the same time. Just like you can\'t be in two classrooms at once!',
       example: 'If Prof. Rossi is scheduled for "Matematica" with G03 at 9:00 on Monday, they cannot also teach "Fisica" with G06 at 9:00 on the same day.',
     },
     {
       id: 'H2',
       name: 'Room Conflict',
       type: 'hard',
       humanRead: 'Each classroom can only hold one class at a time. No double-booking of rooms!',
       example: 'If "Lab Info 1" is assigned to T06\'s class at 10:00 on Tuesday, no other class can use that room at that time.',
     },
     // ... all constraints from SchoolScheduleConstraintProvider
   ];
   ```

5. **Add navigation link**
   ```tsx
   // In App.tsx nav
   <NavLink to="/docs" style={navLinkStyle}>
     {t('docs:title')}
   </NavLink>
   ```

6. **Add i18n support**
   ```json
   // src/main/webui/src/i18n/locales/en/docs.json
   {
     "title": "Documentation",
     "overview": {
       "title": "The Big Picture",
       "description": "The scheduler follows a 5-step process..."
     }
   }
   ```

7. **Generate docs from constraint provider (optional)**
   - Create a build step that extracts constraint comments from Java
   - Or maintain manually with validation tests

### Testing Checklist
- [ ] Docs page accessible from navigation
- [ ] All constraints documented with human-readable text
- [ ] Constraint types visually distinguished
- [ ] Examples are clear and relevant
- [ ] Page is readable on mobile
- [ ] TOC navigation works
- [ ] i18n works for both languages
- [ ] Page prints reasonably well

### Files to Modify
- `src/main/webui/src/App.tsx` (add route)
- `src/main/webui/src/components/DocsPage.tsx` (new)
- `src/main/webui/src/components/docs/ConstraintCard.tsx` (new)
- `src/main/webui/src/components/docs/TOCSection.tsx` (new)
- `src/main/webui/src/data/constraints.ts` (new)
- `src/main/webui/src/i18n/locales/en/docs.json` (new)
- `src/main/webui/src/i18n/locales/it/docs.json` (new)
<!-- SECTION:DESCRIPTION:END -->
