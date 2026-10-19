# VERDICT 30g - TASK-455 v1 (Servizio stilistico de-tag G14 + G12): conferma

**Run:** 2026-07-30, profilo dev fresco (stop+delete+reseed con override
subject_overrides.csv v1), import-calendario, pre-assign (T01 231/231,
3090 totali, identico a 30e), solve-staged. M-TC SPENTO (un fattore alla volta).
HEAD `1b73efa9` (v1) + `7c4f6115`/`8b87028c` (doc). Ciclo ~58min (17:06->18:04),
hard converge in B-ROOM (-2112 -> -125 in ~4min). n=1 NON_REPRODUCIBLE.

**Domanda (mandato):** v1 chiude le room-null come al checkpoint (~125) senza
degradare i 3 KPI di guardia?

## RISPOSTA: SI', e meglio del checkpoint. 50 room-null chiuse, KPI di guardia tengono.

### Hard: le room-null crollano
| metrica | 30e | 30g | delta |
|---|---|---|---|
| **H29 room-null** | 169 | **119** | **-50 (chiuse)** |
| H0 boxed | 4 | 3 | -1 |
| **hard totale** | -177 | **-125** | **+52** |

Le 50 room-null chiuse BATTONO il checkpoint (che prevedeva 125 room-null = 44
chiuse, a placement fisso). Il solve completo arriva a 119 (50 chiuse) perche'
puo' SPARGERE le lezioni su altri slot, cosa che il checkpoint (solo fase aule,
placement fisso) non poteva fare. Deviazione FAVOREVOLE e spiegata.

### I 3 KPI di guardia: tengono tutti
- **Buchi interni H17: 0 -> 0.** Nessuna regressione.
- **Ricorrenza UNPINNED: 74,4% -> 74,5%.** Stabile.
- **Full-day ex-G14: 88,7% -> 88,1%** (-0,6pp, rumore run-to-run). La SHAPE del
  deficit MIGLIORA: giorni sotto 149 -> 159 ma profondi (>=2h) 15 -> 8 e i -1h
  salgono al 95% (134 -> 151): piu' SPALMATI come il golden, meno profondi.

### Il prezzo (medium): M15 e' il costo visibile, non previsto dal checkpoint
| vincolo | 30e | 30g | delta |
|---|---|---|---|
| **M15** same-day contiguita' | -1.200 (1) | **-14.400 (6)** | **-13.200 (+5 split)** |
| M37 keep-room | -31.500 (315) | -34.200 (342) | -2.700 (+27 cambi aula) |
| M21 max 2h/materia | -184 (181) | -193 (185) | -9 (+4, l'effetto visto dalla DoD) |
| M-LF non-lab in lab | -113.000 (113) | -113.000 (113) | 0 (invariato) |
| M20 daily fill | -103.500 | -103.000 | +500 |
| **medium TOTALE** | -979.539 | **-995.929** | **-16.390** |
| soft | 15.244 | 18.175 | +2.931 |

Il costo e' M15 (+13.200): le ore di Servizio stilistico de-taggate (ora non-lab)
vengono contate da M15 e il run le SPEZZA nella giornata (5 split nuovi). M37
(+2.700) e M21 (+9, l'effetto che la DoD aveva gia' visto sul golden) sono
minori. Nessuno di questi e' visibile al checkpoint (fase aule sola): e' il
perche' serve il solve di conferma.

## Lettura

v1 scambia 50 room-null HARD (lezioni fisicamente senza aula) per ~16k medium
(per lo piu' M15: il run spezza il Servizio stilistico ora non-lab). Hard >> medium,
e le 50 room-null erano un difetto fisico (l'orario aveva 169 lezioni senza stanza);
chiuderle e' un guadagno reale. I KPI di guardia tengono (buchi 0, ricorrenza 74,5%,
full-day nella forma migliore). Il prezzo M15 e' il dato da portare a maintainer: e'
accettabile come scambio hard-per-medium, ma e' il costo esplicito del de-tag, e il
checkpoint non lo vedeva.

Conferma ilmetro: checkpoint per scartare in fretta + misurare, solve per la
conferma prima di adottare. Qui il checkpoint aveva detto "v1 pulita" (solo fase
aule); il solve rivela che pulita sulle room-null si, ma a costo M15. La decisione
di adozione resta a maintainer sui numeri.

## Conclusione

v1 e' confermata sana: -52 hard (50 room-null chiuse), KPI di guardia stabili,
shape del deficit migliore. Costo: -16k medium (M15 principalmente). Non adottato
automaticamente: la decisione e' di maintainer. Se adottata, il passo successivo e'
misurare il vincolo M-TC SOPRA v1 (prima al checkpoint sull'export 30g, poi eventuale
solve), un fattore alla volta.
