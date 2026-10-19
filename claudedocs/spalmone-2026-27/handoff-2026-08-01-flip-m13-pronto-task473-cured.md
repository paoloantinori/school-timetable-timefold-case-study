# HANDOFF 2026-08-01 sera - FLIP m-13 PRONTO (TASK-473 cured, run3 clean)

> **DECISIONE (a nome di maintainer, dichiarazione solo): flip m-13 PRONTO, resta il SI di maintainer.**
> Il blocker (abort AnchorRepair, TASK-473) e' risolto. run3 source=input post-cura **completa
> end-to-end** (B-ROOM girato, 0 room-null). La cura TASK-452 riconfermata (0 H20). Ricorrenza
> unpinned 75,9% (la migliore dei 3 run). Trade da pesare nel SI: full-day 85,2% vs golden 90,2%
> (costo source=input, accettato dalla direttiva correttezza-logica-vs-costo).

## COSA E' SUCCESSO (questa sessione)

1. **DIAGNOSI TASK-473 (causal-chain, confermata dal codice)**: il guard `assertImproveOrNoop`
   lanciava `IllegalStateException` su violazione improve-or-noop -> il ciclo staged abortiva ->
   B-ROOM saltato -> 9446 room-null. Il `countConflicts` del pass conta solo H1/H3; il
   `timeslotTierHardScore` del guard conta tutte le famiglie timeslot-tier (H-COPRESENCE incluse):
   divergono per costruzione. Nell'abort reale il counter era onestamente 0; la `hardAfter<hardBefore`
   (-6 -> -7) ha fatto scattare il throw. Famiglia peggiorata: candidata H-COPRESENCE (abilitato,
   91 celle depth>=2), NON meccanicamente confermata (l'analysis catturato e' post-abort/post-release
   e diverge: -30 non -7).
2. **CURA TASK-473 (commit `3b177376`, pushato)**: `ensureImproveOrNoop` sostituisce
   `assertImproveOrNoop`: snapshot del placement pre-pass (`PlacementSnapshot`), e su violazione
   (hard peggiorato OR conflitto H1/H3 aggiunto) REVERT dello snapshot + WARN + continua verso
   B-ROOM (NOOP onesto, mai abort). Applicato al guard condiviso (tutti e 4 i pass). Diagnostica
   `worsenedFamilies` (try-catch, mai re-abort). Costanti `MOVE_TIMESLOT`/`NULL_ROOM` strutturano
   il contratto reference-semantics. Trigger KEEP BOTH (deciso con AskUserQuestion: la clausola
   H1/H3 e' l'unico segnale per un doppio-book compensato che B-ROOM non puo' riparare).
3. **DoD verde**: /simplify applicato (javadoc reorder, costanti callback, fix wording stale,
   dedup test); /code-review high run (Important #1 diagnostic-throw fixed, #2 L1 diagnostic test
   deferred a run3); test.sh EXIT=0 (934/0/0). Nuovo L0 `RepairGuardRevertTest` (6 test).
4. **RUN3 (source=input post-cura + cura-473)**: ciclo COMPLETATO. AnchorRepair clean (no revert,
   no abort), B-ROOM 604s, **-6hard (3 unassigned = residuo atteso TASK-435; 0 H20/H29/holes)**,
   ricorrenza unpinned 75,9%. L'abort e' GONE. Capture: `task452-results/verify-source-input-run3/`.
   Verdetto: `verify-source-input-run3/VERDICT-2026-08-01.md`.
5. **TASK-472 (durante il solve)**: script `scripts/analyze/block_size_report.py` (sibling T16
   3h-non-6, commit `9b45ded1`, pushato). Report ripetibile block-size >N h; sanity golden: 8003
   blocchi, 302 >3h (3,8%), residual 605. TASK-472 resta aperto (perimetro ampio, DoD a chiusura).

## CONFRONTO A 3 (dai raw)

| metric | run3 (input post-cura+473) | paired-golden-run1 | run1 (input pre-cura) |
|---|---|---|---|
| hard | -6 (H0: 3 unassigned) | 0 | -28 (H0 7 + H20 14) |
| H20 | 0 | 0 | 14 |
| room-null | 0 (B-ROOM girato) | 0 | 0 |
| ricorrenza unpinned | 75,9% | 75,2% | 75,7% |
| full-day ex-G14 | 85,2% | 90,2% | 87,0% |
| buchi | 0 | 0 | 0 |

## STATO RUN (app source=input post-cura+473, PID defunto, profilo production popolato)
- App: il solve e' finito (cycle finished 10:46). Il profilo `production` ha la soluzione run3
  persistita (per leggerla senza rewipe: modalita' update, NON `delete production`).
- HEAD `feature/cartesian-block-entity` con cura 452 + 473 + script 472 (pushato).

## PROSSIMI PASSI (decide maintainer)
- **Il SI di maintainer sul flip m-13.** run3 e' valida e batte il golden sulla ricorrenza; il trade
  e' full-day 85,2% (costo source=input). Dichiarazione solo, nessuna adozione fatta.
- (Opzionale) confermare meccanicamente la famiglia della violazione run-b (H-COPRESENCE) con un
  run `-Dplanner.phase-transition-dump.enabled=true` su java -jar: non serve per il flip (il revert
  e' family-agnostic), solo per chiusura archeologica.
- (Opzionale) L1 diagnostic test differito (worsenedFamilies con snapshot reali): la run3 non ha
  esercitato il path revert (violazione stocastica); il try-catch + L0 lo coprono.

## REGOLE STANDING (invariate)
Nessuna adozione, default golden, verdetti dai raw (analysis.json/KPI/score.txt), tempi assoluti,
-D sulla riga java -jar (PLANNER_JAVA_OPTS, non MAVEN_OPTS). Punto di ripresa: questo handoff +
`verify-source-input-run3/VERDICT-2026-08-01.md` + TASK-473 (completed) + run3 capture.

## DEFERRED (annotati in TASK-473)
- L1 diagnostic test (worsenedFamilies con ConstraintMatchSnapshot reali): non unit-exercised,
  try-catch lo rende safe, run3 integrazione.
- [REVERTED] suffix consistency per LabDay/Surplus: il WARN condiviso li copre.
