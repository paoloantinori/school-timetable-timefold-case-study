# Esperimenti su checkpoint locali, 2026-07-25 pomeriggio (zero solve completi)

Quattro esperimenti + due analisi, tutti su checkpoint committati (dump per confine di
fase di val408, TASK-415), eseguiti in locale in un pomeriggio. Nessun ciclo laptop speso.
Harness gated (env var, fuori dalla suite di default): Task419Stage1SpikeTest,
Task415ScoreTrajectoryTest, Task403EarlyRepairCheckpointTest,
Task415BenchmarkScreeningDemoTest. Varianza dichiarata: E1/E4 sono UNA traiettoria LS
(singolo seed); E2/E3 deterministici. Nulla di tutto questo e' evidenza di adozione
(TASK-401: un checkpoint fissa una traiettoria).

## E1 - Spike stage-1 TASK-419 (promozione template -> pinned): VIA LIBERA

Ingresso-fase-2 di val408-run1, 6.959 lezioni promosse (73% delle libere; regola = M32
deviatesFromTemplate negata, stesse esenzioni), LS fase 2 per 120s:
- ZERO deadlock, ZERO conflitti, ZERO unassigned a fine run (ipotesi A totale: lo stage 2
  di degrado non avrebbe avuto lavoro su questa traiettoria).
- Ricorrenza TENUTA a 73,8% (il single-solve con M32 si ferma a 52,8%).
- hard -148 -> -27 in 120s (la coda libera si sistema attorno al template congelato).

CAVEAT DI DESIGN (nuovo prerequisito per TASK-419, scoperto dallo spike): M20 e i suoi
fratelli pinned-exempt (M15/M18/M28) non VEDONO i pin promossi: M20 parte a -3,23M (vs
-1,07M per lo stesso grid unpinned) e spinge a ri-riempire giorni gia' pieni. Prima di un
arm laptop serve la semantica per i pin PROMOSSI (contarli nel netting alla maniera delle ore
Stage pinnate, o un flag distinto dal pinned autoritativo).

## E2 - Traiettoria score per fase (val408-run1): cosa compra l'erosione

| Stato | hard | medium | M20* | M22 | M15 | M32 |
|---|---|---|---|---|---|---|
| fine costruzione | -159 | -2.227.933 | -1.070.000 | -586.000 | -412.800 | -140.198 |
| fine fase 2 | -2 | -2.014.485 | -869.000 | -565.000 | -270.000 | -278.675 |
| fine fase 3 | -2 | -2.013.997 | (invariato, delta 488 = 0,024%) | | | |
| fine fase 4/5/6 | -1 | -2.042.443 | -856.000 | -563.000 | -318.000 | -282.372 |

La fase 2 brucia 21,5 punti di ricorrenza per +213k medium lordi (M20 +201k, M15 +143k,
M22 +21k) e ne RESTITUISCE il 65% a M32 (-138k): netto ~3.400 medium per punto di
ricorrenza. La fase 3 e' inerte alla quarta cifra (quarta conferma). Caveat: stati
ricostruiti senza box (i dump non lo portano), hard qui esclude H0.

## E3 - Repair anticipato ph2->ph3 (TASK-403): INCONCLUDENTE, onestamente

Il CompactionPhase sul grid di fine-fase-2 e' un no-op (2 gap H17 non chiudibili, zero
conflitti aggiunti) - MA il confronto col ph4 reale (-31 -> -2 hard) non e' decisivo:
quel -31 era quasi tutto H0 (box), e i dump pipe NON portano il box. Prerequisito per
rendere il test decisivo: PhaseTransitionMarker deve emettere anche le righe boxed
(annotato in TASK-403; l'harness e' gia' pronto).

## E4 - Screening benchmark (demo): REFUTATO dal multi-seed della sera stessa

Il primo passaggio (90s, un run) suggeriva "gli ultimi 3.500s di fase 2 comprano zero
medium e bruciano 20 punti di ricorrenza". Il check multi-seed della sera lo ha REFUTATO,
con due scoperte metodologiche piu' preziose del claim:
1. **Gli harness smoke giravano a SEED FISSO 0**: il SolverConfig ad-hoc non imposta
   environmentMode -> default Timefold PHASE_ASSERT, seed 0 (testuale nei log). Le
   "repliche" erano la stessa traiettoria campionata a step diversi (206/285/389 in 90s
   nominali, per contesa CPU); la produzione gira NON_REPRODUCIBLE. Ogni confronto locale
   di varianti fatto con questi harness era quindi sulla STESSA traiettoria (il ranking
   tabu7/tabu11 e' nullo). Gap da chiudere: env var per environmentMode/seed.
2. **Nessun ginocchio nella finestra 206-2.482 step**: medium monotono e ancora +72/step
   a 600s; ricorrenza in discesa continua. Il punto 600s (seed 0) batte il run reale
   3.600s su ENTRAMBI gli assi (-1,70M vs -2,01M; 68,6% vs 52,8%): varianza
   seed-to-seed, non risparmio gratis.
La domanda 326 resta APERTA nella forma giusta: dose-response del budget di fase 2 sotto
NON_REPRODUCIBLE reale = bracci laptop corti (600s/1800s, economici) vs baseline 3.600s.

## Anatomia dell'erosione (task419-erosion-anatomy-2026-07-25.md)

La gradazione per scarsita' morde a meta': 4 docenti Q22 su 6 tra i meglio conservati
(T19 97,1%, T01 93,8%), ma T17 (59,5%) e T12 (62,4%) sono i PEGGIO
conservati di tutti i 25 - il peso M32 e' per-CORSO e i loro corsi sono flessibili anche
se il docente e' a giorni fissi. Raffinamento candidato: peso = f(scarsita' corso,
scarsita' docente). L'80% delle ore spostate e' deviazione genuina fuori template;
l'erosione e' SISTEMATICA tra i run (r=0,95): pressione strutturale, non rumore.

## Decisione apparecchiata (ratifica/scelta a maintainer)

Tre leve sullo stesso gap (ricorrenza/cadenza verso il golden), stato POST multi-seed:
1. **TASK-326** (budget fase 2): il claim locale e' stato refutato; la domanda giusta
   (dose-response sotto randomicita' vera) si risponde SUL laptop con bracci corti ed
   economici (600s x3, 1800s x2 vs baseline val408 3.600s x2) - programma notte 25/26.
2. **TASK-419** (solve a stadi): spike verde (zero deadlock, 73,8% tenuto - anche questo
   a seed 0: UNA traiettoria, ma la fattibilita' zero-deadlock e' un risultato di
   struttura, meno seed-sensibile di un confronto di score), prerequisito di design
   sulle esenzioni M20-family per i pin promossi.
3. **Cluster mosse/accettore** (280 e figli): screening da RIFARE dopo il fix
   seed/environmentMode negli harness; il ranking di oggi non vale.
Nota: 326 e 419 potrebbero ancora comporsi; prima i dati veri della notte laptop.
