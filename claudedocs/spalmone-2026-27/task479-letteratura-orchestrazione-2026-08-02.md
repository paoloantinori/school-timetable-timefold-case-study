# TASK-479 - Posizionamento in letteratura del layer di orchestrazione a livello di fase

Input per la sezione 3 ("posizionamento in letteratura") di `claudedocs/orchestration-layer-architecture.md`.
Ricerca via `crw search`/`crw scrape`, non duplica `claudedocs/research_hard-variance-timetabling_2026-08-01.md`
(quel report copre portfolio/restart e una prima menzione di fix-and-optimize per il caso
specifico dell'ExactFinisher; qui la stessa idea è ricollocata nella tassonomia generale e
allargata agli altri operatori del layer: staged pinning, gate a tier, repair con contratto).

## 1. Hybrid metaheuristics / matheuristics: la tassonomia classica

- **Puchinger & Raidl, "Combining Metaheuristics and Exact Algorithms in Combinatorial
  Optimization: A Survey and Classification"** (LNCS 3562, 2005).
  https://www.ac.tuwien.ac.at/files/pub/puchinger-05.pdf
  Definizione: divide gli ibridi in **collaborative** (gli algoritmi si scambiano
  informazioni ma restano entità separate, eseguiti sequenzialmente, intrecciati o in
  parallelo) e **integrative** (una tecnica è un componente subordinato incorporato
  nell'altra, con un algoritmo master e uno slave).
  Mappatura: il nostro layer è prevalentemente **collaborativo sequenziale** (staged
  pinning promuove/demuove tra fasi separate, i coordinatori pre-solve girano prima della
  metaeuristica), con un'eccezione **integrativa**: ExactFinisher è un algoritmo esatto
  incorporato come subroutine di riparazione dentro il ciclo staged.

- **Blum, Puchinger, Raidl & Roli, "Hybrid metaheuristics in combinatorial optimization:
  A survey"**, Applied Soft Computing 11(6), 2011 (versione breve: Blum & Raidl, BIOMA
  2010). https://repositum.tuwien.at/handle/20.500.12708/53274
  Definizione: unifica sotto una tassonomia comune sia gli ibridi
  metaeuristica+metaeuristica sia metaeuristica+esatto, distinguendo decomposizione,
  cooperative search e incorporazione di componenti esatte/di simulazione.
  Mappatura: i nostri pass deterministici (Box/Hole/Anchor/SurplusRebalance) sono
  "decomposizione" nel loro senso: ciascuno opera su una fetta ristretta del problema
  (un buco, un gruppo di giorni) invece che sull'intera soluzione.

- **Talbi, "A Taxonomy of Hybrid Metaheuristics"**, Journal of Heuristics 8(5), 2002
  (citato e ripreso in Blum & Raidl 2011). Definizione: assi ortogonali low-level vs
  high-level e relay vs teamwork per classificare l'ibridazione.
  Mappatura: il nostro layer è "high-level relay": le fasi si passano il testimone
  (relay) senza condividere lo spazio interno di ricerca (high-level), a differenza di
  un'ibridazione low-level che modificherebbe l'operatore di mossa della LS stessa.

- **Raidl & Puchinger, "Combining (Integer) Linear Programming Techniques and
  Metaheuristics for Combinatorial Optimization"** (Springer, 2008).
  https://link.springer.com/chapter/10.1007/978-3-540-78295-7_2
  Definizione: capitolo di raccordo che applica la tassonomia del 2005 specificamente ai
  casi con un solver MIP/ILP come componente esatto.
  Mappatura: è la lente più diretta su ExactFinisher, che è per l'appunto un componente
  di enumerazione esatta (non un MIP vero, ma con lo stesso ruolo) incorporato nel ciclo.

## 2. Fix-and-optimize / MIP-based LNS / POPMUSIC

