# VERDICT TASK-621 prototipo H7-parallel-relay: con i gate onesti NON chiude su questo stato

Tre iterazioni dichiarate, tutte su integrato-1 (T13 2027-01-20, 7h/6 su
TRE gruppi 8-15, tutte unpinned):
- iter1 (cross-day relay, niente gate source): 1 mossa applicata MA -4 -> -4: H7
  curato si scambiava con H32 -1 aperto (il giorno sorgente perdeva l'ora sotto
  quota: nessun controllo sorgente nel primo giro).
- iter2/iter3 (con i gate sorgente): SRC-YIELD=6 su 6 candidati con l'aritmetica
  STRETTA e poi con quella TOLLERANTE (max(quota-tol, floor)): STESSO esito.

## La lettura strutturale (perche' la famiglia NON chiude qui)

L'H7-cap e' un vincolo del DOCENTE-GIORNO; curarlo richiede che UNA delle 7 ore
lasci la data. Ma le 7 ore sono su TRE gruppi DIVERSI (G06 x3, G01 x2, 2
MODA x1, G10 x2): ogni gruppo-giorno e' a quota piena (il mid-day export
richiede yield tollerante assente, la coda non puo' lasciare senza buco). La
variant (b) "riordino intra-giorno" e' STRUTTURALMENTE inutile per H7: un
riordino lascia invariato il numero di ore del docente-giorno (e l'H7 misura
proprio quello). L'unica cura possibile e' la CATENA CROSS-GROUP di depth-2:
l'ora del cerniera esce dal giorno verso un'altra data DI UN ALTRO GRUPPO via
swap (una lezione di altro gruppo del giorno 01-20 scambia posto con una
lezione di T13 di un altro giorno), che e' una macchina diversa e piu'
grande del relay a mossa singola.

## VERDICT (tre righe)

1. IPOTESI: il relay parallelo chiude l'overcap con una mossa (a)/(b).
2. MISURATO: variant (a) same-group relay: con gate sorgente onesti SRC-YIELD
   blocca tutti i 6 candidati (i giorni ospite sono a quota); variant (b)
   intra-giorno: strutturalmente non puo' curare H7 (lascia invariato il
   conteggio docente-giorno). L'H7 -1 dell'integrato resta APERTO: e'
   FAMIGLIA non coperta a mossa singola.
3. RACCOMANDAZIONE: TASK-621 resta APERTA con la specifica estesa: la cura e'
   la CATENA CROSS-GROUP depth-2 (swap fra lezione-cerniera di gruppo G1 e
   lezione di altro gruppo G2 su data diversa), non il relay a mossa singola.
   Il census e' completo (H31-FIRST=1, SRC-YIELD=6, H1/VRP/altri gate: vedi
   iter3 log). NESSUNA adozione, NESSUN porting (una famiglia che non chiude
   sul suo bacino-target non si porta).
