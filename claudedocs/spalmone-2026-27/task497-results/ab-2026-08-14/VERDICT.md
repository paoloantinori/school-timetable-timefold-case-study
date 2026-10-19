# VERDICT A/B TASK-497 (M20 pool-cap, forma b) - hard 0 tenuto, medium -63k DENTRO banda varianza, penalta' unreachable G08 eliminata (laptop, 2026-08-15)

> laptop MISURA, la ratifica e' di maintainer/Fedora. Prototipo f28f37de, flag
> planner.constraints.m20-cap-pool.enabled DEFAULT OFF in ogni caso. Il gate del mandato:
> DIREZIONE (medium miglior o neutro) + hard 0 (binario) + KPI in banda.

## Setup (braccia appaiate)

- HEAD bfad98df, JAR rebuildato e verificato (metodi TASK-497 e banner nel constant pool).
- Driver: nuovo `scripts/benchmark/ab-task497-m20-poolcap.sh` (thin wrapper, un braccio per
  `baseline-golden.sh`). Identico protocollo nelle due braccia: source=golden DEFAULT,
  staged, multi-start DEFAULT ON, MT-LS DEFAULT ON. UNICA differenza: il -D.
  - CONTROL ab-off-run1: nessun -D (flag OFF: ratio-cap TASK-461 live = produzione).
  - TREATMENT ab-on-run1: `-Dplanner.constraints.m20-cap-pool.enabled=true`.
- Banner verificati per braccio: control `armed=false`, treatment `armed=true`, entrambi
  `3 group(s) over-capacity` (G04, G08, G09 attesi); ratio identico
  ({4=0.898, 8=0.940, 9=0.994}) in entrambe.
- Stream score preservati: `score-stream.txt` per braccio (copia dell'app.log, il
  gitignore `*.log` li mangerebbe).

## Risultati

| metric | CONTROL (ratio) | TREATMENT (pool-cap) | giudizio |
|--------|-----------------|----------------------|----------|
| **hard** | **0** | **0** | gate BINARIO superato |
| medium (score.txt) | -1.212.396 | -1.275.447 | -63.051 |
| medium (analysis.json re-score) | -1.643.206 | -1.705.552 | -62.346 |
| wall | 94 min (2 tentativi) | 105 min (2 tentativi) | pari |
| full-day (KPI std vs quota RAW) | 87,3% | 87,0% | in banda |
| ricorrenza unpinned | 76,0% | 76,0% | in banda |
| buchi H17 interni | 0 | 0 | in banda |
| day-start anchored | 100% | 100% | in banda |

Nota canale: analysis.json re-scora ~431k sotto score.txt ENTRAMBE le braccia (offset
costante, re-score post-reload). Il delta fra braccia e' stabile sui due canali
(-63.051 vs -62.346): confronto A/B valido.

## Il -63k e' DENTRO la banda di varianza census

Ricalcolato dai raw n=19 committati (grasp-seed-1..19): medium min -1.492.888,
max -1.145.270, |delta| dal mean con min 2k / mediana 61k / max 238k. Il delta A/B
(-63k) e' alla MEDIANA della variabilita' single-run dello STESSO solver (census
single-construction). Un singolo run per braccio non distingue questo delta dal rumore:
per questo il run2 appaiato (Filone A del mandato) e' giusto e parte stanotte.

## Decomposizione (analysis.json)

| constraint | OFF | ON | delta |
|------------|-----|-----|-------|
| M20 Daily quota fill | -64.000 (49 match) | -83.500 (57) | -19.500 |
| M20.2 Empty expected day | -165.000 (18) | -191.000 (19) | -26.000 |
| M33 Surplus | -19.100 | -20.100 | -1.000 |
| M15 (varianza nota) | -134.400 | -148.800 | -14.400 |
| famiglia M20 totale | -229.000 | -274.500 | -45.500 |

Il peggioramento e' TUTTO nella famiglia day-fill; M33 piatto (nessuna spinta a
sovra-riempire). Il resto del -63k e' M15/varianza.

## DOMANDA DECISIVA: la penalta' unreachable e' sparita col flag ON?

Risposta dal ricalcolo per-gruppo sui export (popolazione del vincolo: lezioni reali
non-pseudo per (gruppo, data); target = quello del regime stesso):

| gruppo | pool | collocato (entrambe) | target OFF (ratio) | target ON (pool) | deficit vs proprio target OFF->ON |
|--------|------|----------------------|--------------------|------------------|-----------------------------------|
| G04 | 750 | 750 | 691 (ratio SOTTENDE 59h) | 750 esatto | 19h/6gg -> 42h/18gg |
| G08 | 626 | 626 | 666 (floor +40h NON collocabile) | 626 esatto | 59h/15gg -> 56h/12gg |
| G09 | 666 | 666 | 670 | 666 esatto | 9h/8gg -> 19h/12gg |

1. **G08: SI', sparita.** Il ratio non tagliava NESSUN giorno (6x0.94=5.64 -> 6):
   target 666 contro pool 626 = 40h MAI collocabili, floor permanente. Col flag ON il
   target e' 626: il residuo 56h e' deficit REALE di spreading (giorni non perfettamente
   riempiti), non impossibile. E' l'unico dei tre gruppi dove il numero giusto e'
   davvero raggiunto: la sanita' del target e' ripristinata.
