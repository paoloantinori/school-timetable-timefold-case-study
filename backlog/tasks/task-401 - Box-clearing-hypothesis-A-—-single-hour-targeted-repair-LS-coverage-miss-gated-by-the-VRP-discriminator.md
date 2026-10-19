---
id: TASK-401
title: >-
  Box-clearing hypothesis A — single-hour-targeted repair (LS-coverage miss);
  gated by the VRP discriminator
status: Done
assignee: []
created_date: '2026-07-23 11:03'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies:
  - TASK-399
priority: medium
ordinal: 225000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
HYPOTHESIS A for why the TASK-398 box doesn't clear to 0: the boxed single-hour lessons ARE placeable, but the generic LS (change/swap/pillar/ruin-recreate across everything) doesn't find the slot — it isn't searching the right moves. The human clears the residual by moving SINGLE hours around the stable block skeleton.

Supporting evidence (run-2 discriminator): all 6 boxed lessons have 109-126 placement-conflict-free cells (H1/H3/H7/H11 level) — there is room; they are not placement-deadlocked. The open check is whether those cells are VRP-legal (see the VRP-discriminator task — if yes, A holds).

WHAT (if the VRP discriminator confirms A): add a single-hour-TARGETED repair — either a dedicated move selector that relocates only single-hour lessons (leaving the recurring block skeleton fixed), or a construction-tier-3 pass that places the residual by rearranging singles. Measure: does final box reach 0 WITHOUT sacrificing the block skeleton / recurrence?
GATED BY: the VRP-discriminator task (do that first; pursue A only if boxed lessons have a VRP-legal conflict-free slot). Per user: this is the primary direction; hypothesis B is the fallback only if A doesn't deliver.

**GATE SUPERATO (TASK-399, Fedora 2026-07-23): il discriminatore VRP CONFERMA A.** Tutte e 6 le
lezioni boxate del run 2 hanno celle VRP-legali conflict-free contro la griglia finale — ma solo
**3-7 ciascuna** su ~1.000 VRP-legali (T04 3, T11 5, T13 7, T14 4,
T02 6/3 giorni per la coppia), tutte tardo-anno (feb-giu 2027). La scarsità È la spiegazione
del miss (1-su-~200 per mosse stocastiche) e il motivo per cui il repair per ENUMERAZIONE
(esattamente `BoxVrpFeasibilityDiscriminatorTest`'s loop: VRP ∩ occupazione ∩ cap) è la forma giusta
del fix — non servono mosse più furbe, serve enumerare le celle del residuo. Nota design: le celle
libere possono richiedere uno SWAP (spostare una singola ora già piazzata per liberare una cella
migliore) solo se il greedy diretto non chiude — prima provare il piazzamento diretto, che il
discriminatore dice essere sufficiente (matching simultaneo delle 6 verificato a mano).

**Protocollo checkpoint (concordato con maintainer, 2026-07-23) — testare il repair SENZA ri-solvare:**
lo stato finale run-2 è ricostruibile dagli artifact committati (`val398-run2/lessons.txt`, il
pattern fixture di `BoxVrpFeasibilityDiscriminatorTest`); il primo test del repair è un harness che
carica quel checkpoint e esegue SOLO la fase di repair (pattern `withCustomPhaseCommandList` di
`WeeklyTemplateConstructionRealSeedTest`) — secondi, non 2h. Per i run futuri sul laptop: catturare
anche `GET /api/schedule/export` JSON ai confini di fase (post-ph1 deterministico, post-ph3
pre-repair) così il checkpoint è importabile direttamente via `import-schedule.sh` senza
ricostruzione. L'adozione finale richiede comunque UN A/B completo (la LS è stocastica — il
checkpoint fissa una traiettoria).
<!-- SECTION:DESCRIPTION:END -->

## IMPLEMENTATO (Fedora, 2026-07-23) — checkpoint-validato; resta l'A/B laptop come gate finale

