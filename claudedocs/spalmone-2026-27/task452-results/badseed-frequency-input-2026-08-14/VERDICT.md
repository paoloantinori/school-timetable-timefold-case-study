# VERDICT - Frequenza bad-seed SINGLE-CONSTRUCTION (source=input, multi-start OFF). m-13 / flip #1. 2026-08-14.

> Mandato notturno (Fedora, 2026-08-13 sera). Obiettivo dichiarato: "stimare p(0-hard) di una
> **singola** costruzione input-derived" per decidere il flip #1 (golden -> input) sulla
> *distribuzione*, non su n=1. Questa e' la metrica che il batch procedurale N=32 (solve-staged
> default maxAttempts=3) **non poteva isolare**, perche' ogni "run" di quel batch e' un ciclo
> multi-start con pick-best su 3 tentativi.

## TL;DR

1. **Baseline (costruzione deterministica single-shot) = 0hard** (1522s, medium -1039471).
   La costruzione DEFAULT di source=input, DA SOLA senza multi-start, raggiunge 0hard: robusta
   quanto golden single-shot. **Metrica (a) per il flip #1 = SI** (la costruzione che la produzione
   gira per prima e' robusta single-shot).
2. **GRASP single-construction p(0-hard) = 8/12 = 0.667, Wilson95 [0.391, 0.862].** Eseguite
   N=12 (target 16, stop notturno per tempo; seed-12 completato al resume mattutino, vedi
   §limiti). **4 bad su 12, TRE classi di gravita' distinte**:
   - seed-1 + seed-9 = H17 -9 (9 buchi TUTTI G11, bacino strutturale TASK-481/482,
     RIPRODOTTO su due seed = non singleton);
   - seed-6 = H29 -1 (room-tier, territorio M-TC);
   - **seed-12 = H0 -32 (16 lezioni UNPLACED) + H17 -6 (G03), totale -38hard**: il WORST CASE
     osservato, una costruzione che lascia 16 boxed che l'intero ciclo staged (46 min, 3 iterazioni
     timeslot) non colloca MAI. Nota curiosa: il mandato notturno ipotizzava la distribuzione
     "(0 / -38 / -56 / altri)" e seed-12 ha prodotto esattamente -38hard.
   Misura pulita: con multi-start OFF, la coda bad-seed e' REALE, riproducibile e PIU' GRAVE
   di quanto il batch procedurale lasciasse vedere. M-TC NON copre H17/H0 (timeslot-tier).
3. **Risposta alla domanda aperta "M-TC ha curato H17 o il multi-start?"**: chiarita. Con
   multi-start OFF, il bacino H17 e' ancora RAGGIUNGIBILE dalla costruzione (3/12 seed: due volte
   su G11, una su G03, piu' il -6 dentro seed-12). M-TC NON lo ha eliminato dallo spazio di
   costruzione (M-TC e' room-tier, H17 e' timeslot-tier). Il multi-start resta l'UNICA copertura
   procedurale dell'H17 e dell'H0-boxed. Il batch procedurale N=32 p=1.0 e' la copertura che
   FUNZIONA; questo batch dimostra che e' NECESSARIA per la coda GRASP, non ridondante.
4. **P(>=1 0-hard | N multi-start) a single-construction p=0.667**: N=3 -> 0.963 [0.774, 0.997];
   N=6 -> 0.999 [0.949, 1.000]. Il multi-start N=3 ratificato (DEFAULT ON) copre proceduralmente
   la coda AL POINT ESTIMATE, ma il CI_lo chiede N=7 per P>=0.95 robusto su tutto l'intervallo.
   N raccomandato P>=0.95: point=3, CI_lo=7.

_Status: SUPERSEDED by the n=19 extension (same protocol + MT_LS_OFF comparability flag), see
`VERDICT-extension-n19.md`: p(0-hard) = 13/19 = 0.684 Wilson95 [0.460, 0.846]; N for P>=0.95
CI-robust drops from 7 to 5; TWO NEW bad-basin signatures (seed-17 H17 G13 slot-12, seed-18
H28 no-teacher-return). This file is the night-1 record (n=12), unchanged below._

