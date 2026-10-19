# Recon tecnico TASK-480 - multi-start vero da costruzioni indipendenti (2026-08-02)

> Scopo: raccogliere i fatti tecnici (repo + Timefold 2.3.0 community) che servono al design
> del TASK-480. Nessuna proposta qui, solo evidenza con file:riga o fonte precisa. Deliverable
> figlio di `backlog/tasks/task-480 - ...md` e del verdetto
> `claudedocs/spalmone-2026-27/task452-results/verdict-portfolio-n3-2026-08-02.md`.
>
> Metodo per l'evidenza Timefold: la versione usata dal progetto e' fissata in `pom.xml:16`
> (`<timefold-solver.version>2.3.0</timefold-solver.version>`). Ho estratto
> `~/.m2/repository/ai/timefold/solver/timefold-solver-core/2.3.0/timefold-solver-core-2.3.0-sources.jar`
> in `/tmp/tf230-src` e letto le classi direttamente: e' la fonte piu' precisa possibile perche' e'
> esattamente il jar che il progetto compila ed esegue (niente drift di versione tra doc
> online e codice reale). Le citazioni "jar 2.3.0" sotto puntano a quel sorgente; le citazioni
> "docs.timefold.ai" sono verificate via fetch diretto delle pagine.

---

## 1. La nostra fase di costruzione: deterministica? dove sono i tie-break?

