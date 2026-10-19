---
id: TASK-612
title: >-
  TASK-612 - EDF relay-fill + H32 targets: meccanizzare la famiglia IMPORT-RELAY
  del residuo H34/H32 (strada verso 0, figlio TASK-609)
status: Done
assignee: []
created_date: '2026-09-14 06:21'
updated_date: '2026-09-15 14:03'
labels:
  - claude-planned
  - composite-moves
  - finisher
  - prototype
milestone: m-14
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Figlio di TASK-609 (strada verso 0-hard, mandato maintainer 14/09 'vediamo di arrivare a questo 0'). CONTESTO: il banco step6 dimostra -11 -> 0 con le 40 mosse cieche; il CompositeSwapFinisher RATIFICATO copre la famiglia boxed/H0 (-11 -> -8 sul replay m39-off); il residuo -8 = H34 x3 (G06 06-10, G09 06-03, G12 04-16), H32 x2 (G13 12-14, G13 01-21), H17 x3 (teacher-locked, TASK-613). Il census delle 40 mosse vincenti (moves-5.txt) dice che le celle H34/H32 si chiudono con UNA famiglia dominante: l'IMPORT-RELAY (estrazione dalla CODA di un giorno A-QUOTA + ripristino ESATTO della cella svuotata con materiale da un giorno in VERO surplus; catene fino a profondita' 3: import + 2 riporti, es. difetto G12 04-16). La diagnosi AC#2 di TASK-609 ha chiuso il perche' EDF non lo fa: GATE-E (donor solo sopra max(quota, floor)) respinge l'88% dei candidati = modello ESTRATTIVO PURO, mentre le danze cieche sono ESTRATTIVE+RIPONENTI. EDF oggi non vede nemmeno le celle H32 parziali (target solo hours==0 H34 + H33 opt-in).

OBIETTIVO: estendere EmptyDayFinisher con due famiglie opt-in DEFAULT OFF (precedente v0.2-swap/H33, stessa casa):
(1) RELAY move family (flag planner.construction.edf-relay.enabled): quando extractionFor fallisce, provare l'import-relay: donore ANY stesso-gruppo (coda-first, scansionando descending), lezione L con landing legale (H1/ROOM/VRP) al target; dopo la mossa la cella di L viene RIEMPITA ESATTAMENTE da M proveniente da un giorno che passa GATE-E (surplus vero, ledger vivo), OPPURE ricorsione profondita' <=2 con M intermedio a-quota la cui cella viene riempita a sua volta (il terminale DEVE passare GATE-E). Atomicita' per tentativo con rollbackFrom; undo gia' gestito dal Pass di EDF.
(2) H32 target family (flag planner.construction.edf-h32.enabled): giorni NON vuoti sotto quota (need = quota - tolleranza(G09 1h) - hours > 0), scope identico a H32 (isQuotaFillExcluded per 1/G08 e G14).
(3) Wiring wrapper: quando uno dei due flag e' armato, EDF gira ANCHE sullo STATO CONSEGNATO al punto pre-save del wrapper multi-start (dopo CompositeSwapFinisher), improve-or-noop full-hard — stessa logica del riposizionamento TASK-609: il residuo che sopravvive all'EDF mid-ciclo nasce a valle (rifinitura di consegna).
(4) Banco step11 sul replay m39-off: load -> CSF (-11 -> -8) -> EDF esteso (flag ON) -> misura. BERSAGLIO provato dal banco step6: -8 -> -3 (H34 x3 + H32 x2 chiuse; restano H17 x3). Poi pin del numero esatto.
(5) Cross-basin m39-on (-13 -> -10 dopo CSF) con assert di non-regressione + no-op su stato pulito.

