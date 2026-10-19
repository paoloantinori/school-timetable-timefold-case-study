---
id: TASK-504
title: >-
  Warm-start dei pesi adattivi tra tentativi multi-start (registri TASK-500,
  SPECULATIVO: gate A/B obbligatorio)
status: To Do
assignee: []
created_date: '2026-08-16 16:00'
updated_date: '2026-08-16 21:02'
labels:
  - claude-planned
  - solver
  - multistart
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Idea emersa dalla discussione multi-start smart (maintainer 2026-08-16 sera, "mi interessa tutto questo"). Oggi TASK-500 impara i pesi per move stream DENTRO un tentativo (statistiche single-writer dal loop MT-LS) ma il registro muore col tentativo. Questo task valuta il passaggio di memoria: salvare il registro a fine tentativo e seminare il successivo. Un solo meccanismo, nessuna nuova inferenza. RISCHIO dichiarato (per questo e' speculative e priorita' bassa): i pesi imparati in un bacino sbagliato possono avvelenare il tentativo successivo; la mitigazione (ereditare solo dai tentativi buoni) ne riduce il valore perche' i tentativi buoni fermano il multi-start (stop-al-primo-0-hard), quindi l'eredita' utile arriverebbe solo dal best-so-far non-0-hard. Valutare SOLO con A/B prima di prendere sul serio.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Meccanismo di persistenza/ricarico del registro pesi tra tentativi (serializzato a fine tentativo, letto all'inizio del successivo)
- [ ] #2 Mitigazione poison-basin implementata: eredita' SOLO dal best-so-far / tentativi 0-hard, mai da tentativi falliti
- [ ] #3 Gate A/B appaiato su good-seed e bad-seed prima di qualunque considerazione di adozione (hard, medium, wall)
- [ ] #4 Esito onesto anche negativo: se l'A/B mostra neutralita' o degrado, chiudere Done con verdetto e tenere il supporto flag OFF
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## PROTOTIPO IMPLEMENTATO flag OFF (16/08 notte, secondo mandato, commit 27b65bc0)

Primitive in AdaptiveMoveWeights: snapshotForWarmStart() + seed() (clamp nella busta [LO*base, HI*base] del learning in-run; NaN/inf/negativo rifiutati; 0.0 di OTHER sano perche' la sua base e' 0). Wrapper: flag planner.multistart.warm-start-weights.enabled DEFAULT OFF, banner. Mitigazione poison-basin AC#2: eredita' SOLO da tentativo 0-hard o best-so-far-migliorato (il 0-hard ferma il loop, quindi in pratica eredita' dal best-so-far non-0-hard, esattamente la forma prevista).

Test WarmStartWeightsTest 5/5 + battery 15/15 + full suite 1048/0 EXIT 0. AC#1 e #2 coperte; AC#3 (gate A/B) e AC#4 differiti: NESSUN solve stanotte, il prototipo aspetta il gate su good+bad seed.
<!-- SECTION:NOTES:END -->
