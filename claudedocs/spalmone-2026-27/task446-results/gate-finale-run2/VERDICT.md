# GATE FINALE - RUN DI CONFERMA n=2 (stessa config run1), VERDICT (2026-07-29 notte)

**RISPOSTA IN UNA RIGA: i numeri del gate finale sono RIPRODUCIBILI, non una traiettoria
fortunata.** Stesso codice di HEAD (1c1a6472 = run1 + un solo .md), stessi flag (constrained-teacher
ON su riga java -jar), stesso profilo fresco, stesso import + pre-assign + solve-staged. Run2
atterra su -4hard/-1208323medium/27728soft contro il -5hard/-1209153medium/24938soft di run1:
hard 1 MIGLIORE, medium entro 830 (0,07%), tutte le KPI dentro ±0,5pp, H28 e room-hard a 0 in
entrambi, pin autoritativi 3090 intatti in entrambi. La LS e' stocastica e i due run fanno
trade-off interni leggermente diversi (run2 guadagna M15/M37, perde un po' di room-null), ma
convergono allo stesso piano di qualita'. n=2 conferma l'adozione e' numericamente solida; la
decisione resta a maintainer.

## Setup (identico a run1, verificato)

- HEAD `1c1a6472` (run1 era `9f855bf2`; diff = solo `campaign-status-2026-07-29-risveglio.md`,
  NESSUN cambio di codice). JAR M37+H28 riutilizzato senza rebuild.
- Avvio PROD via `java -jar` con `PLANNER_JAVA_OPTS=-Dplanner.pre-assign.constrained-teacher.enabled=true`,
  `-Xmx2g`. Banner a solve-time verificato: `constrained-teacher=true` (la `prod-start-and-wait.sh`
  mette il flag sulla riga java -jar, non MAVEN_OPTS).
- Profilo `production` FRESCO (volume cancellato, DataGenerator reseeds). Seed deterministico
  verificato: 1368 timeslot, 27 teacher, 14 gruppi, 2395 dailyactivityquota, 18 room, 0 pinnate.
- import-calendario: **lessonsPinned=866 (MODA), stagePinned=1993, stageOffGridHours=327** (identici
  a run1). lesson_total post-import **12494** (12821 fresche - 327 stub Stage off-grid convertiti).
- pre-assign (Phase 0): **"Phase 0 done: 2 teachers, 231 pinned, 72 skipped"** = il "231 pinned"
  del mandato, identico a run1. Phase 0 gira solo se il flag e' ON -> proof funzionale flag attivo.
- Stato pre-solve: **3090 authoritative pins** (2859 import + 231 Phase 0), identico a run1.

## Score finale affiancato

| | run1 (gate-finale) | run2 (questo) | delta |
|---|---|---|---|
| score | -5hard/-1209153medium/24938soft | **-4hard/-1208323medium/27728soft** | hard -1, med -830, soft -2790 |
| hard | -5 | -4 | **run2 1 meglio** |
| medium | -1209153 | -1208323 | run2 830 meglio (0,07%) |
| soft | 24938 | 27728 | run2 2790 peggio |

## Hard e decomposizione

| componente | run1 | run2 |
|---|---|---|
| H0 (boxed genuine-unplaceable) | -5 / 5 match | **-4 / 4 match** |
| room-hard H2 / H-M2 | 0 genuine | 0 genuine |
| H28 (no teacher return, Fase 2 guard) | 0 match | 0 match |
| altri hard (H1/H3/H5/H7/H17/H20...) | 0 | 0 |

Hard e' SOLO H0 boxed in entrambi (4-5 lezioni genuinamente non piazzabili), niente conflitti,
niente stanza, H28 cristallizzato a 0. Nessuna degenerazione: l'abort criterion "hard congelato su
componente non-room" non si e' mai attivato (hard e' sceso -188 -> -4 attraverso le fasi).

## CRITERIO ROOM (la metrica chiave del mandato) - room-null real-unpinned

Query (definizione run1): `timeslot_id IS NOT NULL AND room_id IS NULL AND pinned=false AND
teacher.pseudo=false`. coincide con il match-count di M36 (0 pseudo room-null in entrambi).

| | run1 | run2 | delta |
|---|---|---|---|
| room-null real-unpinned (totale) | **254** | **270** | +16 |
| -- di cui LAB (capacity-constrained) | 251 | 262 | +11 |
| -- di cui NON-LAB | 3 | 8 | +5 |
| M36 score / match | -254000 / 254 | -270000 / 270 | +16000 / +16 |

Il residuo e' ancora interamente dominato dal LAB (97% in run1, 97% in run2): il componente
non-lab, che M37 aveva risolto da 291 -> 3 in run1, resta essenzialmente chiuso anche in run2
(3 e 8, entrambi trascurabili vs i 291 pre-M37). Il +16 di run2 e' tutto capacita' lab al picco
(dominio Fase 3 / TASK-449), e la LS lo ha scambiato con guadagni altrove (vedi M15/M37 sotto).
"Room-null real non-pinnato tende a 0": 254 e 270 sono lo stesso ordine di grandezza, il trend
M37 (non-lab risolto) e' confermato. Il criterio (==0) resta aperto in entrambi, per lo stesso
motivo (lab capacity-constrained).

## KPI standard affiancate (tutte VERDI e >= soglia in entrambi)

| metrica | run1 | run2 | soglia / golden |
|---|---|---|---|
| full-day | 80,9% (1938/2395) | 80,8% (1934/2395) | >=78 / golden 85,4 |
| day-start | 99,2% (2191/2208) | 99,3% (2190/2206) | >=99 |
| ricorrenza unpinned | 74,2% (6976/9399) | 74,6% (7013/9400) | >=72 |
| buchi interni | 0 | 0 | 0 |
| cadence burst>2x | 36,1% (2,04) | 35,7% | ~golden 41,6 |
| H28 no-return | 0 | 0 | 0 |
| room-hard H2/H-M2 | 0 genuine | 0 genuine | 0 |

Tutte le KPI entro ±0,5pp tra i due run. La riproducibilita' e' su tutto il profilo, non solo
sullo score.

## day-start-anatomy (scarto >= 3 vs golden)

| | run1 | run2 |
|---|---|---|
| unica cella con scarto >= 3 | G11 venerdi run 16 / golden 0 | G11 venerdi **run 15** / golden 0 |
| giorno-gruppo totali in ritardo | 17 / 1955 | ~16 / 1955 |

Stessa identica anomalia (G11 venerdi' sistematici, Fase 4 defer come shell-game-prone). run2 ha
15 ritardi vs i 16 di run1 (-1). day-start globale 99,2% -> 99,3%. Il pattern residuo e' lo stesso
e confermato non-fortuna: e' strutturale (1 gruppo, 1 weekday), non una traiettoria.

## Medium: trade-off interni diversi, totale identico

I due run convergono allo stesso medium totale ma con scambi diversi (la LS e' stocastica):

| constraint | run1 | run2 | nota |
|---|---|---|---|
| M22 calendar compactness | -561000 / 13 | -561000 / 13 | identico |
| M20 daily quota | -202000 / 132 | -199000 / 132 | run2 -3000 |
| M32 template deviation | -115931 / 744 | -116174 / 746 | ~identico |
| M35 spread | -20450 / 74 | -20300 / 77 | ~identico |
| **M15 same-day consecutive** | **-16800 / 9** | **-4800 / 4** | **run2 molto meglio** |
| M37 ex-H16b (cambio aula) | -12400 / 124 | -10200 / 102 | run2 meglio |
| M36 room-null | -254000 / 254 | -270000 / 270 | run2 +16000 (peggio) |
| M33 surplus | -7100 / 67 | -7200 / 70 | ~identico |
| M12 overflow | -6676 / 130 | -6510 / 128 | run2 -166 |
| M18 day-start | -850 / 17 | -850 / 16 | ~identico |
| M3 capacity | -641 / 641 | -682 / 682 | run2 +41 |

Interpretazione del trade-off: run2 ha preferito costruire blocchi same-day same-subject piu'
compatti (M15 -16800 -> -4800, 9 -> 4 split) e ha cambiato aula meno spesso (M37 124 -> 102),
al prezzo di 16 ore lab in piu' senza stanza (M36 254 -> 270). Il saldo netto e' +830 medium a
favore di run2 e -1 hard (4 boxed vs 5). Nessuno dei due run domina l'altro: sono due punti
equivalenti dello stesso fronte di Pareto. E' esattamente cioe' che ci si aspetta dalla
ripetizione stocastica di una LS convergente.

## Traiettoria per fase (hard)

| fase | run1 endScore hard | run2 endScore hard |
|---|---|---|
| 1. CH - Lesson Placement | -188 | -188 (identico) |
| 2. LS - Compactness (B-TS-1) | -31 | -37 |
| 3. Compaction | -29 | -34 |
| 2. LS - Compactness (B-TS-2) | -31 | -34 |
| 3. Compaction | -29 | -32 |
| 2. LS - Compactness (B-TS-3) | -27 | -30 |
| 4. CH - Room Assignment | -5 | **-4** |
| 5. LS - Room Refinement (B-ROOM) | -5 | -4 |

La CH iniziale e' identica (-188, deterministica). Le fasi LS divergono di ~3-6 hard in corso
d'opera (varianza stocastica attesa) e riconvergono in B-ROOM a -4/-5. niente di patologico.

## Ciclo e durata

| | run1 | run2 |
|---|---|---|
| start solve | ~00:18 (app 00:13) | 02:57:54 |
| fine (validation solve-completed) | ~00:57 | 03:43:19 |
| durata solve | ~40min | ~45min |
| authoritative pins fine | 3090 (preservati, newlyViolated=0, baseline=true) | 3090 (preservati, newlyViolated=0, baseline=true) |

run2 ~5min piu' lungo: B-TIMESLOT-1 e' corsa ~20min vs ~16min (trovava micro-guadagni di medium
che resettavano il timer unimproved). Entro il normale. Nessun crash, nessuna anomalia di
punteggio, NESSUN criterio di abort attivato.

## Criteri di abort (tutti rispettati, nessun fermo)

- hard congelato su componente non-room: MAI (sceso -188 -> -4).
- H0 in crescita: MAI (4-5 boxed stabili, in calo).
- room-null real-docente non scende in B-ROOM: FALSO (medium crollato -10,3M -> -1,2M in B-ROOM,
  M36 residuo 270, stesso ordine di run1).
- crash / anomalie punteggio: NESSUNO.

## Conclusione e confine di mandato (FERMATI qui)

**Riproducibilita' confermata (n=2).** Il pacchetto 435 + 446 + M37 + H28 produce lo stesso piano
di qualita' su due traiettorie indipendenti: hard 4-5 (solo boxed), medium ~-1,209M (±0,07%),
KPI tutte verdi e within-noise, H28/room-hard a 0, room-null dominato dal lab (non-lab risolto),
day-start G11 venerdi' come unica anomalia strutturale (non fortuita). I trade-off interni
(M15 vs M36) cambiano tra i run ma il totale no.

Il laptop ha MISURATO, NON adotta. Nessun default change, nessuna chiusura task, nessuna ratifica,
file protetti intoccabili. La decisione resta a maintainer (vedi `campaign-status-2026-07-29-risveglio.md`
colonna 3): adozione del pacchetto, patch tag stanze (DataGenerator.getRoomTags), TASK-450 venerdi',
TASK-447 pesi hard. Residui aperti invariati rispetto a run1: (1) lab room-null 251-262 -> Fase 3 /
TASK-449; (2) day-start G11 venerdi -> Fase 4 LS-scope; (3) TASK-447.

## Cattura

`./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/task446-results/gate-finale-run2`
artefatti: score.txt, analysis.json, phases.json, export.json.gz, lessons.txt, KPI (fill/daystart/
daystart-anatomy/resemblance/cadence/holes/moda-knot/sharpness), confronto-gate-finale-run2.html,
day-start-anatomy.html.