**Si, e' deterministica al 100% a parita' di profilo DB** (stessi id di `StudentGroup`/`Course`/
`Lesson`/`Teacher`, vedi sotto perche' questo e' vero). Non c'e' NESSUNA sorgente di casualita' nel
codice: nessun `java.util.Random`, nessuna chiamata a un selettore Timefold (che sarebbe
l'unico modo di ereditare il seed del solver, vedi paragrafo 2). Confermato per assenza:

```
grep -n "Random" DayClusteredConstructionPhase.java WeeklyTemplateBuilder.java WeeklyTemplatePlanner.java
-> nessun risultato
```

### Perche' le HashMap non introducono varianza inter-run
`groupByStudentGroup()` (`src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java:534-543`)
e `lessonsByCourse` dentro `placeLessonsRoundRobin` (linee 578-583) sono `HashMap` semplici, non
`TreeMap`/`LinkedHashMap`. In Java l'ordine di iterazione di una `HashMap` dipende da
`hashCode()` delle chiavi. Se `StudentGroup`/`Course` usassero l'hashCode di default
(identita'/indirizzo di memoria), l'ordine cambierebbe ad ogni JVM restart: la "trappola
classica" che i docs Timefold segnalano esplicitamente (vedi paragrafo 2, "Reproducible Environment
Mode"). Verificato che NON e' il caso nostro:

- `src/main/java/com/school/planner/domain/StudentGroup.java:103-115`: `equals`/`hashCode`
  basati su `id` (`id.hashCode()`, `Long.hashCode()`).
- `src/main/java/com/school/planner/domain/Course.java:106-114`: idem.
- `src/main/java/com/school/planner/domain/Lesson.java:476-484`: idem.

Quindi l'iterazione delle `HashMap` e' deterministica finche' gli `id` (assegnati dal DB
all'inserimento, stabili per un profilo gia' seedato) restano gli stessi. Su un RESEED del
profilo (nuovi autoincrement) l'ordine potrebbe cambiare, ma non e' rilevante per un multi-start che
gira sullo STESSO profilo/DB.

### Tie-break espliciti (i punti di iniezione candidati, con evidenza)

| # | File:riga | Cosa ordina | Tie-break attuale |
|---|-----------|-------------|--------------------|
| 1 | `WeeklyTemplateBuilder.java:250-254` | Elenco `courses` prima del placement | `weeklyTarget` desc, poi nome gruppo, poi subject, poi nome docente (chiave SEMANTICA, non `course.id`, il commento a riga 243-249 dice che l'id avrebbe dato 75-81% varianza run-to-run su reseed) |
| 2 | `WeeklyTemplateBuilder.java:269-272` | `teacherOrder` (ordine di elaborazione docenti, "hardest-constrained-teacher-first") | `freeCapacity(teacherOccupiedBySlotKey)` asc, poi `teacherId` asc |
| 3 | `WeeklyTemplateBuilder.java:334-335` | `startOffset` = punto di partenza della scansione weekday per un corso | `Math.floorMod((group.name+"|"+subject).hashCode(), 5)`: hash STRING, stabile per gli stessi nomi, MAI randomizzato |
| 4 | `WeeklyTemplateBuilder.java:727-762` (`tryPlaceChunk`) | Ordine dei 3 pass (room-tag pref -> theory-spread pref -> first-fit) | Deterministico, gated da 2 flag booleani (`roomTagAware`, `theorySpread`) |
| 5 | `WeeklyTemplateBuilder.java:930-943` (`nextContiguousSlot`) | Quale slot libero nella finestra vince | Il PRIMO libero scandendo dal minimo (bottom-up), mai randomizzato |
| 6 | `WeeklyTemplateBuilder.java:624-634` (`reserveFirstHourBlock`) | Tra i candidati di un gruppo, chi vince il blocco slot0-1 | Preferenza per il docente-ancora golden (`wantMatches`), poi chiave semantica |
| 7 | `DayClusteredConstructionPhase.java:1073-1074` | Ordine dei gruppi nello stamp cronologico | `groups.sort(Comparator.comparing(g -> g.name))`: alfabetico, non l'HashMap grezza |
| 8 | `DayClusteredConstructionPhase.java:246-248, 264-273` | Ordine dei gruppi nella coda round-robin (repair tail) | **HashMap grezza** (`groupedByStudentGroup.entrySet()`), NON risortata per nome: diverge dallo stamp (punto 7) |
| 9 | `DayClusteredConstructionPhase.java:807-878` (`findBestLessonForSlot`) | Quale lezione riempie uno slot nel repair tail | `priority = 10000 - daysSinceLastPlacement - course.totalHours/10`; a PARITA' di priority vince il PRIMO trovato iterando `lessonsByCourse.entrySet()` (HashMap, ordine per id-hash) |
| 10 | `WeeklyTemplateBuilder.java:1000-1066` (`allocateLargestRemainder`) | Quali corsi ricevono l'ora extra/il taglio nell'arrotondamento Hamilton | Ordinamento per resto decrescente/crescente, tie-break su chiave semantica |

**Nota architetturale che il verdetto n=3 gia' dimostra empiricamente**: il portfolio DRAFT-005
oggi esistente (`ScheduleService.java:1308-1360`, vedi paragrafo 3) NON tocca nessuno di questi 10 punti:
rilancia solo lo stage B-TIMESLOT (LS) a partire dal MEDESIMO output di stage A (costruzione) +
PROMOTION gia' persistito su DB (`loadSchedule(true)` in `runTimeslotSolveStage`, riga 1339,
ricarica lo STESSO schedule per ogni candidato). Ecco perche' i candidati convergono: la
costruzione (i 10 punti sopra) e' identica byte-per-byte tra i round, quindi i 7504 pin promossi
sono identici, e la LS parte dallo stesso punto. Un vero multi-start deve variare uno o piu' dei
10 punti PRIMA della promotion, non ripetere solo la LS a valle.

---

## 2. randomSeed / environmentMode: tocca la fase di costruzione custom?

**No, per costruzione dell'API, verificato sull'interfaccia, non per inferenza.**

- `docs.timefold.ai` (fetch diretto, pagina redirect risolta su
  `running-timefold-solver/library/library-integration`, sezione "Random Instance and
  Reproducibility"): "A solver has a single Random instance... The environment mode
  influences the seed of that Random instance." Il seed alimenta UN SOLO `Random`/`RandomSource`
  per tutto il solver, condiviso dai selettori euristici (CH/LS built-in Timefold).
- Stesso doc, sezione "Reproducible Environment Mode": "your application might still not be
  fully reproducible because of certain factors like use of a HashSet (or another Collection
  with undefined iteration order between JVM runs) for collections of planning entities or
  planning values", la stessa trappola che ho escluso al paragrafo 1 (i nostri `equals`/`hashCode` sono
  id-based, non identity-based).
- **Prova diretta sul jar 2.3.0**: `ai.timefold.solver.core.api.solver.phase.PhaseCommandContext`
  (`/tmp/tf230-src/ai/timefold/solver/core/api/solver/phase/PhaseCommandContext.java`, 108 righe
  lette per intero): l'INTERA interfaccia che una `PhaseCommand` custom riceve (il nostro
  `DayClusteredConstructionPhase implements PhaseCommand<Solution_>`) espone solo:
  `getSolutionMetaModel()`, `getWorkingSolution()`, `isPhaseTerminated()`, `execute(Move)`,
  `executeAndCalculateScore(Move)`, `executeTemporarily(...)`, `lookUpWorkingObject(...)`.
  **Nessun metodo per accedere al `Random`/seed del solver.** Il `RandomSource` vive in
  `SolverScope` (`/tmp/tf230-src/ai/timefold/solver/core/impl/solver/scope/SolverScope.java:53,146-151`,
  campo privato `workingRandom` con getter/setter), una classe INTERNA (`impl.solver.scope`) mai
  esposta a un `PhaseCommand`.
