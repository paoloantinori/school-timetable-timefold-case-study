---
id: DRAFT-005
title: >-
  SPIKE: reimplementare in autonomia le feature Timefold Enterprise utili
  (portfolio parallelo, constraint profiling, score-analysis DIY)
status: Draft
assignee: []
created_date: '2026-07-29 05:30'
labels:
  - claude-planned
  - spike
milestone: m-12
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spunto: la limitazione community di ConstraintVerifier.penalizesBy(int) (decomposizione pesi multi-tier = "score analysis", Enterprise) che ha costretto a verificare H0/H29 via getScore() in TASK-447. maintainer (2026-07-29): creare un backlog per provare a implementare DA SOLI le feature solo-Enterprise che potrebbero esserci utili.

Lista Enterprise VERIFICATA sui docs Timefold 2.3.0 (https://docs.timefold.ai/timefold-solver/latest/commercial-editions/commercial-editions, 9 feature): Score Analysis, Recommendation API, Performance improvements, Nearby selection, Multithreaded solving (move threads), Partitioned search, Constraint profiling, Multistage moves, Throttling best-solution events.

Valutazione fattibilità-DIY x valore per QUESTO progetto (schedule scolastico, LS perf-bound, orchestratore staged gia' esistente):

1. MULTITHREADED SOLVING -> PORTFOLIO MULTI-START PARALLELO. VALORE ALTO (i run di campagna sono lunghi; LS e' il bottleneck). Move-threading vero NON e' DIY-viable (richiede score incrementale thread-safe = internals del motore). L'alternativa realistica e fattibile: lanciare N istanze solver indipendenti in parallelo (diverso seed/config) e tenere il migliore - un wrapper a layer orchestrazione (il ciclo staged gia' esiste, si replicano N solve e si fa pick-best sullo score decomposto). Da misurare: speedup wall-clock a parita' di qualita', o guadagno qualita' a parita' di budget. Checkpoint-based, default-OFF.

2. CONSTRAINT PROFILING. VALORE ALTO (sapere quale constraint stream e' lento orienta le ottimizzazioni di score-calc). DIY fattibile: per-constraint stopwatch attorno al calcolo (o profilazione esterna JFR/async-profiler durante un solve). Timefold community espone i tempi via ConstraintMatch? Verificare. Consegna: ranking tempi per constraint su un run reale.

3. SCORE ANALYSIS (DIY). VALORE MEDIO-BASSO perche' GIA' aggirato: il progetto usa ConstraintMatchSnapshot/ConstraintMatch (API community) in ScheduleService.timeslotTierHardScore e computeConstraintMatchSnapshot, che decompongono score per-constraint (match count + score). Il gap Enterprise e' il ScoreManager.explainScore()/diff piu' ricco e la comodita' penalizesBy(weight). DIY: helper di decomposizione/diff score riutilizzabile per VERDICT e assert di peso nei test (pattern getScore() gia' usato). Marginal, secondario.

4. NEARBY SELECTION. VALORE MEDIO (assegnazione stanze + cartesian selector potrebbero beneficiare). DIY: custom move selector che biasa verso valori "vicini". Medio. Il progetto usa gia' cartesian move selectors.

5. PARTITIONED SEARCH. VALORE MEDIO, DIY HARD: partizionare per gruppo e' rotto dal coupling dei docenti (un docente insegna a piu' gruppi -> vincoli cross-partizione). Priorita' bassa.

6-9. Recommendation API, Multistage moves, Throttling events, generic Perf improvements: valore basso/non discreto per la campagna. Skip.

PRIMA DELLA DECISIONE: inventario verificato (fatto, vedi sopra). Le implementazioni concrete sono spike checkpoint-based dietro flag default-OFF (coerenti con l'addendum): niente adozioni, niente default-ON, niente run live oltre budget. Ordine di valore proposto: (1) portfolio parallelo, (2) constraint profiling, poi 3/4 se regge.

OUT OF SCOPE (maintainer decide): adozione di uno qualsiasi dei prototipi come default, eventuale acquisto licenza Enterprise.

---

DESIGN (Fable, 2026-07-30 sera - fatti verificati sul codice, non assunti):

(1) PORTFOLIO MULTI-START - l'architettura la decidono due fatti misurati sul repo:
- VINCOLO BLOCCANTE per il ciclo intero: il ciclo staged MUTA gli statici TRA gli stadi
  (ScheduleService: setPromotedLessonIdsStatic alla PROMOTION riga ~1207, refreshPinStatics
  alla demozione ~1462-1465, azzeramento alla RELEASE ~1526). Due cicli staged paralleli
  nella stessa JVM si corrompono a vicenda. Portfolio "N cicli interi in-JVM" = VIETATO
  senza un refactor statici->contesto per-solve, fuori budget spike.
- FATTO ABILITANTE per lo stadio singolo: nessuna classe di src/main/java/.../solver/
  chiama un setter statico (grep verificato 2026-07-30): durante un singolo solver.solve()
  gli statici sono read-only. Quindi il portfolio SCOPED A UNO STADIO (N istanze dello
  STESSO stadio, stessi statici) e' lecito in-JVM. La concorrenza fra N Solver distinti
  nella stessa JVM e' supportata da Timefold (e' il modello di SolverManager).
- DISEGNO: wrapper in runStagedSolver dietro `-Dplanner.portfolio.size` (default 1 = OFF).
  Allo stadio bersaglio (primo: B-TIMESLOT-1, lo stadio piu' lungo e piu' seed-sensibile):
  N copie del SolverConfig con randomSeed distinti, N problemi INDIPENDENTI, executor a N
  thread, pick-best su score decomposto (timeslot-tier hard prima, poi medium), il ciclo
  prosegue col vincitore. DA VERIFICARE in implementazione con un test da 5 minuti: se
  solver.solve(problem) muta l'istanza passata; se si', clonare via la serializzazione
  export/import gia' esistente (protocollo checkpoint TASK-401), non scrivere un cloner.
- MISURA (due confronti, mai uno solo): (a) parita' di budget wall-clock: N istanze a
  budget/N vs 1 istanza a budget pieno; (b) parita' di budget per istanza (costo Nx):
  guadagno qualita' assoluto. KPI: hard/boxed/full-day/ricorrenza. ATTENZIONE metodo: i
  parametri LS sono invisibili al checkpoint di costruzione (lezione spike S1/S3 del
  30/07), quindi la misura richiede mini-solve reali dello stadio: laptop, slot diurno.
  Stanotte si puo' solo COSTRUIRE (wrapper + unit test del pick-best + flag OFF).
- RISORSE: ~13k Lesson per clone; N=3-4 sul laptop. Verificare l'heap: 2g bastano per N
  working solution Bavet? Se no, heap 4g sulla riga java -jar (e' gia' il launch mode).

(2) CONSTRAINT PROFILING DIY - protocollo leave-one-out:
- La community non espone timing per-constraint (inventario docs verificato sopra): il
  segnale disponibile e' la "Score calculation speed" per fase nei log del solver.
- HARNESS: stesso problema fisso, K run LS brevi (60-120s, stesso seed), ognuno con UN
  constraint disabilitato via filtro per-ID al build del provider (system property
  test/diag-only con lista di ID canonici, filtro sull'array di defineConstraints; NON
  riusare debugConstraintsDisabled che e' tutto-o-niente). Delta % di score calculation
  speed = costo del constraint. Ranking = deliverable. Attribuzione secondaria facoltativa:
  async-profiler/JFR su un run reale, frame Bavet -> lambda del provider.
- Nessun cambio di comportamento: il filtro e' inerte senza la property.

(3) SCORE-ANALYSIS HELPER: resta terzo e opzionale (gia' aggirato con ConstraintMatch).

ESECUZIONE: implementazione delegabile (zai/notturna: wrapper portfolio + harness profiling,
flag OFF, unit test, DoD); le MISURE (mini-solve di stadio, ranking profiling su run reale)
sono lavoro laptop diurno. Adozione: maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Inventario feature Enterprise verificato sui docs Timefold 2.3 correnti (fatto: 9 feature elencate nella descrizione)
- [ ] #2 Prototipo portfolio multi-start STADIO-scoped (design in descrizione: mai N cicli interi in-JVM, statici mutati tra stadi) dietro flag default-OFF, misura con mini-solve di stadio su laptop nei DUE confronti (budget/N e budget pieno) - i parametri LS sono invisibili al checkpoint di costruzione (TRE righe: ipotesi/risultato/tenere-buttare)
- [ ] #3 Constraint profiling DIY: ranking tempi per-constraint su un run reale (TRE righe)
- [ ] #4 Opzionale: score-analysis helper DIY per VERDICT/assert peso se 1 e 2 reggono (TRE righe)
- [ ] #5 Nessuna adozione/default-ON: tutto flag default-OFF, checkpoint-based; adozione = maintainer
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
