# HANDOFF 2026-08-02 alba5b (zai). TASK-479 Done + TASK-481 fork al decisore.

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity`, tutto pushato
> (`0515d1ac`). **App UP** (prod, `java -jar`, jar = 15b2e0ec con EF ON via -D; il flip default-ON
> conta solo senza -D). **DB = A/B run2 finale (0hard)** su `planner-data-production`. Container
> `planner-pg` up. Verdetti dai raw. Regole standing invariate. **maintainer sveglio/attivo.**

## COSA E' SUCCESSO QUESTA SESSIONE (alba5b)

### 1. Verifica EF (mandato iniziale): in cassa e corretto
- Codice committed+pushed (`40d257ae`/`5164d377`): opt-out `isExactFinisherEnabled`, knob
  `DEFAULT_MAX_CANDIDATES=1000000`, banner, test 963/0/16. EF ORTOGONALE agli H17 (analysis.json
  bad-seed: `H17 -6hard/6matches`, H0=0 via EF 18/18). Confermato.

### 2. TASK-481 (bloccante H17): FERMO al fork, decisione di Fedora/maintainer
- **CORREZIONE FATTUALE alla diagnosi alba4**: diceva "no metaheuristic, hill-climbing default,
  change+swap only; cura = aggiungi tabu/late-acceptance". FALSO. `solverConfig.xml` Phase 2 ha gia'
  `<acceptor><entityTabuSize>7>` (tabu), `<forager><acceptedCountLimit>1000>`, e 4 selettori
  (change/swap/pillarChange/pillarSwap). Il solver staged B-TIMESLOT carica quell'XML
  (`ScheduleService.timeslotPhasesConfig` via `createFromXmlResource`). La cura "aggiungi
  metaeuristica" e' doppiamente falsa.
- **Spike offline** `H17HoleCloseDiscriminatorTest` (gated `PLANNER_TEST_H17_HOLE=1`, export bad-seed
  fedele H1/H3=0 H17=-6 exact): 1-move fill 0/6, 2-move evict+fill 0/6. Cross-check raw: i 10 docenti
  d'aula di G11 sono tutti occupati @10 ogni mercoledi' del buco; i 3 liberi (T12/T22=LAB,
  T24=sparse) hanno @10 fuori VRP. Cella @10 saturata: buco HARD-FORCED nel vicinato 1/2-mosse del
  bad-seed. Chiuderlo richiede cascata cross-gruppo a 3+ mosse (libera un docente @10 + riempi + backfill).
- **PROPOSTA con fork A/B/C/D** in
  `claudedocs/spalmone-2026-27/task481-results/PROPOSTA-cura-h17-2026-08-02.md`. La scelta dipende dal
  requisito di prodotto: **singolo run 0-hard deterministico (Opz A, composition-aware closer depth
  2-3, RACCOMANDATA) vs multi-start accettabile (Opz C, defer a TASK-480)**. Fermo in attesa di
  decisione (direttiva: fermati alla proposta se grosso/ambiguo). Commit `1fd243e1`.
- **Record correction PENDING** (a chiusura, ovunque si vada): `CLAUDE.md` sezione RATIFICA TASK-478 e
  `task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md` ripetono l'errore "no metaheuristic";
  da correggere (gia' registrato in PROPOSTA sezione 5 + note TASK-481).

### 3. TASK-479 (secondario, in attesa del fork): DONE
- `claudedocs/orchestration-layer-architecture.md` (309 righe, 5 sezioni) + riga nella Documentation
  Map di PROJECT_INDEX.md + blocco RELATED in `solver-phases.md`. Commit `0515d1ac`. Draft delegato a
  subagent Sonnet, ingestion verificata (agent-data-verification: citazioni spot-checkate vs sorgente,
  scope clean, 0 em-dash). Spine: tabu+pillar gia' attivi in Phase 2, "aggiungi metaeuristica" non e'
  cura universale. Backlog TASK-479 = Done (AC#1/#2/#3 spuntati; DoD docs-equivalent nel finalSummary).

## PUNTI DI RIPRESA
- **TASK-481 (PRIORITA'):** leggere `task481-results/PROPOSTA-cura-h17-2026-08-02.md`, decidere il fork
  (A vs C, vedi quadro li'), poi implementare l'opzione scelta con catena localizzata + DoD + A/B
  (path input 0-hard pieno su n>=2 incl. bad seed). Note + commento #1 nel task TASK-481.
- **TASK-479:** chiuso. Solo se si vuole una review piu' stretta, skeptic Opus sulle citazioni del doc.
- **Record correction CLAUDE.md/diagnosi:** da fare a chiusura TASK-481.
- Handoff precedente (riferimento): `handoff-2026-08-02-alba5-ratifica-ef-default-on.md` (ratifica EF).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar` (PLANNER_JAVA_OPTS),
  rebuild prima di run misurati, handoff+/clear a ~450k, mai fermo, DoD obbligatoria su codice solver,
  default source resta golden.
