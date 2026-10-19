---
id: TASK-584
title: >-
  Re-validazione 0hard sul vincolame corrente: batch bad-seed con
  H31+H32+H33+H34 attivi
status: Done
assignee: []
created_date: '2026-09-05 06:02'
labels:
  - claude-planned
  - solver
  - measurement
  - m-13
milestone: m-13
dependencies:
  - TASK-582
  - TASK-574
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MISURA, da fare DOPO i fix della linea SurplusHarvest (TASK-582) e OneOffFinisher (TASK-574), altrimenti misura il pass nella finestra sbagliata.

MOTIVO (nodo centrale dell'audit 2026-09-04, vedi audit-ultimi-2-mesi-2026-09-04.md sez. 3): la milestone m-13 e' dichiarata SBLOCCATA l'11/08 col claim "p(0-hard) = 32/32 = 1,0, Wilson95 [0,89,1,0]; multi-start consegna 0-hard statisticamente garantito". MA quella misura N=32 e' del 9-10/08, PRECEDENTE all'introduzione di quattro vincoli hard oggi DEFAULT ON: H32 quota-fill (19/08), H31 start-of-day (20/08), H33 day-floor (20/08), H34 empty-day (26/08). Conferma indipendente: lo 0hard storico del 25/08 girava su jar PRE-H34; ricalcolato col metro attuale farebbe -21 H34 (censimento backlog TASK-577). Quindi il claim "0hard garantito" NON e' stato re-validato sul vincolame corrente.

DA FARE: ripetere il bad-seed frequency batch (metodo del pool N=32 del 9-10/08, script scripts/benchmark/multistart-ab.sh o equivalente) ma sul vincolame CORRENTE (H31+H32+H33+H34 DEFAULT ON, SurplusHarvest winner-tail armato se TASK-582 chiuso). Metrica: p(0-hard) per singola costruzione + P(>=1 0-hard | N tentativi) al variare di N. Confronto col p=0,75-1,0 storico.

ESITI: (a) se p resta alta e N=3 copre con CI_lo>0,95: claim m-13 re-validato, si aggiorna la milestone con la data della re-validazione; (b) se p e' calata: si quantifica e si decide il budget N come decisione di COSTO (non si tocca il modello, direttiva primaria: correttezza logica sopra il costo computazionale).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Batch di N>=16 costruzioni GRASP indipendenti eseguito sul vincolame corrente, con score.txt per ciascuna
- [ ] #2 p(0-hard) per singola costruzione calcolata col vincolame corrente, con intervallo Wilson95
- [ ] #3 Verdetto scritto: il claim m-13 e' confermato / ridimensionato / smentito sul vincolame corrente, con la curva P(>=1 0-hard | N)
- [ ] #4 Se p e' calata: raccomandazione sul budget N del multi-start o sul budget di consegna, come decisione di costo (non di modello)
- [ ] #5 Riga di aggiornamento nella milestone m-13 che registra la data della re-validazione e l'esito
<!-- AC:END -->

## Implementation Notes

<!-- NOTES:BEGIN -->
DESIGN DEL BATCH (5/09, zai; le voci [FEDORA] restano a Fedora che consegna il piano al ritorno; NON lanciato).

1. VEICOLO: `scripts/benchmark/multistart-ab.sh` ESISTENTE e gia' risolve la logistica (N boot prod SEQUENZIALI su UN profilo, flag GRASP live per boot, nessun reset tra run: solve-staged ri-pianifica da zero). Per run i:
   `PLANNER_JAVA_OPTS="-Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=$i -Dplanner.multistart.max-attempts=1"` (+ vedi punto 2).
   maxAttempts=1 e' la metrica del pool storico: p(0-hard) per SINGOLA costruzione (il claim m-13 da re-validare).

2. [FEDORA] ARMING SurplusHarvest: TASK-582 (winner-tail) e' To Do. (a) SH OFF = misura il vincolame puro, confrontabile col claim storico; (b) SH ON v11-p2 = misura la configurazione candidate di produzione. RACCOMANDAZIONE zai: (a): la domanda 584 e' la re-validazione di un claim fatto SENZA SH; il contributo SH e' un delta da misurare dopo.

3. N E BUDGET (il vincolo duro): ciclo single-attempt ~1.5-2h (notte-4sep: tentativo-1 ~85 min al confine + stanze + rifinitura) => N=16 = 24-32h, NON sta in una notte. PIANO: driver RESUMABLE (appende i seed completati a un file di stato, li salta al restart): N=8 la prima notte (seeds 101-108), N=8 la seconda (109-116), statistiche pooled su N=16. Curva preliminare su N=8 con CI piu' largo la prima mattina.

4. RACCOLTA per run: score (api.sh score a consegna) + sanity-report + UNA riga doom-prediction-registry (l'estrattore /tmp/extract581.py gira sul log del run: il batch DOPPIA come accumulo del registro TASK-581). NO export/battery completa per run (8 x 1.2G evitati); capture completa SOLO per un eventuale run 0-hard da conservare.

5. STATISTICHE: p-hat = #0hard/N, Wilson95, curva P(>=1 0-hard | N) per N=1..5, confronto dichiarato contro p=0,75 (n=8, 3/08) e il 32/32 del 9-10/08 (PRE-H31/H32/H33/H34: questa e' la differenza di metro che il batch esiste per misurare).

6. PUNTI DI ATTENZIONE: (a) con maxAttempts=1 l'unico tentativo e' l'ultimo: il winner-refinement di consegna gira SEMPRE (skip-doomed-room non scatta) e la save post-rifinitura e' ora incondizionata (cure fd5872d4+169fba74): gli score consegnati sono confrontabili tra loro; (b) la riga "delivery concordance OK (H29...)" (TASK-526, cdc6844c) sara' la prima validazione LIVE del metro di fiducia su 8+ consegne consecutive; (c) il profile-predictor NON scarta mai l'ultimo tentativo: i bacini doomed girano fino in fondo (costo pieno per run, incluso nel budget del punto 3).

7. GATE OPERATIVI per OGNI boot (regola TASK-579): preflight pre-lancio (--classes SurplusHarvest se armato, --flags col seed del run) + post-lancio --expect-banner (grasp seed=i e surplus-harvest=... a seconda dell'arming); caffeinate; heartbeat-v2 pattern (poll diretto api.sh status ogni ~35 min, NESSUN watcher background: muoiono a ogni nuovo turno); log per run separato (/tmp/planner-prod-s$i.log).
<!-- NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
