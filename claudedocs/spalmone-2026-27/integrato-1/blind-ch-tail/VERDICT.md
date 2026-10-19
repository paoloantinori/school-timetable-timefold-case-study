# VERDICT: blind puzzle CH-tail (18/09, Fedora)

## Domanda

Come si riempiono i giorni dichiarati-vuoti di fine anno, e quali famiglie di
mosse mancano alla costruzione? (Suggerimento maintainer: un blind puzzle; il
golden aveva gia' risposto al COSA: piccolo-pool teorico.)

## Stato di partenza

Costruzione deterministica (dump post-cura-input G07): G12 9 giorni
dichiarati vuoti con 414h residue non piazzate; G06 4 giorni/162h;
G09 3 giorni/324h. Puzzle neutro a 3 linee, operatori/task/date
rinominati, regole hard solo combinatoriali.

## Esito arbitratore (75 mosse, gate hard sullo stato COMPLETO a 14 gruppi)

- **LEGALI 69/75 (92%)** al netto dei gate arbitrati (CELL/OP-SLOT/OP-CAP/
  SHAPE/CAP/MOD).
- EMENDAMENTO ONESTO (post-decodifica): 28 delle 75 mosse piazzano item
  PSEUDO (Esame x13, Stage x15) che il mio calcolo del pool residuo ha
  erroneamente offerto come ore ordinarie: escludendole, **41/47 reali legali
  (87%)**. I 6 reject: 4 OP-SLOT (T11 su G06 D37: occupato da
  ALTRI gruppi, invisibile al cieco per costruzione del puzzle) e 2 OP-CAP
  (T04 7h/giorno su D30). Zero CELL, zero SHAPE, zero CAP, zero MOD.

## Predizioni (PREDIZIONE.md, scritte PRIMA)

1. TRASFERIMENTI DIRETTI maggioranza, esprimibili: **CONFERMATA FORTE**
   (zero rilocazioni: il cieco non ha toccato NIENTE di gia' piazzato).
2. Famiglia mancante = catene di spostamento a monte: **REFUTATA** (non servono).
3. Il cieco non preserva la contiguita' per-docente: **REFUTATA** (tutti i
   giorni proposti sono contigui da slot 8: SHAPE mai scattato).
4. Swap cross-gruppo dello stesso docente: **NON MATERIALIZZATA**.

## Il verdetto contro il criterio pre-registrato

">= 60% mosse legali => la catena le sa esprimere: il difetto e' di RICERCA
(ordine/pesi), non di mosse mancanti". **87-92% >> 60%: SCATTA.** La
costruzione lascia centinaia di ore del pool NON PIAZZATE mentre i giorni
dichiarati vuoti sono celle legali riempibili con placement diretti che una
mente fresca trova in un passaggio. Non manca una mossa: manca che il fill
di coda CONSUMI il pool residuo (il box pass e la fill tail si fermano).

## Convergenza indipendente golden + cieco (la specifica di pratica-2)

Il cieco ha riempito con Lingua italiana, Barberia, Comportamento sociale,
Servizio tecnico e stilistico, Sviluppo sostenibile (gli item reali del pool
residuo); il golden riempie gli stessi giorni con Economia e Diritto, Storia,
Comportamento Sociale, Lingue, Matematica, Scienze. **Stessa CLASSE (piccolo
pool teorico 1h), composizione non identica**: due solutori indipendenti
convergono sulla direzione di pratica-2, il mix esatto resta del golden.

## Correzioni di spec che il round regala

1. **MESCOLARE le materie nel riempimento**: il cieco fa giorni
   MONOGRAFICI (5x stessa materia), il golden mescola 2-3 materie/giorno;
   i giorni monografici del cieco violerebbero M21 (cap 2h teoria/giorno).
   Il filler va a rotazione per-materia.
2. **Niente pseudo nel pool** (Esame/Stage: errore mio di contabilita',
   il filler li deve escludere per costruzione).
3. **Visione cross-gruppo del docente obbligatoria** (i 4 OP-SLOT reject
   erano collisioni con gruppi fuori dallo sguardo del cieco: il filler
   reale consulta l'occupazione globale, che la nostra Live gia' ha).

## Prossimo passo

Pratica-2 promossa a candidata con doppia evidenza (banco fill-record +
golden + cieco): il suo gate end-to-end e' il prossimo ciclo (integrato-6).
La meccanica: nel DayClusteredConstructionPhase, riserva al pass fill-tail
le ore del piccolo-pool teorico per i giorni dichiarati vuoti di coda
(newest-first come il pre-booking 564), con rotazione per-materia e
l'occupazione docente live.

Artefatti: puzzle/puzzle.txt, arbitrato/{moves-blind-r1.txt, arbitrate.py,
output}, DECODIFICA.md, PREDIZIONE.md.
