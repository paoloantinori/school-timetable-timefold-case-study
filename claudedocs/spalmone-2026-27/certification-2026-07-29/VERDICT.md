# VERDICT - Certificazione 2026-07-29 (sera)

Certificazione del pacchetto TASK-456 (lab-fallback default ON + esclusione stanza
capienza<=0) via solve staged completo. Esito: **NON CONVERGITO** (solve fermato in
B-TIMESLOT-1), ma con tre risultati utili e una anomalia di convergenza da indagare.

## Avvio (corretto, cancello superato)

- Lancio via `PLANNER_JAVA_OPTS="-Dplanner.phase-transition-dump.enabled=true
  -Dquarkus.hibernate-orm.schema-management.strategy=update" ./scripts/core/prod-start-and-wait.sh`
  (java -jar diretto, NON mvn/MAVEN_OPTS: il -D raggiunge la JVM dell'app).
- **Dump mechanism VERIFICATO** (il cancello critico di maintainer): PhaseTransitionMarker
  ha scritto `phase-2-2-ls-compactness{,-boxed,-id}.txt` sotto target/phase-transitions/
  (11210 placed + 1611 boxed al boundary di costruzione). Il MAVEN_OPTS trap e' stato
  evitato.

## Risultato: solve NON convergito

- Score fermato: **-11177hard/-1018785medium/61977soft**, stagedStage **B-TIMESLOT-1**
  (mai avanzato a PROMOTION/demotion/B-ROOM). Solve fermato a 23:20 dopo ~26 min.
- Decomposizione hard (analysis, ground truth dello stato corrente):
  - **H29 room-null: -10457hard (10447 placed senza stanza)**. ATTESO in B-TIMESLOT:
    le stanze si assegnano in B-ROOM, che non e' mai partito. Non e' un difetto.
  - **H0 boxed: -720hard (360 lezioni non collocate)**, in calo dai 1611 post-costruzione
    (la LS ne ha collocate ~1251).
  - **Tutti gli altri hard = 0** (H1/H3/H5/H7/H11/H17/H20/H23/H24/H25/H26/H28). Nessun
    conflitto docente/gruppo/calendario/quota. Il posizionamento timeslot e' PULITO a
    parte i 360 boxed.
- Le medium sono ancora ottimizzabili (M22 -466k, M20 -311k, M32 -184k dominano) ma non
  e' mai stato raggiunto il tier hard 0 che la campagna raggiunge ("hard 0 doppio").

## TASK-456 NON implicato (chiarito)

La anomalia e' **la non-avanzata del ciclo staged oltre B-TIMESLOT-1**, non un difetto
di posizionamento. In B-TIMESLOT le stanze NON sono ancora assegnate (H29 room-null lo
conferma: 10447 su 0 stanze), quindi l'esclusione cap-0 (Room.isAssignable) e il
lab-fallback ON (Lesson.getAvailableRooms) - entrambi riguardano le STANZE, fase B-ROOM
- **non si sono ancora applicati**. Il pacchetto TASK-456 ha girato senza crashare
attraverso costruzione + LS; il suo effetto e' pendente (B-ROOM non partito).
Meccanismo probabile dello stallo: la LS di B-TIMESLOT-1 continua a trovare piccoli
guadagni soft (S7/S13/S17 salivano), resettando l'unimprovedSecondsSpentLimit della
fase, che quindi non termina mai e non lascia avanzare il ciclo alla demozione/B-ROOM.

## Cosa questa certificazione consegna (parziale)

- `phase-transitions/phase-2-2-ls-compactness*` (construction-end) - utile per il
  dossier di costruzione del **task 459**.
- `analysis-partial.json`, `score-partial.txt` - stato parziale.
- I dump per-fase delle fasi successive (B-ROOM, repair, ecc.) **NON prodotti** (il
  ciclo non c'e' arrivato) - task **450** (ritardo ven G11) e **460** (ricombinazione)
  restano scoperti su questa run.

## Prossimi passi (per la prossima sessione)

1. Indagare la non-avanzata di B-TIMESLOT-1 (terminazione della fase LS: probabilmente
   manca un secondsSpentLimit assoluto a complemento dell'unimproved, oppure il soft
   drift va capped). Confrontare con un run di reference (il ciclo staged di campagna
   avanza): potrebbe essere un artifact del lancio prod (env-mode NON_REPRODUCIBLE) o
   della configurazione di terminazione.
2. **A/B TASK-456-off** (`-Dplanner.room.lab-fallback.enabled=false`) per confermare
   formalmente la non-regression: atteso lo stesso stallo in B-TIMESLOT-1 (rooms non
   ancora in gioco), che proverebbe l'indipendenza.
3. Rilanciare la certificazione una volta sbloccata l'avanzata del ciclo; poi
   capture-run.sh per la batteria KPI + VERDICT finale.

## File in questa directory

- `phase-transitions/` - i 3 dump di fase (construction-end).
- `analysis-partial.json`, `score-partial.txt` - stato al momento dello stop.
- `VERDICT.md` - questo documento.

---

## CORREZIONE (Fedora, 2026-07-29 23:30): NON c'era nessuno stallo, il solve e' stato fermato troppo presto

Il verdetto sopra e' sbagliato nel punto principale e la diagnosi va ritirata. Ho ricostruito
la cronologia del run di riferimento certificato (`task447-results/cert/phases.json`, dati
del motore, non un riassunto):

| fase | durata | inizio da avvio |
|---|---|---|
| 1. CH - Lesson Placement | 0,3 min | 0,0 min |
| 2. LS - Compactness | **18,8 min** | 0,3 min |
| 3. Compaction | 0,1 min | 19,1 min |
| 2. LS - Compactness | 3,9 min | 19,2 min |
| 3. Compaction | 0,1 min | 23,1 min |
| 2. LS - Compactness | **15,8 min** | 23,2 min |
| 3. Compaction | 0,1 min | 39,0 min |
| 4. CH - Room Assignment | 0,0 min | 39,2 min |
| 5. LS - Room Refinement | 10,1 min | 39,2 min |
| **totale** | | **49,3 min** |

Il ciclo di campagna dura **49 minuti**, non 40, e le stanze entrano in gioco solo al minuto
**39,2**. Al minuto 26, cioe' quando questo solve e' stato fermato, il run di riferimento si
trovava anch'esso in una fase LS di B-TIMESLOT (la terza iterazione, iniziata al 23,2 e
lunga 15,8 minuti) e da li' e' proceduto normalmente fino alla fine.

