# Research Report: approcci per il deficit di G04 del venerdi

**Date**: 2026-07-30
**Depth**: exhaustive
**Confidence**: HIGH sui fatti empirici (ricalcolati sui raw), MEDIUM sulla graduatoria degli approcci (nessuno ancora testato)

## Executive Summary

La domanda "come riempiamo il venerdi di G04" e' **mal posta**, e la ricerca lo ha
scoperto prima di arrivare alla letteratura. Le ore mancanti **non esistono**: G04 ha 750
entita' Lesson, tutte collocate, contro le 835 che il foglio calendario si aspetta, e **il
golden ne colloca esattamente 750 anche lui**. Il divario di 85 ore e' fra due fogli di
input, non fra noi e la soluzione della scuola. Cio' che ci separa davvero dal golden e' la
**FORMA** con cui distribuiamo la stessa penuria: il golden toglie un'ora a 90 giornate e
non scende MAI a -2, noi teniamo piu' giornate perfette ma ne sfondiamo 22 a -2 o -3.
La leva giusta quindi non e' cercare ore che non ci sono, e' rendere il costo della penuria
piu' fortemente convesso oppure guidare la ricerca verso le giornate profonde. Il
meccanismo convesso **esiste gia'** in M20, ma il suo margine (1.500 punti) non domina i
vincoli con cui compete (M15 circa 1.200, M32 fino a 500 per lezione, M22 1.000 al giorno).

## Findings

### 1. Le ore mancanti non esistono (HIGH, ricalcolato sui raw)

| fonte | ore G04 |
|---|---|
| entita' Lesson nell'export 30d (docente reale) | **750**, di cui 0 senza timeslot |
| incarichi.csv | **750** |
| calendario_fc.csv (aula+lab) | **835** |
| golden (lessons.csv, docenti reali) | **750** |

Il solver colloca tutto cio' che esiste. Il divario di 85 ore e' una **incoerenza fra due
fogli autoritativi**, e il golden la subisce identica. Stessa famiglia degli artefatti gia'
chiusi (sabato/Fasullo, stage): stiamo misurando contro un bersaglio irraggiungibile.
Ne consegue che lo SPIKE 4 ("ricerca, non capacita'") e' vero solo localmente: sul singolo
venerdi campione i docenti sono liberi con ore residue, ma a livello d'anno non esiste
nessuna ora ulteriore da collocare.

### 2. Il difetto vero e' la FORMA della penuria (HIGH)

Con le stesse 750 ore, distribuite diversamente:

| profondita' del deficit | RUN | GOLDEN |
|---|---|---|
| 0 (giornata piena) | 90 | 55 |
| -1h | 33 | **90** |
| -2h | **14** | **0** |
| -3h | **8** | **0** |

Per giorno della settimana il golden concentra la rinuncia su lunedi (-26) e giovedi (-29) e
tiene il **venerdi a zero** (165 su 165), mentre noi spalmiamo (-31 venerdi, -32 martedi).
La politica implicita della scuola e' "taglia un'ora a molte giornate, mai due"; la nostra e'
"tieni piu' giornate perfette, e svuotane alcune".

### 3. Il meccanismo convesso esiste gia', ma e' debole (HIGH)

`SchoolScheduleConstraintProvider.m20DeficitCost` e' gia' progressivo:
`costo(d) = 1000*d + 500*d*(d-1)/2`.

| deficit | costo |
|---|---|
| -1h | 1.000 |
| -2h | 2.500 |
| -3h | 4.500 |
| -4h | 7.000 |

Tre giornate a -1h costano 3.000, una a -3h ne costa 4.500: la preferenza per lo spalmato
c'e', ma vale **1.500 punti**, cioe' meno di una divisione di materia (M15 circa 1.200)
sommata a tre deviazioni di template (M32 fino a 500 per lezione), o di una giornata di M22
(1.000). Una mossa che spalmerebbe il deficit ma rompe template o adiacenza viene quindi
respinta.

### 4. Cosa dice la teoria (MEDIUM, una fonte primaria letta)

Il quadro formale e' il **minimum-norm load balancing** (arXiv 1905.00044): minimizzare la
SOMMA e' indifferente alla forma (una macchina sovraccarica e molte scariche danno lo stesso
valore), mentre minimizzare una norma simmetrica monotona, o un costo separabile convesso,
forza il bilanciamento. Il punto rilevante per noi e' testuale: *"a weakly convex function
may still allow modest imbalance, while high strong convexity drives solutions toward
near-perfect evenness"*. La nostra curva e' debolmente convessa, e infatti tollera
esattamente lo sbilanciamento modesto che osserviamo.

### 5. Leve native disponibili nel motore (HIGH, documentazione ufficiale)

