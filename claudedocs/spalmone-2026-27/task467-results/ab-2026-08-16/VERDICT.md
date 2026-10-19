# VERDICT TASK-467 AC#4 - wiring dell'enumerator multi-candidato nel pre-assign (2026-08-16)

> Filone 1 del mandato Fedora 16/08 mattina. AC#4 = collegare il core AC#2 (commit
> bc713894: `rankTopK`/`enumerateTopK`, ranking input-driven, top-k) al ciclo staged,
> dietro flag `planner.pre-assign.rotation-enumerator.enabled` DEFAULT OFF, con il
> meccanismo di validazione 0hard (PreAssignmentValidator) IDENTICO.

## TL;DR

**Gate di non-interferenza: PASS.** A/B appaiato (baseline-golden.sh, golden, tutto
ratificato ON): hard 0 in entrambi i bracci, KPI in banda, ricorrenza non degradata.
**Ma il gate e' strutturalmente VACUO come misura di efficacia**: sul seed 2026/27
`LabRotation.count() == 0` (rotazioni rinviate a TASK-364), quindi il pre-assign salta
Phase 1/2/3 INTEGRALMENTE prima di raggiungere il wiring (verificato nel codice a
`HybridPhase1aService` riga ~590 e nei log di ENTRAMBI i run). Il A/B dimostra che il
flag non sborda; NON dimostra che il ranking aiuti. **Raccomandazione per maintainer:
nessuna adozione (resta DEFAULT OFF, come da mandato); la misura di efficacia e'
differita a quando esistono righe di rotazione (TASK-364) oppure a una probe con
rotazioni sintetiche.**

## Il diff (commit base b9a2ad91 + fix post-review)

- `HybridPhase1aService.runPhase1`: quando il flag e' armato, il piano dei giorni di
  rotazione arriva da `assignWithEnumeratorPlans` (k piani ranked in ordine, il primo
  fattibile vince, fallback greedy su null). Slot-finding, validazione e pinning
  INVARIATI: l'overload a 6 argomenti interseca solo i valid-days per coppia.
- `GreedyRotationAssigner.assignTeachers` (6 argomenti): la nuova restrizione
  per-PAIR `Map<BlockKey, Set<DayOfWeek>>`; il 5 argomenti ora DELEGA al 6 con null
  (un solo corpo, non due copie divergenti).
- `RotationEnumerator.preAssignEnabled()`: gate opt-in default OFF, distinto dal flag
  skeleton AC#1 (`planner.rotation.enumerator.enabled`); banner in
  `ScheduleService.refreshSolverStatics` (`Rotation enumerator pre-assign (TASK-467):
  enabled=%b`) per silent-flag-drop detection.
- Test: `RotationEnumeratorWiringTest` 8/8 (vedi "Cosa pinna i test" sotto).

### Findings della code review (tutti corretti in questo commit)

**Round 1 (pre-fix, 6 finding, tutti corretti):**

1. **CRITICO - restrizione union-based meccanicamente inerte.** La prima versione
   restringeva ogni docente all'UNIONE dei weekday del suo j-esimo timeline: ogni
   timeline completa usa OGNI cella weekday disponibile, quindi l'unione e' INVARIANTE
   fra candidati e tutti i k piani producevano la STESSA restrizione (il
   multi-candidato era finto). FISSO: restrizione per-PAIR dal mapping
   (cella → gruppo) del timeline, che varia davvero fra candidati (`restrictionFromTimeline`).
   Il test `twoRankedCandidatesProduceDifferentRestrictions` pinna esattamente questo:
   due timeline con la STESSA unione weekday devono produrre restrizioni DIVERSE.
2. **Pair-dropping silente (shell-game).** `backtrackAssign` SALTA una coppia con
   valid-days vuoto (warn + continua), quindi un piano che svuotava una coppia faceva
   "success" mentre ne buttava le sessioni. FISSO: `restrictValidDays` RITORNA l'insieme
   delle coppie contraddette (piene prima, vuote dopo) e l'overload fallisce il piano
   subito ("plan infeasible"), il chiamante passa al candidato successivo. Una coppia
   che era gia' vuota PRIMA del piano resta lo skip pre-esistente della baseline.
3. Duplicazione overload 5/6 argomenti (~45 righe): il 5 ora delega al 6.
4. Javadoc orfano (il doc di `pinTeacherDayAssignment` era finito sopra il metodo nuovo):
   riportato al suo metodo.
5. Scratch map a id negati in `buildEnumeratorTeachers`: mappe nominate.
6. Helper `weekdayUnion` morto dopo il fix #1: rimosso.

