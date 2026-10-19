# VERDICT TASK-419 train-2 (TASK-422 SurplusRebalance + TASK-423 M33 anti-surplus) - 2026-07-27 night

Descrittivo, nessun giudizio di adozione (decide Fedora sull'evidenza). HEAD **a71f77b5** (git pull verificato: match esatto `a71f77b5...`, fast-forward pulito). Measure-only: nessuna adozione/revert/config.

Runbook identico al treno (HEAD bffcf412): caffeinate, heap 2g, profilo `dev` fresco a ogni run, UN solve alla volta, app lanciata via `java -jar` diretto con `-Dplanner.phase-transition-dump.enabled=true` sulla command line (mai MAVEN_OPTS, nessun fork mvn, il -D arriva alla JVM app). Prep identico al treno e verificato deterministico su entrambi i run (1368 timeslot / 27 teacher / 273 course / 12286 lesson post-import; import-calendario RC=0, stagePinned 1785, off-grid 295, skip 5; pre-assign NO-OP candidatesTried=0; authoritative pin 2651 = 866 MODA + 1785 stage). 5 phase-transition dump raccolti per run (+5 boxed): il -D ha lavorato.

## Sintesi

- **hard 0 in ENTRAMBI i run** (run1 0, run2 0). H16b=0, H17=0, H0=0 per additivita' (hard totale 0 = ogni vincolo hard a 0). **buchi interni 0 giorni / 0 slot** in entrambi (golden 1). HoleRepair chiude H17 a 0 in entrambi (2 hole slots -> 0, conflicts 0).
- **giorni-sopra IN CALO verso il golden** (la metrica nuova del treno-2): kpi-fill `sopra` **111 (run1) / 118 (run2)** vs treno 126/136, vs golden 70. SurplusRebalance surplus days (quota-0 esclusi, il conteggio interno del pass): run1 79 -> 69, run2 99 -> 77. M33 (il vincolo nuovo) punisce i residuali: 69 / 77 match.
- **full-day SU** in entrambi i run: **77,0% (run1) / 76,6% (run2)** vs treno 75,5/75,0, vs val408 79,9/80,0, vs golden 85,4. Il rebalance converte surplus in deficit coperto: la metrica si muove nella direzione attesa (+1,5pp sul treno), dove il treno (M20 progressivo da solo) non l'aveva mossa.
- **Ricorrenza TIENE**: unpinned **73,5% (run1) / 73,7% (run2)** (target >=70; treno 73,4-73,9; val408 52,8/53,6).
- **COSTO medium comparabile al treno, non peggiore**: medium **-2,301M (run1) / -2,330M (run2)** vs treno -2,281/-2,376M. run1 train2 +20k vs train-run1; run2 train2 -45k vs train-run2. Il nuovo termine M33 aggiunge ~11-12k medium, ma SurplusRebalance riduce M20 (deficit fill migliora), pareggiando. M20 -1,400M/447 (run1) e -1,412M/450 (run2) vs treno -1,45M/470.
- **M33 attivo e leggero**: -11.200 medium / 69 match (run1), -12.000 / 77 (run2). 100/h sopra l'atteso, sui giorni surplus residuali post-rebalance. La LS non parcheggia piu' gratis sopra il piano.
- **SurplusRebalance VERIFICATO, improve-or-noop tenuto**: NESSUN IllegalStateException in nessun run (l'invariante fail-closed non e' mai scattata). run1 16h mosse (deficit 461->447, surplus 79->69, M20 day-fill 1.417.500 -> 1.405.000), run2 33h mosse (deficit 477->450, surplus 99->77, M20 1.448.000 -> 1.412.000). 0 min-slide DE-ANCHORS in entrambi (attesi 0). move-budget WARN mai raggiunto.

## Top flags (deviazioni misurate, nessun allarme soglia)

1. **giorni-sopra cala ma resta sopra il golden** (111/118 vs golden 70). SurplusRebalance + M33 lo riducono di ~15-18 punti vs il treno, ma non lo portano a 70: il residual e' surplus strutturale che il rebalance non puo' spostare (nessun giorno deficit della stessa classe che possa accettare l'ora, o vincoli M18 veto). La quota M33 (69/77 surplus days) e' la leva score che continua a premere sulla LS su quel residual.
2. **Varianza run-to-run su h mosse dal rebalance** (run1 16h, run2 33h) e sul box post-B1 (run1 box=1 -> demozione -> B2; run2 box=0 -> nessuna demozione). Stessa famiglia di varianza del treno (train-run1 2 giri/4 pin, train-run2 0/0). Nessun run invalido. Tempi: run1 ~91min (2 iter B-TIMESLOT), run2 ~62min (1 iter, box=0 pulito).
3. **M15 piu' alto del treno ma non esplosivo**: 22/26 match (-43k/-73k medium) vs treno 11/32. Varianza run-to-run nella banda attesa; ben sotto il golden (~98 split). M32 stabile (786/793 vs treno 787/803).

Nessun allarme soglia: ricorrenza >=70 PASS (73,5-73,7), burst <55 PASS (45,1-45,5), M32 non esplode (786/793), M15 non esplode (22/26), buchi 0 PASS, hard 0 PASS, 0 IllegalStateException PASS, 0 DE-ANCHORS PASS.

## Tabella (train2 x2 vs treno x2 vs val408 vs golden)

| Metrica | train2-run1 | train2-run2 | treno run1 | treno run2 | val408 (r1/r2) | golden |
|---|---|---|---|---|---|---|
| hard | **0** | **0** | -3 | 0 | -1/-1 | 0 |
| H16b | 0 | 0 | 0 | 0 | 0/0 | 0 |
| H17 | 0 | 0 | 0 | 0 | n/a | 0 |
| medium | -2.301.305 | -2.330.579 | -2.281.494 | -2.375.862 | -1.905/-1.910 M | n/a |
| soft | +12.677 | -3.307 | +9.883 | -6.438 | -43,9k/-47,1k | n/a |
| **ricorrenza unpinned** | **73,5%** | **73,7%** | 73,9% | 73,4% | 52,8/53,6 | n/a |
| ricorrenza totale | 75,1% | 75,3% | 75,4% | 75,1% | 58,9/59,6 | n/a |
| **full-day** | **77,0%** | **76,6%** | 75,5% | 75,0% | 79,9/80,0 | 85,4 |
| **giorni-sopra (kpi-fill)** | **111** | **118** | 126 | 136 | n/a | 70 |
| sotto (deficit h) | 440 / 1584h | 442 / 1588h | 461/1608h | 462/1618h | n/a | n/a |
| day-start | 95,9% | 96,0% | 95,9% | 95,7% | 93,7/93,7 | ~99,3-100 |
| sharpness pooled | 82,8% | 82,1% | 79,8% | 81,1% | 79,7/77,5 | n/a |
| burst>2x | 45,1% | 45,5% | 45,9% | 45,9% | 81,6/79,1 | 41,6 |
| gap-weeks | 1813 | 1776 | 1826 | 1716 | 2405/2331 | n/a |
| moda-knot | 0 | 0 | 0 | 0 | n/a | 0 |
| **buchi interni (giorni/slot)** | **0/0** | **0/0** | 0/0 | 0/0 | n/a | 1/1 |
| **M33 (medium/match)** | **-11.200/69** | **-12.000/77** | n/a | n/a | n/a | n/a |
| **M15 (medium/match)** | -43.200/22 | -73.200/26 | -19.200/11 | (32 match) | n/a | ~98 |
| M32 (medium/match) | -123.129/786 | -124.142/793 | -123,5k/787 | -126,1k/803 | n/a | n/a |
| M20 (medium/match) | -1.400.500/447 | -1.412.000/450 | -1,45M/470 | -1,45M/470 | n/a | n/a |
| M20.2 (medium/match) | -112.000/10 | -98.500/9 | -71/-98k | -71/-98k | n/a | n/a |
| M22 (medium/match) | -583.000/13 | -582.000/13 | n/a | n/a | n/a | n/a |
| promoted | 7132 | 7132 | 7132 | 7132 | n/a | n/a |
| **demozioni (giri/pin)** | **1 / 2** | **0 / 0** | 2 / 4 | 0 / 0 | n/a | n/a |
| B-TIMESLOT iter / wall | 2 iter (3726s + 1434s) | 1 iter / 3435s | 3 iter (~112min) | 1 iter (~41min) | n/a | n/a |
| B-ROOM wall | 299s | 255s | 266s | 286s | n/a | n/a |
| **SurplusRebalance: h mosse** | **16** | **33** | n/a | n/a | n/a | n/a |
| **SurplusRebalance: deficit days** | 461 -> 447 | 477 -> 450 | n/a | n/a | n/a | n/a |
| **SurplusRebalance: surplus days (quota-0 esclusi)** | 79 -> 69 | 99 -> 77 | n/a | n/a | n/a | n/a |

## Righe nuove (richiesta esplicita)

- **SurplusRebalance (TASK-422, log `TASK-422 surplus rebalance`)** - il pass deterministico surplus->deficit DOPO HoleRepair nella fase RELEASE, prima di B-ROOM:
  - run1: **16 hour(s) moved above-quota -> deficit, M20 day-fill cost 1.417.500 -> 1.405.000** (-17.000 medium), deficit days 461 -> 447, surplus days 79 -> 69.
  - run2: **33 hour(s) moved above-quota -> deficit, M20 day-fill cost 1.448.000 -> 1.412.000** (-36.000 medium), deficit days 477 -> 450, surplus days 99 -> 77.
  - NB: il conteggio `surplus days` del rebalance ESCLUDE i giorni quota-0 (giorni dove il gruppo non ha riga DailyActivityQuota: un quota-0 day e' un donatore legittimo ma non viene contato come "surplus" nella cifra che legge l'operatore, vedi `SurplusRebalance.DayFill`). Per questo 79/99 (pre) e 69/77 (post) differiscono dal `sopra` del kpi-fill (111/118, che conta invece tutti i giorni sopra l'atteso).
  - **move-budget WARN**: mai raggiunto in nessun run (MAX_MOVES_PER_PASS non saturato).
- **HoleRepair (log `TASK-419 hole repair`)** - invariato dal treno, confermato:
  - run1: H17 hole slots 2 -> 0 (closed 2 by level = **0 free import, 2 on-template import, 0 min-slide**), conflicts added 0. M20 day-fill cost **1.417.500 -> 1.417.500** (neutro: gli on-template import li preleva da giorni che potevano permetterlo).
  - run2: H17 hole slots 2 -> 0 (closed 2 by level = **0 free import, 2 on-template import, 0 min-slide**), conflicts added 0. M20 day-fill cost **1.448.000 -> 1.448.000** (neutro).
  - **min-slide DE-ANCHORS**: 0 in entrambi (attesi 0).
- **IllegalStateException fail-closed (improve-or-noop)**: **NESSUNA in nessun run**. L'invariante di `SurplusRebalance.repair` (throw se chiamata a solver attivo, vedi linea 227) e di `HoleRepair.repair` (linea 148) non sono mai scattate. Entrambi i pass hanno completato clean. Se un ciclo avesse abortito sarebbe stato un finding CRITICO da riportare subito: non e' il caso.
- **Demozioni**: run1 1 giro / 2 pin (lesson 568, 569 - G03 / T03 / Laboratorio Termoidraulico, M32 weight=101), box=1 dopo B1 -> B2 box=0 -> RELEASE. run2 0/0 (box=0 gia' dopo B1, RELEASE diretta, singola iterazione).
- **DE-ANCHORS (attesi 0)**: 0 min-slide DE-ANCHORS in entrambi i run (HoleRepair), 0 SurplusRebalance anchor break.
- **M33 (TASK-423, nuovo vincolo)**: `M33 - Surplus beyond expected day hours`, peso M33_SURPLUS_WEIGHT=100/hour, medium. run1 -11.200/69, run2 -12.000/77. Lo specchio di M20: M20 carica le ore MANCANTI dal piano del giorno, M33 carica le ore SOPRA il piano. I 69/77 match corrispondono ai surplus days residuali post-rebalance.

## Lettura (descrittiva)

Il treno-2 (SurplusRebalance + M33) muove le metriche nella direzione che il treno (M20 progressivo da solo) non era riuscito a muovere: **full-day su (+1,5pp) e giorni-sopra giu' (~15-18 punti)**, mantenendo hard 0, ricorrenza >=70, e senza esplodere M15/M32. La meccanica e' pulita: SurplusRebalance sposta determinista 16-33h sopra-quota -> deficit (migliora M20 di 17-36k medium), M33 aggiunge un light medium (~11-12k) che continua a premere sul surplus residual che il rebalance non puo' spostare (nessun matched deficit della stessa classe, o veto M18). Il costo medium netto e' comparabile al treno (M33 +11-12k pareggiato dal M20 guadagnato dal rebalance), non peggiore.

HoleRepair resta la vittoria hard (H17 0, buchi 0 in entrambi, identico al treno). La nuova leva del treno-2 e' tutta sul lato medium/day-fill: converte surplus in deficit coperto, e il full-day lo testimonia (77,0/76,6 vs 75,0-75,5 del treno). Il residual sopra-golden (111/118 vs 70) e' surplus strutturale (stage windows, G14/FC dove lo sheet chiede 990h ma il corso ne ha 115, finestre), non un difetto della leva.

Stabilita' hard 0/0 molto stretta (come il train-run2), nessun IllegalStateException, 0 DE-ANCHORS. Tempi: run2 ~62min (1 iter, box=0 pulito), run1 ~91min (2 iter per 1 demozione).

## Note tecniche

- **Bug `api.sh wait` scoperto e aggirato** (non e' un bug del solver, del harness script): `api.sh wait` ha `set -e` e grep su `"stagedStage":"..."` (valore quotato). A ciclo staged completato `stagedStage` e' `null` (JSON non quotato), il grep no-matcha, `pipefail` fa `exit 1` SUBITO. Risultato: `api.sh wait` esce SEMPRE non-zero quando il ciclo e' done, anche se `api.sh status` riporta `solverStatus: NOT_SOLVING` sano. Il wrapper robusto del batch treno (re-arm su wait non-zero) non era sufficiente: la re-check parsava la forma JSON `"solverStatus":"..."` ma `api.sh status` stampa la forma umana `solverStatus: NOT_SOLVING` (senza virgolette), quindi `sv` risultava sempre vuoto e la done-condition non si soddisfaceva mai -> loop infinito, capture mai partita. run1 e' stata catturata manualmente (java vivo, solve DONE e persistita nel DB: 12286 lesson assegnate, 2651 pinned). Per run2 il wrapper e' stato corretto: poll diretto di `api.sh status` (forma umana), done quando `solverStatus: NOT_SOLVING` E nessuna riga `stagedStage:`. run2 ha completato end-to-end e auto-catturato (wait DONE rilevato alle 04:02:37). Questo bug va riportato a chi mantiene api.sh (file tracciato, non e' la mia call modifireferente): `api.sh wait` e' inaffidabile per la done-detection del ciclo staged.
- **Deterministicita' seed verificata su entrambi i run**: 1368 timeslot / 27 teacher / 273 course / 12286 lesson post-import; import stagePinned 1785 / off-grid 295 / skip 5; authoritative pin 2651 (866 MODA + 1785 stage); pre-assign candidatesTried 0. Identici al treno e a CLAUDE.md (valori post-TASK-408).
- **Stale gate verify-baseline (registrato, non toccato)**: `verify-baseline.sh seed` asserisce timeslot 1376 (produce 1368, post-TASK-408); `verify-baseline.sh import` asserisce stage 1800/off-grid 280 (produce 1785/295, post-TASK-408). Entrambi gli actual combaciano con CLAUDE.md; solo lo script e' stale (datato 22/07, pre-TASK-408). File tracciato, non modificato.
- **-Dplanner.phase-transition-dump.enabled=true verificato**: 5 dump di fase raccolti per run (+5 boxed) in `target/phase-transitions/` (con resemblance per-fase), su entrambi i run. La capture li raccoglie.
- **kpi-holes.txt non vuoto in entrambi i run** (metrica richiesta): 29 righe ciascuno, per-gruppo con classificazione OK-pieno / fine-anticipata / inizio-tardivo / entrambi / BUCO-INTERNO. BUCO-INTERNO 0 giorni / 0 slot in entrambi.
- **M32 knobs a default** (numerator=100000 min=20 max=500): corretto, l'utente non ha chiesto di toccarli (nessun cambio di config).
- **Caffeinate**: `caffeinate -is -t 28800` attivo (idle+system, 8h) per coprire i ~2,5h di solve totale. Profilo dev fresco a ogni run, UN solve alla volta.