- Dove il seed viene effettivamente consumato:
  `ai.timefold.solver.core.impl.solver.DefaultSolverFactory` (jar 2.3.0, righe 217-223):
  ```java
  var randomSeed_ = solverConfig.getRandomSeed();
  if (randomSeed_ == null && environmentMode_ != EnvironmentMode.NON_REPRODUCIBLE) {
      randomSeed_ = DEFAULT_RANDOM_SEED;
  } else if (randomSeed_ == null) {
      randomSeed_ = RandomGenerator.getDefault().nextLong();
  }
  return DefaultRandomSource.seededSupplier(randomSeed_);
  ```
  Questo `RandomSource` alimenta solo i selettori euristici Timefold (CH/LS), MAI una
  `PhaseCommand` custom, che non ha alcun canale per riceverlo.

**Conclusione verificata**: `<randomSeed>`/`environmentMode` NON possono influenzare
`DayClusteredConstructionPhase`/`WeeklyTemplateBuilder`/`WeeklyTemplatePlanner` nemmeno in
teoria: non e' un'ipotesi su "come Timefold di solito si comporta", e' un fatto sull'API che il
nostro codice compila contro (2.3.0). Coerente con quanto il codice del portfolio DRAFT-005
gia' annota (`ScheduleService.java:1335-1337`): "under REPRODUCIBLE the seed binds
(deterministic, reproducible); under NON_REPRODUCIBLE (production default) Timefold uses a
fresh seed each run so this is a no-op", riferito pero' solo alla LS di stage B-TIMESLOT, mai
alla costruzione.

Config attuale del progetto: `src/main/resources/application.properties:11`
(`quarkus.timefold.solver.environment-mode=NON_REPRODUCIBLE` in produzione) e riga 10
(`%dev...=STEP_ASSERT` in dev). Nessun `randomSeed` fisso nel `solverConfig.xml`
(`grep -n "randomSeed" src/main/resources/solverConfig.xml` -> nessun risultato): il portfolio
DRAFT-005 imposta un seed per-candidato via `config.setRandomSeed((long) i + 1L)`
(`ScheduleService.java:1338`), che sotto `NON_REPRODUCIBLE` e' un no-op per definizione (il
codice stesso lo commenta).

---

## 3. SolverManager community: N solve indipendenti in parallelo? Limiti per il nostro caso.

