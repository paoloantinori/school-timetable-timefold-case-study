# TASK-482: Kempe-chain move selector. Design + offline spike. 2026-08-03.

> Sessione alba10 (zai). READ-ONLY design + de-risk per la specifica originale (mandate
> "read-only design + de-risk work for an overnight autonomous run"). Nessuna modifica a codice
> di produzione: solo il nuovo test `KempeChainH17DiscriminatorTest` e questo doc. Metodo di
> verifica API: jar Timefold 2.3.0 estratto in `/tmp/tf230-kempe`, firme lette con `javap` sul
> bytecode reale (non memoria), come nella recon TASK-480.

## TL;DR (per il decisore)

1. **VERDETTO SPIKE: NO.** Una Kempe-chain swap tra due timeslot CHIUDE il buco @10 di G11
   (holeClosed=true su tutti i donori validi) ma NON in modo sostenibile: OGNI chain apre un
   nuovo gap altrove (noNewGap=false, 60/60 donori su 6 giorni) E introduce regressioni hard
   (ttierHardd da -1000 a -2000, conflitti H1/H3 breaking the Kempe closure). **0/6 sustainable,
   0/6 shell-game puro, 6/6 no-improve** (non migliorano netto perche' il danno hard supera il
   guadagno H17).
2. **TASK-482 si chiude NO con evidenza.** La catena di Kempe NON e' la cura strutturale per il
   bacino saturo @10. La causa e' la STESSA del depth-3 cascade (TASK-481): il bacino @10 e'
   saturo di docenti (11 gruppi packati), ogni blocker e' mid-run, spostarlo apre un gap sulla
   sua group-day. La Kempe chain non elude questa saturazione: la sposta al donor's day o
   introduce conflitti hard al target slot.
3. **La via principale resta il multi-start (TASK-480, adottato).** Kempe-chain e' deferito
   permanentemente: il bacino saturo vanifica il vantaggio strutturale della mossa.

---

## (a) Model expression: how a Kempe chain maps on our planning entity

### Il modello
Entita': `Lesson` (una ora scolastica). Variabili di planning: `timeslot` (basic, non list) e
`room`. Una Kempe chain opera sulla **conflict graph** dove:
- **Vertici** = lezioni a uno di due timeslot (i due "colori")
- **Edge** = conflitto H1 (stesso docente non-pseudo) o H3 (stesso student group)
- **Chain** = componente connessa contenente il donor, nel sottografo indotto dai due colori
- **Swap**: lezioni della chain al colore c1 (ts_hole) -> c2 (ts_donor), e viceversa

### Il buco @10 di G11 (il target)
Nel bad-seed, G11 il mercoledi' ha lezioni a slot 8,9,_,11,12,13 con gap @10. Lo slot @10 e'
saturo: 12 gruppi hanno una lezione li' (tutti docenti diversi tranne i conflitti che la chain
segue). I 4 docenti di G11 che potrebbero stare @10 (T08/T23/T02/T06)
sono tutti occupati @10 con un altro gruppo.

### Cosa viene scambiato, cosa resta fisso, cosa termina la chain
- **Scambiato**: lezioni della chain al ts_hole <-> ts_donor. Il donor (G11) va a ts_hole
  (chiude il buco). Il blocker (altro gruppo, stesso docente del donor) va a ts_donor.
- **Fisso**: lezioni NON nella chain (restano al loro slot). Pinned lessons e pseudo-docente
  (Stage) sono esclusi dalla chain (ostacoli fissi).
- **Terminazione**: la BFS si ferma quando non ci sono piu' edge di conflitto tra la frontiera
  corrente e lezioni del colore opposto. In pratica la chain e' piccola (2-7 lezioni).

