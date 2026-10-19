---
id: TASK-488
title: >-
  Ricorrenza 3o anno bassa (G10 67% / G09 70% / G11 71% vs 1o anno
  74-83%) - gap ricorrenza
status: Done
assignee: []
created_date: '2026-08-03 20:50'
updated_date: '2026-08-03 21:56'
labels:
  - claude-planned
  - research
  - spalmone-2027
  - golden-first
  - read-only-gap
milestone: m-12
dependencies: []
priority: medium
type: spike
ordinal: 307000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
READ-ONLY MEASURE (TASK-486 coda, 2026-08-03, run control-off production-like 0-hard). resemblance-kpi.py sulla run goodseed control-off (task486-results/ab-mtc-per-retry-goodseed-control-off/lessons.txt):

ricorrenza TOTALE 74.4%, UNPINNED 74.3%. Per-gruppo UNPINNED, le coorti 3o anno + G12 hanno la ricorrenza PIU' BASSA: G10 67.3%, G12 68.2%, G09 70.2%, G11 71.2% vs 1o anno (G03 82.6%, G04 79.3%, G02 74.6%, G01 74.5%). Gap ~5-15pp tra 3o anno e 1o anno.

GAP: il calo di ricorrenza settimanale (template stability) cresce col anno di corso (3o < 2o < 1o). Ipotesi da verificare: i corsi del 3o anno hanno piu' stage/off-grid/quote frammentate che rompono il template settimanale, o la LS erode di piu' i gruppi con meno pin autoritativi (3o anno ha meno lab rotazione?). L'erosione CONSTRUCTION-vs-FINALE precisa (quanto perde il template dalla costruzione al finale) serve il dump ID per-fase (TASK-453, run live gated maintainer, -Dplanner.phase-transition-dump.enabled=true + lesson-identity-erosion.py). Su questa run ho solo il finale (74.4%).

Domanda: perche' il 3o anno perde piu' ricorrenza del 1o? Research/spike, NESSUNA adozione default.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Caratterizzato (2026-08-03, read-only). Ricorrenza 3o anno 67-71% vs 1o anno 74-83% (gap ~5-15pp). Cadenza: 3o anno gap_media 8-11 (burst 2.4-2.7) vs 1o 3-6 (1.7-1.9), sistematico (38-65% pairs burst>2x). PREDIZIONE STAGE-SHIFT CONFERMATA: 3o anno ha ~300h stage / ~38 giorni (G09 38/300, G10 38/300, G11 37/295, G12 43/295) che il 1o anno NON ha (0 stage). Le settimane di stage interrompono la griglia -> cadenza rotta + ricorrenza bassa. COMPORTAMENTO ATTESO (stage by design), non difetto. RACCOMANDAZIONE: IGNORE (non curare, lo stage c'e' per curriculum); follow-up KPI stage-aware resemblance (escluda settimane stage dal denominatore) per misurare template stability onesta. Doc: task488-results/VERDICT-488-2026-08-03.md.
<!-- SECTION:FINAL_SUMMARY:END -->
