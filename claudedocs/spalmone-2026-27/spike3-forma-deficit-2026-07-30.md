# SPIKE 3 - Forma del deficit: concentrazione dei giorni deep - 2026-07-30 notte

## Consegna (3 righe)
- **Ipotesi:** i giorni deep-deficit (>=2h) sono DIFFUSI (costo di sistema) o CONCENTRATI su pochi
  gruppi / (gruppo, giorno-settimana), come il ritardo della prima ora?
- **Numero misurato:** deep-deficit (excl G14 + sabati off-grid) = **31 giorni**, **100% su 3 gruppi**
  (G04 23, G08 6, G12 2); **G04 da solo = 74%**; la cella **G04 venerdi' = 13 giorni**
  (dominante), top-5 celle (gruppo,wd) = 87%; **68% in mar-giu** (window tier-drift). G04 ha docenti
  venerdi'-disponibili ampi (T15 222h, T14 90h, T25, T21, T11, T23,
  T06; solo T05/T24 ven-OFF): NON e' carenza strutturale di supply, e' ricerca/coordinamento.
- **Tenere: SI', attackable.** C'e' una firma forte (G04, venerdi', mar-giu), non e' diffuso. Caveat:
  il kpi-fill (post-fix sabati) dice "51 deep / G12 10 / G04 23"; tolti i 14 weekday-closure
  phantoms il deep placeable del run e' 37, sempre concentrato su G04. Prossimo passo: feasibility focalizzata
  G04 venerdi' (e' recoverable dato il calendario split FC + i docenti disponibili?).

## Metodo
Ricalcolo da `certification-2026-07-30b/lessons.txt` (ore piazzate per (gruppo,data)) e
`export.json.gz` dailyActivityQuotas (atteso = aulaHours+labHours, base M20 senza stage). deficit =
atteso - piazzato. Deep = deficit>=2. Esclusi G14, sabati (off-grid per natura), giorni a quota 0
(no-session/festivi). Read-only.

## Concentrazione (firma, non diffuso)
Per gruppo (deep days): **G04 23, G08 6, G12 2** = 31. top-3 = 100%. G04 = 74%.
Per (gruppo, weekday): **G04 ven 13**, G04 gio 4, G04 mar 4, G08 gio 4, G04 mer 2, G12
gio 2, G08 mer 1, G08 ven 1. top-5 celle = 27/31 = 87%. Celle distinte: 8.
Per mese: gen 3, feb 1, mar 5, apr 9, mag 7, giu 0(sabati esclusi), set 4, ott 1, dic 1 -> **mar-giu
= 21/31 = 68%** (coincide col window del TASK-457: 67-68% deep in mar-giu).

## Cosa dice sull'attaccabilita'
La concentrazione c'e', ed e' netta come il ritardo della prima ora (G11 ven). La cella G04
venerdi' da sola e' 13 giorni: e' una FIRMA, non rumore di sistema. Inoltre G04 NON e' supply-starved
di venerdi' (docenti disponibili ampi), quindi il deficit e' di ricerca/coordinamento, non strutturale
come G06/T01 (overdemand). -> Attaccabile.

## Riconciliazione col kpi-fill (fedelta')
**Aggiornamento LAVORO 1 (2026-07-30): il fix sabati e' FATTO** (`daily-fill-kpi.py` esclude gli
sabati off-grid in tutto lo script). Il kpi-fill ora dice "51 deep, G12 10, G04 23" (non piu'
77 / 29): i 29 deep di G12 erano quasi tutti sabati di Stage off-grid, ora esclusi. Dei 51, **14
sono weekday-closure phantoms** (date di chiusura senza Timeslot): tolti, il deep placeable del run
30b e' **37** (G04 23, G08 13, G12 10). I "31" di questo SPIKE sono una misura con altro
denominatore (export.json `aulaHours+labHours`, non `class_days.csv`): stessa direzione (G04
dominante, concentrazione mar-giu), totale assoluto diverso. golden deep = 9 (corretto).

## Prossimo passo (costo)
Feasibility focalizzata su G04 venerdi': e' il deficit recoverable dato il calendario split FC
(aula/lab/stage quota) e i docenti venerdi'-disponibili? Se sì, e' un target di ricerca (coordinare
G04 ven come la prima ora); se no, e' un vincolo del calendario FC da dichiarare. Analisi read-only
sul dump, zero run.