## Perche' questo batch e' necessario (il gap che il batch procedurale N=32 offuscava)

Il census posseduto (task480-results/badseed-frequency-laptop-2026-08-09 + seeds17-32) misura
**p(0-hard) della PROCEDURA multi-start** (solve-staged default), NON di una singola costruzione.
Lo confessa il suo stesso VERDICT (sezione "Cosa misura esattamente multistart-ab.sh"):
> "Ogni run = boot con -Dgrasp.seed=i + solve-staged (DEFAULT multi-start maxAttempts=3, NON
> disabilitato dallo script). Quindi '0hard per run' = il ciclo staged (multi-start pick-best)
> raggiunge 0hard."

Il batch N=32 ha riportato p=32/32=1.0 (procedurale). Ma golden, il default attuale, e' una
costruzione DETERMINISTICA SINGLE-SHOT che raggiunge 0hard (p=1.0 su N=32 golden census,
TASK-480). Confrontare "golden single-shot" con "input multi-start pick-best" e' mele-vs-arance.
Per un flip onesto del default serve sapere: **una singola costruzione source=input, da sola
(multi-start DISABILITATO), e' robusta quanto la singola costruzione golden?**

Questo batch lo misura: `-Dplanner.multistart.enabled=false` fa girare `runStagedCycle` diretto
(ScheduleService:914), un solo ciclo, niente wrapper pick-best. Identico nel comportamento al
path golden (tentativo 1 = GRASP off, costruzione deterministica).

## Setup e metodo

- **Profilo**: `bsinput14` fresh (seed DataGenerator + import-calendario + pre-assign
  source=input). Boot prod via `planner.sh prod` (java -jar diretto), -D su PLANNER_JAVA_OPTS
  (CLAUDE.md MAVEN_OPTS lesson: i -D arrivano all'app JVM solo sulla riga java -jar).
- **Config**: tutti i flag ratificati DEFAULT ON (constrained-teacher source=input,
  first-hour-slot, lab-day-consolidation, co-presenza, curriculum-poststage, ExactFinisher).
  M-TC per-retry e' SUBORDINATO a multi-start: con multi-start OFF il wrapper non gira mai,
  quindi M-TC non entra mai nel constraint set (banner `armed=true` ma inerte; javadoc riga 1459).
  Singola costruzione = tentativo 1 = M-TC off, come disegnato.
- **Metrica per run**: UN ciclo staged (multi-start OFF), nessun retry, nessun pick-best.
  Score letto a fine ciclo. `enabled=false` confermato dal banner APP (non dallo script):
  `Multi-start staged retry: enabled=false` su executor-thread-1 (canale indipendente dal
  passthrough dello script, antidoto al bug silent-seed-drop del TASK-480).
- **Run**: 1 baseline GRASP-off (costruzione deterministica = controllo, l'equivalente diretto
  del determinismo golden) + N=16 GRASP seed=1..16 (costruzioni randomizzate single-shot, per
  campionare la robustezza della variazione di costruzione SENZA il salvagente pick-best).
- **Anti-bug**: banner APP grep per ogni run (boot.log per-run snapshot prima del overwrite).
  Nessun timeout-cap (wall cap 5400s/run). md5 lessons.txt distinti tra seed (TASK-480).
- **Output**: questa dir. Raw: `score.txt`, `analysis.json`, `export.json.gz`, `phases.json`,
  `lessons.txt`, KPI battery (`kpi-holes.txt` incluso, serve a Filone 2).

## Lettura statistica (due metriche distinte, entrambe oneste)

1. **Baseline (costruzione deterministica, GRASP-off)**: e' la metrica piu' load-bearing per il
   flip #1. La produzione girerebbe prima la costruzione default; se questa raggiunge 0hard
   single-shot, il path input e' robusto come golden SENZA dipendere dal multi-start. N=1 (e'
   deterministica), quindi e' un punto di riferimento, non una distribuzione.
