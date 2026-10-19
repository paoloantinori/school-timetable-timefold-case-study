---
id: TASK-486
title: >-
  M-TC per-retry: ON solo nei tentativi multi-start >=2 (good basin non paga 2x,
  bad basin ottiene la cura room-tier)
status: Done
assignee:
  - claude
created_date: '2026-08-03 15:57'
updated_date: '2026-08-03 15:58'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
type: enhancement
ordinal: 305000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
maintainer (2026-08-03): la ratifica M-TC DEFAULT-ON PIATTO e' RESPINTA (wall-clock 2x su ogni run inaccettabile, anche good basin, anche M-TC inerte). TERZA opzione verificata meccanicamente fattibile: M-TC ON solo nei tentativi di retry (multi-start attempt >=2), OFF al tentativo 1.

PERCHE' E' MEGLIO: multi-start (DEFAULT ON, TASK-483) fa attempt 1 deterministico e stop-al-primo-0-hard. Good basin -> stop ad attempt 1 con M-TC OFF = ~20 min byte-identico a oggi (niente 2x). Bad basin (residual hard dopo attempt 1) -> attempt >=2 con M-TC ON = cura deterministica del room-null room-tier. I good basin non pagano mai l'overhead, i bad basin ottengono la cura che il solo multi-start aggira solo probabilisticamente.

VERIFICA MECCANICA (gia' fatta, costruire sopra): labTagCapacity riga ~1903 legge isTagCapacityEnabled() LIVE alla costruzione dello stream (non cached); ogni tentativo ricostruisce il solver via runStagedCycle -> SolverFactory.create(config).buildSolver() e rilegge il flag. PRECEDENT ESATTO: applyGraspSeedForAttempt (riga ~1523) setta grasp.enabled=false per attempt 1, true seeded per >=2, salva/ripristina la property nel finally del wrapper.

TASK:
1. PROTOTIPO: applyMtcForAttempt(attempt) mirror di applyGraspSeedForAttempt (attempt 1 -> tag-capacity.enabled=false; >=2 -> true). Chiamato in runOneMultiStartAttempt accanto ad applyGraspSeedForAttempt. Save/restore property originale nel finally del wrapper (come GRASP). Unit test puro mtcEnabledForAttempt (1 false, >=2 true).
2. A/B: (a) GOOD BASIN deterministic multi-start on: attempt 1 M-TC off -> wall ~20min hard 0 (come ab-mtc-goodseed-off 1230s/0hard)? (b) BAD BASIN grasp-seed-6 room-null meccanica: attempt 1 M-TC off non cura, attempt >=2 M-TC on cura a 0-hard?
3. PREDIZIONE FALSIFICABILE ex-ante: good basin ~20min hard 0 (come M-TC-OFF); bad basin cura a 0-hard su attempt >=2 (come M-TC-ON).

GATE DI AMBITO: ha senso SOLO perche' multi-start e' DEFAULT ON. Se multi-start OFF (single solve), non ci sono attempt >=2 e M-TC per-retry = M-TC-OFF. Documentare subordinazione.

REGOLE: TU MISURI, MAI adozione default (M-TC resta opt-in, multi-start resta DEFAULT ON). L'attivazione per-retry e' la feature nuova (attiva solo se multi-start gira e attempt>=2). DoD: /simplify + /code-review high + test.sh exit 0. commit+push raw + VERDICT-mtc-per-retry-<data>.md (predizione ex-ante + esito buono/cattivo entrambi bacini). Ratifica a maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Design (2026-08-03)

DECISIONE DI MODELLAZIONE (mandate "decidi tu"): la feature per-retry e' dietro un suo flag `planner.multistart.mtc-per-retry.enabled` DEFAULT FALSE (opt-in prototype). Quando OFF: il wrapper NON tocca M-TC -> zero behavior change (M-TC resta opt-in off). Quando ON: attempt 1 setta tag-capacity.enabled=false, >=2 setta true. Cosi' "prototipo dietro flag" + "MAI adozione default" sono entrambi onorati, e l'A/B e' un flag-flip. Subordinata a multi-start (vive nel wrapper runStagedCycleWithRetry): se multi-start OFF (single solve), il wrapper non gira e M-TC per-retry = M-TC-OFF.

Implementazione: mirror riga-per-riga di applyGraspSeedForAttempt. mtcEnabledForAttempt(a)=a!=1 (puro, testabile). applyMtcForAttempt(a) setta planner.tag-capacity.enabled (no-op quando il prototipo e' off: possiede lui il gate). Chiamato in runOneMultiStartAttempt accanto a applyGraspSeedForAttempt. Save/restore origMtc nel finally del wrapper INCONDIZIONALE (come GRASP, simplify Finding 1: applyMtcForAttempt e' no-op quando off, quindi il restore e' no-op). restoreGraspProperty rinominato restoreSystemProperty (generico, ora usato anche per M-TC).

PREDIZIONE FALSIFICABILE ex-ante: good basin (deterministic, multi-start on) wall ~20min hard 0 (come ab-mtc-goodseed-off 1230s/0hard, M-TC off ad attempt 1 = byte-identico); bad basin (grasp-seed-6) cura a 0-hard su un attempt >=2 (come M-TC-ON piatto). Se il bad basin non cura neanche con M-TC on al retry (multi-start cambia la costruzione GRASP, magari non genera il room-null), e' risultato onesto.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
