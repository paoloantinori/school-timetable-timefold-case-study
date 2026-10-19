# TASK-446: HANDOFF a sessione fresca (M36 retier, ri-gate L5 da completare)

**Data:** 2026-07-28 (sera). **Stato:** M36 (retier di H27) implementato, L0 verde,
non-degenerazione verificata. **UN RUN STAGED E' IN CORSO** sul profile production
(flag 435 ON, M36 nel JAR). La sessione fresca deve completare il ri-gate L5.

## Contesto (leggi prima di agire)

- **Commit chain (tutto su file, git pull aggiornato):** censimento `9d8182f8`, impl
  Alt-B `cc174fcf`, harness `383adc90`, checkpoint `7ccc5820`, simplify `bdfc6922`,
  code-review `455d12eb`, DoD `2117ae4b`, smoke L4 `95f1f8e0`, gate L5 (RITIRATO)
  `354a214f`, H27 (abort) `8bbc09dd`, **M36 retier `6dfe8b1b`** (HEAD).
- **Mandato originale:** in `backlog/tasks/task-446 - ....md` (note sections) + questo
  file. gate-l5 VERDICT RITIRATO (room-null trap). gate-l5-h27-abort/ = diagnostic della
  beffa H27 (9404 unassigned).
- **Decisione fix Fedora:** retier H27 (hard, ONE_HARD) in **M36 "Assigned lesson must
  have a room"** ofMedium(1000), STESSO filtro (timeslot!=null && room==null && !pinned
  && !pseudo), pattern "logicamente hard tenuto medium" famiglia M20/M22. Non arbitra con
  H0 (timeslot e' allowsUnassigned=true): timeslot-null resta dominato dal tier hard, la LS
  non svuota l'orario.

## Cosa e' GIA' stato verificato (non rifarlo)

- **M36 L0 unit test:** 4 ConstraintVerifier verdi (penalize unpinned-real-room-null;
  silence pinned/pseudo/boxed). `SchoolScheduleConstraintProviderTest` metodi `m36_*`.
- **M36 non-degenerazione (smoke ~3min, poll iniziale):** score mid-B-TIMESLOT-1 **-35hard**
  (scende, NON -9404 come H27), **12429/12494 placed** (NON 3090), **H0 = 65 boxed stabile**
  (NON 9404), M36 = -2133000 medium (offset, tier medium non paralizzante). Conferma: il tier
  hard vieta lo svuotamento, M36 non arbitra con H0.
- **JAR:** ricostruito con M36 (marker `M36 - Assigned lesson must have a room` nel fast-jar).

## RUN IN CORSO (profile production, flag 435 ON, M36)

- Start: ~21:08. B-TIMESLOT-1 in corso al passaggio del testimone.
- caffeinate attivo (2h). heap 2g. budget pieni di default.
- **Comportamento atteso (come il gate L5 originale ma con stanze assegnate a fine):**
  B-TIMESLOT-1 ~20min timeslot-hard verso ~0 (3 iter), stadi det conflict-free, **B-ROOM
  assegna le stanze** (M36 ofMedium(1000) lo forza), score finale hard ~0 (H0 box ~4) + medium.

## COSA DEVE FARE LA SESSIONE FRESCA (ri-gate L5)

1. **`git pull`** (porta eventuali fix Fedora; la riga tabella CLAUDE.md M36 la aggiorna
   Fedora/maintainer a ratifica).
2. **Controlla lo stato del run:** `./scripts/api/api.sh status`.
   - Se `solverStatus: NOT_SOLVING` e stagedStage vuoto: run completato, vai al capture.
   - Se ancora in stagedStage B-TIMESLOT-N: monitora (poll ogni ~10min: `api.sh status` +
     `api.sh score` + `grep "stage B-TIMESLOT" /tmp/planner-prod.log`) fino a NOT_SOLVING.
     Criteri di ABORT (intervieni se): H0 cresce oltre ~100 (degenerazione, non deve
     succedere con M36 ma verifica); boxed instabile; crash/NPE; residuo room-hard
     H2/H16b/H-M2 > 0 a fine B-ROOM.
3. **A run completato, capture canonica:**
   `./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/task446-results/gate-l5-m36`
   (capture-run.sh fix export 5ece9868, export.json.gz VERO).
4. **VERDICT gate-l5-m36/VERDICT.md** con confronto vs baseline final-confirm-435 (-41) +
   golden + **CRITERIO ROOM (quello che ha beccato il buco):** `unpinned-real-room-null == 0`
   a fine ciclo. Query DB:
   `SELECT COUNT(*) FROM lesson l JOIN course c ON l.course_id=c.id JOIN teacher t ON c.teacher_id=t.id WHERE l.timeslot_id IS NOT NULL AND l.room_id IS NULL AND l.pinned=false AND t.pseudo=false`
   deve essere 0. Room-KPI: H2/H16b/H-M2 devono restare **0 CON le stanze assegnate** (non
   silenti su null); M3/S11 annotati.
5. **DoD:** /simplify + /code-review high sul diff M36 + `./scripts/core/test.sh` EXIT=0
   (suite completa con M36).
6. **Commit + push** (gate-l5-m36/ + VERDICT + nota backlog). laptop MISURA soltanto; ADOZIONE
   (docs CLAUDE.md riga M36, chiusura task) a Fedora/maintainer sui numeri.

## Se il run e' morto / profile sporco
- `./planner.sh kill-app && ./planner.sh stop && yes | ./planner.sh delete production`
- `PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.enabled=true" ./scripts/core/prod-start-and-wait.sh`
- `./scripts/api/import-calendario.sh && ./scripts/api/api.sh pre-assign` (poll status COMPLETE)
- `./scripts/api/api.sh solve-staged` e monitora come sopra.

## Criteri di SUCCESSO del gate (da VERDICT)
hard ~0 (H0 box ~4 genuine-unplaceable accettabile, come smoke L4); KPI: full-day >= 78,
day-start >= 99, ricorrenza unpinned >= 72, buchi interni 0, burst ~golden; **room-null
unpinned reali == 0** (NUOVO criterio room); tempo-a-hard-0 annotato (~30min vs ~2h baseline).
