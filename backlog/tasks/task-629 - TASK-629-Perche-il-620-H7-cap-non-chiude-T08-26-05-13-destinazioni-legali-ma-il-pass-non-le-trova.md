---
id: TASK-629
title: >-
  TASK-629 - Perche' il 620 H7-cap non chiude T08 26/05: 13 destinazioni
  legali ma il pass non le trova
status: Done
assignee: []
created_date: '2026-09-19 18:29'
updated_date: '2026-09-20 11:26'
labels:
  - claude-planned
  - h7-cap
  - blind-puzzle
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal blind puzzle sul -1 di integrato-5: la cella H7 (T08 2027-05-26, 7h su cap 6) e' CURABILE (13 destinazioni legali trovate dalla ricerca esaustiva: es. G01 25/05 slot14, T08 5/6->6/6, contiguita' preservata). Il 620 H7-cap arm (ratificato DEFAULT ON) dovrebbe chiuderla ma non lo fa. Ipotesi: (1) il 620 esporta la prima ora del giorno overcap, non l'ultima (l'ora isolata e' slot 13, l'ultima); (2) un gate H31/VRP rifiuta la destinazione; (3) l'oracolo improve-or-noop rigetta per pareggio netto. Il blind ha indicato la direzione giusta (muovere l'ora isolata cross-date).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 #1 Census esaustivo delle destinazioni legali per l'ora H7 (completato: 13 destinazioni per G01 slot13, 3 per G10 slot12)
- [x] #2 #2 Instrumentazione del 620 su questa cella specifica: log per-mossa che mostra quale gate rifiuta (o se la mossa non viene proprio candidata)
- [x] #3 #3 Identificazione della causa: prima-ora vs ultima-ora, gate H31, VRP, oracolo revert, o ricerca del donatore
- [x] #4 #4 Fix portato e banco di conferma: la cella T08 26/05 chiude a 0
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
19/09 sera (Fedora, analisi codice zero-compute): CAUSA TROVATA nel codice. tryH7CapPair riga 603-623: il donatore (findTailOnlyTolerantDonor) viene cercato SOLO quando candSlot == srcDay.firstKey() (prima ora). Per l'ultima ora (il caso T08 slot 13), il ramo else scatta e il SOURCE-YIELD gate rifiuta perche' 6-1=5 < quota 6: il giorno scenderebbe sotto quota senza donatore che ripristini. La cura: estendere findTailOnlyTolerantDonor anche alle esportazioni di ultima ora (la forma del donatore tail-only e' gia' corretta per quel caso: atterra nello slot vacato che e' il tail del run). FIX: nel ramo else, se candSlot == srcDay.lastKey(), cercare comunque il donatore (che ripristinera' il conteggio) invece di scartare per yield.

20/09 (code-review high del fix 603a9fce, 10 finding): F1 REGRESSIONE curita (facac79c): la forma v1 (posizionale firstKey||lastKey -> donatore obbligatorio) cancellava il path donor-less yield-passed per giorni con slack. Forma v2 INVARIANT-BASED: needsBackfill calcolato una volta, donatore cercato solo quando il giorno non sopravvive. F4 (quota size() vs nonStageHours) curito nello stesso commit. FINDING DEFERATI: F2 (H28 belt mancante nel h7cap), F3 (stale srcDay crash), F5 (verifyNoNewHoles assoluto vs monotono), F6 (ALREADY-CURED re-check), F7 (donor search dentro dest loop: performance), F8 (duplicazione h7cap/h7d3), F10 (SOURCE-YIELD scope su gruppi esclusi H32). Nessuno bloccante per il banco di conferma.

