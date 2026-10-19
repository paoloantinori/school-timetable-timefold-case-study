---
id: TASK-317
title: Upgrade Timefold Solver 2.0.0 → 2.1.0
status: Done
assignee: []
created_date: '2026-06-16 08:36'
updated_date: '2026-06-16 09:16'
labels:
  - upgrade
  - timefold
milestone: m-0
dependencies: []
priority: medium
ordinal: 141000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bump Timefold Solver from 2.0.0 to 2.1.0 (latest stable, May 2026). This is a minor release with no breaking API changes — OpenRewrite migration recipe available but not required.

**Motivation:**
- Multi-threaded solving improvements (sweet spot shifted from ~4 threads to 8–16)
- Precompute bug fixes — we're about to adopt precomputed joins (TASK-314), so land on 2.1.0 first
- Fail-fast detection for duplicate planning entities/facts (catches silent correctness bugs)
- Assorted bugfixes from 2.0.0 cracks

**Scope:**
- One-line version bump in `pom.xml` (`timefold-solver.version` property)
- Run OpenRewrite migration recipe to catch any silent deprecations
- Rebuild + run tests
- Verify solver still works end-to-end

**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md, claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 pom.xml timefold-solver.version = 2.1.0
- [x] #2 mvn clean package succeeds
- [x] #3 Test suite passes (constraint verifier + integration)
- [x] #4 Solver starts and produces a valid score on dev profile
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
**BLOCKED — discovered pre-existing test breakage.**

Attempted 2.0.0 → 2.1.0 bump on 2026-06-16. Discovered that 130+ tests are ALREADY FAILING on pristine HEAD (commit b406ee4) with Timefold 2.0.0. The failures are NOT caused by the 2.1.0 bump — they date back to the 2.0.0 upgrade itself (commit 02f05a6, TASK-306).

**Root cause:** Timefold 2.0.0 added strict constraint ID validation. The `AbstractConstraintBuilder.sanitize()` method (verified present in 2.0.0 jar via decompilation) rejects any character that isn't alphanumeric, space, underscore, hyphen, apostrophe, parenthesis, or full stop. Our entire convention uses `"H1: Teacher conflict"` format with a colon, which is rejected.

**Failing tests (111 errors in ConstraintProviderTest alone):**
- SchoolScheduleConstraintProviderTest: 111 errors
- JustificationTest: 14 errors
- ScheduleResourceTest: 5 failures
- SchoolScheduleSolverIntegrationTest: 3 errors

**Implication:** The 2.1.0 upgrade is not blocked by anything new — the SAME validation exists in 2.0.0. Both versions require migrating constraint IDs from `"H1: Name"` to `"H1 - Name"` (or similar allowed separator).

**Defer this task until TASK-318 (convention migration) is complete.** After the convention is migrated, the 2.1.0 bump becomes a trivial one-liner.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Bumped `pom.xml` `timefold-solver.version` from 2.0.0 to 2.1.0. All 273 tests pass. No code changes required beyond TASK-318 (constraint ID convention migration).

**Gains from 2.1.0:**
- Multi-threaded solving improvements (sweet spot shifted from ~4 to 8-16 threads)
- Precompute API bug fixes (relevant for upcoming TASK-314)
- Fail-fast detection for duplicate planning entities
- Assorted bugfixes from 2.0.0 cracks
<!-- SECTION:FINAL_SUMMARY:END -->
