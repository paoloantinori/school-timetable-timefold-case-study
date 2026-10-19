# TASK-482 Kempe-chain MoveSelector. Design + pre-validazione meccanica su grasp-seed-1. 2026-08-03.

> Fedora re-spike (mandato autonomo). La sessione precedente (alba10) aveva gia' costruito un
> discriminatore offline e chiuso TASK-482 NO sul bad-seed task478 (@10). Fedora (contesto autonomo,
> non ne era a conoscenza) ha chiesto il prototipo dietro flag + la caratterizzazione su grasp-seed-1
> (il seed nominato esplicitamente). Questo doc: model expression, dove vivrebbe nel codice, criterio
> di accettazione, e la pre-validazione MECANICA su grasp-seed-1 che RISOLVE la domanda di fattibilita'.

## TL;DR

1. **Domanda di fattibilita' (Fedora): la Kempe chain chiude il gap H17 su grasp-seed-1? NO.**
   Pre-validazione meccanica: **0/9 sustainable, 9/9 no-improve** (test
   `KempeChainH17DiscriminatorTest.kempeChainH17CloseGraspSeed1`, gated `PLANNER_TEST_KEMPE=1`).
   Stessa root cause del @10 (slot saturo + ostacoli VRP-bloccati).
2. **MoveSelector di produzione NON costruito.** La pre-validazione dice che ogni chain e' no-improve
   (H17d=0 o shell-game noNewGap=false o H3 -1000): la LS le rifiuterebbe (hill-climbing) o le
   accetterebbe solo peggiorando (tabu). Un MoveSelector reale sarebbe INERTE sul bad seed. Costruirlo
   e' alta fatica / basso valore atteso. Decisione ragionata: NON si costruisce (la pre-validazione
   serve proprio a questo), documentata.