2. **GRASP seeds (costruzioni randomizzate single-shot)**: p(0-hard) stimato con Wilson95 su
   N=16. Misura la coda: quante costruzioni randomizzate, da sole, raggiungono 0hard? Se p<<1,
   il path input ha una dipendenza STRUTTURALE dal multi-start che golden non ha (flip introduce
   rischio). Se p~1, single-construction input e' robusta quanto golden (flip sicuro).

## Per-run (dai raw score.txt)

| run | score | wall(s) | 0-hard? | composizione hard |
|-----|-------|---------|---------|-------------------|
| baseline-grasp-off (det) | 0hard/-1039471medium | 1522 | YES | - (costruzione deterministica) |
| grasp-seed-1 | -10hard/-1173842medium | 2545 | no | H17 -9 (9 BUCO-INTERNO, TUTTI G11) + H29 -1 |
| grasp-seed-2 | 0hard/-1315086medium | 2970 | YES | - |
| grasp-seed-3 | 0hard/-1303621medium | 1900 | YES | - |
| grasp-seed-4 | 0hard/-1194070medium | 1856 | YES | - |
| grasp-seed-5 | 0hard/-1268903medium | 1142 | YES | - |
| grasp-seed-6 | -1hard/-1325361medium | 1499 | no | H29 -1 SOLO (0 BUCO-INTERNO = zero H17, room-tier) |
| grasp-seed-7 | 0hard/-1242715medium | 1804 | YES | - |
| grasp-seed-8 | 0hard/-1230471medium | 1427 | YES | - |
| grasp-seed-9 | -9hard/-1256706medium | 2449 | no | H17 -9 (9 BUCO-INTERNO, TUTTI G11) |
| grasp-seed-10 | 0hard/-1266353medium | 1413 | YES | - |
| grasp-seed-11 | 0hard/-1204101medium | 1571 | YES | - |
| grasp-seed-12 | -38hard/-1150656medium | 2787 | no | H0 -32 (16 UNPLACED) + H17 -6 (G03) - WORST CASE |
| grasp-seed-13..16 | _(stop mattutino per consegna)_ | - | - | - |

**Composizione hard verificata indipendentemente** (analysis.json hard-neg + kpi-holes.txt BUCO-INTERNO),
non dal solo score: seed-1 e seed-9 = 9 buchi TUTTI in G11 (bacino H17 strutturale noto,
TASK-481/482, RIPRODOTTI su due seed diversi); seed-6 = 0 buchi H17 quindi -1hard = H29 room-null
(bacino room-tier, territorio M-TC); seed-12 = H0 -32 con 16 lezioni unplaced + H17 -6 in G03
(worst case). **Quattro bad su 12, tre classi di gravita' DISTINTE tutte riprodotte a
single-construction** (il batch n=8 pre-M-TC aveva caratterizzato le prime due).

## Confronto con i batch esistenti

| batch | source | metrica | N | p(0-hard) | Wilson95 |
|-------|--------|---------|---|-----------|----------|
| n=8 (2026-08-03, pre-M-TC) | input | PROCEDURALE (maxAttempts=3) | 8 | 0.750 | [0.409, 0.929] |
| n=16 laptop (08-09) | input | PROCEDURALE | 16 | 1.000 | [0.823, 1.000] |
| n=32 pooled (08-10) | input | PROCEDURALE | 32 | 1.000 | ~[0.89, 1.0] |
| golden census (TASK-480) | golden | SINGLE-SHOT | 32 | 1.000 | - |
| **QUESTO (2026-08-14)** | **input** | **SINGLE-CONSTRUCTION (maxAttempts=1)** | 12+1 | **0.667** | **[0.391, 0.862]** |

