---
id: TASK-652
title: >-
  Consolidare le 8 copie del predicato stanza-libera-a-slot in solver/ (+
  esenzione co-presenza H2 come parametro)
status: Done
assignee: []
created_date: '2026-09-23 05:45'
updated_date: '2026-09-27 20:23'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla review /simplify della TASK-641 (altitudine+reuse, 23/09): il predicato "stanza libera a slot di destinazione" esiste in 8 implementazioni hand-rolled nel package solver/ (ViolationScopedSteepestFinisher.freeRoomsAt, CompositeSwapFinisher.roomFor/Live.roomTaken, HoleRelayFinisher.roomFor con ~20 call-site su Live, RoomTailFinisher.freeTagRoomAt/freeOrdinaryRoomAt su PureHoleRepair.Grid.roomCell, QuotaFillRepair roomBusy inline, OneOffFinisher.Index.roomAtSlot, SurplusHarvest), con tre schemi di chiavi diversi (timeslot.id vs oggetto Timeslot vs stringa "id|date|slot") e protocolli di esclusione propri. COSTO: le copie divergono gia' sugli assi che contano; NESSUNA modella l'esenzione co-presenza di H2 (una coppia ospite golden-practiced puo' legalmente condividere stanza: ogni copia privata e' conservativa e perde classi di cure). La forma con insieme di esclusione della 641 e' il candidato a predicato condiviso. CONSOLIDAMENTO: estrarre un unico helper canonico (con l'esenzione come parametro) e migrare gli 8 siti; richiede banco di non-regressione sui sibling (RoomTail/HRF/CSF hanno i propri indici mutanti, non e' drop-in meccanico). La 642 (metamodel resolver) e' lo stesso filone di consolidamento, sito diverso.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Forma adottata (2026-09-27)

**Il predicato condiviso e' `RoomAvailability` (solver/), l'ITERAZIONE resta nei siti**: gli 11 siti censiti non condividono solo il predicato ma l'INDICE di occupanza, e l'indice (non il predicato) e' la parte load-bearing di ogni pass (dichiarato nel javadoc di classe con la ragione per-caso). Un indice unico sarebbe il cambiamento sbagliato: gli indici differiscono PER MOTIVI REGISTRATI (roomCell string-keyed incrementale HRF/CSF con invariante F5, single-holder probe RoomTail/PHR, count-map VSF holdersBySlotRoom perche' un Set maschererebbe una terza lezione co-presente, Room->count incrementale OneOff resolveRoom, Map<Long,Set<Long>> incrementale con undo EDF).

**API**: `occupies(Lesson,Room,Timeslot)` (primitive, equals DEL DOMINIO: via rapida identita' + id; timeslot testato PRIMA della stanza perche' e' il discriminante selettivo negli scan non raggruppati), `conflicts(mover,room,ts,other,exemption)` (self-exclusion via Lesson.equals + hook esenzione H2), `occupiedRoomIdsBySlot` (bulk build one-pass, pattern VSF), `occupied(room,ts,list)` (forma no-mover SurplusHarvest).

**Siti DELEGANTI (4)**: QuotaFillRepair.findMove (scan inline -> conflicts, flag double-break collassato in labeled continue `donorLoop`), SurplusHarvest.Occupancy.roomBusy (bySlot), ViolationScopedSteepestFinisher.generateRoomVariants (bulk build), OneOffFinisher block-move roomTaken:287 (il sito mancato trovato dalla review /simplify, con cintura morta `|| y.room == x.room` rimossa: e' la via rapida di identita' di Room.equals).

**Siti TENUTI BY DESIGN (7, censiti nel javadoc)**: HRF/CSF roomTaken, RoomTail+PHR.Grid cellOk, OneOff resolveRoom, EDF roomsByTs, VSF holdersBySlotRoom + freeRoomsAt probe. Regola per il futuro: quando un'esenzione verra' cablata in un sito tenuto, il per-pair passa da `conflicts` cosi' la semantica resta single-sourced.

**Esenzione co-presenza H2**: parametro come da titolo task; NESSUN sito la passa oggi (null = comportamento conservativo attuale, piu' stretto di H2: dichiara il blind spot). Il predicato CANONICO da delegare e' `BaseConstraintProvider.isCoPresenceGuestPair(a,b)` (puntato nel javadoc dell'interfaccia, con contratto argomenti: la cella si legge dall'occupante `b`).

## Delta documentati (verificati skeptic CONFIRMED)

(a) QFR/OneOff passano da equality id-only a equals di dominio: widening che spara solo dove il vecchio NPE-ava (fatti null-id stessa-istanza: crash->BUSY, direzione sicura) o su cloni same-id (irraggiungibile: lista lezioni JPA un riferimento per PK). (b) build VSF salta lezioni null-id prima indicizzate sotto chiave null (intrivialmente osservabile: l'unico consumer sorta per room.id e NPE-rebbe prima). Prosa precisata: OneOff era GIA' equals-based (pure equivalence), il delta reale e' la self-exclusion QFR `o != l` -> `!o.equals(l)` (identico negli stati raggiungibili).

## Residui dichiarati

- QFR:487 busyTeachers (H1) resta id-only: preesistente, fuori da questo diff (censito dalla review).
- Due invarianti non enforced da assertion (skeptic): (1) mai due Lesson same-id nella stessa lista; (2) sync bySlot/occ.move in SurplusHarvest. Entrambe strutturali oggi.
- VSF javadoc "DECLARED BLIND SPOT" (esenzione non cablata) resta aperto fino a che un sito non passa l'esenzione.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso 27/09 sera. Consegna: RoomAvailability (predicato unico stanza-libera-a-slot, equals DI DOMINIO + hook esenzione co-presenza H2 come parametro, predicato canonico isCoPresenceGuestPair puntato nel javadoc). 4 siti deleganti (QFR findMove con labeled continue, SH roomBusy, VSF bulk build, OneOff:287 con cintura morta rimossa), 7 siti tenuti BY DESIGN coi propri indici (census completo nel javadoc: l'indice e' la parte load-bearing, un indice unico sarebbe il cambiamento sbagliato). Delta documentati e verificati: id-only -> equals dominio (widening: spara solo dove il vecchio NPE-ava, crash->BUSY direzione sicura); build VSF salta null-id (inosservabile).

EVIDENZA: /simplify 4 reviewer (6 finding applicati, scarti motivati); code-review high CLEAN con mutation-test eseguito sul test (gamba rimossa -> fallisce il test giusto -> ripristinato); silent-failure CLEAN; skeptic CONFIRMED; suite Fedora 1398/0/104 EXIT 0 (baseline 1391 + 7 RoomAvailabilityTest); banco laptop su snapshot reale h32b-run1: QuotaFillRepairCheckpointTest VERDE (4 moves / 1 cella / 0 conflitti), SurplusHarvestLiveStateTest VERDE; PureOneOffCheckpointTest ROSSO (-151 vs -12) con RUN DISCRIMINANTE al commit PRE 1f8456d7 IDENTICO: pin stantio dei round TASK-574, NON regressione 652 (task di re-pin: TASK-672). Commit 898beabd pushato.
<!-- SECTION:FINAL_SUMMARY:END -->
