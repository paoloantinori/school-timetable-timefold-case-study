# TASK-413 val413 A/B (laptop) - VERDICT descrittivo (2026-07-24/25)

Misura-only. Due solve HEAD (M32 "Template deviation" graduato per scarsita' + WeeklyTemplatePlanner
pre-solve) su profilo fresco, stesso stack adottato val414, caffeinate attivo, un solve alla volta.
Nessun giudizio di adozione: le cifre sono qui, la decisione e' di Fedora.

Fonti: `val413-run1/` e `val413-run2/` (capture-run.sh: score.txt, analysis.json, phases.json,
lessons.txt, kpi-{fill,daystart,resemblance,moda-knot,cadence}.txt, phase-markers-log.txt,
confronto-*.html). Baseline `task414-results/val414-run{1,2}` (stesso stack senza M32).

## 1. Score finale (hard/medium/soft) e box

| metric | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|
| hard | -1 | -2 | **0** | **-1** |
| medium | -1,805,530 | -1,842,517 | -2,088,075 | -2,122,087 |
| soft | +63,145 | +62,731 | -47,651 | -41,676 |
| box (unassigned) | 0 (12.301/12.301) | 0 | 0 (evicted 8) | 0 (evicted 5) |
| placed lessons | 12.301 | 12.301 | 12.301 | 12.301 |

Box 0 in tutti e quattro i run (phase-markers: "box repair ... still boxed 0, conflicts added 0";
hard 0/-1/-1/-2, nessun H0). Moda-knot 0 in tutti e quattro (G05 zero-slack risolto).

## 2. M32 medium totale e matches

M32 NON compare nel breakdown di `analysis.json` (la ricostruzione per-constraint di ScheduleService
non e' stata aggiornata per M32: gap di ripple di TASK-413). La sua contribuzione e' quindi
osservabile solo come RESIDUO = score-medium meno la somma dei medium listati nel breakdown.
Il match-count non e' recuperabile da questa capture (peso graduato per scarsita', non divisibile).

| metric | val413-run1 | val413-run2 |
|---|---|---|
| score medium | -2,088,075 | -2,122,087 |
| analysis breakdown sum (medium listati, M32 esclusa) | -1,817,099 | -1,850,515 |
| **M32 medium (residuo derivato)** | **~ -270,976** | **~ -271,572** |
| M32 matches | non disponibile (drill-down la omette) | non disponibile |

I medium listati (esclusa M32) sono confrontabili con val414: val413-run1 -1,817,099 vs val414-run1
-1,805,530 (delta ~-12k); val413-run2 -1,850,515 vs val414-run2 -1,842,517 (delta ~-8k). Cioe' il
medium "non-M32" e' sostanzialmente invariato; l'intero aumento del medium (~+271k/run) e' M32 stessa.

Breakdown medium listati, val413-run1 / val413-run2:
M20 -1.001.000 / -1.025.000; M22 -584.000 / -591.000; M20.2 -147.000 / -151.000; M15 -55.200 / -55.200;
M28 -10.900 / -10.100; M18 -10.050 / -9.550; M12 -8.629 / -8.351; M24 -85 / -91; M21 -66 / -64;
M30.1 -71 / -60; M31 -42 / -36; M29 -30 / -31; M25 -26 / -23; M30.2 -8 / -17; M27 -6 / -6; M5 +14 / +14.

Soft (figher, non giudizio): val413 e' negativo (-47,7k / -41,7k) vs val414 positivo (+63,1k / +62,7k),
swing ~-110k. Dominato da S1 teacher-gaps: val413-run1 S1 = -175.440 (1.129 match). Premio soft
(S7 +100.770, S17 +11.980, S13 +10.312, S12 +6.090) sotto il peso di S1.

## 3. Ricorrenza UNPINNED (totale e per gruppo)

| metric | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|
| ricorrenza TOTALE | 47,4% | 46,4% | 59,7% | 59,3% |
| **ricorrenza UNPINNED** | **37,9%** | **36,6%** | **53,5%** | **53,1%** |
| UNPINNED denominatore | 3.650/9.635 | 3.531/9.635 | 5.156/9.635 | 5.114/9.635 |

Per-gruppo UNPINNED (val413-run1 / val413-run2; val414-run1 riferimento da golden-cadence-reference.md):

| gruppo | val414-run1 | val413-run1 | val413-run2 |
|---|---|---|---|
| G01 | 32,5% | 64,6% | 66,0% |
| G02 | 37,2% | 63,9% | 62,1% |
| G03 | 46,5% | 59,8% | 59,4% |
| G04 | 42,9% | 53,7% | 54,4% |
| G05 | 36,9% | 49,5% | 48,7% |
| G06 | 38,2% | 55,7% | 55,3% |
| G07 | 43,7% | 50,4% | 50,9% |
| G08 | 41,5% | 52,1% | 50,8% |
| G09 | 28,5% | 41,6% | 41,0% |
| G10 | 28,5% | 44,8% | 45,1% |
| G11 | 31,0% | 40,8% | 39,5% |
| G12 | 34,8% | 47,7% | 47,9% |
| G13 | 38,9% | 57,2% | 55,1% |
| G14 | 60,0% | 59,1% | 61,7% |

