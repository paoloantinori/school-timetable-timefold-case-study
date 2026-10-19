# Confirm run MT-LS ratificato - production standard (laptop, 2026-08-14)

> Filone A del mandato notturno 14/08. Obiettivo: verificare la ratifica cbb74818
> (multi-threaded LS DEFAULT ON) nel path production-standard: profilo fresco,
> import-calendario, pre-assign, solve-staged con NESSUN -D (tutto default).

## Setup

- HEAD `e964f667` (main checkout, pull del 14/08). JAR rebuildato (`rebuild.sh --build-only`)
  e verificato: classi `solver/multithreaded/*` presenti nel jar app (13:31).
- Profilo fresco `mtls-confirm-14` (seed DataGenerator 1368 timeslots / 14 gruppi / 12.821 lessons).
- Gate import SUPERATO: 1993 stage pins + 866 MODA lab skeleton + 327 off-grid.
  NOTA: il gate seed FALLISCE su "expected 1376, got 1368" -> il gate e' stantio, non il seed:
  1368 e' il numero corretto post-TASK-408 (CLAUDE.md: 171 giorni x 8 slot). Divergenza
  documentata, gate da aggiornare a parte (non bloccante, le tre asserzioni import passano).
- Banner boot: `Multi-threaded LS: enabled=true, threads=8`; `Multi-start staged retry:
  enabled=true, maxAttempts=3`; `grasp=false seed=0` (costruzione deterministica);
  `M20 supply-cap ratios (capacity > curriculum): {4=0.898, 8=0.9399, 9=0.994}`.
- MT-LS armato per il solve: `Staged solve: MultiThreadedLocalSearchDecider armed
  (8 threads, 1 LS phases swapped)` + `Multi-threaded LS phase started: 8 move threads,
  buffer size 80` (log app, thread staged-solve).

## Risultato

| metric | valore | riferimento ieri (A/B) |
|--------|--------|------------------------|
| **hard finale** | **0** | 0 (entrambi gli arms) |
| medium | **-947.797** | -1.654.263 (OFF) / -1.711.636 (ON-rerun) |
| soft | +8.446 | -19.850 / -22.511 |
| **wall solve-staged** | **~22 min** (565s B-TIMESLOT + 604s B-ROOM) | 114 min (OFF) / 86 min (ON-rerun) |
| full-day | 90,3% | in banda |
| ricorrenza unpinned | 75,8% | 75,6-75,7% |
| buchi H17 interni | **0** | 0 |
| day-start scarti >=3 | 4 celle (G13 ven 13, G11 mer 4, G09 ven 4, G04 mar 3) | banda nota |

**Verdetti del mandato, tutti verificati sul log:**

1. **Banner**: presente e corretto. `enabled=true threads=8`.
2. **hard finale = 0** al tentativo 1. Il ciclo e' completo e legittimo: cronologia
   phases.json piena (CH -> LS Compactness -> Compaction -> B-TIMESLOT gate 565s ->
   B-ROOM 604s), `staged multi-start attempt 1 done: score=0hard/...`, stop al primo
   0-hard (design del wrapper, TASK-483). Nessun crash silenzioso.
3. **Wall vs atteso**: NON confrontabile 1:1 con l'86 min di ieri, per un motivo
   strutturale onesto: il wall dell'intero solve dipende da QUANTI tentativi serve il
   multi-start. Ieri l'arm ON-rerun usò 2 tentativi (tentativo 1 B-TIMESLOT 3096s non
   chiuse lo 0-hard; tentativo 2 lo chiuse: 679s + 603s). Oggi il tentativo 1 ha chiuso
   subito (565s + 604s). Per-attempt, i cicli sono nella stessa forma (B-ROOM 604s identico
   al secondo). La speedup 25% resta quella misurata dall'A/B within-session di ieri; questo
   run conferma che il path default production consegna 0-hard e che nessuna regressione
   meccanica e' comparsa dopo la ratifica.
4. **BrokenBarrierException**: **ZERO a livello ERROR in tutto il log** (grep
   `ERROR|BrokenBarrier` = 0 match). Il cleanup ratificato in cbb74818 le ha silenziate.

## Osservazioni (per valore, non per blocco)

1. **Medium -948k e' il migliore mai visto su questo path** (riferimenti: ab-off
   -1.654k, ab-on-rerun -1.711k). Driver: M32 -131k/843 match (vs -620k/4667 di ieri):
   il ciclo ha trovato una schedule con template-deviation molto piu' bassa. Single run,
   n=1, non A/B: lo segnalo come punto, non come claim.
2. La breakdown M20/M33 del run (M20 -87k/84 match, M33 -13.3k) resta dominata dal
   pattern G04 (47 giorni a -1h e 19 ven a -2h): coerente col floor strutturale di
   TASK-497. La misura per-gruppo e' in kpi-fill.txt.

## Artefatti

Raw completi in questa directory (score.txt, analysis.json, export.json.gz, phases.json,
lessons.txt, KPI battery, HTML comparator). Boot log NON incluso: `*.log` e' gitignored
(contiene i banner citati sopra, citati per riga di log).

Artifacts committed: report + all KPI .txt + score/analysis/phases/export/lessons.
