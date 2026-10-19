# TASK-509: percorso ship-best-no-0-hard e discrepanza score.txt vs analysis.json

## Il percorso (mappato sul codice)

Quando NESSUN tentativo arriva a 0-hard (budget esaurito o tutti condannati),
`runStagedCycleWithRetry` (ScheduleService) percorre, in ordine:

1. `MultiStartRunner.run` termina con best = miglior tentativo per
   (hard, medium, soft); tie = l'ULTIMO tentativo (che e' cio' che il DB gia' tiene).
2. Corner-b TASK-498: se il vincitore e' in `roomUnrefinedAttempts` (ha saltato la
   LS room perche' condannato), log "best attempt N shipped with CH-only rooms
   (no 0-hard attempt ran) - refining its rooms before delivery" e lancia
   `runRoomSolveStage(best, false)`: un solver COMPLETO (CH room + LS refinement
   multi-thread). E' best-solution-monotonic: puo' solo migliorare la griglia.
3. Il risultato raffinato sostituisce il best e cade nel percorso di restore
   (`saveSchedule` se serve).
4. `finally`: azzera la narrativa (stagedAttempt/stagedMaxAttempts/stagedBudgetSeconds
   = 0). `stagedStage` NON viene ripristinato qui: era stato settato a
   STAGE_ROOM_SOLVE dentro l'ultimo tentativo e resta non-null finche'... il punto
   critico e' che il corner-b gira DOPO il loop, quando la narrativa del CICLO e'
   gia' chiusa: per il driver il solve appare finito mentre la rifinitura lavora.

## La discrepanza, meccanicamente (round 3, timestamp per timestamp)

- 21:30:23,127: NPE, tentativo 5 abortito SENZA `saveFinalSolution`. Il DB resta nello
  stato intermedio del tentativo 5 (fase room: timeslot del tentativo precedente,
  stanze azzerate per il re-plan): e' lo stato TORN che analysis congelera'.
- 21:30:23,375: corner-b lanciato (rifinitura del tentativo 1).
- 21:30:24,584: LS refinement multi-thread partita (tipicamente ~604s).
- ~21:31: il driver polla NOT_SOLVING (il solver ad hoc non passa da solverManager:
  falsa segnale #1) + stagedStage null (la narrativa del ciclo e' gia' chiusa: falsa
  segnale #2, la stessa coppia documentata in CLAUDE.md). Stabile x2: il driver
  dichiara FINISH e parte capture-run.
- 21:32:12: `analyzeScore` ricarica il DB (stato torn del tentativo 5) e riconteggia:
  **-7795hard** (H29 -7739 + H0 -40 + H17 -14 + H7 -2). Questo e' analysis.json.
- 21:32:38: il driver legge `api.sh score` -> `getLatestScore()` -> volatile aggiornato
  dal listener `solver.addEventListener(newBest)` della rifinitura ANCORA IN CORSO:
  **-42hard** in-memory, MAI persistito (la rifinitura non salviera' prima di
  ~21:40, e il driver uccide l'app alle 21:36:59).

Round 2 (medium -382k, stesso meccanismo senza NPE): anche li' tutti i tentativi
condannati => corner-b => cattura mid-rifinitura => score.txt = best in-memory della
rifinitura, analysis = ultimo stato salvato. La differenza di magnitudine (solo medium
in round 2, hard -7753 in round 3) e' la profondita' dello strappo: in round 3 il
tentativo 5 e' morto a meta' fase room (DB torn), in round 2 il DB era integro.

PERCHE' treatment-specific: solo il treatment produce tentativi condannati (il
control fa 0-hard al primo) e quindi invoca il corner-b. Il control non passa MAI
da questo percorso: score.txt == analysis.json (verificato in run3: identico).

## Il contratto (proposta)

- **Verita' del dato consegnato: il DATABASE** (cio' che analysis/export/lessons.txt
  leggono dopo il ri-caricamento). E' l'unico stato che sopravvive al processo ed e'
  quello che la scuola riceverebbe.
- `latestScore` e' NARRATIVA DI PROGRESSO: valida finche' il solver che la emette
  salva a fine run; dopo un tentativo fallito o durante una rifinitura di consegna
  descrive uno stato in-memory non persistito. Non va mai confrontata con il DB
  fuori da una scansione completata.
- Conseguenza operativa: il gate A/B deve leggere SOLO analysis.json (o capture-run),
  mai score.txt, quando il braccio puo' avere tentativi condannati.

## Fix proposti (NON applicati: prerequisiti round 4)

1. **Quiescenza del corner-b**: mantenere `stagedStage` non-null (es. un valore
   "B-ROOM-FINALIZE") per la durata della rifinitura di consegna, e ripristinarlo a
   null nel finally DOPO. Il driver smetterebbe di uccidere la rifinitura. Rischio
   basso, ma cambia la narrativa visibile in UI: va fatto con gli occhi aperti.
2. **score endpoint**: dopo un tentativo fallito (lastSolverError non null) o durante
   il corner-b, etichettare il punteggio come potenzialmente non persistito (o
   leggerlo dal DB). Alternativa minima: documentare nel contratto REST che
   /api/schedule/status riporta la narrativa, non il consegnato.
3. Round 2/3 VERDICT aggiornati con questo meccanismo gia' in ROUND 3-DIAGNOSI
   (Fatto 3): la discrepanza e' SCOPERATA e spiegata, non assorbita.

## Test localizzato

Il meccanismo vive nell'interazione runtime solver/driver/DB: non catturabile con un
unit test senza un solve. La parte testabile (contratto flag/narrativa) e' banale e
gia' coperta indirettamente. ONESTO: nessun nuovo test; la verifica del fix 1 sara'
il round 4 (driver non uccide la rifinitura => score.txt == analysis.json).
