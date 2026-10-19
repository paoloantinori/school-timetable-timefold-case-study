# TASK-463 + TASK-464 combined full-solve - VERDETTO run1+run2 n=2 (zai, 2026-07-31 notte)

Traguardo dichiarato: HARD 0 combinato (464 azzera i room-null, 463 chiude i 18 buchi H17 G13).
Braccio: 451 ON (default) + constrained-teacher ON (default) + **464 ON** + **463 ON**, entrambi i
flag sulla riga `java -jar` via `PLANNER_JAVA_OPTS` (MAI MAVEN_OPTS). Profilo production fresco
(drop-and-create seed + import-calendario + pre-assign, identico ai run di riferimento). Banner a
solve-start confermati entrambi `enabled=true` (`H-COPRESENCE ... enabled=true, depth>=2 cells=91`;
`TASK-463 lab-day consolidation: enabled=true`). **Run1 + Run2: n=2 concorde, hard 0
riproducibile (non lucky basin, lezione E3 superata).**

## VERDETTO (3 righe, dai raw)
1. **HARD 0 CENTRATO - primo run feasible (hard 0) della campagna 2026/27.** 463 chiude tutti i 18
   buchi H17 G13 (`lab-day gaps 18->0, closed 18; donors relocated 18, skipped shell-game 0 /
   no-move 0, conflicts added 0`); 464 azzera i room-null. Breakdown analysis: OGNI constraint ha
   `hard: 0` (nessun H0 boxed, H17, H29 room-null, H1/H2/H3 conflitto). vs baseline 464-ON-only
   -18/-17 hard (run1/run2 di oggi).
2. **Coordinatore 463 pulito e completo.** 18 transazioni commit (donors relocated 18 = 18 anchor
   import), 0 donatori scartati shell-game (guardia anti-shell-game mai attivata), 0 scartati
   no-move, conflicts added 0 (`assertImproveOrNoop` ha tenuto = no-worsen timeslot-tier garantito).
   Le mosse legali per la donatrice ESISTONO sempre (stop-condition di maintainer non attivata).
3. **Nessuna regressione hard/strutturale; costo soft dichiarato.** buchi interni 18->0, full-day
   89,3->89,5%, resemblance unpinned 75,5->75,6%, G11 ven late-start 0/0 (AC#3 rispettata, no
   reintroduzione). Day-start anchored 99,4% (golden-level). Medium -960k->-985813 (~-22k): 463
   chiude il buco slot-11 G13 shiftando 11 giorni G13 ven a late-start (M18 -650, 13 matches):
   trade-off hard-vs-soft largamente favorevole (hard 0 vs -18).

## A/B table (raw dagli analysis.json + export.json + KPI; baseline = task464-results/on-run1,2)

| metric | baseline 464-ON (on-run1 / on-run2) | combined run1 | combined run2 |
|---|---|---|---|
| **score hard** | **-18 / -17** | **0** | **0** |
| score medium | -963349 / -956377 | -985813 | -994722 |
| score soft | 5610 / 8290 | 3323 | 2747 |
| H17 vertically compact (hard) | -18 / -17 (18/17 G13 slot-11) | **0 (18->0)** | **0 (18->0)** |
| H29 room-null (hard) | 0 / 0 (464 gia' azzera) | 0 | 0 |
| buchi interni (G13 slot-11) | 18 / 17 | **0** | **0** |
| coordinator: gaps closed / relocated / shell-game / conflicts | n/a (flag off) | 18 / 18 / 0 / 0 | 18 / 18 / 0 / 0 |
| full-day rate | 89,3% / 89,3% | 89,5% | 90,4% |
| resemblance unpinned | 75,6% / 75,5% | 75,6% | 75,8% |
| day-start anchored | (non catturato) | 99,4% | 99,5% |
| G13 ven late-start (M18 costo soft) | 0 (anchored, ma con buco slot-11) | 11 (senza buco) | 10 (senza buco) |

**Conferma n=2.** Hard 0 in entrambi i run; coordinatore DETERMINISTICO (18 gap chiusi / 18
donors relocated / 0 shell-game / 0 conflicts in entrambi, ordine (group,date,slot)). Soft con
varianza run-to-run normale (medium -985813/-994722, full-day 89,5/90,4%, day-start 99,4/99,5%).
Non e' un lucky basin.

## Catena causale (perche' hard 0 e' del combined, non della varianza)
- Baseline 464-ON: room-null gia' 0 (464), residuo hard = soli 18 buchi H17 G13 ven slot-11.
- Combined +463: il coordinatore, per ognuno dei 18 gap, identifica il donatore cross-gruppo che
  satura T10@11 (1/G08), lo rilocalizza a uno slot 0-hard (guardia anti-shell-game + H17-safe
  departure), e importa una lezione anchor (T10/LM) nel gap. 18/18 transazioni commit, 0 revert.
- Identico setup, JAR identico (rebuilt col codice 463), solo il flag 463 cambia vs la baseline
  464-ON: l'attribuzione e' pulita. n=1 (run2 di conferma in corso per chiudere la varianza).

## Costo soft onesto (M18 day-start)
463 converte 18 buchi H17 HARD -> 0 al prezzo di 11 late-start G13 ven (il consolidamento sposta
l'inizio del giorno G13 dallo slot 8 con buco@11 allo slot 9 contiguo). M18 medium -650 (13 match
totali: G13 11 + G09 1 + G14 1). Trade-off: -18 hard (->0) contro ~-550 medium. Il day-start
globale resta 99,4% (golden ~99,3-100%). AC#3 rispettata (G11 ven 0/0: nessuna reintroduzione
dei late-start che TASK-451 aveva chiuso).

## Setup / riproducibilita'
Capture completo (capture-run.sh + export.json.gz + analysis.json + tutti i KPI + viz HTML) in
`task463-results/combined-run1/`. Comando:
```
PLANNER_JAVA_OPTS="-Dplanner.co-presenza.enabled=true -Dplanner.construction.lab-day-consolidation.enabled=true" \
  ./scripts/core/prod-start-and-wait.sh 300
./scripts/api/import-calendario.sh && ./scripts/benchmark/verify-baseline.sh import
./scripts/api/api.sh pre-assign-reset && ./scripts/api/api.sh pre-assign-reset && ./scripts/api/api.sh pre-assign  # COMPLETE
./scripts/api/api.sh solve-staged        # poll stagedStage; ~22min (B-TIMESLOT +767s -> B-ROOM -> done +1353s)
./scripts/benchmark/capture-run.sh <outdir>
```
Riga-chiave del coordinatore nel log (`/tmp/planner-prod.log`): `LabDayConsolidation (TASK-463
prototype): lab-day gaps N->M (closed K); donors relocated X, skipped shell-game Y / no-move Z,
conflicts added W`.

## Decisione
**Nessuna adozione.** Entrambi i flag restano DEFAULT OFF; la decisione e' di maintainer coi numeri
davanti. Il verdetto e' la MISURA DEL MECCANISMO 463+464 (traguardo tecnico hard-0 raggiungibile);
l'adozione si decide sul confronto (a) co-presenza 464 generalizzata vs (b) rotazione + teoria in
aula (mandato rotazione 31/07 sera), dopo la risposta di referente sul modello fisico reale
(doc `modello-rotazione-acconciatura-2026-07-31.md`).
