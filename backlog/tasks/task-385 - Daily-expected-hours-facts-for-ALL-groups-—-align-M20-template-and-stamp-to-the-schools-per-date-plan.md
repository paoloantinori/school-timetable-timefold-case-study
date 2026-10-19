---
id: TASK-385
title: >-
  Daily expected-hours facts for ALL groups — align M20, template and stamp to
  the school's per-date plan
status: Done
assignee: []
created_date: '2026-07-21 04:49'
updated_date: '2026-07-21 11:13'
labels:
  - claude-planned
dependencies: []
ordinal: 209000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
maintainer 2026-07-21: dalla prima giornata la soluzione non crea giornate piene secondo il numero ATTESO per gruppo/giorno - aspetto molto importante, da usare come indice di similarita' col golden. MISURE (run 14): full-day rate 55,5% vs golden 85,4% (KPI permanente: scripts/analyze/daily-fill-kpi.py; atteso = calendario_class_days.csv, tutti i 14 gruppi); deficit 2.175h su 595 giorni (252 giorni con buchi 1-2h = arrotondamento per difetto delle quote settimanali del template; 237 giorni quasi vuoti 5-8h = corsi esauriti/code repair tail); 471 giorni SOPRA l'atteso. ROOT CAUSE STRUTTURALE: la nozione di giorno pieno di M20/template e' il calendario SETTIMANALE (GroupCalendarRule), che coincide con l'atteso PER-DATA della scuola solo sul 10,9% dei giorni (su ~2.000 giorni il calendario dichiara 2-3 slot piu' del foglio) - ottimizziamo verso il bersaglio sbagliato. FIX (converge con TASK-384): (1) estendere il fact per-(gruppo,data) a TUTTI i 14 gruppi da calendario_class_days.csv - expectedHours per tutti + modality (il fact stage-day di TASK-384 cade gratis); ATTENZIONE al ripple: DailyActivityQuota oggi definisce 'quota group' per H23/H24/M30/M31 e il VRP aula/lab - o un fact/colonna separata (expectedHours) o semantica quota estesa con cautela (H23/H24 restino FC-only); (2) M20 target := expectedHours(gruppo,data) dove presente; (3) WeeklyTemplateBuilder day shapes := modal expectedHours per weekday (non finestra calendario) + allocazione largest-remainder per riempire la settimana template ESATTAMENTE (oggi il rounding-down lascia buchi sistematici da 1-2h); (4) stamp fill-to-expected; (5) KPI full-day in experiment-log per ogni run. Nota G14: il foglio gli da' giorni che il suo calendario Gio-Ven non ha (Q16) - gestire la precedenza. Target: full-day rate verso l'85% golden SENZA perdere ricorrenza 41,5% (misurare entrambi).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fact expectedHours per tutti i 14 gruppi (FCQuota flag preserva la semantica L1-bis; census consumer verificato), M20/template/stamp allineati all'atteso per-data della scuola, KPI full-day permanente (scripts/analyze/daily-fill-kpi.py + mirror Java). RUN 15: full-day 55,5%->74,1% (golden 85,4%), medium -3,46M->-1,52M (2,3x), blocchi tenuti (1.861), ricorrenza 38,8% (-2,7pt). Hard -426 (3 H15+6 H17, ricerca incompiuta con CH -57k): caso (b) della direttiva modello-vs-ricerca -> modello confermato, benchmark metaeuristiche prioritario. Scoperte per la scuola: G14 foglio 990h vs domanda 115h (9x); tensione due-fogli FC (fc.csv autoritativo vs class-days per-data). DoD completa.
<!-- SECTION:FINAL_SUMMARY:END -->
