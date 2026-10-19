---
id: TASK-464
title: >-
  Co-presenza ospite nel lab (golden-first): stato legale per coppie
  (gruppo,tag) praticate dal golden
status: Done
assignee: []
created_date: '2026-07-31 10:53'
updated_date: '2026-08-01 09:24'
labels:
  - claude-planned
  - solver
  - golden-first
milestone: m-12
dependencies: []
priority: high
ordinal: 287000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Direttiva maintainer 2026-07-31 pomeriggio: agire sulle piste di re-interpretazione dei ~92 room-null lab (tag acconciatura 85%, termoidraulica 14%). Scoperta chiave: le 44 violazioni tag-capacity del GOLDEN sono G14 + G12 su acconciatura, e le inferenze Q19/Q20 (domande-brevi.md) mostrano che nel golden quei gruppi stanno nel laboratorio DA OSPITI in contemporanea con una classe titolare (G14 30h tutte di giovedi', G12 12h giovedi'). Il nostro H2 (room conflict hard) vieta la co-presenza, quindi il motore lascia l'ospite room-null dove la scuola lo mette dentro.

Obiettivo: modellare la co-presenza ospite come stato LEGALE per le coppie (gruppo, tag) che il golden pratica (candidate iniziali: G14 e G12 su acconciatura, da Q19/Q20), invece dell'attuale esito room-null penalizzato da H29. Possibili forme da valutare in fase di design: esenzione mirata dal conflitto stanza per le lezioni 'ospite' (capienza fisica permettendo, stanza cap 22), oppure assegnazione condivisa dichiarata. Prototipo dietro flag default-OFF, misura al checkpoint stanze poi full-solve A/B; nessuna adozione senza maintainer (golden-first, CLAUDE.md sezione 'Golden-first recalibration').

GATED dall'esito dello spike read-only gia' in coda su zai (anatomia room-null vs celle di co-presenza golden). Correlato ma NON duplicato di TASK-449 (allocatore pre-assegnazione tag mono-stanza) e TASK-455 (M-TC tag capacity): quelli gestiscono la scarsita' del tag, questo legalizza la co-presenza che il golden pratica. Riferimenti: claudedocs/spalmone-2026-27/task455-tag-capacity-30e-2026-07-30.md (anatomia 169: Lavaggio/T18 68 gio, Servizio stilistico/T26 60 mer/gio, Barberia/T19 16 mar, termoidraulica 24 mar).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Design breve documentato con la forma scelta (esenzione H2 mirata vs assegnazione condivisa) e le ragioni della scelta
- [x] #2 Prototipo dietro flag default-OFF: con flag OFF il comportamento e' byte-identico all'attuale (verificato)
- [x] #3 A/B con batteria KPI standard (capture-run.sh) + conteggio dei room-null convertiti in ospite-legale, misurato al checkpoint stanze e poi full-solve
- [x] #4 Le coppie (gruppo,tag) abilitate derivano SOLO da evidenza golden (G14/G12 su acconciatura), mai generalizzate a tutti i gruppi
- [x] #5 DoD piena (simplify + code-review high + test.sh con exit code)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-31 13:11
---
AC#1 DESIGN BREVE completato (zai, 2026-07-31): artifact task464-copresenza-ospite-design-2026-07-31.md. Forma scelta = esenzione pairwise mirata di H2 + nuovo count-cap H-COPRESENCE (hard) per (tag, weekday, slot), entrambi flag-gated default OFF, depth e coppie SOLO da evidenza golden. DECISIONE maintainer (anti-attesa): modellazione NON gated a referente (golden autoritativo a livello orario; domanda fisica aula-normale nel doc TASK-466c).
---

