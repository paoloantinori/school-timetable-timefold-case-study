---
id: TASK-449
title: >-
  Allocatore pre-assegnazione per lab a tag mono-stanza
  (acconciatura/termoidraulica/meccanica): pattern 435 esteso alla
  risorsa-stanza scarsa
status: Done
assignee: []
created_date: '2026-07-28 20:13'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 272000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Contesto maintainer (sera 2026-07-28): la scarsita' dei tag per slot e' usata IMPLICITAMENTE dallo human planner come highly-constrained-resource nelle PRIME fasi della sua soluzione; le vecchie implementazioni avevano una fase euristica dedicata (HybridPhase1a acconciatura/labs con LabRotation) che assegnava quei lab puntando alla massima regolarita' settimanale - oggi dormiente (LabRotation non seedate 2026/27; il calendario importer TASK-364 pinna MODA/stage ma NON acconciatura/termoidraulica/meccanica). EVIDENZA gate-l5-m36: 256 ore lab room-null da collisione same-tag (acconciatura 142, termoidraulica 96, meccanica 18; ogni tag ha ESATTAMENTE 1 stanza utilizzabile). SOLUZIONE STRUTTURALE (human-faithful, principio 435 'pin the most constrained first'): un allocatore pre-assegnazione per RISORSA-TAG (generalizzazione di ConstrainedTeacherAllocator): per ogni tag mono-stanza, distribuire upfront i giorni/blocchi lab dei gruppi che lo richiedono su slot disgiunti con cadenza settimanale regolare (golden-first per il pattern dei giorni), validare 0hard via PreAssignmentValidator, pinnare CON stanza (lezione 435: pin senza stanza = room-less permanente). Il vincolo hard tag-capacity (treno 28/07 sera) resta come rete di sicurezza; questo task rimuove la pressione alla radice. Flag-gated default OFF fino a gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Allocatore per-tag con regolarita' settimanale (pattern giorni dal golden), pin con stanza, idempotente, loggato per gruppo/data
- [ ] #2 L0 unit + harness fine-costruzione: 0 collisioni same-tag, 0 room-null sui lab pinnati
- [ ] #3 Guard esistenti verdi (GoldenSolutionRegressionTest, ricorrenza >= floor)
- [ ] #4 Gate con flag ON: room-null lab = 0 senza residui M36 sui tag mono-stanza
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED OUT (verifiche indipendenti Fedora 2026-08-13 sui raw): A/B live entrambi i regimi - golden ON -5hard/3 tentativi senza 0hard vs OFF 0hard/2 tentativi; input ON -5hard/3 tentativi vs OFF 0hard. Il pre-booking mono-room-tag over-constrain il bacino introducendo residuo hard che il multi-start non cura. Prototipo resta committed default-OFF. La soluzione strutturale resta capacita' (piu' stanze/tag) o de-tag, non l'allocatore. Docs: task449-results/VERDICT-ab-2026-08-09.md + VERDICT-final-experiments-2026-08-11.md. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: zai
created: 2026-08-09 11:32
---
A/B LIVE PARTIALE (2026-08-09, zai, Fedora-offline): doc VERDICT-ab-2026-08-09.md. ab-task449-mono-room.sh, source=golden, within-session.

Arm OFF (control): VALID, 0hard (2 tentativi), 75,8% ricorrenza, 85,9% full-day, 112 min.
Arm ON: INCONCLUSIVE. baseline-golden.sh poll-cap 150min ha tagliato attempt 3 mid-B-TIMESLOT; capture fallito (Export failed). Best COMPLETED = attempt 2 a -19hard (NON 0hard). Segnale SUGGESTIVO-NEGATIVO: 449 ON non ha raggiunto 0hard in 2 tentativi (vs OFF 0hard in 2); -19hard e' peggio del bad-seed -6 del OFF, e il multi-start non lo cura in 3 tentativi. 449 ON sembra DEGRADARE l'hard (pre-booking over-constrain).

Difetto harness fixato: baseline-golden.sh POLL_CAP ora env-configurable (default 150). master-batch-2 (in coda dopo bad-seed) re-runna l'arm ON pulito con POLL_CAP=240: conferma NEGATIVA (non 0hard -> 449 Ruled OUT) o MISTA (0hard lento -> neutro). Verdetto finale dopo re-run.

PER FEDORA: NON adottare 449 ora (segnale suggestivo-negativo + Final Summary diceva non-aiuta). Il re-run chiarira'. Status resta To Do.
---

author: zai
created: 2026-08-09 22:23
---
A/B RE-RUN CONFIRMED NEGATIVE -> 449 RULED OUT (2026-08-10, zai). Re-run pulito master-batch-2 (POLL_CAP=240, 3 tentativi completi, 179 min, capture clean): arm ON best = -5hard (NON 0hard) anche con 3 tentativi. Arm OFF = 0hard in 2. Quindi 449 ON NON raggiunge 0hard neanche con 3 tentativi completi: il pre-booking mono-room-tag OVER-CONSTRAIN il bacino (introduce -5hard residuo che il multi-start non cura). Confermato su run pulita, non artefatto cap.

VERDETTO FINALE: 449 RULED OUT. NON adottare. Degrada l'hard (0hard -> -5hard) + non chiude il collo capacitivo acconciatura (residuo 142h/428). La soluzione strutturale resta capacita (piu' stanze/tag) o de-tag, non l'allocatore. Prototipo resta committed default-OFF. doc VERDICT-ab-2026-08-09.md (finale).
---
<!-- COMMENTS:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
