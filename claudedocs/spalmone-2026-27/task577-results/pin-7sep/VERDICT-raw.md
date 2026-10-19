# VERDICT-raw: ciclo pin-full-cells (profilo pin-7sep, 7/09 sera)

Ciclo GO-CICLO-PIN: jar 19:42:32 su HEAD 7f79fc27 (contiene 1c3be0d1: pin
REALE, entrambi i flag; + 1419ea68 contatori chainBlk; + a58b3708 cura input
ESTESA 19 righe), `PREFLIGHT-OK classes=[SurplusHarvest] flags-verified`
(surplus-harvest + pin-full-cells + checkpoint staged-checkpoints-pin),
SURPLUS_TRACE_ALL=1 nel processo. Suite PASSO 1: 1286/0/0/42 EXIT 0 (conferma
della verde di Fedora, non scoperta).

## GUARDIA al banner: SUPERATA

`TASK-590 pin-full-cells: promotedPin set on 9628 lessons in full declared
cells` (19:52:42, fine costruzione tentativo 1): N=9628 lezioni pinnate.
Le celle piene a quota a fine costruzione sono MOLTE (la costruzione uniforme
riempie quasi tutto a quota): il metro della domanda non e' il conteggio
lesioni ma H34-a-consegna vs H34-a-fine-costruzione.

## DOMANDA PRE-REGISTRATA (mandato 16:40, invariata)

Le celle dichiarate che la costruzione riempie a quota sopravvivono alla LS?
- Metro: H34 a consegna (analysis.json del capture) vs H34 a fine costruzione
  (le celle NON-piene a fine costruzione sono il complemento delle pinnate:
  conta dal dump di confine o dal tracer al primo sparo SH).
- Lettura attesa: se H34-consegna < celle-vuote-a-fine-costruzione, il pin
  protegge (alcune celle vuote in costruzione restano vuote o si riempiono,
  ma nessuna cella PIENA si svuota); se H34-consegna == celle che la LS
  svuotava comunque senza pin (confronto col v132: input NON identico, cura
  estesa: confronto indicativo non rigoroso, dichiarato).
- NOTA input: questo ciclo gira sulla cura ESTESA (19 righe phantom rimosse:
  le mie 6 + le 13 di a58b3708): il confronto v132-vs-pin e' a input
  diverso, la domanda autocontenuta resta valida.

## RISPOSTE (consegna 05:33; ciclo 19:52-05:33, ~9h40m; 5 tentativi + rifinitura)

**LA DOMANDA: H34 a consegna = 4 celle vuote (analysis, -4 hard). H34 a fine
costruzione = decine (il banner pinnava 9628 lezioni in celle PIENE: la
costruzione riempie quasi tutto; le vuote a costruzione erano ~22 per il
tracer v12.3). Quindi il confronto netto stavolta e' pin-vs-nopin:**

- **pin-7sep: [-8/0]hard, H34=4** (celle vuote a consegna)
- **v132-7sep (SENZA pin, input quasi identico): [-6/0]hard, H34=3, H33=2**

**Il pin PEGGIORA di 2 hard end-to-end** (4 vs 3 celle vuote, -8 vs -6):
CONFERMA il segno del banco locale di Fedora (PinDrainBenchTest 54b40c18:
LS drena 22->24 MA il pin peggiora 22->30 e blocca la chiusura H33 29->4
invece di 29->0, perche' congela le ore-donatrici). Caveat dichiarato: bacino
e input non identico (cura estesa a 19 righe in questo ciclo), ma il segno
concorda su banco + end-to-end. **Raccomandazione Fedora NON ADOTTARE: il
dato laptop e' coerente.**

Altri dati:
- Guardia banner superata al lancio: N=9628 (tentativo 1), poi 649-9466 nei
  tentativi GRASP (la costruzione randomizzata pina meno).
- avr=0, ISE=0 su 9h40m. Concordanza OK memory=0 db=0 (12a consecutiva).
- Sanity NON CONFORME 5 gruppi (G07 x2, G08, G09, G10, G14).
- 10 righe SH (5 occasioni x 2): una occasione moves=40 cells=8 (tentativo
  con donatori liberi), un'altra moves=4 cells=1.

## CONTATORI chainBlk (verbatim, l'input per il design v14)

chainBlkSameGroup=0 e chainBlkSlide=0 su TUTTE le occasioni;
**chainBlkSlideH1 domina (16, 160, 8, 220, 216)**: i near-miss della catena
sono bloccati dallo slide-H1 del BLOCKER (la sua slide colliderebbe col
docente), NON da conflitti same-group ne' da slide generiche. Il design v14
che voglia convertire i near-miss in compound deve risolvere lo slide-H1
del blocker (lo stesso collo del pass plain).

Righe complete nel log archiviato /tmp/planner-pin-7sep-final.log.

## Score / righe SH / sanity / concordanza

score.txt: `score: [-8/0]hard/[-1231766/-15367]soft` (analysis medium
riconta -771202: divergenza sorgenti nota TASK-509). Sanity 5 rossi.
Concordanza 12a. Full-day e KPI nel capture.
