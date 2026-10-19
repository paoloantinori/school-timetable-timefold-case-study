---
id: TASK-601
title: >-
  CH teacher-centric copia-del-planner-umano: la coppia (docente,gruppo) sceglie
  le celle con occupancy globale; pochi-ore prima; flag OFF, banco prima di ogni
  ciclo
status: Done
assignee: []
created_date: '2026-09-11 15:53'
updated_date: '2026-09-12 06:15'
labels:
  - claude-planned
  - construction
  - teacher-centric
  - prototype
  - bench-first
milestone: m-14
dependencies: []
references:
  - TASK-600
  - TASK-599
  - TASK-577
  - TASK-576
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PROTOTIPO di costruzione teacher-centric che copia l'algoritmo del pianificatore umano, ora SPECIFICATO verbatim dall'intervista (claudedocs/spalmone-2026-27/referente-audio-2026-09-11/SINTESI.md + human-planner-heuristics.md). GO di maintainer 11/09 sera, sequenza: DOPO TASK-600 (verita' input M29) e TASK-599 (infrastruttura steering).

SPECIFICA (l'algoritmo di referente, da trattare come AC):
1. laboratori prima; 2. vincoli a giorno fisso (VRP, non pin: "solo martedi' ma non terminano l'incarico"); 3. stage; 4. I POCHI-ORE PER PRIMI (G14: "talmente poche ore che si giocano poco" = most-constrained-first); 5. SLOT REGOLARE PER DOCENTE GLOBALE FRA GRUPPI: la coppia (docente, gruppo) sceglie le proprie celle guardando l'occupazione del docente su TUTTI i gruppi; per i monti alti DUE giorni scelti dalla coppia; 6. quota fine-anno per tesine (gia' coperto da M12/cadenza).

I DUE GAP REALI vs il nostro WeeklyTemplateBuilder (gruppo-centrico): (a) most-constrained-first per corsi a poche ore; (b) selezione celle teacher-centric con occupancy globale. Il pair-major TASK-577 Fase A ha dimostrato che il SOLO riordino nel builder gruppo-centrico NON e' la leva (+2pp sotto gate): l'ipotesi nuova e' il gap (b), che spiega la concentrazione-coppie del golden (mediana top-3 = 80%) meglio di ogni ordinamento.

VEICOLO: WeeklyTemplateBuilder teacher-centric dietro flag default-OFF (planner.construction.human-planner-ch.enabled, forma opt-in). DEVE consumare la matrice rosso/verde dichiarata per (materia, docente) quando arrivera' dal workbook (vedi TASK-602), con fallback a hint golden-derivati: input dichiarato batte inferenza.

METODO (maintainer: "privilegiando test locali prima di lunghi run"): SCALA OBBLIGATA per ogni braccio: (1) unit sintetici; (2) banco sui fixture/checkpoint esistenti (pattern PoolCapFillBenchTest/ReserveCensusBenchTest/WitnessBenchTest, non cicli); (3) ciclo completo SOLO gate di adozione. Predizione scritta PRIMA di ogni banco. CRITERI DI MORTE pre-registrati (sul modello pair-major): concentrazione-coppie top-3 deve superare il golden-baseline 0.80 di ALMENO +5pp a fine costruzione E nessuna regressione H32-family in costruzione (celle sotto-quota), altrimenti l'ipotesi muore e si registra il verdetto senza ciclo. Guard: placement-parity TASK-517 sul piano prodotto + pair-cells M38 come binari.

AC di massima: (1) design doc con la mappa passo-referente -> nostro-meccanismo; (2) unit + banco verde con predizioni rispettate o morte registrata; (3) flag default OFF, nessun default toccato senza gate TASK-596; (4) se vince: VERDICT e passaggio al gate.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso con verdetto KILLED-BY-EVIDENCE pre-build (12/09, doc task601-results/PREBUILD-MEASUREMENT-2026-09-12.md): la misura anti-inerzia fatta PRIMA di costruire il prototipo mostra che lo stack attuale (post ratifica TASK-599) produce GIA' concentrazione-coppie top-3 mediana 0,82 SOPRA la pratica golden 0,80 (stesso metro del banco pair-major) col MIGLIORE sotto-quota mai misurato (39, vs 41 OFF e 59 del pair-major bocciato). Il criterio di successo del prototipo (>=0,85) non chiude nessun residuo: la consegna corrente [-11/0] = H34 coda 10 (import teacher-locked) + H7 1 (bacino), zero componente shape. La premessa era assorbita dal pass-0 spread ratificato: la regola del pianificatore umano era il pezzo mancante della forma-coppia, non la CH teacher-centric completa. REVIVAL: solo se emerge residuo con componente shape (ricorrenza <70% o famiglia H* su coppie multi-gruppo); la matrice rosso/verde dichiarata (TASK-602) si consuma nel pass-0 esistente, non serve una CH nuova.
<!-- SECTION:FINAL_SUMMARY:END -->
