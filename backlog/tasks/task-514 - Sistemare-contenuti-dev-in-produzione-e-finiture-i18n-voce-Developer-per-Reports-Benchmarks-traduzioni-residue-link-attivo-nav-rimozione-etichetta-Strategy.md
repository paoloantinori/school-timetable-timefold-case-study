---
id: TASK-514
title: >-
  Sistemare contenuti dev in produzione e finiture i18n (voce Developer per
  Reports/Benchmarks, traduzioni residue, link attivo nav, rimozione etichetta
  Strategy)
status: To Do
assignee: []
created_date: '2026-08-17 06:52'
labels:
  - claude-planned
  - ui
  - i18n
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla valutazione UX 17/08 (due opportunita' fuse perche' affini e basso sforzo): (1) Reports e Benchmarks sono contenuti dev esposti in produzione senza spiegazione (Reports = file KPI interni, Benchmarks = dati del marzo 2026 che sembrano attuali); (2) traduzioni IT residue ('Progression', 'Export/Import', 'Solution not yet scored', 'Strategy') e salto di lingua tra sezioni documentali; (3) manca l'evidenza del link attivo nel nav. Anche l'etichetta 'Strategy: Cartesian + Block Entity' (nome storico del branch family) va rimossa o rimpiazzata con qualcosa di vero per l'utente (es. anno scolastico).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Voce 'Developer' nel nav che raccoglie Reports e Benchmarks, con avvertenza che sono artifact interni di sviluppo
- [ ] #2 Reports (oggi 471 righe di file KPI tipo confronto-*.html) non piu' esposti come sezione primaria
- [ ] #3 Benchmarks: i 268 risultati storici (ultimo 2026-03-17) etichettati come storici o filtrati
- [ ] #4 Traduzioni IT completate: 'Progression', 'Export/Import', 'Solution not yet scored' e affini; decisione presa sulla lingua delle sezioni documentali (Story/Reports it vs Docs/Phases en)
- [ ] #5 Link attivo evidenziato nel nav
- [ ] #6 Typecheck e build UI verdi
<!-- AC:END -->
