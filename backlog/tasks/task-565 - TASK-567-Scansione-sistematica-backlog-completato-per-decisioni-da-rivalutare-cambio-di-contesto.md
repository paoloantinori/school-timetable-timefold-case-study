---
id: TASK-565
title: >-
  TASK-567: Scansione sistematica backlog completato per decisioni da rivalutare
  (cambio di contesto)
status: Done
assignee: []
created_date: '2026-08-29 05:34'
updated_date: '2026-08-29 11:41'
labels:
  - claude-planned
  - meta-review
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scansione sistematica del backlog completato (128 task) per identificare decisioni la cui motivazione di reiezione potrebbe non reggere piu' nel contesto attuale. Il contesto e' cambiato radicalmente negli ultimi 10 giorni: H34 introdotto (il baseline hard e' passato da 0hard a -12/-81), le metaeuristiche di agosto sono state rivalutate (misuravano H29, non le metaeuristiche), la cura F1 ha cambiato il seed. Una bocciatura motivata da '0hard -> -5hard' oggi sarebbe accettabile; una bocciatura basata sulla campagna metaheuristic di agosto ha un presupposto infondato.

CANDIDATE IDENTIFICATE NELLA PRIMA SCANSIONE:
1. TASK-292 (cartesianProduct): RULED-OUT per campagna agosto, ma la campagna misurava H29. Già implicitamente rivalutato dal re-benchmark 29/08 (nessuna adozione pratica). Aggiornare motivazione.
2. TASK-449 (allocatore lab mono-stanza): RULED OUT per '0hard -> -5hard' + 'non chiude collo'. La prima motivazione non regge piu' (baseline -12). La seconda regge. Candidata debole.
3. TASK-470 (cura M-TR): PROPOSTA gated, mai implementata né testata. Dormiente, non bocciuta. Da riesumare se il contesto lo giustifica.

La scansione completa va fatta leggendo le Final Summary di tutti i 128 task completati e classificando le reiezioni per: (a) motivazione ancora valida, (b) motivazione superata dal cambio di contesto, (c) proposta dormiente mai testata.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Scansione sistematica di backlog/completed/ con vocabolario di reiezione
- [x] #2 Per ognuna: la motivazione regge al cambio di contesto (H34, baseline -12, campagna agosto rivalutata)?
- [x] #3 Lista candidate a rivalutazione con motivazione e priorita'
- [x] #4 Task di rivalutazione creati per le candidate effettive, con evidenza
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
29/08 13:45 scansione completata; esiti nel final summary; task di rivalutazione TASK-569/570 creati.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Scansione eseguita 29/08 (agente Explore, 128 file completati letti; 16 decisioni di reiezione/rinvio trovate; 11 corto-listati assorbiti/ratificati/analisi senza reiezione). ESITO: (a) motivate ancora valide 8 (221/276 G14, 336 rolling-horizon, 440, 038, 468, 472, 470); (b) premessa SUPERATA dal cambio contesto 8 (296, 292, 291, 476, 449, 280, 281, 332); (c) dormienti 0 (i prototipi erano tutti implementati e A/B-testati). TASK-470 pre-identificata CONFUTATA: e' classe (a) (reiezione su evidenza medium di A/B live, non su campagna/baseline/seed; era stata implementata e testata, non dormiente). Verifica a campione delle citazioni: 4/4 combaciano (296:83, 292:79, 476:54, 470:45). Task di rivalutazione creati: TASK-569 (famiglia metaheuristic, 6 task, con primo passo obbligatorio: distinguere artefatti pre-riparo vs banco riparato 04/08) e TASK-570 (476 T1/T2/T3); 449 registrata come confermata-mitigata (RoomTailFinisher chiude il suo bersaglio) dentro TASK-570.
<!-- SECTION:FINAL_SUMMARY:END -->