Quindi: **il comportamento osservato era quello atteso a quel minuto**. Non esiste evidenza di
una non-avanzata del ciclo staged, e l'ipotesi sul soft drift che resetta
`unimprovedSecondsSpentLimit` non ha nessun supporto: e' stata costruita su un sintomo che il
run di riferimento presenta identico. Cade anche il punto 1 dei "prossimi passi" (non c'e'
niente da indagare) e diventa inutile l'A/B del punto 2, che serviva a scagionare TASK-456 da
uno stallo che non c'e'.

Cio' che resta valido di questo documento: il cancello del dump per fase e' stato superato
davvero (verificato anche da me sul processo: `java -Xmx2g -Dplanner.phase-transition-dump.enabled=true
... -jar`, con i file scritti sotto target/phase-transitions), e la decomposizione hard con
H29 room-null atteso pre-B-ROOM e tutti gli altri hard a zero e' un dato utile.

**Lezione operativa**: un solve lungo non si interrompe per un sospetto. Si aspetta
`NOT_SOLVING` dal motore, oppure si confronta l'istante corrente con la cronologia per fase di
un run di riferimento PRIMA di dichiarare uno stallo. Fermare al minuto 26 un ciclo da 49
minuti ha buttato via il run e ha prodotto una diagnosi falsa.

**Azione**: certificazione da rifare dall'inizio col protocollo pieno, lasciandola arrivare a
terminazione naturale.

### Il meccanismo, trovato nel codice (non piu' per analogia)

maintainer ha mostrato il log di polling, che aggiunge il fatto decisivo: alle 23:13:02 e alle
23:18:29 il poll leggeva `solverStatus: NOT_SOLVING` con `stagedStage: B-TIMESLOT-1`. Sembrava
la prova di un solve morto. **Non lo e', ed e' un falso segnale strutturale.**