created: 2026-07-31 13:11
---
PIANO IMPLEMENTAZIONE (file-per-file, per sessione fresca, non iniziato in codice perche' cambio load-bearing su H2 merita contesto pieno):
1. SchoolSchedule.java: aggiungere isCoPresenceEnabled() (template isTagCapacityEnabled, default FALSE, LIVE) + static map goldenCoPresenceDepthStatic (key 'tag|weekday|slotIndex' -> depth) + getter goldenCoPresenceDepth(tag,weekday,slot) + setter.
2. ConstrainedTeacherAllocator.java: aggiungere loadGoldenCoPresenceDepth(Map<String,String> subjectToTag) (mirror loadGoldenSlot0Anchors: single-pass seed/lessons.csv via SeedCsv, skip closed, mappa subject->tag,Conta gruppi distinti per (tag, weekday, slotIndex); slotIndex dallo start-hour via TimeslotSequence.SLOT_START_TIMES).
3. ScheduleService.refreshSolverStatics (~linea 1794, dopo M-TC): derivare subjectToTag dai Course (hanno subject + requiredRoomTags, autorevole niente duplicazione SubjectRegistry), chiamare loader, SchoolSchedule.setGoldenCoPresenceDepthStatic, banner enabled=%b + depth cells.
4. BaseConstraintProvider.roomConflict (linea 364-379): dopo i filter esistenti aggiungere .filter((a,b) -> !(SchoolSchedule.isCoPresenceEnabled() && isCoPresenceGuestPair(a,b))). isCoPresenceGuestPair = stesso tag (course.requiredRoomTags single) && goldenCoPresenceDepth(tag, weekday, slotIndex) >= 2. OFF -> !(false&&..) = true -> H2 byte-identico.
5. SchoolScheduleConstraintProvider: nuovo constraint coPresenceCap (clone M-TC pattern linee 1790-1809): forEachIncludingUnassigned.filter(enabled && single-tag && real teacher).groupBy(tag, weekday, slotIndex, count).filter(count > depth).penalizeHard(matchCount). 'H-COPRESENCE - ...'.
6. ScheduleService.ROOM_TIER_HARD_FAMILIES (linea 768): aggiungere 'H-COPRESENCE' cosi' timeslotTierHardScore resta == boxed pre-B-ROOM.
7. Test: ConstraintVerifier su H2 con flag OFF penalizza same-room same-slot come prima (byte-identico); e con flag ON la coppia acconciatura in cella depth>=2 non penalizza H2, mentre coPresenceCap penalizza oltre depth.
---

created: 2026-07-31 13:11
---
DOVD/e misure (AC#2,#3,#5) rinviati a sessione di implementazione: byte-identico a OFF (unit test), checkpoint stanze (room-null H29 su celle co-presenza: target 92 -> 0 per guest abilitati), poi full-solve A/B n>=2 con capture-run.sh se checkpoint regge (lezione E3: n=1 inganna). /simplify + /code-review high + test.sh EXIT 0. Riferimenti: spike-room-null-copresenza-2026-07-31/VERDICT.md (clearance cap2=48 cap3=2 cap4=23 cap5=19, depth Mon 08-12=2 Thu 08-12=5 Thu13=4 Thu14-15=2); M-TC template SchoolScheduleConstraintProvider:1757-1809; flag template SchoolSchedule.isTagCapacityEnabled:372.
---

created: 2026-07-31 13:39
---
CHECKPOINT STANZE SUPERATO (zai, 2026-07-31 sera, RoomPhaseSnapshotTest export-mode): flag ON -> LAB room-null 92 -> 0 (92/92 ospite-legale), hard -92 -> 0. Depth map pubblicata (91 celle depth>=2 / 136, acconciatura). Cap H-COPRESENCE non over-fira (hard=0 = co-locazione esatta fino alla depth golden). OFF baseline replica il spike (92->92, hard -92, H2 blocca la co-locazione). Nessun tier degradato (medium -825805 -> -825678). AC#2 (byte-identical OFF) verificata da CoPresenceConstraintTest (8 test) + loader test (2) + 345 regression green. AC#4 (coppie SOLO da golden) verificata dai test h2_flagOn_pairOutsideCoPresenceCell + nonTaggedPair. AC#3 checkpoint-done; full-solve A/B n>=2 (laptop scale) = prossimo passo (il checkpoint e' lower bound: fissa i timeslot baseline, solo phase-5 LS, puo' solo aggiungere beneficio). Artifact: task464-copresenza-checkpoint-2026-07-31.md. DoD in corso (/simplify + /code-review high + test.sh EXIT 0).
---

created: 2026-07-31 14:01
---
CODE-REVIEW HIGH completato (zai, 2026-07-31 sera, 2 verificatori Opus, diff post-simplify). VERDETTO: NESSUN bug nel path flag-OFF (byte-identical confermato su tutti e 4 i sub-path: H2 filter short-circuit, cap 0-match, statica map mai letta, refreshSolverStatics side-effect solo LOG). UN bug CONFERMATO (media severita', solo flag-ON) CORRETTO: H-COPRESENCE era classificato ROOM-tier (inserito in ROOM_TIER_HARD_FAMILIES) ma e' TIMESLOT-tier (groupa su (tag,startDateTime), non reference l.room) -> accecava il gate staged + assertImproveOrNoop dei repair, lasciando persistere violazioni over-depth in B-ROOM. FIX applicato: rimosso da ROOM_TIER_HARD_FAMILIES (ora timeslotTierHardScore lo conta e il ciclo lo risolve in B-TIMESLOT, desiderabile). Commenti coPresenceCap + ROOM_TIER_HARD_FAMILIES aggiornati. Findings DEFERRED al full-solve A/B: (a) assumption mono-room acconciatura (lo cap conta per-tag ignorando la stanza fisica; speculare a M-TC, ok finche' acconciatura e' mono-room); (b) full-solve A/B n>=2 resta il gate di adozione. Test-hygiene fix applicato: SnapshotFixture.resetStatics() ora pulisce anche goldenCoPresenceDepthStatic. DoD: /simplify fatto (3 reviewer: estratto buildSubjectToSingleTagMap, catturato enabled build-time in roomConflict, tighten loader test), /code-review high fatto (questo), test.sh EXIT 0 in corso di re-run sul codice finale.
---

created: 2026-08-01 09:24
---
[zai 2026-08-01] RATIFICA ADOZIONE (maintainer, combined 463+464): flag DEFAULT ON con opt-out esplicito. isCoPresenceEnabled convertito a forma opt-out (null o qualunque valore tranne "false" = ON), stessa forma di isLabFallbackEnabled. Evidenza adozione: full-solve A/B n=2 (task464-results/{off-baseline,on-run1,on-run2}) hard -112 -> -17/-18 (floor storico), 94 acconciatura room-null -> ospite-legale, 0 over-depth cap, nessuna regressione full-day/ricorrenza/medium; combined run3 hard -6. Gate semantica-stanze chiuso dall'INPUT (auto-verifica referente-autoverifica-domande-2026-08-01.md): Q20 G12 aula NORMALE (calendario 604/295/91 = seed), Q19 G14 ospite NEL lab (50h L su 12 giovedi, co-locazione coperta da esenzione H2). DoD ratifica completa: /simplify (4 agent clean); /code-review high (2 skeptic Opus CONFIRMED 0 bug correttezza; default-ON provably inert quando depth map vuota: depth 0 -> isCoPresenceGuestPair false -> H2 byte-identical + cap filter depth>=2 false -> 0 penalty; 2 commenti stale corretti BaseConstraintProvider+RoomPhaseSnapshotTest; skeptic B flag 'golden test non assertiona H-COPRESENCE' RISOLTO aggiungendo assertZeroHard+assertMatchCount H-COPRESENCE a GoldenSolutionRegressionTest: il golden rispetta la propria depth -> 0 over-depth, invariant pinned); test.sh 934/0/0 EXIT 0. CLAUDE.md aggiornato (sezione RATIFICA + riga H-COPRESENZA non piu' 'flag OFF, no adoption'). NOTA golden-runtime: loadGoldenCoPresenceDepth e' lettura del golden su percorso di produzione, censita in TASK-452 #7.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RATIFICATO DEFAULT ON 2026-08-01 (maintainer, combined con TASK-463). isCoPresenceEnabled -> opt-out form (null|!"false" = ON, -Dplanner.co-presenza.enabled=false per opt-out). Evidenza: full-solve A/B n=2 hard -112 -> -17/-18 (94 acconciatura room-null -> ospite-legale, 0 over-depth cap, nessuna regressione full-day/ricorrenza/medium); combined run3 hard -6. Gate semantica-stanze chiuso da INPUT: Q20 (G12 12h parallele = aula NORMALE, calendario 604/295/91 = seed) e Q19 (G14 ospite NEL lab, 50h L su 12 giovedi). DoD ratifica: /simplify clean, /code-review high 2 skeptic Opus CONFIRMED (0 bug; default-ON provably inert a depth-map vuota; 2 commenti stale corretti BaseConstraintProvider+RoomPhaseSnapshotTest; coverage gap -> asserzione golden H-COPRESENCE aggiunta a GoldenSolutionRegressionTest per pinare 'golden rispetta propria depth'), test.sh 934/0/0/16skip EXIT=0. CLAUDE.md: sezione RATIFICA combined + riga H-COPRESENZA (non piu' 'flag OFF, no adoption').
<!-- SECTION:FINAL_SUMMARY:END -->
