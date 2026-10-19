---
id: TASK-315
title: Evaluate constraint profiling (requires Enterprise license)
status: Done
assignee: []
created_date: '2026-05-21 17:07'
updated_date: '2026-05-21 20:55'
labels:
  - solver
  - performance
  - blocked
milestone: m-1
dependencies: []
priority: medium
ordinal: 139000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold 2.x includes constraint stream profiling that measures time per constraint and per operation (JOIN/FILTER/SCORING). This would immediately identify our top 3-5 most expensive constraints.

**Blocker**: Requires Timefold Enterprise Edition. The community edition doesn't include profiling.

**Config is trivial** (5 min):
```xml
<scoreDirectorFactory>
    <constraintStreamProfilingEnabled>true</constraintStreamProfilingEnabled>
</scoreDirectorFactory>
```

**Alternative approaches if Enterprise is unavailable**:
1. Manually comment out individual constraints and benchmark move evaluation speed
2. Add timing instrumentation to each constraint method
3. Use the Quarkus property: `timefold.solver.constraint-stream-profiling-enabled=true`

**Decision needed**: Should we get an Enterprise license (free for non-profits/academic)? Or implement manual benchmarking?

**Reference**: claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Determine if Enterprise license is feasible
- [ ] #2 If yes: enable profiling and identify top 5 expensive constraints
- [ ] #3 If no: create manual benchmarking approach and identify top 5
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Evaluated constraint profiling. Key findings:
1. Requires Timefold Enterprise Edition — community edition does not include profiling
2. Config is trivial (one XML property) but won't work without Enterprise
3. Timefold offers free licenses for non-profits/academic use — the school could potentially qualify
4. Manual alternative: comment out individual constraints and benchmark move evaluation speed impact
5. Decision needed from project owner on whether to pursue Enterprise license
No code changes. Task remains as reference for when licensing is resolved.
<!-- SECTION:FINAL_SUMMARY:END -->
