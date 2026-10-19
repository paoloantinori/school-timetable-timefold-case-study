---
id: TASK-393
title: Final compaction phase — re-close H17 student-day gaps the local search re-opens
status: Done
assignee: []
created_date: '2026-07-22 12:00'
updated_date: '2026-07-22 12:15'
labels:
  - claude-planned
dependencies: []
ordinal: 217000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La costruzione (TASK-390) chiude i buchi H17 evitabili all'inizio, ma la fase 3 (ruin-recreate) è l'ULTIMA fase che tocca i timeslot e PUÒ ri-aprire un buco H17 (-1 hard) se così risolve un H3 (-1000): bilancio hard +999, l'acceptor lo accetta. Nulla dopo la fase 3 (fasi 4/5 sono solo room) lo ri-chiude. Serve una fase finale deterministica che ritappa i buchi evitabili che la LS lascia, riusando l'approccio della costruzione. Deterministico batte la LS stocastica sull'affidabilità per questo compito di coda (stesso motivo per cui l'abbiamo messo in costruzione). Lexicograficamente sicuro sull'hard (chiude buchi o no-op, mai peggiora); se l'ipotesi "la LS ri-apre buchi" fosse falsa, la fase è un no-op deterministico = rete di sicurezza permanente. Direttiva utente (2026-07-22): costruirla speculativamente è giustificato — worst case no-op, e un solo solve laptop misura sia l'ipotesi (la fase agisce?) sia l'effetto (hard/KPI).
<!-- SECTION:DESCRIPTION:END -->

## Piano

**1. Estrarre l'algoritmo in un componente condiviso (`StudentDayCompaction`).**
Oggi `compactStudentDays` + `isSlotConflictFreeFor` sono privati in `DayClusteredConstructionPhase`. Estrarre il core in una classe/utility che opera su `SchoolSchedule` + un callback di mossa (così è disaccoppiata dal `phaseContext` specifico della fase): `compact(schedule, moveTimeslotAndNullRoom)` → ritorna stats {gapsBefore, gapsAfter, moved}. Sia la coda della costruzione sia la nuova fase finale la chiamano con il proprio `phaseContext.execute(Moves.change(...))`. Niente duplicazione.

**2. Raffinatezza "improve-or-noop" (perché è l'ultima fase, nessuna LS a valle).**
Commit di una mossa SOLO se riduce il conteggio di buchi interni del giorno (non solo se è legale). Uno "shift" (lezione di mezzo nel buco → il buco si riapre altrove, stesso conteggio) NON si committa: costerebbe ricorrenza senza chiudere. Garantisce: o chiude netto, o no-op. Mai spreco.

**3. Nuova `CompactionPhase` (customPhase) DOPO la fase 3, prima delle room.**
`com.school.planner.solver.CompactionPhase implements PhaseCommand`, chiama `StudentDayCompaction.compact(...)` con `Moves.change(timeslot)` + `Moves.change(room, null)` (la fase-4 room-CH riassegna pulito). Logga `gapsBefore→gapsAfter` (la fase È il proprio strumento di misura per l'ipotesi). Inserita in `solverConfig.xml` tra la fase 3 (RuinRecreate) e la fase 4 (room CH), con un `PhaseTransitionMarker`.

**4. Ripple solver-config-sync (OBBLIGATORIO, `.claude/rules/solver-config-sync.md`).**
Aggiungere una fase RINUMERA le room (4→5, 5→6). Aggiornare: `SolverConfigParser` (PHASE_NAMES/count), `SolverProgressionListener` (rilevamento fase se serve), `PhaseProgressionService`, `solverPhases.ts`, `SolverPhasesPage.tsx`, `UnifiedSolverControls.tsx` (se mostra le fasi), `claudedocs/solver-phases.md`, i18n (messages_en/it + frontend locales se nuove chiavi), `MEMORY.md`. Validare `bash scripts/validate/validate-solver-xml.sh` dopo l'edit XML.

**5. Guard.**
`WeeklyTemplateConstructionRealSeedTest` copre la costruzione (non la fase finale — quel test gira solo la CH). Aggiungere un test che la fase finale NON crea conflitti (stessa invariante della costruzione) e chiude buchi se presenti. Riusare l'infrastruttura del test esistente o un test dedicato sul componente `StudentDayCompaction` (unit test puro, senza solver, sull'algoritmo improve-or-noop + conflict-free).

**6. Validazione laptop (un solo solve di HEAD).**
Misura: (a) `gapsBefore` della fase finale > 0 → la LS ri-apre buchi (ipotesi vera); (b) hard finale sceso; (c) KPI (full-day/ricorrenza/blocchi) non regrediti. Se la fase è un no-op costante e non aiuta → valutare se tenerla come rete di sicurezza o rimuoverla (revert del ripple). Aggiungere al prompt laptop di TASK-392/391.

## IMPLEMENTATO (2026-07-22, commit ce519a42) — codice landato, resta la validazione laptop

`StudentDayCompaction` (componente condiviso estratto, regola move-max-only improve-or-noop + conflict-free) + `CompactionPhase` (customPhase finale dopo la fase 3) + pipeline 5→6 fasi (Compaction=4, room=5/6) con ripple completo (solverConfig/parser/listener/frontend/docs, incluso il fix StatusBadge phase6 che era raggiungibile ma non stilizzato). Costruzione rifattorizzata per usare lo stesso componente (40 mosse/0 conflitti sul real-seed). Verificato: XSD valido, test.sh exit 0 (689), ui-typecheck + ui-build verdi.

**Runtime della fase finale NON ancora eseguito** (nessun solve locale — i test lunghi girano sul laptop): la validazione laptop di HEAD (TASK-392) misurerà in un solo solve (a) `gapsBefore` della fase finale > 0 → la LS ri-apre buchi (ipotesi vera); (b) hard finale; (c) KPI. Follow-up: unit test dedicato di `StudentDayCompaction` (l'algoritmo è già guardato su dati reali da `WeeklyTemplateConstructionRealSeedTest`; un unit test puro con statics permissive coprirebbe gli edge case forced/pinned/conflict).

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify — l'estrazione di StudentDayCompaction È la semplificazione (rimuove duplicazione, componente riusabile); algoritmo già rivisto dai 4 agent /simplify nel commit costruzione precedente
- [x] #2 Skill /code-review — inline (non model-invocable): move-max-only improve-or-noop, conflict-free, VRP, pin-skip, determinismo, room-null→fase5 verificati; ripple config verificato green
- [x] #3 Verifica EXIT CODE: XSD valido, test.sh exit 0 (689), ui-typecheck + ui-build verdi (frontend toccato). Runtime fase finale → solve laptop
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
