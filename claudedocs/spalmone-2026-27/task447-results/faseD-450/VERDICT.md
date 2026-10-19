# FASE D (TASK-450) - Donatore coda-di-giornata per AnchorRepair: direzione NON REGGE

Data: 2026-07-29. Verdetto checkpoint-based (mandato: implementa/misura, se non
regge documenta e fermati, niente fix creativi). Esito: **la direzione (d) non
regge**, documentato e fermato. NESSUN codice di repair modificato.

## Direzione testata (maintainer 29/07 mattino, FASE D)

Estendere l'import cross-date di AnchorRepair per accettare come donatore una
lezione che sia CODA della sua giornata (toglierla non apre un buco H17) e il cui
docente sia libero nello slot bersaglio, con la guard M18 esistente sulla data di
origine (no shell-game). Bersaglio: G11 venerdi 16 ritardi -> 0, nessuna nuova
cella con scarto >= 3.

## Metodo: harness deterministico sul repair

`SpikeApplicabilityAnatomyTest` (SPIKE 1 "AnchorRepair feasibility") sullo snapshot
gate-finale (`claudedocs/spalmone-2026-27/task446-results/gate-finale/lessons.txt`):
per ogni giorno M18-in-ritardo prova a chiudere il leading hole con un import
HoleRepair-style attraverso il gate chain identico ad AnchorRepair
(conflict-free, passesPlacementGates, isDepartureSafe, M18 guard, VRP) e conteggia
quale gate scarta ogni candidato. M18 guard OFF in questo spike = caso PIU'
permissivo della produzione (production e' M18 ON, quindi piu' restrittiva).

Stato confermato (`kpi-daystart-anatomy.txt`): G11 venerdi 16 ritardi (run
inizia alle 9, golden alle 8), 15 consecutivi 2026-09-18..2027-01-08 + 1 stray
2027-03-19.

## Trovato: UNCLOSABLE, blocco = teacher-busy-at-slot + VRP (NON departure)

Per ognuno dei 15 venerdi consecutivi: `UNCLOSABLE [stop: teacher-busy-at-slot]`.
Breakdown del pool donatori di G11 (666 candidati, same-group, cross-date):
`{VRP=111, departure-unsafe=159, teacher-busy-at-slot=396}`.

- 396 candidati: il docente e' GIÀ occupato allo slot-0 del venerdi (H1 conflict).
- 159 candidati: docente libero, ma departure-unsafe (NON coda: toglierli aprirebbe un buco H17).
- 111 candidati: docente libero E coda (departure-safe), MA VRP-esclusi (lo slot-0
  del venerdi non e' nel loro available set).

Riconciliazione con la correzione Fedora (T05/T04/T03/T24 liberi
allo slot-0): i 4 docenti liberi esistono, ma le loro lezioni di G11 sono poche e
ricadono nei 270 non-busy -> tutte VRP-bloccate (111) o non-coda (159). La massa
delle lezioni di G11 (396) e' di docenti occupati (T23/T21/ecc., che a
slot-0 venerdi insegnano G02). Non e' saturazione della capacita' docente totale,
e' allocazione: i docenti liberi non sono assegnati a G11 allo slot-0 del venerdi.

## Perche' la direzione (d) non regge

La direzione "donatore coda + docente libero + guard M18" **descrive il gate
attuale** (isDepartureSafe = coda, isSlotConflictFree = docente libero, M18 guard =
keepsDayAnchored). Il gate accetta gia' tutti i donatori coda+liberi. Il blocco reale
NON e' l'H17-departure che la direzione targetizza: e' teacher-busy (396, la maggioranza)
+ VRP (111, i donatori coda+liberi che la direzione vorrebbe accettare sono gia'
VRP-esclusi). Implementare (d) quindi non apre nuove chiusure: G11 venerdi resta
UNCLOSABLE. La direzione risolve un vincolo (departure) che qui non e' il collo di
bottiglia.

## Lead secondario (NON in scope FASE D, da root-causeare a parte)

I **111 donatori coda + docente libero sono VRP-esclusi dallo slot-0 del venerdi**.
Lo slot-0 del venerdi e' calendar-open e vuoto (e' il leading hole), quindi in
teoria dovrebbe essere nel available set di una lezione G11. Se quella
esclusione VRP e' un artefatto di filtro (es. DailyActivityQuota / stage / pinned),
sbloccarla potrebbe rendere importabili i 111 e chiudere G11 venerdi. Da
verificare in uno spike dedidato (candidato SPIKE 5): ipotesi "l'esclusione VRP
dello slot-0 venerdi per G11 e' spuria", da misurare.

## Raccomandazione

1. **NON adottare (d)**: non chiude G11 venerdi (VRP + teacher-busy, non departure).
2. La direzione che evita il problema a monte resta la **(c) ancora settimanale nel
   template di costruzione** (WeeklyTemplateBuilder riserva la cella (venerdi,
   slot-0) per G11 -> M32 la difende in costruzione, prima che la LS la sposti).
   E' lo SPIKE 3 e valuta questa come alternativa al repair.
3. Oppure accettare il residuo (16 giornate in ritardo, tutte G11 venerdi,
   pattern noto; day-start globale 99.2%).

## Consegna a 3 righe (addendum)

- Ipotesi: estendere AnchorRepair ad accettare donatori coda-di-giornata chiude i 16
  ritardi di G11 venerdi.
- Risultato misurato (harness SpikeApplicabilityAnatomyTest su gate-finale):
  UNCLOSABLE; il blocco e' teacher-busy-at-slot (396/666) + VRP (111), non
  l'H17-departure che la direzione targetizza (i donatori coda+liberi sono gia'
  VRP-esclusi).
- Raccomandazione: BUTTARE la (d); valutare la (c) ancora nel template di
  costruzione (SPIKE 3); lead secondario = root-causeare l'esclusione VRP dei 111
  donatori coda+liberi (possibile filtro spurio).
