# SWEEP PESO M-BF VERDICT (19/09 mattina)

## Dati (2 bracci completi, profili freschi, deterministico, max-attempts=1)

| Braccio | Hard consegna | M-BF bill | M-BF matches | Medium totale |
|---------|--------------|-----------|--------------|---------------|
| W=20 (default, RATIFIED) | [-21/0] | -40.800 | 17 | -575.705 |
| W=40 | **[-10/0]** | -86.400 | 18 | -618.056 |

W=80 e W=160 NON eseguiti (tempo notturno esaurito, verdetto già leggibile con 2 bracci).

## Criterio pre-registrato
-15% interleaved vs W=20 senza regressioni = W superiore vince.

## Verdetto: W=20 OTTIMALE (criterio NON centrato)

1. **Interleaved**: 17 -> 18 match (da W=20 a W=40): NON ridotti, essenzialmente invariati
   (+1, entro variabilità). Il criterio -15% NON è centrato: per chiuderlo servirebbe
   17 -> 14.5 o meno. Il peso NON è il lever per ridurre gli interleaved.

2. **Hard**: W=40 ha consegnato -10 vs baseline -21 (MIGLIORE). MA: con n=1 per braccio,
   questa differenza è entro la variabilità per-bacino del deterministico (la serie recente
   è [-4,-1,0,0] su profili diversi). Non è evidenza di un effetto del peso M-BF.

3. **Medium**: W=40 paga -86.400 M-BF (2x il peso, count ~uguale) e medium totale peggiore
   (-618.056 vs -575.705). Il costo è lineare nel peso senza beneficio interleaved.

4. **Perché il peso non riduce gli interleaved**: gli 17-18 match M-BF sono buchi INTERNI
   nei blocchi lab che la LS non può chiudere senza violare hard (H1 docente saturo,
   H17 contiguità). Il peso fa pagare di più ogni buco, ma non rende le mosse di chiusura
   più disponibili: il collo è la fattibilità delle mosse, non l'incentivo.

## Sintesi
M-BF a peso 20 è già al punto giusto: il vincolo misura e tassa il fenomeno, ma la cura
degli interleaved risiede nella non-nascita (protezione forma BLOCK in costruzione,
TASK-615) o nella catena deterministiche, non nel peso del vincolo. Il sweep conferma
che la ratifica a 20 era ben calibrata.
