---
id: TASK-640
title: >-
  TASK-640 - Footprint memoria 2.6.0 nel ciclo completo: OOM a 2g e 4g dove
  2.3.0 girava a 2g (A/B 637 sospeso)
status: Done
assignee: []
created_date: '2026-09-21 21:15'
labels:
  - claude-planned
  - timefold-2.6
  - memory
  - blocker
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SECONDO blocco 2.6.0 (laptop 21/09 notte, A/B 637 sospeso): il ciclo completo che girava a heap 2g da mesi su 2.3.0 ora OOM-a su 2.6.0 sia a 2g (fase LS di B-TIMESLOT, ~10 min) sia a 4g (deroga dichiarata: B-TIMESLOT completa ma OOM alla query JDBC del pin-conservation check al rilascio). Log di entrambi i tentativi committati dal laptop in task637-results/. Nessun dato A/B valido. laptop fermata correttamente (0,2GB fisici liberi + podman DB ~5GB: terzo tentativo = rischio freeze documentato 13-14/09). SONDA IN CORSO: la validazione TASK-639 a 3g su Fedora gira LO STESSO CICLO: esito discrimina footprint-di-codice vs pressione-macchina. SOSPETTI se codice: PR 2596 RecordedUndoMove (undo+score per mossa trattenuti), riscrittura shadow-variable 2.5, retention nodi bavet. NOTA: i test 2.6.0 fin qui passati a 3g (golden 48s, banco LS 303s su 12.5k lezioni) indicano che il picco e' specifico del ciclo completo (LS lunga + release), non del carico base.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sonda in vivo: la validazione TASK-639 a 3g su Fedora (in volo) completa o OOM: discrimina footprint-di-codice vs pressione-macchina
- [ ] #2 Se OOM: profilazione heap del ciclo 2.6.0 (jcmd GC.class_histogram al picco) vs 2.3.0 stesso checkpoint: identificare la struttura cresciuta (sospetti: RecordedUndoMove/PR 2596 tiene undo+score per mossa; shadow-variable state supplies; bavel node retention)
- [ ] #3 Se completa a 3g: misura del picco heap 2.3.0 vs 2.6.0 sullo stesso ciclo (jcmd GC.heap_info campionato) per quantificare il delta
- [ ] #4 Cura all'altitudine giusta (config JVM/code nostra) o issue upstream con i numeri
- [ ] #5 Il ciclo completo su 2.6.0 torna nei binari di memoria documentati (heap 2-3g) prima di riprendere gli A/B notturni
<!-- AC:END -->

## SETACCIO + IPOTESI (22/09 mattina)

Bug-tracker completo (enumerato, negative evidence inclusa): nessun OOM mai riportato; PR 2642 quantifica la tempesta: elaborazione shadow-variable allocava ~40 GB/s (ridotti a ~10 in rc-1): spiega il footprint e, da rallentamento GC, anche la firma dello stallo. 2649 taglia l'overhead per-mossa dell'undo (2596). TUTTE le cure sono in 2.7.0-rc-1, NESSUNA in 2.6.0. ESperimento decisivo in coda: ripro su rc-1.


## CHIUSURA come indagine (maintainer 22/09), RESIDUO pratico

Il footprint 2.6.0 non si indaga oltre (stessa decisione). RESIDUO CONFIG da fissare in adozione rc-1: il ciclo completo su rc-1 e' stato validato a heap 8g; il floor operativo per i cicli (laptop runbook diceva 2g) va MISURATO su rc-1 (una run a 3g) e aggiornato nei mandati; le cure perf 2642/2660/2649 sono dentro rc-1. La firma OOM associata allo stall e' nella memoria occhi-aperti.
