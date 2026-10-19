---
id: TASK-542
title: >-
  Checkpoint-resume a fase arbitraria del ciclo staged: cattura a ogni confine,
  ripresa senza pipeline, iterazione predizione-verifica in minuti sul residuo
status: Done
assignee: []
created_date: '2026-08-21 18:20'
updated_date: '2026-08-22 04:05'
labels:
  - claude-planned
  - orchestration
  - checkpoint
  - priority-directive
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Direttiva maintainer 21/08 sera (condizioni per la fiducia): (1) niente bendable finche' non torna la fiducia; (2) traguardo visibile = quasi-0hard, e se non arrivano capire come farli arrivare; (3) modalita' che catturi checkpoint a QUALUNQUE fase (non solo CH/LS) e verifichi le modifiche SU DI ESSI senza ripartire la pipeline dall'inizio. Ogni modifica: predizione scritta prima, verifica sul checkpoint in minuti. E' il veicolo per iterare sul residuo -642 e dimostrare miglioramenti misurabili e prevedibili.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Checkpoint serializzabile a OGNI confine di fase del ciclo staged: fine A-CONSTRUCTION, post-PROMOTION, ogni iterazione B-TIMESLOT, ogni C-DEMOTION, post-RELEASE, ogni repair pass, fine B-ROOM: griglia + stato statics (promoted pins, M32 map, budget maps, promotedLessonIds) + cursore di fase + score
- [ ] #2 API/modality di RESUME: load checkpoint -> ricostruire SchoolSchedule e statics -> continuare il ciclo dalla fase successiva senza rilanciare dall'inizio (es. POST /api/schedule/solve-staged-resume)
- [ ] #3 Round-trip verificato: resume da checkpoint e continuazione fino a fine ciclo produce un run completo valido
- [ ] #4 Predizione-verifica in minuti: dal checkpoint -642 post-RELEASE (attempt 1 braccio A): applicare una modifica, dichiarare la predizione PRIMA, misurare il delta hard, tutto in meno di 10 min senza pipeline completa
- [ ] #5 Determinismo dei segmenti riavviati dichiarato (MT-LS: statistico, documentato)
- [ ] #6 Sanity gate 535 eseguibile su ogni checkpoint
- [ ] #7 DoD completa per stadio
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: zai
created: 2026-08-21 18:37
---
STADIO 1 COMPLETATO (commit 3b5faf02, suite 1099/0 EXIT 0): censimento confini + formato checkpoint, TDD.
---

author: zai
created: 2026-08-21 18:37
---
CENSIMENTO (StagedPhaseBoundary, 15 confini mappati 1:1 su runStagedCycle): CYCLE_START (post loadSchedule), POST_CONSTRUCTION, POST_PROMOTION, POST_B_TIMESLOT-n (iterato), POST_DEMOTION-n (iterato), POST_RELEASE, POST_HOLE_REPAIR, POST_LAB_DAY_CONSOLIDATION, POST_COMPOSITION_HOLE_CLOSER, POST_ANCHOR_REPAIR, POST_SURPLUS_REBALANCE, POST_QUOTA_FILL_REPAIR, POST_EXACT_FINISHER, POST_B_ROOM, POST_ROOM_QUOTA_FILL_REPAIR. Ogni costante espone resumeEntryPoint() = la fase SUCCESSIVA (mai la appena completata: ripeterla doppierebbe promozione/smarcatura).
---

author: zai
created: 2026-08-21 18:37
---
FORMATO (StagedCheckpoint record + StagedCheckpointSerializer JSON): versione (fail-loud su future), cursore (boundary + iteration per il loop B-TIMESLOT + attempt + graspSeed per il contesto multi-start), score + gate (boxed, timeslot-tier hard), griglia (LessonPlacement: lessonId, timeslotId, roomId, pinned, promoted). Statics: l'UNICO stato ciclo-derivato non ricomputabile e' promotedLessonIds, trasportato come flag promoted della griglia (fonte unica); tutto il resto statics (calendari, quote, M32, pool cap, VRP) e' input-derived e viene RIPUBBLICATO da refreshSolverStatics al resume, non trasportato (un checkpoint che trasporta stato ricomputabile stantia al primo cambio input).
---

author: zai
created: 2026-08-21 19:00
---
NOTA STADIO 2 (da code-review gate, history-angle): la CATTURA deve leggere lesson.promotedPin (NON lesson.pinned) per il flag promoted della griglia: prima di POST_RELEASE un pin autoritativo ha pinned=true/promotedPin=false e andrebbe incluso per sbaglio confondendo i due flag; dopo POST_RELEASE entrambi sono false per gli ex-promossi.
---

author: zai
created: 2026-08-21 19:00
---
NOTA STADIO 2 (comments-angle): al resume, refreshSolverStatics va eseguito DOPO aver ripristinato i flag pinned sulle lezioni (la mappa statica pinnedLessonTimeslotsByGroup filtra su lesson.pinned, che include i promossi: l'ordine restore-grid -> refreshStatics e' obbligatorio).
---

author: zai
created: 2026-08-21 19:00
---
REVIEW GATE STADIO 1 (DoD): /simplify 4 agenti (finding applicati: linearOrder+resumeEntryPoint derivati dall'ordine di dichiarazione, Optional al posto del sentinel NONE=null, exception ladder serializer sgonfiata, assertFalse; skip motivati: memoizzazione promotedLessonIds una-volta-per-resume, ObjectMapper non-CDI per record puri, flag pinned ridondante tenuto per self-describing). /code-review high 4 reviewer: CLAUDE.md pulito, bug scan pulito, history pulito (claim verificate su promote/demote/release), comments-angle tutte le claim HOLDS + oracolo completato con la meta' negativa (ogni chiamata run* del corpo runStagedCycle deve avere mapping: aggiunta di pass non tracciati ora fallisce il test).
---

author: zai
created: 2026-08-22 04:05
---
NOTTE 21/08->22/08 (zai, 20 commit): STADI 2+3 COMPLETI a DoD (suite 1110/0 EXIT 0, simplify 2 round, code-review pulito): cattura a 15 confini verificata live, restore fedele (iter-1 byte-identico), resume full-end validato, sanity via checkpoint-to-dump.py, writer con guardia anti-overwrite. BANCO ATTIVO (task542-results/iterations-542.md): iter-0 baseline, iter-1 CONFERMATA (resume ~3min), iter-2 CONFERMATA (il -642 era artefatto protocollo: senza import-calendario la coda di stub Stage produce residuo fantasma), iter-3 SMENTITA+classificata (repair non collocano, serve LS), iter-4 CONFERMATA sui meccanismi (32/32 collocate, H32 e H0 a ZERO, consegna -33hard) con residuo migrato su H17 -30. PROSSIMO (iter-5): cura durabile in ConstrainedTeacherAllocator (mai pin su date senza riga quota). AC#4 dimostrato: 4 iterazioni predizione->verifica in una notte sullo stesso substrato.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
