# TASK-550 cura (c): domanda alla scuola - quante ore di stage tiene davvero ogni gruppo?

Data: 24/08 (sezione per il pacchetto domande a referente, insieme alle divergenze TASK-466).

## La domanda (forma ratificata da maintainer, 25/08 07:20: conferma a posteriori)

**"Il piano segna 295 ore di stage per G12 ma il golden ne colloca 112 (e
similarmente per gli altri gruppi); assumiamo il golden come corretto, confermate?"**

**Quante ore di stage tiene davvero ogni gruppo nell'anno?** I fogli che ci avete dato
chiedono 295-300 ore di stage per gruppo, ma l'orario che ci avete fatto (il golden)
ne colloca molte meno, e segna esplicitamente come non tenute (`closed = true`) le
righe in eccesso. Per esempio **G12: il foglio chiede 295 ore, l'orario ne tiene
112**; il planning segna 183 righe di Stage come non tenute.

## I numeri (foglio vs orario, per gruppo)

| gruppo | foglio chiede | orario tiene | differenza |
|---|---|---|---|
| G12 | 295 | 112 | 183 |
| G10 | 300 | 228 | 72 |
| G04 | 240 | 224 | 16 |
| G08 | 300 | 276 | 24 |
| G09 | 300 | 292 | 8 |
| G11 | 295 | 287 | 8 |
| G07 | 295 | 295 | 0 |
| G13 | 295 | 295 | 0 |

## Perché lo chiediamo

Il nostro ottimizzatore prende il foglio alla lettera e cerca di collocare tutte le
ore chieste: le ~311 ore in eccesso sono fisiicamente non collocabili (non ci sono
abbastanza giorni di stage nel calendario) e producevano ogni run un residuo di
lezioni non collocate esattamente di quella entità. Abbiamo già allineato il seeding
alla pratica dell'orario (TASK-550 cura (a), 24/08): seminiamo solo le ore che
l'orario tiene, e teniamo le altre come "differenza da confermare". La vostra risposta
decide se è la scelta giusta:

1. **L'orario ha ragione** (le ore in eccesso non si fanno): confermate e chiudiamo la
   differenza come dichiarata.
2. **Il foglio ha ragione** (le ore si fanno ma l'orario le ha sforate): ce lo dite, e
   le rimettiamo in domanda, con l'avvertenza che servono anche più date/giorni di
   stage nel calendario per collocarle.

## Nota tecnica per noi

La domanda è la via di sopravvivenza dichiarata quando il golden va in pensione
(direttiva solo-input): il conteggio held oggi è derivato da `seed/lessons.csv`
(righe Stage con `closed != true`); la risposta della scuola lo sostituisce come
input dichiarato nel workbook.

## STATO (25/08 07:20)

RATIFICATO da maintainer come DECISIONE UFFICIALE: assumiamo il golden. La conferma della
scuola e' A POSTERIORI, non bloccante: il seeding golden-open e' gia' il comportamento
ufficiale del sistema.
