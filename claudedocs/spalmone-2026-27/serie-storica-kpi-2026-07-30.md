# Serie storica KPI - metro unico (DailyActivityQuota) - 2026-07-30

**METRO DICHIARATO (in testa, come chiesto):** full-day rate e deep-deficit sono misurati
sulla fonte **DailyActivityQuota (aulaHours+labHours)**, la stessa che il solver targetizza
con M20. Strumento: `scripts/analyze/daily-fill-kpi.py` (allineato 2026-07-30, vedi
`task457-falsificazione` "METODOLOGIA RISOLTA"). Denominatore ~2099 giorni attesi (~1928
ex-G14). Fino al 2026-07-30 lo strumento leggeva `calendario_class_days.csv` (artefatto:
portava 14 weekday-closure phantoms + 4 no-session + G08 sovrastimati, denominatore ~2369);
i numeri di quella vecchia serie NON sono confrontabili con questi. Score / boxed / room-null
/ holes sono source-independent (output del solver, non della metrica full-day).

**Fonte expected per run:** `own` = export.json.gz del run stesso; `canon` = DailyActivityQuota
canonica (export 30b, stesso seed 2026/27, piano scuola identico) per i run senza export;
`canon(ERR)` = export del run presente ma non leggibile, usata canonica. La fonte expected e'
il piano scuola (input), uguale per tutti i run; cambia solo il placed.

## 1. Linea di base (run di riferimento della campagna)

I run che la campagna confronta. Constrained-teacher (447/30b/30d/gate446) cluster a full-day
~89,5-90,1% e boxed 3-5; default senza constrained-teacher (29b/30a) a ~86,5% e boxed 35
(saturazione T01); 30c (slot0-balancing) a 86,6% con 6 buchi H17 (la regressione tier).

| run | score | boxed | room-null | fullDay ex-G14 | deep | mar-giu | def.h | holes H17 | nota |
|---|---|---|---|---|---|---|---|---|---|
| 447 (riferimento, ct ON) | -195hard | 3 | 189 | 89,5% | 27 | 19 (70%) | 1048 | 0 | baseline campagna |
| 29b (TASK-456, default) | -237hard | 35 | 167 | 86,5% | 24 | 17 (71%) | 1101 | 0 | no ct: 35 boxed T01 |
| 30a (default, n=2) | -234hard | 35 | 164 | 86,6% | 24 | 17 (71%) | 1099 | 0 | no ct: 35 boxed |
| 30b (ct ON, confronto 447) | -169hard | 3 | 163 | 90,1% | 31 | 21 (68%) | 1043 | 0 | ct flag manuale |
| 30c (slot0-balancing ON) | -107hard | 6 | 89 | 86,6% | 29 | 22 (76%) | 1102 | 6gg/6slot | REFUTATO: 6 buchi H17 |
| 30d (ct DEFAULT, conferma) | -173hard | 3 | 167 | 89,8% | 28 | 19 (68%) | 1044 | 0 | ct default, no flag |
| combined-run1 (463+464, traguardo) | **0hard** | 0 | 0 | 89,5% | 25 | - | 164 | 0 | **HARD 0 n=2, primo orario feasible** (H17 chiusi da 463, room-null azzerati da 464) |
| combined-run2 (463+464, traguardo) | **0hard** | 0 | 0 | 90,4% | 27 | - | 156 | 0 | **HARD 0 n=2** (flag 463/464 entrambi ON, RIPRODUCIBILE non lucky basin) |
| gate-l5 (TASK-446 gate) | -4hard | 4 | - | 90,1% | 28 | 20 (71%) | 1042 | 0 | near-feasible |
| gate-finale (TASK-446) | -5hard | 5 | - | 89,6% | 26 | 18 (69%) | 1048 | 0 | near-feasible |
| final-confirm-435 (m-12) | -928hard | 14 | - | 86,3% | 30 | 17 (57%) | 1081 | 15gg/15slot | ct parziale |

`(ct = constrained-teacher; mar-giu = share deep-deficit nei mesi mar-giu.)`

**Conferma 30d vs 30b visiva:** hard -173/-169, boxed 3/3, room-null 167/163, full-day
89,8%/90,1%, deep 28/31, holes 0/0. Tutto nel rumore: il DEFAULT constrained-teacher coincide
con il flag manuale. La linea di base della campagna e' **447/30b/30d** (ct ON, full-day
~89,5-90,1%, boxed 3, holes 0).

**TRAGUARDO combined 463+464 (31/07 notte, HARD 0 n=2):** supera la linea di base. Con 463
(coordinatore lab-day, chiude i 18 H17 G13) + 464 (co-presenza, azzera i room-null) entrambi
ON, **hard 0** (boxed 0, room-null 0), full-day 89,5/90,4%, deep 25/27, holes 0, RIPRODUCIBILE
n=2 (non lucky basin). E' il primo orario feasible della campagna e il nuovo ceiling. Dettaglio
+ A/B in `task463-results/VERDICT-combined-2026-07-31.md` (464: +94/+95 hard vs OFF). Flag
463/464 restano DEFAULT OFF, adozione = maintainer (in attesa Q20 referente sulla semantica stanza).

