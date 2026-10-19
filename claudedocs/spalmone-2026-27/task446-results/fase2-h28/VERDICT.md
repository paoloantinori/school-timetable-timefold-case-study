# FASE 2 - H28 teacher no-return (hard), VERDICT (2026-07-28 notte)

**ESITO: PASS.** H28 cristallizza l'invariante golden "no teacher return" come guard hard a
delta zero (golden + run gate-l5-m36 = 0 ritorni). laptop implementa/misura, NON adotta
(riga CLAUDE.md H28, ratifica = maintainer domattina).

## Cosa cambia

- `SchoolScheduleConstraintProvider.teacherNoReturnWithinGroupSubjectDay` (nuovo, id `H28 -
  No teacher return within group subject day`, tier HARD). Dentro (gruppo, materia, data):
  penalizza la coppia di lezioni STESSO docente tra le quali esiste una lezione di docente
  DIVERSO in slot strettamente intermedio (A-B-A). `timeslottedLessons` (pattern TASK-377,
  vede le room-null delle fasi timeslot) self-join per coppie ordinate stesso-docente
  (filtering: !id.equals && slotIndexOf(a)<slotIndexOf(b) && sameTeacher), ifExists una c
  strettamente tra a e b con docente diverso (TriJoiner filtering). Niente esenzione
  pinned/pseudo (l'invariante vale sull'orario intero). Registrato (55 -> 56 vincoli).
- 4 test L0 (`h28_*`): penalize A-B-A; silence single-handoff, mono-teacher day, two
  contiguous blocks (il pattern golden A->B). Room null nei fixture (esercita il path
  forEachIncludingUnassigned).
- `GoldenSolutionRegressionTest`: `assertMatchCount(snapshot,"H28",0)` (lock dell'invariante).

## Checkpoint

- **L0** `SchoolScheduleConstraintProviderTest`: 308 test (304 + 4 H28), 0 fallimenti.
- **Golden guard** `GoldenSolutionRegressionTest`: verde (golden 0 ritorni).
- **0-match sul dump gate-l5-m36** (Python che mirrorra H28: coppia stesso-docente con
  docente diverso strettamente tra): **0 return pair su 6846 (gruppo,materia,data) giorni**.
  Conferma il run gia' a 0 (verifica Fedora) -> guard a delta zero.

## DoD review (simplify + code-review high)

- **Correttezza CLEAN**: penalizza ESATTAMENTE A-B-A (bound "strettamente tra" esclusivi,
  no off-by-one), niente double-count (ifExists e' booleano: una coppia = ONE_HARD anche con
  piu' testimoni), nessun NPE (tutti gli stream filtrano timeslot!=null; slotIndexOf
  null-guarded), perf OK (bucket (gruppo,materia,data) <0.2 lezioni in media), correttamente
  NON in ROOM_TIER_HARD_FAMILIES (H28 e' timeslot-tier hard, le fasi timeslot possono
  risolverlo muovendo slot), tier HARD appropriato (golden-first, 0 violazioni).
- **Qualita' Finding 1 APPLICATO**: i 3 stream inline `forEachIncludingUnassigned+filter`
  routati sullo shared helper `timeslottedLessons` (BAVET node-sharing + consistenza con
  tutti gli altri vincoli timeslot). Self-join vs forEachUniquePair: self-join giustificato
  (forEachUniquePair cadrebbe nella trappola TASK-377, inerte nelle fasi timeslot).
- **BoxRepairCheckpointTest FIX (bloccante)**: il fixture val410-run2 (run pre-H28) ha 14
  ritorni docente che ora contano hard; l'asserzione "final hard = solo H17" (-1) diventava
  -15. Fix robusto: catturo lo start hard (con box) e asserisco `final == start + box` (il
  box si azzera, il repair non cambia altro hard). Non brittle a nuovi vincoli hard futuri.
- **Finding 3 DEFERITO (opzionale)**: test "strettamente-tra su gap" (c non adiacente) e
  `penalizesBy(2)` (giorno con 2 ritorni). Nice-to-have, non blocker; i 4 test coprono la
  matrice. Suite `test.sh`: re-run per l'EXIT=0 DoD dopo i fix.

## Confine di mandato

laptop MISURA/IMPLEMENTA, non adotta. Nessun default change, nessuna chiusura task, nessuna
ratifica. La riga CLAUDE.md "Canonical Constraint IDs" per H28 resta a maintainer domattina.
H28 e' complementare a S16 (premio same-teacher adiacente) e M15 (contiguita' same-subject).
