---
id: TASK-414
title: >-
  BoxRepair con eviction a un livello — il box 410 è ipotesi B (celle VRP
  sature): swap strutturalmente necessario
status: Done
assignee: []
created_date: '2026-07-24 16:20'
updated_date: '2026-07-24 15:34'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies:
  - TASK-410
priority: high
ordinal: 238000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
L'A/B cumulativo (task410-results) lascia box 9-11, quasi tutto **T01/G06** (60h
Q22-confinate al giovedì) + 1 T08/G01. Discriminatore VRP sul run-2 (mondo Q22):
**VERDETTO B** — T01 ha 216 celle VRP-legali (i giovedì) ma **0 libere**; anche
T08 (995 VRP) ha 0 libere. La griglia ha saturato le celle compatibili: il piazzamento
diretto non esiste, serve EVICTION. Il golden dimostra la fattibilità (231h T01 tutte
di giovedì, incl. le 60 di G06): è un limite di search/repair, non di modello (direttiva
primaria: Q22/M20.2/410 NON si annacquano).

## DESIGN — eviction a un livello in BoxRepair (la mossa del planner umano)

Dopo il pass diretto, per ogni lezione L ancora boxata:
1. Per ogni cella ts nel VRP di L che passa i gate NON-conflitto (H14, cap H7/H11 di L,
   H17-safe per il giorno di L): identifica gli occupanti bloccanti (stesso gruppo e/o
   docente a ts).
2. Se il blocco è UN SOLO occupante O unpinned: cerca per O una cella libera ts2 che passi
   TUTTI i gate di O (VRP di O, conflict-free, cap, H17-safe di arrivo) E la cui PARTENZA
   sia H17-safe (h17DayPenalty del giorno di O senza O ≤ prima: O al bordo del run o
   giorno-che-si-svuota — MAI aprire un buco).
3. Muovi O→ts2, piazza L→ts. Net hard: −1 (H0), nessun nuovo hard per costruzione (stessi
   gate esatti di sempre, più il gate di partenza). Deterministico (ordine L, ts, O, ts2
   canonici), bounded (≤216 ts × 1-2 occupanti × celle-libere-di-O).
4. Aggiornamento mappe: byTs, groupDaySlots (rimozione slot di O + inserimenti), teacherDaily
   per entrambe le date di O — attenzione alla rimozione (nuovo caso: finora le mappe solo
   crescevano).

RAFFINAZIONE (maintainer 2026-07-24, da applicare nel pass di review se il laptop non l'ha già
fatto): nella scelta della vittima O preferire l'occupante MENO VINCOLATO (più celle libere
alternative, es. max |celle libere di O|) — la controparte dell'ordinamento
most-constrained-first: si costruisce dai vincolati, si sfrattano i liberi.

VALIDAZIONE SNAPSHOT (direttiva maintainer): checkpoint harness su val410-run2 (11 boxate,
mondo Q22) — atteso 11 → ~0 con conflitti 0 e gap 0; il caso T01 è la fixture reale
perfetta. Poi suite completa + skeptic sul claim improve-only esteso (la partenza H17-safe
è il punto nuovo da refutare). A/B laptop di conferma nel prossimo giro cumulativo.

NOTA G04 (dal verdetto 410, NON in scope qui): 18-23 giorni-vuoti M20.2 di G04 sono
strutturali (domanda 750h < piano 990h) — domanda per la scuola (Q di 407: compattare o
spalmare), non eviction.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita (skeptic Opus sul claim improve-only con eviction)
- [ ] #3 test.sh EXIT=0 + checkpoint val410-run2 (box 11→~0, conflitti 0, gap 0) + A/B laptop cumulativo successivo
<!-- DOD:END -->



## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-24 15:34
---
Auto-implemented (Claude, 2026-07-24): one-level eviction in BoxRepair, pushed as 69e0c36c. val410 box 11->0 via eviction (improve-only, conflicts 0, no new gap), val398 box 6->0 via direct (no regression). /simplify + /code-review high (Opus skeptic) run; fixed 2 confirmed bugs (teacherDaily same-day H7 corruption, at-cap same-group cap false-negative). test.sh EXIT 0, golden untouched. NOTE: maintainer's d5bd952a refinement (least-constrained-victim selection) is NOT yet incorporated; chronological-first currently. Recommend a follow-up to add victim-selection-by-constrainedness if maintainer wants it before the laptop A/B.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
IMPLEMENTED + PUSHED (commit 69e0c36c, on top of maintainer's docs d5bd952a). One-level eviction in BoxRepair: after the direct pass, for each still-boxed L find a VRP cell whose single unpinned blocker O can relocate to a fully-legal cell (incl. new departure-H17-safe gate), then commit O->ts2 + L->ts atomically. Load-bearing subtlety: same-group blocker -> L refills O's exact slot, group-day map slot-preserving, departure gate skipped; different-group (teacher) blocker -> genuinely vacates, departure gate applies.

CHECKPOINT EVIDENCE (snapshot-first, maintainer's directive):
- val410-run2 (Q22 world): box 11 -> 0, all via eviction, conflictsAdded 0, no NEW H17 gap. The run's 1 pre-existing H17 gap stays (compaction's domain, out of scope for H0-clearing); hard -12 -> -1.
- val398-run2 (pre-Q22): box 6 -> 0 via direct placement, evicted 0 (eviction is a no-op, proves no TASK-401 regression).
Checkpoint asserts the REAL solver hard-score (== -expectedStartGaps), not conflictsAdded alone (which only sees same-slot H1/H3, not daily caps).

DoD: #1 /simplify applied (arrivalBeforeMap removed, gate helper takes pre-fetched day map). #2 /code-review high run (4 Opus finders + improve-only skeptic): 2 confirmed bugs FIXED before push: (A) teacherDaily same-day relocation corrupted the count (decrement without re-increment) -> silent H7 violation; fixed (same-day teacher = count-neutral no-op, mirroring the group map). (B) at-cap same-group-cap false-negative; fixed (swap-aware caps in findEviction). Em-dashes cleaned from added prose. Skeptic could NOT refute H1/H3/H11/H14/H17, pinned-movement, or re-boxing. #3 test.sh EXIT 0 (714 tests, golden untouched). BoxRepairCheckpointTest generalized (runCheckpoint) + 4 departure-gate unit tests.

KNOWN/DEFERRED:
1. maintainer's refinement (his d5bd952a note): eviction should prefer the LEAST-constrained victim O (max free alt cells), counterpart of most-constrained-first. My impl takes the first legal relocation in canonical (chronological) order. Does not change the checkpoint (still 11->0). A focused follow-up to add victim-selection-by-constrainedness, with its own checkpoint confirmation, recommended.
2. laptop cumulative A/B confirmation deferred to the next round (as DoD #3 itself states).
3. One-level ceiling (2+ blockers / pinned blocker not evictable) is by design; the A/B will show if a 2nd level is ever needed.
4. A both-pinned-exempt gap left by a different-group departure is H17-legal (0 hard) but carries soft cost; eviction trades H0 for it (its job).
<!-- SECTION:FINAL_SUMMARY:END -->