## 2. Cosa e' cambiato vs la vecchia serie (class_days)

| run | fullDay old (class_days) | fullDay NEW (DailyActivityQuota) | shift |
|---|---|---|---|
| 447 | 87,0% | 89,5% | +2,5pp |
| 30b | 88,6% | 90,1% | +1,5pp |
| 30d | (n/a, nuovo) | 89,8% | - |
| deep 30b | 51 (77 raw) | 31 | -20 |

I numeri OLD (88,6% / 87,0% / 51-77 deep) che compaiono nei VERDICT/QUADRO/handoff pre-2026-07-30
appartengono alla serie class_days e NON vanno mischiati con questa. Dove un doc cita un numero
OLD, questo tabella e' il riferimento corretto.

## 3. Record completo (tutti i dump con lessons.txt utilizzabile)

Per tracciabilita'. I run di validazione/benchmark (task326-419, benchmark-386/391) sono
esperimenti di metaeuristica/config, non la linea di base: stanno qui per record, non per
confronto diretto coi run di campagna (config e seed possono differire nei dettagli).

| run | score | boxed | room-null | fullDay ex-G14 | deep | mar-giu | def.h | holes | fonte |
|---|---|---|---|---|---|---|---|---|---|
| certification-2026-07-29b | -237hard | 35 | 167 | 86,5% | 24 | 17 | 1101 | 0gg/0slot | own |
| certification-2026-07-30a | -234hard | 35 | 164 | 86,6% | 24 | 17 | 1099 | 0gg/0slot | own |
| certification-2026-07-30b | -169hard | 3 | 163 | 90,1% | 31 | 21 | 1043 | 0gg/0slot | own |
| certification-2026-07-30c | -107hard | 6 | 89 | 86,6% | 29 | 22 | 1102 | 6gg/6slot | own |
| certification-2026-07-30d | -173hard | 3 | 167 | 89,8% | 28 | 19 | 1044 | 0gg/0slot | own |
| task447-results/cert | -195hard | 3 | 189 | 89,5% | 27 | 19 | 1048 | 0gg/0slot | canon |
| m12-results/final-confirm | -41hard | 41 | - | 85,0% | 29 | 17 | 1125 | 0gg/0slot | canon |
| m12-results/final-confirm-435 | -928hard | 14 | - | 86,3% | 30 | 17 | 1081 | 15gg/15slot | own |
| m12-results/gate2-run430-433 | -4hard | 1 | - | 80,9% | 48 | 24 | 1222 | 3gg/3slot | canon |
| m12-results/gate2-run433only | -56hard | 56 | - | 86,8% | 31 | 19 | 1111 | 0gg/0slot | canon |
| m12-results/gate2-runA1 | -24hard | 24 | - | 82,6% | 41 | 22 | 1189 | 0gg/0slot | canon |
| m12-results/m12-run1 | 0hard | - | - | 86,0% | 58 | 21 | 1170 | - | canon |
| m12-results/m12-run2 | -54hard | 54 | - | 81,2% | 77 | 41 | 1260 | - | canon |
| m12-results/m12-run2bis | 0hard | - | - | 86,4% | 58 | 21 | 1168 | 0gg/0slot | canon |
| task446-results/gate-finale | -5hard | 5 | - | 89,6% | 26 | 18 | 1048 | 0gg/0slot | own |
| task446-results/gate-finale-run2 | -4hard | 4 | - | 89,5% | 30 | 21 | 1048 | 0gg/0slot | own |
| task446-results/gate-l5 | -4hard | 4 | - | 90,1% | 28 | 20 | 1042 | 0gg/0slot | own |
| task446-results/gate-l5-m36 | -4hard | 4 | - | 89,6% | 24 | 17 | 1047 | 0gg/0slot | canon |
| task446-results/smoke-l4 | -4hard | 4 | - | 90,1% | 28 | 20 | 1041 | 0gg/0slot | own |
| task405-results/val405-run1 | 0hard | - | - | 83,4% | 143 | 85 | 1439 | - | own |
| task405-results/val405-run2 | 0hard | - | - | 83,7% | 124 | 80 | 1416 | - | own |
| task405b-results/val405b-run1 | 0hard | - | - | 83,9% | 141 | 80 | 1433 | - | own |
| task405b-results/val405b-run2 | -1hard | - | - | 83,8% | 126 | 78 | 1405 | - | own |
| task406-results/val406-run1 | -1hard | - | - | 87,4% | 83 | 39 | 1190 | - | canon(ERR) |
| task406-results/val406-run2 | 0hard | - | - | 87,1% | 87 | 38 | 1182 | - | canon(ERR) |
| task408-results/val408-run1 | -1hard | - | - | 88,3% | 68 | 33 | 1134 | - | canon |
| task408-results/val408-run2 | -1hard | - | - | 88,4% | 72 | 36 | 1137 | - | canon |
| task410-results/val410-run1 | -10hard | 9 | - | 86,3% | 105 | 36 | 1248 | - | canon |
| task410-results/val410-run2 | -12hard | 11 | - | 85,3% | 113 | 37 | 1268 | - | canon |
| task413-results/val413-run1 | 0hard | - | - | 87,1% | 100 | 44 | 1252 | - | canon |
| task413-results/val413-run2 | -1hard | - | - | 85,9% | 109 | 47 | 1271 | - | canon |
| task414-results/val414-run1 | -1hard | - | - | 84,8% | 108 | 43 | 1252 | - | canon |
| task414-results/val414-run2 | -2hard | - | - | 85,3% | 105 | 43 | 1247 | - | canon |
| task326-results/val326-b1800-run1 | -4hard | - | - | 87,0% | 70 | 35 | 1143 | - | canon |
| task326-results/val326-b1800-run2 | 0hard | - | - | 86,9% | 82 | 40 | 1167 | - | canon |
| task326-results/val326-b600-run1 | -10hard | - | - | 85,7% | 79 | 44 | 1181 | - | canon |
| task326-results/val326-b600-run2 | -8hard | - | - | 84,2% | 79 | 37 | 1187 | - | canon |
| task326-results/val326-b600-run3 | -6hard | - | - | 85,0% | 84 | 46 | 1182 | - | canon |
| task398-results/val398-run1 | -6hard | 5 | - | 85,3% | 106 | 81 | 1338 | - | canon |
| task398-results/val398-run2 | -6hard | 6 | - | 84,2% | 105 | 78 | 1349 | - | canon |
| task401-results/val401-run1 | -1hard | - | - | 85,6% | 113 | 85 | 1335 | - | canon |
| task401-results/val401-run2 | -1hard | - | - | 85,7% | 106 | 83 | 1335 | - | canon |
| task403-results/val403-early-run1 | 0hard | - | - | 88,7% | 71 | 36 | 1139 | - | canon |
| task403-results/val403-early-run2 | -1hard | - | - | 87,6% | 75 | 35 | 1156 | - | canon |
| task419-results/val419-staged-run1 | -16hard | 2 | - | 84,3% | 91 | 52 | 1221 | - | canon |
| task419-results/val419-staged-run2 | -22hard | 9 | - | 84,6% | 90 | 49 | 1222 | - | canon |
| task419-results/val419-m32x3-run1 | -1hard | - | - | 87,5% | 76 | 34 | 1152 | - | canon |
| task419-results/val419-m32x3-run2 | -2hard | - | - | 87,6% | 77 | 36 | 1153 | - | canon |
| task419-results/val419-train-run1 | -3hard | 3 | - | 82,9% | 75 | 34 | 1214 | 0gg/0slot | canon |
| task419-results/val419-train-run2 | 0hard | - | - | 82,2% | 73 | 36 | 1217 | 0gg/0slot | canon |
| validation-393-results/val-run1 | -1hard | - | - | 85,5% | 108 | 84 | 1335 | - | canon |
| validation-393-results/val-run2 | 0hard | - | - | 85,2% | 111 | 84 | 1356 | - | canon |
| benchmark-386-results/arm-A..F | (8 run, fullDay 71,9-86,7%, deep 101-161) | | | | | | | | canon |
| benchmark-391-results/arm-* | (6 run, fullDay 83,2-87,0%, deep 97-111) | | | | | | | | canon |