3. **Residuo (l'unica cosa che un prototipo reale potrebbe ancora testare):** la LS che COMBINA una
   chain no-improve con mosse di follow-up per recuperare. Il discriminatore offline testa la chain in
   ISOLAMENTO (apply, score, undo). Solo un MoveSelector nella LS reale risponde a questo. E' speculativo
   (le chain sono no-improve gia' prima dei follow-up) e NON e' stato misurato. Tracciato come unica via
   residua, NON raccomandata a meno che maintainer non voglia perseguirla nonostante il NO.

## (a) Model expression: come si modella una Kempe chain sul nostro modello

Entita': `Lesson` (una ora scolastica), singola planning entity. Variabili: `timeslot` (basic) e `room`.
Una Kempe chain opera sul **conflict graph** a due colori (i due timeslot):
- **Vertici** = lezioni a uno dei due timeslot, UNPINNED, docente reale (non pseudo), che possono LEGALMENTE
  spostarsi al timeslot opposto (l'opposto e' nel loro value range / VRP, `Lesson.getAvailableTimeslots()`).
- **Edge** = conflitto H1 (stesso docente non-pseudo) o H3 (stesso student group). Due lezioni con edge non
  possono stare nello stesso timeslot.
- **Chain** = componente connessa contenente il donor (BFS sui due colori).
- **Swap**: lezioni della chain al colore c1 (ts_hole) -> c2 (ts_donor), e viceversa. Per il teorema di
  Kempe, le coppie in conflitto nella chain si scambiano insieme -> nessun nuovo H1/H3 TRA lezioni della chain.

**Coppie di slot candidate**: guidate dai gap H17. Per ogni (group, date) con un gap allo slot h, il donor
e' una lezione dello stesso gruppo (docente reale, unpinned) su un'altra data, con h nel VRP. ts_hole = h,
ts_donor = il donor's slot.

**Assunzioni di modellazione (decise io, mandate "decidi tu e documenta")**:
- **Pin**: le pinned lessons e pseudo-docente (Stage) sono OSTACOLI FISSI (esclusi dalla chain, restano al
  loro slot). Non si muovono.
- **VRP-blocked**: una lezione il cui VRP non include il timeslot opposto e' un OSTACOLO FISSO (stays, blocks).
  E' questo il punto di rottura del teorema di Kempe (vedi (c)).
- **Block-entity**: NON rilevante. La famiglia block-entity e' storica (LabBlock rimosso TASK-228/267); oggi
  c'e' solo `Lesson`. La Kempe chain opera su Lesson, nessuna struttura block da gestire.

## (b) Dove vivrebbe nel codice

Move selector custom nella **LS B-TIMESLOT** (`solverConfig.xml`, phase 2). Timefold 2.3.0 community
(verbatim dal jar, verificato dal design doc precedente `task482-results/design-kempe-chain-2026-08-03.md`):
- `MoveListFactory<Solution_>` (EAGER, piu' semplice): `createMoveList(Solution_) -> List<Move>`. Registrato
  `<moveListFactory><moveListFactoryClass>...</moveListFactoryClass></moveListFactory>`. Fattibile ma non
  efficiente (tutte le chain in una lista).
- `MoveIteratorFactory<Solution_, Move_>` (LAZY, consigliato produzione): `createRandomMoveIterator(sd, rand)`
  produce chain on-demand. Registrato `<moveIteratorFactory>`.
- KOpt (Kempe della letteratura per LIST variables) NON utilizzabile: il nostro `timeslot` e' basic variable,
  KOpt e' solo per `@PlanningListVariable`.
- `Move` API 2.3.0: `execute(MutableSolutionView)`, cambio variabile via `mutableSolutionView.changeVariable(...)`.

**Gating (se fosse costruito)**: `planner.kempe-chain.enabled` default FALSE (opt-in come
CompositionHoleCloser), letto LIVE per-build. La factory ritorna lista vuota / iterator vuoto quando off
(regressione zero). NON costruito in questo turno (vedi TL;DR #2).

## (c) Perche' la chain fallisce (root cause, meccanicamente verificata)

La pre-validazione (discriminatore offline con oracolo `solutionManager`) su grasp-seed-1 (@11, 9 date) e
sul bad-seed @10 (6 date, regression check) mostra:

```
grasp-seed-1 (@11): BASE H17=-9, conflicts=0 (fedele). VERDICT 0/9 sustainable, 0/9 shell-game, 9/9 no-improve.
@10 (task478):      BASE H17=-6, conflicts=0.       VERDICT 0/6 sustainable, 0/6 shell-game, 6/6 no-improve.
```

Ogni chain e' no-improve perche':
1. **holeClosed=true ma noNewGap=false (shell-game).** Il donor riempie @11 ma il blocker spostato al ts_donor
   apre un gap sulla sua group-day (mid-run, come il depth-3 cascade TASK-481). Il buco migra.
2. **H17d=0 (nessun guadagno netto).** Il donor chiude @11 ma ne lascia un altro altrove.
3. **ttierHardd=-1000 (H3 regression).** Il teorema di Kempe (conflict-closure) NON vale quando ci sono
   ostacoli IMMOBILI per VRP: una chain lesson arriva a uno slot dove una lezione VRP-bloccata (non nella
   chain) condivide il gruppo -> nuovo H3. -1000 = esattamente 1 nuovo conflitto di gruppo (peso 1000).

La root cause e' la STESSA del depth-3 cascade: il bacino (@10/@11) e' SATURO (11-12 gruppi packati), ogni
blocker e' mid-run, spostarlo fa danno altrove. La Kempe chain non elude la saturazione: la sposta o
introduce conflitti hard.

## (d) Criterio di accettazione (se fosse costruito)

La close e' SUSTAINABLE iff (VERBATIM dal discriminatore, estensione del gate depth-3):
- H17 migliorato (h17d < 0)
- Nessuna regressione timeslot-tier hard (ttier == 0)
- Il buco effettivamente chiuso (holeClosed)
- Nessun nuovo gap per-(group,date) (noNewGap) [anti-shell-game]

Su grasp-seed-1 NESSUNA chain soddisfa questi 4 criteri (0/9). La LS, valutando il delta immediato di ogni
chain, rifiuterebbe le no-improve (hill-climbing) o le accetterebbe solo peggiorando (tabu, senza garanzia
di recupero). Il MoveSelector sarebbe inerte.

## (e) Decisione

**TASK-482 confirmato NO su grasp-seed-1 (meccanicamente), MoveSelector NON costruito.**

La pre-validazione (discriminatore offline, oracolo reale) risponde alla domanda di fattibilita' di Fedora:
la Kempe chain non chiude il gap su grasp-seed-1 (0/9). Costruire il MoveSelector di produzione (alta fatica,
API 2.3.0 MutableSolutionView) per un vincolo che la pre-validazione dice inerte non e' ragionevole. La
pre-validazione serve proprio a evitare questa spesa. La via principale resta il **multi-start (TASK-480/483,
adottato DEFAULT ON)**: selezionare un bacino good-seed che evita la saturazione @10/@11, non curare
localmente un bacino saturo.

**Residuo non misurato (onesto):** la LS che combina una chain no-improve con follow-up. Solo un MoveSelector
nella LS reale lo testerebbe. Speculativo (le chain sono no-improve gia' in isolamento). Se maintainer vuole
perseguirlo nonostante il NO, serve il prototipo completo + A/B con guardia. Non raccomandato.

## Riproducibilita'

```
PLANNER_TEST_KEMPE=1 ./scripts/core/test.sh -c KempeChainH17DiscriminatorTest
```
Entrambi i test (@10 regression + grasp-seed-1) passano come spike informativo (EXIT 0). Il discriminatore
e' gated default-OFF (`@EnabledIfEnvironmentVariable`), quindi il `test.sh` normale lo salta (zero impatto
suite). Raw: grasp-seed-1 e' `task480-results/badseed-frequency/grasp-seed-1/export.json.gz`.

## Puntatori

- Discriminatore (esteso, seed-1 + @10): `src/test/java/com/school/planner/bootstrap/KempeChainH17DiscriminatorTest.java`
- Design + verdetto precedenti (alba10): `task482-results/design-kempe-chain-2026-08-03.md`
- FORK depth-3 (root cause condivisa): `task481-results/FORK-shell-game-depth3-2026-08-02.md`
- Ricerca (finding 3, Kempe): `claudedocs/research_multistart-vs-alternative_2026-08-02.md`
- Verdetto questo turno: `task482-results/VERDICT-kempe-2026-08-03.md`