`ScheduleService.runStagedSolver` costruisce un `Solver` ad hoc con `SolverFactory.create(config).buildSolver()`
e lo esegue con `solver.solve(problem)`. Il ciclo staged **non passa da `solverManager`**, che e'
invece la sorgente di `solverStatus` esposto da `/status`. Quindi durante QUALUNQUE solve staged
`solverStatus` vale `NOT_SOLVING` dall'inizio alla fine, per costruzione. Il codice stesso lo
dichiara alla riga 335, dove la condizione di riposo vera e' `getStatus() == NOT_SOLVING && stagedStage == null`:
il campo che dice se il ciclo sta girando e' **`stagedStage`**, e valeva B-TIMESLOT-1, cioe' stava girando.

Cadono cosi' tutti e tre i sintomi su cui si reggeva la diagnosi:
1. `NOT_SOLVING`: non significa niente durante un solve staged (dimostrato sopra).
2. hard fermo a -3965 per 11 minuti mentre medium e soft migliorano: e' il comportamento
   normale dentro una lunga fase LS. Nel riferimento la prima LS dura 18,8 minuti e la terza
   altri 15,8. In piu' fra le 23:07 e la fine hard e' passato da -3965 a -11177, che con la
   contabilita' del TASK-447 (hard = boxed x2 + room-null x1) significa migliaia di lezioni
   COLLOCATE che non hanno ancora una stanza: era progresso, letto come peggioramento.
3. nessun nuovo dump: i dump si scrivono ai confini di fase, quindi dentro una fase LS lunga
   non ne compaiono per definizione.

**Regola operativa definitiva per il polling di un solve staged**: si guarda `stagedStage`,
mai `solverStatus`. Il solve e' finito quando `stagedStage` e' nullo.

### E lo "stallo di hard"? Il run certificato lo fa IDENTICO, per 15,8 minuti

Il sintomo che sembrava piu' grave, cioe' hard fermo a -3965 per 11 minuti mentre solo soft
migliorava, e' il comportamento del run DI RIFERIMENTO, quello certificato che chiude a -195.
Traiettoria per fase (`task447-results/cert/phases.json`, dati del motore):

| fase | durata | hard inizio -> fine | soft inizio -> fine |
|---|---|---|---|
| 1. CH - Lesson Placement | 0,3 min | 0 -> -2386 | 0 -> -49840 |
| 2. LS - Compactness | 18,8 min | -2386 -> **-2226** | -49840 -> 37225 |
| 3. Compaction | 0,1 min | -2226 -> -2224 | 37225 -> 36493 |
| 2. LS - Compactness | 3,9 min | **-2226 -> -2226** | 36493 -> 37464 |
| 3. Compaction | 0,1 min | -2226 -> -2224 | 37464 -> 36636 |
| 2. LS - Compactness | **15,8 min** | **-2226 -> -2226** | 36636 -> 38049 |
| 3. Compaction | 0,1 min | -2226 -> -2224 | 38049 -> 37343 |
| 4. CH - Room Assignment | 0,0 min | -9407 -> -9407 | 34268 |
| 5. LS - Room Refinement | 10,1 min | **-9407 -> -195** | 34268 -> 25758 |

Nel riferimento hard resta **congelato a -2226 per 15,8 minuti consecutivi** in una LS di
B-TIMESLOT, mentre soft sale piano: e' esattamente il quadro letto come stallo. E soprattutto
**hard converge SOLO nell'ultima fase**, passando da -9407 a -195 nei dieci minuti finali di
rifinitura delle stanze. Quindi giudicare la convergenza guardando hard a metA' ciclo e' un
errore di metodo: a quel punto hard non e' ancora l'obiettivo attivo della ricerca.

Il nostro run mostrava -3965 invece di -2226, cioe' un residuo un po' piu' alto, ma il
PATTERN e' lo stesso. Non c'era nulla da diagnosticare: mancavano solo i venti minuti finali,
quelli in cui il lavoro vero su hard viene fatto.
