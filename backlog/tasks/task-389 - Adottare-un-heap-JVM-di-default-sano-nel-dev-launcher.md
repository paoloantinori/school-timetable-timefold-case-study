---
id: TASK-389
title: Adottare un heap JVM di default sano nel dev launcher
status: Done
assignee: []
created_date: '2026-07-21 22:00'
updated_date: '2026-07-22 07:05'
labels:
  - claude-planned
dependencies: []
ordinal: 213000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da TASK-387 (run 17): a 260MB default l'app IDLE post-solve fiammeggia un core in GC-thrash (Old gen fisso all'81% con l'orario risolto da 12.300 lezioni; osservato via thread dump, l'utente l'ha notato come drain). A 2GB sparisce e le pause young-GC scendono da 183 a 63ms. L'heap NON cambia lo score (ruled out come leva), ma un default sano e' un miglioramento operativo (fix del drain a riposo + runtime piu' liscio per tutti i run/benchmark). DA DECIDERE: (1) dimensione (1g basta? 2g?); (2) meccanismo per mvn quarkus:run (JAVA_TOOL_OPTIONS=-Xmx nel wrapper - verificato funzionante in run 17 - oppure il jvmArgs del plugin quarkus); (3) applicare a planner.sh/dev-start-and-wait.sh + prod path (java -Xmx -jar). Tocca il launcher condiviso -> sign-off utente. Coordinare col laptop-RUNBOOK (che gia' raccomanda un heap vero per i benchmark; correggere la dicitura 'solve GC-throttled' -> l'overhead GC totale e' solo 2,3%, non dominante).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes (2026-07-22)

**DECISIONI:** (1) dimensione **2g** — valore verificato end-to-end in run 17 (fixa il drain a riposo, pause young-GC 183→63ms), RAM abbondante su entrambe le macchine; override-abile via env `PLANNER_HEAP=<size>`. (2) meccanismo: `JAVA_TOOL_OPTIONS=-Xmx` per i `mvn quarkus:run` (verificato run 17), `-Xmx` diretto per i `java -jar` prod. (3) applicato a TUTTI e 4 i fork-site della JVM app: `planner.sh` cmd_start (scripted-bg), cmd_run_internal (dev fg), cmd_run_prod_internal (prod fg) + `scripts/core/prod-start-and-wait.sh` (prod bg). `dev-start-and-wait.sh`/`rebuild.sh`/benchmark scripts delegano a `planner.sh` → coperti automaticamente. Var `PLANNER_HEAP="${PLANNER_HEAP:-2g}"` in entrambi gli script (nessun config sourced condiviso esiste - duplicazione pragmatica, commentata).

**DoD:** /simplify → applicato fix di quoting (`"-Xmx${PLANNER_HEAP}"` intero arg, coerente sui 4 siti), skippati come fuori-scope la dup 2-file (pre-esistente, nessun config condiviso) e il refactor prod-start-and-wait→delega. /code-review high → trovato+corretto un clobber reale: `JAVA_TOOL_OPTIONS="-Xmx.."` inline sovrascriveva un eventuale `JAVA_TOOL_OPTIONS` d'ambiente (proxy/agent/-Xss persi) → cambiato in append `"${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Xmx.."` (preserva le opzioni estranee, set -u-safe, ultimo -Xmx vince). laptop-RUNBOOK aggiornato (il launcher ora fa 2g di default; niente più prefisso manuale JAVA_TOOL_OPTIONS; override con PLANNER_HEAP). La dicitura 'GC-throttled' era già stata corretta da run 17 (commit bbb8b73e), l'experiment-log riga 614 è già accurata.

**VERIFICA:** heap confermato **contro l'istanza in esecuzione** — `jcmd VM.flags` mostra `-XX:MaxHeapSize=2147483648` (2GB esatti) sulla JVM app avviata dal launcher; log mostra `Picked up JAVA_TOOL_OPTIONS: -Xmx2g`. `./scripts/core/test.sh` **exit 0**, 688 test verdi (0 failures/errors). Frontend non toccato (no ui-typecheck/ui-build). NOTA collaterale (non-bloccante, fuori TASK-389): lo startup a fresco è lento (~6 min) e ogni load schedule ricomputa gli MRV counts (~37s per 12.581 lezioni) — caratteristica pre-esistente indipendente dall'heap.
