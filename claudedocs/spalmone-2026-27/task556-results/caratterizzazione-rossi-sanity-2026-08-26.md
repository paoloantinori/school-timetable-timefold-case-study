# TASK-557 - Caratterizzazione dei rossi sanity della consegna 0hard (mandato 26/08 item 3)

Consegna analizzata: ciclo qfrv2-on dci-556q2, **0hard/-1.317.237**, sanity NON CONFORME
su 5 gruppi (G07, G08, G09, G10, G11) con 4 giorni sotto-minimo e 2 giorni
con buchi interni. Mandato: tabella per-gruppo, provenienza (bacino B-TIMESLOT del
tentativo singolo), ipotesi di cura SENZA implementarla.

## Tesi in una riga

I rossi NON sono giorni corti: sono **giorni MISTI** (ore ordinarie e ore di Stage nello
stesso giorno per lo stesso gruppo). Il run ne produce 6, il golden 2. La consegna e'
0hard perche' sui giorni dichiarati S-puri (quota aula+lab = 0) NESSUN vincolo prezza
le ore ordinarie: H32 non chiede nulla, M33 e' fuori ambito (quota grezza 0), M20 non
ha target. Sono celle di parcheggio gratis che la prima LS timeslot riempie.

## Tabella per-gruppo (le 6 coppie rosse)

| gruppo | data | tipo rosso | run (ord+stage) | quota foglio | golden stesso giorno | nascita |
|---|---|---|---|---|---|---|
| G09 | 22/02 | sotto-min 1h<2 | 1 ord (slot0) + 7 stage | 8,S | 0 ord + 8 stage | B-TIMESLOT-1 |
| G09 | 20/04 | sotto-min 1h<2 | 1 ord (slot0) + 7 stage | 8,S | 0 ord + 8 stage | B-TIMESLOT-1 |
| G10 | 01/04 | sotto-min 1h<4 | 1 ord (slot0) + 7 stage | 4,S | 4 ord + 4 stage (misto) | B-TIMESLOT-1 |
| G11 | 11/02 | sotto-min 1h<4 | 1 ord (slot0) + 7 stage | 8,S | 0 ord + 8 stage | B-TIMESLOT-1 |
| G07 | 09/06 | buchi [1,2] | 4 ord + 2 stage | 5,S (STALE per il golden) | 5 ord + 0 stage | B-TIMESLOT-1 |
| G08 | 01/04 | buchi [1,2,3] | 2 ord + 4 stage | aula=2, stage=4 (dichiarato) | 2 ord + 4 stage (identico) | COSTRUZIONE |

Nota lettura: lo slot 0 e' l'ora delle 8:00. In tutti e 4 i sotto-min l'ora ordinaria
parcheggiata e' ESATTAMENTE la prima del giorno, con gli stage che coprono 9:00-16:00.

## Censimento globale (il fenomeno, non solo la coda)

| | run | golden |
|---|---|---|
| giorni misti (ord+stage) | **6** | **2** |
| giorni stage-puri | 250 | 270 |
| giorni ord-puri | 1922 | 1958 |

