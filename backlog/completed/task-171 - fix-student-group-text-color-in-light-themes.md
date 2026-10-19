---
id: TASK-171
title: fix  student group text color in light themes
status: Done
assignee: []
created_date: '2026-03-05 09:24'
updated_date: '2026-03-05 14:25'
labels:
  - ui
dependencies: []
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
the text is white on white background, or something very close to that, that makes it impossible to read to the human users. it renders correctly on the dark theme instead

use /frontend-design to help you
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed student group text color being unreadable in light themes after switching from school theme.

**Root cause**: `--header-text` CSS variable was only defined in school theme (#ffffff). When switching school → light, the stale white value remained on `:root`, causing white-on-light-gray text.

**Changes** (`src/main/webui/src/theme.tsx`):
1. Added `--header-text` to light (`#333333`) and dark (`#f5f5f5`) themes so all 3 themes are self-contained
2. Added cleanup logic in `useEffect` to `removeProperty()` all theme variables before applying new theme, preventing any future stale variable bugs

Commit: 9ff6ba4
<!-- SECTION:FINAL_SUMMARY:END -->
