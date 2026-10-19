# REPORT notturno 2: diagnosi H28 integrato-1 + design wiring produzione 638 (23/09, laptop, rc-1)

## FILO A - la H28 di integrato-1 e' la STESSA cella del puzzle, composita sul TIER STANZE

### Identificazione (scanner col predicato esatto del provider su lessons.txt)

CELLA: **G13 | 2027-02-12 | Lavorazioni meccaniche | T10 @(8,11) | mezzo
T15 @9**. Giorno: @8 Lav-T10, @9 Lav-T15, @10
TecnicheSaldatura-T15, @11 Lav-T10, @12 **Cad**-T15. E' la
STESSA ferita del puzzle 637-on (blind-and-moves/REPORT.md), con una sola
differenza: @12 e' Cad invece di Tecniche di saldatura.

Le settimane dei due docenti (dal capture): T15 12/02 occupa @8 (G08
Recuperi), @9, @10, @11 (G08 Controllo numerico), @12: libero solo @13.
T10 12/02 occupa @8, @11, @12 (G04 Tecnologia): liberi @9, @10, @13
(DIVERSO dal 637-on, dove @12 era libero e @9 occupato).

### Verifica golden practice

Scanner A..B..A su TUTTO seed/lessons.csv col predicato del provider: **ZERO
violazioni** (invariante TASK-448 confermata meccanicamente). La cella NON e'
strutturale: il golden non la pratica da nessuna parte.

### La cura a mano e l'arbitrato Java (scratch su fixture ricostruita, cancellato)

Cura analoga a quella del puzzle: **swap M-Lav@9 <-> M-Cad@12** (stesso
docente, stesso gruppo, intra-giorno; T15 resta su @9/@12, T10
intatto; Lav perde il "B" di materia uguale tra @8 e @11).

ARBITRATO: la swap CHIUDE la H28 (la famiglia scompare dal dopo) ma il hard
va **-52 -> -1051**: nasce **H2 Room conflict h=-1000**. Meccanismo: le stanze
viaggiano con le lezioni. Nel fixture pipe+CH-greedy, M-Lav@9 sta in **AULA
9** (aula generica) e M-Cad@12 in **R15**: dopo la swap
R15 atterra su @9, dove il pass greedy ha gia' prenotato un'altra
lezione. Nel 637-on (export room-FAITHFUL) la stessa famiglia di swap era
pulita perche' le stanze reali non collidevano.

### Verdetto

NON strutturale (golden zero A..B..A), NON timeslot-composita: la cura
timeslot ESISTE ed e' una mossa sola. E' **composita sul tier stanze**: serve
swap dei timeslot + riassegno stanza (o il room-refine che nel ciclo reale
gira a valle). Il 638 standalone non puo' chiuderla perche' muove solo
timeslot e l'oracolo full-provider vede l'H2 e rifiuta (correttamente: nello
sweep esausto la swap sara' stata valutata con delta -999). La "classe di
mossa mancante" qui e': **swap con riassegno stanza** (o composite
timeslot+room), che al punto di consegna produzione va decisa esplicitamente
(vedi FILO B).

## FILO B - design del wiring produzione (NESSUN implemento, nessun flip)

### Punto d'innesto

`ScheduleService`, catena di consegna del wrapper multi-start, righe ~2681-2707:
CSF (runCompositeSwapFinisher) -> EDF delivery (runEdfExtensionsOnDelivery)
-> HRF (runHoleRelayFinisher) -> **[638 QUI]** -> save incondizionato
(TASK-526). Posizione: ULTIMO della terna, dopo HRF: la terna corre i
specialisti per famiglia (boxed -> fill -> holes), il 638 e' il chiuditore
generico che spazza il residuo restato (incluse le cure classe-context che
nessuno specialista esprime). Una volta per consegna (non per tentativo),
stesso ritmo dei sibling: la pass e' a costo censito (0,4-4s sugli stati del
catalogo) e idempotente.

### Director: cosa c'e' davvero a quel punto

NON esiste un director vivo del ciclo: lo staged solver e' terminato. La
convenzione di famiglia e' `passWithOracle`: PlacementSnapshot +
`computeConstraintMatchSnapshot` prima/dopo (director FRESCO per snapshot,
da `getScoreDirectorFactory()` cacheata una volta) + `ensureImproveOrNoop`
full-hard con revert. Quindi:

- La domanda "riusa il director del ciclo" si riformula: il finisher deve
  ACCETTARE la factory condivisa (`ScoreDirectorFactory` iniettata) invece di
  costruirsi SolutionManager/SolverConfig per chiamata (la forma banco, oggi:
  un intero constraint-set build per finish()). I suoi director interni di
  valutazione restano freschi-per-pass come i sibling: e' la forma di casa.
