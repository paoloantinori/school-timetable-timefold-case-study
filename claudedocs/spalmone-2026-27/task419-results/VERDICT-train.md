# VERDICT TASK-419 train (treno solver: M20/M20.2 progressivi + HoleRepair + A4 ranking) - 2026-07-26 night

Descrittivo, nessun giudizio di adozione (decide Fedora sull'evidenza). HEAD bffcf412.
Runbook: caffeinate, heap 2g, profilo `dev` fresco a ogni run, UN solve alla volta, app lanciata
via `java -jar` diretto con `-Dplanner.phase-transition-dump.enabled=true` sulla command line
(nessun fork mvn, il -D arriva alla JVM app; 5 dump di fase raccolti per run, stasera capture diceva
"none" per un path mismatch ora risolto). Profilo fresco a ogni run, prep identico
(import-calendario, pre-assign no-op verificato). Attesa staged-aware robusta (loop attorno ad
api.sh wait con retry su early-exit).

## Sintesi

- **HoleRepair VERIFICATO, chiude i buchi H17 a 0**: run1 H17 hole slots 2 to 0, run2 4 to 0,
  conflicts added 0, 0 min-slide DE-ANCHORS in entrambi. buchi interni (kpi-holes, metrica nuova
  di maintainer) **0 giorni / 0 slot** in entrambi i run (golden 1, smoke2 pre-treno ne aveva 9). E il
  payoff sul hard: **hard 0 (run2) e -3 (run1)**, dove stasera (pre-treno) era -15/-16/-22 con
  H17 a 9 match non chiudibili da nessuna mossa del pipeline.
- **hard e' H0-driven o nullo, mai H17**: run2 box=0 -> hard 0 perfetto (H0=0, H17=0, H16b=0);
  run1 box=3 accettato dopo 2 giri di demozione -> hard -3 = solo H0=3. **H16b=0** invariato dal
  fix, **H17=0** in entrambi.
- **Ricorrenza TIENE**: unpinned **73,9% (run1) / 73,4% (run2)** (target >=70; stasera 73,8-74,1;
  val408 52,8/53,6). La metrica chiave resta nella banda attesa.
- **COSTO medium reale**: medium **-2,28M (run1) / -2,38M (run2)** vs stasera -1,86/-1,89M, cioe'
  +420/+490k. Driver principale e' M20 progressivo (marginal 1000->4500 sui giorni profondi):
  M20 medium ~1,45M in entrambi i run (470 match), M20.2 -71/-98k. Si paga di piu' sui giorni
  profondi che restano profondi.
- **full-day NON recupera, anzi -1,3/-1,8pp**: full-day **75,5% (run1) / 75,0% (run2)** vs stasera
  76,8-77,6, vs val408 79,9-80,0, vs golden 85,4. La leva M20 progressivo NON ha spinto il full-day
  verso 79-80 in NESSUN run; e' leggermente sotto stasera. Conferma su due run (non varianza).
- **Meccanica staged**: promoted 7132 (identico a stasera), authoritative 2651 to 2651 intatto,
  M32 residuo stabile (-123,5k/787 e -126,1k/803 vs -124k/793 stasera). min-slide DE-ANCHORS 0,
  move-budget WARN mai raggiunto.

## Top flags (deviazioni misurate, nessun allarme soglia)

1. **full-day sotto stasera in ENTRAMBI i run** (75,0-75,5 vs 76,8-77,6). La leva M20 progressivo,
   che doveva spingere verso 79-80, non ha mosso la metrica nella direzione attesa; l'ha leggermente
   pegiorata. E contemporaneamente il medium e' salito di ~450k. Questo e' il tradeoff centrale per
   la decisione: HoleRepair vince sul hard (0/-3 vs -15/-22) ma il M20 progressivo costa medium
   senza guadagno sul full-day.
2. **Demozioni: varianza run-to-run** (run1: 2 giri, 4 pin; run2: 0). Non e' sistematico del treno:
   run2 e' pulito come stasera (box=0, 0 demozioni). run1 e' un bacino sfortunato (box=3 -> demote
   cap -> H0=3 accettato). La stabilita' hard 0/-3 e' comunque molto piu' stretta di stasera (-15/-22).
3. **hard e' H0-driven (run1) o nullo (run2), non H17-driven** come stasera. Cambia la natura del
   residuo hard, non solo l'ampiezza.

Nessun allarme soglia: ricorrenza >=70 PASS (73,4-73,9), burst <55 PASS (45,9 entrambi),
M32 non esplode (787/803 vs 793), M15 non esplode (11 e 32, golden 98), buchi 0 PASS.

## Tabella (treno x2 vs stasera staged vs val408 vs golden)

| Metrica | train-run1 | train-run2 | stasera staged (smoke2/r1/r2) | val408 (r1/r2) | golden |
|---|---|---|---|---|---|
| hard | -3 | **0** | -15/-16/-22 | -1/-1 | 0 |
| H16b | 0 | 0 | 0/0/0 | 0/0 | 0 |
| H17 | 0 | 0 | 9/9/9 (non chiudibile) | n/a | 0 |
| medium | -2.281.494 | -2.375.862 | -1.862/-1.870/-1.886 M | -1.905/-1.910 M | n/a |
| soft | +9.883 | -6.438 | +1.450/-1.074/-4.703 | -43,9k/-47,1k | n/a |
| **ricorrenza unpinned** | **73,9%** | **73,4%** | 74,1/73,8/73,8 | 52,8/53,6 | n/a |
| ricorrenza totale | 75,4% | 75,1% | 75,7/75,4/75,4 | 58,9/59,6 | n/a |
| **full-day** | **75,5%** | **75,0%** | 77,6/76,8/77,1 | 79,9/80,0 | 85,4 |
| day-start | 95,9% | 95,7% | 96,9/96,9/96,7 | 93,7/93,7 | ~99,3-100 |
| sharpness pooled | 79,8% | 81,1% | 83,6/83,4/83,8 | 79,7/77,5 | n/a |
| burst>2x | 45,9% | 45,9% | 43,0/43,4/44,3 | 81,6/79,1 | 41,6 |
| gap-weeks | 1826 | 1716 | 1772/1721/1728 | 2405/2331 | n/a |
| M32 residuo (medium/match) | -123,5k/787 | -126,1k/803 | -125k/798, -124k/793, -124k/792 | n/a | n/a |
| **M15 matches** | **11** | **32** | (non in tabella stasera) | n/a | ~98 |
| M20 (medium/match) | -1,45M/470 | -1,45M/470 | n/a | n/a | n/a |
| **buchi interni (giorni/slot)** | **0/0** | **0/0** | (kpi-holes vuoto pre-treno; smoke2 9) | n/a | 1/1 |
| promoted | 7132 | 7132 | 7132/7132/7132 | n/a | n/a |
| **demozioni (giri/pin)** | **2 / 4** | **0 / 0** | 0/0, 0/0, 0/0 | n/a | n/a |
| B-TIMESLOT iter / wall | 3 iter (3713s + B2 + 831s) | 1 iter / 2058s | 1 iter, 3336/3726/2262s | n/a | n/a |
| B-ROOM wall | 266s | 286s | 352/302/272s | n/a | n/a |

## Righe nuove (richiesta esplicita)

- **buchi interni (hole-topology-kpi, metrica nuova di maintainer)**: 0 giorni / 0 slot in entrambi i run.
  Golden 1/1 (l'eccezione pinned H17 documentata). smoke2 pre-treno ne aveva 9. HoleRepair li chiude.
- **HoleRepair import per livello** (log `staged solve: TASK-419 hole repair`):
  - run1: H17 hole slots 2 to 0, closed 2 by level = **0 free import, 2 on-template import, 0 min-slide**, conflicts added 0.
  - run2: H17 hole slots 4 to 0, closed 4 by level = **2 free import, 2 on-template import, 0 min-slide**, conflicts added 0.
  - **min-slide DE-ANCHORS**: 0 in entrambi (atteso 0). move-budget (60) WARN mai raggiunto.
  - **M20 day-fill cost (before -> after)** (log `TASK-419 hole repair: M20 day-fill cost`):
    run1 **1.452.500 -> 1.452.500** (neutro: gli on-template import li preleva da giorni che potevano
    permetterlo, costo M20 netto 0); run2 **1.448.500 -> 1.454.500** (+6.000: gli import costano
    6k medium sui giorni sorgente, piccolo e within il bound MAX=500 del singolo). Il trade e'
    leggere come previsto: un punto hard H17 vale piu' di qualsiasi medium.
- **M15 matches**: 11 (run1), 32 (run2). Varianza run-to-run, ma entrambi ben sotto il golden (~98
  split same-day same-subject). Non esplode.

## Lettura (descrittiva)

HoleRepair e' la vittoria netta del treno: porta H17 a 0 (dove stasera restava -9 hard, 9 buchi
non chiudibili da nessuna mossa esistente) e buchi interni a 0, e il hard finale e' 0/-3 (vs
-15/-22). La meccanica staged e' pulita (promoted 7132, authoritative 2651 intatto, M32 stabile,
0 DE-ANCHORS). Il costo e' medium: ~+450k, guidato dal M20 progressivo sui giorni profondi, e il
full-day NON e' migliorato (75,0-75,5 vs 76,8-77,6 stasera, lontano da 79-80 e da golden 85,4).
Cioe' il M20 progressivo fa pagare di piu' gli stessi giorni profondi senza riempirli: la leva
preme ma la metrica non si muove (i giorni profondi sono in larga parte strutturali, caveats
G14/FC dove lo sheet chiede 990h ma il corso ne ha 115, o finestre stage). Tempi: run2 ~40min
(1 iter), run1 ~112min (3 iter per la demozione).