### Effetto netto sul buco @10
Il donor arriva a @10 (holeClosed=true). MA:
1. Il blocker spostato a ts_donor o apre un gap sulla sua group-day (mid-run, come depth-3)
2. Il donor lascia un gap sulla sua vecchia day (se era mid-run li')
3. Lezioni VRP-bloccate al target slot (che non possono entrare nella chain) creano conflitti
   H1/H3 con i chain lessons che arrivano: il teorema di Kempe (conflict-closure) NON vale
   quando alcune lezioni sono immobili per VRP

### Contrasto esplicito con pillarChange / pillarSwap
| Aspetto | pillarChange/pillarSwap | Kempe chain |
|---------|-------------------------|-------------|
| Granularita' | Colonna intera (TUTTE le ~12 lezioni a uno slot) | Sottoinsieme conflict-connected (2-7 lezioni) |
| Cosa muove | Whole pillar to new value | Solo lezioni nella chain |
| H1/H3 neutrality | Si' (tutte le lezioni si muovono insieme) | NO in presenza di VRP-blocked lessons |
| Raggiunge il 3-move composition | No (blunt: muove tutto) | Teoricamente si', ma il bacino saturo vanifica il guadagno |

Il `pillarChangeMoveSelector` (jar 2.3.0: `ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedPillarChangeMove`)
ha campi `pillar: List<Object>` (tutte le entita' al timeslot) e `toPlanningValue`. Muove la
colonna intera. La Kempe chain muove solo il sottoinsieme conflict-connected. La differenza
e' la granularita', ma per questo bacino nessuna delle due funziona.

---

## (b) Timefold 2.3 community mechanics (verified on jar sources)

### Jar verification method
`~/.m2/repository/ai/timefold/solver/timefold-solver-core/2.3.0/timefold-solver-core-2.3.0.jar`
estratto in `/tmp/tf230-kempe`. Firme lette con `javap` (non decompilatore: signature-level
ground truth dal bytecode). Ogni citazione sotto e' verificata sul bytecode reale.

### Custom move API: tre opzioni, in ordine di complessita'

#### Opzione 1: `MoveListFactory` (piu' semplice, eager)
```
ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory<Solution_>
  + List<? extends Move<Solution_>> createMoveList(Solution_)
```
Registrata in solverConfig.xml come:
```xml
<moveListFactory>
  <moveListFactoryClass>com.school.planner.solver.KempeChainMoveListFactory</moveListFactoryClass>
</moveListFactory>
```
XSD: `solver.xsd:276` `<xs:element name="moveListFactory" type="tns:moveListFactoryConfig"/>`.
Config class: `ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig`
(getter/setter `moveListFactoryClass`, `moveListFactoryCustomProperties`).

Limitazione: EAGER. `createMoveList` costruisce TUTTE le mosse possibili in una `List`. Per un
bacino con ~500 donori x ~170 giorni = 85K chain candidate, e' fattibile ma non efficiente (ogni
chain richiede BFS + score check).

#### Opzione 2: `MoveIteratorFactory` (lazy, consigliata per produzione)
```
ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory<Solution_, Move_ extends Move<Solution_>>
  + long getSize(ScoreDirector<Solution_>)
  + Iterator<Move_> createOriginalMoveIterator(ScoreDirector<Solution_>)
  + Iterator<Move_> createRandomMoveIterator(ScoreDirector<Solution_>, RandomGenerator)
  + default void phaseStarted(ScoreDirector<Solution_>)
  + default void phaseEnded(ScoreDirector<Solution_>)
```
Registrata in solverConfig.xml come:
```xml
<moveIteratorFactory>
  <moveIteratorFactoryClass>com.school.planner.solver.KempeChainMoveIteratorFactory</moveIteratorFactoryClass>
</moveIteratorFactory>
```
XSD: `solver.xsd:276` `<xs:element name="moveIteratorFactory" type="tns:moveIteratorFactoryConfig"/>`.
Config class: `ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig`
(getter/setter `moveIteratorFactoryClass`, `moveIteratorFactoryCustomProperties`).

Vantaggio: LAZY. `createRandomMoveIterator` produce chain on-demand, paginando il costo. Il
`RandomGenerator` e' il solver's random (iniettato), quindi la chain e' deterministica sotto
REPRODUCIBLE.

#### Opzione 3: custom `MoveSelector` (massimo controllo, non necessaria qui)
```
ai.timefold.solver.core.impl.heuristic.selector.move.MoveSelector<Solution_>
  extends IterableSelector<Solution_, Move<Solution_>>
  + default boolean supportsPhaseAndSolverCaching()
```
Sottoclassare `AbstractMoveSelector` (impl). Piu' complicata delle factory; non necessaria
perch MoveIteratorFactory copre il caso d'uso.

### Il `Move` interface (preview API, 2.3.0)
```
ai.timefold.solver.core.preview.api.move.Move<Solution_>
  + void execute(MutableSolutionView<Solution_>)
  + default Move<Solution_> rebase(Lookup)
  + default SequencedCollection<Object> getPlanningEntities()
  + default SequencedCollection<Object> getPlanningValues()
  + default String describe()
```
NOTA CRITICA: in 2.3.0 `execute` prende `MutableSolutionView`, NON `ScoreDirector`. Per cambiare
una variable: `mutableSolutionView.changeVariable(planningVariableMetaModel, entity, value)`.

`AbstractMove` (impl): NON espone piu' `doMove`/`isMoveDoable`/`createUndoMove` (API legacy
OptaPlanner rimossa). La nuova catena e': `Move.execute(MutableSolutionView)` +
`MutableSolutionView.changeVariable(...)`.

