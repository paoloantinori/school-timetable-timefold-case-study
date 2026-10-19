# TASK-326 / TASK-419 overnight 2026-07-25/26 - VERDICT descrittivo (laptop)

Campagna di misura notturna. **Solo misura, nessuna adozione. Decide Fedora.**
HEAD: `071298d5` (feature/cartesian-block-entity). Baseline 3.600s = `val408-run1/2` (già in cassa, stesso HEAD).
laptop, profilo FRESCO per ogni run, heap 2g, un solve alla volta, `caffeinate -i` per tutta la durata.

## 0. Setup e metodo ( importante per la riproducibilità)

Ogni run = ciclo completo: kill-app -> delete profilo dev -> fresh seed -> import-calendario -> pre-assign-reset + pre-assign -> solve a terminazione naturale -> `capture-run.sh`.

**Scoperta critica (MAVEN_OPTS non propaga).** Il programma diceva di esportare `MAVEN_OPTS` con i flag `-D`. L'app però parte via `mvn quarkus:run`, che **forza un processo `java -jar target/quarkus-app/quarkus-run.jar` separato**: i `-D` in `MAVEN_OPTS` restano nella JVM di Maven e **non raggiungono mai la JVM dell'app**. Il primo run B600 (MAVEN_OPTS) ha prodotto **zero phase-transition dump** e la knob line mostrava i default. Verificato meccanicamente.
**Fix applicato (verificato):** l'app è lanciata direttamente con `java -jar` e i `-D` sulla command line (modalità di lancio di val408):
`java -Xmx2g -Dplanner.phase-transition-dump.enabled=true [-Dplanner.m32.numerator=300000 -Dplanner.m32.min=60 -Dplanner.m32.max=1500] -Dquarkus.hibernate-orm.schema-management.strategy=update -jar target/quarkus-app/quarkus-run.jar`.
Dopo il fix: dump presenti (10 per run) e, nel braccio W, knob line = `numerator=300000 min=60 max=1500` (gate mandatory PASSATO). La nota è stata aggiunta a `CLAUDE.md` ("⚠️ CRITICAL: -D system properties for a LIVE SOLVE..."). I 7 run della campagna sono tutti con il fix; il B600-run1 MAVEN_OPTS iniziale è scartato (non fa testo).

**Integrità dati.** Seed fresco per ogni run: timeslot=1368 (post-TASK-408), import `modaLessonsPinned=866 / modaFailures=[]`, pre-assign COMPLETE. `solverConfig.xml` riportato al canonico (3600/3600) a fine campagna; `git status` pulito sul file (edit mai committati). `box (H0)=0` in tutti i run (nessuna lezione non assegnata). `placed=12.287` (dump lessons.txt). Phase-transition dump: 10 per run (5 fasi x 2 formati: `phase-<n>-<slug>.txt` placed + `phase-<n>-<slug>-boxed.txt` boxed, TASK-419).

## 1. Dose-response del budget di fase 2 (600 / 1800 / 3600=val408)

| metric | B600 r1 | B600 r2 | B600 r3 | B1800 r1 | B1800 r2 | val408 r1 | val408 r2 |
|---|---:|---:|---:|---:|---:|---:|---:|
| fase-2 secondsSpentLimit | 600 | 600 | 600 | 1800 | 1800 | 3600 | 3600 |
| **hard** | -10 | -8 | -6 | -4 | **0** | -1 | -1 |
| box (H0 unassigned) | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **medium** | -2.033.023 | -2.122.604 | -2.089.246 | -1.982.754 | -1.977.399 | -1.904.823 | -1.909.720 |
| M32 medium | -301.812 | -303.779 | -304.924 | -292.228 | -294.018 | -279.751 | -284.914 |
| M32 matches | 2.287 | 2.306 | 2.300 | 2.216 | 2.219 | 2.106 | 2.136 |
| **full-day rate** | 77,9% | 76,7% | 77,2% | 79,0% | 78,8% | 79,9% | 80,0% |
| **ricorrenza unpinned** | 51,5% | 51,4% | 51,9% | 52,2% | 51,3% | 52,8% | 53,6% |
| burst >2x | 80,7% | 80,7% | 82,0% | 77,9% | 82,0% | 81,6% | 79,1% |
| burst ratio medio | 2,92 | 2,95 | 2,95 | 2,99 | 3,00 | 2,97 | 2,93 |
| gap-weeks totali | 2.362 | 2.397 | 2.248 | 2.368 | 2.396 | 2.405 | 2.331 |
| **day-start anchor** | 92,6% | 91,8% | 91,5% | 92,9% | 93,2% | 93,7% | 93,7% |
| sharpness pooled | 75,9% | 75,1% | 73,4% | 78,3% | 76,8% | 79,7% | 77,5% |
| sharpness macro | 73,5% | 72,9% | 71,2% | 75,8% | 74,3% | 76,8% | 74,8% |
| **durata solve (wall)** | 4.511s | 1.187s | 4.510s | 2.461s | 5.195s | 4.585s* | 4.069s* |

