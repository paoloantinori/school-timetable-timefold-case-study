---
id: TASK-471
title: >-
  Workbook: distribuzione teoria come input dichiarato (1h/giorno vs 2h, dal
  golden, conferma scuola)
status: Done
assignee: []
created_date: '2026-08-01 01:31'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
references:
  - >-
    claudedocs/spalmone-2026-27/task470-results/VERDICT-construction-gate-2026-08-01.md
  - claudedocs/spalmone-2026-27/task468-results/VERDICT-sweep468-2026-08-01.md
priority: medium
ordinal: 294000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ORIGINE: verdetto TASK-470 (claudedocs/spalmone-2026-27/task470-results/VERDICT-construction-gate-2026-08-01.md). I lever di COSTRUZIONE (weekday + chunk) sono PARZIALI: best case theory-as-1h+dedup = 1142 giorni-2+, ancora 2,6x il golden 440. Lo spike ha mostrato che il residuo rimanente e' "ripetizioni vincolate" (ore settimanali > weekday disponibili). Il golden fa 440 distribuendo la teoria in modo che il vincolo non stringa (432/~6742 lezioni teoria = 6,4%: la scuola piazza ~94% della teoria a 1h/giorno). Quella distribuzione e' una PRATICA della scuola non deducibile dai soli input di costruzione.

OBIETTIVO (direttiva solo-input, maintainer 2026-07-30 + 2026-07-31): trattare la distribuzione della teoria come INPUT DICHIARATO nel workbook (veicolo TASK-445), non come scoperta di costruzione. Il golden e' l'oracolo che rivela QUALI materie la scuola piazza a 1h/giorno e QUALI ammettono il blocco 2h same-day.

DELIVERABLE:
1. Estrarre dal golden la distribuzione per-materia del "teoria-day shape": per ogni materia teorica (isTheoryForRule, fonte SubjectNormalization.THEORY_RULE_PRACTICAL_EXEMPT), quante ore/giorno la scuola colloca (1h quasi-sempre vs 2h ammessi). Sorgenti: scripts/analyze/cadence-kpi.py + golden-cadence-reference.json + un nuovo estrattore "subject-theory-day-shape" se serve. Seed data dal residual dump (top subject run: Lingua Italiana, Matematica, Lingua Inglese, Scienze = alta concentrazione 2h nel run, da confrontare col golden).
2. Proporre la distribuzione come DEFAULT nel foglio workbook (una colonna "politica blocco teoria" per materia: 1h/giorno preferito vs 2h ammessi), estratta dal golden.
3. Canale di conferma = scuola (referente). I borderline Figurino/Personalizzazione/Tecnologia e creativita' (AC#2, domanda referente aperta) si risolvono qui: il golden dice cosa fa la scuola davvero.

PERIMETRO: e' INPUT, non fix di costruzione. Una volta dichiarato, M-TR (TASK-468) misura contro la pratica dichiarata e il builder puo' onorare una policy 1h dichiarata dove l'input lo dice (senza indovinare). Famiglia: TASK-445 (workbook) + TASK-466 (golden come oracolo sugli input). Non bloccante per la campagna corrente (M-TR resta flag OFF, misuratore).

STATO: BOZZA / da pianificare con referente. Nessuna implementazione finche' il foglio e' approvato.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
COMPLETO: foglio 22_TeoriaBlocco depositato nel workbook (theory_block_policy.py, 196 coppie (classe,materia), split 157-31-8, origine dal golden come input dichiarato). Identity gate test_theory_block_identity.py verificato EXIT 0 da Fedora il 2026-08-13 (round-trip fedele, set-based match col draft). Codice: cd70ccce + narrowing d8f86e47. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
