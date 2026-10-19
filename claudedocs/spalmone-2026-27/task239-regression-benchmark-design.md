# TASK-239 - Regression-detection benchmark: DESIGN (read-only spike)

Stato: design + prototipo read-only, **NON adottato** (nessun wiring CI). Data: 2026-08-04.
Prototipo: `scripts/benchmark/check-regression.py` (sostituisce il placeholder omonimo).
Self-test: `scripts/benchmark/test_check_regression.py` (10 scenari, exit 0).
Verdetto: `task239-results/VERDICT-239-2026-08-04.md`.

Questo doc dice COS'E' un regression check per noi, QUALI KPI salvaguardare, COS'E'
il baseline, COME si confrontano run-to-run vista la varianza multi-start, e DOVE
si salvano i risultati. E' costruito sul tooling esistente (`capture-run.sh` + gli
script KPI), non lo reinventa.

## Reframe (maintainer, 2026-08-04): spike read-only, non adozione CI

Il task originale (2026-03-14) parlava di pipeline CI notturna + pubblicazione
risultati (AC #1, #4). maintainer lo ha riframmato: **design + prototipo read-only,
nessuna adozione**. La decisione di integrarlo in CI resta di maintainer. Gli AC #1
(pipeline CI) e #4 (pubblicazione) sono quindi fuori scope; gli AC #2 (risultati
con timestamp), #3 (soglie di allarme), #5 (doc soglie) sono coperti dal design e
dal prototipo come meccanismi pronti all'uso, non esercitati in CI.

## Il problema centrale: la LS e' NON_REPRODUCIBLE

La local search e' `NON_REPRODUCIBLE` (`solverConfig.xml`), quindi due run sullo
**stesso commit + stessa config + stesso seed** producono KPI diversi. Questo
rompe l'idea ingenua "confronto una run nuova contro una run baseline, punto".
Misurato in campagna (fonte: `campaign-status-2026-08-04.md`,
`task480-results/badseed-frequency/VERDICT-badseed-frequency-2026-08-03.md`):

- **p(0-hard) per singola costruzione = 0,750** (n=8, Wilson95 [0,41; 0,93]).
  Una singola run consegna hard residuo circa una volta su quattro, **anche senza
  alcuna regressione**: e' varianza del bacino, non un difetto di codice.
- **KPI continui** (full-day, ricorrenza, medium) oscillano run-to-run a commit
  fisso. Esempio reale misurato in questo spike: due run entrambe source=golden
  good-seed, hard 0 entrambe, ma full-day 90,9% vs 88,3% (-2,6pp) e medium
  -963k vs -1074k (-110k). Nessuna regressione: rumore.

Da qui due regole di confronto oneste, entrambe implementate nel prototipo.

## Regola 1: hard e' binario e blocante, con gestione della varianza

Hard e' `0` (good) o `<0` (male) e la direttiva maintainer 2026-08-02 lo dichiara
**binario e blocante end-to-end**. Ma una singola run con hard<0 contro un
baseline a 0-hard NON e' una regressione: potrebbe essere il bad-seed (~25% delle
volte). Quindi:

- **single run**, baseline 0-hard, candidate hard<0 -> **INCONCLUSIVE** (exit 2).
  Il tool dice esplicitamente "potrebbe essere varianza, ripeti come aggregate".
- **aggregate** (best-of-N costruzioni, vedi `multistart-ab.sh`), baseline 0-hard,
  aggregate best-hard<0 -> **REGRESSION** (exit 1). Qui N tentativi indipendenti
  non raggiungono cio' che il baseline raggiungeva: il bacino e' peggiorato.
- baseline stesso hard<0 -> **n/a**: un baseline infeasibile non e' un riferimento
  valido (non si regression-checka contro un orario non ammissibile).

N=3 porta P(>=1 0-hard) > 0,95 sul bacino buono; e' il default operativo del
multi-start ratificato (TASK-483). Per il regression check, l'aggregate e' la via
autorevole per hard; la single-run e' solo uno screen veloce.

## Regola 2: KPI continui a banda con tolleranza, single-run = indicativo

Per i KPI continui il confronto e' (candidate - baseline) con due soglie per KPI,
**warn** e **fail**:

