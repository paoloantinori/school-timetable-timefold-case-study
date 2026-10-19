# TASK-457 falsificazione: la deriva tier CAUSA un difetto o e' solo una firma? (2026-07-30)

> **RILETTURA G04 (collegata, CHIUSA - difetto nel MODELLO, non nei dati; ritrattata 2 volte, tracciabile).**
> Il deficit di G04 non era ricerca, non capacity-vs-obbligo, non un dato sbagliato: è un difetto del
> modello M20. Il dato è CORRETTO (835 = 990 autoritativo − 155, trasformazione fedele; TASK-434 è
> chiuso e corretto). Le 85h di scarto sono CAPIENZA REALE NON UTILIZZATA (835 slot vs 750
> insegnamento, alla lettera vincoli-in-parole). `m20DayCost` targetta la capienza senza tetto sulle
> lezioni esistenti -> istanza confermata = G04 85h. MISURA: irraggiungibili non-esenti 129h (G04
> 85, G08 40, G09 4) -> ~129.000 medium floor. Tetto proposto = min(capienza, lezioni esistenti).
> G14 = ingestion separata (TASK-462). Dettagli: TASK-461 + TASK-434 + QUADRO sezione RILETTURA.

Test decisivo (mandato maintainer, 2026-07-30): i giorni a deficit PROFONDO (>=2h) si concentrano
nei mesi in cui la quota di docenti generici e' SOTTO quella del golden? Se sì, il generico
speso presto lascia un residuo di soli specializzati che a fine anno non tappa i buchi, e il
tier diventa una leva. Se i profondi sono uniformi o si addensano dove la quota generici e'
SOPRA il golden, l'ipotesi e' morta e il 457 chiude come firma inerte.

