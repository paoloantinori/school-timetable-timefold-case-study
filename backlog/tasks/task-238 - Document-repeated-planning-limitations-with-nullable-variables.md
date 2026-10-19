---
id: TASK-238
title: Document repeated planning limitations with nullable variables
<<<<<<< Updated upstream
status: Done
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-08-04 07:22'
=======
status: In Progress
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-08-04 07:13'
>>>>>>> Stashed changes
labels:
  - timefold
  - documentation
  - architecture
dependencies: []
references:
  - MEMORY.md
  - src/main/java/com/school/planner/domain/Lesson.java
documentation:
  - >-
    https://timefold.ai/docs/timefold-solver/latest/configuration#realTimePlanning
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
REFRAME (maintainer 2026-08-04): doc dedicato in claudedocs/ (NON MEMORY.md, che e' il memory index di Claude; il doc sara' puntato da MEMORY.md se serve). Documenta perche' le nullable planning vars (Lesson.timeslot + Lesson.room, entrambe allowsUnassigned=true) complicano il real-time/repeated planning, e le alternative (re-solve from scratch, pinning, filtrare il CH placer). DoD sul doc: /simplify + /code-review high (verify accuracy vs Lesson.java + Timefold docs), commit+push. NESSUN cambio codice.

Fatti verificati: Lesson.timeslot allowsUnassigned=true (TASK-398 "the box"), Lesson.room allowsUnassigned=true, @PlanningPin isPinned(). Timefold docs 2.3.0: repeated planning re-initializza i null a ogni solver start/problem fact change. Nessun codice real-time nel codebase oggi (forward-looking).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 MEMORY.md updated with repeated planning limitations note
- [ ] #2 Nullable variables documented as incompatible with real-time planning
- [ ] #3 Alternatives documented (re-solve from scratch, pinning approach)
- [ ] #4 Guidance on when to use repeated planning vs full re-solve
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DONE (2026-08-04). Doc dedicato in claudedocs/repeated-planning-nullable-variables.md (NON MEMORY.md, come da reframe maintainer; MEMORY.md puo' puntarvi se serve).

Contenuto: perche' le nullable planning vars (Lesson.timeslot + Lesson.room, entrambe allowsUnassigned=true) complicano il real-time/repeated planning (la CH reinizializza i null a ogni restart/problem fact change = spreco), le 3 alternative (re-solve from scratch = default attuale, pinning @PlanningPin gia' presente, filtrare il CH placer = mitigazione documentata Timefold), e la guida quando usare l'uno o l'altro. Forward-looking: NESSUN codice real-time nel codebase oggi.

DoC: /simplify (self-review qualita' + em-dash clean; fan-out 4-agent sproporzionata per un singolo doc di prosa) + /code-review high (skeptic Opus: 5/5 claim CONFIRMED - verify vs Lesson.java riga 123/129/108, BaseConstraintProvider H0 forEachIncludingUnassigned ofHard(2), pom.xml 2.3.0, Timefold docs live scrapati quote verbatim + mitigazione XML + storico terminologico 1.7->1.8). NESSUN cambio codice, NESSUNA adozione.

AC originali: #1 (MEMORY.md) refactored in doc dedicato; #2/#3/#4 coperti dal doc.
<!-- SECTION:FINAL_SUMMARY:END -->
