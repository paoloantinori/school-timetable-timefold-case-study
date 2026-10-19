---
id: TASK-650
title: 'Unslop dei testi pubblicati: README + pagina Docs e stringhe webapp'
status: To Do
assignee: []
created_date: '2026-09-23 04:53'
updated_date: '2026-09-23 05:39'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Passaggio /pstack:unslop su TUTTI i testi pubblicati: README.md (repo + GitHub, faccia pubblica) e i testi della webapp (pagina Docs: constraints.ts, solverPhases.ts, e le stringhe UI visibili in italiano/inglese che descrivono vincoli e fasi). CONTESTO: la webapp e' il canale di interazione principale (direttiva UI=protocollo campagna) e il sito pubblico (planner.example.org) serve quella pagina: i testi sono la faccia verso la scuola. Regole unslop: frasi dichiarative brevi, niente long-dash, niente due-punti come connettore a meta' frase, niente riempitivi; stesso registro in it e en (i18n: messages_*.properties backend e locales frontend vanno allineati se toccati). PROCEDURA: censire i testi pubblicati (README, Docs page, badge/status UI), passare /pstack:unslop per file, diff a colpo d'occhio per non perdere contenuto tecnico (shorten by selecting, not compressing), rebuild UI + screenshot della pagina Docs prima/dopo (regola UI/layout: mockup renderizzato, mai solo testo). WARN: i testi dei vincoli sono il contratto documentativo delle ratifiche - l'unslop non cambia la semantica, solo la forma; dove forma e semantica si toccano (es. descrizioni con enumerazioni), prevale la precisione.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Esecuzione 23/09: agente unslop completata (~90 stringhe su README, docs.json en+it, phases/reconciliation/complexity/benchmark/admin/schedule locale, messages_en/it.properties; typecheck EXIT 0, JSON validati, nessun commit). SEI questioni semantiche segnalate: 5 CURATE a mano nel thread principale con semantica certa dalle ratifiche (h34 descrizione invertita en+it: il difetto e' la vuotezza non la presenza; manualDescription en stantio pre-demotion allineato a it+M28; h17 modale should->must en+it; h28 registro seconda->terza persona it; mcps traduzione 'peso simbolico' it). La sesta (ortografia è/e' mescolata) e' TASK-651 separata. RESTA APERTO: screenshot prima/dopo della pagina Docs (richiede rebuild+deploy webapp, finestra dedicata; l'agente l'ha saltato correttamente perche' il mandato vieta i rebuild).
<!-- SECTION:NOTES:END -->