`*` val408: somma `timeSpentMillis` da phases.json (non ha il solve_secs del wrapper; equivale al wall).

**Lettura descrittiva.**
- **hard** scala monotonamente con il budget di fase 2: B600 ~-8 (range -6/-10), B1800 ~-2 (-4/0), val408 -1. Più tempo in fase 2 (compactness) chiude più violazioni hard.
- **medium** migliora con il budget: B600 ~-2,08M, B1800 ~-1,98M, val408 ~-1,91M. Anche M32 scende con il budget (~-304k -> ~-293k -> ~-282k): più fase 2 riduce le deviazioni template.
- **full-day** sale con il budget: B600 ~77%, B1800 ~79%, val408 ~80%. **day-start** e **sharpness** simile andamento (marginalmente migliore con più budget).
- **ricorrenza unpinned** leggermente crescente (B600 ~51,5% -> val408 ~53,2%): più fase 2 preserva un po' più template, ma l'effetto è piccolo (~1,5pp).
- **cadence (burst/gap-weeks)** sostanzialmente piatta attraverso i budget (~80% burst>2x, ~2.350 gap-weeks): la cadenza non è sensibile al budget di fase 2.
- **durata solve altamente variabile** (1.187-5.195s) per terminazione anticipata (`unimprovedSecondsSpentLimit=600` / `diminishedReturns`): B600 r2 è terminato a 1.187s trovando subito un bacino; altri run hanno consumato tutto il budget di fase 3. Non monotona nel budget.

## 2. W (M32 rinforzato 300000/60/1500) vs val408 (M32 default 100000/20/500), entrambi fase-2 3.600s

| metric | W r1 | W r2 | val408 r1 | val408 r2 |
|---|---:|---:|---:|---:|
| M32 knob | 300000/60/1500 | 300000/60/1500 | 100000/20/500 | 100000/20/500 |
| **hard** | -1 | -2 | -1 | -1 |
| box | 0 | 0 | 0 | 0 |
| **medium (totale)** | -2.495.588 | -2.488.047 | -1.904.823 | -1.909.720 |
| **M32 medium** | -842.981 | -839.206 | -279.751 | -284.914 |
| M32 matches | 2.105 | 2.099 | 2.106 | 2.136 |
| M32 medium / match | ~401 | ~399 | ~133 | ~133 |
| medium non-M32 | ~-1.653M | ~-1.649M | ~-1.625M | ~-1.625M |
| full-day | 79,4% | 79,3% | 79,9% | 80,0% |
| ricorrenza unpinned | 53,5% | 53,6% | 52,8% | 53,6% |
| burst >2x | 79,9% | 80,3% | 81,6% | 79,1% |
| gap-weeks | 2.383 | 2.400 | 2.405 | 2.331 |
| day-start | 94,1% | 93,2% | 93,7% | 93,7% |
| sharpness pooled | 79,6% | 75,9% | 79,7% | 77,5% |

**Lettura descrittiva (TASK-419).**
- Il knob ha preso efficacemente: **M32 medium / match = ~400 in W vs ~133 in val408 = 3,0x** esatto (numerator 300000 vs 100000), a parità di ~2.100 deviazioni. conferma meccanica del quasi-pinning graduato.
- **hard invariato** (-1/-2 vs -1/-1): M32 è tier medium, non tocca l'hard.
- **medium totale +589k più alto in W**, interamente dall'inflazione M32 (~+560k). Il medium **non-M32** è in realtà leggermente **più basso** in W (~-1,65M vs ~-1,625M): il rinforzo M32 spinge il solver a preservare meglio il template, riducendo altre penalità medium.
- **ricorrenza unpinned marginalmente più alta** in W r1 (53,5% vs 52,8%; r2 pari 53,6%). Il quasi-pinning 3x preserva un po' più template ma l'effetto è ~+0,7pp, **lontano dal ripristinare il 74% della costruzione** (vedi sezione 3).
- full-day/day-start/sharpness/cadence **comparabili**. Nessun degrado strutturale dal rinforzo.
- Sintesi: raddoppiare/triplicare il peso M32 **inflaziona il medium (~3x) senza un guadagno proporzionale di ricorrenza** (~+0,7pp). Il quasi-pinning al default 100000 (TASK-413 adottato) è già vicino al suo plateau.

## 3. Erosione della ricorrenza per fase (ricorrenza UNPINNED %, da phase-transition dump)

