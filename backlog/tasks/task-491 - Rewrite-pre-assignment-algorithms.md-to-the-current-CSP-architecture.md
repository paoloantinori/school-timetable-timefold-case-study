---
id: TASK-491
title: Rewrite pre-assignment-algorithms.md to the current CSP architecture
status: Done
assignee: []
created_date: '2026-08-04 07:54'
updated_date: '2026-08-04 08:12'
labels:
  - claude-planned
  - documentation
  - read-only
dependencies: []
references:
  - claudedocs/pre-assignment-algorithms.md
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
  - CLAUDE.md
priority: low
type: docs
ordinal: 308000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Docs-vs-code audit (2026-08-04, TASK-283 coda) found claudedocs/pre-assignment-algorithms.md badly stale: it describes BacktrackingPreAssigner/LabBlockPreAssigner (DELETED) and the LabBlock entity (removed TASK-228/267). A STALE banner was added (commit 031adc6e) as a stopgap.

Full fix: rewrite the doc body to describe the CURRENT CSP-based pre-assignment pipeline: HybridPhase1aService (phase 1a CSP), DayAssignmentCsp (enumeratePairsRecursive, MAX_CANDIDATES), GreedyRotationAssigner (accepts partial), RotationCandidate/RotationTestRunner (early-abort), PreAssignmentValidator (canPinHardConstraints), PreAssignmentEntityExtractor/PreAssignmentContext. Authoritative sources: CLAUDE.md "Pre-Assignment Workflow", PROJECT_INDEX service layer, claudedocs/human-planner-heuristics.md, and the code itself. Verify accuracy (skeptic vs code). DoD: /simplify + /code-review high (accuracy) + commit+push.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DONE (2026-08-04). Full rewrite di claudedocs/pre-assignment-algorithms.md: sostituisce la descrizione stale (BacktrackingPreAssigner/LabBlockPreAssigner/LabBlock, rimossi) con l'architettura ATTUALE verify-vs-codice.

Contenuto (code-grounded, file:line refs): entry/orchestration (PreAssignmentResource async-202 -> HybridPhase1aService.run), la 5-phase pipeline (Phase 0 TASK-435 constrained-teacher DEFAULT ON, 0b TASK-449 mono-room-tag OFF, 1 acconciatura CSP, 2 altri-lab CSP always-success, 3 professionalizzanti deterministico), il CSP engine (GreedyRotationAssigner backtracking + DayAssignmentCsp meta-CSP MAX_CANDIDATES=10000), il validator gating (canPin/canPinHardConstraints), context/extractor batch preload, cosa si pinna vs solver, i -D flag live, il report. Sezione Dead/legacy: RotationCandidate test-only (mai istanziata in src/main), RotationTestRunner enumeration morta (sopravvivono POJO TestConfig/BlockKey/SlotAssignment + clearCapacityCache). Corregge anche CLAUDE.md ("acconciatura->labs->professionalizzanti" e' la sub-sequence; la pipeline piena e' Phase 0/0b prima).

DoD: /simplify (self-review struttura, em-dash clean) + /code-review high (skeptic Opus: TUTTI i claim concettuali/behavioral/default/dead-code CONFIRMED; 3 difetti localizzati di file:line + 1 overstatement self.get() trovati e corretti: computeValidDaysForPair :144->:364, comment TASK-435 :42-43->:528-539, self.get() scope ristretto a Phase 0/0b, RotationTestRunner "only POJOs" ammorbidito con clearCapacityCache). Doc-only -> test.sh N/A. NESSUNA adozione.
<!-- SECTION:FINAL_SUMMARY:END -->
