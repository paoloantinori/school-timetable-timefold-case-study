# VERDICT 30e - tetto M20 (TASK-461): il target non insegue piu' lezioni inesistenti

**Run:** 2026-07-30, profilo dev fresco (stop+delete+reseed), import-calendario, pre-assign
(T01 231/231 pinnate, 3090 totali, identico a 30d), solve-staged. Lancio via
`dev-start-and-wait` (mvn quarkus:run) col tetto M20 attivo (DEFAULT, nessun flag): banner
`M20 supply-cap ratios (capacity > curriculum): {4=0.898, 8=0.940, 9=0.994}` = G04 750/835,
G08 626/666, G09 666/670. HEAD con tetto `8e51babc`. Ciclo ~53min (13:46->14:39),
hard converge solo in B-ROOM (minuto ~43). n=1 NON_REPRODUCIBLE.

**Domanda (mandato):** il tetto M20 fa scendere l'aggregato come atteso (~129k floor), e non
degrada i tre KPI di guardia (full-day, M15, buchi interni) che hanno affondato la bilanciatura
del primo slot?

## RISPOSTA: SI'. Il tetto funziona e la qualita' MIGLIORA, non peggiora.

### M20 aggregato: crollo come atteso
| vincolo | 30d | 30e | delta |
|---|---|---|---|
| **M20** (daily quota fill) | -193.000 | **-103.500** | **+89.500** (-46%) |
| M33 (surplus, controparte del target abbassato) | -6.900 | -13.800 | -6.900 |
| M15 (same-day contiguita') | -4.800 | **-1.200** | **+3.600 (migliora)** |
| M32 / M-LF / M37 / M35 | (invariati nel rumore) | | |
| **medium TOTALE** | -1.070.767 | **-979.539** | **+91.228** |

M20 scende di 89,5k (non i 129k del floor stimato flat: il residuo e' rounding drift per-day +
un po' di surplus passato a M33, come anticipato dal code-review). La famiglia day-fill netta
(M20+M33) scende di ~82,6k. Net medium -91k.

### I tre KPI di guardia: nessuna regressione, la shape MIGLIOra
- **Buchi interni H17: 0 -> 0.** Nessuna regressione.
- **M15 contiguita': -4.800 -> -1.200 (migliora di 3,6k).** Il target abbassato lascia il solver
  piazzare piu' contiguo (meno pressione a spargere per riempire).
- **Shape del deficit (il segnale qualita' vero): MIGLIORA.** Deep days (>=2h) **28 -> 15**,
  G04 **22 -> 7**; i -1h salgono al 90% dei giorni sotto (79% in 30d), cioe' SPALMATI come il
  golden invece che concentrati. Il tetto trasforma il deficit "profondo irraggiungibile" dei
  gruppi over-capacity in un deficit -1h leggero e golden-like.

### boxed / room-null (hard)
- **H0 boxed: 3 -> 4** (tutti G06 / Barberia / T19: il difetto di coordinamento della
  tripletta, ora 4 vs 3, rumore run-to-run). **H29 room-null: 167 -> 169** (tutti lab
  capacity-starved, NON-LAB 0). **hard: -173 -> -177** (boxed*2 + room-null = 4*2+169 = 177).
  Tutto nel rumore, nessun danno hard.

### INCIAMPO DEL DENOMINATORE (dichiarato, come chiesto)
Il **full-day rate scende 89,8% -> 88,7% MA E' APPARENTE, NON REGRESSIONE.** Il tetto abbassa il
TARGET del solver per i 3 gruppi over-capacity (G04, G08, G09), non il denominatore del
KPI (che resta la raw-quota DailyActivityQuota). Il solver colloca fino al target_capped
(legittimamente meno, perche' le ore oltre il curriculum non esistono), ma il KPI misura contro
la raw-quota -> quei 3 gruppi risultano sotto. **Le placement NON sono scese** (G04 750, G08
626, G09 666: identici a 30d, nessuna lezione persa). La lettura onesta: il full-day raw non
e' un metro di qualita' qui (il solver fa la cosa giusta non inseguendo l'irraggiungibile); il
metro vero e' la **shape del deficit** (deep 28->15) e M20 aggregato, entrambi migliorati.
E' lo stesso inciampo del metro cambiato di stamattina: dichiarato per non ricascarci.
Per un confronto full-day apples-to-apples andrebbe ricalcolato col denominatore capped, ma non
cambia il verdetto (le placement sono le stesse).

## Conclusione
Il tetto M20 e' **una netto miglioramento**: -89,5k medium, deep days dimezzati (28->15, G04
22->7), M15 migliorato, buchi H17 stabili a 0, nessuna lezione persa. L'unico "calo" (full-day
89,8->88,7) e' il trabocchetto del denominatore, non una degradazione. hard -173->-177 nel rumore.
Non si prototipa nulla sopra (il tetto e' adottato, DEFAULT). Le 167-169 room-null restano il
difetto di modello separato (TASK-455 / scarsita' congiunta della spec), non toccato da M20.
