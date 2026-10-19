# HANDOFF 2026-08-01 sera2 (zai) - CURA HARD-0 RATIFICATA LIVE + RUN4 PARITA' PARZIALE (hard 0, full-day 87,9%)

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `04900742` (cura pushata) +
> doc run4 locali (VERDICT/QUADRO/backlog, commit doc a seguire). La cura hard-0 e' IMPLEMENTATA,
> ratificata LIVE (run4 hard 0) e pushata. Default solver: golden; source=input opt-in. Verdetti
> dai raw. Regole standing invariate (background shell NON risveglia: presidiare i poll a finestre).

## COSA E' STATO FATTO (mandato 4 punti CHIUSI)

1. **CURA HARD-0 IMPLEMENTATA** (commit `04900742`, opt-in `source=input`, default golden INVARIATO).
   `buildInputDerivedProfile` era stage-blind + alpha-front-load -> G10 finiva sui suoi Thursday di
   stage (Mar 4-Apri 22, off-grid) -> 3 ore senza cella (run3 hard -6). Cura:
   - **stage-aware**: nuovo param `stageDatesByGroup` (groupName->Set<LocalDate> da
     `DailyActivityQuota.stageHours>0` via `isStageDate`, stessa fonte INPUT di M-CPS, MAI golden);
     il profilo salta i giorni di stage del gruppo. Segnale `stageHours` NON `aulaHours`: per docente
     LAB (T01) aulaHours=0 conflitta stage-day con lab-day (G10 ha 9 Thursday lab aula=0 lab>0,
     verificato). Production caller `HybridPhase1aService.indexStageDatesByGroup(groupNameById)`.
   - **interleaved largest-remaining**: sostituisce il greedy alpha shared-cursor. G10 ora ottiene
     Thursday non-stage sparsi Sep-Giu (come il golden) invece della coda Mar-Apr.
   - Totals invariati (231h), cap h7Cap+openSlots preservato, deterministico.

2. **DoD PIENA** sul diff: /simplify 4 agent (aulaHours-fold RESPINTO con evidenza 9 lab days;
   applicati isStageDate predicate, passa groupNameById, drop byGroup vestigiale, capture
   bestOpenSlots/bestRemaining; note javadoc su density-proxy + closed-day semantics); /code-review
   high skeptic Opus 7/7 CONFIRMED 0 bug (totals, no-stage, determinism, no-regression-golden,
   lab-day, wiring, disjointness+H7); test.sh **948/0/0/16 EXIT 0**; test RED->GREEN (G10 7 stage
   date pre-cura -> 0 post-cura). Nessun frontend, nessun default toccato.

3. **RUN4 DI PARITA'** (protocollo run3 identico, fresh reseed): **HARD 0 CHIUSO, full-day 87,9% =
   PARITA' PARZIALE**. Score `0hard/-1018024medium/8359soft` (run3 era -6). 0 unassigned nel DB, 0
   hard matches. G10 pinnato su 9 Thursday non-stage (match repro Python). B-TIMESLOT parte meglio
   (-24/box0 vs run3 -30/box3). AnchorRepair REVERTED (H28) non abortisce. full-day 87,9% (+2,7pp su
   run3) ma SOTTO 89 (-2,1pp) e sotto golden 90,2% (-2,3pp). Doc:
   `task452-results/verify-source-input-run4/VERDICT-2026-08-01.md`. **Flip m-13 NON ancora.**
   Capture raw: `task452-results/verify-source-input-run4/`.

4. **DOCS AGGIORNATI**: TASK-452 #7, TASK-476 #4, QUADRO (sezione SERATA 01/08), questo handoff.

## VERDETTO RUN4 (3 righe dai raw)
1. **HARD 0 CONFIRMED** (score 0hard, 0 unassigned DB, 0 constraint con negativeHard): il bloccante
   hard-0 del gate m-13 e' RIMOSSO, la cura ha tenuto.
2. **FULL-DAY 87,9% = PARITA' PARZIALE**: +2,7pp su run3 (85,2%) ma sotto soglia 89 (-2,1pp) e sotto
   golden 90,2% (-2,3pp). Flip m-13 NON ancora.
3. **GAP RESIDUO NON e' della cura**: deficit full-day concentrato su FC-venerdi (G04 57%,
   G08 65%, 22 deep -2h ven) = TASK-476 T1 SCHOOL-GATED su T05-venerdi (maintainer: skip run, flagga
   a referente Q22). Prossima leva per il gate pieno = T1 (school-gated) o medium fill, non il profilo.

## STATO RUN / APP / INFRA
- App UP su profilo `production` via `java -jar` con `-Dplanner.pre-assign.constrained-teacher.source=input`
  (PLANNER_JAVA_OPTS, non MAVEN_OPTS). La soluzione in DB e' il run4 hard-0.
- **Infra GOTCHA (importante per i prossimi run)**: il volume `production` era **PG16 incompatibile**
  con image **PG17** (postgres FATAL "database files are incompatible with server"). Cancellato e
  reseedato fresco PG17: il seed e' identico (DataGenerator deterministico), ma per un run nuovo su
  production NON serve piu' il delete (il volume ora e' PG17). `dev` era gia' PG17. Se un futuro
  `planner.sh prod production` fallisce con "PostgreSQL did not become ready within 30s", verificare
  `podman inspect planner-pg` per il FATAL PG16/17.
- Container `planner-pg` nominato cosi' ma `podman-tools.sh logs <profile>` cerca
  `planner-postgres-<profile>` (mismatch); per i log usare `podman inspect` o il log path diretto.

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **Gate m-13 pieno (full-day>=89)**: la cura ha chiuso hard-0; restano 2,1pp di full-day. Leva =
   TASK-476 TG04-venerdi (school-gated, domanda referente Q22 "T05 venerdi vincolo reale o
   preferenza?") OPPURE tuning medium fill. NON toccare il profilo constrained-teacher (e' a posto).
2. **TASK-477 G14** (R10, referente Q19): implementare rifiniture (a) escludere coppie G14
   dall'esenzione H2 co-presenza 464, (b) steering stanza G14->R10. Dopo decisione maintainer + A/B.
3. **TASK-475** (doppio R16 T20): attendere risposta referente (A1/A1+A2/A3).
4. **Q29 T05-venerdi**: attendere risposta referente (vincolo reale o preferenza?) - e' il gate del T1.
5. **Decisione flip m-13**: resta di maintainer. hard-0 chiuso, trade residuo = full-day 87,9% vs golden
   90,2%. Dichiarazione solo, nessuna adozione.

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/verify-source-input-run4/VERDICT-2026-08-01.md` (run4 raw + verdetto)
  + `task452-results/hard0-diagnosis-g10-t01-stage-blind-2026-08-01.md` (catena causale).
- Cura: commit `04900742` + TASK-452 #7. DoD: skeptic CONFIRMED 7/7, test.sh 948/0/0/16.
- QUADRO sezione SERATA 01/08.
- M-CPS: commit `abbd7f91` + TASK-469 (chiuso). G14: TASK-477.
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS, non MAVEN_OPTS) + rebuild prima di run misurati (lezione JAR-stale), handoff+/
  clear a ~450k, **presidiare i poll a finestre (background shell NON risveglia)**, DoD obbligatoria
  su codice.
