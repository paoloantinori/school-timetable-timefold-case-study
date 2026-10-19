---
id: TASK-485
title: >-
  M-TC: esenzione co-presenza praticata (mirror H-COPRESENCE) per chiudere la
  cura pulita su seed-6
status: Done
assignee: []
created_date: '2026-08-03 11:49'
updated_date: '2026-08-03 12:52'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
type: enhancement
ordinal: 304000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up dell'A/B TASK-455 seed-6 (2026-08-03). M-TC cura l'hard (0-hard, collisione meccanica relief-ata) ma costa -204.714 medium di cui -120.000 e' penalta' FALSA su 120 match di co-presenza PRATICATA dal golden (acconciatura 107 + moda 13, meccanica 0). M-TC non esenta le coppie praticate depth>=2 come fanno H2/H-COPRESENCE.

Fix: aggiungere a SchoolScheduleConstraintProvider.labTagCapacity un'esenzione per le celle (tag, startDateTime) la cui domanda e' coppie praticate dal golden depth>=2, riusando la depth map gia' pubblicata da ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth (stessa sorgente di H-COPRESENCE). NON fare sweep di peso (parallelo M-TR: overflow praticati non scarsi, peso e' sub-leva sbagliata).

PRE-CONDITION (key follow-up differito): verificare sul golden depth map se la coppia meccanica G04 + G13 del 2026-11-04 e' praticata (depth>=2). Se SI, l'esenzione va limitata per non disattivare il relief del target (altrimenti room-null torna); se NO, l'esenzione pulita preserva la cura.

Validazione: re-run ./scripts/benchmark/task455-mtc-seed6.sh dopo l'esenzione. Atteso: 0-hard + medium ~come OFF (cura pulita, esito a). Confronto vs task480-results/badseed-frequency/grasp-seed-6.

NESSUNA adozione in questo task: M-TC resta default-OFF. Ratifica a maintainer solo dopo che l'esenzione e' misurata pulita. Riferimenti: task455-results/VERDICT-ab-mtc-seed6-2026-08-03.md, CLAUDE.md M-TC + H-COPRESENCE.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-08-03 zai] IMPLEMENTATA E VERIFICATA. labTagCapacity filter ora esenta le celle practiced via !SchoolSchedule.isCoPresenceCell(tag, start.getDayOfWeek(), start.toLocalTime()) (stesso bridge e predicato di coPresenceCap/H-COPRESENCE, una sola definizione). TDD: LabTagCapacityConstraintTest +1 test (practiced cell exempt), 5/5 GREEN. Verifica meccanica del riuso: bridge byte-identical a coPresenceCap. Pre-condizione (1) verificata col numero: meccanica|WEDNESDAY|13:00 golden depth=1 (NON praticata) -> esenzione lascia fuori il target, cure preservata. A/B conferma (ab-mtc-seed6-exemption): 0-hard, M-TC match 120->0 (penalita' falsa -120.000 SPARITA), medium -1.464.102 -> -1.353.502. MA medium NON tornato a OFF (-1.259.388): -94.114 residuo e' displacement REALE (M20 -53k, M15 -31k, spostamento lezioni per dare stanza alla meccanica), non penalita' falsa (full-day -0,8pp identico con/senza esenzione). L'esenzione ha rimosso la parte falsa, il costo reale del cure resta. KEY OPEN QUESTION per ratifica: il -94k e' costo del cure su seed-6 o M-TC perturba la LS anche sui bacini buoni? A/B good-seed non fatto. Verdetto: task455-results/VERDICT-ab-mtc-seed6-2026-08-03.md (sezione FOLLOW-UP).
<!-- SECTION:NOTES:END -->
