---
id: TASK-550
title: >-
  Coda triangolo FA: le 12 H0 Servizio tecnico e stilistico (analisi
  post-braccio congiunto e opzioni)
status: Done
assignee: []
created_date: '2026-08-25 08:16'
updated_date: '2026-08-25 16:12'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Famiglia 3 del -152: le 12 H0 di Servizio tecnico e stilistico FA (4+8), coda del triangolo TASK-435. CONTESTO GIA' RAGIONATO (25/08, per non ricominciare da capo): il triangolo grande (overdemand T01 30h, 35 lezioni) fu curato dalla pre-assegnazione constrained-teacher TASK-435 (luglio, ratificata; slack da -30 a +5h). Le 12 sono lo strascico: ore che restano senza cella compatibile quando il solver muove il resto; meccanismo tipo residui H17 teacher-locked applicato a questo docente. La predizione del braccio congiunto le lascia intatte (H0 46 -> ~12). Opzioni candidate iniziali: (a) estendere la logica PureHoleRepair alle H0 VRP-locked di docente (la fase oggi chiude solo i buchi H17); (b) aggiustare il perimetro/le priorita' della pre-assegnazione 435; (c) domanda scuola se pratica non deducibile. Questo task e il posto dove accumulare nuove informazioni senza ricostruire il contesto.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Dopo il verdetto del braccio congiunto: analisi da export delle 12 residue (quali 4+8: gruppo, data, materia, docente bloccante; nessun run)
- [ ] #2 Confronto con la pre-assegnazione TASK-435: le 12 cadono dentro o fuori le ore pre-assegnate? Lo slack +5h e' consumato da dove?
- [ ] #3 Valutazione opzioni nel registro del task: (a) estensione PureHoleRepair-like alle H0 VRP-locked di docente; (b) aggiustamento perimetro pre-assegnazione; (c) domanda scuola se e' pratica non deducibile; aggiungere qui nuove opzioni man mano
- [ ] #4 Raccomandazione a maintainer con evidenza; nessuna adozione senza gate
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSA PER SOPRAVVIVENZA (25/08 18:10): le 12 H0 che questo task doveva analizzare sono SPARITE da sole col braccio congiunto delle cure (verdetto c1ee1997: triangolo FA 12->0). Nessuna analisi necessaria: il fenomeno e' stato assorbito dalle cure di input. Il task resta come record del contesto (cosa era il triangolo, cosa fe' TASK-435) ma non ha piu' oggetto.
<!-- SECTION:NOTES:END -->
