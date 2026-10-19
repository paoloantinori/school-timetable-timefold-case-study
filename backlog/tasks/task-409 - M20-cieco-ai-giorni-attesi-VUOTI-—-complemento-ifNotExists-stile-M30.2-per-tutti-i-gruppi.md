---
id: TASK-409
title: >-
  M20 cieco ai giorni attesi VUOTI — complemento ifNotExists (stile M30.2) per
  tutti i gruppi
status: Done
assignee: []
created_date: '2026-07-24 11:40'
labels:
  - claude-planned
  - constraints
  - spalmone-2027
dependencies: []
priority: high
ordinal: 233000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scoperto dallo strumento visivo golden-vs-run (TASK-408, maintainer+Fedora 2026-07-24): i nostri
run lasciano giorni attesi COMPLETAMENTE vuoti (G04/G08/G07, concentrati da marzo in
poi) che il golden non ha quasi mai. Causa nel codice: lo stream di M20
(`horizontallyCompact`, SchoolScheduleConstraintProvider ~1356) è Lesson-sourced con filtro
`totalHours > 0 && totalHours < quota` → un giorno svuotato del tutto NON è mai visto (uno
stream sulle Lesson non può produrre un giorno senza lezioni). **Incentivo perverso:** giorno
a 1h ≈ 5.000 medium, giorno a 0h = 0 → il solver è premiato a EVACUARE i giorni quasi vuoti.
Stesso buco già chiuso per i giorni-lab FC da M30.2 (`ifNotExists` guidato dai fatti
DailyActivityQuota) ma mai esteso a M20.

WHAT: complemento "M20.2 - Empty expected day" — stream sourced su DailyActivityQuota
(expected aula+lab > 0, base stage-esclusa coerente con dailyQuota/TASK-385), `ifNotExists`
Lesson della stessa (gruppo, data) con timeslot non-null; penalità ≥ quota×1000 (un giorno
vuoto deve costare ALMENO quanto un giorno a 1h: quota×1000 elimina l'incentivo a evacuare;
la continuità del gradiente suggerisce esattamente quota×1000). ATTENZIONE pinned-semantics:
M20 esenta le lezioni pinned; per il giorno vuoto non ci sono lezioni da esentare — decidere
day-level rispetto ai pin del golden import (un giorno del golden atteso-ma-vuoto pinnato
altrove NON deve far male al guard): misurare sul golden e pinnare (dal confronto visivo il
golden ha pochissimi giorni attesi vuoti → atteso un residuo piccolo, forse 0).
G14: esclusa o gestita (la sua attesa-990 vs domanda-115 renderebbe OGNI giorno "vuoto
colpevole" — stessa esenzione M20 già esistente per G14 da riusare).

Validazione: ConstraintVerifier (giorno vuoto penalizzato, giorno 1h > 0h in costo relativo
corretto), golden guard pinnato, A/B laptop (atteso: i rossi scuri spariscono dalle strisce,
full-day ↑, M20-evacuation cessata). Rigenerare confronto-golden-vs-run.html post-fix come
verifica visiva.
<!-- SECTION:DESCRIPTION:END -->

## IMPLEMENTATO (Fedora 2026-07-24) — resta l'A/B laptop cumulativo

M20.2 "Empty expected day": forEach(DailyActivityQuota) aula+lab>0, G14 esente,
ifNotExists timeslottedLessons (i PINNED contano: un giorno coperto da pin non è vuoto),
peso ofMedium(1000)×dailyQuota = il limite esatto del gradiente M20 a zero ore → svuotare
un giorno non è mai più economico di lasciarci 1 ora (skeptic Opus: economia CONFERMATA,
transizione 1h→0h = +1000 netto). Golden: solo 2 giorni attesi vuoti su 2.213 (0,09%),
pinnati −8000/2 (residuo calibrato); il fixture golden ora seeda anche
generateExpectedDailyHours (mancava dal TASK-385). 4 ConstraintVerifier test.

INCLUSO il follow-up 406 (la leva "known gap" del verdetto laptop): BoxRepair ora preferisce
celle su giorni con deficit expected (fallback cronologico), test unitario dedicato,
checkpoint: box 6→0 invariato, medium −47k. Caveat greedy/H0 annotato nel codice.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 /simplify eseguita — helper isG14 (triplicazione), import DailyActivityQuota, caveat perf annotato; test-gap (d) colmato con BoxRepairDeficitPreferenceTest
- [x] #2 /code-review con skeptic Opus — entrambi i claim CONFERMATI (stream mechanics, economia anti-evacuazione, hard-safety per-placement); caveat H0-greedy documentato
- [x] #3 test.sh EXIT=0 (708/708 incl. golden −8000/2). PENDING: A/B laptop cumulativo (Q22+406+409+410) + riverifica visiva
<!-- DOD:END -->
