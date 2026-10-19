# Golden self-match: quanto e' regolare la pratica del golden sulla propria forma

Data: 2026-09-02. Domanda (maintainer, post-chiusura filone pair): "non e' che la costruzione
regolare che facciamo noi e' piu' stringente di quella che fa l'human planner, e per via
di questo cerchiamo di mantenere la regolarita' piu' rigidamente di quanto non si conceda
l'human planner?"

## Misura

Per ogni coppia (docente, gruppo) del golden con >=20 ore: settimane attive = settimane ISO
con almeno un'ora della coppia; cella "regolare" = (weekday, slot) presente in almeno P delle
settimane attive; **golden self-match = ore in celle regolari / ore totali della coppia**.
Popolazione: 169 coppie, 10.487 ore (sabati, docente Fasullo e Stage esclusi, trappole note).

Trappola metodologica scartata: il CSV `golden-pair-cells.csv` codifica l'UNIONE ANNUALE delle
celle praticate (mediana 7 celle per forma, max 22, contro ore settimanali tipiche 1-2; 153/172
forme piu' larghe di una settimana tipo): misurare il golden contro quelle celle da 100%
circolare (verificato: 10487/10487). La settimana-tipo al P% e' la definizione onesta.

## Risultati

| Soglia P | golden self-match | coppie >=90% |
|----------|-------------------|--------------|
| 0,25 | 82,5% | 68 |
| **0,50** | **67,1%** | **45** |
| 0,75 | 45,2% | 27 |

Distribuzione BIMODALE a P=0,50:
- 45 coppie rigidissime (T24 su G04/G07/G11 al 100%, T26-G14 100%, T22-G07 100%): la scuola difende LA FORMA su queste.
- Codino a 0% (NESSUNA cella compare in meta' delle settimane): T18-G06 (120h, 37 settimane!), T02-G05/G10/G12, T20-G06: il planner umano usa le coppie grosse come stucco.

## Lettura (la risposta alla domanda: SI', in parte)

1. Il golden pratica la propria regolarita' al **67%**: regolarita' morbida, non da stampo.
   La costruzione pair partiva al 72,7% (contro il bersaglio LARGO dell'unione annuale) e la
   serie E difendeva quel livello: pretesa SOPRA la pratica della scuola = la classe d'errore
   della ratifica TASK-517 (golden-first ai piani), quarta occorrenza dopo TASK-432/430/chain.
2. Il run di fine ciclo sta al 40% sullo stesso bersaglio largo: NON tutto il calo e'
   "ritorno alla normale pratica": resta un gap vero (~27 punti vs 67), ma la meta non e'
   72,7 ne' 100: e' il livello praticato, per-coppia.
3. La cura che ne discende e' SELETTIVA, non piu' forte: difendere la forma dove il golden
   e' rigido (le 45 coppie), lasciarla scivolare dove la scuola stessa la lascia scivolare
   (il codino a 0). La classificazione rigido/sciolto e' dichiarabile nel workbook (stessa
   via di copresence-depth e slot0-anchors, TASK-452).
4. Riletta in questa luce, la ratifica M38 a peso 1 (2/09) e' coerente: dà direzione quasi
   gratis senza rigidita'; e il builder pair resta giustamente opt-in.

## Riproduzione

Script inline nel transcript del 2/09 (join `seed/lessons.csv` x `seed/golden-pair-cells.csv`,
decodifica celle = prefisso giorno L/M/T=mer/G/V + slot 1-based, stessa di
`PairCellsTemplateBuilder.loadGoldenPairForms`). Da promuovere a `scripts/analyze/` se la
difesa selettiva viene costruita.