Dalla `move-selector-reference` di Timefold:
- **probabilistic selection**: `<entitySelector>` con `cacheType >= STEP`,
  `<selectionOrder>PROBABILISTIC</selectionOrder>` e `probabilityWeightFactoryClass`
  (`SelectionProbabilityWeightFactory.createProbabilityWeight`). E' il meccanismo nativo per
  **polarizzare la ricerca verso le entita' delle giornate in deficit**.
- **filtered selection**: `<filterClass>` con `SelectionFilter.accept()` per scartare mosse
  inutili (ne usiamo gia' uno, `CompositeMoveFilter`).
- **ruin and recreate**: `<ruinRecreateMoveSelector>` con `<minimumRuinedCount>` e
  `<maximumRuinedCount>`. Esiste ancora nel nostro `solverBenchmarkConfig`, era **inerte**, e
  TASK-420 lo tolse dalla produzione: perche' fosse inerte non e' mai stato spiegato.
- **fixedProbabilityWeight** per figlio dello `unionMoveSelector`: taratura a costo zero.

## Approcci, in ordine di rapporto valore su rischio

1. **Irrobustire la convessita' di M20** (`M20_PROGRESSION_STEP`, oggi 500). E' un cambio di
   una costante su un meccanismo gia' in produzione, colpisce esattamente il difetto (la
   forma) ed e' misurabile al checkpoint. Vincolo gia' dichiarato nel codice: l'aggregato di
   M20 non deve muoversi in modo significativo. Rischio: interagisce con M15, M32 e M22,
   quindi va misurato e non ragionato.
2. **Polarizzare la ricerca sulle giornate profonde** con `probabilityWeightFactoryClass`
   sull'entitySelector, peso proporzionale al deficit del giorno della lezione. Nativo, non
   tocca il modello, attacca il lato "la ricerca non ci prova". Richiede una classe piccola e
   la DoD piena.
3. **Ruin & recreate mirato** sulle giornate in deficit. Potenzialmente il piu' efficace,
   perche' ricostruisce un giorno intero invece di spostare una lezione, ma prima va capito
   **perche' era inerte**: riattivarlo senza quella spiegazione ripeterebbe l'errore.
4. **Riconciliare i due fogli di input** (calendario 835 contro incarichi 750). Non e' una
   cura del solver, ma e' la radice onesta: finche' il bersaglio e' irraggiungibile, ogni KPI
   di riempimento di G04 misura anche l'errore dei dati. Diventa una domanda per la scuola.
5. **Ribersagliare la metrica**: smettere di misurare G04 contro 835 e misurare la FORMA
   contro il golden (giornate a -1 contro giornate a -2 o peggio). E' gratis e rende visibile
   il progresso vero.

Scartato: qualunque leva mirata al "venerdi di G04" in quanto tale. Il venerdi non ha
niente di speciale, e' il golden che sceglie di tenerlo intero rinunciando altrove.

## Confidence Assessment

- **HIGH**: i quattro conteggi del punto 1 (ricalcolati da export, incarichi, calendario e
  golden), la tabella delle forme del punto 2, la formula e i valori di M20 (letti nel
  codice), le leve Timefold (documentazione ufficiale).
- **MEDIUM**: la graduatoria degli approcci. Nessuno e' stato testato; l'ordine riflette
  costo e rischio, non efficacia misurata. In particolare la voce 1 potrebbe non bastare da
  sola.
- **NON VERIFICATO**: perche' il ruin & recreate fosse inerte; se una convessita' piu' forte
  degradi ricorrenza o cadenza; se le altre 13 classi mostrino la stessa incoerenza fra
  calendario e incarichi (misura da fare, e cambierebbe la portata della voce 4).
- **Limite della parte esterna**: le ricerche web hanno prodotto molto rumore. Le fonti
  esterne effettivamente lette sono due; non ho gonfiato la bibliografia con riferimenti non
  aperti. Il paper ALNS per il curriculum-based course timetabling (10.1007/s10479-016-2151-2)
  e' dietro autenticazione e NON e' stato letto.

## Sources

1. Timefold Solver, *Move selector reference*: filtered, probabilistic e sorted selection,
   `ruinRecreateMoveSelector`.
   https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference (letto)
2. *Simpler and Better Algorithms for Minimum-Norm Load Balancing*, arXiv:1905.00044: somma
   indifferente alla forma contro norme e costi convessi. https://arxiv.org/pdf/1905.00044 (letto)
3. *Adaptive large neighborhood search for the curriculum-based course timetabling problem*,
   Ann Oper Res. https://link.springer.com/article/10.1007/s10479-016-2151-2 (NON letto, paywall)
4. Fonti primarie interne, ricalcolate per questo rapporto: `certification-2026-07-30d/`
   (export.json.gz, lessons.txt), `data/incarichi.csv`, `data/calendario_fc.csv`,
   `data/lessons.csv`, `SchoolScheduleConstraintProvider.m20DeficitCost`.
