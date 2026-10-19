---
id: TASK-603
title: >-
  UX disponibilita' docenti: griglia settimanale red/yellow/green per slot
  (senza materie/gruppi) che salva in RecurringAvailabilityRule
status: Done
assignee: []
created_date: '2026-09-11 15:56'
updated_date: '2026-09-11 21:49'
labels:
  - claude-planned
  - ux
  - input-collection
  - availability
  - workbook
milestone: m-14
dependencies: []
references:
  - TASK-602
  - TASK-445
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'audio referente 11/09 (transcript-2, 2:34): la griglia settimanale colorata PER DOCENTE e' il suo modello mentale dei vincoli ("per ognuno devi fargli la settimana di rosso, verde, rosso giallo"); la sua obiezione e' al COSTO Excel, non alla forma. Con una UX dedicata la stessa forma diventa il canale di input piu' affidabile: disponibilita' dichiarata dal docente, senza materie ne' gruppi (DISTINTA dalla matrice (materia,docente) dei TASK-601/602: due fogli workbook diversi).

SEMANTICA (mappa 1:1 sull'esistente): rosso = slot escluso -> AvailabilityType.VACATION (hard H5 via espansione); verde = preferito -> PREFERRED (soft); giallo = neutro -> nessun record (default). Entita' RecurringAvailabilityRule + AvailabilityExpansionService + REST CRUD gia' esistenti: manca solo la UI docente-centrica.

DUE LIVELLI (uno dentro l'altro):
(a) MINIMALE: griglia 5 giorni x 8 slot nella UI admin (click cicla colore, salva come RecurringAvailabilityRule). Zero autenticazione nuova; la compiliamo noi con loro al telefono/riunione. Sblocca subito la raccolta.
(b) COMPLETA: self-service con link individuale per docente (token per-docente, stato compilato/da compilare, vista sola-lettura del proprio). Feature di prodotto a se', valutare dopo (a).

AC di massima (livello a): (1) pagina griglia con i tre stati e caricamento dello stato corrente dalle regole esistenti; (2) scrittura idempotente (giallo = cancella la regola, non la crea); (3) round-trip col workbook (availability_compress.py) verificato; (4) rebuild + e2e minimi. Frontend: componenti in src/main/webui/src/components/admin/ accanto a TeacherAvailAdmin.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
LIVELLO (a) COMPLETATO l'11/09 notte (commit 9dff3c73 + 86ce8e84 + ed724bbb, pushati):

PAGINA: /admin/teacher-weekly-grid (TeacherWeeklyGridAdmin.tsx), griglia 5x8 con slot labels da useSlotSchema (pausa pranzo evidenziata), click cicla verde->rosso->giallo->verde, header del giorno cicla la riga (riga mista -> azzera a libero), legenda scolastica, salvataggio con dirty-check, lista read-only delle regole non esprimibili (windowed + always su giorni fuori griglia, caption onesta su cosa un save tocca).

SEMANTICA (verificata sul codice, non assunta): rosso=VACATION hard H5, giallo=PREFERENCE soft-avoid S3 (S3 PENALIZZA le lezioni nelle finestre PREFERRED: la mappa della descrizione originale era invertita, corretta), verde=default. Rosso domina giallo alla pittura.

BACKEND: endpoint bulk PUT /api/recurring-availability-rules/teacher/{id}: delete always + insert runs in UNA transazione, UNA espansione (il loop client-side aveva una finestra distruttiva: create fallita a meta' lasciava il docente a ZERO regole). GUARD (dalla review): ogni run validato contro le windowed SOPRAVVISSUTE (la griglia non le dipinge: senza guard un save poteva creare coppie sovrapposte che nessun boundary tollera, doppio-count H5/S3); runs con finestra rifiutati; weekend always-rules surfaced read-only.

VERIFICA SU SUPERFICIE REALE (rebuild + browser via Playwright MCP): pittura da regole esistenti, click, save atomico (1 PUT), persistenza, ricarica fedele al server, cancellazione con pulizia espansione. Banco REST 26/0 (test bulk: replace + windowed sopravvissuta + 400 run-windowed + 400 run-sovrapposto-a-windowed). ui-typecheck 0, vite build 0, rebuild 0. Suite completa 1304/0/0 + 1 flake transazionale sotto thrash (GoldenSolutionRegressionTest ritentato VERDE EXIT 0: pattern noto dell'ambiente, non del diff).

/simplify: StatusBanner condiviso (era la 12a copia inline; GroupCalendarAdmin migrata alla condivisa), baseline via useMemo (niente stato doppio desyncabile), cycleDay semplificato, lunch-border calcolato una volta. DEFERRED (da fare se la pagina resta): token CSS per i 3 colori nel tema (oggi ternario JS: il tema school collassa sui colori chiari), unificazione convenzione matrice con utils/groupCalendar.ts, grid 0-indexed. LIVELLO (b) self-service link-per-docente: NON fatto, valutare dopo l'uso del livello (a).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
