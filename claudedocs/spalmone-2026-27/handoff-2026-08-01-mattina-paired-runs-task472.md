# HANDOFF 2026-08-01 mattina - PROGRAMMA: run appaiati post-cura + TASK-472 (maintainer sveglio, direttive accettate)

> **STATO RUN (zai, 01/08 ~08:50): cura VALIDATA, flip BLOCCATO da AnchorRepair.**
> - Run (a) golden appaiato (`task452-results/paired-golden-run1/`): **0 hard / -983635 medium /
>   4009 soft, full-day 90,2%, holes 0** (= combined-run2, riferimento confermato).
> - Run (b) source=input post-cura (`task452-results/verify-source-input-run2/`): **cura OK (0
>   H20, i 14 spariti) MA il ciclo staged ABORTISCE ad AnchorRepair** (improve-or-noop violato:
>   timeslot-tier hard -6 -> -7, conflict counter dice 0) -> **B-ROOM non gira -> 9446 room-null
>   (H29) -> run invalida**. Verdetto dettagliato: `verify-source-input-run2/VERDICT-2026-08-01.md`.
> - **Flip m-13 NON pronto.** La cura e' necessaria ma non sufficiente: il root cause e'
>   AnchorRepair (counter missa una famiglia hard timeslot-tier, probabile H-COPRESENCE abilitato;
>   da confermare coi phase-transition dumps). La cura sposta il landscape (T01 6h su piu'
>   giovedi') abbastanza da triggerare il bug latente. Run-1 pre-cura e run (a) golden non abortiscono.
> - **TASK-472 (durante i solve): FATTO.** Pilot no-2h-teoria: script `scripts/analyze/
>   theory_adjacent_report.py` + classification M-high-value (432 celle, 24% capacity-driven);
>   T16 3h-non-6: classification M-high-value pre-referente (93% rispetta 3h, 11 blocchi 5-6h).
>   Commit `fb54a749` + `646b7b23`. Block-size report script differito.
> - **Prossimo passo (decide maintainer):** investigare l'abort AnchorRepair (counter vs hard reale)
>   e curarlo, POI ri-testare run (b). Senza quello il flip resta bloccato su un difetto che NON
>   e' la cura. Niente adozione, default golden.


HEAD `feature/cartesian-block-entity` (`011ea62a`, pushato). Cura TASK-452 DENTRO + DoD clean
(commit `a511d39c` codice + `011ea62a` docs). App/DB running ma sulla JAR **pre-cura**: va rebuildata.

## DIRETTIVE maintainer ACCETTATE (quadro mattina)
- combined candidato **gated Q20**.
- **M-TR peso 1 OFF** come misuratore + la sostanza va nel workbook TASK-471.
- Pacchetto referente **resta pronto**.
- **Via libera ai run appaiati post-cura**.

## CHIAVE DI LETTURA NUOVA (TASK-472, `backlog/tasks/task-472 -*.md`)
Per ogni vincolo SCRITTO (docx/xlsx) violato dal golden: classificare **H-vero vs M-alto-valore**
(se quasi-sempre rispettato puo' essere un H che l'human planner non risolveva, o un M ad alto
valore; distingui con tasso + clustering temporale/di-gruppo + correlazione scarsita'). Obiettivo
sempre **0-hard**. REPORT RIPETIBILE per regola (script in `scripts/analyze/`, input golden CSV o
lessons.txt run; output occorrenze + spiegazione tentata o 'inspiegata'). Le euristiche di
costruzione inferite dal golden sono **CANDIDATE** legittime (riduzione spazio), non overfitting.

## PROGRAMMA

### (1) RUN APPAIATI post-cura (approvati) - confronto a 3
Tre run a confronto: **input-run1 pre-cura** (gia' catturato in `task452-results/verify-source-input-run1/`),
**input-run2 post-cura** (nuovo), **golden appaiato** (nuovo). Flag identici a run1 tranne
`source=input` (l'asse del confronto): co-presenza ON + lab-day-consolidation ON (468 default OFF).

**Protocollo combined-run2 (identico per entrambi i run nuovi):**
1. `./planner.sh kill-app`
2. rebuild JAR con cura: `./scripts/core/rebuild.sh` (mvn clean package). Poi kill-app (rebuild.sh restarta via planner.sh run; per il solve voglio java -jar).
3. wipe profilo fresco: `yes | ./planner.sh delete production`
4. start java -jar con `PLANNER_JAVA_OPTS` (vedi sotto), timeout 300s:
   `PLANNER_JAVA_OPTS="..." ./scripts/core/prod-start-and-wait.sh 300`
5. `./scripts/api/import-calendario.sh` (2859 pin = 866 MODA + 1993 stage; verify-baseline)
6. `./scripts/api/api.sh pre-assign-reset` x2, poi `./scripts/api/api.sh pre-assign`
7. `./scripts/api/api.sh solve-staged` ( aspetta staged-aware: poll `stagedStage` fino a null; ~34-40 min)
8. `./scripts/benchmark/capture-run.sh <outdir>` (score/analysis/lessons/KPI/viz)

**(a) golden appaiato** -> capture `task452-results/paired-golden-run1/`
  `PLANNER_JAVA_OPTS="-Dplanner.co-presenza.enabled=true -Dplanner.construction.lab-day-consolidation.enabled=true"`
  (source=golden DEFAULT, nessun source=input)
**(b) source=input post-cura** -> capture `task452-results/verify-source-input-run2/`
  `PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input -Dplanner.co-presenza.enabled=true -Dplanner.construction.lab-day-consolidation.enabled=true"`

**Verdetto (3 righe dai raw):** i 14 H20 devono sparire in (b); confronta hard/boxed/H0/KPI
(full-day, ricorrenza, day-start, holes) tra i 3. Se (b) chiude vicino al golden appaiato ->
dichiara **"flip m-13 PRONTO, resta il SI di maintainer"**; **nessun flip** (solo dichiarazione).

### (2) DURANTE i solve (~40 min ciascuno, non restare passivo) - TASK-472
Caso PILOTA gia' misurato: **no-2h-teoria (TASK-468)**, tasso 432/440 su 6742 ore teoria golden
(usare come pilota della classificazione H-vs-M). Poi **T16 3h-non-6** (4 lunedi' 6h + 6x5h).
Per ciascuno: proposta di tier motivata (tasso, clustering, correlazione scarsita'/periodi modulo)
+ **prima versione dello script report ripetibile** in `scripts/analyze/` (input: golden CSV
`seed/lessons.csv` o `lessons.txt` di un run; output: occorrenze + spiegazione tentata o
'inspiegata'). **Commit incrementali.**

### (3) FINE RUN
Aggiorna QUADRO + handoff con il verdetto a 3 e lo stato TASK-472.

## REGOLE STANDING
Nessuna adozione, default golden, verdetti dai raw (analysis.json/KPI/score.txt), tempi assoluti,
-D sulla riga java -jar (PLANNER_JAVA_OPTS, non MAVEN_OPTS). App source=input overnight (vecchia
JAR pre-cura) va killata e sostituita con la JAR curtata. Punto di ripresa: questo handoff +
VERDICT (blocco CURA IMPLEMENTATA) + census #1 + TASK-472.
