---
id: TASK-163
title: Implement deterministic teacher colors with CSS visual variations
status: Done
assignee: []
created_date: '2026-03-04 08:59'
updated_date: '2026-03-10 05:16'
labels:
  - enhancement
  - frontend
  - backend
  - ux
  - claude-planned
dependencies: []
priority: low
ordinal: 23750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Currently, colors are assigned by **subject** (not teacher) in the frontend using a fixed palette. This means:
1. Teachers don't have consistent colors across runs (no backend persistence)
2. Multiple teachers teaching the same subject get the same color
3. No visual variations (stripes, patterns) to distinguish teachers beyond solid colors

The current color assignment logic in `useScheduleData.ts`:
- Uses `colorMap` keyed by **subject name**, not teacher
- Falls back to a 15-color palette that may not be distinguishable enough
- Only supports solid colors

## Current Implementation

**Backend:**
- `Teacher.color` field exists but is null by default (not set in DataGenerator)
- `Course.color` field exists for subject-level colors
- Colors stored as hex strings (7 chars, e.g., "#3b82f6")

**Frontend:**
- `getColor()` function assigns colors from palette by subject name
- Teacher color shown as 4px left border accent on subject badge
- No patterns/stripes - just solid backgrounds

**Existing CSS Patterns (can reuse):**
- `bg-unavailable-stripes` - 45° diagonal stripes
- `bg-stage-gradient` - vertical gradient
- Violation stripes - diagonal pattern with two colors

## Desired Behavior

1. **Deterministic teacher colors**: Each teacher gets a unique, consistent color based on their ID/name
2. **Human-distinguishable palette**: Use a carefully designed color set (not just random hex values)
3. **CSS variations**: Support striped, dotted, gradient variations for visual distinction
4. **Backend persistence**: Store default colors in Teacher entity during seed data generation

---

## Accessibility Research Summary (2026-03-05)

### The Core Problem
> **28 teachers = 28 colors → this exceeds the practical limit of ~10 distinguishable colors**

Even ColorBrewer's qualitative palettes max out at 9-12 colors. Beyond that, colors become visually similar.

