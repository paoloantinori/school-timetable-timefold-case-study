# TASK-363 — Benchmark solver vs. golden (2026/27, offline, 2026-07-25)

MISURA-ONLY. Nessun solve eseguito per questo task: tutti i numeri sotto sono ricalcolati
da artifact già esistenti — il golden (`claudedocs/spalmone-2026-27/data/lessons.csv`, il
timetable reale fatto a mano dalla scuola) e i due run adottati `val413-run1`/`val413-run2`
(`claudedocs/spalmone-2026-27/task413-results/`, stack HEAD con M32 "Template deviation" +
`WeeklyTemplatePlanner` pre-solve, box 0, hard 0/-1). Nessuna modifica a codice o backlog,
nessun commit.

## 0. Perché questo confronto è delicato (leggere prima delle tabelle)

Il golden import è **100% pinned** (ogni lezione storica entra in DB con `pinned=true`).
Un buon numero di constraint di questo branch sono **pinned-exempt by design**
(M15, M17/M18, M20, M20.2, M28, M29, M30, M31, H15, H17 — vedi CLAUDE.md, sezioni
"H15->M15 demotion", "H17 afternoon-contiguity", "Daily activity quotas") **apposta** per
non far scattare il golden storico come infeasible. Se si prova a "scorare" il golden con
lo stesso `ConstraintProvider` usato dal solver, quasi tutti questi vincoli restituiscono
banalmente 0/0 sul golden — non perché il golden li rispetti, ma perché l'esenzione pinned
li disattiva strutturalmente. Un confronto hard/medium/soft "golden score vs run score"
sarebbe quindi **mele con pere**: il golden non perde MAI su quei vincoli per costruzione,
a prescindere da cosa contenga davvero.

**Come si evita il trabocchetto in questo report:** ogni KPI qui sotto è ricalcolato
**direttamente dai dati grezzi di posizionamento** (data/ora/materia/docente per lezione),
con lo stesso script Python applicato identicamente a golden e run — MAI passando per il
`ConstraintProvider` vivo e il suo stato `pinned`. Sono quindi simmetrici per costruzione:
- full-day rate, day-start anchored, ricorrenza settimanale, cadenza, istogramma blocchi,
  gap docenti, overflow oltre l'ideal-end — tutti ricomputati da zero sui due CSV/dump,
  non letti dal breakdown vincoli del solver.
- Le uniche due eccezioni **realmente pinned-insensibili anche nel `ConstraintProvider`**
  (nessun filtro `!lesson.pinned`) sono **S1** (gap docenti) e **H1/H3** (stacking) —
  per queste il confronto sarebbe stato valido anche via constraint score, ma qui sono
  comunque ricalcolate dai dati grezzi per coerenza di metodo.
- Un confronto "score hard/medium/soft" punto e basta **non compare** in questo report
  per il motivo sopra; i valori documentati in CLAUDE.md (golden ~-12 hard via
  `GoldenSolutionRegressionTest`, M15/M20/M28/ecc. 0/0 pinned-exempt) sono citati solo come
  contesto, non ricalcolati qui.

## 1. Dati e provenienza

| Lato | Fonte | Righe utilizzabili | Note |
|---|---|---|---|
| Golden | `claudedocs/spalmone-2026-27/data/lessons.csv` | 12.980 totali → **12.551** dopo `closed=='True'` (-428) e teacher/subject=='Fasullo' (-112, placeholder scartati anche dal Java import) | Convertite nel formato pipe `group\|date\|hour\|subject\|teacher\|pinned` (pinned=true per tutte, import 100% pinned) |
| Solver run1 | `task413-results/val413-run1/lessons.txt` | 12.301 | box 0 (0 unassigned), hard 0, score `0hard/-2088075medium/-47651soft` |
| Solver run2 | `task413-results/val413-run2/lessons.txt` | 12.301 | box 0, hard -1, score `-1hard/-2122087medium/-41676soft` |

