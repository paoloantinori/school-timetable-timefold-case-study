# TASK-446 gate-l5-m36, VERDICT (2026-07-28 21:51, run completato)

**ESITO: GATE FALLISCE sul CRITERIO ROOM.** Tutto il resto e' verde e migliore
del baseline; l'unico blocco e' `unpinned-real-room-null = 547` (criterio: ==0).
Nessuna degenerazione, nessun abort, run pulito in ~43min. laptop ha MISURATO;
nessun fix autonomo (budget esaurito). La prossima mossa e' di Fedora.

## Run (staged, profile production, flag 435 ON, M36 nel JAR)

- Start 21:08, fine 21:51 (~43min vs ~2h baseline). stagedStage CLEAR.
- Pipeline: A-CONSTRUCTION (5s) -> B-TIMESLOT #1/#2/#3 (box 65->4, demozioni
  selettive) -> RELEASE (7202 promoted rilasciati) -> HoleRepair (H17 holes
  24->0) -> AnchorRepair (late 54->18, newly-late 0) -> SurplusRebalance
  (deficit 147->135) -> B-ROOM (605s).
- **Pin autoritativi preservati 3090 -> 3090.** Validation newlyViolated=0.
- Score finale: **-4hard/-1480200medium/30536soft**.

## CRITERIO ROOM ( quello che chiude il buco del gate-l5 ritirato) -> FAIL

Query (colonne verificate su information_schema, tutte lowercase:
`lesson.{timeslot_id,room_id,pinned,course_id}`, `teacher.pseudo`):
```sql
SELECT COUNT(*) FROM lesson l
  JOIN course c ON l.course_id=c.id
  JOIN teacher t ON c.teacher_id=t.id
WHERE l.timeslot_id IS NOT NULL AND l.room_id IS NULL
  AND l.pinned=false AND t.pseudo=false
```
**Risultato: 547. Criterio: 0. FAIL.**

Suddivisione del residuo (placed, room=null):
- 1993 pseudo (Stage) -> esenti per filtro, by design.
- 866 pinned -> esenti per filtro, by design.
- **547 real-unpinned -> NON esenti, sono il target del criterio.**

M36 li penalizza regolarmente: `M36 = 0hard/-547000medium/547 match` (547 x 1000).
Quindi M36 e' ATTIVA e non arbitrata, ma non ha *forzato* l'assegnazione della
stanza: 547 lezioni restano room-null a fine B-ROOM.

### Diagnostica (perche' 547) -> NON e' scarsita' di capacita'

- **Picco domanda = 14 lezioni/timeslot, picco stanze usate = 13, stanze totali
  18 (16 a capacita' > 0).** Nessuna scarsita' generale: al timeslot piu' carico
  competono 14 lezioni con >=5 stanze libere.
- I 547 si splitpano **291 non-lab (aula) + 256 lab**.
- **291/291 non-lab (100%) hanno una stanza libera e usabile al proprio
  timeslot** (subquery EXISTS su room.capacity>0 non occupata). Le aule non
  hanno vincolo di tag (H-M2 vincola solo i lab), quindi sono assegnabili con
  zero conflitto hard possibile, eppure restano null.
- I 256 lab sono candidati a GENUINA scarsita' di tag sotto H-M2 (da confermare
  a livello di tag, ma plausibile).

**Lettura:** la meta' non-lab e' un gap di efficacia di M36, non scarsita'. M36
(medium 1000) non sta guidando il room-CH / LS-room a colmare i null: il penale
medium non viene ottimizzato contro durante l'assegnazione delle stanze.
**Alzare il peso di M36 da solo potrebbe NON chiudere i 291 non-lab** se il
pricer di room non lo vede. Le opzioni architetturali (decisione di Fedora):
(a) cablare M36 nel pricer/move di room-assignment (come M20 in m20DayCost), o
(b) un pass deterministico di room-fill (sibling di HoleRepair), o
(c) LS-room piu' lunga, o (d) rivedere il meccanismo. La meta' lab (256) va
verificata contro i tag-room prima di giudicarla infeasibile.

## Room-KPI -> OK (genuini, non silenti sul null)

- **H2 / H16b / H-M2 = 0 (hard)** con **9084 stanze assegnate** (non la
  silent-null trap del gate-l5 ritirato: qui le stanze ci sono davvero, quindi i
  vincoli room sono valutati davvero).
