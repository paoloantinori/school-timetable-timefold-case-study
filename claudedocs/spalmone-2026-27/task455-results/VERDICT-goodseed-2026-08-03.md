# VERDICT TASK-455 good-seed A/B, 2026-08-03

> Risponde alla domanda bloccante per la ratifica di M-TC (formulata da me nell'handoff seed-6 e
> confermata da Fedora): M-TC+esenzione su un GOOD SEED costa ~nullo (si accende solo quando serve)
> oppure ha un costo fisso (perturba la LS anche sui bacini buoni)? Raw: `ab-mtc-goodseed-{off,on}/`.

## Setup

Due run, STESSO good basin deterministico (GRASP off = il path production, multi-start forzato OFF
via `-Dplanner.multistart.enabled=false` cosi' e' un singolo solve, source=input). Unica variabile:
`-Dplanner.tag-capacity.enabled`. Banner verificato per run (OFF enabled=false, ON enabled=true).
Good seed = il baseline deterministico (baseline-grasp-off e' 0-hard). Nessuno sweep di peso
(lezione M-TR). Script: `scripts/benchmark/task455-mtc-goodseed.sh`.

## Predizione dichiarata PRIMA del risultato (ex-ante)

Sul seed-6 M-TC+esenzione era a 0 match (inerte) e il -94k residual era dominato dal displacement
del relief meccanica (M20 -53k, M15 -31k), che su un good seed NON c'e' (niente room-null da
curare). Predizione: **costo quality ~nullo**, medium ON entro |50k| di OFF, hard 0 entrambi,
M-TC ~0 match sull'ON. Falsificabile: se medium ON peggiora >50k vs OFF, e' perturbazione fissa.

## Esito

| KPI | OFF (M-TC off) | ON (M-TC on + esenzione) | Delta |
|-----|----------------|--------------------------|-------|
| hard | 0 | 0 | pari |
| medium | -1.033.738 | -1.057.698 | **-23.960 (< \|50k\|)** |
| full-day | 87,2% | 87,6% | +0,4pp (ON meglio) |
| ricorrenza unpinned | 74,1% | 74,4% | +0,3pp (ON meglio) |
| holes OK-pieno / fine-anticipata | 1755 / 184 | 1766 / 180 | +11 / -4 (ON meglio) |
| **wall-clock** | 1.230s | **2.435s** | **~2x** |
| M-TC match | n/a | **0 (inerte)** | |

Predizione **CONFIRMATA** sul quality: medium -24k (nella varianza LS, < |50k|), tutti i KPI di
guardia ~OFF o leggermente migliori, hard 0 entrambi, M-TC inerte (0 match: il good basin non ha
overflow non-praticati da penalizzare). Il -94k del seed-6 era il **costo del cure** (relief della
meccanica), NON perturbazione fissa: sui bacini buoni M-TC e' inerte e il costo quality e' ~nullo.

## Trovato un secondo costo: wall-clock ~2x (NON nella predizione, da segnalare)

ON ha impiegato 2.435s vs 1.230s dell'OFF (~2x). La B-TIMESLOT-1 dell'ON e' corsa ~28 min vs ~15
min dell'OFF: con M-TC nel constraint set (anche inerte a fine run, 0 match), la LS trova piu'
medium da inseguire durante la ricerca e l'unimproved timer si resetta piu' volte -> B-TIMESLOT
piu' lunga. E' un costo OPERATIVO reale (solve 2x piu' lungo anche quando M-TC non serve), non un
costo di quality. Singolo run: non isolato dalla varianza wall, ma la B-TIMESLOT raddoppiata e'
strutturale (effetto di M-TC sul LS), non rumore.

## Classificazione

Esito MISTO, onesto:
- **Quality cost: ~nullo** (adoptable pulito sul piano del valore dell'orario). M-TC+esenzione non
  peggiora medium/full-day/ricorrenza/holes sui bacini buoni, e cura i bacini cattivi (seed-6
  room-null) al costo onesto del cure.
- **Wall-clock cost: ~2x** (solve 40 min vs 20 min). Da pesare: per un solve one-shot/notturno e'
  tollerabile; raddoppia il compute.

## Raccomandazione (maintainer decide, io non applico)

Il criterio del mandato (quality ~nullo -> prepara patch di ratifica) e' **soddisfatto**. Patch di
ratifica preparata (DEFAULT ON, opt-out, docs) ma NON applicata, pronta sul branch
`ratification/m-tc-default-on`. La decisione di accendere il DEFAULT ora include il trade-off
wall-clock 2x, che maintainer valuta: M-TC complementare al multi-start (cura il bacino room-tier
"quasi buono", il multi-start aggira quello H17 strutturale), a costo di un solve 2x piu' lungo.

## Nota tecnica per la ratifica

Se si vuole il beneficio (cura room-null sui bacini cattivi) SENZA il costo wall-clock fisso sui
bacini buoni, l'alternativa e' NON accendere M-TC di default e affidarsi al multi-start (che
ritenta su un buon bacino). M-TC DEFAULT-ON ha senso solo se si vuole la cura DETERMINISTICA del
room-null (non probabilistica come il multi-start). E' una scelta di maintainer, non di misura.