| KPI | senso | unita' | warn | fail | sorgente (capture-run.sh) |
|-----|-------|--------|------|------|---------------------------|
| full_day | higher_better | pp | 2,0 | 5,0 | `kpi-fill.txt` riga `FULL-DAY RATE` |
| recurrence_unpinned | higher_better | pp | 2,0 | 5,0 | `kpi-resemblance.txt` riga `ricorrenza UNPINNED` |
| day_start | higher_better | pp | 1,0 | 3,0 | `kpi-daystart.txt` riga `DAY-START ANCHORED RATE` |
| holes (BUCO-INTERNO) | lower_better | count | 5 | 15 | `kpi-holes.txt` blocco `TOTALI PER CLASSE` |
| medium | higher_better | score | 50000 | 120000 | `score.txt` campo medium |

`higher_better` per medium significa "meno negativo e' meglio". Le soglie sono
policy stabile (non numeri di run), sovrascrivibili con `--spec <json>`. Una
single-run che cade in banda **warn** e' "indicativa, probabilmente rumore"; in
banda **fail** e' regression dichiarata. `--strict` promuove warn a exit 1 per
chi vuole soglie piu' tese.

I KPI di guardia della campagna (soglie minime assolute, non relative al
baseline) restano in `CLAUDE.md` / `campaign-status-*`: hard 0, full-day >= 78,
ricorrenza unpinned >= 72, buchi 0 (good seed). Il regression checker confronta
**relativamente al baseline**; le soglie assolute sono un check ortogonale che un
human legge dal report (il baseline ratificato e' gia' sopra le soglie assolute,
quindi un pass-vs-baseline implica pass-vs-assolute).

## Il baseline: una capture dir + una spec di regole (niente numeri magici)

Il baseline **non e' un JSON di numeri scritti a mano**: e' una directory
prodotta da `capture-run.sh` su una run ratificata (commit + config + source
noti). I valori assoluti si leggono parsing quella dir, cosi' non c'e' copia
stale-prone (la direttiva "puntatori, non copia" di `CLAUDE.md`). La **spec**
(`DEFAULT_SPEC` nel prototipo, overridabile via `--spec`) definisce solo le
REGOLE (quali KPI, senso, soglie warn/fail), che sono policy stabile.

Baseline di riferimento naturale oggi: `task469-results/ab-cps-on-run1`
(source=golden, hard 0, full-day 90,9%, ricorrenza 75,6%): e' la run ratified
piu' recente con M-CPS ON. Pero' e' una **single run**, quindi per un baseline
autorevole su hard servirebbe un aggregate (vedi Limitazioni).

## DOVE si salvano i risultati

Il tool scrive (su richiesta, sempre fuori dal repo se non serve versionarli):

- `--out-json <path>`: report machine-readable (verdetto, exit_code, per-KPI
  baseline/candidate/delta/status, parse gaps, note). E' il formato che una
  futura CI leggerebbe.
- `--out-md <path>`: report markdown (tabella + verdetto + note), nello stile dei
  `VERDICT-*.md` di campagna.
- stdout: tabella + verdetto (per uso interattivo).

Pattern consigliato: per un ciclo di regression, `--out-json` e `--out-md` in una
dir timestampata (es. `local/regression/<ISO>/`), insieme al path della capture
candidate. Non si versiona in repo: e' output, non sorgente (come le
`task*-results/` che si versionano solo come evidenza di ratifica).

## Exit codes (config-gated, CI-ready anche se non cablato)

| code | verdetto | quando |
|------|----------|--------|
| 0 | PASS (o PASS with warnings) | nessun KPI in fail; warn tollerati salvo `--strict` |
| 1 | REGRESSION | un KPI continuo in banda fail, oppure hard regredito su aggregate confermato; o warn con `--strict` |
| 2 | INCONCLUSIVE | single run hard<0 vs baseline 0-hard (varianza, serve aggregate) |
| 3 | ERROR | input mancante: dir non trovata, KPI file non parsabili |

## Rapporto con il tooling esistente (cosa si riusa, cosa non si reinventa)