- M3 (capacita'): -392 medium / 392 match (annotato).
- S11 (affinita' stanza-gruppo): -4200 soft / 395 match (annotato).

## KPI standard vs baseline final-confirm (-41) e golden -> TUTTI VERDI

| metrica | baseline -41 | M36 run | soglia | golden |
|---|---|---|---|---|
| hard | -41 | **-4** | ~0 (box~4 ok) | - |
| H0 / box | ~41 | **4** | basso | - |
| full-day | 77.2% | **80.9%** | >=78 | 85.4% |
| day-start | 99.3% | **99.2%** | >=99 | ~99.3-100% |
| ricorrenza unpinned | 72.7% | **74.9%** | >=72 | - |
| buchi interni | 0 | **0** | 0 | - |
| burst >2x | ~golden | 34.4% (burst 2.06) | ~golden | 41.6% |
| runtime | ~2h (stuck) | **~43min** | ~30min | - |

La fuoriuscita del -41 sistemico (H0 box 41 -> 4) e' confermata e la H16b
room-paralysis del baseline e' dissolta (room-hard 0 genuino). Il run e'
veloce e i KPI tengono o migliorano. **L'unico blocco e' il criterio room.**

## DoD code-review (simplify + code-review high), finding deferiti a Fedora/backlog

DoD eseguita sul diff M36 (commit 6dfe8b1b + trim commento). `test.sh` EXIT=0
(BUILD SUCCESS, 2:22). Nessun fix autonomo (mandato: laptop misura, adozione a
Fedora); i finding confermati sono adoption-dependent e deferiti.

/simplify (4 agent quality-only):
- APPLICATO: trim del javadoc M36 (rimosso ref stale "H27 abort" e i numeri di
  run che marciscono 12259/12490 e -9404hard; sostituiti con pointer al
  diagnostic dir gate-l5-h27-abort/). Comment-only, compile-check verde.
- SKIPPATI (pre-existing, shared-infra, blast radius ampio, no drive-by
  refactor): filtro teacher/pseudo duplicato (4 copie H4/M28/M36/SurplusRebalance);
  timeslot!=null inline vs helper timeslottedLessons() (pattern file-wide);
  peso 1000 magic number senza knob; BAVET node-sharing con REAL_TEACHER.

/code-review high (4 agent correctness + conventions):
- Core retier CORRETTO (agent removed-behavior, verifica profonda incl. test
  run e bytecode Timefold): nessun NPE, score-type HardMediumSoftScore match,
  H0-arbitraggio sound. H2/H16b/H-M2 restano null-safe post-retier (forEach
  esclude room-null via hasNoNullVariables comunque allowsUnassigned; H16b/H-M2
  hanno filtro null esplicito). `penalizesBy(1)` e' la convenzione match-count
  del file (M12 e H2-ofMedium la usano uguale), NON un magnitude bug. Conventions
  CLAUDE.md conformi (formato ID, nessun colon, M36 e' nuovo ID lecito).
- 2 finding CONFERMATI (consumer regression da hard a medium), DEFERITI:
  1. `useLessonViolations.ts` filtra `level==='HARD'` solo (riga 64-77): i
     room-null M36 (ora medium) perdono highlight/tooltip/conto nella vista
     per-cell timeline (restano visibili nel panel Analysis medium breakdown).
     Pre-retier (H27 hard) erano evidenziati.
  2. `/feasibility` (`ScheduleResource` riga 250-277): room-null costava
     ONE_HARD (gate hard-infeasible); ora -1000 medium nel bucket M20/M22, fino
     a ~50 room-null passano il badge verde "operationally feasible"
     (`DEFAULT_MAX_TOTAL_GAP_MINUTES=50000`). La detail map li elenca, ma
     l'headline passa a "feasible".
- Altri DEFERITI (model/behavior/test-altitude, decisione Fedora):
  - `!l.pinned` vs `!isAuthoritativePin(l)` (TASK-419): divergenza dai fratelli
    M20/M22; salto di score medio `1000 * |promoted room-null|` alla
    smarcatura/release.
  - `penalizesBy(1)` non cattura un revert di tier (rinforzo opzionale a
    `.penalizes(HardMediumSoftScore.ofMedium(1000))`).
  - [altitude] M36 scored-constraint e' troppo shallow per garantire room a
    tutti (291 non-lab assegnabili a stanza libera restano null, vedi
    Diagnostica): servirebbe un pass deterministico RoomFill (sibling di
    HoleRepair) o cablare M36 nel pricer di room.
  - [altitude] nessun knob `planner.m36.weight` (M32/M34/M35 lo hanno).
  - [altitude] nessun test snapshot/checkpoint ciclo-level per room-null==0.

## Conclusione e confine di mandato

M36 (retier di H27) ha RISOLTO la degenerazione che aveva bocciato H27:
tier hard intatto, H0=4 (non 9404), orario non svuotato, run in ~43min. Come
"logicamente hard tenuto medium" fa esattamente il suo lavoro di non
paralizzare. MA non realizza l'obiettivo aggiuntivo del gate (stanze ad ogni
reale non-pinnato): 547 restano room-null, di cui 291 assegnabili a stanza
libera, segno che il medium di M36 non guida il room-assignment.

laptop ha MISURATO soltanto. Nessuna adozione, nessun cambio config/default, nessun
terzo fix autonomo (budget esaurito). La decisione (peso M36, pricer di room,
pass deterministico, o accettare il residuo) spetta a Fedora/maintainer sui numeri.

## Cattura

`./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/task446-results/gate-l5-m36`
artefatti: analysis.json, score.txt, KPI (fill/daystart/resemblance/cadence/
holes/moda-knot/sharpness), confronto-gate-l5-m36.html, export.json.gz (443 KiB,
vero), lessons.txt, phases.json.
