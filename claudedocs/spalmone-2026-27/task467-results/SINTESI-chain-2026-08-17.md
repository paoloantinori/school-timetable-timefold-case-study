# Sintesi della giornata chain (17-18/08, per il risveglio di maintainer)

> Otto round in tre turni. Punto di partenza: la chain (deriver 364 + enumerator
> 467) produceva -42hard deterministici (20 boxed RA di T15 su G04).
> Punto di arrivo: -3hard con H0 a zero. Prototipi tutti DEFAULT OFF, nessuna
> adozione: le decisioni restano a te.

## La storia in una tabella

| round | meccanismo | treatment | verdetto |
|---|---|---|---|
| probe 510 | diagnosi piazzamento esatto | - | STRUTTURALE: RA lab-bound (lun/ven VRP-vietati), mercoledi' H7-saturi |
| 6 | reservation flat ceil(domanda/giorni-L) | -70 | FALLITO: rifiuta blocchi interi (mercoledi' 174h -> 0 pinned), numeratore grezzo (G08 5h/giorno) |
| 7 | numeratore non-lab + ritmo golden (2h) + CLIP | -53 | FALLITO ma RA curata (0 boxed); le code del clip diventano boxed |
| 8 | reservation TEACHER-wide + plan sizing netto | **-3** | GATE SFIORATO: H0 zero, residuo H7 -2 + H17 -1 (LS-varianza) |

## Cosa ha funzionato

1. Il ritmo DERIVATO dal golden (cap meno carico medio del docente nei giorni
   della materia): banner con provenienza, replicato dal codice la tua misura
   (4h medio su cap 6). Nessuna formula inventata.
2. La reservation TEACHER-wide (round 8): senza, i pin degli altri gruppi
   mangiano l'headroom; con, i 13 mercoledi' con RA stanno a 4h come il golden.
3. Il CLIP (mai rifiutare il blocco intero) + il plan sizing (budget netto per
   pair prima del greedy): insieme chiudono H0.
4. Il protocollo di cattura a quiescenza confermata: due catture mid-flight
   (run6, run8) avrebbero dato numeri falsi senza il polling live.

## Cosa NON ha funzionato (e resta aperto)

1. hard 0 e' binario: -3 non passa il gate. Il residuo e' la famiglia
   LS-varianza dei bad-seed storici, non un blocco del piano.
2. La placement-parity (TASK-517) conta ancora 20 flag famiglia-B: la stessa
   cura va estesa alle altre coppie (T06, T18, T22).

## Decisioni per te (nessuna presa da noi)

1. Round 9: stesso treatment su seed diverso (o N=2-3): -3 e' pavimento o
   singolo-run?
2. Estensione della cura alle altre coppie flaggate (meccanismo identico,
   nessun nuovo ingrediente).
3. Ratifica della direttiva golden-first estesa ai PIANI (paragrafo proposto
   in placement-parity-check-2026-08-17.md).
4. Se un giorno la chain si ratifica: la bozza andra' scritta coprendo deriver
   + enumerator + TASK-502 + budget-reservation, forma opt-out standard.

## Mappe dei commit

Implementazione: 1c646a1c (r6), eb73654e (review), 3e08d4e6 (fix TreeMap),
58fabfbd (r7 golden), 1c0a497e (fix clip), 1bbe4d9a (r8 teacher-wide + sizing).
Analisi: cc62f69a (TASK-517 check), 72bb2ef9 (verdetto r8). Verdetto cumulativo:
ab-chain-2026-08-16/VERDICT.md (round 3-8).
