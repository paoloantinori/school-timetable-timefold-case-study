---
id: TASK-614
title: >-
  TASK-614 - CRASH delivery: placement fuori VRP sopravvive al ciclo e uccide la
  consegna (n=2, escalation del precedente 28/08, path DEFAULT)
status: Done
assignee: []
created_date: '2026-09-14 13:06'
updated_date: '2026-09-15 14:03'
labels:
  - bug
  - crash
  - delivery
  - high-priority
milestone: m-14
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SCOPERIA dal gate A/B TASK-612 (laptop 14/09, braccio CONTROLLO con flag tutti OFF: il crash e' sul path di DEFAULT). Il thread staged-solve MUORE alla delivery refinement (runRoomSolveStage, rifinitura stanze del tentativo condannato dopo che il predictor ha scartato 3 reseed): IllegalStateException 'The value (2027-06-10T11:00 - 2027-06-10T12:00) ... assigned to the entity (Lavorazioni meccaniche(10060)), but it is outside of the related value range' da AbstractScoreDirector.assertValueRangeForBasicVariables al re-init del solver di consegna. NESSUNA consegna, UI ferma NOT_SOLVING con stagedStage nullo, pick-best mai raggiunto. Firma IDENTICA al precedente n=1 del 28/08 (stesso timeslot 2027-06-10T11:00-12:00, stessa finestra VRP, stessa materia Lavorazioni meccaniche, allora entity 10021): ALLORA la violazione era emersa DENTRO FinalClosingCampaign e il wrapper la catturava ('FAILED continuing with the pre-campaign winner', consegna sopravvissuta); OGGI (n=2) emerge su un confine SENZA catch: escalation di gravita' perche' la placement fuori-VRP sopravvive nello stato consegnato e l'assert puo' scattare su QUALUNQUE re-init successivo. RAW: claudedocs/spalmone-2026-27/task612-results/ab-gate/arm-a/app-crash.log + crash-context.txt (commit c7cc6700).

DIAGNOSI DA FARE (chi muove una lezione fuori dal suo VRP durante il ciclo?): sospetti ordinati: (1) pass deterministici che computano celle per ARITMETICA di slot invece che via Timeslot reale/VRP (la data 06-10 e' fine-anno dove la griglia ha giorni corti con buchi: un 'slot+1' o 'last+1' calcolato male puo' produrre un timeslot fuori range per quel corso); (2) il 28/08 la firma era in FinalClosingCampaign: partire dal suo modulo di mosse e dai sibling che condividono lo stesso idioma aritmetico (QFR/EDF slides usano tsByDateSlot.get(j-1) con null-check, vanno verificati per giorni con griglia bucherellata); (3) restore/save multi-start (stale state fra tentativi). CURE A DUE LIVELLI: (a) causa radice (il pass che scrive il placement illegale); (b) robustezza del confine: la delivery refinement NON deve poter uccidere la consegna (catch + fall-back al pre-refinement winner, stesso pattern del catch FinalClosingCampaign). PRIORITA' ALTA: e' un ammazza-consegne sul path default (n=2 su ~15 solve dell'ultima settimana), peggio di qualunque residuo hard.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DIAGNOSI AVVIATA (Fedora, lettura raw arm-a + stack laptop): l'eccezione appare DUE volte nello stesso log del 14/09: alle 12:57:27 DENTRO FinalClosingCampaign, CATTURATA dal wrapper ('FAILED continuing with the pre-campaign winner'), e poi al thread-death alla delivery refinement (runStagedSolver:1755 -> DefaultSolver.solve -> setInitialSolution -> assertValueRangeForBasicVariables). CONSEGUENZE: (1) la placement illegale (Lavorazioni meccaniche ~10060 @ 2027-06-10T11:00, fuori VRP) e' creata A MONTE di FCC e sopravvive nello stato: FCC non e' il creatore, e' solo il primo re-init che la inciampa; (2) qualunque re-init successivo la riprende (la delivery refinement e' quello senza catch); (3) firma data+ora+materia IDENTICA al n=1 del 28/08 su bacino diverso = calcolo deterministico legato al confine fine-anno (06-10 = ideal end date di TUTTI i gruppi nei log TargetEndDate) + ora 11:00. MANDATO DIAGNOSTICO IN VIRO SUL laptop: walk dei filtri VRP sull'export arm-a (quale filtro esclude 06-10@11 per quel corso), confronto cached-vs-fresh VRP (trappola TASK-578 inversa: cache piu' larga), riga quota del gruppo per 06-10. CURE A DUE LIVELLI confermate nel design: (a) causa radice (il pass che piazza senza VRP fresco), (b) catch alla delivery refinement con fall-back al winner pre-rifinitura (il pattern FCC).

