# HANDOFF 2026-07-29 (notte) - Tappa 4 + TASK-456 ratificati; certificazione NON convergita

Scritto a fine turno (zai). HEAD `5ee5171f` (sync remote, branch feature/cartesian-block-entity).
SUPERA gli handoff precedenti per TASK-445 (Tappa 4) e l'adozione TASK-456.

## Verde e pushato questo turno

1. **TASK-445 Tappa 4** (piano-giorni come pattern 13/14/15) - commit `063156cb`, pushato.
   - piano_giorni_compress.py + gate test_piano_giorni_roundtrip (byte-identico in-grid +
     onesta' per-pattern con boundaries TIGHT + fail-loud residui) + test_piano_giorni_negative
     (prova il fail-loud su 4 corruzioni). 172 pattern + 113 eccezioni spiegano 2352 date
     in-grid (0 residui); 43 righe off-grid (stage su sabati/chiusure) dichiarate in 99 GAP-2.
   - DoD piena: /simplify (9 finding), /code-review high (5 finding confermati, incl. il gate
     widening tightness critico: 70/172 erano widen-invisible ora 0), 5 gate Python EXIT 0,
     test.sh 879/0/0.

2. **TASK-456 ratifica** (lab-fallback default ON + esclusione stanza cap-0) - commit
   `5ee5171f`, pushato.
   - SchoolSchedule.isLabFallbackEnabled default TRUE (opt-out "false"); Room.isAssignable
     (capacity>0) esclude le stanze cap-0 dal dominio valori (Lesson.getAvailableRooms);
     lezioni PINNATE grandfathered (nullarle le congelerebbe room-null, H29 senza recupero);
     M3 residuo invariato. Regola GENERALE su capacity, mai un nome: oggi centra solo AULA
     POLIVALENTE (cap 0; R17 22). Verifica dati vivi combaciata con maintainer.
   - DoD: /simplify (5: Stream->List, isAssignable, commenti stale, test @BeforeEach,
     CLAUDE.md M-LF), /code-review high (2 commenti stale M-LF/RoomPhase + M-LF golden pin=0;
     claim di drift verificata e refutata count=0), test.sh 879/0/0, nuovo
     LessonAvailableRoomsTest (5 test). CLAUDE.md M-LF row aggiornato (default OFF->ON).

## Certificazione: AVVIATA, dump verificato, NON convergita (VERDICT scritto)

- Lancio corretto: `PLANNER_JAVA_OPTS="-Dplanner.phase-transition-dump.enabled=true
  -Dquarkus.hibernate-orm.schema-management.strategy=update" ./scripts/core/prod-start-and-wait.sh`
  (java -jar diretto). **Dump mechanism VERIFICATO** (phase-2 scritto, MAVEN_OPTS trap evitato).
- Solve fermato in **B-TIMESLOT-1** dopo ~26 min, score -11177hard. Decomposizione (analysis):
  H29 room-null -10457 (ATTESO, B-ROOM non partito) + H0 boxed -720 (360 lezioni); tutti gli
  altri hard = 0 (timeslot pulito). **TASK-456 NON implicato**: le stanze non sono ancora
  assegnate (H29 room-null lo conferma), quindi cap-0 exclusion + lab-fallback non si sono
  ancora applicati. Lo stallo e' la non-avanzata del ciclo staged oltre B-TIMESLOT-1 (la LS
  continua a trovare guadagni soft -> unimprovedSecondsSpentLimit non scatta -> la fase non
  termina -> niente demozione/B-ROOM).
- Artefatti in `claudedocs/spalmone-2026-27/certification-2026-07-29/` (phase dumps
  construction-end utili per task 459, analysis-partial, score-partial, VERDICT.md).
- I dump delle fasi successive (B-ROOM, repair) NON prodotti: task 450 (ritardo ven G11)
  e 460 (ricombinazione) restano scoperti su questa run.

## App/infra state

- App PROD JAR ancora UP (PID 41352, java -jar quarkus-run.jar, log /tmp/planner-prod.log),
  DB planner-pg UP (dati dev persistiti via schema=update). La prossima sessione puo'
  killarla (`./planner.sh kill-app`) o riusarla. La solve e' stata stoppata (NOT_SOLVING,
  stagedStage B-TIMESLOT-1).

## NEXT - priorita' per la prossima sessione

1. **Indagare la non-avanzata di B-TIMESLOT-1** (probabile: manca un secondsSpentLimit
   assoluto a complemento dell'unimproved, o il soft drift va capped). Confrontare col run
   di reference (il ciclo staged di campagna avanza). Potrebbe essere artifact del lancio
   prod o della config di terminazione. **A/B TASK-456-off** (`-D...lab-fallback.enabled=false`)
   per confermare formalmente la non-regression (atteso: stesso stallo, rooms fuori gioco).
2. **Rilanciare la certificazione** a ciclo sbloccato, poi `capture-run.sh` per la batteria
   KPI + VERDICT finale. Produce i dump per-fase per 450/459/460.
3. **Tappa 5 del workbook** (99_Validazione lato sistema, 23 controlli Python V1..V23) - NON
   iniziata (limite di contesto; pulita da iniziare fresca). Casi di prova: G14 990/115,
   G04 stage senza monte-ore, T01 80.2%. Nuovo modulo sotto scripts/workbook/ + gate.

## Regole invariate (valide per la sessione fresca)

checkpoint-first, NESSUN run live del solver senza verifica dump, NESSUNA adozione di
default, file protetti fermi allo scope, /simplify + /code-review high su ogni incremento,
test con exit code letto, commit e push a ogni tappa. Run dei gate workbook:
`scripts/workbook/.venv/bin/python scripts/workbook/<gate>.py` (PYTHONPATH=. da scripts/workbook).
