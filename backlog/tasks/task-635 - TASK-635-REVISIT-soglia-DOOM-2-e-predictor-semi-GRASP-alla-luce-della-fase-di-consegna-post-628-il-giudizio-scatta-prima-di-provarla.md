---
id: TASK-635
title: >-
  TASK-635 - REVISIT: soglia DOOM -2 e predictor semi GRASP alla luce della fase
  di consegna post-628 (il giudizio scatta prima di provarla)
status: Done
assignee: []
created_date: '2026-09-20 19:28'
updated_date: '2026-10-04 09:25'
labels:
  - claude-planned
  - m-15
  - revisit
  - multi-start
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal dubbio di maintainer (20/09 sera, durante integrato-6): "ha senso o sono decisioni storiche da rivalutare alla luce dei progressi recenti?" sul meccanismo DOOMED del wrapper. I FATTI: tentativo 1 di integrato-6 condannato al confine post-RELEASE con timeslot-tier -17 (soglia STAGED_HARD_ACCEPT_THRESHOLD = -2, TASK-498 del 21/08); via del condannato = stanze CH-only, rifinitura saltata, chiusura -7478 (numero non-confrontabile, gonfio di H29 CH-only); tentativo 2 in respinamento semi GRASP (predictor TASK-624 scarta costruzioni con >150 buchi H17).

LE DUE DECISIONI STORICHE DA RIVALUTARE (entrambe PRE-628, cioe' pre-scoperta che lo 0-hard nasce nella fase di consegna):
1. SOGLIA -2 (TASK-498, 21/08): nata per risparmiare ~600s di rifinitura stanze sui tentativi condannati. MA il giudizio scatta PRIMA che la fase di consegna (CSF+EDF+HRF, pre-save) venga mai provata su quel tentativo. La catena di consegna OGGI (613+619+620+621+629v4+H30) e' molto piu' forte di quella del 21/08: sui basi m39 ha chiuso -18->-7 e -22->-11. Uno stato -17 al confine NON e' necessariamente morto: non lo testiamo mai.
2. PREDICTOR >150 buchi costruzione (TASK-624): scarta semi GRASP a raffica (32/32 su integrato-5; in corso su integrato-6). MA il banco pratica-2 (TASK-627) ha mostrato che un seme GRASP (s101) chiudeva 10/10 celle vuote sul SUO bacino: la qualita' della costruzione non predice la consegna post-catena. Il criterio misura la partenza, non l'arrivo che la catena di riparazione+consegna produce.

FALSIFICAZIONE ECONOMICA (banco, minuti, niente ciclo): invocare la terna di consegna sullo stato condannato del tentativo 1 di integrato-6 (checkpoint per-attempt in staged-checkpoints/attempt1/, forma DeliveryTrioBenchTest col catalogo TASK-634: la voce di catalogo catturata stanotte e' proprio questo stato). ESITO A: -17 scende molto (verso -1..-5) -> la soglia -2 va ricalibrata (o la fase di consegna va girata PRIMA del giudizio doom per tentativo) con evidenza; ESITO B: resta ~-10 -> soglia giusta, domanda chiusa con prova. Stessa logica per il predictor: un seme scartato (>150) su cui la terna chiude bene e' l'evidenza per ammorbidire il criterio.

REGOLA DI RIFERIMENTO (memoria reevaluate-past-rejections): una bocciatura tecnica vecchia e' ipotesi sul contesto d'allora; macchinario cambiato = prova rifatta. Qui il macchinario e' cambiato DUE VOLTE (catena consegna 613->633, e banco 628 che ha spostato la comprensione di dove nasce lo 0).

CONFINI: non si tocca la corsa integrato-6 in volo; il banco parte a consegna fatta (o sul checkpoint gia' catturato se la corsa e' ferma). Nessun flip di soglia/predictor senza A/B e ratifica maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-04 07:45
---
DECISIONE (maintainer, 04/10, 'ricalibra la soglia') + IMPLEMENTAZIONE: la soglia condivisa -2 diventa il knob planner.staged.hard-accept-threshold DEFAULT -40, letto LIVE (ScheduleService.stagedHardAcceptThreshold()), banner proprio in refreshSolverStatics; i due siti (gate accetto B-TIMESLOT / isDoomedAtBoundary) restano sullo STESSO valore (forma TASK-498). EVIDENZA di adozione: banco DoomTrioFalsifyBenchTest (boundary -29 -> consegna -2, 93%) + braccia notte 03/10 (boundary -10/-13 bollite DOOMED, consegnate 0 e -2 via corner-b) + tabella decisione SkipDoomedRoomConfigTest con edge -40/-41 + knob rollback -2. NOTA ONESTA nei docs: le demozioni notturne erano BOX-driven (3-5), il morso soglia-specifico sulla gate B-TIMESLOT e' il regime box==0 in banda; il predictor 150 buchi NON toccato (prosciolto per esito, condannato per metrica: la sua cura e' lavoro separato). Gates: /simplify 4-reviewer + code-review high + suite; RATIFICA in CLAUDE.md.
---

author: claude
created: 2026-10-04 08:02
---
CORREZIONE post code-review 6-angle (verificata sul codice): la frase precedente 'il morso soglia-specifico sulla gate B-TIMESLOT e il regime box==0 in banda (demozioni risparmiate ~20min)' era FALSA - i semi di demozione sono esattamente le lezioni boxed (StagedPinPolicy.selectDemotions), quindi con box==0 anche la -2 demoteva nulla e usciva senza re-solve: il morso comportamentale della ricalibratura e' SOLO il verdetto DOOMED. Costo dichiarato nuovo: nei run col budget (budget-seconds>0, opt-in) un tentativo in-banda non-ultimo spende ~10min di rifinitura dal budget. Cure review applicate: righe guard del test a -2890 (discriminanti), knobs.md rigenerato (getProperty->getLong), SCHEMA.md registry aggiornato al knob, commento MultiStartRunnerTest, narrativa javadoc+CLAUDE.md dedup e corretta ('curata solo al 15%', banda composita, residual riscritto). Skip motivati: doppia lettura property nel log DOOMED (log-only, shape 3-arg pura), no-clamp su override positivo (forma di casa), estrazione acceptWithoutDemotion (gap pre-esistente end-to-end).
---

author: claude
created: 2026-10-04 09:18
---
CICLO DI ADOZIONE (lane 5438/8081, profilo fresco, default -40, POST 09:43->delivery 11:13, wall 90min): banner -40 presente; B-TIMESLOT #1/#2/#3 = tsh -27/-27/-27 box 4 (demozioni BOX-driven e inerti come la notte, la soglia non c'entra); boundary -11 IN BANDA -> ZERO righe DOOMED (le braccia notturne ne avevano una ciascuna a -10/-13) e room-refinement LS INLINE COMPLETA (601s, -11 -> -2), la stessa fase che la -2 saltava rimandandola alla corner-b; catena al delivery: EDF 0, HRF 0, steepest -2 -> -2 (residuo H33-class, identico al controllo notturno). CAVEAT INFRA: il driver ha pollato lo status durante la finestra pre-save (falso quiescente noto) e ha pkillato l'app a 11:13:28, 3s dopo steepest: la saveSchedule finale non e' atterrata nel DB lane, l'ultimo stato osservato del vincitore e' -2 (riga steepest). Volume planner-data-t635thr scartabile. Verdetto: gli osservabili funzionali della ricalibratura sono tutti confermati end-to-end su bacino fresco.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Soglia staged hard-accept RICALIBRATA -2 -> -40 (commit 1c233e10, push su main): knob planner.staged.hard-accept-threshold DEFAULT -40 LIVE + banner, entrambe le gate sullo stesso valore (forma TASK-498), rollback -D=-2 testato. Evidenza: banco 4c3513c4 (-29 -> -2, 93%) + notte 03/10 (-10/-13 consegnati 0/-2) + ciclo adozione fresco (zero DOOMED, rifinitura inline 601s -11 -> -2, catena conferma -2). Gates: /simplify 4-reviewer, code-review high 6-angle (1 mia claim falsa corretta in corso d'opera), suite 1404/0/0 EXIT 0. La meta' predictor del task resta aperta come lavoro separato (scarto giusto per esito, metrica H17 curabile: la coda H0 boxed e' il blocco vero).
<!-- SECTION:FINAL_SUMMARY:END -->

NOTTE 03/10 (laptop, lane 8081/DB usa-e-getta): (1) il veicolo RESUME cross-profilo e' FALSIFICATO per questo uso: il restore di checkpoint catturati in ALTRI profili produce placement id-incoerenti (misurato: H3 group-conflict 210 match, hard -286k su due stati; causa: id-space dei DB di cattura != id-space del DB fresco). Il banco trio resta l'unica via. (2) INPUT SEED-PULITO OTTENUTO: i due cicli freschi della notte (bracci del 503) hanno catturato staged-checkpoints/attempt1/POST_RELEASE.json del ciclo B2 con timeslotTierHard=-29 (range condannato) sul seed dell'albero corrente: e' il candidato pronto per il banco trio. (3) EVIDENZA LIVE ADIACENTE: in ENTRAMBI i cicli freschi il tentativo 1 non-pulito e' stato spedito CH-only e la sola fase di consegna (rifinitura stanze + trio) l'ha chiuso: braccio A [0/0]hard, braccio B [-2/0]hard da pre-rifinitura -3. Non e' lo stato -3..-17 esatto del task (il tsh=-29 di B2 lo e'), ma conferma la direzione: la consegna cura molto piu' di quanto la soglia assume.

