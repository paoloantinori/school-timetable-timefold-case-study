---
id: TASK-553
title: >-
  TASK-551 exec: braccio medium-pura da 0hard (resume: +57k, inside-ciclo in
  corso)
status: Done
assignee: []
created_date: '2026-08-25 17:48'
updated_date: '2026-08-25 19:41'
labels:
  - claude-planned
dependencies: []
priority: medium
ordinal: 100
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-551 in esecuzione (via maintainer 18:46). Punto 1: braccio paired resume dallo stato 0hard del ciclo finisher-ON: LS medium-pura con accettazione stretta hard-sicura, 1200s, knob T549. RISULTATO (68001d4b): SMENTITA del <=5k atteso, il medium si muove di +57.608 (3,1%) mantenendo hard 0: il medium-post-repair di ieri era stato bocciato per la POSIZIONE (partiva da consegna convergente), non per l'idea; swap-early/0hard-precoce RIAPRE. Caveat: statics del resume vs consegna dichiarata. Punto 2 (in corso): conferma inside-ciclo, ciclo completo con la fase ON in coda (flag planner.construction.medium-pure.enabled DEFAULT OFF, commit ce4ca834). Implementazione: fase finale del ciclo, sorella del rimosso medium-post-repair, accettazione stretta.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Braccio resume (gia' misurato, 68001d4b): +57.608 medium (3,1%) in 1200s da stato 0hard, hard tenuto; caveat statics
- [ ] #2 Conferma inside-ciclo (in corso): ciclo completo con fase medium-pura ON in coda, atteso +40..60k senza rompere 0hard
- [ ] #3 Verdetto finale CONFERMATA/SMENTITA con entrambe le misure; raccomandazione adozione a maintainer (fase resta default-OFF fino al suo gate)
- [ ] #4 Se confermata: DoD della fase (/simplify + review + test.sh) prima della proposta di ratifica
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
COMPLETATA (f5ad9782): inside-ciclo +17.426 medium/1200s (predizione +40-60k SMENTITA in banda), hard -6 residuo. Caveat statics spiega ~2/3 del +57k del resume. La questione NON si chiude ne' come metro ne' come headroom puro: la fase inside trova 10x piu' di ieri ma non converte abbastanza da auto-giustificarsi. Fase resta DEFAULT OFF; raccomandazione = trade-off wall-vs-medium per maintainer.
<!-- SECTION:NOTES:END -->
