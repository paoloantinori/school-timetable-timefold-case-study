---
id: TASK-513
title: >-
  Protezione visiva dei bottoni distruttivi (Reset/Delete/Upload: stile danger +
  conferme visibili)
status: Done
assignee: []
created_date: '2026-08-17 06:52'
labels:
  - claude-planned
  - ui
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla valutazione UX 17/08: i bottoni distruttivi (Reset in home, Delete su ogni riga admin, Upload CSV che sovrascrive dati) sono visivamente identici alle azioni innocue. La doppia conferma del Reset esiste (TASK-388) ma un utente nuovo non lo sa dal primo sguardo. Basso sforzo, alto impatto percepito. Il valutatore non ha potuto testare le conferme senza rischiare dati reali: verificare anche il comportamento effettivo, non solo lo stile.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Stile danger (rosso + icona) su Reset (home), Delete (ogni riga admin) e Upload CSV (sovrascrive dati)
- [ ] #2 La protezione esistente del Reset (doppia conferma TASK-388) resta intatta e diventa visibile dal primo sguardo (testo del bottone o tooltip che la dichiara)
- [ ] #3 Delete admin: conferma inline o richiesta di conferma prima dell'eliminazione definitiva
- [ ] #4 Verificato che nessun azione distruttiva e' raggiungibile con un singolo click accidentale
- [ ] #5 Typecheck e build UI verdi
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