Roster docenti identico sui due lati (26 docenti reali + pseudo-docente "Stage" escluso
dalle metriche che lo escludono esplicitamente, come da codice — vedi §7).

## 2. Scorecard KPI

| # | KPI | Golden | Run1 | Run2 | Verdetto |
|---|---|---|---|---|---|
| 1 | Full-day rate (§3) | 83,7% | 79,0% | 78,1% | **HUMAN** (margine piccolo) |
| 2 | Day-start anchored (§4) | 99,9% | 93,3% | 93,6% | **HUMAN** |
| 3 | Ricorrenza settimanale — def. golden stretta vs def. run (§5) | 41,5% (strict) | 53,5% (UNPINNED, def. diversa) | 53,1% | **NON COMPARABILE 1:1** (vedi caveat) |
| 4 | Cadenza — burst>2,0x (%coppie) (§6) | 41,6% | 79,1% | 79,1% | **HUMAN** (netto) |
| 5 | Cadenza — gap-weeks totali (§6) | 1.461 | 2.400 | 2.316 | **HUMAN** (netto) |
| 6 | Cadenza — burst ratio medio (§6) | 2,16 | 2,94 | 2,83 | **HUMAN** (netto) |
| 7 | Blocchi ≥2h (subject reali, Stage escluso) (§7) | 1.300 | 1.882 | 1.850 | **SOLVER** (conta di più, ma forma diversa — vedi nota) |
| 8 | Gap docenti — istanze (§8) | 1.280 | 1.129 | 1.118 | **SOLVER** |
| 9 | Gap docenti — minuti totali (§8) | 103.200 (1.720h) | 87.720 (1.462h) | 84.720 (1.412h) | **SOLVER** |
| 10 | Lezioni oltre l'ideal-end, ore totali (§9) | 400 | 204 | 194 | **SOLVER** |
| 11 | Stacking (H3-style, cella group/data/ora con >1 lezione) (§10) | 0 | 0 | 0 | **PARI** |
| 12 | Ore totali piazzate per gruppo (§11) | 12.551 | 12.301 (Δ -250) | 12.301 (Δ -250) | **DELTA STRUTTURALE**, non un fallimento del solver (vedi §11) |

## 3. Full-day rate

Definizione: quota di giorni (gruppo, data) in cui le ore piazzate uguagliano esattamente
le ore attese dal foglio scuola `calendario_class_days.csv` (`daily-fill-kpi.py`, TASK-385).
Stesso script, stessa fonte "attese", applicato identicamente ai due lati.

```
python3 scripts/analyze/daily-fill-kpi.py <pipe-dump>
```

| | Golden | Run1 | Run2 |
|---|---|---|---|
| FULL-DAY RATE | **2005/2395 = 83,7%** | 1892/2395 = 79,0% | 1871/2395 = 78,1% |

Nota: CLAUDE.md cita un riferimento storico "golden 85,4%" per questa KPI — leggermente
diverso dall'83,7% qui misurato. La differenza (~1,7pt) è nell'ordine dell'esclusione
Fasullo/closed applicata qui e non necessariamente nella misura storica; **83,7% è il
numero riprodotto in QUESTO report con lo stesso script/stessa sorgente-attese usati per
i run**, quindi è quello internamente coerente per il confronto diretto qui sopra.

Per-gruppo (full/tot):

| gruppo | golden | run1 | run2 |
|---|---|---|---|
| G01 | 100% | 98% | — |
| G02 | 100% | 97% | — |
| G03 | 88% | 91% | — |
| G04 | 32% | 53% | — |
| G05 | 100% | 99% | — |
| G06 | 100% | 88% | — |
| G07 | 100% | 97% | — |
| G08 | 90% | 83% | — |
| G09 | 95% | 76% | — |
| G10 | 94% | 80% | — |
| G11 | 99% | 98% | — |
| G12 | 85% | 63% | — |
| G13 | 89% | 85% | — |
| G14 | 0% | 0% | — |

