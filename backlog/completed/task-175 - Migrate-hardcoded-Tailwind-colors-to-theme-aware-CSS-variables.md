---
id: TASK-175
title: Migrate hardcoded Tailwind colors to theme-aware CSS variables
status: Done
assignee: []
created_date: '2026-03-05 12:47'
updated_date: '2026-03-05 14:35'
labels:
  - frontend
  - accessibility
  - ux
  - claude-planned
dependencies: []
priority: medium
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Many components use hardcoded Tailwind color classes (`bg-red-50`, `text-teal-600`, `bg-amber-100/15`, etc.) that don't adapt to the project's custom theme system (light/dark/school). Light-tinted backgrounds (`*-50`, `*-100`) are particularly broken on dark theme — they become jarring bright rectangles or lose text contrast.

The project uses CSS variables defined in `theme.tsx` (not Tailwind's `dark:` prefix), so all theme-sensitive colors must use `var(--...)`.

**Note:** Saturated button backgrounds (`bg-*-500 text-white`) are OK — they carry their own contrast. The problem is exclusively with **tinted/light backgrounds** and their associated text/border colors.

## Audit Results

### Critical — User-facing interactive elements

| Component | Issue |
|-----------|-------|
| `PreAssignErrorNotification.tsx` | `bg-red-50/80`, `text-red-600`, `border-red-200`, `bg-amber-50`, `text-amber-800` |
| `UnifiedSolverControls.tsx:514-516` | Speed indicator: `bg-amber-50`, `bg-green-50`, `bg-red-50` |
| `StatusBadge.tsx:8-17` | Phase badges: `bg-amber-100/15 text-amber-600`, `bg-green-100/15 text-green-600`, etc. |
| `ScheduleSidebar.tsx:114-115` | Uses `dark:` prefix which won't work (no Tailwind darkMode config) |
| 7 admin pages | Error banners: `bg-red-100 text-red-600`; success: `bg-green-100 text-green-700` |

### Medium — Documentation/info pages

| Component | Issue |
|-----------|-------|
| `AnalysisPanel.tsx` | Constraint level badges/tabs: `bg-red-100`, `text-red-600`, `border-red-200` |
| `DocsPage.tsx` | Level dots and section borders |
| `ComplexityPage.tsx` | Level indicators: `bg-red-500/10`, `border-red-500/20` |
| `SolverPhasesPage.tsx` | CH/LS badges: `bg-emerald-500/10`, `text-emerald-700` |
| `ConstraintCard.tsx` | Level backgrounds: `bg-red-500/[0.08]` |

## Implementation Approach

Add semantic CSS variable groups to `theme.tsx` (light, dark, school):

| Variable Group | Covers |
|----------------|--------|
| `--feedback-error-bg/border/text` | Admin banners, PreAssignErrorNotification |
| `--feedback-success-bg/border/text` | CsvImportWizard, CoursesAdmin success |
| `--feedback-warning-bg/border/text` | Suggestion boxes, speed stalled indicator |
| `--phase-ch-bg/text` | StatusBadge, SolverPhasesPage |
| `--phase-ls-bg/text` | Same |

The existing `--score-hard/medium/soft-*` variables can be reused for constraint indicators (same semantic concept).

Existing `--toast-success/error-*` variables (added in commit 3c75d64) already cover the UnifiedSolverControls toasts.

## Files to modify

- `theme.tsx` — Add ~15 new CSS variables per theme
- `PreAssignErrorNotification.tsx` — Use `--toast-error-*` vars
- `StatusBadge.tsx` — Use `--phase-*` vars
- `UnifiedSolverControls.tsx` — Speed indicator use `--feedback-*` vars
- `AnalysisPanel.tsx` — Use `--score-*` vars (already exist)
- `ScheduleSidebar.tsx` — Replace broken `dark:` with CSS vars
- Admin pages (7 files) — Use `--feedback-error/success-*` vars
- `DocsPage.tsx`, `ComplexityPage.tsx`, `ConstraintCard.tsx` — Use `--score-*` vars
- `SolverPhasesPage.tsx` — Use `--phase-*` vars
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All tinted backgrounds (bg-*-50, bg-*-100) replaced with theme-aware CSS variables
- [ ] #2 PreAssignErrorNotification readable on dark and school themes
- [ ] #3 StatusBadge phase indicators readable on all three themes
- [ ] #4 Admin error/success banners readable on dark theme
- [ ] #5 Speed indicator in UnifiedSolverControls readable on dark theme
- [ ] #6 ScheduleSidebar remove broken dark: prefixes, use CSS vars instead
- [ ] #7 No Tailwind dark: prefix used anywhere (project doesn't support it)
- [ ] #8 Visual verification on all 3 themes: light, dark, school
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Migrated all hardcoded Tailwind color classes to theme-aware CSS variables across 20 files.

**Changes:**
- 7 admin pages: error/success banners → `--feedback-error/success-*` vars
- `PreAssignErrorNotification.tsx`: red/amber backgrounds → `--toast-error-*`, `--feedback-warning-*`
- `StatusBadge.tsx`: phase badges → `--phase-*` vars
- `UnifiedSolverControls.tsx`: speed indicators → `--feedback-*` vars
- `ScheduleSidebar.tsx`: removed broken `dark:` prefixes, uses CSS vars
- `AnalysisPanel.tsx`, `DocsPage.tsx`, `ComplexityPage.tsx`, `ConstraintCard.tsx`: constraint indicators → `--score-*` and `--constraint-*` vars
- `tailwind.config.js`: extended with CSS variable references for new utility classes

Commit: f53f358
<!-- SECTION:FINAL_SUMMARY:END -->
