---
id: TASK-643
title: 'Timefold PR: executeTemporaryMove exception-safe (restore su throw)'
status: Done
assignee: []
created_date: '2026-09-23 04:52'
updated_date: '2026-09-23 09:22'
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
PR 1 di 3 (le piccole indiscutibili, apri-dialogo prima della flagship). EVIDENZA NOSTRA: rc-1 executeTemporaryMove non ha try/finally; un throw a meta' mossa lascia la working solution MUTATA (colpito dal 638: ora il nostro evaluate() e' fatal-by-design con IllegalStateException perche' lo stato non e' garantito, vedi ViolationScopedSteepestFinisher.evaluate javadoc). PROPOSTA upstream: restore dello stato precedente alla throwable durante executeTemporaryMove (o API exception-safe esplicita documentata). Target: 2.7.0 finale (29/09) o 2.8. Prima di aprire: riprodurre con test minimale sul loro test harness (MoveTester). Ricordare /pstack:unslop prima di postare.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SETACCIO UPSTREAM 23/09 (agente read-only, verifiche meccaniche): VERDETTO = NON preparare la PR. La mancanza di try/finally e' DELIBERATA: PR #2649 (merged 11/09, 6 giorni prima del tag rc-1) ha rimosso AutoCloseable+finally con rationale nel javadoc di EphemeralMoveDirector (undo di azioni half-applied ritenuto piu' pericoloso della terminazione; wording 'treat all exceptions as fatal' chiesto in review da Christopher-Chianelli). Storia: #2596 (agosto) aveva introdotto l'undo-in-eccezione, #2649 l'ha rimossa. #2666/#2667 risolvono il caso flawed-move a monte della selezione. La forma PR riaprirebbe una decisione appena presa senza nuove evidenze, pre-confutata dal loro javadoc. VIA ONESTA: discussion con (a) undo atomico-sicuro (rollback journal) o (b) variante opt-in best-effort restore. Bozza riscritta in forma discussion dall'agente; NESSUN invio senza maintainer.

DIRETTIVA maintainer 23/09 sera (memoria open-core-posture-maintainer): CONTRIBUTI UPSTREAM ACCANTONATI. La discussion non parte; il draft resta in claudedocs/upstream/ come archivio di ricerca (era gia' pronto post-adversarial, POST-WITH-EDITS con 7 cure). Cio' che ci resta di valore: la conoscenza del contratto fatal-on-throw deliberato (valida il nostro design), e il caso d'uso documentato se un domani servira'.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
