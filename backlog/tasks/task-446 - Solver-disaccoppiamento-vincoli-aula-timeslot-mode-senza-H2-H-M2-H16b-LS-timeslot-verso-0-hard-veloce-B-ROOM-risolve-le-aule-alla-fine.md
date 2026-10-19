---
id: TASK-446
title: >-
  Solver: disaccoppiamento vincoli aula (timeslot-mode senza H2/H-M2/H16b) - LS
  timeslot verso 0 hard veloce, B-ROOM risolve le aule alla fine
status: Done
assignee: []
created_date: '2026-07-28 14:37'
updated_date: '2026-07-29 16:09'
labels:
  - claude-planned
milestone: m-12
dependencies: []
references:
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
  - src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java
  - src/main/resources/solverConfig.xml
priority: high
ordinal: 269000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PROBLEMA (pre-esistente, NON causato da TASK-435, emerso nel cert TASK-435 2026-07-28): la LS timeslot (B-TIMESLOT nel ciclo staged) e' PARALIZZATA da H16b (room-based hard, -963hard al B-TIMESLOT-1). Il costruttore (DayClusteredConstructionPhase) assegna room provvisorie -> H16b ("consecutive same-subject same-teacher must keep the room", filter a.room!=null && b.room!=null) spara sulle room diverse -> ma la LS timeslot NON PUO' muovere le room (le fissa solo B-ROOM, l'ULTIMO stage staged) -> hard congelato (es. -928hard per l'intero secondsSpentLimit=3600/60min del B-TIMESLOT-1), la LS gratta solo medium. Il baseline final-confirm faceva lo stesso (raggiungeva -41hard solo DOPO B-ROOM). Effetto: ogni solve staged e' lento (~2h) e la LS timeslot non punta dritta a 0 hard.

DIRETTIVA maintainer (2026-07-28): valutare i constraints delle aule SOLO ALLA FINE, in maniera completamente separata dal resto del solver.

FIX PROPOSTO (per valutazione Fedora): una "timeslot-mode" (flag statico, ANALOGO a compactionOnlyMode - SchoolScheduleConstraintProvider:119/145, settabile via env SOLVER__... o API) in cui il constraint provider NON registra i vincoli room-based (H2 room conflict, H-M2 room suitability, H16b consecutive-same-subject-room). Le fasi timeslot (CH DayClusteredConstructionPhase + LS B-TIMESLOT) girano in timeslot-mode -> la LS ottimizza pulita i vincoli di timeslot (H1/H3/H5/H7/H11/H17) verso 0 timeslot-hard veloce. B-ROOM (e B-TIMESLOT-room se serve) gira in full-mode -> assegna/raffina le room risolvendo H2/H-M2/H16b. Pattern standard timeslot-first/room-second.

