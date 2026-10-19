# VERDICT notte 31/08-1/09: E1/E1bis (predictor bypass) + F6 + F7 design

Autore: Fedora (notte autonoma, direttiva "lavorate tutta la notte, decide Fedora").
Raw: `e1-predictor-bypass/` + `e1bis/` in questa directory, tutti pushati. La notte in una frase:

**la forma della coppia si costruisce (72,7% di golden-cell match), il ciclo la erode
tutta (40%: dove sta gia' il builder corrente), e il difetto e' circoscritto: i repair
post-RELEASE chiudono i buchi di costruzione spostando proprio le lezioni in-cella.**

## La tavoletta (golden-cell match, decodifica 1-based verificata su T16)

| | alla costruzione | a fine ciclo |
|---|---|---|
| builder corrente | 39,5% | 40,8% (v2.1b) |
| builder pair + F5 | **72,7%** | 40,0% (E1) / 39,6% (E1bis) |

## Le tre risposte della notte

1. **Il predictor era un falso-condannato per il builder pair** (n=2). La firma
   "200 buchi H17 di costruzione" che ieri sera condannava ogni start non predice il
   destino: il ciclo consegna [-10/-11]hard con ZERO H17 residuo (famiglie residue:
   H34/H0/H33/H32 a magnitudini 1-6), 12510/12512 lezioni, wall 1h09-1h45, full-day
   90,8-91,4 (golden 91,9), sanity 5 rossi della famiglia pre-esistente (il GRASP del
   builder corrente ne ha 6). La consegna e' comparabile-oltre la baseline v2.1b (-12).

2. **L'erosione ha una catena causale verificata nel codice, non ipotizzata**. La
   PROMOTION protegge le lezioni durante la LS timeslot; il RELEASE le libera prima
   delle stanze; i repair deterministici girano POST-RELEASE su tutto libero
   (HoleRepair.java:76: "an ex-promoted lesson is by then an ordinary unpinned
   lesson") e chiudono i ~200 buchi di costruzione spostando proprio le ex-promosse
   in-cella. I buchi di costruzione sono il carburante dell'erosione.

3. **F7 ha una direzione unica: la protezione della forma lungo la catena.** Tre
   strumenti sinergici (design nel task): (A) repair pair-aware, la costruzione
   pubblica la mappa lezione-cella e i repair ordinano i candidati da spostare
   "non-in-cella prima" (priorita', non divieto: minimo intervento, zero nuova
   meccanica); (B) meno buchi a monte (F6/stamp-skip: gli 89 canali per-data:
   sinergico); (C) M32-pair a valle (opzione A TASK-575, ora con evidenza completa).
   Con essa: rilassare la soglia predictor SOLO col pair ON (knob condizionato).

## Cosa e' morto stanotte (onesto, misurato, reverted)

- **F6-a** (adiacenza-consapevole nel builder): peggiorativa (107 -> 112 buchi),
  revertata in un'ora. Il posto giusto della cura NON era il builder: 89/90 canali
  sono celle saltate per-data, il template copre.

## Consegne accessorie

- KPI pair-stability nella capture canonica (opzione C ratificata, fail-soft).
- surefire.heap property-izzato (default invariato, da ratificare).
- Strumentazione skip per gruppo+causa; suite 1220/0 post-F5.
- Viz didattico pair-cells corretto (pannello 2: stesso venerdi', transizione 7/5).

## Domande per il mattino (maintainer)

1. F7: via (A) repair pair-aware come minimo intervento, o (C) M32-pair, o entrambe?
   Con essa: soglia predictor condizionata al pair.
2. Le 5 domande dell'intervista (debito quasi/-15hard, TASK-573, surefire.heap,
   TASK-572, pacchetto referente).
3. Opzione A TASK-575: la condizione "F5 consegnata" e' soddisfatta da stanotte.