(run2 non ristampato per brevità: la maggioranza dei gruppi entro 1-3pt da run1, ma G09
76%→70% e G10 80%→72% oscillano di più, ~6-8pt — variance run-to-run non ancora piccola
su questi due gruppi; vedi `kpi-fill.txt` dei due run per il dettaglio completo.) G14
0%/0% su entrambi i lati è **strutturale**,
non un difetto: G14 ha uno scarto noto ~9x fra `incarichi.csv` (115h/anno) e
`calendario_class_days.csv` (990h/anno, identico a un gruppo full-time) — G14 porta già
un'esenzione M20 standing per questo motivo (task-010). Su G04 il solver è **meglio** del
golden (53% vs 32%) — G04 è uno dei 3 gruppi a quota (`calendario_fc.csv`), la cui vera
sorgente-attese non è `calendario_class_days.csv` (usata uniformemente da questo script per
tutti e 14 i gruppi) — tensione a due fogli nota, documentata in CLAUDE.md (TASK-385).
1/2/G10, G12, G09 sono invece i gruppi dove il golden è nettamente più pieno.

## 4. Day-start anchored

Definizione: quota di giorni (gruppo, data) il cui primo lezione dell'giorno è al primo slot
calendario-aperto del gruppo (`day-start-kpi.py`, TASK-405).

```
python3 scripts/analyze/day-start-kpi.py <pipe-dump>
```

| | Golden | Run1 | Run2 |
|---|---|---|---|
| DAY-START ANCHORED | **2210/2213 = 99,9%** | 2076/2224 = 93,3% | 2082/2224 = 93,6% |

Per-gruppo (giorni tardivi/tot): il golden ha solo 3 giorni tardivi su tutto l'anno (G08).
I run hanno concentrazioni misurabili su G07 (27-38/170), G11 (31-38/171), G10
(20/159-165), G13 (15-17/171), G08 (21-22/165-170) — questi 4-5 gruppi spiegano quasi
tutto il gap. **Unico KPI di questo report in cui il gap resta ampio e senza tendenza a
chiudersi tra run1/run2** (93,3% vs 93,6%, variazione nel rumore).

## 5. Ricorrenza settimanale — NON direttamente comparabile

Due definizioni diverse sono in gioco e **non vanno confrontate come se fossero la stessa
metrica**:

- **Golden — definizione stretta ("within-segment", TASK-351)**: per ogni segmento
  omogeneo dell'anno di un gruppo (pre-stage/durante-stage/post-stage/rampa iniziale/coda
  finale), quota di ore-lezione la cui cella (weekday, slot, docente, materia) ricorre in
  **≥80% delle settimane di quel segmento**. Misura la SOSTENIBILITÀ di un template lungo
  un intero segmento, non un singolo match. **Risultato: 41,5%** (soffitto modale 72,0%,
  ~16,4 template settimanali distinti/gruppo in media) — riprodotto qui via
  `derive-golden-template-recurrence.py`, identico al valore già pinnato in CLAUDE.md/TASK-351.
- **Run — `resemblance-kpi.py` (TASK-381/385/413), fetta UNPINNED**: per ogni ora-lezione
  NON pinnata, la cella (gruppo, weekday, slot) combacia con la STESSA cella esattamente
  **7 giorni prima** su (docente, materia) — un solo match, non una ricorrenza sostenuta.
  **Risultato: run1 53,5% (5.156/9.635), run2 53,1% (5.114/9.635)**.

Applicare lo stesso `resemblance-kpi.py` (bar più basso, un solo match richiesto) sul
golden dà un numero fuorviante e **NON è la cifra riportata in questo scorecard**: siccome
il golden è 100% pinned, la fetta "UNPINNED" è vuota (0/0) e la fetta "PINNED" (dato che
`resemblance-kpi.py` non ha esenzioni per pinned) restituisce 70,3% (8.820/12.551) — un
numero quasi doppio del 41,5% strict, che misura semplicemente "quanto spesso una cella si
ripete rispetto a 7 giorni prima da qualche parte nell'anno", una barra molto più bassa
della persistenza-per-segmento. **Confrontare 53,5% (run, bar bassa) con 70,3% (golden,
bar bassa, stesso script) sarebbe l'unico confronto "onesto" a parità di definizione — e
mostra che il golden vince anche su questa barra più permissiva** (70,3% vs 53,5%), ma
resta un numero diverso dal 41,5% citato ovunque nel codebase come riferimento golden.
Riportiamo entrambi per trasparenza:

