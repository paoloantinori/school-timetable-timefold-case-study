# VERDICT TASK-480 Fase 4 - multi-start A/B (source=input, regime bad-seed H17). 2026-08-02.

> Verdetto unico di chiusura TASK-480: (a) gate di indipendenza come PREMESSA, (b) A/B N-processi
> come ground truth [P(>=1 0-hard) + wall-clock], (c) raccomandazione + pacchetto operativo +
> verdetto comparativo multi-start vs ExactFinisher (AC#3). Numeri dai raw (score.txt /
> analysis.json / kpi-holes.txt / phases.json), non dal SUMMARY auto-generato (che aveva un bug di
> conteggio, vedi §3.2).

## TL;DR

- **PREMESSA (gate di indipendenza): DIVERSIFICATO.** La cella critica @10 del mercoledi (G11) e'
  tie-break-sensibile, NON constraint-forced: 5/5 teacher-multisets distinti, 13/14 gruppi variano
  @10 su 5 costruzioni GRASP. Senza questo l'A/B non avrebbe premessa.
- **A/B (ground truth, source=input, n=3 GRASP + baseline):** **2/3 GRASP seeds a 0-hard**
  (grasp-2, grasp-3); grasp-seed-1 nel bacino bad-seed (-11hard, H17 -10/10match). Baseline GRASP-off
  0-hard. **p_hat(good) = 0,667, Wilson 95% CI [0,21; 0,94].**
- **P(>=1 0-hard | N=3 GRASP) = 0,96 (point), CI [0,50; 1,00].** Coerente col prior teorico del
  research (p~0,6 -> ~0,95).