Ore ordinarie su giorni S-dichiarati (aula+lab=0): run 20h (G08 7, G13 5, G07 4,
G09 2, G11 1, G10 1) vs golden **25h** (G08 16, G07 5, G10 4, G13 4).
Il golden PRATICA il mixing dove il foglio lo dichiara (G08: 16h ordinarie su giorni
S del foglio fine FC) e quasi solo li': la differenza run-vs-golden non e' il mixing in
se', e' il mixing NON dichiarato (4 giorni in piu').

## Provenienza per fase (checkpoint del ciclo, stessa costruzione deterministica)

- CYCLE_START: 0 misti.
- POST_CONSTRUCTION: 1 misto (G08 01/04, 2 ord + 3 stage: quello che il foglio dichiara).
- POST_B_TIMESLOT-1 (prima LS timeslot): **tutti e 6 i giorni misti gia' nella forma
  finale** (G09 1+7 ecc.), composizione identica a consegna.
- Da B-TIMESLOT-1 alla consegna: FROZEN (nessuna fase successiva li' tocca: demozioni,
  release, repair chain, stanze).

Quindi: la firma e' proprio del **bacino della prima LS timeslot del tentativo
singolo**, coerente col controllo discriminante della notte (rossi identici con v2 OFF:
QFR non c'entra) e con l'A/B gate (e) (rossi identici con e senza gate). La costruzione
NON fabbrica i giorni misti (a parte il dichiarato G08).

## Perche' 0hard coesiste con i rossi (il buco di metro)

1. Sotto-min: il sanity conta SOLO le ore non-Stage del giorno; un giorno 1 ord + 7
   stage conta "1 ora" contro il minimo golden praticato (2..4). H33 non li vede per
   SCOPE ESPLICITO (verificato nel codice, SchoolScheduleConstraintProvider riga 717:
   giorni con quota aula+lab <= 0 sono "other buckets own it", fuori H33) e H32 nella
   stessa forma non chiede nulla. Il rosso nasce dal confronto col minimo PRATICATO
   dal golden sui giorni ord-puri, non da alcuna violazione: il vincolo e' pago, la
   pratica diverge.
2. Buchi: H17 vede le ore di Stage (sono lezioni), lo script sanity NO. I "buchi [1,2]"
   di G07 09/06 sono le DUE ORE DI STAGE h9-h10 incastrate tra le ordinarie h8 e
   h11; i "[1,2,3]" di G08 01/04 sono le tre ore di stage h9-h11. Sono span di giorni
   misti, non buchi veri: H17 = 0 correttamente.

## Due difetti dello script sanity (misura, trovati characterizzando)

1. **Mappa slot errata**: la griglia reale e' CONTIGUA 8..15 (verificato in DB: 174
   date per ciascuna delle ore 8,9,...,15; NON esiste pausa-pranzo come slot). La
   `slot_index` dello script (`h<13 ? h-8 : h-9`) collassa h12 e h13 entrambe su slot
   4 e non vede mai uno slot 7. Con la griglia contigua la mappa giusta e' `h-8`.
   Non cambia i rossi odierni (i buchi segnalati sono slot occupati da stage), ma
   sfigura qualunque futuro conteggio intorno a h13.
2. **Stage escluso dal conteggio buchi**: le ore di Stage andrebbero contate come
   OCCUPANTI nello span (o i giorni misti riportati come segnale dedicato), altrimenti
   ogni giorno misto appare bucato.

## Confutazione dell'ipotesi del mandato

"L'H33 floor nel pricer di costruzione" NON cura questi rossi: (a) i 6 giorni non sono
corti (7-8 ore totali), (b) nascono nella LS, non nella costruzione, (c) H33 e' gia'
soddisfatto. La famiglia giorni-corti VERA (G07/G09 dei cicli precedenti, giorni
ord-puri sotto il minimo) e' questione referente pendente (giorni corti voluti o subiti);
questa firma e' un fenomeno diverso con lo stesso colore nel report.

## Cure candidate (registrate, NON implementate)

1. **METRO (la piu' promettente): dare prezzo alle ordinarie sui giorni stage**. Due
   forme equivalenti: estendere l'ambito di M33 anche ai giorni a quota grezza 0
   (surplus = ogni ora ordinaria oltre 0) oppure una medium dedicata stage-day-purity
   (~100/ora). Golden-first: il golden stesso tiene 25h ordinarie su giorni S, quindi
   TIER MEDIUM con residuo calibrato ~25h, MAI hard. Oggi il parcheggio costa 0 ed e'
   anzi premiato da H31 (l'ora ordinaria a slot 0 fa partire il giorno alle 8).
2. **SEARCH-side costruzione**: distribuire le DATE degli stub stage seguendo la
   pratica del golden (date effettivamente praticate, non solo "stageHours > 0":
   il caso G07 09/06 e' una data fuori finestra pratica che il foglio dichiara S
   per sbando). Con guardia TASK-517: non piu' rigido del golden.
3. **FIX SCRIPT SANITY** (da fare comunque, e' misura non modello): mappa slot h-8,
   stage occupante nel conteggio buchi, giorni misti come riga dedicata.
4. La domanda referente Q-G12/giorni resta la chiave di volta: se la scuola conferma i
   giorni stage PURI come pratica, la cura (1) sale di priorita' e il residuo ~25h del
   golden diventa il valore calibrato.
