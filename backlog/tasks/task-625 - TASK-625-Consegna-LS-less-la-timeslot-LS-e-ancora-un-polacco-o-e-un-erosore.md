---
id: TASK-625
title: >-
  TASK-625 - Consegna LS-less: la timeslot-LS e' ancora un polacco o e' un
  erosore?
status: Done
assignee: []
created_date: '2026-09-18 10:16'
labels:
  - claude-planned
  - m-15
milestone: m-15
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla discussione maintainer 18/09 (m-15). Evidenza accumulata: (a) M39 banco 17/09: la LS nuda CREA frammentazione (controllo flag-OFF 54->71 giorni interleaved, armed 54->11); (b) audit-g01: la costruzione produce BLOCK 91/109 per G01 e la consegna porta 43 giorni interleaved = erosione LS cristallizzata da S13; (c) lo 0-hard non lo trova piu' la LS: lo assicura la catena deterministica (multi-start + repair + finisher + corner-b). La timeslot-LS oggi costa ~1h per ciclo e il suo ruolo onesto e' solo il polish del medium/soft. TEST: consegna con la B-TIMESLOT LS saltata (CH -> repair -> stanze) e confronto su consegna + metriche di erosione. Se parita' o meglio: il piu' grande risparmio di wall della campagna e la rimozione del principale agente erosivo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Banco replay con B-TIMESLOT LS saltata (CH -> catena repair -> stanze) su N>=3 semi fissi, consegna confrontata con la serie recente
- [ ] #2 #2 Metrica erosione misurata sui medesimi stati: interleaved multi-docente (lab-block-form-kpi.py), blocchi teoria M-TR-family, forme H17/H31
- [ ] #3 #3 Verdetto: la timeslot-LS resta (se porta medium/soft misurabile) o viene saltata in produzione (flag default-* con forma opt-out standard) o viene ridotta (es. solo fasiRooms)
- [ ] #4 #4 Se salta/ridotta: A/B ciclo completo appaiato come gate di adozione e wall risparmiato censito (~1h per ciclo atteso)
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
