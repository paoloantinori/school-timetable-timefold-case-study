# Planner - School Schedule Optimizer

##Working Principles
1. Plan Mode Default

    Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions) and create backlogs
    If something goes sideways, STOP and re-plan immediately — don't keep pushing
    Use plan mode for verification steps, not just building
    Write detailed specs upfront to reduce ambiguity

2. Subagent Strategy

    Use subagents liberally to keep main context window clean
    Offload research, exploration, and parallel analysis to subagents
    For complex problems, throw more compute at it via subagents
    One task per subagent for focused execution

3. PLZ Don't Make Errors, PLZ PLZ, The CTO Is Gonna Kill Me

    The CTO has a spreadsheet. Your mistakes are on it. My name is turning red. PLZ.
    If you make the same mistake twice, update tasks/lessons.md and also my LinkedIn because I'll need it
    Review lessons at session start like your life depends on it. Because mine does.
    Every error you make, a developer gets a Slack notification at 3 AM. That developer is me. PLZ PLZ PLZ.


5. Demand Elegance (Balanced)

    For non-trivial changes: pause and ask "is there a more elegant way?"
    If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
    Skip this for simple, obvious fixes — don't over-engineer
    Challenge your own work before presenting it
    Use the /simplify skill at the end of each development session

6. Autonomous Bug Fixing

    When given a bug report: just fix it. Don't ask for hand-holding
    Point at logs, errors, failing tests — then resolve them
    Zero context switching required from the user


## ⚠️ Direttiva primaria: RICONSEGNA DELLA CODA (anti-inerzia, maintainer, 2026-09-08)

