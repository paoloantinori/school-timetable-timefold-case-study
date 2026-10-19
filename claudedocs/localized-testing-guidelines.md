# Test localizzati per fase — linee guida di sviluppo (2026-07-24)

Direttiva di metodo (maintainer, 2026-07-24): massimizzare le modifiche testabili in modo
LOCALIZZATO, partendo da uno snapshot corrispondente a una fase precedente, invece di
rieseguire ogni volta il ciclo completo (rebuild → profilo fresco → import-calendario →
pre-assign → solve multi-ora). Il ciclo completo resta il gate di ADOZIONE, eseguito meno
frequentemente e sul laptop.

Basato su ricognizione misurata (val414-run1/run2, phases.json + surefire reports reali):
non su stime.

## 1. Perché: dove va davvero il tempo

Ciclo completo ≈ 67 min di solve + minuti di rebuild/profilo/import/pre-assign. La
distribuzione del solve (val414, due run concordi):

| Fase | Cosa | Durata reale | Guadagno |
|---|---|---|---|
| 1 CH `DayClusteredConstructionPhase` | template+round-robin+box (timeslot+room) | **6s** | hard da −∞ a −1223 |
| 2 LS Compactness | timeslot (change/swap/pillar, tabu 7) | **3600s (cap)** | hard −1223 → −10/−13, medium −0,4M |
| ~~3 LS RuinRecreate~~ | timeslot (ruin 5-20) | ~~120s~~ | **score INVARIATO nei 2 run → RIMOSSA da TASK-420 (2026-07-27)** |
| 3 (era 4) `CompactionPhase` (BoxRepair+eviction+compaction) | timeslot+room | **0,7s** | hard −10/−13 → −2 |
| 4 (era 5) CH Room | room | **0,6s** | invariato |
| 5 (era 6) LS Room Refinement | room | **284-324s** | hard −2 → −1/−2, soft +20k |

Le fasi deterministiche (1, 3, 4) costano <10s e producono quasi tutto il guadagno hard;
il 90% del costo è UNA fase stocastica (2). Corollari: (a) testare le fasi deterministiche
in isolamento è quasi gratis ed è già possibile; (b) ogni esperimento sulla fase 2 è
intrinsecamente costoso → va sul laptop, e ogni run laptop deve produrre artifact riusabili;
(c) la vecchia fase 3 inerte è stata rimossa (TASK-420) dopo 6 misure consecutive a
contributo nullo: la numerazione qui sotto è quella nuova a 5 fasi.

## 2. Contratto di stato per fase (cosa serve per ripartire da lì)

