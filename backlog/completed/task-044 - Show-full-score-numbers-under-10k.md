---
id: task-044
title: Show full score numbers under 10k
status: Done
assignee: []
created_date: '2026-02-19 20:18'
updated_date: '2026-02-21 10:17'
labels:
  - claude-planned
  - ui
dependencies: []
priority: low
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Score display currently abbreviates all numbers using k/M suffixes. For small scores (under 10,000), this is less readable than showing the full number. Users want to see exact values when the score is close to zero (solution is nearly valid).

## User Value

- Better readability for near-optimal solutions (small hard scores)
- Exact score visibility when debugging constraint violations
- Gradual transition from abbreviated to full numbers as solution improves

## Current Behavior

The `formatNumber` function in `SolverControls.tsx` (line 177-183) always abbreviates:
```typescript
const formatNumber = (n: number): string => {
  const abs = Math.abs(n);
  const sign = n < 0 ? '-' : '';
  if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `${sign}${(abs / 1_000).toFixed(1)}k`;
  return String(n);
};
```

This means `-8,542` displays as `-8.5k`, losing precision exactly when it matters most.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a score with absolute value less than 10,000, when displayed in the UI, then show the full number without abbreviation (e.g., '-8,542' not '-8.5k')
- [x] #2 Given a score with absolute value 10,000 or greater, when displayed in the UI, then show the abbreviated format (e.g., '-12.3k' not '-12,345')
- [x] #3 Given a score in the millions range, when displayed in the UI, then show the 'M' suffix (e.g., '-1.2M' not '-1,234,567' or '-1234k')
- [x] #4 Given a score of exactly 0, when displayed in the UI, then show '0' not '0k' or '0.0k'
- [x] #5 Given a negative score, when displayed in the UI, then preserve the negative sign in the abbreviated form (e.g., '-8.5k' not '8.5k')
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Files to Modify
- `src/main/webui/src/components/SolverControls.tsx` (primary)
- `src/main/webui/src/components/AnalysisPanel.tsx` (if it uses same formatting)
- `src/main/webui/src/components/UnifiedSolverControls.tsx` (if applicable)

### Change Required

Modify the `formatNumber` function to add a threshold:

```typescript
const formatNumber = (n: number): string => {
  const abs = Math.abs(n);
  const sign = n < 0 ? '-' : '';
  
  // For small numbers (under 10k), show full number with locale formatting
  if (abs < 10_000) {
    return sign + abs.toLocaleString();  // e.g., "8,542" or "0"
  }
  
  // For larger numbers, use abbreviations
  if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}M`;
  // abs is now 10,000 to 999,999
  return `${sign}${(abs / 1_000).toFixed(1)}k`;
};
```

### Examples

| Input | Before | After |
|-------|--------|-------|
| 0 | "0" | "0" |
| 500 | "500" | "500" |
| -1234 | "-1.2k" | "-1,234" |
| 8542 | "8.5k" | "8,542" |
| -10000 | "-10.0k" | "-10.0k" |
| 1234567 | "1.2M" | "1.2M" |

### Edge Cases

1. **Zero**: Should display as "0" (no suffix)
2. **Locale**: Use `toLocaleString()` for thousands separator (respects user locale)
3. **Negative**: Preserve sign, apply to absolute value before formatting

### Testing

1. Start solver, observe score display
2. Wait for hard score to drop below 10k
3. Verify full number is shown
4. Check AnalysisPanel score breakdown uses same formatting

### Risks

| Risk | Mitigation |
|------|------------|
| Inconsistent formatting across components | Check all uses of formatNumber/formatScore |
| Locale formatting adds non-breaking spaces | Test in multiple browser locales |
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Single function change |
| Risk | 1 | Display only |
| Testing | 1 | Visual verification |
| Dependencies | 1 | None |
| **Total** | **1** | 15-30 minutes |

---

## Priority: **LOW** (Minor Polish)

Small UX improvement. Quick fix.
<!-- SECTION:NOTES:END -->