- **Lindahl, Sørensen & Stidsen, "A fix-and-optimize matheuristic for university
  timetabling"**, Journal of Heuristics 24(4), 2018.
  https://link.springer.com/article/10.1007/s10732-018-9371-3 (anche
  https://orbit.dtu.dk/en/publications/a-fix-and-optimize-matheuristic-for-university-timetabling/)
  Definizione: fissa la maggior parte delle variabili di decisione usando conoscenza del
  dominio, crea un sotto-problema più piccolo sulle variabili restanti e lo risolve
  (quasi) all'ottimo con un solver MIP, iterando su partizioni diverse. Funziona come
  una large neighborhood search dove il "repair" della neighborhood è un solve esatto.
  Già citato nel report del 2026-08-01 (fonte 6) per l'ExactFinisher: qui la stessa fonte
  copre ANCHE lo staged pinning, perché la partizione fissato/libero è esattamente
  "pinned/unpinned" applicata all'intera pipeline, non solo al residuo finale.

- **Taillard & Voss, "POPMUSIC, Partial Optimization Metaheuristic under Special
  Intensification Conditions"** (2002).
  https://link.springer.com/chapter/10.1007/978-1-4615-1507-4_27
  Definizione: template generico per problemi grandi. Si decompone la soluzione
  corrente in parti, si aggregano alcune parti scelte in un sotto-problema e lo si
  ri-ottimizza (esattamente o euristicamente), ripetendo su tutte le parti.
  Mappatura: i repair mirati per (gruppo, giorno) o per cella scarsa (HoleRepair,
  SurplusRebalance) sono istanze POPMUSIC con criterio di selezione della parte guidato
  dal deficit/surplus misurato, non da un ciclo round-robin sulle parti.

- **Pisinger & Ropke, "Large Neighborhood Search"** (capitolo in Handbook of
  Metaheuristics, aggiornato 2010/2019).
  https://www.researchgate.net/publication/226905030_Large_Neighborhood_Search
  Definizione: LNS itera distruggi-e-ripara su un intorno ampio della soluzione
  corrente; il passo di riparazione può essere un'euristica o un metodo esatto
  (MIP/CP). È questa la formulazione generale di cui il fix-and-optimize è il caso
  con riparazione esatta.
  Mappatura: l'intero ciclo staged (RELEASE selettivo, repair, re-solve) è un LNS a
  grana di fase: "distruggi" = demozione selettiva dei pin, "ripara" = i pass
  deterministici o la LS successiva.

- **Adamo et al., descrizione fix-and-optimize, PATAT 2022** (formalizzazione F/U).
  https://patatconference.org/patat2022/proceedings/PATAT_2022_paper_28.pdf
  Definizione: le variabili si dividono in F (fissate) e U (libere); si ottimizza il
  sotto-problema ristretto a U con F congelato, e il risultato dipende fortemente da
  come F è scelto.
  Mappatura: è la formalizzazione letterale di "il solver gira sulle sole entità
  unpinned" del nostro ciclo staged, con F = pin autoritativi/promossi.

## 3. Multi-stage/phased pipeline nel timetabling

- **Müller, "ITC2007 solver description: A hybrid approach"** (Annals of OR / PATAT
  2008). https://muller.unitime.org/itc2007.pdf
  Definizione: tre fasi in sequenza, Iterative Forward Search (costruzione fino alla
  feasibility), Hill Climbing (ottimo locale), poi Great Deluge/Simulated Annealing per
  uscire dagli ottimi locali. È il solutore che vinse la traccia exam di ITC2007.
  Mappatura: è la controparte diretta della nostra pipeline a 5 fasi (CH lezioni, LS
  compattezza, repair, CH stanze, LS stanze): feasibility-first poi
  ottimizzazione, con transizione di fase decisa da una condizione osservabile (qui:
  fine della IFS quando la soluzione è feasible; da noi: fine di ogni fase quando lo
  score di quel tier converge).

