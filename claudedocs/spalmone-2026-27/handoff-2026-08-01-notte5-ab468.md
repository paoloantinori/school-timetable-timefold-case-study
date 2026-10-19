# HANDOFF notte5 01/08 (zai) - TASK-468 AC#1-4 done, A/B ON completato (peso 1 troppo debole)

HEAD `feature/cartesian-block-entity` pushato fino a `38d21e87`. Programma in 4 parti TUTTO
COMPLETO. Parte 1 (AC#3), 2 (A/B), 3 (referente), 4 (bucket C): DONE e pushate.

**HEADLINE A/B (parte 2)**: M-TR a peso 1 e' troppo debole. Run ON = -2hard (1 H0 unassigned, non
strutturale), M-TR residuo **1694 gruppi vs golden 440 runtime = ~4x**. M20 (1000-4500/ora)
sovrasta il peso 1: il solver impacchetta la teoria in 2h molto piu' del golden (che la spalma a
1h/giorno). Self-gating del design (~432) NON tiene a peso 1. Costo KPI neutro-varianza, nessuna
regressione strutturale. Verdetto: `task468-results/VERDICT-ab468-2026-08-01.md` + capture
`ab-on-run1/`. **NESSUNA adozione** (flag 468 DEFAULT OFF). Per renderlo effettivo: alzare il
knob del peso finche' il residuo si avvicina a 440, poi A/B (costo M15/M20 da misurare).

