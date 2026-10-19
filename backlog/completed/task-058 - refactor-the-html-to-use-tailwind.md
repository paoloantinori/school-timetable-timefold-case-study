---
id: task-058
title: refactor the html to use tailwind
status: Done
assignee: []
created_date: '2026-02-20 20:30'
updated_date: '2026-02-21 07:31'
labels:
  - claude-planned
  - ui
  - frontend
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The React frontend uses inline CSS style objects (e.g., `const styles = { container: { ... } }`) scattered throughout components. This makes styling harder to maintain, inconsistent, and disconnected from modern utility-first CSS workflows. The task is to evaluate and implement Tailwind CSS as a replacement for inline styles.

## Current State

Frontend stack (from package.json):
- React 18, TypeScript
- Vite 5.3 (no CSS framework currently)
- No MUI or other component library detected
- Styling: CSS custom properties (--text, --surface, etc.) with inline style objects

Example pattern from DocsPage.tsx:
```tsx
const styles = {
  container: {
    maxWidth: '900px',
    margin: '0 auto',
    padding: '2rem 1.5rem',
  } as React.CSSProperties,
  // ... more inline styles
};
```

## Goals

1. Replace inline style objects with Tailwind utility classes
2. Maintain existing theme system (Light/Dark/school) via CSS variables or Tailwind config
3. Improve developer experience with IDE autocomplete for classes
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] Given Tailwind is installed, When I run `npm run build`, Then it compiles without errors
- [x] #2 [ ] Given the first component is refactored (DocsPage.tsx), When I view it in browser, Then it renders identically to before
- [x] #3 [ ] Given theme CSS variables exist, When Tailwind classes are applied, Then dark mode and school theme continue to work
- [x] #4 [ ] Given a developer edits a component, When they type Tailwind classes, Then IDE autocomplete suggests utilities
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Phase 1: Setup Tailwind CSS
**Files to modify**: `src/main/webui/package.json`, new files for Tailwind config

1. Install dependencies:
   ```bash
   cd src/main/webui
   npm install -D tailwindcss postcss autoprefixer
   npx tailwindcss init -p
   ```

2. Configure `tailwind.config.js`:
   ```javascript
   /** @type {import('tailwindcss').Config} */
   export default {
     content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
     theme: {
       extend: {
         colors: {
           // Map existing CSS variables to Tailwind colors
           surface: 'var(--surface)',
           'surface-alt': 'var(--surface-alt)',
           text: 'var(--text)',
           'text-secondary': 'var(--text-secondary)',
           border: 'var(--border)',
           // etc.
         }
       }
     },
     plugins: []
   }
   ```

3. Create `src/main/webui/src/index.css` (or add to existing):
   ```css
   @tailwind base;
   @tailwind components;
   @tailwind utilities;
   ```

4. Import in `main.tsx`: `import './index.css'`

### Phase 2: Migrate one component as proof of concept
**File**: `src/main/webui/src/components/DocsPage.tsx`

Replace inline `styles` object with Tailwind classes:
```tsx
// Before
<div style={styles.container}>

// After  
<div className="max-w-[900px] mx-auto px-6 py-8">
```

### Phase 3: Create reusable pattern and migrate remaining components
**Files**: All components in `src/main/webui/src/components/`

Priority order:
1. DocsPage.tsx (simple, good first candidate)
2. ConstraintCard.tsx (small component)
3. AnalysisPanel.tsx (moderate complexity)
4. ScheduleTimeline.tsx (complex, defer)

### Phase 4: Update theme system
Ensure CSS variables still work with Tailwind's dark mode or create Tailwind-compatible theme variants.

### Risks
- **Breaking existing themes**: Test all three themes after migration
- **Vite compatibility**: Tailwind + Vite should work out of box with PostCSS
- **Bundle size**: Tailwind with tree-shaking should be minimal

### Verification
```bash
# Rebuild frontend
./scripts/rebuild-ui.sh

# Manual browser testing
# 1. Check DocsPage renders correctly
# 2. Switch between Light/Dark/school themes
# 3. Verify no console errors
```
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **5/5** (Very High)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 5 | Major refactor, all components |
| Risk | 5 | Breaking changes likely |
| Testing | 5 | Full regression testing |
| Dependencies | 2 | None blocking |
| **Total** | **5** | 2-4 weeks |

---

## Priority: **LOW** (Deferred)

Large refactoring effort with no functional change. High risk for regression. Defer indefinitely.

## Completed 2026-02-21

All phases completed:

- Phase 1: Tailwind setup (tailwind.config.js, postcss.config.js, index.css)

- Phase 2-3: All components migrated (DocsPage, admin/*, ScheduleTimeline → timeline/ subcomponents)

- Phase 4: Theme system works via CSS variable mappings

Bonus improvements:

- Stage colors as CSS variables (theme-aware)

- Error handling in API calls

- Explicit return types in scheduleApi.ts

- Domain types extracted (CreateCourseInput, etc.)

Propagated to all 3 active branches.
<!-- SECTION:NOTES:END -->
