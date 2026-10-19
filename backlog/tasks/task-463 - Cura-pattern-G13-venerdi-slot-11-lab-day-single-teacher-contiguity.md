---
id: TASK-463
title: Cura pattern G13 venerdi slot 11 (lab-day single-teacher contiguity)
status: Done
assignee: []
created_date: '2026-07-31 05:08'
updated_date: '2026-08-01 09:24'
labels:
  - claude-planned
milestone: m-12
dependencies: []
references:
  - claudedocs/spalmone-2026-27/spike-h17-holes-g13-friday-2026-07-31.md
  - claudedocs/spalmone-2026-27/task451-first-hour-slot-2026-07-30.md
  - 8dc0d7f9
documentation:
  - claudedocs/spalmone-2026-27/QUADRO-2026-07-30.md
  - claudedocs/spalmone-2026-27/handoff-2026-07-31-mattino.md
priority: medium
ordinal: 286000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La cura a valle dei 18 buchi H17 HARD mid-day lasciati dall'adozione di TASK-451
first-hour-slot (default ON da 401b5cac). Diagnosi (spike 8dc0d7f9): i 18 gap NON sono
H17 sparso, sono UN SOLO pattern: G13 venerdi slot 11, lab meccanica 5h, 18 occorrenze.
Il golden e il run 30d fanno il blocco compatto mono-docente (T10 8-12); il run col
first-hour-slot ON lo spezza su 2 docenti (T10 + T15) con un gap a slot 11. E'
artefatto di ricerca (feasible: golden/30d lo fanno), curabile a valle.

MECCANISMO: consolidamento del lab-day con >=N ore dello stesso tag su un DOCENTE UNICO
in blocco contiguo (come golden/30d T10 8-12 per G13 venerdi). Famiglia TASK-449 /
M-TC ma sul DOCENTE (coordinamento cross-gruppo del giorno del docente), non sul tag/aula:
la scarsita' congiunta qui e' (docente, giorno), non (risorsa, giorno). Vedi "LA TRIPLETTA"
nel QUADRO per il meccanismo comune.

Non e' un veto all'adozione di TASK-451 (gia' ratificata): e' il perfezionamento che
assorbe il costo gap del trade-off (16 late-start medium <-> 18 gap hard curabili).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flag default-OFF (opt-out, stessa forma delle altre leve di costruzione: -Dplanner.construction.<name>.enabled).
- [ ] #2 Misurato al CHECKPOINT di costruzione prima (ricorrenza, full-day, skipConflict non peggiorano), poi full-solve A/B x2 al gate laptop.
- [ ] #3 Riduce i 18 buchi H17 mid-day (G13 venerdi slot 11) SENZA reintrodurre i 16 late-start M18 di G11 ven che TASK-451 ha chiuso.
- [ ] #4 Il blocco lab-day >=N ore stesso tag su docente unico e' contiguo (golden-like: T10 8-12), non spezzato su 2 docenti.
- [ ] #5 Nessuna adozione senza gate laptop A/B x2 (direttiva primaria: correttezza logica del modello prima del costo).
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTATO + DoD piena, commit 50ff589c (pushato). Repair pass `LabDayConsolidation` (cross-group teacher-day coordinator) sibling di HoleRepair/AnchorRepair, post-HoleRepair/pre-AnchorRepair nel ciclo staged, flag default OFF self-gated.

ROOT CAUSE verificata sui raw (run1 lessons.txt): i 18 buchi H17 sono UN pattern (18 ven G13 gap@11), NON same-day split. Teacher e' per-Course: il gap esiste per scarsita' cross-gruppo (docente, giorno) - T10 e T15 saturati allo slot 11 dai gruppi FC. Cura = coordinatore cross-gruppo (rilascia il docente anchor saturato dal donatore, importa lezione del corso anchor), non consolidamento same-day. Anchor = plurality da incarichi (T10 110h >> T15 30h), NESSUNA lettura golden (nessuna voce TASK-452).

DoD: /simplify (5 finding applicati), /code-review high Opus (0 bug confermati, 3 watch-item uncertain - U1 H28 da grepbare al run ON), test.sh 915/0/0 EXIT 0, unit test 3/3.

PENDENTE: A/B full-solve x2 (463 OFF vs ON, 464 ON). Runbook in handoff-2026-07-31-sera.md. Dopo A/B: decisione adozione (maintainer). Follow-up candidato se residuo: vincolo-segnale LS (opz 2, non ora).
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-01 09:24
---
[zai 2026-08-01] RATIFICA ADOZIONE (maintainer, combined 463+464): flag DEFAULT ON con opt-out esplicito. isLabDayConsolidationEnabled convertito a forma opt-out (null o qualunque valore tranne "false" = ON), stessa forma di isLabFallbackEnabled/isFirstHourSlotEnabled. Evidenza adozione: il combined run3 source=input post-cura+473 con co-presenza ON + lab-day-consolidation ON chiude hard -6 (i 3 residui TASK-435), 0 room-null/H20/holes, ricorrenza unpinned 75,9% (migliore dei 3, batte golden 75,2%). DoD ratifica completa: /simplify (4 agent, diff pulito, finding 'estrai helper' saltato perche' confligge con altitude e toccherebbe codice certificato fuori-diff); /code-review high (2 skeptic Opus: 0 bug correttezza, 6 read-site tutti via helper con 0 bypass, 2 commenti stale corretti); test.sh 934/0/0 EXIT 0. CLAUDE.md aggiornato (sezione RATIFICA + javadoc). AC#5 (gate A/B) superato dal combined run3.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RATIFICATO DEFAULT ON 2026-08-01 (maintainer, combined con TASK-464). isLabDayConsolidationEnabled -> opt-out form (null|!"false" = ON, -Dplanner.construction.lab-day-consolidation.enabled=false per opt-out). Evidenza: combined run3 source=input con entrambi ON chiude hard -6 (i 3 residui TASK-435, 0 room-null/H20/holes, ricorrenza unpinned 75,9% migliore dei 3). DoD ratifica: /simplify clean (4 agent, finding estrai-helper saltato), /code-review high 2 skeptic Opus CONFIRMED (0 bug correttezza, 6 read-site tutti via helper, 0 bypass; 2 commenti stale corretti BaseConstraintProvider+RoomPhaseSnapshotTest), test.sh 934/0/0/16skip EXIT=0. CLAUDE.md: sezione RATIFICA combined + javadoc LabDayConsolidation/ScheduleService aggiornati (non piu' PROTOTYPE default OFF).
<!-- SECTION:FINAL_SUMMARY:END -->
