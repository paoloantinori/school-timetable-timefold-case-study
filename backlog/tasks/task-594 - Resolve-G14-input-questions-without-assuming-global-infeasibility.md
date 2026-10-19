---
id: TASK-594
title: Resolve G14 input questions without assuming global infeasibility
status: Done
assignee: []
created_date: '2026-09-09 10:11'
updated_date: '2026-09-11 07:58'
labels:
  - claude-planned
  - zero-hard-recovery
  - input-clarification
milestone: m-14
dependencies: []
references:
  - TASK-593
  - TASK-477
  - >-
    claudedocs/spalmone-2026-27/task577-results/twin-tail-a1/VERDICT-twin-tail-a1-2026-09-07.md
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Close the authoritative G14 calendar/availability/teaching-practice questions already raised in the referente questionnaire. The donor/slide dead end in the twin-tail report is not a global impossibility certificate. This task owns school semantics only; TASK-593 owns current-state search witnesses and TASK-477 retains room steering. Start reviewing the question and existing answers immediately, without waiting for the baseline task. No protected data edit or external message without required authorization.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Existing G14 questions and any school answers are located and linked without duplicate or unapproved outbound messages.
- [ ] #2 Current declared calendars, hours, teacher availability, and authoritative pins are reconciled against the questioned practice.
- [ ] #3 Each issue is classified as authoritative input contradiction, clarified valid restriction, or unresolved question; local move failure alone is not input-infeasibility evidence.
- [ ] #4 An authorized decision is recorded with scope and source; unanswered school questions remain explicitly blocked.
- [ ] #5 Any approved correction has synchronized input copies and passing relevant seed/VRP/provider regression tests; an unchanged-input decision is documented.
- [ ] #6 Changed input invalidates older experiment fingerprints and requires fresh control evidence before adoption.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AGGIORNAMENTO 11/09 (analisi golden del questionario, maintainer "approvo tutto"): il golden risponde alla gran parte delle domande G14 senza referente.

(1) TRIANGOLO Q1 RIFORMULATO: il golden colloca TUTTA la Confezione G14 (30h T16) al GIOVEDI' e i venerdi' (3h x 14) con CD/CS/Orientamento; T16 al venerdi' fa Confezione G01 (89h) + G09 (51h). Le opzioni A (taglio 2h) e C (venerdi' 2h) del questionario sono SMENTITE dai dati; B inutile. Foglio e golden G14 coincidono riga per riga per weekday (giov 73 + ven 42 + sab 5). Il difetto e' solver-side -> TASK-599 aperto (steering weekday per coppie docente-saturo). La consegna control-10sep perde 6pt su questa famiglia (3 ore H0).

(2) SABATO 16/01 (Q4): confermato dal golden (5h Orientamento T14 sabato, con 8h Stage G12 lo stesso giorno); NESSUNA cura input serve: il pool incarichi 115h non contiene le 5h e la differenza 120-vs-115 e' tutta li'. Documentato in referente-notifica-cure-golden-first-2026-09-11.md.

(3) Q3 G13 RITIRATA: ricalcolo per-data foglio-vs-golden delta mediano 0 (153/171 identiche, ±1h); il "10-12h/giorno" nasceva dal raddoppio con la riga Totale ore corso. Nessuna divergenza reale.

Residuo genuinamente di referente: conferma prassi giovedi'-G14 (riformulata), giorni voluti G12 (Q2, con risposta suggerita "subiti" dal golden che riempie 100% dei giorni con griglia).
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Obtain explicit authorization before protected input edits and external communication.
- [ ] #2 Apply /simplify and high-effort code review if code changes; document an evidence-only exemption otherwise.
- [ ] #3 Run applicable main-session input contract tests with exit codes and verify persisted decisions.
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
