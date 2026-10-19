# RE-CERT TASK-617 consolidamento (wt/617-consolidation @ cc383bde): 6/6 al numero, BEHAVIOR-PRESERVING provato

Predizione (mandato): TUTTO identico al byte/al numero. Misurato (heap 12g):

| banco | atteso | misurato | esito |
|---|---|---|---|
| step14 m39-off | -11 -> -3 -> 0 (3/3/2/7) | **identico** (targets=3 slides=3 rotations=2 moves=7) | VERDE |
| step14 m39-on | -13 -> -6 -> -2 (4/3/0/8) | **identico** (targets=4 slides=3 rotations=0 moves=8) | VERDE |
| step40 (620, reab-cura export) | -1 -> 0, 1 pair/2 moves | **identico** (moves=4 h17+h7cap) | VERDE |
| step48 (x-swap + depth-3, integrato-1) | -4 -> -1, moves=6 | **identico** (targets=2 slides=2 moves=6, -4 -> -1) | VERDE |
| step46 (x-swap solo) | -4 -> -2 | -4 -> **-1** | VERDE con spiegazione (vedi sotto) |
| suite integrale | 1353/0/0/92 EXIT 0 | **1353/0/0/92 EXIT 0 BUILD SUCCESS** | VERDE |

## step46 non e' una regressione: CONTROL su base pulita

step46 fallisce l'assert -2 anche sul base 0ceca537 NON-rifattorizzato (worktree
di controllo pulito, stesso export, stesso esito -4 -> -1 con H7D3-applied nel
log): NON e' il refactor. Causa: step46 arma solo x-swap e NON disarma depth-3,
che su 0ceca537 e' DEFAULT ON (ratificato): il braccio depth-3 scatta insieme e
chiude anche l'H7 (-1 invece di -2). Il test e' STANTIO rispetto al flip di
default, non il comportamento. Le due mosse x-swap sono IDENTICHE alle celle
(G13 06-10 @2 e @3, donor 2027-01-12 e 2026-11-24).

## Le sei estrazioni, stato onesto

1. COMPOSITE RECORDER: FATTO (10 rec.apply / 13 rec.revert / zero revertPair /
   zero raw origTs fuori classe). Il recorder e' anche la cura strutturale del
   bug di igiene del banco 622 (record-dopo-check-then-null-out): la forma
   apply-dopo-il-check rende quella classe di bug non-esprimibile.
2. ENUMERAZIONE OVERCAP UNICA: FATTO (enumerateOvercaps, doppia scansione
   teacher-hours eliminata, label census entrambe preservate).
3. DONOR FINDER UNICO: FATTO (findTailOnlyTolerantDonor(Timeslot), scala di
   gate unica, due caller).
4. QUOTA-MAP + sameGroupDaysByDistance: PARZIALE DICHIARATO: la mappa
   quotaByName e' in Live (lookup O(1) per il finder), sameGroupDaysByDistance
   convertisce i DUE siti contigui; i DUE siti dests (Object[] con slot-candidates
   nel loop) RESTANO come erano: forzarli cambierebbe la forma del loop per zero
   guadagno di comportamento.
5. h28PairSafe ovunque: NON FATTO, DICHIARATO: il 4-clause bespoke del depth-3
   resta (annotato in linea): le 2 clausole extra coprono tuple che la forma a
   2 clausole non vede; forzarlo WEAKENIREBBE il gate.
6. h28ReturnsIn filter-first: GIA' ERA (mappa keyed + TreeMap values()): tolto
   solo il re-check ridondante del nome gruppo, commento in linea.

## Effetti sul 622

Il recorder condiviso risolve STRUTTURALMENTE la classe del bug di igiene del
banco 622 (il banco scriveva origTs.put DOPO il room-check e poi null-out
l'incoming senza revert: con apply-dopo-check quella forma non si puo' scrivere).
Il 622-duale e' SBOCCATO sul piano strumentale.

## VERDETTO

BEHAVIOR-PRESERVING PROVATO: tutti i banchi al numero, l'unica divergenza
(step46) riprodotta identica sul base non-rifattorizzato (test stantio, non
comportamento). Suite verde. 1787 -> 1757 righe con -102/+89 nette e cinque
copie di recorder eliminate. Il settimo braccio ora ha la casa in ordine.
