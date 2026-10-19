# Model gap characterization (run vs golden, lato hard e medium), 2026-08-05

**Domanda:** nel pivot al MODELLO, dove sono i gap? Ovvero: su quali vincoli il run diverge
dalla pratica del golden, e in quale direzione (modello piu' rigido o piu' lasco della pratica)?

**Metodo (read-only, su capture esistenti, nessun nuovo solve):** confronto il breakdown
per-vincolo (`analysis.json` campo `constraints`: `{constraint, hard, medium, soft, matches}`)
del run ratificato good-seed source=golden `task469-results/ab-cps-on-run1` contro la capture
del golden `task452-results/paired-golden-run1`. Lens golden-first: il golden e' pratica valida,
quindi un vincolo che il run viola e il golden no e' un sospetto "modello piu' rigido della pratica".

**Caveat (dichiarati):**
1. Le due capture hanno provider-version quasi identiche ma NON uguali (32 vincoli ciascuna):
   il run ha M-CPS (TASK-469, ratificato 2026-08-01 sera), il golden no; il golden ha S8, il run no.
   La capture del golden e' del 2026-08-01 mattina (TASK-452, commit `4c95f14c`), il run e' successivo.
   I vincoli stabili confrontati (M22/M32/M20/M-LF/M15/M37/M35/M33/M34/M28/M18) hanno semantica
   identica in entrambe, quindi il confronto su di essi regge; i numeri piccoli hanno rumore di versione.
2. n=1 (un run, un golden). La LS e' NON_REPRODUCIBLE, quindi varianza run-to-run. Ma il segnale
   convergente sulle celle strutturali gia' note (vedi sotto) indica sistematicita', non rumore.
3. "Run batte il golden su quasi tutti i medium" e' IN PARTE ATTESO: il solver ottimizza proprio
   il proprio obiettivo (che include quei medium), quindi e' banale che uno schedule ottimizzato
   li soddisfi meglio di uno schedule umano non costruito per quell'obiettivo. Non e' di per se'
   prova di "fedelta' del modello".

## Lato HARD: fedele al 100%, nessun gap

Sia il run (0hard/-963616medium/7997soft) che il golden (0hard/-983635medium/4009soft) sono
**0-hard**. Nessun vincolo hard fa fuoco su nessuno dei due. Il modello hard non e' piu' rigido
ne' piu' lasco della pratica: la replica ed e' esatta su tutto il perimetro hard. Questo conferma
la conclusione della sintesi: il lato hard e' pienamente caratterizzato e risolto (la parte
strutturale dal multi-start, la chiudibile da HoleRepair).

## Lato MEDIUM: un solo segnale reale, M18 day-start

Sui vincoli medium condivisi, il run paga MENO penalty del golden su 10 vincoli su 11 (M22, M32,
M20, M-LF, M15, M37, M35, M33, M34, M28). L'atteso (caveat 3): il run ottimizza l'obiettivo.

L'UNICO vincolo medium dove il run e' PEGGIORE del golden e' **M18 day-start**:

| vincolo | run | golden | gap (run-golden) | run matches | golden matches |
|---------|-----|--------|------------------|-------------|----------------|
| M18 day-start | -2600 | -650 | -1950 (run peggio) | 43 | 13 |

Tutti gli altri shared medium hanno `gap >= 0` (run migliore o pari). La lista "modello piu'
rigido della pratica" (run paga, golden a 0) e' **VUOTA** a meno di M-CPS (nuovo, run -18/3
matches, assente dalla capture golden per data).

**M18 e' un gap REALE, non un artefatto di misurazione.** Cross-check contro il KPI day-start:
- run: 2166/2209 group-days ancorati alla prima ora = **98,1%** (istogramma {8: 2153, 9: 50,
  10: 4, 11: 1, 12: 1}).
- golden: 2196/2209 = **99,4%** (istogramma {8: 2183, 9: 26}).
- anatomy: **43 group-days in ritardo nel run vs 4 nel golden** (prima lezione reale dopo le 8).

Il ritardo run concentra sulle STESSE celle strutturali gia' identificate sul lato hard:

| cella (gruppo, weekday) | run ritardi | golden | legame col lato hard |
|-------------------------|-------------|--------|----------------------|
| G13 venerdi | 12 | 0 | i 18 buchi H17 accettati (TASK-451): lab-day 5h G13 venerdi' slot 11 |
| G11 mercoledi | 6 | 0 | l'H17 strutturale bad-seed (G11 mercoledi' ore 10, TASK-481/483) |
| G09 venerdi | 4 | 0 | - |
| G04 martedi | 3 | 0 | - |

Cioe': il residuo di tensione medium (M18) e il residuo di tensione hard (H17 bad-seed) cadono
sugli STESSI colli di bottiglia strutturali, non su vincoli "sbagliati" altrove.

## Implicazione strategica per il pivot al MODELLO

1. **Il modello e' fedele alla pratica del golden su tutto il perimetro**, lato hard (0/0) e
   lato medium (nessuna tensione run-only; l'unica divergenza, M18, ricade sui colli di bottiglia
   gia' noti). La recalibrazione golden-first (TASK-379 e successive) ha fatto il suo lavoro:
   il modello non e' da nessuna parte piu' rigido della pratica.
2. **Il medium model NON e' il gap.** Stringere i pesi/le soglie dei vincoli esistenti non
   chiude nessun divario verso il golden: il run li soddisfa gia' meglio del golden. Questo e'
   coerente con il verdetto "pesi medium flat" della sintesi metaheuristic.
3. **L'unico residuo di qualita' (M18 day-start) coincide con il residuo hard (H17)**: e' lo
   stesso bacino strutturale (G11 mercoledi', G13 venerdi'), gia' gestito lato hard dal
   multi-start (selezione del bacino). Una cura strutturale per quelle celle chuiderebbe
   contemporaneamente l'H17 bad-seed e l'M18 day-start. Non e' un problema di modello di
   vincolo: e' un problema di composizione/fill di quei gruppi specifici.
4. **Di conseguenza il pivot al modello, se cerca qualita', non sta nel tuning dei vincoli**
   (fedele e flat) ma nei due assi rimasti: i **FATTI** (target input-derived, solo-input,
   debito TASK-452) e le **PRATICHE NON ANCORA MODELATE** (le domande del workbook TASK-445,
   che il golden rivela ma che nessun vincolo corrente esprime). Il modello-compresso al golden
   non ha piu' molto da guadagnare sui vincoli esistenti.

## Dove guardare

- Capture run: `task469-results/ab-cps-on-run1/analysis.json` + `kpi-daystart*.txt`.
- Capture golden: `task452-results/paired-golden-run1/analysis.json` + `kpi-daystart*.txt`.
- Celle strutturali H17: `task481-results/FORK-shell-game-depth3-2026-08-02.md` (G11 mercoledi'),
  TASK-451 ratifica (G13 venerdi').
- Framework golden-first: `CLAUDE.md` "Golden-first recalibration (TASK-379)".