### KOpt (Kempe-like) esiste SOLO per list variables
```
ai/timefold/solver/core/config/heuristic/selector/move/generic/list/kopt/KOptListMoveSelectorConfig.class
ai/timefold/solver/core/impl/heuristic/selector/move/generic/list/kopt/SelectorBasedKOptListMove.class
```
KOpt e' il Kempe-chain della letteratura per **list variables** (vehicle routing, nurse
scheduling). Il nostro `Lesson.timeslot` e' una **basic variable** (`@PlanningVariable`, non
`@PlanningListVariable`), quindi KOpt NON e' utilizzabile. Non esiste alcun Kempe-chain
built-in per basic variables in Timefold 2.3.0: e' per questo che TASK-482 richiede una
`MoveIteratorFactory` custom.

### Pillar (built-in, attivo in Phase 2): cosa fa davvero
```
ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedPillarChangeMove<Solution_>
  fields: GenuineVariableDescriptor variableDescriptor, List<Object> pillar, Object toPlanningValue
  + boolean isMoveDoable(ScoreDirector)
  + protected void execute(VariableDescriptorAwareScoreDirector)
```
Un `PillarDemand` (`ai.timefold.solver.core.impl.heuristic.selector.move.generic.PillarDemand`)
computa le pillars come `List<List<Object>>`: liste di entita' che condividono lo stesso valore
della planning variable. Per `timeslot`, una pillar = tutte le `Lesson` allo stesso timeslot.
`pillarChange` muove l'intera lista a un nuovo valore. E' una **colonna**, non una chain.

---

## (c) Offline spike: verdict and evidence

### Test
`src/test/java/com/school/planner/bootstrap/KempeChainH17DiscriminatorTest.java`
(gated `PLANNER_TEST_KEMPE=1`, default OFF). Run:
```
PLANNER_TEST_KEMPE=1 ./scripts/core/test.sh -c KempeChainH17DiscriminatorTest
```
Exit code: **0** (test passes as informational spike). Tempo: 172s (H2 in-memory, proprio JVM,
non conflitta con il batch solve su PostgreSQL multistart).

### Risultati
```
BASE: hard=-114006 medium=-1307639 soft=2352 ; H17=-6 ; H1/H3 conflicts=0
```
La ricostruzione e' H1/H3-fedele (conflicts=0), come il discriminatore depth-3.

```
VERDICT Kempe-chain (SUSTAINABLE): 0/6 ; shell-game: 0/6 ; no-improve: 6/6
```

### Pattern rappresentativo (2026-11-11, donor T04)
Chain piu' piccola (2 lezioni):
```
chain=2 lessons ; H17d=-1 ttierHardd=-1000 holeClosed=true noNewGap=false => no-improve
  G11 Lingua Italiana (T04) 2026-10-12@10 -> 2026-11-11@10  (fills hole)
  G02  Lingua Italiana (T04) 2026-11-11@10 -> 2026-10-12@10  (blocker displaced)
```
- H17d=-1: il buco G11 @10 e' chiuso (donor riempie la cella)
- ttierHardd=-1000: 1 conflitto hard introdotto (G02 arriva a 2026-10-12@10 dove e' in
  conflitto con una lezione non nella chain)
- noNewGap=false: un nuovo gap appare altrove (G11 2026-10-12 perde @10, o G02 2026-11-11
  perde @10 aprendo un gap mid-run)

