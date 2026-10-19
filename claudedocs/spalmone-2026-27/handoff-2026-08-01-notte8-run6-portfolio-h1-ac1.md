# HANDOFF 2026-08-01 notte8 (zai) - RUN6 portfolio=0hard (H1) + A1 T20 + AC#1 ExactFinisher + procedura portfolio

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `947c62c4` (notte 01/08 pushato: 4 punti
> mandato, tutti chiusi). **App UP su run6** (`java -jar` con `-Dplanner.portfolio.size=2
> -Dplanner.pre-assign.constrained-teacher.source=input`, log `/tmp/planner-prod.log`). **DB = run6 (0hard)**
> (12494 lessons, 0 unassigned, 3090 pinned). Container `planner-pg` PG17 up su volume `planner-data-production`.
> Verdetti dai raw. Regole standing invariate.

## MANDATO 4 PUNTI - TUTTI CHIUSI

### (1) PATCH A1 T20 moda - APPLICATA (maintainer approvato, evidenza riga 97)
- `DataGenerator.createLabAssistants`: +`createLabAssistant(T20,"moda")` dopo T14/moda. Commit `98efafc5`.
- Evidenza verbatim estratto `vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` riga 97: "T20 | Libero
  per doppio in 2 (100 ore) e g09 (85 ore) in R16". M-LA gestisce la co-presenza, nessun nuovo vincolo.
- DoD: full test.sh EXIT 0, BUILD SUCCESS, 0 failures. Registro autorizzazioni (memory `datagenerator-authorized-changes`) aggiornato.
- TASK-475: A1 CHIUSO (commento + DoD #3 spuntata, commit `eb4e8c09`); A2 (M coPresenceBudget, budget vincolanti) RESTA DESIGN GATED referente.

### (2) RUN6 DISCRIMINATORIO portfolio size=2 - H1 CONFERMATA, H2 REFUTATA
- run6 = solve-staged `-Dplanner.portfolio.size=2` + source=input, protocollo run4/run5. Capture `task452-results/verify-source-input-run6/`.
- **Score finale: 0hard/-1041543medium/3419soft** (unassigned=0). vs run5 single -38hard (16 stranded), run4 single 0hard.
- Portfolio: candidate 1 = -1831hard, candidate 2 = -1832hard (pre-B-TIMESLOT-ROOM, near-identical), picked 1 -> 0hard post-B-ROOM (604s). B-TIMESLOT 2005s (~33min, 2 candidati).
- **Verdetto (3 righe)** in `task452-results/verify-source-input-run6/VERDICT-2026-08-01.md`: H1 (varianza LS
  domabile col multi-start) CONFERMATA; H2 (tight-edge strutturale) REFUTATA. n=1 portfolio = treatment test
  (non statistica): single P(fail)~50% (run4/run5 n=2), portfolio 1/1=0hard -> serve n>=3 run per P(fail) statistica.
- Costo: portfolio size=2 ~44min ciclo (vs ~26min single); medium -3% (-1042k vs -1012k, search-tunable).

### (3) DOC PROCEDURA PORTFOLIO - SCRITTO (NON ratificato)
- `procedura-portfolio-path-input-2026-08-01.md`: come lanciare (java -jar + PLANNER_JAVA_OPTS, NON MAVEN_OPTS),
  N raccomandato (3, ~87.5% P(hard-0)), dove catturare (portfolio-poll-capture.sh). DRAFT-005 gated, resta a maintainer.
- Linkato in TASK-452 + TASK-476 (commenti + reference) per il gate m-13: il path input hard-0 e' ora robusto (portfolio + ExactFinisher).

### (4) SPIKE TASK-478 AC#1 ExactFinisher - FEASIBLE depth-0
- Read-only su export run5 (16 unassigned = 11 T08/G03/Math + 5 T19/G06/Barberia). Doc `task478-results/ac1-feasibility-residuo-run5-2026-08-01.md`. Script `/tmp/ac1_feasibility2.py`.
- **Depth-0 (no-bump) 0-hard completion ESISTE**: T08/G03 45h collocabili (need 11), T19/G06 377h (need 5).
- FINDING chiave: il residuo NON e' H1/H3-limited (378+402 celle H1+H3 libere >> 16); e' trappola dell'ottimo
  locale LS -> corrobora meccanicamente H1 (varianza). L'ExactFinisher trasformerebbe hard-0 del path input da
  probabilistico a deterministico per i residui. Caveat: H2 (room) non verificabile da lessons.txt; all-hard definitivo = AC#3.

## VERDETTO NOTTE (dai raw)
1. **Il path source=input ha hard-0 ROBUSTO e DOMABILE**, non strutturale. run6 portfolio=0hard + AC#1
   (celle depth-0 abbondanti) + diagnosi run4/run5 (varianza LS) convergono: il -38 di run5 era una traiettoria
   subottimale, non infeasibilita'. Portfolio (riduce P(strand)) + ExactFinisher (chiude deterministicamente il
   residuo) = hard-0 robusto+deterministico per il flip m-13.