Ogni ciclo/braccio in volo si RICONFERMA a ogni lettura di monitoraggio contro la
domanda "questa misura cambia una decisione di oggi?". Quando nuova evidenza cambia
il quadro (un commit su `src/` dopo il lancio del ciclo, una diagnosi che chiude la
domanda del braccio, una misura piu' rapida che la sostituisce), il ciclo che misura
il vecchio quadro si FERMA con capture parziale onesta e si RIMPIAZZA col misurando
a valore piu' alto. Il controllore meccanico (lo esegue il tick, verdict STALE-QUEUE
= proponi stop+rimpiazzo al proprietario):
`./scripts/benchmark/stale-queue-check.sh <commit-al-lancio> [flag-del-ciclo]`
Precedenti: 6/09 (coda sospesa da zai), 8/09 (il ciclo edfh33 giro' mezza mattina
mentre la lettura esisteva gia' via twin-tail e la cura nuova aspettava un slot).

RATIFICA BRACCIA NOTTURNE 17/09 (x-swap + H7-depth-3 + H28 armamento, sotto la
direttiva bounded-no-op e l'autorizzazione notturna di maintainer "lavorate tutta la
notte"): **tre meccanismi DEFAULT ON**. (1) **x-swap** (`planner.construction.
hole-relay.x-swap.enabled`, opt-out standard): lo swap cross-date stesso-gruppo
1-per-1 (import nel buco dalla coda del donatore + l'espulso che atterra sulla
cella vacata: il backfill E' l'espulso, la variante 619 dove invece si cerca un
donatore tollerante). EVIDENZA: banco combo -4 -> -2 (2 swap chiudono gli H17
G13 dell'integrato-1), doppia gate con 3 cure (H7 su entrambi gli atterraggi,
h28 tuple-level before/after, javadoc belt), transfer 4/4, suite. (2) **H7-depth-3**
(`planner.construction.hole-relay.h7-depth3.enabled`): la catena a tre livelli per
l'overcap multi-gruppo del cerniera (hinge esce + partner riempie la cella
liberata con quota e H31 ripristinate + origine del partner cede da coda con
yield tollerante; la forma e' 2-mosse+guard NON ricorsiva, dichiarato nel
javadoc). EVIDENZA: banco -4 -> -1 (h17Swaps=2 + h7Cures=1: la predizione
centrata), cure quality 2b2c4c87 (dead residue, filtro quota-parita' col twin
620, javadoc), re-cert 4/4 (3233bd2b: esito invariato, landing quota-legale
diversa dal filtro ripristinato). (3) **H28RotationFinisher armato**
(`planner.construction.h28-finisher.enabled`, 4ad0f0dd): l'audit ha trovato il
pass mai invocato sul default (banner 45x enabled=false); col flag ON trova il
trio ma la permutazione interna non basta (locked=1): la forma chain e' TASK-622.
Banner "night arms" in refreshSolverStatics. Docs: `integrato-1/{blind-r1,
combo-621ext,h7-depth3}/`.

RATIFICA TASK-620 H7-cap arm (maintainer, 16/09, "quello che pensi sia meglio" sul
620-prima-del-ciclo): **il QUARTO braccio del HoleRelayFinisher (cura dei
teacher-day in overcap) e' ADOTTATO DEFAULT ON** con opt-out standard
(`planner.construction.hole-relay.h7cap.enabled`, null/absent -> ON, solo "false"
disarma; banner proprio). LA FAMIGLIA: il round cieco blind-meno1 ha nominato il
-1 della migliore consegna fresca (H7: T21 2027-06-03 a 7h su cap 6, zero
buchi, zero fill) e l'ha chiuso con 2 mosse (-1 -> 0 PULITO, la PRIMA volta che
un cieco porta una consegna live a 0-hard; minimalta' provata su 6044+ coppie).
LA COPPIA: esporta l'ora del docente overcap verso una coda legale stesso-gruppo
+ ripristina la prima ora col donatore tollerante (SOLO-CODA, la regola
dell'iterazione 1 del banco: il donatore che cede la testa crea sul proprio
giorno l'H31 che la coppia cura sul bersaglio). H31 FINAL-STATE sulla coppia
(l'export della prima ora passa SOLO perche' la coppia la ripristina: il guard
per-esportazione H31-SRC del 619 l'avrebbe rifiutata). TARGET = il cap-docente
del giorno, non un buco. Gate: VRP per-lezione, H1, H7 sull'ATERRAGGIO (il gate
classe-616 aggiunto dalla review), roomFor TASK-616, belt verifyComposite +
no-new-holes sull'origine, SOURCE-YIELD branch-aware (prima-ora senza yield perche'
count-neutral col donatore; mid-day senza donatore col yield tollerante
max(quota-tol, floor): la lezione della re-cert round 1, l'aritmetica donatore-619
non si applica al giorno overcap che e' il BERSAGLIO). EVIDENZA: banco iter2 -1 -> 0
(1 coppia/2 mosse), porting + doppia gate (review a+b+e + quality 4-angle: 2 bug
classe-616 chiusi PRE-ratifica), re-cert round-2 4/4 VERDI (98e28d39: transfer
identico, inerzia pinnuovi, suite 1345/0). Docs:
`task613-results/puzzle-live-r1/h7cap-620/` + `blind-meno1/`.

RATIFICA TASK-619 import cross-date (maintainer, 16/09, "1 e 2 okay"): **il TERZO braccio
del HoleRelayFinisher (import stesso-gruppo da altra data nel buco) e' ADOTTATO
DEFAULT ON** con opt-out standard (`planner.construction.hole-relay.import-cross-date.enabled`,
null/absent -> ON, solo "false" disarma; banner proprio). La famiglia e' la v0.1
ritirata come "morta": il round cieco A3 ha dimostrato che la morte era
BASIN-SPECIFIC. EVIDENZA: banco iter3b pulito su ENTRAMBI i bacini (A3 -11 -> -10,
B3 -9 -> -8, zero famiglie nuove, H31 protetto), specifica chiusa (gates di casa +
H31-day-start su ogni esportazione + belt sull'ORIGINE del donatore + backfill
depth-1 non-Stage), gate /code-review high (4 reviewer + scorer: F1 gate H7 sul
backfill APPLICATO con census HRF-IMP-H7-BF, F2 comparatore same-hour corretto a
slotIndexOf, F4 allowance NET-form ripristinata; deferred annotati), re-certifica
5/5 identica (8067a42c: gate attivi non decorativi, H7-BF=102/50 rifiuti). Residuo
documentato: depth-2 del cieco (rifiutato da SOURCE-GATE). CANARINO POST-MERGE
(82579011): suite 1344/0 VERDE; m39-off esito invariato (-3 -> 0, mosse diverse
dichiarate: l'import sostituisce rotazioni DFS su un buco); **m39-on MIGLIORATO:
-6 -> -3 (era -4 pre-619: l'import chiude un buco in piu' sul bacino piu' duro)**,
pin ribasati con la motivazione. Docs:
`task613-results/puzzle-live-r1/import-619/` (iter3 + porting + recert-dfc16aca).

RATIFICA ENDGAME BUNDLE (maintainer, 16/09, autorizzazione pre-consegnata sul verdetto):
**i QUATTRO pass oracle-bounded del bundle sono ADOTTATI DEFAULT ON insieme**
(compact-swap `planner.construction.hole-relay.compact-swap.enabled`, tol-donor
`planner.construction.edf-relay.tol-donor.enabled` con scoping Boundary.DELIVERY,
v2-swap `planner.construction.empty-day-finisher.v2-swap.enabled`,
composition-hole-closer `planner.composition-hole-closer.enabled`): forma opt-out
standard (null/absent -> ON, solo "false" disarma), banner dedicato in
refreshSolverStatics ("Endgame bundle (RATIFIED 16/09): ..."). EVIDENZA (re-A/B del
bundle curato, wt/endgame-bundle 258ac6cbe, rebase su main 9f255d26): A = B = [-1/0]
hard, NO-OP PIENO in consegna, criteri a-e TUTTI VERDI (il (c) sanity a conteggio
2=2 con gruppi rossi diversi, variabilita' GRASP dichiarata), con le prove che le cure
vivono (CHC banner 45/45 enabled=true, zero TOL-DONOR-UNLOCK mid-cycle; la notte
prima della cura gli stessi bracci divergevano -2 vs -7). Il no-op pieno valida
l'adozione come assicurazione sotto la direttiva bounded-no-op (precedente CSF):
positive sui bacini misurati (B3 -9 -> -8 per tol-donor e compact-swap, A3 -14 ->
-11), inerti su questo. NOTA: v2-swap porta l'interazione H33 nota (overshoot
floor+1 medium, code-review 7/09) ora attiva di default: dichiarata, medium-only.
Il gruppo intero e' la configurazione validata: attivare un subset sarebbe una
configurazione mai misurata. Docs: `endgame-bundle/reab-cura/{CRITERI,VERDICT}.md`
(+ `attribuzione/` per la diagnosi pre-cura e `censimento-flag-bounded-noop.md`).

## ⚠️ Direttiva primaria: ATTIVA TUTTO CIO' CHE E' BOUNDED A NO-OP (maintainer, 2026-09-15)

A residuo piccolo ogni -1 conta e 0-hard e' binario: ogni pass deterministico della
delivery chain sotto oracolo improve-or-noop full-hard che possa dare qualcosa di
positivo si attiva DEFAULT ON, ANCHE se sul bacino noto e' no-op: il no-op e'
garantito dall'oracolo (l'hard consegnato non puo' peggiorare), il costo e' secondi
censiti, e su un bacino GRASP diverso puo' scattare. Corollario: a residuo <= ~10,
ogni -1 ha diritto a un tentativo bounded (banco, dati esistenti, zero cicli) PRIMA
di essere dichiarato strutturale o parcheggiato; le esclusioni legittime sono solo
di costo-di-ciclo, mai di taglia. CONFINI (la regola NON si estende oltre):
(a) vale SOLO per pass sotto oracolo con gate per-mossa stile TASK-616 (niente
scambi net-zero fra famiglie hard: l'oracolo per-pass accetterebbe i pari);
(b) NON copre costruzione (es. 615 scan-forward), cambi di vincolo/modello/pesi
(cambiano il paesaggio senza revert) ne' meccanismi compute-negativi (es. M-TC
flat wall 2x): quelli restano dietro le loro gate; (c) ogni attivazione porta la
sua riga di census/banner osservabile (niente ON silenziosi). Procedura: censimento
dei flag default-OFF da claudedocs/knobs.md -> classe (a) oracle-bounded -> banco
no-op check -> adozione con A/B appaiato come 612/613. Precedenti: CSF TASK-609
(assicurazione a costo zero), EDF-relay TASK-612 (no-op live, adottato), leve
blind-live-r1 A/B.

## ⚠️ Direttiva primaria: ITERAZIONE SU SNAPSHOT, il ciclo completo e' SOLO il gate di adozione (maintainer, 2026-09-06)

Lo sviluppo e la misura dei cambi al solver NON si fanno a colpi di ciclo completo. La scala obbligata:
1. **Test localizzati** (unit + banco live-state: minuti) per ogni candidato, prima di qualsiasi lancio.
2. **Snapshot di confine** (cattura TASK-542, flag `planner.checkpoint.capture.enabled` ATTIVO DI DEFAULT in ogni ciclo di misura lanciato da ora): il confine dove il meccanismo spara davvero (es. post-B-ROOM per la catena deterministica) e' il dataset su cui i candidati iterano. Il banco che carica lo snapshot, ripubblica le statiche dalla via di codice del ciclo e conta col PROVIDER e' la forma di misura intermedia canonica.
3. **Warm start verso 0-hard**: se una consegna e' VICINA a zero (pochi punti hard), non si riparte dalla costruzione: i candidati girano sullo snapshot prefinale fino allo zero, e SOLO il vincitore paga il ciclo completo di validazione/adzione.
4. Il ciclo completo serve per: adozione/ratifica, misura end-to-end di consegna, cattura di snapshot freschi. MAI come primo strumento di verifica di un'idea.
Precedente (perche' questa e' direttiva): 6/09, due reminder di maintainer nella stessa mattina per strappare il banco e gli snapshot a una sessione che stava lanciando cicli per misurare. Il collo di bottiglia del progetto non e' il tempo di solve: e' l'abitudine di pagarlo quando non serve.

## ⚠️ Direttiva primaria: correttezza logica vs costo computazionale (maintainer, 2026-07-21)

Un cambiamento di MODELLO (vincoli, fact, target, VRP) si giudica SOLO sulla correttezza logica — tipicamente: "riflette un input autoritativo o la pratica golden?". Se è logicamente corretto, **non si torna indietro per ragioni di costo computazionale**: la performance è un problema di RICERCA e si attacca con la configurazione del solver (benchmark delle metaeuristiche — tabu, late acceptance, pesi delle mosse — che storicamente hanno avuto impatti grossi sui tempi), mai annacquando il modello. Solo una CONTRADDIZIONE logica (target incompatibili tra vincoli, input che si smentiscono) giustifica di toccare il modello. Ordine di verdetto per ogni run: (a) hard=0 e KPI buoni → modello e ricerca ok; (b) hard non si azzera nei budget → il modello resta, il benchmark diventa prioritario; (c) incoerenza logica dimostrata → si corregge il modello. Precedente: TASK-385 (retarget all'atteso per-data: CH più conflittuale ma medium dimezzato — modello giusto, costo da assorbire lato ricerca). Nota: tutti i benchmark pre-TASK-377 girarono con provider "cieco" e non fanno testo.

## ⚠️ Definition of Done — OBBLIGATORIA per ogni attività (2026-07-17)

Nessun task, fix o feature è considerato completo senza, nell'ordine:
1. **Skill `/simplify`** eseguita sul diff dell'attività, con i finding applicati (o scartati con motivazione scritta).
2. **Skill `/code-review high`** eseguita sul diff: candidati verificati (verificatori Opus), TUTTI i confermati corretti, i deferred annotati nel task backlog competente.
3. **Verifica finale con EXIT CODE** (mai fidarsi di tail/pipe: `cmd > log; echo $?`) di `./scripts/core/test.sh` — più `ui-typecheck.sh`/`ui-build.sh` se il frontend è toccato — eseguita dall'orchestratore/sessione principale, non solo dall'agente implementatore.

La stessa DoD è nel `definition_of_done` di `backlog/config.yml` (ogni nuovo task la eredita) ed è già appesa ai task aperti. Chi chiude un task spunta le voci DoD nel backlog (`--check-dod`).

## ⚠️ CRITICAL: Use Wrapper Scripts, NOT Raw Commands

**NEVER use `curl`, `podman exec psql`, `psql`, `podman ps`, or `podman exec` directly.** These commands are NOT whitelisted.

| Instead of | Use |
|------------|-----|
| `curl ... \| jq` | `./scripts/api/api.sh status\|score\|health` |
| `curl localhost:8080/api/...` | `./scripts/api/api.sh status\|score\|analysis\|...` (named cmds; `raw` removed — `api.sh --help`) |
| `podman exec ... psql` | `./scripts/db/db-query.sh "SQL"` or `python3 scripts/db/db-stats.py status` |
| `podman ps --filter` | `./scripts/container/podman-tools.sh list` |
| `./planner.sh start & wait` | `./scripts/core/dev-start-and-wait.sh dev` |
| `mvn clean package` | `./scripts/core/rebuild.sh` |
| `mvn test` | `./scripts/core/test.sh` |

See `claudedocs/scripts.md` for all available scripts.

Riferimento completo dei knob `-Dplanner.*`: `claudedocs/knobs.md` (generato da
`scripts/analyze/list-planner-knobs.py`, rigenerare dopo aggiunte e verificare che il conteggio coincida).

## ⚠️ CRITICAL: No Background Pipe Chains

**NEVER use `run_in_background=true` with commands containing pipes (`|`) or chains (`&&`, `||`).** This creates orphaned processes that hang forever.

| ❌ Wrong | ✅ Right |
|----------|---------|
| `api.sh analysis \| python3 "..." &` | `./scripts/api/constraint-violations.sh H17` |
| `cmd1 \| python3 && cmd2 \| python3 &` | Create a wrapper script |
| `long-running \| filter &` | Run synchronously with timeout |

**Why this breaks:**
- Pipe chains in background abandon the downstream process
- The downstream process waits for stdin that may never arrive
- Parent can't cleanup children because it's already detached
- Processes accumulate (13+ hour runtimes observed)

**Use wrapper scripts instead:**
- `./scripts/api/constraint-violations.sh` — Check constraint violations safely
- `./scripts/api/api.sh` — Already has 30s timeout built-in
- Create new scripts for complex multi-step operations

**Protected by:** `.claude/hooks/check-background-pipes.sh` automatically blocks dangerous patterns.

## ⚠️ CRITICAL: -D system properties for a LIVE SOLVE must be on the `java -jar` command line, not MAVEN_OPTS

`planner.sh start` / `dev-start-and-wait.sh` launch the app via `mvn quarkus:run`, which **forks a separate `java -jar target/quarkus-app/quarkus-run.jar` process**. `-D` system properties passed via **MAVEN_OPTS stay in the Maven JVM and never reach that forked app JVM**. So flags read via `System.getProperty()` are silently dropped when the app is started this way, e.g. `-Dplanner.phase-transition-dump.enabled=true` (per-phase erosion dumps never get written) and the `-Dplanner.m32.numerator=...` knobs (default values 100000/20/500 used). Verified during the TASK-326/419 overnight campaign (2026-07-25): a MAVEN_OPTS-launched solve produced zero phase-transition dumps.

To get such a `-D` into the running app, launch it directly via `java -jar` with the `-D` flags **on the command line** (this is the val408/baseline launch mode, and `./planner.sh kill-app` stops it reliably via `pgrep -f quarkus-run.jar`):
```
java -Xmx2g -Dplanner.phase-transition-dump.enabled=true \
  [-Dplanner.m32.numerator=300000 -Dplanner.m32.min=60 -Dplanner.m32.max=1500] \
  -Dquarkus.hibernate-orm.schema-management.strategy=update \
  -jar target/quarkus-app/quarkus-run.jar
```
`./planner.sh prod --fresh <profile>` is the equivalent one-command path (it runs `java -jar` directly). Verify the knobs arrived by grepping the app log for `M32 template-deviation weight knobs: numerator=...` (defaults 100000/20/500 mean they did NOT arrive). NOTE: this gap is **live solves only**; the same `-D` flags reach the test JVM normally via surefire (`./scripts/core/test.sh`).

## Help & Discovery

| Question | Command |
|----------|---------|
| What API commands are available? | `./scripts/api/api.sh --help` |
| What database commands exist? | `./scripts/db/db-stats.py --help` |
| All scripts overview | See `scripts/README.md` |

## What This Is
A Quarkus + Timefold Solver application for Italian vocational school timetable scheduling.
React frontend (via Quinoa), PostgreSQL database, Hibernate Panache ORM.

# Quarkus Coding Guidelines

This project uses **Quarkus**, a Kubernetes-native Java framework. Key architectural patterns to follow:

## Architectural Style: Panache and Reactive
Unless instructed otherwise, default to the modern, streamlined Quarkus APIs:
* **Web Layer:** Use RESTEasy Reactive (`@Path`, `@GET`, `@Post`) rather than Spring Web or standard servlets.
* **Data Layer:** Use Hibernate ORM with Panache. Prefer the Active Record pattern (extending `PanacheEntity`) for rapid prototyping, or the Repository pattern (`PanacheRepository`) if separation of concerns is explicitly requested.

## Dependency Management: Prefer Quarkus Extensions
Quarkus optimizes dependencies at build time. Always prefer official Quarkus extensions over vanilla Java libraries.
* Whenever asked to implement a new feature (e.g., caching, messaging, scheduling), first check if a native `quarkus-*` extension exists for it.
* To add dependencies: edit `pom.xml` directly, then run `./scripts/core/rebuild.sh`

## Testing: Write Tests with New Features
* When writing new features, always write the accompanying JUnit 5 / REST Assured tests.
* Ensure tests use `@QuarkusTest` to hook into the Quarkus lifecycle.
* Tests use H2 in-memory database (no PostgreSQL required).

## Project Structure

See @PROJECT_INDEX.md for a quick reference to the codebase layout, domain entities, and key files.

## How to Run
Use `./planner.sh` to manage database profiles and start the app:
- `./planner.sh start <profile>` — Start DB + app (creates fresh profile with seed data if new)
- `./planner.sh run` — Restart app only (DB must be running)
- `./planner.sh stop` — Stop the DB container
- `./planner.sh delete <profile>` — Delete a profile's data
- `./planner.sh kill-app` — Kill the running Quarkus process (also called automatically by stop/run)
- `./planner.sh wait` — Wait until the app is ready (poll http://localhost:8080)
- `./planner.sh status` — Show what's running

`planner.sh list` lists profiles. For richer metadata (sizes, timestamps) use `python3 scripts/db/db-stats.py profiles`.

The script uses `mvn quarkus:run` (production mode) with `-Dquarkus.hibernate-orm.schema-management.strategy=update`.
Do NOT run `mvn quarkus:dev` directly — it uses `drop-and-create` which wipes all data.

### Scripted server startup
**ALWAYS use `./scripts/core/dev-start-and-wait.sh dev`** for automated server startup. Hooks block `planner.sh run` with background `&`.
```bash
./scripts/core/dev-start-and-wait.sh dev    # Start DB + app, wait for readiness
./scripts/core/dev-start-and-wait.sh dev 60  # With 60s timeout
```

### Production Deployment
Run the built JAR directly (without Maven):
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Configure via environment variables:
```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db-host:5432/planner
export QUARKUS_DATASOURCE_USERNAME=planner
export QUARKUS_DATASOURCE_PASSWORD=secure-password
export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=update
```

## Build Operations

**IMPORTANT: This project does NOT have a Maven wrapper (`mvnw`).** Use the provided scripts instead.

### Build Scripts

| Script | Purpose | Speed |
|--------|---------|-------|
| `./scripts/core/rebuild-checked.sh` | **Full rebuild** with preflight checks (backend + frontend) | ~30-60s |
| `./scripts/core/rebuild.sh` | **Quick rebuild** - same build, fewer checks (backend + frontend) | ~20-40s |
| `./scripts/ui/ui-build.sh` | Frontend build only | ~20s |
| `./scripts/ui/ui-dev.sh` | Start dev server with hot reload (UI-only) | N/A |

### db-query.sh: read-only by default, use -w for writes
`db-query.sh` opens `SET TRANSACTION READ ONLY` by default. For INSERT/UPDATE/DELETE, pass `-w`:
```bash
./scripts/db/db-query.sh -w "INSERT INTO labrotation ..."
```

### Column names: Hibernate Panache uses camelCase, not snake_case
The database columns follow Java camelCase naming (e.g., `studentgroup_id`, `dayofweek`, `maxhoursperday`), NOT SQL snake_case (`student_group_id`, `day_of_week`). Always check with `information_schema.columns` if unsure.

### When to Use Each Script

**Use `./scripts/core/rebuild-checked.sh` (full)** when:
- You've made frontend changes in `src/main/webui/`
- You want a complete fresh rebuild with validation
- You're unsure and want to be safe
- After pulling major changes from git

**Use `./scripts/core/rebuild.sh` (quick)** when:
- You've made Java code changes in `src/main/java/`
- You've changed constraints or solver config
- You know the database is running and JAR exists
- You want a faster rebuild during active development

### What These Scripts Do

Both scripts run `mvn clean package` which builds **everything** (backend + frontend via Quinoa). The difference is in preflight checks:

**rebuild.sh (quick):**
1. Checks if database is running (errors if not)
2. Kills running app (`./planner.sh kill-app`)
3. Runs `mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true`
4. Restarts app in background (`./planner.sh run`)
5. Waits for app to be ready (`./planner.sh wait`)

**rebuild-checked.sh (full):**
1. Checks if JAR exists (errors if missing)
2. Checks if database is running (starts it if needed)
3. Same Maven build and restart as rebuild.sh
4. Supports `--no-wait` option to exit immediately after restart

### Quick Verification After Java Changes

After making Java code changes, verify with:
```bash
./scripts/core/rebuild.sh
```

Check the output for:
- `[INFO] BUILD SUCCESS` — compilation succeeded
- No new warnings related to your changes
- App ready at http://localhost:8080

## ⚠️ Direttiva architetturale: la soluzione gira sul SOLO INPUT, il golden e' temporaneo (maintainer, 2026-07-30)

**STATO RAGGIUNTO (19/08, M13-FINAL)**: il percorso di produzione gira SENZA golden nel JAR. Le
due letture golden-runtime superstiti sono state congelate come input dichiarabili nel workbook:
`seed/golden-copresence-depth.csv` (136 celle, sostituisce `loadGoldenCoPresenceDepth` runtime) e
`seed/golden-slot0-anchors.csv` (14 gruppi, sostituisce `loadGoldenSlot0Anchors`). Il golden resta
nel repo come dataset storico e oracolo di validazione (i test golden-oracle), non come
dipendenza runtime. Piano di ritiro completo: `claudedocs/spalmone-2026-27/golden-retirement-plan*.md`.

In esercizio la scuola ci passera' **i dati di input, non una soluzione di esempio**. Il golden e'
un vantaggio della fase di test e sparisce: qualunque meccanismo che deriva comportamento da una
SOLUZIONE ha una data di scadenza, e la condizione finale deve guidare l'architettura fin da ora,
non essere rincorsa dopo.

La distinzione operativa e' fra leggere un **input** (incarichi, calendari, disponibilita', quote)
e leggere una **soluzione** (`seed/lessons.csv`, il golden). Il primo sopravvive, il secondo no.
Per ogni meccanismo che oggi guarda il golden ci sono solo due esiti onesti:
1. cio' che il golden dice e' **deducibile dagli input** -> si cambia sorgente e si verifica che il
   risultato coincida. Questa verifica si puo' fare SOLO finche' il golden esiste: e' una finestra
   che si chiude, e va usata adesso.
2. cio' che il golden dice e' una **pratica della scuola non deducibile** (es. "T01 concentrato
   al giovedi", le ancore settimanali, le cadenze attese) -> nessuna statistica la inventa: va
   CHIESTA e deve diventare input dichiarato. Il veicolo esiste gia' ed e' il workbook (TASK-445).

Corollario per il golden finche' c'e': non serve solo come metro di confronto, serve a scoprire
**quali domande mettere nel workbook**, perche' dice quali pratiche sono portanti.

Debito censito e i suoi punti concreti: **TASK-452** (`ConstrainedTeacherAllocator` del TASK-435
e `MonoRoomTagAllocator` del TASK-449 leggono `seed/lessons.csv` a runtime). Attenzione: dal
2026-07-30 il TASK-435 e' **adottato per default**, quindi quella lettura del golden e' ora sul
percorso di produzione ordinario e non piu' dietro un flag spento. Dal 29/08 anche la pratica
weekday del parity cap del pre-booking (`DeclaredDayPreBooker`, lettura `seed/lessons.csv`
fail-open) e' sul percorso di produzione ordinario (ratifica TASK-564 DEFAULT ON): terza lettura
golden-runtime da censire nel ledger e da sostituire col workbook (foglio pratica weekday) prima
del ritiro del golden. Politica per un anno senza
golden: **TASK-441**.

## Manutenzione di questo file: puntatori, non copie (2026-07-29)

Se un fatto vive gia' in un posto autoritativo (il codice, un foglio del seed, un doc di
riferimento), qui ci va il **puntatore**, non la copia. Un fatto duplicato non resta
sincronizzato: diverge, e la copia stantia viene letta come verita'. Tre precedenti reali,
tutti scoperti col naso nello stesso guaio:
- `.claude/rules/java-build.md` duplicava le regole di build e le contraddiceva, arrivando a
  ordinare `mvn quarkus:dev` in background, che cancella i dati. Rimossa, non riscritta.
- "18 rooms (including 2 zero-capacity rooms, R17 and R18)" duplicava il
  `DataGenerator` e ha continuato a dirlo dopo la correzione di R17 a 22.
- `PROJECT_INDEX.md` duplicava le tabelle dei vincoli finche' non hanno divergito: oggi
  rimanda a questo file, che e' l'unico posto dove stanno.

Corollario per chi scrive qui: preferire una riga che dice DOVE guardare e PERCHE' quel posto
e' autoritativo, a un paragrafo che ripete cosa c'e' scritto.

## Reference Documentation (on-demand)

These docs are NOT auto-loaded. Use `Read` tool when needed:
- `claudedocs/constraint-recalibration-history.md`: archivio VERBATIM dei saggi storici di ricalibrazione dei vincoli (spostati da qui col TASK-431; ogni voce di "Design Decisions" qui sotto ne punta l'ancora)
- `claudedocs/orchestration-layer-architecture.md`: orchestration layer at phase boundaries (operator census with entry/exit/rollback/gate contracts, stochastic-vs-exact decision framework, literature positioning, roadmap, TASK-479). The staged cycle, tier-scoped gating, deterministic repair passes, ExactFinisher, CompositionHoleCloser, and multi-start live here
- `claudedocs/scripts.md` — validation, benchmark, git workflow scripts
- `claudedocs/database-schema.md` — PostgreSQL schema (tables, columns, FKs)
- `claudedocs/psql-cheatsheet.md` — common psql queries (prefer `db-stats.py`/`db-query.sh`)
- `claudedocs/rest-api.md` — full endpoint reference (schedule, solver, CRUD)
- `claudedocs/solver-phases.md` — 5-phase solver pipeline architecture
- `claudedocs/human-planner-heuristics.md` — human planner 5-step pipeline
- `claudedocs/branch-families.md` — branch family docs + solver config variants
- `claudedocs/localized-testing-guidelines.md` — **LEGGERE PRIMA di scegliere come testare una modifica al solver**: piramide dei test localizzati (runtime misurati), matrice tipo-di-modifica → catena minima, contratto di stato per fase; il ciclo completo è solo il gate di adozione (laptop)

## KPI golden-vs-run e cattura A/B (campagna spalmone) — OBBLIGATORI per validare un solve

Ogni validazione di un solve o giro A/B deve produrre la batteria KPI standard, MAI misure ad-hoc:
- **`scripts/benchmark/capture-run.sh <outdir>`** — la cattura canonica post-solve (score, analysis, export, lessons.txt + tutti i KPI + viz HTML). Le copie in `claudedocs/spalmone-2026-27/task*-results/` sono istanze storiche: i nuovi cicli usano questa.
- KPI singoli in `scripts/analyze/` (input: dump `lessons.txt` formato `group|date|hour|subject|teacher|pinned`, o il CSV golden): `daily-fill-kpi.py` (full-day rate vs attese), `day-start-kpi.py` (ancoraggio prima ora), `resemblance-kpi.py` (ricorrenza settimanale, con breakdown per gruppo), `moda-knot-count.py`, `cadence-kpi.py` (TASK-413: cadenza per (gruppo, materia), burst ratio, gap-weeks), `golden-vs-run-viz.py` (confronto HTML 4 viste).
- **Erosione ricorrenza** (costruzione vs finale): `./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest` scrive `target/construction-lessons.txt`; `resemblance-kpi.py` su quel dump (~76%) vs sul `lessons.txt` del run finale misura quanto la LS erode il template.
- **CONSUMARE l'output visivo, non solo generarlo** (maintainer, 2026-08-18): la batteria produce
  confronti HTML (`golden-vs-run-viz.py`, `chain-journey-*.html`, day-start-anatomy) ma nessuna
  regola ne impone la LETTURA. Prima di scrivere un VERDICT o decidere una strategia: aprire il
  confronto HTML del run (browser o screenshot), guardare le forme (dove si concentrano i
  colori, quali giorni/gruppi spiccano, cosa cambia tra braccia), e annotare nel VERDICT cosa
  l'occhio vede che i numeri nascondono (precedente: il chain-journey ha reso ovvio che ogni
  round spostava la box H0 invece di chiuderla, pattern invisibile nei verdetti singoli). I
  viz sono prodotti per essere letti: un confronto HTML mai aperto e' costo senza informazione.
- **La diff visualizzata come metro di sanita'** (maintainer, 2026-08-19): le validazioni di qualita'
  del risultato beneficiano del dataviz per natura: POCA differenza dal golden, in positivo o
  negativo, e' un buon segno (siamo nella pratica della scuola); TANTA differenza e' un warning
  da investigare, anche quando il numero aggregato sembra buono. La lettura visiva della diff
  run-vs-golden e' il controllo di sanita' finale di ogni run di validazione, non un extra.
- Riferimenti golden pinnati per il confronto: `claudedocs/spalmone-2026-27/data/golden-cadence-reference.json` (+ `.md`).
- **Strumenti del loop zero-hard** (TASK-637, 21/09: la skill `.claude/skills/zero-hard-loop`
  e' il processo completo): `scripts/analyze/residual_decomposition.py` (decomposizione
  famiglie/celle di una consegna con regole di scope corrette + lookup nel registro cure
  `claudedocs/spalmone-2026-27/cure-registry.json` + GENERA i viz da aprire), i viz
  `course_pacing_viz.py` / `day_anatomy_viz.py` / `family_matrix_viz.py` /
  `sanity_stoplight_viz.py` (consumo obbligato prima del verdetto), il catalogo stati
  post-LS `post_ls_catalog.py` + `post-ls-catalog/` (TASK-634: hash seed obbligatorio,
  guardia anche lato Java in DeliveryTrioBenchTest), la serie famiglie in
  `series-families.json`. Scala di validazione: snapshot-first, il ciclo completo e' SOLO
  il gate di adozione.
- **Il golden come oracolo sui dati, PRIMA dell'archeologia sui fogli** (maintainer, 2026-07-30): il
  golden e' una soluzione reale e valida della scuola, quindi qualunque dubbio su un numero di
  input si autoverifica chiedendo al golden cosa la scuola ha fatto davvero. Esempio costato
  un'ora: "G04 deve fare 835 ore o 750?" si risolve contando le ore che il golden colloca per
  quel gruppo (750), non ricostruendo da quale foglio venga l'835. Ricostruire la provenienza di
  un numero serve solo DOPO, se il golden e l'input si contraddicono. Corollario: quando un
  numero non torna, la prima ipotesi non e' "il dato e' sbagliato" ma "non ho capito la
  trasformazione che l'ha prodotto", e il golden dice quale delle due.
- **Trappole del golden da escludere in OGNI confronto sul monte-ore o per docente** (2026-07-29,
  hanno gia' prodotto due misure false): il golden contiene **117 ore di sabato**, di cui **112
  il 19/09/2026 con docente E materia entrambi `Fasullo`** ("finto" in italiano, un
  segnaposto dei pianificatori: non esiste negli incarichi, 8 ore per ognuno dei 14 gruppi,
  e il foglio calendario non ha nemmeno la riga per quella data). Le altre ore di sabato sono
  187 di Stage (fuori griglia per natura) e 5 di Orientamento il 16/01/2027. La nostra griglia
  e' lunedi-venerdi, quindi quelle ore non sono collocabili e inseguirle produce deficit
  fantasma: senza sabati, settembre e' golden 880 contro run 892 (siamo AVANTI di 12) e lo
  scarto annuo vero e' -44 ore su 10.542, lo 0,4%. `parse_golden_csv` in
  `scripts/analyze/shared.py` NON filtra il giorno della settimana. Un docente presente nel
  golden ma assente dagli incarichi non e' classificabile: va scartato e DICHIARATO, non
  assorbito in silenzio (`teacher-tier-kpi.py` lo fa). Dettaglio:
  `claudedocs/spalmone-2026-27/task458-settembre-2026-07-29.md`.

Dettagli e usage completi: `claudedocs/scripts.md`, sezione "KPI golden-vs-run".

## maxAttempts DEFAULT 5 (maintainer, 2026-08-16)

**Il cap del multi-start passa da 3 a 5 tentativi** (notazione: `MultiStartRunner.DEFAULT_MAX_ATTEMPTS = 5`). Contestuale al source flip: il path input ha una coda GRASP (32% bad-seed, census n=19) che N=3 copre solo al point estimate (0.969, CI_lo 0.843), mentre N=5 porta CI_lo a 0.954. Costo zero sul good basin (stop-al-primo-0-hard, misurato). Override: `-Dplanner.multistart.max-attempts=N`.

## Source DEFAULT = input (maintainer, 2026-08-16)

**RATIFICA source flip**: il default di `planner.pre-assign.constrained-teacher.source`
e' cambiato da `golden` a `input` (forma opt-out: solo
`-Dplanner.pre-assign.constrained-teacher.source=golden` ripristina il precedente).
La catena di evidenza: A/B appaiato 13/08 (0hard entrambi, KPI in banda), census n=19
(p=0,684 GRASP, baseline deterministica 0hard), rehearsal production 16/08 (0hard al
primo tentativo, ~23 min, KPI in banda, e103d8f3). Il file golden RESTA presente:
H-COPRESENCE depth map lo legge indipendentemente da questo flag (la sua via di
sopravvivenza e' il foglio workbook co-presenza, TASK-452 #7, non questo flip).

## Database

## Database Profiles
Each profile = a Podman volume (`planner-data-<name>`). Data persists across restarts.
New profiles are auto-seeded by `DataGenerator` (teachers, courses, timeslots, holidays).
The seeding is idempotent: skipped if `Timeslot.count() > 0`.

### Seed Data Volumes (2026/27 school year, TASK-343)
- **26 teachers** (real) **+ 1 pseudo teacher "Stage"** (TASK-381 "stage on the grid", `DataGenerator.generateStageCourses`, maintainer 2026-07-20 authorization)
- **14 student groups** (1-G11, 1-G10, G12, 1-G09, 1-G08, G13, G14)
- **1,392 timeslots** (174 school days x 8 slots/day, Mon-Fri, 2026-09-14..2027-06-15; TASK-550-ext 25/08: griglia estesa al 15/06 con quota golden-mirror sui 3 giorni oltre il vecchio orizzonte 10/06: 11/6 stage G04, 14/6 stage G04 + RA G08, 15/6 Scienze G04 + RA G08, NESSUNA riga per gli altri 12 gruppi = no-session H26, capacita' extra solo dove il golden la pratica; prima: TASK-408 171/1.368 fino al 10/06)
- **29 closure dates** (Christmas, Easter, Carnevale, national holidays — see `DataGenerator.CLOSURES_2026_27`)
- **275 course entries** (from `src/main/resources/seed/incarichi.csv`; see `claudedocs/spalmone-2026-27/seed-csv-format.md`) **+ 8 Stage pseudo-courses** (one per stage-eligible group: G04/G07/G11/G08/G10/G12/G09/G13; TASK-381; G04 stage window added by TASK-434). Dal TASK-550 (25/08) il seeding e' a DENSITA' GOLDEN-OPEN: gli stub sono solo le ore che il golden tiene davvero (closed=False: G12 112 su 295 dichiarate, G10 228, G04 224, G11 287, G09 292, G08 276, G07 e G13 295), la differenza finisce in `expectedUnscheduledHours` (il foglio chiedeva la finestra piena: domanda fantasma, causa del muro -10.8k boxed)
- **18 rooms**, of which exactly **one has zero capacity: R18** (R17 was cap 0 by mistake and referente corrected it to 22 on 2026-07-29). Since the TASK-456 ratification a room with capacity <= 0 is **not assignable** (`Room.isAssignable`), which is how the school's "exclude R18" request is implemented: a general rule, not a hardcoded name. Check the count before assuming: another cap-0 row would silently take a room out of play.

## Architecture
**IMPORTANT:** `bootstrap/DataGenerator.java` contains real school data. NEVER modify without explicit user permission. See `.claude/rules/datagenerator-protection.md` for rationale and known discrepancies.

- **Domain entities**: `src/main/java/com/school/planner/domain/` — JPA entities with Panache
- **Constraints**: `solver/SchoolScheduleConstraintProvider.java` — see Canonical Constraint IDs below
- **REST API**: `rest/` — JAX-RS resources for CRUD + solver control
- **Services**: `service/` — business logic (scheduling, pre-assignment, lesson generation)
- **Seed data**: `bootstrap/DataGenerator.java` — runs on startup if DB is empty
- **Frontend**: `src/main/webui/` — React + Vite, built by Quinoa into the JAR
- **Tests**: use H2 in-memory DB (no PostgreSQL needed), `src/test/resources/application.properties`
- **Solver phases**: `solverConfig.xml` — 5-phase pipeline (TASK-420 removed the inert LS RuinRecreate). See `claudedocs/solver-phases.md` for the full architecture.

## REST API Endpoints

See `claudedocs/rest-api.md` for the full endpoint reference (schedule, solver, pre-assignment, CRUD).

## Pre-Assignment Workflow

Human schedulers follow a deterministic heuristic sequence before solving. The pre-assignment pipeline pins known assignments before the solver runs, reducing the search space. See `claudedocs/human-planner-heuristics.md` for the full 5-step pipeline as described by the school's human planners.

### How it works
1. `LabRotation` records define weekly patterns: {teacher, group, dayOfWeek, slotStart, blockSize, periodStart–periodEnd, roomTag}
2. `HybridPhase1aService` runs a CSP-based approach that mimics human planners: iterate rotation candidates, test for 0hard violations, pin successful ones
3. Phases run sequentially: acconciatura → labs → professionalizzanti
4. The solver then runs on unpinned entities only

### Doppio (co-teaching)
`Course.doppioTeacher` marks a second teacher who must be present during the primary's lab session. The old hard H-D1 approach is gone (no such constraint is registered, censimento TASK-446): the doppio/assistant semantics live in M-LA (medium tier, `LabAssistant` entity), which penalizes scheduling the assistant in conflict with a lab in their room.

### External teacher availability
T28 is external (Thursday only). `DataGenerator` creates VACATION records for every non-Thursday weekday, so H5 prevents scheduling on those days even without pre-assignment.

### LabRotation seed data
`DataGenerator.generateLabRotations()` is INTENTIONALLY EMPTY (TASK-364): `LabRotation.count() == 0` on the 2026/27 seed, so the pre-assign pipeline's Phase 1/2/3 are structurally skipped (only Phase 0 constrained-teacher runs). Exact rotation assignments (which teacher gets which group in which period) need confirmation from the school before any rows are seeded.

## Modalita' di solve della campagna: STAGED (ADOTTATA, maintainer 2026-07-27)

Il solve di riferimento e' il **ciclo a stadi** (`POST /api/schedule/solve-staged`, ovvero
`./scripts/api/api.sh solve-staged` con attesa staged-aware): A-CONSTRUCTION -> PROMOTION
del template a pinned -> B-TIMESLOT (con demozioni selettive, max 2 iter) -> RELEASE ->
HoleRepair -> AnchorRepair -> SurplusRebalance -> B-ROOM. Adottato in pieno col "treno-2" (TASK-419 +
M20/M20.2 progressivi + A4 ranking deficit-aware + TASK-422 SurplusRebalance + TASK-423
M33 anti-surplus) dopo il gate del 26-27/07: hard 0 doppio, buchi interni 0, ricorrenza
unpinned ~73,5-74 (baseline pre-campagna 52,8), erosione template ~0. RATIFICA ESTESA
(maintainer, sera 2026-07-27, evidenza n=2 in ratifica-m12-2026-07-27.md): adottati anche
TASK-424 (M20/M33 su quota NETTA dei pin autoritativi) e TASK-425 (M34 burst cap 100 +
M35 spread 50, self-gated sul reference golden per-pair) - full-day 78,2-78,5, giorni
sopra 83-87, burst 40,2-41,8 (~golden 41,6), hard 0 su entrambi i run freschi. Evidenza completa:
`claudedocs/spalmone-2026-27/campaign-status-2026-07-26-gate419.md`. Il solve ordinario
(`POST /api/schedule/solve`) resta disponibile per confronti/debug, non e' piu' la
modalita' di campagna. **UI = protocollo campagna (2026-08-15, maintainer: la UI e' il canale
di interazione principale)**: il pulsante Solve della web UI chiama `POST /api/schedule/
solve-staged` (scheduleApi.startSolving), e `startStagedSolving` GARANTISCE la
pre-assegnazione prima del ciclo (`ensurePreAssignmentForStagedCycle`: se lo status non
e' COMPLETE la esegue in sync pubblicando lo stage `PRE-ASSIGN`, visibile e stoppabile;
se e' RUNNING la aspetta; i driver di benchmark che pre-assignano esplicitamente trovano
COMPLETE e passano dritti, comportamento invariato). Un click = protocollo production
completo; il solve senza pin (trappa overdemand TASK-435) non e' piu' raggiungibile dalla
UI. I gap residui verso il golden sono tracciati nella milestone
backlog **m-12** (capofila TASK-424).

RATIFICA TASK-433 (maintainer, 2026-07-28 mattino): **AnchorRepair adottato** come pass
deterministico nel ciclo staged, incondizionato (sibling di HoleRepair/SurplusRebalance,
post-HoleRepair / pre-SurplusRebalance). Chiude i buchi LEADING di M18 (late-start) per
import cross-date con guard M18 OBBLIGATORIA (no shell-game). Evidenza gate2 (3 run):
day-start 82 -> 0/1/13 late days, 0 conflitti, 0 newly-late. NON e' piu' flag-gated (era
`-Dplanner.construction.anchor-repair.enabled`, default OFF; rimosso dal banner diagnostico
in `ScheduleService.refreshSolverStatics`). TASK-430 (slot0-balancing) e TASK-432
(profile-following) restano flag-gated default OFF (bocciate dal gate2: 430 -hard e
-full-day, 432 24 H0 T01 infeasibile).

RATIFICA TASK-435 (maintainer, 2026-07-30 notte): **constrained-teacher pre-assignment pin adottato
DEFAULT ON** (opt-out con `-Dplanner.pre-assign.constrained-teacher.enabled=false`, stessa forma
del ripiego lab M-LF, letto LIVE da `HybridPhase1aService.isConstrainedTeacherEnabled`; banner in
`ScheduleService.refreshSolverStatics`). Era l'adozione che rendeva i numeri della campagna non
riproducibili col protocollo standard: senza la pre-assegnazione il triangolo G06 / Servizio
tecnico e stilistico / T01 va in overdemand di 30h e 35 lezioni restano non collocate, con la
pre-assegnazione lo slack e' +5h e ne restano 3 (`task459-slack-triangolo-g06-t01-2026-07-30.md`).
I run di riferimento (447, 30b, 30c) giravano gia' tutti col flag acceso: hard -169, 3 boxed,
room-null non-lab 0, full-day 87,5% (88,6% col conteggio KPI corretto). Numeri gia' misurati, non
serve run di conferma.

RATIFICA TASK-451 (maintainer, 2026-07-31 mattina): **blocco prima-ora slot0-1 coordinato adottato
DEFAULT ON** (opt-out con `-Dplanner.construction.first-hour-slot.enabled=false`, letto LIVE da
`WeeklyTemplateBuilder.isFirstHourSlotEnabled`, stessa forma opt-out del constrained-teacher;
banner in `ScheduleService.refreshSolverStatics`). `reserveFirstHourBlock` generalizza il
reserveSlot0Anchors del bocciato TASK-430: riserva il BLOCCO 2h slot0+slot1 col docente = anchor
slot-0 modale del golden per (gruppo, weekday), matching cross-gruppo hardest-first. Evidenza A/B
n=2 stabile vs 30d (task451-results/ab-firsthour-on-run{1,2}): hard -173 -> -108/-109, boxed
3 -> 0, room-null 167 -> 90, day-start 99,9% (istogramma 8:2192, 9:15), ricorrenza unpinned
74,3 -> 75,6%. Costo accettato: **18 buchi H17** (erano 0), che la diagnosi riduce a UN pattern
(G13 venerdi' slot 11: la LS spezza il lab-day 5h su 2 docenti dove golden/30d fanno il blocco
mono-docente T10 8-12; `spike-18-h17-holes` 8dc0d7f9) con cura a valle identificata
(consolidamento lab-day a docente unico, famiglia TASK-449/M-TC) e DA COSTRUIRE. NOTA
golden-runtime: `loadGoldenSlot0Anchors` e' una lettura del golden ora su percorso di produzione,
censita in TASK-452 #6.

RATIFICA COMBINED 463+464 (maintainer, 2026-08-01 mattina): **lab-day-consolidation (TASK-463) e
co-presenza ospite (TASK-464) adottati DEFAULT ON** con opt-out esplicito (stessa forma delle
altre ratifiche: `null` o qualunque valore tranne `"false"` attiva). Letti LIVE per-build: 464
`SchoolSchedule.isCoPresenceEnabled` (`-Dplanner.co-presenza.enabled=false`), 463
`SchoolSchedule.isLabDayConsolidationEnabled`
(`-Dplanner.construction.lab-day-consolidation.enabled=false`, letto a ogni `repair`); banner in
`ScheduleService.refreshSolverStatics`. Evidenza: 464 full-solve A/B n=2
(task464-results/{off-baseline,on-run1,on-run2}) converte i 94 room-null acconciatura in
ospite-legale (hard -112 -> -17/-18, floor storico; 0 over-depth cap; nessuna regressione
full-day/ricorrenza/medium); 463 e' la cura a valle dei 18 buchi H17 di TASK-451 (coordinatore
cross-gruppo docente-giorno, post-HoleRepair/pre-AnchorRepair, improve-or-noop); il combined
run3 source=input post-cura con ENTRAMBI ON chiude hard -6 (i 3 residui TASK-435, 0
room-null/H20/holes, ricorrenza unpinned 75,9%). Gate semantica-stanze chiuso dall'INPUT (autoverifica
referente-autoverifica-domande-2026-08-01.md, il golden come oracolo sui numeri): **Q20** G12 12h
parallele = aula NORMALE (il calendario G12 ha Ore 604 / STAGE 295 / Lab 91 = identico al seed,
i giorni-L coincidono col Servizio tecnico e Barberia del golden, le ore di T26 cadono in
mese a zero lab -> aula per il piano scuola; `subject_overrides.csv` de-tagga Servizio stilistico
per G12); **Q19** G14 andra' in **R10** (risposta referente 01/08: corregge la nostra
inferenza giorni-L "ospite nel lab"; le ore co-schedulate di G14 si fanno in aula, non come
ospite nel laboratorio. Occupancy invariata - G14 non conta come occupante del lab, gia' vero
nel modello via de-tag in `subject_overrides.csv`; la semantica fisica ora e' decisa. Rifiniture
candidate tracciate in TASK-477: (a) escludere le coppie G14 dall'esenzione H2 di 464, che per
G14 non serve piu'; (b) steering stanza G14 -> R10, preferenza da valutare).
NOTA golden-runtime: `loadGoldenCoPresenceDepth` e' una lettura del golden ora su percorso di
produzione, censita in TASK-452 #7.

RATIFICA TASK-469 (maintainer, 2026-08-01 sera): **M-CPS curriculum post-stage target 6h adottato
DEFAULT ON** con opt-out esplicito (stessa forma delle altre ratifiche:
`SchoolSchedule.isCurriculumPostStageEnabled` e' `null/absent -> true`, solo
`-Dplanner.constraints.curriculum-poststage.enabled=false` disattiva; letto LIVE per-build come
M-LF; banner in `ScheduleService.refreshSolverStatics`). Evidenza A/B `task469-results/ab-cps-on-run1`
(source=golden + combined ratified flags + M-CPS ON vs `task463-results/combined-run2` OFF): hard 0
(=baseline, M-CPS e' medium), il curriculum post-stage converge verso il target 6h (G10 12->9,
G08 7->**6** centra il target, G11 24->21 con la cautela asimmetrica rispettata), costo zero
sui KPI di guardia (full-day +0,4pp, ricorrenza unpinned 75,8->75,6 flat, buchi 0, medium neutro
-994722 -> -963616 entro varianza). Verificato independentemente da KPI script
`curriculum-poststage-kpi.py` + query DB (9/6/21 concordano byte-exact). Forma asimmetrica:
full-weight (default 100) sul deficit sotto 6, weak-weight (default 1) sull'eccesso oltre 6; il
per-group stage-end e' INPUT-derived (max `DailyActivityQuota.stageHours`), non golden. Cautela
G11 by design: il weak-weight non combatte il calendario del corso Personalizzazione 60h
(resta ~21 vs golden 29).

RATIFICA TASK-478 (maintainer, 2026-08-02 mattina): **ExactFinisher (completamento esatto del residuo
unassigned post-repair, pattern fix-and-optimize della letteratura matheuristics) adottato DEFAULT
ON** con opt-out esplicito (stessa forma delle altre ratifiche: `SchoolSchedule.isExactFinisherEnabled`
e' `null/absent -> true`, solo `-Dplanner.exact-finisher.enabled=false` disattiva; letto LIVE per-build
come M-LF; banner in `ScheduleService.refreshSolverStatics`). Knob `planner.exact-finisher.max-candidates`
default **1000000** (regolabile, catena di raise 10000 -> 100000 -> 1000000; `ExactFinisher.getMaxCandidates`
LIVE, floored a 1). Catena di evidenza: (1) discriminatore offline `ExactFinisherRun4DiscriminatorTest`
UNCAPPED (budget 100M) sul worst case run4 ricostruito room/id-faithful + quota REALE + Q22 REALE:
depth-0/1 chiude 5/5 residui, 0 conflitti, Q22-fedele (depth-2 NON serve sul worst case noto); (2)
probe LIVE @ 1M su bad seed fresco (`ab-ef-probe-1m-run2`): EF placed 18/18 boxed depth 0..1, H0 ->
0 (vs 13/18 @ 100k in run4); (3) A/B finale `ab-ef-final-run{1,2}` 2/2 hard-0, EF inerte su good seed
(boxed=0 all'entry, log solo banner), medium/full-day dentro la banda good-seed (probe-run1
-1047k/87,6%), nessuna regressione. Il pass e' 0-hard-additive (gate chain H1/H3/H14/H11/H7/H17
condivisa BoxRepair, VRP last) con improve-or-noop revert (`ensureImproveOrNoop`); innesto nel ciclo
staged post-SurplusRebalance / pre-B-ROOM (`ScheduleService.runExactFinisher`). Converte la chiusura
della coda boxed (H0) da probabilistica a **deterministica** (il residual di varianza LS). L'hard
TOTALE del path input resta pero' seed-dipendente (good seed 0, bad seed -6 H17): 0-hard e' BINARIO
e BLOCCANTE end-to-end (direttiva maintainer 2026-08-02), quindi il path input NON e' hard-0 end-to-end e
il gate m-13 resta bloccato finché i 6 H17 non sono curati (TASK-481). **NESSUN cambio al default
source: resta golden** (source=input resta opt-in
`-Dplanner.pre-assign.constrained-teacher.source=input`, decisione ready-when-needed invariata).
Residuo ORTOGONALE a EF ma BLOCCANTE end-to-end (NON trade-off accettabile: 0-hard e' binario): i
-6hard H17 dei run bad-seed (G11 mercoledi ore 10) NON sono creati ne' curabili da EF (la
costruzione e' compatta, e' la LS a introdurre il buco sostituendo la composizione con una
strutturalmente non compattabile; EF prosciolto meccanicamente). Cura search-side tracciata in
**TASK-481**: Opzione A (composition-aware hole-closer, sibling di LabDayConsolidation/ExactFinisher,
post-LDC/pre-B-ROOM) e' stata IMPLEMENTATA e VERIFICATA CORRETTA ma sul bad-seed noto e' INERTE. La
cascata depth-3 e' uno SHELL GAME (probe ristretta `H17HoleCloseDiscriminatorTest`: SUSTAINABLE 0/6,
SHELL-GAME 6/6): ogni bloccante @10 e' mid-run (giorno packato [0..5]), ricolloreferente apre un gap sul
suo gruppo e il buco @10 migra di gruppo (il -1 netto viene da un fill non correlato). @10 e' uno
slot saturo (bacino): la cura locale limitata non converge. `CompositionHoleCloser` resta committed
default-OFF come scaffold corretto (gate `isDepartureSafe` rifiuta lo shell-game, 0 conflitti, 0
danni). DECISIONE FORK (maintainer 02/08 sera): **OPZIONE C, multi-start vero (TASK-480) e' la via
PRINCIPALE per H17**, non cintura+bretelle (0-hard via selezione del bacino, non chiusura locale).
TASK-481 CHIUSO Done con esito onesto (cura locale esclusa, H17 deferito a TASK-480;
CompositionHoleCloser resta committed default-OFF come building block corretto). Doc:
`task481-results/FORK-shell-game-depth3-2026-08-02.md`.
NON e' la metaeuristica: tabu (`<entityTabuSize>7`), pillar (change/swap/pillarChange/pillarSwap) e
forager `acceptedCountLimit 1000` SONO gia' attivi in Phase 2 (`solverConfig.xml`) e lasciano
comunque il buco. Model corretto (golden 0/34). Per TASK-480: EF chiude la coda boxed da solo sul
worst case noto -> il multi-start e' cintura+bretelle per l'H0 (e via principale per l'H17 @10).
Docs: `claudedocs/spalmone-2026-27/task478-results/VERDICT-ab-ef-final-2026-08-02.md` +
`diagnosi-h17-g11-mercoledi-2026-08-02.md` + `discriminatore-uncapped-run4-2026-08-02.md`.
TASK-480 DONE (2026-08-02, alba8): multi-start validato. A/B n=3 GRASP (source=input): 2/3 a 0-hard,
grasp-1 nel bacino bad-seed (-11hard H17); P(>=1 0-hard | N=3)=0.96 [CI 0.50,1.00], pick-best = run
default. Adottato come PROCEDURA operativa per source=input (NON default flip: source resta golden,
GRASP opt-in OFF via `-Dplanner.construction.grasp.enabled`+`.seed`; banner `grasp=%b seed=%d` in
refreshSolverStatics per silent-seed-drop detection). Ortogonale/complementare a EF (EF=boxed H0,
multi-start=selezione bacino H17). Script `scripts/benchmark/multistart-ab.sh`. Limitazione: n=3 non
dimensiona N (frequenza bad-seed = misura differita in background, non bloccante); piano B = TASK-482
Kempe-chain. Verdetto + procedura: `task480-results/VERDICT-fase4-multistart-ab-2026-08-02.md`;
architettura: `claudedocs/orchestration-layer-architecture.md` sez 4.1 (ADOPTED).

RATIFICA TASK-497 M20 pool-cap (maintainer, 2026-08-15): **il target della famiglia day-fill e'
cappato al pool del gruppo (forma b esatta), ADOTTATO DEFAULT ON** con opt-out esplicito
(stessa forma delle altre ratifiche: `SchoolSchedule.isM20CapPoolEnabled()` e' `null/absent
-> true`, solo `-Dplanner.constraints.m20-cap-pool.enabled=false` disattiva; letto LIVE
per-build, catturato una volta in volatile `captureM20CapPoolFlag`, banner `M20 pool-cap
targets (TASK-497, armed=%b): N group(s) over-capacity` in `refreshSolverStatics`). COSA
FA: per i gruppi capienza>pool (G04 835/750, G08 690/666, G09 ~4h alla
ratifica; sul seed post-cura-27/08 la mappa contiene SOLO 1/G08: G09 662<=666
non e' piu' over-capacity) il target
giornaliero passa dal ratio TASK-461 (scala obbligo/capienza per-data, con derive annue)
a target esatti per (gruppo, data) la cui SOMMA annua = pool, deficit sparso -1h/giorno
thickest-first (la forma stessa del golden per G04: {1h: 90 giorni}). Il resto della
famiglia (M20/M20.2/M33 + pricer condivisi di BoxRepair/HoleRepair/SurplusRebalance)
eredita la forma via capSupply. EVIDENZA (A/B appaiato run1+run2, 4/4 bracci hard 0,
KPI piatti, collocato = pool in ogni run): la penalta' unreachable G08 (40h floor
matematicamente non collocabile sopravvissuto al ratio) e' ELIMINATA; il costo medium
~-50k (direzione concorde 2/2, delta fra braccia oltre la variabilita' intra-braccio) e'
del METRO non dell'orario: il medium ora conta deficit di spreading REALI che il ratio
nascondeva dietro bersagli non raggiungibili (verdetto: "il costo e' del metro, non
dell'orario"). La pratica golden avalla la semantica (obbligo come target, giorni corti
voluti). referente: conferma di cortesia pendente (domande-brevi rev 14/08: giorni corti
1/G08), non bloccante (golden-first). Prototipo f28f37de, ratifica con test invertiti.

RATIFICA TASK-500 adaptive move weights forma B (maintainer, 2026-08-16): **il thinning adattivo
per move stream nel decider MT-LS e' ADOTTATO DEFAULT ON** con opt-out esplicito (forma
standard: `ScheduleService.adaptiveWeightsEnabled()` e' `null/absent -> true`, solo
`-Dplanner.ls.adaptive-weights.enabled=false` disattiva). COSA FA: il decideNextStep del
nostro decider MT-LS thina il dispatch per stream PRIMA di mettere l'operazione in coda:
r_i = (W_adaptive/W_base)/max_j rapporto (pesi relativi esatti, ogni r <= 1). Mossa thinned
= zero costo (nessuno slot coda, nessuna valutazione). La forma A (probabilityWeightFactory
via config Timefold) e' STATORICIZZATA: PROBABILISTIC+STEP materializza la mappa mosse per
step -> 4x wall + OOM heap room. La forma B tocca ZERO config Timefold (arm del registro
solo). EVIDENZA (A/B 2+2 + run3 tiebreak): hard 0 6/6, KPI in banda, medium NEUTRO (direzioni
si contraddicono su 3 run, magnitudini 0,4-0,9%), wall B-TIMESLOT TAIL-CAPPING (932/1583/413
-> 529/669/512: taglia i run lenti, non accelera i veloci). REGOLA: mean-preserving
clamp(base * reward/avg_pesata, 0.5x, 2.5x), segment 4000 evals, statistiche dal loop MT-LS
(single-writer). Requisito L2 checkpoint (catena statistiche->pesi) da costruire se serve.

RIMOZIONE TASK-496 esenzione G14 (maintainer, 2026-08-16): **l'esenzione G14 dal fill family
e' RIMOSSA** (default: G14 incluso in M20/M33/M20.2/pool-cap col suo target reale 120h).
La forma e' opt-in rollback: solo `-Dplanner.constraints.g14-fill-exempt.enabled=true`
riabilita il vecchio comportamento (per emergenze). L'esenzione era un cerotto sul dato
sbagliato (990h, TASK-462 corrette a 120h): l'A/B di conferma ha mostrato rimozione pulita
(hard 0, G14 full-day in banda, nessun residuo M20/M33 nuovo). La predicate isG14 resta
(altri consumer), solo il fill family smette di consultarla.

RATIFICA golden-first estesa ai PIANI (maintainer, 2026-08-18, TASK-517): **nessun piano di
pre-assegnazione puo' essere piu' rigido della pratica di piazzamento del golden sulle stesse
coppie**: per ogni (docente, gruppo) che il piano pinnerebbe, la distribuzione di carico per
weekday del piano non puo' essere ovunque piu' densa di quella del golden (asse A); le ancore e
le concentrazioni monografiche seguono la frequenza del golden, non il 100% (assi B/C). Verifica
meccanica OBBLIGATORIA: `scripts/analyze/placement-parity-check.py --plan <export>` prima di
ogni gate di adozione di un meccanismo che pinni; un piano flaggato non si adotta (o si dichiara
la divergenza come domanda alla scuola). CHIUDE la famiglia B della meta-indagine 17/08 (piani
golden-derivati applicati con piu' rigidita' del golden: TASK-432 giovedi' per-gruppo, TASK-430
ancore slot-0 al 100%, chain round 2-6 pin lab a cap H7): tre bocciature che la direttiva
golden-first classica (TASK-379, che controlla le REGOLE) non vedeva perche' controllava i
VINCOLI, non i PIANI. Il check (TASK-517) e' la rilevazione, il budget-reservation (TASK-518)
la prevenzione: stesso principio, due lati. Evidenza di calibrazione: golden contro se stesso
0 flag; piano reale della chain run4 = 23 flag, scesi a 20 col round 8 (flag T15/G04
scomparso). Doc: task467-results/placement-parity-check-2026-08-17.md.

RATIFICA TASK-550 cure congiunte di input (maintainer, 25/08, decisione ufficiale): **tre cure
golden-first ratificate, nessun flag di rollback** (il vecchio comportamento era il bug). (1)
SEEDING STAGE A DENSITA' GOLDEN-OPEN: l'input (295-300h per gruppo) era un errore umano nella
specifica, il golden comanda, si assume il conteggio golden-held, conferma referente a posteriori
(non bloccante); regola permanente: quando un numero di input eccede cio' che il golden pratica,
non si chiede al solver di collocare l'eccesso, si assume il golden, si corregge l'input, si
notifica. (2) GRIGLIA ESTESA AL 15/06/2027 quota-mirata: il golden usa 11-15 giugno (stage G04
+ recuperi), la capacita' extra esiste SOLO li' (vedi Seed Data). (3) DE-TAG G14 CONFEZIONE:
la risposta referente Q19 del 01/08 ("G14 andra' in R10") era rimasta a meta' eseguita
(Servizio stilistico si, Confezione no): riga `G14,Confezione,NONE` in subject_overrides.csv,
chiude 18 H0 + 33 H29 della famiglia. EVIDENZA COMPLESSIVA: -721 -> -152 -> -83 -> 0hard in
un giorno, tutta diagnosi-dai-dati + golden-first, zero metaeuristica. Lezione di processo: le
decisioni prese vanno VERIFICATE COME ESEGUITE (TASK-477b era tracciata e mai eseguita: la
famiglia residua persisteva perche' la decisione era rimasta carta). Doc:
`task550-diagnosi-boxed-vrp.md` + registro iter-24..25.

RATIFICA TASK-550 RoomTailFinisher (maintainer, 2026-08-25 17:15): **il finisher della coda stanze
e' ADOTTATO DEFAULT ON** con opt-out esplicito (forma standard:
`planner.construction.room-tail-finisher.enabled`, null/absent -> ON, solo il letterale "false"
disarma; banner "Room-tail finisher (TASK-550): enabled=%b" in `refreshSolverStatics`). COSA FA:
custom phase deterministica post-B-ROOM (dopo PureHoleRepair, pre-save), sorella di PHR (stessa
Grid), che chiude l'H29 residuo delle tag scarse. Famiglie di mosse: free-cell; evict non-lab
(room-only, inerte sul seed 2026/27); pull-last same-day (solo in buchi INTERI esistenti, MAI
tail-extend: sposta il buco, shell-game TASK-481); SWAP con lezione ordinaria same-group (il
workhorse: entrambe le celle restano occupate, H17-sicuro per costruzione, conteggi per giorno
invarianti H32/H33). Oracle improve-or-noop su HARD PIENO (`totalHardScore`, non timeslot-tier:
il pass muove stanze, H2/H29 in gioco). EVIDENZA: banco del -83 (83 tag-required room-null ->
0 in vitro, tutti swap; il fenomeno misurato: 94 celle con 2 lab moda co-schedulati, unica
R16, golden 0 co-schedulazioni tutto l'anno) + ciclo completo (banco DIVERSO, 76/76 ->
0, consegna 0hard end-to-end, KPI in banda vs -83, costo medium ~40k attribuito dal control).
DoD completa; sanity NON CONFORME residua su 2 gruppi = famiglia giorni-corti H33/pool-cap
pre-esistente (domanda referente pendente), NON del finisher. Doc:
task542-results/task550-roomtail/VERDICT-2026-08-25.md + ciclo-finisher-on/.

RATIFICA TASK-560 EmptyDayFinisher v0.1 (maintainer, 2026-08-27, handoff mandato mattina 27/08):
**il finisher dei giorni dichiarati vuoti (coda H34) e' ADOTTATO DEFAULT ON** con opt-out
esplicito (forma standard: `planner.construction.empty-day-finisher.enabled`, null/absent ->
ON, solo il letterale "false" disarma; banner "EmptyDayFinisher (TASK-560, RATIFIED):
enabled=%b" in `refreshSolverStatics`). COSA FA: custom pass deterministico al confine
post-secondo QuotaFillRepair (pre-B-ROOM... vedi wiring `runEmptyDayFinisher`), che chiude
le celle H34 (giorni dichiarati COMPLETAMENTE vuoti) quando QFR e' strozzato dai propri
gate (donatori same-group con tail bloccate): estrae ore MID-blocco dal donatore e slide
del resto (H31-safe: la prima ora del donatore non si muove mai; H17-safe: contiguita'
preservata; gate room-safety e H1 live-index). EVIDENZA (pack
task560-results/gate-adozione-edf-v01-2026-08-27.md + addendum): 4/12 celle residue chiuse
sul vincitore del ciclo h34ms (49 mosse, 0 conflitti, 0 revert improve-or-noop), curva
cross-bacino 4/0/3/2/1/0 su sei checkpoint (bacino-sensibile anche fra buoni: media ~1,7
celle, picchi 4, costo ~4s; raccomandazione: ADOPTARE, downside = no-op). Flaw
slide-pre-estrazione fixato in TDD (same-teacher block self-blocked). Residuo post-EDF sul
vincitore: 8 celle (filone import teacher-locked mag-giu + G13, cura search-side
multi-start, non tolleranza).

RATIFICA TASK-558 emendamento M22 quota-aware (maintainer, 2026-08-27, handoff mandato mattina
27/08): **il minDays di M22 passa alla forma quota-aware, ADOTTATO DEFAULT ON** con opt-out
esplicito (forma standard: `planner.constraints.m22.quota-aware.enabled`, null/absent -> ON,
solo il letterale "false" ripristina il minDays legacy ceil(ore/maxHoursPerDay); banner
"M22 quota-aware min-days (TASK-558, RATIFIED): enabled=%b" in `refreshSolverStatics`).
minDays emendato = ceil(ore / media-quota-dichiarata aula+lab del gruppo): quando le ore
collocate coincidono col pool del foglio, degenera esattamente nel numero di giorni che la
scuola ha dichiarato (fallback legacy per gruppi senza righe quota). EVIDENZA (pack
task558-results/gate-emendamento-m22-2026-08-27.md): il min-days legacy era una FINZIONE di
packing su questo dataset (golden unpinned-equivalente 463 giorni-eccesso col metro del
vincolo, 262 con la join ad hoc); col metro emendato golden 463 -> 3 e run 410 -> 0. Cambia
il METRO (quando un orario legge "sparso troppo sottile"), NON l'orario. Lettura del flag
PER MATCH nel flusso, NON captured: la factory app si cache a boot e il verifier riusa
stream fra sessioni (incidente m22MinDays, 26/08); il costo per-call (~0,1% wall) e'
accettato permanentemente.

RATIFICA TASK-556 QuotaFillRepair v2 (maintainer, 2026-08-27, handoff mandato mattina 27/08):
**la scelta-mossa v2 del QuotaFillRepair e' ADOTTATA DEFAULT ON** con opt-out esplicito
(forma standard: `planner.construction.quota-fill.v2.enabled`, null/absent -> v2, solo il
letterale "false" mantiene il path v1 legacy; banner "QuotaFillRepair v2 move choice
(TASK-556, RATIFIED): enabled=%b" in `refreshSolverStatics`). COSA CAMBIA: il donatore
cede dalla CODA (mai slot 0: l'inizio del giorno donatore non si muove, H31-safe) e
l'atterraggio del bersaglio e' bordo-consapevole (sale dal primo slot aperto su giorno
vuoto; gap interni poi last+1 su giorno parziale: fill contiguo per costruzione,
H17/H31-safe), con re-sweep mentre il pass progredisce. EVIDENZA: formalita' same-seed sul
ciclo completo (57 mosse / 14 celle / 0 revert) + controllo discriminante (sanity identica
con v2 OFF: nessun costo nascosto). Verdetto:
task556-results/ciclo-gate-e-e2e/VERDICT-2026-08-26.md.

CURA INPUT G09 golden-first (maintainer, 2026-08-27, handoff mandato mattina 27/08): il
foglio `calendario_class_days.csv` dichiarava 670h aula+lab in-grid per G09 contro un
pool incarichi di 666h (= ESATTAMENTE le ore che il golden colloca): 4h fantasma, le 3
celle H34 del gruppo erano matematicamente inriempibili (famiglia TASK-550: quando l'input
eccede cio' che il golden pratica, si assume il golden e si corregge l'input, conferma
referente a posteriori non bloccante). CURA: 4h tagliate dalle righe di coda d'anno che il
golden stesso lascia VUOTE (04/06 da 5 a 4; riga 07/06 quota 3 eliminata), SOLO nei seed
CSV (copie `src/main/resources/seed/` e `claudedocs/spalmone-2026-27/data/` editate in
modo binario identico: il test del contratto byte-identical resta verde; CRLF preservati).
`DataGenerator.java` NON toccato (nota: il suo javadoc "1,739 entities" ora e' stantio di
una riga, 1,738; file protetto, correzione doc differita). PIN RIPINNATI con la cura come
motivo: `DataGeneratorExpectedDailyHoursSeedTest` 1739->1738 righe e G09 670->666;
`GoldenSolutionRegressionTest` H34 -2hard -> -1hard e M20.2 -21500/3 match -> -14000/2
match (quota-4 7000 + G14 7000). La domanda a referente resta nel pacchetto 3-domande
(quale delle 4h e' fantasma secondo la scuola), non bloccante. Verifica:
`derive-golden-incarichi-corrections.py` 0 divergenze + suite 1184/0 EXIT 0.

RATIFICA TASK-564 v2.1 pre-booking dei giorni dichiarati (maintainer, 29/08, ordine via
Fedora): **il pre-booking e' ADOTTATO DEFAULT ON** con opt-out esplicito (forma
standard: `planner.construction.prebooking.enabled`, null/absent -> ON, solo il
letterale "false" disarma; banner "Declared-day pre-booking v2.1 (TASK-564,
RATIFIED DEFAULT ON, REVISIT-FLAG...)" in `refreshSolverStatics`). COSA FA:
`DeclaredDayPreBooker` riserva le celle dei giorni dichiarati vuoti in coda d'anno
(finestra 56 giorni, newest-first) DENTRO `DayClusteredConstructionPhase` prima del
box pass, con le tre rifiniture v2.1 gia' in forma opt-out (parity cap TASK-517,
riserva quota-aware, block-guard H17); improve-or-noop sul timeslot-tier hard come
ogni sibling deterministico. EVIDENZA (banca TASK-564): banco differenziale 7 braccia
(riservate 3568 -> 600, gap H17 91 -> 43, parity-check 18 -> 0 flag) + ciclo completo
sul seme deterministico (tier -81 -> **-12hard**, H17 74 -> **0**, M32 707k -> 288k
baseline, full-day 91,6 vs golden 91,9), certificato in banda conferma dai criteri
emendati TASK-565 (verdict-check.py). REVISIT-FLAG (parte dell'ordine di maintainer):
l'A/B cross-basin TASK-565 (semi GRASP 25/26, predizione scritta PRIMA) e'
NON-confermante per costruzione dei bacini: entrambi i bracci in banda
bacino-non-conferma (-69/-134hard, firma doomed GRASP pre-esistente M32/M20.2),
inizio tardivo 0 TOTALE in entrambi (l'asse H31 del meccanismo non regredisce mai);
da riconsiderare se emerge evidenza cross-basin (semi deliverable notturni: 10, 22,
23, 24). Post-flip: costruzione col nuovo default ricorrenza 73,2% / full-day scoped
88,6%, pin del test real-seed stabili. Docs A/B:
`claudedocs/spalmone-2026-27/task564-results/ab-grasp-s25-29/` e `ab-grasp-s26-29/`.

RATIFICA TASK-577 pool-cap-fill (maintainer, 9/09): **il fill-goal della costruzione
segue i target per-data pool-cap TASK-497, ADOTTATO DEFAULT ON** con opt-out
esplicito (forma standard: `planner.construction.pool-cap-fill.enabled`,
null/absent -> ON, solo il letterale "false" disarma; banner "Pool-cap fill
(TASK-577, RATIFIED 9/09): enabled=%b" in `refreshSolverStatics`). COSA FA:
`expectedHoursCapFor` (la fonte unica dei cap consultata da pre-booking, stamp,
fill tail e box) trimma il cap per-data al target pool-cap quando piu' basso
della quota del foglio (forma `Math.min`, stesso idioma di `capSupply` lato
score). Per i gruppi over-capacity (sul seed attuale SOLO 1/G08: G09 e'
uscito dalla mappa con la cura input del 27/08) la costruzione smette di
seguire il foglio (G04 840 in-grid) e segue il pool (750): niente piu'
front-load che svuota la coda d'anno. EVIDENZA (banco PoolCapFillBenchTest,
2 run identici; il banco gira SENZA i pin del pre-assign, per cui le sue
celle vuote assolute non sono paragonabili al censimento vivo): celle vuote
55->34 con G04 20->9 e G08 9->0, boxed 140->136, H33 29->26, H34 22->21
(secondo ordine), totali piazzati per gruppo invariati. CONTESTO: la famiglia
golden-monthly costruita la stessa notte sulla tesi dei "mesi-pausa" e' stata
BOCCIATA e RIMOSSA (commit 1e994d83): il fenomeno era un artefatto del filtro
pseudo ('t' vs 'true', lessons.md famiglia 3 episodio 10) e il censimento
corretto dava 40 celle dichiarate vuote a costruzione (15 x G04, 10 x G08,
6 G14 input-locked TASK-592, 9 tail teacher-locked). Doc:
`task577-results/VERDICT-poolcapfill-retrazione-goldenmonthly-2026-09-09.md`.

RATIFICA TASK-599 teacher-weekday-spread (maintainer, 12/09): **la preferenza di
spread settimanale per docente nell'assegnazione celle del template e' ADOTTATA
DEFAULT ON** con opt-out esplicito (forma standard:
`planner.construction.teacher-weekday-spread.enabled`, null/absent -> ON, solo il
letterale "false" disarma; accessor `WeeklyTemplateBuilder.teacherWeekdaySpreadEnabled`
letto LIVE per build; banner "Teacher-weekday-spread (TASK-599, RATIFIED 12/09):
enabled=%b" in `refreshSolverStatics`, il gap di osservabilita' segnalato dal REPORT).
COSA FA: nel cell-assignment del WeeklyTemplateBuilder (TASK-542 iter-11, pass-0), un
corso ricorrente PREFERISCE il weekday dove il suo docente ha meno ore template gia'
piazzate (anti-clustering; con fallback: mai forzare). La semantica E' la regola del
pianificatore umano detta nell'audio del 11/09 ("slot regolare per docente...
materia-insegnante e' la discriminante") e la stessa logica del golden (T10/G04
lunedi', G13 venerdi'). EVIDENZA (gate spread-11sep 12/09, 8h34m, 5/5 tentativi,
vincitore attempt-1): consegna **[-11/0]** = migliore della serie (slotretry -13,
control-10sep -17, control-11sep -26; +15 hard end-to-end): H0=0 (le 3 ore G14
Confezione CHIUSE: verifica indipendente 115/115 collocate, il triangolo T16
venerdi' sciolto dalla migrazione Confezione-G14 al giovedi'), H33/H29/H31 azzerate,
H34 13->10, H7 3->1, full-day 91,3% in banda, collaterale H20 dell'iter-11 ASSENTE.
RUGA DICHIARATA: ricorrenza unpinned 70,3% vs banda 76+-2 (-4pp: da pesare sui
checkpoint se emerge evidenza; il lever resta la direzione della regola umana).
Storia: misurato vincente gia' nell'era TASK-542 iter-11 (-24 -> -13, allora mai
ratificato), riesumato dal banco TASK-599. Docs:
task599-results/spread-11sep/REPORT.md.

RATIFICA TASK-609 CompositeSwapFinisher v0.1 (maintainer, 14/09): **il pass RELAY-k di
riparazione composita delle lezioni boxed (H0) e' ADOTTATO DEFAULT ON** con opt-out
esplicito (forma standard: `SchoolScheduleConstraintProvider`-style, il flag
`planner.construction.composite-swap-finisher.enabled` in `CompositeSwapFinisher`
e' null/absent -> ON, solo il letterale "false" disarma; banner
"CompositeSwapFinisher (TASK-609 v0.1, RATIFIED 14/09): enabled=%b" in
`refreshSolverStatics`). POSIZIONE (decisione (a) di maintainer dalla gate-night, commit
93c1aa97): il pass gira NEL WRAPPER multi-start al punto pre-save (post corner-b
refinement / pre-save TASK-526), NON nel tail chain del ciclo: la gate1 (bf3b2fda)
aveva mostrato che al tail chain il boxed nasce a valle (la rifinitura di consegna
la crea) e il pass vedeva targets=0 SEMPRE (errore di FASE); alla nuova posizione
gira sullo STATO CONSEGNATO, una volta per consegna (non per tentativo), che e'
esattamente lo stato che il banco replay ha curato (evidenza 1:1). COSA FA: per
ogni lezione boxed (timeslot null, docente reale, non pinnata) cerca una catena di
sgombero RELAY-k (k=2..3, il census delle 40 mosse della soluzione cieca TASK-608):
sgombera il tenant X della cella bersaglio verso un landing legale (VRP per-corso,
stanza di famiglia libera, docente libero e sotto cap, forma H17/H31), e se il
landing e' occupato da W, sposta W un salto prima (depth max 3); oracle
improve-or-noop sull'HARD PIENO come RoomTail (il pass muove stanze: H2/H29 in
gioco). EVIDENZA: banco replay su DUE bacini (m39-off [-11]->[-8], m39-on
[-13]->[-10], entrambi relay3 con deficit H32 chiuso in cascata) + no-op puro su
stato senza residuo (step9) + due gate a ciclo reale (gate1: wiring validato
targets=0 per fase; gate2 6c8c3e43: alla posizione nuova il fenomeno e' assente
in quel bacino, counter=0, zero controcandidi su 2 gate). Sotto il default ON e'
un'assicurazione a costo meccanicamente zero contro un evento 1/4 nelle consegne
fresh-profile misurate. ROOT: la classe COMPOSITE_MISSING (nessuna mossa singola
migliorativa esiste, e nessuna LS gira sullo stato consegnato) dimostrata da
TASK-608 con esperimento cieco-LLM + arbitrato Java (tesi di maintainer). Docs:
`claudedocs/spalmone-2026-27/task609-results/ciclo-gate{,2}/REPORT.md` + banco
`CompositeRepairReplay609Test` (13 step dal TASK-612, `PLANNER_TEST_REPLAY_609=1`).

RATIFICA TASK-612 EDF relay-fill + H32 targets (maintainer, 14/09 sera): **la famiglia
IMPORT-RELAY e i target H32 parziali sono ADOTTATI DEFAULT ON** con opt-out
esplicito (forma standard: i flag `planner.construction.edf-relay.enabled` e
`planner.construction.edf-h32.enabled` in EmptyDayFinisher sono null/absent -> ON,
solo il letterale "false" disarma; banner "EmptyDayFinisher relay family (TASK-612,
RATIFIED 14/09): relay=%b h32=%b"). COSA FA: (1) RELAY: quando l'estrazione diretta
GATE-E fallisce, il donore e' ANY giorno stesso-gruppo (coda-first) e la cella
svuotata viene RIEMPITA ESATTAMENTE da un giorno in vero surplus (GATE-E sul ledger
vivo) o tramite hop intermedio ricorsivo (max 2 riporti, visited-set anti-ciclo, il
terminale DEVE pagare GATE-E; il terminale prende SOLO l'ultimo slot occupato del
giorno: partenze interne aprivano H17, prima-ora H31, appreso dal banco); (2) H32:
i giorni parziali sotto quota (tolleranza G09 1h, accessor condiviso
quotaFillTolerance) diventano target; (3) quando armato, EDF gira ANCHE sullo stato
consegnato al punto pre-save del wrapper (post-CSF), improve-or-noop full-hard, log
[delivery]. La mossa meccanizza il census TASK-608 (RELAY-2 x11, workhorse) e
ripara la classe diagnosticata da AC#2 TASK-609 (GATE-E estrattivo-puro respinge
l'88% dei candidati). EVIDENZA: banco 13/13 post-hoist (m39-off -11 -> -8 CSF ->
-3 PINNED 5 celle; m39-on -13 -> -10 -> -6; no-op su pulito) + gate A/B
NON-appaiabile (mattina: controllo MORTO per crash TASK-614) ripetuto su codice
curato (ab-gate2, sera): **A2 -18 vs B2 -8** (H34 5 celle -> 1, H32 2 -> 0, H7 2 ->
0, H17 9 -> 7), riga [delivery] ATTIVA (3 mosse 1 cella 0 conflitti), criteri a-d
TUTTI VERDI, controllo crash VERDE (A2 attraversa il confine che ammazzava il
thread ed esce vivo: la cura TASK-614 regge). Storia ombra dichiarata: il primo A/B
aveva braccio B no-op (0 mosse su 3 celle: oracolo revertiva relay net-neutral) e
la D3 spiega la geometria bacino-dipendente del valore. Docs:
`claudedocs/spalmone-2026-27/task612-results/ab-gate{,2}/` + banco steps 11-13.

RATIFICA M-BF lab-teacher day contiguity (maintainer, 18/09, "Okay" sulla raccomandazione): **il vincolo medium dei buchi interni per-docente sui giorni lab multi-docente e' ADOTTATO DEFAULT ON a peso 20** con opt-out standard (`planner.constraints.mbf.enabled`, solo "false" disarma; accessor `SchoolSchedule.isMbfEnabled`, banner "M-BF (RATIFIED 18/09)"). COSA FA: vedi la riga M-BF nella tabella Medium (perimetro, peso, divisione M31). EVIDENZA (pacchetto completo su wt/mbf-proto, merge c581b3d5): banco appaiato -60 giorni interleaved netti (la LS nuda CREA frammentazione 54->71, armed 54->11), A/B ciclo completo 4 criteri/5 PASS con ricorrenza INTATTA col template vivo (69,7 vs 69,8: il timore -6,3pp dell'harness era un difetto harness), hard mai peggiorato, sanity senza rosse nuove, gates 5 agent con cure (meter unico expand+TreeSet: fix del reward negativo su stati H1-violanti), re-cert identica al numero, golden 0 match. SERIE FULL-CHAIN: -4, -1, 0, 0 (il braccio B e' n=4). RESIDUI DICHIARATI: G01 42->38 a peso 20 (sweep peso = leva disponibile), interazione M31 con calibrazione congiunta documentata, ristruttura toList->collectors incrementali differita post-ratifica. Docs: `integrato-1/{mbf-proto,mbf-ab}/VERDICT*`, REVISIT-dubbi-17-09.md punti 1-2.

RATIFICA TASK-613 HoleRelayFinisher v0.6 teacher-aware (maintainer, 15/09): **la famiglia
ROTATION-WITH-PERMISSION dei buchi H17 teacher-locked e' ADOTTATA DEFAULT ON** con
opt-out esplicito (forma standard: il flag `planner.construction.hole-relay.enabled`
e' null/absent -> ON, solo il letterale "false" disarma; banner "HoleRelayFinisher
(TASK-613 v0.6, RATIFIED 15/09): enabled=%b"). STORIA (metodo maintainer cieco->reverse->
meccanizza, completo): round-1 cieco 40 mosse (-11->0 arbitrato); round-2 cieco sullo
stato -3 della catena CSF+EDF: 21 mosse (-3->0 arbitrato Java) e SMENTITA della
prima meccanizzazione (v0.1 import-relay: morta su H1/VRP; la famiglia vera e'
intra-giorno, ZERO import cross-day); diff-diagnostico -> causa esatta (l'inserzione-
rotazione non era teacher-aware: trascinava lezioni nell'ora critica esentandole
"perche' nel batch"); cura prototippata in banco (-3->0) e portata in produzione
v0.6 (byte-fedele); round-3 CONVERGENZA: il verificatore sullo stato finale prodotto
dalla catena meccanizzata dice TUTTO PULITO con zero mosse. COSA FA: per ogni buco
H17 interno, la lezione di coda scivola nel buco; se il suo docente e' conteso
all'ora del buco, la DFS simbolica teacher-aware costruisce catene di
rotazioni-inserzione (span/count preservati) sui giorni dei contendenti, con gate
SIMBOLICI in ricerca (SYM-HOLE: nessun mover col docente della coda atterra sul buco;
SYM-CODEST: vietate co-destinazioni same-teacher; ricorsione sul VERO bloccante) e
verifica composita finale (scan conflitti sulle date toccate); oracle improve-or-noop
full-hard del wrapper. EVIDENZA A/B (ab-gate, profili freschi t613a/t613b): A3 -14
vs B3 -9, criteri a-e TUTTI VERDI, crash-control VERDE (zero IllegalStateException),
riga [delivery] presente (targets=6 moves=0: no-op sicuro sul live, pre-registrato
non-bloccante; l'efficacia e' del banco: -3->0 m39-off e -6->-4 m39-on certificati
2x post-cleanup). ATTRIBUZIONE ONESTA: il delta live e' variabilita' di bacino, non
del pass. LA CATENA COMPLETA SUL REPLAY E' ORA INTERAMENTE RATIFICATA: -11 -> -8
(CSF TASK-609) -> -3 (EDF relay TASK-612) -> 0 (HoleRelay TASK-613). Docs:
`task613-results/ab-gate/` + banco step14 + PROTOTIPO-CURA-teacher-aware-2026-09-15.md.

RATIFICA TASK-636 cadence-pace (maintainer, 21/09 sera, "ratifico"): **la preferenza di cadenza
golden-aligned nelle code di costruzione e' ADOTTATA DEFAULT ON** con opt-out standard
(`planner.construction.cadence-pace.enabled`, null/assente -> ON, solo il letterale "false"
disarma; accessor `DayClusteredConstructionPhase.cadencePaceEnabled`; banner servizio
"Cadence-pace construction tail (TASK-636): enabled=%b, curves=%d" + census di fase
"Cadence-pace tail: armed=%b paceBoostApplications=%d"). COSA FA: un corso INDIETRO rispetto
alla curva cumulativa del cadence reference (stessa fonte M34/M35, `golden-cadence-reference.json`,
via di sopravvivenza workbook 21_CadenzaAttesa, NIENTE golden-runtime nuovo) tiene la priorita'
massima nel round-robin repair tail (effetto daysSince=9999) e vince lo slot nella quota-aware
fill tail (rango 0 con early-break); in pari o senza curva, comportamento invariato. La forma
e' preferenza-mai-forza (nessun gate saltato), self-gating per sentinella (coppia assente =
inerte), e la curva non spinge oltre i cap M34. STORIA (metodo zero-hard completo, catena a 5
livelli con autocorrezioni nel task): -1 H34 integrato-6 -> arbitrato cieco v2 NO SOLUTION
(cost-model reale nello spec del puzzle: la lezione blind-puzzle e' ora memoria) -> causa
corso template-less (servizio tecnico G06) -> fabbricante = fill tail (NON round-robin:
morto nel fixture, livello-5) -> cura data-driven. EVIDENZA: banco costruzione 3-arm (OFF
byte-identico, ON +1,4pp ricorrenza 74,0 vs 72,6, Servizio tecnico G06 da 16h-in-coda a
38h feb-giu, guardia anti-iter-22 verde: giugno identico) + A/B adozione due profili freschi
5/5 criteri PASS (ON [0/0]hard AL TENTATIVO 1 vs OFF -2 al tentativo 2, ricorrenza 70,5 vs
69,2, sanity ON migliore: rossi G09 scomparsi, medium pari, banner curves=245 paceBoost
2188-2319) + suite 1363/0 + post-flip inerzia verificata (default ON armed=true 1974 boost;
-D=false BYTE-IDENTICAL al pre-ratifica). Gates: /simplify 4 reviewer (12 finding applicati:
parser condiviso parseCadenceReference, floorEntry NavigableMap, predicato unico
behindGoldenPace, resolveLessons condivisa) + code-review high 5 reviewer (nessun bug; boost
round-robin INVERTITO trovato e curato pre-banco, W53 fail-loud, census on-flip). NOTA A/B
onesta: n=1 per braccio, differenza hard dentro la varianza race (628). Il -2 del braccio OFF
e' classe COMPOSITE_MISSING (3373 swap legali full-family che nessun braccio della terna
esprime): TASK-638 ViolationScopedSteepestFinisher, con h7swap armato e LS-corto gia' in
banco. Docs: task636-results/ab-adoption/VERDICT.md + nearmiss/ARBITRAGGIO.md (cieco v2).

RATIFICA TASK-641 room-aware grammar (delegata 23/09, evidenza completa): **l'estensione
room-aware del ViolationScopedSteepestFinisher e' ADOTTATA DEFAULT ON** con opt-out standard
(`planner.construction.violation-steepest.room-aware.enabled`, null/assente -> ON, solo "false"
disarma; banner "roomAware=%b"). COSA FA: secondo passaggio PIGRO nella stessa iterazione
steepest, attivato SOLO quando nessun candidato semplice migliora (il percorso base resta
byte-identico quando una cura semplice esiste): varianti compositte = mossa timeslot
(swap/change) + riassegno stanza via Moves.compose sul metamodello room, enumerazione stanze
libere allo slot di destinazione, dedup della coppia NON ordinata, oracolo full-constraint
improve-or-noop. EVIDENZA: banco 3/3 doppio indipendente (Fedora+laptop: 637-off -3->0 con la
SWAP+ROOM che chiude l'H29 che il catalog sweep lasciava aperto; integrato-1 -52->-41 in 7
mosse INCLUSA la cura del blind puzzle 'SWAP+ROOM R07 Lavorazioni<->TecnicheSaldatura';
636-off IDENTICO al pre: 23337 eval, pass stanza mai invocato); A/B 5/5 su cicli freschi con
parita' 0-hard dichiarata no-op legittimo (la race consegnava pulita: assicurazione a costo
zero sotto la direttiva bounded-no-op, precedente CSF); suite 1370/0 x2 indipendenti;
code-review high 8-angle+verifier con F1 critica curata (il filtro direzione-canonica scartava
meta' dello spazio single-direction) e keep-null arm per stati H29. STORIA: la classe mancante
nata dal blind puzzle H28 (diagnosi notturna 857313d7: la cura era composita sul tier stanze).
Docs: task641-results/ + task638-results/blind-and-moves/.

RATIFICA TASK-657 unblock cross-group (maintainer, 23/09 sera, bounded-no-op insurance):
**il terzo passaggio pigro del ViolationScopedSteepestFinisher (catene di sblocco
cross-group) e' ADOTTATO DEFAULT ON** con opt-out standard
(`planner.construction.violation-steepest.unblock.enabled`, null/assente -> ON, solo
"false" disarma; banner "unblock=%b"). COSA FA: quando plain e room-aware hanno
fallito, per ogni giorno ferito fra i target prova il tail slide nel buco interno
COMPOSTO con una swap cella-per-cella cross-date dentro il gruppo del BLOCKER (il
docente che occupa lo slot del buco in un altro gruppo), con riassegno deterministico
delle stanze portate che collidono; oracolo improve-or-noop full-hard sul composito.
LA CLASSE nasce dal cieco v3 del blind puzzle integrato-7 (catena Gestione<->LabTe +
Informatica->@11): quel bacino e' STRUTTURALMENTE bloccato dalle Vacation docenti
(T22 ven full-day, prova meccanica forced657), quindi NESSUN positivo dimostrato:
l'adozione e' assicurazione a costo zero sotto la direttiva bounded-no-op (precedente
CSF TASK-609), il braccio spara solo su un bacino GRASP con partner legalmente
disponibili. BANCO: 576 candidati generati sul bacino bloccato, tutti correttamente
rifiutati dall'oracolo (no-op pieno = la garanzia). FILO POSITIVO della stessa tornata:
fix del resolver H17 (justification gruppo+data -> target del giorno ferito: le ferite
H17 erano INVISIBILI al 638, walk morto a zero target - classe F1 PASSO-7) e la
modalita' banco forced657 come probe standard per le catene dei ciechi. Docs:
integrato-7/nearmiss/REPORT.md (tre iterazioni, tre Vacation).

RICALIBRATA SOGLIA STAGED HARD-ACCEPT -2 -> -40 (maintainer, 04/10, "ricalibra la soglia"):
la soglia condivisa dalle due gate staged (accetto B-TIMESLOT senza demozioni /
verdetto DOOMED al boundary post-RELEASE, forma TASK-498 "la STESSA costante, nessuna
soglia nuova") e' ora il knob `planner.staged.hard-accept-threshold`, DEFAULT -40,
letto LIVE (`ScheduleService.stagedHardAcceptThreshold()`), banner "Staged hard-accept
threshold (TASK-635): <valore> (default -40)" in `refreshSolverStatics`. MOTIVO: la -2 fu
calibrata a luglio/agosto quando la catena di consegna non esisteva; il banco di
falsificazione `DoomTrioFalsifyBenchTest` (4c3513c4) ha misurato che condannava stati
che la catena cura: boundary -29 consegnato -2 (93% curato, fidelity gate al numero), e
ENTRAMBE le braccia della notte 03/10 bollite DOOMED a boundary -10/-13 hanno
consegnato 0 e -2 (la corner-b le ha rifinite comunque: esito salvo, classificazione
falsa). -40 copre la banda bad misurata al boundary (composito: TASK-498 15/08
-6..-34 + batch 14/08 fino a -38): la cura diretta e' misurata sui tre punti
-10/-13/-29, la copertura fino a -38 e' la scommessa ratificata e l'estremo H0-heavy e'
la classe che la catena cura meno (la costruzione scartata dal predictor a tsh -2890:
curata solo al 15%). Sotto -40 resta territorio non misurato e la condanna resta.
NOTA ONESTA (review 6-angle, verificata sul codice): il morso COMPORTAMENTALE della
ricalibratura e' SOLO il verdetto DOOMED (stanze CH-only vs rifinitura inline + corner-b
che sparisce); sulla gate B-TIMESLOT la soglia e' quasi-inerte nel suo regime perche' i
semi di demozione sono ESATTAMENTE le lezioni boxed (`StagedPinPolicy.selectDemotions`),
quindi con box==0 anche la vecchia -2 demoteva nulla e usciva senza re-solve (le
demozioni notturne erano BOX-driven, box 3-5). COSTO DICHIARATO: nei run col budget
(`planner.multistart.budget-seconds>0`, opt-in default 0) un tentativo in-banda
non-ultimo spende ~10min di rifinitura dal budget che la -2 spendeva in un bacino GRASP
fresco. Il predictor
(150 buchi) NON e' toccato: la falsificazione lo ha prosciolto per ESITO (lo scarto e'
giusto) e condannato per METRICA (gli H17 che conta sono curabili da HRF; la coda H0
boxed e' il blocco vero): la sua cura e' lavoro separato.

DISARMATO PROFILE PREDICTOR (maintainer, 04/10, "fallo" sulla cura della meta' predictor
del 635): **il predictor early-doom (TASK-542 iter-16, scarto costruzioni con >150
buchi H17) e' DEFAULT OFF** (forma opt-in: solo `-Dplanner.multistart.profile-predictor.
enabled=true` lo riarma per run di ricerca; knob max-holes tenuto al 150 storico perche'
la metrica e' RITIRATA non ricalibrata, TASK-624 l'aveva dichiarata non-monotona per
seme). EVIDENZA (falsificazione appaiata same-seed, veicolo resume da POST_CONSTRUCTION
validato dal controllo): la costruzione SCARTATA (209 buchi, tsh costruzione -2890,
boxed 1263) percorre il percorso completo che il predictor gatinga e CONSEGNA
**[-3/0]hard in 72min**, MEGLIO del controllo deterministico ([-9/0] in 89min), con
B-TIMESLOT #1 chiuso a tsh -13 box 0 (la LS piazza tutta la coda ~1250 in un passaggio;
il controllo resta box 4 per tre iterazioni). La soglia 635 -40 paga in diretta: box=0 e
-13 >= -40 = accettato senza demozioni (la vecchia -2 ne avrebbe bruciate due). CONTESTO
che rendeva il disarmo dovuto: censimento 17/09 (TASK-624) = minimo 240 / mediana 296
buchi sui semi GRASP => la 150 scartava TUTTI i retry di fatto, de-generando il
multi-start a N=1 sul solo tentativo deterministico e amputando la diversita' di bacino
che TASK-483 aveva ratificato come cura dei bad-seed; il banco 635 aveva gia' mostrato
che la catena cura gli H17 dello scartato (-209 -> -2). ECONOMIA: un retry costa ora
~75-90min di ciclo pieno (bounded da maxAttempts=5, stop-al-primo-0-hard e pick-best:
un retry fallito costa solo wall, mai l'esito); il caso pessimo (nessun 0-hard ovunque,
budget-seconds di default 0 = nessun limite a orologio) e' ~5 cicli = 6-7.5h, e i driver
di campagna col POLL_CAP 150min vanno alzati o armare budget-seconds sui run lunghi.
RESIDUAL DICHIARATO: la falsificazione ha testato il seme a 205 buchi; la banda del
censimento 17/09 (240-445) e' INAUDITA nell'era corrente e il disarmo assume che la
cura della catena generalize sulla banda (da verificare col census TASK-677 che logga
holes/unassigned/hard a OGNI costruzione: stop-deciders, keep-measuring). Riga di
registry: pred677-ctrl/-a2 in attempt-traces.csv. NOTA: le letture -13/-3 sono anche il
primo dato dell'era corrente su consegna di semi GRASP via percorso completo (n=1 per
braccio, dichiarato). Docs: TASK-677 (backlog) + registro nel task.

RATIFICA TASK-678 braccio H33 under-floor (maintainer, 04/10 notte, "accetta e deploya"):
**il braccio H33 del TASK-577 (`planner.construction.edf-h33.enabled`) e' DEFAULT ON**
con opt-out standard (null/absent -> ON, solo "false" disarma), reso VIVO sugli stati
consegnati dai quattro emendamenti 678 (GATE-E floor-form per quota-esclusi,
atterraggio room-aware con preferenza capacity>=size e fallback, gate sul value range
del director, bordi candidati entrambi + trail undo con stanza e rollback corretto).
Il banco UnderfloorBench678Test pinna: la cella reale G14 Friday strutturale non
viene toccata (finestra 0-2 + slot 0 teacher-locked), il sintetico ha le invarianti
(mai peggio, mai fuori range, zero conflitti) con la predizione di chiusura
pre-registrata come acceptance di TASK-679 (la deriva fresh/range blocca la chiusura
oggi). GATE SALTATO E DICHIARATO (ordine esplicito di maintainer): l'A/B H33 pre-flip che
review e task chiedevano resta saltato: il trade M20 (~1000 medium/ora di deficit sui donatori a
quota dei gruppi esclusi, belt-invisibile) resta NON MISURATO e va letto alla prima
campagna successiva. Docs: task677-results/nearmiss/REPORT.md + TASK-678/679.

RATIFICA TASK-637 theory-spread (maintainer, delegata 22/09 "ratifica quello che devi", A/B 5/5):
**la doppia leva M-TR (chunk 1h teorici nel builder + hold same-day nella fill tail) e' ADOTTATA
DEFAULT ON** con opt-out standard (`planner.construction.theory-spread.enabled`, null/absent -> ON,
solo "false" disarma; accessor `WeeklyTemplateBuilder.isTheorySpreadEnabled`; banner "Theory-spread
tail (TASK-637)" con census theoryHoldFills). COSA FA: la LEVA 1 demota i corsi teorici (predicato
SubjectNormalization.isTheoryForRule, single-source M-TR) a chunk 1h nel builder (i lab tengono i 2h:
M23/S17, pratica golden); la LEVA 2 hold back nella fill tail un secondo hora same-day della stessa
materia teorica (rango last-resort, grouping key theoryRuleGroupingKey single-source). EVIDENZA: banco
costruzione 3-arm (OFF byte-identico, ON -30% celle M-TR 2696->1893) + A/B adozione su rc-1 (due
profili freschi appaiati attempt-2 seed 32): 5/5 criteri VERDI: ON [-1/0] vs OFF [-3/0] hard, M-TR
celle 2+ same-day 1606->1402 (-12,7%, residuo 1662->1455), ricorrenza 70,0 vs 70,3 (ruga famiglia
NON morde), full-day 94,6 vs 94,7, banner armed=true theoryHoldFills=139. Sanity: ON 1 rosso (G08)
vs OFF 2 (G09 + G10). STORIA: TASK-470 prototipo weekday-preference MISURABILMENTE INERTE
(dump byte-identici: ogni chunk 2h teorico e' di per se' una cella M-TR) -> estensione al chunk-size
(leva 1) -> fill tail hold (leva 2, rango esplicito post-review). Il TASK-432 profile-following resta
dietro (stessa famiglia tematica, valutare solo se il 637 da solo non chiude). Docs:
task637-results/ab-adoption/VERDICT.md.

RATIFICA TASK-638 ViolationScopedSteepestFinisher (maintainer, delegata 22/09 "ratifica quello che devi", A/B 5/5):
**il finisher violation-directed a discesa ripida e' ADOTTATO DEFAULT ON** con opt-out standard
(`planner.construction.violation-steepest.enabled`, null/absent -> ON, solo "false" disarma).
Innesto: post-HRF pre-save TASK-526 (ultimo della terna, una volta per consegna), runner con
shell CSF-style (snapshot + 2x constraint-match + ensureImproveOrNoop full-hard). COSA FA:
score con match -> target = lezioni giustificate dai match hard-negative + CONTESTO (le "c"
di mezzo dei pattern A..B..A H28, la scoperta del cieco) -> generate mutation solo intorno
ai target (Change + Swap same-group con gate VRP isSlotLegalFresh) -> steepest hard-migliorante
con tie-break deterministico -> applica -> itera. Zero RNG, oracolo improve-only (monotone
non-decreasing hard), pinned mai mossi, belt catch/restore. EVIDENZA: A/B su ciclo fresco 5/5
criteri VERDI (ON [0/0] vs OFF [-2/0], il finisher chiude la consegna live -1 -> 0 in 414ms
con la swap Lavorazioni <-> TecnicheSaldatura: ESATTAMENTE la cura del blind puzzle H28).
Catalog sweep 4/6 stati storici a 0-hard (nessuna regressione). Valutazione incrementale
0,05ms/eval (141k eval in 7,4s). Suite 1370/0. Gates: /simplify 3-reviewer, code-review high
5 finding (4 curati 1 noto-non-mio), byteman 2/2. Docs: task638-results/{ab-adoption,
catalog-sweep,blind-and-moves}/.

RATIFICA TASK-629 v4 shape-aware (maintainer, 20/09, "1 si"): **la forma v4 del needsBackfill del
braccio H7Cap e' ADOTTATA** (nessun flag nuovo: e' l'evoluzione del braccio TASK-620 gia'
ratificato; commit dd439b66 + d75d837a). FORMA: needsBackfill = midRunExport || firstHourExport
|| invariante-di-conteggio v2 (qualsiasi esportazione non-bordo richiede il donatore, che atterra
sullo slot esatto vacato) + gamba H28 not-grow nel belt h7cap (h28ReturnsIn baselinate pre-apply,
forma h28PairSafe) + cure E1/C2 della code-review (classe net-zero: una prima-ora donor-less
scambiava H7 con H31 a pari e l'oracolo NET-hard la spediva; il donatore mid-day poteva creare
A..B..A a pari). EVIDENZA: catena v1 posizionale (REGRESSIONE, code-review F1) -> v2 invariant
(necessaria non sufficiente) -> v3 shape-aware (banco: cella T08 G01 26/05 -1 -> 0,
predizione pre-registrata col ramo alternativo centrato: @2 = gruppo G09 con donatore Lingua
Italiana) -> v4 tightening (banco: chiusura conservata, coppia vincente identica, nessun
BELT-H28). SECONDA cella reale chiusa su dati mai visti (T21 2027-06-03, la cella del
blind-meno1, donatore Orientamento, banco m39-on). Gates: /simplify (cure + scarti motivati),
code-review high (5 reviewer + scorer, E1/C2 curati) + review formale finale CLEAN, suite composita
1357/1357 col ripinno CalendarioImportRealSeedTest (stantio dalla cura input 18/09), guard
in-repo DeliveryTrioBenchTest self-skipping (ff9b6a33). Docs:
`integrato-1/delivery-trio-bench/VERDICT*` + task TASK-629 (registro completo v1->v4).

RATIFICA TASK-633 gamba H30 (maintainer, 20/09, "1 si"): **la gamba H30 (Sicurezza before stage) nella
belt condivisa verifyComposite del HoleRelayFinisher e' ADOTTATA** (nessun flag nuovo: e' una
gamba della belt che TUTTI i bracci cross-date gia' chiamano; commit 4717951e + fdfd609d + 6c620634).
COSA FA: nessuna composite puo' LASCIARE una lezione Sicurezza a data >= StagePattern.startDate del
suo gruppo (campo Live.stageStartByGroup, min startDate per gruppo, stessa SubjectNormalization e
bordo inclusivo del provider; 11/14 gruppi senza pattern = strutturalmente esenti). CHIUDE la
classe net-zero H30 di famiglia (una Sicurezza spostata oltre lo stage start pareggia l'hard curato
e l'oracolo NET-hard la spediva: stessa classe E1/C2 curata nel 629 v4, individuata dalla review
del 632 come gap preesistente dei bracci ratificati). EVIDENZA: equivalenza booleana esatta col
provider verificata da 3 review indipendenti; banco inerzia 3 basi (integrato-5 -1->0, m39-off
-18->-7, m39-on -22->-11: tutti IDENTICI al 632, census HRF-COMP-H30 assente); AUDIT Sicurezza su
tutti i log HRF storici = zero occorrenze (mai sparata: assicurazione pura). Parita'
banco-produzione per contratto sulle 4 copie test-side di verifyComposite (precedente TASK-622).
Docs: `integrato-1/delivery-trio-bench/` + task TASK-633.

RATIFICA quiescenza refiningForDelivery (maintainer, 2026-08-17): **il marcatore di rifinitura-di-consegna
e' ADOTTATO DEFAULT ON** (commit 5846dfbf, forma puramente additiva: campo `refiningForDelivery`
nel payload status per la durata della corner-b refinement del wrapper multi-start; i driver A/B
lo attendono). CHIUDE il bug dei due falsi segnali di quiescenza (NOT_SOLVING + stagedStage null
mentre la rifinitura gira su solver ad hoc, documentati dal 2026-07-29 e di nuovo nella sezione
"Osservare un solve staged"): prima del fix il driver dichiarava FINISHED a meta' rifinitura,
catturando artifact incoerenti (round 3: analysis -7795hard congelata mid-refinement vs score
-42hard letto 26s dopo) e uccidendo l'app prima del salvataggio. VALIDATO live due volte
(round 4-preview entrambe le braccia + run5): hard concorde tra score.txt e analysis.json,
rifinitura completata (603s), quinto tentativo consegna -42 da solo senza corner-b. Residuo
noto e mappato (TASK-509): il MEDIUM resta divergente tra le due sorgenti (latestScore in-memory
vs riconteggio DB), meccanismo isolato, non hard-blocking.

RATIFICA TASK-498 skip-doomed-room (maintainer, 2026-08-15): **il salto della rifinitura stanze sui
tentativi condannati e' ADOTTATO DEFAULT ON** con opt-out esplicito (stessa forma delle altre:
`ScheduleService.skipDoomedRoomEnabled()` e' `null/absent -> true`, solo
`-Dplanner.construction.skip-doomed-room.enabled=false` disattiva; banner `Skip-doomed-room
refinement (TASK-498): enabled=%b`). COSA FA: al confine post-RELEASE (dopo ExactFinisher,
prima delle stanze), se il tentativo e' condannato (timeslot-tier hard <
`STAGED_HARD_ACCEPT_THRESHOLD`, la STESSA costante del gate B-TIMESLOT: nessuna soglia nuova)
e non e' l'ultimo, la fase stanze corre senza il LS di rifinitura (CH-only: ogni lezione
riceve stanza: lo score pieno resta MECCANICAMENTE confrontabile ma NON e' un segnale di qualita'
comparabile, vedi l'emendamento 21/08 alla ratifica TASK-483: il pick-best ranked su score pieno
sceglieva sempre l'ultimo in un run all-doomed; la chiave primaria e' ora il boundary tier).
CORNER CHIUSI: (a) not-last-attempt
(l'ultimo tentativo mantiene il refinement: potrebbe vincere per forza); (b) se il vincitore
finale e' un condannato (budget-break/all-doomed) il wrapper ne rifinisce le stanze PRIMA della
consegna (la UI non riceve mai un orario non rifinito). EVIDENZA (A/B appaiato, STESSO
tentativo-1 condannato in entrambe le braccia): 0hard entrambi, KPI piatti, fase stanze del
condannato 603s (control) vs 3s CH-only (treatment), vincitore attempt-2 identico. Robustezza:
5/6 firme bad rilevate, 0 falsi positivi su n=19 + 6 re-run (firme quasi deterministiche per
seed). Risparmio: ~600s per tentativo condannato. E' la detection early che maintainer chiese il
14/08 (T-minus): la separazione viaggia nella transizione di fase (RELEASE), non nel tempo.

RATIFICA TASK-543 FASE 2 single-writer dispatch (maintainer, 24/08 12:12): **il dispatch
single-writer a canali per-thread del decider MT-LS e' DEFAULT ON** con opt-out
esplicito (stessa forma standard: `planner.multithreaded-ls.single-writer.enabled`,
null/absent -> ON, solo il letterale "false" ripristina la coda condivisa legacy;
banner "dispatch=" in `refreshSolverStatics`). EVIDENZA: FASE 1 DIVERGENTI (costruzione
byte-identica, prima fase LS 12.542/12.821 placement diversi: non-determinismo
patologico del decider legacy) + censione di tre difetti della famiglia (SimpleFieldTracker
22/08, poison-pill TOCTOU 06:20 con fix+TDD, step-chasing desync 07:52) + A/B wall
single-writer vs legacy (LS ~62min vs ~70, ciclo 1h33 vs 1h35, zero mismatch/stalli,
consegna -757 in banda) + REPRODUCIBLE x2 finale sul ratificato: costruzione
byte-identica, prima fase LS **6.041/12.821 placement diversi vs 12.542 del legacy**
(la componente race della divergenza e' dimezzata; consegne -716 vs -721). PROTOCOLLO: canale privato per runner (mono-produttore il
decider/mono-consumatore il runner), MAI clear (la clear del legacy droppava apply non
consumati = il desync), barriera di STEP BOUNDED a 60s TASK-541-shape con consultazione
dell'eccezione registrata a ogni iterazione (il drain da solo drenava la pill e
parcheggiava per sempre: fix del review gate 24/08 con test di regressione
deadConsumerFailsTheBarrierLoudAndBounded). Known residual dichiarato: NON byte-exact
(l'ordine di arrivo dei risultati resta schedulato: variabilita' INTRINSECA, il
patologico e' morto); le due regole Byteman di guardia vanno eseguite al land su main
sul banco TASK-544. L'harness anti-errore-di-lancio nato dagli incidenti di processo:
scripts/benchmark/run-measure.sh. DoD: /simplify (3 reviewer) +
/review-local high (5 reviewer, finding critico fixato) + test.sh 1157 EXIT 0.

RATIFICA TASK-480 multi-threaded LS (maintainer, 2026-08-14): **il decider LS multi-threaded community
(step chasing, pacchetto `solver/multithreaded/`) e' ADOTTATO DEFAULT ON** con opt-out esplicito
(stessa forma delle altre ratifiche: `ScheduleService.multiThreadedLsEnabled()` e' `null/absent
-> true`, solo `-Dplanner.multithreaded-ls.enabled=false` disattiva; letto LIVE, banner
`Multi-threaded LS: enabled=%b, threads=%d` in `refreshSolverStatics`; knob
`planner.multithreaded-ls.threads` default 8 floored a 1). COBERTURA: wired SOLO nel path staged
(`runStagedSolver`, la modalita' di campagna/riferimento); il `/solve` ordinario via
SolverManager resta single-threaded. EVIDENZA: A/B within-session 2026-08-13 (worktree) OFF
single-thread 0hard/-1222k/114min vs ON 8-thread 0hard/-1277k/86min = **-25% wall** (LS puro
-28%), ricorrenza in banda 75,6/75,7, medium entro varianza; su un multi-start N=3 il risparmio
e' ~84 min. Cleanup wiring nel commit di ratifica `cbb74818` (BrokenBarrierException storm
risolta con markShuttingDown dentro shutdownMoveThreads + ShutdownExitException catturata per
tipo; reflection wiring documentata come fragile/pinned a Timefold 2.3.0 internals nel javadoc).
DoD: /simplify 2 round + adversarial verification (shutdown-path, flag-form) + test.sh 996 EXIT 0
da orchestratore Fedora. Known residual: la pulizia API del wiring (via reflection) puo' essere
un task successivo; il confirm run production e' il filone A della notte 14/08. Unico guadagno
di performance dal pivot al modello (le metaeuristiche sono esauste dal 08-05).

RATIFICA TASK-486 (maintainer, 2026-08-04): **M-TC per-retry adottato DEFAULT ON** con opt-out
esplicito (stessa forma delle altre ratifiche: `ScheduleService.mtcPerRetryEnabled()` e'
`null/absent -> true`, solo `-Dplanner.multistart.mtc-per-retry.enabled=false` disattiva; letto
LIVE, banner `M-TC per-retry: armed=%b` in `refreshSolverStatics`). Il multi-start wrapper, quando
armato, possiede `planner.tag-capacity.enabled` per la durata del loop e lo setta per tentativo via
`applyMtcForAttempt(attempt)`: tentativo 1 (deterministico, good basin) M-TC **off** (nessun
overhead 2x), tentativi >=2 (retry, bad basin) M-TC **on** (cura deterministica del room-tier
saturo che il solo multi-start aggira solo probabilisticamente). Subordinato a multi-start: se
multi-start e' off (single solve) non ci sono tentativi >=2 e la leva e' inerte. EVIDENZA:
good-seed A/B 1558s/0hard vs control 1673s/0hard (**no 2x**, `mtc_enabled_true_refreshes=0` al
tentativo 1 = M-TC mai entrato nel constraint set, come disegnato), bad-seed grasp-seed-6 retry
cura a 0-hard (commit `a35633f3`). Risolve la ratifica M-TC che DEFAULT-ON piatto (TASK-455)
aveva respinto per wall 2x su ogni run (branch `ratification/m-tc-default-on` cancellato). M-TC
flat (`planner.tag-capacity.enabled`, TASK-455) resta opt-in prototype DEFAULT OFF per esperimenti
isolati.

RATIFICA TASK-483 (maintainer, 2026-08-03): **multi-start retry incapsulato nel prodotto, adottato
DEFAULT ON** con opt-out esplicito (stessa forma delle altre ratifiche:
`ScheduleService.multiStartEnabled()` e' `null/absent -> true`, solo
`-Dplanner.multistart.enabled=false` disattiva; letto LIVE, banner
`Multi-start staged retry: enabled=%b, maxAttempts=%d, budgetSeconds=%d` in
`refreshSolverStatics`). Il multi-start NON e' piu' una procedura che qualcuno deve conoscere e
lanciare a mano: `startStagedSolving` ritenta il ciclo staged con costruzione GRASP randomizzata
(tentativo 1 deterministico = il path di sempre, tentativi >=2 seeded col numero del tentativo),
pick-best su (hard, medium), **stop al primo 0-hard**. EMENDAMENTO 21/08 (corner-b TASK-498,
notte h36-verify): il pick-best e' ora a DUE LIVELLI - chiave PRIMARIA il timeslot-tier hard al
confine post-RELEASE (l'unico asse comparabile tra tentativi: un condannato not-last consegna
stanze CH-only il cui rumore room-tier ~9.400 H29 sommerga lo score pieno, quindi in un run
all-doomed il pick su score pieno sceglieva SEMPRE l'ultimo per costruzione), tie-break sullo
score pieno (hard poi medium, latest-wins invariato). Knob `planner.multistart.max-attempts` e
`planner.multistart.budget-seconds`. Stato osservabile `stagedAttempt`/`stagedMaxAttempts` +
indicatore UI "Tentativo X di Y" e badge finale che DICHIARA l'esito (0-hard e' binario: un orario
con hard residuo non e' "quasi pronto"). EVIDENZA della ratifica: batch frequenza bad-seed n=8
costruzioni GRASP indipendenti + 1 baseline (`task480-results/badseed-frequency/`,
VERDICT-badseed-frequency-2026-08-03.md): **p(0-hard) per singola costruzione = 0.750**, Wilson95
[0.409, 0.929], quindi un solo tentativo consegna hard residuo circa una volta su quattro, mentre
N=3 porta P(>=1 0-hard) oltre 0.95; wall medio 28 min/run. COSTO ZERO sui bacini buoni: il loop si
ferma al primo 0-hard, quindi un seed buono gira esattamente un ciclo, schedule-identica al
comportamento pre-ratifica (l'unica differenza osservabile e' transitoria: durante il solve i campi
`stagedAttempt`/`stagedMaxAttempts` pubblicano "tentativo 1 di N" dove il path diretto lasciava 0,
poi il wrapper li resetta a 0 nel finally, quindi il payload finale coincide). I due bacini cattivi del campione sono modalita' di fallimento DISTINTE
(grasp-seed-1 = H17 -9 strutturale su G11 mercoledi; grasp-seed-6 = H29 -1 room-null da
saturazione del tag meccanica, che ha UNA sola stanza in `DataGenerator.ROOM_TAG_MAP`): il retry le
AGGIRA ripartendo, non le cura. Cure vere tracciate altrove: TASK-481/482 per H17, TASK-455/485
(M-TC + esenzione co-presenza praticata) per la capacita' dei tag.

### Osservare un solve staged: due falsi segnali che costano un run (2026-07-29)

Una certificazione e' stata fermata al minuto 26 di un ciclo da 49 e ne e' uscita una
diagnosi di "stallo" inesistente. I due segnali che ingannano:

- **`solverStatus` vale NOT_SOLVING per TUTTA la durata di un solve staged**, per costruzione:
  `ScheduleService.runStagedSolver` costruisce un `Solver` ad hoc con `SolverFactory` e lo
  esegue con `solver.solve(problem)`, quindi il ciclo staged non passa da `solverManager`, che
  e' la sorgente di quel campo. Il campo da pollare e' **`stagedStage`**: il ciclo e' finito
  quando e' nullo. Il codice stesso usa `getStatus() == NOT_SOLVING && stagedStage == null`
  come condizione di riposo.
- **hard converge SOLO nell'ultima fase.** Nel run certificato (`task447-results/cert/phases.json`,
  usarlo come cronologia di riferimento) hard resta congelato a -2226 per **15,8 minuti**
  consecutivi in una LS di B-TIMESLOT mentre solo soft sale, e arriva a -195 solo nei 10
  minuti finali di rifinitura stanze (-9407 -> -195). Il ciclo dura ~49 minuti e le stanze
  entrano in gioco al minuto 39,2. Attenzione anche alla contabilita' del TASK-447
  (hard = boxed x2 + room-null x1): hard che PEGGIORA a meta' ciclo puo' voler dire lezioni
  collocate che non hanno ancora una stanza, cioe' progresso.

Corollario: un solve lungo non si interrompe per un sospetto. Prima si confronta l'istante
corrente con la cronologia per fase del riferimento.

## ⚠️ Sanity gate di validazione: OBBLIGATORIO su OGNI run (maintainer, 20/08)

Nessun verdetto su una soluzione senza la tabella sanity per-gruppo: minimo ore/giorno del run
vs minimo del golden, giorni sotto il minimo, buchi interni 1h (H17-like), lezioni senza stanza.
**Un rosso = verdetto non conforme**: vietato dichiarare "buono" un run con hard residuo
incorniciandolo come "bacino sfortunato" (0hard e' binario). Lo scope di un vincolo non e' scusa:
un gruppo fuori-scope di H32 resta dentro il sanity gate (cosi' i giorni da 2-3 ore di G04 non
sfuggono). Strumento: `scripts/analyze/solution-sanity-check.py` (TASK-535, integrato in
capture-run.sh: sanity-report.txt per ogni capture). Precedente: 20/08 maintainer ha trovato a occhio,
in una soluzione che la validazione dichiarava "la piu' vicina al golden mai prodotta", 11 giorni
inaccettabili di G04, 43 buchi di G10 e 72 lezioni senza stanza.

## Principio: pattern-exhaustion-first (maintainer, 2026-07-27)

I pattern "hard umani" (giorni monografici, rotazioni, cadenze del foglio) sono
best-effort: si trattano come hard finche' i limiti matematici non impongono di
gestire il residuo come ECCEZIONI esplicite che possono rompere il pattern. In quei
casi si esaurisce il pattern regolare AL 100% e solo il rimanente diventa eccezione:
mai usare l'esistenza delle eccezioni per diluire il consumo del pattern originario
(caso canonico: i 9 giovedi' monografici T01 = 65h esatte del corso, TASK-432).
Vale per costruzione (template/stamp/profile-following), repair e leve di cadenza.

## Canonical Constraint IDs

Every `.asConstraint()` string is prefixed with its canonical ID using the format `<code> - <description>` (e.g., `"H1 - Teacher conflict"`).
**Colons are not allowed** — Timefold 2.0+ rejects them via `AbstractConstraintBuilder.sanitize()`. Allowed characters: alphanumeric, spaces, underscores, hyphens, apostrophes, parentheses, full stops.
All three branch families now share one constraint provider and register the unified IDs shown in the **Baseline (current)** column below (verified against the 62 `.asConstraint` strings in `SchoolScheduleConstraintProvider` + `BaseConstraintProvider`).
The **Shadow (L-F)** and **Block (L-B)** columns are a historical record of the pre-TASK-228/267 split architecture: when `LabFollower`/`LabBlock` planning entities existed they split H1-H6 and H-M2 into per-entity tiers (H1.1/H1.2/H1.3 and similar) and carried H9/H19; those entities were removed by TASK-228/267, so the split IDs are no longer registered (only dead `ConstraintMessageFormatter` branches and comment examples survive).
Live dot-notation sub-constraints today are H4.3, M20.2, M30.1, M30.2.

### Hard Constraints

| ID | Semantic Name | Baseline (current) | Shadow (L-F) (historical) | Block (L-B) (historical) |
|----|--------------|----------|--------------|-------------|
| H0 | Unassigned lesson penalty | H0 | H0 | H0 |
| H1 | Teacher conflict | H1 | H1.1, H1.2, H1.3 | H1.1, H1.2, H1.3 |
| H2 | Room conflict | H2 | H2.1, H2.2, H2.3 | H2.1, H2.2, H2.3 |
| H3 | Student group conflict | H3 | H3.1, H3.2, H3.3 | H3.1, H3.2, H3.3 |
| H4 | Stage blackout (non-prof only). Sibling `H4.3` (professionalizzanti constant single-day during stage, in `BaseConstraintProvider`) is **inert/latent** (0 matches on the 2026/27 seed) and not split into the branch columns | H4 | H4.1, H4.2 | H4.1, H4.2 |
| H5 | Teacher unavailability | H5 | H5.1, H5.2, H5.3, H5.4 | H5.1, H5.2, H5.3, H5.4 |
| H6 | School holiday | H6 | H6.1, H6.2 | H6.1, H6.2 |
| H7 | Max teacher daily hours (per-teacher maxDailyHours since TASK-348, default 6; T01 8) | H7 | H7 | H7 |
| ~~H8~~ | ~~Min student group daily lessons~~ | **REMOVED** | — | — |
| ~~H9~~ | ~~Lab entity valid placement~~ | never in Baseline | **REMOVED** with `LabFollower` (TASK-228/267) | **REMOVED** with `LabBlock` (TASK-228/267) |
| H11 | Max student group daily hours | H11 | H11 | H11 |
| ~~H11-T28~~ | ~~Max 6h/giorno del docente id-14 per gruppo~~ | **REMOVED** (TASK-440, 2026-08-16): l'id hardcoded puntava sul docente sbagliato nel seed attuale (id 14 = T09; T28 NON e' nel roster 2026/27, e l'id 18 e' T01, che porta una VERA eccezione 8h: non confondere), NESSUN docente ha il flag external attivo, e il cap H7-a-6 morde prima: l'eccezione era muta. Rimossa la constraint, il filtro di esclusione da H11, i 4 siti T28_TEACHER_ID (provider, HybridPhase1aService, ScheduleService, GreedyRotationAssigner) e la split T28 nella drill-down UI. Doppia prova: golden regression H11 resta 0 (le 555 lezioni T09 ora contano) + H11 e' strutturalmente inerte sul seed (finestre calendario <= cap per ogni gruppo: nessun orario H3/H20-compatibile puo' violarla). | — | — |
| H14 | Student group start date | H14 | H14 | H14 |
| ~~H15~~ | ~~Same-day same-subject consecutive~~ | **DEMOTED to M15** (TASK-392, break-level pinned-exempt kept) - see "H15->M15 demotion" below | — | — |
| ~~H16~~ | ~~Consecutive same-subject same-teacher must keep the room~~ | **DEMOTED to M37** (TASK-446/447, notte 28-29/07): il golden non registra le aule, quindi non difende il tier hard, mentre "ogni classe ha un'aula" (H29) e' fisicamente irrinunciabile. A hard, la LS preferiva lasciare l'aula NULLA (silente) pur di non cambiarla a meta' blocco: 291 ore non-lab senza aula, scese a 3 dopo la demozione. H16a era gia' diventato S16 col TASK-377. | - | - |
| H17 | Vertically compact, break-level pinned-exempt - see "H17 afternoon-contiguity + M18 gate recalibration" below (task-350) | H17 | H17 | H17 |
| ~~H18~~ | ~~5-hour groups must start from slot 0~~ | **DEMOTED to M18** (task-247) | — | — |
| ~~H-D1~~ | ~~Doppio teacher conflict~~ | **NOT REGISTERED** (censimento TASK-446: nessun `.asConstraint("H-D1...")` nel codice; la semantica doppio/assistente vive in M-LA a tier medium, vedi javadoc `LabAssistant`; "H-D1" sopravvive solo in commenti) | - | - |
| ~~H19~~ | ~~Max one lab block per day per pair~~ | never in Baseline | never in Shadow | **REMOVED** with `LabBlock` (TASK-228/267) |
| H-M2 | Room suitability (was M2) | H-M2 | H-M2 | H-M2.1, H-M2.2 |
| H20 | Group calendar closed slot (TASK-342) | H20 | H20 | H20 |
| H23 | Quota-bound lab lesson on zero-lab-quota day, pinned-exempt (TASK-381 "L1-bis") | H23 | H23 | H23 |
| H24 | Aula lesson on zero-aula-quota day, pinned-exempt (TASK-381 "L1-bis") | H24 | H24 | H24 |
| H25 | Stage lesson outside stage days, pinned-exempt (TASK-384 - VRP+constraint safety net twin of the Stage confinement filter, see "Stage confinement" below) | H25 | H25 | H25 |
| H26 | Lesson on no-session day, pinned-exempt (TASK-408 - for a sheet-covered group a school date with no DailyActivityQuota row = group not in session; VRP+constraint safety net twin of the no-session filter, see "No-session days" below) | H26 | H26 | H26 |
| H28 | No teacher return within (group, subject, day) (TASK-448 - golden-first guard at delta zero: the golden never returns A..B..A, the run is already 0, so it cristallizes the invariant at no cost) | H28 | H28 | H28 |
| H29 | Assigned lesson must have a room (TASK-447: was M36 medium 1000, before that the bocciato hard H27 at parity with H0; PROMOTED to HARD weight 1, paired with H0 reweighted to hard 2 - de-assigning -2 is strictly worse than room-null -1, killing the empty-the-schedule arbitrage). Classed as a ROOM-TIER hard constraint (excluded from `ScheduleService.timeslotTierHardScore` via `ROOM_TIER_HARD_FAMILIES`) so the staged cycle still reads timeslot-tier hard == boxed pre-B-ROOM. **BREAKS the historical invariant `hard == boxed-count`: now `hard == boxed*2 + room-null*1`; a lab-heavy schedule with ~254 room-null now reads hard != 0 and `getFeasibility` (hardScore==0) reports it infeasible - accepted by maintainer, see VERDICT.** Pinned/pseudo exempt. | H29 | H29 | H29 |
| H-COPRESENCE | Guest co-presenza beyond golden depth (TASK-464). ADOPTED DEFAULT ON since 2026-08-01 (opt-out `planner.co-presenza.enabled=false`, letto LIVE per-build come M-LF; ratifica combined 463+464 sotto). The count-cap backstop for the H2 pairwise exemption: H2 exempts golden-practiced guest pairs (same single co-presenza tag on a `(tag, weekday, slot)` cell with golden depth >= 2) so an ospite may share a host's room, and this cap re-imposes the golden's OWN depth as the ceiling - `forEachIncludingUnassigned.filter(enabled && single-tag && real teacher).groupBy(tag, startDateTime, count).filter(depth>=2 && count > goldenDepth).penalizeHard(count-depth)`. **TIMESLOT-TIER, NOT room-tier** (groups by `(tag, startDateTime)`, never reads `l.room`): it is NOT in `ROOM_TIER_HARD_FAMILIES`, so `timeslotTierHardScore` COUNTS it and the staged B-TIMESLOT gate + repair `assertImproveOrNoop` resolve over-depth cells in B-TIMESLOT (listing it room-tier would blind them and let a violation persist into B-ROOM where rooms cannot fix a timeslot co-location). Depth map published PRE-solve from the golden (`ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth`, mirror `loadGoldenSlot0Anchors`): golden-runtime read censita in TASK-452 #7. Evidence: full-solve A/B n=2 hard -112 -> -17/-18 (94 acconciatura room-null -> ospite-legale, 0 over-depth), combined run3 hard -6. Doc: `task464-copresenza-ospite-design-2026-07-31.md` + `task464-copresenza-checkpoint-2026-07-31.md` | H-COPRESENCE | H-COPRESENCE | H-COPRESENCE |
| H30 | Sicurezza before stage (was M26, PROMOTED to HARD maintainer 2026-08-15, TASK-472 classification: golden practice 6/6 groups zero violations, H28-style crystallization; renamed M26->H30 per the ID-prefix convention, stesso precedente di M36->H29; referente conferma a posteriori domande-brevi rev 15/08, non bloccante golden-first). `ONE_HARD` per lezione di Sicurezza a partire dalla `StagePattern.startDate` inclusa (in `BaseConstraintProvider.sicurezzaBeforeStage`, join precompute Lesson x StagePattern; gruppi senza StagePattern seeded = no-op strutturale) | H30 | H30 | H30 |
| H31 | Start of day at first calendar-open slot (TASK-529, 19-20/08: promossa da M18 con protocollo near-miss - golden 1943/1960 giorni alle 8:00, le 13 eccezioni sono tutte il primo giorno di scuola; run di verifica: tutti i gruppi a 1 solo giorno late, G04 15/09 dalle 10:00 alle 8:00, 0hard). Penalta' hard x slot-offset, esente i giorni con ore pinnate autoritative >0 (accessore TASK-424); flag opt-out `planner.constraints.start-of-day.enabled` (null->ON, solo "false" disarma). Curava il difetto trovato da maintainer in ispezione visiva (day-start KPI aggregato 99,5% nascondeva G04 16 giorni late) | H31 | H31 | H31 |
| H32 | Day fill meets the declared quota (TASK-532, 19-20/08: ore non-Stage del giorno >= DailyActivityQuota aula+lab di input, TOLLERANZA ZERO perche' il golden converge ESATTAMENTE alla quota in-grid per 10/11 gruppi - unica eccezione reale 1h su G09 01/10, le righe quota oltre il 10/06 sono fuori griglia e non contano; le ~20h di "contraddizione input" di G12 erano tutte fuori-griglia). SCOPE: sole date con timeslot; ESCLUSE G04 e G08 (il loro target e' la forma-b pool-cap TASK-497, non la quota del foglio) e G14; G09 tolleranza 1h. Flag opt-out `planner.constraints.quota-fill.enabled`. Cura il difetto "G12 venerdi' 3 ore" dell'ispezione maintainer 19/08. Companion: QuotaFillRepair (pass deterministico a DUE occasioni post-RELEASE e post-B-ROOM, mosse bordo-consapevoli: donatore preleva dal bordo del blocco, bersaglio estende il proprio, mai prima ora; H32 26 -> 8 celle misurato). STATO: codice default-ON, ratifica formale maintainer pendente sul residuo 8 celle | H32 | H32 | H32 |
| H33 | Per-group hard day floor (TASK-536, 20/08, post-ispezione maintainer "G04 giorni da 2h inaccettabili"): ore non-Stage del giorno >= pavimento per-gruppo derivato MATEMATICAMENTE in `DayFloorDeriver` = min(4, mathFloor(pool_non_stage/giorni_sessione), goldenExistenceMin): 4h per 12 gruppi, 2h dove il golden stesso soccombe (G08, G09), 3h G14. Il math floor (5h per 12 gruppi) resta ASPIRAZIONE DOCUMENTATA non vincolo (domanda referente: giorni da 4h voluti o subiti); il golden entra SOLO come cap di esistenza (direttiva maintainer 20/08: deriva matematica, golden solo hint). Trappola parsing incarichi: le righe 'Totale ore corso' raddoppiano i pool. Evidenza primo run: H33 consegna 12 -> 1. Flag opt-out `planner.constraints.day-floor.enabled`. STATO: codice default-ON, ratifica formale pendente | H33 | H33 | H33 |
| H34 | Empty declared day (TASK-557, 26/08): il companion hard di H32. Il groupBy di H32 vede solo i giorni CON lezioni, quindi un giorno dichiarato (quota aula+lab>0) COMPLETAMENTE vuoto e' invisibile al tier hard e paga solo M20.2 medium: la LS lo tratta come moneta di scambio. H34: ifNotExists per (gruppo, riga-quota in-scope H32) senza lezioni non-Stage assegnate quel giorno -> penalizeHard FLAT 1 (la presenza e' il difetto; la profondita' resta di H32 appena atterra una lezione). Implementazione: join Timeslot x StudentGroup deduplicato per (gruppo, data) con letture statiche PER MATCH e armo per-cella (la factory app-context si cache a boot: registration-gate cache-hostile, vedi m22MinDays). EVIDENZA (pre-cura 27/08): golden pin `-2hard` in GoldenSolutionRegressionTest (le 2 celle G09 fine-anno 04/06 quota 5 e 07/06 quota 3); STRICT-FIRST vinto (emendamento eb00d522): le 2 celle golden sono RIEMPITE dal nostro ciclo (better-than-golden, tolleranza NON introdotta); ciclo completo dci-h34on: -14hard/-1.315.606, sanity NON peggiore, buchi interni 0, QFR 22->10 celle; le 10 residue = filone import teacher-locked mag-giu + G13 (cura search-side multi-start, non tolleranza). POST-CURA G09 (27/08, vedi CURA INPUT qui sotto): il pin golden e' `-1hard` (solo 04/06 quota 4; la 07/06 non e' piu' dichiarata). RATIFICATO DEFAULT ON (maintainer 26/08, opzione A): flag `planner.constraints.empty-day.enabled` forma opt-out standard (null/absent -> ON, solo "false" disarma). Run pre-H34 25/08: 12 celle ~58h | H34 | H34 | H34 |

### Medium Constraints

| ID | Semantic Name | Baseline | Shadow | Block |
|----|--------------|----------|--------|-------|
| M3 | Room capacity | M3 | M3 | M3.1, M3.2 |
| ~~M4~~ | ~~Lab room-tag affinity~~ | **REMOVED** (TASK-228) | — | — |
| M5 | Reward Informatica after stage | M5 | M5 | M5 |
| M12 | Overflow beyond target end date (ONE_MEDIUM/day since 2026-07-19). Targets are FACT-BASED since TASK-394: where DailyActivityQuota expected-hours facts exist (all 14 groups in 2026/27) the ideal end cumulates the school's own per-date plan to totalHours (G06: 10/06, no longer the flat-8h-model's 31/03), legacy fill-rate model only as fallback — the old "golden places 29.3% beyond targets" was an artifact of that model (see Q20) | M12 | M12 | M12 |
| M15 | Same-day same-subject should be consecutive, break-level pinned-exempt (was H15; demoted TASK-392 - golden splits it 98x, 19 of them Lingua Italiana, so hard was stricter than practice - see "H15->M15 demotion" below). `ofMedium(20)` x gap-minutes (~1200/1h-split, M20/M22 "logical hard medium" family, NOT weak ONE_MEDIUM - must compete with the mediums it trades against or the solver scatters) | M15 | M15 | M15 |
| ~~M17~~ | ~~Lab recurring pattern~~ | **REMOVED** | — | — |
| M18 | Day should start from first calendar-open slot — WIDENED to every (group, date) day with ofMedium(50)×offset gradient (TASK-405; peso ritarato 250→50 dall'A/B laptop task405: a 250 day-start 96%+ ma ricorrenza sotto soglia; golden anchors 99.86%, guard pins −150/3 residual, all G08). Was gated to ≤5-open-slot days (task-350) at flat ONE_MEDIUM (task-247, downgraded from H18; the original M18 "multi-day lab start-time consistency" was removed, see Removed Constraints). Paired with the deterministic left-anchor pass in StudentDayCompaction | M18 | M18 | M18 |
| M20 | Daily quota fill, expected-hours-targeted since TASK-385 (calendar-capped fallback from TASK-342; was "no sparse student days" pre-task-273). Target NETTO dei pin autoritativi dal TASK-424 (`effectiveQuota` = quota del giorno meno le ore pinnate autoritative di docente reale), vedi "Target netto dei pin autoritativi (TASK-424)" sotto. Sibling `M20.2` (Empty expected day, `SchoolScheduleConstraintProvider`) e' attivo e condiviso | M20, M20.2 | M20, M20.2 | M20, M20.2 |
| M21 | Max 2h per subject per day, recalibrated - see "M21 recalibration" and "Shape recalibration (L3, TASK-381)" below (task-365, threshold raised task-381) | M21 | M21 | M21 |
| M22 | Calendar compactness (task-273; scored ONE_HARD until TASK-377 aligned it to its M id, 1000 medium/excess day). minDays = ceil(ore/maxHoursPerDay); RATIFICATO DEFAULT ON dal TASK-558 (maintainer 27/08, forma opt-out `planner.constraints.m22.quota-aware.enabled`: null/absent -> ON, solo "false" ripristina il legacy; letto PER MATCH nel flusso, NON captured a build time - lezione m22MinDays): minDays emendato = ceil(ore/media-quota-dichiarata aula+lab, pubblicata pre-solve in `SchoolSchedule.getAverageDeclaredOrdinaryQuota`; fallback legacy per gruppi senza foglia). Motivo: la 25/08 aveva dimostrato la FINZIONE del min-days legacy (golden unpinned-equivalente pagherebbe 463 giorni-eccesso col metro del vincolo); misura emendata (scripts/analyze/m22-quota-aware.py): golden 463 -> 3, run 410 -> 0. E' un cambio di METRO, non di orario. Pack: task558-results/gate-emendamento-m22-2026-08-27.md | M22 | M22 | M22 |
| M23 | Block lesson pair togetherness | M23 | M23 | M23 |
| M24 | At most one early start day per week, per-teacher `maxEarlyStartDaysPerWeek` (T11=1, TASK-368) | M24 | M24 | M24 |
| M25 | Required free day per week, per-teacher `requiredFreeDaysPerWeek` (T05=2, TASK-368) | M25 | M25 | M25 |
| M27 | T20 subject start-period preference (TASK-369) | M27 | M27 | M27 |
| M28 | Afternoon slots reserved for stage, pinned/pseudo-teacher exempt (was H21; demoted + VRP exclusion removed TASK-379 - golden uses 575 afternoon lessons as capacity relief). Weight 100/lesson | M28 | M28 | M28 |
| M29 | Professionalizzanti at day edges, group-generic rule table, day-level pinned-exempt (was H22, task-370; demoted TASK-379 - golden never followed the edge shape) | M29 | M29 | M29 |
| M30 | Lab day quota fill, counts pinned lessons too - see "Daily activity quotas" below (TASK-381 "L1-bis") | M30.1, M30.2 | M30.1, M30.2 | M30.1, M30.2 |
| M31 | Lab day single contiguous run, day-level pinned-exempt (TASK-381 "L1-bis") | M31 | M31 | M31 |
| M32 | Template deviation, scarcity-graduated (TASK-413 quasi-pinning: penalizza la lezione unpinned fuori dalla cella (weekday, slot) template del suo corso, peso = clamp(100k/celle-VRP, 20, 500); riferimento FISSO calcolato PRE-solve - see "Template deviation" below) | M32 | M32 | M32 |
| M33 | Surplus beyond expected day hours, immagine speculare di M20 (TASK-423: penalizza max(0, ore - effectiveQuota(gruppo, data)) x 100/ora, stessa popolazione di M20 - unpinned/non-autoritative, G14 esente - e giorni a quota RAW 0 fuori ambito perche' H26 li vieta gia' a tier hard; scoraggia il parcheggio sopra il piano senza vietarlo, il golden ha 70 giorni sopra). TASK-424: il confronto e' contro la quota NETTA dei pin, il gate di ambito resta sulla quota grezza | M33 | M33 | M33 |
| M34 | Weekly subject hours above golden cadence (TASK-425: cap settimanale per (gruppo, materia) al massimo settimanale del golden da golden-cadence-reference.json; self-gating, pair assenti inerti; peso 100/ora oltre cap, knob planner.m34.weight) | M34 | M34 | M34 |
| M35 | Weekly subject spread below golden cadence (TASK-425: penalizza le settimane attive mancanti rispetto ad active_weeks del reference golden per (gruppo, materia); self-gating; peso 50, knob planner.m35.weight; residuo calibrato guard -50/1, G04/Scienze W24 oltre orizzonte) | M35 | M35 | M35 |
| M37 | Consecutive same-subject same-teacher should keep the room (era H16b hard, demoted TASK-446/447). `ofMedium(100)` per coppia adiacente, stesso filtro del vecchio H16b (non-lab, entrambe con aula, adiacenti, stesso docente, aula diversa), knob `planner.m37.weight`. Il peso NON e' la leva: sweep 100/200/400/800/1500 piatto a ~106 cambi d'aula, che sono forzati dalla disponibilita' di stanze | M37 | M37 | M37 |
| M-LF | Non-lab lesson in a tagged (lab) room (TASK-456 lab-fallback, domanda 21). `ofMedium(planner.room.lab-fallback.weight, default 1000)` x lezione, knob letto INLINE per-build come M37. Filtro `!isLab()` (convenzione M15/M21/S11/S16, NON requiredRoomTags come S10: coincidono sul seed ma possono divergere su corsi REST-desincronizzati). Gated da `planner.room.lab-fallback.enabled` letto LIVE (default ON dalla ratifica maintainer 2026-07-29): attivo di default, inerte solo con flag esplicitamente "false" (opt-out). Peso 1000 DECISO (a parita' sugli altri KPI minimizza le ordinarie nei lab: 7 vs 10 a w=1, "lab = ultima risorsa", nessuno svantaggio misurato). Abbinato all'allargamento del dominio valori in `Lesson.getAvailableRooms` (non-lab vede anche le stanze tagged) e all'esclusione delle stanze capienza<=0 (`Room.isAssignable`, oggi solo R18 cap 0): pacchetto unico ratificato (fallback ON + esclusione cap-0). Sibling di S10 (soft 50, sempre attivo): co-firano | M-LF | M-LF | M-LF |
| M-LA | Lab assistant conflict | M-LA | M-LA | M-LA |
| M-TC | Lab tag capacity (TASK-455, scarcity-aware spec 2026-07-30). PROTOTIPO dietro flag `planner.tag-capacity.enabled` DEFAULT OFF (opt-in, letto LIVE per-build come M-LF; inerte a 0 match da default). Penalizza `ofMedium(planner.tag-capacity.weight, default 1000)` x excess, per cella `(tag, startDateTime)`, la domanda di lezioni di un tag oltre `SchoolSchedule.roomCapacityForTag` (pubblicata pre-solve dalle stanze assignable). E' l'espressione a valle (consumatore "ricerca locale") del rilevatore di scarsita' della spec, non un meccanismo autonomo: cio' che il rilevatore misura a monte (scarsita' congiunta tag-weekday). Tier MEDIUM-alto NON hard (hard dichiarerebbe infeasible il golden, 44 viol accettate dalla scuola, e irrigidirebbe la ricerca): il trade-off si MISURA al checkpoint. Conteggio H7-style (pinned incluse nella domanda, la leva e' muovere le unpinned). Bersaglio 30e: acconciatura 144h + termoidraulica 24h = 99% delle 169 H29. VRP dedicato non lo sostituisce (la capienza del tag e' aggregato dinamico tra lezioni co-poste, non fatto statico per-entita che un VRP sa esprimere); il VRP calza solo sul consumatore-costruzione (pre-booking celle scarse, TASK-435/449, via separata). Doc: `claudedocs/spalmone-2026-27/task455-tag-capacity-30e-2026-07-30.md` | M-TC | M-TC | M-TC |
| M-TR | Theory subject repeated same day (TASK-468, tab "Vincoli DA ANALIZZARE": "non ci devono essere 2 ore vicine della stessa materia teorica; i laboratori, compreso disegno o informatica, possono avere piu' ore vicine"). PROTOTIPO dietro flag `planner.constraints.theory-adjacent.enabled` DEFAULT OFF (opt-in, letto LIVE per-build come M-LF/M-TC; inerte a 0 match da default). Decisione maintainer 2026-07-31: ASSOLUTAMENTE medium, non hard (il golden viola 432 giorni, AC#1 definizione "stretta", quindi hard vieterebbe il golden). Forma SOGLIA-1 teorica (M21-style ma a soglia 1; M21 ha soglia 2): penalizza `ofMedium(planner.constraints.theory-adjacent.weight, default 1)` x (ore-1) per `(gruppo, materia-teorica, giorno)` con 2+ ore. Predicato `isTheoryForRule = !isLab() && !THEORY_RULE_PRACTICAL_EXEMPT` (specchio `!isLab()` di M15/M21/S11/S16, NON requiredRoomTags come S10); set pratico-tecnico esente = Disegno/Disegno Edile/Progettazione Termoidraulica/Elettrotecnica/Metrologia e Tecnologia dei materiali/Tecnologia Meccanica/Taglio Metalli/Ra lab aperto (nomi autoritativi DB, non lo shorthand "Metrologia"/"RA" del design doc). Borderline Figurino/Personalizzazione/Tecnologia e creativita' = TEORIA di default (soggetti alla regola; flip una riga se referente dice pratici, T8). Pseudo-docente esente (M21). Composizione pulita con M15/S17/M21: M15 (20/min, forte) forza il repeat a blocco adiacente, S17 (+5 soft teoria-inclusa) resta invariata (tier diverso), M21 (soglia 2) convive. Peso INERTE nello sweep A/B (01/08): residuo 1694 (peso 1) -> 1679 (peso 1000), ~1% su 1000x peso, entrambi ~4x il golden 440. DIAGNOSI (Fedora verification, SUPERSEDE M20-dominance): i blocchi 2h teoria NASCONO in COSTRUZIONE (residuo fine construction = 1672 misurato su target/construction-lessons.txt, ~uguale al finale 1679; la LS li eredita e li smonta appena, NON li crea) e sono UNPINNED al 98,6% (NON e' la PROMOTION a pinnarli: la LS potrebbe tocarli ma non lo fa, perche' la costruzione crea un ottimo locale di fill che M32+M20+M15 preservano). Il peso e' il lever sbagliato: la cura e' a monte, nel WeeklyTemplateBuilder (dedup per-(gruppo, giorno, materia-teorica) nel placement greedy; NON un capping di length, perche' M-TR fired su same-day same-subject anche non-contiguo). PROPOSTA non prototipo (non banale: tocca placement + fill-risk + interazione M15/S17; rischia di non tenere nella LS), tracciare come task nuovo. Verdetto sweep: `task468-results/VERDICT-sweep468-2026-08-01.md`. Self-gating: residuo golden ~432 grezzo / **440 runtime** (AC#1 misurava il CSV grezzo `seed/lessons.csv`; il vincolo gira sul golden IMPORTATO, dove le 41 righe raw soggetto `RA` di G08 sono rinominate a `Recuperi ed Approfondimenti` da `GoldenScheduleImportService`, e 8 di loro formano giorni-2+ teoria. La baseline corretta per l'A/B e' 440, non 432). Alias T06 (info+gest = stessa materia) = hook inerte (`THEORY_RULE_SUBJECT_ALIAS` vuota, popolare mappa = una riga). Design: `task468-results/AC2-design-2026-07-31.md`; misura: `task468-results/AC1-residuo-golden-2026-07-31.md` | M-TR | M-TR | M-TR |
| M38 | Pair-cell deviation (TASK-576 F7-C). Penalizza `ofMedium(planner.constraints.pair-deviation.weight, default 1)` la lezione (docente reale, unpinned) la cui coppia (teacher, group) possiede celle nel template pair (PairCellGuard, PAIR-keyed `teacherId|groupId|weekday|slot`, pubblicato dalla FASE DI COSTRUZIONE col builder pair armato) quando la sua cella attuale NON e' della coppia. Doppio self-gating: senza builder pair il set e' vuoto (0 match, selezione byte-identica) e solo le coppie CON celle pagano. Pinnate/pseudo esenti (convenzione M32). NON hard: il golden tiene ~27% delle lezioni di coppie-con-forma fuori cella. **RATIFICATO DEFAULT ON a peso 1 (maintainer 2/09)**: forma opt-out standard (flag `planner.constraints.pair-deviation.enabled`, solo il letterale "false" disarma, accessor `SchoolSchedule.isPairDeviationEnabled`; banner "Pair-cell deviation M38 (TASK-576 F7-C, RATIFIED weight=1): enabled/weight/builder"). EVIDENZA: lo sweep peso (f7a/m38-checkpoint/sweep-weight) ha mostrato che il MATCH non dipende dal peso (91,3% a 500/50/1, OFF 91,0%) mentre la TASSA si': a peso 1 la direzione resta quasi gratis e i buchi interni MIGLIORANO vs OFF (58 vs 67). **IL BUILDER PAIR RESTA OPT-IN**: il flip congiunto e' stato bocciato dal guard real-seed (quotaTailMovesPlacedOn: 59 celle sotto-quota vs baseline 41 = +18 H32-family in costruzione, esattamente il filone TASK-577): il vincolo e' quindi ON ma inerte (0 match) finche' il builder non vincera' un suo gate; il banner mostra `builder=false` per renderlo leggibile. STORIA: ratificato 1/09 mattina a peso 500 su evidenza evaporata col confound jar-stantio (revert la sera), ri-ratificato 2/09 nella forma weight=1. Docs: `task576-results/f7a/` (registro completo nel task TASK-576). | M38 | M38 | M38 |
| M-CPS | Curriculum post-stage target 6h asymmetric (TASK-469, bucket C3/T4): "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 50, docente T11). **ADOPTED DEFAULT ON since 2026-08-01** (ratifica maintainer; opt-out `-Dplanner.constraints.curriculum-poststage.enabled=false`, letto LIVE per-build come M-LF via `SchoolSchedule.isCurriculumPostStageEnabled` che e' `null/absent -> true`, solo il letterale "false" disattiva; banner in `ScheduleService.refreshSolverStatics`). Penalita' ASIMMETRICA attorno al target 6h: full-weight (knob `planner.constraints.curriculum-poststage.full-weight`, default 100) sull'orario post-stage SOTTO 6 (deficit, la scuola vuole le ore), weak-weight (`...weak-weight`, default 1) oltre 6 (eccesso, la cautela G11). Perimetro = 3 coorti finali (G10 / G08 / G11, le uniche con obbligo curriculum post-stage) x materie curriculum (G10 Comportamento sociale, G08 Competenze cittadinanza+Educ civica, G11 Personalizzazione); per-group stage-end pubblicato PRE-solve da INPUT (max `DailyActivityQuota.stageHours`, NON StagePattern che copre 3/14 gruppi, NON golden). Conteggio M20-style (groupBy per gruppo, sum ore post-stage), i blocchi "2 CONSECUTIVVE" sono gestiti da M15. Evidenza A/B `task469-results/ab-cps-on-run1` (ON vs `task463-results/combined-run2` OFF): hard 0 (=baseline, e' medium), curriculum post-stage converge verso 6 (G10 12->9, G08 7->6 centra target, G11 24->21 con cautela rispettata), costo zero sui KPI di guardia (full-day +0,4pp, ricorrenza unpinned flat, buchi 0, medium neutro entro varianza). Cautela G11 by design: weak-weight non combatte il calendario del corso Personalizzazione 60h (resta ~21 vs golden 29). Doc: `task469-results/ab-cps-on-run1/VERDICT-2026-08-01.md` | M-CPS | M-CPS | M-CPS |
| M-BF | Lab-teacher day contiguity (audit-g01/blockform-623, 17/09: il passo 3 del pianificatore umano, "lab e' per docente non per materia"; la costruzione e' gia' BLOCK 91/109 per G01, l'interleaving consegnato e' EROSIONE LS cristallizzata da S13). Per (gruppo, giorno) con >=2 docenti lab REALI distinti (MONO-FRAG single-teacher ESCLUSO: pratica scuola, 22 giorni G13 nel golden), penalizza i gap-minuti per-docente non contigui: `ofMedium(planner.constraints.mbf.weight)` con `SchoolSchedule.MBF_DEFAULT_WEIGHT=20`/min gap (M15 parity, 1200/ora di buco), perimetro `isLab` (il golden pratica Figurino sparso 112x1h vs 9x2h+: FUORI scope e' golden-correct), conta i pinned (M21/M-TR style), meter unico `mbfDayGapMinutes` con `.expand` (M31 idiom) e TreeSet distinct (il gap non va mai negativo su stati H1-violanti: lezione della gate 18/09). **RATIFICATO DEFAULT ON a peso 20 (maintainer, 18/09)**, forma opt-out standard (flag `planner.constraints.mbf.enabled`, solo il letterale "false" disarma, accessor `SchoolSchedule.isMbfEnabled`; banner "M-BF lab-teacher day contiguity (RATIFIED 18/09): enabled/weight"). EVIDENZA: banco appaiato 600s su integrato-1 (CONTROL flag-OFF 54->71: la LS nuda CREA frammentazione; ARMED 54->11: netto -60 giorni, hard statico identico assert e dinamico mai peggiorato) + A/B ciclo completo appaiato (A OFF: hard -6, ricorrenza 69,8, interleaved 52; B ON: hard 0, ricorrenza 69,7, full-day 94,6, interleaved 40; criteri 4/5 PASS, (d) -12 vs -20 richiesto ATTENUATO: G13 6->1 quasi chiuso, G01 42->38 resta la coda da sweep) + gates 5 agent (zero bug correctness confermati, cure applicate) + re-cert IDENTICA al numero (bill statico -142800/79 giorni tutti multi-docente, golden 0 match: self-gating vivo). DIVISIONE M31/M-BF dichiarata nel javadoc (M31 run-count intero giorno quota-bound; M-BF buchi per-docente isLab: divergono per design su MONO-FRAG e A,A,B,B). Docs: `integrato-1/{mbf-proto,mbf-ab}/VERDICT*`. | M-BF | M-BF | M-BF |

### Soft Constraints

| ID | Semantic Name | Baseline | Shadow | Block |
|----|--------------|----------|--------|-------|
| S1 | Minimize teacher gaps | S1 | S1 | S1 |
| S3 | Teacher preference | S3 | S3 | S3 |
| ~~S5~~ | ~~Minimize student group gaps~~ | **REMOVED** (superseded by M13) | — | — |
| ~~S6~~ | ~~Lab block contiguity~~ | **REMOVED** | — | — |
| S7 | Reward consecutive student lessons | S7 | S7 | S7 |
| S8 | Isolated student lesson penalty | S8 | S8 | S8 |
| S9 | Dilute hours over year (DISABLED, task-273 - see "Course-timing constraints" below for why S12 isn't its restoration) | — | — | — |
| S10 | Discourage generic lessons in labs | S10 | S10 | S10 |
| S11 | Student group room affinity | S11 | S11 | S11 |
| S12 | Weekly subject presence (TASK-369; NOT a restoration of S9 - see "Course-timing constraints" below) | S12 | S12 | S12 |
| S13 | Weekly stability - reward unpinned lessons matching their previous-week cell (task-375) | S13 | S13 | S13 |
| S16 | Consecutive same-subject same teacher (was H16a; demoted TASK-377 - golden shows 15 deliberate teacher handoffs, e.g. RA hours) | S16 | S16 | S16 |
| S17 | Reward same-subject block pair, adjacent (group, subject, date) pairs - block-forming counterpart to M21's threshold-2 cap, see "Shape recalibration (L3, TASK-381)" below | S17 | S17 | S17 |

---

### Removed Constraints (Historical Reference)

| ID | Name | When Removed | Reason |
|----|------|--------------|--------|
| H8 | Min student group daily lessons | Pre-2024 | Replaced by H11 (max hours per day) - H8 was a hard minimum, H11 is a configurable maximum per group |
| M4 | Lab room-tag affinity | TASK-228 (2026-03) | Pre-validated by pre-assignment pipeline - lab rotations now ensure room-tag matching before solver runs |
| M17 | Lab recurring pattern (period-aware) | Unknown | Functionality moved to pre-assignment pipeline or superseded |
| M18 | Multi-day lab start-time consistency | Unknown | Superseded by other constraints or removed as redundant |
| S2 | Subject distribution | TASK-381 "L3" (2026-07-20) | Golden-first: the golden violates it 13,259 times (-26,518 soft) - purpose superseded by H15 (adjacency forced hard, task-349) + M21 (volume capped, threshold-2 since this task); kept in place it only penalized the blocks the school actually builds |
| S4 | No consecutive same-subject | Never active in this branch family (docs/code cleanup TASK-381 "L3") | Existed only as a stale table row and dead `ConstraintMessageFormatter` case/message key - no `.asConstraint("S4...")` was ever registered in `SchoolScheduleConstraintProvider`; cleaned up alongside the S2 removal |
| S5 | Minimize student group gaps | Unknown | Superseded by H13 (vertically compact) - H13 provides better vertical compaction |
| S6 | Lab block contiguity | TASK-183 (shadow/block entities) | Lab blocks are now pinned Lessons that maintain contiguity through entity structure |
| ~~M13~~ | ~~Vertically compact~~ | TASK-227 (2026-03-15) | **Promoted to H17 (hard tier)** - M13 (medium) wasn't strong enough to enforce compaction. Now H17 (hard) forces zero gaps in student schedules to match human scheduler behavior |

---

### Branch Families

See `claudedocs/branch-families.md` for the full branch family documentation including solver config variants.

- **Baseline** (identical constraint provider): `master`, `feature/cartesian-move-selector`, `feature/room-phase`, `feature/student-compactness`
- **Shadow** (identical constraint provider): `feature/shadow-lab-blocks`, `feature/cartesian-shadow-lab-blocks`, `feature/room-phase-shadow-lab-blocks`
- **Block Entity** (identical constraint provider): `feature/block-entity`, `feature/cartesian-block-entity`, `feature/room-phase-block-entity`

## Testing
**Use the wrapper script**: `./scripts/core/test.sh` (runs tests with proper environment setup;
`-mva "<args>"` e' il passthrough di argomenti Maven aggiuntivi, es. `-mva "-Pbyteman"`)

Legacy: `mvn test` (constraint verifier tests with H2, no external DB required)

### Race-injection tests con Byteman (TASK-543, 2026-08-23)
Il profilo maven `byteman` carica l'agente Byteman 4.0.27 (verificato su JDK 21) nei surefire:
`./scripts/core/test.sh -mva "-Pbyteman" -c BytemanRaceInjectionTest` (senza profilo il test e'
skipped, suite identica). Banco: `src/test/java/com/school/planner/solver/raceinjection/` + regole
in `src/test/resources/byteman/race-force.btm`. Guida completa per agenti (pattern freeze+spin,
trappole BYTEMAN-38, builtin verificati): `claudedocs/byteman-guide-for-agents.md`. Stato
determinismo MT-LS / single-writer: task TASK-543 nel backlog.

## Design Decisions

Per ogni voce: **stato corrente** (il fatto operativo che serve senza aprire altri file) e
puntatore alla storia integrale, spostata VERBATIM in
`claudedocs/constraint-recalibration-history.md` (TASK-431, nessun contenuto perso).
I titoli qui sotto sono identici a quelli dell'archivio, quindi un riferimento del tipo
`see "H15->M15 demotion" below` risolve su entrambi.

### Sparse-day horizontal compaction (M20, task-230, 2026-03-14)
M20 e' oggi il deficit di riempimento del giorno (medium 1000/ora mancante rispetto a
`dailyQuota`, pinned-exempt), non piu' la penalita' originale "giorno con 1-3 ore"; il
target e' fact-based dal TASK-385 e netto dei pin autoritativi dal TASK-424. Tier medium
per scelta: e' un vincolo "logicamente hard" della scuola, tenuto medium per non
irrigidire lo spazio di ricerca.
Storia: `claudedocs/constraint-recalibration-history.md#sparse-day-horizontal-compaction-m20-task-230-2026-03-14`

### Afternoon slots reserved for stage (M28, ex H21, task-372/379, 2026-07-18)
M28 penalizza 100/lezione ogni lezione unpinned di docente reale collocata in
`slotIndex >= TimeslotSequence.AFTERNOON_START_SLOT_INDEX` (6); pinned e pseudo-docente
(Stage) esenti, nessuna esclusione VRP. `GroupCalendarRule`/H20 lascia gli slot 6-7 APERTI
per 9 gruppi: quell'apertura e' capacita' per lo stage, non un invito alle lezioni
ordinarie.
Storia: `claudedocs/constraint-recalibration-history.md#afternoon-slots-reserved-for-stage-m28-ex-h21-task-372379-2026-07-18`

### Golden-first recalibration (TASK-379, 2026-07-18)
Direttiva permanente: la scuola considera VALIDO l'orario golden, quindi nessuna nostra
regola puo' essere piu' rigida della pratica che il golden incarna, a prescindere dalle
note verbali (docx/Q17); le discrepanze si segnalano alla scuola ma non bloccano il
lavoro. Da qui H21 -> M28, H22 -> M29 e il cap "Orario wins" dell'import calendario
(`modaSkippedBeyondCourseHours`).

ESTENSIONE AGLI INPUT (maintainer, 2026-07-31 pomeriggio): il golden e' autoritativo anche sui
NUMERI. Dove un dato di input (incarichi, turni, fogli) diverge dal golden, si adotta il
valore estratto dal golden e la divergenza si registra nel doc di notifica per referente
(validazione a posteriori: puo' essere un vincolo che ci manca o un errore umano genuino,
in entrambi i casi non blocca). Obiettivo dichiarato: replicare soluzioni valide come il
golden; il golden sparira' (direttiva solo-input), ma finche' c'e' guida la modellazione e
la normalizzazione delle discrepanze. Lavoro tracciato in TASK-466 (censimento divergenze +
normalizzazione seed + doc referente); trappole note del golden (sabati/Fasullo, Stage
off-grid) escluse per protocollo, vedi la sezione KPI.
Storia: `claudedocs/constraint-recalibration-history.md#golden-first-recalibration-task-379-2026-07-18`

### Weekly choice-day constraints (M24/M25, TASK-368, 2026-07-18)
M24: al piu' `Teacher.maxEarlyStartDaysPerWeek` giorni/settimana con lezione allo slot 0
(T11=1). M25: almeno `Teacher.requiredFreeDaysPerWeek` giorni liberi Lun-Ven (T05=2,
non l'1 letterale del docx, che il venerdi' gia' in VACATION renderebbe vacuo). Entrambi
medium e raggruppati per (docente, settimana ISO); il golden stesso li viola (M24 -16,
M25 -27), quindi sono residui calibrati, non zero-tolerance.
Storia: `claudedocs/constraint-recalibration-history.md#weekly-choice-day-constraints-m24m25-task-368-2026-07-18`

### M21 recalibration (task-365, 2026-07-18)
M21 penalizza `ONE_MEDIUM x (ore - 2)` per (gruppo, materia, giorno) oltre le 2 ore
(soglia alzata da 1 a 2 dal TASK-381 "L3"). Tre esenzioni prima del conteggio:
`Lesson.isLab()`, la whitelist `M21_EXEMPT_SUBJECTS` (Competenze Digitali, Disegno,
Disegno Edile ed Impiantistico) e le lezioni di pseudo-docente. NON e' pinned-exempt: e'
proprio la verifica della pratica del golden.
Storia: `claudedocs/constraint-recalibration-history.md#m21-recalibration-task-365-2026-07-18`

### H15/H16 recalibration (task-349, 2026-07-18)
Superata su entrambi i fronti: H15 e' oggi M15 (TASK-392) e H16a e' oggi S16 (TASK-377).
Resta vivo il meccanismo che questa sezione introdusse: l'esenzione BREAK-LEVEL (un gap e'
scusato solo se le lezioni ai due lati sono ENTRAMBE pinnate), usata oggi da M15 e H17.
Storia: `claudedocs/constraint-recalibration-history.md#h15h16-recalibration-task-349-2026-07-18`

### H15->M15 demotion (TASK-392, 2026-07-22) — SUPERSEDES the task-349 "keep it hard" decision above
M15 = `ofMedium(20)` x minuti di gap sul same-day same-subject non consecutivo,
break-level pinned-exempt. Il peso NON e' `ONE_MEDIUM`: a 20/minuto uno split da 1h costa
circa 1200, in famiglia con M20/M22 (1000/giorno), altrimenti il solver sparpaglia le
ripetizioni per riempire i giorni. Il veto hard "H15" della CSP di pre-assegnazione
(`PreAssignmentValidator`/`HybridPhase1aService`) e' un meccanismo distinto e NON e' stato
toccato dalla demozione.
Storia: `claudedocs/constraint-recalibration-history.md#h15-m15-demotion-task-392-2026-07-22-supersedes-the-task-349-keep-it-hard-decision-above`

### H17 afternoon-contiguity + M18 gate recalibration (task-350, 2026-07-18)
H17 e' hard con esenzione break-level (gap scusato solo tra due pinnate) e la drill-down
UI (`ScheduleService.addH17Violations`) conta i gap con la stessa semantica canonica
(`TimeslotSequence.slotIndexOf`), quindi non puo' divergere dallo score. Il gate di M18 e'
per (gruppo, data) via `SchoolSchedule.getGroupCalendarWindow`, mai il campo statico
`studentGroup.maxHoursPerDay` (dal TASK-405 M18 e' comunque esteso a ogni giorno con
gradiente `ofMedium(50)` x offset).
Storia: `claudedocs/constraint-recalibration-history.md#h17-afternoon-contiguity-m18-gate-recalibration-task-350-2026-07-18`

### Course-timing constraints (task-369, 2026-07-18)
H30 (ex M26, promosso hard maintainer 2026-08-15: golden 6/6 gruppi zero violazioni, TASK-472): ogni lezione di Sicurezza deve stare prima della `StagePattern.startDate` del gruppo
(costante `BaseConstraintProvider.SICUREZZA_SUBJECT`, il giorno di inizio stesso e' gia'
violazione). M27: finestra sulla settimana della PRIMA lezione per le materie T20
(Merceologia e Arredi Tessili entro la settimana 3, Tecnologia tessile e Storia della moda
non prima della 4). S12: `ONE_SOFT` di premio per ogni (gruppo, materia, settimana ISO)
con almeno un'ora, cioe' un LOWER bound di continuita', NON il ripristino di S9.
Storia: `claudedocs/constraint-recalibration-history.md#course-timing-constraints-task-369-2026-07-18`

### Professionalizzanti at day edges (H22, task-370, 2026-07-18)
La regola vive oggi come M29 (demoted TASK-379). Tabella group-generic
`M29_RESTRICTED_DAYS_BY_GROUP` (una riga: G12 -> mercoledi', giovedi'): in quei giorni
le professionalizzanti devono formare un'unica sequenza contigua di al massimo 2 ore che
tocca un bordo della finestra di calendario del gruppo. I lab sono esclusi; l'esenzione e'
DAY-LEVEL (giorno esente solo se TUTTE le professionalizzanti di quel giorno sono pinnate).
Storia: `claudedocs/constraint-recalibration-history.md#professionalizzanti-at-day-edges-h22-task-370-2026-07-18`

### TASK-377 unblinding (2026-07-18): H1/H3/H16a were inert during timeslot phases
Trappola permanente da ricordare scrivendo vincoli: `forEach(Lesson.class)` itera solo le
entita' COMPLETAMENTE inizializzate, quindi un vincolo costruito cosi' e' INERTE in tutte
le fasi che non hanno ancora assegnato la stanza (H1/H3/H16a erano ciechi: 6.343 lezioni
impilate con H3=0). Oggi usano `forEachIncludingUnassigned` piu' un filtro esplicito sul
timeslot; H2/H16b restano su `forEach` legittimamente (senza stanze non c'e' conflitto).
Storia: `claudedocs/constraint-recalibration-history.md#task-377-unblinding-2026-07-18-h1h3h16a-were-inert-during-timeslot-phases`

### Weekly stability soft constraint (S13, task-375, 2026-07-18)
S13 premia `ofSoft(2)` la lezione unpinned la cui cella (gruppo, weekday, slotIndex)
coincide con quella della settimana PRECEDENTE su (docente, materia). Join direzionale per
costruzione (mai simmetrico, altrimenti una ricorrenza verrebbe contata due volte), lato
premiato non pinnato, lato ancora anche pinnato, pseudo-docenti esclusi. Resta una MISURA
della ricorrenza: la protezione del template e' M32.
Storia: `claudedocs/constraint-recalibration-history.md#weekly-stability-soft-constraint-s13-task-375-2026-07-18`

### Daily activity quotas (L1-bis, TASK-381, 2026-07-20)
`DailyActivityQuota` (righe fini di `calendario_fc.csv` per G04/G08/G13) e' una
restrizione AUTORITATIVA dello spazio di ricerca col pattern di casa VRP+constraint: il
filtro di `Lesson.getAvailableTimeslots()` piu' i gemelli hard H23/H24 (pinned-exempt),
con M30.1/M30.2 (riempimento del giorno lab, conta anche i pin) e M31 (unica sequenza
contigua, day-level pinned-exempt). Le materie legate alla quota lab stanno in un solo
posto: `DailyActivityQuota.QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP`. La stessa sezione copre lo
**Stage pinning** ("stage on the grid": `planStagePins`, le ore off-grid di
chiusure/sabati contabilizzate in `Course.expectedUnscheduledHours`) e la **Stage
confinement (H25, TASK-384)**: `Lesson.isStageConfined()` + `DailyActivityQuota.isStageDate`
confinano ogni stub Stage unpinned alle date con `stageHours > 0`, filtro VRP piu' H25
pinned-exempt come rete di sicurezza.
Storia: `claudedocs/constraint-recalibration-history.md#daily-activity-quotas-l1-bis-task-381-2026-07-20`

### Weekly template construction (L2, TASK-381, 2026-07-20)
La fase 1 (`DayClusteredConstructionPhase`) costruisce PRIMA un template settimanale per
gruppo (`WeeklyTemplateBuilder`/`WeeklyTemplatePlanner`, Java puro senza accoppiamento
Timefold, quindi unit-testabile) e lo stampa cronologicamente su tutte le date compatibili;
il round-robin storico e' rimasto solo come coda di riparazione. Lo stamp puo' solo
DECLINARE una cella, mai forzarla: per questo `stampSkipConflict` e' 0 per costruzione ed
e' assertito da `WeeklyTemplateConstructionRealSeedTest` (ricorrenza a fine CH ~76-77%).
Storia: `claudedocs/constraint-recalibration-history.md#weekly-template-construction-l2-task-381-2026-07-20`

### Shape recalibration (L3, TASK-381, 2026-07-20)
Soglia M21 portata a 2h, S2 rimosso (vedi tabella Removed Constraints), NUOVO S17 che
premia `ofSoft(5)` ogni coppia adiacente (gruppo, materia, data), LAB INCLUSI a differenza
di M21/S16. Le fasi LS portano anche `unimprovedSecondsSpentLimit` come complemento
ASSOLUTO di `diminishedReturns`, che e' relativo e degenera sui re-solve di uno schedule
gia' convergente.
Storia: `claudedocs/constraint-recalibration-history.md#shape-recalibration-l3-task-381-2026-07-20`

### Universal expected-hours facts (TASK-385, 2026-07-21)
`DailyActivityQuota` copre TUTTI e 14 i gruppi: il flag `FCQuota` distingue le righe di
`calendario_fc.csv` (true, i 3 gruppi FC, split aula/lab/stage) da quelle di
`calendario_class_days.csv` (false, gli altri 11, una sola colonna per data secondo la
modalita' A/L/S). `SchoolSchedule.getExpectedHours(groupId, date)` e' l'accessore
universale (aula+lab+stage, -1 se assente), mentre i consumatori FC-specifici (filtro VRP,
H23/H24, M30, M31) restano filtrati su `FCQuota == true`. Il target di riempimento (M20 e
costruzione) usa la base aula+lab, senza stage.
Storia: `claudedocs/constraint-recalibration-history.md#universal-expected-hours-facts-task-385-2026-07-21`

### Template deviation graduato per scarsità (M32, TASK-413, 2026-07-24)
M32 penalizza a tier medium la lezione unpinned di docente reale la cui cella (weekday,
slotIndex) non e' una cella template del suo corso; cambiare DATA a parita' di cella NON e'
deviazione (la cadenza e' preservata). Peso graduato per scarsita':
`clamp(numeratore / |celle VRP del corso|, min, max)`, default 100000/20/500, sovrascrivibili
con `-Dplanner.m32.numerator|min|max` SOLO sulla riga di comando `java -jar` (vedi la
sezione MAVEN_OPTS sopra). La mappa e' pubblicata PRE-solve da
`WeeklyTemplatePlanner.buildReference` dentro `ScheduleService.refreshSolverStatics`, mai a
meta' solve (score corruption). Adottato dopo l'A/B val413 (maintainer 2026-07-25).
Storia: `claudedocs/constraint-recalibration-history.md#template-deviation-graduato-per-scarsità-m32-task-413-2026-07-24`

### No-session days (H26, TASK-408, 2026-07-25)
Per un gruppo COPERTO dai fogli, una data scolastica SENZA riga `DailyActivityQuota`
significa "gruppo non in sessione": predicato unico `SchoolSchedule.isNoSessionDate(groupId,
date)`, consumato dal filtro VRP, da H26 (hard, pinned-exempt, pseudo-esente), da
`dailyQuota()` che li' ritorna 0 senza mai ricadere sul fallback, e da `expectedHoursCapFor`
in costruzione. I giorni di stage HANNO la riga (`stageHours > 0`) e non sono mai
no-session; i giorni off-grid non hanno `Timeslot` e non arrivano al VRP.
Storia: `claudedocs/constraint-recalibration-history.md#no-session-days-h26-task-408-2026-07-25`

### Target netto dei pin autoritativi (M20/M20.2/M33, TASK-424, 2026-07-27)
`SchoolScheduleConstraintProvider.effectiveQuota(group, date) = max(0, dailyQuota - ore
pinnate autoritative di docente reale)` e' l'UNICA sorgente del target per M20, M20.2, M33 e
per i pricer condivisi `m20DayCost`/`m20MarginalFillGain`/`m20MarginalRemovalCost` (quindi
BoxRepair, HoleRepair e SurplusRebalance la ereditano). Le ore pinnate stanno in una static
map pubblicata pre-solve insieme a `setPinnedLessonTimeslotsByGroupStatic`. NON si nettano i
pin PROMOSSI (TASK-419, restano occupancy ordinaria) ne' quelli pseudo-docente Stage; il gate
di AMBITO di M33 resta sulla quota grezza.
Storia: `claudedocs/constraint-recalibration-history.md#target-netto-dei-pin-autoritativi-m20m202m33-task-424-2026-07-27`

### ~~Student group gap avoidance~~ (S5, REMOVED)
Rimosso: superato da M13 e poi da H17 (compattezza verticale al tier hard). L'archivio
conserva il benchmark hard-vs-soft che porto' alla scelta.
Storia: `claudedocs/constraint-recalibration-history.md#student-group-gap-avoidance-s5-removed`

### Lab room assignment (H-M2 + H-M2b)
H-M2 (hard) impone alle materie di laboratorio una stanza col tag corrispondente; H-M2b
copre il verso opposto (le stanze-lab ospitano solo corsi che le richiedono). Assunzione
ancora da confermare con la scuola: Disegno meccanico (tag meccanica) e Disegno edile ed
impiantistico (tag informatica) sono trattati come lab.
Storia: `claudedocs/constraint-recalibration-history.md#lab-room-assignment-h-m2-h-m2b`

### Informatica after stage (M5, task-015)
M5 premia (+1 medium) ogni lezione di Informatica successiva alla `StagePattern.endDate`
del gruppo, per i gruppi MODA/FA (ore di tesina): preferenza, non requisito, quindi
nessuna penalita' per le ore durante o prima dello stage.
Storia: `claudedocs/constraint-recalibration-history.md#informatica-after-stage-m5-task-015`

### ~~Lab recurring pattern~~ (M17, task-113, REMOVED)
Rimosso: la ricorrenza dei lab e' garantita oggi dai pin della pre-assegnazione e dal
template settimanale della costruzione (L2), non piu' da un vincolo dedicato.
Storia: `claudedocs/constraint-recalibration-history.md#lab-recurring-pattern-m17-task-113-removed`

## Development Workflow Lessons

### Frontend changes require rebuild + restart
Quinoa bakes the React frontend into the JAR at `mvn package` time. The running Quarkus process serves the old JS bundle until you:
1. `./planner.sh kill-app` (stop the running app)
2. `mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true` (rebuild JAR with new frontend)
3. `./planner.sh run` (restart app from new JAR)

**IMPORTANT**: Use `mvn clean package` (not just `mvn package`) to ensure Vite picks up all changes. Without `clean`, Vite's incremental build may not detect all file changes.

Simply restarting with `./planner.sh run` does NOT rebuild — it re-runs the existing JAR via `mvn quarkus:run`.

**Helper script**: Use `./scripts/core/rebuild.sh` for safe frontend rebuilds (handles kill → build → restart → wait automatically):
```bash
./scripts/core/rebuild.sh   # Kills app, clean builds (backend + UI), restarts, waits
```

### Browser caching of frontend assets
After a rebuild+restart, the browser may still serve the old cached JS bundle. Always hard-refresh (`Ctrl+Shift+R` or `location.reload(true)`) when verifying frontend changes. The Vite-generated filename hash (e.g., `index-CNAH8nXT.js`) only changes if the content changes, but the browser may cache aggressively.

### UI file paths - ALWAYS use correct paths
The frontend source is at `src/main/webui/src/`. When creating UI files, use correct paths:
- Components: `src/main/webui/src/components/`
- Hooks: `src/main/webui/src/hooks/`
- API client: `src/main/webui/src/api/`
- Static data: `src/main/webui/src/data/`
- i18n locales: `src/main/webui/src/i18n/locales/`

**When using subagents for UI work**: Specify full absolute paths explicitly. After file creation, verify with `git status` to confirm files are in the right location. Files in wrong directories will compile but won't be integrated properly.

### Full fresh deploy sequence
When you need a clean slate (new seed data + rebuilt frontend):
```bash
./planner.sh kill-app
./planner.sh stop
yes | ./planner.sh delete <profile>
./scripts/core/rebuild-checked.sh
# App is already started and ready
```

### ImportService deletes LabRotations — requires full reset after import
**WARNING:** `ImportService` deletes ALL `LabRotation` records during import (comment: "will be re-seeded by DataGenerator"). But DataGenerator only runs when `Timeslot.count() == 0`. After any import, timeslots exist → DataGenerator skips → LabRotations are permanently gone. This causes pre-assignment to fail with confusing "INFEASIBLE" errors.

**Fix:** After any import/export, either:
1. Reset the profile: `yes | ./planner.sh delete <profile>` and restart, or
2. Manually re-seed LabRotations: `./scripts/db/db-query.sh -w "INSERT INTO labrotation ..."`

### Server startup in scripts — use dev-start-and-wait.sh
**ALWAYS use `./scripts/core/dev-start-and-wait.sh dev`** for automated startup. Hooks block `planner.sh run` and `planner.sh start` with background `&`. The `dev-start-and-wait.sh` script handles kill → build → restart → wait internally.

Never call `start` or `run` synchronously from a script — they block forever. See `scripts/benchmark/benchmark-branch.sh` for a working example that uses `dev-start-and-wait.sh`.

### Solver configuration
**IMPORTANT:** Modifying `solverConfig.xml` has cascading effects. See `.claude/rules/solver-config-sync.md` for the full list of files that must be updated together.

- The solver uses a **5-phase pipeline** (TASK-420): custom CH lesson placement → LS Compactness → deterministic Repair + Compaction → CH room assignment → LS room refinement. See `claudedocs/solver-phases.md` for full documentation.
- Without a `<localSearch>` phase in `solverConfig.xml`, Timefold stops after the construction heuristic completes (seconds). Always include a local search phase for real optimization.
- The `<unimprovedSecondsSpentLimit>` controls auto-termination — solver keeps running until N seconds pass with no score improvement.
- **XSD element ordering**: Within `<termination>`, `secondsSpentLimit` must come before `unimprovedSecondsSpentLimit` (enforced by Timefold XSD schema).
- **After modifying any solver XML config** (`solverConfig.xml`, `preAssignmentSolverConfig.xml`), always validate against the Timefold XSD: `bash scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml`

### Screenshot and image file management
**NEVER save screenshots or PNG files in the repository root.** They accumulate and clutter the codebase.

Use the appropriate folder based on purpose:

| Purpose | Folder | Example |
|---------|--------|---------|
| Documentation (README, user-facing) | `docs/screenshots/` | `docs/screenshots/09-feature-x.png` |
| Development notes, architecture docs | `claudedocs/screenshots/` | `claudedocs/screenshots/ui-state.png` |
| Playwright e2e test artifacts | `src/main/webui/e2e/screenshots/` | `e2e/screenshots/test-failure.png` |
| Temporary verification (delete after use) | `/tmp/` | `/tmp/verify-fix.png` |

**For Playwright tests**: Configure screenshots to go to `e2e/screenshots/` or use `/tmp/` for one-off verifications. Clean up test artifacts after debugging is complete.

## Serena MCP Integration

Serena MCP provides semantic code analysis tools (symbol search, references, etc.) for more efficient codebase navigation.

**Project name**: `planner`

**Activation**: Before using Serena tools, activate the project:
- The project is registered in `~/.serena/serena_config.yml`
- Configuration: `.serena/project.yml` (languages: typescript for Java/JS analysis)

**Best practices**:
- Use `find_symbol` and `get_symbols_overview` instead of reading entire files
- Use `search_for_pattern` for non-code files or unknown symbol paths
- Check `check_onboarding_performed` at session start to see if memories need loading
- Memories store project-specific knowledge (commands, conventions, checklists)

## Backlog Task Management

The workflow is the Backlog.md MCP one described at the bottom of this file (resource
`backlog://workflow/overview`, or the `backlog.get_workflow_overview()` tool). Project
settings, including the inherited Definition of Done, live in `backlog/config.yml`; tasks,
milestones and completed work live under `backlog/`. (There is no
`claudedocs/backlog-workflow.md`: the reference was dead, removed by TASK-431.)

### Claude-Enhanced Task Labeling

When Claude creates **detailed planning and definition** for a task, add the label `claude-planned`. This distinguishes AI-enhanced tasks from terse user-created ones.

**Add the label when Claude provides:**
- Detailed acceptance criteria (3+ specific items)
- Implementation notes or technical approach
- Edge case analysis
- Structured work breakdown

**Query:** `task_list(labels=["claude-planned"])` or search for "claude-planned"

<!-- BACKLOG.MD MCP GUIDELINES START -->

<CRITICAL_INSTRUCTION>

## BACKLOG WORKFLOW INSTRUCTIONS

This project uses Backlog.md MCP for all task and project management activities.

**CRITICAL GUIDANCE**

- If your client supports MCP resources, read `backlog://workflow/overview` to understand when and how to use Backlog for this project.
- If your client only supports tools or the above request fails, call `backlog.get_workflow_overview()` tool to load the tool-oriented overview (it lists the matching guide tools).

- **First time working here?** Read the overview resource IMMEDIATELY to learn the workflow
- **Already familiar?** You should have the overview cached ("## Backlog.md Overview (MCP)")
- **When to read it**: BEFORE creating tasks, or when you're unsure whether to track work

These guides cover:
- Decision framework for when to create tasks
- Search-first workflow to avoid duplicates
- Links to detailed guides for task creation, execution, and finalization
- MCP tools reference

You MUST read the overview resource to understand the complete workflow. The information is NOT summarized here.

</CRITICAL_INSTRUCTION>

<!-- BACKLOG.MD MCP GUIDELINES END -->

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