20/09 mattina (banchi laptop, delivery-trio-bench + follow-up strumentato c25c9d51): AC#1/#2/#3 CHIUSI, la causa e' la FORMA non la posizione. Banco 629v2-confirm: cella NON chiude, census EXPORT-H7=3 DONOR-TOL=4 BELT=2 NO-CANDIDATE=1; le 2 coppie col donatore trovato muoiono sul belt. Follow-up con debug per-candidato (righe verbatim nel VERDICT): le 2 BELT muoiono su SRC-HOLES con buco NUOVO (CAND pre-pair src[first=0 last=4 missing=-] CONTIGUO, BELT post-pair missing=2,): l'export mid-day @2 dal giorno contiguo era count-safe donor-less (5 ore vs quota 4) e la belt HA RAGIONE di revertirlo. Predizione F5 (buco preesistente, cura holes-monotone) SMENTITA: il difetto e' l'invariante v2 che conta le ORE ma non la FORMA. FIX v3 (dd439b66): needsBackfill = midRunExport || invariante-v2 (qualsiasi export non-bordo richiede il donatore, che atterra sullo slot esatto vacato). B2 secondario: T13 enumerato ma tutte le destinazioni muoiono su EXPORT-ROOM (base pipe senza stanze): la misura reale di T13 richiede base export room-faithful, differita.

PENDING: banco conferma v3 su integrato-5 (laptop in volo): predizione Fedora = DONOR-TOL +2 e cella NON chiude (donatori tail-only tolleranti assenti per quel gruppo) SE @2 e' lo stesso gruppo di @3/@4; chiude a 0 se @2 e' un gruppo diverso con donatore. Il campo group= aggiunto alle righe debug discriminera'. Gates formali (/simplify + code-review high) da pagare DOPO conferma banco, prima di qualunque adozione.

20/09 CHIUSURA (aggiorna il PENDING sopra, ormai superato): v3 CONFERMATA sul banco (b3e933e7: cella T08 -1 -> 0, ramo ALTERNATIVO della predizione: @2 = gruppo G09 diverso da @3/@4, donatore Lingua Italiana disponibile, export Matematica -> 2027-04-13@7 + donatore sullo slot vacato). GATES PAGATE: /simplify 4 agent (2 cure 1952cc4f, scarti motivati S4 falso positivo pre/post-pair, S5 census aggregato voluto, R1 fuori diff) + code-review high 5 reviewer + scorer (A1/C2/E1 a 75 confermati) + suite 1357/1357 composita (1355 verdi a dd439b66 + ripinno CalendarioImportRealSeedStageTest 351d6892, stantio dalla cura 4f9dbc07 del 18/09: la suite main era rossa da quella cura). V4 GATE CURES (d75d837a): E1 firstHourExport sempre col donatore (H31-sensitive: l'oracolo e' NET-hard, una prima-ora donor-less scambia H7 con H31 a pari e SPEDISCE), C2 gamba H28 nel belt (h28ReturnsIn not-grow, forma h28PairSafe), A2 commento. Cure PURE TIGHTENING: la chiusura T08 sopravvive (argomento monotonia nel commit). F5 RFC data dal banco: REFUTATO (il buco era NUOVO). F2 CURATO in v4. Deferiti restanti: F3 (stale srcDay, angolo conservative), F6 (ALREADY-CURED), F7 (hoist donor search, cautela census), F8 (twin h7d3 shape guard), F10 (SOURCE-YIELD scope).

FOLLOW-UP COSTRUITO (20/09, direttiva near-miss->blind puzzle): puzzle T13 2027-01-20 (7h/cap6 su 4 gruppi). Lettura strutturale: finestra 13-29/01 TUTTA a quota esatta per i 4 gruppi, tolleranze 0, nessun surplus -> la famiglia export+donatore e' MORTA li' (il donatore aprirebbe H32 sul proprio giorno). Famiglia candidata che h7cap NON sa esprimere: SWAP 1-per-1 (es. G/L1 D0@13 <-> T20/L1 19/01@13, conteggi invariati, cioe' la grammatica x-swap applicata a target overcap). Cieco zero-contesto in volo sul puzzle; se conferma swap -> prototipo h7-swap.
<!-- SECTION:NOTES:END -->
