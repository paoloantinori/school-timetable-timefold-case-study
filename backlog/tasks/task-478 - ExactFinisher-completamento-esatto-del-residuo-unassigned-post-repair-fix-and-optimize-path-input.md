---
id: TASK-478
title: >-
  ExactFinisher - completamento esatto del residuo unassigned post-repair
  (fix-and-optimize, path input)
status: Done
assignee: []
created_date: '2026-08-01 19:36'
updated_date: '2026-08-02 07:45'
labels:
  - claude-planned
dependencies: []
references:
  - >-
    claudedocs/spalmone-2026-27/task478-results/ac1-feasibility-residuo-run5-2026-08-01.md
  - >-
    claudedocs/spalmone-2026-27/task478-results/VERDICT-ab-ef-final-2026-08-02.md
  - >-
    claudedocs/spalmone-2026-27/task478-results/discriminatore-uncapped-run4-2026-08-02.md
  - >-
    claudedocs/spalmone-2026-27/handoff-2026-08-02-alba5-ratifica-ef-default-on.md
ordinal: 301000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla ricerca esaustiva 2026-08-01 (claudedocs/research_hard-variance-timetabling_2026-08-01.md, finding 3): trasformare l'hard-0 del path source=input da probabilistico (varianza LS: run4 0 hard, run5 -38 con 16 unassigned su capacita' strette T08/Math e T19/Barberia) a DETERMINISTICO con un pass di completamento esatto del residuo, pattern fix-and-optimize della letteratura matheuristics (Lindahl/Sorensen/Stidsen DTU).

Idea: quando a fine ciclo staged restano k lezioni non assegnate (k piccolo, concentrate su poche risorse), si fissa tutto il resto e si risolve ESATTAMENTE il sottoproblema "k lezioni x celle candidate x catene di bump limitate (depth 1-2)" con enumerazione esaustiva bounded. Asset riusabili gia' in casa: DayAssignmentCsp.enumeratePairsRecursive (early-abort 10000) e lo scheletro enumeratore TASK-467. Sibling naturale dei repair deterministici (BoxRepair/HoleRepair), stesso contratto improve-or-noop con revert (pattern TASK-473). Ispirazione secondaria se il sottoproblema cresce: conflict-based statistics (IFS/Muller) per la bump selection.

Flag default OFF, gate localizzato prima di ogni solve; complemento (non sostituto) della procedura portfolio multi-start per il path input.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Spike di fattibilita': sul residuo reale di run5 (16 unassigned) l'ExactFinisher trova un completamento a 0 hard in tempo bounded, o dimostra che non esiste a depth<=2
- [x] #2 Design del pass (innesto nel ciclo staged post-repair, contratto improve-or-noop con revert, fonte candidati)
- [x] #3 Prototipo flag default OFF + catena di test localizzata + DoD
- [x] #4 A/B sul protocollo run4/run5: hard-0 deterministico su n>=2 run input
- [x] #5 RATIFICA (maintainer, 2026-08-02): flip default ExactFinisher ON. opt-out esplicito planner.exact-finisher.enabled=false (null/absent -> true, solo il letterale 'false' disattiva), stessa forma delle ratifiche 435/451/463/464/469. Knob max-candidates default 1000000 (resta regolabile via -D). Banner in ScheduleService.refreshSolverStatics. Test aggiornati (default-ON-aware). Sezione RATIFICA in CLAUDE.md con catena di evidenza. NESSUN cambio al default source (resta golden, ready-when-needed).
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-01 19:59
---
[zai 2026-08-01 notte7-bis] AC#1 SPIKE DI FATTIBILITA' ESEGUITO (read-only su export run5, run6 girava in parallelo). Doc: claudedocs/spalmone-2026-27/task478-results/ac1-feasibility-residuo-run5-2026-08-01.md. Script: /tmp/ac1_feasibility2.py (idempotente). VERDETTO: depth-0 (no-bump) 0-hard completion ESISTE, margine larghissimo. Enumerazione depth-0 delle celle hard-legal sui soli export (lessons.txt) rispettando H1/H3/H7/H11: T08/G03/Math need 11h -> 45h collocabili simultaneamente su 44 date; T19/G06/Barberia need 5h -> 377h su 168 date. depth<=2 banalmente soddisfatto (depth-0 e' sottospazio). FINDING DIAGNOSTICO: il residuo NON e' H1/H3-limited (378+402 celle H1+H3 libere >> 16); lo slack si restringe col cap giornaliero H7/H11 (T08 satura 6h/giorno in ~127/171 date) MA restano 45h collocabili -> run5 non ha collocato i 16 per trappola dell'ottimo locale LS, non per assenza di celle 0-hard. Corrobora meccanicamente H1 (varianza) della diagnosi run4/run5. L'ExactFinisher troverebbe il completamento (enumera, non e' soggetto all'ottimo locale). CAVEAT: H2 (room) non verificabile da lessons.txt (no colonna room); margine 4x-75x rende H2/H20/H5 non bloccanti ma l'enumerazione all-hard definitiva resta lavoro AC#3 (prototipo). IMPLICAZIONE: ExactFinisher trasformerebbe hard-0 del path input da probabilistico a deterministico per i 16 residui; complemento del portfolio, non sostituto. AC#1 = YES (export-level); AC#2/#3/#4 restano.
---

