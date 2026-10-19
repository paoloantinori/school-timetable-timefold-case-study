---
id: TASK-517
title: >-
  Golden placement-parity check: nessun piano di pre-assegnazione piu' rigido
  della pratica di piazzamento del golden (chiusura famiglia B della
  meta-indagine)
status: Done
assignee: []
created_date: '2026-08-17 13:28'
updated_date: '2026-08-17 20:33'
labels:
  - claude-planned
  - validation
  - golden-first
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Esito della meta-indagine 17/08 (richiesta da maintainer): i fallimenti delle ultime settimane si dividono in due famiglie. Famiglia A (vincoli piu' stringenti del golden: H15/H16/H21/H22/S2/M-TC/M-TR): gia' corretta e istituzionalizzata dalla direttiva golden-first TASK-379. Famiglia B (PIANI piu' stringenti del golden): NON codificata, tre occorrenze - TASK-432 (giovedi' monografici forzati per-gruppo, sovrapprenotazione dei giovedi' contesi di T01), TASK-430 (ancore slot-0 del golden imposte al 100% dove il golden le pratica al 99,86%), chain round 2-6 (pin lab a cap H7 dove il golden tiene 4h su 6 nei giorni con RA). Il tratto comune: pattern estratto dal golden (sembra golden-compatibile) applicato con piu' rigidita' di quanta il golden ne usi; ogni vincolo resta legale, l'arrangiamento e' piu' denso, il solver non recupera perche' le parti imposte sono fisse. La direttiva golden-first non scatta perche' controlla le regole, non i piani. Il check di parita' di piazzamento e' la generalizzazione del test fatto a mano il 17/08 (T15 4h nei giorni RA).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Check implementato come dimensione dell'audit TASK-506 (o test separato se piu' naturale): per ogni (docente, gruppo) che un piano di pre-assegnazione pinnerebbe, confronto tra la distribuzione di carico giornaliero del PIANO e quella REALE del golden per la stessa coppia (per weekday, includendo il mix materie enumerata/non-enumerata)
- [x] #2 Segnala ogni asse su cui il piano e' ovunque piu' denso del golden (esempio di riferimento misurato 17/08: il golden tiene T15 a 4h su cap 6 nei 13 giorni con RA di G04; un piano che satura a 6 con solo lab deve essere flagged)
- [x] #3 Validazione sui tre casi storici della famiglia B: TASK-432 (giovedi' per-gruppo), TASK-430 (ancore slot-0), chain round 2-6 (pin lab a cap): il check deve flagsare tutti e tre (ricostruiti dai rispettivi task-results o dai flag OFF nel codice)
- [x] #4 Calibrazione: il check NON deve flagsare i piani buoni (control della chain, import calendario/golden stesso)
- [x] #5 Proposta di paragrafo CLAUDE.md che estende la direttiva golden-first ai PIANI (regola: nessun piano di pre-assegnazione puo' essere piu' rigido della pratica di piazzamento del golden sulle stesse coppie), da ratificare
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-17 20:33
---
IMPLEMENTATO (laptop 17/08 notte, commit cc62f69a): scripts/analyze/placement-parity-check.py, 3 assi (A densita' per-weekday pair, B ancora slot-0, C concentrazione materia-weekday), read-only exit 0.
---

created: 2026-08-17 20:33
---
AC#3 validazione: self-test con le tre firme storiche perturbate sul golden (tutte scattano) + piano REALE chain run4 (23 flag, incluse le firme T15 e Lavorazioni meccaniche G04 100% vs golden 70/82 spalmato). AC#4 calibrazione: golden self-check 0 flag; piani ratificati non aggiungono pin oltre il golden.
---

created: 2026-08-17 20:33
---
AC#5: proposta di paragrafo CLAUDE.md (direttiva golden-first estesa ai PIANI) scritta in claudedocs/spalmone-2026-27/task467-results/placement-parity-check-2026-08-17.md, DA RATIFICARE, non applicata.
---

created: 2026-08-17 20:33
---
Nota: la validazione TASK-430/432 e' per firma sintetica sul golden (le loro run originali non sono disponibili come export); la firma chain r2-6 e' validata sul run4 reale. Dichiarato nel doc.
---
<!-- COMMENTS:END -->
