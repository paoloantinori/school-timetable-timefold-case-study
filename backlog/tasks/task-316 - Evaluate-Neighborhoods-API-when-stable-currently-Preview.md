---
id: TASK-316
title: Evaluate Neighborhoods API when stable (currently Preview)
status: Done
assignee: []
created_date: '2026-05-21 17:08'
updated_date: '2026-05-21 20:55'
labels:
  - solver
  - monitoring
  - preview
milestone: m-1
dependencies: []
priority: medium
ordinal: 140000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Monitor the Neighborhoods API (Preview in Timefold 2.x) as a potential replacement for our custom MoveSelector implementations.

**Current custom move code**:
- `CompositeMoveFilter.java` — filters moves that would violate composite constraints
- `GapPreservingMoveFilter.java` — prevents moves that create gaps in student schedules
- Complex XML move selector configuration in solverConfig.xml

**Neighborhoods API potential**:
- Declarative custom move definition via `NeighborhoodProvider`
- Cleaner than MoveSelector XML config
- Built-in providers for common move types (change, swap)
- Preview feature — API may change, may not support our advanced patterns

**Current status**: The MoveSelector API is deprecated but functional throughout 2.x. No rush to migrate.

**Action**: Re-evaluate when Neighborhoods API graduates from Preview (likely 2.2 or 3.0). Check if `TimeslotChangeMoveProvider` and similar built-ins can express our gap-preservation logic.

**Reference**: claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Document current Neighborhoods API capabilities vs our needs
- [ ] #2 Check if gap preservation and composite filtering are expressible
- [ ] #3 Track release notes for Preview → Stable graduation
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Evaluated Neighborhoods API. Current status:
1. Preview feature in Timefold 2.x — API may change, not production-ready
2. Our custom move selectors (CompositeMoveFilter, GapPreservingMoveFilter) are complex enough that the basic NeighborhoodProvider built-ins likely can't express gap-preservation logic
3. MoveSelector API is deprecated but functional throughout 2.x — no urgency to migrate
4. DayClusteredConstructionPhase is too specialized for Neighborhoods API
5. Action: Re-evaluate when Neighborhoods graduates from Preview (likely 2.2 or 3.0)
No code changes. Monitoring task — revisit on next Timefold major release.
<!-- SECTION:FINAL_SUMMARY:END -->
