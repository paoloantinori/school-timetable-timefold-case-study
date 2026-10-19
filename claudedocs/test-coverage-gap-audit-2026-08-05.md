# Test Coverage Gap Audit: `service/` + `solver/` (2026-08-05)

**Scope:** which critical `service/` and `solver/` classes lack tests.
**Method:** enumerated 37 service + 25 solver main classes (62 total), mapped each to
(a) a dedicated `*Test.java` and (b) any reference in `src/test/java` (direct or indirect
coverage via integration / snapshot / golden tests). A class with zero test references AND
no exercising endpoint is treated as a gap. Test pool: 120 files.

Grep-based mapping has a known limit: a class can be exercised by a REST endpoint test
without its name appearing. The Tier 1 candidates below were verified by hand against
endpoint coverage, not just name search.

## Tier 1: critical logic with NO test exercising it

### `ImportService` (service) (HIGH)
Schedule export/import round-trip (`/api/schedule/export`, `/api/schedule/import`) used for
A/B testing and checkpoints. No dedicated test; `ScheduleResourceTest` does not exercise
import/export; the offline snapshot harnesses (`SnapshotFixture.loadFromExport`) deliberately
bypass it. Its fidelity caveats (does not touch `DailyActivityQuota` / `GroupCalendarRule`,
truncates `LabRotation`) are documented in `CLAUDE.md` but UNTESTED. Load-bearing for the
entire A/B methodology.
**Recommended:** a round-trip test (export a solved schedule, import into a fresh profile,
assert lesson/timeslot/room/pinned equality, and assert the documented divergences).

### `PreAssignmentValidator` (service) (HIGH)
Validates pre-assignment feasibility. Zero references in tests. A bug surfaces as false
INFEASIBLE errors or silently invalid pins. Pure logic over a candidate set, directly
unit-testable.
**Recommended:** unit tests over feasible / infeasible candidate fixtures.

### `DayAssignmentCsp` (service) (MEDIUM)
The CSP engine at the core of pre-assignment (`HybridPhase1aService`). No direct unit test;
exercised only indirectly via `HybridPhase1aService` integration tests. A focused test would
isolate the 0-hard placement search from the orchestration.
**Recommended:** unit test feeding rotation candidates and asserting the 0-hard pin selection.

## Tier 2: critical classes with no DEDICATED unit test (covered indirectly)

Exercised by integration / snapshot / golden tests but no focused unit test, so a regression
they do not isolate is hard to localize:

- `DayClusteredConstructionPhase` (via `WeeklyTemplateConstructionRealSeedTest`)
- `WeeklyTemplatePlanner` (via construction tests + the M32 reference build)
- `CompactionPhase`, `StudentDayCompaction` (via checkpoint tests)
- `AnchorRepair` (via checkpoint tests, 4 refs)
- `BaseConstraintProvider` (via the combined `SchoolScheduleConstraintProviderTest`, 311 tests)
- `HybridPhase1aService` (9 refs, integration)
- `LessonGenerator` (7 refs)

Lower priority than Tier 1: these ARE tested, just not in isolation.

## Tier 3: low-criticality infrastructure (untested, low risk)

- `GapPreservingMoveFilter`, `UnpinnedLessonFilter` (move filters, 0 refs): passive filters.
- `PhaseTrackingListener`, `SolverProgressionListener` (listeners, 0 refs): observation-only.
- `MonoRoomTagAllocator`: `MonoRoomTagAllocatorTest.java` exists but is a STUB (zero
  references to the class). Either implement it or remove the stub.

## Not gaps

Value objects / reports / DTOs that do not need dedicated tests:
`CalendarioImportReport`, `GoldenImportReport`, `HybridPhase1aReport`, `FailureAnalysis`,
`LabSession`, `RotationCandidate`, `SlotCandidate`, `PreAssignmentContext`,
`PreAssignmentFeasibilityReport`, `PreAssignmentEntityExtractor`, `TeacherDayAssignment`,
`PhaseProgressionService`, `ValidationSnapshotRepository`, `DataMutationEvent`.

Indirectly covered (verified): `SolverConfigParser` (8 indirect refs across the snapshot
harnesses), `CompositionHoleCloser` (`compositionHoleCloserClosesBadSeed` in
`H17HoleCloseDiscriminatorTest`), `ConstraintMessageFormatter` (has a test).

## Summary

- 62 main classes in `service/` + `solver/`; 120 test files.
- **3 real gaps** (Tier 1): `ImportService` (HIGH), `PreAssignmentValidator` (HIGH),
  `DayAssignmentCsp` (MEDIUM).
- 7 critical classes indirectly covered with no dedicated unit test (Tier 2).
- 5 low-criticality infrastructure classes + 1 stub test (Tier 3).
- The constraint model and the deterministic repair passes (BoxRepair, HoleRepair,
  SurplusRebalance, LabDayConsolidation, ExactFinisher) and the construction template
  (WeeklyTemplateBuilder) all have direct tests. The gaps are in pre-assignment validation,
  the CSP core, and the schedule import/export fidelity.
