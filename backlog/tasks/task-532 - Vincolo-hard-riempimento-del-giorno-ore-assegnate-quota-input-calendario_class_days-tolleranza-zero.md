---
id: TASK-532
title: >-
  Vincolo hard riempimento del giorno: ore assegnate >= quota input
  (calendario_class_days), tolleranza zero
status: Done
assignee: []
created_date: '2026-08-19 14:31'
updated_date: '2026-08-19 19:01'
labels:
  - claude-planned
  - hard-constraint
  - golden-first
  - input-first
dependencies: []
references:
  - 'scripts/analyze (analisi statica quota-vs-golden, sessione 19/08)'
  - >-
    claudedocs/spalmone-2026-27/task529-results/ (run H31 di riferimento con i 7
    giorni corti)
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal rilievo visivo di maintainer (G12 ven 02/10 solo 3 ore). Analisi statica Fedora 19/08: golden vs calendari input (calendario_class_days + calendario_fc, aula+lab, stage escluso, sabati/Fasullo esclusi) mostra convergenza ESATTA del golden alla quota giornaliera per 10/11 gruppi (unica eccezione reale: G09 01/10 quota 6 vs golden 5, 1h). Le ~20h di "contraddizione input" di G12 sono tutte in righe 14-24/06/2027 OLTRE la fine anno (10/06): fuori griglia, senza timeslot, non contano. Run attuale: 7 giorni <=3h di cui 6 venerdi' (02/10, 09/10, 23/04 con 2h, 30/04, 07/05, 14/05, 21/05) = violazione dell'input dichiarato, non solo del golden.

SPEC (mandata a zai 19/08, in coda dopo TASK-530): vincolo HARD (non quasi-hard: tolleranza ZERO) per ogni (gruppo, data con Timeslot esistente): ore assegnate non-Stage >= DailyActivityQuota aula+lab del giorno. SCOPE: sole date con timeslot; ESCLUDI G04 e G08 (pool-cap TASK-497 forma-b e' il target giusto, non doppiare); ESCLUDI G14; G09 tolleranza 1h dichiarata. Sostituisce la forma debole "target = conto golden sparpagliato" del primo mandato.

Prevalidazione Test A gia' fatta (analisi statica sopra); replicare come test. Test C: i run 0hard attuali violano (7 giorni), quindi il vincolo morde: attendersi medium shift.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Golden-oracle test: il golden importato viola il vincolo 0 volte (1h tollerata su G09 01/10)
- [ ] #2 Run di verifica: giorni <4h di G12 = 7 -> 0; distribuzione 4h/5h di G12 verso 12/120 dell'input
- [ ] #3 0hard end-to-end mantenuto (multi-start)
- [ ] #4 Distribuzione G08 e G04 venerdi' invariata o migliorata (target TASK-497 non doppiato)
- [ ] #5 DoD completa: /simplify, /code-review high, test.sh EXIT 0
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 16:35
---
IMPLEMENTATO (42241f87, suite 1082/0, golden-oracle test 3/3): H32 quota-fill HARD in SchoolScheduleConstraintProvider: per (gruppo, data con lezioni), ore non-Stage >= quota aula+lab dichiarata (DailyActivityQuota), tolleranza ZERO (G09 1h), escluse G04 / G08 / G14 (spec). SCOPE SUBTLETY documentata nel codice: il groupBy vede solo date CON lezioni, quindi un giorno TOTLMENTE vuoto resta a M20.2 (medium): H32 pesa il partial underfill (la classe dei 7 giorni <=3h). Flag opt-out planner.constraints.quota-fill.enabled (null->ON). Oracle test: scope replicato (date toccate dal golden + cut orizzonte 10/06 per le righe fantasma 14-24/06) = golden PULITO.
---

created: 2026-08-19 16:35
---
RUN h32-run1 (31681571): primo run col vincolo, 5 tentativi completati. ESITO: **-200hard NON 0hard** (H32 -57 su 57 giorni under-quota + H17 -143). AC#2 parziale: G12 giorni <=3h 7 -> 4, e i 3h residui sono (18/09, 02/10, 30/04, +1): il vincolo MORDE e la costruzione/LS non chiude tutto. Under-quota G12 residui: 23 giorni (margine 1h quasi ovunque, qualche 7-vs-8 su giovedi' aprile).
---

created: 2026-08-19 16:35
---
DIAGNOSI del residuo (per il prossimo passo): i gruppi in deficit di POOL (G09 -28, G10 -16, G11 -24, G12 -20, G13 -18, G07 -5: pool < quota-sum anche DOPO il taglio delle righe fantasma) non possono soddisfare H32 ovunque: la matematica lo vieta. Il golden li soddisfa perche' quelle divergenze pondono sulle righe fantasma fuori griglia... VERIFICARE: se il pool in-griglia < quota in-griglia anche per questi gruppi, H32 cosi' com'e' INFEASIBLE strutturalmente e serve o la tolleranza o l'esclusione. Dato urgente per Fedora: somma quota IN-GRIGLIA (<=10/06) vs pool per G09/G10/G11/G12/G13/G07.
---

created: 2026-08-19 16:35
---
AC#1 (golden-oracle) VERDE, AC#2 parziale (7->4), AC#3 (0hard) ROSSO al primo run, AC#4/5 da completare quando la forma si stabilizza.
---

created: 2026-08-19 19:01
---
CURA A MONTE IMPLEMENTATA (afb24485, suite 1082/0): quota-aware fill tail nello stamp. Dopo le celle del template per (gruppo, data), se il giorno e' sotto la quota dichiarata (stessa funzione del vincolo: expectedHoursCapFor), il tail piazza qualsiasi lezione ancora-unplaced del gruppo (VRP-legale, canPlaceLesson/exceedsDailyCap gated, sotto cap giorno) negli slot aperti del giorno fino a quota. In-payload nel test real-seed: 2.734 piazzamenti del tail su 10.183. Test aggiornato: skipConflict ora informativo (cross-group teacher contention legittima oltre le celle template), l'invariante reale (0 conflitti ACCETTATI) resta asserta dallo score.
---

created: 2026-08-19 19:01
---
RUN h32b-run1 (6c7f2670, 5 tentativi, ~2h): **-104hard** (H32 -5 su 5 giorni tutti G10 + H17 -99). La firma attesa si materializza: G12 under-quota 23 -> 0, giorni <4h GLFAlmente = 0, unica famiglia H32 residua = G10 (5 giorni: da verificare se H1-teacher-availability blocca, candidate per la lista a Fedora). H17 -99 e' il danno collaterale residuo (era -143): la pressione scende ma non a zero.
---

created: 2026-08-19 19:01
---
CONFRONTO h32-run1 -> h32b-run1: hard -200 -> -104, H32 -57 -> -5, H17 -143 -> -99, under-quota scope 63 -> 5, full-day 83,2 -> 84,9. Fill-parity page da generare sul finale quando la forma converge (prossimo passo: classificare le 5 celle G10 + decidere se H17 -99 cerca cura o accettazione).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
