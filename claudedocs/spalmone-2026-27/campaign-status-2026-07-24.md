# Stato campagna spalmone 2026/27 — chiusura ciclo del 2026-07-24 (~21:00)

## Stack adottato (tutto su HEAD, validato dall'A/B cumulativo task414-results)
- **TASK-398+401**: box-the-residual + BoxRepair per enumerazione (hard −1/0, box 0).
- **TASK-394**: ideal-end fact-based (G06 10/06, non più 31/03; M12 −24k → −7k).
- **TASK-405**: day-start anchoring (81,9% → ~95%; M18 widened peso 50 + left-anchor pass).
- **TASK-406**: repair tail deficit-aware (full-day 77 → 79 nel suo giro).
- **Q22/TASK-412**: 7 docenti a giorni fissi golden-derived come dati (T01 100% gio...);
  fingerprint docenti ora combacia col golden. In attesa conferma referente (domande-brevi Q22).
- **TASK-409**: M20.2 giorni-attesi-vuoti (economia anti-evacuazione; golden 2/2.213).
- **TASK-410**: settimane attive stage-aware (fixture costruzione 54 → 85% scoped).
- **TASK-414**: eviction a un livello (ipotesi B risolta: box 9-11 → 0 LIVE, evicted 3/7,
  conflitti 0, gate partenza H17-safe).

## Stato finale misurato (val414, 2 run)
hard −1/−2 (SOLO H17, variance), **box 0/0** (12.301/12.301), knot 0, ricorrenza 37,9/36,6
(sopra soglia), blocchi 2.159/2.198, day-start 95,2/94,4, full-day 77,2/77,5,
M20+M20.2 ~−1,14M, medium tot ~−1,8M.

## Residui APERTI (search-domain / dati, mai annacquare il modello)
1. **KPI gap vs golden**: full-day ~77 vs 83,7 golden; M20+M20.2 −1,14M vs −786k del vecchio
   401 (in parte fisiologico: vincoli più precisi misurano di più). Lever: TASK-413
   (quasi-pinning GRADUATO per scarsità — il prossimo grosso pezzo), M22/TASK-408, benchmark
   metaeuristiche (la ruin-recreate è spesso inerte nei run recenti).
2. **G04**: 18-27 giorni-vuoti M20.2 strutturali (domanda 750h < piano 990h) — domanda per
   la scuola (con la Q "compattare o spalmare" del 407).
3. Coda task: 413 (meccanismo quasi-pinned), 408/M22, 411 (misura nitidezza), 412-generale,
   403 (repair tra ph2-ph3), 404 (Quarkus 3.33 LTS a fine campagna), 402 (fallback, superata
   dai fatti: la B si è risolta con l'eviction, non rilassando il template).

## Strumenti permanenti nati nel ciclo
golden-vs-run-viz (4 viste, rigenerato a ogni A/B), day-start-kpi, discriminatore VRP
generalizzato, checkpoint harness generalizzato (val398/val410), protocollo checkpoint
(export JSON + validazione snapshot-first), procedura tmux laptop (CLAUDE.local.md).
