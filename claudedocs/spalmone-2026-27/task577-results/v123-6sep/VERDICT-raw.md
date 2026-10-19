# VERDICT-raw: ciclo v12.3 completo (profilo v123-6sep, 6/09)

Ciclo col pacchetto 53f73b3c (v12.1 popolazione giusta + v12.2 donatori non
affamati + v12.3 base quota giusta), config identica a notte-4sep (default tutto,
solo surplus-harvest armato, costruzione deterministica), profilo fresco, env
SURPLUS_TRACE_ALL=1 (tracer dei bersagli e rifiuti per-cella attivo: da
verificare nel log, altrimenti limite dichiarato qui). Questo VERDICT e' stato
pre-registrato PRIMA del lancio: le risposte sotto le domande arrivano alla
consegna. Baseline di confronto: notte-4sep (v11-p2, -8 hard, SH moves=0 su 10
occasioni) e il primo sparo v12-pura delle 09:42 (moves=198 cells=38 reverted=7,
slideH1=9712, teacherBusy=1229).

## DOMANDE PRE-REGISTRATE (le 5 del GO-V12 + 3 del pre-mortem)

a. **moves>0 in almeno un'occasione?** (baseline 0/10). Gia' SI' meccanicamente
   nel v12-puro (198); qui conta la stabilita' del dato sul pacchetto completo.

b. **oracleRevert collassa?** da 208-736 (notte-4sep) verso ~0-50. Nel v12-puro:
   reverted=7. Attesa conferma.

c. **Quanto vale slideH1?** Nel v12-puro: 9712 (il collo era REALE e la guardia
   lo sposta in ricerca). Attesa: stesso ordine o minore (v12.1-12.3 riducono i
   rifiuti a monte).

d. **Consegna**: hard vs -8 baseline (nota: il batch 584 ha mostrato che senza
   SH i bacini consegnano -65/-167: se il v12.3 chiude le celle H34 l'hard
   scende), zero assertValueRange/ISE, sanity, riga delivery concordance.

e. **CELLE**: attese CHIUSE le pass-curable G06 09+10/06 e G12 07/05;
   NON attese G09 03/06 e G11 04/06 (deficit input TASK-586). Il v12-puro
   mostrava cells=38 allo sparo del confine: molte piu' del previsto (v12.1
   popolazione giusta): la sesta domanda di Fedora (quante in-scope nel
   CONSEGNATO) e' la misura che conta.

f. **[pre-mortem] moves tocca il cap 200?** Il v12-puro si e' fermato a 198
   (=cap 200 vicino). Se il v12.3 satura il cap, il collo e' di budget, non di
   mosse legali: registro e la domanda alla curatrice.

g. **[pre-mortem] wall per sparo**: differenza dei timestamp delle righe
   SurplusHarvest. Allerta se >15 min (il banco dice fino a ~9 min/sparo).

h. **[pre-mortem] dal tracer (SURPLUS_TRACE_ALL)**: le celle in-scope ESISTONO
   come bersagli al momento dello sparo? (l'ipotesi v11 era che le celle del
   consegnato non esistano al confine: il tracer risponde per-cella).

## RISPOSTE (alla consegna, 19:42; ciclo 11:38-19:42, ~8h)

a. **moves>0? SI', in 1 occasione su 4** (terza: `moves=32 cells=8 reverted=0`
   alle 16:55). Le altre tre occasioni moves=0. La v12 pura interrotta faceva
   198 mosse al primo sparo: il pacchetto 12.1-12.3 ha RIDOTTO la portata
   cambiando popolazione/base quota (v12.1 "popolazione giusta").

b. **oracleRevert: COLlassO TOTALE** = 0 su TUTTE le occasioni (baseline
   notte-4sep 208-736). La guardia slideH1 ha spostato in ricerca tutto cio'
   che l'oracolo rimediava dopo.

c. **slideH1 per occasione**: 584, 280, 408, 6872. Il collo resta variabile:
   domina le occasioni a mosse=0; nell'occasione che muove (280) e' comunque
   il rifiuto principale ma lascia passare 32 mosse.

d. **Consegna: `[-8/0]hard/[-1214925/-10987]soft`** = hard IDENTICO al
   baseline notte-4sep (-8). ZERO assertValueRange, ZERO
   IllegalStateException (avr=0 su 8h). Concordanza:
   `delivery concordance OK (H29 room-null memory=0 db=0)` (9a validazione
   live consecutiva del metro, 9/9 dalla sua nascita). Sanity NON CONFORME
   5 rossi (G07 x2, G09, G10, G14; date sparse coda-anno: 25/05,
   22/02+24/02+20/04, 22/04, 12/03, 29/01+02/04). Full-day 92,1%.

e. **CELLE pass-curable**: le G06 09+10/06 e le G12 07/05 NON risultano
   nei rossi sanity (i rossi sono altre date): l'unica occasione che ha mosso
   ha chiuso 8 CELLE (mappa esatta cella-per-cella da fare sul dump; le
   date dei rossi residui non coincidono con le 6 attese). G09/G11
   attese NON chiuse (deficit input TASK-586).

f. **moves NON tocca il cap 200**: max 32. Il collo non e' di budget.

g. **wall per sparo**: le coppie di righe per occasione distano 5-14 secondi
   fra loro (due passate della stessa occasione); le OCCASIONI distano
   106-156 min l'una dall'altra (sono i confine dei tentativi). NESSUNO
   sparo oltre 15 min: il costo del pass resta trascurabile nel ciclo
   (i secondi delle righe, non i minuti del banco).

h. **TRACER: i bersagli ESISTONO allo sparo** (248 righe SH-trace nel log;
   es. `TARGET G12 2027-05-14 quota=4` ripetuto): l'ipotesi v11 "le celle
   non esistono al confine" e' CONFUTATA per il v12.3 con SURPLUS_TRACE_ALL:
   le celle in-scope ci sono, il collo e' nei rifiuti (slideH1/border), non
   nell'assenza di bersagli.

## DATI GREZZI (le 8 righe SH complete)

```
13:34:55 + 13:35:01  moves=0  cells=0 reverted=0  border=264  slideH1=584  teacherBusy=192  noDonors=0
15:22:29 + 15:22:34  moves=0  cells=0 reverted=0  border=104  slideH1=280  teacherBusy=12   noDonors=8
16:55:48 + 16:55:54  moves=32 cells=8 reverted=0  border=104  slideH1=280  teacherBusy=12   noDonors=8   <-- L'OCCASIONE CHE MUOVE
19:31:40 + 19:31:54  moves=0  cells=0 reverted=0  border=2168 slideH1=6872 teacherBusy=1360 noDonors=0
```
(Le coppie sono le due passate per occasione; la quarta occasione e' il
tentativo vincitore: rifiuti alti, la LS del vincitore aveva gia' consumato
i donatori facili.)

## CHECKPOINT (dataset per il banco di domani)

staged-checkpoints-v123/attempt1..N: griglie serializzate ai confine,
incluso il PRE-FINALE post-B-ROOM dove il pass spara. E' il dataset che il
banco di maintainer carichera' domani per misurare i candidati-correzione in
minuti. Dimensioni trascurabili.

## Score / sanity / concordanza

score.txt: `score: [-8/0]hard/[-1214925/-10987]soft`.
Sanity: NON CONFORME 5 gruppi (dettaglio sopra; peggio del -8 di notte-4sep
che ne aveva 3: le mosse del pass hanno spostato rosso su date diverse,
da mappare cella-per-cella sul dump). Concordanza: OK memory=0 db=0.
