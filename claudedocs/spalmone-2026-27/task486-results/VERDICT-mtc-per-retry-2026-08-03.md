# VERDICT TASK-486 (M-TC per-retry). A/B good-basin, 2026-08-03

> Risponde al mandato di maintainer (03/08): la ratifica M-TC DEFAULT-ON PIATTO e' RESPINTA
> (wall 2x su ogni run, anche good basin, anche M-TC inerte; vedi VERDICT-goodseed). TERZA opzione:
> M-TC ON solo nei tentativi di retry multi-start (attempt >=2), OFF al tentativo 1. Questo A/B
> misura la claim load-bearing: **armare la feature costa zero sui good basin?**

## Meccanismo (come funziona)

`planner.multistart.mtc-per-retry.enabled` (DEFAULT FALSE, opt-in prototype, stessa forma di
`isTagCapacityEnabled`: solo il letterale "true" arma). Subordinata a multi-start (DEFAULT ON):
se multi-start e' off, il wrapper non gira e non ci sono attempt >=2, quindi la leva e' inerte.

Quando ARMATA, il wrapper `runStagedCycleWithRetry` possiede `planner.tag-capacity.enabled` per
la durata del loop (save/restore come fa per GRASP), e `applyMtcForAttempt(attempt)` lo setta per
tentativo: attempt 1 a false (good basin, no 2x), attempt >=2 a true (bad basin, cura
deterministica room-tier). `mtcEnabledForAttempt(a) = a != 1` (puro, mirror di
`graspEnabledForAttempt`).

## Predizione DICHARATA PRIMA del risultato (ex-ante, falsificabile)

- **Arm (a) good basin**: mtc-per-retry ON + multi-start ON + source=input (deterministic). Il
  tentativo 1 e' il baseline deterministico (0-hard, VERDICT-goodseed riga 12). M-TC off al
  tentativo 1, quindi la LS non vede M-TC nel constraint set, quindi **wall ~1230s (NON 2x)**,
  hard 0, 1 tentativo (no retry, M-TC mai armata true durante il solve). Il segnale load-bearing
  e' il wall-clock: M-TC nel constraint set e' la firma ~2x; se feature wall simile a control wall,
  M-TC era off. Falsificabile: se feature wall > ~1800s (verso il 2x = 2435s), M-TC e' entrato nel
  constraint set del tentativo 1 e la feature e' rotta.
- **Arm (b) bad basin (grasp-seed-6 meccanica)**: la cura (M-TC on porta seed-6 a 0-hard) e' gia'
  misurata oggi in VERDICT-ab-mtc-seed6 (raw verificato: OFF -1hard H29, ON 0hard). La consegna
  per-retry setta lo STESSO flag (`planner.tag-capacity.enabled=true`) ad attempt >=2 via
  `applyMtcForAttempt`, quindi la cura e' consegnata per costruzione. Caveat onesto: sul seed
  corrente il tentativo 1 e' good (0-hard), quindi il loop multi-start si ferma a 1 e il retry NON
  scatta mai in-vivo. La feature e' un safety-net che si attiva solo su input/seed dove il
  tentativo 1 fallisce (che sul seed corrente non accade). La validazione della cura e' quindi per
  composizione (unit-test della consegna + misura piatta del payload), non in-vivo.

## Setup A/B

Due run, STESSO good basin deterministico (source=input), multi-start ON su ENTRAMBE (la feature
lo richiede; DEFAULT ON dal TASK-483). Sola variabile `-Dplanner.multistart.mtc-per-retry.enabled`:
- **control-off**: OFF (production di oggi: il wrapper non tocca `planner.tag-capacity.enabled`).
- **feature-on**: ON (attempt 1 forza `tag-capacity.enabled=false`; un retry la armerebbe true).

Banner verificato per run (MAVEN_OPTS: il -D raggiunge l'app JVM solo sulla riga `java -jar`):
`Multi-start staged retry: enabled=true` e `M-TC per-retry: armed=<false|true>`. Assenza del log
"restoring best attempt" significa good basin, 1 tentativo. `mtc_enabled_true_refreshes` conta se
M-TC ha mai letto enabled=true durante il solve (atteso 0 su entrambe).

Profilo `multistart` riusato (seed + import-calendario + pre-assign source=input dalla
badseed-frequency di oggi, 3090 pin; ogni solve-staged ripiana da zero). Script:
`scripts/benchmark/task486-mtc-per-retry.sh`. Raw: `task486-results/ab-mtc-per-retry-goodseed-{control-off,feature-on}/`.

## Evidenza pregressa (raw, oggi, stessa codebase); arm (b) cura

| run | score | wall | fonte raw |
|-----|-------|------|-----------|
| seed-6 M-TC OFF | -1hard (H29 room-null meccanica G04 T15) | 1794s | task480-results/badseed-frequency/grasp-seed-6/score.txt |
| seed-6 M-TC ON | **0hard** (curato) | 1975s | task455-results/ab-mtc-seed6/score.txt |
| goodseed M-TC OFF | 0hard | 1230s | task455-results/ab-mtc-goodseed-off/score.txt |
| goodseed M-TC ON (flat) | 0hard | **2435s (~2x)** | task455-results/ab-mtc-goodseed-on/score.txt |

La cura e' misurata: M-TC on converte seed-6 da -1hard a 0hard (relief della collisione meccanica,
overflow celle da 1 a 0). Il 2x wall e' misurato: M-TC nel constraint set raddoppia il wall anche
da inerte. TASK-486 e' il veicolo che consegna la cura (retry) senza il 2x (tentativo 1 off).

