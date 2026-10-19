---
id: TASK-417
title: >-
  Fase 5 (CH room) e' un null-trap vestigiale: non assegna mai una room reale -
  valutare rimozione o riconfigurazione (benchmark)
status: Done
assignee: []
created_date: '2026-07-25 04:44'
updated_date: '2026-08-04 00:06'
labels:
  - claude-planned
  - spalmone-2027
  - solver-config
dependencies: []
priority: low
ordinal: 300
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scoperta della verifica skeptic 2026-07-25 (sorgenti Timefold 2.3.0 + solverConfig.xml + DayClusteredConstructionPhase), nata dall'harness RoomPhaseSnapshotTest di TASK-415: la fase 5 (queuedEntityPlacer su Lesson.room, allowsUnassigned=true) (a) SALTA ogni lezione con room gia' assegnata (CH costruita withReinitializeVariableFilterEnabled: isReinitializable=false -> emptyIterator), e (b) per ogni lezione con room null sceglie SEMPRE null: NullAllowingValueRange antepone null al value range (get(0)=null, iteratore ORIGINAL), initializingScoreTrend=ONLY_DOWN (solverConfig.xml:10) attiva FIRST_NON_DETERIORATING_SCORE, e ConstructionHeuristicDecider AMMETTE apposta la mossa non-doable null->null ("try to not assign that value") che pareggia lastStepScore e vince il pick early. Le room reali le assegnano la fase 1 (DayClusteredConstructionPhase.assignVariables/findAvailableRoom, roomsAssignedCount) e la LS di fase 6. Evidenza indiretta live: val413/val414 fase 5 = 0,5-0,6s a score invariato in tutti i run. E' comportamento Timefold BY-DESIGN documentato ("Failure to penalize unassigned variables can cause a solution with all variables unassigned to be the best solution"), NON un bug upstream (ricerca 2026-07-25: nessuna issue, workaround ufficiale = constraint di penalita').

OPZIONI da valutare (benchmark territory, direttiva primaria: la correttezza del modello non si tocca, questa e' pura configurazione di ricerca): (1) rimuovere la fase 5 (2 fasi custom + XML sync ripple: .claude/rules/solver-config-sync.md); (2) tenerla ma renderla utile: valueSelector SORTED con null per ultimo, o un vincolo che penalizza room null (pattern H0) cosi' il pareggio sparisce; (3) status quo documentato (la fase 6 LS gia' copre). Misura: tempi/score fase 6 con e senza; harness RoomPhaseSnapshotTest come test locale del ramo (2), laptop per il gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Decisione documentata (rimozione/riconfigurazione/status quo) con evidenza benchmark
- [ ] #2 Se si tocca solverConfig.xml: ripple completo di solver-config-sync.md + XSD validation
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
MISURA CHECKPOINT 2026-07-26 (RoomPhaseSnapshotTest, pipe mode su val419-staged-run1, slice G01 990 lezioni): greedy room pre-assign + SOLA fase 6 LS -> hard -165 -> 0 in 10s/197 step. La fase 5 CH non e' necessaria per l'hard pulito (conferma il null-trap: non assegna mai stanze reali). MA: rooms unassigned 320 -> 382 DOPO la LS (la fase 6 con allowsUnassigned ripulisce i conflitti anche DISASSEGNANDO). Quindi la domanda di questo task va riformulata: non 'rimuovere la fase 5' ma 'chi assegna le stanze che restano null' (candidati: greedy deterministico pre-fase-6 come nel harness, o config fase 6 senza unassign). Caveat: slice mono-gruppo, contesa cross-gruppo assente - TREND, mai adozione. Prossimo passo: stessa misura in export mode su un run full-grid.

--- Misura full-grid 2026-08-04 (VERDICT-417, read-only, NESSUN cambio solverConfig) ---

Misurato dal phase-progression di 3 run full-grid (task486-results: goodseed-on/off, bad-basin). La fase CH-room (solverConfig phase 4) ha score IDENTICO a inizio e fine su tutti e 3 i tier (~820ms) in tutti i run: delta-zero, nessuna mossa migliorativa. La fase LS-room (phase 5) fa -9404hard -> 0hard: tutto il room assignment e' li'.

Room-null PRIMA/DOPO fase 4 (inferenza rigorosa): la fase 5 e' room-only e porta l'hard a 0, quindi boxed=0 entrando la fase 4, quindi i -9404 sono interamente room-null. room-null = 9404 PRIMA della fase 4, INVARIATO a 9404 DOPO (la CH picka null, null-trap), -> 0 dopo la fase 5. Conferma il null-trap vestigiale su full-grid, estendendo il checkpoint 2026-07-26 (slice mono-gruppo).

AC#1 (evidenza benchmark documentata): FATTA in VERDICT-417. Lo SPAZIO DECISIONALE (3 opzioni: rimuovere fase 4 / reconfig SORTED+penalize-null / status quo) e' documentato; la DECISIONE e' GATED maintainer (tocca solverConfig.xml = percorso adottato). AC#2 (ripple solverConfig + XSD): N/A, solverConfig non toccato.

DoD: analysis+VERDICT (nessun nuovo script, nessun diff codice), quindi /simplify e /code-review high non si applicano (sono per diff di script); la DoD qui e' la qualita' del VERDICT (evidenza citata, inferenza dichiarata con catena logica, limiti onesti).

Doc: claudedocs/spalmone-2026-27/task417-results/VERDICT-417-2026-08-04.md. Dati grezzi: task486-results/ab-mtc-*/phases.json (3 run).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
