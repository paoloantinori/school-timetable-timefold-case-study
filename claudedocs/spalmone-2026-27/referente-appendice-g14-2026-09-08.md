# Appendice dati per Q-G14-VENERDI (rev 16 + aggiornamento 7/09 sera)

Numeri esatti per chiudere la domanda in un giro. Tutti verificabili dai fogli
del workbook e dal golden (lessons.csv).

## Il fatto

Il foglio G14 dichiara 120h totali ma i docenti offrono 115h
(T06 20, T13 20, T14 15, T26 30, T16 30:
la riga 'Totale ore corso' dice 120, la somma delle righe docente fa 115).
Le 5h mancanti coincidono con la riga di sabato 16/01/2027 (5h, aula): quel
sabato il golden della scuola mette 5h di Orientamento (T14). La
griglia lun-ven non ha sabato: quelle 5h non sono collocabili in griglia.

## Il blocco (riprodotto in due bacini diversi, 7/09)

Due venerdi' qualsiasi di G14 restano a 2h su 3 dichiarati, e le 2 ore di
Confezione di T16 restano non collocate (28 su 30 piazzate). Il motivo e'
un triangolo sugli slot delle 10:00:

- i venerdi' G14 aprono 3 slot (8, 9, 10): la terza ora puo' stare SOLO alle 10;
- alle 10 dei venerdi' T16 fa le sue ore di Confezione di G05 (dai
  nostri run: 12/03, 30/04, 19/03, 9/04: tutti i venerdi' campionati);
- l'unico giorno G14 con ore in surplus e' il giovedi' 13/05 (5h piazzate
  su 3 dichiarate), ma le ore in surplus sono di T16, che alle 10 del
  giovedi' fa G01: non possono nemmeno essere spostate dentro il giorno.

Cioe': le uniche ore G14 disponibili a fine anno sono di T16, e T16
alle 10 (l'unico slot dove servono) e' sempre occupato da G05.

## Le tre risposte possibili (una basta)

1. Le 2 ore di Confezione G14 in piu' (30 offerte vs 28 collocabili) si
   dichiarano non collocabili e si tolgono dal monte (come gia' fatto per
   l'eccesso G09).
2. In quei due venerdi' (quali che siano: capita un paio all'anno) la scuola
   sposta un'ora di Confezione di G05 altrove: se e' pratica possibile, le
   2 ore G14 entrano.
3. I venerdi' in questione sono da 2h, non 3: si corregge il foglio.

## Cosa sblocchereste rispondendo

Le celle sotto-pavimento (H33) dei venerdi' G14 e le 2 ore non collocate
(H0): insieme valgono -6 hard sulla consegna. Nessun algoritmo puo' fare
nulla finche' il triangolo sta negli input: e' l'unica famiglia residua del
-6/-8 che non ha cura search-side.

## VERIFICHE DAL GOLDEN (8/09 sera, aggiunte alle domande)

### 1. Il triangolo T16 e' SISTEMATICO (non specifico di due date)

Il golden mette T16 alle 10:00 del venerdi' con G01 in **33 dei 37 venerdi'**
dell'anno scolastico (89%). E **non mette MAI T16 su G14 di venerdì**
(0 ore in tutto l'anno). La scuola non risolve il triangolo: lo subisce da sempre.

### 2. I 35 giorni vuoti di G12 sono SUBITI (il golden li riempie tutti)

Tutti i 134 giorni con quota dichiarata di G12 sono pieni nel golden
(esattamente a quota: 134/134, zero vuoti). Il foglio chiede e la scuola fa.
I nostri 35 vuoti sono un limite del solver, non una scelta della scuola.

### 3. L'unica divergenza sistemática foglio-vs-golden e' G13

Confronto per-data su tutti i gruppi (tolleranza 1h, esclusi pool-cap e G14):
**solo G13 diverge**, in 134 date, tutte nello stesso verso: il foglio chiede
5-6h/giorno in piu' di quanto il golden pratica (es. foglio 10-12h, golden 5-6h).
Gli altri 12 gruppi: ZERO divergenze >1h. O il foglio G13 sovradichiara,
oppure il golden lo sottoutilizza: e' una domanda da aggiungere al pacchetto.
