# VERDICT TASK-419 (staged, post-fix H16b) + TASK-403 (early repair) - 2026-07-26

Descrittivo, nessun giudizio di adozione (decide Fedora). HEAD efd79a21 (fix H16b) + docs.
Runbook: caffeinate, heap 2g, profilo `dev` fresco a ogni run, UN solve alla volta,
`java -jar` con `-Dplanner.phase-transition-dump.enabled=true`. Wrapper a poll robusto
(retry fetch fail + conferma SOLVING_ACTIVE prima di accettare NOT_SOLVING).

## Sintesi

- **Fix H16b VERIFICATO**: lo smoke di stamattina (pre-fix) era FALLITO a -897hard dominato
  da H16b (-895hard). Il fix efd79a21 (split stage B al confine timeslot/room: B-TIMESLOT
  coi pin, RELEASE, poi B-ROOM muove solo stanze senza pin promossi) porta H16b a **0** e
  hard totale a -15/-16/-22 nei 3 run (H17+H0, non piu strutturale).
- **Ricorrenza target MET**: ricorrenza unpinned **73,8-74,1%** (target >=70; val408 52,8/53,6;
  +21pp). La metrica chiave si muove come atteso.
- **Erosione del template azzerata** (la vittoria centrale della 419): ricorrenza TOTALE
  costruzione ~76% -> finale ~76% (~0 erosione: i pin promossi tengono il template attraverso
  la LS). val408 erode 76% -> 59% (17pp persi). Dump phase-2-entry (template stamp) = 75,8%
  TOTALE, finale 75,7%.
- **Costo**: full-day ~77% (vs val408 79,9%, ~3pp in meno; non crollo). hard -15/-22 (H17
  gaps + H0 unassigned, varianza run-to-run; val408 -1).
- **Meccanica staged pulita** in tutti i 3 run: promoted 7132, authoritative 2651->2651
  intact, promotedpin=0 dopo RELEASE, 0 demozioni (timeslot-tier hard gia near-threshold).

## TASK-419 staged (post-fix): 3 run, flusso B-TIMESLOT/RELEASE/B-ROOM

Stage (tempi assoluti, run1 rappresentativo): A-CONSTRUCTION(4s,-1221hard) ->
PROMOTION(7132) -> B-TIMESLOT-1 (timeslot phases, pin tenuti, ~2260-3730s) ->
RELEASE (smarca 7132) -> B-ROOM (room CH+refinement, ~270-350s, senza pin promossi) ->
close. B-TIMESLOT finisce con score ~-914/-918hard ma timeslot-tier hard ~-15 (il resto
e room-hard che B-ROOM risolve); B-ROOM porta a -15/-16/-22hard finale, H16b=0.

| Metrica | smoke2 | run1 | run2 | val408 r1 | val408 r2 |
|---|---|---|---|---|---|
| hard | -15 | -16 | -22 | -1 | -1 |
| **H16b** | **0** | **0** | **0** | 0 | 0 |
| medium | -1.862.211 | -1.869.970 | -1.885.868 | -1.904.823 | -1.909.720 |
| soft | +1.450 | -1.074 | -4.703 | -43.915 | -47.086 |
| **ricorrenza unpinned** | **74,1%** | **73,8%** | **73,8%** | 52,8% | 53,6% |
| ricorrenza totale | 75,7% | 75,4% | 75,4% | 58,9% | 59,6% |
| full-day | 77,6% | 76,8% | 77,1% | 79,9% | 80,0% |
| day-start | 96,9% | 96,9% | 96,7% | 93,7% | 93,7% |
| sharpness pooled | 83,6% | 83,4% | 83,8% | 79,7% | 77,5% |
| burst>2x | 43,0% | 43,4% | 44,3% | 81,6% | 79,1% |
| gap-weeks | 1772 | 1721 | 1728 | 2405 | 2331 |
| M32 residuo (medium/match) | -125k/798 | -124k/793 | -124k/792 | n/a | n/a |
| B-TIMESLOT wall | 3336s | 3726s | 2262s | n/a | n/a |
| B-ROOM wall | 352s | 302s | 272s | n/a | n/a |
| promoted / demozioni | 7132 / 0 | 7132 / 0 | 7132 / 0 | n/a | n/a |

