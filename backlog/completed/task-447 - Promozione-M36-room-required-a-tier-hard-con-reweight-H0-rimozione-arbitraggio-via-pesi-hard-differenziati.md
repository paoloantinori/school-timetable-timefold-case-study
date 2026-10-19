---
id: TASK-447
title: >-
  Promozione M36 room-required a tier hard con reweight H0 (rimozione
  arbitraggio via pesi hard differenziati)
status: Done
assignee: []
created_date: '2026-07-28 19:07'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 270000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Proposta maintainer (sera 2026-07-28, durante il loop fix del gate L5 446): il retier di H27->M36 a medium pesante fu scelto per non invalidare la comparabilita' storica (hard = conteggio boxed in tutti i benchmark/VERDICT) e le invarianti dei repair che leggono i delta hard - NON perche' il tier hard sia sbagliato. Se si accetta la discontinuita', entrambi possono stare a hard con pesi diversi, es. H0 ofHard(2) e H27-room-required ofHard(1): l'arbitraggio da svuotamento sparisce perche' de-assegnare passa da -1 (H27) a -2 (H0), peggioramento hard netto. Coerente con la direttiva tripla-applicabilita' (i logicamente-hard tendono a hard) e col playbook H17: repair/stabilizzazione prima, promozione poi. PREREQUISITI da verificare prima del tentativo: (1) censimento dei siti che leggono lo score hard assumendo 1 hard = 1 lezione boxed (StagedPinPolicy, assertImproveOrNoop dei repair, script di analisi, drill-down UI); (2) accettazione esplicita della rottura di comparabilita' coi run storici (nuova baseline da quel punto); (3) catena checkpoint: L0 unit anti-arbitraggio (unassign peggiora hard netto), harness fine-B-ROOM (room assegnate, B-TIMESLOT non si svuota, boxed stabile), poi UN gate A/B vs M36-medium su hard-0-tempo e KPI parita'. Giudizio sull'evidenza, ratifica maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Censimento dei siti hard-score-dipendenti completato e allegato al task
- [ ] #2 L0 anti-arbitraggio verde (unassign = peggioramento hard netto con H0=2/H27=1)
- [ ] #3 Harness fine-B-ROOM: room-null unpinned reali = 0, boxed non in crescita
- [ ] #4 A/B vs M36-medium: tempo-a-hard-0 e KPI in parita' o meglio
- [ ] #5 Ratifica esplicita maintainer per l'adozione del tier hard
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
OBSOLETO/ASSORBITO: ratificato come H29 hard weight 1 il 2026-07-28 (TASK-447 VERDICT), stringa "H29 - Assigned lesson must have a room" verificata in SchoolScheduleConstraintProvider il 2026-08-14. Con H0 reweighted a hard 2. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