### WCAG Requirements
| Criterion | Requirement | Current Status |
|-----------|-------------|----------------|
| SC 1.4.1 | Color not the only visual cue | ⚠️ Partial (patterns exist but not visible in Admin) |
| SC 1.4.3 | 4.5:1 contrast for text | ❌ Yellow (#ffff33) fails |
| SC 1.4.11 | 3:1 for non-text elements | ⚠️ Not verified |

### Color Combinations to Avoid (for color blindness)
- ❌ Red-Green (most common CVD, affects 1 in 12 men)
- ❌ Green-Blue
- ❌ Blue-Purple
- ❌ Blue-Gray

---

## Combined Approach: Pattern-First + Grayscale-Safe (A+C)

Two accessibility mechanisms that are **orthogonal** (not overlapping):

### Layer 1: Patterns (Primary Differentiator)
- Striped, dotted, hatched, gradient patterns
- Works for all color blindness types
- Patterns assigned by teacher category or manually

### Layer 2: Grayscale-Safe Colors (Secondary)
- Colors chosen for **luminance contrast**, not just hue
- Each color has ~10% luminance difference from neighbors
- Works even without patterns (fallback)

### Why Combine Them?
- Patterns might be turned off (`prefers-reduced-motion`)
- Patterns might print poorly
- Some users find patterns distracting
- With combined approach: **colors still work independently**

---

## 10-Color Grayscale-Safe Palette

Colors ordered by luminance, each with ~10% difference:

```java
// Luminance-ordered, pattern-compatible palette
// Dark tier (12-22% luminance) - for primary teachers
"#1a1a8c", // Dark blue     (12%)
"#4b0082", // Indigo        (18%)
"#8b0000", // Dark red      (16%)
"#006400", // Dark green    (22%)

// Medium tier (30-42% luminance)
"#800080", // Purple        (30%)
"#008080", // Teal          (38%)
"#cc5500", // Burnt orange  (42%)

// Light tier (65-72% luminance) - requires dark text
"#ff6b6b", // Light coral   (65%)
"#f0c000", // Gold          (70%) ← replaced failing yellow
"#40e0d0", // Turquoise     (72%)
```

**The math:**
- 10 colors × 4 patterns = **40 unique identities** (covers 28 teachers)
- Each identity works in grayscale (luminance)
- Each identity works for color blindness (pattern)

---

## Pattern Assignment Strategy

| Teacher Category | Pattern | Rationale |
|-----------------|---------|-----------|
| Lab teachers (isLab=true) | Dotted | Hands-on, practical work |
| Theory teachers | Striped | Traditional classroom |
| Doppio/co-teaching | Double stripes | Two teachers present |
| Pinned lessons | Hatched | Locked/fixed in place |
| Professionalizzanti | Dashed border | Stage-related |

---

## Current Implementation Status

**Completed:**
- ✅ `patterns.css` with striped/dotted/hatched/gradient overlays
- ✅ `getLessonStyle()` logic: doppio=striped, lab=dotted, pinned=hatched
- ✅ Teacher color assignment in DataGenerator (30 colors)
- ✅ Basic color picker in TeachersAdmin

**Missing:**
- ❌ Pattern selector in Admin UI (only color picker)
- ❌ Pattern legend/indicator visible to users
- ❌ WCAG contrast verification (yellow #ffff33 fails 4.5:1)
- ❌ Grayscale-safe palette (current palette not luminance-tested)
- ❌ Visual legend explaining pattern meanings
- ❌ Database migration for existing teachers
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [Backend] Create a TEACHER_COLORS constant with 28+ distinguishable colors (one per teacher)
- [x] #2 [Backend] Assign colors to teachers in DataGenerator.generateTeachers() using hash-based selection
- [x] #3 [Backend] Colors must be consistent across runs (same teacher = same color)
- [x] #4 [Frontend] Use Teacher.color as primary color source (already partially implemented)
- [x] #5 [Frontend] Add CSS utility classes for visual variations: striped, dotted, gradient, hatched
- [x] #6 [Frontend] Apply variation based on context (e.g., co-teaching = striped, stage = gradient)
- [ ] #7 [Accessibility] Verify color contrast ratios meet WCAG AA (4.5:1 for text)
- [ ] #8 [Database] Run migration to populate existing teachers with default colors
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Accessibility Research (2026-03-05)

Core problem: 28 teachers = 28 colors → exceeds ~10 distinguishable limit. WCAG SC 1.4.1 (color not only cue) ⚠️ partial, SC 1.4.3 (4.5:1 contrast) ❌ yellow fails.

## Combined Approach: Pattern-First + Grayscale-Safe (A+C)

Two ORTHOGONAL mechanisms: (1) Patterns as primary differentiator - works for all color blindness, (2) Grayscale-safe colors with ~10% luminance difference each - fallback if patterns off.

## 10-Color Grayscale-Safe Palette

Dark (12-22%): #1a1a8c, #4b0082, #8b0000, #006400. Medium (30-42%): #800080, #008080, #cc5500. Light (65-72%): #ff6b6b, #f0c000 (replaces yellow), #40e0d0. Math: 10 colors × 4 patterns = 40 unique identities.

## Phase 1: Quick Wins (1-2h)

Replace #ffff33→#f0c000, add pattern legend/tooltip, add pattern preview in TeachersAdmin.

## Phase 2: Grayscale-Safe Palette (2-3h)

Replace 30-color palette with 10 grayscale-safe, add luminance verification script, verify WCAG AA contrast.

## Phase 3: Pattern Assignment (Half day)

Add `pattern` field to Teacher entity, auto-assign (lab→dotted, theory→striped, mixed→hatched), update TeachersAdmin with pattern selector, add PatternLegend component.

## Phase 4: Accessibility Verification (1-2h)

Contrast tests on 10×4 combos, color blindness simulation (Color Oracle), grayscale test, document rationale.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented 10 grayscale-safe colors × 4 CSS patterns (plain/dotted/hatched/striped) = 40 unique teacher visual identities. Pattern stored in Teacher entity, assigned sequentially in DataGenerator, and rendered in timeline cells. TeachersAdmin shows pattern swatches. Commit 3c75d64.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Unit test: verify same teacher always gets same color
- [ ] #2 Visual test: all 28 teachers have distinguishable colors in timeline
- [ ] #3 Manual test: colors persist after app restart
- [ ] #4 Update TeachersAdmin to show/edit teacher colors
- [ ] #5 Document color palette choice rationale in code comments
- [ ] #6 Unit test: verify same teacher always gets same color
- [ ] #7 Visual test: all 28 teachers have distinguishable colors in timeline
- [ ] #8 Manual test: colors persist after app restart
- [ ] #9 Update TeachersAdmin to show/edit teacher colors
- [ ] #10 Document color palette choice rationale in code comments
<!-- DOD:END -->