## Commit (tutti pushati a 4b5f3ca0)
1. `c9708097` feat(task468): M-TR theory-adjacent prototype (AC#3), flag default OFF
2. `5fc98443` docs(referente): pacchetto pronto da inviare (rotazione + Q19/Q20 + T1-T8)
3. `4b5f3ca0` docs(part4-bucket-c): verdetto + TASK-469 T11 (gated referente)

## PARTE 1 - TASK-468 AC#3: DONE
Prototipo M-TR "theory subject repeated same day" dietro flag
`planner.constraints.theory-adjacent.enabled` DEFAULT OFF, per design a88c23eb. Forma soglia-1
(M21-style a soglia 1): ofMedium(knob default 1) x (ore-1) su (gruppo, materia-teorica, giorno)
2+ ore. isTheoryForRule = !isLab() && !THEORY_RULE_PRACTICAL_EXEMPT (8 materie pratico-tecniche,
nomi DB autoritativi). Pseudo esente. Alias T06 = hook inerte. Test TheoryAdjacentConstraintTest
(10 test). DoD completa: /simplify (4 agenti, 2 fix), /code-review high (2 reviewer, 0 bug codice),
test.sh EXIT 0 (925 test). AC#3 spuntata nel task, DoD checkata.
- Constraint count 59 -> 60. GoldenSolutionRegressionTest verde (flag OFF inerte).

## PARTE 3 - referente doc: DONE
`claudedocs/spalmone-2026-27/referente-pacchetto-da-inviare-2026-07-31.md`. Single file, executive
summary + rotazione + Q19/Q20 + T1-T8. NESSUN invio (resta a maintainer). Scelta scope: non fuso con
domande-brevi (domande gia' risposte/morte) ne' notifica-divergenze (artefatto separato).
Compagni se maintainer li vuole dentro.

## PARTE 4 - bucket C: DONE (no prototype, scelta motivata)
`part4-bucket-c-verdetto-2026-08-01.md`. Nessun prototipo flag-OFF giustificato dal bucket C:
(v) stage no-lunedi' 100% rispettata ma esclusa dal programma (H25 copre); (iii) T11
rispettato su G10 ma NON al 100% (G08 off-by-one 5h, G11 scope diverso) -> prototipo ora
violerebbe golden-first. Scelta: TASK-469 (T11 gated referente T4, prototipo post-rispista).
- NOTA: il doc referente e il doc bucket-C contengono gia' le domande referente (T1-T8). TASK-469 formalizza il lavoro post-referente.

## PARTE 2 - A/B ON: IN CORSO (l'unico punto aperto)
Setup identico a combined-run2 (profilo production fresco via java -jar drop-and-create +
import-calendario + pre-assign) + flag 468 ON. Flags su riga java -jar via PLANNER_JAVA_OPTS
(verificati sul cmdline: co-presenza, lab-day-consolidation, theory-adjacent = true). Seed fresco
verificato (DataGenerator 266 corsi, 2320 stage stub). Pre-solve M-TR gia' firing (-48 medium,
24 match sui pinned) = flag attivo confermato.
- solve-staged lanciato ~00:31. Stato a 01:04: `stagedStage: B-TIMESLOT-3`, hard -1845 (room-null,
  atteso pre-B-ROOM, NON morte), medium -855318. Stages: CONSTRUCTION -> PROMOTION -> B-TIMESLOT-1/2/3
  -> RELEASE -> HoleRepair -> AnchorRepair -> SurplusRebalance -> B-ROOM. B-ROOM non ancora iniziato.
  Più lungo di combined-run2 (~35min), probabilmente per il termine M-TR che sposta il landscape LS.
- Baseline OFF = `task463-results/combined-run2/` (0hard/-994722medium, hard 0 verificato, 0 SENZA
  AULA). NON rifare.

### Come riprendere la parte 2 (solve in corso o da ri-lanciare)
1. Poll: `./scripts/api/api.sh status | grep stagedStage`. Se non-null, solve ancora attiva (solverStatus
   NOT_SOLVING e' il gotcha, NON morte). Se null -> done. Se l'app e' stata restartata/killata nel
   frattempo, il solve e' perso: ri-lanciare con `PLANNER_JAVA_OPTS="-Dplanner.co-presenza.enabled=true
   -Dplanner.construction.lab-day-consolidation.enabled=true -Dplanner.constraints.theory-adjacent.enabled=true"
   ./scripts/core/prod-start-and-wait.sh 300` poi import-calendario + verify-baseline + pre-assign + solve-staged.
2. Quando done: `./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/task468-results/ab-on-run1`
3. **Verdetto 3 righe dai raw** (ab-on-run1 vs combined-run2):
   - **hard**: resta 0? (boxed + room-null). Se >0, regressione strutturale.
   - **M-TR residuo** vs **baseline 440 runtime** (NON 432! vedi nota): il run-ON avra' un residuo
     M-TR; confrontarlo col golden 440 dice se il peso e' accordato. Score M-TR e' in analysis.json
     (`M-TR - Theory subject repeated same day`).
   - **costo sugli altri KPI**: full-day (kpi-fill), ricorrenza unpinned (kpi-resemblance), day-start
     (kpi-daystart), M15/S17 score (analysis.json). Confronto vs combined-run2.
4. 2° run ON autorizzato solo se il 1° e' ambiguo/interessante.
5. Commit+push del verdetto in `task468-results/VERDICT-ab468-2026-08-01.md`. Spunta AC#4 nel task.
6. **NESSUNA adozione**: flag 468 resta DEFAULT OFF. Verdetto = misura del meccanismo.

### NOTA CRITICA: baseline M-TR = 440 runtime, NON 432 grezzo
AC#1 ha misurato il CSV grezzo (432, con "RA" esente). Il vincolo gira sul golden IMPORTATO dove
le 41 righe raw "RA" di G08 sono rinominate a "Recuperi ed Approfondimenti" da
GoldenScheduleImportService (+8 giorni-2+). Baseline corretta per l'A/B = **440**. Verificato
meccanicamente (re-measure con rename). Registrato in CLAUDE.md (riga M-TR) + commento TASK-468.

## Regole standing rispettate
- Flag 463/464/468 tutti DEFAULT OFF, nessuna adozione.
- commit+push a ogni chiusura (c9708097, 5fc98443, 4b5f3ca0).
- Verdetti dai raw (pre-solve M-TR -48 medium verificato dal analysis.json).
- Tempi assoluti (solve started 00:31, B-TIMESLOT-3 a 01:04).
- DoD completa su parte 1 (l'unica con codice).
