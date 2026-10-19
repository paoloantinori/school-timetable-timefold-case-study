# BASELINE TASK-615: difetti NATI alla costruzione (dump WeeklyTemplateConstructionRealSeedTest, deterministico = l'inizio del ciclo m39-off)

Stato di FINE COSTRUZIONE, prima di ogni repair. H17 = gap interni per gruppo-giorno
sullo span occupato (tutte le lezioni, incluse Stage). H34 = giorno dichiarato
aula+lab>0 con zero lezioni non-Stage, SCOPE H32 (1/G08 e G14 esclusi: il loro
target e' la forma-b pool-cap, non il foglio). H32 = ore non-Stage sotto
quota-tolleranza (G09 1h), stessi gruppi in scope.

| gruppo | gg con H17 gap | slot H17 | gg vuoti H34 | gg sotto-quota H32 | ore H32 mancanti |
|---|---|---|---|---|---|
| G01 | 0 | 0 | 0 | 0 | 0 |
| G02 | 0 | 0 | 1 | 1 | 6 |
| G03 | 0 | 0 | 0 | 1 | 1 |
| G04 | 0 | 0 | 0 | 0 | 0 |
| G05 | 2 | 2 | 0 | 9 | 21 |
| G06 | 3 | 3 | 4 | 14 | 44 |
| G07 | 3 | 5 | 0 | 2 | 5 |
| G08 | 1 | 1 | 0 | 0 | 0 |
| G09 | 3 | 10 | 3 | 6 | 16 |
| G10 | 4 | 9 | 0 | 2 | 7 |
| G11 | 6 | 7 | 2 | 7 | 23 |
| G12 | 3 | 6 | 13 | 19 | 74 |
| G13 | 29 | 35 | 1 | 5 | 20 |
| G14 | 2 | 2 | 0 | 0 | 0 |
| **TOTALE (scope)** | **56** | **80** | **24** | **66** | **217** |

Note: G13 domina H17 (29 gg / 35 slot: la famiglia residua storica); G12
domina H32 (19 gg / 74h) e H34 (13 gg: i giorni-L del foglio rimasti vuoti);
i 14+2 giorni 'vuoti' di 1/G08 sono fuori scope (pool-cap). Questa e' la
baseline contro cui giudicare le ipotesi TASK-615 (slack-strutturale,
contiguita'-first, donor-aware): tutto cio' che la catena di repair chiude
oggi parte da qui.

## SPIKE contiguita-first (FALSIFICATO, 15/09 mattina, flag-gated test-side)
- OFF: 80 gap (byte-identico alla baseline); ON: 274 (+194, 3,4x). G10 9->79, 2-G11 esplosi.
- MECCANISMO: nextContiguousSlot e' GIA' contiguo (first-fit bottom-up); il tail-first fa atterrare
  chunk oltre i pin lasciando slot bassi vuoti.
- LA VIA PROMETTENTE (prossimo spike): scan-forward-within-day - quando il placement colpisce
  un conflitto docente, provare lo slot successivo NELLO stesso giorno invece di abbandonarlo.
  Il difetto strutturale e' che placeChunkOnce valuta UN solo slot per weekday.