## Risultati A/B arm (a)

Entrambe le run: multi-start ON, source=input, stesso good basin deterministico. Sola variabile `mtc-per-retry`.

| run | hard | medium | wall | retry_fired | mtc_enabled_true_refreshes |
|-----|------|--------|------|------------|---------------------------|
| control-off (mtc-per-retry OFF) | **0** | -1.027.479 | 1673s | no | 0 |
| feature-on (mtc-per-retry ON) | **0** | -1.039.195 | 1558s | no | 0 |

Riferimento (oggi, flat): goodseed M-TC OFF 1230s / ON 2435s (~2x). Le due run qui sono ~1600s
entrambe, ben sotto la firma 2x (~2460s).

## Verdetto

**Arm (a) PREDIZIONE CONFIRMATA.** Armare mtc-per-retry e' zero-cost sui good basin:
- **hard 0 su entrambe** (good basin, come atteso).
- **wall NON 2x**: feature 1558s ≈ control 1673s (delta -115s entro varianza LS), entrambe
  ~1600s, ben lontano dalla firma 2x (~2460s del flat M-TC-ON). M-TC non e' entrato nel constraint
  set del tentativo 1.
- **1 tentativo su entrambe** (`retry_fired=no`): il loop multi-start si ferma al primo 0-hard,
  M-TC non viene mai armata true.
- **`mtc_enabled_true_refreshes=0` su entrambe** (anche la feature run): M-TC ha letto
  `enabled=false` a ogni refresh durante il solve. La feature ha forzato `tag-capacity.enabled=false`
  al tentativo 1 e il loop non e' mai arrivato a >=2, quindi M-TC e' restata inerte.

Il -11.716 medium e il -115s wall (feature vs control) sono varianza LS (due boot separati, random
seed LS non fissato tra run), non effetto feature: entrambe le run hanno M-TC inerte
(`mtc_enabled_true_refreshes=0`), quindi constraint set identico e nessuna leva diversa in gioco.
La firma da temere (2x wall, ~2460s) e' assente.

**Arm (b) PREDIZIONE: cura confermata per composizione (non in-vivo su questo seed).**
- La cura (M-TC on -> seed-6 0-hard) e' misurata oggi (raw: OFF -1hard H29 -> ON 0hard).
- La consegna per-retry setta lo STESSO flag (`tag-capacity.enabled=true`) ad attempt >=2 via
  `applyMtcForAttempt`, identico al flat A/B. La strategy `mtcEnabledForAttempt` e' unit-testata
  (1 false, >=2 true).
- Caveat onesto confermato: sul seed corrente il tentativo 1 e' good (0-hard), quindi il retry NON
  scatta in-vivo (lo mostrano entrambe le run con `retry_fired=no`). La feature e' un safety-net
  che si attiva solo su input/seed dove il tentativo 1 fallisce. Su questo seed la cura non e'
  esercitata in-vivo; la sua validita' poggia sulla misura piatta (payload) + l'unit test della
  consegna.

**Conclusione.** La feature combina correttamente i due fatti stabiliti: M-TC cura il bacino
room-tier (misurato) senza il costo 2x sui good basin (misurato qui, zero-cost). E' un prototipo
opt-in (DEFAULT OFF), subordinato a multi-start (DEFAULT ON): NESSUNA adozione default (regola di
maintainer). Ratifica a maintainer: la leva e' pronta come safety-net opt-in per input/seed dove il tentativo
1 e' un bad basin room-tier; sul seed corrente e' inerte (good basin, 1 tentativo).

## AC#4 (bad basin in-vivo, seguito)

### Perché la costruzione `-Dgrasp.seed=6 + multi-start ON` è inerta (verificato sul codice)

Il wrapper `runStagedCycleWithRetry` chiama `runOneMultiStartAttempt(attempt)` -> `applyGraspSeedForAttempt(attempt)`,
che al tentativo 1 setta `grasp.enabled = graspEnabledForAttempt(1) = (1 != 1) = false`. Quindi il
tentativo 1 è SEMPRE deterministico e sovrascrive qualunque `-Dplanner.construction.grasp.enabled=true
+ grasp.seed=6` esterno (salvato come origSeed e ripristinato a loop finito, mai usato al tentativo
1). Conseguenza: con la costruzione del prompt, il tentativo 1 è il good basin deterministico (0-hard,
confermato 2 volte oggi), il loop si ferma a 1, `retry_fired=NO`. AC#4 non è triggerabile così.

