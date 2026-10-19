---
id: TASK-619
title: >-
  TASK-619 - IMPORT CROSS-DATA stesso-gruppo: la v0.1 ritirata e' VIVA sul
  bacino A3 (famiglia terza del round cieco), terzo braccio del
  HoleRelayFinisher
status: Done
assignee: []
created_date: '2026-09-15 20:55'
labels: []
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task613-results/puzzle-live-r1/
  - src/main/java/com/school/planner/solver/HoleRelayFinisher.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal round cieco A3 (consegna arm-a del gate 613, [-14/0], artefatti in task613-results/puzzle-live-r1/a3-round/ su wt/blind-live-r1): il cieco chiude 5/8 ma due chiusure sono ARTEFATTO (H31 reale su G14 da svuotamento che fa iniziare il giorno alle 10; H33 da donatori manuali senza floor); le LEVE PORTE sullo stesso bacino fanno -14 -> -11 PULITE. LA FAMIGLIA TERZA: l'IMPORT CROSS-DATA STESSO-GRUPPO, cioe' la v0.1 del HoleRelayFinisher ritirata come 'morta' dopo i banchi replay (H1/VRP mangiavano tutto): su A3 e' VIVA, la sua morte era bacino-specifica. Census completo e specifica nel VERDICT A3: terzo braccio del finisher (dopo plain-slide, compact-swap e DFS cross-classe), gates di casa (VRP per-lezione, roomFor 616-style, verifyComposite), backfill profondita' 1. Banco prima su A3 (bersaglio provato dal cieco) e B3 (dove era morta: no-op atteso), poi gates e A/B per l'adozione secondo la direttiva bounded-no-op. PRIORITA' ALTA per l'endgame: e' l'unica famiglia NUOVA certificata dal metodo cieco su un bacino live.
<!-- SECTION:DESCRIPTION:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
