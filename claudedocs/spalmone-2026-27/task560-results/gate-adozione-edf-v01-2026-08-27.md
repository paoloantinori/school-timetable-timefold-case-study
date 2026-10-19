# GATE DI ADOZIONE: EmptyDayFinisher v0.1 (TASK-560) - pack di decisione per maintainer

Un documento, cinque minuti. Stato: flag `planner.construction.empty-day-finisher.enabled`
DEFAULT OFF. L'adozione e' la decisione pendente; tutta l'evidenza sotto e' misurata
(26-27/08, worktree day-compact-542, ciclo multi-start dci-h34ms + banchi localizzati
su checkpoint).

## Cosa fa il pass

Alla sua occasione nel ciclo staged (post seconda QuotaFillRepair, pre medium-pure),
per ogni giorno dichiarato VUOTO (celle H34 residue) pianifica una TRANSAZIONE DI
CELLA INTERA: riempie la cella a quota con ore di coda dai giorni donatore in surplus
(gate: il donatore resta sopra max(quota propria, floor praticato)), con le slide di
contiguita' H17. Se la quota non e' raggiungibile, la cella non si tocca affatto
(legge della valle H32: un riempimento parziale peggiora il quadro).

## L'evidenza

**1. Curva cross-bacino** (banco localizzato per tentativo, resume dal
POST_ROOM_QUOTA_FILL_REPAIR del ciclo multi-start, 5 tentativi):

| tentativo | gate EF | celle pre | celle chiuse | mosse | conflitti | revert |
|---|---|---|---|---|---|---|
| 1 (vincitore, deterministico) | -14 | 12 | **4** | 49 | 0 | 0 |
| 2 (GRASP) | -50 | 10 | **0** | 0 | 0 | 0 |
| 3 (GRASP) | -61 | 10 | **3** | 30 | 0 | 0 |
| 4 (GRASP) | -62 | ~10 | **2** | 12 | 0 | 0 |
| 5 (GRASP) | -85 | ~10 | **1** | 14 | 0 | 0 |

Lettura: l'impatto e' CORRELATO alla qualita' del bacino. Sul vincitore del
multi-start l'adozione vale 12 -> 8 celle; sui GRASP medi ~2; sul peggiore 0
(donatori ancor piu' strozzati: GATE-E 2015, H1 383).

**2. Costi**: 0 conflitti e 0 revert su TUTTI i banchi (il guard improve-or-noop
non e' mai scattato). Wall del pass: ~4 secondi (00:26:45 resume ->
00:26:49 pass completato sul vincitore, 49 mosse): trascurabile rispetto al
ciclo ~90 min.

**3. Le celle chiuse sul vincitore** (verificate nel DB post-fix-infra):
G06 10/06, G09 27/05, G10 17/05, G11 07/06.

**4. Le 8 residue e PERCHE' non chiude**: G09 10+24/05 sono
MATEMATICAMENTE INRIEMPIBILI (il foglio dichiara 670h, il pool ne ha 666:
nessun pass puo' creare 4 ore; il golden stesso lascia 2 giornate G09
vuote); le altre 5 (G07 03/06, G11 04/06, G12 16/04, G13 19+17/24/05)
sono bloccate da move-connectivity: le mosse singole esistono (autopsia A2:
12/12 celle con candidate) ma cadono nella valle H32 o nelle slide.

**5. Sanity**: nessun impatto misurato (il pass non tocca giorni con lezioni,
solo celle vuote; i rossi sanity restano la firma giorni-misti TASK-559).

## Pro

- 4 celle sul vincitore a costo quasi zero (4s, 0 conflitti, 0 revert).
- Forma whole-cell = mirror della gate (c) di QFR v2: stessa legge scoperta
  indipendentemente dal lato deterministico e dal lato LS.
- Il flaw v0.1 (slide pre-estrazione) e' fixato con TDD e la curva e' misurata
  post-fix.

## Contro

- Bacino-dipendente (0 sul peggiore): non e' una garanzia, e' un boost.
- Le G09 non le tocchera' mai finche' il foglio over-dichiara (domanda
  referente pendente).
- E' un prototipo v0.1: la forma e' minimale (donatori per ordine di iterazione,
  no backtracking fra donatori).

## Raccomandazione

ADOTTARE (flip a DEFAULT ON con la forma opt-out standard). Le 4 celle sul
bacino buono sono l'80% del residuo chiudibile-con-ricerca; il costo e'
trascurabile e misurato; il rischio e' zero per costruzione (improve-or-noop +
transazioni atomiche). Le contro non sono rischi del pass ma limiti del
dominio (foglio e bacini). Se maintainer preferisce aspettare: la risposta referente
su G09 e' l'evento che sblocca il quadro completo.

## ADDENDUM (27/08 01:05, quinta tratta): il sesto punto della curva

Il vincitore del ciclo h34on (single-attempt deterministico, gate -14, 10 celle)
benched col v0.1 dal suo POST_B_ROOM: **0 celle chiuse** (la coda tail che
precede il pass - room-tail + QFR2 - e' corsa integra: QFR2 0 mosse).

La curva completa diventa: **4 / 0 / 3 / 2 / 1 / 0** su sei bacini, di cui due
"buoni" entrambi gate -14 (il vincitore h34ms chiude 4, il vincitore h34on
chiude 0). LETTURA AGGIORNATA: il pass e' bacino-sensibile ANCHE fra bacini
buoni (la geometria dei surplus e dei donatori varia con la traiettoria LS,
non solo con la qualita' del gate). L'atteso per adozione resta un GUADAGNO
MEDIO ~1.7 celle/bacino (10/6) con picchi di 4 sul bacino giusto, non una
garanzia. La raccomandazione ADOTTARE non cambia (costo ~4s, 0 conflitti,
0 revert: il downside e' il no-op), ma la frase "12 -> 8 sul vincitore" va
letta come "fino a 12 -> 8 sul vincitore osservato".