METODO (direttive): banco prima di tutto, ciclo completo SOLO come gate di adozione finale. Nessun default flip senza APPROVED di maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [#1] Estensione EDF dietro due flag opt-in DEFAULT OFF (edf-relay, edf-h32), forma Boolean.parseBoolean come V2_SWAP/H33, con reject census chiavi RELAY-*
- [ ] #2 [#2] Banco step11 replay m39-off: load -> CSF (-11->-8) -> EDF esteso -> misura e PIN del risultato (bersaglio -3: H34 x3 + H32 x2 chiuse, restano H17 x3)
- [ ] #3 [#3] Cross-basin m39-on post-CSF (-10): EDF esteso non peggiora (assert non-regressione) e chiude cio' che chiude
- [ ] #4 [#4] No-op su stato pulito (stato riparato step6/9): EDF esteso con flag ON non muove nulla
- [ ] #5 [#5] Wiring wrapper pre-save dopo CSF, improve-or-noop full-hard, banner+log distinguibile dal confine mid-ciclo
- [ ] #6 [#6] DoD completa: /simplify, code-review high, test.sh EXIT 0; nessun default flip (flag restano opt-in fino ad APPROVED di maintainer)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTAZIONE v0.1 COMPLETATA E MISURATA (14/09 mattina, banco 13/13 verde): EmptyDayFinisher esteso con (a) RELAY_FLAG planner.construction.edf-relay.enabled opt-in DEFAULT OFF: relayFor (donor stesso-gruppo scansionato coda-first, landing H1/ROOM/VRP) + refillAt ricorsivo profondita'<=2 con visited-set anti-ciclo, terminale GATE-E sul ledger vivo; (b) H32_FLAG opt-in: target parziali sotto-quota con tolleranza G09 1h; (c) wiring ScheduleService runEdfExtensionsOnDelivery al punto pre-save post-CompositeSwapFinisher, improve-or-noop full-hard, log [delivery]; banner relay=%b h32=%b.

MISURE (banco step11/12/13, export m39-off/m39-on): step11 m39-off: -11 -> -8 (CSF) -> -3 (EDF ext, +5, PINNED): 5/5 celle chiuse (H34 x3 + H32 x2), 39 mosse, restano solo H17 x3 teacher-locked (famiglia TASK-613). step12 m39-on cross-basin: -13 -> -10 (CSF) -> -6 (+4): 4 celle chiuse, non-regressione OK. step13 stato pulito (moves-5 + room pass): moves=0, hard -1 -> -1 byte-identical, no-op puro. Medium su m39-off MIGLIORA (-798k -> -774k).

BUG PRESO DAL BANCO (il mestiere del banco): prima forma del refill terminale prendeva QUALSIASI slot del giorno surplus: GATE-E e' hour-based, cieco alla forma; partenze interne aprivano H17 e partenze prima-ora aprivano H31 (-8 -> -14 con 7 buchi e 4 late-start nuovi). FIX census-faithful: il terminale puo' prendere SOLO l'ULTIMO slot occupato del giorno ('tail extractions', la forma esatta delle 40 mosse: tutti i terminali a ora 13). Dopo il fix: -3 pulito.

BUG PRESO DAL BANCO #2: step13 senza room pass post-mosse leggeva -15001 di H2 fantasma (le rilocazioni cieche non toccano le stanze): estratti applyBlindMoves/applyBlindRoomPass condivisi con step9 (dedup).

CENSUS step11: {RELAY-ACCEPTED=12, RELAY-REFILL-TERMINAL=12, RELAY-REFILL-CHAIN=11, RELAY-NO-REFILL=13, RELAY-GATE-E=141644, H1=4859, ROOM=4568, VRP=1297}: le catene profonde (11 refill-chain su 12 import) sono la NORMA, non l'eccezione: la profondita' 3 del census e' necessaria.

GATE /simplify (4 reviewer, tutti consumati): riuso (relayMovable ora condivisa da extractionFor; accessor quotaFillTolerance nel provider accanto a isQuotaFillExcluded, usato da H32 constraint + EDF; shell wrapper unificata in ScheduleService.edfPassWithOracle; loadBasin condiviso da load/step8/step12; helper runCsf/runEdfExt nei nuovi step), efficienza (HOIST dei verdetto GATE-E: la lista yielding e' calcolata UNA volta per relayFor invece che per candidato: il census misurava ~141k RELAY-GATE-E ricalcoli; argomento di sicurezza verificato avversarialmente da 2 reviewer: il delta ledger tocca solo target+donore, entrambi esclusi, failed candidate fanno rollback pieno, la ricorsione depth-1 ricompputa con null), semplificazione (descendingKeySet, import nudi, hoist relayOn), altitude (extensionsArmed() punto unico per il gate delivery; javadoc classe aggiornata alle famiglie opt-in). 6 finding minori di accuratezza doc/test corretti (banner/javadoc extensionsArmed, scope commento call-site, commento hoist: l'esclusione del donore sta nel visited-set, Result/Cell javadoc + H32, findBoxed fuori dal loop, CLAUDE.md '10 step'->13). GATE code-review high (4 reviewer): bug-scan CLEAN su tutti e 5 i vettori (stato failed-relay, staleness hoist, CME, lastKey su giorno vuoto, helper bench); storia git CLEAN su tutti gli assi (semantica TASK-560, m22MinDays, TASK-578, invarianti step8/9, wrapper). DEBITI DICHIARATI: (1) 3 copie inline della tolleranza 'G09' fuori dal diff (GoldenSacrificeBudget:111, SurplusHarvest:196, FinalClosingCampaign:423) da migrare all'accessor; (2) snapshot back-to-back CSF/EDF al delivery (una full evaluation risparmiabile infilando l'after di CSF come before di EDF; pattern di famiglia, differito); (3) scheletro scansione giorni-stesso-gruppo triplicato (extraction/relay/compound, unificazione non meccanica); (4) interazione mid-cycle-armed (flag armati = EDF gira 2 volte per solve) coperta dall'A/B live del gate, non dal banco.

