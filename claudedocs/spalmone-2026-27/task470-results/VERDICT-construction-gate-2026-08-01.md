# TASK-470 - VERDETTO gate construction theory-spread (zai, 2026-08-01)

Domanda del gate: il prototipo theory-spread nel `WeeklyTemplateBuilder` (lever = scelta
del weekday candidato, preferenza con fallback, flag `planner.construction.theory-spread.enabled`
DEFAULT OFF) fa scendere il residuo M-TR di costruzione sostanzialmente verso il golden ~440?
Risposta: **NO.** Il lever weekday e' strutturalmente insufficiente (residuo PIATTO). Uno spike
diagnostico (theory-as-1h-chunks, revertito) conferma che il lever reale e' la dimensione del
chunk, ma anche quello plateau a ~2,6x il golden: il gap residuo e' in gran parte a livello di
INPUT, non di costruzione. Dettaglio sotto.

Setup: `WeeklyTemplateConstructionRealSeedTest` (real seed, construction-only, no LS). Flag ON
via `System.setProperty` (pattern in-repo dei sibling slot0/first-hour/room-aware; `test.sh` non
forwarda `-D`, quindi il toggle e' nel test). Misura residuo con
`scripts/analyze/theory_adjacent_residual_dump.py` (classificazione teoria STRETTA mirror del
vincolo, validato a 1679 sul run dump = identico al vincolo). HEAD `feature/cartesian-block-entity`.

## Risultati (verificati dai raw)

| metric | golden | OFF baseline | weekday-only ON (prototipo) | 1h-spike ON (throwaway) |
|---|---|---|---|---|
| **M-TR residuo giorni-2+** | 432 raw / 440 runtime | **1669** | **1675** (+0,4%) | **1142** (-31%) |
| M-TR units (sum ore-1) | - | 1750 | 1713 (-2,1%) | 1163 (-34%) |
| zero H1/H3 (safety) | - | OK | OK | OK |
| stampSkipConflict | 0 | 0 | **0** (invariato) | **0** (invariato) |
| ricorrenza fine construction | ~99% | ~76,7% | 77,6% | 77,4% |
| placed (stamp) | - | ~8581 | 8700 | 8448 |

**Lettura del prototipo (weekday-only):** residuo 1669 -> 1675, PIATTO (leggermente peggiore).
`units` scende (1750->1713) e i subject top si spostano (Lingua Italiana 353->326): il prototipo
FUNZIONA (la preferenza e' onorata, ridistribuisce), ma non muove la metrica decisiva (giorni-2+).
Guardie di sicurezza VERDI: zero H1/H3, `stampSkipConflict=0` invariato a flag-OFF (la preferenza
con fallback non puo' introdurre conflitti: e' un puro riordino del weekday in `tryPlaceChunk`,
il fallback e' il first-fit originale). Byte-identico a flag OFF per costruzione.

## DIAGNOSI (causal-chain + adversarial-verification)

Il lever "scelta del weekday" e' strutturalmente INSUFFICIENTE. Meccanismo: con la chunking
**2h-first** del builder, **ogni chunk 2h di teoria e' esso stesso un giorno-2+** (d a una data
2 ore di quella materia). Spargere chunk 2h su weekday diversi crea PIU' giorni-2+ (4h concentrati
su un giorno = 1 giorno-2+; 2h+2h su due giorni = 2 giorni-2+), non meno. Il generatore del residuo
e' la **dimensione del chunk (2h)**, non il weekday.

Verifica avversaria:
- "il prototipo non fired abbastanza" -> CONFUTATO. `units` scende, i subject top si spostano.
  La preferenza e' onorata; ridistribuisce; non riduce i giorni-2+.
- "un altro ordering del weekday funzionerebbe" -> CONFUTATO dalla struttura. Qualunque chunk 2h
  = 1 giorno-2+ qualunque weekday scelga. Solo 1h/data riduce i giorni-2+.
- Validazione incrociata col golden: golden 432 / ~6742 lezioni teoria = 6,4% (scuola piazza
  ~94% teoria a 1h/giorno); run 1669/6752 = 24,7%. Il gap golden-vs-run E' la forma 1h-vs-2h.

## SPIKE diagnostico (throwaway, revertito) - il lever reale e' parziale

Per verificare la diagnosi: theory-as-1h-chunks + dedup (stesso flag, chunk forzato a 1 per
teoria). Risultato: **1142 giorni-2+** (da 1669). Conferme:
1. Chunk-size E' un lever piu' forte del weekday (1142 < 1675).
2. **MA plateau a ~1142 = 2,6x golden 440.** Chunk-size da solo NON basta.

Il residuo rimanente (1142) e' "ripetizioni vincolate": materie teoriche le cui ore settimanali
superano i weekday effettivamente disponibili (calendario chiuso, docente condiviso non libero),
per cui lo spread a 1h/giorno e' infeasible e il repeat e' forzato. Il golden fa 440, quindi la
scuola distribuisce la teoria in modo che il vincolo non stringa: pratica non catturata dai soli
input di costruzione.

## VERDETTO STRATEGICO

I lever di COSTRUZIONE (weekday e chunk) sono **PARZIALI**. Best case costruzione (1h+dedup) =
1142, ancora 2,6x golden. Il gap verso golden 440 e' in gran parte a livello di **INPUT**:
allocazione ore/materia, disponibilita' docente, e la pratica della scuola di distribuire la
teoria. Coerente col quadro generale (il peso M-TR era inerte nello sweep; la sorgente non era
accordabile ne' col peso ne', ora appurato, col solo builder).

**Raccomandazione (adozione = maintainer):**
- **NON inseguire il residuo M-TR nel builder.** Ne' weekday (prototipo gate-failed) ne' chunk
  (spike: 1142, parziale) chiudono il gap.
- **Trattare la distribuzione della teoria come pratica da dichiarare nel workbook**
  (TASK-445/466, direttiva solo-input): il golden dice QUALI materie vanno a 1h/giorno e quali
  ammettono il blocco 2h; quella distribuzione e' input, non scoperta di costruzione.
- Il prototipo weekday-only (gated DEFAULT OFF) resta committed come **artifact documentato +
  building block**: la sua dedup per-(gruppo, giorno, materia-teorica) e' componente necessaria
  di qualunque cura 1h futura (se il workbook ridimensiona il gap sotto soglia e si vuole
  chiudere quel che resta in costruzione). Il refactor `isTheoryForRule` single-source in
  `SubjectNormalization` resta (M-TR e costruzione ora concordano su cosa e' teoria).

## Cosa e' committed (HEAD, flag DEFAULT OFF, nessuna adozione)
- `SubjectNormalization`: set `THEORY_RULE_PRACTICAL_EXEMPT` + `isTheoryForRule(boolean, String)`
  (fonte unica, condivisa M-TR + builder).
- `SchoolScheduleConstraintProvider`: `isTheoryForRule(Lesson)` delega (behavior identico,
  `TheoryAdjacentConstraintTest` 10/10 verde).
- `WeeklyTemplateBuilder`: flag `isTheorySpreadEnabled()` (opt-in), `theorySpread` in
  `PlacementState`, `tryPlaceChunk` 3-pass (tag+spread / spread / first-fit fallback, mirror
  dello S2-spike pattern), `subjectAlreadyOnDay` (riusa la `result` map).
- `ScheduleService`: banner `theory-spread=%b`.
- `WeeklyTemplateConstructionRealSeedTest`: `theorySpreadOn_measuresResidualAtConstructionEnd`
  (MISURA non verdetto, dump `construction-lessons-theoryspreadon.txt`).

## Verifica (adversarial-verification, numeri dai raw)
- Gate weekday-only piatto (1669->1675): CONFIRMED. Setup pulito (stessa classe, flag via
  setProperty, M-TR refactor verificato 10/10). Spike 1142 conferma chunk-size lever ma parziale.
- Baseline 1669 ~= 1672 documentato (VERDICT-sweep468): il refactor `isTheoryForRule` ha
  preservato il comportamento (regression check sul mio stesso refactor).
- Golden 440 runtime (432 raw + 8 rename RA): non ri-misurato qui, portato dai doc AC1/sweep.
- Diagnosi chunk-size: CONFIRMATA dallo spike (1142 < 1675) + validazione golden (1h-vs-2h).