Il drop PROCEDURALE (1.0) -> SINGLE-CONSTRUCTION (0.667) e' il segnale: la copertura 0hard del
path input VIVE nel multi-start pick-best, non nella singola costruzione. Golden non ha questo
drop (single-shot 1.0). Questa e' la dipendenza strutturale che il flip introducerebbe.

## Verdetto per il flip #1 (decisione maintainer)

**NON FLIPPARE ora. Registra ready-when-needed.** Il flip e' DEFERIBILE, non bocciato: la logica
e' onesta su entrambi i lati.

- **A favore del flip (non bloccante):** la costruzione DEFAULT deterministica di input e'
  single-shot robusta quanto golden (baseline 0hard). Se la produzione gira SEMPRE la costruzione
  default per prima (tentativo 1), il path input = golden in robustezza hard-0.
- **CONTRO il flip (perche' ora no):** il solve-staged ratificato e' multi-start DEFAULT ON, quindi
  in esercizio il path input DIPENDE dal multi-start pick-best per garantire 0hard (coda GRASP
  p=0.667 single-construction, 4/12 bad, H17 riproducibile su 3 seed, fino al -38hard boxed di
  seed-12). Golden e' single-shot robusto SENZA dipendere dal multi-start. Il flip scambierebbe
  "golden single-shot robust" con "input multi-start-dependent": introduce una dipendenza
  strutturale che golden non ha, per un guadagno (solo-input, direttiva) che resta valido ma non
  urgente (golden esiste ancora).
- **Condizione di flip sicura (ready-when-needed):** o (a) si adotta una cura deterministica
  dell'H17 G11 (TASK-481/482 territorio: composition-aware / Kempe, entrambi esclusi a valore
  singolo ma aperti a livello costruzione), oppure (b) si accetta esplicitamente che 0hard e'
  garantito proceduralmente (multi-start), non per costruzione. (b) e' la posizione m-13 attuale:
  il gate e' sul SIGNIFICATO dell'accettazione procedurale, e questo batch ne misura il COSTO
  nascosto (33% coda single-construction, 4/12).
- **Coda H17 e' il vincolo reale, non H29:** l'H17 e' timeslot-tier (M-TC non lo tocca) e
  RIPRODUCIBILE (3/12 seed colpisiti da H17, su due gruppi diversi: G11 e G03). Finche'
  resta, il flip aggiunge rischio. La cura H17 (territorio TASK-481/482) e' la vera via al flip
  sicuro.

## Limiti

- **N=12 non N=16 (stop notturno + resume mattutino):** target era 16-32. Primo stop alle 06:27
  (VERDICT + Filone 2 prima del risveglio), resume --no-setup alle 06:35 che ha completato
  seed-12 (il worst case -38hard: da solo giustifica il resume) prima dello stop finale alle
  07:22 (seed-13 appena avviato, abortito in B-TIMESLOT-1, non catturato). p_hat=0.667 con
  TRE classi di gravita' caratterizzate; estendere a N=16/32 stringerebbe la CI ma la coda e'
  gia' dimostrata riproducibile (4 bad su 12, H17 su 3 seed diversi).
- N=12 su una laptop sola (within-machine). Cross-environment: Fedora al rientro.
- n=1 baseline: deterministica, un punto (riproducibilita' asserita dal gate indipendenza TASK-480).
- L'H17 e' LS-probabilistico: questo batch lo caratterizza a single-construction (senza multi-start)
  ma n=12 non esclude un H17 occasionale su seed non ancora campionati.

## Puntatori
- Raw: `task452-results/badseed-frequency-input-2026-08-14/{baseline-grasp-off,grasp-seed-1..16}/`
- Stats helper: `scripts/analyze/badseed-frequency-stats.py` (Wilson95, baseline escluso dal sample GRASP)
- Batch script: `scripts/benchmark/multistart-ab.sh` con `MULTI_START_OFF=1` (mode aggiunta)
- Batch esistenti (procedurali, per confronto): `task480-results/badseed-frequency{,-laptop-2026-08-09,-laptop-2026-08-09-seeds17-32}/`