AMBIENTE (14/09, hard reboot 10:51 + direttiva heap): il box Fedora ha subito 3 incidenti memoria + 1 hard reboot da test Maven a heap default (16g). Direttiva attiva: OGNI test Maven con -Dsurefire.heap=3g. IL BANCO REPLAY NON ENTRA in 3g ne' 6g (il parse dell'export JSON da 111MB satura il heap: OOM in SnapshotFixture.parseExport): per direttiva stessa NON si scala oltre sul box affollato; la verifica banco del codice post-hoist va in FINESTRA DEDICATA = il laptop (step 1 del mandato), poi il gate A/B (step 2). Gli export m39-off/m39-on sono stati ripristinati in /tmp (m39-on anche come copia locale nel repo tree, NON committata) e verificati gzip-OK.

STATO PRE-COMMIT: i pin -3/-6/no-op sono stati verificati VERDI 2x stamattina ma PRIMA dell'hoist GATE-E; l'hoist e' verificato da code-review avversariale (2 reviewer indipendenti) ma il banco post-hoist deve ancora girare (step 1 mandato laptop). Il commit dichiara questa finestra aperta.

FINESTRA CHIUSA (14/09, laptop, controverifica raw da Fedora): banco post-hoist 13/13 VERDE. Corsa 1 (senza property alt): 11 step verdi, step8/12 in errore per il default /tmp/m39on assente sul laptop (atteso, documentato nel mandato); corsa 2 con -Dplanner.test.replay.alt=<repo path>: 13/13, 0 errori, pin esatti: step11 -11 -> -8 -> -3 (moves=39 cellsClosed=5), step12 -13 -> -10 -> -6 (moves=36 cellsClosed=4), step8 -13 -> -10, step13 no-op byte-identical, step7 CSF -11 -> -8. L'hoist dei verdetti GATE-E e' quindi verificato sul banco: l'evidenza di TASK-612 e' completa pre-A/B. Il gate A/B (STEP 2) era in corso a ~1h35m con i flag verificati sulla riga del JVM braccio B (-Xmx2g + edf-relay + edf-h32, catturati da ps).

