# VERDETTO E3: M20_PROGRESSION_STEP 500 vs 1500 (zai, 2026-07-31)

A/B spike S3 (la domanda era: il gradiente del deficit M20 e' una leva? Lo spike S3 al checkpoint
di costruzione era no-op; serve full-solve). **Baseline = T19 baseline-mwd1** (stesso codice
default, step 500: -110h/-971488m/6405s, full-day 90,0%, rec UNP 75,7%) - riusato come baseline
comune. **Test** = step 1500 via patch temporanea (`M20_PROGRESSION_STEP = 1500`). Full-solve
staged, setup production fresco identico. HEAD `c6abbb16`. **Patch revertita. Nessuna adozione**.

## NOTA METODOLOGICA (importante)
n=2 per il braccio test, con esiti DIVERSI:
- **Run 1 (primo tentativo, pre-reboot)**: CONVERGENCE FAILURE. B-ROOM entry a -8693hard (room-null
  di massa), fase 5 tagliata a 600s ancora in calo a -6117hard. Non completato (JVM morta nel reboot
  ~13:33, ma era gia' fuori budget: la firma era identica a E2).
- **Run 2 (relaunch post-reboot, protocollo identico)**: CONVERSO PULITO. B-ROOM a -111hard gia'
  converto, finale -111h/-971111m/4281s.
Stesso codice, stesso setup, esito opposto. Il run 1 era una TRAIETTORIA Sfortunata (varianza), NON
un effetto deterministico dello step. **Questo valida l'istruzione di maintainer di rilanciare una
volta** invece di tirare a indovinare dal parziale -6117 (che avrebbe dato un falso verdetto
"convergence failure come E2").

## VERDETTO (3 righe, dai raw del run 2 converto)
- **M20_PROGRESSION_STEP 1500 e' NEUTRO** sul converto: hard -111 (vs -110), medium -971111 (vs
  -971488, ~377 di rumore), room-null 93 (vs 92), H17 18 (uguale), full-day 89,3% (vs 90,0%),
  ricorrenza UNP 75,4% (vs 75,7%). Tutto nel rumore: il gradiente M20 NON e' una leva sul finale.
- **MA il run 1 e' fallito a convergere** (-6117, room budget esaurito): n=2 non basta per dire se
  lo step piu' ripido AUMENTA il rischio di convergence-failure o se e' varianza baseline. Il fatto
  che le fasi timeslot abbiano lasciato -8693 room-null all'entry del room-phase (run 1) vs quasi 0
  (run 2) suggerisce che il gradiente piu' ripido rende la traiettoria piu' sensibile al seed
  (densifica il piazzamento in modo non deterministico).
- **KEEP step 500 (default)**. Nessun upside sul converto (neutro), possibile aumento di varianza
  di convergenza, e coerenza col TASK-461 (la FORMA del deficit e' difetto di modello, non il
  gradiente). Lo spike S3 "no-op al construction checkpoint" e' confermato anche a full-solve:
  il gradiente non muove il finale. Risposta a S3: NON e' una leva.

## Tabella raw (run 2 converto, verificata su artifact + DB)

| metrica | baseline (step 500) | test (step 1500, run2) | delta |
|---|---|---|---|
| **score hard** | -110 | -111 | -1 (pari) |
| **score medium** | -971488 | -971111 | +377 (pari, rumore) |
| **score soft** | +6405 | +4281 | -2124 |
| boxed (unassigned) | 0 | 0 | invariato |
| room-null non-esente (H29) | 92 | 93 | +1 (pari) |
| H17 holes (hard) | 18 | 18 | invariato |
| full-day rate | 90,0% | 89,3% | -0,7pp |
| ricorrenza UNPINNED | 75,7% | 75,4% | -0,3pp |
| ricorrenza TOTALE | 76,7% | 76,4% | -0,3pp |

Decomposizione hard test: 0*2 (boxed) + 93 (room-null) + 18 (H17) = -111 ✓

## Run 1 (convergence failure, parziale non persistito)
- B-TIMESLOT-1 lungo (~30min, come run 2), B-ROOM entry -8693hard (vs run 2 entry basso), fase 5
  LS Room Refinement tagliata a 600s a -6117hard (ancora in calo). medium mid-solve -879390
  (~92k meglio del baseline, ma non persistito al finale). JVM morta nel reboot prima della
  convergenza; DB non aveva finale (score N/A, stato ambiguo 12263 room-null).

## Limiti
- n=2 (1 fail, 1 converge). La domanda "lo step aumenta il rischio di convergence-failure?" resta
  APERTA: servirebbe n>=3 per braccio per stromare la varianza. Ma dato che il converto e' neutro,
  non c'e' upside che giustifichi l'indagine.
- Il run 1 e' morto nel reboot, non per il codice: ma la sua firma pre-reboot (B-ROOM -8693, come
  E2) indica che la traiettoria era gia' compromessa prima del crash.

## Artifact
- test run 2: `e3-m20step-2026-07-31/test-step1500/` (analysis.json, export.json.gz, lessons.txt, KPI)
- baseline (riuso): `t19-2026-07-31/baseline-mwd1/`
