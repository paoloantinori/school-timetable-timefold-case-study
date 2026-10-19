---
id: TASK-528
title: >-
  Pagina 'Vincoli' nel sito: condensa visiva di TUTTI i vincoli input con
  verifica veloce e modifica rapida
status: To Do
assignee: []
created_date: '2026-08-19 06:39'
updated_date: '2026-08-19 07:22'
labels:
  - claude-planned
  - ui
  - feature
  - input-quality
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Richiesta maintainer 19/08: oggi i vincoli input sono sparsi across 12 pagine admin CRUD e nessuna vista li condensa. Serve una pagina che favorisca verifica facile/veloce e modifica altrettanto veloce. I vincoli utente vivono in: TeacherAvailability, RecurringAvailabilityRule, GroupCalendarRule, DailyActivityQuota, StagePattern, ProfessionalizzantiDay, LabRotation (0 sul 2026/27), LabAssistant, campi Teacher (maxDailyHours, maxEarlyStartDaysPerWeek, requiredFreeDaysPerWeek), esenzioni/override. La pagina e' anche il ponte verso il workbook: quando la scuola dichiarera' le pratiche (foglio co-presenza, ritmo, ecc.), qui si vedranno accanto ai vincoli classici.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Nuova pagina 'Vincoli' nel nav principale (accanto a Docs) che aggrega in UNA vista tutti i vincoli passati in input, organizzata per categoria leggibile: disponibilita' docenti (eccezioni, giorni fissi dedotti con evidenza 'dedotto, non dichiarato'), calendari per gruppo (finestre per weekday, quote per data, sessioni/usanze), stage (finestre per gruppo), rotazioni lab, co-presenza, cap docenti (H7 maxDailyHours e per-gruppo), regole settimanali (M24/M25 per docente), professionalizzanti
- [ ] #2 Ogni riga mostra: chi e' vincolato (docente/gruppo), cosa, valore, FONTE (foglio di provenienza o 'dedotto'), e azione veloce di modifica (link alla pagina admin competente o edit inline dove banale)
- [x] #3 Contatori in testa: quante regole attive per categoria, con evidenza di quelle caricate da import vs dichiarate da UI (provenienza)
- [x] #4 Le divergenze note (input vs golden, classe referente: G08 666 vs 626 ecc.) evidenziate in una sezione 'da confermare con la scuola'
- [x] #5 Vuoto = dichiarato: le categorie senza righe (es. rotazioni lab = 0 sul 2026/27) mostrate esplicitamente come 'nessuna dichiarata' perche' il silenzio e' informazione
- [x] #6 Backend: endpoint REST aggregato dedicato (es. GET /api/constraints/summary) che raccoglie tutto in un colpo, niente N chiamate dal frontend
- [ ] #7 Verifica browser con l'utente reale (maintainer): la pagina deve rispondere alla domanda 'cosa abbiamo detto al solver?' in 30 secondi di lettura
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 07:22
---
IMPLEMENTATA (708a32ef) e VERIFICATA IN BROWSER (http://localhost:8080/constraints): backend ConstraintSummaryResource GET /api/constraints/summary (una chiamata sola, AC#6) con 9 categorie popolate dai conteggi live del DB, emptyMessage esplicite per rotazioni/professionalizzanti/assistenti (AC#5) e sezione daConfermare con le 3 divergenze referente (AC#4). Frontend ConstraintsPage: card contatori, sezioni con conteggio+provenienza+fonte, tabelle righe (chi/cosa/valore/fonte), sezione ambra da-confermare. Nav 'Vincoli' accanto a Docs. Screenshot task528-constraints-page.png. rest-api.md aggiornata.
---

created: 2026-08-19 07:22
---
AC#2 (azione veloce di modifica / link alle pagine admin) NON completa: le sezioni mostrano i dati ma non ancora i link admin: da aggiungere in un follow-up (mappa categoria -> rotta admin: teachers/availability/calendars). AC#7 (verifica con maintainer) in attesa: la pagina e' live sul dev e pronta per la lettura di 30 secondi.
---
<!-- COMMENTS:END -->
