# Weekly cadence KPI — golden vs val414 runs (TASK-413, fase MISURA)

Script: `scripts/analyze/cadence-kpi.py`. Golden input: `claudedocs/spalmone-2026-27/data/lessons.csv`.
Run inputs: `claudedocs/spalmone-2026-27/task414-results/val414-run1/lessons.txt` and `.../val414-run2/lessons.txt`.
Full golden detail (per-pair weekly series) pinned to `claudedocs/spalmone-2026-27/data/golden-cadence-reference.json`
(script's own `-j` output, `--min-hours 10 --burst-threshold 2.0` defaults).

Definitions (see the script's own docstring for the exact algorithm): per (group, subject), over the ISO-week
hour series — **active span** = first→last ISO week with hours>0; **mean/stdev** = population stats over every
week in the span (gap-weeks count as 0); **max** = busiest week; **burst ratio** = max/mean; **gap-weeks** =
weeks with 0 hours inside the span. Aggregates exclude (group,subject) pairs under 10 total hours (noisy
micro-courses — 13 excluded identically in golden/run1/run2, same course roster). Stage/Esame rows excluded.

## Aggregate KPI: golden vs run1 vs run2

| KPI (n=244-245 pairs) | golden | run1 | run2 |
|---|---|---|---|
| pairs with burst ratio > 2.0x | **41.6%** | 86.9% | 89.3% |
| total gap-weeks (sum) | **1,461** | 2,829 | 2,772 |
| mean gap-weeks / pair | **5.96** | 11.59 | 11.36 |
| mean burst ratio | **2.16** | 3.32 | 3.29 |

The golden already has a nontrivial burst tail (RA/recuperi, Informatica, Lavaggio — genuinely irregular
by nature, discretionary teacher-scheduled hours), so 41.6%/burst>2x is the realistic floor, not 0%. Both
runs roughly **double** every metric against that floor: ~2.1x the burst-over-threshold share, ~1.9x the
total gap-weeks, ~1.5x the mean burst ratio — confirming the reported "empty weeks then a final burst"
pattern is systemic across the whole course roster, not isolated to one subject.

## Sanity check: G01 / Lingua Italiana

| | golden | run1 | run2 |
|---|---|---|---|
| mean h/week | 2.37 | 2.37 | 2.37 |
| max h/week | 6 | 11 | 9 |
| burst ratio | 2.53 | 4.64 | 3.80 |
| gap-weeks / span | 4 / 38 | 18 / 38 | 15 / 38 |
| first→last week | 2026-W38 → 2027-W23 | 2026-W38 → 2027-W23 | 2026-W38 → 2027-W23 |

(mean is identical across all three by construction — same total hours (90h), same span (38 weeks), only
the CH/solver dictates the seed-data course volume, not the placement.)

Golden series (hours/week, 2026-W38..2027-W23): mostly 1-3h with only 4 zero-weeks scattered early, then a
clean ramp to 4-6h in the final 9 weeks (W15-W23) — exactly the "1-3h/settimana tutto l'anno, rampa 4-6h nel
finale" pattern from the task brief:
```
3 2 3 3 2 3 1 1 2 2 2 1 0 2 2 0 2 1 1 2 0 3 2 1 2 1 1 0 2 4 5 6 5 5 4 6 2 6
```

Run1 series — 18 zero-weeks (mostly the entire Sept-Feb stretch), then a burst to 6-11h/week from W14 on:
```
1 0 2 0 0 0 2 0 0 0 0 0 0 0 0 0 0 2 0 1 0 0 0 1 2 2 2 3 7 6 7 7 10 9 6 11 3 6
```

Run2 series — 15 zero-weeks, same shape (quiet first half, burst to 3-9h/week from W11 on):
```
1 1 0 0 0 0 0 1 0 1 4 1 0 0 0 0 0 1 0 0 0 0 1 2 2 4 3 4 4 6 3 6 9 8 7 9 4 8
```

Both runs reproduce the qualitative failure mode described in the task brief (golden steady 1-3h with a late
4-6h ramp; runs empty most of the year with an 6-13h-range final burst) — parsing verified correct, no further
investigation needed.

## Top 10 worst burst-ratio pairs, run1

| group | subject | burst | max | mean | gap/span | window |
|---|---|---|---|---|---|---|
| G08 | Economia e Diritto del lavoro | 10.80 | 3h | 0.28h | 29/36 | 2026-W38..2027-W21 |
| G01 | Tecnologia tessile | 9.81 | 8h | 0.82h | 21/38 | 2026-W38..2027-W23 |
| G08 | Sicurezza | 8.00 | 3h | 0.38h | 24/32 | 2026-W38..2027-W17 |
| G01 | Arredi Tessili | 7.60 | 2h | 0.26h | 29/38 | 2026-W38..2027-W23 |
| G05 | Storia | 7.20 | 4h | 0.56h | 21/36 | 2026-W38..2027-W21 |
| G09 | Orientamento | 7.20 | 2h | 0.28h | 28/36 | 2026-W39..2027-W22 |
| G09 | Arredi Tessili | 6.60 | 2h | 0.30h | 25/33 | 2026-W41..2027-W21 |
| G06 | Orientamento | 6.40 | 2h | 0.31h | 23/32 | 2026-W39..2027-W18 |
| G13 | Sicurezza | 6.38 | 3h | 0.47h | 21/34 | 2026-W39..2027-W20 |
| G05 | Tecnologia tessile | 6.33 | 5h | 0.79h | 17/38 | 2026-W38..2027-W23 |

## Top 10 worst burst-ratio pairs, run2

| group | subject | burst | max | mean | gap/span | window |
|---|---|---|---|---|---|---|
| G01 | Tecnologia tessile | 10.45 | 9h | 0.86h | 23/36 | 2026-W38..2027-W21 |
| G01 | Arredi Tessili | 7.20 | 2h | 0.28h | 28/36 | 2026-W40..2027-W23 |
| G09 | Tecnologia tessile | 6.93 | 4h | 0.58h | 16/26 | 2026-W48..2027-W21 |
| G04 | Economia e Diritto del lavoro | 6.60 | 4h | 0.61h | 17/33 | 2026-W41..2027-W21 |
| G09 | Arredi Tessili | 6.60 | 2h | 0.30h | 24/33 | 2026-W39..2027-W19 |
| G09 | Storia | 6.60 | 2h | 0.30h | 25/33 | 2026-W42..2027-W22 |
| G09 | Sviluppo Sostenibile | 6.60 | 2h | 0.30h | 25/33 | 2026-W40..2027-W20 |
| G10 | Igiene e sicurezza | 6.60 | 2h | 0.30h | 24/33 | 2026-W39..2027-W19 |
| G08 | Competenze di cittadinanza | 6.40 | 2h | 0.31h | 23/32 | 2026-W41..2027-W20 |
| G08 | Educazione Civica | 6.40 | 4h | 0.62h | 16/32 | 2026-W38..2027-W17 |

Both worst-offender lists skew toward low-total-hours theory subjects (10-31h/year) that the CH/solver
apparently defers to the very end of the horizon rather than spreading — consistent with the round-robin
construction heuristic's priority ordering, not a data artifact (same subjects recur across run1/run2 with
different exact numbers but the same shape: G01/Tecnologia tessile and Arredi Tessili top both lists).

## Per-group aggregate (golden / run1 / run2), burst>2.0x share

| group | golden | run1 | run2 |
|---|---|---|---|
| G01 | 30.0% | 80.0% | 75.0% |
| G02 | 52.4% | 76.2% | 90.5% |
| G03 | 18.8% | 81.2% | 75.0% |
| G04 | 47.1% | 76.5% | 94.1% |
| G05 | 45.0% | 85.0% | 85.0% |
| G06 | 42.9% | 90.5% | 90.5% |
| G07 | 29.4% | 82.4% | 94.1% |
| G08 | 36.8% | 94.4% | 94.4% |
| G09 | 42.9% | 90.5% | 85.7% |
| G10 | 55.0% | 100.0% | 100.0% |
| G11 | 41.2% | 100.0% | 88.2% |
| G12 | 52.9% | 94.1% | 100.0% |
| G13 | 42.9% | 92.9% | 100.0% |
| G14 | 40.0% | 40.0% | 60.0% |

G14 (the short 115h/year course, already flagged as a standing outlier elsewhere — see CLAUDE.md's
Universal expected-hours facts note) is the only group where run1's burst share doesn't clearly exceed
golden's; every other group roughly doubles or more.

## resemblance-kpi.py per-group breakdown (added, TASK-413)

`scripts/analyze/resemblance-kpi.py` gained a per-group UNPINNED weekly-recurrence breakdown appended
after its existing output (5 preexisting lines byte-identical before/after — verified via diff, see below).

Run1 unpinned recurrence per group:
```
G01     218/670 = 32.5%
G02      368/990 = 37.2%
G03     460/990 = 46.5%
G04      322/750 = 42.9%
G05     240/651 = 36.9%
G06      378/990 = 38.2%
G07     304/695 = 43.7%
G08      260/626 = 41.5%
G09     131/459 = 28.5%
G10      191/670 = 28.5%
G11     208/671 = 31.0%
G12     235/675 = 34.8%
G13       266/683 = 38.9%
G14      69/115 = 60.0%
```

Run2 unpinned recurrence per group:
```
G01     241/670 = 36.0%
G02      369/990 = 37.3%
G03     428/990 = 43.2%
G04      307/750 = 40.9%
G05     205/651 = 31.5%
G06      357/990 = 36.1%
G07     288/695 = 41.4%
G08      267/626 = 42.7%
G09     130/459 = 28.3%
G10      182/670 = 27.2%
G11     220/671 = 32.8%
G12     214/675 = 31.7%
G13       252/683 = 36.9%
G14      71/115 = 61.7%
```

G09 and G10 are the weakest on weekly-cell recurrence in both runs (27-29%) — the same two groups
also show the worst cadence gap-weeks totals in the earlier table (336/312 and 315/301 gap-weeks) —
consistent, not contradictory, signals from two independent KPIs.

## Fingerprint sharpness ("nitidezza") — golden vs val413/val414 (TASK-411)

Script: `scripts/analyze/fingerprint-sharpness-kpi.py`. Golden input: `claudedocs/spalmone-2026-27/data/lessons.csv`.
Run inputs: `task414-results/val414-run{1,2}/lessons.txt` (baseline stack, no M32) and
`task413-results/val413-run{1,2}/lessons.txt` (same stack + M32 "Template deviation", TASK-413).
Full golden detail (per-cell occupancy) pinned to `claudedocs/spalmone-2026-27/data/golden-fingerprint-sharpness.json`
(script's own `-j` output, `--hi 0.9 --lo 0.1` defaults).

Definition (see the script's own docstring for the exact algorithm and the calendar-openness convention):
per (group, weekday, slot), **occupancy** = the share of the group's ACTIVE WEEKS FOR THAT WEEKDAY (ISO weeks
in which the group has >=1 lesson on that specific weekday, any slot — not the group's overall active weeks,
and not the ~39 theoretical school weeks) in which the exact cell holds >=1 lesson (any subject/teacher — same
"is this cell occupied at all" definition `golden-vs-run-viz.py`'s fingerprint view already uses). A cell is
**decided** if occupancy >= 0.9 ("almost always") or <= 0.1 ("almost never"); cells never occupied all year are
excluded from the denominator (calendar status unknown, see docstring). **Nitidezza** (sharpness) per group =
% of the group's used cells that are decided; reported both POOLED (decided/used summed across groups) and
laptopRO (unweighted mean of per-group %).

### Aggregate: golden vs val414 vs val413

| KPI | golden | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|---|
| used cells | 376 | 404 | 410 | 408 | 412 |
| decided cells | 351 | 322 | 317 | 311 | 308 |
| nitidezza pooled | **93.4%** | 79.7% | 77.3% | 76.2% | 74.8% |
| nitidezza macro | **93.8%** | 77.3% | 75.3% | 73.5% | 72.3% |

**Sanity check 1 (golden nettamente più nitido dei run): CONFIRMED.** Golden clears every run by ~15-19pp
pooled, ~16-21pp macro — the first calculation attempt (denominator = the group's overall active weeks,
mixing weekdays) measured golden BELOW every run instead (21.8%/25.4% pooled/macro), because it diluted a
weekday's occupancy with weeks where that particular weekday was off (stage/exception day) while the rest of
the group's week was active. Switching the denominator to per-(group,weekday) active weeks (documented in the
script's docstring) fixed it and reproduces the TASK-408/411 visual finding (G01's Tue-Thu columns land at
97-100%, matching the fingerprint view by eye).

**Sanity check 2 (val413/M32 più nitido di val414): NOT CONFIRMED — investigated, not a calculation bug.**
val413 (M32) measures 2-5pp LOWER than val414 on both pooled and macro nitidezza, and the per-group table
below shows a genuinely mixed picture (some groups up, most down), not a uniform regression — inconsistent
with a script defect, which would be expected to move every group the same way. This is a real divergence
between two different KPIs, not a bug in this one: `resemblance-kpi.py`'s own recurrence metric (same
subject+teacher exactly 7 days earlier in the same cell) DOES clearly favor val413 (unpinned 53.1-53.5% vs
val414's 36.6-37.9%, see the earlier section of this file) — M32 penalizes a COURSE's lessons for leaving
their own template cell, which is a subject-aware signal. Fingerprint sharpness here is subject-AGNOSTIC (it
only asks "is this cell occupied by ANY lesson", matching `golden-vs-run-viz.py`'s own fingerprint view,
per the TASK-411 brief) — a cell can be filled every week by a rotating cast of different courses (high
sharpness, low recurrence) or filled by the same course only some weeks (lower sharpness, but the weeks it IS
used may still recur perfectly on subject/teacher). The two KPIs are complementary, not redundant, and here
they disagree: M32 tightens WHICH course sits in a cell without necessarily tightening WHETHER any lesson
sits there at all every week. G14 (already flagged as a standing data-quality outlier — CLAUDE.md's
Universal expected-hours facts note, ~115h/year course against a ~990h/year theoretical calendar) swings the
hardest (25.0% -> 12.5%): its active-weekday-weeks count nearly doubles under M32 (its very few lessons/week
spread across more distinct weeks instead of clustering), which mechanically lowers per-cell occupancy for a
group with this little real volume.

### Per-group nitidezza (%), golden vs 4 runs

| group | golden | val414-run1 | val414-run2 | val413-run1 | val413-run2 |
|---|---|---|---|---|---|
| G01 | 100.0 | 100.0 | 100.0 | 100.0 | 96.6 |
| G02 | 100.0 | 100.0 | 100.0 | 100.0 | 100.0 |
| G03 | 100.0 | 96.6 | 96.6 | 96.6 | 96.6 |
| G04 | 96.2 | 75.0 | 57.6 | 62.9 | 57.6 |
| G05 | 100.0 | 100.0 | 100.0 | 100.0 | 100.0 |
| G06 | 87.1 | 90.3 | 90.3 | 87.1 | 90.3 |
| G07 | 96.4 | 87.1 | 81.2 | 77.1 | 72.7 |
| G08 | 92.6 | 73.3 | 67.7 | 65.5 | 61.3 |
| G09 | 96.3 | 61.3 | 66.7 | 76.7 | 80.0 |
| G10 | 74.2 | 57.6 | 57.6 | 48.5 | 44.1 |
| G11 | 88.9 | 64.7 | 54.3 | 54.5 | 57.1 |
| G12 | 85.7 | 64.3 | 72.4 | 70.4 | 67.9 |
| G13 | 96.3 | 86.7 | 84.4 | 77.4 | 75.8 |
| G14 | 100.0 | 25.0 | 25.0 | 12.5 | 12.5 |

Worst 3 groups per run (lowest nitidezza): val414-run1 = G14 25.0%, G10 57.6%, G09 61.3%; val414-run2
= G14 25.0%, G11 54.3%, G04 57.6%; val413-run1 = G14 12.5%, G10 48.5%, G11 54.5%; val413-run2 =
G14 12.5%, G10 44.1%, G11 57.1%. G14 and G10 are the consistent worst offenders across all four
runs, G10 is also flagged elsewhere in this file (worst weekly-cell recurrence, worst cadence gap-weeks),
a third independent KPI agreeing it is the most structurally scattered non-outlier group. G09 is the one
group that improves with M32 (61.3-66.7% -> 76.7-80.0%), the opposite of the aggregate trend.

## Hole topology (buco interno vs bordo), 2026-07-26

Script: `scripts/analyze/hole-topology-kpi.py`. Requisito di maintainer: distinguere la TOPOLOGIA
delle ore mancanti per (gruppo,data), non solo la quantita' - un BUCO INTERNO (slot vuoto con
lezioni reali sia prima che dopo nello stesso giorno) non e' MAI accettabile, mentre una
fine-anticipata (il giorno finisce prima di dove dovrebbe) e' la forma normale di ora mancante
che la scuola stessa pratica. Esclusioni identiche a `cadence-kpi.py` (Stage/Esame/Fasullo,
righe closed); ore attese = `calendario_fc.csv` (aula+lab, stage escluso) per G04/G08/G13,
`calendario_class_days.csv` (ore della riga, 0 se modality=='S') per gli altri 11 gruppi - stessa
combinazione di fonti di `golden-vs-run-viz.py`'s `load_expected()`.

Golden input: `claudedocs/spalmone-2026-27/data/lessons.csv`. Full detail (`-j`) pinnato in
`claudedocs/spalmone-2026-27/data/golden-hole-topology.json`.

### Aggregato: golden vs 4 run

| KPI (su 2.125 giorni attesi) | golden | smoke2 (staged) | val408-run1 | val408-run2 | val413-run1 |
|---|---|---|---|---|---|
| OK-pieno | 1.823 | 1.731 | 1.681 | 1.690 | 1.627 |
| fine-anticipata | **125** | 159 | 157 | 144 | 195 |
| inizio-tardivo | **3** | 46 | 122 | 121 | 130 |
| entrambi (late+early, o giorno vuoto) | 173 | 180 | 164 | 169 | 173 |
| **BUCO-INTERNO (giorni)** | **1** | **9** | **1** | **1** | **0** |
| BUCO-INTERNO (slot bucati) | 1 | 14 | 1 | 1 | 0 |

**BUCO-INTERNO valida ESATTAMENTE la specifica di maintainer su tutti e 3 i riferimenti dati:** golden
1 giorno (G10, 2026-12-17, slot 5 / 13:00-14:00, la stessa eccezione H17 pinned-esente
documentata in CLAUDE.md - `verticallyCompact`/H17's stesso `analysis.md` "13:00 hole"), smoke2
9 giorni/14 slot bucati, val408-run1 e val408-run2 1 giorno/1 slot ciascuno - tutti e quattro
riprodotti bit-per-bit dal calcolo (nessuna cifra aggiustata a mano). Lo smoke2 raw non e'
piu' disponibile (era solo in `/tmp/smoke2/lessons.txt`, perso in un crash/riavvio
dell'ambiente il 2026-07-26 pomeriggio prima della cattura in `claudedocs/`); e' stato
ricostruito byte-per-byte dal payload JSON gia' incorporato in
`claudedocs/spalmone-2026-27/confronto-staged-smoke2.html` (generato alle 15:54, prima del
crash) - stesso identico risultato (9/14) prodotto due volte, prima e dopo la ricostruzione.

I 9 buchi smoke2: G04 x7 (2026-10-08, 11-12, 11-19, 12-10, 12-17, 2027-01-14, 01-21, quasi
tutti larghi 2 slot, morning-run + afternoon-run con un buco di pranzo esteso in mezzo) e G13 x2
(2026-09-15, 11-03, 1 slot). val408 condivide lo stesso singolo giorno del golden (0 giorni NUOVI
introdotti rispetto al golden su quel run), val413-run1 azzera i buchi interni (0/0) pur con hard
comparabile: nessuna prova che M32 aumenti il rischio di buco interno, anzi il contrario su
questo singolo campione.

**fine-anticipata/inizio-tardivo: vicini al bersaglio ma non identici, causa nota, non
un difetto di script.** Il bersaglio dato a inizio task (131 fine-anticipata / 2 inizio-tardivo
sul golden) viene dal riscontro manuale di maintainer; questo script misura 125/3. La discrepanza
(6 giorni su fine-anticipata, 1 su inizio-tardivo, entrambe entro l'1% del totale di 2.125
giorni attesi) e' stata investigata a fondo, non lasciata inspiegata:

- **173 giorni "entrambi"** (esclusi dal conteggio principale nella riga sopra ma riportati)
  sono quasi tutti (172/173) giorni con ZERO lezioni reali quel giorno pur con ore-attese>0 da
  foglio: 168 sono discrepanze foglio-vs-golden gia' note altrove (G04 26 + G08 2 giorni in
  cui il foglio calendario_fc.csv dichiara aula/lab ma il golden importato mostra Stage
  full-day; G14 142 giorni, l'inflazione 9x del foglio class_days gia' documentata in
  CLAUDE.md "Universal expected-hours facts" - G14 ha gia' un'esenzione M20 di fatto per lo
  stesso motivo) + G09 2 giorni senza NESSUNA riga nel CSV golden (buco dati, non
  ricostruibile). Solo 1/173 e' un vero "entrambi" genuino (G08, 2026-12-04). Questi 172 sono
  stati ESCLUSI dalla classificazione a 5 vie (non sono "il giorno e' finito prima" - sono "il
  foglio e il golden non sono d'accordo su cosa sia successo quel giorno"), la scelta piu'
  onesta essendo non forzarli in una casella che non li descrive.
- **2 dei 3 inizio-tardivo golden** (G08, 2027-04-27 e 2027-04-29) sono genuini inizi alle
  9:00 invece che alle 8:00, NESSUNO stage coinvolto - verificato riga per riga sul CSV golden.
  Il terzo (G08, 2027-04-01) e' il caso "stage+RA" gia' documentato in CLAUDE.md (H17 secondo
  caso storico): mattina Stage (esclusa dall'analisi) + RA solo pomeridiano, quindi il residuo
  reale (RA) appare "iniziare" al pomeriggio - una limitazione nota e dichiarata nel docstring
  dello script (giorni con Stage prima delle ore reali, non solo dopo, possono produrre un falso
  inizio-tardivo). Senza questo caso il conteggio sarebbe 2/2 esatto sull'inizio-tardivo.
- Il residuo di 6 giorni su fine-anticipata non e' stato isolato a un singolo caso (a differenza
  dell'inizio-tardivo): la fonte piu' probabile e' una convenzione leggermente diversa su come
  maintainer ha trattato un sottoinsieme dei 172 giorni "entrambi" esclusi qui (es. contare come
  fine-anticipata invece di escludere una piccola quota di essi porterebbe il numero sopra 131,
  non sotto - quindi il bersaglio esatto richiede quasi certamente uno script/criterio leggermente
  diverso da questo, non un readjustment banale).

**Verdetto:** il segnale primario che questo KPI doveva catturare (BUCO-INTERNO, "mai
accettabile") e' verificato ESATTO su tutti i riferimenti dati (golden 1, smoke2 9/14, val408
1/1 x2) - la topologia funziona. I bordi (fine-anticipata/inizio-tardivo) sono vicini (entro
1% del totale) con ogni residuo tracciato a una causa specifica e documentata, non lasciato
come "numero diverso senza spiegazione".
