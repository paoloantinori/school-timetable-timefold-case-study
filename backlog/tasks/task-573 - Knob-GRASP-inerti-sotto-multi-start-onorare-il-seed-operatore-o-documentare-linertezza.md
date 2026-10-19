---
id: TASK-573
title: >-
  Knob GRASP inerti sotto multi-start: onorare il seed operatore o documentare
  l'inertezza
status: Done
assignee: []
created_date: '2026-08-30 04:26'
updated_date: '2026-09-01 17:32'
labels:
  - claude-planned
  - quasi-tier
  - tech-debt
milestone: m-13
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scoperto nella notte 30/08 (pushback zai verificato da Fedora nel codice di runStagedCycleWithRetry): sotto multi-start il wrapper possiede planner.construction.grasp.enabled/.seed per la durata del loop: tentativo 1 SEMPRE deterministico, retry seeded col numero del tentativo (TASK-483). Conseguenza: i -D grasp dell'operatore sono INERTI su tutto il ciclo staged (il banner di boot li stampa perche' legge le proprieta' al refresh, poi il wrapper sovrascrive): confondono chi lancia (notte 30/08: Fedora ordino' un "run3 seed 25" credendo di cambiare bacino; anche la narrativa "run2 seed 9" era infondata).

Due opzioni (maintainer):
A. HONOR: far rispettare al wrapper il seed dell'operatore (es. tentativi >=2 seeded operator_seed+attempt, o tentativo 1 grasp se l'operatore lo chiede esplicitamente): costo piccolo, rende i -D effettivi come knobs.md li descrive.
B. DOCUMENTARE: dichiarare l'inertezza in knobs.md + banner (logga "grasp operator flags OWNED by multi-start, ignored") e lasciare il design TASK-483.

Riferimento: task538-results/DECISIONE-debito-quasi-brief.md nota meccanica; righe runStagedCycleWithRetry ~2425-2440.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTATA opzione A HONOR (1/09 notte, zai, commit 03280f60 verificato Fedora): flag planner.multistart.honor-operator-grasp.enabled DEFAULT OFF (forma opt-in). ON + grasp.seed=S dell'operatore: i tentativi >=2 usano seed S+attempt; il tentativo 1 resta deterministico (design TASK-483 intatto a flag OFF: selezione invariata). Il BANNER DI TRASPARENZA e' SEMPRE attivo (parte B regalata: dichiara quando i flag operatori sono OWNED dal multi-start e ignorati). HonorOperatorGraspTest 4/4, suite 1244/0/0/39 EXIT 0, knobs 115->116, log f7a/task573/. Da qui in poi un 'run seed 25' del operatorscape e' EFFETTIVO a flag ON. Chiusura formale del task: diff piccolo (flag+banner+test) gia' verificato; passata /simplify di Fedora sul commit al prossimo giro di pulizia se il gate la chiede.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