### Community vs Enterprise, con la prova del gating (non solo il doc)
- `SolverManagerConfig.parallelSolverCount` (jar 2.3.0,
  `ai/timefold/solver/core/config/solver/SolverManagerConfig.java:26,74-84`): **community**, nessun
  controllo di licenza. Default `AUTO` = meta' dei core disponibili
  (`resolveParallelSolverCountAutomatically`, stesso file). Meccanismo:
  `ai.timefold.solver.core.impl.solver.DefaultSolverManager` riga 57:
  `solverThreadPool = Executors.newFixedThreadPool(parallelSolverCount, threadFactory);`, un
  semplice `ExecutorService` Java, un thread per Solver. Sottomettere 5 problemi con
  `parallelSolverCount=4` fa partire i primi 4 subito, il quinto quando uno finisce (confermato
  anche da `docs.timefold.ai/.../running-timefold-solver/library/library-integration`, stesso
  esempio testuale).
- `SolverConfig.moveThreadCount` (jar 2.3.0, `SolverConfig.java:217,351-357`): il CAMPO esiste
  nel core (compila senza enterprise sul classpath), ma il suo USO a runtime e' gated a licenza.
  Prova diretta: `ai.timefold.solver.core.impl.localsearch.DefaultLocalSearchPhaseFactory`
  (jar 2.3.0, righe 169-173):
  ```java
  var decider = moveThreadCount == null
      ? new LocalSearchDecider<>(...)   // community path
      : TimefoldSolverEnterpriseService.loadOrFail(Feature.MULTITHREADED_SOLVING)
              .buildLocalSearch(moveThreadCount, ...);
  ```
  `TimefoldSolverEnterpriseService.loadOrFail` (jar 2.3.0,
  `ai/timefold/solver/core/enterprise/TimefoldSolverEnterpriseService.java`) fa
  `Class.forName("ai.timefold.solver.enterprise.core.DefaultTimefoldSolverEnterpriseService")`
  via reflection e lancia `IllegalStateException` con messaggio "No valid Timefold License was
  found... Maybe add the ai.timefold.solver.enterprise:timefold-solver-enterprise-core
  dependency" se la classe/licenza manca. `Feature.MULTITHREADED_SOLVING` e' uno degli 8 valori
  dell'enum `Feature` nello stesso file (insieme a `PARTITIONED_SEARCH`, `NEARBY_SELECTION`,
  `AUTOMATIC_NODE_SHARING`, `MULTISTAGE_MOVE`, `CONSTRAINT_PROFILING`, `SCORE_ANALYSIS`,
  `RECOMMENDATIONS`): `parallelSolverCount`/`SolverManager` NON compare in questa lista, a
  riprova che e' genuinamente community.
- `pom.xml` del progetto: nessuna dipendenza `timefold-solver-enterprise*` (solo `core`,
  `quarkus`, `quarkus-jackson`, `benchmark`), confermato che oggi il progetto NON ha la
  licenza enterprise, quindi `moveThreadCount` non e' utilizzabile, `SolverManager`/
  `parallelSolverCount` si'.

### Perche' SolverManager NON e' il meccanismo giusto per il NOSTRO ciclo staged
`ScheduleService.runStagedSolver` (`src/main/java/com/school/planner/service/ScheduleService.java:1085-1094`)
usa deliberatamente un `SolverFactory` ad-hoc (`SolverFactory.<SchoolSchedule>create(config).buildSolver()`
+ `solver.solve(problem)`), MAI `solverManager` iniettato (campo a riga 107). Commento a riga
1071-1074: "never on the injected SolverManager, which the staged cycle stopped using when
stage B was split in two (a SolverManager solve is one whole-XML pipeline; this cycle needs
three different phase subsets, in an order it interleaves with DB work of its own)".
`SolverManager.solve(problemId, problem)` risolve UN problema con UNA config XML intera; il
nostro ciclo staged e' A-CONSTRUCTION -> PROMOTION (scrittura DB) -> B-TIMESLOT -> RELEASE ->
HoleRepair/AnchorRepair/SurplusRebalance (Java puro, niente Solver) -> B-ROOM: `SolverManager`
non ha un concetto di "fermati a meta' XML, fai lavoro DB, riprendi con un'altra XML".

