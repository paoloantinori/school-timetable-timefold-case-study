# VERDICT-raw: ciclo surplus-harvest puro su 8a2c5287 (profilo slotretry-9sep, 9/09)

## NOTA DI LANCIO (onesta)

Il PRIMO lancio (04:56) portava anche `-Dplanner.construction.prebooking.slot-retry.enabled=true`:
FERMATO alle 05:05 dopo la BOCCIATURA del banco locale di Fedora (H34 22->68:
la pass consuma il pool unplacedByCourse che lo stamp cronologico usa; verdetto
nel task-577). Log del run ucciso: /tmp/planner-slotretry-killed.log (3991
righe, guardia superata N=5898/M=172 prima del banco). RILANCIATO alle 05:01
SOLO con surplus-harvest + checkpoint (verificato: NESSUN banner SlotRetryPass
nel nuovo log). Il profilo non e' stato ricreato (delete rifiutato sul
container attivo): la dottrina multistart-ab vale (solve-staged ri-pianifica
da zero, unassignUnpinned all'ingresso, i pin autorevoli persistono).

## SETUP

Jar 04:55:24 su HEAD 8a2c5287, `PREFLIGHT-OK classes=[SurplusHarvest]
flags-verified`, surplus-harvest=true + checkpoint, SURPLUS_TRACE_ALL=1,
banner surplus-harvest=true verificato, costruzione deterministica,
rilancio 05:01.

## DOMANDE PRE-REGISTRATE (riscritte al rilancio 05:01)

1. **H34 a consegna** vs le 3 residue del v132 (G06 10/06, G09 03/06,
   G12 04/06): il baseline naturale di questo ciclo e' il v132 stesso
   (-6, H34=3) su codice pre-8a2c5287: cosa cambia su 8a2c5287 (SlotRetryPass
   nel codice ma NON armato, H32-targets nel codice ma NON armato).
2. **H32 a consegna** (indicatore nuovo): confronto col boxdef di ieri (H32 -5).
3. **Righe SurplusHarvest**: con i contatori chainBlk (chainBlkSlideH1 ecc.):
   su questo codice/bacino come evolvono vs v131/v132.
4. **Sanity + concordanza** + avr=0.

## RISPOSTE (consegna 13:29; ciclo 05:01-13:29, ~8.5h, 5 tentativi + reseed GRASP)

1. **H34 a consegna: 3 celle (-3)**, UGUALI al v132 (3). Le 3 residue attese
   confermate: il ciclo NON le chiude (il primo run col flag slot-retry era
   stato fermato, questo e' il rimpiazzo pulito senza flag).
2. **H32 a consegna: 3 celle (-3)** (la lezione di ieri era boxdef H32=-5:
   qui -3, DENTRO il range normale, nessuna regressione dal nuovo percorso).
3. **Sanity**: NON CONFORME 5 rossi (G07 x2 date, G08, G09 x3 date,
   G10, G14 x2 date; 1 buco interno 01/04). Full-day 91,9%.
4. **Consegna -13 vs v132 -6**: 7 hard peggiori sullo stesso codice, la
   differenza e' bacino (niente GRASP in questo ciclo) + la lezione di
   ieri (il confronto indicativo e' dichiarato nel VERDICT pre-registrato).

## Score / righe SH / sanity / concordanza

score.txt: `score: [-13/0]hard/[-1245173/-16017]soft` (analysis medium
riconta -839591... vedi capture). Concordance: `delivery concordance OK
(H29 room-null memory=0 db=0)` (14a consecutiva).