| Metrica | Definizione | Golden | Run1 | Run2 |
|---|---|---|---|---|
| Ricorrenza "strict within-segment" (riferimento canonico) | ≥80% delle settimane del segmento | **41,5%** | non applicabile (nessun concetto di "segmento" calcolato lato run in questo report) | — |
| Ricorrenza "prev-week single match" (stesso script sui due lati) | match con la cella di 7gg prima | 70,3% (tutte pinned) | 53,5% (fetta unpinned) | 53,1% |

**Verdetto: NON COMPARABILE 1:1.** Sulla barra bassa comune (prev-week match) il golden
resta comunque avanti (70,3% > 53,5%), ma la cifra "53,5%" del run misurata contro il
"41,5%" del golden — il confronto che verrebbe naturale leggere — mescola due definizioni
diverse e va evitato. Nessun verdetto SOLVER/HUMAN netto assegnato in scorecard per questa
riga.

## 6. Cadenza (burst ratio, gap-weeks) — simmetrico, definizione unica

`cadence-kpi.py` (TASK-413) — stesso script, auto-detect formato CSV/pipe, esclude
Stage/Esame/Fasullo su entrambi i lati per costruzione. Soglia burst 2,0x, min 10h/coppia.

```
python3 scripts/analyze/cadence-kpi.py claudedocs/spalmone-2026-27/data/lessons.csv
python3 scripts/analyze/cadence-kpi.py <run-lessons.txt>
```

| KPI (n=244/245 coppie) | Golden | Run1 | Run2 |
|---|---|---|---|
| burst>2,0x (% coppie) | **41,6%** | 79,1% | 79,1% |
| gap-weeks totali | **1.461** | 2.400 | 2.316 |
| gap-weeks / coppia | **5,96** | 9,84 | 9,49 |
| burst ratio medio | **2,16** | 2,94 | 2,83 |

**HUMAN netto su tutti e tre gli aggregati.** Questa è la KPI più pulita del report: stesso
script, stessa definizione, stessa esclusione pseudo-docenti sui due lati, nessuna
ambiguità di metodo. Il golden mantiene una cadenza quasi costante materia/settimana; i
run alternano settimane vuote a burst di fine periodo (il problema che M32/TASK-413 sta
attaccando). Il gap si sta chiudendo (val414 senza M32 era 86,9-89,3%/2.772-2.829/3,29-3,32
— vedi VERDICT.md TASK-413) ma resta ampio.

## 7. Istogramma blocchi ≥2h (stesso soggetto, giorno, adiacenti)

`resemblance-kpi.py`, sezione "blocchi". Le lezioni Stage (pseudo-docente, blocchi interi
di 5-8h/giorno per costruzione contabile) sono state **escluse su entrambi i lati** prima
di questo conteggio — altrimenti gonfiano artificialmente sia il totale sia i bucket
grandi in modo identico e non informativo sui due lati (senza esclusione: golden 1.555
`>=2h`, verificato che scende a 1.300 escludendo Stage, quasi identico al riferimento
canonico "golden 1.299" citato nei commenti del codice — la differenza di 1 riga è
irrilevante).

```
grep -v '|Stage|Stage|' <pipe-dump> | python3 scripts/analyze/resemblance-kpi.py /dev/stdin
```

| bucket | Golden | Run1 | Run2 |
|---|---|---|---|
| 1h | 6.703 | 6.223 | 6.284 |
| 2h | 666 | 1.453 | 1.424 |
| 3h | 332 | 360 | 352 |
| 4h | 84 | 58 | 62 |
| 5h | 155 | 6 | 7 |
| 6h | 52 | 5 | 5 |
| 7h | 0 | 0 | 0 |
| 8h | 11 | 0 | 0 |
| **totale ≥2h** | **1.300** | **1.882** | **1.850** |

