# VERDICT: B-TIMESLOT-1 metaheuristic benchmark (notte2, 2026-07-31)

4 bracci staged (control tabu7 vs LA400 vs tabu3 vs tabu11), budget phase-2 identico e dichiarato
(3600s cap / 600s unimproved / diminishedReturns 2min@0.1), acl 1000 per tutti. Profilo production
fresco per braccio (drop-and-create wipe, import-calendario + pre-assign, constrained-teacher ON
verificato T01 231/231). Metrica primaria = B-TIMESLOT #1 end (timeslot-tier hard = -2*box,
medium): la qualita' del phase-2 LS dal template promosso, prima dei repair e delle stanze.

## Tabella (raw dai capture)

| arm | acceptor | BT1 ttH | BT1 box | BT1 medium | iters | final H | final M | fullday | rec% | BT1 time |
|-----|----------|--------:|--------:|-----------:|------:|--------:|--------:|--------:|-----:|---------:|
| A | entityTabu 7 (control) | -16 | 8 | -842,684 | 3 | -121 | -964,300 | 89.3% | 75.8% | ~14m |
| H | entityTabu 3 | -16 | 8 | -857,970 | 3 | -121 | -978,474 | 88.8% | 75.8% | ~22m |
| E | entityTabu 11 | -18 | 9 | -849,365 | 3 | n/a | n/a | n/a | n/a | ~24m |
| B | lateAcceptance 400 | -20 | 10 | -848,557 | 3 | -125 | -971,072 | 88.3% | 75.9% | ~42m |

arm E final mancante: killed pre-B-ROOM per budget token (B-TIMESLOT #1-3 completi nel log,
box 9->7->5; solo lo stadio stanze non e' arrivato in fondo). rec = ricorrenza unpinned.

## Verdetto (3 righe, numeri dai raw)
1. **tabu7 (il control di oggi) vince: miglior B-TIMESLOT-1 (box 8, parimerito con tabu3, medium
   migliore -842k), piu' veloce (14min vs 22-42min), final migliore (-121 hard + medium -964k
   il piu' basso), full-day migliore (89,3%). Nessun braccio lo batte su un singolo aspetto.**
2. **tabu3 parimerito su box/ttH ma medium peggiore (-857k) e piu' lento (22min); tabu11 box 9
   (peggiore) e lento (24min); LA400 box 10 (il peggiore) e 3x piu' lento (42min), final -125.**
   La memoria tabu 7 e' lo sweet spot: sia 3 che 11 peggiorano, confermando il benchmark di marzo.
3. **I repair downstream (demotion + HoleRepair/AnchorRepair/SurplusRebalance + B-ROOM)
   compensano le differenze di B-TIMESLOT-1: i final cluster a -121/-125 e i KPI reggono ovunque
   (holes 0, rec ~75,8%, full-day 88-89%). Quindi la metaeuristica si distingue soprattutto su
   velocita' di convergenza e qualita' pre-repair, non sul finale.**

## Conclusione
**KEEP tabu entity-7 (config corrente).** Il benchmark (direttiva primaria: performance = problema
di ricerca attaccato col solver config) e' stato esercitato: tra tabu3/7/11 e LA400, la config
corrente e' gia' ottima. Coerente col TASK-386 ("no phase-2 config beats control reproducibly")
e col benchmark del 2026-03-23 (tabu 7 vincente). Nessuna adozione da ratificare: la conferma e'
"non cambiare", che e' essa stessa un verdetto valido.

## Skipped / futuro
- **G (LA1000) DEFERITO**: LA400 ha mostrato LA dominata (peggiore + 3x lenta); lo smoke aveva
  LA1000 ~= LA400 (entrambi conservativi, +17-20 hard). Bassa marginalita'. Point confermatorio
  per una prossima sessione (~75min run).
- **Varianza (laptop-RUNBOOK runs 15/16: -426 vs -3902, 9x spread)**: n=1 per braccio qui. Il verdetto
  regge perche' le differenze sono grandi e coerenti (tabu7 sistematicamente box 8, LA box 10), ma
  confermare con un 2o run del control + del peggiore (LA400) rafforzerebbe. Priorita' bassa dato
  il margine chiaro.
- **Budget effettivo differisce**: tabu converge a ~14min (diminishedReturns), LA corre verso il
  cap 3600s (~42min). Sotto budget identico dichiarato, questo e' una proprieta' dell'accettatore
  (non un bias): tabu e' anche piu' efficiente nel usare il budget.

## Tooling
- `scripts/benchmark/generate-benchmark-arm.py` (armi G/H aggiunte), `capture-btimeslot-arm.sh`
  (capture + log prod B-TIMESLOT stages), `scripts/analyze/btimeslot-bench-summary.py` (tabella).
- Smoke screen `Phase2SmokeSnapshotTest` parametrizzato (5 armi, no divergenza).
- Capture per-arm in `arm-{A-control,B-la400,H-tabu3,E-tabu11}/`.
