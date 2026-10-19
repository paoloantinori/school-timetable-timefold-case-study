---
id: TASK-511
title: >-
  Correggere la documentazione UI di M20 (semantica di marzo, superata da
  TASK-385/424/497)
status: Done
assignee: []
created_date: '2026-08-17 06:51'
labels:
  - claude-planned
  - ui
  - docs
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla valutazione UX 17/08 (audit fonte + passata browser). L'entrata M20 in docs.json (it e en) porta la semantica di marzo (task-230): 'Compattezza Orizzontale (Riempi Giorni Precedenti)', 'riempire i primi giorni prima... periodo di tolleranza di 30 giorni', esempio del riempimento consecutivo. Superata da tre revisioni: target fact-based TASK-385, target netto dei pin TASK-424, pool-cap TASK-497. Prova del drift: M33, scritta dopo, descrive M20 correttamente ('dove M20 addebita le ore che mancano a un giorno'). Unico contenuto davvero fuorviante trovato nella Docs page.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Nome, descrizione ed esempio di M20 aggiornati alla semantica corrente in it e en: penalizzazione del deficit di riempimento giornaliero rispetto al piano di ore attese della scuola per (gruppo, data) [TASK-385], target netto dei pin autoritativi [TASK-424], tetto al monte-ore assegnato [TASK-497 pool-cap]
- [ ] #2 Il nome non dice piu' 'Riempi Giorni Precedenti' ne' cita la tolleranza 30 giorni (rimossa)
- [ ] #3 L'esempio non parla piu' di colmare giorni consecutivi ma di rispettare l'atteso del calendario (es. un giorno atteso a 5h con 4h collocate = deficit 1h)
- [ ] #4 Verifica di coerenza: la descrizione di M33 (che descrive M20 correttamente) resta valida senza modifiche
- [ ] #5 Typecheck e build UI verdi
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
