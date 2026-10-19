# E1bis (riproduzione, config identica a E1): numeri puri, n=2

## CONSEGNA (01:05 -> 02:14, wall 1h09m, 1 tentativo, 0 scarti)
[-11/0]hard / [-814.877 medium]. Decomposizione: H34 -6, H17 -3, H29 -1, H32 -1.
12510/12510 assegnate. full-day 1776/1955 = 90,8%. BUCO-INTERNO 3gg/3slot. Sanity 5 rossi.

## RIPRODUZIONE n=2 (E1 vs E1bis)
                    E1              E1bis
consegna            [-10/0]         [-11/0]
H17 residuo         0 (0gg/4slot*)  -3 (3gg/3slot)
buchi interni       1gg/4slot       3gg/3slot
full-day            91,4%           90,8%
sanity rossi        5               5
wall                1h45m           1h09m
golden-cell match   30,5% (403/1322) 28,9% (382/1322)
(*E1 non aveva H17 nel drill-down ma 4 slot di buco topologico)

## DOMANDE DEL MANDATO
1. La consegna si riproduce? QUASI: -10 vs -11, stessa fascia near-zero, stessa
   composizione a 1-2 unita' di distanza per famiglia (H34 5 vs 6, H32 1 vs 1;
   E1 aveva H0/H33, bis ha H17/H29: famiglie diverse, magnitudini 1-3).
2. L'erosione golden-cell si riproduce? SI': 30,5% vs 28,9% (delta 1,6pp): il
   template pair parte a ~72,7% di match a costruzione (banco) e le fasi LS lo
   erodono a ~29-31% consegnato in ENTRAMBI i run.
## VERDETTO CORE CONFERMATO n=2
La costruzione pair parte con ~200 buchi H17 e la consegna ne ha 3-4 slot in entrambi
i run: il predictor resta un falso-condannato per il builder pair (il ramo
pre-registrato di E1 si riproduce).
