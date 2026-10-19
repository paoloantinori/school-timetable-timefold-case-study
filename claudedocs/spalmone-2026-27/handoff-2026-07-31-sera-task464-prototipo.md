# HANDOFF sera 31/07 (zai, ~16:05) - TASK-464 PROTOTIPO COMPLETO + DoD, flag OFF

HEAD `feature/cartesian-block-entity`. Riprende da `handoff-2026-07-31-sera.md` (e6469fef).
Questa sessione: implementato TASK-464 (prototipo co-presenza ospite, flag DEFAULT OFF), superato
il checkpoint stanze, eseguita DoD piena. Nessuna adozione (flag resta OFF, decisione maintainer).

## FATTO questa sessione
- **TASK-466(b) no-op + tabella STALE** (`4b662ff6`, pushato). Diff incarichi-vs-golden a 0
  divergenze reali => normalizzazione seed vacua sul lato incarichi. Tabella discrepancy in
  `.claude/rules/datagenerator-protection.md` aggiornata (T28/G09 = RESOLVED). Doc referente +
  task annotati. (TASK-466a `bd91715b` e TASK-465 `ebaa1b75` gia' chiusi nella sessione precedente.)
- **TASK-464 prototipo co-presenza ospite IMPLEMENTATO + DoD** (codice + test + docs, commit a
  seguire). 7 punti del design file-per-file + 1 correzione code-review. Flag
  `planner.co-presenza.enabled` DEFAULT OFF (opt-in, LIVE per-build come M-LF/M-TC).

### TASK-464: cosa e' nel codice
1. `SchoolSchedule.java`: `isCoPresenceEnabled()` + statica `goldenCoPresenceDepthStatic`
   (cella `tag|weekday|slotStart` -> depth) + `coPresenceCell`/`goldenCoPresenceDepth`/
   `isCoPresenceCell` + reset in `clearStaticState`.
2. `ConstrainedTeacherAllocator.java`: `loadGoldenCoPresenceDepth(subjectToTag)` (mirror
   `loadGoldenSlot0Anchors`: single-pass golden, skip closed, conta gruppi distinti per cella) +
   helper `buildSubjectToSingleTagMap(Iterable<Course>)` (sorgente autorevole subject->tag,
   condivisa production+harness, finding simplify).
3. `ScheduleService.refreshSolverStatics`: pubblica depth map + banner (pre-solve, come M32).
4. `BaseConstraintProvider.roomConflict`: esenzione pairwise `.filter((a,b) -> !(coPresenceEnabled
   && isCoPresenceGuestPair(a,b)))`; `coPresenceEnabled` catturato a build-time, non per-pair
   (finding efficiency). OFF -> `!(false&&..)` = true -> H2 byte-identico.
5. `SchoolScheduleConstraintProvider.coPresenceCap`: clone M-TC, HARD weight 1 x excess,
   `groupBy(tag, startDateTime, count).filter(depth>=2 && count>depth).penalizeHard(count-depth)`.
6. Test: `CoPresenceConstraintTest` (8: byte-identico OFF + esenzione ON + guardie AC#4 + cap) +
   `CoPresenceDepthLoaderTest` (2: replica spike, exact pin Thu 08:00 acc depth=5, over-count guard).
7. `RoomPhaseSnapshotTest`: env-bridge `PLANNER_TEST_CO_PRESENCE` (pubblica depth map, mirror
   tag-capacity bridge).

### CORREZIONE CODE-REVIEW (importante, cambia il design)
Il design diceva "H-COPRESENCE in ROOM_TIER_HARD_FAMILIES, fire in B-ROOM". **SBAGLIATO**
(2 verificatori Opus convergenti): H-COPRESENCE non reference mai `l.room` (groupa su
`(tag, startDateTime)`), quindi e' TIMESLOT-tier. Inserito in ROOM_TIER_HARD_FAMILIES accecava
il gate staged + assertImproveOrNoop dei repair (una cella over-depth leggeva "clean", persisteva
in B-ROOM dove le stanze non risolvono una co-locazione di timeslot). **FIX: NON e' in
ROOM_TIER_HARD_FAMILIES** -> `timeslotTierHardScore` lo conta e il ciclo lo risolve in B-TIMESLOT
(desiderabile: ferma il CH dal creare stack room-impossibili). Commenti + CLAUDE.md + design doc
aggiornati con la correzione.

## MISURE
- **Checkpoint stanze (RoomPhaseSnapshotTest export-mode, baseline-mwd1, slice 5 gruppi acc)**:
  flag ON -> LAB room-null **92 -> 0** (92/92 ospite-legale), hard **-92 -> 0**, medium
  -825805 -> -825678, cap non over-fira (hard=0). OFF baseline replica spike (92->92, hard -92).
  Artifact: `task464-copresenza-checkpoint-2026-07-31.md`.
- **DoD piena**: /simplify (3 reviewer: estratto buildSubjectToSingleTagMap, catturato enabled
  build-time, tighten loader test); /code-review high (2 Opus: 1 bug confermato corretto = la
  classificazione tier; resto deferred al full-solve A/B); **test.sh EXIT 0** su codice finale
  (912 test, 0 fail, 16 skipped env-gated).

## PROSSIMO PASSO = full-solve A/B (scala laptop, NON in questa sessione)
Il checkpoint e' lower bound (fissa i timeslot baseline, solo phase-5 LS, puo' solo aggiungere
beneficio). Full-solve A/B n>=2 (flag OFF vs ON, `capture-run.sh`) e' la conferma su traiettoria
intera (lezione E3: n=1 inganna). Da fare come laptop overnight. Comando checkpoint (per riprodurre):
```
PLANNER_TEST_ROOM_PHASE=1 \
PLANNER_TEST_ROOM_PHASE_EXPORT=claudedocs/spalmone-2026-27/t19-2026-07-31/baseline-mwd1/export.json.gz \
PLANNER_TEST_ROOM_PHASE_GROUPS="G02,G06,G10,G12,G14" \
PLANNER_TEST_ROOM_PHASE_BUDGET=60 \
PLANNER_TEST_CO_PRESENCE=1 \   # omesso per baseline OFF
./scripts/core/test.sh -c RoomPhaseSnapshotTest
```
Findings DEFERRED al full-solve A/B: (a) assumption mono-room acconciatura (cap conta per-tag
ignorando stanza fisica; speculare a M-TC, ok finche' acc e' mono-room); (b) peso del cap (hard 1)
vs H1/H3 (1000) nella fase timeslot: misurare se il solver lo onora.

## Stato ambiente
- HEAD sara' pushato a fine sessione. App: era UP su production col finale E3 relaunch. Per run
  nuovi: stop+delete production, restart. laptop post-reboot: **`podman machine start`** prima di
  ogni container; `prod-start-and-wait` con **timeout 300000** sul Bash tool.
- `claudodocs/` (typo dir, non mia) untracked: non committarla.
- solverConfig canonico (tabu7). Default omogenei: first-hour-slot ON + constrained-teacher ON.
- H-COPRESENCE flag OFF in produzione (byte-identico verificato su 912 test).

## Regole standing (invariate)
Nessuna adozione (tutto 2026/27 sperimentale resta non-adozione salvo maintainer). commit+push a ogni
chiusura. verdetti 3 righe dai raw. QUADRO da aggiornare (sezione TASK-464 prototipo + 466b no-op).
handoff+/clear a ~450k.

## Riferimenti rapidi
- `task464-copresenza-ospite-design-2026-07-31.md` (design + correzione code-review inline)
- `task464-copresenza-checkpoint-2026-07-31.md` (misure checkpoint)
- `spike-room-null-copresenza-2026-07-31/VERDICT.md` (gate + clearance 92)
- `task466-incarichi-vs-golden-diff-2026-07-31.md` + `referente-notifica-divergenze-input-vs-golden-2026-07-31.md`
- CLAUDE.md sezione "Canonical Constraint IDs" -> riga H-COPRESENCE
