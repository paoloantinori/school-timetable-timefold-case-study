---
id: TASK-610
title: >-
  Mosse multi-step DENTRO il solver (custom Move atomiche): la domanda riaperta
  di maintainer
status: To Do
assignee: []
created_date: '2026-09-13 08:31'
updated_date: '2026-09-15 05:29'
labels:
  - claude-planned
  - custom-moves
  - ls
  - rivisitazione
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DOMANDA RIAPERTA (maintainer, 13/09: 'non sono convinto'): possiamo introdurre mosse multi-step DENTRO il solver (custom Move atomiche evict+place/rotazioni) invece di lasciare i compositi solo ai finisher deterministici?

EVIDENZA PRO (dalla sessione 13/09): la classe composita esiste e vale hard (banco TASK-609: -11 -> -9 con una 2-mosse); l'LS con mosse singole non attraversa le valli; i bacini mid-cycle (H17 dei GRASP condannati, TASK-584: costruzioni stabili -9765) sono un problema REALE diverso dal residuo finale e sono territorio LS; Timefold 2.6.0 porta il paradigma iterator-based per Neighborhoods e selezione piu' equa delle mosse custom (sinergia con TASK-606).

EVIDENZA CONTRO (da superare, pre-registrata): il residuo consegnato si forma DOPO l'ultima LS (wiring verificato): mosse composite nella LS non lo toccano; il протotipo vincente e' una scansione deterministica mirata (secondi) con oracle improve-or-noop, inerte senza residuo; una Move atomica ricca costa valutazioni per step nella hot path; il decider MT-LS e' reflection-pinned a 2.3.0 internals (rischio wiring).

