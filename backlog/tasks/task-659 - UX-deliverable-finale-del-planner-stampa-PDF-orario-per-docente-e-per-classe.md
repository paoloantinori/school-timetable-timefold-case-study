---
id: TASK-659
title: >-
  UX: deliverable finale del planner - stampa/PDF orario per docente e per
  classe
status: Done
assignee: []
created_date: '2026-09-24 08:45'
updated_date: '2026-09-26 17:10'
labels:
  - claude-planned
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding blocker della UX review 24/09 (adversarial, persona = segreteria didattica): il deliverable finale del planner NON esiste nell'app. Zero occorrenze di window.print/@media print/pdf in src/main/webui (grep verificato). L'unico export e' GET /schedule/export che scarica schedule-export-<ts>.json il cui stesso tooltip dice 'Esporta l'orario come JSON per test A/B' (UnifiedSolverControls.tsx:608-624, it/schedule.json). La vista Per Docente / Per Classe esiste in griglia (TimelineHeader.tsx:108-121) ma non se ne puo' produrre alcun artefatto. Il lavoro del planner finisce con 'condividere l'orario con docenti e classi': oggi dead-end.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Esiste una via per produrre un orario per DOCENTE (settimanale) in forma stampabile (print CSS o PDF), con intestazione anno scolastico e nominativo
- [x] #2 Esiste una via per produrre un orario per CLASSE (settimanale) in forma stampabile
- [x] #3 La stampa non include chrome dell'app (nav, bottoni, pannelli dev): solo titolo, griglia, legenda materia
- [x] #4 Verifica con screenshot della stampa/rendering (regola: mai ASCII, pagina renderizzata)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-26 17:20 IMPLEMENTATA LA SCELTA DI maintainer 'b e c' (26/09): UNA resa server (PrintViewService, XHTML strict) per entrambe le rappresentazioni: (b) GET /print/{docente|classe|aula}/{id} = vista dedicata senza chrome con toolbar screen-only (stampa + link PDF), (c) GET /print/{kind}/{id}.pdf = stesso documento via openhtmltopdf (fork VIVO io.github 1.1.87 verificato su repo1, il com. del 2021 e' morto). Colori = priorita' schermo (teacher.color > course.color > hash pastel; docente sheet per-materia per non essere monocroma; input admin validato safeHex strict). Bottone Stampa nell'header abilitato da UNA selezione filtro (docente o classe), indipendente dal raggruppamento. Nome istituto da property planner.print.school-name. Gates: /simplify 4-angle + /code-review high 8-angle COMPLETE (F1 injection chiusa, F3 N+1 confutato con evidenza EAGER). Test: PrintResourceTest 5/5 (HTML, classe, PDF %PDF, 404, week malformata). AC#1-3 soddisfatte; AC#4 (screenshot) = verifica dal vivo sul pubblico dopo il deploy.

2026-09-26 19:20 VERIFICA VIVA SUL PUBBLICO (41fa0a98 deployato, suite 1389/0): (b) /print/docente/1?week=2026-09-14 renderizza la scheda completa (istituto, A.S. 2026/27, T02, 18 celle colorate coi colori docente del seed, lun-ven, toolbar, legenda 4 voci); emulazione print nasconde la toolbar (foglio pulito); (c) /print/docente/1.pdf?week=... = 200 application/pdf 7505B magic %PDF. Screenshot: /tmp/print-659-{docente,docente-print}.png su laptop. TASK CHIUSO 4/4 AC: il deliverable finale del planner esiste.
<!-- SECTION:NOTES:END -->