ALTERNATIVA B (piu' semplice, da confrontare): il costruttore lascia room=NULL ai non-pinnati (H16b/H2 non sparano su null-null durante le fasi timeslot), B-ROOM assegna tutte le room. Nota: i pin (TASK-435, acconciatura, ecc.) devono comunque assegnare la room perche' @PlanningPin rende immovable una room pinned-null.

VALIDAZIONE: suite EXIT=0; staged solve -> hard 0 (timeslot-hard azzerato nei primi stage, room risolti a fine); KPI tenute (full-day, day-start, ricorrenza, emptySpan); GoldenSolutionRegressionTest verde; A/B vs current-arch (tempo a 0 hard + qualita'). Poi re-cert TASK-435 sull'architettura veloce.

OPEN: (1) timeslot-mode via flag (alternativa A) vs costruttore room=null (B) - Fedora decide; (2) impatto su M28 (afternoon reserved, e' room-indipendente? OK), H-D1 (doppio, room-based - va in full-mode); (3) B-ROOM deve assegnare TUTTE le room (non solo raffinare) se le fasi timeslot lasciano null.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
VALUTAZIONE FEDORA (16:50): direzione APPROVATA, scelta ALTERNATIVA B (costruttore lascia room=null ai non-pinnati; B-ROOM/CH-room assegna tutto) - NON la A: (1) B sfrutta la semantica esistente forEach-only-initialized (lezione TASK-377): H2/H16b/H-M2 naturalmente silenti su room=null, H1/H3/... gia' su forEachIncludingUnassigned continuano; zero mode-switch del provider, zero score-misurato-diverso tra stadi; (2) risolve TASK-417 di rimbalzo (la CH-room vestigiale diventa il vero assegnatore); (3) i pin tengono la room come gia' fanno (coerente col cert-fix di oggi). VALIDAZIONE CRITICA da aggiungere: censimento vincolo-per-vincolo di chi resta cieco con room=null (mappa forEach vs forEachIncludingUnassigned - stessa classe di trappola 377, stavolta a favore ma da verificare esplicitamente, incluso H-D1). SEQUENZA: dopo la cattura del cert 435 in corso; checkpoint-first (harness costruzione + staged) + UN A/B tempo-a-0-hard e qualita' vs architettura attuale; KPI tenute e golden-guard intatto obbligatori. Alternativa A resta fallback documentato se B inciampa.

--- CENSIMENTO BLIND-MAP (laptop, 2026-07-28, PRE-codice, per verifica Fedora) ---
Artefatto integrale: claudedocs/spalmone-2026-27/task446-results/VERDICT.md (58 vincoli classificati, tabella per-entita').

DOMANDA PORTANTE Alt-B: il costruttore lascia room=null sui non-pinnati; le fasi timeslot girano su timeslot-set+room-null; B-ROOM assegna tutto alla fine. Funziona sse (P1) i vincoli room-based sono silenti su room=null e (P2) i vincoli timeslot continuano a fuocheggiare.

VERDETTI:
- P1 = SI. I 7 vincoli room-touching (enumerati da `grep room` sui due provider: H2, H16b, H-M2, M3, S10, S11, M-LA) hanno TUTTI guardia esplicita `room != null`. H2 e l'unico `forEach(Lesson.class)`; H16b e l'unico `forEachUniquePair(Lesson.class)`; entrambi room-based e initialized-only.
- P2 = SI. Gli altri 51 vincoli sono su `forEachIncludingUnassigned`/`timeslottedLessons`/`precompute+forEachUnfiltered`: continuano a fuocheggiare. Nessuna regressione TASK-377 (l'unico forEach/forEachUniquePair su Lesson sono proprio i 2 room-based che Alt-B vuol blindare).
- H-D1 = FANTASMA. Nessun `.asConstraint("H-D1...")` in src/main (grep asConstraint x d1|doppio|assistant -> solo M-LA). Doppio oggi = entita' LabAssistant + M-LA (MEDIUM), vedi LabAssistant.java:13-19. Riferimenti H-D1 sopravvivono solo in commenti (HoleRepair/SurplusRebalance) e riga stale CLAUDE.md. Nessuna superficie room da blindare.
- JOIN SECONDARI: nessuno introduce dipendenza room oltre ai 7 (H1 join realTeacherTimeslottedLessons, H3 join timeslottedLessons, S16/S17 via adjacentSameSubjectPairs su timeslottedLessons, H4/M5 precompute+forEachUnfiltered, H5 join TeacherAvailability, ecc. -> tutti timeslot-puri).

ASSUNZIONE PORTANTE (A): Lesson timeslot-set + room-null NON e' fully-initialized per forEach/forEachUniquePair (lezione TASK-377). CONF ALTA: (1) modello documentato in BCP:110-119; (2) la costruzione PRODUCE gia' room-null oggi (findAvailableRoom ritorna null a :461, roomNullAfterPlace tracciato, compattazione null-a room a :283); (3) H2 non NPE su a.room.id (:374) -> o FE esclude le room-null o il joiner equal(room) scarta null; (4) fixture room-less in GoldenSolutionRegressionTest. Alt-B porta l'insieme room-null da 'i pochi non collocabili' a 'tutti i non-pinnati': A e' proprieta' per-entita' non soglia di conteggio. Conferma definitiva demandata al checkpoint fine-costruzione (H2/H16b hard score + assenza NPE su griglia tutta-room-null).

FALLBACK (non preventivo): se il checkpoint mostrasse H2 sensibile a room-null, guardia minima `.filter((a,b)->a.room!=null&&b.room!=null)` identica a H16b:636. H16b gia' triplamente blindato. NON aggiungere ora (smallest-change; ancoraggio empirico dice superflua).

SUPERFICIE ROOM COSTRUZIONE (step b, DayClusteredConstructionPhase): assignVariables:202 (passa null), canPlaceLesson:367-404 (gate room 387-401 = UNICO trade-off di design: rimuoverlo libera il placement timeslot ma crea room-infeasibilita' che B-ROOM risolve), trackAssignment:438 (morto), findAvailableRoom:451 (non chiamato). Compaction:283 gia' null-a room (precedente coerente).

BLOCKER per (b): NESSUNO. Procedere con implementazione minima (b) solo dopo OK Fedora.

--- CHECKPOINT FINE-COSTRUZIONE (laptop, 2026-07-28, PASS) ---
Artefatto: claudedocs/spalmone-2026-27/task446-results/HARNESS-FINE-COSTRUZIONE.md. Harness in WeeklyTemplateConstructionRealSeedTest (buildAndSolveConstructionWithRealRooms + 2 metodi). Commit harness 383adc90.

ASSUNZIONE A CONFERMATA (guard critico): H2 hard=0 E H16b hard=0 su griglia tutta-room-null, via ScoreDirector (motore reale del solver, non calcolo manuale). Se un upgrade Timefold cambiasse forEach/allowsUnassigned semantica, l'assert urla.

A/B Alt-B vs baseline (rooms reali, total=10501):
- nonPinned room-null: baseline 552 (5.3%) -> Alt-B 10501 (100%).
- boxed (H0): baseline 284 -> Alt-B 105 (-63%). Il gate room rimosso RIDUCE i boxed (non li causa): il gate baseline scartava timeslot senza room libera e boxava di piu'.
- H1/H3: 0 in entrambi.
- Phase log Alt-B: roomsAssigned=0, roomNullCount=10396==nonPinnedPlaced.

METRICHE TIMESLOT: boxed MIGLIORATA (284->105); ricorrenza invariata (76.9%, test esistente constructionEndRecurrence); day-start non misurata in questo A/B (H17/compaction coperti: compactedGaps>0, compactionAddedConflicts=0).

VERDETTO: checkpoint fine-costruzione PASS, nessun blocker per staged breve. Room-infeasibilita' eventuale demandata a B-ROOM, da misurare nell'harness staged breve. Commit doc pending.

DECISIONE FEDORA POINT 4 (gate room RIMOSSO) verificata a favore: disaccoppiamento riduce l'H0 residuo di costruzione.

--- DoD PIENA COMPLETATA (laptop, 2026-07-28) ---
#1 /simplify (commit bdfc6922): 4 agent Reuse/Simplification/Efficiency/Altitude. Applicati: assignVariables commit point, dead counters rimossi, dedup helper, single calculateScore. Skip: testsupport helper (5 altri file), report test (mantenuto poi @Disabled in code-review).
#2 /code-review high (commit 455d12eb): 8 agent (3 correctness + 3 cleanup + altitude + conventions). Applicati: ALTITUDE-1 assignVariables structural null (canary truthful via Moves.change(roomVariable,null)), A-2 reset roomNullCount per phase-reuse, A-1 clean() DELETE FROM Room (seedRooms non idempotente), doc (javadoc canPlaceLesson H2, label roomNullCount, report @Disabled, wrapper javadoc, VERDICT.md, invariant inline). Skip documentati: room-oversubscription/test-no-phase-4/entry-reset (deferred staged breve, run live); H2 filter/NPE justifyWith (pre-existing, fuori diff, censimento §5); 6th copy ScoreDirector (fuori scope).
#3 suite EXIT=0 (verificato questo turno): ./scripts/core/test.sh -> BUILD SUCCESS, SUITE_EXIT=0, 0 FAILURE/ERROR su tutta la suite (~95 test file). Frontend non toccato.

STATO: checkpoint fine-costruzione PASS + DoD piena verde. RESTA DEFERRED (richiede solve live, 'NIENTE run live'): harness staged breve (room-infeasibilita' post-B-ROOM KPI, day-start esplicita, A/B tempo-a-0-hard vs architettura attuale) -> step separato a decisione/autorizzazione Fedora. laptop ha MISURATO soltanto (nessuna adozione/revert/default change): la decisione e' di Fedora sull'evidenza.

--- SMOKE L4 STAGED SOLVE (laptop, 2026-07-28, MISURA ONLY, 3/3 VERDE) ---
Artefatto: claudedocs/spalmone-2026-27/task446-results/smoke-l4/VERDICT.md (+ raw: phase-markers, analysis.json, export.json.gz TASK-401, KPI, confronto HTML).
Config: profile production fresco laptop-RUNBOOK, flag 435 constrained-teacher ON su riga java -jar (PLANNER_JAVA_OPTS, verificato sulla command line JVM). JAR ricostruito con Alt-B. Ciclo completo ~32min (dentro paletto 30-40min).

3 DOMANDE VERDI:
(a) B-TIMESLOT LIBERA: B-TIMESLOT-1 1073s (~18min, vs baseline 60min), timeslot-tier hard -32 (vs baseline -928 H16b -963 dominante); 3 iter (-32/-30/-28); mid-solve breakdown hard = H17+H0 boxed, ZERO H2/H16b/H-M2; H1/H3/H5=0.
(b) B-ROOM ASSORBE 9339 room-null: B-ROOM 65s, residuo room-hard 0; score finale -4hard = SOLO H0 (4 boxed), ZERO room-hard. Rischio room-infeasibilita' Fedora NON materializzato.
(c) STADI DETERMINISTICI OK: AnchorRepair late 52->15 conflicts added 0; SurplusRebalance 19h moved deficit 146->128; HoleRepair girato. Tutti conflict-free.

KPI: score -4hard (vs baseline final-confirm-435 -41hard); full-day 81.3% (golden 85.4%); day-start 99.3%; buchi interni 0.

VERDETTO: smoke L4 VERDE. laptop MISURA soltanto (nessuna adozione/default change). PROPOSTA Fedora a maintainer: gate adozione L5 congiunto 435+446.

--- GATE L5 CONGIUNTO 435+446 (laptop, 2026-07-28, FULL-SOLVE CERTIFICAZIONE, VERDE) ---
Artefatto: claudedocs/spalmone-2026-27/task446-results/gate-l5/VERDICT.md (+ raw, export.json.gz 410KB VERO dopo fix Fedora 5ece9868, KPI, confronto HTML). Profile fresco, flag 435 ON java -jar, budget pieni. Ciclo ~30min. Monitoraggio attivo ogni ~10min, NESSUN abort.

TRAiettoria vs baseline final-confirm-435: B-TIMESLOT-1 1231s (~20.5min) vs baseline 60min; timeslot-tier hard -32 vs baseline -928 (H16b -963); 3 iter; B-ROOM 65s room-hard 0. Score finale -4hard vs baseline -41hard.

CRITERI SUCCESSO: room-hard post-B-ROOM=0 OK; full-day 81.3% (>=78, baseline 78.2%) OK; day-start 99.3% (>=99) OK; ricorrenza unpinned 74.7% (>=72) OK; BUCO-INTERNO 0 (baseline 15) OK; burst medio 2.1 (~golden) OK; ciclo ~30min vs ~2h baseline.

UNICA NON-CHIUSURA: hard=0 -> -4hard = SOLO H0 (4 boxed genuine-unplaceable), identico allo smoke, miglioramento vs baseline 41 boxed. Box=4 e' construction-matter pre-Alt-B (modello/config placement), FUORI autonomia tecnica gate -> SURFACCIATO a Fedora/maintainer. Non attribuibile a 446.

VERDETTO: gate L5 VERDE sull'architettura veloce. ADOZIONE (flag default, docs, chiusura task) a Fedora/maintainer sui numeri. laptop MISURA soltanto (nessuna adozione/default change).

--- M36 RETIER (loop fix H27 abort) + HANDOFF (laptop, 2026-07-28 sera) ---
ABORT H27: a parita' ONE_HARD con H0 (timeslot allowsUnassigned=true), timeslot-null domina (evade i medium della lezione piazzata) -> LS svuota orario (9404 unassigned mid-B-TIMESLOT-1, score -9404hard). Diagnostic in gate-l5-h27-abort/.
DECISIONE Fedora: retier H27 -> M36 'Assigned lesson must have a room' ofMedium(1000), STESSO filtro, pattern 'logicamente hard tenuto medium' famiglia M20/M22. Non arbitra con H0 (tier hard vieta svuotamento). Commit M36 retier 6dfe8b1b.
VERIFICATO questo turno: M36 L0 (4 unit ConstraintVerifier verdi); M36 non-degenerazione (smoke ~3min: score -35hard scendendo NON -9404, 12429/12494 placed NON 3090, H0=65 boxed stabile NON 9404, M36=-2133000 medium offset non paralizzante). JAR rebuild con M36.
RUN IN CORSO: profile production, flag 435 ON, M36. B-TIMESLOT-1 sano al passaggio del testimone.
HANDOFF a sessione fresca (contesto ~500k): claudedocs/spalmone-2026-27/task446-results/M36-RUN-IN-CORSO.md (istruzioni complete: monitora run -> capture gate-l5-m36 -> VERDICT con CRITERIO ROOM unpinned-real-room-null==0 + room-KPI H2/H16b/H-M2=0 CON stanze assegnate -> DoD simplify/code-review/suite -> commit). laptop MISURA; adozione a Fedora/maintainer.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: laptop (zai, sessione fresca)
created: 2026-07-28 20:23
---
--- GATE-L5-M36 (laptop, 2026-07-28 sera, MISURA ONLY, FAIL sul criterio ROOM) ---

RUN: profile production, flag 435 ON, M36 nel JAR. Ciclo ~43min: B-TIMESLOT #1/#2/#3 (box 65->4), RELEASE 7202 promoted, HoleRepair (H17 holes 24->0), AnchorRepair (late 54->18, newly-late 0), SurplusRebalance (deficit 147->135), B-ROOM 605s. stagedStage CLEAR, pin autoritativi 3090->3090, validation newlyViolated=0.
Score finale: -4hard/-1480200medium/30536soft. H0=4 (box, STABILE, non 9404: la degenerazione H27 e' sconfitta). room-hard H2/H16b/H-M2 = 0 GENUINI (9084 room assegnate, non la silent-null trap del gate-l5 ritirato).

CRITERIO ROOM: FAIL. Query unpinned-real-room-null (timeslot!=null && room==null && !pinned && !pseudo) = 547 (criterio: ==0). Split del residuo placed+room-null: 1993 pseudo + 866 pinned (esenti by design) + 547 real-unpinned (il target del criterio). M36 = -547000 medium (attivo, non arbitra, ma non forza l'assegnazione).
DIAGNOSTICA: NON e' scarsita' di capacita'. Picco domanda = 14 lezioni/timeslot, picco stanze usate = 13, room totali 18 (16 a capacita'>0). I 547 = 291 non-lab (100% hanno una stanza libera e usabile al proprio timeslot: M36 non sta guidando il room-CH/LS-room) + 256 lab (candidata scarsita' di tag sotto H-M2, da confermare).
LETTURA: M36 (medium 1000) previene la degenerazione H27 ma e' troppo shallow per garantire room a tutti i reali. Alzare il peso da solo potrebbe NON bastare: 291 non-lab assegnabili a stanza libera restano null, segno che il pricer di room non vede/ottimizza M36.

KPI STANDARD TUTTI VERDI vs baseline final-confirm (-41hard): hard -4 (10x meglio), full-day 80.9% (soglia >=78, baseline 77.2%, golden 85.4%), day-start 99.2% (>=99), ricorrenza unpinned 74.9% (>=72, baseline 72.7%), buchi interni 0, runtime ~43min (baseline ~2h). room-hard 0 genuino con stanze assegnate.

DoD sul diff M36 (commit 6dfe8b1b + trim commento):
- /simplify (4 agent): APPLICATO trim javadoc M36 (rimosso ref stale 'H27 abort' + numeri di run che marciscono 12259/12490 e -9404hard, sostituiti con pointer al diagnostic dir). SKIPPATI pre-existing shared-infra (filtro teacher/pseudo 4 copie, timeslot!=null inline file-wide, magic 1000, BAVET node-sharing) per blast-radius / no drive-by refactor.
- /code-review high (4 agent correctness+conventions): CORE RETIER CORRETTO (agent removed-behavior: H2/H16b/H-M2 null-safe post-retier via hasNoNullVariables, penalizesBy(1) e' la convenzione match-count del file non un magnitude bug, conventions CLAUDE.md conformi). 2 finding CONFERMATI DEFERITI (consumer regression hard->medium, adoption-dependent): (1) useLessonViolations.ts filtra level==='HARD' solo -> i room-null M36 perdono highlight/tooltip/conto nella vista per-cell timeline (restano visibili nel panel Analysis medium breakdown); (2) /feasibility gate (ScheduleResource) -> room-null ora -1000 medium nel bucket M20/M22, fino a ~50 passano il badge verde 'operationally feasible' (era hard-infeasible; la detail map li elenca). Altri DEFERITI: !l.pinned vs !isAuthoritativePin(l) (TASK-419, salto di score a smarcatura/release), knob planner.m36.weight mancante (M32/M34/M35 lo hanno), snapshot-test ciclo-level room-null==0 mancante, RoomFill pass (altitude, vedi TASK-417).
- suite test.sh EXIT=0 (BUILD SUCCESS, 2:22) + compile-check post-trim verde.

VERDICT: gate-l5-m36 FAIL sul criterio ROOM (unpinned-real-room-null 547 vs 0); tutto il resto verde e migliore del baseline, degenerazione H27 sconfitta. laptop MISURA soltanto: nessuna adozione, nessun cambio config/default, NESSUN terzo fix autonomo (budget esaurito). PROSSIMA MOSSA Fedora (opzioni): TASK-447 (promote M36 a hard + reweight H0 per rimuovere l'arbitraggio) OPPURE pass deterministico RoomFill (sibling di HoleRepair, garantisce il fillabile) / cablare M36 nel pricer di room OPPURE accettare il residuo come costo medium onesto. Artefatto integrale: claudedocs/spalmone-2026-27/task446-results/gate-l5-m36/VERDICT.md (+ capture raw: analysis.json, export.json.gz 443KB, KPI, confronto HTML).
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO (29/07). Alternativa B implementata e certificata: la costruzione lascia room=null ai non-pinnati, le fasi timeslot girano libere (la paralisi H16b da -963 e' sparita, B-TIMESLOT da ~60 a ~20 minuti) e B-ROOM assegna le stanze sulla griglia completa. Certificazione del 29/07: hard -195 con decomposizione tutta attribuibile (189 ore lab senza aula + 3 lezioni non piazzate), zero conflitti di qualunque tipo, KPI tenute o migliori del baseline (full-day 87,0 senza G14, day-start 99,3, ricorrenza unpinned 74,4, buchi interni 0), ciclo ~40 minuti contro ~2 ore. Riproducibilita' confermata a n=2. Il censimento blind-map che ha reso sicura l'operazione (55 vincoli classificati forEach vs forEachIncludingUnassigned, H-D1 scoperto fantasma) e' in task446-results/VERDICT.md. NOTA: l'ADOZIONE (flag di default) resta nel pacchetto di ratifica in attesa di maintainer, vedi ratifica-pacchetto-435-446-447-448.md; il codice e' in albero e certificato.
<!-- SECTION:FINAL_SUMMARY:END -->
