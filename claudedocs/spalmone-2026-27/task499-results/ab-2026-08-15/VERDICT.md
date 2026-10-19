# VERDICT A/B TASK-499 (nearby template filter) - hard 0 entrambi, B-TIMESLOT wall -33%, medium migliore (laptop, 2026-08-15)

> laptop MISURA, ratifica di maintainer/Fedora. Flag planner.ls.nearby-template-filter.enabled
> DEFAULT OFF (opt-in prototype, TASK-499). Gate: hard 0, KPI in banda, WALL LS.

## Setup

- HEAD 7af82dee, JAR rebuildato. Driver `scripts/benchmark/ab-task499-nearby-filter.sh`
  (baseline-golden.sh per braccio). source=golden DEFAULT, multi-start ON, MT-LS ON,
  skip-doomed-room ON (ratificato) in ENTRAMBE le braccia: unica differenza il -D.
- Banner verificati: control `enabled=false`; treatment `enabled=true` + wiring
  confermato nel log: `armed (1 change-move selectors wired)` sul solver B-TIMESLOT
  (lo 0 della fase costruzione e' atteso: la F5 del review ha reso il guard esplicito).

## Risultati

| metric | CONTROL (OFF) | TREATMENT (ON) | giudizio |
|--------|---------------|----------------|---------|
| **hard finale** | **0** | **0** | gate BINARIO superato |
| medium / soft | -963.784 / +5.441 | **-957.730** / +6.375 | treatment MIGLIORE +6k |
| tentativi | 1 (good basin) | 1 (good basin) | parita' |
| **B-TIMESLOT wall** | **1034s** | **693s** | **-33% (la fase target)** |
| B-ROOM wall | 603s | 603s | identica (filtro non wirato lì, atteso) |
| **wall totale** | **30 min** | **24 min** | **-20%** |
| move evaluation speed | 104.4k/sec | 102.0k/sec | simile (vedi lettura) |
| full-day | 89,7% | 90,3% | in banda (meglio) |
| ricorrenza unpinned | 75,7% | 75,0% | in banda (-0,7pp) |
| buchi H17 | 0 | 0 | in banda |

## Lettura

1. **Il meccanismo funziona come disegnato**: il filtro riduce le mosse GENERATE (le
   grado-3 non arrivano al forager), non il costo della singola valutazione. Coerente
   con move-eval-speed simile (~100k/sec entrambe: ogni valutazione superstite costa
   uguale) e wall della fase molto inferiore: lo stesso budget temporale viene speso
   solo su mosse che M32 non avrebbe scartato.
2. **-33% sulla fase target** e' il numero principale. E' anche PARI AL PLUS della
   stessa misura del LS multi-thread ratificato (-25% su tutto il ciclo): questo
   prototipo agisce su una sola fase (B-TIMESLOT) e porta -33% su di essa, -20% sul
   ciclo intero. I due effetti si COMpongono (MT-LS era attivo in entrambe le braccia).
3. **Nessun danno di qualita'**: hard 0, KPI in banda, medium migliore. La paura del
   design (restringere le mosse di fuga) non si materializza su good basin: keep 0.20
   lascia abbastanza varietà.
4. **ONESTA' statistica**: n=1 per braccio. La varianza within-arm del wall B-TIMESLOT
   e' nota dalle cronologie (565-3096s a seconda del basin): entrambi i run hanno
   chiuso al tentativo 1 sullo STESSO basin deterministico (golden), quindi il confronto
   e' appaiato al meglio possibile senza multi-run, ma un run2 di conferma resta la
   prossima misura naturale prima della ratifica.
5. **Scope dichiarato (F6 del review)**: il filtro agisce SOLO sullo stream change
   (peso 5/11 dell'union): swap (3) e pillar (2/1) restano non filtrati. Il -33% arriva
   quindi da 5/11 dello stream: estendere il grading agli swap/pillar e' la prossima
   leva ovvia se si vuole di piu'.

## DoD

- Design doc: claudedocs/design/nearby-selection-community.md (reference Enterprise,
  forma community, rischio mosse di fuga, disclaimer ordering-vs-filter).
- /simplify + /code-review high: fatti in-sessione con 5 finding applicati (parse-clamp
  condiviso, esenzione pseudo/null-teacher M32 specchiata, parametro morto, banner col
  clamp reale, guard 0-wired) + 1 bug self-review (wiring post-build no-op) chiuso.
- Test: NearbyTemplateMoveFilterTest 2/2 (grade table + bounds) + NearbyTemplateWiringTest
  1/1 (wiring conta >=1 su XML produzione). Suite adiacente verde (22 test).
- full test.sh: ESEGUITO post-A/B, EXIT 0 (1007 test, 0 failure, 23 skip preesistenti).

## Raccomandazione per maintainer

Il prototipo e' promettente oltre l'atteso (wall -20% ciclo, nessun costo di qualita',
medium migliore). Prossimi passi naturali prima della ratifica: (a) run2 di conferma
appaiato, (b) test.sh full, (c) misura su un BAD seed (source=input grasp) dove le mosse
di fuga contano: se il filtro non degrada nemmeno li', il caso e' solido. Il flag resta
DEFAULT OFF. Raw in questa directory (score-stream.txt preservati).


## RUN2 di conferma (2026-08-15 sera): qualita' CONFERMATA, wall NON confermato

Stesso protocollo appaiato (RUN_SUFFIX=run2), stesso basin deterministico.

| metric | run1 OFF | run1 ON | run2 OFF | run2 ON |
|--------|----------|---------|----------|---------|
| hard finale | 0 | 0 | 0 | 0 |
| medium | -963.784 | **-957.730** | -968.446 | **-962.780** |
| wall totale | 30 min | **24 min** | **29 min** | 37 min |
| B-TIMESLOT | 1034s (1 iter) | 693s (1 iter) | 984s (1 iter) | 617+673+140s (3 iter) |
| full-day | 89,7% | 90,3% | (in banda) | (in banda) |

1. **Qualita': DIREZIONE CONCORDE 2/2.** hard 0 in tutte e 4 le run; medium migliore
   col filtro in entrambe le coppie (+6k, +5k); KPI in banda.
2. **Wall: NON replicato.** Run1 ON -33% su B-TIMESLOT; run2 ON +45% (1430s su 3
   iterazioni di demotion vs 984s su 1). Il filtro cambia la traiettoria del loop
   B-TIMESLOT/demotion: a volte chiude prima (run1), a volte aggiunge iterazioni
   (run2). Il -33% del run1 era VARIANZA DI TRAIETTORIA, non un guadagno stabile:
   il claim wall va RITIRATO dal verdetto pre-ratifica.
3. Conclusione onesta: il filtro e' SICURO per qualita' (4/4 run hard 0, medium
   migliore 2/2) ma NON e' una leva di wall dimostrata.

## BAD-SEED ESCAPE TEST (seed 17, firma H17 G13, multi-start OFF)

| arm | hard | firma | full-day | B-TIMESLOT |
|-----|------|-------|----------|------------|
| filtro OFF | -2 (H17) | buchi G13 mercoledi' | 81,6% | 985s |
| filtro ON | -6 (H17) | identica firma | 81,3% | 1135s |

1. **Nessuna fuga in NESSUN braccio**: entrambi restano nel bad basin con la stessa
   firma H17. Il filtro non apre ne' chiude la fuga: coerente col census (questo seed
   non e' MAI sfuggito a costruzione singola: -7, -6, e oggi -2/-6).
2. **-2 vs -6 e' DENTRO la varianza storica del seed** (banda osservata -2..-7 su 4
   run): il braccio OFF ha fatto il MIGLIOR risultato storico del seed, l'ON e' al
   valore modale. Non si puo' affermare che il filtro peggiori il bad basin, ne' che
   lo aiuti: stesso esito di classe.
3. **In produzione la fuga non dipende da mosse lontane**: multi-start ON (ratificato)
   riparte con altra costruzione e skip-doomed-room (ratificato) salta la rifinitura
   del condannato: la via di fuga e' la diversificazione, non la mossa lontana nel
   basin condannato. La preoccupazione del design e' chiusa in entrambi i sensi:
   il filtro non serve alla fuga e non la blocca (la valvola 0.20 non ha danneggiato
   nemmeno il recupero parziale: firma e classe di gravita' invariate).

## VERDETTO FINALE per maintainer (run1 + run2 + bad-seed)

- **SICURO ma non utile come leva wall**: hard 0 su 4/4 run good-basin, medium
  migliore 2/2, KPI sempre in banda, bad-seed invariato nella firma e nella classe.
- **Il claim wall -33% del run1 NON e' replicato** (run2: +45% per piu' iterazioni di
  demotion): la traiettoria cambia in entrambe le direzioni.
- **Raccomandazione: NON adottare** (il flag resta OFF). Il problema che il filtro
  affronta (valutazioni sprecate su mosse grado-3) resta reale, ma questa forma non
  lo converte in wall stabile: il collo si sposta sul loop di demotion. Le alternative
  naturali se si vuole riprovarci: (a) filtro anche su swap/pillar (5/11 dello stream
  oggi), (b) bias invece di filtro (semantica Enterprise vera, richiede ValueSelector
  custom), (c) niente: la qualita' non guadagna abbastanza da sola.
- Raw: badseed-2026-08-15/ (braccia con score-stream preservati).
