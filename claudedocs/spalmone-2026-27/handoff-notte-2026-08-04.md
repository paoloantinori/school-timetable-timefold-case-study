# Handoff notte 2026-08-04: coda read-only ESURITA, prossimo passo = maintainer sull'intervento

> Turno read-only chiuso onestamente: ~16 task chiusi stasera/notte, ognuno con verdetto onesto e
> commit. La coda read-only e' **esausta**; i task di INTERVENTO sono gated maintainer. I risultati
> load-bearing (486, 453) sono verificati qui dai VERDICT doc, non da memoria. HEAD arrivo `fbc3cc1b`.

## 1. Elenco task chiusi (task - esito - commit)

| Task | Esito (1 riga) | Commit |
|------|----------------|--------|
| TASK-486 (AC4) | M-TC per-retry: cura bad basin CONFERMATA in-vivo (att1 M-TCoff -1hard -> att2 M-TCon 0hard), good basin zero-cost | `1feeef2b` `a35633f3` |
| TASK-453 | Erosione construction->finale MOBILE = 16.6% retention (83.4% moved), concentrata in LS-Compactness, preservata dai PIN | `ffff1834` |
| TASK-482 | Kempe-chain H17 discriminator CHIUSO NO (0/9 sustainable, nessun MoveSelector) | `77a63b14` |
| TASK-487 | G04 friday daily-fill gap caratterizzato (run 3.59 vs golden 5.17, quota 5) - DEFER | `40ec8a58` |
| TASK-488 | 3o-anno low recurrence = stage-shift CONFIRMED (300h stage vs 0) - IGNORE | `2599575b` |
| TASK-412 | Teacher concentration LARGELY RESOLVED (VACATION enforce 5/6, run=golden); T12 residual DEFER | `0ccaa291` |
| TASK-472 (AC2) | Golden constraint classification: tutte le coded rules MEDIUM golden-consistent; AC#2 script + VERDICT | `d558c787` |
| TASK-395 | golden-overflow-by-group KPI script + VERDICT (read-only) | `36dcaa7b` |
| TASK-417 | CH-room phase (solverConfig phase 4) INERT - null-trap confirmed full-grid | `5664c7df` |
| TASK-236 | HashMap iteration order NON e' sorgente di varianza run-to-run | `40e1dc78` |
| TASK-477 | G14 refinements: (a) no-action (de-tag gia' fatto), (b) defer | `b7e4308e` |
| TASK-339 | Pinned lessons cause ZERO hard - canPin() works, no extension warranted | `bfd694b9` |
| TASK-293 | Shadow vars daily NON warrantate - premessa refutata (Constraint Streams gia' incrementali) | `8b94e83a` |
| TASK-158 | Incremental score calculator DEFER - POC stale (3 vincoli non 9), conflict gia' joiner-ottimizzati | `f3cc565f` |
| TASK-235 | Doc storico VRP memory/multi-entity scritto; skeptic ha corretto attribuzione (TASK-153 non 456) | `5eeddbdc` |
| TASK-133 | solver-configurations.md refreshato a 5-phase Lesson-singola TF2.3; skeptic ha corretto "Fase 1 assegna stanze" (NULLa, TASK-446) | `fbc3cc1b` |

## 2. Risultati load-bearing (verificati dai VERDICT doc)

**TASK-486 M-TC per-retry VALIDATO** (`task486-results/VERDICT-mtc-per-retry-2026-08-03.md`). Due claim
load-bearing, entrambe misurate:
- **No 2x sul good basin.** Arm (a) A/B: feature-on 1558s vs control-off 1673s (delta entro varianza LS),
  hard 0 su entrambe, 1 tentativo, `mtc_enabled_true_refreshes=0`. La firma da temere (2x wall del flat
  M-TC-ON, ~2435s) e' assente: M-TC non entra nel constraint set del tentativo 1.
- **Cura il bad basin.** Arm (b) AC#4 in-vivo (force-grasp-seed=6, stesso basin su 2 tentativi):
  attempt 1 M-TC off = **-1hard** (H29 room-null meccanica G04), attempt 2 M-TC on (retry) = **0hard**.
  `retry_fired=yes`, `mtc_enabled_true_refreshes=4`. Cura attribuibile a M-TC (within-run A/B, sola
  variabile M-TC off/on), corrisponde al flat seed-6 A/B.
- **Stato:** prototipo opt-in (DEFAULT OFF `planner.multistart.mtc-per-retry.enabled`), subordinato a
  multi-start (DEFAULT ON). NESSUNA adozione default (regola maintainer). **Ratifica a maintainer**: pronto come
  safety-net opt-in per input/seed dove il tentativo 1 e' un bad basin room-tier; sul seed corrente e'
  inerte (good basin, 1 tentativo).

**TASK-453 erosione measured** (`task453-results/VERDICT-erosione-2026-08-03.md`). MOBILE set (lezioni
unpinned a entrambi i cut, 1752/10445): **cell-retention construction->finale = 291/1752 = 16.6% kept,
83.4% moved**. L'erosione e' concentrata nella fase **LS-Compactness** (1449/1744 = 83% delle cell-changes
always-mobile; Compaction 51, fasi room 0), dominata dalle **ore teoriche generaliste mai-pinnate**
(Lingua 3.6%, Matematica 6.1%, Scienze 0%, Recuperi 8%). Insight load-bearing: **il template (74%
resemblance) e' preservato dai PIN (promoted + authoritative), non dalle lezioni libere**. Leva pratica:
alzare la resemblance riducendo il rimescolamento delle mai-pinnate (M32 quasi-pinning agisce qui), NON
pinnando di piu'. Read-only, nessun cambio al modello/adozione.

**I negativi puliti** (verdetto onesto "non e' il colpevole / non warrantato", nessuna azione):
- TASK-236: l'ordine HashMap NON e' varianza run-to-run (hashCode id-deterministico, path critici gia'
  TreeMap-sorted; la varianza e' LS NON_REPRODUCIBLE di Timefold).
- TASK-339: i pin causano ZERO hard; `canPin()` funziona, nessuna estensione warranted.
- TASK-417: la fase CH-room (phase 4) e' INERT sui tempi solver rispetto al null-trap (full-grid confirm);
  non e' un collo.
- TASK-293: le shadow variables per i daily NON sono warrantate (premessa refutata: Constraint Streams
  gia' incrementali, `toList()` non-incrementale gia' corretto in M18/TASK-243; unico residuo M29 su
  popolazione minuscola).

## 3. Gap che restano aperti (DEFER, onesti)

- **TASK-487** (G04 friday daily-fill deficit): caratterizzato (run 3.59 vs golden 5.17, quota 5),
  DEFER. Cura non read-only.
- **TASK-477** (G14 refinements): (a) esenzione H2 co-presenza per G14 = no-action (de-tag gia'
  fatto); (b) steering stanza G14 -> R10 = defer (preferenza da valutare).
- **TASK-412** (T12 residual): teacher concentration LARGELY RESOLVED, ma il residuo specifico
  T12 DEFER.
- **TASK-158** (incremental score calculator): DEFER. Gate mancante: AC#1 (profilare % score-calc in
  H1-H3) non fatto. Solo se profilazione mostra H1-H3 dominanti E search-config esaurito.
- **Profilazione H1-H3** (gap trasversale da 158/293): nessuna misura di quanto i conflict pesano sullo
  score-calc. Richiede solve profilato (~28 min, async-profiler/JFR, meglio laptop).

## 4. Cosa attaccare con maintainer sveglio (intervento gated, in ordine di priorita')

maintainer via Fedora ha dato la priorita'. I task di INTERVENTO non sono read-only (toccano codice/modello):

1. **TASK-486 ratifica M-TC per-retry** (e' VALIDATO, manca solo il via di maintainer sul default). La leva e'
   pronta come safety-net opt-in; decidere se/come esporla (default rimane OFF salvo ratifica).
2. **TASK-460 brainstorming ricombinazione orizzontale**: blocker TASK-453 RIMOSSO (commit `8d33d51d`
   "unstale blocker (TASK-453 done) + read-only approach seeds"); l'evidenza erosione (453, 16.6% MOBILE)
   e' disponibile per seminare l'approccio.
3. **TASK-470/471** workbook teoria-spread (gated maintainer): veicolo per dichiarare le pratiche non
   deducibili (direttiva solo-input).
4. **TASK-449** allocatore pre-assegnazione lab mono-stanza (gated maintainer).

**Non cercare read-only forzato.** La coda e' esausta; i task 2026-02/03 residui (096/097/132/181/238)
sono quasi certamente stale come 293/158/133 (pre-TASK-228/420/446) e valgono un triage di staleness,
non rilavorarli. Meglio fermarsi qui e far decidere maintainer sul prossimo INTERVENTO.
