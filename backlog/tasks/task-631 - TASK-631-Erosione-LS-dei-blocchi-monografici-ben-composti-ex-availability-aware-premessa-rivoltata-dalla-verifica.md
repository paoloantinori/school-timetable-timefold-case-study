---
id: TASK-631
title: >-
  TASK-631 - Erosione LS dei blocchi monografici ben composti (ex
  availability-aware: premessa rivoltata dalla verifica)
status: To Do
assignee: []
created_date: '2026-09-20 06:52'
updated_date: '2026-09-20 15:55'
labels:
  - claude-planned
  - construction
  - availability-aware
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PREMESSA RIVOLTATA (20/09, verificato su checkpoint): la costruzione compone GIÀ il giovedi' G13 10/06 contiguo (T15 Cad 8-11, POST_CONSTRUCTION di integrato-1 confermato dal laptop + dump deterministico 18/09). Il buco del consegnato nasce a VALLE: la LS dissolve ~7 ore Cad di T15 fuori da giugno e consolida le LMC di T10 dentro 9-10/6 (4h il 9 + 2h il 10 pomeridiane), lasciando il gap 10-11 che il repair non chiude (T10 VRP-bloccato la mattina del giovedi', diagnosi 630 valida). IL TASK SI RISCRIVE come diagnosi+cura dell'EROSIONE LS dei blocchi monografici composti bene: (fase 1) censimento di quale fase/mossa frantuma il blocco (sospetto meccanico: l'esenzione break-level dell'H17 rende gratuita la frantumazione mentre i vicini sono pinnati, e la demozione selettiva lascia il buco stranded dopo lo spostamento; ma il blocco era PINNATO post-promozione: serve la traccia di quale demozione lo ha sciolto e con quale guadagno medium), (fase 2) cura candidata al livello giusto (guard alla demozione che lascerebbe un buco esentato, o protezione M32-style del blocco monografico, o gamba repair che riporta le ore erose), (fase 3) banco differenziale + ratifica. La domanda originale di maintainer (come indirizziamo il solver sulle finestre di disponibilita') resta aperta ma la risposta non e' nel builder per questo caso: la composizione availability-aware resta un'ipotesi senza caso noto.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Meccanismo nel WeeklyTemplateBuilder: lettura delle RecurringAvailabilityRule per (docente, weekday) e preferenza per assegnazioni che producono giorni contigui dentro ciascuna finestra di disponibilita' (nessun nome hardcoded)
- [ ] #2 #2 Banco differenziale: costruzione con e senza il meccanismo su N>=2 semi. Firma misurata: giorni con buchi H17 a costruzione, BLOCK-form per (gruppo, giorno), contiguita' per-docente. Predizione: G13 Thursday 10/06 chiude (giorno contiguo T15 8-11 + T10 12-13)
- [ ] #3 #3 Golden-parity: il pattern di assegnazione del meccanismo coincide col golden per i casi noti (G13 Thursday = T15 morning; casi che il golden mostra)
- [ ] #4 #4 Adattabilita': test con input modificato (vacazione spostata da T10 a T15) che produce l'assegnazione opposta, dimostrando che la logica e' data-driven non hardcoded
- [ ] #5 #5 Se banco positivo: gate end-to-end con A/B appaiato e ratifica maintainer
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
20/09 POMERIGGIO, VERIFICA DI PREMESSA IN CORSO (micro-mandato laptop): il dump deterministico di costruzione del 18/09 (target/construction-lessons.txt, catena 599 attiva) mostra il giovedi' G13 2027-06-10 GIÀ CONTIGUO A COSTRUZIONE: T15 Cad @8-9-10-11 (la forma golden che il meccanismo vorrebbe produrre). Se il checkpoint POST_CONSTRUCTION di integrato-1 conferma, la PREMESSA del task cambia: il buco del consegnato e' EROSIONE LS a valle (meccanismo candidato: l'esenzione break-level dell'H17 rende gratuita la frantumazione di un blocco pinnato, e la demozione selettiva lascia il buco stranded che il repair non chiude perche' T10 e' VRP-bloccato), e la cura giusta e' protezione-dell'erosione (lato demotion/LS), non composizione availability-aware. Verifica in volo.

20/09 ESITO VERIFICA (laptop, checkpoint POST_CONSTRUCTION integrato-1): giovedi' 10/06 CONTIGUO a costruzione (T15 Cad 8-11 monografico; 06-03 stage puro, 06-17 oltre orizzonte, coerenti). Consegnato invece: T15 2h @8-9 + GAP 10-11 + T10 LMC @12-13, e il 9/6 ribaltato da Cad T15 a LMC T10. La ristrutturazione LS muove ~7 ore Cad fuori giugno e 6 ore LMC dentro 9-10/6. Prossimo: censimento fase/mossa (artefatti: phases/phase-markers del ciclo se presenti, o replay LS su POST_CONSTRUCTION con census delle demozioni che toccano celle promosse).

20/09 SERA, BANCO DIAGNOSI FASE (laptop 25d649a7, resume v132 + phase-transition dump, 66min, EXIT 0): l'erosione della monografia avviene DENTRO la fase LS B-TIMESLOT (entry: 8-11 tutte Cad T15; fine LS: 8 Cad / 9-10 Tecniche di saldatura / 11 LMC, giorno ancora CONTIGUO). In questa race il buco NON si e' formato: la forma consegnata di integrato-1 e' race-dipendente (coerente col verdetto 628 famiglie-migranti). Demozione ESCLUSA (il cambio soggetti precede ogni demozione). CONFRONTO FRA LE DUE RACE: la dissoluzione della monografia avviene in entrambe (e' il comportamento LS medio-driven), solo la contiguita' differisce. DEDUZIONE: la LS non puo' accettare una mossa che crea H17 VISIBILE, quindi il buco della race sfortunata nasce (a) sotto esenzione break-level (vicini ancora pinnati, poi la demozione lo rende visibile) oppure (b) da un pass non-LS con oracolo improve-or-noop che tiene i PARI (stessa classe net-zero di E1/C2/H30: un repair che chiude la sua famiglia spostando l'ora di T15 e apre il gap a pari). SPECIFICA LISTENER (prossimo passo, build di domani): loggare le TRANSIZIONI DI VISIBILITA' H17 sulle celle guardate (quando un gap interno diventa visibile: o si forma tra unpinned, o l'esenzione entrambi-pinnati cade), non i mix di materia. Seam candidato: il single-writer apply del decider MT-LS (delicato: TASK-543) o un dump-trigger; da valutare con calma, non stasera.
<!-- SECTION:NOTES:END -->

## CARATTERIZZAZIONE 21/09 pomeriggio (zero-compute, dai cataloghi)

Le celle H17 del residuo integrato-1 consegnato: G13 2027-06-10 ore [10,11] (il caso-campione del task) e G08 2027-04-01 (giorno 8-15 con 6 buchi interni, da kpi-holes). Il costruito DELLA STESSA CORSA non e' archiviato (integrato-1/run non ha construction dump; il solo task615 dump e' di era/seed diversa), quindi il delta erosione stesso-ciclo non e' misurabile sui cataloghi esistenti. ARRIVA GRATIS con l'A/B 636 in volo (capture ON: POST_CONSTRUCTION + consegnato per entrambi i bracci, stesso ciclo): al VERDICT, contare i buchi interni per (gruppo,giorno) nei due stati e confrontare la geografia (G13/G08 in testa). Il listener al confine MT-LS resta il design se il delta conferma che la LS dissolve blocchi composti; la misura dira' anche QUANTO (frequenza per fase, dal phases.json).