BANCO ESEGUITO (04/10, commit 4c3513c4, DoomTrioFalsifyBenchTest): ESITO A sulla SOGLIA e diagnosi sulla metrica del PREDICTOR, entrambi con fidelity gate a eguaglianza esatta (restored hard/tsh = registrati, al numero). (1) SOGLIA: attempt1 POST_RELEASE del ciclo notturno B2, tsh=-29 (sotto -2 = condannato): la catena di consegna CSF->EDF->HRF->steepest porta tsh -29 -> -2 (+27, 93% curato; il residuo H33 x1 e lo stesso -2 che il ciclo reale ha consegnato: il banco riproduce lesito vivo). La soglia -2 dichiara morto cio' che la consegna cura quasi tutto. (2) PREDICTOR: seme scartato (>150 buchi) tsh=-2890 -> -2446 (+444, 15%): gli H17 (la metrica dello scarto) sono curati da HRF (-209 -> -2), la coda H0 boxed (2400+) e il blocco vero: lo scarto e giusto per ESITO ma sbagliato per METRICA. NESSUN flip (i CONFINI del task): la decisione e di maintainer. Strumento permanente: il banco gira in ~150s su qualunque checkpoint stesso-seed (PLANNER_TEST_DOOM_FALSIFY=1 + datagenerator.enabled, javadoc completo).

POST-SCRIPTUM (05/10, review finale della ratifica 678): la promessa "la meta' predictor resta aperta come lavoro separato" e' stata ONORATA nella stessa sessione: TASK-677 (disarmo del predictor, falsificazione appaiata sullo scartato che consegna -3) copre la cura; il residuo "coda H0 boxed come blocco vero" e' la domanda aperta registrata nel registro di TASK-677 con il census keep-measuring (holes/unassigned/hard a ogni costruzione). Nessun task duplicato da aprire.
