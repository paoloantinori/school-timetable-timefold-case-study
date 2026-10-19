# FASE 4 - TASK-450 AnchorRepair cross-group swap (direzione b), VERDICT (2026-07-28 notte)

**ESITO: ANALISI + DEFER (mandate: "se lo scambio non basta, NON alzare pesi: documenta e
passa oltre").** Lo swap cross-group sull'ancora slot-0 e' SHELL-GAME-PRONE: il root cause e'
capacita' docente satura al Friday slot-0, non un problema risolvibile con uno swap
deterministico. Nessun codice aggiunto (la codebase resta pulita per la Fase 6). laptop MISURA/
ANALIZZA, NON implementa un fix creativo.

## Target (correzione importante vs il task)

Il task diceva "G11 Friday late-start days -> 0". Il dump gate-l5-m36 + day-start-anatomy.py
mostrano **15** G11 Friday in ritardo (NON 3): ogni venerdi' 2026-09-18 .. 2027-01-08, tutti
che iniziano a slot 1 (ore 9) vs golden slot 0 (ore 8). Il delta (G11, venerdi') = 15-0 = 15,
l'UNICA cella con scarto >= 3. Quindi il target Fase 4 e' 15 venerdi', non 3.

## Root cause (verificato su 2 venerdi', 2026-09-18 e 2026-10-02)

Su OGNI venerdi' G11, tutti e 4 i suoi docenti sono saturi allo slot-0 da altri gruppi:
- T23 (G11 Educazione Civica @slot1) -> slot0 occupato da G08 (Storia)
- T21 (G11 Lingua Inglese @slot1) -> slot0 occupato da G06 (Lingua Inglese)
- T06 (G11 Informatica @slot2) -> slot0 occupato da G09 (Informatica)
- T12 (G11 Disegno Edile @slot3-4) -> slot0 occupato da G07 (Disegno Edile)

AnchorRepair import (direzione b attuale) fallisce: HoleRepair.findImport rigetta ogni donore
same-group perche' il suo docente confligge (H1) al Friday slot-0. Nessun import legale. E'
saturazione H1 cross-group allo slot-0 del venerdi', esattamente il caso che il javadoc di
AnchorRepair anticipa.

## Perche' lo swap cross-group e' shell-game-prone (non basta)

Uno swap scambierebbe il lesson slot-1 di G11 (es. T23/Educazione Civica) con il lesson
slot-0 del gruppo bloccante dello STESSO docente (es. G08/T23/Storia). Risultato: G11
parte slot-0 (ancorato); ma G08 perde il suo lesson slot-0 e, per H3 (una lezione/gruppo/slot),
non puo' averne un secondo -> G08 parte slot-1 -> LATE. Lo swap sposta semplicemente il ritardo
da G11 al donore = SHELL GAME puro, proprio cio' che il guard `keepsDayAnchored` (HoleRepair
365, SurplusRebalance 475-495) esiste per vietare.

L'unico swap shell-game-free richiederebbe che la finestra-calendario del donore parta da slot-1
(cosi' non e' ancorato a slot-0), OPPURE una rotazione/cascataia multi-step. Per G11 Friday i
gruppi bloccanti hanno lesson slot-0 (e' per quello che bloccano), quindi le loro finestre
includono slot-0: lo swap diretto e' shell-game per tutti. Non c'e' un commitSwap primitiva
oggi (la piu' vicina e' HoleRepair.commitCrossDayMove, mono-lesson); aggiungerne una e'
sostanziale e rischiosa, e NON risolverebbe (la guard no-shell-game rigetterebbe ogni swap).

## Root cause reale: capacita' docente al Friday slot-0

Il venerdi' slot-0 ha piu' lezioni che lo vogono di quanta capacita' ci sia (docenti condivisi
tra gruppi). E' un problema strutturale di capacita'. Per chiudere davvero i 15 venerdi' G11
bisognerebbe spostare una lezione FUORI dal Friday slot-0 (altro giorno/slot), liberando
capacita'. Quello e' uno spostamento di TIMESLOT, scope della Local Search (move selector), NON
un pass deterministico Phase-3 di AnchorRepair. Alzare i pesi (M18) non e' autorizzato
(mandate: "NON alzare pesi di testa tua").

## L2 delta (predetto, defer)

Con un guard no-shell-game stretto, lo swap cross-group chiuderebbe ~0 dei 15 venerdi' G11
(tutti shell-game-blocked). Il delta day-start (G11, venerdi') resta 15. Misurare
l'esatto 0 richiederebbe implementare la commitSwap + la guard + il checkpoint L2; non fatto
(per mandate "documenta e passa oltre" + per non rischiarizzare la Fase 6 con una primitiva
nuova su una metrica che il root cause dice gia' non chiudibile deterministicamente).

## Raccomandazione (per maintainer)

1. Indirizzare i 15 venerdi' G11 via MOVE SELECTOR LS (TASK-292 cartesianProductMoveSelector,
   gia' in backlog), che puo' spostare una lezione fuori dal Friday slot-0 liberando capacita',
   NON via swap deterministico Phase-3.
2. OPPURE accettare il pattern: e' 1 gruppo (G11), day-start globale 99.2% (sopra soglia
   >=99), il venerdi' e' l'unico giorno syst. in ritardo. Costa -15 M18 medium, onesto.
3. NON inseguire lo swap deterministico: e' shell-game-prone per costruzione.

## Confine di mandato

laptop ANALIZZA, NON implementa fix creativi (mandate: "se lo scambio non basta, NON alzare pesi
di testa tua: documenta e passa oltre"). La codebase resta pulita (nessun codice Fase 4):
M37 + H28 dalla Fase 1-2, l'esperimento tag e' in-harness (Fase 3). Fase 6 girera' su questa
base NOTANDO che day-start (G11 venerdi') non e' fissato (swap defer).
