---
id: TASK-644
title: >-
  Timefold PR: accessor tipizzato PlanningVariableMetaModel (eliminare il
  downcast)
status: Done
assignee: []
created_date: '2026-09-23 04:52'
updated_date: '2026-09-23 08:55'
labels:
  - upstream
  - claude-planned
dependencies: []
references:
  - src/main/java/com/school/planner/solver/ViolationScopedSteepestFinisher.java
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PR 2 di 3 (piccole). EVIDENZA NOSTRA: per ottenere la PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> si naviga getMetaModel().entity(Lesson.class).variable("timeslot", Timeslot.class) e si DOWNCASTA da VariableMetaModel: l'accessor tipizzato non esiste in rc-1 (il downcast e' "la via sanzionata", fallisce loudly su list variable = voluto, ma e' un cast che ogni consumer deve ripetere; 7 copie nostre, vedi task consolidamento). PROPOSTA upstream: variable(...) overload che ritorna direttamente PlanningVariableMetaModel (o un asPlanningVariable()). Target 2.7.0 finale+. Riprodurre il bisogno con un test minimale. /pstack:unslop prima di postare.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SETACCIO UPSTREAM 23/09: VERDETTO = SUPERATA. L'accessor tipizzato ESISTE GIA' in rc-1: GenuineEntityMetaModel.basicVariable(name, Class) ritorna PlanningVariableMetaModel direttamente (PR #2037 gennaio 2026, issues #2030/#2293). Verificato da noi nel jar rc-1 (javap): genuineEntity(Class).basicVariable(String,Class) firmato tipizzato. I nostri 7 downcast con entity().variable() sono un bug LOCALE nostro (usavamo l'accessor generico). AZIONE: sostituzione locale in TASK-642 (che diventa banale: swap meccanico genuineEntity().basicVariable(), niente helper downcast). Residuo upstream: solo micro-discussion sulla deprecazione degli accessor generici, priorita' bassa. Task upstream chiuso come such; la parte utile confluisce nel 642.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
