# HANDOFF 2026-08-02 alba (zai) - n=3 portfolio REFUTATO + ExactFinisher AC#2/#3 consegnato

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity`. **DB = run8 (-56hard,
> ~28 unassigned) su `planner-data-production`** (12494 lessons, 2882 pinned). **App KILLED**
> (kill-app alle 01:21 per il build DoD). Container `planner-pg` up. Verdetti dai raw.
> Regole standing invariate.

## MANDATO NOTTE - TUTTO CHIUSO

### (1) Verify Fedora (sessione precedente) - FATTO
run6 0hard CONFIRMED dai raw (`score.txt` 0hard, DB 12494/0 unassigned, portfolio genuine
2-candidate). A1 T20 in cassa CONFIRMED (commit `98efafc5`, T20 id 16 esiste, fresh reseed
safe). H1 meccanicamente corroborato, statistica pending n=3 (ora raccolta, vedi sotto).

### (2) run7 + run8 portfolio size=2 + source=input - CATTURATI, n=3 COMPLETO
- run7: **-36hard, 15 unassigned** (3 round B-TIMESLOT, pickBest -2137/-2141/-2145). FAIL.
- run8: **-56hard, ~28 unassigned** (3 round, pickBest -2091/-2094/-2098). FAIL.
- Capture: `task452-results/verify-source-input-run{7,8}/` (score, portfolio-candidates, KPI, confronto).
- **Verdetto n=3** (`task452-results/verdict-portfolio-n3-2026-08-02.md`): P(pass)=1/3, P(fail)=2/3.

### (3) FINDING: portfolio size=2 NON riduce la varianza (candidati convergenti) - REFUTATA la teoria
In ogni round i 2 candidati del portfolio convergono a hard near-identical (-2137/-2137,
-2141/-2141, -2091/-2091...) perche' partono dalla stessa costruzione + PROMOTION (7504 pin
identici) e la LS converge allo stesso ottimo locale. I candidati NON sono indipendenti, quindi
`1-(1-p)^N` (procedura `procedura-portfolio-path-input-2026-08-01.md`) NON si applica.
Portfolio ~ single solve (n=3 portfolio 1/3 pass vs run4/run5 single 1/2 pass: nessun
miglioramento). Costo ~3x B-TIMESLOT non giustificato. **NON adottare.**

### (4) ExactFinisher TASK-478 AC#2 + AC#3 - CONSEGNATO (flag OFF, 5/5 test PASS)
- **AC#2 design**: `task478-results/ac2-exact-finisher-design-2026-08-01.md` (insertion
  post-SurplusRebalance/pre-B-ROOM, contratto improve-or-noop+revert TASK-473, gate chain,
  depth 0..1, depth<=2 target futuro differito).
- **AC#3 prototype**: `solver/ExactFinisher.java` (depth 0..1, MAX_K=30, MAX_CANDIDATES=10000,
  MCV, enumeration reusing `enumeratePairsRecursive` form) + `ExactFinisherTest` (5 test).
  Flag `planner.exact-finisher.enabled` default OFF (`SchoolSchedule.isExactFinisherEnabled`,
  copia `isTagCapacityEnabled`). Runner `runExactFinisher` in ScheduleService (mirror
  SurplusRebalance) + banner in `refreshSolverStatics`.
- **Bug fix (code review)**: `findRelocationCell` usava il fresh-placement gate per una
  relocation (H17 double-count same-day); ora usa `BoxRepair.isLegalRelocation` (resasi
  package-private) con `refiller` param. Depth-2 differito (O1 arrival a cella O2-occupata
  richiede gate combinato bump+relocation non in BoxRepair).
- **DoD gate**: `test.sh -c ExactFinisherTest` EXIT=0, BUILD SUCCESS (compila TUTTO), 5/5 PASS
  (include depth-1 regression `depth1RejectsSameDayRelocationThatWidensH17`). Full `test.sh`
  resta gate laptop adozione (AC#4). `/simplify` + `/code-review high` formali: da fare (review
  adversariale gia' fatta: 1 bug found+fixed, 8 suspect cleared).

## VERDETTO NOTTE (dai raw)
1. **Il path source=input ha hard-0 PROBABILISTICO al ~33-50%, e il portfolio NON lo doma**
   (candidati convergenti). La cura e' DETERMINISTICA: ExactFinisher (enumera il residuo,
   non soggetto all'ottimo locale). AC#1 aveva mostrato celle depth-0 abbondanti sui residui
   run5; run7/run8 (15 e ~28 unassigned, stessa classe T08/T19) confermano il residuo
   e' collocabile.
2. **ExactFinisher AC#2/#3 consegnato e verificato** (compila, 5/5 test, bug relocation fixato).
   L'A/B (AC#4) e' ora la PRIORITA' per il flip m-13: ExactFinisher ON sui residui run7/run8
   dovrebbe chiuderli deterministicamente.
3. **Nessuna adozione**: ExactFinisher resta flag OFF; portfolio resta DRAFT-005 (ora refutato,
   non adottare). A1 T20 resta l'unica adozione (autorizzata maintainer).

## STATO RUN / APP / INFRA
- **App KILLED** (kill-app 01:21 per il build DoD). Per ripartire: `java -jar` con i flag
  desiderati (vedi procedura, MA portfolio non serve). Per testare ExactFinisher: aggiungere
  `-Dplanner.exact-finisher.enabled=true`.
- **DB = run8 (-56hard)** su `planner-data-production`. Per run nuovo: reset + import-calendario
  + pre-assign + solve-staged (no fresh reseed; T20 LabAssistant solo a fresh reseed).
- Container `planner-pg` up. Jar `target/quarkus-app/quarkus-run.jar` = HEAD pre-ExactFinisher
  (ExactFinisher e' nel sorgente, compilato nei test ma l'app lanciata va rebuild-ata per
  includerlo).

## PROSSIMI PASSI (lavoro aperto)
1. **ExactFinisher A/B (AC#4)**: rebuild jar con ExactFinisher, lanciare sui residui run7/run8
   (import run7/run8 export, solve-staged con `-Dplanner.exact-finisher.enabled=true`), verificare
   chiude il residuo a 0hard deterministico. E' la prova di valore e la priorita' per il flip.
2. **Correggere `procedura-portfolio-path-input-2026-08-01.md`**: la tabella `1-(1-p)^N` e'
   refutata (n=3 empirico 33% vs teorico 87,5%); aggiungere banner REFUTATO (puntatore al
   verdict). Backlog: TASK-452 + TASK-476.
3. **Backlog**: spuntare TASK-478 AC#2/#3 (DONE), lasciare AC#4 aperto; commentare TASK-452 col
   n=3 (portfolio refutato, ExactFinisher priorita').
4. **Full `test.sh`** come gate laptop prima dell'A/B ExactFinisher (AC#4).
5. **Follow-up cheap**: fix javadoc stale `loadGoldenCoPresenceDepth` (TASK-452 #7); depth-2
   ExactFinisher (gate combinato bump+relocation + test) se l'A/B mostra depth-0/1 insufficiente.

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/verdict-portfolio-n3-2026-08-02.md` (n=3) +
  `task478-results/ac2-exact-finisher-design-2026-08-01.md` (AC#2) + `ac1-feasibility-*.md` (AC#1).
- Capture: `task452-results/verify-source-input-run{6,7,8}/`.
- Commit notturni: `98efafc5` (A1 T20), poi questo handoff integra run7/run8 + ExactFinisher AC#2/#3.
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS non MAVEN_OPTS), presidiio con poll sincroni (la shell in background NON
  risveglia), DoD obbligatoria su codice solver adottato, handoff+/clear a ~450k.

## QUADRO ALBA
- **Obiettivo mandato**: n=3 statistica portfolio + ExactFinisher AC#2/#3. **TUTTO CHIUSO.**
- **Sorpresa chiave (negativa ma utile)**: il portfolio multi-start NON aiuta (candidati
  convergenti). Riorienta il flip m-13 sull'ExactFinisher deterministico, non sul portfolio.
- **ExactFinisher**: prototipo depth 0..1 funzionante (5/5 test), bug relocation fixato, flag OFF.
  Pronto per l'A/B (AC#4), che diventa la priorita'.
- **Contesto**: notevole ma nei limiti. Handoff pulito; prossimo /clear riparte da qui.