Chain piu' lunga (4 lezioni):
```
chain=4 lessons ; H17d=-1 ttierHardd=-1000 holeClosed=true noNewGap=false => no-improve
  G04  Storia           (T23)     2026-11-11@10 -> 2026-10-05@11
  G11 Lingua Italiana (T04) 2026-10-05@11  -> 2026-11-11@10
  G02  Eduzione Civica (T23)     2026-10-05@11  -> 2026-11-11@10
  G02  Lingua Italiana (T04) 2026-11-11@10 -> 2026-10-05@11
```
- La chain alterna: donor (T04, G11) -> blocker (T04, G02) -> cross-edge
  (T23: G04 Storia -> G02 Eduzione Civica). 4 lezioni, 2 docenti, 3 gruppi.
- H17d=-1 ma ttierHardd=-1000: il cross-edge introduce un conflitto non risolvibile nella chain
  (una lezione non nella chain e' al target slot con stesso docente/gruppo).

### Perche' la chain fallisce (meccanismo)

1. **La saturazione @10 vanifica il swap.** Il donor arriva a @10 (hole chiuso) ma il blocker
   spostato al ts_donor o apre un gap (se mid-run sulla sua group-day) o conflitta con una
   lezione non-chain gia' al target. Questo e' lo STESSO meccanismo del depth-3 cascade:
   spostare un blocker saturo fa danno altrove.

2. **Il teorema di Kempe NON vale con VRP-blocked lessons.** La conflict-closure della chain
   richiede che TUTTE le lezioni con un edge di conflitto tra i due colori siano nella chain.
   Le lezioni VRP-bloccate (che non possono muoversi al colore opposto perche' fuori value
   range) sono escluse dalla chain ma restano al loro slot: quando una chain lesson arriva al
   loro slot, il conflitto e' nuovo. Il ttierHardd negativo (-1000/-2000) e' la prova
   meccanica di questo breaking.

3. **Il donor lascia un gap sulla sua vecchia day.** Il donor G11 si muove da, es.,
   2026-10-12@10 a 2026-11-11@10. Il 2026-10-12 perde @10 e se il donor era mid-run li', si
   apre un nuovo gap H17. La catena non puo' riempirlo perche' il donor e' l'unico G11 nella
   chain.

### Criterio di sostenibilita' (VERBATIM dal discriminatore depth-3)
La close e' SUSTAINABLE iff:
- H17 improved (h17d < 0)
- No timeslot-tier hard regression (ttier == 0)
- Hole actually closed for G11 (holeClosed)
- No new per-(group,date) gap (noNewGap)

Questi 4 criteri sono l'estensione del `isMidRun` gate del depth-3 (bloccante edge-removable):
un nuovo gap anywhere = hole relocated = shell-game. Il Kempe chain non li soddisfa MAI su
questo bad seed.

### Confronto con depth-3 cascade (TASK-481)
| Metric | depth-3 cascade | Kempe chain |
|--------|----------------|-------------|
| Sustainable closes | 0/6 | 0/6 |
| Shell-game closes | 6/6 | 0/6 (peggio: neanche shell-game puro, tutte no-improve) |
| Hole filled | Sometimes (via unrelated fill) | Always (donor goes to @10 by construction) |
| Hard regression | No (depth-3 e' H1/H3-neutrale per costruzione) | Si' (VRP-blocked breaking closure) |
| Root cause | Blocker mid-run -> gap relocates | Stesso + VRP-blocked -> hard conflict added |

La Kempe chain e' in realta' PIU' debole del depth-3 su questo bacino: il depth-3 almeno
raggiunge shell-game closes (net H17 -1), mentre la Kempe chain introduce anche hard regression,
rendendola peggio del no-op.

---

## Decisione

**TASK-482 si chiude NO con evidenza.** La Kempe-chain non e' la cura strutturale per il
bacino saturo @10. La causa radice (saturazione @11 gruppi packati, ogni blocker mid-run) e'
immune sia al depth-3 cascade che alla Kempe chain: qualsiasi mossa locale che sposti un
blocker da @10 fa danno altrove. La via principale resta il **multi-start (TASK-480, adottato)**:
selezionare un bacino good-seed che evita la saturazione, non curare localmente un bacino saturo.

### Cosa non e' stato verificato
- **Performance in production LS.** Non implementato in produzione (regola overnight). Il spike
  e' offline con oracle, non misura l'impatto su wall-clock/iterazioni della LS.
- **Kempe chain su GRAFI diversi.** Lo spike usa H1+H3 come edge graph. Una chain su solo H1
  (solo docente) o solo H3 (solo gruppo) potrebbe raggiungere stati diversi, ma il meccanismo
  di fallimento (saturazione + VRP-blocked) e' indipendente dal tipo di edge.
- **Chain con ri-ancoraggio.** Una variante che, dopo lo swap, ri-ancora le group-days
  affette con un pass di StudentDayCompaction. Ma questo e' un repair post-mossa, non una
  proprieta' della chain stessa, e StudentDayCompaction e' gia' max-only ( TASK-481 ha mostrato
  che non chiude composition-required gaps).

## Puntatori

- Spike: `src/test/java/com/school/planner/bootstrap/KempeChainH17DiscriminatorTest.java`
  (run: `PLANNER_TEST_KEMPE=1 ./scripts/core/test.sh -c KempeChainH17DiscriminatorTest`)
- Test log: `/tmp/kempe-test-full.log`
- Jar estratto: `/tmp/tf230-kempe` (Timefold 2.3.0 core, non committed, ephemeral)
- Discriminatore depth-3 (sibling): `src/test/java/com/school/planner/bootstrap/H17HoleCloseDiscriminatorTest.java`
- FORK depth-3: `claudedocs/spalmone-2026-27/task481-results/FORK-shell-game-depth3-2026-08-02.md`
- Recon TASK-480 (metodo jar verification): `claudedocs/spalmone-2026-27/task480-recon-timefold-multistart-2026-08-02.md`
- Arch doc sez 4.4: `claudedocs/orchestration-layer-architecture.md:347-353`
- Bad-seed export: `claudedocs/spalmone-2026-27/task478-results/ab-ef-probe-1m-run2/export.json.gz`

---

## Verifica indipendente (main session, 2026-08-03, adversarial-verification)

Il verdetto NO e' stato sottoposto a verifica avversaria (il meccanismo "VRP spezza la Kempe
chain" era elegantemente soddisfacente, red flag). Sopravvive. Decomposizione meccanica
ottenuta aggiungendo un breakdown per-vincolo al test e ri-eseguendolo:

- **Re-run riproduce 0/6 sustainable** (EXIT=0, stesso bad-seed, stesso oracolo `solutionManager`).
- **Il -1000 e' H3 (Student group conflict), non H1 e non un artefatto.** Breakdown per-vincolo
  sul primo chain: base H3=0 -> after H3=-1000. H1/H2/H3 hanno peso 1000 ciascuno
  (`BaseConstraintProvider.java:348/393/452`, `HardMediumSoftScore.of(1000,0,0)`), quindi -1000 =
  **esattamente 1 nuovo conflitto di gruppo**. La chain lesson arriva a uno slot dove una lezione
  VRP-bloccata (fuori value range, non entra nella chain) condivide il gruppo -> nuovo H3. Il
  teorema di Kempe (conflict-closure) non vale con ostacoli immobili per VRP: provato meccanicamente.
- **Base -114006 non e' un artefatto**: e' H2=-114000 (114 conflitti di stanza x1000, attesi perche'
  lo snapshot e' un EF-probe pre-B-ROOM con stanze non differenziate) + H17=-6. Il test esclude
  correttamente il tier stanza (H2/H29/H0) e legge solo il tier timeslot: base timeslot-tier
  hard = -6 (H17), coerente col framing del bad-seed.
- **Nessun codice di produzione modificato** (git: solo il test + questo doc + output batch).
- **Citazione Timefold verificata** con `javap` sul jar 2.3.0: `MoveIteratorFactory` con
  `getSize(ScoreDirector)` / `createOriginalMoveIterator(ScoreDirector)` /
  `createRandomMoveIterator(ScoreDirector, RandomGenerator)` byte-exact.
- **Criterio decisivo**: ogni chain fallisce il gate `ttier==0` (regressione H3 -1000). Questo e'
  indipendente dal segno di h17d e basta da solo a escludere ogni close sostenibile. (h17d=-1 su
  ogni chain: la chain chiude @10 ma apre 2 gap altrove, netto peggiore; secondario rispetto a H3.)

**TASK-482 si chiude NO con evidenza meccanicamente verificata, non solo teoria.**
