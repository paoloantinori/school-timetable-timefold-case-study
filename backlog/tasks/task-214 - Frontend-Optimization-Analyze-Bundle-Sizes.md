---
id: TASK-214
title: 'Frontend Optimization: Analyze Bundle Sizes'
status: Done
assignee: []
created_date: '2026-03-09 16:35'
updated_date: '2026-03-10 05:28'
labels:
  - frontend
  - optimization
  - performance
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Analyze frontend bundle sizes and implement optimizations if needed to keep responses within reasonable buffer sizes.

**Current State (2026-03-10):**
- index.html: 650 bytes ✅
- Main JS bundle: 527KB ⚠️
- CSS bundle: 34KB
- Total: ~563KB

**Analysis Completed (2026-03-10):**
All analysis tasks completed via subagent exploration. Findings:

1. ✅ Code splitting: NOT configured in vite.config.ts
2. ❌ Source maps: Still ENABLED (no production disable)
3. ❌ Vendor chunks: No manualChunks configured
4. ⚠️ HTTP compression: DISABLED (intentional - Vert.x bug #13797 workaround)

**Bundle Composition:**
- Single monolithic 530KB JS bundle (index-BfZXsFni.js)
- vis-timeline is the largest single dependency
- All routes load upfront (no React.lazy/Suspense)
- No route-based or vendor-based code splitting

**Largest Dependencies:**
| Library | Impact | Notes |
|---------|--------|-------|
| vis-timeline | High | Core visualization, hard to replace |
| React + ReactDOM | High | 18.3.1, tree-shakeable |
| date-fns | Medium | Using individual imports ✅ |
| react-router-dom | Medium | 6.23.0 |
| react-window | Medium | Good for runtime performance |

**Quick Win Optimizations Available:**
1. Disable source maps in production (1 line in vite.config.ts)
2. Add route-based code splitting with React.lazy()
3. Configure manualChunks for vendor separation

**See:** memory/bundle-optimization-analysis.md for full subagent reports
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
**2026-03-10: Analysis Complete**

All four analysis tasks completed via subagents:
- Vite configuration explored: No code splitting, source maps enabled
- HTTP compression: Disabled (Vert.x bug workaround - intentional)
- Build output analyzed: 530KB monolithic bundle, vis-timeline is heaviest dep
- Dependencies reviewed: date-fns using good import patterns, react-window for virtualization

**Status:** Analysis complete, optimizations identified but not implemented.

**Next steps:** Implement quick wins (disable sourcemaps, add code splitting) if needed.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
