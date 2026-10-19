---
id: TASK-523
title: >-
  Rehearsal senza golden della chain (rotazioni deriver congelate come input,
  approccio a checkpoint L1/L2/L3)
status: Done
assignee: []
created_date: '2026-08-18 14:48'
updated_date: '2026-08-18 23:22'
labels:
  - claude-planned
  - solver
  - chain
  - m-13
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decisione maintainer 18/08 sera: niente round 9-bis (forced-seed non-production: il multi-start reale e attempt 1 deterministico + retry reseedsati, gia misurato da run8 = -3). La prova che conta per la produzione: la chain SENZA golden, col deriver congelato come input (stessa via di sopravvivenza di H-COPRESENCE). Approccio a checkpoint per feedback veloce: L1 pre-assign only (~1 min, il cuore), L2 confronto piani via SnapshotFixture (secondi), L3 full solve solo se L1 verde (cap 240). Mandato spedito 18/08 sera (/tmp/zai-goldenfree.txt sul laptop).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 L1 (~1 min): rotazioni deriver congelate come input statico, golden ASSENTE nel profilo di test, deriver OFF, enumerator + budget-reservation ON, SOLO pre-assign. Piano verificato: H20/H7 zero sui pin (analysis post-pre-assign), parity check asse A, banner fallback con clamp che tiene
- [x] #2 L2 (secondi): confronto piano golden-free vs piano golden-live run8 via SnapshotFixture: stesse coppie? distanza reservation fallback-vs-derived per coppia chiave (T15/G04). Il numero decide se congelare anche il ritmo nel workbook
- [x] #3 L3 (~2h, solo se L1 verde): full solve senza golden, cap driver 240 (fix censuramento), polling a quiescenza, confronto hard/KPI vs run8
- [x] #4 Sezione GOLDEN-FREE nel VERDICT. Bozza workbook_freezing rotazioni proposta a maintainer, NON applicata
- [x] #5 Chiude la domanda m-13 (genericity senza golden) per la chain
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-18 21:38
---
CHIUSURA (18/08 notte, mandato finale): L1/L2/L3 completati nella giornata (GOLDEN-FREE nel VERDICT; coppia non-T15 identiche, distanza = delta reservation 1h-vs-2h; L3 -27hard). Segue il mandato di chiusura con decisione maintainer: chain NON fruttabile, control campione, meccanica flag OFF come capacita' workbook-futura, EPILOGO scritto nel VERDICT.
---

created: 2026-08-18 21:38
---
PROVA m-13 (filone 1 chiusura): control SENZA golden con la SOLA mappa co-presenza congelata (seed/golden-copresence-depth.csv, 136 celle generata dal loader reale, identity gate GoldenCoPresenceFreezeTest): RISPOSTA NO. Best -25hard su 5 tentativi (122 min vs 23 min dello 0hard col golden). H0 ZERO (0 boxed), H29 -25 = Laboratorio Termoidraulico (G11 14 + G03 11, saturazione stanza unica termoidraulica). La mappa congelata FUNZIONA: acconciatura room-null ZERO (l'esenzione-ospite sopravvive al congelamento; era la regressione -15 della rehearsal).
---

created: 2026-08-18 21:38
---
Cosa manca oltre alla mappa (documentato, non iterato): NON slot0 (qualita'), NON constrained-teacher (source=input default): manca l'indirizzo di qualita' che il golden dava alla traiettoria (full-day 71,0% vs 87,5%) e il residuo room-tier e' variabilita' di ricerca. Item workbook per il futuro.
---

created: 2026-08-18 21:38
---
Loader golden-free: tre loader degradano graceful (commit giornata); CoPresenceDepth ora legge la mappa congelata quando il golden manca (warn FROZEN con numero celle). Golden ripristinato nel worktree e nel JAR, identity gate verde sul tree finale.
---

created: 2026-08-18 23:22
---
M13-FINAL (notte 18/19-08): 0-HARD SENZA GOLDEN, deterministico al tentativo 1, ~15 min. Profilo m13-gf2 col golden FISICAMENTE assente dal JAR e i DUE congelamenti caricati (co-presenza 136 celle + ancore slot-0 67 righe/14 gruppi, banner FROZEN verificati). KPI indistinguibili dal control col golden: full-day 87,8% (vs 87,5), ricorrenza unpinned 74,2% (pari), buchi 0, medium -1.028.908. m-13 CHIUSO POSITIVAMENTE: il path production sopravvive al golden con due soli congelamenti.
---

created: 2026-08-18 23:22
---
Censimento completo dei lettori golden runtime (filone 0): loadAllGoldenProfiles INERTE (source=input default), slot0-anchors CONGELATO, co-presenza CONGELATA, mono-room-cadence/BudgetReservation/LabRotationDeriver INERTI (flag OFF), GoldenScheduleImportService endpoint esplicito. Nessun lettore non censito vivo sul control.
---

created: 2026-08-18 23:22
---
Congelamenti workbook definitivi: (1) profondita' co-presenza per cella (tag, weekday, slot); (2) ancora slot-0 per (gruppo, weekday). Generati dal loader reale con identity gate (GoldenSlot0FreezeTest + GoldenCoPresenceFreezeTest, entrambi verdi sul tree finale col golden ripristinato). Con il golden presente i file restano dormienti (fallback solo se il CSV manca): il path di produzione di oggi e' byte-identico a ieri.
---

created: 2026-08-18 23:22
---
Commit: dbc4d7c6 (congelamento slot0) + finale con M13-FINAL nel VERDICT e capture m13-final-run2.
---
<!-- COMMENTS:END -->
