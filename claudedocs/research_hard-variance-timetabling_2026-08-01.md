# Research Report: eliminare la varianza hard residua nella LS di timetabling (path source=input)

**Date**: 2026-08-01
**Depth**: exhaustive
**Confidence**: HIGH sulle conclusioni principali (letteratura convergente + doc ufficiale Timefold)

## Executive Summary
La varianza run-to-run verso hard-0 e' un fenomeno noto e teoricamente caratterizzato (run-time
distribution heavy-tailed della ricerca combinatoria): il rimedio canonico e' proprio
restart/portfolio, quindi la proposta gia' in campo e' su base solida. La ricerca pero' ha
trovato UN complemento strutturale che non era nella proposta: il **completamento esatto del
residuo** (fix-and-optimize / exact finishing), che trasformerebbe l'hard-0 del path input da
probabilistico a deterministico, ed e' coerente con la filosofia dei repair deterministici gia'
di casa (e riusa asset esistenti: DayAssignmentCsp, scheletro enumeratore 467).

## Findings

### 1. Fondamento teorico del portfolio/restart (conferma della proposta) - HIGH
Gomes & Selman: le distribuzioni dei tempi di successo della ricerca combinatoria randomizzata
sono heavy-tailed; restart periodici o N run indipendenti (portfolio) neutralizzano la coda e
sono la pratica canonica [1][2]. Le strategie di restart universali (Luby) sono la variante
sequenziale log-ottimale [3]. Il portfolio multi-start DRAFT-005 gia' costruito e' quindi
l'implementazione corretta della cura standard: N=3-4 run portano P(almeno un hard-0) da ~50%
per-run a ~87-94% (stima da n=2, da raffinare con run6).

### 2. Determinismo del seed NON e' una cura - HIGH
Timefold: environmentMode REPRODUCIBLE fissa il seed, ma la nostra terminazione e' wall-clock
(`unimprovedSecondsSpentLimit`), che rompe comunque la riproducibilita'; e congelare un seed
"fortunato" e' fragilita' travestita (la doc OptaPlanner nota che il seed variabile e' "piu'
robusto contro un seed sfortunato") [4][5]. Non percorribile come soluzione.

### 3. COMPLEMENTO NUOVO: exact finishing del residuo (fix-and-optimize) - HIGH
Letteratura matheuristics per university timetabling (Lindahl/Sorensen/Stidsen, DTU): si fissa
la gran parte della soluzione e si risolve ESATTAMENTE (MIP/CP) un sottoproblema piccolo [6].
Applicato al nostro caso: quando a fine ciclo restano k lezioni non assegnate (k~16,
concentrate su 2 risorse strette), il sottoproblema "k lezioni x celle candidate x catene di
bump limitate" e' minuscolo e si risolve in modo esatto e deterministico. Asset gia' in casa:
`DayAssignmentCsp.enumeratePairsRecursive` (enumerazione esaustiva con early-abort) e lo
scheletro enumeratore del TASK-467. Costo: medio (un pass "ExactFinisher" post-repair,
flag-gated). Beneficio: hard-0 DETERMINISTICO sul path input, niente lotteria ne' N run.

### 4. Conflict-based statistics (IFS/UniTime, Muller) - MEDIUM
Tecnica provata nei solver UniTime/ITC: statistiche pesate sui conflitti guidano QUALE
assegnazione esistente sfrattare quando si piazza una lezione in conflitto, evitando cicli
[7][8]. Non serve adottare IFS: la sola idea (bump selection guidata dai conflitti ricorrenti)
e' un upgrade mirato per HoleRepair/BoxRepair se l'ExactFinisher non bastasse.

### 5. Ruin&Recreate mirato sulle entita' non assegnate - MEDIUM
Timefold community ha il Ruin&Recreate (issue #550 / PR 1010) [9]; il nostro TASK-420 rimosse
una fase R&R INERTE com'era configurata, non il meccanismo in se'. Un R&R MIRATO (selettore
custom su unassigned + vicinato delle risorse strette) e' un'alternativa low-medium alla LS
cieca, ma resta stocastico: inferiore all'exact finishing per l'obiettivo affidabilita'.

### 6. Scartate con motivo
- **Pesi adattivi/shifting penalty** [10]: cambia le dinamiche del modello a run-time,
  contro la nostra architettura a pesi fissi ratificati. No.
- **Multithreaded/partitioned search**: enterprise-only in Timefold [11]; il parallelismo
  utile a noi e' gia' ottenibile lanciando N processi portfolio.
- **Reheating SA / diversificazione tabu**: leve generiche gia' assorbite dal benchmark
  metaeuristiche storico; nessuna evidenza specifica per il nostro failure mode.

## Confidence Assessment
Sicuri: il portfolio e' la cura standard e teoricamente fondata; il seed-fixing non e' una
cura; l'exact finishing e' pratica consolidata in letteratura e a costo medio da noi per gli
asset CSP esistenti. Incerti: la stima P(fail)~50% per-run e' da n=2 (il run6 discriminatorio
la raffina); il costo reale dell'ExactFinisher dipende da quanto e' pulita l'interfaccia dei
repair (da spike).

## Sources
1. Gomes & Selman, constraint-programming.com/letters/Papers/v1/gomes-selman.pdf
2. Williams/Gomes/Selman, heavy-tails backdoors restarts (cstheory ref)
3. Luby restarts, cs.utexas.edu/~marijn/publications/rapid.pdf + or-tools manual restart
4. docs.timefold.ai running-the-solver (environmentMode/seed)
5. docs.jboss.org EnvironmentMode (NON_REPRODUCIBLE robustness note)
6. orbit.dtu.dk "A fix-and-optimize matheuristic for university timetabling"
7. muller.unitime.org (IFS + conflict-based statistics, LSCS'04)
8. unitime.cz/present/patat08itc.pdf (ITC2007 hybrid solver)
9. github.com/TimefoldAI/timefold-solver issue #550 / PR 1010 (Ruin&Recreate)
10. academia.edu Tabu search examination timetabling (adaptive shifting penalty)
11. docs.timefold.ai enterprise-edition (multithreaded/partitioned)
