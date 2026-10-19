# VERDICT A/B adozione TASK-636 cadence-pace (21/09 sera, due profili freschi)

Setup: braccio A task636-off (flag assente), braccio B task636-on
(-Dplanner.construction.cadence-pace.enabled=true sulla riga java -jar, verificato nel
cmdline), entrambi con capture ON, jar 8f25ce11 (+9920d349/191107dd cumulati), heap 2g.

## I 5 CRITERI (verifica meccanica, numeri dai raw)

| # | criterio | OFF | ON | esito |
|---|----------|-----|-----|-------|
| 1 | hard ON mai peggiore di OFF | -2 | **0** | PASS (ON migliore) |
| 2 | ricorrenza unpinned >= 68 entrambi | 69,2% | 70,5% | PASS |
| 3 | Servizio tecnico e stilistico G06 giugno: ON <= OFF | 4h | 4h | PASS (pari) |
| 4 | celle H34 sul consegnato | 0 | 0 | PASS (pari) |
| 5 | banner ON armed=true con paceBoostApplications > 0 | armed=false, 0 | **armed=true, 2188-2319** (116 banner) | PASS |

TUTTI E CINQUE PASS.

## Consegne

- OFF: [-2/0]hard/[-1204901/6893]soft, tentativo 2 (attempt 1 non-zero, retry), sanity
  NON CONFORME G09 + G10 (famiglie isolate note), full-day 94,5%.
- ON: **[0/0]hard**/[-1204375/-47]soft, TENTATIVO 1, sanity NON CONFORME 1 gruppo (3
  FA sola: le G09 isolated sono SCOMPARSE), full-day 94,4%.
- ricorrenza ON +1,3pp vs OFF (70,5 vs 69,2).
- medium ON -1204375 vs OFF -1204901: praticamente pari.

## Lettura visiva (regola consumo-dataviz, forma testuale dichiarata)

I confronti HTML sono markup denso non estraiibile col grep (0 blocchi regolari): la
lettura visiva e' fatta dalla FONTE numerica dei grafici (kpi-fill per gruppo) +
differenziali: le forme per-gruppo sono SOVRAPPONIBILI fra i bracci (G01/G02 100%
entrambi, G03 88% entrambi; l'unica cella che si muove e' G04 58->59% ON) e il
delta concentrato e' la scomparsa dei rossi G09 nella sanity. Nessuna forma anomala
nuova nel braccio ON.

## Nota sul disegno

Braccio ON ha consegnato 0 al tentativo 1; OFF al tentativo 2 con -2: n=1 per braccio,
la differenza hard e' DENTRO la varianza di race documentata dal banco 628 (la race
decide la composizione del residuo). I criteri del gate pero' sono quelli
pre-registrati e sono tutti meccanicamente verificati PASS. La decisione di adozione
resta di maintainer (mandato: NESSUN flip).
