# VERDETTO A/B source=input FLIP #1 (census TASK-452 ESITO 1) - zai, 2026-08-13

> Mandato Fedora (2026-08-13, post-VERDICT 470 = VARIANZA): **misura soltanto, non
> adotta/non reverta/non flippa default**. A/B appaiato CONTROL (source=golden, default)
> vs TREATMENT (source=input, knob gia' pronto), stesso protocollo del 470 (staged,
> multi-start maxAttempts=3, M-TC per-retry + repair DEFAULT ON), stessa macchina/sessione
> (laptop-RUNBOOK: within-machine comparability only). Scopo: confermare che input-derived
> produce schedule confrontabili col golden-derived FINCHE IL GOLDEN E ANCORA DISPONIBILE.
> Default resta **source=golden**: NESSUN flip.

## Perimetro (FLIP #1 del census TASK-452)

L'unica lettura runtime del golden sul percorso di produzione (#1 del census,
`loadAllGoldenProfiles`, DEFAULT ON, TASK-435) e' l'unico **ESITO 1** = sostituibile a
sorgente input senza chiedere nulla alla scuola. Il knob
`-Dplanner.pre-assign.constrained-teacher.source=input` (su riga `java -jar` via
`PLANNER_JAVA_OPTS`, NON MAVEN_OPTS) fa prendere il branch `buildAllInputDerivedProfiles`
(il profilo T01 ricostruito da incarichi + H5 weekday + calendario, con la cura
stage-aware interleaved del 2026-08-01) invece di `loadAllGoldenProfiles`. Il resto della
fase (detection, plan, pinning) e' source-agnostic. Census + storia run1-8:
`task452-deducibility-census-2026-08-01.md` (#1).

## PROVA MECCANICA DEL FLIP (banner asymmetry, dai raw app.log)

| arm | banner `TASK-452 source=input` | profile sorgente | tentativi |
|---|---|---|---|
| CONTROL (ab-off-run1) | **0** occorrenze | golden-derived (default) | 2 (`cycle finished` x2) |
| TREATMENT (ab-on-run1) | **1** occorrenza ("built input-derived profiles for 2 constrained teacher(s)") | input-derived | 1 |

Il control non ha MAI preso il branch input (0 banner); il treatment lo ha preso una volta
(1 banner). Il flip e' mechanical-confirmed end-to-end.

## ESITO: ENTRAMBI 0hard, schedule CONFRONTABILI, flip SICURO (a parita' di questo campione)

```
CONTROL  (source=golden, default): 0hard / -1270592medium / -23373soft  [2 tentativi, ~99min]
TREATMENT(source=input, opt-in)   : 0hard / -1038664medium /  2981soft  [1 tentativo,  ~35min]
```

## CONFRONTO APPAIATO (dai raw, stessa macchina Darwin arm64, stessa sessione)

| metric | CONTROL (source=golden) | TREATMENT (source=input) | delta (treat-control) | riferimento golden (paired-golden-run1) |
|---|---|---|---|---|
| hard | **0** | **0** | 0 | 0 |
| H0 unassigned (DB) | 0 | **0** (verificato `count(*) WHERE timeslot_id IS NULL`=0) | 0 | 0 |
| constraint negativeHard | nessuna | **nessuna** (analysis.json) | = | 0 |
| medium | -1270592 | **-1038664** | +231928 (migliore) | -983635 |
| soft | -23373 | **2981** | +26354 (migliore) | 4009 |
| full-day ex-G14 | 86,2% (1661/1928) | **86,9%** (1676/1928) | +0,7pp | 90,2% |
| ricorrenza unpinned | 75,6% (7110/9404) | 74,3% (6984/9404) | -1,3pp | 75,2% |
| buchi interni | 0 | **0** | = | 0 |
| tentativi multi-start | 2 | **1** | -1 | 1 |
| wall (solve-staged) | ~99 min | ~35 min | -64 min | n/a |

## LETTURA

1. **Hard 0 su ENTRAMBI (verificato dal reale artefatto, non solo score string).** Treatment:
   score 0hard + DB `count(*)` unassigned = 0 + analysis.json nessuna constraint con
   negativeHard. La fattibilita' input-derived regge: il profilo T01 ricostruito dal
   solo input (incarichi + H5 + calendario, cura stage-aware) piazza i Thursday di G10 su
   date non-stage senza saturare, come il golden. Il flip #1 e' **feasible end-to-end**.
2. **TUTTE le metriche del treatment sono DENTRO la banda di varianza within-session, non
   statisticamente distinguibili dal control.** Medium -1039k vs -1271k: entrambi fuori dal
   riferimento golden -984k, ma entro la banda dei run storici input (-966k..-1095k su n=8;
   il -1271k del control e' un esito di varianza, non il golden riferimento). Full-day
   86,9% vs 86,2%: differenza 0,7pp, sotto il rumore. Ricorrenza 74,3% vs 75,6%:
   differenza 1,3pp, in banda golden (~75%). Nessun KPI collassa.
3. **Il treatment NON e' "migliore" del control nonostante i numeri grezzi lo dicano.** Il
   control ha beccato un bacino debole (-1271k medium, full-day 86,2%, 2 tentativi: il
   multi-start ha dovuto ripartire); il treatment un bacino leggermente migliore (-1039k,
   1 tentativo). Su n=1 appaiato questo e' **varianza**, non segnale: il laptop-RUNBOOK
   (run-16 finding: -426 vs -3902 hard a parita' di config) e la bad-seed frequency del
   census (TASK-480: p(0-hard)~0.75 per singolo run input) dicono che un solo accoppiamento
   misura un punto, non la distribuzione. La lettura onesta e' **parita'** (input
   schedule-comparable a golden), non "input vince".
4. **Caveat strutturale del campione: il control NON e' il golden riferimento.** Il
   riferimento golden e' paired-golden-run1 (0hard/-984k/90,2%), ma il control di questo A/B
   ha chiuso a -1271k/86,2% su un bacino debole. Quindi il confronto e' "mediocre golden
   basin vs input", non "reference golden vs input". Il treatment regge anche contro il
   riferimento diretto: -1039k e' piu' vicino al -984k golden di quanto sia il control, e
   86,9% full-day e' sotto il 90,2% golden dello stesso -3,3pp visto in tutti i run input
   storici (gap noto, concentrato su FC-venerdi TASK-476 + G10, work item di ricerca non
   di modello).

## VERDETTO (dai raw, 3 righe)

1. **ENTRAMBI 0hard** (control e treatment), hard-0 verificato end-to-end dal DB e
   dall'analysis. Il FLIP #1 del census e' **SICURO a parita' di questo campione appaiato**:
   input-derived produce una schedule feasible e confrontabile col golden-derived.
2. **Medium nella banda di varianza, KPI reggono.** Nessuna metrica collassa passando a
   input: full-day, ricorrenza, buchi, soft tutti within-machine-comparable. Il gap
   full-day residuo (~87% vs golden 90%) e' il gap noto di TUTTI i run input (FC-venerdi
   school-gated TASK-476), non un regresso introdotto dal flip.
3. **DECISIONE resta a Fedora/maintainer (non flippo).** Il mandato e' MISURA soltanto. L'esito
   rafforza il verdetto "READY-WHEN-NEEDED" del census (run4-8): il path input e' feasible
   e paritetico oggi, quindi pronto quando servira' a regime post-golden. MA finche' il
   golden esiste il default-derived resta storicamente strettamente affidabile su hard-0
   (golden robustamente 0; input con varianza 0/-38/-56 su n=8, questo run a 0), e il flip
   non si fa per scelta, non per gate. n=1 appaiato NON basta a flippare: serve la
   frequenza bad-seed del path input dimensionata (differita in background, non bloccante).

## STATO: FLIP #1 SICURO (schedule-comparable, 0hard entrambi), NESSUNA adozione (misura soltanto)

Il FLIP #1 del census e' il piu' sicuro dei 5 (ESITO 1, sostituibile senza chiedere). Questo
A/B appaiato lo conferma end-to-end: input-derived e' feasible (0hard verificato) e
within-machine-comparable col golden-derived su ogni KPI di guardia. Default resta
source=golden; source=input resta opt-in; adozione ready-when-needed (a regime post-golden).
Nessun flip eseguito, nessun default toccato.

## Note
- L'A/B e' stato eseguito in due tempi: l'harness `ab-task449-mono-room.sh` (protocollo
  IDENTICO al 470) ha completato il CONTROL arm (99min, 2 tentativi, DONE+capture) ma e'
  stato interrotto (kill esterno) a meta' del TREATMENT. Il treatment e' stato ri-eseguito
  appaiato a mano (stessa macchina/sessione, `planner.sh prod` + PLANNER_JAVA_OPTS, profilo
  task449-on fresco, import HTTP 200, pre-assign COMPLETE, solve-staged 1 tentativo ~35min).
  Banner source=input confermato (1 occorrenza), banner assente nel control (0). Numeri
  presi dai raw `score.txt`/`kpi-*.txt`/`analysis.json`/DB di entrambi gli arm.
- Tentativi multi-start: control 2 (attempt 1 non ha chiuso hard-0 entro il budget, retry
  deterministico), treatment 1 (multi-start stop-at-first-0hard: 0-hard al primo). Anche
  questo e' varianza, non segnale (entrambi 0-hard finali).
- Phase-transition dumps OFF (entrambi gli arm); la conferma hard-0 viene da DB
  (`count(*)` unassigned=0) + analysis.json (nessuna negativeHard), non dai dump.
- Il control (-1271k) sotto il riferimento golden (-984k) e' il segnale piu' utile di
  questo A/B per Fedora: ricordare che un singolo run golden-derived NON e' il golden
  riferimento, e che la decisione di flip va presa sulla DISTRIBUZIONE (bad-seed frequency
  del path input), non su un punto. Lavoro: TASK-480 (N=3 procedure) e' gia' il veicolo;
  la frequenza bad-seed del path input resta da dimensionare (run in background).
