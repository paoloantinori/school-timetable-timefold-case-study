---
id: TASK-676
title: >-
  Consolidare il resolver del metamodel timeslot (7 copie private + letterale
  "timeslot" inline)
status: Done
assignee: []
created_date: '2026-09-22 20:07'
labels:
  - refactor
  - solver
  - metamodel
dependencies: []
type: enhancement
ordinal: 324000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla /simplify del PASSO 7 catena (22/09): il codebase ha 7 copie private della stessa navigazione metamodel per la variabile "timeslot" di Lesson su rc-1, nessuna condivisa, e il letterale "timeslot" e' inline ovunque (rename romperebbe solo a runtime):

1. ViolationScopedSteepestFinisher.timeslotMetaModel (director-based, downcast PlanningVariableMetaModel)
2. PairBlockShiftMoveIteratorFactory.timeslotVariableModel:133-145 (IDENTICA in forma, director-based)
3. HarvestMove.TIMESLOT_VARIABLE:31-34 (director-FREE: PlanningSolutionMetaModel.of(SchoolSchedule.class, Lesson.class).genuineEntity(Lesson.class).basicVariable("timeslot", Timeslot.class))
4. CompactionPhase.java:53-55 (inlined)
5. DayClusteredConstructionPhase.java:286-288 (via PhaseCommandContext)
6. KempeCellNeighborhoodProvider.resolveTimeslotVariable:85-93 (bench-only)
7. ScheduleService (7 ulteriori letterali "timeslot")

Cura: un unico resolver condiviso (la forma director-free di HarvestMove e' la piu' pulita per i siti statici; quella director-based per i siti con director vivo) + costante unica per il nome variabile. Esce dal perimetro del PASSO 7 (mandato: commit solo finisher+banco), per questo e' task proprio. Nota il commento esistente in PairBlockShift: "genuineVariable() is generic-unfriendly" - il downcast e' la forma che compila.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 RINUMERATO da TASK-641 a TASK-676: il file condivideva l'id con il task room-aware ratified 23/09 (chiuso oggi). Il lavoro qui (consolidare le 7 copie private del resolver del metamodel timeslot) resta APERTO e non va confuso con il TASK-652 (RoomAvailability, Done: quello era il predicato stanza-libera-a-slot).

2026-10-03 triage batch: DUPLICATO di TASK-642 (consolidamento resolver PlanningVariableMetaModel, stesso perimetro)
