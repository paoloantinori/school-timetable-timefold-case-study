---
id: TASK-591
title: Upgrade Timefold oltre 2.3.0 + integrazione Neighbors API
status: Done
assignee: []
created_date: '2026-09-09 04:37'
updated_date: '2026-09-09 04:37'
labels:
  - claude-planned
  - dependency-upgrade
dependencies: []
references:
  - claudedocs/orchestration-layer-architecture.md
documentation:
  - 'https://docs.timefold.ai'
  - claudedocs/orchestration-layer-architecture.md
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Upgrade di Timefold Solver oltre la 2.3.0 attuale e valutazione della Neighbors API corrente come sostituto dei controlli di legalita' fatti a mano nei nostri pass deterministici.

CONTESTO:
- Siamo su Timefold 2.3.0 (community). TASK-323 (upgrade 2.1.0->2.3.0) fu fatto con A/B benchmark.
- Il wiring multi-threaded LS (TASK-543, single-writer dispatch) usa REFLECTION sugli internals di Timefold 2.3.0, dichiarato fragile/pinned nel javadoc: ogni upgrade va verificato contro quegli internals (il primo passo dell'upgrade e' mappare cosa si rompe).
- I nostri pass deterministici (SurplusHarvest, QuotaFillRepair, EmptyDayFinisher, BoxRepair...) reimplementano a mano i controlli di legalita' (H1/H3/H7/H17, indici live): una neighbors API nativa potrebbe darli a costo inferiore e senza deriva dal motore.
- TASK-310 valuto' la Neighborhoods API nell'era 1.x/2.0 (Done, esito storico): rivalutare con la forma API attuale, non riaprire quel verdetto acriticamente (memoria: reevaluate-past-rejections).
- La ricerca /pa:research exhaustive del 8/09 sui custom-moves ha indicato la direzione: pass deterministici come custom-moves/filter nella LS. La neighbors API e' il gancio naturale per quella migrazione.

PRIMO PASSO OBBLIGATORIO: verificare dalla fonte ufficiale (docs.timefold.ai + changelog GitHub) qual e' l'ultima versione e cosa offre la neighbors API nella forma attuale; NON fidarsi della memoria sulle versioni.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Versione target e changelog verificati dalla fonte ufficiale (non memoria)
- [ ] #2 Impatto sul wiring reflection MT-LS mappato: riscritto, oppure mantenuto con test di regressione verdi
- [ ] #3 Prova di concetto neighbors API sui pass deterministici (banco localizzato, non ciclo)
- [ ] #4 A/B benchmark solverConfig se la versione cambia comportamenti di ricerca; suite completa verde sul vincitore
- [ ] #5 Decisione documentata: adottare o rimandare, con evidenza
<!-- AC:END -->

2026-10-03 chiuso come SUPERATO: la meta' upgrade e' interamente fatta (2.7.0 finale); la meta' Neighborhoods vive in TASK-675 (ricerca, banco A/B prima di migrare) che e' il successore legittimo di questo task.