| Fase | Variabile mossa | Pinned esclusi | Riparte da stato parziale? |
|---|---|---|---|
| 1 | timeslot+room | sì, esplicito (`!isPinned() && timeslot==null`) | **sì: è no-op sulle lezioni già assegnate** (tracking maps le indicizzano soltanto) |
| 2 | timeslot | via `@PlanningPin` nativo | LS rivisita tutto il non-pinnato, assegnato o no |
| 3 | timeslot+room | sì (`lesson.pinned` letto direttamente) | sì: deterministico, improve-or-noop, `conflictsAdded==0` garantito |
| 4 | room | via `@PlanningPin` | ⚠️ VERIFICATO SUI SORGENTI Timefold 2.3.0 (skeptic 2026-07-25, senso OPPOSTO all'inferenza precedente): la CH SALTA le entità con room già assegnata (reinitialize-filter attivo) e per quelle con room null sceglie SEMPRE null (`NullAllowingValueRange` antepone null, ONLY_DOWN+FIRST_NON_DETERIORATING lo accetta al pareggio; Timefold esegue di proposito la mossa null→null: "try to not assign that value"). Quindi la CH room non assegna MAI una room reale: le room vere arrivano dalla fase 1 e dalla LS di fase 5 — vedi TASK-417. Un harness offline su snapshot a room null DEVE bypassare questa CH (pre-assegnazione greedy), come fa RoomPhaseSnapshotTest |
| 5 | room | via `@PlanningPin` | LS |

Lo snapshot minimo per ricostruire uno stato di fase è il dump
`group|date|hour|subject|teacher|pinned` (lessons.txt) + l'elenco delle boxate
(boxed.txt): è ciò che `BoxRepairCheckpointTest` già ricostruisce in memoria. Lo snapshot
FEDELE è l'export JSON (`api.sh export`): include timeslot/room/pinned anche null, ma
vedi i caveat import (§6).

## 3. La piramide dei test localizzati (esistente, runtime misurati)

Dal più economico al più costoso — usare SEMPRE il livello più basso che copre la
modifica, salire solo se serve:

| Livello | Strumento | Runtime | Copre |
|---|---|---|---|
| L0 | `SchoolScheduleConstraintProviderTest` (ConstraintVerifier, 311 test) | **3,3s** | semantica isolata di ogni vincolo (pesi, esenzioni, edge) |
| L0 | `BoxVrpFeasibilityDiscriminatorTest` | **1,3s** | ipotesi A/B sulle boxate di un run reale (gate VRP vero + occupazione) |
| L1 | `GoldenSolutionRegressionTest` | **9,3s** | compatibilità col golden di QUALSIASI modifica a modello/vincoli (~30 pin zero-tolerance + residui calibrati) |
| L1 | KPI Python offline (`cadence`, `resemblance`, `daily-fill`, `day-start`, `moda-knot`, `hole-topology`, viz) | secondi | qualunque dump lessons.txt: golden, run storici, dump di costruzione |
| L2 | `BoxRepairCheckpointTest` (checkpoint val398/val410) | **48s** | fase 3, ex 4 (repair/eviction/compaction) su stati FINALI di run reali |
| L2 | `WeeklyTemplateConstructionRealSeedTest` (+ dump `target/construction-lessons.txt`) | **53s** | fase 1 completa su CSV reali: ricorrenza, full-day, stamp stats; il dump alimenta i KPI |
| L2 | `CalendarioImportRealSeedStageTest` | **3,2s** | pinning Stage/MODA (conservazione ore) |
| L3 | suite intera `./scripts/core/test.sh` | **~6,2 min** | tutto il precedente insieme (4 classi = 78% del tempo) |
| L4 | smoke solve locale con budget ridotto | ~10-15 min | trend, MAI evidenza di adozione |
| L5 | **ciclo completo A/B x2 sul laptop** | ~1,5h/run + setup | **unico gate di adozione** (la LS è stocastica: ogni checkpoint fissa UNA traiettoria — precedente TASK-401) |

## 4. Matrice: tipo di modifica → catena minima di test

| Modifica | Catena locale (ordine) | Il ciclo completo serve per |
|---|---|---|
| Peso/tier di un vincolo esistente | L0 verifier (asserzione peso) → L1 golden guard → score su snapshot (§5.2, oggi manuale via server+import) | effetto sulla ricerca (tradeoff medium) |
| Vincolo NUOVO (es. M32) | L0 verifier (tutti i casi+esenzioni) → L1 golden guard (pin nuovo) → L2 fixture costruzione se interagisce col CH → magnitudo su snapshot reale | comportamento della LS col nuovo segnale |
| Filtro VRP / `getAvailableTimeslots` | L0 discriminatore (le boxate cambiano ipotesi?) → L1 golden guard → L2 fixture costruzione (stamp skip) | fattibilità end-to-end |
| Template/costruzione (fase 1) | L2 fixture costruzione (ricorrenza/full-day/stamp) → KPI sul dump → L1 guard | erosione a fine solve (solo un solve la misura) |
| Repair/eviction/compaction (fase 3, ex 4) | L2 checkpoint harness (secondi, delta esatti H17/conflitti/box) | tenuta su traiettoria LS fresca |
| Config LS (mosse, tabu, pesi, termination fase 2) | L0 verifier → L1 golden guard → **`Phase2SmokeSnapshotTest`** (LS da snapshot, budget 90s, TREND non adozione; `PLANNER_TEST_PH2SMOKE=1`) → score-a-snapshot (delta medium per vincolo) → laptop A/B | tutto: è benchmark territory per definizione |
| Fasi room (4/5), H2/H-M2/M3/M-LA | **`RoomPhaseSnapshotTest`**, due modalita': (a) pipe (`PLANNER_TEST_ROOM_PHASE=1`) LS di refinement room da snapshot con room null + greedy pre-assign, `RealSeedFixture.seedRooms()` replica le 18 Room reali da `rooms.csv`; (b) export (`PLANNER_TEST_ROOM_PHASE_EXPORT=<path>`, richiede anche `PLANNER_TEST_ROOM_PHASE=1`) via `SnapshotFixture.loadFromExport`, parte dalla room REALE del run (incluse quelle genuinamente null), fedele anche su `Course.isLab`/`requiredRoomTags`. Entrambe con slice di gruppo per restare offline | effetti su popolazione completa (lo slice è offline; l'adozione resta laptop) |
| Import/seed/dati (CSV, DataGenerator) | test real-seed dedicati (pattern `RealSeedFixture`) + derive-golden-*.py | niente, se i numeri di conservazione tornano |

