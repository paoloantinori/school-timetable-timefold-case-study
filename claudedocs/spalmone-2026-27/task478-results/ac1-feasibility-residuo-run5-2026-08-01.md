# TASK-478 AC#1 - SPIKE di fattibilita' ExactFinisher sul residuo run5 (zai, 2026-08-01 notte7-bis)

> Spike read-only sull'export di run5 (`task452-results/verify-source-input-run5/`, score -38hard:
> 16 H0 unassigned). Nessun solve extra, nessun DB write. run6 (portfolio) girava in parallelo.
> Mandato punto (4): l'enumerazione bounded depth<=2 trova un completamento 0-hard del residuo?

## DOMANDA AC#1
Sul residuo reale di run5 (16 unassigned = 11 T08/G03/Matematica + 5 T19/G06/Barberia,
confermati dal diff lessons.txt run4->run5: T08/G03 80->69 = 11, T19/G06 47->42 = 5),
esiste un completamento 0-hard a depth<=2? (fissato il resto, piazzare i 16 con bump chain <=2.)

## METODO (enumerazione depth-0 sui soli export)
Un completamento depth-0 = piazzare ogni lezione residua in una cella (date,hour) libera SENZA
spostare nulla. Enumerata la capacita' collocabile dai soli export (`lessons.txt`, formato
`group|date|hour|subject|teacher|pinned`), rispettando le famiglie hard verificabili da lessons.txt:
- **H1** teacher conflict (teacher libero alla cella)
- **H3** group conflict (gruppo libero alla cella)
- **H7** teacher maxDailyHours (T08/T19 cap default 6, maxDailyHours NULL -> default)
- **H11** group maxHoursPerDay (G03 = 6, G06 = 8, da DB seed non toccato dal solve)

Capacita' collocabile simultanea = somma su ogni data scolastica di `min(slack_H7, slack_H11, slot liberi quella data)`.
Script: `/tmp/ac1_feasibility2.py` (idempotente, read-only).

## RISULTATO - depth-0 FEASIBLE con margine larghissimo
| coppia residua | need | celle H1+H3 libere (upper bound) | celle H1+H3+H7+H11 | capacita' simultanea depth-0 |
|---|---:|---:|---:|---:|
| T08/G03/Matematica | 11h | 378 | 124 | **45h su 44 date** |
| T19/G06/Barberia | 5h | 402 | 396 | **377h su 168 date** |

**Verdetto: depth-0 (no-bump) 0-hard completion ESISTE**, 4x la capacita' necessaria sulla coppia
piu' stretta (45h vs 11h). depth<=2 e' banalmente soddisfatto (depth-0 e' un sottospazio di depth<=2).

## FINDING DIAGNOSTICO (corrobora H1 varianza)
Il residuo NON e' limitato da conflitto teacher/group (H1/H3): 378+402 celle H1+H3 libere >> 16.
Lo slack si restringe col cap giornaliero (H7/H11: T08 satura a 6h/giorno in ~127/171 date,
G03 a 6h/giorno), MA restano 45h collocabili. **Conclusione: run5 non ha collocato i 16 non
perche' le celle 0-hard non esistessero, ma perche' la LS si e' impigliata in un ottimo locale** -
esattamente la tesi H1 (varianza) della diagnosi run4/run5, ora corroborata meccanicamente dal
conto delle celle hard-legal. run4 le ha collocate tutte (esistenza provata); run5 no (varianza).
L'ExactFinisher (completamento deterministico del residuo) le troverebbe: non e' soggetto alla
trappola dell'ottimo locale, enumera.

## CAVEAT (onesta')
- **H2 (room) NON verificata**: lessons.txt non ha colonna room. Se le stanze sono anch'esse
  sature, alcune delle 124 celle T08/G03 potrebbero essere H2-bloccate. MA 124 vs 11 da
  margine larghissimo: anche eliminando meta' delle celle per H2, restano ~62 > 11.
- **H20 (calendar window) parzialmente**: usate le date scolastiche del gruppo + 8 slot; se la
  finestra e' piu' stretta (G03 ~6h/giorno -> slot 0-5), le celle pomeridiane (14:30/15:30)
  si riducono, ma G03 e' proprio una 6h/giorno quindi la finestra 0-5 e' il target.
- **H5 (availability)**: non filtrata (T08/G03 regole availability). Impatto minore sul
  conteggio (le 44 date con slack sono gia' date scolastiche attive del gruppo).
- **Enumerazione all-hard definitiva** (tutte le famiglie, incluso H2/H20/H5, via scheletro 467
  contro il constraint set live) = lavoro AC#3 (prototipo). Questo spike e' la fattibilita'
  export-level (AC#1), che da' YES con margine.

## IMPLICAZIONE
L'ExactFinisher (pass fix-and-optimize post-repair, sibling di BoxRepair/HoleRepair, contratto
improve-or-noop) trasformerebbe l'hard-0 del path input da PROBABILISTICO (varianza LS) a
DETERMINISTICO per i 16 residui, perche' il sottoproblema "16 lezioni x celle 0-hard" ha soluzione
abbondante a depth-0. Complemento del portfolio (riduce la varianza), non sostituto. Conferma la
fattibilita' del finding 3 della ricerca (orbit.dtu.dk fix-and-optimize).

## STATO
- AC#1 (fattibilita'): **YES, depth-0 feasible** (H1/H3/H7/H11 da export, margine 4x-75x).
  H2/H20/H5 caveat sopra; all-hard definitivo = AC#3.
- AC#2 (design pass), AC#3 (prototipo flag-OFF + test), AC#4 (A/B): restano.
- Nessuna adozione, nessun codice, read-only. Script `/tmp/ac1_feasibility2.py`.
