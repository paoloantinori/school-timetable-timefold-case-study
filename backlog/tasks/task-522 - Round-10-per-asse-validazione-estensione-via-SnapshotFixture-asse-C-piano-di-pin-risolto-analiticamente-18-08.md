---
id: TASK-522
title: >-
  Round 10 per-asse + validazione estensione via SnapshotFixture (asse C = piano
  di pin, risolto analiticamente 18/08)
status: Done
assignee: []
created_date: '2026-08-18 09:10'
labels:
  - claude-planned
  - solver
  - chain
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'analisi 18/08 su ab-on-run8-final/export.json.gz (verificata sul campo pinned di ogni lezione): tutti e 7 i flag di concentrazione (asse C) del parity check sono creati dal PIANO DI PIN, non dal solver (4 coppie 100% pinnate, le libere allineate al weekday pinnato). Conseguenze: l'estensione del gate (asse A) non li cura (assi diversi); il round 10 va letto per asse o la ratifica sembrera' fallita; la cura di C, se voluta, sta nel deriver (weekday per materia dallo spread golden). Nota instradata a zai il 18/08: questo task ne traccia la verifica d'uso.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Validazione del filone 2 (estensione gate asse A) con SnapshotFixture.loadFromExport sull'export ab-on-run8-final PRIMA del round 10 completo: il gate esteso contro il piano reale run8, flag A attesi a zero in secondi, senza run alla cieca
- [ ] #2 Nel ROUND 10 il parity check riportato PER ASSE: flag A obiettivo zero, flag C attesi ~7 residui (forma del piano, mai danno: nessun boxed)
- [ ] #3 Eventuale ratifica chain: criterio dichiarato per asse nel testo della bozza
- [ ] #4 Cura asse C (deriver: weekday per materia seguendo lo spread golden) tracciata come task separato se/un giorno richiesta: non e' danno, e' igiene famiglia-B
<!-- AC:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