(golden: full-day 85,4%, burst>2x 41,6%.) Ricorrenza unpinned per-gruppo (smoke2,
rappresentativo; run1/run2 concordi): range 63,2% (G12) - 80,8% (G02); G11 ~64-77%,
G14 73,9%, G13 78,3%. Detail nei kpi-resemblance.txt per-run.

**Lettura staged**: ricorrenza +21pp e sharpness +5-6pp vs val408, day-start +3pp,
burst/gap-weeks lontani dallo scatter di val408 (burst 43% vs 80%, gap-weeks ~1750 vs ~2350).
Controprezzo: full-day -3pp, hard -15/-22 (H17/H0). M32 residuo stabile (~793 lezioni
deviano dal template, ~124k medium). Tempi: ciclo totale 43-67 min.

## TASK-403 early repair (CompactionPhase tra ph2/ph3) - FASE 2 di oggi

Domanda: "la fase 3 su stato hard-pulito smette di essere inerte?". SI. Anatomia fasi
(score hard alle transizioni), 403 run1 vs val408 run1:

| Fase | val408 (no CP) | 403 (con CP) |
|---|---|---|
| ph2 LS end | -31hard (3600s) | -29hard (app.log) -> CP -> -1hard (phases.json ph2 end) |
| **CompactionPhase** (custom ph2/ph3) | n/a | **-29 -> -1hard (<1s)** |
| ph3 RuinRecreate | start -31 end -31 (605s) **INERTE** | start **-1** end -1 (159s) hard-pulita |
| ph4 Compaction | **-31 -> -2hard (1s)** repair | -1 -> -1 (0s) **no-op** (gia fatto) |
| ph6 Room Refinement end | -1hard | 0hard |

val408 run2 conferma (ph3 -30->-30 inerte, ph4 -30->-1 repair). 403 run1/run2 finali:
0hard/-1.930.380medium e -1hard/-1.956.373medium (ricorrenza 52,6/53,1%, uguale a val408).
Il repair anticipato e low-cost sul finale (il solver converge allo stesso posto); il suo
effetto e tutto sulla transizione ph2/ph3 (fase 3 hard-pulita, ph4 diventa no-op).

## Note tecniche
- **Fix H16b (efd79a21)**: prima i pin promossi fissavano timeslot E room (implicita),
  congellando i blocchi same-subject-same-teacher in stanze diverse (-895hard). Ora
  B-TIMESLOT protegge solo i timeslot; dopo RELEASE, B-ROOM assegna le stanze liberamente
  -> H16b=0.
- **Wrapper fix in corsa**: il primo 403 run1 era INVALIDO (score 0/0/0): in modalita NORMAL
  `api.sh wait` (`fetch status || exit 1`) usciva rc=1 in una race al startup del solver ->
  cattura a vuoto + kill-app a 8s. Sostituito con poll robusto (retry + conferma
  SOLVING_ACTIVE). run1 rifatto, tutti i run successivi ok.
- Phase-transition dumps: flag funzionante, 10 file per run in `phase-transitions/`. I dump
  sono OVERWRITTEN ad ogni iterazione B -> riflettono l'ultima. capture-run dice "none"
  perche cerca nel path default `target/phase-transitions`, non nel dir custom.
- `export.json.gz` (146B) e solo il messaggio di stato di `api.sh export` (export reale 20M
  in /tmp); `lessons.txt` ha i dati completi.
- Smoke di stamattina (pre-fix, FALLITO) salvato in `val419-staged-smoke/` con
  `OSSERVAZIONI-smoke-failure.md` (diagnosi H16b che ha motivato il fix).
