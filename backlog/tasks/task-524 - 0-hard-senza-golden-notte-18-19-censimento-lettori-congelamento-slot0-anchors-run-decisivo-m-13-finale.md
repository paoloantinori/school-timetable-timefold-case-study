---
id: TASK-524
title: >-
  0-hard senza golden (notte 18/19): censimento lettori, congelamento
  slot0-anchors, run decisivo m-13 finale
status: Done
assignee: []
created_date: '2026-08-18 21:54'
labels:
  - claude-planned
  - solver
  - m-13
  - golden-free
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Obiettivo notturno dato da maintainer la sera del 18/08: "fino a che non abbiamo 0hard senza golden non abbiamo ottenuto niente", con inferenza dal golden per strategie/euristiche generalizzate che portino a 0hard spontaneo senza golden. Stato: control golden-free = -25 H29 puri (H0 zero) con solo co-presenza congelata; le letture runtime golden sul path control sono due censite (co-presenza congelata, slot0-anchors da congelare); col golden e' 0hard deterministico x5. Il congelamento e' la generalizzazione legittima: pratica del golden promossa a input dichiarato (workbook), non copia della soluzione. Mandato spedito 18/08 notte (/tmp/zai-zero.txt): censimento -> congelamento ancore -> run decisivo -> diagnosi checkpoint se serve.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Filone 0: censimento COMPLETO dei lettori golden runtime (grep SeedCsv.load/GOLDEN_RESOURCE): ogni sito classificato congelato/da-congelare/inerte-sul-path-control; sorprese documentate prima di procedere
- [ ] #2 Filone 1: slot0-anchors congelate con la stessa tecnica co-presenza (mappa statica + loader FROZEN fallback + identity gate + banner)
- [ ] #3 Filone 2 (DECISIVO): control golden-free completo (golden fisicamente assente, entrambi i congelamenti caricati): BERSAGLIO 0hard. Se riuscito: sezione M13-FINAL nel VERDICT, m-13 CHIUSO POSITIVAMENTE, congelamenti dichiarati come voci workbook
- [ ] #4 Filone 3 (solo se fallisce): diagnosi checkpoint via SnapshotFixture (secondi, non run): composizione residuo, confronto per fase vs run col golden. Se room-tier puro: documenta e fermati (cura search-side M-TC/485 = decisione maintainer). Massimo un secondo run se la cura e' meccanica e ovvia
- [ ] #5 Al mattino: censimento + esito run decisivo + lista definitiva congelamenti workbook
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
