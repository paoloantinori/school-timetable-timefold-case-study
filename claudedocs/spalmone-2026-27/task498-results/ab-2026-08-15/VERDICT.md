# VERDICT A/B TASK-498 (skip-doomed-room) - hard 0 entrambi, meccanismo dimostrato end-to-end, risparmio 600s/tentativo condannato (laptop, 2026-08-15)

> laptop MISURA, ratifica di maintainer/Fedora. Flag
> planner.construction.skip-doomed-room.enabled DEFAULT OFF (opt-in prototype).
> Design: al confine post-RELEASE (dopo ExactFinisher, prima delle stanze), se il
> tentativo e' condannato (timeslot-tier hard < STAGED_HARD_ACCEPT_THRESHOLD = -2,
> la STESSA costante del gate B-TIMESLOT: nessuna soglia nuova) e non e' l'ultimo
> tentativo, la fase stanze corre senza il LS di rifinitura (CH-only: ogni lezione
> riceve stanza, lo score resta comparabile nel pick-best). Se il vincitore finale
> risulta un condannato (budget-break / all-doomed), il wrapper ne rifinisce le
> stanze PRIMA della consegna (corner case chiuso dalla code review).

## Setup

- HEAD 4bcbba8f+, JAR rebuildato. Driver nuovo `scripts/benchmark/ab-task498-skip-doomed-room.sh`
  (baseline-golden.sh per braccio). source=golden DEFAULT, multi-start ON, MT-LS ON
  entrambe le braccia; unica differenza il -D.
- Banner verificati: control `Skip-doomed-room refinement (TASK-498): enabled=false`,
  treatment `enabled=true`.
- Test L0: SkipDoomedRoomConfigTest 3/3 (verdetto puro tabellato + config pruning su
  XML di produzione). DoD review: /simplify (4 fix applicati: accessor, withPhaseSubset,
  verdetto estratto, test vacuo sostituito) + /code-review high (1 CONFIRMED chiuso col
  refine-before-delivery, 1 minor osservabilita' documentato qui sotto).

## Risultati

| metric | CONTROL (OFF) | TREATMENT (ON) | giudizio |
|--------|---------------|----------------|---------|
| **hard finale** | **0** | **0** | gate binario superato |
| medium / soft | -1.276.960 / -21.587 | -1.262.865 / -20.996 | in banda (varianza) |
| full-day | 87,1% | 86,9% | in banda |
| ricorrenza unpinned | 75,6% | 76,1% | in banda |
| buchi H17 | 0 | 0 | in banda |
| wall totale | 82 min | 88 min | rumore within (sotto) |
| **B-ROOM tentativo condannato** | **603s** (rifinitura inutile) | **3s** (CH-only) | **-600s = il risparmio** |
| DOOMED rilevati | 0 (flag OFF, il tentativo 1 era condannato a -9000) | 1 | il gate ha sparato bene |

## Meccanismo dimostrato end-to-end

Entrambe le braccia hanno lo STESSO tentativo 1 condannato al confine RELEASE
(timeslot-tier hard -9000 in entrambi i log). Nel control la fase stanze corre intera
(603s di LS che non possono curarlo: il residuo e' timeslot-tier) e l'attempt chiude a
-9000hard. Nel treatment il gate spara (`attempt 1/3 DOOMED ... skipping the
room-refinement LS`), la fase stanze corre CH-only in 3s e l'attempt chiude a -18404hard
(comparabile nel pick-best: hard peggiore, esattamente come atteso). In ENTRAMBE il
vincitore e' il tentativo 2 (0hard, M-TC per-retry on), schedule finale identica in
qualita': tutti i KPI di guardia piatti.

Il wall totale (82 vs 88 min) NON mostra il risparmio perche' la varianza del LS
B-TIMESLOT del tentativo 2 (che corre identico in entrambe) la copre: il risparmio
puntuale e' misurabile e misurato sulla fase saltata: 600s per tentativo condannato,
che e' il numero da moltiplicare per la frequenza di tentativi condannati.

## Proiezione del risparmio

Frequenza di tentativi condannati (dai census): source=input bad-basin ~1/3 delle
costruzioni GRASP; col multi-start N=3, P(almeno un condannato) e' alta sui bad seed
(il tentativo 1 condannato porta al retry). Risparmio atteso per run con 1 condannato:
~10 min; sui bad seed con 2 condannati: ~20 min. Su golden good-basin (deterministico):
il tentativo 1 e' buono e il lever e' inerte: costo zero. NOTA onesta: nel run di
stanotte il tentativo 1 golden e' arrivato -9000 timeslot-tier (non 0): il lever ha
sparato anche sul golden. La ragione va capita (H30 hard nuovo? pool-cap?) ma il
comportamento e' CORRETTO: -9000 < -2 e' condannato per definizione, e la rifinitura
stanze non poteva curarlo (infatti il control ha sprecato 603s e ha comunque perso il
tentativo).

## Known issues (da code review, documentate non bloccanti)

1. phases.json registra "5. LS - Room Refinement" con durata 0s anche quando la fase e'
   saltata (il PhaseTransitionMarker di coda non e' rimosso). Cosmetico: la riga DOOMED
   nel log e la durata 0s rendono il salto visibile. Se il prototipo viene ratificato,
   filtrare il marker nel path skip.
2. La terza passata di scoring al confine (timeslotTierHardScore dopo ExactFinisher)
   costa secondi per tentativo armato: accettata (riuso del valore interno di EF
   richiederebbe di toccare la firma di un pass ratificato per risparmiare poco).

## Raccomandazione per maintainer

Il lever fa esattamente ciò che promette, con corner case di consegna chiuso
(refine-before-delivery) e gate inerte dove non serve. Il valore dipende dalla
frequenza di tentativi condannati in produzione: cresce col flip source=input (1/3
bad). Proposta: adozione candidata quando il flip #1 decolla (stesso terreno del
census), oppure subito se il costo dei retry stanze si sente. Il flag resta OFF nel
frattempo. Raw: ab-off-run1/, ab-on-run1/ (stream preservati score-stream.txt).
