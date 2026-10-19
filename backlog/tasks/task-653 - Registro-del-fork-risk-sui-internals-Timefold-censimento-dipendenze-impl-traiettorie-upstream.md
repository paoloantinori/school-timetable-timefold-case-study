---
id: TASK-653
title: >-
  Registro del fork-risk sui internals Timefold (censimento dipendenze impl/* +
  traiettorie upstream)
status: Done
assignee: []
created_date: '2026-09-23 09:17'
updated_date: '2026-09-27 06:15'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
I fatti del 23/09 formano un pattern: #2649 riscrive MoveDirector (fatal-on-throw deliberato), l'Indictment API e' uscita dall'OSS main, lo strato pubblico api/score/analysis e' documentato 'exclusive to Enterprise Edition', il nostro MT-LS vive su wiring a reflection, e consumiamo ConstraintMatchPolicy.ENABLED + impl/* in tutta la delivery chain (CSF/EDF/HRF/638). Oggi siamo al sicuro (riverificato su rc-1, suite 1370/0, banco 3/3), ma la zona di dipendenza e' in ristrutturazione attiva. CONTENUTO: (1) censire OGNI dipendenza nostra da internals Timefold (impl/* importati, reflection wiring del decider, ConstraintMatchPolicy, preview API, Moves.compose/executeTemporaryMove) con per ognuna: versione di riferimento, traiettoria upstream nota, segnale d'allarme osservabile; (2) valutare per ognuna il piano B (fork, venduto propio, riscrittura); (3) VINCOLO sul follow-up mini-GLS nel decider (dalla ricerca 23/09): richiederebbe ENABLED durante LS, la cui raggiungibilita' su OSS e' ine censita (gap 3 del PoC 645) e potenzialmente enterprise-only: prima di investire nell'esperimento, il census deve tornare favorevole; (4) checklist upgrade 2.7.0 finale (29/09): aggiungere il passo 'setacciare release notes per sostituzioni delle nostre agganciate' (lezione 644: il workaround che avevamo costruito era gia' stato sostituito dall'API e non lo sapevamo). Artefatto: claudedocs/timefold-internals-risk.md.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ELEVAZIONE STRATEGICA (direttiva maintainer 23/09 sera, memoria open-core-posture-maintainer): accantonati i contributi upstream (643/645 shelved; 646/647/648 non si propongono piu' come issue, la loro sostanza confluisce qui). Il task diventa il piano di AUTOSUFFICIENZA: per ogni dipendenza da internals/enterprise-gated, la via di default e' la reimplementazione propria sul precedente MT-LS (decider nostro da agosto). Cio' che sappiamo gia' dall'evidenza 23/09: i constraint match via impl sono raggiungibili su OSS (le nostre vie funzionano su rc-1); l'abilitazione durante LS e' possibile ma solo via MonitoringConfig/environmentMode (da documentare nel registro); lo score analysis pubblico e' Enterprise-only ma noi usiamo il nostro drill-down. Niente progetti nuovi finche' l'open-core non ci morde davvero: il registro serve a VEDERLO arrivare.

2026-09-27 REGISTRO COMPLETO (claudedocs/timefold-internals-risk.md, 3c5f1a6e): 21 dipendenze censite in 5 famiglie (impl/score 4 voci, impl/localsearch 6 voci, impl/heuristic 4 voci, preview/api 5 voci, reflection 1 voce), ognuna con: dove, cosa facciamo, traiettoria upstream, allarme osservabile, piano B. Sintesi per priorita': ALTO = reflection wiring decider (MT-LS morto se rotto, piano B = eigen-solver); MEDIO = ConstraintMatch (enterprise-gated) e preview API Moves/metamodel (641/638 dipendono); BASSO = tutto il resto. Checklist upgrade 2.7.0 inclusa (7 passi meccanici). TASK CHIUSO.
<!-- SECTION:NOTES:END -->