PRE-AUTORIZZAZIONE DI ADOZIONE (maintainer, 14/09 pomeriggio: 'ti dico gia' ora, che se conferma voglio adottare'). CRITERI DI CONFERMA PRE-REGISTRATI (scritti PRIMA del VERDICT, perche' l'adozione non diventi interpretazione): il gate A/B conferma SE (a) hard braccio B (flags ON) <= hard braccio A con stessa meta' di bacino (nessuna regressione di famiglia oltre la variabilita' nota), (b) la riga 'EmptyDayFinisher [delivery] (TASK-612 relay=true h32=true)' appare nel log B nel punto previsto (post CSF, pre-save), (c) sanity gate per gruppo non peggiorata, (d) KPI in banda. Il caso counter=0 (bacino senza residuo fill-family) NON blocca l'adozione: l'efficacia e' provata dal banco (due bacini +5/+4, no-op provato), il ciclo misura la non-regressione e il fuoco-effettivo quando il fenomeno c'e' (stessa logica della ratifica TASK-609: assicurazione a costo zero, evento raro dipendente dal bacino). SE CONFERMA: flip dei DUE flag alla forma opt-out standard ratificata (null/absent -> ON, solo 'false' disarma, Boolean.parseBoolean come CSF/EDF base), banner 'RATIFIED 14/09', voce CLAUDE.md completa, commit con evidenza A/B. Il flag TASK-613 RESTA opt-in (nessuna evidenza banco ancora: step14 in coda).

VERDICT A/B (laptop c7cc6700, controverificato raw da Fedora): NON CONFERMATIVO, NESSUN FLIP. (1) Braccio A (controllo, flag off) MORTO: IllegalStateException VRP-assert alla delivery refinement (Lavorazioni meccaniche fuori value range, 2027-06-10T11:00), firma IDENTICA a TASK-565 n=1 del 28/08 ma su confine NON catturato (runRoomSolveStage, fuori da ogni catch): thread death, NESSUNA consegna. Il crash e' sul path DEFAULT, indipendente dai flag in gate. (2) Braccio B (relay+h32 ON, guardie 3/3 verdi) CONSEGNATO [-15/0] in ~72min: riga [delivery] presente al punto previsto ma 0 moves / 0 cells su 3 celle fill-family residue (H32 x2 + H34 x1): sul bacino live i gate respingono tutto (le H17 x8 dominanti sono teacher-locked TASK-613), no-op pulito, NESSUNA regressione ma NESSUN guadagno end-to-end. (3) Criterio pre-registrato (a) non valutabile (A non ha consegnato): la pre-autorizzazione di maintainer ('se conferma voglio adottare') NON scatta. STATO: i due flag RESTANO opt-in DEFAULT OFF. DECISIONE SUSSESSIVA per maintainer: (i) lasciare opt-in finche' un A/B appaiabile (dopo la cura del crash TASK-565) non misura il controvalore, oppure (ii) adottare come assicurazione a costo zero sulla sola evidenza banco+no-op (CSF-precedent),sapendo che il guadagno live e' non-dimostrato su questo bacino. RACCOMANDAZIONE Fedora: (i) PRIMA la cura del crash (ammazza-consegne, n=2, default path) e l'A/B appaiabile DOPO. KPI B per memoria: full-day 94,1, ricorrenza unpinned 71,3, sanity 3 rossi, hard mem/db concordi.

DIAGNOSI D3 (laptop 588b75e2, step16 su export arm-b, doc task614-results/DIAG-tre-domande): il no-op live NON e' mancanza di candidati: il relay ha valutato ~2,4M verdetti GATE-E (hoist), 63k reject H1, 60k ROOM, 227 NO-REFILL, e ha FORMATO 3 relay completi (3 ACCEPTED, 3 refill terminal, 3 chain)... che l'oracolo improve-or-noop ha REVERTITO TUTTI: chiudere deficit-1 qui non migliora l'hard netto (l'import compra altrove, H7 -2 residuo suggerisce il cap docente). Celle fill live: G12 09-15 e 04-16 (H32 deficit-1) + G09 06-03 (H34, deficit-4). LETTURA: il pass e' corretto e sicuro (l'oracolo tiene, 0 danno); il suo valore live dipende dalla geometria del bacino (le 3 celle live non hanno la geometria net-positive dei bacini del banco). Nessuna modifica al codice: l'adozione resta in attesa dell'A/B appaiabile post-TASK-614, come deciso da maintainer.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RATIFICATO DEFAULT ON (maintainer, 14/09 sera): flag planner.construction.edf-relay.enabled + planner.construction.edf-h32.enabled, forma opt-out standard, banner in refreshSolverStatics. EVIDENZA: banco 13/13 post-hoist (m39-off -11 -> -8 CSF -> -3 EDF-relay PINNED 5 celle; m39-on -13 -> -10 -> -6) + gate A/B ab-gate2: A2 -18 vs B2 -8, riga [delivery] ATTIVA, criteri a-d TUTTI VERDI, crash-control VERDE. Suite 1327/0 poi 1332/0. Commit 43520db3 + nits 4a240948.

GATE DOD POST-HOC (15/09, sessione post-compattazione): /simplify (4 agent) + code-review (review-local 4 agent + verificatore adversariale code-reviewer) sul range 1c4ce938..1bf70bc1. SUL 612: NESSUN finding comportamentale (vie critiche relay verificate pulite due volte: rollbackFrom speculare con hoursByKey, ricorsione depth-2 con visited, tail-only terminal, GATE-E su ledger vivo, nessuna chiave mappa mutata in iterazione live). Struttura/perf differiti a TASK-617. Comment fixes applicati (7c7f4fa9). Provider 315/0 EXIT 0 heap 3g.
<!-- SECTION:FINAL_SUMMARY:END -->