**Meccanismo:** `BoxRepair` (classe pura, `com.school.planner.solver`, forma gemella di
`StudentDayCompaction`) — piazza le lezioni boxate per ENUMERAZIONE diretta con 4 gate: VRP
(`getAvailableTimeslots`), conflict-free (`StudentDayCompaction.isSlotConflictFree`, canonico
condiviso, pseudo-esente come l'H1 reale), cap H7/H11 + H14 (il VRP non filtra la start date),
e **H17-safe a delta esatto** (`h17DayPenalty` prima/dopo il piazzamento simulato, con la
semantica break-level pinned-exempt reale di task-350). Eseguito da `CompactionPhase` (fase 4,
nessun cambio XML): repair → compattazione → secondo repair. Room nullata al piazzamento (la
ph5 la assegna).

**Validazione checkpoint (`BoxRepairCheckpointTest`, locale ~25s):** stato finale run-2
ricostruito in-memory dagli artifact committati (12.295 righe + 6 boxate) → **box 6 → 0 al
primo passaggio, conflitti 0, gap 0, hard −6 → 0**. Celle scelte: T04 02-22@8,
T11 02-15@8, T13 02-05@12, T14 06-08@13, T02 05-28@12 + 06-04@10.

**Skeptic Opus (code-review): claim improve-only REFUTATO e corretto.** Il gate H17 v1
(adiacenza/buco) ignorava l'esenzione break both-pinned: piazzare una lezione unpinned DENTRO
un buco esente delimitato da due pin flippa il break da esente a penalizzato (buco width-3:
H0 +1, H17 −2, netto −1). Fix: gate a delta esatto; controesempio pinnato in
`BoxRepairH17SafetyTest`. Latente documentato (non gate-ato): H4.3, inerte con
`ProfessionalizzantiDay` a zero righe.

**GATE FINALE SUPERATO (2026-07-23, task401-results, verificato da Fedora sui raw): ADOTTATO.**
Entrambi i run: hard -1 (solo H17), box 0 (BoxRepair 4→0 e 6→0, conflitti 0), 12.301/12.301
piazzate, KPI tenuti. Il residuo che la LS lascia (4-6 lezioni) viene chiuso dall'enumerazione
esattamente come da predizione della 399. M15 salito a 19-23 (da 13-14; golden 98): trade
reale del repair, da monitorare. Il testo sotto è il gate originale, conservato per storia.

**GATE FINALE (laptop, da eseguire):** A/B con solve completi ×2 su HEAD (398+401 insieme) —
adottare se hard finale = 0 (o ≤ baseline −1/0) CON box finale 0 E KPI tenuti (full-day,
blocchi, ricorrenza — il repair non deve sacrificare lo skeleton). Il checkpoint prova il
meccanismo sullo stato finale run-2; il solve fresco prova che regge su una traiettoria LS
nuova. Catturare anche l'export JSON ai confini di fase (protocollo checkpoint).

**FOLLOW-UP ACCODATO (suggerimento maintainer 2026-07-23, SEQUENZIALE dopo il gate base) —
promosso a task dedicato: TASK-403. Repair anche TRA ph2 e ph3.** L'ipotesi: invocare il repair solo in ph4 è tardi — chiudere
il box prima della ruin-recreate la farebbe partire da hard 0, e con base hard-0 la ruin
(che accetta peggioramenti temporanei) esplora il medium più liberamente, senza la priorità
lessicografica dell'hard residuo a condizionare ogni accettazione; riduce anche il rischio
che le celle libere spariscano prima di ph4. Evidenza a contorno: la ph2 NON spreca budget
sull'hard (run 2: hard fermo a −8 ma medium −2,31M → −1,41M per tutta l'ora), quindi il
guadagno atteso è search-freedom della ph3 + chiusura anticipata, non recupero di budget.
Nota: subito dopo la CH sarebbe un no-op per costruzione (boxUnplaceableLeftovers È già
quell'enumerazione; le celle nascono solo col riarrangio della LS). Implementazione: seconda
entry custom-phase in solverConfig.xml tra ph2 e ph3 (ripple checklist solver-config-sync:
SolverConfigParser, solverPhases.ts, solver-phases.md, i18n). Misura A/B vs HEAD: medium
finale, tempo-a-0-hard, KPI — braccio da eseguire SOLO dopo che il gate base è passato.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati — groupDaily eliminato (derivabile da groupDaySlots); `isSlotConflictFree` unificato canonico in StudentDayCompaction (pseudo-esente, corregge anche il bias conservativo della compattazione); `buildOccupancyIndex` condiviso; tabella MAX_HOURS_PER_DAY dedupata (5 copie → RealSeedFixture). Skippata (motivata): estrazione parse-helper degli artifact run-2 (file statici congelati, rischio drift nullo)
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti — skeptic Opus ha REFUTATO il claim improve-only (gate H17 cieco all'esenzione both-pinned) → corretto con gate a delta esatto + `BoxRepairH17SafetyTest` che pinna il controesempio; tutti gli altri hard verificati coperti (H4.3 latente documentato)
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore — test.sh EXIT=0 (697/697, incl. golden + i 3 harness), ui-typecheck.sh EXIT=0, ui-build.sh EXIT=0 (solverPhases.ts toccato)
<!-- DOD:END -->
