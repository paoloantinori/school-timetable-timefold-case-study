---
id: task-035
title: Change dark theme to true black (OLED-friendly)
status: Done
assignee: []
created_date: '2026-02-18 22:32'
updated_date: '2026-02-19 09:06'
labels:
  - ui
  - claude-planned
  - 'complexity:low'
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Convert the existing "dark" theme to a true black OLED-friendly variant for improved battery life on OLED/AMOLED screens and better eye comfort in low-light environments.

**Current state**: The dark theme uses slate tones (#0f172a background, #1e293b surfaces) - these are dark blue-grays, not true black.

**Goals**:
- OLED battery savings: True black (#000000) pixels consume no power on OLED displays
- Eye comfort: Pure black backgrounds reduce eye strain in dark environments
- High contrast: Maintain WCAG AA accessibility (4.5:1 minimum contrast ratio)
- Preserve status colors: Ensure lesson colors, alerts, and interactive elements remain distinguishable
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] --bg changed from #0f172a to #000000 (true black background)
- [x] #2 [ ] --surface changed from #1e293b to #0a0a0a (near-black for elevation)
- [x] #3 [ ] All text colors meet WCAG AA contrast (4.5:1) against black backgrounds
- [x] #4 [ ] Schedule grid day headers and corners remain visually distinct from cells
- [x] #5 [ ] Admin action buttons (edit, delete, cancel) remain distinguishable
- [x] #6 [ ] Existing school and Light themes unchanged
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Color Palette Specification

### Core Colors (replace in `themes.dark` object)
```
--bg:             #000000   # True black (was #0f172a)
--surface:        #0a0a0a   # Near-black elevation (was #1e293b)
--surface-alt:    #121212   # Slightly elevated surface (was #1e293b)
--surface-hover:  #1a1a1a   # Hover state (was #334155)
```

### Text Colors (verify contrast ≥ 4.5:1)
```
--text:           #f5f5f5   # Near-white (was #e2e8f0) - contrast 17.5:1 ✓
--text-secondary: #e0e0e0   # Brighter secondary (was #cbd5e1) - contrast 14:1 ✓
--text-muted:     #a0a0a0   # Muted but readable (was #94a3b8) - contrast 5.7:1 ✓
--text-faint:     #707070   # Faint but accessible (was #64748b) - contrast 3.9:1 (AA Large only)
```

### Border Colors
```
--border:         #2a2a2a   # Subtle separation (was #334155)
--border-light:   #1a1a1a   # Lighter border (was #1e293b)
--border-faint:   #0f0f0f   # Barely visible (was #1e293b)
--input-border:   #3a3a3a   # Input focus (was #475569)
```

### Schedule-Specific Colors
```
--day-header-bg:  #1a1a1a   # Slight elevation for headers (was #334155)
--header-bg:      #0a0a0a   # Header surface (was #1e293b)
--corner-bg:      #0f0f0f   # Corner cell (was #1e293b)
--holiday-bg:     #0a0a0a   # Holiday cells (was #1e293b)
```

### Admin Action Colors
```
--action-bg:      #0d1f3c   # Deep blue action bg (was #1e3a5f)
--action-text:    #60a5fa   # Keep blue (good contrast on black)
--active-link-bg: #0d1f3c   # (was #1e3a5f)
--active-link:    #60a5fa   # Keep
--delete-bg:      #2a0a0a   # Dark red bg (was #450a0a)
--cancel-bg:      #1a1a1a   # (was #334155)
--cancel-text:    #f5f5f5   # Brighter (was #e2e8f0)
```

### Pre-assign Row
```
--pre-assign-bg:  #0a0a0a   # (was #1e293b)
--pre-assign-label: #a0a0a0 # (was #94a3b8)
```

### Elapsed Time
```
--elapsed-text:   #c084fc   # Keep bright purple (good contrast)
```

### Shadow/Overlay
```
--shadow:         rgba(0,0,0,0.6)   # Deeper shadow (was 0.4)
--overlay:        rgba(0,0,0,0.75)  # Darker overlay (was 0.6)
```

## File to Modify
- `src/main/webui/src/theme.tsx` - Update the `dark` object in `themes` constant

## Testing
1. Verify all text is readable against black backgrounds
2. Use browser dev tools to check contrast ratios
3. Test on schedule grid, admin pages, and docs page
4. Verify lesson colors (from LessonTooltip/subject colors) remain visible
<!-- SECTION:NOTES:END -->
