# GATE DI DECISIONE: emendamento M22 quota-aware min-days (TASK-558) - pack per maintainer

Stesso formato del pack EDF v0.1. Stato: flag
`planner.constraints.m22.quota-aware.enabled` DEFAULT OFF (prototipo opt-in).
L'emendamento e' una decisione di METRO, non di orario: cambia cosa il vincolo
M22 chiede, non cosa la scuola pratica.

## Cosa cambia

M22 (calendar compactness) oggi calcola il minimo numero di giorni di lezione
come `ceil(ore / maxHoursPerDay)`: un'ipotesi di packing alla capacita'
massima. L'emendamento lo calcola come `ceil(ore / media-quota-dichiarata)`:
il minimo deriva dal foglio stesso. Proprieta' algebrica: quando le ore piazzate
coincidono col pool del foglio, minDays DEGENERA nel numero esatto di giorni
dichiarati (una soluzione fedele al foglio non puo' piu' leggere come "troppo
sottile").

## I numeri (misurati col metro che replica la popolazione del vincolo)

| | min-days legacy | min-days emendato |
|---|---|---|
| golden (unpinned-equivalente) | 463 giorni-eccesso | **3** |
| nostro run (checkpoint ciclo) | 410 | **0** |

Il golden sotto il metro attuale e' vacuo sul pinnato ma l'unpinned-equivalente
paga 463 giorni-eccesso: la penalita' M22 che vediamo nei run (~-427k medium)
e' caricata su un minimo-giorni che NESSUNA soluzione fedele al foglio puo'
rispettare, golden incluso. Sotto l'emendamento la famiglia smette di esistere
come gap: resta solo M20.2 (che e' dato, non metro).

## Cosa NON cambia

- Il -12hard/-22.026medium misurato del golden pinnato resta identico (il
  vincolo e' vacuo sul golden importato: l'emendamento risponde alla domanda
  unpinned-equivalente).
- L'orario prodotto: l'emendamento NON sposta nemmeno una lezione (e' un cambio
  di punteggio, non di ricerca). Il medium totale dei run scendera' di ~-427k
  perche' la penalita' sparira', non perche' l'orario migliori.

## Pro

- Il metro smette di contraddire il foglio (golden-first: il golden paga 463
  giorni-eccesso sotto il min-days legacy: 154 volte le sue 3 celle reali).
- Il gap giorni riportato alla scuola diventa SOLO M20.2 (dato dichiarato) +
  le domande referente: piu' onesto e piu' actionable.
- Costo zero: proprieta' algebrica verificata, media pubblicata nello stesso
  loop delle statiche, fallback legacy per fixture senza foglio.

## Contro

- Perde pressione: se la scuola un giorno volesse GIORNI MENO del foglio
  (compattazione oltre il dichiarato), l'emendato non la premia. Ma e' esatta-
  mente la semantica voluta: il foglio e' l'autorita' sul numero di giorni.
- E' un cambio di interpretazione del report (i confronti storici con M22
  -427k non sono piu' paragonabili senza nota).

## Raccomandazione

ADOTTARE (flip a DEFAULT ON con la forma opt-out standard). E' il completamento
naturale del golden-first: il foglio dichiara i giorni, il metro li rispetta.
La pressione M22 sparisce e il vero gap giorni (M20.2, legato alle domande
referente) resta visibile e separato. Se maintainer preferisce tenerla spenta finche'
il pacchetto referente non e' spedito: l'evidenza non cambia, si rinvia solo la
lettura pulita dei report.