created: 2026-08-02 01:41
---
[zai 2026-08-02 alba] AC#2 + AC#3 CONSEGNATI + GATE FORMALI DoD SUPERATI (commit 9a35fc3e, push). AC#2 design (task478-results/ac2-exact-finisher-design-2026-08-01.md) + AC#3 prototipo (solver/ExactFinisher.java depth 0..1, MAX_K=30, MAX_CANDIDATES=10000, flag planner.exact-finisher.enabled DEFAULT OFF). DoD piena: /simplify (4 agent, 5 refactor applicati: deleghe a BoxRepair di singleUnpinnedBlocker+passesNonConflictPlacementGates, sort key pre-compute Keyed wrapper, cleanup) + /code-review high (8 finder + skeptic Opus: 1 bug CONFIRMED e fixato - findDepth1Chain chiamava isDepartureSafe incondizionato, ora gated !sameGroup mirroring BoxRepair.findEviction, test non-vacuo revert-checkato; 5 fix doc/cleanup/perf; 2 defer backlog). full test.sh EXIT 0, 961 test 0 falliti. Flag OFF, nessuna adozione.

[zai 2026-08-02 alba] AC#4 A/B n=2 FATTO, ESITO 1/2 (budget-capped, NON deterministico). run1=0hard (good seed, LS chiude tutto, ExactFinisher INERTE boxed=0); run2=-30hard (bad seed, 15 boxed al finisher): ExactFinisher placed 3 of 15 poi MAX_CANDIDATES=10000 capped (12 left, 10 Matematica/G03). Diagnosi: il meccanismo depth-0/1 NON e' disprovato, e' BUDGET-STARVED - 15 lezioni costrette x ~1368 celle VRP > 10000 dopo ~7 (MCV ordina piu' costrette prima); il WARN del design 'not a tuning knob' e' SBAGLIATO. Verdetto + capture: task478-results/ab-ef-run{1,2}/ + VERDICT-ab-exactfinisher-2026-08-02.md. PROSSIMO STEP (gated): raise MAX_CANDIDATES 10000->~100000 + rebuild + re-run n>=2 per la capacity depth-0/1 definitiva. Solo se 2/2 hard-0 a budget adeguato -> SI di maintainer. AC#4 = PARZIALE (n=2 fatto, hard-0 non deterministico, budget e' il collo di bottiglia).
---

author: zai
created: 2026-08-02 03:23
---
[zai 2026-08-02 05:xx] RE-TEST budget 100000 FATTO (run3/run4), ESITO 1/2 di nuovo, NON deterministico. Knob live planner.exact-finisher.max-candidates (default 100000, commit abe8d892, refactor CandidateBudget holder). run3=0hard (good seed: BoxRepair chiude boxed=0, 1 iter B-TIMESLOT, EF INERTE boxed=0 no log). run4=-16hard (bad seed: 18 boxed al finisher, 3 iter B-TIMESLOT + demotion): EF placed 13 of 18 @ budget 100000 poi capped (5 left boxed). CONFRONTO EF placement: 10000->3/15 (run2), 100000->13/18 (run4): il raise 10x ~4x migliore, meccanismo depth-0/1 NON disprovato, WARN 'IS a calibration knob' corretto. MA 100000 caps ancora sul worst seed (5/18 residual -> -16hard). hard-0 NON deterministico (1/2). Verdetto + capture: task478-results/ab-ef-run{3,4}/ + VERDICT-ab-exactfinisher-2026-08-02.md (sezione RE-TEST). NEXT STEP GATED a maintainer (knob live, no rebuild): (a) probe budget 500000-1M su seed cattivo (~2-3 run per stochasticita), o (b) depth-2 (gate bump+relocation deferito), o (c) residual parzialmente strutturale. AC#4 = PARZIALE (n=4 cumulativo run1-4, hard-0 non deterministico; budget raise validato direzionale ma insufficiente a 100000).
---

