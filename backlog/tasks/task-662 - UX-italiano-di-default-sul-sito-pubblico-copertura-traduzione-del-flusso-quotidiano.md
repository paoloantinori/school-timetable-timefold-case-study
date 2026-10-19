---
id: TASK-662
title: >-
  UX: italiano di default sul sito pubblico + copertura traduzione del flusso
  quotidiano
status: Done
assignee: []
created_date: '2026-09-24 08:45'
labels:
  - claude-planned
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding major della UX review 24/09, misurato sul sito pubblico: lingua di default ENGLISH per staff italiano. i18n/index.ts:28 lng: navigator.language.startsWith('it') ? 'it' : 'en' con fallback en; un PC di scuola con locale non-IT apre tutto in inglese; la scelta manuale EN/IT non viene persistita (solo changeLanguage). Anche in modalita' italiana restano inglesi, verificati a schermo e in codice: Top Violations (nomi vincoli), 'Compaction requirements not met', card Hard/Medium/Soft (AnalysisPanel.tsx:321-341), descrizioni dei 58 ID vincolo nel tooltip lezione (LessonTooltip.tsx:72-136), messaggi feasibility dal backend (ScheduleResource.java:292-322), 'Solution not yet scored'. Paradossalmente messages_it.properties esiste con italiano planner-grade ('Docente {0} ha buchi nel suo orario il {1}') ma il pannello analisi chiama l'endpoint senza parametro lingua e prende il default inglese (AnalysisPanel.tsx:221 vs ScheduleResource.java:256).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Il sito pubblico parte in italiano per default (decisione esplicita, non navigator.language)
- [x] #2 La scelta manuale della lingua sopravvive al reload (localStorage)
- [x] #3 Zero stringhe inglesi hardcoded nel flusso quotidiano: verifica con walkthrough screenshot in italiano di Orario + Analizza + tooltip lezione + badge stato
- [x] #4 I dettagli violazioni dell'AnalysisPanel arrivano in italiano (parametro lang o traduzione lato UI)
<!-- AC:END -->
