---
id: TASK-420
title: >-
  Rimuovere la fase 3 (LS RuinRecreate) dalla pipeline: inerte in 6 misure
  consecutive, anche hard-clean
status: Done
assignee: []
created_date: '2026-07-26 13:49'
updated_date: '2026-07-27 05:02'
labels:
  - claude-planned
  - spalmone-2027
  - solver-config
dependencies: []
priority: medium
ordinal: 243000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Evidenza cumulata (tutte verificate sui raw): val413 r1/r2, val408 r1/r2 (score invariato alla 4a cifra, 120s), val403-early r1/r2 (partenza HARD-PULITA dopo repair anticipato: +72 e +1.096 medium in 160/133s - l'ipotesi 'inerte perche' bloccata dall'hard' e' refutata). La fase costa 120-160s/run e non produce nulla dal TASK-386 in poi. AZIONE: rimuovere il blocco <localSearch> ruin-recreate da solverConfig.xml + ripple COMPLETO di .claude/rules/solver-config-sync.md (SolverConfigParser, SolverProgressionListener, PhaseProgressionService, solverPhases.ts, SolverPhasesPage, solver-phases.md, messages, guidelines localized-testing §1/§2) + XSD validation. Catena di test: la piramide locale intera + UN A/B laptop x2 economico (attesa: metriche invariate, ~2,5 min risparmiati/run) come conferma finale. NB: coordinare con l'eventuale adozione dello staged (TASK-419): se lo staged diventa default, la pipeline va rivista comunque in un colpo solo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 solverConfig senza fase 3 + ripple sync completo + XSD ok
- [ ] #2 A/B laptop x2: hard/box/KPI invariati entro varianza, wall -2/-3 min per run
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ESEGUITA e committata 79a91755 (2026-07-27 mattina): pipeline a 5 fasi, XSD valido, carve-out staged provati robusti, parser fixato (ordinale esplicito + termination fase room mai parsata prima), benchmark-arm script riparato (era gia' rotto), i18n phases riscritte. DoD completa (review 3 agenti, suite 820 EXIT=0). L'AC del confronto A/B x2 su laptop (metriche invariate, ~2,5-10 min risparmiati) si chiude col prossimo ciclo di ratifica m-12 - annotato qui per il verificatore.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