- **Fawcett et al., "A Modular Multiphase Heuristic Solver for Post Enrolment Course
  Timetabling"** (ITC2007 track 2).
  https://www.cs.ubc.ca/~hutter/EARG.shtml/earg/papers08/cfawcett_patat08.pdf
  Definizione: fase costruttiva che produce un assegnamento parziale feasible rispetto
  ai vincoli hard, seguita da fase/i di miglioramento; l'architettura è modulare così le
  fasi si possono sostituire/tarare indipendentemente.
  Mappatura: è il precedente più vicino alla nostra scelta di rendere ogni pass
  (BoxRepair, HoleRepair, AnchorRepair, SurplusRebalance, LabDayConsolidation) un
  modulo indipendente e testabile in isolamento, invece di un'unica euristica monolitica.

- **Ali, Mouhoub et al., "A Multi-Phase Hybrid Metaheuristics Approach for the Exam
  Timetabling Problem"** (2016).
  https://www.worldscientific.com/doi/10.1142/S1469026816500231
  Definizione: tre fasi esplicite, pre-processing, costruzione, enhancement, sullo
  stesso schema feasibility-first via via raffinato.
  Mappatura: conferma che il pattern a fasi con ruoli distinti (preprocessa, costruisci,
  migliora) è ricorrente nel timetabling, non un'idiosincrasia nostra.

- **Burke, Hyde, Kendall, Ochoa, Özcan & Woodward, "Hyper-heuristics: A survey of the
  state of the art"**, JORS 64, 2013.
  https://people.cs.nott.ac.uk/pszrq/files/HHSurveyJORS2013.pdf
  Definizione: un hyper-heuristic è un metodo di ricerca che opera su uno spazio di
  euristiche (o componenti euristiche) invece che di soluzioni, selezionando/generando/
  adattando quale euristica applicare: un layer di controllo SOPRA le
  euristiche/metaeuristiche di base.
  Mappatura: il nostro layer è concettualmente più vicino a questo che a un multiphase
  solver fisso, ma con una differenza di grana importante (vedi conclusione sotto): un
  hyper-heuristic seleziona tra euristiche di MOSSA a ogni iterazione, il nostro layer
  seleziona/gate-a tra PASS interi ai confini di fase, con contratto misurabile invece
  che con una policy di selezione appresa/euristica.

## 4. Contratti tra fasi: improve-or-noop, rollback

- **Zilberstein & Russell, "Monitoring the Progress of Anytime Problem-Solving"**,
  AAAI 1996. http://rbr.cs.umass.edu/papers/HZaaai96.pdf
  Definizione: un algoritmo anytime restituisce una soluzione valida a ogni punto di
  interruzione, con qualità che non deve MAI peggiorare al crescere del tempo di calcolo
  (monotonicità).
  Mappatura: è la proprietà che il contratto improve-or-noop-con-revert (TASK-473) rende
  operativa nei nostri pass deterministici: lo score dopo un pass non è mai peggiore di
  prima del pass, esattamente come la garanzia anytime a ogni punto di interruzione.

- **Demirović, Chu & Stuckey, "Solution-Based Phase Saving for CP: A Value-Selection
  Heuristic to Simulate Local Search Behavior in Complete Solvers"**, CP 2018.
  https://research.monash.edu/en/publications/solution-based-phase-saving-for-cp-a-value-selection-heuristic-to/
  Definizione: dopo ogni restart, la ricerca CP "ricorda" i valori della miglior
  soluzione trovata finora e orienta l'assegnamento delle variabili verso il
  riprodurla, portando così una soglia di qualità attraverso i restart invece di
  ripartire da zero.
  Mappatura: è l'analogo per CP del nostro "quasi-pinning" (M32 template deviation) e
  dello staged pinning stesso: entrambi portano avanti, attraverso un confine
  discontinuo (restart per CP, transizione di fase da noi), l'informazione della
  miglior soluzione già trovata invece di perderla.

