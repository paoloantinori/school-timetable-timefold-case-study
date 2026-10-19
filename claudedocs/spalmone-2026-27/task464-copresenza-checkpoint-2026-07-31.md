# TASK-464 checkpoint stanze: co-presenza ospite (zai, 2026-07-31 sera)

AC#3 checkpoint (RoomPhaseSnapshotTest export-mode). Flag OFF e' byte-identical (unit test
CoPresenceConstraintTest + 345 regression tests green). Qui si misura il flag ON al checkpoint
delle stanze: bersaglio "room-null 92 -> quanti diventano ospite-legale".

## VERDETTO (3 righe, dai raw)
- **Checkpoint SUPERATO: con flag ON i 92 lab room-null diventano 0 (92/92 ospite-legale),
  hard -92 -> 0.** La depth golden pubblicata (91 celle depth>=2 di 136 tallied, acconciatura)
  sblocca l'esenzione H2 sulle celle giuste; il cap H-COPRESENCE non over-fira (hard=0, non
  negativo: il solver co-loca esattamente fino alla depth golden, mai oltre).
- **Nessun tier degradato:** medium -825805 -> -825678 (leggermente migliore), soft stabile,
  H29 a 0 (room-null chiuso), 0 violazioni H2 (le coppie co-presenti sono esentate, non
  ignorate: la proxy "92 room-conflict cells" conta la co-locazione fisica, non lo score H2).
- **Off e' il baseline esatto del spike:** prima=92 dopo=92 (LS non puo' co-locare, H2 blocca),
  hard=-92. On riempie i 92 che off lascia room-null. Replica fedele del VERDICT spike (92
  riprodotto) e convergenza pulita in 60s di phase-5 LS sui 5 gruppi acconciatura.

## Raw (export.json.gz baseline-mwd1, slice 1/2/G10 + G12 + G14, budget 60s)

| metric | OFF (baseline) | ON (co-presenza) |
|---|---|---|
| LAB REAL room-null before | 92 | 92 |
| LAB REAL room-null after | 92 (LS filled 0) | **0 (LS filled 92)** |
| non-lab REAL room-null after | 0 | 0 |
| room-conflict cells (>1 lesson/cell, proxy) | 0 | 92 (co-locazione fisica, H2 esenta) |
| score hard | -92 | **0** |
| score medium | -825805 | -825678 |
| depth map pubblicata | n/a | 91 celle depth>=2 / 136 tallied |

Comando (export mode, slice ai 5 gruppi co-presenza):
```
PLANNER_TEST_ROOM_PHASE=1 \
PLANNER_TEST_ROOM_PHASE_EXPORT=claudedocs/spalmone-2026-27/t19-2026-07-31/baseline-mwd1/export.json.gz \
PLANNER_TEST_ROOM_PHASE_GROUPS="G02,G06,G10,G12,G14" \
PLANNER_TEST_ROOM_PHASE_BUDGET=60 \
PLANNER_TEST_CO_PRESENCE=1 \   # omesso per il baseline OFF
./scripts/core/test.sh -c RoomPhaseSnapshotTest
```

## Perche il checkpoint e' un lower bound sul full-solve
Il checkpoint fissa i timeslot del run baseline e lascia lavorare solo la phase-5 LS sulle
stanze: puo' solo AGGIUNGERE beneficio (il solver gia' collocato puo' ora co-locare gli ospiti).
Un full-solve lascerebbe tutte le fasi sfruttare la co-presenza, quindi il 92->0 qui e' il minimo
garantito. Il cap hard non ha distorto la ricerca (hard=0, nessuna cella oltre depth).

## Prossimo passo (full-solve A/B, scala laptop)
Il checkpoint regge -> full-solve A/B n>=2 (flag OFF vs ON) e' la conferma su traiettoria intera
(lezione E3: n=1 inganna sui convergence-failure). Misura: capture-run.sh su entrambi i bracci,
confronto score/hard/room-null/full-day/ricorrenza/cadence. Da fare in sessione laptop o notturna
(~50min/solve x2 + baseline). Nessuna adozione (flag resta OFF, decisione maintainer).

## Limiti / caveat (ereditati dal design)
- Golden senza room: la depth e' gruppi co-presenti per (tag, weekday, slot), inferita. Non
  distingue "5 in 1 lab" da "alcuni in aula normale" (domanda referente, nel doc TASK-466c).
- n=1 export (baseline-mwd1); l'anatomia e' stabile across run (spike E3 relaunch), le
  magnitudini esatte no. Il full-solve A/B chiude la varianza di traiettoria.
