---
id: TASK-582
title: >-
  SurplusHarvest winner-tail: spostare il pass sul winner nel wrapper
  multi-start (dopo restore-best, pre-consegna)
status: Done
assignee: []
created_date: '2026-09-05 06:02'
labels:
  - claude-planned
  - solver
  - h34
  - surplus-harvest
milestone: m-13
dependencies:
  - TASK-581
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cura della famiglia hard dominante residua H34 (5-6 celle strutturali mag-giu, invarianti al seme). Il pass SurplusHarvest esiste ed e' sano (oracolo improve-or-noop, atomicita' per-cella v10), ma GIRA NELLA FINESTRA SBAGLIATA.

EVIDENZA (verificata sui raw): nei cicli v11 e v11-n2 tutte le passate al confine danno `moves=0` perche' le celle H34 che la consegna mostra NON ESISTONO ANCORA quando il pass gira (tracer surplus.trace: al confine post-B-ROOM il banco vede G04 marzo-aprile / G12 set-ott; le celle G08 maggio che il run consegna vuote si formano DOPO, lungo i tentativi successivi / le fasi successive). La seconda occasione in-ciclo (boundary POST_FINAL_SURPLUS_HARVEST dopo medium-pure) e' risultata RIDONDANTE (medium-pure DEFAULT OFF = no-op, stato identico fra le due occasioni).

LA FINESTRA VERA: sul WINNER nel wrapper multi-start, DOPO restore-best, PRIMA della consegna definitiva. Li' lo stato e' quello che verra' consegnato e le celle ci sono. Le 10 passate moves=0 del ciclo v11 lo dimostrano.

DA FARE: (1) spostare/aggiungere l'occasione SurplusHarvest nel MultiStartRunner dopo il restore del best: runSurplusHarvest(bestSolution) + saveSchedule esplicito; (2) assicurarsi che l'istanza su cui gira sia quella che persiste (vedi TASK-581); (3) rimuovere o disattivare le passate ridondanti in-ciclo che misurano moves=0 (sono rumore). Forma opt-out standard gia' esistente (planner.construction.surplus-harvest.enabled).

PRECONDIZIONE: TASK-581 (istanza orfana) deve decidere su quale istanza gira la tail, altrimenti il pass rischia di girare su un'istanza che non persiste.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 L'occasione SurplusHarvest gira sul winner dopo restore-best nel wrapper (riga di wiring citata), su un'istanza che persiste (deciso da TASK-581)
- [ ] #2 Le passate pre-esistenti ridondanti (post-B-ROOM o coda-tentativo, quelle che davano moves=0) rimosse o disattivate, con motivazione scritta
- [ ] #3 saveSchedule esplicito dopo la passata winner-tail
- [ ] #4 Banco: su uno stato con celle H34 residue note, il pass nella finestra winner-tail produce moves>0
- [ ] #5 Ciclo completo sul laptop: il log mostra la passata winner-tail con moves>0 e la consegna documenta se le celle H34 residue calano vs baseline v11-n2 (-13hard, H34 -6)
<!-- AC:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
