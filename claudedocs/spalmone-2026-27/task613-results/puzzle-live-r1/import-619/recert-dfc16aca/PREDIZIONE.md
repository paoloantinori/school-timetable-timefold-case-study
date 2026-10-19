# PREDIZIONE re-certifica dfc16aca (Fedora, pre-registrata; laptop la riporta PRIMA delle corse)

> transfer A3/B3 IDENTICI al numero (quelle forme non scattavano sui bacini
> certificati), HRF-IMP-H7-BF atteso a 0 nel census, inerzia m39-off/-on
> invariata, suite EXIT 0. Divergenze dalla predizione: fermati, cattura, riporta.

Numeri attesi (dal transfer pre-fix):
- A3 bank-equivalent: -14 -> -11 (levers) -> -10, targets=6 slides=1 moves=2;
- A3 production: -14 -> -10;
- B3: -9 -> -8, 1 import/2 mosse, H31 assente;
- inerzia m39-off -3 -> 0 (targets=3 slides=3 rotations=14 moves=17), m39-on -6 -> -4;
- suite integrale EXIT 0.
F5 (pin step33b a -10) applicato in questa re-certifica come richiesto dal commit.

Esito misurato: VEDI SOTTO.

## ESITO MISURATO (wt/619-porting @ dfc16aca, heap 12g): numeri 5/5 IDENTICI, TRE scostamenti dichiarati per l'adjudication

NUMERI (tutti conformi):
- A3 bank-equivalent: -14 -> -11 -> -10, targets=6 slides=1 moves=2. = atteso.
- A3 production: -14 -> -10 (F5 applicato: assert -10 ora pinato nel banco). = atteso.
- B3: -9 -> -8, 1 import/2 mosse, H31 assente. = atteso AL NUMERO.
- inerzia m39-off -3 -> 0 (targets=3 slides=3 rotations=14 moves=17) e m39-on -6 -> -4. = atteso.
- suite integrale: 1344/0/0/83 EXIT 0 BUILD SUCCESS. = atteso.

TRE SCOSTAMENTI DALLA LETTERA DELLA PREDIZIONE (nessuno cambia un numero,
tutti da adjudication Fedora prima del merge):
1. HRF-IMP-H7-BF NON e' a 0: 102 reject su A3, 50 su B3. Il gate FA LAVORO
   REALE nella ricerca (scarta donatori backfill che avrebbereo aperto H7),
   senza cambiare l'esito sui bacini certificati. La predizione 'atteso a 0'
   leggeva il census come inerzia del gate; il census conta i RIFIUTI, e il
   gate sta rifiutando: il fix F1 e' ATTIVO, non decorativo.
2. Su B3 la mosse vincente CAMBIA identita': pre-fix era Lingua Inglese
   (T21) da 2026-12-14@4; ora Relazione con il cliente (T13) da
   2026-12-01@3 = STESSA ORA del buco: il comparatore same-hour (F2) era
   dichiarato ma MORTO (getHour 8-15 vs slotIndexOf 0-7 mai uguali) e ora
   vive: il tier di ordine ora esiste e cambia quale donatore vince. Esito
   identico (-8, 1 import, 2 mosse), mossa diversa: coerente con F2, MA e'
   un cambio di comportamento vs il banco iter3b.
3. (minore) F4 net-form: nessun effetto osservabile sui bacini (i permessi
   occupant==cand non si presentavano), dichiarato per completezza.

## VERDETTO (tre righe)

1. IPOTESI: i tre fix di gate non cambiano i numeri certificati (le forme non
   scattavano sui bacini).
2. MISURATO: numeri 5/5 identici (-10/-10/-8, inerzia, suite), MA il census
   H7-BF=102/50 prova che il gate e' attivo (non inerte) e su B3 il fix F2
   cambia QUALE import vince (same-hour ora possibile): comportamento
   intenzionalmente diverso dal banco, esito identico.
3. RACCOMANDAZIONE: il branch e' pronto per il merge (numeri intatti, fix
   attivi e corretti); i tre scostamenti sono dichiarati per la ratifica.