### Run del 31/07 (aggiornamento coda D, metro DailyActivityQuota verificato)

Misurati questa sessione con `scripts/analyze/daily-fill-kpi.py` (metro dichiarato in testa) +
`analysis.json` per score/boxed(H0)/room-null(H29). I run combined sono anche in sezione 1
(traguardo). boxed=0 dove non esposto (H0 a 0 match, consistente con lo score hard).

| run | score | boxed | room-null | fullDay ex-G14 | deep | holes H17 | fonte |
|---|---|---|---|---|---|---|---|
| task463-results/combined-run1 | 0hard | 0 | 0 | 89,5% | 25 | 0 | own |
| task463-results/combined-run2 | 0hard | 0 | 0 | 90,4% | 27 | 0 | own |
| task464-results/off-baseline | -112hard | 0 | 94 | 89,5% | 10 | 18 (G13) | own |
| task464-results/on-run1 | -18hard | 0 | 0 | 89,3% | 9 | 18 (G13) | own |
| task464-results/on-run2 | -17hard | 0 | 0 | 89,3% | 13 | 17 (G13) | own |
| task451-results/ab-firsthour-on-run1 | -108hard | 0 | 90 | 89,7% | 10 | 18 (G13) | own |
| task451-results/ab-firsthour-on-run2 | -109hard | 0 | 91 | 89,5% | 12 | 18 (G13) | own |
| t19-2026-07-31/baseline-mwd1 | -110hard | 0 | 92 | 90,0% | 15 | 18 (G13) | own |
| t19-2026-07-31/test-mwd2 | -68hard | 1 | 42 | 88,8% | 12 | 24 | own |
| bench-btimeslot1/arm-A-control (tabu7) | -121hard | - | - | 89,2% | 16 | - | own |
| bench-btimeslot1/arm-H-tabu3 | -121hard | - | - | 88,7% | 12 | - | own |
| bench-btimeslot1/arm-B-la400 | -125hard | - | - | 88,3% | 12 | - | own |
| bench-portfolio-n3 | -120hard | - | - | 89,1% | 11 | - | own |

