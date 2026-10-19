---
id: TASK-548
title: >-
  Audit penalta' immutabili (pattern context-shift): pin-pin, over-cap da pin,
  costi costanti nello score
status: Done
assignee: []
created_date: '2026-08-24 14:05'
updated_date: '2026-08-24 14:15'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal punto 1 della TASK-547 (pattern context-shift di employee-scheduling, ritenuto promettente da maintainer il 24/08): audit delle penalta' costanti irraggiungibili nel nostro score. Noi abbiamo il problema in tre punti: (a) conflitti fra DUE pin autoritativi in H1/H3 (fatto immutabile: se penalizzati, e' rumore costante che inquina il pick-best); (b) over-cap H7/H11 su giorni dominati da pin (l'eccesso pre-esistente e' costante non correggibile: il pattern dice di non penalizzarlo, forma Math.max(limit, contextMinutes)); (c) il futuro solve su sottofinestre di produzione (fuori-finestra = context: contato nei vincoli di confine, mai modificato, mai penalizzato tra se'). Le penalta' costanti ingannano la ricerca e i confronti tra tentativi; possibile collegamento al floor misurato nell'era bendable (costi immobili). Lavoro da fare su Fedora con checkpoint/export esistenti; nessun run lungo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Misurazione meccanica dal checkpoint della migliore consegna disponibile (o export -6/-757): quanto hard/medium deriva da fatti immutabili (conflitti pin-pin in H1/H3, over-cap H7/H11 da pin autoritativi, altre penalta' costanti non correggibili da nessuna mossa)
- [ ] #2 Per ciascuna classe trovata: entita' della penalta' (gruppo/docente/data), ammontare, e se e' costante tra tentativi (rumore pick-best) o variabile
- [ ] #3 Se la misura da zero: verdetto 'nessuna penalta' immutabile' e chiusura
- [ ] #4 Se la misura da qualcosa: proposta di modello secondo il pattern context-shift (coppie context-context esenti, eccesso pre-esistente oltre cap non penalizzato, forma Math.max(limit, contextMinutes)) da portare a maintainer; NESSUN cambio al modello senza approvazione
- [ ] #5 Riferimenti: task547-community-projects-analysis.md punto 1 (pattern context-shift di employee-scheduling) + collegamento al floor del bendable (costi immobili)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
COMPLETATA su Fedora (669ba453), come suggerito da maintainer: nessun run, solo l'export -6 gia in locale. Misurate 10 classi immutabili (H1/H3 pin-pin, H7 over-cap, H5 vacation, H6 festivo, H26 no-session, H28 no-return, H17 buchi, H31 late-start, M33 surplus sopra quota): TUTTE A ZERO su 274 pin autoritativi. Verdetto AC#3: nessuna penalta' immutabile nel modello attuale; il pattern context-shift resta come reference design per il futuro solve su sottofinestre di produzione (fuori-finestra = context). Doc: task548-audit-immutable-penalties.md.
<!-- SECTION:NOTES:END -->