Tutti i gruppi salgono tranne G14 (gia' alto, ~60%, invariato). I piu' deboli restano G09 / G10 /
G11 (39-46%) ma ~+12-16pt sopra val414. Salto aggregato UNPINNED +15-16pt (37-38% -> 53%).

## 4. Cadence KPI aggregati (n=244 pair, soglia burst 2.0x, min 10h)

| KPI | golden | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|---|
| burst>2.0x (share pair) | 41,6% | 86,9% | 89,3% | 79,1% | 79,1% |
| gap-weeks totali | 1.461 | 2.829 | 2.772 | 2.400 | 2.316 |
| gap-weeks / pair | 5,96 | 11,59 | 11,36 | 9,84 | 9,49 |
| burst ratio medio | 2,16 | 3,32 | 3,29 | 2,94 | 2,83 |

Tutti e tre gli aggregati cadence si spostano verso il golden (41,6% / 1.461 / 2,16) rispetto a
val414 (86,9-89,3% / 2.772-2.829 / 3,29-3,32).

## 5. Full-day / day-start / moda-knot

| metric | golden | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|---|
| full-day rate | 85,4% | 77,2% | 77,5% | 79,0% | 78,1% |
| day-start anchored | ~99,3-100% | 95,2% | 94,4% | 93,3% | 93,6% |
| moda-knot (stacked cell) | n/a | 0 | 0 | 0 | 0 |

Full-day +1-2pt verso golden. Day-start -1-2pt via dal golden (93,3/93,6 vs 95,2/94,4): unico KPI
che peggiora. Istogramma prima-ora run1: {8:2063, 9:119, 10:34, 11:5, 12:3}; run2 simile.

## 6. Tempi assoluti (solve e fasi)

Tempi wall CEST (locale laptop). Fasi in UTC (da phases.json).

**val413-run1**: solve 22:28:16 -> 23:36:47 CEST (~68,5 min).
- ph1 CH Lesson Placement   20:28:16->20:28:22Z (6,8s)   0 -> -1.223h/-2.296.112m/-92.950s
- ph2 LS Compactness        20:28:22->21:28:23Z (3600s) -> -16h/-2.071.709m/-64.304s
- ph3 LS RuinRecreate       21:28:23->21:30:24Z (120s)  no-change (-16h)
- ph4 Compaction            21:30:24->21:30:24Z (0,7s)  -16h -> -1h
- ph5 CH Room Assignment    21:30:24->21:30:25Z (0,5s)  no-change
- ph6 LS Room Refinement    21:30:25->21:36:47Z (382s)  -1h -> 0h/-2.088.075m/-47.651s

**val413-run2**: solve 23:41:31 -> 00:58:03 CEST (~76,5 min; +8min vs run1 per ph3 piu' lunga).
- ph1 CH Lesson Placement   21:41:31->21:41:37Z (6,4s)   0 -> -1.223h/-2.296.112m/-92.950s
- ph2 LS Compactness        21:41:37->22:41:38Z (3600s) -> -15h/-2.110.146m/-57.032s
- ph3 LS RuinRecreate       22:41:38->22:51:20Z (582s)  no-change sul hard (-15h), soft -57.032 -> -56.709
- ph4 Compaction            22:51:20->22:51:21Z (0,75s) -15h -> -1h
- ph5 CH Room Assignment    22:51:21->22:51:22Z (0,5s)  no-change
- ph6 LS Room Refinement    22:51:22->22:58:03Z (401s)  -1h -> -1h/-2.122.087m/-41.676s

Tutte le fasi isComplete=true in entrambi i run (terminazione naturale). Diffrenza di durata:
run2 ph3 582s vs run1 ph3 120s (stessa config, dinamica solver; entrambe terminano per
unimprovedSecondsSpentLimit). Hard finale 0 (run1) vs -1 (run2): stessa famiglia H17, variance
paragonabile a val414 (-1/-2).

## 7. Sintesi delle cifre (descrittiva, senza giudizio)

- Hard: 0 / -1 (val413) vs -1 / -2 (val414). M32 e' medium-only e non peggiora l'hard.
- Medium: -2,09M / -2,12M (val413) vs -1,81M / -1,84M (val414). L'aumento ~+271k/run coincide con la
  contribuzione derivata di M32; il medium non-M32 e' invariato (-1,82M / -1,85M vs -1,81M / -1,84M).
- Soft: -47,7k / -41,7k (val413) vs +63,1k / +62,7k (val414). Swing ~-110k, S1 teacher-gaps dominante.
- Ricorrenza UNPINNED: 53,5% / 53,1% (val413) vs 37,9% / 36,6% (val414). +15-16pt, ogni gruppo su.
- Cadence (burst>2 / gap-weeks / burst ratio): 79,1% / ~2,36k / 2,9 (val413) vs 86,9-89,3% / ~2,8k /
  3,3 (val414), verso golden 41,6% / 1,46k / 2,16.
- Full-day 79,0 / 78,1% (val413) vs 77,2 / 77,5% (val414), verso golden 85,4%.
- Day-start 93,3 / 93,6% (val413) vs 95,2 / 94,4% (val414), via dal golden (unico KPI in regresso).
- Box 0 / moda-knot 0 in tutti e quattro.
- Consistenza run1/run2 val413: bassa variance (hard 0/-1, recurrence 53,5/53,1, cadence 79,1/79,1),
  non la spread 9x temuta dal runbook.

## 8. Caveat fattuali (per Fedora)

1. M32 assente dal drill-down analysis (ScheduleService non aggiornato). Contribuzione ~-271k/run
   e' RESIDUO derivato (score-medium meno somma medium listati); match-count non recuperabile.
   Potrebbe in principio includere M3/M26/M-LA se non-zero e anch'esse omesse, ma M32 e' l'unico
   medium NUOVO di questo task e ordine di grandezza coerente tra i due run (270.976 / 271.572).
2. Soft-tier negativo in val413 vs positivo in val414: figura riportata, non valutata.
3. day-start regredisce ~1-2pt; ogni altro KPI staggerato verso il golden.
