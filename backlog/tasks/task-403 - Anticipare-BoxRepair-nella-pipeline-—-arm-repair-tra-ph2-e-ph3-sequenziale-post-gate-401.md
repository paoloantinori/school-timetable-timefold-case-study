---
id: TASK-403
title: >-
  Anticipare BoxRepair nella pipeline — arm "repair tra ph2 e ph3" (sequenziale,
  post-gate 401)
status: Done
assignee: []
created_date: '2026-07-23 17:30'
updated_date: '2026-07-26 13:49'
labels:
  - claude-planned
  - search
  - solver-config
  - spalmone-2027
dependencies:
  - TASK-415
priority: medium
ordinal: 400
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Suggerimento maintainer (2026-07-23), accodato durante la TASK-401: invocare il BoxRepair SOLO in
fase 4 potrebbe essere tardi nella catena — chiudere il box già PRIMA della ruin-recreate
(fase 3) le darebbe una base hard-0, e con base hard-0 la ruin (che accetta peggioramenti
temporanei) esplora il medium più liberamente, senza la priorità lessicografica dell'hard
residuo a condizionare ogni accettazione. Riduce anche il rischio che le celle libere per il
box spariscano prima che la fase 4 le enumeri.

**Analisi già fatta (non rifarla):**
- La premessa "la LS spreca budget inseguendo l'hard" NON regge ai dati: nel run 2 del 398
  A/B la ph2 tiene l'hard fermo a −8 ma lavora il medium da −2,31M a −1,41M per tutta l'ora
  (per questo il cutoff unimproved-600s non scatta: lo score migliora, sul tier medium).
  Il guadagno atteso è quindi search-freedom della ph3 + chiusura anticipata del box, NON
  recupero di budget.
- Subito dopo la CH l'anticipo è un NO-OP per costruzione: `boxUnplaceableLeftovers` È già
  quell'enumerazione a fine fase 1 — le celle fattibili per il residuo nascono solo col
  riarrangio della LS (misura TASK-399). Il punto utile più precoce è tra ph2 e ph3.

**Implementazione:** seconda entry `<customPhase>` con `CompactionPhase` in solverConfig.xml
tra la fase 2 (LS Compactness) e la ruin-recreate. La pipeline diventa 7 fasi → ripple
COMPLETO della checklist `.claude/rules/solver-config-sync.md`: SolverConfigParser,
SolverProgressionListener/PhaseProgressionService se sensibili al conteggio fasi,
solverPhases.ts, SolverPhasesPage, claudedocs/solver-phases.md, i18n. Validare con
validate-solver-xml.sh. (Alternativa più leggera da valutare: entry riusando la stessa
classe senza rinumerare la semantica UI — decidere in fase di piano.)

**Misura (A/B sul laptop, stessa procedura laptop-RUNBOOK):** arm vs HEAD — medium finale,
tempo-a-0-hard, box a fine ph2-repair vs fine ph4, KPI (full-day/blocchi/ricorrenza).

**GATED: eseguire SOLO dopo che il gate base della TASK-401 (A/B 398+401, in corso sul laptop
al momento della creazione di questo task) è PASSATO.** Se il gate base fallisce, questo
task si riesamina nel contesto della decisione revert.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh incl. golden, validate-solver-xml.sh, ui-typecheck/ui-build se il frontend è toccato), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-25 ~15:20: evidenza checkpoint avviata - CompactionPhase/BoxRepair eseguito sull'ingresso-fase-3 reale di val408-run1 (harness pattern BoxRepairCheckpointTest): misura in secondi cosa risolverebbe il repair anticipato.

EVIDENZA CHECKPOINT (E3, 2026-07-25): INCONCLUDENTE per limite fixture, dichiarato onestamente - i dump pipe di fase non portano il BOX, che e' proprio cio' che il repair anticipato risolve in produzione (ph4 reale: hard -31 -> -2, quasi tutto H0). Il repair sul grid fine-fase-2 ricostruito e' no-op (2 gap H17 non chiudibili, coerente col residuo reale -2). PREREQUISITO nuovo: estendere il dump di PhaseTransitionMarker con le righe boxed (modifica src/main piccola, stesso flag) - poi Task403EarlyRepairCheckpointTest (gia' scritto, gated PLANNER_TEST_403_REPAIR) diventa decisivo.

EVIDENZA DECISIVA (2026-07-26 03:31, harness locale sui dump con box della notte): sullo stato REALE di fine-fase-2 (val326-b1800-run1: box 34, gap 5, hard -39) il CompactionPhase anticipato ripulisce il box INTERO (34->0, 23 eviction, 0 conflitti aggiunti), gap 5->4, hard -39->-4, in <1s deterministico. Il repair anticipato fa al confine ph2/ph3 cio' che la fase 4 fa dopo. Resta da misurare su laptop l'effetto DOWNSTREAM (la fase 3 su stato hard-pulito smette di essere inerte?): arm da 1 riga di solverConfig (marker+CompactionPhase tra ph2 e ph3, ripple solver-config-sync) nel prossimo ciclo. Harness ora parametrizzabile (env value = path dump).

VERDETTO DAI 2 RUN laptop (2026-07-26, verificato sui raw e dal run.log): il repair anticipato al confine ph2/ph3 FUNZIONA ed e' riproducibile (-29/-21 hard -> -1 in <1s; fase 4 ridotta a no-op) MA la fase 3 resta INERTE anche partendo hard-pulita (+72 e +1.096 medium in 160/133s): l'ipotesi 'inerte perche' hard-blocked' e' REFUTATA. Sesta misura consecutiva di inerzia (val413 x2, val414 x2 implicite, val408 x2, val403 x2 hard-clean). Metriche finali invariate vs val408 entro varianza. CONCLUSIONE: il valore del repair anticipato NON e' migliorare il finale ma SEMPLIFICARE la pipeline - la vera azione e' RIMUOVERE la fase 3 (vedi proposta in coda al backlog); il repair puo' restare in fase 4 dov'e'. Questa task si chiude come RISPOSTA OTTENUTA senza adozione dell'arm.
<!-- SECTION:NOTES:END -->
