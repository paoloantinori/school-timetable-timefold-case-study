# VERDICT-raw: ciclo v13.1 su input curato (profilo v132-7sep, 6/09 pomeriggio)

Ciclo di verifica del PASSO 1 (TASK-586 cura input): jar 12:43:33 su HEAD
89e505d1 (le 6 righe quota fantasma 14-15/06 rimosse da entrambe le copie CSV),
`PREFLIGHT-OK classes=[SurplusHarvest] flags-verified` con checkpoint
staged-checkpoints-v132 e SURPLUS_TRACE_ALL=1 verificati nel processo, banner
surplus-harvest=true, costruzione deterministica.

## CONFRONTABILITA' (dichiarata prima dei risultati)

L'input e' quello di v123/v131 MINUS le 6 righe fantasma (G09 14+15/06,
G10 15/06, G11 15/06, G13 14+15/06): righe oltre l'orizzonte timeslot
dei non-FC, mai riempibili, che il v12.3 enumerava come bersagli fantasma.
Con la cura H26 le esclude alla radice: il pass non deve piu' vederle.
Confronto principale: v123/v131 ([-8/0], pass 1 occasione su 4-5 con
moves 32-40) SULLA PARTE COMUNE dell'input; le differenze di mappa attese
solo sulle date curate.

## DOMANDE (pre-registrate al lancio 12:44)

1. Le celle fantasma 14-15/06 (G09, G10, G11, G13) scompaiono
   dall'enumerazione del pass? (grep SH-trace TARGET per quelle date: attesa ZERO)
2. Le 4 celle H34 residue reali (G06 10/06, G09 03/06, G11 04/06,
   G12 14/05) vengono chiuse dal pass col nuovo input?
3. Consegna hard vs -8, avr=0, sanity, concordanza (11a).
4. REJECTS: il profilo post-cura (senza i fantasma) come cambia vs v123
   (slideH1 280-6872, teacherBusy 12-272)?

## RISPOSTE (consegna 19:37; ciclo 12:44-19:37, ~7h)

1. **Fantasma spariti: SI', ZERO.** Nessun TARGET 14-15/06 nel tracer (G09,
   G10, G11, G13): la cura TASK-586 li ha tolti alla radice (H26).
2. **Le 4 celle reali NON compaiono come TARGET alle occasioni di questo
   ciclo** (i bersagli enumerati erano altre celle: la mappa per-cell del
   confine varia per tentativo). Una occasione ha chiuso: moves=4 cells=1.
   L'analisi per-cell delle 4 attese va fatta sul dump (le date dei rossi
   sanity non coincidono con le 4: 27/05, 09/06, 24/02, 16/03, 09/04,
   04/03, 12/03, 30/04).
3. **Consegna [-6/0]hard/[-1261185/-17488]soft: LA MIGLIORE DELLA SERIE**
   (v123 -8, v131 -8, notte-4sep -8). avr=0, ISE=0 su 7h. Concordanza OK
   memory=1 db=1 (11a consecutiva). Sanity NON CONFORME 4 rossi + 1 lezione
   senza stanza (nuovo: da mappare). Full-day 92,1%.
4. **REJECTS profile (5 occasioni)**: border 168-632 (invariato in ordine),
   slideH1 512-1968 (il collo confermato), teacherBusy 24-560, chainNearMiss
   8-280 con chainFail 0-104 (la catena v13.1 scopre molti near-miss nel
   vincitore: 280 con 104 fail all'ultima occasione). oracleRevert=0 ovunque.

## Score / righe SH complete / sanity / concordanza

score.txt: `score: [-6/0]hard/[-1261185/-17488]soft`. Le 10 righe SH sono nel
log archiviato /tmp/planner-v132-7sep-final.log (rotazione applicata). Doom
rows: 27 righe v132-7sep nel registro (122 totali).
