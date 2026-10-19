# HANDOFF FINALE per Fedora - turno Fedora-offline (zai, 2026-08-07 → 08-11)

Fedora e' crashata ~2026-08-07 e resta offline. Questo turno ha tenuto avanti la campagna
dalla laptop locale (36 GB / 12 core arm64, certificata come solve host: baseline 0hard/75,9%
ricorrenza). ~4 giorni di lavoro autonomo, 24 commit, tutto pushato su `feature/cartesian-block-entity`.
**maintainer chiede la tua valutazione indipendente** delle conclusioni portanti (sezion sotto).

## VALUTAZIONE INDIPENDENTE RICHIESTA (claim load-bearing da verificare)

Queste sono le affermazioni che reggono decisioni; rifalle tue dai raw, non dal mio verdetto:
1. **m-13 p(0-hard)~1,0 su n=32** (source=input, config corrente). Raw: `task480-results/
   badseed-frequency-laptop-2026-08-09/` (N=16) + `...-seeds17-32/` (N=16 ext). Verifica: 32/32
   0hard? L'H17 e' davvero assente in 32? (maintainer ha APPROVATO la chiusura procedurale su questo.)
2. **449 RULED OUT** (-5hard fisso su golden E input). Raw: `task449-results/` + `task449-results-input/`.
   Verifica: -5hard in 3 tentativi su entrambi i regimi? Il pre-booking degrada davvero?
3. **470 NEUTRAL** (0hard, -54k medium entro ~2x varianza). Raw: `task470-results/`. Verifica:
   e' neutro o il -54k e' rumore? (single A/B; un re-run lo confermerebbe.)
4. **471 workbook + 21_CadenzaAttesa** (gates). Verifica: ri-esegui `test_theory_block_identity`
   + `test_cadence_identity` + `test_validazione` (io li ho avuti verdi; rifalli).
5. **stability good-seed** (0hard 3/3, spread 0,2pp). Raw: `stability-golden-2026-08-09/`.

## Le 3 decisioni di maintainer (da ratificare/implementare)

| # | decisione | stato | cosa serve da te |
|---|-----------|-------|------------------|
| 1 | **m-13: approvato** il multi-start come chiusura procedurale del path input | DOC ratifica scritta (`task471-m13-procedural-closure-APPROVED-2026-08-11.md`, prossimo commit) | ratifica o contrasta; il gate m-13 si sblocca su questo |
| 6 | **M34/M35 auto-disable LOUD** | IMPLEMENTAZIONE in corso (banner refreshSolverStatics + check 99_Validazione; flag era silent) | review del diff Java + DoD |
| 7 | **backlog TASK-230**: rnumerable l'active Timeline-view, keep completed M20=230 | FATTO (rename manuale) | review |

## Cosa e' STATO FATTO (per review keep/stop, commit per commit)

**Workbook (input dichiarato, direttiva solo-input):**
- TASK-471 foglio `22_TeoriaBlocco` (teoria 1h vs 2h come input, 196 righe). DoD piena
  (/simplify, /code-review via requesting-code-review, test.sh). `cd70ccce`, `d8f86e47`.
- `21_CadenzaAttesa` (cadenza M34/M35 come input, 245 pairs; code-review I1 catchato: filtro
  total_hours>=10 match Java consumer). `965d1349`.
- `baseline-golden.sh` (driver laptop certification). `5059662f`.

**Policy/analysis (model axis):**
- TASK-441 no-golden policy + M21 non-derivabilita' + **source=input viable** (confronto KPI
  end-to-end: hard=0 entrambi, input -1,3pp ricorrenza). `e5caa3a5`, `738a0600`.
- TASK-460 design ricombinazione (de-prior., asse esausto). `59daa4b1`.
- TASK-296 chiuso (ACL rule-out).

**Esperimenti (live A/B, prima mai fatti):**
- **TASK-449 RULED OUT**: live A/B golden + input, -5hard fisso (degrada). `a995c422`, `a0452089`.
- **TASK-470 NEUTRAL**: live A/B, 0hard, -54k medium (entro varianza). `a0452089`.
- **bad-seed N=32**: p(0-hard)=1,0 [0,89,1,0], m-13 forte. `454875df`, `688e256e`.
- **stability good-seed x3**: STABLE (0hard 3/3). `7ef16812`.

**Igiene:** backlog TASK-230 rename (decisione 7). Recovery batch dopo reboot (`6833b045`).

## Finding chiave per te (pushback onesti)

1. **TASK-470 non e' il prossimo passo** (contrariamente al campaign-status-08-05): il suo
   verdetto raccomanda non-adottare, la dedup esiste gia', bloccato su referente. L'ho deferred.
2. **M21/T20 sono costanti Java + colonne workbook**, non letture golden-runtime.
3. **TASK-460 nell'asse metaheuristic ESAURITO**: de-prior. rispetto al pivot MODELLO.
4. **Il collo di bottiglia e' esterno** (referente per conferme workbook); il work autonomo localmente
   e' ora limitato a esperimenti/verifiche (metaheuristic exhausto).

## Commit map (24 commit, 764fba7e..HEAD)
cd70ccce 471 sheet | d8f86e47 471 code-review M1 | 5059662f baseline-golden driver |
e5caa3a5 441 policy+M21 | 59daa4b1 460 design | 48b35169 handoff-1 | b68ed56e baseline PASS |
965d1349 cadenza 21 | 5fcff281 handoff-2 | 738a0600 input-vs-golden | 8637ccb3 ab-task449 harness |
1f916b0d master-batch-1 | 19245830 449 partial | 454875df+1e28137e bad-seed N=16 |
6475a778 master-batch-3 | cfc88856 master-batch 4/5 | a995c422 449 RULED OUT |
7ef16812 stability | c6d93781 master-batch-6 470 | 688e256e bad-seed N=32 | 6833b045 recovery |
a0452089 FINAL 449+470

## Cosa gira ora (36h di laptop autorizzate)
maintainer ha concesso 36h per verifiche/esperimenti/tuning. In coda: conferma operativa m-13,
M-TR A/B (complemento 471), e possibly parallel multi-start (speedup N-container, giustificato
da p(0-hard)>0). Dettaglio nel prossimo handoff incremental.

## Sopravvivenza
Catena sopravvissuta a 1 reboot (recovery batch, `6833b045`). I DONE marker + capture sopravvivono
ai reboot; i processi caffeinate/solve no. Ricondizione: `master-batch-recovery.sh` e' idempotente.
