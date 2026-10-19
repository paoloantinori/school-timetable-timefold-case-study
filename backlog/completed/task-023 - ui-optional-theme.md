---
id: task-023
title: ui - optional theme
status: Done
assignee: []
created_date: '2026-02-18 06:13'
updated_date: '2026-02-19 09:41'
labels:
  - ui
  - enhancement
  - theme
  - claude-planned
dependencies: []
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Beside day and night themes, offer theme selection options. The first new theme should reflect the colors of https://www.example.it/

---

## Redesign Plan

### Original Implementation
- **Commit**: `0602482` (Add dark mode toggle with CSS custom properties)
- **Files affected**: `theme.tsx`, `App.tsx`
- **Current state**: Only Light/Dark toggle exists. school theme was being developed in uncommitted changes (now discarded).

### Design Direction
- **Aesthetic**: Editorial Heritage - warm, professional Italian vocational school aesthetic
- **Key differentiator**: A theme selector dropdown (not just toggle) that feels like choosing a "mood" for the workspace
- **Typography**: Same system fonts, but different color temperature
- **Color**: 
  - **school theme palette** (from example.it analysis):
    - Primary: Deep navy `#1a365d` 
    - Accent: Warm gold `#d69e2e`
    - Surface: Warm cream `#faf5e4`
    - Text: Rich brown `#4a3728`
    - Highlight: Terracotta `#c05621`
- **Motion**: Smooth 300ms transition between themes with a brief "flash" overlay

### school Theme Colors (extracted from website)
```tsx
school: {
  '--bg':             '#faf5e4',  // warm cream
  '--surface':        '#fff8f0',  // warmer white
  '--surface-alt':    '#f5ecd6',  // parchment
  '--surface-hover':  '#ebe3ce',  // light sand
  '--text':           '#4a3728',  // rich brown
  '--text-secondary': '#5c4a3a',  // lighter brown
  '--text-muted':     '#7a6b5a',  // muted brown
  '--text-faint':     '#a89983',  // faded
  '--border':         '#d4c4a8',  // sand border
  '--border-light':   '#e8dcc8',  // light border
  '--border-faint':   '#f0e8da',  // faint border
  '--input-border':   '#c9b896',  // input border
  '--shadow':         'rgba(74,55,40,0.12)',
  '--overlay':        'rgba(26,54,93,0.25)',
  // Schedule-specific
  '--day-header-bg':  '#f0e4d0',  // warm header
  '--header-bg':      '#faf5e4',
  '--corner-bg':      '#fff8f0',
  '--holiday-bg':     '#f5ecd6',
  // Admin-specific
  '--action-bg':      '#1a365d',  // deep navy
  '--action-text':    '#faf5e4',  // cream text
  '--active-link-bg': '#1a365d',
  '--active-link':    '#faf5e4',
  '--delete-bg':      '#fef3c7',  // warning yellow
  '--cancel-bg':      '#e8dcc8',
  '--cancel-text':    '#4a3728',
  // Pre-assign row
  '--pre-assign-bg':  '#faf5e4',
  '--pre-assign-label': '#7a6b5a',
  // Elapsed time
  '--elapsed-text':   '#d69e2e',  // gold accent
}
```

### Implementation Steps

1. **Refactor ThemeProvider to support multiple themes**
   ```tsx
   type Mode = 'light' | 'dark' | 'school';
   
   const themes = { light, dark, school };
   ```

2. **Replace toggle with theme selector dropdown**
   - In App.tsx nav, replace the moon/sun button with:
   ```tsx
   <select 
     value={mode} 
     onChange={(e) => setMode(e.target.value as Mode)}
     style={{ ...styles.themeSelect }}
   >
     <option value="light">Light</option>
     <option value="dark">Dark</option>
     <option value="school">school</option>
   </select>
   ```

3. **Add theme transition animation**
   - CSS class that briefly shows an overlay during theme switch
   - Prevents jarring flash of colors

4. **Style the selector to match nav aesthetic**
   - Dark nav background with cream text for options
   - Small icon next to selector (palette or swatch)

5. **Future-proof for more themes**
   - Store theme as string, not enum
   - Make it easy to add new themes in `themes` object

### Testing Checklist
- [ ] Light theme works as before
- [ ] Dark theme works as before
- [ ] school theme shows warm colors matching example.it
- [ ] Theme persists across refresh
- [ ] Theme transition is smooth (no flash)
- [ ] Dropdown is accessible (keyboard navigation)

### Files to Modify
- `src/main/webui/src/theme.tsx`
- `src/main/webui/src/App.tsx`
<!-- SECTION:DESCRIPTION:END -->
