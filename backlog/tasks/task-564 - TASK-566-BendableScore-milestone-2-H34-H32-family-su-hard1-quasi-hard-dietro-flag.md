---
id: TASK-564
title: >-
  TASK-566: BendableScore milestone-2 H34/H32-family su hard[1] (quasi-hard)
  dietro flag
status: Done
assignee: []
created_date: '2026-08-29 05:28'
updated_date: '2026-09-02 09:00'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Milestone-2 del ramo B (quasi-hard): spostare H34/H32-family dal livello hard[0] al livello hard[1] (quasi) dietro flag planner.constraints.quasi-tier.enabled DEFAULT OFF. La LS potra' attraversare le 6 celle H34 residue (import-locked, filone che nessuna cura A chiude) pagando un prezzo intermedio invece di fallire tutto-o-niente.

INFRASTRUTTURA PRONTA: BendableScore(2 hard + 2 soft) migrato su tutto il codice (milestone-1, suite 1203/0 EXIT 0, branch worktree-quasihard-b, commit f6bc5886). ScoreCompat e' l'unica sorgente della shape. Il livello hard[1] e' riservato e scritto da nessuno in M1.

LAVORO: aggiungere ScoreCompat.ofQuasi(w) che scrive su hard[1], re-tierare H34 e H32-family da ofHard a ofQuasi dietro flag, TDD (la LS deve poter attraversare una violazione quasi senza murarsi), banco localizzato.

RIFERIMENTO: TASK-538 (BendableScore originary), ricerca claudedocs/research_bendablescore-migration_2026-08-28.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ScoreCompat.ofQuasi(w) implementato (scrive su hard[1])
- [ ] #2 H34 e H32-family re-tierate da ofHard a ofQuasi dietro flag planner.constraints.quasi-tier.enabled DEFAULT OFF
- [ ] #3 TDD: test che dimostra che la LS attraversa una violazione quasi senza murarsi
- [ ] #4 Banco localizzato: confronto hard-full vs quasi-tier su checkpoint esistente
- [ ] #5 Suite completa EXIT 0
- [ ] #6 Nessun default flip senza ratifica maintainer
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO IN CASCATA dal verdetto TASK-538 (maintainer 2/09: quasi-tier CHIUSO NEGATIVO): la milestone-2 (H34/H32-family su hard[1] dietro flag) perdeva la premessa: il landscape e' monotono per-ora, il collo e' il campionamento (misurato 15Mx budget LS) e la cura e' SurplusHarvest (TASK-577), non il tier intermedio. Nessun codice di questa milestone mai scritto. Chiusura documentale.
<!-- SECTION:NOTES:END -->
