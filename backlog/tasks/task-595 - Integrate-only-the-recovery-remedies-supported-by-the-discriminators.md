---
id: TASK-595
title: Integrate only the recovery remedies supported by the discriminators
status: Done
assignee: []
created_date: '2026-09-09 10:12'
updated_date: '2026-09-10 23:24'
labels:
  - claude-planned
  - zero-hard-recovery
  - implementation-gate
milestone: m-14
dependencies:
  - TASK-593
  - TASK-590
references:
  - TASK-589
  - TASK-578
  - TASK-574
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Turn the scoped witness/census results into the smallest tested recovery patch. TASK-593 supplies near-delivery witness evidence; TASK-590 supplies construction attribution and conditional prototype evidence. Their completion may include a negative verdict: start implementation only for a positively supported route. Reuse existing repair/stream machinery rather than creating duplicate finishers. This task is conditional integration, not permission to adopt flags or change school input. If both routes are negative, record a replan blocker instead of marking implementation complete.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A candidate-selection record names each chosen mechanism, supporting witness, affected boundary, and rejected alternatives.
- [x] #2 Only supported changes are integrated with an explicit OFF control and regression coverage for pins, inventory, VRP freshness, undo, donor/blocker safety, and full hard-vector acceptance.
- [x] #3 Post-room changes are tested on verified near-delivery state; construction changes are tested from the production-equivalent pre-construction state.
- [x] #4 Candidate comparisons report all hard families and per-cell sanity changes on both reference fixtures; no hidden hole transfer or new genuine conflict is accepted.
- [x] #5 Unproven or rejected mechanisms stay disabled; no defaults or protected input are changed without explicit approval.
- [x] #6 A reproducible candidate artifact with source/build/input/flag manifest is delivered to adoption; no-positive-evidence leaves this task blocked and triggers replan.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Design (notte 10/09, in corso):

ROTTA SCELTA (AC#1): l'UNICA famiglia con evidenza positiva TASK-593 = H28 day-rotation (witness G03/Lab Termoidraulico 2026-10-01: A,A,B,B,B,A -> A,A,A,B,B,B, H28 4->2, tutte le altre 22 famiglie invariate). La rotta costruzione (substitution TASK-590) e' MISURATA-RESPINTA; i donor import depth-1/2/3 sono tutti LOCAL_NO_WITNESS (orfani di referente TASK-592/594).

MECCANISMO: H28RotationFinisher (solver/), sibling deterministico post-OneOffFinisher/pre-save. Generalizza la rotazione ciclica singola del witness a TUTTI gli ordinamenti a blocchi-contigui per docente (k! con k<=4): il gemello 09-24 che il witness non ha chiuso puo' chiudere con l'ordinamento alternativo (T-first). Invarianti strutturali: permutazione interna alle celle del proprio slot-set -> H17/H32/H33/H34/H7 invariati per costruzione; famiglie in gioco = H1/H3/H5/VRP (catena Grid.cellOk), H2 (stanza del mover), H-COPRESENCE (GATE IN-PASS dedicato: delta (tag,startDateTime) vs profondita' golden, speculare a coPresenceCap; introdotto dal finding #1 della code-review), H28 (rimossa dalla contiguita'). Pin: cella con pin/promotedPin = pinBlocked (esito terminale onesto; in produzione il RELEASE TASK-419 demote i promoted prima del confine, verificato). Celle con slot duplicati (stato H3-corrotto): skip + locked (hardening finding #2 della review). Semantica A..B..A a fonte unica: statiche condivise SchoolScheduleConstraintProvider.h28ReturnPair/h28Between usate da stream E detection (precedente isQuotaFillExcluded).

FLAG: planner.construction.h28-finisher.enabled, opt-in DEFAULT OFF (meccanismo non provato; la ratifica e' di TASK-596). Banner prototype in refreshSolverStatics. Knobs 129.

VALIDAZIONE FINALE: (a) H28RotationFinisherTest 10/10 EXIT 0 (flag-off inert, witness pattern, pin-block, H1-lock con revert totale, secondo-ordinamento, gate co-presenza blocca/lock, slot duplicati, predicati puri); (b) WitnessBenchTest#h28FinisherGate TEST AUTONOMO (76-78s, non paga il banco da 10min): fixture A pristine H28 4->2 con 23 famiglie byte-identiche, fixture B inerte; (c) GoldenSolutionRegressionTest EXIT 0 (neutralita' estrazione provider); (d) suite completa in corso.

DOCS: claudedocs/spalmone-2026-27/task595-results/CANDIDATE-SELECTION-2026-09-11.md (decisione + alternativo respinte + round di review) + bench-log-excerpt.txt + gate-final-run.txt.

REVIEW (high, 2 verificadori indipendenti): finding confermati TUTTI fixati (H-COPRESENCE gate, duplicate-slot hardening, javadoc H2 invertito). Finding pre-esistente FUORI diff filed: TASK-598 (statics pollution GoldenSolutionRegressionTest+SchoolScheduleConstraintProviderTest, riprodotto su HEAD pulito).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso con commit c5f64fb6 (pushato). Integrata L'UNICA cura supportata dai discriminatori TASK-593: H28RotationFinisher, pass deterministico post-OneOff/pre-save che riordina le celle A..B..A in blocchi contigui per docente sullo stesso slot-set, enumerando tutti gli ordinamenti (k!<=24). Evidenza: gate autonomo su fixture delivery (H28 4->2 su slotretry, 23 famiglie byte-identiche, v132 inerte, gemello 09-24 locked per enumerazione completa), unit 10/10, golden EXIT 0, suite 1303/0 EXIT 0. Flag opt-in DEFAULT OFF (ratifica = TASK-596). Review high: 3 finding confermati fixati (gate H-COPRESENCE in-pass, hardening slot duplicati, javadoc); 1 pre-esistente fuori-diff filed come TASK-598. Semantica A..B..A a fonte unica provider<->pass (h28ReturnPair/h28Between). MATEMATICA DI CONSEGNA: [-13/0]hard -> [-11/0] col flag ON, consegna invariata col default.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /simplify on the complete implementation diff.
- [x] #2 Run high-effort code review; fix confirmed findings or record justified deferred tasks.
- [x] #3 Main session runs scripts/core/test.sh with explicit exit code plus frontend checks if touched.
- [x] #4 Refresh graphify after code changes and regenerate/verify knob documentation if flags change.
<!-- DOD:END -->
