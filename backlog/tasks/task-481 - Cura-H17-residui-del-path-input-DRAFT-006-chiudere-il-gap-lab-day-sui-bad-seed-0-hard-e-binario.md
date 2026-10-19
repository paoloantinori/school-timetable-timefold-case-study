---
id: TASK-481
title: >-
  Cura H17 residui del path input (DRAFT-006) - chiudere il gap lab-day sui bad
  seed: 0-hard e' binario
status: Done
assignee: []
created_date: '2026-08-02 07:26'
updated_date: '2026-08-02 16:08'
labels:
  - claude-planned
dependencies: []
references:
  - >-
    claudedocs/spalmone-2026-27/task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md
  - >-
    claudedocs/spalmone-2026-27/task478-results/ab-ef-probe-1m-run2/kpi-holes.txt
  - src/main/resources/solverConfig.xml
  - >-
    claudedocs/spalmone-2026-27/task478-results/VERDICT-ab-ef-final-2026-08-02.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Direttiva maintainer 02/08: qualunque hard>0 e' BLOCCANTE end-to-end, indipendentemente dalla grandezza. I 6 H17 residui osservati sui bad seed del path source=input (probe-run2 e run5: buchi verticali, diagnosi nella famiglia lab-day/consolidamento, cura abbozzata come DRAFT-006 nei doc task478/452) NON sono un residuo accettabile: sono l'ultimo difetto aperto tra il path input e l'hard-0 deterministico pieno.

Perimetro: partire dalla diagnosi H17 esistente (task478-results, diagnosi probe-run2), consolidare DRAFT-006 in un design eseguibile (estensione del coordinatore lab-day 463? placement EF compactness-aware? repair dedicato?), prototipo flag-gated con catena localizzata, A/B su bad seed riprodotto. Obiettivo di accettazione: path input a 0-hard pieno su n>=2 includendo almeno un bad seed (H0 chiuso da EF ratificato + H17 chiuso da questa cura).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Design DRAFT-006 consolidato in proposta eseguibile con meccanismo identificato
- [x] #2 Prototipo flag-gated + test localizzati + DoD
- [ ] #3 A/B con bad seed: path input 0-hard pieno (H0+H17) su n>=2
- [x] #4 Cura search-side only: il modello H17 non si tocca (direttiva primaria, golden 0/34).
- [x] #5 Nessuna regressione su good seed (run1/run3: 0 H17 resta 0) e sugli altri vincoli/KPI di guardia entro varianza.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Diagnosi meccanicamente verificata link-by-link (golden 0/34, construction compatta, composizione finale non compattabile, EF prosciolto)
- [ ] #2 Attribuzione causale EF: ortogonale (non crea/non corregge)
- [ ] #3 Cura non tocca il modello H17 (search-side only)
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AGGIORNAMENTO alba5 2026-08-02 (verifica + spike completate, vedi `claudedocs/spalmone-2026-27/task481-results/PROPOSTA-cura-h17-2026-08-02.md`):

CORREZIONE FATTUALE alla diagnosi alba4: diceva "no metaheuristic, hill-climbing default, change+swap only, zero cartesian; cura = switch a tabu/late-acceptance". FALSO. `solverConfig.xml` Phase 2 ha `<acceptor><entityTabuSize>7>` (TABU search attivo, non hill-climbing), `<forager><acceptedCountLimit>1000>`, e QUATTRO selettori (change, swap, pillarChange, pillarSwap). Il solver staged B-TIMESLOT carica quell'XML (`ScheduleService.timeslotPhasesConfig` via `createFromXmlResource`). Tabu+pillar GIA' girano e lasciano comunque il buco. La cura "aggiungi metaeuristica" e' doppiamente falsa: (a) c'e' gia', (b) la strategia di accettazione non allarga il vicinato delle mosse, quindi non compone l'uscita a 3-mosse.

SPIKE OFFLINE (`H17HoleCloseDiscriminatorTest`, gated PLANNER_TEST_H17_HOLE=1, export bad-seed fedele H1/H3=0 H17=-6 exact): 1-move fill 0/6, 2-move evict+fill 0/6. Cross-check raw: i 10 docenti d'aula di G11 {T05,T04,T11,T08,T06,T25,T23,T21,T03,T02} sono tutti occupati @10 ogni mercoledi' del buco; i 3 liberi (T12/T22=LAB, T24=sparse) hanno @10 fuori VRP. La cella @10 e' saturata di docenti d'aula: il buco e' HARD-FORCED nel vicinato 1/2-mosse del bad-seed. Chiuderlo richiede una cascata cross-gruppo a 3+ mosse (libera un docente @10 spostando la lezione di altro gruppo + riempi + backfill).

