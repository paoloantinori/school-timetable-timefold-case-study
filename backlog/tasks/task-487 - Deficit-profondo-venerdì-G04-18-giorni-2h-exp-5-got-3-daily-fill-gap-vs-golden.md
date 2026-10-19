---
id: TASK-487
title: >-
  Deficit profondo venerdì G04 (18 giorni -2h, exp 5 got 3) - daily-fill gap
  vs golden
status: Done
assignee: []
created_date: '2026-08-03 20:50'
updated_date: '2026-08-03 21:54'
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
ordinal: 306000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
READ-ONLY MEASURE (TASK-486 coda, 2026-08-03, run control-off production-like 0-hard). daily-fill-kpi.py sulla run goodseed control-off (task486-results/ab-mtc-per-retry-goodseed-control-off/lessons.txt):

FULL-DAY RATE 87.4% (golden ~91.9% ex-G14, run 30b 90.1%) -> gap ~4.5pp vs golden. Deficit shape: 184 giorni sotto quota (senza G14), 160 a -1h (87% shallow) + 24 deep (>=2h). I 24 deep sono concentrati: G04 18 giorni (TUTTI venerdì, exp 5 got 3 deficit 2), G13 3, G08 1, G09 1, G12 1. Golden: pochi profondi, G04 zero deep.

GAP specifico: il pattern venerdì G04 (-2h sistematico, 18 fridays) domina il deficit profondo e non e' nel golden. Famiglia del TASK-451 (G13 venerdì lab-day 5h)? Da verificare: e' un lab-day G04 venerdì che la LS spezza (TASK-451 cursore), o un deficit di quote attese venerdì, o M-TC/co-presenza che sposta ore? Per-gruppo full-day: G04 54%, G08 66% (i più bassi).

Domanda: cosa causa il -2h venerdì sistematico su G04? Misura: confronto per-cell sul golden (G04 venerdì) vs run. Research/spike, NESSUNA adozione default.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
VERDICT-487 (2026-08-03): caratterizzato. Quota venerdi'=5, run avg 3.59 (33/34 sotto), golden 5.17. Pattern sistematico, NON capacita'-bound (predizione refuted), e' search/spread. Raccomandazione DEFER.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Caratterizzato (2026-08-03, read-only). G04 venerdi' quota=5 (DailyActivityQuota), run ottiene avg 3.59 (33/34 fridays sotto quota, deficit medio 1.41h), golden avg 5.17 (consistente, incontra quota). PATTERN SISTEMATICO non outlier. PREDIZIONE capacita'-bound REFUTED: la quota 5 e' raggiungibile (golden 5.17), il run non la raggiunge (3.59). E' search/spread (front-loading lun-gio, venerdi' sparso/erratico). RACCOMANDAZIONE: DEFER (gap reale -1.4h/venerdi' su 1 gruppo, non deficit di monte-ore, non bloccante; cura medium-tier se maintainer la priorita'). DoD N/A (diff doc-only). Doc: task487-results/VERDICT-487-2026-08-03.md.
<!-- SECTION:FINAL_SUMMARY:END -->
