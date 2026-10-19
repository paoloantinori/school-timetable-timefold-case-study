# TASK-508: diagnosi NPE SimpleFieldTracker + mitigazione priming (flag OFF)

## Lo stack completo (run3 treatment, tentativo 5, 21:30:23)

```
RuntimeException: Move thread 0 exception (propagating to solver)
  at MoveResultQueue.take(MoveResultQueue.java:45)
  at MultiThreadedLocalSearchDecider.forageResult(:195)
  at MultiThreadedLocalSearchDecider.decideNextStep(:144)
  ... DefaultLocalSearchPhase.solve -> runStagedSolver -> runRoomSolveStage ->
      runStagedCycle -> runOneMultiStartAttempt -> MultiStartRunner.run
Caused by: java.lang.NullPointerException: Cannot invoke "String.equals(Object)"
                                  because "existing is null"
  at org.hibernate.bytecode.enhance.internal.tracker.SimpleFieldTracker.contains(:42)
  at org.hibernate.bytecode.enhance.internal.tracker.SimpleFieldTracker.add(:33)
  at Lesson.hibernate_trackChange(Lesson.java)
  at Lesson.hibernate_write_room(Lesson.java)
  at Lesson.setRoom(Lesson.java)                      <- accessor sintetico Timefold
  at Lesson Timefold MemberAccessor Field room executeSetter
  at VariableDescriptor.setValue -> changeVariableFacade
  at SelectorBasedChangeMove.execute
  at MoveDirector.executeTemporary(:431)              <- il thread valuta, NON committa
  at MoveThreadRunner.run(:105)
```

## Il meccanismo

1. Quarkus migliora a build-time le entita' Panache (bytecode enhancement): ogni
   scrittura di campo DENTRO l'entita' passa da write_room a trackChange, che chiama
   SimpleFieldTracker.add(nomeCampo).
2. SimpleFieldTracker tiene un String[] di nomi campo SENZA sincronizzazione:
   contains() itera l'array e fa existing.equals(field); add() fa Arrays.copyOf
   piu' scrittura del nuovo slot.
3. Il MT-LS valuta le mosse su score director FIGLIO (createChildThreadScoreDirector
   MOVE_THREAD, MoveThreadRunner.run): il cloner della working solution copia i
   campi sintetici dell'enhancement PER RIFERIMENTO (il tracker e' un tipo interno
   opaco che non istanzia), quindi le copie figlie condividono IL MEDESIMO oggetto
   tracker dell'entita' originale.
4. Due thread che eseguono la PRIMA scrittura di room sul tracker condiviso
   (l'inseguimento dei passi rende le finestre concorrenti) interlacciano la crescita
   dell'array: una crescita lascia uno slot null e il contains() successivo legge
   l'elemento nullo e NPE.

Perche' solo dopo ~2h e solo nel treatment: la add() avviene SOLO quando il tracker
non contiene ancora il campo (dopo, contains() cortocircuita e l'array non viene
piu' toccato). Il treatment ha una LS room molto piu' lunga (il corner-b di consegna
partiva da 7739 room-null) e quindi molte piu' entita' la cui prima scrittura room
cade nella finestra concorrente. Race raro, non deterministico: 1 occorrenza in ~2h.

## La mitigazione (implementata, DEFAULT OFF)

Lesson.primeDirtyTracking(): auto-assegnazioni di timeslot e room DA DENTRO
l'entita' (le scritture esterne NON vengono riscritte dall'enhancement), chiamata
single-threaded per ogni lezione all'inizio di runStagedSolver quando
planner.mt-ls.tracker-priming.enabled=true (opt-in, LIVE read).

Perche' funziona sotto ENTRAMBI i modelli di condivisione: se il tracker e' condiviso
per riferimento, il priming dell'originale inizializza anche tutte le copie future
(che copiano il riferimento gia' popolato); se invece il cloner createsse tracker
freschi per copia, ogni tracker avrebbe un solo owner (il thread di quella copia) e
non ci sarebbe race comunque. Dopo il priming contains() e' sempre vero, add() non
gira piu', l'array e' di fatto read-only: la race sparisce.

Trade-off documentati:
- costo O(n) auto-assegnazioni una volta per staged solver (trascurabile);
- NON e' una fix architetturale del difetto di thread-safety dell'enhancement
  (resta latente per qualunque altro campo scritto concorrentemente: il priming
  copre SOLO le due planning variable, che sono le uniche scritte dalle move thread);
- alternativa scartata: detach/copie difensive dell'intero grafo (costoso, cambia
  l'identita' delle entita' per il pinning); sincronizzazione del tracker
  (impossibile: codice generato da Hibernate).

Test: LessonTrackerPrimingTest (idempotenza piu' sicurezza concorrente dello stato
primato su 8 thread x 2000 auto-scritture piu' default OFF). ONESTA': la race stessa
non e' deterministica, nessun test puo' dimostrare la NPE senza priming; le asserzioni
portanti sono idempotenza e concorrenza-sicura dello stato primato.

Il default resta OFF: la mitigazione va armata esplicitamente nel prossimo run di
conferma (o ratificata da maintainer dopo il gate).

## Addendum (secondo mandato notturno): il tentativo di race deterministica e i suoi limiti

**Fatto strutturale scoperto**: target/classes NON e' enhancing (javap: nessun metodo
sintetico su Lesson). Il bytecode enhancement vive SOLO in
`target/quarkus-app/quarkus/transformed-bytecode.jar` (il classpath runtime). Quindi
NESSUN unit test della suite puo' esercitare il percorso reale del tracker: il
LessonTrackerPrimingTest gira su classi non-enhanced dove le scritture sono plain
field write. Limite dichiarato: quel test copre il contratto del priming, non la race.

**Probe dedicato** (fuori suite, su transformed-bytecode.jar estratto): chiama i
metodi sintetici `$$_hibernate_write_room` / `$$_hibernate_write_timeslot` via
reflection (cio' che l'accessor Timefold fa a runtime) da 2 thread sulla STESSA
istanza fresca, ordine incrociato (room-then-timeslot vs timeslot-then-room),
20000 entita' x 3 scritture per thread x 5 run: **0 NPE** (189-232 ms per run).

**Disassembly della race** (javap -c di SimpleFieldTracker 7.2.4): contains() itera
una COPIA LOCALE dell'array (aload_0/getfield/astore_2) e invoca existing.equals();
la add() fa copyOf(names, len+1) e POI scrive lo slot nuovo. La NPE "existing is null"
richiede che un thread legga l'array DOPO il putfield del copyOf e PRIMA della
scrittura dello slot (o che due add concorrenti si sovrascrivano la copia): una
finestra di POCHI BYTECODE. A ~100k valutazioni/secondo x 8 thread x ~2h e' una
finestra colpita (1 occorrenza in run3); in un probe sintetico con 2 scritture per
campo per entita' non lo e' (fattore ~10^8 in meno di pressione).

**Verdetto onesto**: la race NON e' riproducibile deterministicamente senza
strumentare il bytecode (iniettare una pausa fra copyOf e la scrittura dello slot),
che non facciamo. La prova meccanica resta lo stack di run3 + il disassembly sopra;
la mitigazione priming resta la risposta giusta perche' elimina la add() dal percorso
dopo la prima scrittura, chiudendo la finestura invece di restringerla.
