# RE-CERT 2b2c4c87 (predizione Fedora, riportata PRIMA delle corse)

> step48 transfer (predizione: -4 -> -1 INALTERATO: i fix toccano solo scarti e
> commenti, l'unico comportamento nuovo e' il filtro quota che potra' scartare
> qualche landing in overshoot: se il T13 chain usa una landing
> legalmente sotto quota, invariato), inerzia m39 pinnuovi, suite.

Esito: VEDI SOTTO.

## ESITO (2b2c4c87, heap 12/16g): 4/4 VERDI, predizione CONFERMATA con UNA divergenza di celle dichiarata

- Transfer: **-4 -> -1 INALTERATO** (2 x-swap identici alle celle di prima +
  depth-3 T13). UNA divergenza di CELLE nel braccio depth-3: la landing
  dell'hinge e' ora 2027-03-18@slot7 (era 2027-01-15@slot4) e il census non
  mostra piu' le chiavi H7D3-VRP=621/NO-PARTNER=40: il filtro quota-parita'
  ripristinato scarta le landing in overshoot che il banco-twin valutava, e la
  catena trova una landing legale DIVERSA (sotto quota). Esito, mosse (6) e
  forma IDENTICI: la predizione 'il filtro potra' scartare qualche landing in
  overshoot... se la chain usa una landing legalmente sotto quota, invariato'
  e' confermata nella lettera (esito invariato) con la cella diversa come
  unica novita' osservabile.
- Inerzia flag OFF: m39-off -11 -> -3 -> 0 (3/3/9/13), m39-on -13 -> -6 -> -3
  (4/3/8/12): pinnuovi INALTERATI.
- Suite: **1353/0/0/92 EXIT 0 BUILD SUCCESS**.

## CONCLUSIONE

Il pacchetto del branch (x-swap curato fb007377 + H28 ratificato 4ad0f0dd +
depth-3 curato 2b2c4c87) e' PRONTO per il merge+ratifica finale di Fedora.
