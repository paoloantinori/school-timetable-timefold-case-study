# TASK-556 VERDICT finale sprint QFR v2 (notte 26/08, ciclo completo di conferma)

## Ipotesi (predizione fcb81e1b, scritta PRIMA del lancio)
Consegna -6..0hard; M20.2 49 -> 34..40 celle; medium -1.40M -> -1.30/-1.34M (-60..-110k);
KPI in banda; sanity residua sugli stessi 2 gruppi giorni-corti.

## Risultato misurato (ciclo completo dci-556q2, ~1h23, capture canonica + manifest finalized)
- CONSEGNA: **0hard/-1.317.237medium/2.288soft**. 0hard in banda alta; medium
  **+84.569 vs il ciclo finisher-ON (-1.401.806): dentro il range predetto**.
- QFR occasione 1 (post-RELEASE): **53 mosse, 15 celle chiuse, 0 conflitti, NESSUN revert**
  (la catena gates a/b/c/d tiene in produzione). Occasione 2 (post-B-ROOM): 0 mosse (pulita).
- Finisher: banco 81 -> 0 (quarto banco diverso, quarta chiusura 100%).
- **M20.2: 49 -> 38 celle** (predetto 34..40: centrato; chiuse 11 delle 21 reali, le 28
  FC/G14 restano by design). M22 invariato (-427k: la finzione, come da analisi).
- Lettura viz (triade, DOM): **giorni vuoti in span TOTALE 42 -> 34** e **giornate profonde
  45 -> 34** (le due facce della cura: meno giorni vuoti e meno giorni magri). Le forme dei
  gruppi puliti restano identiche al golden.

## IL ROVESCIO (sanity gate: NON CONFORME, firma PEGGIORATA)
- 5 gruppi rossi (G07, G08, G09, G10, G11) contro i 2 dei cicli precedenti.
- **2 giorni con buchi interni** (G07 09/06 slot 1-2; G08 01/04 slot 1-3) contro 0.
- Sotto-minimo sparsi su febbraio-aprile (giorni portati a quota ma sotto il min golden).
- Lettura: le mosse v2 riempiono i giorni dichiarati vuoti ATTINGENDO da giorni che finiscono
  sotto il minimo praticato e, in due casi, la composizione lascia buchi che lo scorer hard
  non vede (pattern H17-like ma conteggiati solo dal sanity gate per-gruppo).

## Raccomandazione a maintainer: TENERE il codice, NON adottare il flag
- Il guadagno e' reale e in predizione (+85k medium, -8 giorni vuoti, -11 giornate profonde,
  hard intatto), MA costa firma sanity (5 gruppi rossi, 2 buchi interni): fino a quando la
  domanda giorni (referente) non dice quali giorni vanno riempiti e con quale minimo, il pass
  ottimizza il metro contro la pratica. **Flag resta DEFAULT OFF**; il candidato upgrade
  (prima di qualunque adozione) e' un gate donatore day-floor-aware (il donatore non scende
  sotto il minimo golden-praticato del suo gruppo, oggi H32-quota-only).
- Alternativa/complemento: estendere il gate (c) atomico ANCHE ai fill parziali di giorni
  non-vuoti (oggi atomico solo per i ex-vuoti).

## Artefatti
ciclo-qfrv2-on/ (capture completa 21 file, manifest finalized con banner, app.log merged),
registro iterations-542.md voci DIAGNOSI H31 / ITER-3 / CICLO COMPLETO.
