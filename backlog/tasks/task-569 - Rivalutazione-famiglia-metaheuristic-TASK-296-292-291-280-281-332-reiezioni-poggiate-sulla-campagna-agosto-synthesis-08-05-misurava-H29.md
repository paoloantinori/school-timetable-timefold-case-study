---
id: TASK-569
title: >-
  Rivalutazione famiglia metaheuristic (TASK-296/292/291/280/281/332): reiezioni
  poggiate sulla campagna agosto / synthesis 08-05 (misurava H29)
status: Done
assignee: []
created_date: '2026-08-29 11:40'
updated_date: '2026-08-29 12:10'
labels:
  - claude-planned
  - rivalutazione
  - metaheuristic
milestone: m-13
dependencies: []
references:
  - backlog/completed/task-296 - Tune-acceptedCountLimit-per-LS-phase.md
  - >-
    backlog/completed/task-292 -
    Add-cartesianProductMoveSelector-for-joint-timeslotroom-moves.md
priority: high
type: spike
ordinal: 321000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Creata dalla scansione sistematica TASK-567 (29/08, agente + verifica a campione delle citazioni). SEI reiezioni della famiglia metaheuristica condividono la stessa premesse rotta: la campagna di agosto / synthesis 2026-08-05, che misurava rumore H29 (room-null), non la qualita' delle metaeuristiche; e confronti staged-equivalenti letti a 0hard.

TASK COPIERTI (classe b, premessa dimostrabilmente rotta, citazioni verificate a campione):
- TASK-296 Tune-acceptedCountLimit-per-LS-phase: "RULED OUT... ACL sweep... state-dependent" ancorato al synthesis 08-05. PRIORITA' ALTA: knob piu' economico, segnale pre-campagna positivo (acl=100).
- TASK-292 cartesianProductMoveSelector: "RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05)": la famiglia joint-moves fu esclusa SENZA alcuna misura valida.
- TASK-291 subChainSwapMove: censimento non-robusto/inerte dalla notte 04/08 (TASK-284) = campagna che misurava H29.
- TASK-280 Switch to VND: "VND-FI INERTE sul ciclo staged (verificato)" = equivalenza letta a 0hard; la parte performance del multi-threaded LS sopravvive.
- TASK-281 VND timeout/unimproved tuning: "flat sullo staged... al ceiling" misurato su run d'epoca.
- TASK-332 pillar-moves clean A/B: chiusura puramente "il synthesis lo copre".

PRIMO PASSO OBBLIGATORIO (nota di contesto): distinguere PER OGNI TASK quali artefatti usava: la campagna PRE-riparo (provider cieco, non fa testo) o il banco RIPARATO del 04/08 (be404746, misure reali: VND-FI acl=1 batte Tabu -242 vs -6690 su hard). La memoria di progetto (metaheuristic-is-the-lever, benchmark-harness-repaired) attesta che DOPO la riparazione esistono misure valide: la rivalutazione deve decidere per ciascuno se la reiezione poggiava su quelle (valida) o solo sul synthesis/pre-riparo (rotta).
VINCOLO DI COSTO (direttiva correttezza-vs-costo): nessun annacquamento del modello; qui si parla solo di configurazione del solver, campo legittimo.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Rivalutazione eseguita 29/08 ~14:15 (fonti: sei task completati con citazioni verificate nella scansione TASK-567, memorie benchmark-harness-repaired/metaheuristic-is-the-lever del banco 04/08 riparato, solverConfig.xml attuale letto, re-benchmark pulito 29/08 citato dal mandato Fedora con tabu -110 pulito contro il -6690 d'epoca). TABELLA DELLE EPOCHE: (1) marzo: tabu-7 vinse il benchmark 23/03, divenne stock (era-modello precedente); (2) agosto pre-riparo: le sei reiezioni (292/291/296/280/281/332) poggiavano su punteggi con massa H29; anche il banco RIPARATO 04/08, che diede "VND-FI acl=1 -242 vs Tabu -6690", leggeva un asse hard dominato da H29: il -6690 era rumore stanze (il re-benchmark pulito legge -110), quindi il titolo "VND schiaccia Tabu" e' a sua volta artefatto; (3) regime attuale (v2.1, baseline -12): l'hard e' posseduto dall'orchestrazione (multi-start N=5 ratificato, ExactFinisher, finishers), la LS e' knob di medium; TASK-481 provo' che tabu+pillar+acl-1000 ATTIVI lasciano comunque il residuo H17 (problema di composizione, non di copertura acceptor); i guadagni wall dimostrati e ratificati vengono da LS multi-threaded (-25%), thinning adattivo TASK-500 (tail-capping 932/1583/413 -> 529/669/512, la forma moderna della leva acl), skip-doomed-room (-600s/tentativo). RISPOSTA ALLA DOMANDA DEL MANDATO: NO, nessuna configurazione metaheuristica offre miglioramento dimostrabile di P(0hard)/wall al baseline -12 con v2.1. P(0hard): il residuo (-12) e' H34 import-locked + celle golden-filled di competenza finisher/multi-start, non dell'acceptor; wall: le leve dimostrate sono gia' ratificate e l'urgenza VND (collasso tabu) e' svanita sull'asse pulito; medium: nessun artefatto ha mai mostrato la scelta dell'acceptor muovere il medium in regime pulito. ESITO: FAMIGLIA CHIUSA DEFINITIVAMENTE. CONDIZIONE DI RIAPERTURA registrata (non task): se la campagna riaprirà il gap medium, il primo banco candidato e' l'A/B acceptor su asse pulito (tabu-7 vs VND-FI acl=1 vs late-acceptance), mai piu' su asse H29. Nessuna adozione, nessun banco localizzato: chiusura dichiarata.
<!-- SECTION:FINAL_SUMMARY:END -->
