# VERDICT twin-tail-a1 (7/09 sera, Fedora, warm-start locale dal checkpoint POST_B_ROOM attempt1 del ciclo v132)

Domanda pre-registrata: la famiglia EDF-H33 (flag `planner.construction.edf-h33.enabled`,
commit 1c3be0d1) cosa fa al confine VERO post-B-ROOM del bacino leader di v132?

## Setup

- Checkpoint: `staged-checkpoints-v132/attempt1/POST_B_ROOM.json` (tier -9, boxed 0, il
  leader pick-best alla cattura). Profilo gemello locale `v132-twin`, jar 1419ea68
  (stesso seed del ciclo: nessun cambiamento di seeding da a58b3708).
- Coda via `snapshot-tail.sh -l`: QFR-2 (1 cella chiusa, 4 mosse) + EDF + salvataggio,
  ~15 min, deterministica (due run identici).
- Braccio debug: `-Dedf.debug=true` per il censimento rifiuti del pass.

## Misura

- Consegna locale: `[-7432/0]hard/[-674252/-5513]soft`, tier timeslot **-9 -> -6**.
- Residuo tier: **H34 -3** (3 celle, coda-anno, dominio harvest) + **H33 -2** +
  **H7 -1**. H0 zero.
- Le due celle H33 del v131 (G14 19/3, 9/4) sono CHIUSE in questo bacino; al loro
  posto due venerdi' DIVERSI: **G14 12/03 e 30/04, entrambe 2h su quota 3 / floor 3**.
- EDF-H33: `[EDF-H33] under-floor targets=2`, **0 chiusure**. Censimento rifiuti:
  `{GATE-E=1181, SLIDES=62, VRP=6, H1=59, ROOM=17}`.
- H7: **T26 30/09/2026, 7h su cap 6** (una sola ora in eccesso, chiudibile con
  una mossa singola).

## Diagnosi meccanica del muro H33-G14 (il triangolo T16/G05/G14, RIPRODOTTO)

Il donatore unico con headroom gate-E e' il giovedi' 13/05 (quota 3, placed 5, +2,
nessuna lezione pinnata). Estrazione per slot:

- slot 8/9/10 (ore T06/T26, docenti potenzialmente liberi al venerdi'
  10:00): OGNI estrazione mid-blocco richiede la slide di T16 11->10 sul posto,
  e T16 il 13/05 alle 10:00 fa **G01** (query DB): la slide muore in H1 ->
  i 62 SLIDES.
- slot 11/12 (le ore di T16 stesso): atterraggio al venerdi' 10:00 dove T16
  fa **G05** in ENTRAMBI i venerdi' bersaglio (12/03 e 30/04: query DB) -> gli H1.

Ogni via e' geometricamente forzata a passare o da T16-docente (atterraggio) o
da T16-follower (slide), e T16 alle 10:00 e' occupato da MODA su tutti e tre
i giorni coinvolti. **Il difetto H33-G14 e' cronico e input-locked, non v131-specifico:
due venerdi' qualunque cadono nel triangolo ogni volta che il surplus giovedi' e'
murvaniano.** Stessa radice delle 2 H0 (Confezione T16 28/30). La domanda referente
rev 16 (domande-brevi.md) copre l'asse: la risposta della scuola sblocca H33-G14
E H0-G14 insieme.

## Cosa resta vivo per EDF-H33

La famiglia e' POPOLAZIONE-corretta al confine reale (targets=2 = le due celle vere)
e ha chiuso 6 celle non-G14 nello stato a costruzione (banco realseed). Il suo
valore e' sulle celle H33 di gruppi NON triangolo-lockati; sulle G14 nessun pass
deterministico puo' fare nulla finche' l'input non cambia.

## Prossime mosse (dati, non opinioni)

1. H7 T26 30/09: una mossa singola (LS o repair mirato) la chiude; verificare
   se i cicli notturni la chiudono da soli.
2. H34 x3: dominio harvest, attendere la consegna v132 vera (il vincitore riceve la
   catena completa) e i contatori chainBlk (1419ea68) classificheranno i near-miss.
3. H33-G14: domande-brevi rev 16 (nessuna cura search-side possibile).

Artefatti: analysis.json (consegna), tail-run1.log / tail-run2-debug.log (delle due
corse deterministiche), checkpoint-attempt1-POST_B_ROOM.json (lo stato d'ingresso).

## ADDENDUM (20:45, coda CON harvest armato + tracer)

Il primo twin-tail non armava `surplus-harvest.enabled` (opt-in, null -> OFF): la
coda replica del vincitore era incompleta. Ri-armata TUTTA la catena (edf-h33 +
surplus-harvest + edf.debug + SURPLUS_TRACE_ALL), stessa coda, stesso checkpoint:

- L'harvest spara DUE occasioni tail (prima chance + FINAL v11-p2), entrambe
  **moves=0 cells=0**, consegna identica [-7432/0]hard: coerente con la consegna
  del laptop (-6 che conserva le 3 H34).
- **La finestra NON e' il difetto**: il tracer enumera i 3 bersagli in ENTRAMBE le
  occasioni (48 righe TARGET: 16 per cella). Il difetto e' nei GATE: slideH1=1264
  (la slide dei follower del donatore muore in H1 sull'adiacente), border=544,
  h7=360, roomBusy=96, teacherBusy=88.
- **Il contatore chainBlkSlideH1 (1419ea68) DECODIFICA la firma near-miss**: i
  8 near-miss della catena sono TUTTI chainBlkSlideH1=8 = il blocker viene
  trovato ma i SUOI follower non possono scivolare (H1 sulla destinazione della
  slide del blocker). v131's 96/0/0 would now read as blocke-found-but-unextractable.

Direzione v14 che l'evidenza indica: il muro e' la slide-H1 dei FOLLOWER (donatore
e blocker), non la finestra ne' la selezione docente-bersaglio. La famiglia che
manca e' la chain a tre livelli (donatore -> blocker -> follower-del-blocker) o
l'estrazione same-day-discipline gia' provata in EDF applicata all'harvest donor
side. Da decidere col maintainer dopo i cicli notturni.