**Round /simplify (4 agent paralleli, finding applicati):** mappa nomi-gruppi
condivisa (una sola passata, usata da teachers e dal piano, con WARN su collisione
nome→due id), walk `daysByGroup` hoistato in `RotationEnumerator` (un'unica
definizione: ranking weekday-economy e restrizione derivano dalla stessa), twin
`sortedWeekdays` eliminato, restrizioni per-docente precomputate una volta (niente
ri-derivazione per iterazione j), DE-DUP delle restrizioni identiche fra piani,
`assertNotEquals`, niente `buildFailureAnalysis` sul path fail-fast (costava query
DB per piano scartato), blame deterministico (prima coppia contraddetta in
`pairOrder`, non ordine HashSet), wording neutro nell'assigner (non puo' sapere che
la restrizione viene dall'enumerator), knob nel banner (`topk/weeks-per-period/
max-periods` accanto a `enabled=%b`, stessa logica anti -D-dropped del grasp seed).

**Round 2 (verificatore Opus sul diff finale): NESSUN bug critico/major nei
meccanismi.** Quattro finding, tutti MINOR o condizionati:

- **(A) MAJOR latente, gated OFF - "mai peggio del greedy" vale solo per il
  fallback.** Un piano ADOPTATO cavalca la semantica partial-success del greedy: una
  restrizione puo' restringere una coppia su giorni dove nessuno slot e' piazzabile
  (saturazione H11/stanze) e la coppia viene skippata al livello SLOT (non giorno),
  fuori dalla copertura del fail-fast. Nessun crash, nessun pin sbagliato, ma un
  piano adottato puo' pinnare MENO sessioni del greedy libero, in silenzio (solo la
  riga PARTIAL nei log). NON corretto nel codice: e' una PRECONDIZIONE del gate
  A/B (quando il lever sara' raggiungibile): il confronto dovra' includere il conteggio
  sessioni pinnate, non solo hard/KPI. Il javadoc e' stato riformulato onesto
  ("il FALLBACK branch non puo' fare peggio"; l'adozione cavalca la partial-success
  semantics del baseline).
- (B) MINOR corretto: de-dup delle restrizioni esteso da "solo adiacente" a
  insieme dei provati (un candidato A,B,A' riusciva a passare).
- (C) MINOR documentato NON corretto: nomi-gruppo duplicati corrompono il budget
  model dell'enumerator (due id, stesso nome → budget sovrascritto). Dati
  patologici, WARN presente, deterministico (primo vince). Fix profondo = portare
  l'id dentro `RotationGroup`; fuori scope per un lever inerte.
- (D) MINOR documentato NON corretto: l'ordinamento delle coppie (tightness) e'
  calcolato PRIMA della restrizione. Solo euristica, non correttezza.

**Debito dichiarato (da /simplify efficiency, NON corretto, motivo scritto):** ogni
piano ricompromette il `BacktrackContext` intero (lavoro DB plan-invariante ripetuto
fino a k+1 volte: `buildIdealEndDatesMap`, `TeacherAvailability.find` per coppia).
Struttura pre-esistente del diff (la chiamata per-piano esisteva gia'), lever
strutturalmente irraggiungibile sul seed corrente, e la condivisione del context fra
tentativi richiede cura con lo stato mutable del backtracking. Differito a quando il
lever diventa raggiungibile (TASK-364).

## A/B (non-interferenza)

Protocollo: `scripts/benchmark/baseline-golden.sh`, profili dedicati, source=golden
default, tutto ratificato ON, n=1 per braccio (il gate e' di sicurezza, non di
efficacia: vedi sopra). Driver `ab-driver.log`, arm complete catturate
(`capture-run.sh` battery).

| metrica | ab-off-run1 (control) | ab-on-run1 (treatment) |
|---|---|---|
| hard | **0** | **0** |
| medium | -966.278 | -970.299 (-0,4%, banda noise LS) |
| soft | 6.642 | 7.072 |
| full-day | 90,0% | 89,6% |
| day-start | 99,6% | 98,5% |
| ricorrenza unpinned | 75,3% | 75,7% (non degradata) |
| BUCO-INTERNO | 0 | 0 |
| wall | ~23,6 min (07:04:32→07:28:10) | ~20,7 min (07:28:10→07:48:51) |

Log-chiave (entrambi i run): `No LabRotation rows configured - skipping Phase 1/2/3` +
banner `Rotation enumerator pre-assign (TASK-467): enabled=false/true`. Il braccio ON
non e' mai entrato nel codice del wiring.

## Cosa renderebbe il gate significativo

1. **TASK-364** (righe LabRotation reali): il wiring diventa raggiungibile e l'A/B
   misura il ranking vero (top-k vs candidato unico del greedy). PRECONDIZIONE dal
   round 2 finding (A): il gate dovra' confrontare anche le sessioni pinnate
   (partial-success semantics), non solo hard/KPI.
2. Probe con rotazioni sintetiche (profilo dedicato + righe LabRotation inserite a
   mano): misura di fattibilita'/qualita' senza aspettare TASK-364.
3. Gia' fatto qui: la meccanica multi-candidato e' pinnata a livello unitario
   (8 test: intersezione per-pair, coppie ignorate illimitate, contraddizione =
   fail-fast non skip silente, due candidati = restrizioni diverse, gruppi
   irrisolvibili saltati, default OFF).

## Stato

- Flag: `planner.pre-assign.rotation-enumerator.enabled` DEFAULT OFF (opt-in), come
  da mandato. NESSUNA adozione proposta.
- DoD: /simplify (4 agent: applicati i finding, vedi sopra), /code-review high
  (finding 1-6 tutti corretti o annotati), `./scripts/core/test.sh` EXIT 0
  (dettaglio nel commit).
- Commit: wired in b9a2ad91, fix post-review in questo commit.
