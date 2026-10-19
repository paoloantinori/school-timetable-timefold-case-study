---
id: TASK-470
title: >-
  Cura M-TR construction-origin: theory-spread nel WeeklyTemplateBuilder
  (PROPOSTA, gated maintainer)
status: Done
assignee: []
created_date: '2026-08-01 00:46'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 293000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PROPOSTA (non prototipo, gated maintainer). Origine: la diagnosi Fedora sullo sweep TASK-468 ha mostrato che i blocchi 2h di teoria nascono in COSTRUZIONE (residuo fine construction = 1672 ~uguale al finale 1679; la LS li eredita e li smonta appena, non li crea; blocchi UNPINNED al 98,6%, non e' la PROMOTION). Il peso M-TR e' inerte a qualunque peso in [1,1000]; la cura e' a monte, non un peso.

DESIGN (10 righe, dettaglio in commento #5 di TASK-468 e in task468-results/VERDICT-sweep468-2026-08-01.md sezione DIAGNOSI CORRETTA):
1. Dedup per-(gruppo, giorno, materia-teorica) nel placement greedy di WeeklyTemplateBuilder.
2. isTheoryForRule(course) = !isLab && !THEORY_RULE_PRACTICAL_EXEMPT (specchio del vincolo M-TR); saltare i weekday dove quella materia teorica e' gia' piazzata.
3. Effetto: template stampa teoria a 1h/giorno (pratica golden) invece di blocchi 2h.
4. NON capping di block-length (M-TR fired su same-day same-subject anche non-contiguo).
5. Non banale: tocca decisione di placement + fill-risk + interazione M15/S17.
6. RISCHIO chiave: il residuo construction potrebbe NON tenere nella LS (re-impacchettamento). Serve full-solve staged di conferma, non solo WeeklyTemplateConstructionRealSeedTest.
7. Flag planner.construction.theory-spread.enabled DEFAULT OFF, LIVE come 463/464/468; A/B gate (hard 0, full-day, ricorrenza, M15/M20 + residuo M-TR verso golden 440).

STATO: PROPOSTA. Nessuna implementazione finche' maintainer non gate. Non introdurre adozione.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
COMPLETO con esito onesto: prototipo theory-spread nel WeeklyTemplateBuilder implementato (flag planner.construction.theory-spread.enabled DEFAULT OFF). Due A/B live (11/08 e re-run 13/08 con run3 tiebreaker): medium delta VARIANZA non segnale (run1 -54k, run2 -342k, run3 conferma; la prova decisiva: boxed CH-end 9/51/82 tra tre run ON identici = il medium e' rumore della costruzione, non del flag). 0hard sempre, nessuna regressione. Resta committed default-OFF come artifact innocuo-non-adozionale. Verdetto: task470-results/VERDICT-470-run2-2026-08-13.md. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
