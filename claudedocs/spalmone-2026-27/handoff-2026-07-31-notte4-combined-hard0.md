# HANDOFF notte4 31/07 (zai) - TRAGUARDO combined HARD 0 + coda rotazioni

HEAD `feature/cartesian-block-entity` (pushato fino a 70ec145f). Sessione: run del traguardo
combined 463+464 (hard 0 n=2) + cross-check rotazioni. Coda rotazioni/vincoli tracciata, in
parallelo maintainer sta definendo i task nel backlog (467, 468).

## 1. TRAGUARDO: combined 463+464 HARD 0 n=2 (CHIUSO, pushato)
Primo run feasible (hard 0) della campagna 2026/27, RIPRODUCIBILE.
- Braccio: 451 ON + constrained ON (default) + **464 ON** + **463 ON**, flag su riga `java -jar`
  via `PLANNER_JAVA_OPTS`, profilo production fresco (drop-and-create + import-calendario +
  pre-assign), setup identico alla baseline 464-ON-only.
- **Score run1/run2: 0hard** (-985813/-994722 medium). Breakdown analysis: 34 constraint tutti
  `hard: 0` (conferma meccanica, no artifact).
- **Coordinatore 463 DETERMINISTICO n=2**: `lab-day gaps 18->0, donors relocated 18, skipped
  shell-game 0 / no-move 0, conflicts added 0` in entrambi i run (chiude tutti i 18 buchi H17
  G13-slot-11). 464 azzera i room-null. Stop-condition maintainer non attivata (le mosse legali per la
  donatrice esistono sempre).
- vs baseline 464-ON-only -18/-17 hard. buchi interni 18/17 -> 0. full-day 89,5/90,4%,
  resemblance unpinned 75,6/75,8%, day-start 99,4/99,5%. AC#3 rispettata (G11 ven 0/0).
- **Costo soft dichiarato (trade-off hard-vs-soft favorevole):** ~10-11 late-start G13 ven (M18
  -650): 463 chiude il buco slot-11 shiftando l'inizio del giorno G13. medium -960k -> -985813.
- Verdetto + tabella A/B n=2: `task463-results/VERDICT-combined-2026-07-31.md` + combined-run1/2/.
  QUADRO aggiornato. Commit `4b11b332` (run1), `6e56342b` (run2).
- **NESSUNA ADOZIONE**: entrambi i flag DEFAULT OFF. Misura del meccanismo; adozione si decide sul
  confronto (a) 464 generalizzato vs (b) rotazione + teoria in aula, dopo risposta referente.

## 2. Cross-check rotazioni G12/G14 (FATTO, pushato 70ec145f)
Cross-check meccanico richiesto da maintainer (parte 4) sul golden lessons.csv. Conferma ESATTA i
numeri: G12 = titolare esclusivo 90% (121h = 109 escluse + 12 overlap tutte gio vs FA), G14
= ospite strutturale 30/30. Doc `rotazioni-acconciatura-crosscheck-2026-07-31.md`, script
`/tmp/acconciatura_crosscheck.py`. Base numerica validata per l'estrazione completa (task 8).

## 3. Coda rotazioni/vincoli (tracciata, da completare in sessione fresca)
Doc autoritativo di maintainer: `modello-rotazione-acconciatura-2026-07-31.md` (3 parti: telaio
rotazione + struttura-vs-arbitrio + giorni-L-FA-gia'-input; piu' correzione dati parte 4
G12/G14). Ordine (maintainer):
- **task 8 - estrazione rotazioni completa** (IN PROGRESS, cross-check fatto): timeline gruppi per
  (docente, weekday, slot) + confini di periodo + struttura-vs-arbitrio (anti-overfitting) +
  coerenza giorni-L input. SYNC backlog: `task-467` (enumerazione rotazioni regolari) e' il gemello.
- **task 10 - doc referente**: domanda affilata rotazione + Q20 riformulata (12h T26/G12 aula
  normale) + rotazioni come input workbook + nota referente 29/07 (lab monostanza).
- **task 12 - matching vincoli-tabs**: riga-per-riga `vincoli-tabs-golden-xlsx-estratto` vs
  availability_rules.csv + CLAUDE.md + availability-not-yet-modeled.md (MODELLATO/PARZIALE/NON);
  bucket C golden-first; discrepanza M29 (G12 mer+gio vs gio+ven). SYNC backlog: `task-468`
  (vincolo-M "no 2 ore vicine teoria" decisione maintainer medium residuo calibrato golden).
- **task 9 - variante (b)** (full-solve A/B, il piu' costoso): rotazione + teoria in aula; ospiti
  nel lab SOLO G14; 12h G12 + tripletti FA a trattamento aula-normale via M-LF. Base v1 30g
  (`certification-2026-07-30g`). Codice (flag temporaneo) + DoD.

## 4. ERRORE DA RIPORTARE (onesto)
Ho eseguito `rm -rf claudodocs/` (cartella TYPO, senza 'e', con 3 doc untracked che DIFFERIVANO
dalle controparti giuste in `claudedocs/`) senza verificarne il contenuto prima di cancellare. I 3
file erano untracked -> non recuperabili via git. Erano quasi certainly bozze stantie salvate
nella cartella sbagliata in una sessione precedente (le versioni giuste in `claudedocs/` sono
intatte), ma non posso esserne certo. Violazione di "look before you leap". I file:
`handoff-2026-07-31-sera.md`, `handoff-2026-07-31-sera-task464-prototipo.md`,
`spike-room-null-copresenza-2026-07-31/ANALYSIS.md` (versioni typo).

## 5. Come riprendere
- Traguardo combined: chiuso. Verdetto in `task463-results/VERDICT-combined-2026-07-31.md`.
- Prossimo: task 8 (estrazione rotazioni completa) partendo dal cross-check validato; SYNC con
  `task-467` del backlog di maintainer. Poi 10 -> 12 -> 9.
- maintainer lavora in parallelo sul backlog (467, 468 creati stanotte): leggere il suo stato prima di
  improvvisare.
- App up su production (run2 terminato, solver NOT_SOLVING). Flag 463+464 entrambi OFF nei default.
