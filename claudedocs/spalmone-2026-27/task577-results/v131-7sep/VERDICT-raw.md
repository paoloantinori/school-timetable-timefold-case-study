# VERDICT-raw: secondo ciclo col pacchetto v13.1 (profilo v131-7sep, notte 6-7/09)

Ciclo col pacchetto d5076723 (v13.1 tail-swap stessa-data + delegazioni
fonte-unica + v13 catena two-harvest completata), config identica a
v123-6sep/notte-4sep (default tutto, solo surplus-harvest armato, costruzione
deterministica), profilo fresco, cattura checkpoint in dir dedicata
staged-checkpoints-v131. Pre-registrato PRIMA del lancio: le risposte sotto le
domande arrivano alla consegna. Baseline: v123-6sep ([-8/0], 4 occasioni, 1 con
moves=32 cells=8, oracleRevert=0 ovunque, slideH1 280-6872, bersagli ESISTENTI
al tracer).

## DOMANDA PRE-REGISTRATA (mandato GO-OVERNIGHT)

**Nei bacini condannati il tail-swap spara?** Attesa dal banco locale:
- `chainNearMiss` ALTO (la famiglia di near-miss teacherBusy-al-landing e'
  quella che la catena v13 cerca);
- `chainFail` BASSO ma > 0 se esistono blocker con slide pulita e senza
  headroom (l'extend-only della v13 falliva li': il tail-swap stessa-data
  della v13.1 dovrebbe convertirne una parte);
- `moves>0` SOLO se esiste almeno un compound valido (blocker spostabile con
  la sua ora donata al suo gruppo).

Domande di contesto ereditate dal v123: (b) oracleRevert resta 0? (c) slideH1?
(d) consegna hard/avr/sanity/concordanza? (g) wall per sparo?

## RISPOSTE (consegna 08:26; ciclo 00:28-08:26, ~8h)

**DOMANDA (tail-swap nei condannati): SI', la catena SPARA e trova i near-miss.**
- `chainNearMiss`: 0, 72, 32, 176, 96 per occasione (ALTO come atteso: la
  famiglia teacherBusy-al-landing esiste e la catena la vede).
- `chainFail`: 0, 24, 0, 0, 0 (BASSO; >0 solo nell'occasione che muove: i
  compound tentati ma non completati).
- `chain`: 0, 0, 8, 0, 0 (SUCCESSI di chain-search: NON nel total per design).
- `moves>0`: SI', 1 occasione su 5 (tentativo 2, 04:02): **moves=40 cells=8
  reverted=3** (oracleRevert 3: i compound hanno un piccolo costo di revert,
  nuovo rispetto al v12.3 che era a 0).

**Ereditate:**
- (b) oracleRevert: 0,0,0,0,0 tranne l'occasione che muove (3). Quasi-zero.
- (c) slideH1: 664-1832, resta il collo dominante fra i rifiuti plain.
- (d) consegna **[-8/0]hard/[-1249233/-17987]soft** = in banda (baseline -8).
  avr=0, ISE=0 su 8h. Concordanza OK memory=0 db=0 (10a consecutiva).
  Sanity NON CONFORME 4 rossi (G07, G09, G10, G14; date sparse:
  28/05, 09/06 x3, 23/03, 08/04, 19/03+09/04). Full-day 92,0%.
- (g) wall per sparo: coppie a 5-8s; occasioni a ~87-106 min (i confini).

## DATI GREZZI (10 righe = 5 occasioni x 2 passate)

```
02:15:40+45  moves=0  cells=0 rev=0  border=256 slideH1=848  chain=0  nMiss=0   fail=0
04:02:07+13  moves=40 cells=8 rev=3  border=290 slideH1=724  chain=0  nMiss=72  fail=24  <-- MUOVE
05:34:47+55  moves=0  cells=0 rev=0  border=464 slideH1=1440 chain=8  nMiss=32  fail=0
06:49:57+04  moves=0  cells=0 rev=0  border=240 slideH1=960  chain=0  nMiss=176 fail=0
08:16:26+33  moves=0  cells=0 rev=0  border=520 slideH1=1832 chain=0  nMiss=96  fail=0   <-- vincitore
```

## NOTE

- La portata del pass resta 1-occasione-su-5 (come v123): la meccanica muove
  dove il bacino lo permette; il collo systemico resta slideH1.
- Il tracer: 296 righe SH-trace (bersagli esistono, confermato anche in v13.1).
- Checkpoint: staged-checkpoints-v131/ (dataset banco, dir dedicata).
- Confronto v123 (moves=32 cells=8) vs v131 (moves=40 cells=8): portata simile,
  la catena aggiunge near-miss discovery senza peggiorare la consegna.