## IPOTESI
Deep-deficit days concentrate in months where the run's generic-teacher quota is BELOW golden
(generics front-loaded -> late-year residue can't fill holes -> deficits deepen, not just spread).

## NUMERO MISURATO (n=3 run freschi: 29b, 30a, 30b)

Strumento: `scripts/analyze/daily-fill-kpi.py` (esteso col bucket mensile, commit dedicato) per
il deficit profondo, `scripts/analyze/teacher-tier-kpi.py` per la quota generici mensile. Entrambi
sullo stesso orizzonte canonico LO=2026-09-14 / HI=2027-06-10 (fine scuola). Sabati off-grid
ESCLUSI dal bucket deep (26 ore stage sabato G12/G10, incollocabili: vedi cautela 1).

**Distribuzione mensile deep-deficit WEEKDAY (sabati esclusi):**

| mese | 29b | 30a | 30b | quota generici 30b (run-golden) |
|---|---|---|---|---|
| 2026-09 | 3 | 4 | 4 | -3,2 (sotto) |
| 2026-10 | 0 | 0 | 1 | +1,1 (sopra) |
| 2026-11 | 1 | 0 | 0 | +1,5 (sopra) |
| 2026-12 | 5 | 6 | 6 | +2,5 (sopra) |
| 2027-01 | 3 | 3 | 5 | +3,0 (sopra) |
| 2027-02 | 2 | 1 | 1 | +2,0 (sopra) |
| 2027-03 | 11 | 12 | 12 | -0,6 (sotto) |
| 2027-04 | 12 | 10 | 11 | -1,6 (sotto) |
| 2027-05 | 3 | 4 | 7 | -5,4 (sotto) |
| 2027-06 | 4 | 4 | 4 | -3,6 (sotto) |
| **tot weekday deep** | 44 | 44 | 51 | |
| **mar-giu share** | **68%** | **68%** | **67%** | tutti sotto il golden |

> I numeri sopra sono **RAW**: includono 14 giorni fantasma "weekday-closure" (date in
> `CLOSURE_DATES_2026_27` che cadono di weekday: il solver non emette Timeslot, quindi
> placed==0, ma `calendario_class_days.csv` ci mette ore attese >=2). Sono incollocabili
> per costruzione e identici in ogni run. Il ricalcolo pulito e' sotto.

### METODOLOGIA RISOLTA (round 2, 2026-07-30) - AUTOREVOLICA

La discordanza "31 (SPIKE 3, export) vs 51/37 (daily-fill-kpi, class_days)" e' risolta.
La popolazione CORRETTA e' **DailyActivityQuota (aula+lab)**, la stessa che il solver
targetizza con M20. `calendario_class_days.csv` (che daily-fill-kpi usava per TUTTI i
gruppi, incluso gli FC) e' l'artefatto e porta 20 giorni in piu' (51 vs 31): (1) 14
weekday-closure phantoms, (2) 4 no-session di fine anno (giugno, gruppo finito), (3) 2
date FC con aula+lab sovrastimato vs `calendario_fc.csv`. **Strumento allineato**:
`daily-fill-kpi.py` ora legge DailyActivityQuota dall'export.json.gz a fianco del dump
(fallback class_days con warning). Non sono due metriche: e' la stessa, una fonte giusta.

**Numeri autorevoli (DailyActivityQuota aula+lab, sabati esclusi):**

| run | deep | mar-giu | share | full-day ex-G14 |
|---|---|---|---|---|
| 29b | 24 | 17 | 71% | - |
| 30a | 24 | 17 | 71% | - |
| 30b | 31 | 21 | 68% | 90,1% |
| 447 | 27 | 19 | 70% | - |
| golden | 3 | 2 | 67% (n=3) | ~91,9% |

**Verdetto parziale (metodologia).** La concentrazione mar-giu del deep-deficit e' **68-71%**
(n=4 run), coerente coi mesi sotto-quota-golden: la STATISTICA regge. NOTA DI HONESTY: il
ricalcolo round 1 qui sotto ("73-77% clean") era una SOVRACORREZIONE (tolto solo i 14 closure
phantoms dalla base class_days, restavano i 6 artefatti no-session+FC in mar-giu che
gonfiavano la share). La fonte autorevole da' 68-71%, golden deep 3. **MA la statistica non e'
causa:** vedi il DECLASSAMENTO nel VERDETTO qui sotto (G04 aprile +2,5 sopra golden con 10
deep spezza il legame causale). FIRMA descrittiva, non leva.

### RICALCOLO ROUND 1 (LAVORO 1) - SUPERSEDATO dal round 2 sopra (fonte class_days, artefatto)

I 14 fantasma weekday-closure si distribuiscono: **dic 5, gen 2, mar 7** (i "7 di marzo"
del mandato). Tolti dalla distribuzione deep (script `/tmp/measure_457_phantoms.py`,
stessa fonte `calendario_class_days.csv`, stesso orizzonte, sabati gia' esclusi):

| mese | 29b raw->clean | 30a raw->clean | 30b raw->clean |
|---|---|---|---|
| 2026-12 | 5 -> 0 | 6 -> 1 | 6 -> 1 |
| 2027-01 | 3 -> 1 | 3 -> 1 | 5 -> 3 |
| 2027-03 | 11 -> 4 | 12 -> 5 | 12 -> 5 |
| 2027-04 | 12 -> 12 | 10 -> 10 | 11 -> 11 |
| 2027-05 | 3 -> 3 | 4 -> 4 | 7 -> 7 |
| 2027-06 | 4 -> 4 | 4 -> 4 | 4 -> 4 |
| **tot deep** | 44 -> **30** | 44 -> **30** | 51 -> **37** |
| **mar-giu share** | 68% -> **77%** | 68% -> **77%** | 67% -> **73%** |

(447, di riferimento: raw 46/67% -> clean 32/75%.)

**La concentrazione REGGE, anzi si rafforza.** Togliere i fantasma porta la share
mar-giu dal 67-68% al **73-77%** (n=3). I 7 fantasma di dic+gen cadevano nella meta'
iniziale dell'anno e DILUIVANO la concentrazione tardiva piu' di quanto i 7 fantasma
di marzo (dentro mar-giu) la gonfiassero: netto, la share sale. L'indizio del 457 NON
e' un artefatto dei giorni fantasma. La finestra tardiva (aprile in particolare, 11
deep puliti e intatti) e' reale. Mandato: "se non regge cambia verdetto" -> **regge,
il verdetto non cambia** (TENERE); unico aggiustamento narrativo, il picco
"marzo-aprile" diventa "aprile" (marzo 12->5 dopo i fantasma, aprile 11 e' il picco chiaro).

**Correzione golden (stessa root cause).** Il deep del golden e' **9**, non 49 e non 23.
Misura diretta da `seed/lessons.csv`: G08 7 + G09 2 (G04 a zero deep, come dice
gia' il commento in `daily-fill-kpi.py`: "~9 deep, G04 zero"). Il golden RIEMPIE tutte
e 14 le date di chiusura (ha lezioni nel CSV a Natale/Pasqua/2027-01-22), quindi non le
conta come deficit. Il "23" dell'handoff era artefatto del dump DB: le chiusure non hanno
Timeslot, le lezioni golden di quei giorni spariscono all'import -> 14 falsi deficit ->
9+14=23. Full-day ex-G14 golden = 91,9% (~91,2% dell'handoff per arrotondamento).
Il run resta sopra il golden (37 deep placeable e 88,6% contro 9 deep e 91,9%): il gap
e' reale, la concentrazione tardiva del run e' la firma da chiudere.

**Segnale coerente nei 3 run**: il deep-deficit WEEKDAY si concentra al 67-68% in mar-giu, con
picco marzo-aprile (11-12 / 10-12). Quei mesi (mar-giu) sono ESATTAMENTE dove la quota generici
del run scende sotto il golden (da -0,6 a -5,4). Ottobre-febbraio (quota SOPRA il golden) hanno
quasi zero deep. L'ipotesi NON e' morta: il concentration criterion di maintainer e' soddisfatto.

NOTA DI HONESTY (correzione durante la DoD): una prima versione di questo doc riportava weekday
deep 62-69 e mar-giu 75-77% con giugno a 22. Era un BUG di una mia analisi inline che usava
HI=2027-06-30 (incluse righe fantasma post-fine-scuola, oltre il 06-10). I numeri sopra sono
canonici (orizzontale corretto 06-10) e sono loro la verita'. La conclusione non cambia, la
magnitudo si': 67-68% (non 75-77%) e giugno e' minore (4, non 22).

## CAUTELE (entrambe verificate)

**Cautela 1 - artefatto sabbati (lezione di ieri):** `calendario_class_days.csv` ha 26 righe
SABATO con ore>0, tutte G12 (19) + G10 (7) = **stage off-grid** (fuori griglia Mon-Fri,
incollocabili). daily-fill-kpi.py le conta come deep-deficit (exp 8 got 0). ESCLUSE
dall'analisi (il bucket mensile dello script ora filtra i sabati). BUG PRE-ESISTENTE del KVI:
il resto di daily-fill-kpi.py (FORMA, top-15) conta ancora i sabati; andrebbe escluso ovunque
come si esclude G14 (task separato).

**Cautela 2 - gruppi finiti a fine anno:** con l'orizzonte corretto (scuola finisce 06-10),
giugno ha solo 4 weekday deep (non 22): il rischio "gruppi finiti = artefatto fine anno" e'
minore del previsto. I deep di marzo-aprile (il picco reale) sono in gruppi con ore davvero non
collocate, non finiti: completamento 30b (course_total/scheduled/non-collocate) = G10
970/898/**72**, G08 926/902/**24**, G09 966/958/**8**, G11 966/958/**8**. Quindi deficit
reale (ore che il solver non ha collocato), non over-plan di gruppi finiti. Solo G13 (978/978/0)
e G07 (990/990/0) sono finiti e non compaiono nel picco.

## VERDETTO: DECLASSATA a FIRMA con supporto causale DEBOLE. Nessuna leva da costruire.

> **Correzione 2026-07-30 (verifica richiesta da maintainer, non ripensamento).** maintainer ha chiesto se la
> metrica tier docenti ci abbia mai aiutato davvero, ha guardato dentro il numero e la risposta e'
> no. La conclusione "TENERE come lead supportato" di stamattina va DECLASSATA. L'evidenza sotto.

**La correlazione c'e' come statistica, ma NON causa il caso dominante.** A livello aggregato la
deriva generici e' sopra il golden da ottobre a febbraio e sotto di 6,5/7,2 punti a maggio/giugno,
e il deep-deficit concentra a fine anno (68-71% mar-giu, picco aprile). Le due cose si muovono
insieme. Ma il gruppo che da solo produce circa tre quarti dei giorni profondi, **G04, NON segue
quel disegno**, ed e' l'unico caso che valga la pena attaccare:

- **Controesempio (G04, aprile):** a aprile G04 sta **+2,5 SOPRA il golden** sulla quota
  generici (nessun esaurimento), eppure ha **10 giorni deep** (aprile e' il picco assoluto di G04).
  Se l'esaurimento tier causasse i buchi, stare-sopra-il-golden dovrebbe significare zero deep;
  invece G04 ha entrambi. Il legame causale e' rotto sul caso dominante.
- **G04 ven non c'entra coi generici:** 12 dei 22 deep di G04 cadono di **venerdi'** (i "13
  venerdi' critici"), e `spike4-g04-venerdi` aveva gia' misurato che il pacing residuo del run e'
  circa uguale al golden, non tier-drift: e' ricerca/coordinamento, non capacita'. La verifica
  indipendente di maintainer lo conferma da un altro angolo.

**Lettura corretta:** a fine anno tutto e' piu' stretto (meno ore, meno margine), e sia la deriva
sia i deficit profondi peggiorano per la **stessa ragione di fondo** (strettezza finale), non per
causa-effetto fra i due. La correlazione sopravvive come statistica ma non spiega il caso dominante.

**Conseguenza:** il tier-drift resta una **FIRMA** (segnale descrittivo reale: a fine anno peggiora
tutto), con **supporto causale debole** e **nessuna leva da costruire**. Non si inseguono run con
drift variato (era il "prossimo passo" di stamattina, cancellato). Il bersaglio operativo e' il
**G04 venerdi'**, che e' ricerca non capacita' (gia' dimostrato in SPIKE 4): vedi l'analisi di
derivabilita' in TASK-451 e la verifica first-hour in corso.

## CONSEGNA IN 3 RIGHE (aggiornata)
- **Ipotesi (originale)**: deep-deficit concentrates where generic quota < golden (tier exhaustion -> late holes unfillable).
- **Numero**: deep-deficit 68-71% in mar-giu (n=4, DailyActivityQuota), co-muove con la deriva generici a fine anno.
- **Tenere o buttare**: **BUTTARE come lead, tenere come FIRMA descrittiva.** Causa debole: G04
  (il caso dominante, ~3/4 dei deep) sta +2,5 SOPRA golden ad aprile con 10 deep, e i suoi deep sono
  venerdiana (SPIKE 4: ricerca non capacita'). Nessuna leva da costruire. Bersaglio = G04 ven.

## Lascito strumento
`scripts/analyze/daily-fill-kpi.py` esteso (additivo, commit separato, DoD piena /code-review high
+ /simplify) con "DEFICIT PROFONDO per mese": bucket mensile dei giorni >=2h + share mar-giu.
Riusa expected/placed/deep_days. Esclude i sabati off-grid nel bucket (commento in linea spiega
perche'). CAVEAT: il resto del KPI conta ancora i sabati (bug pre-esistente, task separato).