COSA REGGE della diagnosi alba4: attribuzione causale (artefatto solver, non pratica scuola; EF ortogonale; golden 0/34; un arrangement compatto esiste in costruzione+golden). COSA NON REGGE: descrizione config + cura derivata.

FORK (4 direzioni, scelta Fedora/maintainer, vedere PROPOSTA): A composition-aware hole-closer deterministico profondita' 2-3 (RACCOMANDATA se single-run 0-hard obbligatorio, search-side only, sibling EF); B selettore mosse coordinato (non-deterministico); C defer a multi-start TASK-480 (se multi-start accettabile; problema e' strutturalmente di bacino, good seed run1/run3 = 0 buchi); D pin composizione compatta (sconsigliata, re-introduce H1). Fermo in attesa di decisione: le opzioni si escludono a monte sul requisito single-run-determinismo vs multi-start.

AGGIORNAMENTO alba6 2026-08-02: FORK a Fedora dopo STOP onesto (mandate #3). La 'GREEN' del gate depth-3 (alba5, 6/6) era un FALSO POSITIVO: la probe accettava con net H17 < 0, condizione troppo debole. Ristretta (close sostenibile = bloccante edge-removable), il verdetto e' SUSTAINABLE 0/6, SHELL-GAME 6/6. La cascata depth-3 riempie @10 ma il bloccante ricollocato e' mid-run sul suo giorno: il buco @10 si sposta di gruppo, non si chiude. Il -1 netto veniva da un fill non correlato (la destinazione del bloccante riempie un gap altrove). Diagnostica decisiva (diagnoseBlockerDeparture): tutti i 66 bloccanti @10 (11 gruppi x 6 mercoledi) sono midRun=true, giorni packati [0..5]. Nessun bloccante edge => nessuna close sostenibile depth-3; @10 e' uno slot saturo e una cascata iterativa ciclerebbe senza azzerare H17. E' un artefatto di bacino (good seed run1/run3 = 0 buchi). FORK: C multi-start TASK-480 (leading, bacino), E costruzione composition-aware (rischio H1), F mossa globale (incerto). Doc: task481-results/FORK-shell-game-depth3-2026-08-02.md. CompositionHoleCloser committed default-OFF (corretto, inerte sul target, gate isDepartureSafe rifiuta lo shell-game). AC#3 (A/B 0-hard) NON metto (depth-3 refutato): task resta aperto in attesa di decisione Fedora.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-02 08:15
---
alba5 2026-08-02: verifica EF (in cassa, ortogonale H17) + diagnosi H17 + spike offline completate. La diagnosi alba4 aveva un errore fattuale di config (tabu+pillar+accepted-forager SONO gia' attivi in Phase 2, non hill-climbing). Spike `H17HoleCloseDiscriminatorTest`: buco @10 hard-forced nel vicinato 1/2-mosse del bad-seed (cella @10 saturata di docenti d'aula G11). La cura NON e' banale: proposta con fork A/B/C/D in `claudedocs/spalmone-2026-27/task481-results/PROPOSTA-cura-h17-2026-08-02.md`. Fermo in attesa di decisione (single-run determinismo vs multi-start), come da direttiva 'fermati alla proposta se grosso/ambiguo'. Spike test + proposal committed; nessun codice di cura scritto.
---

created: 2026-08-02 14:16
---
alba6 2026-08-02: STOP onesto + fork a Fedora. La cascata depth-3 e' uno SHELL GAME (probe ristretta: sustainable 0/6, shell-game 6/6; diagnostica: 66/66 bloccanti @10 mid-run, giorni packati). Il buco @10 e' un artefatto di bacino saturo, non chiudibile con cascata locale limitata. A/B annullata (mandate #3). CompositionHoleCloser committed default-OFF come sibling corretto (inerte sul target, nessun danno). Fork: C multi-start (leading) / E costruzione composition-aware / F mossa globale. Doc FORK-shell-game-depth3-2026-08-02.md. test.sh 967/0/0.
---

created: 2026-08-02 14:34
---
alba6 2026-08-02 (DoD sign-off): skeptic Opus (code-review high) = CONFIRMED sul claim 'pass corretto, 0-hard-additive (isSlotConflictFree + commitCrossDayMove live), refuse shell-game (isDepartureSafe, 66/66 mid-run refused), no-harm inert (0 mutation), two-move revert (blockerOrigin catturato pre-commit, revert simmetrico)'. Runner PlacementSnapshot + ensureImproveOrNoop = defense-in-depth. GAP onesto segnalato dallo skeptic: NESSUN test GREEN esercita il path successful-cascade (il bad-seed non ha bloccanti edge-removable, quindi commit-and-import verificato solo per code-reading, non per esecuzione). Condizione PRE-KEEP: se Fedora mantiene/estende CompositionHoleCloser, aggiungere un fixture sintetico con bloccante edge-removable che eserciti il cascade end-to-end. Per ora pass non-adopted default-OFF: gap di copertura documentato, non bug. DoD: /simplify fatto + test.sh 967/0/0 + code-review high CONFIRMED.
---

created: 2026-08-02 16:08
---
alba6-close 2026-08-02: CHIUSO Done con esito onesto. AC#1/#2/#4/#5 met (diagnosi link-by-link, prototipo CompositionHoleCloser+test, search-side only, nessuna regressione per costruzione default-OFF). AC#3 (A/B 0-hard) NON met e NON sara' met su questa linea: cura locale esclusa da evidenza (shell-game 6/6, bacino @10 saturo, 66/66 bloccanti mid-run). H17 DEFERITO a TASK-480 multi-start (decisione maintainer: OPZIONE C, via principale non cintura+bretelle). CompositionHoleCloser resta committed default-OFF come building block corretto. m-13 resta bloccato dai 6 H17 fino a esito TASK-480.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO con esito onesto: la cura LOCALE per H17 e' ESCLUSA dall'evidenza, H17 e' DEFERITO a TASK-480 (multi-start). CompositionHoleCloser RESTA committed default-OFF come building block corretto (inerte sul target saturo, 0 danni, serve al primo target non-saturo).

COSA E' STATO CONSEGNATO (DoD tutta fatta: /simplify + code-review high Opus CONFIRMED + test.sh 967/0/0):
1. Diagnosi H17 meccanicamente verificata link-by-link: il buco @10 di G11 e' un artefatto solver (golden 0/34 compatto), EF ortogonale (non crea/non corregge), costruzione compatta ma la LS introduce il buco sostituendo la composizione con una strutturalmente non compattabile. Correzione fattuale record alba4 (tabu+pillar+forager GIA' attivi in Phase 2, non hill-climbing).
2. CompositionHoleCloser (solver/, nuovo, default OFF opt-in -Dplanner.composition-hole-closer.enabled=true): sibling di LabDayConsolidation che generalizza la cascata cross-group ai giorni d'aula. CORRETTO (0-hard-additive, gate isDepartureSafe rifiuta lo shell-game, two-move revert, improve-or-noop) ma INERTE sul bad-seed (66/66 bloccanti @10 mid-run refused, 0 chiuse, 0 conflitti). Gate H17HoleCloseDiscriminatorTest pinna l'inertezza.
3. Fork doc task481-results/FORK-shell-game-depth3-2026-08-02.md con catena causale e opzioni superstiti.

PERCHE' AC#3 (A/B 0-hard) NON E' MET E NON SARA' MET SU QUESTA LINEA: la cascata depth-3 e' uno SHELL GAME (probe ristretta: SUSTAINABLE 0/6, SHELL-GAME 6/6). Riempie @10 (donor same-teacher) ma il bloccante ricollocato e' mid-run sul giorno del proprio gruppo: il buco @10 MIGRA di gruppo, non si chiude. Il netto -1 veniva da un fill non correlato. Diagnostica decisiva (diagnoseBlockerDeparture): tutti i 66 bloccanti @10 (11 gruppi x 6 mercoledi) sono midRun=true, giorni packati [0..5], bloccante @slot2 in mezzo. Nessun bloccante edge-removable => nessuna close sostenibile depth-3; @10 e' uno slot saturo (bacino) e una cascata iterativa ciclerebbe tra gli 11 gruppi senza azzerare H17.

DECISIONE FORK (maintainer 02/08 sera): OPZIONE C, multi-start vero (TASK-480) diventa la via PRINCIPALE per H17, NON cintura+bretelle. Il problema e' strutturalmente di bacino (good seed run1/run3 = 0 buchi @10, bad seed run2/run4 = saturano @10): 0-hard si raggiunge via selezione del bacino, non chiusura locale. CompositionHoleCloser resta committed default-OFF come scaffold corretto in attesa di un target non-saturo (condizione PRE-KEEP: se mantenuto/esteso, aggiungere fixture sintetico con bloccante edge-removable che eserciti il cascade end-to-end; gap di copertura documentato, non bug).

m-13 resta BLOCCATO dai 6 H17 fino all'esito di TASK-480. Nessun flip di default (source resta golden, CompositionHoleCloser resta opt-in OFF).
<!-- SECTION:FINAL_SUMMARY:END -->
