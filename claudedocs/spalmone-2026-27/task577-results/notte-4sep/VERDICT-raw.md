# VERDICT-raw: ciclo notte-4sep (profilo notte-4sep, 5/09)

Ciclo lanciato 01:06 (jar con quinta-via OOF c54038ba + bug-istanza bbeeb0c5,
preflight `PREFLIGHT-OK classes=[SurplusHarvest,OneOffFinisher] flags-verified`,
banner `surplus-harvest=true` verificato), consegna 08:51 (restore del vincitore),
durata 7h45m. Capture canonica eseguita alle 09:11-09:13; doctor EXIT 0 con 2 WARN
(sanity-reds=3, export 105MiB oltre il cap GitHub: resta sul laptop, non committato).
Dati grezzi, nessuna decisione (il laptop misura).

## LA DOMANDA del mandato: le eccezioni OOF spariscono?

**SI'. `grep -c assertValueRange /tmp/planner-prod.log` = 0; `grep -c
IllegalStateException` = 0.** Zero eccezioni in 7h45m di ciclo completo (5 tentativi
+ 20 reseed del predictor + rifinitura stanze del vincitore). Precedenti alla stessa
durata: 2/09 = 2 crash, v11 = 3, v11-n2 = 2. La guardia per-cell sui follower di
extractAndCompact (quinta via) chiude la famiglia osservata.

## Score di consegna

`score: [-8/0]hard/[-1257347/-16604]soft` (score.txt = riga di restore). hard
concorde con analysis.json; medium ValidationService -798k (divergenza sorgenti
nota, TASK-509). **-8 e' il migliore della serie** (v10 -16, v11 -13, v11-n2 -13)
ed e' la PRIMA consegna con la catena post-B-ROOM viva sul grezzo consegnato
(fix istanza): EDF/SH ora persistono.

## Tentativi (multi-start)

25 righe "attempt done" (incluse le reseeds veloci), 4 verdetti DOOMED al confine
post-RELEASE, 20 discard del profile predictor, NESSUN tentativo a 0-hard: "best
attempt 1 shipped with CH-only rooms", rifinitura stanze (08:41-08:51), restore
alle 08:51.

## Righe SurplusHarvest (grep "SurplusHarvest (TASK" = 10, coppie per occasione)

Tutte `moves=0 cells=0`. REJECTS per occasione (prima e seconda della coppia):

```
02:32/02:33  reverted=736  border=13008 teacherBusy=20408 shape=23168  h31=3368  roomBusy=272
04:16/04:19  reverted=656  border=25872 teacherBusy=28120 shape=11280  h31=3672  roomBusy=240
05:42/05:44  reverted=208  border=13360 teacherBusy=18864 shape=13624  h31=3600  roomBusy=560
07:29/07:31  reverted=552  border=20568 teacherBusy=22704 shape=14664  h31=3112  roomBusy=312
08:37/08:40  reverted=0    border=22720 teacherBusy=27456 shape=24040  h31=4832  roomBusy=1120
```

h14=0 su tutte le occasioni. Il NUOVO contatore roomBusy (guardia reject-on-busy
della voce-2) lavora: 240-1120 rifiuti per occasione, cioe' mosse che ieri
avrebbero spedito H29 e oggi non partono nemmeno. teacherBusy resta il collo
dominante (18-28k), come da registro.

## Sanity

NON CONFORME, rossi su 3 gruppi (famiglie coda-anno note; migliora da 4 del v11-n2):
- G07: 3 giorni sotto min golden 4h (05/05, 28/05, 09/06)
- G09: 2 giorni sotto min 2h (22/02, 24/02)
- G14: 1 giorno sotto min 3h (12/02, 2h)
Zero lezioni senza stanza, zero buchi interni.

## KPI di contesto

- full-day 1804/1955 = 92,3% (golden ~91,9% ex-G14: SOPRA il golden)
- ricorrenza TOTALE 68,3%; UNPINNED 8516/12236 = 69,6%
- day-start: scarti >=3 vs golden: none (dalla capture)

## Nota di processo

La capture e' stata eseguita da Fedora (sveglia) alle 09:11 nella dir di destinazione
mentre l'heartbeat di zai verificava la consegna; VERDICT, righe TASK-581 e commit
sono di zai sopra quella capture (nessuna duplicazione: dir stabile verificata prima
di scrivere).
