# VERDICT TASK-480 gate di indipendenza: DIVERSIFIED, PROCEED. 2026-08-02.

> Gate di indipendenza (research_multistart-vs-alternative_2026-08-02.md, finding 4a) eseguito a
> costo ZERO solve (~12s, seed reale). Risponde alla domanda: la cella @10 (mercoledi ore 10, slot
> del buco H17 di G11) e' CONSTRAINT-FORCED (tie-break-indipendente) o DIVERSIFICABILE? Se
> forced -> multi-start morto, TASK-482 Kempe-chain. Se diversificabile -> PROCEED al multi-start
> N-processi. **Risultato: DIVERSIFICABILE (5/5 profili distinti, 13/14 gruppi variano). PROCEED.**

## Domanda e metodo

Domanda: randomizzando i tie-break della COSTRUZIONE (GRASP-RCL: `startOffset` weekday + `nextContiguousSlot`
free-slot, seedati), il profilo di occupazione @10 del mercoledi cambia tra N=5 costruzioni indipendenti?

Metrica: per ciascuna costruzione, per ogni gruppo, quale docente/materia occupa la cella (Wed, slot 2 =
10:00-11:00); e il multiset dei docenti prenotati @10 (la firma di saturazione). 5 multiset distinti =
la cella e' tie-break-sensitive.

Metodo: template via `WeeklyTemplatePlanner.buildReference` sul seed REALE (incarichi.csv), GRASP off
(baseline) vs 5 seed GRASP. Zero solver, zero LS. Harness: `ConstructionIndependenceGateTest`
(gated `PLANNER_TEST_GATE=1`). Codice GRASP: `WeeklyTemplateBuilder` flag
`planner.construction.grasp.enabled` + `.seed` (default OFF = byte-identico).

Perche' template-only e' sufficiente: il repair-tail della costruzione puo' solo AGGIUNGERE lezioni @10
(riempie celle libere), mai rimuovere una lezione template @10. Quindi un template @10 saturo resta
saturo nella costruzione completa. Il profilo @10 del template e' il segnale di gate sufficiente e piu'
economico. Perche' input-only (no pin golden) e' conservativo per lo STOP: i pin golden aggiungono solo
vincoli (forzano la cella, non la diversificano), quindi se la cella NON diversifica con MENO vincoli
(input), e' vincolo-forzata a maggior ragione con i pin. L'A/B resta la ground truth sufficiente.

## Risultato (dai raw, TEST-...ConstructionIndependenceGateTest.xml)

Baseline (GRASP off) riproducibile (asserzione `baseline1==baseline2` verificata): 13 gruppi con @10
specifico, G14 sempre `<gap>`.

| seed | docenti prenotati @10 (Wed) | n. docenti |
|------|-----------------------------|------------|
| 1 | T02, T05, T07, T08, T09, T15, T17, T21, T23, T25, T26 | 11 |
| 2 | T02, T06, T07, T08, T13, T15, T18, T20, T23, T26 | 10 |
| 3 | T02, T06, T07, T08, T13, T14, T15, T21, T23, T25, T26 | 11 |
| 4 | T02, T05, T09, T13, T15, T18, T21, T23, T25 | 9 |
| 5 | T05, T06, T07, T11, T13, T15, T16, T21, T23, T25, T26 | 11 |

**SUMMARY: distinct teacher-multisets = 5/5 ; gruppi che variano il @10 = 13/14.**

## Segnale specifico bad-seed (G11)

I 10 docenti d'aula di G11 (diagnosi: {T05, T04, T11, T08, T06, T25,
T23, T21, T03, T02}) sono prenotati @10 in SOTTOINSIEMI DIVERSI per seed:
- seed=1 prenota @10 {T05, T08, T21, T23, T25, T02} -> LIBERI @10:
  T04, T11, T06, T03 (4 liberi).
- seed=4 prenota @10 {T05, T21, T23, T25, T02} -> LIBERI @10: T04,
  T11, T08, T06, T03 (5 liberi).

Costruzioni diverse lasciano liberi docenti diversi @10 -> la LS eredita bacini diversi -> in alcuni
bacini un docente libero @10 puo' tappare il buco di G11. In seed 1 e 2, G11 ha addirittura un
@10 `<gap>` gia' in costruzione. E' esattamente la precondizione che il portfolio DRAFT-005 refutato
NON aveva (costruzione+promotion identiche fra candidati -> stesso bacino -> convergenza).

## Verdetto

**DIVERSIFIED. La cella @10 e' tie-break-sensitive, NON constraint-forced. PROCEED al multi-start
N-processi (TASK-480 Fase 3).**

Caveat di fedelta' (onesta'): il gate misura il template sul seed INPUT (no pin golden). I pin golden
aggiungono vincoli, quindi la diversificazione sara' <= quella misurata, ma la cella NON e' forced
(diminuito con meno vincoli), quindi c'e' margine. L'A/B N-processi e' la ground truth: misura
P(almeno un run 0-hard) e wall-clock su bad seed. Se anche un solo run GRASP chiude H17 dove il run
deterministico non chiude, il multi-start e' adottato (come procedura operativa, non flip di default).

## Puntatori

- Harness: `src/test/java/com/school/planner/bootstrap/ConstructionIndependenceGateTest.java`
  (run: `PLANNER_TEST_GATE=1 ./scripts/core/test.sh -c ConstructionIndependenceGateTest`).
- Codice GRASP: `src/main/java/com/school/planner/solver/WeeklyTemplateBuilder.java`
  (`graspEnabled`/`graspSeed`/`newGraspRandom`, iniezione `startOffset` + `nextContiguousSlot`,
  default OFF byte-identico).
- Raw: `target/surefire-reports/TEST-com.school.planner.bootstrap.ConstructionIndependenceGateTest.xml`.
- Research: `claudedocs/research_multistart-vs-alternative_2026-08-02.md` (finding 4a).