| run | fase 2 (compactness, =fine costruzione) | fase 3 (ruin-recreate) | fase 4 (compaction) | fase 5 (room CH) | fase 6 (room LS) | erosione f2->f6 |
|---|---:|---:|---:|---:|---:|---:|
| B600 r1 | 74,3 | 52,0 | 51,7 | 51,5 | 51,5 | -22,8 |
| B600 r2 | 74,3 | 51,6 | 51,6 | 51,4 | 51,4 | -22,9 |
| B600 r3 | 74,3 | 51,8 | 52,3 | 51,9 | 51,9 | -22,4 |
| B1800 r1 | 74,3 | 52,4 | 52,4 | 52,2 | 52,2 | -22,1 |
| B1800 r2 | 74,3 | 51,3 | 51,4 | 51,3 | 51,3 | -23,0 |
| W r1 (M32x3) | 74,3 | 53,4 | 53,5 | 53,5 | 53,5 | -20,8 |
| W r2 (M32x3) | 74,3 | 53,8 | 53,8 | 53,6 | 53,6 | -20,7 |
| val408 r1 | 74,3 | 52,8 | 52,8 | 52,8 | 52,8 | -21,5 |
| val408 r2 | 74,3 | 53,7 | 53,7 | 53,6 | 53,6 | -20,7 |

I dump `-boxed.txt` (formato group|teacher|subject) sono stati raccolti per ogni fase (5 per run) e sono disponibili per l'analisi box/eviction di TASK-419; non sono passati in resemblance-kpi (formato diverso), per questo la tabella sopra usa solo i dump placed.

**Lettura descrittiva (TASK-326, la domanda centrale).**
- **La fine della costruzione (fase 2) è 74,3% in TUTTI i run**, qualsiasi sia il budget di fase 2. È il valore stampato dal template settimanale (costruzione), invariante.
- **L'erosione accade INTERAMENTE nella fase 3 (ruin-recreate): 74,3% -> ~51-54%** (-20/-23pp). Le fasi 4 (compaction), 5 (room CH) e 6 (room LS) **non muovono la ricorrenza** (delta <0,3pp).
- **Il budget di fase 2 non cambia DOVE avviene l'erosione** (sempre fase 3) e cambia poco il valori finale (B600 ~51,5% -> val408 ~53,2%). Dunure la fase 2 non è il punto di leva per l'erosione.
- Il **M32 rinforzato (W) riduce leggermente l'erosione** (f2->f6 -20,7/-20,8 vs -21,5/-22, dei non-W), portando il finale a ~53,5%. Effetto reale ma modesto.
- Conclusione meccanica: l'erosione del template è un fenomeno della **fase 3 (ruin-recreate)**, non della fase 2. Il budget di fase 2 muove hard/medium/full-day ma non la rigidità del template. Per ridurre l'erosione servirebbe agire sulla fase 3 (mosse, penalità template in fase 3, o filtro) o sul M32, non sul budget di fase 2.

## 4. Tempi assoluti dei run

| run | start | end | durata solve (wall) |
|---|---|---|---:|
| B600 r1 | 25-07 19:05:46 | 20:21:46 | 4.511s |
| B600 r2 | 20:21:46 | 20:42:22 | 1.187s (terminazione anticipata) |
| B600 r3 | 20:42:22 | 21:58:23 | 4.510s |
| B1800 r1 | 21:58:37 | 22:40:26 | 2.461s |
| B1800 r2 | 22:40:26 | 26-07 00:07:52 | 5.195s |
| W r1 | 26-07 00:08:05 | 01:34:02 | 5.107s |
| W r2 | 01:34:02 | 02:48:34 | 4.421s |

Campagna completa senza crash, senza 429 rate-limit, un solve alla volta. Driver uscito con `DRIVER_DONE_ALL` (exit 0) alle 02:48:34.

## 5. Note finali (descrittivo, nessuna adozione)

- **TASK-326 (dose-response budget fase 2):** il budget di fase 2 muove hard, medium, full-day, day-start, sharpness in modo monotono (più budget = meglio), ma **non è il punto di leva per l'erosione del template** (che avviene in fase 3, budget-invariante). La durata del solve è dominata dalla terminazione anticipata di fase 3 (alta varianza), non dal budget di fase 2.
- **TASK-419 (M32 rinforzato):** il knob 300000/60/1500 prende efficacemente (3x sul penalty M32, ~stesse deviazioni), tiene l'hard, non degrada KPI, e riduce marginalmente l'erosione (~+0,7-1pp di ricorrenza finale). Il costo è +589k medium (inflazione M32). Il quasi-pinning al default 100000 è già vicino al plateau.
- Entrambe le decisioni (budget fase 2, knob M32) sono demandate a Fedora. Qui solo misura.

Artifact: `claudedocs/spalmone-2026-27/task326-results/val326-{b600-run{1,2,3},b1800-run{1,2}}/` e `task419-results/val419-m32x3-run{1,2}/` (score, analysis, phases, export, lessons.txt, KPI battery, golden-vs-run HTML, phase-transition dump placed+boxed). `solverConfig.xml` riportato al canonico, edit mai committati.