Regola di composizione: una modifica che tocca più righe usa l'unione delle catene, e la
DoD (test.sh EXIT=0 dall'orchestratore) resta sempre.

## 5. Gap infrastrutturali — STATO (TASK-415, 2026-07-24)

I 4 gap del §5 originale sono ora coperti da harness locali. Tutti i nuovi harness sono
**TREND/diagnosi, MAI adozione** (un snapshot fissa UNA traiettoria — TASK-401): l'adozione
passa sempre dall'A/B laptop x2. Tutti gli harness lenti (>60s o a budget) sono gated via
variabile d'ambiente (`@EnabledIfEnvironmentVariable`, DEFAULT OFF dalla suite), per non crescere
il runtime di default (~6,2 min).

1. **Snapshot a ogni confine di fase — CHIUSO.** `PhaseTransitionMarker.changeWorkingSolution`
   scrive ora, dietro `-Dplanner.phase-transition-dump.enabled=true` (DEFAULT OFF, costo
   trascurabile ~12k righe per transizione), un dump pipe
   `group|date|hour|subject|teacher|pinned` (lo stesso formato di `lessons.txt`) per ogni
   confine. `capture-run.sh` li raccoglie additivamente in `<out>/phase-transitions/` e gli
   lancia `resemblance-kpi.py`, così l'erosione della ricorrenza è attribuibile PER FASE
   (costruzione ~77% → finale ~37%, ma con il punto intermedio visibile), non solo end-to-end.
   **TASK-419:** ogni dump ora include anche un sibling `phase-N-slug-boxed.txt` (formato
   `group|teacher|subject` di `boxed.txt`) con le lezioni BOX di quel confine, così un checkpoint
   come `Task403EarlyRepairCheckpointTest` puo' ricostruire il box reale invece di zero (assente
   = zero box, coerente col contratto opzionale di `boxed.txt`).
2. **`SnapshotFixture` condiviso + score-a-snapshot — CHIUSO.** La ricostruzione
   lessons.txt/boxed.txt → stato in memoria è estratta in `SnapshotFixture` (gemello di
   `RealSeedFixture`), riusata da `BoxRepairCheckpointTest`/`BoxVrpFeasibilityDiscriminator`
   `Test` (assert byte-identici prima/dopo). `ScoreASnapshotTest` (env `PLANNER_TEST_SCORE_SNAPSHOT=1`,
   oppure un path snapshot) scora uno snapshot via `SolutionManager` con breakdown hard/medium/soft
   per vincolo, seeding i fact quota/expected-hours così il tier medium è comparabile col live per
   M20/M12 (NON per M32: su snapshot finale il template è vuoto, M32≈0 — TASK-416).
