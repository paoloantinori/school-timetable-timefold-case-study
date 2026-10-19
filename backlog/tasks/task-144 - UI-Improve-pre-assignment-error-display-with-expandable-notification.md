---
id: TASK-144
title: 'UI: Improve pre-assignment error display with expandable notification'
status: Done
assignee: []
created_date: '2026-03-01 17:06'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - pre-assignment
  - error-handling
  - playwright
  - claude-planned
dependencies: []
priority: medium
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

When pre-assignment fails, errors are displayed as brief status text next to the Pre-assign button: "Pre-assign error: Phase 1 (Acconciatura) failed". This provides insufficient context for users to understand what went wrong or how to resolve it.

The existing success notification section (shows "Pre-assignment started" with copy/dismiss) should be extended to also handle errors with expandable details.

## Current Implementation

- **Components affected**: `SolverControls.tsx` and `UnifiedSolverControls.tsx`
- **Current error display**: Brief red text span
- **Success notification**: Expandable section with summary, details, copy, dismiss
- **API error field**: `schedule.preAssignmentError` (string)

## Design Direction

**Tone**: Industrial/utilitarian with clear visual hierarchy - errors command attention without panic.

**Key Features**:
1. **Progressive disclosure**: Summary → expandable details → full context
2. **Phase-aware icons**: Different icons for different failure phases
3. **Actionable guidance**: Suggest next steps when possible
4. **Consistent pattern**: Reuse notification section pattern from success messages

## Acceptance Criteria
<!-- AC:BEGIN -->
### AC1: Error Notification Section
- [ ] #1 Errors appear in the SAME notification section as success messages (not separate red text)
- [ ] #2 Error notification uses red/destructive color scheme with clear visual distinction
- [ ] #3 Includes phase-specific icon (⚠️ for warnings, ❌ for failures)
- [ ] #4 Shows timestamp when error occurred

### AC2: Expandable Error Details
- [ ] #5 "Show details" button expands to reveal full error message
- [ ] #6 Details section shows:
  - Failed phase name (e.g., "Phase 1: Acconciatura")
  - Full error message from API
  - Candidates tried before failure (if available)
- [ ] #7 "Hide details" collapses the section

### AC3: Copy Error to Clipboard
- [ ] #8 Copy button copies full error details (not just summary)
- [ ] #9 Shows "Copied!" feedback on successful copy
- [ ] #10 Copied text is formatted for easy sharing (markdown-friendly)

### AC4: Dismiss Error
- [ ] #11 × button dismisses the error notification
- [ ] #12 Dismissed errors clear the `preAssignmentError` state
- [ ] #13 Dismiss action triggers `POST /api/schedule/pre-assign/reset` to clear server-side error state

### AC5: Error State Persistence
- [ ] #14 Error persists across re-renders until explicitly dismissed
- [ ] #15 Error state cleared when user clicks Pre-assign again (new attempt)
- [ ] #16 Error state cleared on successful pre-assignment completion

### AC6: Accessibility
- [ ] #17 Error notifications use `role="alert"` for screen readers
- [ ] #18 Expand/collapse buttons have proper `aria-expanded` attributes
- [ ] #19 Color is not the only indicator (icons + text + color)

### AC7: Playwright Verification
- [ ] #20 E2E test: Error notification appears when pre-assignment fails
- [ ] #21 E2E test: Details expand/collapse works correctly
- [ ] #22 E2E test: Copy button copies error to clipboard
- [ ] #23 E2E test: Dismiss button clears error notification
<!-- AC:END -->

## Implementation Notes

### Files to Modify
1. `src/main/webui/src/components/UnifiedSolverControls.tsx` - Primary target
2. `src/main/webui/src/components/SolverControls.tsx` - Legacy component (if still used)
3. `src/main/webui/src/components/PreAssignErrorNotification.tsx` - New component (extracted)

### Suggested Component Structure
```tsx
interface PreAssignErrorNotificationProps {
  error: string;
  phase: string | null;
  candidatesTried: number;
  timestamp: Date;
  onDismiss: () => void;
}
```

### CSS Classes to Add
- `.pre-assign-error` - Container with red border/background
- `.pre-assign-error__summary` - Brief error summary
- `.pre-assign-error__details` - Expandable details section
- `.pre-assign-error__actions` - Copy/dismiss buttons

### API Considerations
- Current API returns `error: string | null` - may need enhancement to return structured error with phase, candidates, timestamp
- Consider adding `preAssignmentErrorTime` to API response for accurate timestamps

## Verification

1. **Manual testing**: Trigger pre-assignment failure (e.g., invalid data) and verify notification appears
2. **Playwright tests**: Run `./scripts/ui-test.sh pre-assign-error` after implementation
3. **Accessibility audit**: Use browser dev tools to verify ARIA attributes
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
### Files to Modify
1. `src/main/webui/src/components/UnifiedSolverControls.tsx` - Primary target
2. `src/main/webui/src/components/SolverControls.tsx` - Legacy component (if still used)
3. `src/main/webui/src/components/PreAssignErrorNotification.tsx` - New component (extracted)

### Suggested Component Structure
```tsx
interface PreAssignErrorNotificationProps {
  error: string;
  phase: string | null;
  candidatesTried: number;
  timestamp: Date;
  onDismiss: () => void;
}
```

### CSS Classes to Add
- `.pre-assign-error` - Container with red border/background
- `.pre-assign-error__summary` - Brief error summary
- `.pre-assign-error__details` - Expandable details section
- `.pre-assign-error__actions` - Copy/dismiss buttons

### API Considerations
- Current API returns `error: string | null` - may need enhancement to return structured error with phase, candidates, timestamp
- Consider adding `preAssignmentErrorTime` to API response for accurate timestamps

## Verification

1. **Manual testing**: Trigger pre-assignment failure (e.g., invalid data) and verify notification appears
2. **Playwright tests**: Run `./scripts/ui-test.sh pre-assign-error` after implementation
3. **Accessibility audit**: Use browser dev tools to verify ARIA attributes
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:NOTES:END -->
