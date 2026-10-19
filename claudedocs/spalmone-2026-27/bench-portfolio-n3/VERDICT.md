# VERDICT portfolio N=3 multi-start (B-TIMESLOT) - 2026-07-31 mattino, zai

HEAD (run): `feature/cartesian-block-entity`, JAR 04:25 (= 280278f1, stesso dell'arm A).
Setup identico all'arm A: production fresco (drop-and-create + DataGenerator seed) +
import-calendario + pre-assign-reset + pre-assign, **T01 231/231** (constrained-teacher ON).
Launch: `-Dplanner.portfolio.size=3 -Xmx4g` sulla riga `java -jar` (mai MAVEN_OPTS).
first-hour-slot=OFF in questo run (JAR pre-adozione): A/B vs arm A valido (entrambi OFF).

## VERDETTO (3 righe, numeri dai raw)

1. **NON ADOTTARE (netto negativo).** Il portfolio N=3 multi-start NON batte il single-start
   (arm A) a parita' di condizioni: finale **-120hard/-973892medium/18337soft** vs arm A
   **-121/-964300**. Hard pari (-120 vs -121, rumore), ma medium **~9600 peggiore**
   (-973892 vs -964300), fullday **89,1% vs 89,3%**, ricorrenza UNP **73,9% vs 75,8%** (-1,9pp),
   holes 0=0. Costo **~2,6x wall-clock** (~2h vs ~46min: 9 solve B-TIMESLOT vs 3) e **2x RAM**
   (-Xmx4g vs 2g). Nessun KPI migliora, alcuni peggiorano.
2. **Meccanismo del fallimento (doppio).** (a) Il ciclo staged fa GIA' 3 iterazioni B-TIMESLOT
   con demozioni selettive: il portfolio le moltiplica per 3 (3x3=9 solve) ma i candidati
   **convergono tutti allo stesso hard -2226** (cluster stretto, medium entro ~12k): i
   random-restart non trovano un bacino migliore, lo staged+repair ci arriva gia'. (b) La
   `pickBest` ordina per hard primo: a B-TIMESLOT l'hard e' dominato dal room-null H29
   (~2210, lezioni senza aula pre-B-ROOM), quasi costante tra candidati, quindi NON
   discrimina: scegliere su 1 punto di hard e' rumore, e scarta candidati a medium migliore
   (iter1 cand1 -2227h/-835474m scartato per cand3 -2226h/-841718m, medium peggiore).
3. **Portfolio SHELVED.** Direttiva primaria (performance e' search): questa misura dice che
   il multi-start random-restart NON e' la search giusta per questo problema. Il parallelo
   (misure (a) speedup) e' **moot**: anche a costo zero non aiuta (qualita' pari-peggio).
   Non investire oltre sul portfolio; il budget meglio speso su E1 (profiling leave-one-out)
   e A/B isG14 / M20-1500. Il verdetto e' robusto allo stato di first-hour-slot (finding di
   search-strategy, non di modello), ma l'A/B formale e' OFF-vs-OFF.

## Raw (timing + candidati)

Stage timing (dal log app):
- A-CONSTRUCTION: 5s (-9557h/-1346333m/-53773s, 12427/12494 placed).
- B-TIMESLOT iter1 (3 cand + pickBest): **3746s (~62min)** -> pickBest -2226h/-841718m.
- B-TIMESLOT iter2 (3 cand + pickBest): **1559s (~26min)** -> pickBest -2226h/-832833m.
- B-TIMESLOT iter3 (3 cand + pickBest): **1295s (~22min)** -> pickBest -2226h/-830015m.
- B-ROOM: **605s (~10min)** -> finale **-120h/-973892m/18337s**.
- Totale: 06:09 -> 08:10 = **~121min**.

Candidati (hard/medium/soft, tutti cluster hard -2226/-2227):
- iter1: -2227/-835474, -2226/-841949, -2226/-841718 -> pick -2226/-841718
- iter2: -2226/-834267, -2226/-832833, -2226/-833867 -> pick -2226/-832833
- iter3: -2226/-830065, -2226/-830015, -2226/-830065 -> pick -2226/-830015

## KPI (vs arm A)
| metrica | portfolio N=3 | arm A (control) |
|---|---|---|
| finale hard | -120 | -121 |
| finale medium | -973892 | -964300 |
| full-day | 89,1% | 89,3% |
| ricorrenza UNP | 73,9% | 75,8% |
| buchi H17 | 0 | 0 |
| day-start late | 18 (G11 ven 16) | (baseline OFF, idem) |
| wall-clock | ~121min | ~46min |
| heap | -Xmx4g | -Xmx2g |

## Omogeneita' dei default (annotazione richiesta)
Questo run e' sull'OLD baseline (first-hour-slot OFF, JAR pre-adozione 401b5cac). Da
401b5cac first-hour-slot e' ADOTTATO default ON: i confronti futuri sono omogenei sui
default (ON), e un braccio "OFF baseline" richiede `-Dplanner.construction.first-hour-slot.enabled=false`.
Il verdetto "portfolio non aiuta" e' finding di search-strategy (robusto a first-hour-slot),
ma l'A/B formale qui e' OFF-vs-OFF.
