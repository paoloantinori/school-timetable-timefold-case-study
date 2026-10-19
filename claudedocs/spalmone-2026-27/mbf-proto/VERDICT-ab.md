# VERDICT: A/B di ratifica M-BF a ciclo completo (17/09 sera, laptop)

Esito: **4 criteri su 5 PASS; il (d) interleaved MISS attenuato**. La
ratifica NON si chiude stasera: la direzione e' confermuta senza danni, la
magnitudine e' sotto soglia. Decisione a maintainer/Fedora coi numeri in mano.

## Le consegne (profili freschi appaiati, catena identica, solo il flag differisce)

| | A (controllo, M-BF OFF) | B (M-BF ON, banner armed=true) |
|---|---|---|
| hard consegna | **-6** | **0** |
| medium | -1.103.619 | -1.223.561 |
| tentativi | 3 (predittore ha scartato i GRASP) | 3 (idem) |
| ricorrenza unpinned | 69,8% | 69,7% |
| full-day | 94,4% | 94,6% |
| INTERLEAVED multi-docente | 52 | 40 |

## Contro i criteri pre-registrati (CRITERI.md, scritti prima dei lanci)

a) HARD: B=0 non peggiore di A=-6 (migliore). **PASS**. Nota onesta: lo 0-hard
   di B arriva dalla rifinitura di consegna del suo attempt 1 (stessa forma
   di integrato-3); A invece consegna -6: sul binario hard la differenza
   A/B e' FORTUNA DI BACINO (i due attempt-1 sono bacini diversi dopo il
   primo retry), non merito di M-BF: il medium non compra hard.
b) RICORRENZA: B 69,7 >= 69. **PASS**. Il timore dell'harness (-6,3pp) NON si
   materializza col template vivo: delta A/B -0,1pp. M-BF combatte S13 ma la
   perde solo di un soffio, a peso 20.
c) FULL-DAY: B 94,6 in banda [91,95]. **PASS** (e sopra A).
d) INTERLEAVED: B 40 < A 52 di 12 giorni, servivano >= 20. **MISS ATTENUATO**.
   Decomposizione: G13 6->1 (la famiglia LS-borne quasi azzerata: il caso
   campione del trio H28 ora consegna pulito), G05 2->0, G09 2->1; il
   resto e' G01 42->38 (la famiglia testarda). L'effetto del banco (-60)
   arriva vivo solo in parte: nel ciclo reale M20/M32/S13 contrattano e a
   peso 20 la frammentazione resta conveniente su G01.
e) SANITY: B NON CONFORME su 3 gruppi (G07 coda, G08 incl. il buco
   04/01 ricorrente, G10): TUTTE celle della classe giorni-corti nota,
   nessuna classe nuova. A aveva 4 celle della stessa classe. **PASS**
   (dichiarato: nessuna cella rossa NUOVA).

## EXTRA: B e' il n=4 della serie full-chain

La serie su protocollo integrale diventa: -4, -1, 0, e ora B=0 (A=-6 della
stessa serata conferma che il bacino deterministico stasera non era buono:
la varianza della serie e' reale, p(0-hard) per tentativo resta ~0,75
coerente col census n=8).

## Predizioni a confronto (tutte registrate prima)

- Fedora "A atteso [-4,0]": BOCCIATA (A=-6). La serie si allarga verso il
  basso: segnalato, coerente con l'ipotesi maintainer dei CH-change che densificano
  la coda (il census CH e' gia' in coda a riguardo).
- Fedora "B pari ad A sull'hard": VERA nel senso binario (non peggiore).
- Fedora "ricorrenza 69-73, se <69 sweep": 69,7 dentro.
- Fedora "interleaved decisamente sotto": DIREZIONE si (-12), SOGLIA no.
- laptop "A 0-hard probabile": BOCCIATA (-6); "ricorrenza B 70-72": appena sotto
  (69,7); "interleaved B < 34": vicino ma mancato (40).

## Per maintainer/Fedora (nessuna proposta mia di flip)

1. La DIREZIONE e' pulita: zero danni su hard/ricorrenza/full-day/sanity,
   G13 interleaved 6->1. Il criterio d manca perche' G01 (38 giorni)
   resiste a peso 20.
2. Le leve disponibili, in ordine di costo: sweep peso (40-80/min: il criterio
   b regge con margine, 69,7 a peso 20 lascia spazio), o accettare -12 come
   valore di una polizza che non costa nulla.
3. Il thread G01 e' lo stesso dell'audit: template-builder + S13; M-BF
   da solo non lo smonta.

Artefatti: ab/armA + ab/armB (capture complete, score, KPI, sanity; export
111MB ciascuno rimasti LOCALI). Log: /tmp/mbfa-start.log, /tmp/mbfb-start.log.
