# TASK-478 discriminatore UNCAPPED sul residuo run4 (zai, 2026-08-02 alba3)

> Successore diretto di AC#1 (`ac1-feasibility-residuo-run5-2026-08-01.md`). AC#1 era uno spike
> Python export-level (depth-0, senza room, caveats H2/H20/H5). Questo discrimina con la STESSA
> gate chain del Java ExactFinisher, UNCAPPED, sui 5 residui reali di run4. Read-only, no solve
> (~4s di EF pass). Risponde alla domanda che decide il next-step: il cap era l'unico ostacolo,
> o serve depth-2?

## DOMANDA

Sul residuo reale di run4 (5 H0 unassigned = `G03/T08/Matematica` + 4× `G06/T19/Barberia`
lab tag 'acconciatura', confermati dall'export.json.gz), esiste un completamento **0-hard a depth
0..1 SENZA CAP**? (EFFetto: rilanciare EF sul post-stato run4 con budget illimitato.)

Perche' i 5 rispondono anche per i 18 boxed originali: l'export e' POST-EF, quindi i 13 piazzati
da run4-EF @ 100000 sono gia' in griglia. Rilanciare EF UNCAPPED sul post-stato e' byte-per-byte
"EF resume past the cap". Se i 5 chiudono, tutti i 18 erano depth-0/1-collocabili (13 provati da
run4 + 5 provati qui); se qualcuno resta boxed, quel sottoinsieme e' genuinamente depth-0/1-
infeasible e serve depth-2. Non serve ricostruire i 18 (il pre-EF non e' un artifact catturato).

## METODO (la gate chain REALE, non il proxy Python)

`ExactFinisherRun4DiscriminatorTest` (`src/test/java/.../bootstrap/`), `@QuarkusTest` H2:
1. `SnapshotFixture.loadFromExport(ab-ef-run4/export.json.gz)` ricostruisce la griglia run4
   room- e id-faithful (i 13 piazzati + i 5 boxed = timeslot null).
2. `SnapshotFixture.seedMediumTierFacts(dataGenerator, snap)` semina la `DailyActivityQuota` REALE
   (515 rows) + expected-hours (1739 rows). **Materiale**: il VRP `Lesson.getAvailableTimeslots()`
   consulta la quota per no-session (H26) e lab-quota-day (H23). 4 dei 5 residui sono lab Barberia
   quota-bound: senza quota reale le celle VRP sarebbero silenziosamente piu' larghe e un "feasible"
   sarebbe falso positivo.
3. `System.setProperty(planner.exact-finisher.enabled=true)` + `max-candidates=100000000`
   (effettivamente uncapped: 5 residui × ~1368 celle depth-0, depth-1 ~1368² per blocker <<
   100M).
4. `ExactFinisher.repair(problem, ...)` DIRETTA (no `solver.solve()`, no clone): la stessa gate
   chain della production (H1/H3 conflict, H14/H11/H7/H17 placement, VRP H20/H5/H6/quota), depth
   0..1 (MAX_DEPTH=1). EF NON gate-checka H2/room (la null'a; le stanze sono B-ROOM), quindi la
   fedelta' non dipende dalle room (carry faithful comunque).
5. Assert solo gli invarianti strutturali (box=5, conflict-free start, promoted empty); PRINT il
   verdetto (se i 5 chiudono e' la domanda aperta).

Run: `./scripts/core/test.sh -c ExactFinisherRun4DiscriminatorTest`. BUILD SUCCESS, 1 test, 0
fail, 3.7s (di cui ~3s setup, EF sub-second).

## RISULTATO - ESITO (i): depth-0/1 completion ESISTE UNCAPPED

```
placed 5 of 5, residual 0, conflicts added 0, depth<=1
  G03 | T08 | Matematica          -> 2026-09-23@11  (Wed; T08 non-Q22, ok)
  G06  | T19   | Barberia            -> 2026-09-14@9   (Mon, Q22-compliant)
  G06  | T19   | Barberia            -> 2026-09-14@11  (Mon, Q22-compliant)
  G06  | T19   | Barberia            -> 2026-09-14@12  (Mon, Q22-compliant)
  G06  | T19   | Barberia            -> 2027-06-07@13  (Mon, Q22-compliant)
```

**Verdetto: il cap era l'UNICO ostacolo.** Un completamento 0-hard-additive a depth 0..1 esiste
per tutti e 5 i residui, contro la gate chain reale (VRP con quota reale E disponibilita' Q22
T19 Mon-Tue). Quindi tutti i 18 boxed originali sono depth-0/1-collocabili (13 provati da
run4-EF + 5 provati qui). **depth-2 NON e' necessaria** per il worst case noto (run4).

### Correzione di fedelta' (DoD /code-review skeptic, 2026-08-02)

La prima versione del test passava `includeQ22GoldenDerivedRules=false` a `loadFromExport`: BUG
materiale. run4 e' uno snapshot POST-Q22 (Q22 attivo dal 2026-07-24) e 4 dei 5 residui sono
T19/Barberia, la cui disponibilita' e' **solo** Q22 (VACATION Wed/Thu/Fri -> T19 Mon-Tue).
Con `false` quelle regole vengono droppate e il VRP di T19 si allarga da 2 a 5 giorni: la run
buggata piazzava T19 su Wed/Fri (09-16, 09-18), celle **escluse** nel run4 reale (prova: live
run4 colloca T19 62 Mon / 62 Tue / 0 Wed-Thu-Fri). Il verdetto "5/5" era quindi potenzialmente
un false pass su griglia over-permissive. Corretto a `true`: le nuove collocazioni sono tutte
Q22-compliant (T19 solo Mon) e il verdetto 5/5 **regge sulla griglia fedele**. Il bug era
load-bearing sulle CELLE (non sul count): conferma che EF trova celle Q22-legali anche col VRP
stretto. DoD = /simplify (4 angoli) + /code-review high (skeptic Opus, questo bug) + test.sh EXIT 0.

## PERCHE' QUESTO SOPRAVVIVE AI CAVEAT DI AC#1

AC#1 (Python) detto' YES depth-0 con margine 4x-75x MA con caveat espliciti: H2/H20/H5 non
verificate (lessons.txt non ha colonna room, il Python non applica il VRP Java). Questo discrimina-
tore chiude quei caveat meccanicamente:
- **H20/H5/H6/quota**: applicate dal VRP reale (`getAvailableTimeslots`), seed vero via
  `RealSeedFixture` + `seedMediumTierFacts`. Non proxy.
- **H2 (room)**: EF non la gate-checka (la null'a; rooms = B-ROOM). Quindi la fedelta' non dipende
  dalle room, e `loadFromExport` le carry comunque (faithful).
- **H1/H3/H7/H11/H14/H17**: gate chain reale condivisa con BoxRepair (package-private, non
  reimplementata).

Il residuo run4 e' la STESSA classe di AC#1 (T08/G03 + T19/G06/Barberia), solo piu'
piccolo (5 vs 16). AC#1 era direzione giusta; questo e' conferma meccanica sulla gate chain reale.

## IMPLICAZIONE (decisione)

La probe a budget piu' alto NON e' inutile (a differenza del ramo ii): e' lo step giusto. La
discriminazione esclude "depth-2 necessaria" per il worst case noto. Resta da confermare su un
seed fresco che budget 1M chiuda il residual che appare (non quello specifico di run4). Probe
LIVE knob 1000000 lanciata questa alba (`ab-ef-probe-1m-run1/`, in corso): se un seed cattivo
chiude 0-hard @ 1M, il verdetto e' "budget 1M chiude il worst case noto -> EF adottabile + flip
m-13 hard-0 deterministico". depth-2 resta design-deferred (AC#2 sez. 5) per un worst case CHE
NON SI E' MANIFESTATO.

## STATO

- Discriminatore: **ESITO (i), depth-0/1 ESISTE uncapped** (5/5 placed, 0 conflict, gate chain
  reale + quota reale). depth-2 non necessaria per run4.
- Probe LIVE knob 1M: lanciata (banner `maxCandidates=1000000` verificato), solve in corso.
- Test committed come `ExactFinisherRun4DiscriminatorTest` (diagnostica permanente, specchia
  BoxVrpFeasibilityDiscriminatorTest / BoxRepairCheckpointTest).
- Nessuna adozione, flag EF resta OFF. Verdetti dai raw.