## Note tecniche

- **Stale gate verify-baseline (registrato, non toccato)**: `verify-baseline.sh seed` asserisce
  timeslot 1376, il seed produce **1368** (post-TASK-408, 171 giorni x 8, fix typo chiusura
  2026-01-22 -> 2027-01-22; CLAUDE.md conferma 1368). `verify-baseline.sh import` asserisce stage
  1800 / off-grid 280, l'import produce **1785 / 295** (post-TASK-408). Entrambi gli actual
  combaciano con CLAUDE.md; solo lo script e' stale (datato 22/07, pre-TASK-408). Tutte le altre
  quantita' seed (teacher 27, course 273, lesson 12581, dailyactivityquota 2393, room 18) matchano
  il gate. Non ho modificato lo script (file tracciato, non e' la mia call).
- **-Dplanner.phase-transition-dump.enabled=true verificato**: 5 dump di fase raccolti per run
  (+5 boxed) in `target/phase-transitions/`, con resemblance per-fase. La capture di stasera
  diceva "none" perche' cercava nel path default mentre l'app scriveva altrove; il launch
  java -jar plain usa il path default e capture-run li raccoglie.
- **Attesa robusta**: il `fetch || exit 1` di api.sh wait (il race che ha invalidato un run
  stasera) e' gestito da un loop che, su uscita non-zero, ri-checka `stagedStage` e re-aspetta.
  In run1 ha gestito il timeout del budget 5400s durante B-TIMESLOT-2 (re-armed, completato).
- **kpi-holes.txt non vuoto in entrambi i run** (metrica nuova richiesta da maintainer): run1/run2
  29 righe ciascuno, per-gruppo con classificazione OK-pieno / fine-anticipata / inizio-tardivo /
  entrambi / BUCO-INTERNO.
- **M32 knobs a default** (numerator=100000 min=20 max=500): corretto, l'utente non ha chiesto di
  toccarli (nessun cambio di config).
- Profilo dev fresco a ogni run, deterministicita' seed verificata (run1 e run2 identici:
  1368/27/273/12581). import-calendario RC=200, 0 modaFailures, stagePinned 1785, off-grid 295,
  skip 5. pre-assign no-op (candidatesTried=0, pinned resta 2651).
