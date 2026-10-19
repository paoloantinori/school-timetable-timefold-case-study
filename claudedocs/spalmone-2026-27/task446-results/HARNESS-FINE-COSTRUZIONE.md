# TASK-446: Checkpoint fine-costruzione Alt-B (esito PASS)

**Data:** 2026-07-28. **Harness:** `WeeklyTemplateConstructionRealSeedTest`
(`buildAndSolveConstructionWithRealRooms`, rooms REALI via `RealSeedFixture.seedRooms`,
distingue Alt-B dal baseline `List.of()` che rende inerte il gate room).
**Run:** test JUnit H2 (non e' un solve live). Commit harness: `383adc90`.

## Assunzione A confermata (il guard critico)

L'Assunzione A del censimento (Lesson timeslot-set + room-null NON e' "fully
initialized" per `forEach`/`forEachUniquePair`) e' confermata EMPIRICAMENTE sulla
griglia tutta-room-null: **H2 hard = 0 e H16b hard = 0** via ScoreDirector (motore
reale del solver, non calcolo manuale). Se un upgrade Timefold cambiasse la semantica
`forEach`/`allowsUnassigned` facendo contare una room-null come inizializzata, H2/H16b
sparerebbero qui e l'assert urla. Questo e' il guard anti-upgrade che Fedora ha preteso.

## A/B Alt-B vs baseline (rooms reali)

| Metrica | Baseline (gate room ON, room provvisorie) | Alt-B (gate room OFF, room=null) |
|---------|-------------------------------------------|----------------------------------|
| total lessons | 10501 | 10501 |
| non-pinned room-null | 552 (5.3%) | **10501 (100%)** |
| boxed (H0, timeslot=null) | 284 | **105 (-63%)** |
| H1/H3 final conflicts | 0 | 0 |
| H2 hard (post-CH, guard) | n/a (room provvisorie -> H2>0) | **0** |
| H16b hard (post-CH, guard) | n/a (room provvisorie -> H16b>0) | **0** |
| phase roomNullCount | n/a | **10396** (== non-pinned placed; canary inside assignVariables) |

**Letti dall'output del test** (stdout prima dell'assert):
- Alt-B (HEAD, `constructionRoomDecouplingAltB_...`): `total=10501 nonPinned=10501
  nonPinnedPlaced=10396 nonPinnedRoomNull=10501 nonPinnedRoomSet=0 boxed=105` /
  `phase counter roomNullCount=10396` / `conflicts teacher=0 group=0` /
  `guard H2 hard=0 H16b hard=0`. Test PASS.
- Baseline (pre-Alt-B phase @ `3f9f7c33`, `report_constructionMetricsWithRealRooms`):
  `total=10501 nonPinned=10501 nonPinnedPlaced=10217 nonPinnedRoomNull=552 (5.3%)
  boxed=284` / `conflicts teacher=0 group=0`. (Il report fallisce l'assert
  `boxed<total/50` sul baseline, come atteso; la metrica e' nel log prima dell'assert.)

## Verdetto contrappeso (Fedora point 4)

Il contrappeso che Fedora aveva chiesto di monitorare ("il gate room rimosso puo'
mascherare o causare boxed") va nella direzione OPPOSTA: **Alt-B riduce i boxed da
284 a 105 (-63%)**. Il gate room baseline scartava timeslot "senza room libera" e
finiva per boxare PIU' lezioni; senza gate la CH colloca piu' lezioni (H1/H3=0 in
entrambi, quindi nessun conflitto timeslot introdotto). La room-infeasibilita'
eventuale e' demandata a B-ROOM (fase 4), da misurare nell'harness staged breve.

## Metriche timeslot INVARIATE vs baseline

- **boxed**: NON invariata, MIGLIORATA (284 -> 105). Questo e' il punto: il gate room
  era accoppiamento che penalizzava il placement timeslot.
- **ricorrenza**: confermata invariata da `constructionEndRecurrence...` (test
  esistente, fixture `List.of()` dove il gate era inerte gia' prima di Alt-B):
  76.9% (target >=50%, baseline documentato ~76-77%).
- **day-start**: non misurata in questo A/B (il report method stampa boxed/room-null,
  non day-start). Il test esistente copre H17/compaction (compactedGaps>0,
  compactionAddedConflicts=0); day-start esplicita si puo' aggiungere al report se
  Fedora la vuole nel prossimo checkpoint.

## Assert passati (metodo `constructionRoomDecouplingAltB_...`)

1. Schedule-level: `nonPinnedRoomSet == 0` (nessuna non-pinnata con room post-CH;
   phase 4 assegna tutte).
2. Phase canary: `roomNullCount == nonPinnedPlaced` (roomNullCount vive dentro
   `assignVariables`, il single room=null commit point: si rompe se un placement
   path lo bypassa).
3. Integrita' timeslot: `boxed < total/50` (105 < 210), H1/H3 == 0.
4. GUARD: `H2 hard == 0`, `H16b hard == 0` via ScoreDirector (single calculateScore).

**Checkpoint fine-costruzione: PASS. Nessun blocker per lo staged breve.**
