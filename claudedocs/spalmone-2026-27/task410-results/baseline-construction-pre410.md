# TASK-410 construction-fixture BASELINE (HEAD a5406b4a, BEFORE stage-aware activeWeeks)

Source: `./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest` (deterministic construction phase, no solve)

## Headline KPIs
- recurrence: **76.3%** (7952/10416), target >= 50% — PASS
- stampSkipConflict: **0** — PASS (zero H1/H2/H3 introduced by the stamp)
- final conflicts: teacher=0, group=0 — PASS
- box (unassigned residual): well under lessons/50 (~2%) — PASS
- RAW full-day: 68.2% (1633/2395)
- **SCOPED full-day (excl. G14 + stage-modality rows): 83.5% (1632/1954)** — already >= 65% target, near golden 85.4%
- breakdown: under=232 days (deficit 590h), over=90 (surplus 157h)

## Per-group SCOPED full-day
| group | full/total | % |
|-------|-----------|---|
| G01 | 167/171 | 98 |
| G02  | 168/171 | 98 |
| G03 | 143/171 | 84 |
| G04  | 99/171  | 58 |
| G05 | 164/171 | 96 |
| G06  | 144/171 | 84 |
| G07 | 125/132 | 95 |
| G08  | 91/135  | 67 |
| G09 | 104/131 | 79 |
| G10  | 95/130  | 73 |
| G11 | 124/133 | 93 |
| G12 | 113/134 | 84 |
| G13   | 95/133  | 71 |
(G14 excluded from SCOPED)

## CRITICAL: the mission's "9-45% / 54.2%" baseline is STALE
The mission brief and the test's own comment (lines 446-448, 471) cite SCOPED 54.2% with stage
groups at 9-45%. That was the TASK-385-era measurement. The current HEAD already shows stage groups
at 71-95% / SCOPED 83.5%. What moved them: TASK-390 (leftward compaction), TASK-398 (box-the-residual),
TASK-401 (BoxRepair), TASK-405 (day-start anchoring), TASK-406 (deficit-aware repair tail aligned to
M20 dailyQuota) — NOT stage-aware weekly targets. `countActiveWeeks` still spreads hours over stage
weeks; the deficit-aware repair tail now compensates in the fixture by filling under-target days.

Implication: the FAST SIGNAL is already green. TASK-410's construction-fixture delta will be small
(the fix is still correct — the weekly-target calc IS wrong for stage groups — but the repair tail
masks it in the fixture). The real validation is the live solve (PART 2).
