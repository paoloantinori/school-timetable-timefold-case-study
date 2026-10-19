---
id: TASK-544
title: >-
  Byteman sulle classi reali: race-injection del decider MT-LS (TASK-543 passo
  2)
status: Done
assignee: []
created_date: '2026-08-23 20:49'
updated_date: '2026-08-23 21:39'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Passo 2 di TASK-543: il banco stand-in (BytemanRaceInjectionTest, gia' merged) valida il pattern; ora puntare le regole Byteman alle classi reali. Target: il decider MT-LS (solver/multithreaded/) e/o la finestra publish-then-fill del dirty tracker Hibernate su Lesson, come documentato in claudedocs/research_hibernate-enhancement-mtls-race_2026-08-22.md. Lavoro avviato SU FEDORA (direttiva maintainer 23/08 sera). Metodo: censimento mutazione/lettura, regola freeze sul punto reale, test che dimostra la divergenza score con interleaving forzato, prevalidazione single-writer nello stesso banco. Guida tool: claudedocs/byteman-guide-for-agents.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Censimento dei punti di mutazione vs lettura nel decider MT-LS (quali metodi scrivono lo stato, quali thread leggono durante la valutazione)
- [ ] #2 Regole Byteman puntate alle classi REALI (non stand-in) che congelano la finestra identificata
- [ ] #3 Test deterministico che dimostra la corruzione (score valutato != score ricalcolato) con interleaving forzato, verde-rosso-riproducibile
- [ ] #4 Prevalidazione: con serializzazione (o fix single-writer quando esiste) lo stesso test non mostra corruzione
- [ ] #5 DoD: /simplify + /review-local high + test.sh EXIT 0
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Censimento (AC#1) completato con scoperta rilevante: il decider MT-LS segue gia' il modello upstream (ogni move thread ha il proprio childScoreDirector via createChildThreadScoreDirector(MOVE_THREAD), step chasing con rebasing e barrier): in stato steady NON muta entita' condivise. La race documentata 22/08 sta nel componente dichiaratamente single-thread: SimpleFieldTracker Hibernate (finestra copyOf->aastore verificata col javap su 7.2.4.Final: putfield names offset 34, aastore offset 49).

AC#2/#3/#4 IMPLEMENTATI su Fedora (commit d693d11c): regola Byteman AFTER WRITE names sul tracker REALE, test SimpleFieldTrackerRaceTest: NPE del crash 22/08 riprodotto DETERMINISTICAMENTE in 3s (senza app, tracker istanziabile direttamente), prevalidazione single-writer nello stesso banco (raceMode=false: nessun danno). 2/2 col profilo byteman, 2 skipped senza (suite intatta).

Resta AC#5 (DoD: /simplify + /review-local + test.sh) da eseguire sul diff d693d11c. Prossimo passo opzionale: collegare il banco al punto reale del decider dove due thread toccano lo stesso tracker (setup/rebase), una volta identificato con TASK-543 fase 1.

AC#5 (DoD) CHIUSA (3505d8e9): /simplify 4 agent (helper condiviso runWithFrozenWriter, costanti uniche 30s/10s, @AfterEach, commenti a puntatori, BIND->DO guard leggera), /review-local high PULITO (unico sub-threshold: javadoc predizione corretto), test.sh 4/4 EXIT 0 col profilo byteman, 4 skipped senza. TASK-544 completata.
<!-- SECTION:NOTES:END -->
