# TASK-584: Re-validazione p(0-hard) sul vincolame corrente, braccio (a) SH OFF

**Notte 12-13/09/2026** (mandato maintainer via Fedora). laptop = misura soltanto; nessuna
adozione/revert/config. Decisioni a Fedora sulle evidenze qui sotto.

## Configurazione misurata

- HEAD congelato `2f686d18` (verdetto di morte M39 del 12/09 sera; commit docs-only,
  0 file java). Jar ricostruito dalle fonti di HEAD alle 00:42 del 13/09
  (PREFLIGHT-OK jar/classi/flag).
- Vincolame corrente DEFAULT ON incluso H31+H32+H33+H34; M39 opt-in DEFAULT OFF
  (assente dalla command line, verificato); SurplusHarvest OFF esplicito
  (`-Dplanner.construction.surplus-harvest.enabled=false`, banner `surplus-harvest=false`).
- Boot prod sequenziali su UN profilo dedicato fresco (`task584`: seed 12551 lezioni,
  import-calendario 2009 stage-pin, pre-assign source=input COMPLETE, 3077 pin).
  NESSUN reset tra i run (ogni solve-staged ri-pianifica da zero).
- Per seed i (101..108): `-Dplanner.construction.grasp.enabled=true
  -Dplanner.construction.grasp.seed=i -Dplanner.multistart.max-attempts=1
  -Dplanner.pre-assign.constrained-teacher.source=input
  -Dplanner.construction.surplus-harvest.enabled=false`.
  Metrica = p(0-hard) per SINGOLA costruzione (maxAttempts=1).
- Guard per OGNI boot (doppia guard, regola TASK-579/604): lsof cwd del processo java
  = /Users/user/planner + banner `grasp=true seed=i ... surplus-harvest=false` +
  banner `Teacher-weekday-spread (TASK-599, RATIFIED 12/09): enabled=true`.
- Differenza di METRO vs lo storico: il pool 32/32 del 9-10/08 girava PRE-H31/H32/H33/H34.
  Nota di regime: lo storico disattivava il wrapper (`multistart.enabled=false`), stasera
  `maxAttempts=1` (wrapper con tentativo unico = ultimo: winner-refinement sempre attivo,
  save post-rifinitura incondizionata, consegne confrontabili, design punto 6a).

## (1) Statistica

```
runs=8 (seeds 101-108)   zero_hard=0
hard consegnati: [-33, -34, -40, -27, -37, -30, -35, -31]  (banda stretta, min -27 max -40)
p-hat = 0/8 = 0.000   Wilson95 [0.000, 0.324]
P(>=1 0-hard | N=1..5) = 0.000 alla point estimate;
  all'estremo SUPERIORE del CI (p=0.3244): N=3 -> 0.692, N=5 -> 0.859, N=9 -> 0.971
```

