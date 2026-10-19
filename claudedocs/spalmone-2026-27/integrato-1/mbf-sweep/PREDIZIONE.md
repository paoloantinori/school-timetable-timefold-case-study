# SWEEP PESO M-BF predizione (scritta PRIMA)

## Setup
4 cicli staged (max-attempts=1, deterministico) su profili freschi t626-mbf{W}:
W=20 (default/baseline, RATIFIED), W=40, W=80, W=160.
Knob: -Dplanner.constraints.mbf.weight={W} su java -jar.

## Criterio pre-registrato (dal mandato)
-15% interleaved vs W=20 senza regressioni (hard, full-day, ricorrenza) = W superiore vince.
Altrimenti W=20 e' dichiarato ottimale.

## Predizione
M-BF penalizza i buchi interni dei blocchi lab per (gruppo, giorno). Il peso controlla
quanto la LS e' disposta a pagare in altri medium per chiudere quei buchi.
- W=40: leggero miglioramento interleaved (-5/-10%), hard invariato
- W=80: miglioramento piu' marcato (-10/-20%), MA rischio di regressioni su M15/M32
  (la LS sposta lezioni per chiudere buchi M-BF a scapito di contiguita' M15)
- W=160: forte pressione, alta probabilita' di regressioni hard (la LS sacrifica
  la qualita' complessiva per chiudere i buchi M-BF)
- ATTENDO: W=40 come migliore candidato, W=80 borderline, W=160 con regressioni.
  Il criterio -15% e' ambizioso: M-BF al default W=20 produce gia' ~-40k medium,
  il salto a 40/80 potrebbe non essere sufficiente per -15% interleaved.