- **Chu (citato nella stessa linea di lavoro), identità "LNS = restarts + dynamic
  search + phase saving"** (riferita in
  https://www.researchgate.net/publication/325624849_Constraint_Programming_for_High_School_Timetabling_A_Scheduling-Based_Model_with_Hot_Starts).
  Definizione: la ricetta che rende un restart-based solver competitivo con la LS è la
  combinazione di restart, ricerca dinamica dell'ordine di variabili E phase saving.
  Mappatura: nel nostro caso il "restart" è il ciclo staged stesso (RELEASE selettivo
  seguito da re-solve), e il "phase saving" è il contratto di revert: senza di esso il
  RELEASE rischierebbe di perdere qualità già acquisita, esattamente il fallimento che
  il phase saving previene in CP.

## Tabella riassuntiva: operatore nostro -> nome in letteratura -> riferimento

| Operatore nostro | Nome in letteratura | Riferimento |
|---|---|---|
| Staged pinning (PROMOTION/demozione selettiva, `StagedPinPolicy`) | Fix-and-optimize (partizione F/U); collaborative sequential combination | Lindahl/Sørensen/Stidsen 2018; Puchinger & Raidl 2005 §2.1; Adamo et al. PATAT 2022 |
| Gate a tier (`timeslotTierHardScore` vs `ROOM_TIER_HARD_FAMILIES`) | Modular multiphase solver, feasibility-tier gating tra fasi | Müller (ITC2007 hybrid); Fawcett et al. (modular multiphase solver) |
| Pass deterministici con contratto improve-or-noop + revert (BoxRepair/HoleRepair/AnchorRepair/SurplusRebalance/LabDayConsolidation) | Anytime monotonicity; solution-based phase saving | Zilberstein & Russell 1996; Demirović, Chu & Stuckey 2018 |
| ExactFinisher (sub-solver esatto sul residuo, budget/depth) | Fix-and-optimize / MIP-based LNS con riparazione esatta (integrative: esatto incorporato come operatore di repair) | Lindahl/Sørensen/Stidsen 2018; Pisinger & Ropke (LNS survey); Puchinger & Raidl 2005 §3.1 |
| `CompactionPhase` | Integrative combination, ricerca esatta/deterministica incorporata come operatore VLSN-style | Puchinger & Raidl 2005 §3.1 (Very Large-Scale Neighborhood) |
| Coordinatori pre-solve (profili constrained-teacher, template M32) | Sequential collaborative combination (preprocessing prima della metaeuristica) | Puchinger & Raidl 2005 §2.1 |
| Selezione di QUALE parte scarsa riparare (HoleRepair/SurplusRebalance guidati dal deficit) | POPMUSIC (decomposizione + riottimizzazione mirata delle parti) | Taillard & Voss 2002 |
| Multi-start portfolio (proposto, famiglia TASK-467) | Restart portfolio contro distribuzioni heavy-tailed | Gomes & Selman (già in `research_hard-variance-timetabling_2026-08-01.md`) |
| Il layer nel suo complesso (governo dei confini di fase) | Più vicino a un layer di tipo hyper-heuristic ma scoped ai confini di FASE con contratto misurabile, non alla selezione di MOSSA | Burke et al. 2013 (hyper-heuristics survey) |

## Nota per la sezione 3 del documento di architettura

La differenza che vale la pena scrivere esplicitamente in `orchestration-layer-architecture.md`:
la letteratura hyper-heuristic e multiphase-solver descrive layer che scelgono QUALE euristica
applicare (a livello di mossa o di fase) sulla base di segnali di performance osservati durante
la ricerca. Il nostro layer aggiunge una proprietà che non è centrale in quella letteratura: un
**contratto verificato con revert automatico** per ogni pass di confine (misura pre/post, applica
solo se migliora, altrimenti annulla), reso possibile dal fatto che i nostri pass sono
deterministici e quindi il loro effetto è ricalcolabile e confrontabile in modo esatto. È una
garanzia più forte della semplice "anytime monotonicity" perché non dipende dal tempo di calcolo
ma da un confronto esplicito pre/post ad ogni singola applicazione dell'operatore.
