---
id: TASK-630
title: >-
  TASK-630 - Instrumentazione H17 G13 2027-06-10: perche' il compaction non
  chiude un gap banalmente curabile
status: Done
assignee: []
created_date: '2026-09-19 19:28'
labels:
  - claude-planned
  - h17
  - compaction
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal dissezionamento del -4 di integrato-1: H17 G13 2027-06-10 gap slot 10-11 (2 ore). Slide T10 12->10 e 13->11 e' legalmente perfetta (VRP lab=4 pass, T10 libero 10-11, quota 4h invariata, nessun conflitto). MA ne' il compaction ne' l'HRF l'hanno chiusa. Ipotesi: (a) timing: compaction corso quando le lezioni erano pinnate; (b) re-creazione a valle da LS/corner-b; (c) HRF gate. Mandato inviato al laptop per instrumentazione dopo il banco 628.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Instrumentazione compaction+HRF su G13 2027-06-10 (gap slot 10-11) nel replay di integrato-1
- [ ] #2 #2 Identificazione della causa: timing (pinned al momento del compaction), re-creazione a valle (LS/corner-b), o HRF gate
- [ ] #3 #3 Fix proposto (se banale) o famiglia nominata
- [ ] #4 #4 Banco di conferma: la cella G13 10/06 si compatta
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