2. **G04: nessuna penalta' unreachable prima NE dopo.** Il ratio qui tagliava TROPPO
   (691 < pool 750): il control NON pagava penalta' impossibile ma nascondeva 59h di
   obbligo. Il flag ON chiede 750 (l'obbligo vero): il solver paga 42h di deficit
   REALE (dove mette le ore contro il piano). Il medium sale per onesta' del metro,
   non per orario peggiore. Nota: il golden stesso avrebbe {1h: 90 giorni} di deficit
   con questo metro (domanda A a referente, giorni corti voluti?, gia' registrata).
3. **G09: neutro** (gap 4h, effetto piccolo).

Conclusione meccanica: il cap fa ESATTAMENTE cio' che il modello chiede (somma target =
pool per costruzione, verificato 691->750, 666->626, 670->666). La penalta' unreachable
in senso stretto esisteva SOLO su G08 (e su G04 pre-TASK-461) ed e' sparita dove
esisteva. Il -63k totale e' la somma di: ask piu' alta su G04 (+59h di target, deficit
reale esposto), M20.2 coupling, e varianza single-run.

## KPI di guardia: TUTTI in banda, TUTTI piatti

full-day 87,3 vs 87,0 / ricorrenza 76,0 vs 76,0 / buchi 0 vs 0 / day-start 100 vs 100.
La qualita' dell'orario e' IDENTICA fra le braccia: cambia solo quanto il vincolo chiede.

## Raccomandazione per maintainer (misura, non adozione)

1. hard 0 e KPI: il prototipo e' SICURO (nessun danno orario).
2. Il -63k e' dentro la varianza census ma DIREZIONE-formalmente negativo: il run2
   appaiato (in corso, Filone A) decide se il delta e' sistematico o rumore. Se run2
   mostra delta concorde e simile, il costo e' reale ma e' il prezzo della sincerita'
   del target (punto 2 sopra) e la decisione e' semantica (accettare l'ask piena G04),
   non di ricerca.
3. Il caso G08 da solo (floor 40h eliminato) vale ~40k di medium "impossibile" che il
   control paga OGNI run senza poterlo mai colmare: se si vuole un compromesso, un cap
   ibrido (pool-cap SOLO dove il ratio sottende o non taglia, mantendo il ratio dove
   approssima bene) e' una variabile di design aperta, da valutare solo dopo run2.

Il flag resta DEFAULT OFF. Nessuna adozione stasera.

## Artefatti

- `ab-off-run1/`, `ab-on-run1/`: capture completa (score, analysis, export, phases,
  lessons, KPI battery, HTML) + `score-stream.txt` preservato.
- Driver: `scripts/benchmark/ab-task497-m20-poolcap.sh` (committed col filone A).
- Ricalcolo per-gruppo: riproducibile dai export.json.gz di questa directory.


## TIEBREAK RUN2 (aggiunto 2026-08-15 ore 04:45): DIREZIONE CONCORDE, magnitudo al rumore

Stesso protocollo appaiato (RUN_SUFFIX=run2 del driver, braccia fresche). Risultati:

| arm | run1 | run2 | delta within |
|-----|------|------|--------------|
| OFF (ratio) | 0hard/-1.212.396 | 0hard/-1.245.155 | 32.759 |
| ON (pool-cap) | 0hard/-1.275.447 | 0hard/-1.292.301 | 16.854 |
| delta fra braccia | -63.051 | -47.146 | |

- hard 0 su tutte e 4 le run (gate binario 4/4).
- DIREZIONE concorde 2/2 (ON peggiora in entrambe le coppie), magnitudo media ~55k con
  rumore within-braccio 17-33k: il costo e' PROBABILMENTE reale ma piccolo rispetto alla
  variabilita' single-run census (mediana 61k).
- Famiglia M20 (analysis.json): OFF -229,0k/-265,5k, ON -274,5k/-296,5k. Delta medie
  -38k, spread within OFF 36,5k: il costo vive dove atteso.
- Meccanismo per-gruppo REPLICATO su run2 (identico a run1): G04 19->42h (target sale
  691->750, deficit REALE esposto), G08 60->56h (floor impossibile ELIMINATO),
  G09 25->18h. Entrambe le braccia collocano esattamente il pool (750/626/666) in
  tutti e 4 i run: la differenza e' SOLO il metro.

## Verdetto tiebreak (per maintainer)

1. Il cap funziona come progettato, replicato 2/2: somma target = pool, unreachable
   eliminato dove esisteva (G08), ask piena dove il ratio sottendeva (G04).
2. Il costo medium (~50-60k, direzione stabile) e' la somma di ask piu' alta su G04
   e coupling M20.2: e' un costo del METRO, non dell'orario (KPI piatti ovunque, 4/4
   hard 0, collocato = pool identico).
3. Decisione rimasta SEMANTICA: (a) accettare l'ask piena (il golden paga lo stesso
   prezzo: 90 giorni a -1h su G04) e adottare col tempo, oppure (b) cap ibrido
   (pool-cap solo dove il ratio fallisce: G08 si', G04 resta ratio) per medium
   neutro. In entrambi i casi la decisione va presa con la risposta di referente alla
   domanda A (giorni corti G04 voluti?).
4. Il flag resta DEFAULT OFF. Raw run2: ab-off-run2/, ab-on-run2/ (stream preservati).