| Script | Ruolo | Relazione con il regression check |
|--------|-------|-----------------------------------|
| `capture-run.sh` | cattura canonica post-solve (score, analysis, export, lessons.txt, tutti i KPI, viz) | **Sorgente degli input**: baseline e candidate sono sue directory. Si riusa, non si duplica. |
| `multistart-ab.sh` | N staged solve con seed GRASP indipendenti, pick-best | **Produce l'aggregate**: la sua dir di output e' l'input `--aggregate` del checker per il verdetto autorevole su hard. |
| `compare-experiments.py` | diff di due scorecard JSON di `run-experiment.sh` | Diverso formato (scorecard, non capture-run) e niente gestione varianza. Coesiste; il checker e' specifico per la batteria KPI di campagna. |
| `verify-baseline.sh` | gate di conteggi seed/import (timeslot 1376, teacher 27, ...) | Concetto ORTOGONALE: verifica che il DB sia stato seminato bene, non che i KPI del solve non siano regrediti. Non si tocca. |
| `check-regression.py` (placeholder) | stub "manual review required" | **Sostituito** da questo prototipo. |

## Come si usa (prototipo)

```bash
# Screen veloce: una run nuova vs un baseline ratificato
python3 scripts/benchmark/check-regression.py \
    --baseline claudedocs/spalmone-2026-27/task469-results/ab-cps-on-run1 \
    --candidate <capture-dir-della-run-nuova>

# Verdetto autorevole su hard: aggregate multi-start come candidate
python3 scripts/benchmark/check-regression.py \
    --baseline <baseline-capture-dir> \
    --aggregate <dir-di-N-capture-subdirs-prodotte-da-multistart-ab.sh>

# Report json + md per evidenza
python3 scripts/benchmark/check-regression.py --baseline B --candidate C \
    --out-json local/regression/$(date +%F)/report.json \
    --out-md   local/regression/$(date +%F)/report.md
```

Esempio reale (smoke test di parsing, NON un verdetto di regressione: due run
cross-config source=golden): il tool classifica correttamente i delta entro il
rumore NON_REPRODUCIBLE come WARN, non REGRESSION. Dettaglio in VERDICT-239.

## Config-gating / perche' non e' in CI

E' invocato manualmente. Nessun hook, nessuna GitHub Action, nessun pre-commit.
L'unica manopola di configurazione e' la spec (default embedded o `--spec`).
L'adozione CI e' decisione di maintainer: il prototipo e' pronto quando lo sara'.
Motivazione onesta del non-adozione ora: la LS NON_REPRODUCIBLE richiede che un
gate CI su hard giri in modalita' aggregate (multi-start), e un multi-start
completo sono ~28 min/run x N: troppo per un gate ad ogni commit. Un eventuale
gate CI notturno (AC #1 originale) avrebbe quindi natura diversa (batch, non
block-on-PR), e quella e' una scelta architetturale di maintainer, non del prototipo.

## Limitazioni oneste (cosa il prototipo NON fa ancora)

1. **Baseline single-run su hard e' solo indicativo.** Per un baseline autorevole
   su hard servirebbe un aggregate ratificato (oggi le run ratified sono
   single). Cura: produrre un baseline aggregate di riferimento (N=3 sul bacino
   buono) e versionarlo come riferimento, oppure trattare il baseline sempre come
   `--aggregate`.
2. **Niente breakdown hard per famiglia.** Il checker legge l'hard totale da
   `score.txt`. Un'analisi piu' fine (H17 vs H29 vs H0 boxed) richiede di parsare
   `analysis.json`: l'hook c'e' (il file e' nella capture dir), non e' ancora
   consumato. Da fare se si vogliono soglie per-famiglia.
3. **Niente trend storico.** Non c'e' un DB delle run nel tempo (AC #2 originale
   "stored with timestamp"). Il prototipo scrive report timestampati, ma non li
   accumula in uno store queryabile. Da fare se si vuole capacity planning.
4. **Le soglie warn/fail sono euristiche**, calibrate sul rumore osservato
   (full-day ~2,6pp, medium ~110k tra due run golden). Non sono state validate
   su una serie storica lunga. `--spec` permette di ritoccarle quando si avra'
   quella serie.
5. **Niente auto-save del baseline.** Non c'e' un "imposta questa run come nuovo
   baseline" (il vecchio placeholder aveva un TODO `save_baseline`): intenzionale,
   perche' un baseline e' una scelta ratificata, non un click. Si fa a mano
   puntando `--baseline` a una capture dir ratified.

## Cura a valle (task da aprire se si adotta)

- TASK-candidate: baseline aggregate di riferimento (N=3 good basin) versionato.
- TASK-candidate: parsing `analysis.json` per breakdown hard per-famiglia.
- TASK-candidate: store storico run + trend (AC #2 originale).
- TASK-candidate (maintainer): forma del gate CI se/adottare (notturno batch vs block).
