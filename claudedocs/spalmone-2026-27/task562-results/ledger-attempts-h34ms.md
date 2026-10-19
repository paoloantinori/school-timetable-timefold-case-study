# TASK-562 A5 LEDGER - firme attempt (primo popolamento, ciclo dci-h34ms 26/08)

## Schema (v1)
Per ogni tentativo (riga):
- attempt, seme (deterministico | GRASP-n | scartato-predittore)
- costruzione: (tier hard a fine costruzione, boxed a fine costruzione)
- post_bt1: (tier, boxed) dopo la prima LS timeslot
- exact_finisher: (tier, boxed) a fine catena repair
- esito finale: (score pieno, hard totale) + celle H34 residue (da autopsia A1)
- vincente: si/no (pick-best wrapper)

## Righe (ciclo dci-h34ms, 26/08 sera; misura in corso)

| attempt | seme | costruzione (tier, boxed) | post_bt1 | exact_finisher | autopsia A1 |
|---|---|---|---|---|---|
| 1 | deterministico | -2991, 1306 | -42, 6 | **-14, 0** | H34 12, H17 4, H0 0 |
| 2 | GRASP-2 | -2913, 1274 | -76, 3 | **-50, 0** | H34 10, H17 37, H0 0 |
| 3 | GRASP-3 | -2982, 1274 | -99, 9 | **-61, 0** | H34 10, H17 48, H0 0 |
| 4 | GRASP (in corso) | | | | |
| 5 | GRASP (in corso) | | | | |

## Prima lettura (n=3, non statistica)
- La firma di costruzione NON discrimina: i tre tentativi partono tutti da
  (tier ~-2900/-3000, boxed ~1274-1306): e' la LS a separare i bacini
  (-42 vs -76 vs -99 a post_bt1). Il predittore attuale (H17 a fine
  costruzione) non cattura questa differenza: la firma utile vive DOPO
  la prima LS, non prima (coerente con quanto gia' registrato: il profilo
  buono era indistinguibile a fine costruzione, iter-16/17-quater).
- H34 residuali: 10-12 celle in TUTTI i bacini (deterministico incluso):
  H34 e' bacino-invariante sul seed, coerente con la sua natura
  quota-dichiarata (le celle vuote sono le stesse date del foglio).
- H17 scala col bacino (4 vs 37 vs 48): e' il separatore principale.

## Aggiornamento post-consegna (26/08 23:5x): TUTTI e 5 i tentativi + vincitore

| attempt | seme | costruzione (tier, boxed) | post_bt1 | exact_finisher | esito |
|---|---|---|---|---|---|
| 1 | deterministico | -2991, 1306 | -42, 6 | **-14, 0** | VINCITORE (consegna -16hard pieno dopo stanze) |
| 2 | GRASP-2 | -2913, 1274 | -76, 3 | -50, 0 | condannato |
| 3 | GRASP-3 | -2982, 1274 | -99, 9 | -61, 0 | condannato |
| 4 | GRASP-4 | -3037, 1302 | -107, 21 | -62, 9 | condannato |
| 5 | GRASP-5 | -2928, 1271 | -112, 15 | -85, 7 | condannato |

- Conferma n=5: la firma di costruzione NON discrimina (range -2913..-3037,
  boxed 1271..1306); la prima LS separa monocitamente (-42 > -76 > -99 >
  -107 > -112) e l'ordine si conserva fino al EF. Il deterministico vince
  sempre in questa famiglia di seed: i 4 GRASP sono monotonicamente peggiori.
- H34 residue del vincitore: 12 (verificate dal raw export). Bacino-invarianza
  confermata su tutti i tentativi (10..12 ovunque).


## Estensione (27/08 01:2x, sesta tratta): +2 punti puri dal ciclo h34on

| punto | confine | tier | etichetta |
|---|---|---|---|
| h34on winner (single-attempt det.) | POST_B_ROOM | -14 | buono (consegna -14) |
| h34on winner | POST_RELEASE | -35 | buono |

Campione puro totale: 7 punti (3 buoni + 4 condannati): regola H17>15 OR
tier<-60 = 0 FP / 0 FN su 7/7. NOTA: gli ~300 phases.json storici NON sono
usabili (endScore pieno con room-tier di magnitudine variabile per era:
dimostrazione nel doc del predittore). Validazione vera prospettiva n>=20.
