---
id: TASK-534
title: >-
  compare-runs: pagina confronto run-vs-run + vettore KPI normalizzato (Pareto +
  worst-dimension, niente scalare unico)
status: Done
assignee: []
created_date: '2026-08-19 20:24'
updated_date: '2026-08-20 01:33'
labels:
  - claude-planned
  - dataviz
  - kpi
  - comparison
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal quesito di maintainer (19/08 sera): l'insieme dei dataviz deve diventare lo strumento di comparazione qualitativa fra run (A/B, prima/dopo cura), oggi possibile solo golden-vs-un-run. La batteria capture-run.sh produce gia' gli stessi KPI per ogni run: manca l'aggregatore run-vs-run. Inoltre (secondo quesito) i KPI vanno normalizzati in un VETTORE comparabile con polarita' dichiarata per dimensione, non collassati in uno scalare unico: lo score e' gia' il nostro scalare (gate 0hard binario), un secondo scalare pesato ricreerebbe la compensabilita' che il score gia' soffre (un buon fill nasconde una cattiva cadenza) e i pesi sarebbero arbitrari. Forma scelta (discussa con maintainer): vettore di KPI normalizzati contro riferimenti fissi (golden/quote input) + bande per soglie di campagna + dominanza Pareto + dimensione-piu'-grave (max-min, coerente con la filosofia 0hard-binario: la dimensione peggiore e' quella vincolante). Attenzione circolarita': per alcune dimensioni il golden e' il TARGET (fill, forma), per altre e' solo un punto valido che possiamo battere (gap docente: noi 29% vs golden 41%, S1 soft dice che meno e' leggermente meglio): la polarita' va dichiarata per dimensione, non ereditata dal golden.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 scripts/analyze/compare-runs.sh <runDirA> <runDirB> produce pagina HTML affiancata con tutte le dimensioni KPI e i delta
- [x] #2 Dimensioni minime: fill-parity (giorni sotto quota per gruppo), day-start (late days), cadence, ricorrenza/ricorrenza unpinned, buchi H17, gap docente-giorno, hard residui per famiglia
- [x] #3 Vettore KPI normalizzato con polarita' dichiarata per dimensione (target=golden esatto / >=golden / <=golden / input-dichiarato), bande verde/giallo/rosso per le soglie di campagna note
- [x] #4 Semantica comparazione dichiarata: dominanza Pareto + dimensione-piu'-grave (max-min), NESSUNA media pesata scalare
- [x] #5 Testato su una coppia reale (h32-run1 vs prossimo run conforme) e consegnato con screenshot
- [ ] #6 DoD standard
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 23:22
---
CHIUSURA (notte 19/20-08, commits 1249c43c + 1556d553 + a3f4f046 + 16458b0e): scripts/analyze/compare-runs.py (lettura read-only dei KPI canonici + export per under-quota) e pagina compare-h32b-vs-qfr.html nel repo. REVIEW (agent bloccato dal classificatore, review manuale a 4 fuochi) ha trovato e curato DUE bug di segno: (1) hard trattato come min ma e' max-polarity (negativo meno peggio = piu' grande: -104 -> -38 e' un miglioramento, il vecchio delta lo dava negativo e invertiva Pareto); (2) la branch min calcolava vb-va invece di va-vb. Banda hard ora su valore assoluto (0 verde, <=50 giallo). Verifica a mano su hard -104->-38 e holes 97->5, regex controllate sui file reali, crash-path None guarded.
---

created: 2026-08-19 23:22
---
VERDETTO sulla coppia reale (h32b vs qfr): B (qfr, col QuotaFillRepair) meglio su hard (+66), buchi H17 (+92), full-day (+3,1); peggio su celle sotto-quota (-21); ricorrenza -1,0. NESSUNA dominanza Pareto (il vettore dice la verita' che uno scalare nasconderebbe). Dimensione-piu'-grave: under_quota.
---

created: 2026-08-19 23:22
---
AC#5 (screenshot) e AC#6 (DoD formale): screenshot differito a quando l'app puo' servire la pagina (il run di stanotte la occupa); suite non toccata (python puro, nessun codice Java). AC#1-4 completate.
---

created: 2026-08-20 01:33
---
DoD COMPLETATA (notte 20/08): simplify (alias vestigiale rimosso), code-review manuale a 4 fuochi con DUE bug di segno curati, screenshot della pagina compare registrato (claudedocs/screenshots/task534-compare-runs.png, servita via static server temporaneo e catturata col browser: AC#5). Nessun codice Java toccato (python puro): suite non interessata. Commit 71f5b595.
---
<!-- COMMENTS:END -->
