# MEMO per maintainer, mattina del 13/09: due decisioni pendenti su TASK-596 (gate adozione m-14)

Scritto di notte durante il batch TASK-584 (laptop, seeds 101-108 in volo). Contesto: il banco M39
e' chiuso con bocciatura ratificata; il braccio A e' il primo vettore pulito del quadro ratificato
([-11/0], RA 41/41 verificata end-to-end); il residuo -11 e' interamente classificato in TASK-608
e il task figlio TASK-609 (riparazioni composite) e' pronto al via.

## Decisione 1 (originaria, F2 di TASK-596): quale protocollo e' canonico per la scuola?

Il finding F2 di TASK-539.01 resta aperto: tutta la serie misurata v11..v132 e' girata SENZA
import-calendario (avvio da 274 pin del pre-assign, sha 55ed4ae0a7a9ed5a), mentre il flusso
documentato (skills laptop-test/full-test) include import-calendario (3077 pin, incl. 2009 stage pin
e 295 MODA lab pin). Sono due problemi di ricerca diversi: la decisione dice quale dei due il
ciclo di adozione deve misurare. I due volumi fixture esistono gia' sul laptop (m14-precon-b =
misurato, m14-precon = documentato).

RACCOMANDAZIONE (da confermare): import-calendario SI', il flusso documentato e' quello che la
scuola usera' (i pin di stage e i lab MODA sono input autoritativi della pratica reale); la serie
v e' comunque valida come confronto RELATIVO fra meccanismi. Nessuna urgenza: la decisione scatta
solo al lancio del ciclo di adozione.

## Decisione 2 (NUOVA, emersa stanotte): 596 prima o dopo TASK-609?

TASK-596 e' il gate di adozione del candidato ZERO-hard. Oggi il migliore delivery pulito e' -11,
non zero. Il residuo -11 e' ora interamente riparabile IN TEORIA (TASK-608 Fase C: catena
composita H0 validata, 3 fill H34 completi enumerati col gate E vero, H32/H17 da enumerare), e
TASK-609 esiste per meccanizzarle dietro flag con banco replay.

Le due strade:
(a) 596 ORA sul -11: consegna certificata del migliore attuale, m-14 resta aperto sullo zero;
(b) 609 PRIMA, poi 596 sul candidato (potenzialmente 0-hard): una sola spesa di ciclo completo,
allineata alla direttiva ITERAZIONE SU SNAPSHOT (il ciclo e' SOLO il gate finale).

RACCOMANDAZIONE: (b). Il banco replay di 609 costa ore locali e dice se le riparazioni reggono
sull'evaluator vero; se chiudono, il ciclo di adozione misura il candidato giusto una volta sola.
Se invece il batch TASK-584 di stanotte rivelasse un quadro di affidabilita' critico (p(0-hard)
crollata), la priorita' diventa la qualifica del protocollo stesso e 609 resta il passo comunque
giusto (chiude residui in qualunque quadro).

## Cosa c'e' sul tavolo al risveglio

- Batch 584: curva p-hat su N~8 (i primi due seed: -33, -34;nessuno vicino allo zero finora).
- TASK-608: verdetto Fase C (classificazione completa del residuo) + task figlio 609 pronto.
- Le due decisioni qui sopra: la 1 rinvia al ciclo di adozione, la 2 detta la sequenza.