2. **Adversarial-verification/evidence-first ha pagato**: verifica banner portfolio genuino (2 candidati, non
   fallback), query DB signature run5 prima del reset, probe AC#1 H7/H11-aware (non solo H1/H3), n=1 onestamente
   dichiarato treatment-test non statistica.
3. **Nessuna adozione**: portfolio resta DRAFT-005 gated; ExactFinisher resta AC#1 (spike); A1 T20 e' l'unica
   adozione (autorizzata da maintainer, evidenza riga 97).

## STATO RUN / APP / INFRA
- **App UP su run6** (`java -jar` portfolio.size=2 + source=input). Per un run NUOVO: kill-app, rilanciare con
  i flag desiderati (portfolio.size=1 per single, =3 raccomandato per robustezza). `./planner.sh kill-app`.
- **DB = run6 (0hard)** su `planner-data-production`. Per run nuovo: reset + import-calendario + pre-assign + solve-staged (no fresh reseed; T20 LabAssistant solo a fresh reseed).
- Container `planner-pg` PG17 up. Jar `target/quarkus-app/quarkus-run.jar` da HEAD (21:49).

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **n>=3 run portfolio** per la P(fail) STATISTICA (run6 e' n=1 treatment test). Procedura in `procedura-portfolio-path-input-2026-08-01.md`.
2. **ExactFinisher AC#2-#4** (TASK-478): design pass, prototipo flag-OFF, A/B. AC#1 feasible confermato.
3. **referente Q&A**: Q19 G14 Confezione (lab vs aula -> TASK-477b), Q22 T05-venerdi (TASK-476 T1 school-gated), TASK-475 A2 (budget vincolanti -> M coPresenceBudget).
4. **run6 medium -3%**: tuning search-side se il flip m-13 si attiva con portfolio.
5. **Follow-up cheap**: fix javadoc stale `loadGoldenCoPresenceDepth` (TASK-452 #7 side-finding).

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/verify-source-input-run6/VERDICT-2026-08-01.md` (run6) +
  `procedura-portfolio-path-input-2026-08-01.md` (procedura) + `task478-results/ac1-feasibility-residuo-run5-2026-08-01.md` (AC#1).
- Ricerca: `research_hard-variance-timetabling_2026-08-01.md`. Diagnosi: `task452-results/run4-run5-variance-diagnosis-2026-08-01.md`.
- Commit notturni: `98efafc5` (A1 T20), `eb4e8c09` (TASK-475), `815095aa` (AC#1), `947c62c4` (run6+procedura+link).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su java -jar (PLANNER_JAVA_OPTS non MAVEN_OPTS),
  poll a finestre (no background pipe), DoD obbligatoria su codice solver adottato, handoff+/clear a ~450k.
