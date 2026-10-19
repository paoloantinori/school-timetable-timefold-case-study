# SPIKE 1 (TASK-613): classificazione delle 21 mosse cieche round-2 in primitive

Ipotesi da verificare (mandato): la catena D1 richiede come primitive la
'slide di una coda nel buco di un GIORNO ESENTE'; senza di essa COMP-H1=3 e' fisso.

| blocco (gruppo-giorno) | esente? | mosse | forma | primitive |
|---|---|---|---|---|
| G14 2027-04-30 | SI | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G12 2027-04-30 | - | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G10 2027-04-30 | - | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G02 2027-04-30 | - | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G11 2027-04-30 | - | 1 | slide unica nel buco del DIFETTO (@11) | slide (v0.2d la fa) |
| G08 2026-11-04 | SI | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G13 2026-11-04 | - | 1 | slide unica nel buco del DIFETTO (@11) | slide (v0.2d la fa) |
| G04 2027-03-02 | SI | 3 | 3-ciclo permutazione (inserzione) | rotazione-inserzione del giorno |
| G13 2027-03-02 | - | 1 | slide unica nel buco del DIFETTO (@12) | slide (v0.2d la fa) |

TOTALI: 6 blocchi 3-ciclo, 3 slide-nel-difetto, 0 altre forme.

## Esito in tre righe (ipotesi / risultato / tenere-buttare)

IPOTESI: la cascata D1 e' inesprimibile perche' manca la primitive 'slide
di coda nel buco di un giorno ESENTE'.

RISULTATO: FALSIFICATA nella forma letterale. Delle 21 mosse: 6 blocchi
sono 3-cicli di permutazione-inserzione (G14, G12, G10, G02, G08 x2,
G04) e 3 sono le slide finali NEL buco del difetto (G11 12->11, G13 13->11,
G13 13->12). NESSUNA mossa e' una slide nel buco di un giorno esento diverso dal
difetto: la primitive ipotizzata NON compare nella soluzione cieca. La catena D1
= QUATTRO 3-cicli concatenati per dipendenza docente + 1 slide: cio' che manca
non e' una mossa nuova ma la capacità di concatenare 4 rotazioni-inserzione
(v0.3 ricorsava con legalita' per-passo e moriva sui gate; v0.4 le costruisce
simbolicamente ma il composito finale conserva 3 H1: il difetto e' nella
composizione/verifica, non nell'alfabeto).

TENERE-BUTTARE: BUTTARE la primitive 'slide-in-esento' (non e' nella soluzione);
TENERE l'insegnamento strutturale: il builder ibrido (v0.2d fast-path + catena di
3-cicli concatenati con verifica composita corretta) e' la direzione: il difetto
da curare e' perche' il composito di 4 cicli + slide finisce con 3 H1 allo stato
finale (confrontare l'ordine delle mosse cieche: i 4 cicli sono applicati in una
SEQUENZA precisa di dipendenza docente, forse non quella enumerata dal builder).
