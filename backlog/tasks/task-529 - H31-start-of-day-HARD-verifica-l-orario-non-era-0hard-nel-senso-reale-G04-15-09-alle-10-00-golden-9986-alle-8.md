---
id: TASK-529
title: >-
  H31 start-of-day HARD + verifica: l orario non era 0hard nel senso reale (1
  FC 15/09 alle 10:00, golden 99,86% alle 8)
status: Done
assignee: []
created_date: '2026-08-19 12:18'
labels:
  - claude-planned
  - solver
  - bug
  - model
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Turno maintainer 14:10-14:15 19/08 durante ispezione della soluzione scatter2: (1) G04 15/09 inizia alle 10:00 e 14/09 alle 9:00: violazione hard mai codificata perche ritenuta ovvia, golden la pratica al 99,86%. Mandato spedito 14:10 (/tmp/zai-start.txt), implementato come H31. (2) UI mostra H29 su pin/pseudo esenti. (3) G09 13/05 ore 11-13 Moulage pinnate senza stanza: backfill deterministico via tag. (4) STAGE come costante per slot stage. I punti 2-4 tracciati in TASK-529.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H31 start-of-day HARD implementato col protocollo near-miss (FATTO sul laptop: 4824a91c, golden 1943/1960 alle 8, 13 eccezioni = primo giorno scuola, esenzione giorni-con-pin, flag opt-out planner.constraints.start-of-day.enabled, test 2/2): da verificare con run cap 240 dove G04 15/09 deve aprire alle 8:00
- [ ] #2 UI drill-down: le esenzioni pin/pseudo nel calcolo violazioni client-side (H29 non deve mostrarsi su pin importati e pseudo-Stage)
- [ ] #3 Run di verifica finale con H31 attivo: hard 0, nessun late-start eccetto esenzioni, KPI in banda, diff visualizzata dal golden (poca differenza = ok)
- [ ] #4 Ratifica del default in CLAUDE.md con paragrafo standard se il gate passa (decisione maintainer)
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
