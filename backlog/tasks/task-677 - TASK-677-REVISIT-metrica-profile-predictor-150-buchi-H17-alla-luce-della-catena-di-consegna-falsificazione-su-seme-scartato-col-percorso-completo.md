---
id: TASK-677
title: >-
  TASK-677-REVISIT metrica profile-predictor (150 buchi H17) alla luce della
  catena di consegna falsificazione su seme scartato col percorso completo
status: Done
assignee: []
created_date: '2026-10-04 09:38'
updated_date: '2026-10-04 16:59'
labels:
  - claude-planned
  - m-15
  - predictor
  - falsification
dependencies: []
priority: high
type: task
ordinal: 325000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La meta' predictor del TASK-635, ordinata da maintainer (04/10, "fallo"). I FATTI NOTI: il predictor scarta costruzioni con >150 buchi H17 (TASK-624/542 iter-16); il banco 4c3513c4 ha misurato che la sola CATENA cura gli H17 dello scartato (-209 -> -2) ma solo il 15% del totale: la lettura era "scarto giusto per esito, sbagliato per metrica (H0 boxed il blocco vero)". I DATI CHE CAMBIANO LA LETTURA: (1) registry attempt-traces.csv (159 righe): unassigned non separa nell'era corrente (1280 buono vs 1263 scartato sullo stesso ciclo notte B2, lo scartato ha MENO boxed; la LS piazza la coda ~1300 sui bacini buoni); holes separa perfettamente su TUTTI i dati osservati (buoni 12-19, scartati 151-317, zero errori osservati in entrambe le direzioni). (2) Il banco ha misurato la catena sul costrutto scartato, NON il percorso che il predictor gatinga (LS B-TIMESLOT + riparazioni + stanze + catena). DOMANDA DECISIVA: il percorso completo consegna 0-hard da un costrutto scartato (>150 buchi, checkpoint stesso-seme attempt2/POST_CONSTRUCTION tsh -2890)? ESperimento: solve-staged-resume da quel checkpoint su lane fresca (resume = single-start, predictor bypassato per costruzione). ESITO A (consegna ~0): il predictor e' falsificato nell'era corrente (scarta cio' che consegnerebbe): cura = disarmo/ritiro della metrica + economia da rivalutare. ESITO B (consegna negativo profondo): scarto validato per esperimento diretto: la cura e' la storia onesta (holes = proxy di qualita' del bacino correlato con la causa vera, l'incapacita' della LS, non la causa stessa) + osservabilita' (log unassigned nel discard) + residual dichiarato. CONFINI: nessun flip senza l'esperimento; la soglia 635 (-40) resta; iterazione su snapshot/resume, ciclo completo solo se serve da gate.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-04 12:35
---
ESPERIMENTO ESEGUITO (04/10, falsificazione appaiata same-seed, veicolo resume da POST_CONSTRUCTION): ESITO A. CONTROLLO (attempt1 deterministico): BT#1 tsh -29 box 4 -> consegna [-9/0] 89min (classe attesa: veicolo VALIDATO). SPERIMENTO (attempt2 SCARTATO, holes 205, tsh costruzione -2890, boxed 1263): BT#1 tsh -13 box 0 (la LS piazza TUTTA la coda ~1250 in un passaggio; il controllo resta box 4 per 3 iterazioni) -> consegna [-3/0] 72min, MEGLIO del controllo e PIU' VELOCE. La soglia 635 -40 paga in diretta: box=0 e -13 >= -40 = accettato senza demozioni (la -2 ne avrebbe bruciate due). Lo scarto amputa retry vitali. CURA IMPLEMENTATA: predictor DEFAULT OFF (opt-in solo -D...profile-predictor.enabled=true, forma ricerca), knob max-holes tenuto al 150 storico (metrica RITIRATA non ricalibrata, TASK-624 non-monotona), banner 'Profile predictor (TASK-677 DISARMED 04/10)', javadoc falsificazione, test ridisegnato, 2 righe registry (pred677-ctrl/-a2), RATIFICA CLAUDE.md. NOTE: (1) registry attempt-traces.csv: unassigned NON separa (1280 ctrl vs 1263 scartato), holes separa ma e' falsificato come causa (HRF cura gli H17); (2) n=1 per braccio dichiarato; (3) economia: retry ~75-90min bounded da maxAttempts=5/stop-at-0/pick-best.
---

author: claude
created: 2026-10-04 13:04
---
CORREZIONE post code-review 4-angle: i buchi dello scartato erano 209, NON 205 (il 205 era la riga dell'altro braccio nel log A; il checkpoint attempt2 = 32esimo/ultimo discard del braccio B, 03:58:08, score -13102 combaciante, cursore graspSeed=32). Corretti CLAUDE.md, javadoc e riga CSV (ora con grasp_seed 0/32 dai cursori e doomed=false esplicito). CSV riallineato a 20 colonne (le mie righe erano 19/18: final_hard/medium/wall slittati). Altro dalla review: census incondizionato 'construction census (TASK-677): holes/unassigned/hard' a OGNI costruzione (keep-measuring, il feed del registry non muore col disarmo); residual banda dichiarato (209 testato, 240-445 censimento inaudito in era corrente); economia numerata (pessimo caso ~5 cicli = 6-7.5h, budget-seconds 0 = senza orologio, POLL_CAP 150min dei driver da alzare); nota disarmo nel javadoc AttemptOutcome; javadoc orfano TASK-508 rimosso; WARN scarto senza claim causale ne' '~45min' stantio; test con @AfterEach. Skip motivati: corner discard-deterministico-attempt-1 (pre-esistente, solo opt-in).
---

author: claude
created: 2026-10-04 16:59
---
ADOZIONE (ciclo fresco, lane 8082, default -40 + predictor OFF, rilanciato 16:19 dopo che il primo giro era stato ucciso dal kill-app del redeploy pubblico: kill-app e' per NOME processo, non porta - mio incidente, dichiarato): il meccanismo restaurato funziona end-to-end. attempt 1 deterministico: census 67 buchi/1272 unassigned, BT -30 box 4, boundary -13, rifinitura INLINE -13 -> -4 (non-0: retry parte). ATTEMPT 2 GRASP seed 1: census 206 buchi/1265 (il vecchio predictor l'avrebbe scartato in 11s), BT -136/-121/-118 box 11->8, boundary -101 -> DOOMED LEGITTIMO sotto la -40 ricalibrata (bacino duro, skip-room economico corretto), CH-only. ATTEMPT 3 GRASP seed 2 partito: census 289 buchi/1262 (dentro la banda censimento 240-445: prima conferma era-corrente). Il ciclo prosegue autonomo fino a maxAttempts/pick-best (driver self-cleaning, summary in /tmp/pred-adopt-summary.txt). LESSON OPERATIVA registrata: kill-app ammazza ogni quarkus-run.jar della macchina, lane incluse - mai redeployare col kill-app mentre una lane gira.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Predictor DISARMATO (commit 4219c1a1): falsificazione appaiata same-seed (scartato 209 buchi consegna [-3/0]/72min, MEGLIO del controllo [-9/0]/89min; BT#1 tsh -13 box 0), flag planner.multistart.profile-predictor.enabled DEFAULT OFF opt-in, metrica RITIRATA non ricalibrata (624 non-monotona), census keep-measuring a ogni costruzione (feed registry vivo: 67/206/289 buchi finora), residual banda 240-445 dichiarato. Gates: /simplify 4-reviewer + code-review high 4-angle (209 vs 205 corretto con prova, CSV riallineato) + suite 1404/0/0 EXIT 0 x3 sul frozen tree. Adozione: retry GRASP parte davvero, bacini duri gestiti dalle guardie esistenti.
<!-- SECTION:FINAL_SUMMARY:END -->
