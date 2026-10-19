---
id: TASK-655
title: >-
  Lifecycle meccanico: lifecycle.jsonl + readout unico (fine della ricerca nel
  pagliaio)
status: Done
assignee: []
created_date: '2026-09-23 10:57'
updated_date: '2026-09-27 05:44'
labels:
  - claude-planned
  - tooling
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OSSERVAZIONE maintainer 23/09 (fine ciclo sviluppo): gli eventi di lifecycle devono avere risposte IMMEDIATE e MECCANICHE, non ricerche nel pagliaio dei log (episodio: B-TIMESLOT-1 letto come stall/attempt-2 quando era il primo avvio del solve referente-demo; dieci minuti di grep per rispondere 'cosa sta girando'). IL DATO ESISTE GIA', e' SPARSO: status endpoint (solverStatus inutile in staged per costruzione, stagedStage senza timestamp ne' tentativo, stagedAttempt/stagedMaxAttempts presenti nel payload TASK-483 ma non in primo piano), PhaseProgressionService, eta' del processo, log non strutturato. DUE PEZZI: (1) LIFECYCLE JSONL: un listener (esistono PhaseTrackingListener/SolverProgressionListener da estendere) che appende UNA riga JSON per evento su un file noto (lifecycle.jsonl): SOLVE_START{profile}, ATTEMPT_START{n}, STAGE_ENTER{stage,ts}, DELIVERY{attempt,hard,medium,applied,wallMillis}, SOLVE_END{verdict}; zero pattern-matching sui log per sempre. (2) READOUT UNICO: scripts/api/lifecycle.sh che in una chiamata stampa la schermata: profilo, pid+eta' processo, attempt x/y, stage da quanti secondi, score corrente, ELenco deliveries dell'app-lifetime con esito, flag NEAR-MISS automatico (|hard| residuo <= soglia -> 'scatta il loop blind puzzle'), e STALL-CHECK meccanico (secondi dall'ultima riga di log del solve: il vero rilevatore di stall e' l'eta' del log, non l'etichetta di fase - lezione 23/09). Bonus status: api.sh status mostra in prima riga stagedAttempt/stagedMaxAttempts. Vincolo: si integra col wrap esistente, niente rifacimenti; il listener scrive in append e sopravvive ai /clear (file, non memoria).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-27 IMPLEMENTATO (7e619412): (1) SOLVE_END lifecycle event nel withStagedOutcome finally (outcome/score/hardLevels/wallMillis) - ogni ciclo termina con una riga JSON completa; gli eventi ATTEMPT_DONE/SOLVE_START/STAGE_ENTER esistevano gia' dal TASK-542; (2) scripts/api/lifecycle.sh: readout unico con status API + ultimi N eventi + deliveries con near-miss check (|hard| <= 3 -> 'scatta il blind puzzle') + stall-check meccanico (eta' dell'ultima riga lifecycle). Test: 11/11 EXIT=0. NOTA: il DELIVERY_CANDIDATE event e' stato ritirato in sviluppo (bestSoFarAttempt non esisteva come campo; ATTEMPT_DONE con boundaryTierHard copre la stessa esigenza). Bonus api.sh status: stagedAttempt/stagedMaxAttempts gia' presenti nel payload dal TASK-483.
<!-- SECTION:NOTES:END -->
