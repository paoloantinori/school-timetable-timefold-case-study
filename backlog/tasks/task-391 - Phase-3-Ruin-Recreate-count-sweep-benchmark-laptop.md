---
id: TASK-391
title: Phase-3 Ruin & Recreate count sweep — the idiomatic first lever for the G05 zero-slack knot (laptop benchmark)
status: Done
assignee: []
created_date: '2026-07-22 07:45'
updated_date: '2026-07-22 09:30'
labels:
  - claude-planned
dependencies: []
ordinal: 215000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Leva (c) PRIMARIA per lo 0-hard affidabile (TASK-386 verdetto + ricerca letteratura Timefold 2026-07-22). Tre fonti convergono: (1) i docs Timefold raccomandano Ruin & Recreate come LO strumento per schedule tight bloccati e dicono di ALZARE i ruin count (default 5-20) sui dataset grandi; (2) il verdetto del benchmark laptop (TASK-386) indica il ruin-recreate di fase 3 come la leva ad alta varianza (rescue binario da ~1000 hard); (3) il benchmark laptop NON ha mai testato la fase 3 (era identica in tutti i bracci A-F). La nostra config ship a `minimumRuinedCount=2/maximumRuinedCount=5` — SOTTO il default — su un problema da 12.581 lezioni. Il knot di G05 (990h domanda = 29 slot/sett × ~34 sett, slack≈zero, docenti condivisi con 10-14 gruppi) è un cluster multi-lezione: un ruin da 2-5 non basta quasi mai a dislodgiarlo in un colpo. Search config, modello INVARIATO (direttiva primaria). Se questo raggiunge lo 0-hard affidabile → si evita la chirurgia sulla CH (TASK-390, fallback).

**Bracci (generati da `scripts/benchmark/generate-benchmark-arm.py`, fase 2 tenuta al control):** A = control (ruin 2-5, l'attuale); R1 = ruin 5-20 (default Timefold); R2 = ruin 10-40 (aggressivo, per il cluster stretto). Ogni braccio **2 run** (varianza, finding run-16). ~6-9h totali. **DA ESEGUIRE SUL laptop** (direttiva utente 2026-07-22: i test non brevi girano sul laptop, il portatile a volte viene chiuso; tenere il laptop aggiornato con git pull).
<!-- SECTION:DESCRIPTION:END -->

## Verdict criteria (come TASK-386 + specifici R&R)

1. **hard → 0** (o closest); tie-break **medium**, poi tempo-al-best.
2. **KPI non devono collassare**: full-day ≥ ~74%, ricorrenza unpinned ≥ ~38%, blocchi ≥ ~1.800.
3. **STABILITÀ**: preferire un braccio che atterra basso in ENTRAMBI i run a uno fortunato una volta (run-16: stessa config -426 vs -3902).
4. **Misura specifica del knot**: controllare in `analysis.json` se il residuo H3 di G05 (Scienze/T09 vs Matematica/T08 stacked) SPARISCE con R1/R2 in modo affidabile. È QUESTA la domanda del task, non solo lo hard totale.
5. **Costo**: un ruin più grande rende ogni mossa R&R più cara (meno mosse nel budget) — verificare che non pegggiori la convergenza complessiva.

## Esecuzione (laptop)

Stesso protocollo di `scripts/benchmark/laptop-RUNBOOK.md` (sezione "Ruin & Recreate sweep"): per ogni braccio in {A, R1, R2} (poi re-run di R1/R2, o del vincitore), `generate-benchmark-arm.py --arm <K>` → copia in solverConfig.xml → rebuild → fresh profile → import-calendario → pre-assign → `caffeinate` solve → raccolta score/phases/analysis/KPI in `local/benchmark-results/arm-<K>/`. Riportare in `claudedocs/spalmone-2026-27/benchmark-386-results/` (o cartella sorella) + verdetto.

## VERDETTO + ADOZIONE (2026-07-22) — R1 (ruin 5-20) ADOTTATO

laptop 6 run (3 bracci × 2), verificati da me contro i dati grezzi (non dal sommario laptop). **R1 = ruin 5-20 (default Timefold) è reliably low: hard {-66, -70} spread 4**, vs control 2-5 {-121, -1123} spread 1002, vs R2 10-40 {-2182, -1146} spread 1036 (peggiore). KPI tengono (full-day 76.9-78.1%, blocchi 2005-2081). Meccanismo (phases.json): R1-run2 risale una fase-2 pessima -2199 → -88 (Δ +2111), dove il control da -2138 arriva solo a -1138. Adottato: solverConfig.xml phase-3 min 2→5 / max 5→20 + wording docs. Dettaglio in experiment-log.md ("Benchmark TASK-391").

**CORREZIONE alla premessa del laptop (verificata da lessons.txt):** il laptop concludeva "G05 knot = 0 in tutti i 6 run, premessa refutata". FALSO — A-run2 ha 1 stack G05 (Matematica/T08), R2-run1 ha 2 stack G05 (Scienze/T09 vs Matematica/T08). Il knot ERA reale; la diagnosi (c) era corretta; R1 lo evita in modo affidabile (0/2). Ripple solver-config-sync: nessuna funzionale.

**0-hard NON raggiunto:** R1 chiude il knot H3 di G05 ma lascia H15 -60 (residuo diverso, minore). Il pursuit dello 0-hard continua in TASK-390 (euristiche umane in costruzione), ora mirato a H15.

**Nota:** TASK-324 (2026-07-14) "refutò" il ruin sweep, ma su provider cieco pre-TASK-377 (non fa testo) e non testò mai 5-20. Non contraddice questo risultato.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff — diff config-values + prose, nessun code-surface, pulito (valutazione per-angolo documentata)
- [x] #2 Skill /code-review high — eseguita in-context (skill non model-invocable): valori corretti (5<20, = winner/default), XSD valido, nessun consumatore cross-file dei count, nessun ref live mancato
- [x] #3 Verifica finale con EXIT CODE: test.sh exit 0, 688 test verdi, GoldenSolutionRegressionTest invariato. Frontend non toccato → niente ui-*
<!-- DOD:END -->