### Il vincolo che il verdetto e il task non avevano ancora isolato: STATO STATICO condiviso in-JVM
`SchoolSchedule` pubblica **oltre 20 campi `private static volatile`** che i vincoli leggono
DURANTE il solve (verificato: `grep -n "private static volatile" SchoolSchedule.java` produce
23 righe, esempi: `pinnedLessonTimeslotsByGroupStatic` riga 81, `weeklyTemplateDeviationWeightByCourseStatic`
riga 266, il riferimento M32, `promotedLessonIdsStatic` riga 293, `roomCapacityByTagStatic`
riga 57, `dailyActivityQuotasByGroupStatic` riga 216, `cadenceBurstCapStatic` riga 308). Questi
campi sono `static`, cioe' **condivisi da OGNI thread della stessa JVM**, non per-istanza-Solver:
`refreshSolverStatics` li ripubblica prima di ogni stage e `SchoolScheduleConstraintProvider` li
legge durante la solve. Due cicli staged eseguiti in thread PARALLELI nella STESSA JVM
leggerebbero/scriverebbero questi campi in corsa: il template M32 (o la quota netta M20/M33, o
la capacita' tag M-TC) di un candidato "vincerebbe" e contaminerebbe l'altro candidato a meta'
solve, corruzione silente dello score, non un crash rumoroso.

In aggiunta, ogni stage del ciclo PERSISTE su DB (`saveSchedule`, `saveFinalSolution`,
`Lesson.update(...)` in `unassignUnpinnedPlacements`) sulle STESSE righe `lesson`/`course` del
profilo: N cicli paralleli sullo stesso profilo Postgres si scriverebbero a vicenda addosso
indipendentemente dalla JVM.

**Conseguenza per il design (fatto, non raccomandazione)**: un multi-start "vero" con
costruzioni indipendenti eseguito IN-PROCESS (N thread nella stessa JVM Quarkus) non e' sicuro
con l'architettura attuale (stato statico condiviso + un solo profilo DB). Le due vie
compatibili con questi vincoli sono: (a) **sequenziale, un solo processo** (il ciclo staged gira
N volte in fila, come gia' fa DRAFT-005 per la sola LS), oppure (b) **N processi OS separati**,
ciascuno con la propria JVM (stato statico non condiviso per definizione) e il proprio profilo
DB (o un profilo dedicato a run, per evitare la corsa sulle righe `lesson`). L'opzione (b) e'
quella che il task chiama "il vero replacement community dell'enterprise multithreaded": e'
compatibile con `parallelSolverCount`/`SolverManager` SOLO se si riscrivesse il ciclo per girare
dentro un singolo `Solver.solve()` (non e' il caso oggi), altrimenti e' pura orchestrazione a
livello di script/processo (fuori da Timefold stesso).

### Non verificato
- Numero di core / RAM del laptop usato per i benchmark: non ho accesso diretto al laptop da questa
  sessione. `scripts/benchmark/laptop-RUNBOOK.md` non riporta specifiche
  hardware. Servirebbe `ssh laptop 'sysctl -n hw.ncpu hw.memsize'` per dimensionare quanti processi
  da `-Xmx2g` (il valore standard per un solve live, CLAUDE.md sezione MAVEN_OPTS) stanno in RAM
  in parallelo prima di andare in swap.

---

## 4. Ruin & Recreate in 2.3.0 community: esiste? come si configura mirato?

**Si', e' community** (nessun gate `TimefoldSolverEnterpriseService` sul suo path, a differenza di
`moveThreadCount`/nearby selection/partitioned search, vedi enum `Feature` al paragrafo 3, che non lo
elenca). Prova diretta: le classi vivono tutte in `timefold-solver-core`, non in un artifact
enterprise separato:
```
ai/timefold/solver/core/impl/heuristic/selector/move/generic/RuinRecreateMoveSelector.java
ai/timefold/solver/core/impl/heuristic/selector/move/generic/RuinRecreateMoveSelectorFactory.java
ai/timefold/solver/core/impl/heuristic/selector/move/generic/SelectorBasedRuinRecreateMove.java
ai/timefold/solver/core/config/heuristic/selector/move/generic/RuinRecreateMoveSelectorConfig.java
```
(tutte estratte da `timefold-solver-core-2.3.0-sources.jar`, non da un jar enterprise). Esiste
anche una variante per planning-list-variable:
`ai/timefold/solver/core/config/heuristic/selector/move/generic/list/ListRuinRecreateMoveSelectorConfig.java`
(non rilevante per noi: `Lesson.timeslot`/`room` sono variabili singole, non liste).

### Configurazione (dal config XML element, jar 2.3.0)
`RuinRecreateMoveSelectorConfig.java` (righe 14-40): elemento XML
`XML_ELEMENT_NAME = "ruinRecreateMoveSelector"`, campi disponibili:
- `minimumRuinedCount` / `maximumRuinedCount` (default interni 5/20, "Determined by
  benchmarking on multiple datasets", commento riga 25-27)
- `minimumRuinedPercentage` / `maximumRuinedPercentage`
- `entitySelectorConfig` (annotato `@XmlElement(name = "entitySelector")`)
- `variableName`

Doc (`docs.timefold.ai/.../optimization-algorithms/move-selector-reference`, fetch diretto):
"To enable it, add the following to the localSearch section of the solver configuration:
`<ruinRecreateMoveSelector/>`"; e "The entitySelector property specifies which entity should
be selected... In a model with multiple entities, this property is required, or the solver
will fail because it cannot automatically deduce the entity."

### Come si mira (filtra) a entita' specifiche
Il nested `<entitySelector>` e' un `EntitySelectorConfig` normale, che supporta `filterClass`
(prova diretta, jar 2.3.0, `ai/timefold/solver/core/config/heuristic/selector/entity/EntitySelectorConfig.java:33,69,150,153-154`):
campo `filterClass` di tipo `Class<? extends SelectionFilter>`, stesso meccanismo usato altrove
nel framework per restringere quali entita' un selettore considera. Quindi un ruin&recreate
mirato SOLO a un sottoinsieme di `Lesson` (es. solo le lezioni di un certo gruppo/giorno) e'
configurabile via `<entitySelector><filterClass>...</filterClass></entitySelector>` dentro
`<ruinRecreateMoveSelector>`, senza bisogno di codice enterprise.

### Costo prestazionale (dal doc, non dal nostro benchmark)
Il doc segnala esplicitamente il costo: "Since the RuinRecreateMove is a coarse-grained move,
it is expensive and can slow the solver down significantly" e raccomanda di avvolgerlo in un
`unionMoveSelector` con `fixedProbabilityWeight` basso (es. 1.0 contro 100.0 delle mosse
fine-grained) per limitarne la frequenza. Nessun dato nostro (benchmark locale) su questo costo
per il modello planner: **NON VERIFICATO su dati nostri**, solo dalla doc generale Timefold.

---

## Punti di iniezione candidati (riepilogo secco file:riga)

| File:riga | Tipo di variazione possibile |
|-----------|-------------------------------|
| `WeeklyTemplateBuilder.java:250-254` | Perturbare/randomizzare il tie-break dell'ordine `courses` (oggi: target desc, poi nome semantico) |
| `WeeklyTemplateBuilder.java:269-272` | Perturbare l'ordine `teacherOrder` (oggi: capacita' libera asc, poi id) |
| `WeeklyTemplateBuilder.java:334-335` | Iniettare un "seed di run" nella `rotationKey` prima dell'hash, per cambiare `startOffset` per ogni run indipendente in modo deterministico-per-seed |
| `WeeklyTemplateBuilder.java:727-762` (`tryPlaceChunk`) | Aggiungere un 4 pass/variante di preferenza (oggi 3 pass: room-tag, theory-spread, first-fit) attivabile per-run |
| `WeeklyTemplateBuilder.java:930-943` (`nextContiguousSlot`) | Scegliere uno slot NON il primo libero (es. random tra i liberi, o l'ultimo) per una porzione di run |
| `DayClusteredConstructionPhase.java:1073-1074` vs `:246-248` | Disallineamento GIA' ESISTENTE tra stamp (nome-ordinato) e repair tail (HashMap grezza): un candidato di variazione a costo zero e' forzare/rimuovere questo disallineamento in modo controllato per run diversi |
| `DayClusteredConstructionPhase.java:807-878` (`findBestLessonForSlot`) | Il tie-break a parita' di `priority` (oggi: primo trovato in ordine HashMap) e' un punto naturale per un secondo criterio esplicito variabile per run |
| `WeeklyTemplateBuilder.java:1000-1066` (`allocateLargestRemainder`) | Tie-break sui resti Hamilton, variabile per run |
| `ScheduleService.java:1308-1360` (`runTimeslotSolveStage`) | Il punto di orchestrazione ESISTENTE (DRAFT-005): oggi ricarica lo stesso `loadSchedule(true)` per ogni candidato; per costruzioni indipendenti va spostato PRIMA di `promoteTemplatePins()`/`runConstructionStage`, non dopo |
| `src/main/resources/solverConfig.xml` (sezione `localSearch` di stage B-TIMESLOT) | Aggiungere `<ruinRecreateMoveSelector>` (community, paragrafo 4) come mossa aggiuntiva nella LS: leva DIVERSA dal multi-start (agisce dentro una singola run, non tra run indipendenti) |

---

## Riepilogo (5 righe)

La costruzione (`DayClusteredConstructionPhase`/`WeeklyTemplateBuilder`/`WeeklyTemplatePlanner`)
e' deterministica al 100% sullo stesso profilo (id-based hashCode, nessun `Random`), con ~10
punti di tie-break espliciti che sono gli unici canali per iniettare varianza vera (i piu'
promettenti: `startOffset` hash-based riga 334-335, `teacherOrder` riga 269-272, il
disallineamento stamp/repair-tail riga 1073 vs 246). `randomSeed`/`environmentMode` non possono
toccare questi punti per costruzione dell'API Timefold 2.3.0 (`PhaseCommandContext` non espone
alcun `Random`): il portfolio DRAFT-005 esistente infatti varia solo la LS di stage B-TIMESLOT a
valle di una costruzione+PROMOTION identica, il che spiega esattamente la convergenza osservata
nel verdetto n=3. `SolverManager`/`parallelSolverCount` sono community e userebbero un
`ExecutorService` standard, ma non calzano sul nostro ciclo staged (multi-stage, non un solo
`solve()`) e soprattutto la classe `SchoolSchedule` pubblica oltre 20 campi `static volatile` letti
dai vincoli durante il solve: N cicli paralleli nella STESSA JVM correrebbero su quello stato
condiviso, quindi la via community sicura e' sequenziale-in-processo o N processi OS separati
(ognuno con la propria JVM/profilo DB), non N thread. Ruin&Recreate e' community
(`ruinRecreateMoveSelector`, nessun gate enterprise) e supporta targeting via
`entitySelector/filterClass`, ma e' una leva diversa (dentro una singola LS) dal multi-start
(tra run indipendenti) e il doc stesso avverte che e' costosa senza wrapping probabilistico.
