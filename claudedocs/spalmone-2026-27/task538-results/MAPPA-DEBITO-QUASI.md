# Mappa del debito quasi run1: cosa sta comprando il pure-0 (TASK-538 M3, 29/08 notte)

Strumento: `scripts/analyze/quasi-debt-map.py --run <lessons.txt> --golden`
(dump estratto dal blob del commit 737b4c62: il worktree ha perso i file di un
pull intermedio, l'object store e' integro, vedi nota in coda).

## La riconciliazione

La mappa foglio-grezza (144 celle / 677h / 22 vuoti) NON e' il debito: contiene
due fantasmi che anche il golden pratica (base foglio > pratica scuola, classe
TASK-550):
- **G13**: il foglio dichiara 10-11h/giorno, il golden ne colloca 4-6 (133
  celle "sotto" in entrambi);
- **metà giugno** (G09/G10/G11/G12): giorni dichiarati che nemmeno
  il golden riempie (15 vuoti condivisi).

Il **DELTA run-vs-golden** (stessa base per entrambi, immune dai fantasmi)
riconcilia esattamente col verdetto: **23 celle sotto quota oltre-golden + 7
giorni dichiarati vuoti oltre-golden = il -30 consegnato**.

## La forma del debito nuovo (tutto qui)

| Gruppo | Celle -1h oltre golden | Giorni vuoti nuovi | Dove |
|---|---|---|---|
| G07 | 10 | 0 | sistematico ott-mar, giorni da 5h collocati a 4 |
| G13 | 12 | 0 | sparsi set-mar, run 4h dove golden 5h |
| G06 | 1 | 2 (09-10/06) | 26/05 -1h |
| G10 | 0 | 2 (26/05, 08/06) | |
| G11 | 0 | 1 (21/05) | |
| G12 | 0 | 2 (16/04, 21/05) | |

Totale: 23x1h + 7 giorni pieni (~54 ore su 10.542 = 0,5% dell'orario).

## La lettura per l'arbitraggio (non e' un costo di ricerca)

Il confronto col baseline flag-OFF (H32=1 cella, ma -6 puro con 3 lezioni non
collocate) mostra lo SCAMBIO esplicito, non un fallimento di ricerca: STESSO
input, due equilibri; a flag OFF il solver paga 6 unita' pure per tenere H32=1,
a flag ON paga 23 celle quasi per azzerare il puro. Nell'ordine del punteggio
(una unita' pura > qualsiasi numero di quasi) la scelta e' forzata: [0/-30]
batte [-6/-6]-equivalente. Il debito quasi e' il PREZZO VISIBILE di cio' che
prima era un muro: le lezioni che prima restavano fuori (3 boxed) ora stanno
dentro, e la differenza la pagano i giorni dichiarati.

Le due firme sistematiche (G07 -1h ogni ~2 settimane; G13 -1h sporadico)
meritano una domanda a referente SOLO se si vuole chiuderle a input (pool incarichi
vs quota dichiarata: se il pool non copre la somma delle quote, qualche -1h e'
matematicamente forzato); altrimenti sono la moneta con cui il solver compra
lo 0 puro.

## Nota tecnica

- run1 lessons.txt: blob e18dca0f (commit 737b4c62), estratto in
  /tmp/quasi-run1-raw/; i file del commit non erano nel worktree al momento
  dell'analisi (indice/worktree li ha persi dopo il pull; object store integro,
  fsck pulito; recupero via cat-file per questa analisi, ripristino completo
  del tree differito).
- Lo script resta riusabile per run2: stessa forma, confronto n=2.
