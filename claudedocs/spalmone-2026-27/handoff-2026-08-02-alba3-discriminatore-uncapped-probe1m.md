# HANDOFF 2026-08-02 alba3 (zai). ExactFinisher: discriminatore UNCAPPED decisivo + probe @ 1M

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (`15b2e0ec`, push, rebased
> sopra i 2 commit di maintainer TASK-479/480 `2c19d405`+`fae5605e`). **App UP** (prod, `java -jar` con
> `-Dplanner.exact-finisher.enabled=true -Dplanner.exact-finisher.max-candidates=1000000
> -Dplanner.pre-assign.constrained-teacher.source=input`; banner `maxCandidates=1000000`).
> **DB = probe run2 finale (-6hard = 6 H17, EF 18/18 boxed closed)** su `planner-data-production`.
> Container `planner-pg` up. Verdetti dai raw. Regole standing invariate. **maintainer sveglio/attivo**
> (ha pushato 479/480; ha diretto il presidia sincrono del probe).

## MANDATO (3 punti) - TUTTO CHIUSO

### (1) DISCRIMINATORE OFFLINE EF UNCAPPED sul residuo run4. FATTO + DECISIVO + COMMITTATO
- Domanda (decisionale): il cap era l'unico ostacolo, o serve depth-2?
- **Esito (i): depth-0/1 completion ESISTE UNCAPPED.** Test `ExactFinisherRun4DiscriminatorTest`
  (src/test, @QuarkusTest H2): `SnapshotFixture.loadFromExport(ab-ef-run4/export.json.gz, aes,
  includeQ22=true)` ricostruisce il post-stato run4 (13 piazzati + 5 boxed) room/id-faithful +
  `seedMediumTierFacts` (quota REALE) + Q22 availability REALE (T19 Mon-Tue), poi
  `ExactFinisher.repair` DIRETTA budget 100M (uncapped) depth 0..1 con la STESSA gate chain production.
  **placed 5 of 5, residual 0, conflicts 0.** Collocazioni tutte Q22-compliant (T19 solo Mon;
  T08 Wed ma non-Q22). Il cap era l'UNICO ostacolo; tutti i 18 boxed depth-0/1-collocabili
  (13 run4 + 5 qui). **depth-2 NON necessaria** per il worst case noto.
- **DoD piena**: `/simplify` (4 angoli: delete try/finally ridondante, tighten conflictsAdded==0,
  pin residual==0) + `/code-review high` skeptic Opus (CONFIRMED + fixato un BUG di fedelta'
  materiale: il primo draft passava `includeQ22=false`, droppando la disponibilita' Q22 di T19
  e allargando il VRP 2->5 giorni; corretto a `true`, verdetto regge sulla griglia fedele) +
  `test.sh` EXIT 0 (**963/0/16**, final code).
- Questo CHIUDE i caveat di AC#1 (H2/H20/H5): VRP reale applica H20/H5/quota, EF non gate-checka
  H2 (rooms=B-ROOM). Doc: `task478-results/discriminatore-uncapped-run4-2026-08-02.md`.

### (2) PROBE LIVE knob 1000000. FATTO - VERDETTO GREEN per EF (run2 bad seed)
- Run1 = **GOOD SEED, 0hard**/-1047939medium/2755soft, EF **INERTE** (boxed=0 all'entry, no log):
  la LS ha chiuso tutto da sola. Nessuna regressia a knob 1M, MA nessun esercizio di EF.
- Run2 = **BAD SEED, -6hard**/-1037459medium/2352soft. Firma bad-seed confermata (-2148hard a
  B-TIMESLOT-3, 18 boxed al finisher). **EF @ 1M placed 18 of 18 boxed at depth<=1 (residual 0,
  conflicts added 0)** -> chiude TUTTA la coda boxed (H0 -> 0). B-ROOM poi azzera i room-null.
  Residuo finale **-6hard = 6 H17 internal holes** (strutturale, STESSO count del componente H17
  di run4; pattern lab-day TASK-451/463, ORTOGONALE a EF).
- **CONFRONTO @ 100k vs @ 1M su seed cattivo:**
  | knob | EF placed | H0 residual | H17 | final hard |
  |------|-----------|-------------|-----|------------|
  | 100000 (run4) | 13 of 18 | 5 (-10hard) | 6 (-6hard) | **-16hard** |
  | 1000000 (run2-probe) | **18 of 18** | **0** | 6 (-6hard) | **-6hard** |
- **VERDETTO PROBE: budget 1M chiude la worst-case boxed tail.** EF @ 1M piazza 18/18 a depth 0..1
  senza conflitti su seed cattivo fresco; il cap non e' piu' l'ostacolo (vs 13/18 @ 100k). I -6hard
  residui sono H17 strutturale (non di competenza EF), identico a run4. **EF @ 1M adottabile per la
  chiusura DETERMINISTICA della coda H0** (il residual di varianza LS).
- Per **TASK-480** (maintainer): EF chiude il worst-case boxed tail DA SOLO -> multi-start diventa
  cintura+bretelle per l'H0 (che EF gia' gestisce). L'H17 (-6hard) e' ortogonale: cura diversa
  (consolidamento lab-day 463, o accettare il trade-off come su golden).