Note: i bracci benchmark (btimeslot1 A/H/B, portfolio-n3) non espongono boxed/room-null/holes
(config senza fase stanze o analysis.json non comparabile, come gli altri run benchmark sopra).
`holes H17` per i run non-combined e' il pattern G13-venerdi' (463 lo chiude solo nei combined).
def.h e mar-giu non re-computati qui (lo script monouso `/tmp/serie_storica_kpi.py` e' stale;
per i combined: deficit 164h/156h). VERDETTO 31/07: combined hard 0 e' il nuovo ceiling;
TASK-464 +94/+95 hard netto (room-null 94->0); TASK-451 boxed 3->0 + room-null 167->90 (costo:
18 H17 G13); T19 mwd2 netto-negativo sui KPI (medium -54k) anche se hard migliore.

(`canon(ERR)` = export.json.gz del run presente ma illeggibile, usata canonica. Le righe
benchmark-386/391 sono compattate: 14 run di metaeuristica, score/fullDay nel record grezzo.)

### Run del 01/08 (ab-cps-on-run1, M-CPS ratified reference)

`ab-cps-on-run1 (463+464+M-CPS ON, source=golden) — ratified reference` (2026-08-01). Run A/B
TASK-469 (M-CPS ON) + combined ratified flags (463 lab-day-consolidation + 464 co-presenza + 435
constrained-teacher + 451 first-hour, tutti ON, source=golden). M-CPS e' medium quindi non apre
hard: hard resta 0 come combined-run2, il curriculum post-stage converge verso il target 6h
(G08 7->6 centra il target, G10 12->9, G11 24->21 con cautela). Rifinitura G14->AULA
G13 (TASK-477) e cure hard-0 residuali tracciate altrove. Detail: `task469-results/ab-cps-on-run1/VERDICT-2026-08-01.md`.

| run | score | boxed | room-null | fullDay ex-G14 | deep | holes H17 | fonte |
|---|---|---|---|---|---|---|---|
| task469-results/ab-cps-on-run1 | 0hard | 0 | 0 | 90,8% | 24 | 0gg/0slot | own |

Note: ricorrenza UNPINNED 75,6% (7107/9404, kpi-resemblance), day-start 98,1% (2166/2209,
kpi-daystart), medium -963616 (score). buchi interni 0 (kpi-holes). Score completo:
`0hard/-963616medium/7997soft`. full-day 90,9% con G14 nel denominatore (1778/1957); la colonna
usa l'ex-G14 (1751/1928 = 90,8%) come il resto della tabella. Questo run e' il nuovo ceiling
ratificato (maintainer 01/08 sera): hard 0 n=1 sul profilo combined+M-CPS, costo zero sui KPI di
guardia vs combined-run2 (full-day +0,4pp, ricorrenza flat -0,2pp, buchi 0).

## 4. Note

- **Ricorrenza e day-start** NON sono in questa tabella: sono metriche source-independent
  (leggono il placed + weekday, non il expected-hours), quindi la riconciliazione non le tocca.
  Restano nei `kpi-resemblance.txt` / `kpi-daystart.txt` di ogni run (invariati).
- **boxed / room-null** sono None (`-`) per i run di validazione/benchmark: quei run usavano
  config solver senza la fase stanze o con analysis.json che non espone H0/H29 nello stesso
  formato. Non e' un dato mancante della metrica, e' che quei run non assegnarono stanze.
- **Come rigenerare:** `python3 /tmp/serie_storica_kpi.py` (script monouso; la fonte di
  verita' e' `daily-fill-kpi.py` per full-day/deep e ogni `analysis.json` per score/boxed).
- I numeri di sezione 1 sono la linea di base credibile della campagna. Se un VERDICT/QUADRO
  cita un full-day o deep, rimanda qui invece di duplireferente.
