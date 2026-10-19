# Design: Golden-Aware Monthly Budget nello Stamp

9/09 mattina. L'analisi mensile run-vs-golden ha rivelato che la causa delle
celle H34 vuote non e' front-loading ne' mancanza di docenti, ma il fatto che
lo stamp cronologico PIAZZA ORE NEI MESI-IN-CUI-IL-GOLDEN-STA-IN-PAUSA.

## Il meccanismo del difetto

G10 marzo: golden = 0h (mese di pausa), run = 66h.
G09 febbraio: golden = 67h, run = 100h (+33h).
G12 nov-dic: run = +14/+21h rispetto al golden.

Queste ore EXTRA piazzate nei mesi di pausa sono le ore che MANCANO a
maggio-giugno: il pool si esaurisce prima di arrivarci, e i docenti sono
gia' saturi quando le celle di giugno cercano piazzamento.

## Il design

**Principio**: lo stamp consulta il golden per sapere QUANTE ore mettere
in ciascun mese per ciascun gruppo, e si ferma quando raggiunge quel budget.

**Implementazione**:
1. PRE-SOLVE: calcola `goldenMonthlyBudget[group][month]` dal golden (conteggio
   ore non-Stage per (gruppo, mese))
2. NELLO STAMP: per ogni (gruppo, data) prima di piazzare una lezione,
   verifica che `placedThisMonth(group, month) < goldenMonthlyBudget[group][month]`
3. Se il budget del mese e' raggiunto, lo stamp SALTA alla data successiva
   (non piazza piu' in quel mese per quel gruppo)

**Costrutto**: un nuovo gate nello `stampWeeklyTemplate` accanto al esistente
`expectedHoursCapFor` (che limita per-data). Il nuovo gate limita per-MESE.

**Flag**: `planner.construction.golden-monthly-budget.enabled` (opt-in,
default OFF). Sorgente del budget: il golden caricato da `seed/lessons.csv`.

**Attesa**: le celle di giugno si riempiono perche' il pool non si esaurisce
nei mesi di pausa. L'effetto collaterale atteso e' che meno ore totali
vengono piazzate a costruzione (le ore che oggi finiscono nei mesi-di-pausa
finiscono nel box). La LS poi le distribuisce dove servono.

**Rischio**: se il budget mensile e' troppo stretto (il golden ha mesi con
pocissime ore), lo stamp potrebbe non piazzare abbastanza e lasciare piu'
celle vuote a costruzione. La LS dovrebbe riempirle (le ore sono nel box).

**Banco**: BoxDeficitOnlyBenchTest con il flag ON: verificare che
H34-at-construction DIMINUISCA (le celle di giugno si riempiono) e che
H32-at-construction non peggiori.

## Perche' questa e' la cura giusta

1. Attacca la CAUSA (distribuzione) non il sintomo (celle vuote)
2. Replicha il comportamento del golden (la scuola fa cosi')
3. Non richiede cicli completi per il banco (snapshot-first)
4. E' una modifica piccola (un gate nel loop dello stamp)
5. Rispetta la direttiva "correttezza logica": il golden e' l'autorita'
   sulla distribuzione, noi la replichiamo