**Verdetto (esito (b) del task: p e' calata, e a zero)**: il claim m-13 "p(0-hard)=32/32,
multi-start consegna 0-hard statisticamente garantito" e' **SMENTIC sul vincolame corrente**.
Confronto dichiarato: 32/32 = 1,0 (Wilson ~[0,89;1,0]) del 9-10/08 PRE-H31/H32/H33/H34 vs
0/8 (Wilson [0,000;0,324]) di stanotte sullo stesso protocollo single-construction; anche
il vecchio p=0,75 (n=8, 3/08) sta FUORI e SOPRA il CI attuale. La differenza di metro e'
esattamente quella che il batch esisteva per misurare: i quattro hard DEFAULT ON introdotti
dal 19/08 al 26/08 hanno distrutto la raggiungibilita' dello 0-hard per singola costruzione.

**Raccomandazione sul budget N (esito (a) non scatta; la Decisione e' di Fedora)**: non e'
una scelta di N. Alla point estimate nessun N raggiunge P(>=1 0-hard) > 0; perfino al limite
superiore del CI servirebbe N>=9, e l'evidenza indipendente del banco M39 della stessa
notte (maxAttempts=5 di default, stesse sorgenti) consegna comunque solo -11/-13 hard:
il multi-start NON rimedia su questo vincolame. Coerente con la direttiva primaria
(correttezza logica sopra il costo): il collo e' nel MODELLO/ricerca sui vincoli
H31..H34, non nel budget di tentativi. Nota anche: costruzione stabile a -9765/-9768 su
tutti gli 8 seed (bacino cattivo STRUTTURALE, non fortuna di seed).

## (2) Tabella per seed

| seed | hard consegnato | medium | wall | sanity | concordance (TASK-526) | doom row (ch/h17/bt3-tier) |
|------|----------------|--------|------|--------|------------------------|-----------------------------|
| 101 | -33 | -582099 | 42m | NC rosso: G10, G12 | OK (mem=0 db=0) | -9766 / 15 / -48 |
| 102 | -34 | -543587 | 58m | NC rosso: G10, G11, G12, G13 | OK (mem=0 db=0) | -9766 / 18 / -54 |
| 103 | -40 | -571236 | 72m | NC rosso: G10, G11, G12, G13, G14 | OK (mem=1 db=1) | -9766 / 17 / -55 |
| 104 | -27 | -554338 | 53m | NC rosso: G10, G12 | OK (mem=0 db=0) | -9767 / 12 / -43 |
| 105 | -37 | -540735 | 44m | NC rosso: G10, G11, G12, G13, G14 | OK (mem=1 db=1) | -9765 / 19 / -52 |
| 106 | -30 | -539170 | 45m | NC rosso: G10, G12, G13 | OK (mem=0 db=0) | -9768 / 15 / -48 |
| 107 | -35 | -551898 | 47m | NC rosso: G10, G12, G13, G14 | OK (mem=0 db=0) | -9767 / 17 / -51 |
| 108 | -31 | -583592 | 55m | NC rosso: G10, G12, G13, G14 | OK (mem=0 db=0) | -9765 / 13 / -46 |

Note sulla tabella:
- **Concordance 8/8 OK**: prima validazione LIVE su 8 consegne consecutive del metro di
  fiducia TASK-526; i due run con roomless=1 (103, 105) sono concordanti memory=db su quel
  valore (il check confronta le due sorgenti, non l'assenza di roomless).
- **bt3-tier** = timeslot-tier hard a fine B-TIMESLOT #3 (il valore piu' vicino al confine
  RELEASE disponibile nel log). La riga-verdetto "DOOMED at the post-RELEASE boundary" NON
  esiste nei log di stanotte per costruzione: `isDoomedAttemptForSkipRoom` logga solo quando
  lo skip scatta, e con maxAttempts=1 l'unico tentativo e' sempre l'ultimo (ScheduleService:1966,
  verificato nel codice). Per soglia (tier -43..-55 vs STAGED_HARD_ACCEPT_THRESHOLD -2) ogni
  tentativo era doomed; le righe registro riportano dunque doomed=false nel senso di
  "skip-doomed-room non scattato" (room_skipped reale = false, corretto).
- Sanity: NON CONFORME su TUTTI gli 8 (0-hard e' binario; nessuna sorpresa).
- Wall medio ~52 min/run (vs ~27 min storico: il vincolame piu' pesante paga anche in tempo).

## (3) Fermi / anomalie

**Nessun fermo del banco.** 8/8 boot con doppia guard PASSATA (lsof cwd +
3 banner). Nessun banner errato, nessun surplus-harvest=true, nessun retry necessario.
Tre incidenti minori di tooling, tutti risolti senza impatto sulla misura:

1. **s101 state line `hard=` vuota**: il primo parse dello score (`\[-?[0-9]+hard`) non
   matchava il formato `[-33/0]hard` (le cifre non sono adiacenti a "hard"). Corretto al
   volo (`grep -oE '\[-?[0-9]+/0\]hard' | tr -d '[]hard' | cut -d/ -f1`); la riga di stato
   di s101 e' stata riparata a mano dal doom-row (final_hard=-33, log-derived). I seed
   102-108 hanno lo stato corretto alla prima scrittura. Il dato nel report viene comunque
   dal doom-row/score.txt di ogni seed, non dalla state line.
2. **sed BSD**: il primo fix usava `\?` GNU che il sed di macOS non supporta (parse vuoto
   al test); sostituito con grep/cut portabile prima del seed 102.
3. **s105 lanciato con `&` inline** invece di run_in_background: processo sopravvissuto
   (verificato ALIVE + preflight OK + guard OK nel driver log), raccolta regolare; solo la
   notifica automatica mancava, sostituita dagli heartbeat inline. Nessuna differenza di
   misura.

Anomalie osservative (nessuna azione):
- `api.sh wait` e' ritornato non-zero a ciclo gia' concluso su alcuni seed ("stagedStage
  empty - treating as done"): race noto tra l'ultima poll e il flip di stato; il driver
  gestisce senza rilanciare (regola wait-timeout-rule).
- M39 leftover di Fedora (idle, NOT_SOLVING, flag m39-stage-day) fermato prima del setup:
  volume `planner-data-m39-on` conservato, solo scollegato.
- Export per sanity: 933M l'uno, cancellati dopo il report (conservati score/sanity/
  concordance/doom-row/stream). Nessun run 0-hard => nessuna capture completa, come da
  mandato.

## Raccolta

- Per run: score a consegna + sanity-report + riga doom-prediction-registry (20 colonne,
  appenda a `../doom-prediction-registry/attempt-traces.csv`, cycle_id
  `task584-shoff-13sep`) + riga delivery concordance. Export da 933M cancellato dopo il
  sanity (conservati solo i report).
- Colonna `h17_holes_after_construction`: estratta dalla riga "TASK-419 hole repair -
  H17 hole slots N -> M" (prima occorrenza) = buchi H17 visti dal repair pass
  post-B-TIMESLOT, non strettamente a fine costruzione (fallback dichiarato dal mandato:
  estrattore /tmp/extract581.py non piu' presente).
- Raw per seed: `seed-1xx/` (driver.log, score.txt, sanity-report.txt, concordance.txt,
  doom-row.csv, wait.log, score-stream.txt). Log app: `/tmp/planner-prod-s1xx.log`.
