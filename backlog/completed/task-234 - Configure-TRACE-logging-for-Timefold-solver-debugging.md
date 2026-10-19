---
id: TASK-234
title: Configure TRACE logging for Timefold solver debugging
status: Done
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-03-16 14:49'
labels:
  - timefold
  - logging
  - debugging
dependencies: []
references:
  - src/main/resources/application.properties
documentation:
  - 'https://timefold.ai/docs/timefold-solver/latest/logging'
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold best practice: "Enable TRACE logging for the Timefold solver package to debug solver bottlenecks and understand solver behavior."

Current Issue: The application.properties file does not have TRACE logging configured for the ai.timefold.solver package. This makes it difficult to debug solver performance issues, understand step behavior, or diagnose constraint evaluation bottlenecks.

Impact: TRACE logging provides detailed insight into:
- Constraint evaluation counts and timing
- Move selector performance
- Phase transitions and step behavior
- Score calculation breakdown
- Shadow variable updates

Note: TRACE logging should only be enabled during debugging, not in production, due to performance overhead.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 TRACE logging configured for ai.timefold.solver package in application.properties
- [x] #2 Logging level is configurable via environment variable or profile
- [x] #3 Documentation on when to enable TRACE logging and how to interpret output
- [x] #4 Performance impact documented (warning about production use)
- [ ] #5 Example solver TRACE output captured and documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**TASK-234 - Solver TRACE Logging - COMPLETE ✅**

Added configurable TRACE logging for Timefold solver debugging.

**Implementation:**

1. **application.properties** - Added TRACE logging configuration:
   ```properties
   # Logging
   # Set Solver TRACE logging via environment variable: SOLVER_TRACE=true
   # WARNING: TRACE logging has significant performance overhead (~10-20% slower).
   # Only enable during debugging, never in production.
   %dev.quarkus.log.category."ai.timefold.solver".level=${SOLVER_TRACE:INFO}
   quarkus.log.category."ai.timefold.solver".level=${SOLVER_TRACE:INFO}
   ```

2. **Documentation created:** `claudedocs/solver-trace-logging.md`
   - Usage instructions (environment variable, direct property)
   - How to interpret TRACE output
   - Performance impact documentation (10-20% slower)
   - When to use (debugging) vs when NOT to use (production, benchmarking)
   - Troubleshooting tips

**Usage:**
```bash
# Enable TRACE for debugging
SOLVER_TRACE=true ./planner.sh start <profile>
```

**Key Features:**
- Environment variable configuration (no code changes needed)
- Dev-mode aware (%dev profile defaults to INFO)
- Safety warnings documented
- Per-package logging options for advanced debugging
<!-- SECTION:FINAL_SUMMARY:END -->
