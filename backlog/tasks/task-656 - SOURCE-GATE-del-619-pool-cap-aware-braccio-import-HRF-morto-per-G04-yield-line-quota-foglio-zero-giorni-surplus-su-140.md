---
id: TASK-656
title: >-
  SOURCE-GATE del 619 pool-cap-aware (braccio import HRF morto per G04: yield
  line = quota foglio, zero giorni surplus su 140)
status: Done
assignee: []
created_date: '2026-09-23 11:02'
updated_date: '2026-09-27 05:44'
labels:
  - claude-planned
dependencies: []
references:
  - claudedocs/spalmone-2026-27/integrato-7/nearmiss/REPORT.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-654 (blind puzzle integrato-7, claudedocs/spalmone-2026-27/integrato-7/nearmiss/REPORT.md) ha dimostrato: la consegna [-1/0] (H17 G04 20/11 ora 11) ha una cura a 1 mossa DENTRO la grammatica import-cross-date del HoleRelayFinisher (Disegno T24 lun 16/11 ora 12 -> ven 20/11 ora 11, delta hard +1, donatore H31-safe, costo solo medium M3/M-LF). La consegna l'ha mancata perche' il SOURCE-GATE del 619 valuta la giornata donatrice con yieldLine = max(quota foglio, floor H33): per i gruppi pool-cap (G04, G08, TASK-497) il target NON e' la quota del foglio, e nella consegna G04 ha ZERO giorni surplus su 140 (distribuzione ore-yieldLine: 83x0, 54x-1, 3x-2) -> ogni candidato import muore a HRF-IMP-SOURCE-GATE, sempre. E' la stessa classe del bug curato da TASK-497/577 lato score, sopravvissuta nel gate del finisher. CURE: nel SOURCE-GATE sostituire la quota foglio con il cap pool-cap per-data (stessa fonte di expectedHoursCapFor, TASK-577) per i gruppi nella mappa M20-cap. Banco: replay dello stato integrato-7 (mossa pre-registrata nel REPORT) + check G08 + suite. Nota di processo collegata: driver.sh copia il log app SOLO dopo DELIVERED, un restart sovrascrive /tmp/planner-prod.log prima (census HRF-IMP della notte 23/09 perso): copiare il log nel capture subito dopo il solve.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ESITO 23/09 pomeriggio: (1) la cura pre-registrata del cieco era ILLEGALE (T24 VACATION venerdi', regola ricorrente lun-mar; l'arbitratore salto' l'asse disponibilita'): REPORT.md corretto con sezione CORREZIONE. (2) Enumerazione corretta H1+H5: solo 222 candidati tutti T15, i cui soggetti muoiono (RA H23 quota-bound venerdi quota-lab 0; Lavorazioni/Cad senza stanza meccanica libera @11) = cella STRUTTURALE per il braccio import. (3) La diagnosi del SOURCE-GATE resta un bug di coerenza reale, CURATO come coherence fix (commit 97e32d84): yieldLine pool-cap-aware max(min(foglio, pool-target), floor), mirror del min() di expectedHoursCapFor; banco fidelity publish della mappa nel fixture; regression IDENTICA m39-off -18->-7 (valore storico) e integrato-1 invariato. Nessun positivo dimostrato su QUESTA cella (strutturale). Gates /simplify+review della coherence fix: dovuti come follow-up. Serie: la cella integrato-7 resta -1 strutturale per la catena attuale; cure future passano da input (scuola: altro docente disponibile o stanza) non da grammatica.

2026-09-27 GATES COMPLETE (dovute come follow-up del commit 97e32d84): /simplify 4-angle compact (2 minor: FQ prefissi APPLICATI, flag live-vs-captured ANNOTATO per TASK-671; reuse CLEAN: expectedHoursCapFor NON fornisce il valore, semantica diversa) + /code-review high 7-angle (F1 wiring 2/7 APPLICATO come TASK-671, F2 flag consolidation TASK-671, F3 fidelity DeliveryTrio TASK-671, F4 teardown ANNOTATO pre-existing, F5 gates=questa review, F6 census TASK-671, F7 FQ APPLICATO). Il coherence fix e' corretto nel suo perimetro (SOURCE-GATE del 619); l'estensione agli altri 6 siti e' TASK-671.
<!-- SECTION:NOTES:END -->