3. **Harness fase-2-smoke e fasi room — CHIUSO.** (a) `Phase2SmokeSnapshotTest`
   (env `PLANNER_TEST_PH2SMOKE=1` o path, budget 90s via `PLANNER_TEST_PH2SMOKE_BUDGET`): LS
   Compactness da snapshot, replica programmatica della phase-2 di `solverConfig.xml` (mosse,
   tabu, forager ancorati in modo descrittivo all'XML), output = trend score + ricorrenza. Il
   default è uno snapshot FINALE (val410-run2), quindi misura la tenuta della ricorrenza da lì; per
   partire dalla COSTRUZIONE, copiare `target/construction-lessons.txt` come `lessons.txt` in una dir
   (boxed.txt opzionale, assente = zero boxed) e puntarci l'env.
   (b) `RoomPhaseSnapshotTest` (env `PLANNER_TEST_ROOM_PHASE=1` o path): LS di room refinement da snapshot
   con room tutte null + greedy pre-assign + le 18 Room reali (`RealSeedFixture.seedRooms`, da
   `rooms.csv`), per la PRIMA volta esercita H2/H-M2/M3/M-LA offline (la CH room è bypassata, non
   assegna mai una room reale — TASK-417). **Nota costo:** la LS di room refinement sulla popolazione COMPLETA
   (12k) è proibitivamente lenta offline, quindi l'harness lavora di default su uno SLICE di 1 gruppo
   (~500 lezioni, override env `PLANNER_TEST_ROOM_PHASE_GROUPS`).
   **Modalita' export (chiude il gap "room null" di sopra):** `PLANNER_TEST_ROOM_PHASE_EXPORT=<path>`
   (richiede anche `PLANNER_TEST_ROOM_PHASE=1`) usa `SnapshotFixture.loadFromExport` per caricare
   `export.json.gz` (payload di `GET /api/schedule/export`): niente nulling, niente greedy, si parte
   dalla room REALE (incluse quelle genuinamente null) e dai flag `Course.isLab`/`requiredRoomTags`
   reali del run esportato, cosi' H2/H-M2/M3/M-LA si esercitano su uno stato che il solver ha
   davvero prodotto, non su uno stato sintetico costruito dall'harness.

   Nota invocazione: gli harness sono gated via VARIABILE D'AMBIENTE, non `-D`, perché
   `scripts/core/test.sh` rifiuta i `-D` espliciti e non propaga MAVEN_OPTS al fork surefire; es.
   `PLANNER_TEST_SCORE_SNAPSHOT=1 ./scripts/core/test.sh -c ScoreASnapshotTest`.
4. **Ogni run laptop = asset permanente.** capture-run.sh già cattura lessons/boxed/export +
   ora i dump di transizione: quando un run esibisce uno stato interessante (box non vuoto, nodo
   nuovo, pattern anomalo), promuoverlo a checkpoint committato e aggiungerlo ai parametri degli
   harness L2. I checkpoint invecchiano (val398 pre-eviction): tenerli finché documentano una
   classe di stati, non una versione del codice.

## 6. Caveat export/import (fedeltà dello snapshot)

- L'import (`ImportService`) è fedele per Lesson (incluse room null e lezioni
  unassigned), ma: **non tocca `DailyActivityQuota` né `GroupCalendarRule`** (restano
  quelli del DB target — importare su un profilo con seed diverso produce VRP diversi) e
  **tronca `labrotation` senza reinserirla** (noto: serve reset profilo dopo import).
  Quindi: snapshot reimportati SOLO su un profilo con lo stesso seed di origine.
- Il dump pipe (lessons.txt) perde lessonId e room: basta per gli harness in-memory e i
  KPI, non per un resume fedele della pipeline. Per il resume fedele serve l'export JSON, ora
  caricabile direttamente in memoria (nessun server, nessun import) via
  `SnapshotFixture.loadFromExport`: room/pinned/lessonId/courseId fedeli, `Course.isLab`/
  `requiredRoomTags`/`labBlockSize` fedeli, `GroupCalendarRule` seedato dal calendario reale
  di oggi (non nell'export), `DailyActivityQuota`/`LabAssistant` presenti nell'export ma non
  letti da questo loader (un chiamante che ne ha bisogno chiama `seedMediumTierFacts` sopra,
  come fa gia' per uno snapshot pipe). Vedi il caveat "fedelta' room/id vs leggerezza" nel
  javadoc di classe di `SnapshotFixture`.
- I due XML `preAssignmentPhase1a/1b-*.xml` erano scaffolding non referenziato e sono stati RIMOSSI (TASK-418, 2026-07-25)
  (scaffolding storico; il pre-assign reale è `HybridPhase1aService`, CSP puro pre-solve).
- ⚠️ ARTIFACT ROTTI NOTI (scoperti 2026-07-25 dal lavoro sul loader export): gli
  `export.json.gz` sotto `task406-results/` NON sono export veri, sono ~147 byte di stdout
  del comando gzippato ("Exporting solution to /tmp/..."): un harness puntato lì fallisce
  al gunzip/parse. Gli export di task405/task413/task408 sono verificati validi. Prima di
  usare un export storico come snapshot: `gzip -dc file | head -c 200` deve mostrare JSON.

## 7. Regole operative (riassunto per i coding agent)

1. Prima di toccare codice, classifica la modifica con la matrice §4 e dichiara la catena
   di test minima nel task.
2. Non lanciare MAI un solve completo locale per rispondere a una domanda che uno
   snapshot può rispondere; i solve completi vanno sul laptop (direttiva standing) e SEMPRE
   con `scripts/benchmark/capture-run.sh` (ogni run costoso deve lasciare checkpoint).
3. Il verdetto di un harness su checkpoint vale per QUELLA traiettoria: l'adozione passa
   sempre dall'A/B laptop x2 (val-baseline riusabili: non rifare il braccio A se esiste già
   allo stesso HEAD di modello).
4. Nuovi harness: seguire i pattern esistenti (`RealSeedFixture` per i CSV reali,
   `withCustomPhaseCommandList` per isolare una fase, delta-esatti negli assert come
   `BoxRepairCheckpointTest`).