- Capture: `task478-results/ab-ef-probe-1m-run{1,2}/` (run1 good, run2 bad + EF 18/18).

### (3) QUADRO-2026-08-02.md (indice vivo). FATTO + COMMITTATO
- Nuovo file `QUADRO-2026-08-02.md` sostituisce il 07-30 come indice vivo (dove siamo, adozioni
  ratificate, flag OFF in cassa, menu maintainer, puntatori). Il 07-30 e' archivio con riga di rimando.

## CONNESSIONE con il lavoro di maintainer (sveglio, push 479/480)

Il discriminatore + probe sono l'INPUT diretto per i task che maintainer ha appena aperto:
- **TASK-479** (architettura layer di orchestrazione, docs): censimento operatori + cornice
  stocastico-vs-esatto. Il discriminatore mostra che l'operatore ESATTO (EF depth-0/1) chiude il
  worst case noto -> dato per la cornice "quando esatto basta".
- **TASK-480** (multi-start vero da costruzioni indipendenti): maintainer scrive esplicitamente
  *"confrontare con ExactFinisher: se il finisher chiude il worst case da solo, il multi-start
  diventa cintura+bretelle; se no, e' la via principale"*. Il discriminatore (EF chiude run4
  uncapped) + probe (EF @ 1M su seed fresco) sono i dati per AC#3 di 480. Letto 479/480, non
  toccati (sono To Do claude-planned di maintainer).

## VERDETTO (dai raw)
1. **Discriminatore DECISIVO (outcome i): il cap era l'unico ostacolo.** depth-0/1 chiude run4
   uncapped sulla gate chain reale (Q22-fedele). depth-2 NON serve sul worst case noto.
2. **Probe @ 1M GREEN: budget 1M chiude la worst-case boxed tail.** Su seed cattivo fresco (run2,
   18 boxed), EF @ 1M placed 18/18 depth 0..1 (residual 0, conflicts 0): H0 -> 0. Residuo finale
   -6hard = 6 H17 strutturale (stesso count di run4, ortogonale a EF). @ 100k (run4) EF fermava a
   13/18 (-16hard); @ 1M chiude tutto l'H0. EF @ 1M adottabile per chiusura H0 deterministica.
3. **Resta flag OFF, nessuna adozione.** Il flip (EF ON default + knob 1M) e' gated a maintainer, ora
   inquadrato nel confronto EF-vs-multistart (TASK-480: EF chiude la coda boxed da solo).

## STATO RUN / APP / INFRA
- **App UP** (dalle 05:47): `java -jar` ExactFinisher ON + max-candidates=1000000 + source=input.
  Log `/tmp/planner-prod.log`. Banner `maxCandidates=1000000`.
- **DB = probe run2 finale (-6hard = 6 H17; EF 18/18 boxed chiusi)** su `planner-data-production`.
  Per run nuovo: reset + import-calendario + pre-assign-reset + pre-assign + solve-staged
  (no fresh reseed; LabRotation=0 e' la norma campaign-side, confermato).
- Container `planner-pg` up. Jar `target/quarkus-app/` = HEAD (15b2e0ec, col test discriminatore).
- caffeinate attivo (m12 wrapper).

## PUNTI DI RIPRESA
- Questo handoff + `task478-results/discriminatore-uncapped-run4-2026-08-02.md` (discriminatore) +
  `task478-results/VERDICT-ab-exactfinisher-2026-08-02.md` (sezione DISCRIMINATORE) +
  `QUADRO-2026-08-02.md` (indice vivo).
- Capture: `task478-results/ab-ef-run{1,2,3,4}/` (storico) + `ab-ef-probe-1m-run1/` (good seed 0hard)
  + `ab-ef-probe-1m-run2/` (bad seed, EF 18/18 @ 1M, -6hard H17).
- Commit (push): `15b2e0ec` (discriminatore + QUADRO + docs, DoD piena).
- TASK-478 backlog: comment #4 (discriminatore) + #5 (correzione Q22). TASK-479/480 di maintainer
  (letti, framework per il verdetto EF-vs-multistart).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`
  (PLANNER_JAVA_OPTS), rebuild prima di run misurati, handoff+/clear a ~450k, mai fermo, DoD
  obbligatoria, **flag ExactFinisher resta OFF default**.
