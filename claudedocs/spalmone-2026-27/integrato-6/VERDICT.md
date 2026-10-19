# VERDICT ciclo integrato-6 (20/21-09, corsa di chiusura)

Setup: profilo fresco, java -jar diretto, heap 2g, caffeinate, h7swap ARMATO in misura
+ hrf.debug + cattura checkpoint ON (relaunch 21:41: il primo lancio 18:52 era senza
cattura, killato; del suo tentativo-1 condannato -17 post-RELEASE / CH-only -7478 resta
solo l'attestazione Fedora, log troncato dal banner del rilancio: first-launch-doomed-attempt.log).

## NEAR MISS: consegna [-1/0]hard, REGOLA NUOLO APPLICATA

Vincitore: **attempt 1 deterministico**, score [-1/0]hard/[-1182708/1527]soft.
Cattura IMMEDIATA dello stato consegnato in nearmiss/ (export.json.gz 111MB locale
NON committato + lessons.txt 12551 righe committato) per il blind solve parallelo.

Il -1: famiglia H34 (1 match) secondo l'analysis; il drill-down live dice 0 violazioni
(divergenza analysis-vs-drill-down da inchiodare: mia query SQL trova candidati solo
fuori scope H34). La cella esatta va identificata dal blind o da un riconteggio calmo.

## Serie e predizioni

[-4,-1,0,0,-1(i5),-1]: la predizione 1 (serie 0 oppure near-miss -1..-4) CENTRATA sul
near-miss; P>0.83 non falsificata (un solo run). La race ha ridistribuito come atteso:
tentativo 1 vincitore, tentativo 2 GRASP completato + 3 scarti predictor (175/201/174
holes > 150).

## Verbatim del delivery stage

```
EmptyDayFinisher [delivery]: 0 moves, 0 cells
HoleRelayFinisher [delivery] (TASK-613 v0.6 teacher-aware): targets=7, slides=7, rotations=0, moves=14
staged solve multi-start: restoring best attempt 1 (last run was #1) - score=[-1/0]hard
```

Predizione 2 CONFERMATA: [delivery] presente e ATTIVA (l'HRF ha chiuso 7 buchi con 14
mosse sullo stato consegnato: il -14 pre-trio e' diventato -1: la terna vale 13 punti
proprio all'ultimo chilometro, coerente col verdetto 628).
Predizione 3, ramo assicurazione: census HRF-H7SWAP ASSENTE (nessun overcap al delivery:
h7swap resta prova-di-valore pendente su base con overcap donor-less).

## Sanity: NON CONFORME (2 gruppi, famiglie isolate)

G09: 3 giorni da 1h (23/02, 24/02, 14/04) sotto min golden 2h. G10: 1 giorno da
1h (11/03) sotto min 4h. Stessa classe dei rossi di integrato-5 (ore isolate), nessun
buco interno (0), no lezioni senza stanza, full-day 94,6%.

## Catalogo TASK-634 (addendo eseguito)

catalog-634.json + catalog/attempt{1,2} (21 confini ciascuno, checkpoint JSON
room-faithful con roomId: per-attempt payload = checkpoint-pieno, NON lessons-only,
dichiarato), seed-hashes.txt (hash pre-consegna), commit 7aefd916. Voce index:
integrato-6-winner VALID (roomFaithful=True). Primo lancio registrato come nota.