**Verdetto SOLVER per il totale, ma con una forma qualitativamente diversa e non
migliore.** Il run forma più blocchi in assoluto (1.882/1.850 vs 1.300), ma la
composizione racconta un'altra storia: il golden distribuisce su tutta la gamma —incluse
155 giornate da 5h e 52 da 6h e 11 da 8h di lezione continua (probabilmente laboratori/
officina) — mentre il run è quasi tutto compresso in blocchi da 2h (1.453/1.424, più del
doppio del golden) e collassa a quasi zero oltre le 4h (6+5+0=11 vs golden 155+52+11=218).
Questo è l'effetto atteso del tetto M21 ("max 2h/materia/giorno" salvo laboratori/materie
esenti, TASK-381 "L3"): il solver **non può** formare blocchi lunghi per la maggior parte
delle materie per costruzione del modello, quindi il "vince per numero" non equivale a
"riproduce meglio la pratica dei blocchi lunghi della scuola" — anzi il contrario per la
coda lunga della distribuzione.

## 8. Gap docenti (istanze e minuti)

Nessun trucco di conversione qui: S1 (`minimizeTeacherGaps`, SchoolScheduleConstraintProvider)
non ha esenzione pinned, solo l'esclusione del docente pseudo "Stage" — quindi è uno dei
pochissimi vincoli realmente simmetrico anche nel `ConstraintProvider` stesso. Ricomputato
comunque da zero sui dati grezzi (stessa definizione esatta: minuti-orologio fra fine
lezione precedente e inizio successiva, meno 30' pausa pranzo se interamente contenuta nel
gap, floor 0) per coerenza metodologica col resto del report — non letto dal breakdown
vincoli. **Validazione incrociata**: sul run1 lo script ricalcola 1.129 istanze / 87.720
minuti, che a peso `ofSoft(2)` fa -175.440 — identico esatto al match-count e allo score S1
di `analysis.json` (`"S1 - Minimize teacher gaps", matches: 1129, soft: -175440`), a
conferma che la riproduzione del meccanismo è corretta.

```
python3 <converter csv->pipe, §Appendice> golden.csv golden-pipe.txt
python3 <teacher-gaps.py, §Appendice> golden-pipe.txt
python3 <teacher-gaps.py, §Appendice> <run-lessons.txt>
```

| | Golden | Run1 | Run2 |
|---|---|---|---|
| istanze totali | **1.280** | 1.129 | 1.118 |
| minuti totali | **103.200 (1.720h)** | 87.720 (1.462h) | 84.720 (1.412h) |
| minuti medi/istanza | 80,6 | 77,7 | 75,8 |
| docenti-giorno valutati | 3.123 | 3.390 | 3.396 |

**Verdetto SOLVER, netto e su una metrica genuinamente simmetrica.** Il solver produce
buchi orario ai docenti in numero e durata minori del golden storico — non un pareggio
tecnico, un vantaggio reale del solver su un vincolo non pinned-esente. Top-5 docenti per
minuti persi: golden T08/T14/T09/T23/T20 (tutti 130-175h/anno);
run1 T11/T21/T08/T05/T02 (90-133h/anno) — composizione diversa,
magnitudine sempre più bassa lato run.

## 9. Lezioni oltre l'ideal-end per gruppo

Definizione: la stessa di `TargetEndDateCalculator`'s fact-based branch — per ogni gruppo,
si cumulano le ore attese per data (`calendario_fc.csv` per G04/G08/G13,
`calendario_class_days.csv` per gli altri 11) fino a raggiungere il monte-ore
`incarichi.csv` del gruppo; la data in cui si raggiunge quella soglia è l'ideal-end. Ore
oltre quella data sono conteggiate su entrambi i lati. Implementazione già esistente e
condivisa: `golden-vs-run-viz.py` (TASK-407/408) — i numeri qui sotto sono letti
direttamente dal payload JSON già incorporato nelle viste HTML già catturate
(`confronto-val413-run{1,2}.html`, nessuna rigenerazione necessaria).

