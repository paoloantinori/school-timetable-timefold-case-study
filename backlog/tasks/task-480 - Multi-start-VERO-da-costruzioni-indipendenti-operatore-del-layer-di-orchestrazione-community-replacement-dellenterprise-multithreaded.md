---
id: TASK-480
title: >-
  Multi-start VERO da costruzioni indipendenti - operatore del layer di
  orchestrazione (community replacement dell'enterprise multithreaded)
status: Done
assignee: []
created_date: '2026-08-02 04:10'
updated_date: '2026-08-02 19:58'
labels:
  - claude-planned
dependencies: []
ordinal: 303000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal verdetto n=3 del 02/08 (verdict-portfolio-n3): il portfolio DRAFT-005 e' REFUTATO perche' i candidati condividono costruzione+PROMOTION (7504 pin identici) e le LS convergono allo stesso ottimo locale. La teoria heavy-tails (research_hard-variance-timetabling_2026-08-01) resta valida ma richiede restart INTERI: costruzioni indipendenti (seed/ordine template diversi) -> cicli staged completi indipendenti -> pick best.

Design: operatore a livello di pipeline (famiglia TASK-479): N esecuzioni del ciclo staged con variazione della costruzione (seed della CH, ordine dei gruppi nel template, tie-break), sequenziali (community, un solo processo) o come N processi paralleli orchestrati da script (il vero replacement community dell'enterprise multithreaded/partitioned). Pick best su (hard, medium). Da confrontare con l'ExactFinisher (TASK-478): se il finisher chiude il worst case da solo, il multi-start diventa cintura+bretelle per i casi depth>1; se no, e' la via principale.

Nota canale: esiste gia' l'interesse backlog a reimplementare funzioni enterprise di Timefold; questo task ne e' l'istanza concreta per il multi-start. Nessuna adozione senza A/B con statistica n>=3.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Design: dove si inietta la variazione della costruzione (seed/ordine) e come si orchestrano N cicli con pick-best
- [x] #2 Prototipo flag/script-gated + A/B n>=3 con P(hard-0) misurata vs single-run e vs ExactFinisher
- [x] #3 Verdetto comparativo: multi-start vs ExactFinisher vs combinazione, con raccomandazione
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DESIGN + GATE (Fase 2, 2026-08-02) - AC#1 DONE:

Punti di iniezione (dal recon task480-recon-timefold-multistart-2026-08-02.md): la costruzione e' deterministica al 100% sullo stesso profilo (id-based hashCode, nessun Random). GRASP-RCL iniettato in WeeklyTemplateBuilder su DUE tie-break: (1) `startOffset` (weekday scan start per corso, riga ~334: `grasp.nextInt(5)` invece di `floorMod(hash,5)`); (2) `nextContiguousSlot` (free-slot pick: RCL full-size, random tra i liberi invece del primo). Knob LIVE `-Dplanner.construction.grasp.enabled` + `.seed`, default OFF = byte-identico (PlacementState.graspRandom null -> tutti i branch deterministici). Random seeded per-build (new Random(seed)), per-build risedato, niente static mutabile. Sia la costruzione (fase CH) sia il reference M32 (buildReference->plan->build) usano lo stesso WeeklyTemplateBuilder.build, quindi la variante si propaga a entrambi automaticamente.

GATE DI INDIPENDENZA (research finding 4a, costo ZERO solve ~12s): la cella @10 (Wed slot2, buco H17 di G11) e' CONSTRAINT-FORCED o DIVERSIFICABILE? Harness ConstructionIndependenceGateTest (gated PLANNER_TEST_GATE=1): template via buildReference su seed reale, baseline (GRASP off, riproducibile asserito) vs 5 seed GRASP.

VERDICT GATE: DIVERSIFIED. 5/5 teacher-multisets distinti, 13/14 gruppi variano il placement @10. Segnale specifico bad-seed: i 10 docenti d'aula di G11 sono prenotati @10 in sottoinsiemi DIVERSI per seed (seed=1 libera 4 docenti @10, seed=4 libera 5) -> costruzioni diverse lasciano liberi docenti diversi -> bacini LS diversi -> in alcuni bacini un docente libero @10 puo' tappare il buco. E' la precondizione che il portfolio DRAFT-005 refutato NON aveva (costruzione+promotion identiche). Doc: claudedocs/spalmone-2026-27/task480-results/VERDICT-gate-indipendenza-2026-08-02.md.

FASE 3 (prossima, solo perche' il gate passa): multi-start come SCRIPT DI ORCHESTRAZIONE N-PROCESSI + pick-best su (hard, medium). NESSUN refactor in-process (recon: 23 static volatile in SchoolSchedule vietano il parallelismo in-JVM; maintainer overrule il loop in-process). Ogni processo = java -jar con -Dplanner.construction.grasp.seed=i su profilo DB dedicato (per evitare la race sulle righe lesson), solve-staged, export. pick-best su N export. Procedura operativa, NESSUN cambio di default (source resta golden, GRASP resta opt-in). A/B n>=3 con bad seed incluso, verdetto P(almeno un run 0-hard) + wall-clock.

Piano B (NON ora): TASK-482 Kempe-chain se il gate falliva (non e' il caso) o se l'A/B mostra costo Nx inaccettabile.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-02 16:28
---
alba7 2026-08-02: gate di indipendenza PASSATO (DIVERSIFIED, 5/5 multiset distinti, 13/14 gruppi variano @10). PROCEED a Fase 3 N-processi. GRASP-RCL iniettato in WeeklyTemplateBuilder (startOffset + nextContiguousSlot, default OFF byte-identico, WeeklyTemplateBuilderTest 16/0/0). Gate harness ConstructionIndependenceGateTest (gated PLANNER_TEST_GATE=1). Verdetto: task480-results/VERDICT-gate-indipendenza-2026-08-02.md. Zero solve spesi.
---

created: 2026-08-02 19:58
---
alba8 2026-08-02: TASK-480 DONE. A/B completato (4 run, 8480s). 2/3 GRASP 0-hard, P(>=1|N=3)=0.96. Multi-start adottato come procedura (non default). DoD completa (simplify + code-review high + test.sh 968/0/0). Verdetto: VERDICT-fase4-multistart-ab-2026-08-02.md. Architettura: orchestration-layer-architecture.md sez 4.1 ADOPTED.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
TASK-480 DONE (2026-08-02, alba8). Multi-start validato e adottato come PROCEDURA operativa (NON flip di default) per il path source=input.

A/B (n=3 GRASP + baseline, source=input): 2/3 GRASP seeds a 0-hard (grasp-2, grasp-3); grasp-seed-1 nel bacino bad-seed (-11hard, H17 -10/10match, fenotipo G11-mercoledi, slot migrato @10->@11). p_hat(good)=0.667, Wilson 95% CI [0.21, 0.94]; P(>=1 0-hard | N=3)=0.96 [0.50, 1.00]. pick-best winner = baseline (0hard, best medium). Wall-clock 8480s (~2.35h sequenziale). Gate di indipendenza PASSATO (5/5 teacher-multisets distinti, 13/14 gruppi variano @10).

AC#3 (comparativo): multi-start ORTOGONALE e COMPLEMENTARE a ExactFinisher. EF chiude la coda boxed (H0) in modo deterministico ed e' meccanicamente ortogonale a H17 (la cella H17 e' H1-bloccata); multi-start seleziona un bacino buono tra N costruzioni per evitare che l'H17 si formi. Compongono: N costruzioni, ognuna con EF, pick-best.

LIMITAZIONE onesta: n=3 non dimensiona N in modo stretto (CI larga). La frequenza del bad-seed e' la misura differita (background, ~10-20 run, NON bloccante). Piano B strutturale = TASK-482 Kempe-chain (aprire solo se la stima differita mostra frequenza alta o N inaccettabile).

DoD completa: (1) /simplify 4-agent, 2 finding applicati (banner grasp=%b seed=%d + helpers public per silent-seed-drop observability; pick-best best_medium guard), 4 skippati con motivazione scritta; (2) /code-review high, 2 Important fixati e verificati (boot-timeout kill-app + trap INT/TERM/EXIT; pick-best first-run poisoning), reviewer ha confermato i 4 invarianti load-bearing (byte-identity, reservoir uniforme, no static Random, seed!=disabled); (3) test.sh EXIT=0, 968/0/0/21, BUILD SUCCESS.

Cambamenti codice: WeeklyTemplateBuilder graspEnabled/graspSeed private->public; ScheduleService.refreshSolverStatics banner con grasp=%b seed=%d; multistart-ab.sh (counting fix baseline-separato + pick-best winner per il bug del SUMMARY, + simplify guard, + code-review trap/kill-app/poisoning fix). NESSUN cambio di default: source resta golden, GRASP resta opt-in OFF.

Puntatori: VERDICT-fase4-multistart-ab-2026-08-02.md (verdetto + pacchetto operativo); orchestration-layer-architecture.md sez 4.1 (ADOPTED) e 4.3 (fork chiuso); scripts/benchmark/multistart-ab.sh; research_multistart-vs-alternative_2026-08-02.md; task480-recon-timefold-multistart-2026-08-02.md; VERDICT-gate-indipendenza-2026-08-02.md.
<!-- SECTION:FINAL_SUMMARY:END -->
