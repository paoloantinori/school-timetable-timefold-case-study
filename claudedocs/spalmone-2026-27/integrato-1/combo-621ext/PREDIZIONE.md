# PREDIZIONE combo-621ext (Fedora, pre-registrata; laptop la riporta PRIMA delle corse)

> -4 -> -1 (H17 x2 e H7 chiusi; l'H28 -1 originale resta: e' famiglia senza cura
> in questo giro; zero H31/H32 nuovi: i gate). Se il banco chiude anche l'H28 di
> origine con qualche forma: bonus, census. Se una famiglia non chiude: census del
> gate dominante e la notte ha la prossima specifica.

Esito misurato: VEDI SOTTO.

## ESITO MISURATO (step45, integrato-1 export, heap 16g, tre iterazioni dichiarate): -4 -> -2, H17 CHIUSO pulito, H7 resta col census del gate dominante

- iter1 (belt zero-tolleranza): 0 swap (BELT=40: un giorno a DUE buchi resta con
  un buco dopo il primo swap: il belt richiedeva zero-buchi assoluti).
- iter2: formato printf (crash).
- iter3 (belt holes_after <= holes_before, donor-day resta zero-buchi):
  **2 swap / 4 mosse chiudono ENTRAMBI i buchi G13 06-10** (@10 e @11: import
  dalla coda del donatore + espulso che atterra sulla cella vacata): H17 -2 -> 0.
  **H28 -1 INVARIATO: il gate trasversale HA FATTO IL SUO LAVORO** (nessun nuovo
  ritorno aperto dai 4 movimenti; il -1 e' l'originale).
- H7: h7Cures=0. Census: {SRC-YIELD=3, H31-FIRST=1, VRP=515, H1=2,
  NO-CANDIDATE=1}. Predizione -1: MISURATO -2 (H7 non chiuso).

## Il gate dominante dell'H7 (la prossima specifica)

SRC-YIELD (3 candidati su 4 gruppi ospite) + H31-FIRST (1): coerente con tutto
il filone (620, 621, cieco): OGNI gruppo ospite sul 2027-01-20 e' a quota piena
e l'unica ora esportabile senza buco e' la PRIMA (che apre H31). La depth-2
same-group-landing NON basta: la prossima specifica e' DEPTH-3: hinge esce,
partner atterra sulla cella liberata, e la CELLA DI PARTENZA del partner viene
riempita da un donatore tollerante del gruppo del partner (tre livelli), oppure
l'esenzione H31-per-pin va investigata sul giorno bersaglio.

## VERDETTO (tre righe)

1. IPOTESI: -4 -> -1 (H17+H7 chiusi, H28 resta, zero collaterali).
2. MISURATO: -4 -> -2: H17 CHIUSO con la famiglia cross-date-swap 1-per-1 (2
   swap/4 mosse, il backfill E' l'espulso: la specifica (1) del cieco FUNZIONA),
   H28 gate trasversale PROVATO (zero nuovi ritorni), H7 NON chiuso: census
   {SRC-YIELD=3, H31-FIRST=1} = ogni ospite a quota piena, l'unica uscita senza
   buco e' la prima ora che apre H31.
3. RACCOMANDAZIONE: la specifica (1) e' PRONTA per il porting (swap + H28gate +
   belt holes-monotone); l'H7 passa a DEPTH-3 (catena hinge->partner->donatore
   tollerante) o resta bacino. Nessuna adozione.
