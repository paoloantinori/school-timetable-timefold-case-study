---
id: TASK-387
title: >-
  Heap sizing test — misurare l'impatto del GC sui solve (default 260MB vs
  -Xmx2g/4g)
status: Done
assignee: []
created_date: '2026-07-21 13:47'
updated_date: '2026-07-21 22:00'
labels:
  - claude-planned
dependencies: []
ordinal: 211000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SCOPERTA (thread dump run 16, 2026-07-21, tasks/lessons.md): il dev server gira con l'heap JVM di DEFAULT (~260MB su Fedora via 'mvn quarkus:run'). L'orario 2026/27 risolto (12.300 lezioni + stato incrementale di score Timefold) inchioda l'Old gen all'~81% in modo permanente -> G1 in raccolta quasi continua, **804 young GC per 147s totali = ~183ms di pausa media per young GC** (contro i pochi ms normali). A solver FERMO il processo restava al ~100% CPU per puro GC (solver e validation-watchdog erano parkeggiati nel dump).

IPOTESI DA TESTARE: con pause young da 183ms i SOLVE stessi (run 12-16) sono strozzati dal GC - meno mosse/sec valutate. Parte del residuo -426 hard e della varianza -426 vs -3902 potrebbe essere artefatto di GC, NON di metaeuristica. Se un heap adeguato porta a 0 hard a parita' di config, il 'problema di ricerca' era in realta' l'ambiente di esecuzione.

PROTOCOLLO (coerente con la direttiva primaria modello-vs-ricerca: e' un fix di AMBIENTE, non di modello): (1) run di controllo A = default heap (gia' abbiamo run 15: -426 hard, e run 16); (2) run B = stessa identica config con java -Xmx2g -jar target/quarkus-app/quarkus-run.jar (path prod, planner.sh ~riga 410, dove -Xmx e' banale) - profilo fresco, import identico (866/1800/280), solve, KPI; (3) opzionale run C = -Xmx4g se 2g migliora ma non basta. METRICHE per confronto: jstat -gcutil durante il solve (young-GC avg pause deve scendere a single-digit ms), hard finale, tempo-al-miglior-score per fase, KPI (full-day/ricorrenza/blocchi invariati). VERDETTO: se l'heap risolve il residuo -> aggiornare planner.sh/dev-start-and-wait per un heap di default sano E rivedere se il benchmark metaeuristiche (TASK-386) serve ancora o va rifatto con heap corretto (un benchmark GC-strozzato misura il GC, non la metaeuristica - gia' notato nel laptop-RUNBOOK). PRECEDE logicamente TASK-386. Determinare anche il flag jvmArgs esatto per il path dev 'quarkus:run' (il path prod java -jar e' gia' chiaro).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ESEGUITO (run 17, 86 min, heap 2GB via JAVA_TOOL_OPTIONS). young-GC pausa 183ms->63ms (3x piu' corta) MA overhead GC totale invariato (2,3% wall-time entrambi) -> il GC non strozzava il solver in modo dominante. Hard -1362 dentro la banda varianza run15/16 (-426..-3902), KPI identici a run 15 -> heap NON e' la leva dello score. Residuo = coppia G05 zero-slack + H15/H17 = ricerca, non ambiente. CONCLUSIONE: heap ruled out come bottleneck; TASK-386 (benchmark metaeuristiche) resta la priorita' per il residuo. FOLLOW-UP OPS (creato TASK-389): adottare heap di default nel launcher (fix del GC-thrash a riposo osservato a 260MB + pause piu' lisce), scelta 1g vs 2g + meccanismo (JAVA_TOOL_OPTIONS o jvmArgs quarkus:run) da decidere - tocca planner.sh condiviso, non fatto unattended.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
