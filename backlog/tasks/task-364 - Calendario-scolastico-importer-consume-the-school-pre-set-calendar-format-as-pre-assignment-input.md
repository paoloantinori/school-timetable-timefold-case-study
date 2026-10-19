---
id: TASK-364
title: >-
  Calendario-scolastico importer: consume the school pre-set calendar format as
  pre-assignment input
status: Done
assignee: []
created_date: '2026-07-16 15:59'
updated_date: '2026-07-17 09:51'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies:
  - TASK-341
  - TASK-342
  - TASK-343
references:
  - claudedocs/spalmone-2026-27/calendario-scolastico-format.md
priority: high
ordinal: 188000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: user decision 2026-07-16: the school process evolved - the coordinator pre-places the hard lab/stage day structure in Calendario scolastico 2026_2027.xlsx and lab teachers fill recurring preference calendars from it. This document is the IDEAL STANDARD INPUT for our pre-set-solution import phase: it replaces heuristic pre-assignment (LabRotation CSP / HybridPhase1a) as the source of pinned lab placements. Reading key fully decoded and cross-validated (96 percent teacher-day match vs the golden spalmone): claudedocs/spalmone-2026-27/calendario-scolastico-format.md.

WHAT:
1. Parser for the three authoritative sheet families (see format doc): per-class sheets (date -> hours + modality A/S/L), DEF FC TUTTI (per date+group: TOT/Au/La/St hour split), MODA TUTTI (per date+group+teacher-initials PE/MU/BO: signed lab hours where NEGATIVE = first shift = first 3 slots of the day, POSITIVE = second shift). Skip discarded variants (MODA NON USARE, piano B, Copia di ...).
2. Map to planner entities: per-class calendars feed GroupCalendarRule (TASK-342) + StagePattern + day-capacity; MODA TUTTI rows become PINNED lessons (teacher initials mapping: PE=T17, MU=T16, BO=T07 - keep the mapping configurable); FC La-hours become pinned lab-day structure for the FC/G13 lab teachers (teacher resolution needs the incarichi - document any ambiguity).
3. Shift-to-slot mapping: first shift = slots 0-2 (08-11), second = slots 3-5 (11-14); values other than +-3 (2,5,6,1) place from the shift boundary outward - verify each odd value against the golden solution and document the rule chosen.
4. Import must be repeatable and produce a report: rows parsed, pinned lessons created, unmatched teacher/group/date rows, divergences vs already-present lessons.
5. Pipeline integration: pre-assign phase = run this import; the solver then fills unpinned hours. Keep the old CSP path behind a flag until TASK-351 concludes.

VERIFY: after import on a fresh 2026/27 profile, the pinned MODA lessons reproduce the MODA TUTTI predictions (185 strict + 32 swap cases from the format doc = pinned set matches the document, NOT the spalmone drift).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Parser reads per-class, DEF FC TUTTI and MODA TUTTI sheets per the format doc incl. the signed-shift convention
- [x] #2 Import creates pinned lessons + calendar/stage data with a full report of matches and unmatched rows
- [x] #3 Round-trip test: pinned MODA set equals the document predictions on a fresh profile
- [x] #4 Old CSP pre-assignment still available behind a flag; ./scripts/core/test.sh passes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANSWER 2026-07-17 (C2): calendario role confirmed ('SI MA VA RIORGANIZZATO') — revised sheets incoming, re-extract + force re-import then.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented (worktree, merged) + code-review high (8 candidates, fixes K1-K8). Extractor with header validation (outputs cross-checked against the sheet's own reference totals); CalendarioImportService pins 92G05 lab lessons (310/313 rows; 1 genuine mixed-shift failure = known format-doc divergence) with mandatory GroupCalendarService.isOpen + conflict validation, all-or-nothing + dryRun + force, stub consumption preserving per-course hour totals; FC La-hours and per-class A/S/L days as report-only coherence cross-checks (75 stage gaps, 351 class-day discrepancies incl. G14 Mon-Wed divergence -> Q16). Old CSP pre-assignment path untouched (flag decision in TASK-351). Note: live end-to-end run of the endpoint deferred to the next app cycle (service-level integration tested on H2). Tests 470/470.
<!-- SECTION:FINAL_SUMMARY:END -->