### Hook di test per triggerare AC#4 (`planner.multistart.test.force-grasp-seed=N`)

Per forzare un bad basin al tentativo 1 serve aggirare l'override deterministico del wrapper. Hook
null-default (zero behaviour change in produzione): quando armato, forza TUTTI i tentativi a un seed
GRASP fisso, così tentativo 1 e 2 sono lo STESSO basin e M-TC (off a 1, on a 2) è l'unica variabile.
Il tentativo 1 diventa il controllo within-run, il tentativo 2 la cura. Unit-testato
(`forceGraspSeedHookOverridesAttempt1Determinism`).

### Predizione DICHARATA PRIMA della run (ex-ante, falsificabile)

Run: `force-grasp-seed=6 + multi-start ON + mtc-per-retry ON + source=input`.
- **attempt 1** (seed-6 forzato, M-TC off): **-1hard** (H29 room-null meccanica G04, per flat
  seed-6 OFF VERDICT-ab-mtc-seed6). Il loop non si ferma (residual hard) -> retry.
- **attempt 2** (seed-6 forzato, M-TC on): **0hard** (cura, per flat seed-6 ON). Loop si ferma,
  pick-best = attempt 2.
- **Firma meccanica attesa**: `retry_fired=YES`, `mtc_enabled_true_refreshes>0` (M-TC legge
  enabled=true ai refresh del tentativo 2), score finale **0hard**.
- **Falsificabile**: se attempt 2 (M-TC on) NON cura (ancora -1hard), la consegna per-retry non
  cura in-vivo malgrado il flat A/B (es. la LS nello stato multi-start differisce dal flat). Esito
  onesto negativo. Se la costruzione seed-6 multi-start non genera il room-null (diversa dal flat),
  la cura non serve (esito onesto).

### Risultati AC#4

Run: `force-grasp-seed=6 + multi-start ON + mtc-per-retry ON + source=input`. Wall 3196s (2 tentativi).
Firma meccanica catturata nel boot log per-attempt (riga `staged multi-start attempt N done: mtc=X, score=Y`)
e nello score.txt (`attempts_run`, `mtc_enabled_true_refreshes`).

| segnale | atteso | misurato |
|---------|--------|----------|
| attempt 1 (seed-6, M-TC off) | -1hard | **-1hard/-1.351.993medium** (H29 room-null meccanica, = controllo within-run) |
| attempt 2 (seed-6, M-TC on, retry) | 0hard (cura) | **0hard/-1.387.811medium** |
| retry_fired | yes | **yes** (`attempts_run=2`) |
| mtc_enabled_true_refreshes | >0 | **4** (M-TC ha letto enabled=true a 4 refresh durante il tentativo 2) |
| score finale (pick-best) | 0hard | **0hard** (best=attempt 2, `restore_best=no` perche' best=last) |

**PREDIZIONE CONFIRMATA IN-VIVO.** Stesso basin seed-6 (forzato dal hook su entrambi i tentativi),
M-TC off al tentativo 1 (-1hard, il room-null resta), M-TC on al tentativo 2 (0hard, cura). La firma
per-attempt ("attempt 1 done: mtc=false, score=-1hard" -> "attempt 2 done: mtc=true, score=0hard") e'
la prova meccanica diretta che il retry e' scattato e M-TC e' entrato al tentativo >=2 consegnando la
cura. La cura e' attribuibile a M-TC (within-run A/B: stesso basin, sola variabile M-TC off/on).
Corrisponde al flat seed-6 A/B (OFF -1hard, ON 0hard): la consegna per-retry produce la stessa cura
del flat. AC#4 CHIUSA.

Nota medium (non impatta AC#4): lo score.txt riporta -1.387.811medium (score della solve, M-TC on),
mentre analysis.json (4521 byte, breakdown valido) riporta -1.886.869medium (re-score dell'endpoint
/analysis). Hard=0 in entrambi (la cura hard e' confermata da entrambi). Il medium differisce perche'
il finally del wrapper ripristina `tag-capacity.enabled` al valore di lancio (assente -> cleared),
quindi /analysis re-scored la griglia del tentativo 2 con M-TC off. E' un artifact di fidelity della
re-score dell'endpoint, non un problema di firma. (Discrepanza medium -507.058 non dovuta a M-TC:
apre un sospetto separato sulla fidelity dell'endpoint /analysis vs lo score della solve, da
tracciare come task diagnostico, non blocca AC#4.)

## Riproducibilita'

```
./scripts/benchmark/task486-mtc-per-retry.sh   # 2 run good basin, multi-start ON, source=input
./scripts/benchmark/task486-mtc-ac4-badbasin.sh # AC#4: force-grasp-seed=6, retry cura a 0-hard?
```
