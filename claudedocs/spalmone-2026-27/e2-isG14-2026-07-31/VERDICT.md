# VERDETTO E2: rimozione esenzione isG14 (zai, 2026-07-31)

A/B spike S1 (la domanda era: l'esenzione G14 da M20/M33 si puo' togliere? Lo spike S1 al
checkpoint di costruzione era NO-OP; serve full-solve). **Baseline = T19 baseline-mwd1**
(stesso codice default, isG14 ON: -110h/-971488m/6405s, full-day 90,0%, rec UNP 75,7%) -
riusato come baseline comune degli A/B (config identica). **Test** = esenzione rimossa via patch
temporanea (`isG14()` -> `return false`, chokepoint unico: disabilita tutte le guard M20/M33/
effectiveQuota/NOT_G14). Full-solve staged, setup production fresco identico. HEAD `c6abbb16`.
**Patch revertita prima del commit. Nessuna adozione** (misura).

## VERDETTO (3 righe, dai raw)
- **Togliere l'esenzione MIGLIORA il medium di ~108k** (-863692 vs -971488) tenendo ricorrenza
  (75,6% vs 75,7%) e full-day quasi pari (89,4% vs 90,0%): senza esenzione il solver riempie di piu'
  i giorni di G14 e il deficit M20 scende. MA **hard catastrofico -5716** (-5826 vs -110).
- **E' convergence failure, NON infeasibility**: la fase 5 (LS Room Refinement) era ancora attiva
  (-9422 -> -5826 in 600s, ancora in calo) quando il secondsSpentLimit 600s l'ha tagliata. Le stanze
  sono assegnabili, ma forzando G14 densa (THU/FRI) la domanda di stanze si satura globalmente e
  600s non bastano a chiuderla. Room-null non-esente 92 -> 5808, sistemico su tutti i gruppi
  (G09 779, G05 766, G07 756... G14 solo 56: non e' G14-isolata, e' il cascade).
- **L'esenzione e' un CONVERGENCE AID, non un requisito di correttezza** (le stanze non mancano,
  manca tempo per assegnarle quando G14 e' forzata). Allineato alla direttiva primaria: e' una
  scelta di search-config (dove il solver spende sforzo), attaccabile col budget solver, non col
  modello. **KEEP l'esenzione**: il guadagno medium (~108k) non vale l'esplosione hard, e hard sta
  sopra a medium nella priorita'. Risposta a S1: NON si puo' togliere senza accettare il non-
  convergence del room-phase (o alzare il budget stanze). Lo spike S1 "no-op al construction
  checkpoint" era corretico ma MYOPIC: l'effetto emerge nel room-phase, non in costruzione.

## Tabella raw (verificata su artifact + DB + phase-progression)

| metrica | baseline (esenzione ON) | test (esenzione OFF) | delta |
|---|---|---|---|
| **score hard** | -110 | -5826 | **-5716 peggiore** |
| **score medium** | -971488 | -863692 | **+107796 migliore** |
| **score soft** | +6405 | +20945 | +14540 migliore |
| boxed (unassigned) | 0 | 0 | invariato |
| room-null non-esente (H29) | 92 | 5808 | **+5716 peggiore** |
| H17 holes (hard) | 18 | 18 | invariato |
| full-day rate | 90,0% | 89,4% | -0,6pp |
| ricorrenza UNPINNED | 75,7% | 75,6% | -0,1pp (pari) |
| ricorrenza TOTALE | 76,7% | 76,6% | -0,1pp (pari) |

Decomposizione hard test: 0*2 (boxed) + 5808 (room-null) + 18 (H17) = -5826 ✓

## Meccanismo (ipotesi, non over-claim)
Senza esenzione, M20/M33 penalizzano il sotto-riempimento di G14 (THU 0-4 + FRI 0-2 aperti,
~7 slot/sett per 120h). La LS timeslot spinge lezioni nei giorni G14 per ridurre il deficit
(medium migliora), ma la collocazione piu' densa satura la domanda di stanze globalmente
(stanze condivise scuola-wide). Il room-phase entry passa da ~-2739 (baseline) a -9422 (test),
e 600s non bastano a convergere (tagliato a -5826). Il cascade spiega perche' il room-null e'
sistemico (tutti i gruppi) e non G14-isolato.

## Phase-progression test (fasi, UTC = local -2h)
1. CH Lesson Placement (20s) -> 2. LS Compactness (15min, unimproved 600) -> 3. Compaction ->
2. LS Compactness (7min) -> 3. Compaction -> 4. CH Room Assignment (entry -9422hard) ->
5. LS Room Refinement (600s, **tagliato** a -5826hard, isComplete=true ma per time-limit, non per convergenza).

## Limiti
- n=1. La convergenza-failure e' ben supportata dalla phase-progression (fase 5 ancora attiva al
  cut-off, non minimo locale): la DIREZIONE (off -> hard explosion, medium migliora) e' robusta,
  le magnitudini no. Eventuale conferma: re-run test (se maintainer vuole), ma il verdetto KEEP e'
  conservativo e no-cost a prescindere.
- Il meccanismo del cascade sistemico e' ipotizzato (non isolato da run di controllo).
- Patch revertita; JAR pulito da rebuildare per E3.

## Artifact
- test: `e2-isG14-2026-07-31/test-no-exemption/` (analysis.json, export.json.gz, lessons.txt, KPI, phases.json)
- baseline (riuso): `t19-2026-07-31/baseline-mwd1/`
