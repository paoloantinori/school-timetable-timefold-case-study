# VERDICT: gate A/B appaiabile ripetuto TASK-612 su codice curato (laptop, 14/09 sera)

Mandato /tmp/mandato-ab614.txt (decisione maintainer 14/09: ripetere l'A/B dopo la
cura TASK-614). HEAD 540b0aa6 (= 707c8853 cure + i commit docs/misura del laptop;
le cure FCC (a) 42682724 e catch (b) 2ba9c1d8 sono nel tree, suite 1326/0 EXIT 0
verificata dal laptop nello stadio 1 della catena v2). PREDIZIONE scritta prima dei
risultati in PREDIZIONE.md (questa directory). Protocollo identico al gate del
mattino: run-measure, profili freschi t614a/t614b, heap 2g, caffeinate, doppia
guardia, capture-run, doctor.

## Controllo crash DEDICATO (il piu' importante): VERDE, la cura regge

- Braccio A2 (controllo, flag default): attraversa lo STESSO confine che al
  mattino uccideva il thread ("best attempt 1 shipped with CH-only rooms -
  refining its rooms before delivery", 19:19:05) e NE ESCE VIVO: rifinitura
  completata, saveSchedule 19:29:13, ZERO IllegalStateException in tutto il log.
- Braccio B2 (trattamento): stesso confine, consegnato (20:37:54), ZERO
  eccezioni.
- Entrambe le braccia nello stesso tipo di bacino del mattino (tentativo 1
  condannato CH-only, predictor che scarta i reseed del tentativo 2): la
  meta'-bacino e' comparabile, il confronto e' APPAIABILE.

## Score e famiglie

| braccio | score consegnato | famiglie hard residue |
|---|---|---|
| A2 (controllo) | [-18/0]hard | H17 -9 (8), **H34 -5 (5 celle vuote dichiarate)**, H32 -2 (2), H7 -2 |
| B2 (relay+h32) | **[-8/0]hard** | H17 -7 (7), **H34 -1 (1 cella)** |

Differenza -10 a favore di B2, quasi tutta la famiglia fill (H34 5->1, H32
2->0) piu' H7 2->0 e H17 9->7. Con la cautela del caso (n=1 per braccio, i
bacini GRASP non sono letteralmente identici): il trattamento NON peggiora e
il segno e' quello atteso dalla meccanica.

## Riga [delivery] di B2 (il dato del gate)

```
[EDF-H32] under-quota targets=1
EmptyDayFinisher [delivery] (TASK-612 relay=true h32=true):
  3 moves, 1 cell(s) closed to goal, 0 conflicts, 0 swap-compound(s)
saveSchedule: [-8/0]hard, 3077/12551 pinned
```

Al mattino lo stesso pass era 0 mosse su 3 celle residue; stavolta 3 mosse e 1
cella chiusa alla consegna (e le estensioni girano anche a meta'-ciclo col flag
armato: la famiglia fill mangiata in corsa spiega H34 5->1 vs il controllo).

## Criteri pre-registrati

- (a) hard B <= hard A a parita' di meta-bacino: **VERDE** (-8 vs -18, entrambe
  consegnate dal bacino condannato attempt-1).
- (b) riga [delivery] presente in B al punto previsto: **VERDE** (e attiva:
  3 mosse / 1 cella / 0 conflitti, improve-or-noop rispettato).
- (c) sanity per gruppo non peggiorata: **VERDE** (A2 3 gruppi rossi: G10
  4gg-buchi + G11 1gg + G12 1gg; B2 2 gruppi rossi: G10 2gg-buchi +
  1 gg sotto-min 04-01 + G12 3gg. Buci totali 9 slot (A2) vs 7 (B2);
  nessun gruppo rosso nuovo in B2).
- (d) KPI in banda: **VERDE** (full-day A2 93,0% / B2 94,4%; ricorrenza
  unpinned 71,2% / 71,4%, banda storica ~70-76; day-start scarti >=3: none in
  entrambe).
- Controllo crash: **VERDE** (zero IllegalStateException VRP alla delivery
  refinement in ENTRAMBE le braccia: la cura (a)+(b) regge sul live).

## Concordanza mem/db

Doctor: DOCTOR-CONSISTENT-NONZERO su entrambe (vettore hard concorde
score.txt vs analysis.json: -18/-18 e -8/-8; delivery concordance OK
H29 room-null memory=0 db=0 nel log di entrambe). MEDIUM divergente
(mem -1119539/-1084956 vs db -590025/-553317): TASK-509 noto, pre-esistente.

## Lettura visiva (occhio, confronto-arm-b.html del B2, screenshot nel pane)

- Le G10 mostrano a aprile una zona piu' chiara nel run (il gg sotto-min
  2027-04-01 del sanity) e i G12 un paio di celle bianche sparse: sono
  ESATTAMENTE i rossi del sanity, visibili e localizzati, niente di diffuso.
- La coda d'anno si assottiglia in concerto col golden (stesso andamento del
  gate del mattino); giugno NON e' deserto e stavolta il run e' meno bianco
  del braccio B del mattino sugli stessi gruppi (coerente con H34 1 vs 3).
- Nessun pattern a pettine, nessuna colonna vuota, nessun weekend fantasma,
  nessuna divergenza di forma run-vs-golden oltre i puntuali rossi gia'
  censiti. La lettura visiva CONFERMA la lettura a numeri.

## Esito per Fedora

Gate A/B ripetuto su codice curato: appaiabile, criteri (a)-(d) VERDI,
controllo crash VERDE. Il trattamento (relay+h32 opt-in) mostra il segno
atteso senza costi; l'adozione resta decisione di maintainer/Fedora (questa e'
materia del gate, non un default flip). Suggerimento operativo: le H34 x5
del controllo A2 dicono che la famiglia fill del bacino condannato e'
materia prima del pass armato; con flag OFF quelle 5 celle restano.
