# Research Report: Violation-directed repair in OR/timetabling literature

**Date**: 2026-09-23
**Depth**: exhaustive (9+ ricerche, 3 sessioni di scraping)
**Confidence**: HIGH sul quadro d'insieme, MEDIUM su due dettagli singoli (segnalati)

**Domanda di maintainer** (22/09): abbiamo riscontri in letteratura per il
ViolationScopedSteepestFinisher (TASK-638)? E soprattutto: non e' che e' un side
effect del non aver capito come offrire quelle mosse DENTRO la fase di solve
principale, con un'architettura piu' lineare che lascia tutto al solver?

## Executive Summary

L'approccio ha precedenti forti e diretti in letteratura: e' la famiglia
"repair-based / constraint-guided local search", con QuikFix (timetabling),
LocalSolver (repair dentro LS commerciale), Guided Local Search (penalita'
sulle violazioni dentro il loop) e la tradizione fix-and-optimize delle
matheuristic. La collocazione come FINISHER deterministico post-LS invece che
dentro il loop principale e' anch'essa letteratura codificata (le architetture
vincenti ITC e le matheuristic a strati); la variante "dentro il loop" esiste
(GLS, strategic oscillation) ma cambia accettazione/pesi, non genera mosse, e
in Timefold non c'e' supporto nativo per move selection guidata da constraint
match. Verdetto: non e' un workaround, e' una delle due forme canoniche; la
forma alternativa (guidare il solver principale) e' fattibile ma e' un cambio
di metaheuristica, non una semplificazione.

## Findings

### (a) Precedente per move selection guidata dalle violazioni: SI', canonico

