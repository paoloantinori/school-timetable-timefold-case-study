# Solver XML Configurations

> **Last Updated**: 2026-08-05 (audit vs `solverConfig.xml` attuale: tutti i knob (move selector, acceptor, forager, termination) verificati; §3 M-LA riconciliato con la tabella CLAUDE.md).
> **Timefold**: 2.3.0 (community). Architettura **Lesson-singola** (5-phase pipeline); `LabBlock` e i
> file `preAssignmentPhase1a/1b` non esistono piu'.
> **Pipeline authority**: il PERCHE' delle 5 fasi, il timing tipico e la relazione con la
> pre-assegnazione vivono in `claudedocs/solver-phases.md`. Questo doc copre i **knob di configurazione
> XML** (move selector, acceptor, forager, termination) e i finding sperimentali (pillar moves, LA).

Analizza i file di configurazione XML del solver. **Aggiornarlo quando un XML del solver cambia**
(checklist in fondo). Regola "puntatori, non copie": cio' che vive autoritativamente altrove (pipeline
in `solver-phases.md`, vincoli in `CLAUDE.md`) qui e' puntato, non duplicato.

## Cosa e' superato dal freeze pre-campagna

Fino al 2026-03 questo doc descriveva l'architettura **Block Entity** (`LabBlock` + `Lesson` separate,
vincoli split L-L/L-B/B-B, 7 fasi) su Timefold 1.31.0. Superato da: TASK-228/267 (LabBlock
denormalizzato in `Lesson` pinnate), TASK-418 (pre-assegnazione ora CSP in `HybridPhase1aService`,
nessun XML), TASK-420 (fase LS RuinRecreate inerte rimossa, 6 -> 5 fasi), upgrade Timefold 2.3.0 (la
patch `IdentityHashSet` 1.31.0 e' obsoleta, fixato upstream 1.32.0).

## 1. Main Solver (`src/main/resources/solverConfig.xml`)

Score director: `constraintProviderClass = SchoolScheduleConstraintProvider` (Constraint Streams,
incrementale), `initializingScoreTrend = ONLY_DOWN` (tutti penalize tranne M5; abilita scorciatoie CH).
Terminazione globale: `secondsSpentLimit 28800` (8h, safety net per run incustoditi).

Knob per-fase (la narrativa di pipeline in `solver-phases.md`):

| Fase | Tipo | Move selector (peso) | Acceptor / Forager | Termination |
|------|------|----------------------|--------------------|-------------|
| 1 | custom CH `DayClusteredConstructionPhase` | placement deterministico (clustering giornaliero + force-place); **room=NULL by structure** | n/a | n/a |
| 2 | LS Compactness | change `timeslot` 5.0, swap `timeslot` 3.0, **pillarChange 2.0**, **pillarSwap 1.0**; `CompositeMoveFilter` | `entityTabuSize 7` / `acceptedCountLimit 1000` | diminishedReturns (PT2M, 0.1) + `secondsSpentLimit 3600` + `unimprovedSecondsSpentLimit 600` |
| 3 | custom `CompactionPhase` | repair deterministico (chiude gap H17, improve-or-noop) | n/a | n/a |
| 4 | CH Room Assignment | queuedEntityPlacer DESCENDING, change `room` | n/a | n/a |
| 5 | LS Room Refinement | change `room` 2.0, swap `room` 1.0 | `entityTabuSize 7` / `acceptedCountLimit 1000` | diminishedReturns (PT1M, 0.05) + `secondsSpentLimit 600` |

Note XML (verificate sul codice, non dal commento XML):
- **Deferred-room pattern (TASK-183) ANCORA VALIDO.** La Fase 1 posiziona i timeslot e **NULLa le
  stanze** per structura (TASK-446 Alt-B: `DayClusteredConstructionPhase.assignVariables` fa
  `Moves.change(room, lesson, null)` per ogni lezione, anche su re-solve sporco). Le Fasi 2-3
  ottimizzano timeslot a stanze null. La **Fase 4 e' l'UNICA assegnazione di stanza** (sulla griglia
  completa); la Fase 5 la rifinisce. (Il commento XML Fase 1 "+ room assignment" e' STALE, pre-TASK-446.)
- **Tabu `entityTabuSize 7`** in entrambe le LS: vincitore del benchmark 2026-03-23, raccomandato da
  Timefold per ~10K entita'.
- **`acceptedCountLimit 1000`**: molti move valutati per step (con Tabu, considera downhill non-tabu
  largamente) per uscire dai minimi locali.
- **`CompositeMoveFilter`** (Fase 2): filtra i `CompositeMove(room, timeslot)` auto-generati che
  sprecavano ~8% del budget eval con zero miglioramenti (move-stats 2026-03-24).
- **Terminazione a 3 livelli** (Fase 2): `diminishedReturns` (relativo) + `unimprovedSecondsSpentLimit`
  (assoluto, copre il baseline-degenerato su re-solve) + `secondsSpentLimit` (wall-clock). Complementari
  (TASK-381 L3).

## 2. Pillar moves: SPERIMENTALI, valore NON PROVATO (TASK-332 aperto)

I pillar selector (`pillarChangeMoveSelector` 2.0, `pillarSwapMoveSelector` 1.0) sono nella **Fase 2**,
marcati `EXPERIMENTAL` nel commento XML ("preserving consecutive patterns S7 while resolving teacher
conflicts H1"). Spostano tutte le `Lesson` di un timeslot insieme.

**Stato della conoscenza (onesto):** il loro valore sull'architettura corrente **non e' dimostrato**.
L'unico benchmark pillar (`claudedocs/research/pillar-moves-benchmark-results-20260317.md`) era
**confounded**: il baseline stallava in Fase 3 per un bug non correlato, le due config fermavano a fasi
diverse, confronto invalido; i pillar furono merged su quella base. TASK-332 (A/B pulito
apples-to-apples, a terminazione pari) e' **ancora To Do**: Variant A = config corrente coi pillar,
Variant B = pillar rimossi e peso ridistribuito su change/swap. I pillar sono inoltre **O(n^2) da
generare**: se non aiutano, **costano throughput** (TASK-130, maxSubPillarSize, da assorbire in
TASK-332). La posizione attuale (2.0/1.0) riflette l'eredita' del merge confound, non una misurazione.

> Questo doc NON afferma che i pillar aiutino. Finch' TASK-332 non gira, vanno trattati come esperimento
> non-validato che paga un costo di generazione.

## 3. Lab Assistant (LA): M-LA medium, entita' `LabAssistant`, H-D1 rimosso

Il "doppio" (co-teaching) e' modellato **teacher↔room**, non teacher↔course. `LabAssistant`
(`domain/LabAssistant.java`) associa un `Teacher` a un `roomTag` ("acconciatura", "moda", "meccanica",
"termoidraulica"; coincide coi `Room.tags`). Il solver **decide** a quali sessioni l'assistente assiste
(preferenza), non glielo richiede (vecchio approccio hard).

- **`M-LA - Lab assistant conflict`** (`BaseConstraintProvider.java:800-815`, MEDIUM `ONE_MEDIUM`):
  penalizza la lezione dell'assistente che overlap (stesso timeslot) con un lab nella stanza del suo
  `roomTag`. Dettagli del filtro (restringimento a ~4 docenti) nel codice. Origine: doppio R16
  (T14, T20), workbook "Vincoli DA ANALIZZARE" righe 96-97.
- **Una sola `M-LA` registrata** (grep: una riga, `BaseConstraintProvider:814`; il commento citava
  "M-LA.3"). Le ".1-.4" erano aspirazionali/storiche e sono state riconciliate: la tabella
  CLAUDE.md ora registra l'unico `M-LA`.
- **`H-D1` NON registrato**: censimento TASK-446, nessun `.asConstraint("H-D1...")`. La semantica
  doppio/assistente vive interamente in M-LA (medium); "H-D1" sopravvive solo in commenti.

## 4. Config di esperimento (18 file `solverConfig-*` / `solverBenchmarkConfig*`)

Varianti per A/B e benchmark, non di produzione: `solverConfig-ch-only.xml`, `solverConfig-phase3-{60,120,180}s.xml`,
`solverConfig-smoke.xml`, e la famiglia `solverBenchmarkConfig*.xml` (Tabu-vs-VND, termination test,
move-stats V1/V2/V3, pinned, honest-2026). Snapshot d'esperimento: dettagliarli qui divergerebbero
subito; consultarli direttamente. Runnabili: `./scripts/core/test.sh` (test JVM),
`./scripts/benchmark/run-benchmarker.sh` (benchmark). (Esistono anche 2 varianti in
`src/main/resources/benchmark/` non conteggiate qui.)

## 5. Pre-assegnazione: CSP, nessun XML

La pre-assegnazione (acconciatura/labs/professionalizzanti pin) gira in `HybridPhase1aService` come
**CSP** (Java puro, `DayAssignmentCsp` + `GreedyRotationAssigner`), non via solver XML. I vecchi
`preAssignmentPhase1a/1b.xml` sono rimossi (TASK-418). Autorita': `claudedocs/human-planner-heuristics.md`
(5-step) e `claudedocs/pre-assignment-algorithms.md`.

## Maintenance Checklist

- [ ] Aggiornare le tabelle knob se move selector / acceptor / forager / termination cambiano.
- [ ] Per cambi di PIPELINE (fasi, nomi): aggiornare `solver-phases.md` (autorita') + seguire
  `.claude/rules/solver-config-sync.md`.
- [ ] Validare contro il Timefold XSD: `./scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml`.
- [ ] Aggiornare "Last Updated" in cima.