RISPOSTA D1 (laptop 588b75e2, step15CrashVrpWalk, doc task614-results/DIAG-tre-domande-2026-09-14.md): lo slot 2027-06-10T11 e' legale per 7 filtri su 8 e respinto SOLO da H5.1 teacher-unavailability (T10, gruppo G13, tag meccanica, lezione quota-bound lab: 4 slot legali su 06-10). La quota NON c'entra (riga 06-10: aula=0 lab=4, giorno permesso). Fatti: (a) la placement illegale NON e' mai stata persistita (export: persistita legale a 2026-10-06T14:30) e' esistita solo in memoria; (b) fresh.contains(crashSlot)=false E cached.contains=false, size uguali 372 (nessuna divergenza cache/fresh misurata); (c) INFERENZA dichiarata: il grandfather di computeAvailableTimeslots ri-aggiunge sempre lo slot corrente, quindi su cache ricomputata l'assert non puo' partire: il solver di consegna vedeva un range NON ricalcolato (candidato: cache clonata stantia, classe TASK-578). CLASSE DELLA CURA (a): un pass deterministico muove la lezione con landing gate SENZA consultazione VRP-fresco/availability (famiglia indicata dal 28/08: TASK-577 v5). CURE: (b) IMPLEMENTATA OGGI (catch alla delivery refinement con restore snapshot + consegna del winner pre-rifinitura: la rifinitura non uccide piu' la consegna, il pattern FCC); (a) resta aperta: identificare il pass H5-cieco (serve un run tracciato o Byteman sul confine).

STATO FIX (b): catch RuntimeException alla delivery refinement con PlacementSnapshot.capture PRIMA del freeing distruttivo delle stanze + restore nel catch + consegna winner pre-rifinitura (deliveryRefinementRan resta vero: il restore incondizionato salva lo stato ripristinato, coerente). Compile + 315/0; suite integrale in corso; review agent sui punti (leak marker refiningForDelivery su Error, fall-through refined==null).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO (15/09): cura (a) causa-radice TROVATA E IMPLEMENTATA: il mover era FinalClosingCampaign (gate cieco ad H5: piazzava T10/G13 su slot H5.1-unavailable; il javadoc che negava il caso era falsificato). Fix: isSlotLegalFresh sui DUE landing di FCC (pull-up + candidato donore, 42682724, con fall-through al donore su reject VRP). Cura (b) confine: catch alla delivery refinement con PlacementSnapshot.capture PRIMA del freeing stanze + restore nel catch + consegna winner pre-rifinitura (2ba9c1d8/00ec2b0e). Fixture FCC fixate (707c8853: le 9 failure erano statics non pubblicate). VALIDAZIONE: crash-control dell'A/B 612/613 VERDE su codice curato (l'attraversamento del confine che ammazzava il thread esce vivo); zero IllegalStateException nei gate successivi.

GATE DOD POST-HOC (15/09): la cura e' nel range rivisto 1c4ce938..1bf70bc1 (FCC +33 righe + catch ScheduleService): /simplify 4 agent + code-review (review-local 4 + verificatore adversariale) sul diff: nessun finding sulla cura (il verificatore ha esplicitamente confermato capture-before-freeing e restore-rooms). Bonus finding laterale corretto nel range: FCC %d->%s (46af04b7, FORMAT_FAILURE che dal TASK-537 zittiva la riga di esito FCC in OGNI run, trovato da maintainer in shconsole).
<!-- SECTION:FINAL_SUMMARY:END -->
