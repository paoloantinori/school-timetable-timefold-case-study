# VERDICT TASK-501 - copertura thinning forma B su TUTTI gli stream del decider (2026-08-16)

> Filone 2 del mandato Fedora 16/08 mattina: "verifica se il thinning forma B
> (TASK-500) copre GIA' tutti gli stream che il decider M-T processa. Se tutto
> coperto: TASK-501 chiuso Done con verifica documentata (il thinning agisce a
> livello decider, sotto ogni move selector). Se non coperto: estenderli."

## TL;DR

**Coperto. TASK-501 si chiude Done.**

> NOTA (dopo la stesura, stessa mattina): la RATIFICA TASK-500 (commit bbf89571,
> 16/08) ha reso il thinning forma B DEFAULT ON in produzione. La verifica qui
> sotto e' quindi passata da "copertura di un prototipo gated" a "copertura del
> comportamento di produzione": la conclusione non cambia, la rilevanza sale.

Il thinning forma B agisce su UN solo punto,
`MultiThreadedLocalSearchDecider.decideNextStep` (riga ~157:
`if (!AdaptiveMoveWeights.shouldEvaluate(move))`), che sta SOTTO il move repository
del decider: OGNI mossa che l'union selector produce, da qualunque figlio provenga,
passa di li' prima del dispatch. Il buco strutturale di TASK-499 (filtro solo sui 5
change-move stream timeslot, via ValueSelector) per forma B NON PUO' esistere per
costruzione. La domanda residua (i 2 stream pillar esclusi dal LEARNING) e' una
scelta di design con ragioni proprie, non un resto di forma A: dettaglio sotto.

## La verifica (tutta su codice, 2026-08-16)

### 1. Punto di strozzamento unico

`decideNextStep` itera `getMoveRepository().iterator()`: e' l'UNICO sito di dispatch
(`operationQueue.put` compare solo li', riga ~163; `MoveEvaluationOperation` e'
valle della coda). `shouldEvaluate(move)` viene chiamato su OGNI mossa prima
dell'accodamento; una mossa thinnata non costa ne' slot in coda ne' valutazione.
`record(move, accepted)` viene chiamato nell'unico loop consumatore
(`forageResult`, riga ~209) per ogni risultato dispatchato. Nessuna mossa puo'
aggirare il punto.

### 2. Stream effettivamente processati in produzione (solverConfig.xml)

Due fasi LS, e il loro union selector figli:

| fase | figli dell'union | variabile | stream classificato | adaptive |
|---|---|---|---|---|
| B-TIMESLOT | change, swap, pillarChange, pillarSwap | timeslot | TS_CHANGE, TS_SWAP, TS_PILLAR_CHANGE, TS_PILLAR_SWAP | si'/si'/no/no |
| B-ROOM | change, swap | room | ROOM_CHANGE, ROOM_SWAP | si'/si' |

Tutti e 6 gli stream di produzione sono classificati dalla `MoveTypeClassifier`
(verificato con i riferimenti ALLE CLASSI REALI Timefold in
`AdaptiveMoveWeightsTest.classifierTable`: `SelectorBasedChangeMove`/`SwapMove`/
`PillarChangeMove`/`PillarSwapMove`, niente nome-di-comodimento stantio). Nessuna
altra classe di mossa compare nelle due fasi LS (niente cartesian/k-opt nel config
produzione).

### 3. Chi NON viene thinnato, e perche' (la domanda del mandato)

- **Stream pillar (TS_PILLAR_CHANGE/SWAP), `adaptive=false` → sempre dispatchati.**
  Il motivo MECCANICO scritto nel javadoc della enum ("i pillar selector non
  supportano selezione PROBABILISTIC") e' effettivamente un resto di forma A
  (valeva per `probabilityWeightFactory` a livello selector; il thinning forma B e'
  a livello decider e NON tocca la machineria selector). MA l'esclusione resta
  corretta sotto forma B per tre ragioni INDIPENDENTI:
  1. **Contratto dei pesi ratificati**: i pillar stanno a 2/1 nel config ratificato.
     Forma B preserva i rapporti relativi fra gli stream adaptive mentre i pillar
     restano ESATTAMENTE alla loro frequenza di campionamento ratificata. Farli
     entrare nel learning farebbe driftare la loro frequenza effettiva in [0.5x,
     2.5x]: una leva DIVERSA da quella misurata dall'A/B TASK-500.
  2. **L'evidenza A/B copre questa configurazione**: medium neutro + tail-capping
     sono stati misurati COI pillar fuori dal learning. Includerli = prototipo
     nuovo, gate nuovo (mandato: nessuna adozione senza gate).
  3. **Costo per valutazione**: le mosse pillar sono le piu' costose (sub-pillar);
     thinnarle e' la leva wall-time piu' grossa, non un rider gratuito.
  Contano comunque ai fini osservativi: `record` li conteggia (evals/accepts),
     sono solo esclusi da re-weighing e media (verificato in `updateWeights`:
     `!adaptive[i] → out[i] = base`).
  Se un domani maintainer vuole il learning anche sui pillar: e' un cambio a una riga
  (`adaptive=true` sui due stream) + A/B dedicato. Tracciato sotto, NON fatto.

- **Stream OTHER (base 0), sempre dispatchato.** In produzione ci arrivano solo le
  mosse a valore NULL: entrambe le planning variable hanno `allowsUnassigned = true`
  (`Lesson.timeslot`/`Lesson.room`), quindi le change-move di UN-assign hanno primo
  planning value nullo e classificano OTHER (non Room/Timeslot). Nota di
  granularita', non buco: niente bypassa lo strozzamento, e' solo che le
  unassign-move stanno fuori dal learning (dispatchate sempre, contate sotto OTHER).
  Lo swap con un lato null: stesso trattamento.

### 4. Degradazione dichiarata (nessuna azione)

Con MT-LS OFF il decider non gira: nessuna `shouldEvaluate`/`record`, i pesi
restano alla base (javadoc `AdaptiveMoveWeights`, confermato dal codice: le static
si toccano solo da quei due siti).

## Conclusione

- TASK-499-era gap ("filtro solo sui 5 change stream timeslot"): **inesistente per
  forma B**, per costruzione (livello decider, sotto ogni selector).
- Swap: coperti E nel learning (TS_SWAP/ROOM_SWAP sono `adaptive=true`). Pillar:
  dispatchati attraverso il thinning point, fuori dal learning per scelta con
  ragioni forma-B-valide (sopra). OTHER/unassign: dentro il choke point, fuori dal
  learning, benigno.
- **TASK-501 chiuso Done**: la domanda "estendere il filtro agli altri 6/11 stream"
  si dissolve nella forma B (il successore adattivo copre tutto a livello
  strutturale). Follow-up facoltativo UNO solo, se mai richiesto: `adaptive=true`
  sui pillar + A/B dedicato (leva wall-time grossa, gate obbligatorio).

Verifica su: `MultiThreadedLocalSearchDecider.java` (sito unico dispatch/record),
`AdaptiveMoveWeights.java` (enum/classifier/shouldEvaluate/updateWeights),
`AdaptiveMoveWeightsTest.java` (classifierTable con classi reali),
`src/main/resources/solverConfig.xml` (figli delle due fasi LS),
`Lesson.java` (allowsUnassigned su entrambe le variable).