QUANDO RIAPRIRE: se il CompositeSwapFinisher (TASK-609) ristagna su classi di residuo che la scansione mirata non chiude, o se i bacini mid-cycle tornano prioritari (m-13: la p(0-hard) crollata rende i bacini IL problema).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Pre-registrato: la tesi contraria da superare (deterministica-auditabile vs stocastica; il costo di valutazione delle mosse composite per step; l'interazione col decider MT-LS reflection-pinned)
- [ ] #2 Spike: una custom Move atomica (evict+place) in un ramo usa-e-getta, su banco snapshot: misurare quanto vale nel PATH dove gira davvero (B-TIMESLOT) e se il suo delta si vede nelle consegne
- [ ] #3 Valutare l'upgrade Timefold 2.6.0 (TASK-606) come abilitatore: Neighborhoods iterator-based + selezione piu' equa delle mosse custom
- [ ] #4 Verdetto con predizione scritta PRIMA; nessuna adozione senza gate
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
EVIDENZA NUOVA 14/09 (Fedora, post TASK-608/609/612): la domanda di maintainer 'il solver sa fare mosse multi-step?' ha adesso una risposta misurata su DUE livelli. (1) IL CENSUS: le 40 mosse vincenti del cieco decompongono in RELAY-k (k=2..3) per l'82% del lavoro (RELAY-2 x11, RELAY-3 x2, SAME-DAY-MULTI x2 su 20 mosse strutturali + i fill); ogni mossa singola del repertorio LS e' non-migliorativa su questi stati (COMPOSITE_MISSING dimostrato). (2) LA MECCANIZZAZIONE: le stesse catene sono ora pass deterministici (CompositeSwapFinisher RATIFICATO, EDF relay-fill TASK-612) con oracle improve-or-noop a blocco: il 'custom Move atomico' di questa domanda ESISTE gia' in forma finisher, non in forma Move-listing. RESTA APERTA la forma originaria (Move composita dentro la LS, es. cartesian/pillar custom-key): varrebbe solo dove un processo iterativo puo' trovare catene che il pass deterministico non enumera (spazio oltre depth-3 / crie non localizzate). Il punto di forza attuale contro: nessuna LS gira sullo stato consegnato (phase mismatch, verificato nel wiring) quindi una Move composita li' non verrebbe mai valutata; il posto dove una LS con mosse composite POU' valere e' mid-cycle (TASK-611). RACCOMANDAZIONE: tenere il revisit congiunto a 611: un'unica decisione 'LS composita mid-cycle' copre entrambe.

ESTENSIONE (maintainer, 15/09 notte: 'provare a potare queste mosse e usare la nuova neighbor API di Timefold'): le famiglie meccanizzate nei finisher (RELAY-k di TASK-612, ROTATION-WITH-PERMISSION di TASK-613) hanno ora gate e census collaudati (VRP-fresco pre-mossa, H1-NET con esclusione batch, rooms post, tail-only, depth cap): QUELLA è la potatura. Il passo che manca e che questo task deve coprire: portare le stesse famiglie come MOVE/Neighborhood custom dentro la LS usando la nuova Neighborhood API iterator-based di Timefold 2.6+ (TASK-606 come abilitatore), coi gate come move-filter (il pruning diventa selezione, non enumerazione), cosi' la ricerca puo' trovarle MID-CYCLE invece che solo allo stato consegnato. L'evidenza che giustifica il rilancio: v0.3 census H1NET=91/DEPTH=66 mostra che l'enumerazione deterministica esplode sulla cascata mentre una LS guidata la attraverserebbe (la soluzione cieca la trova in 13 mosse).

DECISIONE (maintainer, 15/09 mattina): l'upgrade Timefold si fa ALL'ULTIMA VERSIONE, e va nel worktree di 610 come primo atto. Verifica Maven Central (metadata 1/9/2026): l'ultima stabile pubblicata e' **2.6.0** (dopo 2.5.0; nessuna 2.7+ esiste). TASK-606 quindi resta corretto nel target: 2.3.0 -> 2.6.0. Worktree pianificati: wt/610-moves-ls (primo atto: spike upgrade pom a 2.6.0 + compile + suite; poi la Move composita con la Neighborhood API iterator-based) e wt/615-guide-a-monte. Test pesanti serializzati/laptop (direttiva heap-3g).
<!-- SECTION:NOTES:END -->

WORKTREE CREATO (15/09): `wt/610-moves-ls` da 1bf70bc1. Timefold main CLONATO e buildato in locale (`/home/user/data/repo/personal/timefold-solver`, 999-SNAPSHOT in ~/.m2, include il PR 2610 coarse-grained neighborhoods MERGED 8/9/2026, DOPO la 2.6.0: NON in nessuna release; Central si ferma a 2.6.0, nessuna 2.7 rc pubblicata, nessun snapshot repo pubblico). Il worktree punta a 999-SNAPSHOT: pom flipato, compile + provider 315/0 VERDI su Timefold main. API preview trovata nel jar: `ai.timefold.solver.core.preview.api.neighborhood.stream` = MoveStreamFactory / MoveStream / MoveIterable / UniNeighborhoodsMapper / UniNeighborhoodsPredicate + datasets (uni/bi), la superficie per esprimere le catene ROTATION-WITH-PERMISSION come filtri della LS. PROSSIMO UNIT: draft della composite Move (rotazione+slide con i gate collaudati come predicate) misurata nel B-TIMESLOT path.

ATTENZIONE STATO WORKTREE (15/09 ore 3:15, accorgimento post-verifica): il commit 72d82bb5 del worktree ha il MESSAGGIO del flip ma il pom contiene ancora 2.6.0 (verificato con git show HEAD:pom.xml): il flip non e' mai atterrato nel commit (probabile auto-commit con messaggio generato su contenuto parziale, piu' un revert successivo non tracciato). PRIMA MOSSA alla riapertura: rifare il flip (sed 2.6.0 -> 999-SNAPSHOT in pom.xml del worktree), verificare con grep, committare con messaggio esplicito. Il jar 999-SNAPSHOT e' gia' in ~/.m2 (build timefold main verificato, PR 2610 incluso: classi preview/api/neighborhood presenti) e la stessa build e' rifacibile in 5 minuti (timefold-solver clone gia' presente in /home/user/data/repo/personal/timefold-solver, ./mvnw -DskipTests install).
