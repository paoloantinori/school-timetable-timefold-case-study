# TASK-408+416 val408 A/B (laptop) - VERDICT descrittivo (2026-07-25)

Misura-only. Due solve HEAD sul treno 2026-07-25 (TASK-408 "giorni no-session chiusi per
modello" + fix typo chiusura 2027-01-22, TASK-416 "M32 reference-mode", ripple TASK-376/418),
profilo fresco, heap 2g, caffeinate attivo, un solve alla volta. Flag phase-transition-dump
ON (`JAVA_TOOL_OPTIONS=-Dplanner.phase-transition-dump.enabled=true`, verificato "Picked up
JAVA_TOOL_OPTIONS" sulla JVM fork). Nessun giudizio di adozione: le cifre sono qui, la
decisione e' di Fedora.

Fonti: `val408-run1/`, `val408-run2/` (capture-run.sh: score.txt, analysis.json, phases.json,
lessons.txt, kpi-{fill,daystart,resemblance,moda-knot,cadence,sharpness}.txt,
phase-markers-log.txt, confronto-*.html, phase-transitions/*-resemblance.txt). Baseline
`task413-results/val413-run{1,2}` (stesso stack con M32 ma PRIMA di questo treno: M32 assente
dal breakdown analysis) e `task414-results/val414-run{1,2}` (M32 off).

## 0. Seed signature post-TASK-408 (profilo fresco, identica nei 2 run)

Il fix typo chiusura `2026-01-22 -> 2027-01-22` rende quella data una chiusura reale:
**timeslot 1368** (era 1376, 172->171 giorni scolastici).Gli altri conteggi seed sono
invariati (course 273, lesson 12581, teacher 27, studentgroup 14, dailyactivityquota 2393,
room 18). `verify-baseline.sh` non e' stato aggiornato dal treno: il gate `seed` fallisce
su timeslot (1376 vs 1368) e il gate `import` su stage/off-grid (1800/280 vs 1785/295);
MODA 866 continua a passare. Questi 3 fail sono la firma attesa di TASK-408 (15h di stage
del venerdi' 2027-01-22 spostate da pin a off-grid per l'assenza di Timeslot quel giorno),
NON regression. Non ho toccato lo script (misura-only; update di competenza Fedora).

## 1. Score finale (hard/medium/soft) e box

| metric | val414-r1 | val414-r2 | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---|---|---|---|---|---|
| hard | -1 | -2 | 0 | -1 | **-1** | **-1** |
| medium | -1.805.530 | -1.842.517 | -2.088.075 | -2.122.087 | **-1.904.823** | **-1.909.720** |
| soft | +63.145 | +62.731 | -47.651 | -41.676 | -43.915 | -47.086 |
| box (unassigned) | 0 | 0 | 0 | 0 | 0 | 0 |
| placed lessons | 12.301 | 12.301 | 12.301 | 12.301 | 12.301 | 12.301 |

Box 0 in entrambi i run (phase-markers: "box repair ... still boxed 0, conflicts added 0";
run1 boxed 28->placed 28 evicted 14; run2 boxed 27->placed 27 evicted 13; val413 boxava 15).
Hard finale -1/-1 = un singolo match H17 (Vertically compact), nessun H0. Moda-knot 0 in
entrambi (G05 zero-slack risolto). Hard e' della stessa famiglia di val413 (0/-1) e val414
(-1/-2); medium scende di ~183k/212k vs val413 (vedi sezione 3).

## 2. M32 nel breakdown analysis (TASK-416) e analysis-vs-score diff

**TASK-416 superato in entrambi i run**: M32 ora compare nel breakdown di `analysis.json`
con medium E match-count (in val413 era assente, derivabile solo come residuo ~-271k).

| metric | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---|---|---|---|
| M32 medium (breakdown) | (assente) | (assente) | **-279.751** | **-284.914** |
| M32 matches | n/d | n/d | **2.106** | **2.136** |
| analysis breakdown sum (medium) | -1.817.099 | -1.850.515 | -1.904.823 | -1.909.720 |
| score medium | -2.088.075 | -2.122.087 | -1.904.823 | -1.909.720 |
| **analysis-vs-score diff** | ~-271k (M32 residuo) | ~-272k | **0** | **0** |

In val408 la somma dei medium listati coincide ESATTAMENTE col medium dello score (diff 0):
il drill-down di ScheduleService e lo score non divergono piu'. Ordine di grandezza di M32
coerente tra i due run (-279.751 / -284.914) e con il residuo derivato di val413 (~-271k):
il fix reference-mode (TASK-416) e l'harness di val413 si validano a vicenda.

## 3. TASK-408: lezioni su 2027-01-22 + composizione medium (M20 phantom-days)

Lezioni piazzate su 2027-01-22 (venerdi', golden 100% chiuso): **0 in entrambi i run**
(val413-r1: 61, val413-r2: 60, val414-r1: 56). Il giorno e' ora vuoto come il golden.

Composizione medium (val408-r1 / val408-r2; val413-r1 riferimento):

| constraint | val413-r1 | val408-r1 | val408-r2 |
|---|---:|---:|---:|
| M20 - Daily quota fill | -1.001.000 | **-856.000** | **-835.000** |
| M22 - Calendar compactness | -584.000 | -563.000 | -558.000 |
| M32 - Template deviation | (residuo ~-271k) | -279.751 | -284.914 |
| M20.2 - Empty expected day | -147.000 | -127.000 | -146.000 |
| M15 - Same-day same-subject consec. | -55.200 | -48.000 | -54.000 |
| M28 - Afternoon stage-only | -10.900 | -12.900 | -14.000 |
| M18 - Day start first open slot | -10.050 | -9.550 | -9.700 |
| M12 - Overflow beyond target | -8.629 | -8.319 | -7.771 |
| (altri medium, tutti <100) | ... | ... | ... |

Il calo medium vs val413 (~-183k run1 / -212k run2) e' concentrato dove previsto dal discovery:
**M20 -145k/-166k** (M20 non premia piu' il riempimento dei giorni fantasma/no-session, ora
trattati come target=0 da `dailyQuota()`/`expectedHoursCapFor`), piu' M22 -21k/-26k e M20.2
-20k (run1). M32 e' invariato (~-280k): il meccanismo TASK-408 non tocca M32, pulisce M20.
Il medium "non-M32 non-M20" e' sostanzialmente invariato.

## 4. Full-day rate totale e per gruppo (la leva phantom ~81% del deficit)

| metric | golden | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---:|---:|---:|---:|---:|
| full-day rate totale | 85,4% | 79,0% | 78,1% | **79,9%** | **80,0%** |

Per-gruppo (val408-r1 / val408-r2; val413-r1 riferimento; golden da discovery):

| gruppo | golden | val413-r1 | val408-r1 | val408-r2 | Δ r1 vs val413 |
|---|---:|---:|---:|---:|---:|
| G01 | 100% | 98% | 100% | 100% | +2 |
| G02 | 100% | 97% | 100% | 100% | +3 |
| G03 | 88% | 91% | 88% | 88% | -3 |
| G05 | 100% | 99% | 100% | 100% | +1 |
| G06 | 100% | 88% | 91% | 94% | +3 |
| G07 | 100% | 97% | 96% | 99% | -1 |
| G09 | 95% | 76% | 69% | 66% | **-7** |
| G10 | 94% | 80% | 77% | 80% | -3 |
| G11 | 99% | 98% | 97% | 97% | -1 |
| **G12** | 85% | 63% | **74%** | **75%** | **+11** |
| G13 | 89% | 85% | 87% | 81% | +2 |
| G14 | 0% | 0% | 0% | 0% | 0 |

**Risultato MISTO, non la "salita netta sui gruppi stage-window" attesa.** G12 e' il colpo
pulito (+11/+12pt, coerente col discovery: le sue 64h fantasma = 100% del suo deficit, e
infatti G12 sale verso il golden 85%). Ma **G09 regredisce -7/-10pt** (76->69/66,
lontano dal golden 95%) e **G10 -3/0**. G11 (gia' al 97-98%, vicino al soffitto) non
muove. Cioe': la chiusura dei giorni no-session aiuta G12 ma le ore disperse NON
riempiono affidabilmente i giorni in deficit di G09 / G10 (si ridistribuiscono su
giorni gia' pieni o vengono assorbite da altri obiettivi LS). Il netto aggregato e' +0,9/+1,9pt
solo. Leva phantom confermata sul meccanismo (M20 -145k) ma non si traduce in guadagno
full-day agregato.

## 5. Ricorrenza UNPINNED (totale e per gruppo)

| metric | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---:|---:|---:|---:|
| ricorrenza TOTALE | 59,7% | 59,3% | 58,9% | 59,6% |
| **ricorrenza UNPINNED** | 53,5% | 53,1% | 52,8% | 53,6% |
| UNPINNED denominatore | 5.156/9.635 | 5.114/9.635 | 5.085/9.635 | 5.167/9.635 |
| blocchi >=2h | 2.111 | 2.111 | 2.140 | (n/d) |

Per-gruppo UNPINNED (val408-r1 / val408-r2; val413-r1 riferimento):

| gruppo | val413-r1 | val408-r1 | val408-r2 |
|---|---:|---:|---:|
| G01 | 64,6% | 61,5% | 66,1% |
| G02 | 63,9% | 62,8% | 60,3% |
| G03 | 59,8% | 57,4% | 60,1% |
| G05 | 49,5% | 46,2% | 51,5% |
| G06 | 55,7% | 56,4% | 58,7% |
| G07 | 50,4% | 54,2% | 51,9% |
| G09 | 41,6% | 41,8% | 38,3% |
| G10 | 44,8% | 44,6% | 46,3% |
| G11 | 40,8% | 38,0% | 39,3% |
| G12 | 47,7% | 47,4% | 47,1% |
| G13 | 57,2% | 55,1% | 54,6% |
| G14 | 59,1% | 66,1% | 72,2% |

Ricorrenza UNPINNED sostanzialmente invariata (52,8/53,6 vs 53,5/53,1): il treno non muove
la ricorrenza finale in modo netto (M32 reference-mode rende M32 visibile ma non cambia la
sua azione sul solver). Bassa variance run1/run2.

## 6. Cadence e sharpness KPI

Cadence (n=244 pair, soglia burst 2.0x, min 10h):

| KPI | golden | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---:|---:|---:|---:|---:|
| burst>2.0x (share pair) | 41,6% | 79,1% | 79,1% | 81,6% | 79,1% |
| gap-weeks totali | 1.461 | 2.400 | 2.316 | 2.405 | 2.331 |
| gap-weeks / pair | 9,84 | 9,84 | 9,49 | 9,86 | 9,55 |
| burst ratio medio | 2,16 | 2,94 | 2,83 | 2,97 | 2,93 |

Cadence sostanzialmente invariata vs val413 (79,1% run2 identico; run1 81,6% leggermente
peggiore), sempre lontano dal golden (41,6%).

Sharpness fingerprint (TASK-411, KPI NUSSO di questo treno - val413 non ha
`kpi-sharpness.txt`, quindi nessun baseline val413; riferimento golden pinnato):

| KPI | golden | val408-r1 | val408-r2 |
|---|---:|---:|---:|
| nitidezza pooled | 93,4% | 79,7% | 77,5% |
| nitidezza macro (media gruppi) | 93,8% | 76,8% | 74,8% |

Gruppi nitidi (100%): G01, G02, G03, G05 in run1. Meno nitidi: G04 (62,9%),
G10 (58,1%), G14 (12,5%, atteso - corso corto). Riportato vs golden, non vs val413.

## 7. Day-start e moda-knot

| metric | golden | val413-r1 | val413-r2 | val408-r1 | val408-r2 |
|---|---:|---:|---:|---:|---:|
| day-start anchored | ~99,3% | 93,3% | 93,6% | 93,7% | 93,7% |
| moda-knot (stacked cell) | n/a | 0 | 0 | 0 | 0 |

Day-start ~invariato (+0,4pt). Istogramma prima-ora run1 {8:2053, 9:109, 10:34, 11:9};
run2 {8:2053, 9:104, 10:34, 11:5}. Moda-knot 0 in entrambi.

## 8. Erosione per fase (AC#5 TASK-415, phase-transition dump - prima misura live)

Ricorrenza UNPINNED al boundary di ogni fase (dump = stato di FINE della fase precedente;
`phase-2` = fine costruzione CH, `phase-3` = fine LS Compactness, ecc.; il final = fine
phase-6 LS Room Refinement, da kpi-resemblance):

| boundary (fine fase) | val408-r1 | val408-r2 |
|---|---:|---:|
| costruzione CH (phase-2 dump) | 74,3% | 74,3% |
| dopo LS Compactness (phase-3 dump) | 52,8% | 53,7% |
| dopo LS RuinRecreate (phase-4 dump) | 52,8% | 53,7% |
| dopo Compaction (phase-5 dump) | 52,8% | 53,6% |
| dopo CH Room (phase-6 dump) | 52,8% | 53,6% |
| **final (kpi-resemblance)** | 52,8% | 53,6% |

**L'erosione (~-21pt) e' concentrata interamente in fase 2 (LS Compactness)**: 74,3% a fine
costruzione -> 52,8/53,7% dopo LS Compactness, poi stabile through fasi 3-6 (RuinRecreate,
Compaction, CH Room, LS Room Refinement non erodono ulteriormente). Coerente col rationale
M32 (TASK-413/414): la LS erode il template perche' spostare una cella costa poco; M32 e'
attivo e ora visibile (~-280k/2.1k match) ma non arresta l'erosione di fase 2 in questi run.

## 9. Tempi assoluti (solve e fasi)

Tempi wall CEST (locale laptop). Fasi: end-score da phases.json.

**val408-run1**: solve 12:15:07 -> 13:31 CEST (~76 min).
- ph1 CH Lesson Placement   end -1.221h/-2.128.596m/-97.705s (construction CH complete 12:15:07)
- ph2 LS Compactness (3600s) end -31h/-1.894.553m/-59.866s
- ph3 LS RuinRecreate       end -31h/-1.894.064m/-59.471s (no-change hard)
- ph4 Compaction            end -2h/-1.908.032m/-64.505s  (box repair 13:25:15)
- ph5 CH Room Assignment    end -2h/-1.908.032m/-64.505s  (no-change)
- ph6 LS Room Refinement    end -1h/-1.904.823m/-43.915s  (FINAL)

**val408-run2**: solve 13:35:14 -> ~14:50 CEST (~75 min).
- ph1 CH Lesson Placement   end -1.221h/-2.128.596m/-97.705s
- ph2 LS Compactness (3600s) end -30h/-1.896.926m/-62.396s
- ph3 LS RuinRecreate       end -30h/-1.896.926m/-62.396s (no-change)
- ph4 Compaction            end -1h/-1.912.931m/-67.956s  (box repair 14:36:49)
- ph5 CH Room Assignment    end -1h/-1.912.931m/-67.956s  (no-change)
- ph6 LS Room Refinement    end -1h/-1.909.720m/-47.086s  (FINAL)

Tutte le fasi isComplete=true in entrambi i run (terminazione naturale). Phase-1 end-score
identico byte-per-byte tra i run (-1.221h/-2.128.596m/-97.705s): costruzione deterministica.

## 10. Sintesi delle cifre (descrittiva, senza giudizio)

- **Hard**: -1/-1 (val408) vs 0/-1 (val413) vs -1/-2 (val414). Stessa famiglia H17, box 0.
- **Medium**: -1.905M/-1.910M (val408) vs -2.088M/-2.122M (val413). -183k/-212k, concentrato
  in M20 (-145k/-166k: non premia piu' i giorni fantasma/no-session) + M22/M20.2. M32
  invariato (~-280k).
- **TASK-416 OK**: M32 visibile nel breakdown (-279.751/2.106 e -284.914/2.136), analysis-vs-
  score diff = 0 (era ~-271k in val413).
- **TASK-408 OK sul meccanismo**: 0 lezioni su 2027-01-22 (61/60 in val413), M20 scende.
- **Full-day**: 79,9/80,0% (val408) vs 79,0/78,1% (val413), +0,9/+1,9pt netto. **Per-gruppo
  misto**: G12 +11/+12 (colpo pulito), G06 +3/+6, ma G09 -7/-10 e G10 -3/0.
  La leva phantom confermata su G12 non si traduce in salita netta agregata.
- **Ricorrenza UNPINNED**: 52,8/53,6% (val408) vs 53,5/53,1% (val413), invariata.
- **Cadence**: 81,6/79,1% burst (val408) vs 79,1/79,1% (val413), ~invariata, lontano golden.
- **Sharpness** (KPI nuovo, no baseline val413): pooled 79,7/77,5%, macro 76,8/74,8% vs golden 93,4/93,8%.
- **Day-start**: 93,7/93,7% (val408) vs 93,3/93,6% (val413), +0,4pt. **Moda-knot**: 0/0.
- **Erosione per fase** (AC#5, prima live): 74,3% (costruzione) -> 52,8/53,7% (dopo LS
  Compactness), poi stabile. Erosione concentrata in fase 2.
- **Consistenza run1/run2**: bassa variance (hard -1/-1, medium -1.905M/-1.910M, recurrence
  52,8/53,6, full-day 79,9/80,0). Non la spread 9x del runbook.

## 11. Caveat fattuali (per Fedora)

1. **Full-day non sale netto come atteso**: la_SEP_rationale del discovery (81% del deficit
   sui gruppi puliti era ore-fantasma) e' meccanicamente confermata (M20 -145k, 2027-01-22=0,
   G12 +11), ma le ore disperse non riempiono i deficit di G09 (-7/-10) / G10. Vale la
   pena indagare dove vanno le ore di G09/G10 (box count piu' alto in compaction, 28/27
   vs 15 di val413, ma final box 0;possibile ridistribuzione su giorni gia' pieni o trade-off
   LS con M32/M15). Riportato, non risolto.
2. **Sharpness senza baseline val413**: KPI introdotto nel treno (TASK-411), val413 non lo ha
   generato. Confronto solo vs golden (93,4%).
3. **verify-baseline.sh non aggiornato dal treno**: 3 fail attesi (timeslot 1368, stage 1785,
   off-grid 295) = firma TASK-408, non regression. Update di competenza Fedora.
4. **Phase-transition dump**: flag funzionante (5 dump per run, dump di fase-1/costruzione
   presente), erosione per fase attribuita. AC#5 TASK-415 soddisfatta sul run laptop reale.
5. **Orchestrazione laptop**: i task Bash in background sono capped a 600000ms (10 min) e vengono
   killed; usato caffeinate detached (`nohup ... & disown`, -t 5400/7200) + polling via
   CronCreate one-shot. Da riutilizzare per run laptop futuri.