- **Varianza reale misurata:** un run e' -11, due sono 0. Il bacino bad-seed H17 e' raggiungibile da
  una costruzione GRASP (fenotipo G11-mercoledi' di TASK-481, slot migrato @10 -> @11). La
  stochasticita' che il multi-start sfrutta e' genuina, non ipotetica: **questo NON e' il caso
  "tutti 0-hard, H17 non stressata"** - grasp-seed-1 l'ha stressata e il pick-best l'ha recuperata.
- **pick-best winner:** baseline-grasp-off (0hard / -1029611medium, il medium migliore tra gli 0-hard).
- **LIMITAZIONE onesta:** n=3 non dimensiona N in modo stretto (CI larga). La frequenza del bad-seed
  va stimata con ~10-20 run in background (NON bloccante).
- **Raccomandazione:** multi-start adottato come PROCEDURA operativa (NON flip di default), per il
  SOLO path source=input. ExactFinisher resta DEFAULT ON (i due compongono, AC#3). Piano B
  strutturale = TASK-482 Kempe-chain, aprirlo solo se la stima differita mostra frequenza bad-seed alta.

## 1. PREMESSA - gate di indipendenza (DIVERSIFIED, costo zero solve)

L'A/B multi-start ha senso SOLO se diversificare la costruzione cambia il bacino che la LS eredita.
Il gate (research finding 4a) lo misura a costo zero solve (~12s, template sul seed reale, no LS):
randomizzando i tie-break della costruzione (GRASP-RCL su `startOffset` weekday +
`nextContiguousSlot`), il profilo di saturazione @10 del mercoledi cambia tra N=5 costruzioni?

| metrica | valore |
|---|---|
| teacher-multisets @10 (Wed) distinti su 5 seed | **5/5** |
| gruppi che variano il docente @10 | **13/14** (G14 sempre `<gap>`) |
| G11: docenti liberi @10 per seed | seed=1 liberi 4, seed=4 liberi 5 (sottoinsiemi diversi) |

**Verdetto gate: la cella @10 e' tie-break-sensibile, NON constraint-forced. PROCEED al multi-start.**
Baseline GRASP-off riproducibile (asserzione `baseline1==baseline2` verificata). Caveat di fedelta':
il gate misura il template sul seed INPUT (no pin golden); i pin golden aggiungono vincoli quindi la
diversificazione sara' <=, ma la cella NON e' forced neanche con meno vincoli. L'A/B e' la ground
truth. Doc integrale: `task480-results/VERDICT-gate-indipendenza-2026-08-02.md`.

## 2. A/B - metodo (ground truth su bad-seed reale)

**Domanda:** N staged solve con N seed GRASP indipendenti (source=input, regime bad-seed) danno
P(almeno un run 0-hard) > 0, dove un single-run talvolta becca H17=-6/-10?

**Setup (`scripts/benchmark/multistart-ab.sh`, sequenziale su un profilo):** profilo `multistart`
preparato una volta (rebuild JAR + fresh seed + import-calendario + pre-assign source=input). Il `-D`
GRASP e' LIVE sulla riga `java -jar` (CLAUDE.md MAVEN_OPTS lesson), quindi ogni seed richiede boot
fresco. Per ogni run: `PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input
-Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=$i"` -> `planner.sh prod`
-> `api.sh solve-staged` -> `capture-run.sh`. 4 run: baseline GRASP-off (controllo) + grasp-seed-1/2/3.

**Perche' sequenziale su un profilo (non parallelo, non in-process):** i 23 campi `static volatile`
di `SchoolSchedule` vietano il parallelismo in-JVM (corruzione silente dello score tra candidati); la
regola "single DB actor" vieta N processi sullo stesso profilo Postgres. Quindi sequenziale. Il
parallelismo N-container (una JVM + un profilo per container) e' lo speedup da costruire SOLO se
l'A/B valida (vedi §6). Evidenza: `task480-recon-timefold-multistart-2026-08-02.md`.

## 3. A/B - risultati (dai raw, score.txt + analysis.json + phases.json)

| run | opts | score | wall | 0-hard? | H17 |
|---|---|---|---|---|---|
| baseline-grasp-off | source=input (GRASP off) | **0hard/-1029611medium/6433soft** | 1848s | YES | 0 |
| grasp-seed-1 | + GRASP seed=1 | **-11hard/-1173327medium/-27130soft** | 2486s | no | **-10 (10 match)** + H29 -1 |
| grasp-seed-2 | + GRASP seed=2 | **0hard/-1308087medium/-28198soft** | 2544s | YES | 0 |
| grasp-seed-3 | + GRASP seed=3 | **0hard/-1297715medium/-24870soft** | 1602s | YES | 0 |

**Totale wall-clock: 8480s = 141,3 min (~2,35h sequenziale).** Tutti i run hanno le 5 fasi complete
(`phases.json` last phase = LS-Room Refinement, `isComplete:true`) -> risultati convergi, non troncati.
L'anti-bug PASS: i 4 `lessons.txt` hanno md5 tutti distinti -> il flag GRASP e' giunto al `java -jar`
per ogni seed e ha variato la costruzione (iniezione `startOffset` + `nextContiguousSlot` in
`WeeklyTemplateBuilder`). Il verdetto si fonda su run davvero indipendenti.

### 3.1 grasp-seed-1 -11hard = bacino bad-seed, non artefatto (verifica raw)

`analysis.json`: `H17 -10hard/10match` + `H29 -1hard/1match` = -11hard. Nessun altro hard residuo
(H0 boxed = 0 via ExactFinisher, com'era atteso: EF ortogonale a H17). `kpi-holes.txt`: i 10 buchi
H17 sono **tutti lo stesso pattern** - G11, mercoledi, 2026-09-16 .. 2026-11-18 (10 mercoledi
consecutivi), giorno packato `[8,9,10,_,12,13]`, buco alle ore 11.

**Confronto raw G11 mercoledi 2026-09-16 (lessons.txt):**
- baseline (0-hard): T08@8-9-10 (Matematica 3h), T06@11 (Informatica), T23@12 ->
  compatto [8-12], nessun buco.
- grasp-seed-1 (-11-hard): T08@8-9, T11@10 (Personalizzazione), **[BUCO ore 11]**,
  T03@12 (Orientamento), T04@13.

**Riconciliazione slot (sfumatura onesta, non overclaim):** la diagnose TASK-481 documentava il
bad-seed con buco **@10 (slot 2)** e il gate studiava la cella @10. grasp-seed-1 ha il buco a
**@11 (slot 3)** - slot adiacente. Stesso fenotipo (G11 mercoledi, gap mid-day in giorno packato,
cadenza settimanale), slot migrato di uno. Coerente col finding shell-game TASK-481 ("il buco migra"),
NON riproduzione byte-exact della cella @10 del gate. Il gate prova che il bacino @10 e'
tie-break-sensibile; grasp-seed-1 mostra che il bad-basin puo' inghiottire anche lo slot adiacente
@11. Il bacino e' il fenomeno, non una cella fissa.

### 3.2 Bug di conteggio nel SUMMARY auto-generato (verdetto dai raw, non dal summary)

Il `SUMMARY.txt` prodotto dallo script riporta "0-hard GRASP runs: 3 / 3". E' un **bug di conteggio**:
il contatore `zero_hard` includeva il run baseline-grasp-off (controllo GRASP-off, non un seed GRASP)
ma il denominatore era N (solo GRASP). Raw truth: **2/3 GRASP seeds 0-hard, 3/4 run totali 0-hard.**
Il verdetto qualitativo dello script (P(>=1 0-hard) > 0, MULTI-START WORKS) e' corretto; solo
l'etichetta del conteggio era sbagliata. **Script corretto** (baseline contato separatamente + riga
pick-best winner aggiunta); **correzione appesa in fondo al SUMMARY** (raw preservato, bug surface,
non sovrascitto). Questo verdetto usa i raw, non il SUMMARY.

## 4. P(>=1 0-hard) - analisi statistica (dai raw)

**Sui 3 seed GRASP:** k=2 good su n=3. **p_hat(good) = 0,667, Wilson 95% CI [0,208; 0,939].**
**P(>=1 0-hard | N=3 GRASP) = 1-(1-p)^3 = 0,963 (point), CI [0,503; 1,000].** Coerente col prior
teorico del research (p~0,6 da 8 run misti -> ~0,95): l'A/B su campione omogeneo conferma l'ordine
di grandezza atteso.

**Sul pool {baseline + 3 GRASP}:** 3/4 run a 0-hard. P(>=1 0-hard nel pool) = 1 su questo campione.
pick-best su (hard asc, medium asc) -> **winner: baseline-grasp-off** (0hard, -1029611medium, il
medium migliore tra gli 0-hard).

### 4.1 LIMITAZIONE onesta (dichiarata)

n=3 GRASP e' troppo piccolo per stimare p in modo stretto: la CI su 3 prove Bernoulli e' larga
([0,21; 0,94] su p; [0,50; 1,00] su P(>=1 0-hard | N=3)). **Non si puo' dimensionare N per un target
P(>=1 0-hard) da n=3 soli.** La frequenza del bad-seed e' la grandezza che manca.

**Misura successiva (NON bloccante, in background nei tempi morti):** stimare la frequenza bad-seed
con ~10-20 run GRASP addizionali sul profilo `multistart` (lo script e' resiliente, gira detached;
caffeinate lo tiene su; poll a tempo). Da k/n si stima p con CI accettabile e si dimensiona N per il
target di affidabilita' voluto. Costo: nessun sviluppo, solo wall-clock nei tempi morti. NON blocca
l'adozione della procedura: il pick-best su N=3-4 raggiunge gia' il target su questo campione.

**Nota su "non forzare la riproduzione bad-seed":** l'A/B ha eseguito il piano previsto (n=3 GRASP +
baseline), non una raffica per forzare il bad-seed. Il bad-seed e' comparso naturalmente in grasp-1
(1 su 3). La sua esistenza e' dimostrata; la sua frequenza e' la misura differita.

### 4.2 Nuance onesta: GRASP e' un hedge, il default vince su medium

I run GRASP a 0-hard (grasp-2 -1,31M, grasp-3 -1,30M) hanno medium **peggiore** del baseline (-1,03M,
~270k peggio). Il pick-best su (hard, medium) premiera' il default quando il default e' 0-hard. Lettura:
la diversificazione GRASP e' una **polizza contro i bacini cattivi** (robustezza: garantisce che almeno
un run sia 0-hard anche quando il default finisce nel bacino bad-seed), non un miglioramento di medium.
Operativamente (vedi §6): **includere sempre un run con costruzione default nel set multi-start** (e'
il primario, migliore su medium quando e' 0-hard) + N-1 seed GRASP come fallback. Il default stesso e'
stocastico (TASK-478 run4 deterministico era -6): anche il baseline potrebbe essere il bacino cattivo,
e li' i seed GRASP sono la rete di salvataggio.

## 5. Verdetto comparativo multi-start vs ExactFinisher (AC#3)

**ExactFinisher (TASK-478, ratificato DEFAULT ON):** chiude la coda boxed (H0) in modo **deterministico**
(pass 0-hard-additive, improve-or-noop, gate chain BoxRepair + VRP last). **Meccanicamente ortogonale
a H17**: piazza solo boxed in celle conflict-free, la cella H17 e' H1-bloccata, quindi EF non la crea
ne' la chiude (prosciolto). Sul worst case noto chiude 5/5 boxed depth 0-1, 0 conflitti.

**Multi-start (TASK-480, questo verdetto):** seleziona un bacino buono tra N costruzioni indipendenti
per **evitare** che l'H17 si formi. Agisce sulla radice (bacino), non sul sintomo (buco).

**Verdetto AC#3: ORTOGONALI e COMPLEMENTARI, non alternativi.** EF e multi-start attaccano residui
hard diversi (H0 boxed vs H17 holes) con meccanismi diversi (chiusura deterministica locale vs
selezione di bacino). Compongono: N costruzioni indipendenti, ognuna con EF che chiude la coda boxed,
pick-best su (hard, medium). EF rende deterministica la chiusura della coda boxed; multi-start rende
affidabile la selezione del bacino senza H17. Nessuno deprecerebbe l'altro. (La cascata locale
composition-aware depth-3 di TASK-481 resta esclusa: shell-game sul bacino saturo.)

