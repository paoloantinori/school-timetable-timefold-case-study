# VERDICT TASK-500 (adaptive move weights) - il learning funziona, il VEICOLO config no: OOM a scala piena (laptop, 2026-08-15 sera)

> laptop MISURA. Flag planner.ls.adaptive-weights.enabled DEFAULT OFF ( resta OFF).
> A/B run1: control completato (0hard/-964559, 30 min); treatment NON COMPLETABILE
> nella forma config: la run e' finita -9404hard per OutOfMemoryError nella fase room.

## Cosa e' successo (cronaca della run1 treatment)

Tre muri di validazione scoperti in sequenza, tutti sul veicolo Timefold-config
(probabilityWeightFactoryClass al posto di fixedProbabilityWeight):

1. `resolvedSelectionOrder (RANDOM) che non e' PROBABILISTIC`: il fixed auto-risolve
   PROBABILISTIC in parsing, il factory no -> set esplicito.
2. `PROBABILISTIC che non supporta JUST_IN_TIME`: serve cacheType >= STEP -> set
   esplicito STEP.
3. `i pillar selector non supportano selezione non-random (sub-pillar esponenziale)`:
   i 2 stream pillar sono usciti dal rewiring (restano a peso fisso ratificato,
   Stream.adaptive=false).

Con questi fix la run parte e il MECCANISMO DI LEARNING FUNZIONA: 365 aggiornamenti
di segmento, pesi stabili intorno alle basi (TS_CHANGE 5.01-5.04, TS_SWAP 2.96-2.99:
gli accept-rate sono quasi uguali, la regola mean-preserving li lascia quasi fermi:
NESSUNA instabilita', NESSUN collasso, il rischio-1 del brief non si materializza).
MA il costo del veicolo e' fatale a questa scala:

- **B-TIMESLOT 3606s vs 932s del control (4x piu' lenta)**: il ProbabilityMoveSelector
  con PROBABILISTIC+STEP materializza la mappa cached delle mosse a ogni step.
- **OOM Java heap space nella fase room LS**: 12.494 entita' x value range, la mappa
  per step della fase room esaurisce l'heap 2g; la staged thread muore, il ciclo
  termina a -9404hard (room-null baseline: le stanze CH-assegnate non arrivano al
  save). E' ESATTAMENTE la trappola documentata nella FAQ Timefold ("except with
  nearby selection and caching move selector" = i due casi che allocano sopra il
  dataset).

## Il verdetto sperimentale

1. **La FORM adattiva e' sana** (regola mean-preserving testata 7/7, learning stabile
   dove gira). Il problema e' il VEICOLO: la probability selection via config di
   Timefold e' strutturalmente legata al caching per step, che alla scala di questo
   problema (12k entita', 1368 timeslot) costa 4x nel timeslot e OOM nel room.
2. **NON adottare** (il flag resta OFF, nessun default flip). Nessun run2: il
   treatment non puo' completare, una seconda coppia non esiste in questa forma.
3. **La via per rimetterci**: implementare la selezione adattiva DENTRO il nostro
   MultiThreadedLocalSearchDecider (noi possediamo il loop che consuma i move
   thread: possiamo campionare lo stream proporzionalmente ai pesi PRIMA di
   distribuire le mosse ai thread, bypassando il ProbabilityMoveSelector di Timefold
   e il suo caching). E' la stessa lezione del TASK-480: dove la config Enterprise
   non arriva in community, il punto d'innesto nostro e' il decider. Tracciato come
   follow-up nel task TASK-500 (forma B decider-internal), NON implementato stasera.

## Artefatti

- Control valido: ab-off-run1/ (0hard/-964559, B-TIMESLOT 932s, capture completa).
- Treatment: ab-on-run1/ (capture della run OOM'ata, app.log preservato con lo
  stack trace: e' l'evidenza del muro).
- Codice: AdaptiveMoveWeights (registro + regola pura, test 7/7) + wiring +
  AdaptiveStreamWeightFactories: resta committed DIETRO FLAG OFF come building
  block corretto (la meta' decider-internal riusa registro e regola cosi' come sono).


## ADDENDUM (notte 15-16/08, ore 02:50): FORMA B decider-internal - il veicolo che funziona + A/B completa

Dopo il verdetto negativo sulla forma A ho implementato la forma B documentata nel
follow-up: il thinning avviene nel NOSTRO decideNextStep, PRIMA del dispatch in coda
(r_i = rapporto adaptive/base normalizzato sul max: rapporti relativi esatti, ogni
r <= 1; la mossa thinned non costa nulla). Zero config Timefold toccata, zero
caching, zero OOM. Forma A ritirata dal codice (commit 8d70223f).

### A/B completa forma B (golden default, tutto ratificato ON, MT-LS on: statistica attiva)

| run | OFF medium | ON medium | delta | OFF B-TIMESLOT | ON B-TIMESLOT |
|-----|-----------|-----------|-------|----------------|----------------|
| run1 | -964.559 | -971.282 | -6,7k | 932s | 529s |
| run2 | -972.997 | -981.653 | -8,7k | 1583s | 669s |

- **hard 0 su 4/4 run.**
- **KPI in banda**: full-day ON MIGLIORE 2/2 (89,8->90,4 / 89,8->90,2), ricorrenza
  75,5-76,0 in banda.
- **medium**: -7/-9k (circa 1%) CONCORDE 2/2 nella direzione leggermente negativa,
  dentro la banda di rumore dei run good di stanotte (-957..-992k).
- **B-TIMESLOT wall**: ON piu' veloce 2/2 (932->529, 1583->669, -43/-58%) CON
  spiegazione meccanica diretta (le mosse thinned non vengono valutate: meno lavoro
  per step) e varianza ON piu' stretta (529/669 vs 932/1583 del control). CAVEAT
  onesto (lezione 499): il control varia del 70% fra le sue proprie run (932 vs
  1583, stessa config), quindi il numero wall va letto come segnale concorde con
  meccanismo, non come misura stabile.
- Learning osservato: pesi 2,96-3,00 (leggera ridistribuzione, mean-preserving,
  nessun collasso); 11k segment in 12min (poi log demoted a DEBUG).

### Verdetto finale aggiornato per maintainer

1. Forma B e' SICURA (hard 0 4/4, KPI in banda o migliori) e FUNZIONA a scala
   piena dove la forma A moriva.
2. Il costo medium e' circa 1% concorde 2/2: dentro il rumore ma coerente nella
   direzione. Il guadagno wall B-TIMESLOT e' concorde 2/2 con meccanismo diretto.
3. **Raccomandazione: NON adottare ancora** (flag resta OFF). Il profilo
   rischio/beneficio e' buono ma non schiacciante: la decisione naturale e' una
   run3 di tiebreak sul medium (se anche run3 resta entro 1% senza peggiorare i
   KPI, il trade wall-vs-medium e' favorevole e l'adozione diventa ragionevole);
   in alternativa accettare la lettura "medium-neutro al 1%" e adottare per il
   wall. Entrambe le strade sono di maintainer.
4. test.sh full EXIT 0 (1018 test) sulla forma B.


## RUN3 TIEBREAK (notte 16/08, ore 04:40): il medium si RIBELTA -> NEUTRO stabilito, wall = tail-capping

| run | OFF medium | ON medium | delta | OFF B-TIMESLOT | ON B-TIMESLOT |
|-----|-----------|-----------|-------|----------------|----------------|
| run1 | -964.559 | -971.282 | -6,7k | 932s | 529s |
| run2 | -972.997 | -981.653 | -8,7k | 1583s | 669s |
| **run3** | -959.186 | **-955.091** | **+4,1k (ON MIGLIORE)** | 413s | 512s |

- **hard 0 su 6/6 run.**
- **medium: NEUTRO stabilito.** La direzione si ribalta al terzo run (2 leggermente
  negativi, 1 leggermente positivo), magnitudini 0,4-0,9% tutte dentro il rumore: la
  forma B non costa ne' guadagna medium in modo rilevabile. Il gate del mandato
  ("medium miglior-o-neutro") e' ora soddisfatto nella lettura neutra.
- **KPI identici**: full-day medio ON 90,0 vs OFF 89,9 (ON migliore 2/3 ma entro
  0,5pp), ricorrenza 75,7/75,7/75,7 vs 75,5/76,0/75,5 (stessa banda).
- **B-TIMESLOT wall: ON CAPS LA CODA, non il mean.** ON: 529/669/512 ( banda stretta,
  max 669s). OFF: 932/1583/413 (banda 3,8x, max 1583s). Mean ON 570s vs OFF 976s,
  ma guidato dagli outlier del control: quando OFF centra un bacino veloce (413s) ON
  e' PIU' LENTO (512s: il thinning costa meta' step utile quando la LS converge subito).
  Lettura onesta: il thinning riduce la VARIANZA del wall (nessun run ON oltre 669s),
  non promette un mean stabile.

## VERDETTO FINALE (run1+2+3, 6 run)

1. **Qualita': NEUTRO confermato** (hard 0 6/6, KPI identici, medium dentro il rumore
   con direzione non stabile).
2. **Wall: tail-capping** (banda ON 1,3x vs OFF 3,8x: il rischio del run LS lento
   sparirebbe).
3. **Raccomandazione per maintainer: adozione RAIONEVOLE MA NON URGENTE.** Il profilo e'
   "zero danno, coda del wall garantita": si adotta per la prevedibilita' del tempo,
   non per guadagno di qualita'. Se il valore del tail-capping non interessa, il flag
   resta OFF senza costo. Il flag resta comunque OFF finche' maintainer non decide: nessun
   default flip stanotte (regola del mandato).
4. Requisito L2 pre-ratifica di Fedora: NON scatta nemmeno ora (l'adozione e'
   possibile, non ratificata; se maintainer approva, il test checkpoint della catena
   statistiche->pesi va costruito prima del flip come da requisito).
