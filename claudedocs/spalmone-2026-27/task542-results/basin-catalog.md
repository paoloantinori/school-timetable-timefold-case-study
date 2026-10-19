# TASK-539 - Biblioteca dei bacini (catalogo per costruzione-seed)

Materia prima: i checkpoint di `staged-checkpoints/` (catturati col flag
`planner.checkpoint.capture.enabled=true`, ciclo staged, confine = gate boxed + timeslot-tier
hard). Avvertenze di riproducibilita':
- l'identita' di un bacino e' legata al COMMIT + allo stato del DB (seed + import-calendario +
  pre-assign): un fixture si riproduce solo su quel profilo; il resume fallisce loud su id
  sconosciuti (per costruzione).
- LS multi-thread = statistica (i gate del POST_B_TIMESLOT variano +-qualche unità a parita'
  di bacino); i repair deterministici sono riproducibili byte-exact (iter-1 del banco).
- la fonte della verita' dei gate e' il campo nei JSON, non i log.

## Bacini catalogati

### B-1 "canonical-fix" (BUONO) - attempt 1 deterministico, post allocator-fix (iter-5)
- Commit: 80ede09f+ (quotaFor missing=0), profilo h42-bench con import-calendario (1993 pin
  stage). Fixture: `staged-checkpoints/attempt1/` (POST_B_TIMESLOT-1/2/3, POST_RELEASE,
  catena repair completa, POST_B_ROOM).
- Gate: post-CH B-TIMESLOT-1 **-45/9** -> -40/7 a release; consegna -24 (solo H17).
- Classificazione: bacino buono del path input post-fix. Residuo: famiglia H17 G13-venerdi'
  (19) + G10 2 + G12 2 (vedi iter-6: la catena repair chiude 5 buchi ma il gate reverta
  il trade H17-vs-H32).

### B-2 "no-import artifact" (CATTIVO, noto) - protocollo senza import-calendario
- Evidenza: iter-0/1/2 del banco (log). Gate post-RELEASE ~-642..-650, boxed ~320 (98% stub
  Stage off-window). NON un bacino di ricerca: e' un artefatto di protocollo. Ogni run senza
  import-calendario produce questa famiglia: il sanity protocol DEVE includere l'import.
- Fixture: nessuno conservato (i checkpoint dev sono stati sovrascritti); l'evidenza e' nel
  log e riproducibile in ~45min (profilo fresco senza import).

### B-3 "surgery-unlocked" (da-curare) - attempt 1 + chirurgia venerdi' G13 (iter-6)
- Fixture: `POST_RELEASE_iter6.json` (2-mosse sul 18/09: G04/T10 11->17/09 13:00,
  G13 Lav.mec 13->11). Gate invariato -24 ma il HoleRepair passa 23->18 buchi (5 chiudibili,
  0 conflitti) prima del revert improve-or-noop: e' il banco per la cura "chiudi senza aprire"
  (iter-7: sorgenti solo da giorni sopra quota, o within-day).
- E' anche il riproduttore del deadlock MT-LS risolto (TASK-541 bounded waits): stop+resume
  nello stesso JVM ora e' atteso senza park (verifica in vivo durante iter-7).

## Nota 22/08 ~10:30 (id-binding in pratica)
Il profilo h42-bench e' stato ricreato (iter-7b aveva contaminato il DB via import A/B,
ripristino fallito): i lesson id sono cambiati di nuovo, quindi i fixture B-1/B-3 e i
checkpoint su disco restano EVIDENZA (gate, diagnosi, riproduttori del metodo) ma non sono
piu' substrati resumabili (il restore fallisce loud: per costruzione). Il nuovo ciclo
canonico (22/08 mattina, stesso protocollo) rigenerera' checkpoint a id freschi.

## Worst/best (per la prevalidazione "chiudi il worst senza rompere il best")
- WORST disponibile: B-3 (residuo attivo, materia del banco).
- BEST disponibile: B-1 (consegna -24).
- La prevalidazione di ogni leva futura: resume da B-1 POST_RELEASE (deve restare >= -24) e
  da B-3 (deve chiudere i 5 buchi senza revert). Runner: `scripts/benchmark/resume-bench.sh`.

## Predittore early-doom (primi dati, n=3 costruzioni)
- post-CH B-TIMESLOT-1 gate: -45 (canonical-fix, consegna -24) vs -650 (no-import, consegna
  -667). La separazione e' NETTA gia' al primo confine post-CH: il gate B-TIMESLOT-1 e' un
  predittore early-doom efficace con soglia ~-100 (n basso, da confermare col census
  badseed-frequency sotto config appaiata - TASK-539 nota).
