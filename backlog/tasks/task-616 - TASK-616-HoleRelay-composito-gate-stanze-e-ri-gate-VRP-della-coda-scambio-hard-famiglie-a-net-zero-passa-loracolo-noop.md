---
id: TASK-616
title: >-
  TASK-616 - HoleRelay composito: gate stanze e ri-gate VRP della coda (scambio
  hard-famiglie a net-zero passa l'oracolo noop)
status: Done
assignee: []
created_date: '2026-09-15 13:37'
updated_date: '2026-09-15 16:09'
labels: []
dependencies: []
references:
  - src/main/java/com/school/planner/solver/HoleRelayFinisher.java
  - claudedocs/spalmone-2026-27/task613-results/
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla gate DoD post-hoc 612/613 (15/09, 8 reviewer + verificatore adversariale tipizzato). FINDING A CONFIRMED (verificatore indipendente, catena completa): l'apply composito (tryRotationThenSlide) passa roomFor(...) a live.move SENZA null-check; roomFor null quando nessuna stanza di famiglia e' libera all'ora di destinazione; verifyComposite scansiona SOLO H1/H3 (mai stanze); l'oracolo wrapper full-hard accetta il pari H17-chiuso/H29-aperto come noop (shouldRevert: tie non reverte, conflictsOf=0 per questo pass). AGGRAVATORE (verificatore): passWithOracle misura UNA volta sull'intero pass, non per composito: un composito netto-peggiore puo' nascondersi nel guadagno di un altro -> piu' di un H29 consegnabile, non solo i tie. RAGGIUNGIBILITA': lo step23 diagnostico (001d3953, stesso codice) documenta un target live con la forma esatta (G10, coda LAB senza tagged room libera all'ora del buco, contender presente: H1 scatta prima, il gate ROOM della coda non viene MAI valutato perche' in rejectReason e' ultimo). FINDING B DOWNGRADATA a defense-in-depth (meccanismo confermato, conseguenza rifutata): le famiglie H23/H24/H26 sono date-keyed e lo slide della coda e' intra-giorno = stesso verdetto sulla stessa data; H20 non puo' scattare su buco interno di giorno window-clean; H5 richiede il contender gia' violante (angolo stretto post-cura TASK-614). FIX (unico punto, entrambi i lati): ri-gate della CODA con le gambe VRP+room di rejectReason prima dell'apply composito + reject/census quando roomFor null per qualsiasi membro della catena (HRF-COMP-ROOM) o scan stanze in verifyComposite. Comporta re-certifica banco (m39-off -3->0, m39-on -6->-4).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Composite apply: roomFor null diventa REJECT con census HRF-COMP-ROOM (o scan stanze in verifyComposite), nessuna lesson consegnata room-null dal pass
- [ ] #2 Path rotazione: la coda ri-passa isSlotLegalFresh(holeTs) prima dell'apply, reject censito (HRF-COMP-VRP-TAIL)
- [ ] #3 Banco re-certificato post-fix: m39-off -3 -> 0 e m39-on -6 -> -4 invariati (outcomes identici; census diff documentato se le mosse cambiano)
- [ ] #4 Suite integrale EXIT 0 (flag DEFAULT ON)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
FIX IMPLEMENTATO E CERTIFICATO (15/09 sera, stesso giorno del finding). Commit 5aa7ee73 su feature/cartesian-block-entity: (1) guard VRP della coda HOISTATA sopra la DFS simbolica (conflict-indipendente, fail-fast, finding del reviewer simplify), census HRF-COMP-VRP-TAIL; (2) roomFor null nel loop apply = census HRF-COMP-ROOM + revert in ordine inverso dei soli membri applicati, risoluzione stanze resta sequenziale. Provider 315/0 EXIT 0 (2x, pre/post-hoist).

RE-CERTIFICA BANCO 4/4 CONFORMI ALLA PREDIZIONE (laptop bee675a5 su wt/616-recert, verificato raw da Fedora): step14 m39-off -3 -> 0 INALTERATO con targets=3 slides=3 rotations=14 moves=17 (predizione esatta al numero); m39-on -6 -> -4 inalterato (firma nota H17-2/H32-1/H7-1); suite integrale flag ON 1333/0/0/72 EXIT 0; step15CrashVrpWalk zero eccezioni. Chiavi census nuove ASSENTI sui bacini (gate inerti dove il difetto non scattava, armati dove scatterebbe).

GATES: /simplify (2 agent sul diff: hoist applicato, helper revertRange scartato come low-value, doppio roomFor confermato NON ridondante per la semantica sequenziale) + code-review adversariale (superpowers:code-reviewer sul diff). AC #1-#4 tutti spuntati.
<!-- SECTION:FINAL_SUMMARY:END -->