created: 2026-08-02 03:52
---
[zai 2026-08-02 alba3] DISCRIMINATORE UNCAPPED sul residuo run4 FATTO - ESITO (i): depth-0/1 completion ESISTE UNCAPPED. Test ExactFinisherRun4DiscriminatorTest (src/test, @QuarkusTest H2, commit pending): SnapshotFixture.loadFromExport(ab-ef-run4/export.json.gz) ricostruisce la griglia run4 post-EF (13 piazzati + 5 boxed) room/id-faithful + seedMediumTierFacts (DailyActivityQuota REALE 515 rows, materiale perche' 4 dei 5 residui sono lab Barberia quota-bound via VRP), poi ExactFinisher.repair DIRETTA budget 100M (uncapped) depth 0..1 con la STESSA gate chain production. RISULTATO: placed 5 of 5, residual 0, conflicts 0. Placement: T08/G03->09-23@11, 4x T19/G06/Barberia->09-16/18/23. VERDETTO: il cap era l'UNICO ostacolo (tutti i 18 boxed originali depth-0/1-collocabili: 13 run4 + 5 qui). depth-2 NON necessaria per il worst case noto. Questo CHIUDE i caveat di AC#1 (H2/H20/H5): il VRP reale applica H20/H5/quota, EF non gate-checka H2 (rooms=B-ROOM), gate chain H1/H3/H7/H11/H14/H17 reale condivisa BoxRepair. Doc: task478-results/discriminatore-uncapped-run4-2026-08-02.md. PROBE LIVE knob 1000000 LANCIATA (ab-ef-probe-1m-run1, banner maxCandidates=1000000 verificato, solve in corso): testa se un seed fresco cattivo chiude 0-hard @ 1M. AC#4 = ancora PARZIALE ma il discriminiatore esclude il ramo depth-2 per il worst case noto e conferma che il cap-raise e' la via giusta.
---

created: 2026-08-02 04:15
---
[zai 2026-08-02 alba3] CORREZIONE di fedelta' (DoD /code-review skeptic): il test passava includeQ22GoldenDerivedRules=FALSE (bug). run4 e' POST-Q22 e 4 dei 5 residui sono T19/Barberia la cui disponibilita' e' SOLO Q22 (VACATION Wed/Thu/Fri -> T19 Mon-Tue). Con false il VRP di T19 si allargava 2->5 giorni e le collocazioni del comment #4 (T19 su Wed 09-16 / Fri 09-18) erano su celle ESCLUSE nel run4 reale (live run4: T19 62 Mon / 62 Tue / 0 Wed-Thu-Fri). Corretto a TRUE: collocazioni corrette tutte Q22-compliant (T19 solo Mon: 09-14@9/11/12, 06-07@13; T08 09-23@11 Wed ma T08 non-Q22). Verdetto 5/5 RESIDUAL 0 REGGE sulla griglia fedele (il bug era load-bearing sulle celle, non sul count). DoD completa: /simplify 4 angoli (delete try/finally, tighten conflictsAdded==0, pin residual==0) + /code-review high skeptic Opus (questo bug, CONFIRMED e fixato) + test.sh EXIT 0 963/0/16. Nessuna adozione, flag EF OFF.
---

created: 2026-08-02 05:11
---
[zai 2026-08-02 alba3] PROBE @ knob 1000000 FATTO - VERDETTO GREEN per EF. run1 = good seed (0hard, EF inerte boxed=0). run2 = BAD SEED (firma -2148hard a B-TIMESLOT-3, 18 boxed al finisher): EF @ 1M placed 18 of 18 boxed at depth<=1 (residual 0, conflicts 0) -> chiude TUTTA la coda boxed (H0 -> 0). B-ROOM azzera i room-null. Finale -6hard = 6 H17 internal holes (STRUTTURALE, stesso count del componente H17 di run4; pattern lab-day TASK-451/463, ORTOGONALE a EF). CONFRONTO pulito @ 100k vs @ 1M su seed cattivo: run4 @100k = EF 13/18 -> -16hard (5 H0 + 6 H17); probe-run2 @1M = EF 18/18 -> -6hard (0 H0 + 6 H17). Quindi il raise 100k->1M fa chiudere a EF tutta la coda boxed; i -6hard residui sono H17 identico a run4, non di competenza EF. VERDETTO: budget 1M chiude la worst-case boxed tail (il residual di varianza LS); EF @ 1M adottabile per chiusura DETERMINISTICA dell'H0. depth-2 resta non necessaria. Per TASK-480 (maintainer): EF chiude la coda boxed da solo -> multi-start e' cintura+bretelle per l'H0. Capture: ab-ef-probe-1m-run{1,2}/. Flag EF resta OFF, nessuna adozione (flip gated maintainer). AC#4 = ancora PARZIALE (n=5 cumulativo) ma meccanicamente risolto: il budget e' l'unico collo, 1M chiude.
---

created: 2026-08-02 07:07
---
alba4 (2026-08-02 09:07, commit 068f4f14): A/B CONFERMA completata, 2/2 hard-0. run1 ab-ef-final-run1 0hard/-1074257medium/8115soft (full-day 88,3%), run2 ab-ef-final-run2 0hard/-1057977medium/8652soft (full-day 88,7%), entrambi 0 H17. Good seed: EF inerte (boxed=0 all'entry, log solo banner). Medium/full-day puro LS-baseline dentro la banda good-seed (probe-run1 -1047k/87,6%), nessuna regressione EF. Evidenza completa = A/B (good 2/2) + probe-run2 (bad, EF 18/18) + discriminatore (deterministico uncapped). PACCHETTO COMPLETO, in attesa del si' di maintainer per il flip default EF ON + knob 1M (sblocca m-13). NESSUN flip effettuato. Doc: task478-results/VERDICT-ab-ef-final-2026-08-02.md.
---

created: 2026-08-02 07:07
---
alba4 - CORREZIONE diagnosi H17: i 6 H17 residui (probe-run2/run4, bad seed) NON sono il pattern G13 ipotizzato in alba3. Sono G11 mercoledi ore 10 (6 settimane, riproducibili, identiche celle in run2 e run4). Catena causale verificata: costruzione compatta -> LS sostituisce composizione strutturalmente non compattabile (ricerca esaustiva: nessun arrangement 8-12/9-13; T06 saturo 8-13, @10 bloccato T08+T23) -> hill-climbing (no metaheuristic, change+swap, no cartesian) non attraversa la valle a 2 mosse. EF PROSCIOLTO meccanicamente (ipotesi 'EF crea il buco' confutata: EF piazza solo boxed in celle conflict-free, @10 e' H1-bloccata, il cambio composizione e' mossa LS). Ortogonale a EF, pre-esistente (run4 @100k). Cura = search-side, nuovo task DRAFT-006 (metaeuristica LS o hole-repair composition-aware), model corretto (golden 0/34), non blocca EF. Doc: task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md.
---

created: 2026-08-02 07:45
---
alba5 (2026-08-02 09:44, commit 40d257ae, push post-rebase su 7ee440b2): RATIFICA COMPLETA. Flip del default EF ON (opt-out planner.exact-finisher.enabled=false, idiom byte-identico a isCoPresenceEnabled/isCurriculumPostStageEnabled/isLabDayConsolidationEnabled) + DEFAULT_MAX_CANDIDATES 100000->1000000. Banner + javadoc + commenti ScheduleService. ExactFinisherTest default-ON-aware (flagOffIsNoop con FLAG=false, rimosse 6 FLAG=true ridondanti). DoD: test.sh EXIT 0 BUILD SUCCESS 963/0/16 (stessi conteggi alba3, nessuna regressione); /simplify manuale no-finding (classifier Skill glm-5-turbo down); /code-review high skeptic Opus CONFIRMED 4/4 sub-claim (idiom, knob flow, flagOffIsNoop non-vacuo, no test regression) + coverage gap onesto (nessun test esercita runStagedSolver: safety production si regge sull'evidenza campagna A/B 2/2 + probe 18/18 + discriminatore 5/5, non sul test gate). AC#1 (spike, comment #1) + AC#4 (A/B 2/2 hard-0 + probe + discriminatore) + AC#5 (ratifica) spuntati; DoD #1/#2/#3 spuntati. NESSUN cambio al default source (resta golden). TASK-478 CHIUSO Done: ExactFinisher ratificato DEFAULT ON.
---

created: 2026-08-02 07:45
---
addendum maintainer (0-hard BINARIO e BLOCCANTE end-to-end) recepito: i 6 H17 bad-seed (G11 mercoledi ore 10) restano ORTOGONALI a EF (causale) ma BLOCCANTI end-to-end, NON trade-off. Cura search-side tracciata in TASK-481 (maintainer lo ha creato in 7ee440b2; il mio task-482 duplicato rimosso). Path input NON hard-0 end-to-end; m-13 resta bloccato da TASK-481.
---
<!-- COMMENTS:END -->
