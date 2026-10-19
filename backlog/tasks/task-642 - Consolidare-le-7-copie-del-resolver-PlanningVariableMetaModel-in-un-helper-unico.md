---
id: TASK-642
title: >-
  Consolidare le 7 copie del resolver PlanningVariableMetaModel in un helper
  unico
status: To Do
assignee: []
created_date: '2026-09-23 04:52'
updated_date: '2026-09-23 08:55'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal wiring produzione 638 (27a515c2): il downcast da VariableMetaModel a PlanningVariableMetaModel per navigare variable("timeslot") e' duplicato in 7 siti (finisher, banco, diff test). Un unico accessor condiviso (stile ScoreCompat) con javadoc del perche' del downcast (rc-1: l'accessor tipizzato non esiste, la lista fallirebbe il cast loudly = voluto). NOTA: questa e' la "TASK-641" citata nei checkpoint 22-23/09 come aperta ma mai creata nel backlog (colpa mia, promessa senza owner; creata ora).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SETACCIO 23/09: la cura e' un semplice swap meccanico a genuineEntity(Lesson.class).basicVariable(name, type) che ritorna PlanningVariableMetaModel SENZA downcast (verificato javap su jar rc-1; disponibile da #2037). Sostituire in tutti i siti: niente helper, niente cast, il javadoc del perche'-del-downcast si cancella. Attendere la fine dell'A/B 641 per non intorbidare la timeline del finisher.
<!-- SECTION:NOTES:END -->