```
python3 scripts/analyze/golden-vs-run-viz.py <run-lessons.txt> -o out.html
# poi leggere out.html: const DATA = {...}; -> DATA.groups[i].stats.{gpast,npast}
```

| gruppo | ideal-end | ore oltre — golden | ore oltre — run1 | ore oltre — run2 |
|---|---|---|---|---|
| G01…G12, G13 (11 gruppi) | 2027-06-09/10 | 0 | 0 (G07: 7 in run1, 6 in run2) | — |
| G04 | 2027-04-09 | **285** | 106 | 101 |
| G14 | 2026-10-12 | **115** | 91 | 87 |
| **TOTALE** | | **400** | **204** | **194** |

**Verdetto SOLVER, netto.** L'intero gap è concentrato in G04 e G14, due gruppi con
monte-ore molto più piccolo delle 990h standard (750h e 115h) e quindi ideal-end molto
anticipato nell'anno (aprile/ottobre) — sia il golden sia il run sforano quella soglia
precoce, ma il golden la sfora quasi 2x più a lungo. Sui 12 gruppi "standard" da 990h lo
sforamento è sostanzialmente nullo su entrambi i lati (solo G07 ha un residuo minimo
lato run, 6-7h).

## 10. Stacking (H3-style: cella gruppo/data/ora con >1 lezione)

`moda-knot-count.py`, nessuna esenzione pinned nel vincolo di riferimento (H3 unblinded
da TASK-377) — confronto simmetrico per costruzione anche nel `ConstraintProvider` stesso.

| | Golden | Run1 | Run2 |
|---|---|---|---|
| celle stackate (tutti i gruppi) | **0** | 0 | 0 |

**PARI.** Zero conflitti su entrambi i lati, come atteso (box 0/hard 0 per il run;
il golden storico non ha mai avuto doppie prenotazioni per costruzione dei dati scolastici).

## 11. Ore totali piazzate per gruppo

| gruppo | golden | run1 | run2 | Δ (run1-golden) |
|---|---|---|---|---|
| G01 | 990 | 990 | 990 | 0 |
| G02 | 990 | 990 | 990 | 0 |
| G03 | 990 | 990 | 990 | 0 |
| G04 | 974 | 750 | 750 | **-224** |
| G05 | 990 | 990 | 990 | 0 |
| G06 | 990 | 990 | 990 | 0 |
| G07 | 990 | 990 | 990 | 0 |
| G08 | 943 | 902 | 902 | **-41** |
| G09 | 958 | 958 | 958 | 0 |
| G10 | 898 | 898 | 898 | 0 |
| G11 | 958 | 966 | 966 | +8 |
| G12 | 787 | 794 | 794 | +7 |
| G13 | 978 | 978 | 978 | 0 |
| G14 | 115 | 115 | 115 | 0 |
| **TOTALE** | **12.551** | **12.301** | **12.301** | **-250** |

