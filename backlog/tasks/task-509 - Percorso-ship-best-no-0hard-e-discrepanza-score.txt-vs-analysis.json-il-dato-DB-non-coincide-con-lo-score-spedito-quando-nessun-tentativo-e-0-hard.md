---
id: TASK-509
title: >-
  Percorso ship-best-no-0hard e discrepanza score.txt vs analysis.json (il dato
  DB non coincide con lo score spedito quando nessun tentativo e' 0-hard)
status: To Do
assignee: []
created_date: '2026-08-16 20:02'
labels:
  - claude-planned
  - solver
  - bug
  - multistart
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Terzo bug nuovo del round 3 (verdict 01ad26d9): quando nessun tentativo del multi-start arriva a 0-hard, il wrapper spedisce il best con sole stanze CH, e il dato caricato dal DB non coincide con lo score spedito: score.txt -42hard/-2.050.380 vs analysis.json -7795hard/-1.709.829 (delta hard -7753, delta medium -338k). Round 2: solo medium (-382k). SEMPRE treatment-specific: il control run3 e' identico nelle due sorgenti (0hard/-1.045.035). TASK-498 corner (b) prevede che il wrapper rifinisca le stanze del vincitore condannato PRIMA della consegna (la UI non deve mai ricevere un orario non rifinito): verificare se quel percorso e' stato rispettato dopo l'abort NPE del tentativo 5. Task gemello della diagnosi H29 (TASK-507) ma focalizzato sul contratto di consegna, non sul meccanismo di ricerca.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Percorso ship-best-no-0hard mappato nel codice (MultiStartRunner/ScheduleService wrapper): cosa viene spedito quando nessun tentativo arriva a 0-hard, quali rifiniture girano e quali no
- [ ] #2 Discrepanza score.txt vs analysis.json spiegata meccanicamente: round 2 solo medium (-382k), round 3 amplificata all'hard (-42 vs -7795). SEMPRE treatment-specific (control identico). Collegamento con l'abort NPE del tentativo 5 da verificare (score in-memory pre-abort vs stato DB post-rilascio pin)
- [ ] #3 Contratto definito e documentato: quale sorgente e' la verita' (il DB post-solve o lo score del solver) e perche'
- [ ] #4 Test localizzato che riproduca o catturi la divergenza (checkpoint-based se possibile)
- [ ] #5 Chiusura dell'AC#4 del TASK-502 che l'aveva issata a finding separato
<!-- AC:END -->
