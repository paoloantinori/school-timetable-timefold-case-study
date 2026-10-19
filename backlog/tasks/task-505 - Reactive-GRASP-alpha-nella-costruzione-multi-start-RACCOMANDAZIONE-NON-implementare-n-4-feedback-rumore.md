---
id: TASK-505
title: >-
  Reactive GRASP alpha nella costruzione multi-start (RACCOMANDAZIONE: NON
  implementare, n<=4 feedback = rumore)
status: Done
assignee: []
created_date: '2026-08-16 16:00'
labels:
  - claude-planned
  - research
  - rejected-recommendation
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Tracciato su richiesta di maintainer ("crea backlogs per tutto", 2026-08-16 sera) MA con raccomandazione esplicita di NON implementare. Idea: adattare l'avidita' della costruzione GRASP (parametro continuo, es. alpha della RCL o bias della distribuzione degli start-offset per-course) in base agli score dei tentativi precedenti (famiglia reactive GRASP, Resende/Ribeiro). RAGIONE DEL RIFIUTO: con maxAttempts=5 il numero di punti di feedback per run e' <=4; adattare un parametro continuo su 4 campioni rumorosi e' fitting del rumore, non apprendimento. Diversamente dai lever discreti di TASK-503 (tabella firma->lever, regole scritte), qui non c'e' segnale sufficiente. Rivalutare SOLO se maxAttempts aumentera' sensibilmente o se si introducesse apprendimento tra RUN diversi (persistenza), che oggi e' fuori scala.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Nessuna implementazione senza una nuova evidenza che cambi il quadro (piu' feedback points per run o firme piu' stabili)
- [ ] #2 Se implementato: alpha adattiva con regole scritte, A/B appaiato, forma opt-in default OFF
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