## 6. Pacchetto operativo (procedura, NON flip di default)

**Quando serve (SOLO path source=input):** il multi-start e' la cintura per il regime bad-seed H17,
che si manifesta quando la pre-assegnazione legge `source=input` (il futuro path solo-input). Sul
`source=golden` (default attuale) il modello e' golden-aligned e H17 e' 0/34. Quindi: **usa il
multi-start quando giri source=input e un single-run torna con hard H17 residui.**

**NESSUN cambio di default:** `source` resta `golden`, GRASP resta `opt-in OFF`
(`-Dplanner.construction.grasp.enabled` default false, byte-identico quando off). Il multi-start e'
una procedura che si attiva esplicitamente, non un comportamento di default.

**Come si lancia:**
```
caffeinate -is ./scripts/benchmark/multistart-ab.sh <N> [profile] [--baseline] [--setup-only|--no-setup]
  N            numero di run GRASP (gate maintainer n>=3; raccomandato 3-5)
  profile      DB profile/volume (default: multistart)
  --baseline   aggiunge un run GRASP-off come controllo (RACCOMANDATO: primario su medium + termine
               di paragone per la diversificazione GRASP)
  --setup-only prepara il profilo (rebuild+seed+import+pre-assign) ed esce
  --no-setup   salta il setup (profilo gia' pronto)
```
Output: `claudedocs/spalmone-2026-27/task480-results/multistart-ab/<run>/` per run (capture-run
artifacts) + `SUMMARY.txt` (per-run hard/medium/wall-clock, 0-hard GRASP count corretto, pick-best
winner). Lo script ora conta il baseline separatamente e riporta il pick-best winner (fix post-A/B).

