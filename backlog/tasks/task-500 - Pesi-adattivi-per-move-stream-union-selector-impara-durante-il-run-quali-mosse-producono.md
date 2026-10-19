---
id: TASK-500
title: >-
  Pesi adattivi per move stream (union selector): impara durante il run quali
  mosse producono
status: Done
assignee: []
created_date: '2026-08-15 17:11'
updated_date: '2026-08-15 20:37'
labels:
  - claude-planned
  - enterprise-reimpl
  - literature-backed
milestone: m-12
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Pesi adattivi tra gli 11 stream dell'union move selector. Direzione con piu' supporto letteratura per il nostro dominio: UniTime (ITC2007), GOAL (vincitore ITC2011), Lectio ALNS (scuola superiore danese, il piu' simile a noi), ITC2021 usano tutti multi-neighborhood con pesi adattivi o ALNS destroy-repair; NESSUNO usa nearby geografico (fonte: claudedocs/research_nearby-selection-efficient-implementations_2026-08-15.md, exhaustive 15/08). DESIGN: il solver impara DURANTE il run quali tipi di mossa (change/swap/pillar, timeslot/room) producono accettazioni/miglioramenti e ripesa la probabilita' di selezione. Forme candidate: (a) adaptive union weights stile Lectio (traccia performance recente per operatore, aumenta/pesca peso di chi produce), (b) multi-armed bandit (UCB1) per stream con ricompensa = improvement rate. PUNTO DI INNESTO: il nostro unionMoveSelector nel solverConfig.xml ha fixedProbabilityWeight per stream: servono probabilityWeightFactory custom per stream che leggano statistiche per-move-type raccolte da un listener. INTERAZIONE con campagna metaheuristic ESAURITA (08-05): i 5 knob medium FLAT erano pesi STATICI; l'adattivita' e' famiglia diversa (dynamic tuning in-run, non static pre-tuning) e non e' stata testata dalla campagna. RISCHI: (1) feedback loop instabile (peso crolla e non recupera: servono epsilon-floor o decay); (2) NON_REPRODUCIBLE gia' dichiarato, l'adattivita' aggiunge un'altra fonte di varianza: il gate A/B va fatto run1+run2 appaiato (lezione TASK-499: n=1 inganna, -33% diventato +45%); (3) interazione con il multi-threaded LS (8 thread leggono gli stessi pesi: o thread-local o volatile letti per-step). GATE: hard 0 binario, KPI in banda, medium miglior-o-neutro su 2+ run appaiate. Prototipo flag default-OFF.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AGGIUNTO REQUISITO PRE-RATIFICA (Fedora 15/08, ispezione dei 7 test del prototipo e4ba7e78): i test attuali sono L0 puri (regola update tabellata, classificatore, inert, boundary: livello corretto, funzioni pure) + wiring test (config-only, non risolve). MANCA il test checkpoint L2 della catena statistiche->pesi: la domanda 'il decider MT-LS alimenta davvero i pesi durante una LS vera e la redistribuzione e' sensata' non e' coperta da nessuno dei 7. Ricetta: harness sul pattern BoxRepairCheckpointTest/RoomPhaseSnapshotTest (snapshot di fase di un run reale ricostruito in memoria) + qualche step di LS col decider custom MT-LS e flag armato + assert che gli stream che producono accept salgono, clamp 0.5x-2.5x rispettato, stream silenziosi scendono. Il punto delicato: i checkpoint test esistenti girano sul solver config standard senza il decider custom: serve l'harness dedicato. Valore: la regressione che impedisce a un futuro refactoring del decider di disconnettere silenziosamente l'adattivita' (stesso tipo di no-op silenzioso che il self-review del 499 trovo' nel wiring post-build).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ESITO AGGIORNATO (laptop, notte 15-16/08 ore 02:50): FORMA B decider-internal IMPLEMENTATA e VALIDATA. Dopo il verdetto negativo sulla forma A (sotto), la forma B (thinning nel decideNextStep, r_i normalizzato sul max, zero config Timefold) gira a scala piena: A/B 2+2 run appaiate hard 0 4/4, full-day ON migliore 2/2, ricorrenza in banda, medium -7/-9k (1%) concorde dentro il rumore, B-TIMESLOT 932->529/1583->669 concorde con meccanismo diretto (mosse thinned non valutate) e caveat control-variance. RUN3 TIEBREAK eseguito (ore 04:40): medium si RIBELTA (+4,1k ON migliore, direzione non stabile su 3 run a magnitudini 0,4-0,9 percento nel rumore) = NEUTRO stabilito; hard 0 6/6; KPI identici; wall B-TIMESLOT ON 529/669/512 contro OFF 932/1583/413: il thinning CAPS LA CODA del wall, non il mean. VERDETTO FINALE: adozione ragionevole ma non urgente, decisione di maintainer, flag resta OFF, requisito L2 da costruire solo se maintainer approva il flip. test.sh EXIT 0 (1018). VERDICT completo in task500-results/ab-2026-08-15/VERDICT.md.

ESITO INTERMEDIO (forma A, 2026-08-15 sera): VERDICT NEGATIVO sul veicolo. Il meccanismo di learning e' sano (regola pura mean-preserving 7/7 test, 365 segment update in-run, pesi stabili alle basi 5.01/2.99, nessun collasso: rischio feedback-loop non materializzato) ma il VEICOLO Timefold-config (probabilityWeightFactoryClass) e' non praticabile a scala piena: tre muri di validazione (RANDOM->PROBABILISTIC esplicito, JIT->STEP esplicito, pillar esclusi per sub-pillar esponenziale) poi il muro fisico: ProbabilityMoveSelector PROBABILISTIC+STEP materializza la mappa mosse/step -> B-TIMESLOT 4x piu' lento (3606s vs 932s) e OOM heap nella fase room (ciclo morto a -9404). Trappola documentata FAQ Timefold (probability+caching). NON adozione (flag resta OFF), run2 impossibile. Il requisito pre-ratifica L2 (checkpoint statistiche->pesi) NON e' scattato: serve solo su A/B positivo. FOLLOW-UP tracciato: forma B decider-internal (campionamento stream nel NOSTRO loop MT-LS, bypass del ProbabilityMoveSelector): AdaptiveMoveWeights registro+regola sono riusabili cosi' come sono. Doc: task500-results/ab-2026-08-15/VERDICT.md.
<!-- SECTION:FINAL_SUMMARY:END -->