- **Guided Local Search (Voudouris & Tsang, anni '90)**: meta-heuristica
  penalty-based che "sits on top of local search algorithms to help guide them
  out of local minima" aggiungendo penalita' dinamiche sulle feature violate.
  Applicata a SAT/MAX-SAT, exam timetabling (Hadjidj & Drias, GRASP+GLS),
  scheduling. E' il precedente piu' citato per "usa le violazioni per guidare
  dove cerca la LS". [HIGH, multipla]
- **Constraint-based local search (Van Hentenryck & Michel, Comet)**: le
  violazioni sono cittadini di prima classe del linguaggio; l'obiettivo e'
  la somma di violazioni e ogni mossa e' valutata per il suo effetto su di
  esse. Il nostro "parti dai match hard-negativi" e' la stessa assunzione
  di fondo. [HIGH]
- **SAT repair heuristics (WalkSAT lineage)**: la selezione della variabile
  da flippare nasce dalle clausole insoddisfatte. QuikFix porta ESATTAMENTE
  questa tradizione nel timetabling (vedi (b)). [HIGH]

Differenza dichiarata: GLS cambia la FUNZIONE OBIETTIVO vista dal solver
(rendere le zone violate piu' costose), noi cambiamo il CANDIDAT SET (generare
mosse solo attorno alle zone violate). Sono i due lati dello stesso principio;
il nostro e' piu' chirurgico e piu' cheap, il loro e' piu' generale.

### (b) Targeted repair / surgical local search: SI', con il paper gemello

- **QuikFix (Clark & Henz), "A Repair-based Timetable Solver"**: "adapts
  repair-based heuristic search known in SAT solving to the timetabling
  domain", con multi-start, tabu list e strategie di selezione della mossa di
  riparazione. E' il nostro approccio quasi 1:1: identificare la violazione,
  generare mosse di riparazione attorno ad essa, applicare. Differenze: loro
  e' il solver intero, noi e' un pass di consegna su un residuo piccolo.
  [HIGH, PDF letto in abstract/indice]
- **LocalSolver, "Solution Repair by Inequality Network Propagation"**:
  meccanismo commerciale di repair dentro local search che "incrementally
  addresses constraint violations", con garanzia di al piu' una riparazione
  necessaria per vincoli binari. Prova che il pattern (violation -> repair
  mirato -> continua LS) vive dentro un prodotto industriale. [MEDIUM-HIGH,
  abstract]
- **Hybrid CSP solving (Monfroy, Saubion, Lambert)**: ibridi local search +
  constraint propagation con euristiche CONFLICT-BASED: la propagazione dice
  dove sono i conflitti e la LS li attacca. [MEDIUM]

Il nostro contributo specifico rispetto a questi: la valutazione incrementale
su TUTTO il constraint set (non solo i vincoli violati) con oracolo
improve-or-noop full-hard, che rende il pass formalmente non-danneggiante.
Questo lo abbiamo dovuto costruire: nessuna delle fonti dichiara la garanzia
di no-regression come contratto del pass.

### (c) Dentro il loop principale o come finisher? Entrambe le forme esistono; la nostra e' codificata

E' il cuore della domanda di maintainer. La letteratura ha ENTRAMBE le
architetture, e la scelta e' un trade esplicito, non una gerarchia:

- **Dentro il loop** = Guided Local Search (penalita' dinamiche nell'obiettivo
  che la LS principale ottimizza), strategic oscillation di Glover (oscillare
  attraverso il confine di fattibilita' usando l'infeasibilita' come guida),
  Comet (l'intero linguaggio e' violation-driven). NOTA TECNICA: in tutte
  queste il meccanismo cambia ACCETTAZIONE o PESI, non genera direttamente le
  mosse; la LS continua a proporre dal suo neighborhood completo e il segnale
  violazione la spinge verso le zone giuste.
- **Fuori dal loop, a strati** = le matheuristic fix-and-optimize (vedi (e)),
  le architetture ITC che separano metaheuristica di qualita' e repair di
  fattibilita', e il nostro staged cycle.

Tre fatti specifici del nostro contesto che la letteratura conferma rilevanti:

1. **Timefold non offre il pezzo che servirebbe per la forma "dentro"**:
   nessun supporto nativo per move selection / entity selection indicizzata
   per constraint match (verificato: risposta del team su StackOverflow +
   la nostra esperienza: ConstraintMatchAwareInjector non e' consultabile dai
   move selector config). La forma "dentro" richiederebbe un custom phase
   comunque, cioe' lo stesso codice collocato altrove. [MEDIUM: SO + esperienza
   diretta]
2. **Il residuo che curiamo nasce COME classe di mosse assenti, non come
   esplorazione mancante**: diagnosi TASK-608 (COMPOSITE_MISSING) - nessuna
   singola mossa migliorativa esiste e nessuna LS gira sullo stato consegnato.
   La LS principale non "non trova" quelle mosse per sfortuna: le pattern che
   le richiedono (swap composito contesto-target) sono nel suo neighborhood
   solo come sequenze net-neutrali che un decider accept-if-better non
   attraversera'. La letteratura GLS risponde a questo con le penalita'
   dinamiche; strategic oscillation con l'escursione controllata
   nell'infeasible; noi con il finisher dedicato. Tutte e tre sono risposte
   legittime allo stesso fenomeno (local optimum rispetto all'hard).
3. **A consegna il problema e' un residuo 0..-3 hard su soluzione converssa**:
   la forma "dentro" ri-aprirebbe il campo medium all'ultimo minuto con
   rischio di regressioni ratificate; il finisher oracle-bounded e'
   meccanicamente no-op-safe. E' lo stesso argomento delle matheuristic
   fix-and-optimize: sub-problemi piccoli, esatti, a fine pipeline.

CONCLUSIONE per la domanda di maintainer: NON e' un side effect di un
misunderstanding. La forma lineare "tutto al solver" esiste in letteratura ma
richiede il meccanismo GLS/oscillation DENTRO il decider (noi lo possediamo:
il decider MT-LS e' codice nostro) e pagherebbe in complessita' di calibrazione
(pesi dinamici che interagiscono con M32/M20/M-BF...) cioe' esattamente il
tipo di Landscape che il progetto ha gia' esplorato e chiuso con la
metaheuristica EXHAUSTED (campaign-status 2026-08-05). Il finisher e' la
forma che il dominio Timefold + il nostro pipeline rendono naturale.

### (d) Rapporto con LNS / destroy-and-repair

LNS distrugge una regione e la ricostruisce (spesso con la stessa CH o un
greedy). Il nostro finisher e' il DUALE: non distrugge nulla, identifica la
regione malata (i match hard-negativi) e cerca esaustivamente nel suo
intorno. Il ponte tra i due in letteratura e' la famiglia **fix-and-optimize**:
- "A fix-and-optimize matheuristic for university timetabling" (J Heuristics
  2018, DOI 10.1007/s10732-018-9371-3): "iteratively fixes the values of some
  variables by heuristic decisions, generating subproblems that can be exactly
  solved by MIP" con "problem specific knowledge" nella scelta di cosa
  liberare. [HIGH]
- La nostra selezione target (lesson delle violazioni + contesto) e'
  l'analogo esatto della loro selezione di variabili da liberare; la nostra
  "risoluzione esatta del sottoproblema" e' steepest descent incrementale su
  pieno constraint set invece di MIP (stessa classe: deterministica, completa
  sul sottospazio, improve-or-noop).
- ALNS (Ropke & Pisinger lineage): gli operatori destroy adattivi includono
  operatori "worst removal" / "related removal" guidati dal costo; la
  variante "violation-guided destroy" e' riconosciuta come la specializzazione
  per vincoli hard. Il 638 e' un ALNS con destroy = vincolo violato e repair
  = mossa singola migliore. [MEDIUM: inferenza da survey, non paper singolo]

### (e) Crossover esatti/metaheuristiche guidato dalle violazioni: SI', e' un filone con nome

- **Matheuristic fix-and-optimize** (sopra): il filone principale; varianti
  robuste per timetabling under uncertainty (hal-03449920) e parallele per
  ITC (DTU). [HIGH]
- **LocalSolver**: propagation (tecnica esatta/CP) dentro LS commerciale. [HIGH]
- **Hybrid CSP + LS** (Monfroy/Saubion): propagation e conflict heuristics
  dentro framework di ricerca LS. [MEDIUM]
- **Strategic oscillation** (Glover): la metaheuristica esplicitamente
  disegnata per abitare il confine feasible/infeasible e usare le violazioni
  come bussola. [HIGH, canonico]

Il 638 sta in questo incrocio: ricerca locale (metaheuristica) + selezione
del sottospazio guidata dai vincoli (tecnica CP) + discesa greedy completa
sul sottospazio (metodo esatto). La definizione di "matheuristic" della
letteratura (Dahan/Stidsen et al) lo copre.

## Risposta composta alle 5 domande

| Q | Risposta | Precedente piu' vicino |
|---|----------|------------------------|
| (a) move selection guidata da violazioni | SI', canonico (GLS, Comet, SAT repair) | GLS Voudouris-Tsang |
| (b) targeted repair chirurgico | SI', paper gemello | QuikFix (Clark-Henz); LocalSolver repair |
| (c) dentro il loop vs finisher | Entrambe codificate; la nostra e' la forma fix-and-optimize/ITC; la forma "dentro" e' GLS/oscillation e richiede calibrazione dei pesi dinamici | fix-and-optimize vs GLS |
| (d) rapporto LNS | Duale del destroy-and-repair; == ALNS con destroy=violazione, repair=steepest | fix-and-optimize (LNS esatto) |
| (e) crossover esatti/metaheuristics | SI', filone "matheuristic" + hybrid CSP/LS + strategic oscillation | fix-and-optimize university timetabling |

## Confidence Assessment

- HIGH: esistenza e forma di GLS, QuikFix, fix-and-optimize, strategic
  oscillation, Comet/constraint-based LS (fonti multiple concordi, DOI/PDF
  verificati).
- MEDIUM: LocalSolver repair (solo abstract), mappa ALNS destroy-guided
  (inferenza da survey), assenza di supporto nativo Timefold per
  constraint-match move selection (una risposta del team + esperienza diretta;
  il setaccio PR 2610/2642 potrebbe cambiarlo in futuro).
- Non verificato: se qualcuno in letteratura dichiara la garanzia
  improve-or-noop sul FULL constraint set come contratto formale del repair
  pass (noi la abbiamo; nessuna fonte la espone come teorema). Il nostro
  contribute artigianale resta la composizione: full-set oracle + delivery
  placement + deterministicita'.

## Sources

1. Voudouris & Tsang, Guided Local Search (GLS) for SAT/MAX-SAT e applicazioni
   scheduling - researchgate.net/publication/345958582
2. Hadjidj & Drias, GRASP and Guided Local Search for examination timetabling -
   semanticscholar.org/paper/ab632ba9bd95abe088bfb26d255d11b5230e7d30
3. Clark & Henz, QuikFix: A Repair-based Timetable Solver -
   metaparadigm.com/~mclark/quikfix-paper.pdf (+ semanticscholar b921d6e1)
4. LocalSolver, Solution Repair by Inequality Network Propagation -
   laas.hal.science/hal-02866559/document
5. Dahan et al / Stidsen, A fix-and-optimize matheuristic for university
   timetabling, J Heuristics 24 (2018) - doi.org/10.1007/s10732-018-9371-3
6. A robust fix-and-optimize matheuristic for timetabling under uncertainty -
   hal.science/hal-03449920/document
7. A parallelized matheuristic for the International Timetabling Competition -
   backend.orbit.dtu.dk/ws/files/276915746
8. Monfroy, Saubion, Lambert et al, Hybrid CSP Solving (LS + propagation,
   conflict-based heuristics) - link.springer.com/chapter/10.1007/11559306_8
9. Glover, strategic oscillation / tabu search with infeasible solutions
   (letteratura canonica, emersa in piu' ricerche)
10. Van Hentenryck & Michel, constraint-based local search / Comet (canonico)
11. Timefold/OptaPlanner: constraint-match-guided entity selection non
    supportato nativamente (risposta team su StackOverflow, ricerca 22/09 sera)
12. Breaking Down the Timetable Puzzle: IST algorithmic framework (GLS+ALNS+
    VNS in production timetabling) - hackernoon.com (divulgativo, usato solo
    come conferma di adozione industriale del mix)

## Follow-up suggeriti (non eseguiti: decisione di maintainer)

- Se la forma "dentro il loop" interessa comunque: il posto giusto e' il
  decider MT-LS (codice nostro) con acceptance penalizzata sulle famiglie
  hard residue (mini-GLS); banco prima su catalogo, costo di calibrazione
  non trascurabile. E' la "architettura lineare" di maintainer, ed e' fattibile,
  ma e' un esperimento metaheuristico completo, non una semplificazione.
- Il setaccio upstream rc-1 -> 2.7.0 finale (29/09) potrebbe includere
  primitive che avvicinano Timefold alla move selection guidata (PR 2610
  Neighborhoods e' gia' meta): da ri-guardare a fine mese.
