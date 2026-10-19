# DESIGN - Nearby Selection community (filone Enterprise reimplementation #2, 2026-08-15)

> Mandato maintainer 15/08 pomeriggio: seconda ondata di feature Enterprise reimplmentate
> in community, dopo il multi-threaded LS (TASK-480, ratificato -25% wall). Design
> PRIMA, prototipo gated default-OFF, A/B con hard 0 + KPI.

## 1. Il problema

Il LS e' il 99% del wall (fase 2 ~59% + fase 5 ~40%). Nella fase B-TIMESLOT il
ChangeMoveSelector genera, per ogni lezione candidata, una mossa verso OGNI timeslot
del value range (1368 celle): la maggior parte finisce su weekday/slot fuori dal
template del corso, e la mossa viene VALUTATA (costo di score delta) e poi scartata
perche' M32 la penalizza. Il costo e' pagato prima dello scarto: e' pura dissipazione.
Il fenomeno e' noto dal TASK-413 (M32 nasce proprio per penalizzare la deviazione) e
dal badseed-structure-correlation (LS2-iters enormi sui seed sfortunati).

## 2. La reference Enterprise (per onesta' di confronto)

- OptaPlanner 8.x aveva NearbySelection in COMMUNITY prima del fork Timefold
  (kiegroup/optaplanner 8.x: NearbySelectionConfig, NearbyDistanceMeter,
  NearbySelectionMediator); con il fork (aprile 2023) nearby selection e' passata a
  Enterprise insieme a moveThreadCount (fonte: blog del fork timefold.ai/optaplanner-fork,
  "All OptaPlanner features are part of Community Edition, except for Multithreaded
  Solving and Nearby Selection").
- Meccanica Enterprise (docs OptaPlanner/Timefold): un ValueSelector wrappato che,
  per l'entita' selezionata, ordina i valori per distanza (NearbyDistanceMeter) e li
  campiona con una distribuzione biased (es. parabolic: i primi blocchi molto piu'
  probabili), con blockDistributionSize a controllare la larghezza. NON filtra mai:
  ogni valore resta raggiungibile con probabilita' non nulla (e' un ordering biased,
  non una restrizione).

## 3. La nostra versione community: FILTRO A GRADINI (non ordering)

Verificato sul jar 2.3.0: l'hook community e' `SelectionFilter<Solution_,T>` con
`accept(scoreDirector, T)`, innestabile via `MoveSelectorConfig.setFilterClass` o
wiring programmatico. Due fatti architettonici decidono la forma:

1. Un filter su `valueSelector variableName="timeslot"` riceve SOLO il timeslot: non
   puo' sapere di quale corso e' la lezione. Inutile per il template.
2. Un filter sul MOVE selector del changeMove riceve la MOSSA completa
   (ChangeMove: lezione + timeslot destinazione): puo' chiedere il corso della lezione
   e la sua relazione col template. E' il punto d'innesto.

La distanza (l'analogo del NearbyDistanceMeter) e' la PROSSIMITA' AL TEMPLATE, con
gradini gia' esistenti nel dominio:
- grado 0: cella template del corso (weekday+slot presenti in
  SchoolSchedule.getWeeklyTemplateCells(courseId), stessa encodeTemplateCell di M32):
- grado 1: stesso weekday, slot diverso (cella non-template del giorno giusto);
- grado 2: stesso slot, weekday diverso (il "colonna" di cadence M32-preservante);
- grado 3: tutto il resto.

DECISIONE (filter, non bias): il prototipo FILTRA il grado 3 con probabilita' p_keep
( DISCLAIMER onesto nel banner: non e' la semantica Enterprise). Due ragioni:
(a) un SelectionFilter e' binario: la distribuzione biased NON e' esprimibile con
l'hook community senza reimplementare il ValueSelector interno (API impl, fragili);
(b) il costo-beneficio domandato dal mandato e' ridurre le valutazioni inutili: il
grado 3 e' la frazione dominante delle celle (stime sotto) e la sua utilita' e' quella
che M32 combatte. Il rischio di perdere mosse di fuga e' mitigato da p_keep
probabilistico: NON filtriamo mai a secco (dettaglio sotto).

## 4. Interazione con M32 (template deviation)

Complementari, non ridondanti. M32 opera DOPO la generazione: penalizza la mossa
fuori template che il forager ha gia' pagato di valutazione. Nearby-filter opera
PRIMA: la mossa grado-3 non viene generata. L'ottimo non cambia se le mosse grado-3
non servono; cambia il costo per step. Coerenza della metrica: M32 usa
isOnTemplateCell/isTemplateCellReference (TASK-419 single source): il filtro USA LA
STESSA chiamata per il grado 0, cosi' le due definizioni non possono divergere.

## 5. Il rischio: restringere le mosse di fuga

Il bad basin (firme H17/H28 dei census) potrebbe richiedere proprio mosse lontane per
uscire. Per questo:
- p_keep default 0.20 (20% delle mosse grado-3 PASSANO comunque): il filtro e'
  probabilistico, non una barriera. La frazione grado-3 che sopravvive e' ancora ~270
  celle per lezione templated.
- flag DEFAULT OFF + gate A/B: la domanda sperimentale e' se il risparmio di
  valutazione supera la perdita di qualita' di ricerca. Misura anche sui bad seed se
  il wall guadagna senza peggiorare l'hard finale.
- fallback naturale: se l'A/B mostra hard peggiori o KPI fuori banda, il grado-2
  (stesso slot altro weekday) resta libero e resta M32 a fare il lavoro: il prototipo
  si spegne e resta solo il design.

## 6. Piano di implementazione (prototipo gated)

- Classe nuova `solver/NearbyTemplateMoveFilter implements SelectionFilter<SchoolSchedule,
  ChangeMove<SchoolSchedule,?>>` (o la firma Move del jar: da verificare al compile):
  accept = true se (mossa non-e-timeslot) OR (grado 0/1/2) OR (random < p_keep).
  Grado via SchoolSchedule.isOnTemplateCell(courseId, toTimeslot) per grado 0 e
  confronto weekday/slotIndex per 1/2 (TimeslotSequence.slotIndexOf).
- Wiring: in ScheduleService.runStagedSolver, dopo il wiring MT-LS, se il flag e'
  armato: traversa i LocalSearchPhaseConfig del config, trova i ChangeMoveSelectorConfig
  della fase timeslot, setta il filter. Solo path staged (come MT-LS).
- Flag `planner.ls.nearby-template-filter.enabled` DEFAULT OFF (lettura LIVE per-build
  come le altre; knob `planner.ls.nearby-template-filter.keep-probability` default 0.20).
- Random seedless (Math.random? NO: usare il random del solver non e' raggiungibile
  dal filter: ThreadLocalRandom, documentato che il filter non e' riproducibile col
  grasp seed. ACCETTATO per il prototipo: l'A/B misura il wall, non il determinismo).

## 7. A/B (gate)

baseline-golden.sh, source=golden, MT-LS ON entrambe le braccia, multi-start ON.
Control: flag OFF. Treatment: flag ON. Metriche:
1. hard 0 entrambi (binario);
2. KPI in banda (full-day, ricorrenza, buchi);
3. WALL delle fasi LS (fase 2 + fase 5 dal log) e move evaluation speed dal log
   Timefold (atteso: piu' eval/sec se il filtro taglia);
4. step count / accept rate se visibili.

VERDICT in task499-results/ (task nuovo da creare col backlog form).
