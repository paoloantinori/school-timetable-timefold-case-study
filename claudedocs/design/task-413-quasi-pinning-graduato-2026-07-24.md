# TASK-413 — Quasi-pinning graduato per scarsità: design (2026-07-24)

## Decisione di meccanismo

Primario: **candidate 2** della task (penalità di deviazione dal template come riferimento
FISSO), con gradazione per scarsità (maintainer 2026-07-24: forza ∝ 1/|celle VRP|). Il
**candidate 1** (solve a stadi con template pinnato + release selettivo) resta lo spike
successivo SOLO se l'A/B del 2 non chiude: il 2 è single-solve (nessun cambio di
orchestrazione/API), checkpoint-first-testabile, e la gradazione per scarsità gli è
naturale (nel candidate 1 il pinning è binario e la gradazione vive solo nell'ordine di
release).

## Il punto tecnico chiave: il template si calcola PRE-SOLVE, non a fine costruzione

La task descriveva "catturare l'assegnazione template a fine costruzione". Farlo
letteralmente (popolare una mappa statica dentro il solve, a fine fase 1) corrompe lo
score: i constraint stream sono funzioni della soluzione + fatti; una mappa che cambia a
metà solve rende lo score della stessa soluzione diverso prima/dopo la cattura (i match
già valutati non vengono ricalcolati — score corruption in assert mode, best-solution
inconsistenti tra fasi).

La via d'uscita è che il template NON dipende dalla costruzione: `stampWeeklyTemplate`
(DayClusteredConstructionPhase:1120) lo deriva SOLO da stato identico al pre-solve
(timeslots, holidays, lezioni pinnate, corsi/ore residue) — la fase 1 gira per prima su
uno stato uguale a quello caricato da `loadSchedule(true)`. Quindi: si estrae la
costruzione del template in un helper puro, `ScheduleService.refreshSolverStatics` lo
invoca pre-solve e pubblica il risultato in una static map (stesso pattern di
`setDailyActivityQuotasStatic`), e la fase consuma lo stesso helper — per determinismo i
due calcoli coincidono (funzione pura, iterazioni ordinate, chiavi = id).

## Meccanismo

1. **Estrazione**: lo "Step 1" di `stampWeeklyTemplate` (+ `buildGroupWeeklyShape`,
   `buildWeeklyTargets`, `buildBlockedTeacherSlots`, `buildBlockedGroupSlots`) diventa un
   helper puro riusabile; la fase lo chiama come oggi (stesso risultato, stessa
   determinismo del real-seed test), `refreshSolverStatics` lo chiama pre-solve.
2. **Static map** su `SchoolSchedule`: `courseId → Set<(weekday, slotIndex)>` (le celle
   template del corso) + `courseId → deviationWeight` (gradazione). Volatile,
   build-locally-assign-last (precedente TASK-378).
3. **Gradazione per scarsità**: `deviationWeight = clamp(K / vrpCells(course), MIN, MAX)`
   con `vrpCells` dal representative unpinned del corso (già in
   `lessonAvailableTimeslotCountsStatic`, ScheduleService:696). Valori iniziali:
   K=100.000, MIN=20, MAX=500 — T01 (~216 celle) → ~463 (quasi immobile, famiglia
   M20/M22 ma sotto), un corso con ~1.300 celle → ~77, ≥5.000 → 20 (economico da
   muovere). K/MIN/MAX sono knob da benchmark, non verità.
4. **Nuovo vincolo M32 — "Template deviation"** (medium): lezione unpinned, docente
   reale, timeslot assegnato, corso PRESENTE nella mappa template, la cui
   `(weekday, slotIndex)` NON è una cella template del suo corso →
   `penalize(ofMedium(1) × weight(course))`. Semantica settimanale: qualunque settimana
   va bene purché la cella settimanale sia quella del corso — spostare una lezione su
   un'altra data stesso weekday/slot non è deviazione (la cadenza è preservata).
   Esenzioni: pinned (non muovibile; golden import), pseudo-teacher (Stage/Esame,
   precedente M21/S12), corsi senza celle template (nessun riferimento da cui deviare).
5. **Perché medium e non un ripeso di S13**: S13 (2 soft, riferimento
   settimana-precedente MOBILE) misura la ricorrenza ma non può difenderla — un burst
   finale di 13h costa quasi nulla. M32 ha riferimento FISSO (il template) e peso nella
   fascia sotto M20/M22: la LS sposta una cella-template solo se ripaga un problema più
   grosso (es. hard, o un giorno M20 intero). S13 resta com'è (misura, non protezione).

## Validazione (checkpoint-first)

- ConstraintVerifier: mappa settata a mano — deviazione pesata, cella giusta 0, pinned 0,
  pseudo 0, corso fuori mappa 0.
- Golden guard (`GoldenSolutionRegressionTest`): fixture 100% pinned + mappa vuota → M32
  = 0/0 zero-tolerance (pin nuovo).
- Real-seed construction test: invariato (byte-identical — l'estrazione non cambia il
  template); il dump di fine costruzione (nuovo artifact) deve dare deviazione M32 ≈
  solo repair-tail.
- Poi laptop A/B x2 HEAD vs M32. Gate: hard/box invariati (−1..0 / box 0), ricorrenza
  finale unpinned ↑ sostanziale (dal ~37% verso ≥55-60), erosione per gruppo ridotta,
  cadence-kpi (gap-weeks, burst ratio) verso il golden, M20+M20.2/full-day/day-start non
  materialmente peggiorati. Strumenti: cadence-kpi.py (nuovo), resemblance-kpi per
  gruppo (nuovo breakdown), golden-vs-run-viz rigenerato.

## Fuori scope (questo giro)

Leve di contorno (rinforzo S12, cap settimanale M21-style, M27 generalizzato) e il
candidate 1 a stadi: solo dopo l'evidenza A/B di M32. TASK-411 assorbita come misura
(cadence/fingerprint), questa è il meccanismo.
