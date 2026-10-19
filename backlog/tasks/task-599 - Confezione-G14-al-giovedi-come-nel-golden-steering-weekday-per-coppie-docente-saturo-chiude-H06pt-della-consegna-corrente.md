---
id: TASK-599
title: >-
  Confezione-G14 al giovedi' come nel golden: steering weekday per coppie
  docente-saturo (chiude H0=6pt della consegna corrente)
status: Done
assignee: []
created_date: '2026-09-11 06:28'
updated_date: '2026-09-12 05:09'
labels:
  - claude-planned
  - zero-hard-recovery
  - teacher-locked
  - golden-first
milestone: m-14
dependencies: []
references:
  - TASK-594
  - TASK-595
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla classificazione del residuo control-10sep (11/09) e dall'analisi golden del questionario (domanda 1 riformulata): il golden colloca TUTTA la Confezione G14 (30h, T16) al GIOVEDI' e riempie i venerdi' G14 (3h x 14) con Competenze Digitali, Comportamento Sociale e Orientamento. T16 al venerdi' e' satura di Confezione G01 (89h golden) + G09 (51h). Il nostro solver pianifica invece Confezione G14 al venerdi' e perde ore: control-10sep consegna 3 ore non collocate (H0=6pt, la famiglia di residuo PIU' costosa della consegna corrente), slotretry ne perdeva 1.

OBIETTIVO: il template/costruzione non deve assegnare Confezione-G14 (e in generale le coppie (docente, gruppo) il cui docente e' strutturalmente saturo quel weekday) al venerdi'. Direzione candidata (da designare a banco, golden-first): uno steering weekday per coppia derivato dalla saturazione del docente (il foglio teacher-weekday-steering.csv esiste gia' come veicolo, verificare la forma), oppure un vincolo di placement in WeeklyTemplateBuilder che declassifichi i weekday dove il docente ha carico sistematico (misurato su incarichi, non golden: sopravvive al ritiro del golden se derivato dagli incarichi).

METODO (direttiva iterazione-su-snapshot): banco sul checkpoint/fixture prima di qualsiasi ciclo; il ciclo completo solo come gate di adozione. Criteri di morte pre-registrati: se il banco non chiude le H0 G14 senza regressioni H1/H3 su T16, la direzione muore.

AC di massima: (1) design a banco con predizione scritta; (2) banco che misura H0/H33 G14 + sanity T16 su fixture/checkpoint; (3) nessun default cambiato senza gate; (4) evidenza VERDICT.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
GATE DI ADOZIONE COMPLETATO 12/09 mattina (REPORT task599-results/spread-11sep/REPORT.md, commit e5151a86 sul laptop):

CONSEGNA **[-11/0]** (8h34m, 5/5 tentativi, vincitore attempt-1 deterministico) = MIGLIORE della serie: slotretry -13, control-10sep -17, control-11sep -26. +15 hard end-to-end sul quadro corrente.

VETTORE: H34 10 (da 13), H7 1 (da 3, T26/T25 ridotti), **H0 = 0: le 3 ore G14 Confezione CHIUSE** (la cura mirata funziona end-to-end), H33/H29/H31 azzerate. VERIFICA INDIPENDENTE Fedora dal lessons.txt: G14 115/115 collocate (pool intero), Confezione 30h distribuita gen-mag.

RISPOSTE PRE-REGISTRATE: (a) SI', H0=0; (b) full-day 91,3% IN BANDA (90+-2), ricorrenza unpinned 70,3% LEGGERMENTE SOTTO la banda 76+-2 (l'unica ruga: da pesare in ratifica); (c) il collaterale H20-pin-fuori-finestra dell'iter-11 NON riappare (zero righe); (d) sotto-quota costruzione non estraibile dal log del ciclo (dichiarato nel REPORT); (e) sanity 4 rossi con composizione MIGLIORE (G14 uscito, G08 rientrato, zero lezioni senza stanza).

Dottore 579.01: DOCTOR-WARN sanity-reds=4, hard concordi (-11), export 105MiB sul laptop.

GAP OSSERVABILITA' (da censire SE ratificato): il flag non ha banner-echo in refreshSolverStatics (verificato nel processo con ps ma non loggato: stessa famiglia del gap surplus-harvest del 5/09).

STATUTO: il flag planner.construction.teacher-weekday-spread.enabled resta OPT-IN DEFAULT OFF. La ratifica (flip alla forma standard null->ON) spetta a maintainer sul pacchetto: pro -15 hard, chiusura H0 G14, nessun collaterale misurato, la semantica E' la regola del pianificatore umano (audio 11/09); contro ricorrenza 70,3% (-4pp sotto banda, da capire se costo del lever o del bacino). Checkpoint del ciclo sul laptop = input per eventuale witness-work sul residuo H34=10.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO con la RATIFICA di maintainer (12/09, commit 7cba5545). teacher-weekday-spread DEFAULT ON (forma opt-out standard null->ON, solo "false" disarma; accessor public; banner-echo aggiunto in refreshSolverStatics chiudendo il gap segnalato dal REPORT; voce CLAUDE.md completa con ruge dichiarata ricorrenza 70,3%). Catena: banco braccio-1 (Confezione-G14 ven->giov, guard 41->39) -> gate spread-11sep [-11/0] MIGLIORE della serie (H0=0 G14 chiuse 115/115, H33/H29/H31 azzerate, H34 13->10, H7 3->1, full-day in banda, collaterale H20 assente) -> ratifica con suite 1304/0 EXIT 0 e construction test 16/16 sul nuovo default. Il lever era vincente anche nell'era TASK-542 iter-11 (-24->-13) e mai ratificato: la semantica e' la regola del pianificatore umano (audio 11/09). Residuo corrente: H34 coda 10 + H7 1.
<!-- SECTION:FINAL_SUMMARY:END -->
