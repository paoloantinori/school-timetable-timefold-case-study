# SPIKE 4 - Feasibility G04 venerdi' (capacita' o ricerca?) - 2026-07-30 notte

## Consegna (3 righe)
- **Ipotesi:** le 13 giornate deep-deficit di G04 venerdi' (74% del deep totale) mancano perche'
  non c'e' capacita' (overdemand, come G06/T01) o perche' la ricerca non le trova?
- **Numero misurato:** G04 ha **13 venerdi' profondi** (deficit 2-3h, piazzati solo slot 8-10,
  vuoto 11-16). Il **GOLDEN riempie G04 venerdi' 34/34** (proof di piazzabilita'). Sul venerdi'
  campione 2027-04-23, a slot 12 e 14 ci sono docenti G04 **liberi con residuo>0** alla data
  (T14 4h, T25 4h, T21 2h; +T23 2h a slot14): le 3 condizioni (cella vuota +
  docente libero + ore residue) VALGONO e il run non le usa. Pacing residuo run~=golden (non e'
  tier-drift/esaurimento).
- **Tenere: SI', attackable. E' RICERCA, non capacita'.** Le ore sono piazzabili (golden 34/34 +
  docenti liberi con residuo presenti), e' la ricerca che non le alloca a G04. E' lo stesso family
  della prima ora (slot contesi cross-gruppo, solver parcheggia i docenti di G04 su altri gruppi).
  Attaccabile con coordinazione, non e' un vincolo da dichiarare alla scuola.

## Metodo (le 3 condizioni, stile G06/T01)
Per ogni (venerdi' profondo, slot vuoto) di G04: (a) ore mancanti vs quota attesa
(calendario_class_days.csv), (b) quali docenti di G04 sono LIBERI in quella cella (non occupati
ad altro gruppo quel giorno a quell'ora, dal dump 30b), (c) se hanno ore residue di incarico G04
a quel punto dell'anno (cumulativo piazzato fino alla data vs incarichi.csv). Le tre condizioni
devono valere insieme. Confronto col golden sulla stessa cella. Dati: certification-2026-07-30b/
lessons.txt, data/lessons.csv (golden), data/incarichi.csv, seed calendario_class_days.csv.

## I 13 venerdi' profondi (run 30b)
2027-02-26, 03-05, 03-12, 03-19, 04-02, 04-09, 04-16, 04-23, 04-30, 05-07, 05-14, 05-21, 05-28.
Tutti: exp=5h, piazzati 2-3h (solo slot 8-9-10), mancano 2-3h, vuoto da slot 10/11 in poi.
Concentrati fine-inverno/primavera (feb-mag), coerente col 68% mar-giu del TASK-457.

## Venerdi' campione 2027-04-23 - le 3 condizioni VALGONO
Run: G04 ha T25 a slot 8,9,10 (3h). Slot 11,12,14,15,16 vuoti (mancano 2h).
Residuo G04 a quella data (incarichi - piazzato fino al 2027-04-23): T14 4h, T25 4h,
T21 2h, T23 2h, T11 1h. (T15/T05/T24/T06/T03/T10 = 0 a quella
data, esauriti o non-venerdi).
Docenti G04 liberi con residuo>0:
  slot 11: T11 (1h)  [T14 e T25 qui sono occupati: T14->G01, T25->G02]
  slot 12: T11, T21(2), T14(4), T25(4)  <- 4 docenti liberi con residuo
  slot 14: T11, T21(2), T14(4), T23(2), T25(4)  <- 5 docenti
LE 3 CONDIZIONI VALGONO (cella vuota + docente libero + residuo) a slot 12 e 14, eppure il run non
piazza. E' ricerca.

## Golden G04 venerdi' 34/34 pieni (proof di piazzabilita')
Sullo stesso 2027-04-23 il golden mette: T25 slot 8,9; T21 10; T14 11; T11 12.
Quindi il golden Coordina T14/T11 su G04 a slot 11,12; il run li parcheggia altrove
(T14->G01 a slot 11) e lascia G04 a 3h. Stessa risorsa, allocazione diversa.

## Non e' pacing (tier-drift)
Residuo a 2027-04-23 run vs golden simile: T14 4 vs 5, T25 4 vs 8, T21 2 vs 2,
T11 1 vs 5. Il run NON esaurisce i docenti prima del golden (semai il golden ha piu residuo).
Quindi non e' esaurimento-anticipato (TASK-457): e' puro scarto di allocazione cross-gruppo sugli
slot di meta' giornata del venerdi'.

## Lettura / prossimo passo
G04 venerdi' e' un target di RICERCA: ore piazzabili (golden 34/34, docenti liberi con residuo
presenti), la ricerca non le assegna. Stesso family della prima ora (contenzione cross-gruppo).
PROSSIMO PASSO (costo): capire PERCHE' la ricerca parcheggi T14/T11 su G01/G02 a
slot 11 invece che su G04. Ipotesi: M20 (quota fill) pesa 1000/ora e G01/G02 potrebbero
avere un deficit di quel giorno che il solver riempie prima, rubando il docente a G04; o M15
contiguita' su G01. Analisi read-only sullo score analysis (chi driver M20 su G04 venerdi'),
non serve run. Se si capisce il driver, e' attaccabile (peso o riparazione mirata), come la prima ora.

## Two-sheet tension RISOLTA (controllo fatto)
G04 e' un gruppo FC split; il target AUTORITATIVO e' calendario_fc.csv, mentre questo KPI
misura contro calendario_class_days.csv. Verificato: fc.csv dà G04 venerdi' = **5h aula** sui
venerdi' campione (2027-03-19, 04-16, 04-23), IDENTICO a class_days.csv. Nessuna divergenza: il
verdetto "ricerca" e' netto, non un artefatto della two-sheet tension.
