---
id: TASK-405
title: >-
  Ancoraggio inizio-giornata al primo slot — golden 99,3% vs noi 81%: widening
  M18 + left-anchor pass deterministico
status: Done
assignee: []
created_date: '2026-07-23 19:15'
labels:
  - claude-planned
  - constraints
  - construction
  - spalmone-2027
dependencies:
  - TASK-401
priority: medium
ordinal: 229000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Osservazione di maintainer (2026-07-23): la differenza di "forma" più visibile rimasta rispetto al
golden è che la scuola riempie ogni giornata DAL PRIMO slot, noi no. La flessibilità era stata
tenuta per paura dell'infattibilità, ma il golden dimostra che una soluzione ancorata esiste.

## Misura (Fedora 2026-07-23, script Python su golden lessons.csv vs val398-run2)
- **GOLDEN: 99,3%** dei giorni-gruppo parte alle 8:00 (2.197/2.213; i 16 alle 9:00 sono
  quasi tutti il lunedì-ramp 2026-09-14, dove il CALENDARIO stesso apre allo slot 1: al netto
  del ramp l'ancoraggio è ~100%, e misurato contro il "primo slot APERTO" fa ~0 violazioni).
- **NOI (val398-run2): 81,0%** (1.762/2.176), code fino alle 12-13. Concentrazione: i gruppi
  con finestra 6h sono quasi perfetti (G01 2/172, G02 3/172), il ritardo sta nei gruppi
  con finestra 8 slot e attese 5-6h (G10 67/163, G07 60/167, G11 56/171, G08 55/158,
  G13 50/163, G09 34/157, G04 30/140): il run del giorno è contiguo (H17) ma GALLEGGIA
  nella finestra perché nulla lo ancora a sinistra.

## Perché oggi non ancoriamo: M18 esiste ma è castrata su due assi
`fiveHourGroupsMustStartFromFirstSlot` (SchoolScheduleConstraintProvider:395) è ESATTAMENTE
la regola giusta ("min slot del giorno > primo slot calendario-aperto"), ma:
1. **Gate ≤5 slot aperti** (task-247/350): i gruppi a finestra 8 — proprio quelli che
   galleggiano — sono esclusi per costruzione.
2. **Peso ONE_MEDIUM piatto per giorno**: 1 contro i 1000/giorno di M20/M22. Nel run reale
   M18 totale = −17. Stessa lezione della M15: a peso 1 contro la famiglia "logical hard
   medium" la regola è invisibile al solver.
La demozione storica H18→M18 (conflitto hard con H11 per G08) resta valida: NON tornare
hard; il golden legittima un medium FORTE, non un hard.

## Design proposto (stessa ricetta di H17/hole-filling: segnale di score + forma per costruzione)
1. **Widening M18**: rimuovere il gate ≤5 (o renderlo configurabile), penalità con GRADIENTE
   per-offset (`minSlotIndex - expectedFirstSlot`, non flat per giorno: dà alla LS una discesa)
   e peso ricalibrato nella famiglia M20/M22 (candidato: ofMedium(N)×offset, N da benchmark;
   attenzione all'equilibrio con M28/S1: ancorare tutti a sinistra comprime il mattino e
   aumenta la contesa docenti, il motivo storico della flessibilità). Golden-first: la regola
   widened misurata sul golden fa ~0 violazioni (il ramp ancora sul primo slot APERTO), quindi
   il guard può pinnarla a 0 e il peso può essere deciso dal benchmark, non dalla paura.
2. **Left-anchor pass deterministico in StudentDayCompaction**: lo stesso trucco move-max-only
   applicato PRIMA del run: se il run del giorno parte a slot k > primo-slot-aperto, muovere
   l'ULTIMA lezione del giorno (max slot) nello slot k-1 fa scivolare il run a sinistra di 1
   preservando contiguità (nessun buco creato), stesso giorno quindi cap H7/H11 invariati,
   stessi gate VRP+conflitti+pinned del pass esistente. Iterare finché k = primo slot aperto o
   la mossa è illegale. Gira dove gira già la compattazione (tail fase 1 + fase 4).
   Improve-or-noop rispetto alla M18 widened (ogni shift riduce l'offset di 1).
3. **BoxRepair è già allineato** (celle in ordine cronologico slot-crescente, adiacenza min-1
   H17-safe): nessun cambio.

## Sequenza e validazione
- DOPO il gate TASK-401 (in corso sul laptop) e idealmente insieme/subito dopo TASK-394: entrambi
  ricompongono il medium, un solo ciclo di ricalibrazione benchmark invece di due.
- Guard: misura golden della M18 widened pinnata (attesa ~0); KPI nuovo "day-start@first-open-slot
  rate" (script sibling di daily-fill-kpi.py) con soglia dal golden (≥~95%).
- A/B laptop (laptop-RUNBOOK): arm widened+left-anchor vs HEAD; gate = hard invariato (0/box 0),
  day-start rate ↑ verso ~95%+, full-day/blocchi/ricorrenza non regrediti, medium non
  peggiorato oltre il previsto (il peso nuovo va scelto lì).
<!-- SECTION:DESCRIPTION:END -->

## IMPLEMENTATO (Fedora 2026-07-23, autonomo post-gate-401) — resta SOLO l'A/B laptop

- **M18 widened**: gate rimosso, gradiente per-offset via `.map` a `DayStartOffset` (finestra
  risolta UNA volta), `ofMedium(250)`×offset, rinominata `dayShouldStartFromFirstOpenSlot` /
  "M18 - Day should start from first open slot". SORPRESA GOLDEN: la misura reale è −750/3
  match (3 giorni G08 non ancorati: 2026-12-04, 2027-04-27, 2027-04-29) = 99,86% ancorato,
  NON zero → pinnata come residuo calibrato nel guard, non zero-tolerance.
- **Left-anchor pass** in StudentDayCompaction (`anchorRunLeft`, move-max→lo−1, commitMove
  condiviso col gap-loop): costruzione real-seed 62 ancorati / 0 conflitti / ricorrenza 77,2%
  tenuta; sul checkpoint run-2 il pass da solo ancora **275 run** con 0 conflitti / 0 gap /
  hard 0 / box sempre 6→0. Skeptic Opus: claim hard-safety **CONFIRMED** (incl. il caso
  pinned-inside-run multi-iterazione, tracciato senza buchi).
- Drill-down `addM18Violations` riallineato (era H18/HARD col gate statico pre-350);
  `day-start-kpi.py` aggiunto (81,9% su val401-run2); i18n/docs/CLAUDE.md aggiornati.

## A/B laptop #1 (task405-results, 2026-07-24, HEAD 8098e6db) — VERDETTO: RETUNE, eseguito

Verificato da Fedora sui raw. Vittorie stabili nei 2 run: **day-start 81,9% → 96,2-96,7%**,
**hard 0** (meglio del −1 baseline), box 0 (12.301/12.301), knot 0, **M12 −24k → −7k**
(la 394 funziona; il residuo ~190-221 giorni-piano oltre è lateness genuina sotto capacità),
pass deterministico **132 giorni ancorati (50 ph1 + 82 ph4) a 0 conflitti, byte-identico**.
Prezzo del peso 250 (la firma "troppo forte" predetta nel brief, confermata in entrambi i run):
ricorrenza 35,8-36,7 → **34,3-34,6 (sotto la soglia ~35)**, full-day −1pt (77,7→76,8-77,1),
blocchi −28/−101, medium totale +85-165k. Composizione: il mover più grosso è **M20
−786k → −899k/−883k (+~105k, match 315→383, riproducibile)** — NON attribuibile con certezza
al peso M18; candidato alternativo: l'effetto del repair-tail bound della 394 (ora a fine
piano) sulla forma della costruzione. M15 varianza alta (28 vs 13, media ~20,5 in banda).

**RETUNE ESEGUITO (Fedora, autonomo): ofMedium(250) → ofMedium(50).** Il pass deterministico
porta il grosso del guadagno indipendentemente dal peso; 50 rende il float non-gratis per i
~70-80 residui (quasi tutti offset-1) senza competere alla scala M20/M22. Golden guard
ripinnato a −150/3; suite 700/700 EXIT=0.

**A/B laptop #2 (da eseguire): discriminante chiave.** Gate: ricorrenza ≥35 recuperata, full-day
≥~77,5, day-start tenuto ≥~95%, medium ≼ baseline 401 + il piccolo add di M18. Se il salto
M20 (+105k) SOPRAVVIVE al peso 50 → è un effetto 394-tail, non M18, e apre la sua diagnosi
dedicata.

## A/B laptop #2 (task405b-results, 2026-07-24, HEAD 21dd0250) — VERDETTO: **ADOTTATO al peso 50**

Verificato da Fedora sui raw. Il retune ha fatto esattamente quanto chiesto: **ricorrenza
recuperata 35,9/35,1** (sopra la soglia ~35, era 34,3-34,6 a peso 250), **day-start TENUTO a
96,0/96,9%** (il pass deterministico lo porta indipendentemente dal peso, come previsto),
hard **0/−1** (il −1 del run 2 è un H17, NON H0), **box 0/0** (12.301/12.301), knot 0,
medium totale leggermente meglio del peso 250 (≈ la riduzione di M18: −23,4k → −5,05k medio).

**DISCRIMINATORE CONCLUSIVO (headline):** il salto M20 (+105k vs baseline 401) **SOPRAVVIVE
IDENTICO al peso 50** (−904k/−885k, 378 match in entrambi i run): abbassare M18 di 5× ha
mosso M18 di ~5× ma M20 di nulla → la regressione medium residua è un effetto del
**repair-tail della TASK-394** (bound a fine piano che rimodella dove la costruzione piazza
i leftover), NON di M18. Diagnosi dedicata: TASK-406. Firma correlata da spiegare lì:
full-day 77,2 (−0,5pt vs baseline) e blocchi 2.023-2.035 (−60/−100).

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati — doppia risoluzione finestra in M18 eliminata (map/DayStartOffset); commitMove estratto; guard len nel KPI script; nota Set.of() in 394
- [x] #2 Skill /code-review high eseguita — skeptic Opus CONFIRMED sull'invariante hard del left-anchor; residui non bloccanti documentati (conflictsAdded può leggere negativo su stack pre-esistenti = miglioramento)
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore — test.sh EXIT=0 (700/700 incl. golden con pin −750/3), ui-typecheck 0, ui-build 0. **PENDING: A/B laptop** (gate: hard ≤ −1 con box 0, day-start rate ↑ ~95%+, KPI non regrediti, medium ricomposto come atteso; il peso 250 è il knob da confermare lì)
<!-- DOD:END -->
