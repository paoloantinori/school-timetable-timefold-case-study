---
id: TASK-516
title: >-
  Round 4: budget-reservation nel deriver (riservare headroom H7 per domanda
  lab-bound non enumerata prima di pinnare) + A/B run6
status: Done
assignee: []
created_date: '2026-08-17 07:01'
updated_date: '2026-08-17 19:46'
labels:
  - claude-planned
  - solver
  - pre-assign
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Conseguenza diretta del probe TASK-510 (772fa07c, blocco STRUTTURALE): il deriver/enumerator pina meccanica/Controllo numerico/Cad riempiendo tutto il budget H7 dei giorni-L del docente, zero headroom per le altre materie quota-bound dello stesso docente (la RA di T15 su G04: lun/ven spare-aula inutilizzabili perche' la RA e' lab-bound, mercoledi' H7-saturo). Raccomandazione del probe: budget-reservation lato deriver. Alternative gia' scartate con evidenza: guard aggregati (refutati run4/run5), slegare RA dal bound-set (contro la pratica golden), accettare -42 (blocca m-13), piu' tentativi (bacino deterministico).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Budget-reservation nel deriver (o dove il pin plan viene costruito): prima di pinnare i blocchi lab di (docente, gruppo) sui giorni-L, riservare per ogni giorno-L headroom = quotaBoundLabDemandNonEnumerated(teacher,group)/labDays ore di cap H7 del docente (la RA di T15 su G04 deve trovare cella legale)
- [x] #2 Flag planner.pre-assign.budget-reservation.enabled, opt-in, DEFAULT OFF, forma standard, banner in refreshSolverStatics
- [x] #3 Unit test del caso esatto del probe: la reservation deve lasciare almeno una cella H7-legale per la RA di T15 su almeno un mercoledi' (il caso 20 boxed deve diventare 0 nel pre-assign plan)
- [ ] #4 A/B run6: control = chain ON senza reservation (= run4), treatment = chain + reservation. Gate: hard 0 nel treatment (i 20 boxed collocati), KPI in banda (full-day, ricorrenza, buchi), wall
- [ ] #5 Se il gate passa: bozza di ratifica per maintainer (testo CLAUDE.md forma opt-out), NON applicata
- [x] #6 Nessun default flip
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-17 13:07
---
ESITO (laptop 17/08 pomeriggio, ROUND 6 nel VERDICT ab-chain): AC#4 GATE FALLITO, AC#5 non scatta (nessuna bozza). Implementata come da mandato (AC#1/2/3: regola ceil(domanda/giorni-L), gate H7 netto della reservation, flag default OFF, banner, BudgetReservationTest 5/5, suite 1063/0; commits 1c646a1c + review eb73654e + fix crash TreeMap 3e08d4e6).
---

created: 2026-08-17 13:07
---
Run6: control -42 (replica esatta run4/run5, terza conferma del bacino deterministico, 20 boxed RA). Treatment -70hard PEGGIORE: 22 boxed (RA 13 + Lavorazioni meccaniche 9, nuove), H29 -24 room-null, KPI in banda ma wall 182min vs 129.
---

created: 2026-08-17 13:07
---
Diagnosi: la reservation taglia il cap GIORNALIERO ma il greedy pinna BLOCCHI interi: cap 6-1=5 rifiuta i blocchi 6h OVUNQUE, il pair intero overflowa al solver (mercoledi T15: 174h pinned -> 0 pinned). Inoltre numeratore grezzo: il corso meccaniche 110h di G08 conteggiato come domanda non-enumerata perche' quella fase pinnava altri soggetti del pair -> reservation 5h/giorno su G08 (cap effettivo 1).
---

created: 2026-08-17 13:07
---
Cura candidata round 7 (NON implementata): CLIP del blocco al cap nettato (shape-hygiene TASK-502-style) invece del rifiuto intero + numeratore limitato alle materie lab-bound che NESSUNA fase pinna. Prototipo resta DEFAULT OFF (AC#6 rispettata).
---

created: 2026-08-17 13:07
---
Nota protocollo: la prima cattura del treatment era uno stato di volo (analysis -2526, H29 -2080, ciclo ancora in B-ROOM tra due stagedStage nulli del poll); la cattura valida e' ab-on-run6-final (polling live fino a quiescenza reale).
---

created: 2026-08-17 19:46
---
ROUND 7 (17/08 sera, riforma golden-guided 58fabfbd + fix clip 1c0a497e, suite 1065/0): GATE FALLITO ma bersaglio curato. Control -42 (20 boxed RA, ennesima replica). Treatment -53: RA G04 0 boxed (29h collocate sui mercoledi', convivenza golden riprodotta), MA 25 ore di code clip di Lavorazioni meccaniche nuove boxed + H7 -3 (33 mercoledi' a 6h, 1 a 7h). Il box si e' spostato dalla RA alle code del clip: la reservation presidia il ritmo solo dove il blocco non entra, non su tutti i giorni-L (mercoledi' rimasti a 174h pinned).
---

created: 2026-08-17 19:46
---
Dato positivo strutturale: il meccanismo golden (2h riservate derivate dal carico medio golden 4h, banner con provenienza) FUNZIONA sul bersaglio; manca il vincolo di budget TOTALE pair sui giorni-L. Candidato round 8 (NON implementato, per maintainer): gate aggregato per-pair per-weekday applicato PRIMA del pinning (somma pin + domanda non-enumerata <= cap x giorni-L), ritmo golden come target.
---

created: 2026-08-17 19:46
---
Prototipo resta DEFAULT OFF, nessuna bozza di ratifica. Artefatti: ab-off-run7, ab-on-run7(-final), ab-on-run7-invalid1 (crash IndexOOB createTempSession, fix 1c0a497e).
---
<!-- COMMENTS:END -->
