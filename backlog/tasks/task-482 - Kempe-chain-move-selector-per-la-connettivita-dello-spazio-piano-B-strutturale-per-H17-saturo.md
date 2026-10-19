---
id: TASK-482
title: >-
  Kempe-chain move selector per la connettivita' dello spazio (piano B
  strutturale per H17 saturo)
status: Done
assignee: []
created_date: '2026-08-02 16:10'
updated_date: '2026-08-04 04:34'
labels:
  - claude-planned
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla ricerca 02/08 (claudedocs/research_multistart-vs-alternative_2026-08-02.md, finding 3): Lewis & Thompson dimostrano che aumentare la CONNETTIVITA' dello spazio delle soluzioni con operatori di vicinato specializzati migliora sistematicamente la qualita' raggiungibile nel course timetabling, e l'operatore canonico del dominio e' la Kempe chain (scambio coordinato della composizione tra due slot).

E' la risposta strutturale al nostro fallimento H17: la cascata locale depth<=3 non chiude il buco perche' lo slot e' saturo (shell game, FORK-shell-game-depth3-2026-08-02.md); una Kempe chain riorganizza wholesale la composizione dello slot, e a differenza del multi-start non paga il fattore Nx sul wall-clock.

Perimetro: move selector custom nella LS B-TIMESLOT (Timefold community: custom MoveSelector/MoveIteratorFactory), coppie di slot candidate guidate dai gap H17, catena costruita sui conflitti docente/gruppo. Rischio dichiarato: tocca un percorso adottato (la LS di produzione), quindi flag/config-gated e A/B con guardia sulle regressioni. Piano B rispetto al multi-start TASK-480: si apre se il gate di indipendenza del multi-start fallisce, o se il pattern H17 si ripete rendendo il costo Nx inaccettabile.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Design del move selector Kempe-chain per il nostro modello (coppie slot, costruzione catena, criterio di accettazione)
- [ ] #2 Prototipo config-gated + test localizzati + DoD
- [ ] #3 A/B: chiusura dei buchi H17 su bad seed senza regressioni su hard/full-day/ricorrenza
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Kempe-chain H17 discriminator: 0/6 + 0/9 sustainable on two seeds, CHIUSO NO (bacino saturo non connettivita, multi-start resta la via). Commits 77a63b14 + b36d7626.
<!-- SECTION:FINAL_SUMMARY:END -->

## Riesame 20/08 (post H31/H32/H33 + QFR, approvato da maintainer)

- Ipotesi: il modello radicalmente cambiato (H31/H32/H33 hard, QFR, floor) ha cambiato i bacini: la Kempe chain potrebbe chiudere sostenibilmente i buchi H17 dove ad agosto chiudeva 0/15.
- Risultato misurato: bacino cattivo attuale = task532-results/h32b-run1 (-104hard, H17 -99 su 98 match). Cluster dominante G10 buco @10 ogni mar/ven da settembre a febbraio (38 date). Discriminator su 10 date campione: 0/10 sostenibili, 0/10 shell-game, 10/10 no-improve (~600 donor candidati/giorno). Le chain chiudono il buco ma rompono le quote H32 (delta tipico H32 -1/-2, H2 -1000). Log /tmp/kempe-h32b.log (verdetto riga 2533).
- Tenere/buttare: BUTTARE, TASK-482 resta NO anche sul modello attuale, nessun reopen. Il NO di agosto era "slot saturo non connettivita'"; il NO di oggi e' "le quote hard H32 vincolano ogni ricollocazione": causa diversa, stesso esito. Probe permanent env-gated in KempeChainH17DiscriminatorTest.kempeChainH17CloseH32bRun1.

AGGIORNAMENTO 26/08 15:2x (TASK-560 banco Kempe): la clausola pre-registrata del banco e' scattata a FAVORE del piano B: le 10 celle H34 residue della consegna dci-h34on NON sono chiudibili da mosse composte same-group (KempeCellNeighborhoodProvider Move Streams, 0/10, ogni transazione hard-peggiorativa o gate-e-bloccata): la via dichiarata per quella famiglia di residui e' il MULTI-START (selezione bacino) o il KEMPE-CHAIN VERO (questo task: catene cross-teacher multi-giorno). La tecnologia Move Streams (preview NEIGHBORHOODS) e' confermata idonea: join condizionali, groupBy, pick/asMove funzionano sul nostro dominio (provider committed come riferimento: solver/KempeCellNeighborhoodProvider.java).
