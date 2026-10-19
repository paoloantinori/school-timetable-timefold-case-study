# VERDICT-raw: ciclo v11-n2 (profilo sh-s10, 4/09)

Cattura canonica eseguita dal main clone alla consegna (stagedStage scomparso, app viva).
Capture: EXIT 0. Doctor: EXIT 0 con WARN (vedi sotto). Dati grezzi, nessuna decisione (il laptop misura).

## Score di consegna

`score: [-13/0]hard/[-1312519/-17025]soft` (score.txt, identico alla riga di restore del wrapper).

Concordanza hard score.txt vs analysis.json: OK (-13). Medium: ValidationService riconta -1.298.841
(divergenza fra sorgenti nota, TASK-509).

Ciclo: lanciato da Fedora via ssh ~14:10 su profilo sh-s10; consegna 21:46:14
(trigger=solve-completed). Durata ~7h36m.

## Tentativi (multi-start)

- 5 tentativi, NESSUNO a 0-hard: "best attempt 1 shipped with CH-only rooms (no 0-hard attempt ran)",
  poi rifinitura stanze del vincitore e "restoring best attempt 1 (last run was #5)".
- 28 righe "attempt N done" totali (inclusi i reseeds veloci), di cui 23 "DISCARDED by the profile
  predictor" (H17 holes > 150).

## Righe SurplusHarvest (grep "SurplusHarvest (TASK" /tmp/planner-prod.log = 6)

Il pass e' ARMATO ed e' entrato 6 volte, TUTTE con moves=0 cells=0 (nessuna mossa legale al confine):

```
15:28:41  moves=0 cells=0 reverted=864  REJECTS: border=16064 h14=0 h7=1024  h11=0 teacherBusy=19
15:30:24  moves=0 cells=0 reverted=864  REJECTS: border=16064 h14=0 h7=1024  h11=0 teacherBusy=19
18:45:06  moves=0 cells=0 reverted=928  REJECTS: border=19208 h14=0 h7=2936  h11=0 teacherBusy=26
18:47:38  moves=0 cells=0 reverted=928  REJECTS: border=19208 h14=0 h7=2936  h11=0 teacherBusy=26
21:33:00  moves=0 cells=0 reverted=1016 REJECTS: border=21352 h14=0 h7=0     h11=0 teacherBusy=2178
21:35:50  moves=0 cells=0 reverted=1016 REJECTS: border=21352 h14=0 h7=0     h11=0 teacherBusy=2094
```

h14=0 su tutte le occasioni (lo scope pre-start non rifiuta piu' niente). Il gate dominante resta
border; teacherBusy cresce verso fine ciclo (19 -> 2178). Stesso esito del v11 (10 passate, tutte
moves=0): su questi bacini al confine non esiste mossa legale per il pass.

## assertValueRange

`grep -c "assertValueRange" /tmp/planner-prod.log` = **6 frame / 2 IllegalStateException**.
Entrambe NON fatali (il ciclo e' proseguito fino alla consegna; wrapper intatto): la famiglia
"quinta via OneOffFinisher" gia' osservata nel v11 (3 occorrenze li'), diagnosi in corso su quel
filone. Nell'intero ciclo nessuna fermata: zero morti del processo.

## Sanity

NON CONFORME, rossi su 4 gruppi (famiglie coda-anno note):
- G07: 1 giorno con buco interno (2027-06-09, slot 1-2)
- G09: 2 giorni sotto min golden (2027-02-22 e 2027-02-24, 1h)
- G10: 1 giorno sotto min golden (2027-03-24, 1h)
- G14: 1 giorno sotto min golden (2027-05-07, 2h)
- 0 lezioni senza stanza su tutti i 14 gruppi; buchi interni totali 1.

## KPI di contesto (dalla batteria capture)

- full-day 1787/1955 = 91,4% (golden ~91,9% ex-G14; run 30b 90,1%)
- ricorrenza TOTALE 67,9%; UNPINNED 8459/12234 = 69,1%; PINNED 34/274 = 12,4%
- day-start: nessuno scarto >=3 vs golden
- blocchi >=2h: 2759 (golden 1.299)

## Doctor e export

- DOCTOR EXIT 0 con 2 WARN: sanity-reds=4 (riportati sopra) e export sopra il cap GitHub.
- `export.json.gz` (105 MiB) resta SOLO sul laptop (path: questo direttorio), NON committato
  (limite 100MB GitHub, prassi della serie).
