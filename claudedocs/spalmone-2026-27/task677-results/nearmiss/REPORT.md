# Near-miss round 04/10 (post 635+677): decomposizione e cieco G14

## Le due consegne decomposte

| stato | score | celle |
|---|---|---|
| pred-a2 (resume su costruzione scartata) | [-3/0] | G14 2027-05-07: H31 x1 + H33 x1 (nodo: 2 ore ordinarie alle 9-10, floor 3, prima slot 8) + G11 2027-06-04 H34 quota 4 (giorno dichiarato vuoto) |
| adozione 677 finale (436min, 5 tentativi, predictor OFF) | [-2/0] | G12 2027-04-16 H34 quota 5 (golden pieno 8-12, run vuoto) + G13 2027-12-02 H32 quota 5 placed 4 (manca slot 12) |

Registry: H33 ASSENTE (famiglia scoperta), H31 CURATA-RATIFICATA ma la cella sopravvive,
H32 CURATA con geografia limitata dichiarata, H34 IN-COSTRUZIONE (due celle fresche).

## Anatomie (day_anatomy_viz, consumate)

- G14 05-07: golden = CD@8 + CS@9-10 (3h); run = CD@9 + CS@10 (2h, +1 slide, -1 ora).
- G12 04-16: golden pieno 8-12 (5 materie diverse); run tutto EMPTY.
- G13 12-02: run 8-11 pieno, slot 12 EMPTY (golden: T05 Metrologia).

## Cieco G14 05-07 (spec /tmp/blind-g14-spec.md, mondo chiuso 3 giorni, cost model reale)

ESITO: **NO-SOLUTION dimostrato** (deduzione + BFS esaustiva: 8 sole mosse legali dallo
stato iniziale, 6359 stati entro 6 mosse, il goal mai raggiunto; nessun docente G14
mai libero allo slot 8 del venerdi in nessuno stato raggiungibile). Catena: le 4 lezioni
che occupano l'8 del venerdi appartengono a gruppi-quota il cui giorno e' congelato
(quota 1:1 + compattezza + partenza: nessuna prima mossa legale esiste per sbloccarli).
MEZZA CURA VALIDA (1 mossa, legale): G14 Orientamento 05-06@12 -> venerdi@11
(T14 libero a 11): chiude H33, NON H31.

## Arbitraggio (limiti dello spec, dichiarati)

Il mondo chiuso a 3 giorni e la lettura quota-esatta rendono il congelamento piu'
forte del modello reale (H32 e' difetto, non uguaglianza; l'anno intero ammetterebbe
cascate cross-date fra giorni dello stesso gruppo). Il verdetto va letto: **la ferita
non e' curabile nel vicinato locale** (la classe import/swap della catena non la
esprime); la cura dell'H31 richiederebbe una cascata cross-date su giorni di altri
gruppi non mostrati: non provata, presumibilmente fuori portata. L'H33 invece e'
curabile da un import cross-date same-group banale che NESSUN pass in servizio esegue:
EDF mira ai giorni VUOTI dichiarati, QFR/H32 ai giorni sotto-quota, e G14 e' fuori
scope H32/H34: **i giorni sotto-pavimento non-vuoti dei gruppi fuori-quota sono un
punto cieco della catena** (famiglia mancante, H33 assente dal registry).

## Conseguenze (file altrove) - EMENDATA dopo "prova con 678" (sera 04/10)

CORREZIONE: la frase qui sotto ("NESSUN pass in servizio mira ai giorni sotto-pavimento")
era SBAGLIATA: il braccio H33 ESISTE (TASK-577, 7/09, `EmptyDayFinisher.H33_FLAG`,
opt-in DEFAULT OFF, bancato sulle due G14 Friday della v131) ma era DOPPIAMENTE
MORTO sugli stati consegnati: GATE-E legge l'asse quota (i donatori convergono a quota
-> tutti rifiutati) e la mossa trascina la stanza (occupata all'atterraggio).
La "mezza cura" del cieco (import @11) era un ARTEFATTO dello spec senza il calendario
dei gruppi: il venerdi G14 apre slots 0-2, l'11 e' fuori range, e l'unica terza cella
e' lo slot 0 (teacher-locked da cinque giorni saturi di gruppi-quota). Il NO-SOLUTION
del cieco era GIUSTO anche nel modello reale.

ESITO "prova con 678" (banco UnderfloorBench678Test, 3/3 verdi; EMENDATO dopo
code-review 3-angle): (1) emendamenti all'braccio (GATE-E floor-form per quota-esclusi
CON TRADE M20 DICHIARATO, atterraggio room-aware col gate sul value range e preferenza
capacity>=size, bordi candidati entrambi, trail undo con asse stanza e rollback
corretto); (2) il controllo positivo sintetico ha tre esiti osservati per ordine
donatori non pinnato (rifiuto pulito 679-bloccato / mossa net-zero che la belt
production revertirebbe / chiusura -7): il banco oggi assertisce le INVARIANTI (mai
peggio, mai fuori range, zero conflitti) e la PREDIZIONE DI CHIUSURA e' pre-registrata
come acceptance di TASK-679 (la deriva range/fresh taglia in entrambe le direzioni:
il range ESCLUDE celle che il fresh dichiara legali); (3) la cella reale resta
structuralmente chiusa e l'braccio correttamente non tocca nulla (assertito).
RITROVATI BONUS: (a) divergenza isSlotLegalFresh vs value range (TASK-679 aperto);
(b) bug sweep bordo-unico (fixato); (c) review F1: il rollback corrompeva l'indice
stanze sulle mosse con riassegno (fixato, percorso vivo per H34/H32).

- TASK-678 aggiornato con l'esito; registry cure: voce H33 aggiunta.
- Le due celle H34 restano nella famiglia aperta TASK-636/550.
- Captures e viz: pred-a3/ e adopt-final/ in questa directory.
