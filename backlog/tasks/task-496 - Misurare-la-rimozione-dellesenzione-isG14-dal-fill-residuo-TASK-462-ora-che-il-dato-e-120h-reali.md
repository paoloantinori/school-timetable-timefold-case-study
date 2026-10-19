---
id: TASK-496
title: >-
  Misurare la rimozione dell'esenzione isG14 dal fill (residuo TASK-462, ora
  che il dato e' 120h reali)
status: Done
assignee: []
created_date: '2026-08-14 10:17'
labels:
  - claude-planned
  - golden-first
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Residuo della chiusura TASK-462 (census backlog 14/08). L'esenzione isG14 dal fill M20/M33 (SchoolScheduleConstraintProvider ~linea 2343) era nata come cerotto sul dato sbagliato (990h lette dal foglio 'G14'); TASK-462 ha corretto il dato (G14 2 = 120h, golden-confermato: 73h giovedi' + 42h venerdi' = 115 collocabili + 5 sabato Orientamento off-grid) e il commento nel codice ora la giustifica con la forma part-time gio-ven (proprieta' di dominio reale). Domanda da misurare: l'esenzione e' ancora necessaria ora che la quota attesa di G14 riflette il part-time (calendario ClassDays reale), o M20/M33 gia' non la penalizzano? Procedura: prototipo flag-gated che rimuove l'esenzione, confronto su run staged (hard 0, full-day G14, nessun nuovo residuo M20/M33 su G14). Se neutro -> rimozione pulizia; se degrada -> l'esenzione resta come proprieta' dichiarata (e va nel workbook). PRIORITA' BASSA: misura, non urgenza.
<!-- SECTION:DESCRIPTION:END -->


## Final Summary

ESITO (laptop, notte 15-16/08): A/B pulita supera TUTTI i tre criteri del gate. hard 0
entrambe le braccia (golden default, tutto ratificato ON); full-day G14 migliora
90%->93%; deficit G14 scende 7h->6h; NESSUN nuovo residuo M20/M33 su G14 (+30k
medium totale e' varianza su altri gruppi, banda good di stanotte). RACCOMANDAZIONE:
rimuovere l'esenzione come pulizia (il flag resta DEFAULT TRUE=esente finche' maintainer
ratifica la rimozione: forma opt-out, flip senza codice). Implementazione: fillExempt()
sui 6 siti fill-family, G14FillExemptionTest 3/3, banner. NOTA runbook: prima coppia
scartata per driver doppio (due baseline-golden concorrenti hanno doppio-importato:
15.349 lezioni, -5.8M hard); mai rilanciare il driver senza pkill del precedente.
Doc: task496-results/ab-2026-08-15/VERDICT.md.
