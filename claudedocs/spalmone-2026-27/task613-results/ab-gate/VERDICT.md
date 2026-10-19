# VERDICT: gate A/B TASK-613 v0.6 teacher-aware (laptop, 15/09 mattina, HEAD 9e49107a)

Protocollo = ab-gate2 TASK-612 (run-measure, profili freschi t613a/t613b, heap
2g, caffeinate, tripla guardia, capture-run, doctor). PREDIZIONE.md scritta
prima dei risultati. A3 = flag default (relay/h32 del 612 gia' ON in
produzione); B3 = + -Dplanner.construction.hole-relay.enabled=true.

## Score e famiglie

| braccio | score | famiglie hard residue | sanity | full-day | ricorrenza unpinned |
|---|---|---|---|---|---|
| A3 (control) | [-14/0]hard | H17 -8 (7), H32 -2, H29 -1, H33 -1, H34 -1, H7 -1 | 5 rossi (G10, G11, G12, G13, G14) | 94,3% | 71,6% |
| B3 (hole-relay ON) | **[-9/0]hard** | H17 -6 (6), H32 -2, H34 -1 | 3 rossi (G10, G11, G12) | 94,3% | 71,1% |

Entrambe nello stesso meta-bacino dei gate precedenti (attempt-1 condannato
CH-only, predictor che scarta i reseed, consegna via delivery refinement).

## La riga [delivery] TASK-613 in B3 (il dato del gate)

```
EmptyDayFinisher [delivery] (TASK-612 relay=true h32=true): 0 moves, 0 cells
HoleRelayFinisher [delivery] (TASK-613 v0.6 teacher-aware):
  targets=6, slides=0, rotations=0, moves=0
saveSchedule: [-9/0]hard ... delivery concordance OK (H29 memory=0 db=0)
```

Il pass ha VISTO 6 buchi H17 residui alla consegna e non ha mosso NULLA:
sul live la DFS teacher-aware non ha trovato catene pulite per quei 6 (i gate
simbolici hanno respinto tutto; l'oracolo improve-or-noop e' intatto per
costruzione). E' il caso counter-attribuzione: l'efficacia del pass resta
quella del banco (-3 -> 0 su replay), il ciclo misura non-regressione, come
da criteri pre-registrati.

## Criteri pre-registrati

- (a) hard B <= hard A a meta'-bacino comparabile: VERDE (-9 vs -14). NOTA
  ONESTA DI ATTRIBUZIONE: il delta NON e' del pass 613 (0 mosse in consegna):
  e' variabilita' di bacino GRASP (le due attempt-1 consegnate differono).
- (b) riga [delivery] 613 presente al punto previsto: VERDE (targets=6,
  moves=0: counter non-zero sui target, zero mosse, no-op pulito).
- (c) sanity non peggiorata: VERDE (5 rossi -> 3 rossi, nessun gruppo rosso
  nuovo; G13 e G14 rosso in A3 risultano puliti in B3).
- (d) KPI in banda: VERDE (full-day 94,3% entrambe, ricorrenza 71,6/71,1,
  day-start scarti >=3: none).
- (e) controllo crash: VERDE (zero IllegalStateException in ENTRAMBE; A3 ha
  attraversato vivo il delivery refinement, concordanza mem/db OK).

## Concordanza mem/db

Doctor CONSISTENT-NONZERO entrambe (hard -14/-14 e -9/-9 concordi
score.txt vs analysis.json; delivery concordance OK nei log). Medium
divergente (mem -1083496/-1082034 vs db -551784/-551313): TASK-509 noto.

## Lettura visiva (confronto-arm-b.html, crop coda anno nel pane)

- Le righe G10/G11 mostrano UNA-E-DUE celle bianche puntuali a meta'
  anno (i giorni 12-10 rossi del sanity); G12 le solite 3 sparse di
  settembre-ottobre (09-18, 10-02, 10-09); G09 una cella bianca isolata
  (famiglia H34): TUTTI i difetti sono visibili, localizzati e coincidono
  coi rossi censiti, nessuna diffusione.
- G13 in B3 appare PULITA (nessun rosso, coerente con analysis).
- La coda giugno si assottiglia in concerto col golden, non deserto.
  Nessun pettine, nessuna colonna vuota, nessun weekend fantasma: la
  lettura visiva CONFERMA i numeri.

## Esito per Fedora/maintainer

Gate A/B TASK-613: TUTTI i criteri VERDI (a-e), con l'attribuzione onesta:
sul live il pass e' stato un no-op sicuro (6 target visti, 0 mosse), il suo
valore dimostrato resta quello del banco (-3 -> 0 meccanizzato, certificato
2x). Nessuna controindicazione all'adozione; nessun guadagno end-to-end
dimostrato SU QUESTO bacino (il residuo live non ha catene esprimibili
pulite per i 6 buchi visti). L'adozione resta decisione di maintainer.