**Costo wall-clock:**
- **Sequenziale (oggi):** ~Nx singolo run. Un ciclo staged e' ~27-42min (1602-2544s osservati); N=3 +
  baseline = ~2,35h (8480s misurati).
- **Parallelo N-container (da costruire, Fase 4 se industrializzato):** ~1x wall-clock. Un container =
  una JVM + un profilo Postgres su porta dedicata (5432+i app su 8080+i + N volumi). Il vincolo
  static-volatile e' soddisfatto (stato non condiviso tra JVM); il single-DB-actor e' soddisfatto
  (un profilo per container). Risorse laptop: 12 core / 36 GB RAM (da dimensionare, `-Xmx2g` standard).

**Pick-best:** su (hard, medium) lessicografico. Il winner e' il run 0-hard col medium meno negativo.
Se nessun run e' 0-hard, nessun winner pulito -> segnale che serve N piu' alto o il piano B
(TASK-482 Kempe-chain). **Includere sempre il run default nel set** (primario su medium quando e' 0-hard).

## 7. Raccomandazione

1. **Adotta il multi-start come PROCEDURA operativa** per il path source=input (pacchetto §6), NON flip
   di default. Raggiunge il target: P(>=1 0-hard | N=3) = 0,96 point, e il pick-best ha lavoro reale
   (grasp-1 e' -11, recovered da grasp-2/3 = 0).
2. **ExactFinisher resta DEFAULT ON** (chiude la coda boxed H0). I due compongono (AC#3): ogni run del
   multi-start gira con EF attivo.
3. **Dichiara la limitazione del campione** (n=3, CI larga) e **differisci la stima della frequenza
   bad-seed** a una misura in background (~10-20 run addizionali), non bloccante. Da li' si dimensiona N.
4. **Piano B strutturale = TASK-482 Kempe-chain** (connectivity cure, 1 run, niente Nx): aprirlo SOLO
   se la stima differita mostra frequenza bad-seed alta (p basso) o N richiesto inaccettabile. Per
   ora p_hat=0,67 non lo giustifica.
5. **Bug script corretto** (conteggio baseline + pick-best winner); SUMMARY raw preservato con
   correzione appesa.

## Puntatori

- Gate verdict: `task480-results/VERDICT-gate-indipendenza-2026-08-02.md`.
- A/B raw: `task480-results/multistart-ab/{baseline-grasp-off,grasp-seed-1..3}/` (score.txt,
  analysis.json, lessons.txt, kpi-*, phases.json) + `SUMMARY.txt` (con correzione appesa).
- Research: `claudedocs/research_multistart-vs-alternative_2026-08-02.md` (finding 4a, quadro teorico, p~0,6).
- Recon: `task480-recon-timefold-multistart-2026-08-02.md` (perche' sequenziale, 23 static volatile, AC#3).
- Diagnosi H17: `task481-results/PROPOSTA-cura-h17-2026-08-02.md` + `FORK-shell-game-depth3-2026-08-02.md`.
- Script: `scripts/benchmark/multistart-ab.sh`. Codice GRASP: `WeeklyTemplateBuilder.java`
  (`graspEnabled`/`graspSeed`/`newGraspRandom`).