- Il metamodel si risolve dal solution descriptor della factory UNA volta
  (stesso oggetto per ogni director creato da quella factory).

### Prerequisiti (elenco cosa manca, nessuno implementato)

1. FIRMA: `finish(problem, ScoreDirectorFactory, maxIterations,
   maxCandidates)`; il banco continua a passarsi la factory autocostruita.
   Rimuove il per-call build del constraint set.
2. ORACOLO: avvolgere in `passWithOracle(problem, "violation steepest
   finisher", ...)` (gratis: snapshot, before/after, improve-or-noop, revert,
   log). Il runner `runViolationScopedSteepestFinisher` come i sibling.
3. FLAG + BANNER: opt-in default OFF fino a ratifica (forma di casa:
   `planner.construction.violation-steepest.enabled`), banner in
   refreshSolverStatics con census (targets/evaluated/applied/hard
   prima/dopo/wall gia' nella Stats).
4. DECISIONE TIER STANZE (la scoperta del FILO A): al punto di consegna la
   griglia ha le stanze FINALI (post B-ROOM) e nulla gira dopo. Due opzioni:
   (a) accettare che l'oracole rifiuti le swap che collidono stanze (sicuro,
   perde le cure classe integrato-1); (b) estendere la mossa vincente a
   composite timeslot+room (Moves.swap anche sul room metamodel nel caso
   peggiori, o riassegno della stanza collisa) valutata come un'unica mossa.
   Raccomandazione: partire da (a) (l'oracolo gia' protegge), tenere (b)
   come task proprio se il census mostra cure perse. Nota H29: una lezione
   PIAZZATA dal finisher atterra senza stanza (residuo -1 nello stato
   637-off): stessa decisione, lato placement.
5. KNOBS: portare maxIterations/maxCandidates a costanti censite del ciclo
   (default sweep: 10/100000; costo 0,4-4s misurato) con opt-out.
6. MOVE_TIMESLOT: i sibling mutano col BiConsumer raw di famiglia; il 638
   usa Moves notificate (la ragione della sua esistenza): nessuna azione,
   dichiarato perche' la differenza e' voluta.

### Cosa NON serve

Nessuna modifica al solverConfig (il finisher e' un pass Java puro), nessun
Neighborhood API (le factory one-shot Moves.* bastano), nessun thread in
piu' (gira nel thread di consegna come i sibling).

## AGGIORNAMENTO 23/09 notte: IMPLEMENTATO (opt-in default OFF)

Il wiring e' implementato esattamente come il design sopra: innesto post-HRF
pre-save, runner con shell CSF-style, flag strict-literal
(`"true".equals(...)`, forma h7swap), banner in refreshSolverStatics, knobs
Integer.getInteger (10 / 100.000, censiti in knobs.md). Verifiche:
banco 636-off -2->0 con la stessa swap (la forma convenienza delega allo
stesso corpo della firma produzione: sola l'origine della factory differisce,
e la factory del ciclo e' cacheata da getScoreDirectorFactory); no-op
flag-off osservato (banner enabled=false nei log di suite, blocco
staticamente irraggiungibile, un getProperty per consegna); suite 1370/0.

Code-review high (5 finding): F1 CURATO - il runner NON usa passWithOracle
(che non restaura se la pass lancia) ma la shell CSF-style con catch +
PlacementSnapshot.restore + consegna invariata (pattern corner-b
TASK-614): un IllegalStateException mid-walk non puo' uccidere la consegna.
F2 NOTO-NON-MIO - le mosse delle pass di consegna (CSF/EDF/HRF e ora VSS)
vivono nel vincitore in-memory e il re-save post-loop scatta solo se
restoreNeeded o refinement CH-only: la divergenza DB/consegna e' la
proprieta' MISURATA dalla trust metric TASK-526 (reportDeliveryConcordance,
non-blocking by design): pre-esistente per l'intera terna, ereditata
identica, nessun tocco (mandato: tria intoccabile). F3 CURATO (guardia null
nel pre-loop dei partner). F4 CURATO (byGroup hoistato walk-invariante accanto
a byId). F5: la directory stray planner/ conteneva l'UNICA copia di un file
613 (notes-a3-canmove.py, percorso raddoppiato da un agente): rilocato in
task613-results/puzzle-live-r1/a3-round/, albero rimosso; clanner/tmp.dart
resta (senza casa evidente, segnalato).