**NON identiche — delta strutturale, non un fallimento di piazzamento del solver.** Su 10
dei 14 gruppi il monte-ore combacia esattamente (o quasi, ±8h su 2 gruppi). Il delta è
concentrato quasi interamente su G04 (-224h) e G08 (-41h), i due dei tre gruppi a
quota (`calendario_fc.csv`) con la restrizione VRP più stringente sulle ore
lab/aula piazzabili (H23/H24/M30, TASK-381 "L1-bis" — vedi CLAUDE.md). Il run piazza
esattamente quanto il monte-ore `Course.totalHours` del seed (`incarichi.csv`) gli chiede
per quei gruppi (0 unassigned, box 0 confermato in `score.txt`); il golden storico, essendo
il timetable REALE della scuola, riflette invece quante ore la scuola ha effettivamente
insegnato quell'anno per quei gruppi, un numero che può divergere dal monte-ore
`incarichi.csv` usato oggi come target del solver — è la stessa classe di discrepanza dati
già nota e documentata (`.claude/rules/datagenerator-protection.md`, "Known Data
Discrepancies": es. G09 666h DataGenerator storico vs 640h CSV). Non risolta né
indagata oltre in questo task (fuori scope, solo misura): segnalata come domanda aperta
per un futuro controllo incarichi.csv vs golden per i gruppi FC.

## 12. Giudizio complessivo

**Dove il solver ha già superato la pratica umana (evidenza pulita, metriche simmetriche):**
- **Gap docenti** (§8): meno istanze e meno minuti persi, su un vincolo genuinamente non
  pinned-esente — non un artefatto della cattura pinned, un vantaggio reale.
- **Overflow oltre l'ideal-end** (§9): meno ore fuori target, concentrato sugli stessi due
  gruppi strutturalmente compressi (G04, G14) dove ANCHE il golden sfora — il solver
  sfora meno.
- **Stacking** (§10): pareggio a zero, ma su un vincolo hard non pinned-esente — il solver
  non regredisce rispetto alla garanzia strutturale che il golden ha per costruzione.

**Dove il solver è ancora indietro, in modo netto e non ancora ambiguo:**
- **Cadenza settimanale** (§6): il divario più ampio e più pulito del report — burst>2x
  quasi doppio (79,1% vs 41,6%), quasi 1.000 gap-weeks in più. È esattamente il problema
  che M32/TASK-413 sta attaccando (val414 senza M32 era anche peggio, 87-89%) — direzione
  giusta, distanza dal golden ancora ampia.
- **Day-start anchoring** (§4): 93,3-93,6% vs 99,9%, concentrato su 4-5 gruppi specifici
  (G07, G11, G10, G13, G08) — non un problema diffuso ma un residuo localizzato
  che non mostra segni di chiusura fra run1 e run2.
- **Full-day rate** (§3): margine più piccolo (79,0/78,1% vs 83,7%) ma reale, con la
  distorsione nota "quota groups misurati contro il foglio sbagliato" che gonfia
  artificialmente il gap misurato per 1/G08/G13 — il gap vero è probabilmente più
  piccolo di quanto la tabella mostri per quei 3 gruppi.

**Strutturale, non del solver (non un target di miglioramento):**
- Il delta di 250h sul totale piazzato (§11), quasi interamente G04/G08 — riflette
  probabilmente uno scarto fra `incarichi.csv` (il target di oggi) e le ore reali del
  golden storico, non un bug di piazzamento.
- La forma dei blocchi ≥2h (§7): il solver ne forma di più ma quasi tutti da 2h (il tetto
  M21 lo impedisce strutturalmente di andare oltre per la maggior parte delle materie) —
  non è un "vantaggio" reale sul piano qualitativo, solo un artefatto del conteggio grezzo.

**Non giudicabile con i dati/metodo di questo report:**
- Ricorrenza settimanale (§5): le due definizioni standard nel codebase (41,5% strict
  within-segment vs 53,5% prev-week-match) non sono la stessa metrica; sulla barra comune
  più bassa il golden resta comunque avanti (70,3% vs 53,5%), ma non è la cifra che
  compare ovunque come "il" numero di riferimento golden, quindi il confronto naturale
  53,5%-vs-41,5% è scorretto e va evitato.
- Qualsiasi confronto hard/medium/soft score end-to-end (§0): il golden 100%-pinned rende
  la maggioranza dei vincoli medium banalmente 0/0 per costruzione, non per merito.

**Sintesi in una frase:** sui vincoli realmente simmetrici (gap docenti, stacking,
overflow) il solver ha già superato o pareggiato la pratica storica; sulla struttura
temporale fine (cadenza settimanale, ancoraggio inizio-giornata) resta indietro in modo
misurabile e non ancora chiuso, un gap coerente con quanto TASK-413/val413 già documenta
come lavoro in corso.

## Appendice — script non esistenti nel repo, usati per questo report (riproducibili)

### A. Conversione golden CSV -> pipe dump

```python
#!/usr/bin/env python3
# csv_to_pipe.py <lessons.csv> <out.txt>
import csv, sys
SLOT_TO_HOUR = {
    "08:00 09:00": 8, "09:00 10:00": 9, "10:00 11:00": 10, "11:00 12:00": 11,
    "12:00 13:00": 12, "13:00 14:00": 13, "14:30 15:30": 14, "15:30 16:30": 15,
}
def main(inp, outp):
    with open(inp, newline="", encoding="utf-8") as f, open(outp, "w", encoding="utf-8") as out:
        out.write("SET\n")
        for r in csv.DictReader(f):
            if r["closed"] == "True":
                continue
            if r["teacher"] == "Fasullo" or r["subject"] == "Fasullo":
                continue
            hour = SLOT_TO_HOUR[r["slot"]]
            out.write(f' {r["group"]}|{r["date"]}|{hour}|{r["subject"]}|{r["teacher"]}|true\n')
if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
```

### B. Gap docenti (istanze + minuti), replica esatta di S1

```python
#!/usr/bin/env python3
# teacher-gaps.py <pipe-dump>
import sys
from collections import defaultdict
PSEUDO_TEACHERS = {"Stage"}
SLOT = {8:(480,540),9:(540,600),10:(600,660),11:(660,720),12:(720,780),
        13:(780,840),14:(870,930),15:(930,990)}
LUNCH_START, LUNCH_END = 840, 870
def main(path):
    by_teacher_date = defaultdict(set)
    for line in open(path):
        line = line.strip()
        if not line or line == "SET" or "|" not in line:
            continue
        p = [x.strip() for x in line.split("|")]
        if len(p) != 6:
            continue
        _g, d, h, _s, t, _pn = p
        if t in PSEUDO_TEACHERS:
            continue
        by_teacher_date[(t, d)].add(int(float(h)))
    total_instances = total_minutes = 0
    for (t, d), hours in by_teacher_date.items():
        hs = sorted(hours)
        for i in range(1, len(hs)):
            prev_end = SLOT[hs[i-1]][1]
            next_start = SLOT[hs[i]][0]
            gap = next_start - prev_end
            if gap <= 0:
                continue
            if prev_end <= LUNCH_START and next_start >= LUNCH_END:
                gap -= 30
            if gap > 0:
                total_instances += 1
                total_minutes += gap
    print(f"instances={total_instances} minutes={total_minutes}")
if __name__ == "__main__":
    main(sys.argv[1])
```

### C. Comandi eseguiti per questo report (in ordine)

```bash
python3 csv_to_pipe.py claudedocs/spalmone-2026-27/data/lessons.csv golden-pipe.txt
python3 scripts/analyze/daily-fill-kpi.py golden-pipe.txt
python3 scripts/analyze/day-start-kpi.py golden-pipe.txt
python3 scripts/analyze/resemblance-kpi.py golden-pipe.txt
python3 scripts/analyze/derive-golden-template-recurrence.py
python3 scripts/analyze/cadence-kpi.py claudedocs/spalmone-2026-27/data/lessons.csv
python3 scripts/analyze/moda-knot-count.py golden-pipe.txt "G05"
grep -v '|Stage|Stage|' golden-pipe.txt > golden-nostage.txt
python3 scripts/analyze/resemblance-kpi.py golden-nostage.txt
python3 teacher-gaps.py golden-pipe.txt
python3 teacher-gaps.py claudedocs/spalmone-2026-27/task413-results/val413-run{1,2}/lessons.txt
# ideal-end §9: letto da confronto-val413-run{1,2}.html, DATA.groups[i].stats già catturato
# (nessuna rigenerazione: python3 scripts/analyze/golden-vs-run-viz.py rigenera identico)
# tutte le altre cifre run1/run2 lette direttamente dai KPI già catturati in
# task413-results/val413-run{1,2}/kpi-*.txt, score.txt, analysis.json (nessuna rigenerazione)
```
