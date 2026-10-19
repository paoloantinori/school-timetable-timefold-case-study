# DESIGN - Adaptive move-stream weights (TASK-500, Enterprise reimplementation #3, 2026-08-15 sera)

> Mandato Fedora 15/08 sera. Brief: backlog TASK-500 (form Lectio vs UCB1, innesto
> probabilityWeightFactory, tre rischi con mitigazione, gate 2+ run). Design PRIMA,
> prototipo flag default-OFF, A/B run1+run2 appaiato (lezione 499: n=1 inganna).

## 1. Il problema e la letteratura

L'union move selector della fase B-TIMESLOT pesa gli stream STATICAMENTE (change 5,
swap 3, pillarChange 2, pillarSwap 1; room 2/1 nella fase B-ROOM) da sempre: quei numeri
sono del 2026-03 (benchmark TASK-386) e la campagna metaheuristic ESAURITA (08-05) ha
mostrato che i knob STATICI di quella famiglia sono FLAT. La letteratura timetabling
(research_nearby-selection-efficient-implementations_2026-08-15.md): UniTime (ITC2007),
GOAL (ITC2011), Lectio (ITC2011, scuola superiore danese, il dominio piu' simile al
nostro) e ITC2021 usano TUTTI multi-neighborhood con pesi adattivi o ALNS: il
apprendimento in-run e' la famiglia che non abbiamo mai provato (dynamic tuning in-run,
non static pre-tuning: NON coperta dalla campagna esausta).

## 2. Forma scelta: pesi adattivi Lectio-style (NON UCB1)

DECISIONE: segment-accumulation adaptive weights stile Lectio. Motivazione:

1. **Segnale**: il nostro reward naturale e' l'accept-rate per stream (accettazioni /
   valutazioni), un average-reward: la forma Lectio lo consuma direttamente. UCB1
   aggiunge un termine di esplorazione sqrt(log N / n_i) che introduce un iperparametro
   (scala C) da tunare alla cieca sul nostro dominio: un secondo knob oltre ai tre
   della forma base, senza evidenza letteratura timetabling che lo giustifichi.
2. **Testabilita'**: la regola di update e' una funzione pura (counters -> pesi),
   tabellabile in unit test L0 esattamente come isDoomedAtBoundary (requisito del
   mandato). UCB1 e' tabellabile anch'esso ma con stato per-arm piu' ricco (pull count
   + reward cumulativo) che moltiplica i casi.
3. **Letteratura**: Lectio e' il sistema piu' vicino al nostro dominio (scuola
   superiore, vincoli hard fitti) e usa esattamente questa forma (performance recente
   per operatore, ripeso a segmenti).

**Regola di update (segment accounting) - CORRETTA in implementazione (il table test
ha catturato che la prima forma poteva solo demuovere)**:
- counters per stream: evals_i, accepts_i (il loop del decider e' l'unico scrittore).
- ogni SEGMENT_EVALS globali (default 4.000): reward_i = accepts_i / evals_i;
  avg = media PESATA SULLE BASI dei reward degli stream con evals >= MIN_EVALS (50);
  weight_i = clamp(BASE_i * reward_i / avg, LO * BASE_i, HI * BASE_i)
  con LO = 0.5, HI = 2.5, BASE = i pesi ratificati statici.
- La forma e' MEDIA-PRESERVING: uno stream sopra la media sale (fino a 2.5x base),
  uno sotto scende (fino al floor 0.5x), uguaglianza totale lascia le basi intatte.
  La prima forma (EPSILON + (1-EPSILON)*reward/best) aveva range [eps, 1]x base: poteva
  SOLO demuovere, il clamp hi era irraggiungibile. CORREZIONE documentata nel test.
- counters resettati a ogni segment (finestra, non cumulativo: la performance RECENTE
  comanda, come Lectio).

Perche' questi numeri: LO 0.5 e' il floor anti-feedback-loop del rischio 1 (uno
stream a zero accettazioni tiene META' del suo peso ratificato: nessuno stream muore,
quindi nessun apprendimento resta intrappolato); HI 2.5 impedisce il monopolio (uno
stream fortunato non supera 2.5x il suo peso ratificato: l'union resta mista). I knob
(segment, minEvals, lo, hi) sono letti da property con clamp.

## 3. Punto di innesto

1. **Pesi**: probabilityWeightFactoryClass al posto di fixedProbabilityWeight sui figli
   dell'unionMoveSelector delle fasi LS (verificato sul jar 2.3.0: stesso
   ProbabilityMoveSelector, stessa cache machinery che gia' gira coi pesi fissi: il
   factory e' un drop-in, costo per chiamata = una lettura volatile). Wiring
   programmatico in runStagedSolver PRIMA di buildSolver (lezione 499: post-build e'
   no-op silenzioso), solo path staged, solo quando il flag e' armato.
2. **Statistiche**: il nostro MultiThreadedLocalSearchDecider e' il collettore naturale:
   valuta OGNI mossa e conosce l'esito. Classificazione della mossa in stream via
   preview API pubblica (getPlanningValues: runtime type Room -> stream room,
   Timeslot -> stream timeslot; classe per change/swap/pillar). Un bump AtomicLong per
   eval e uno per accept: costo O(1) ammortizzato su ogni valutazione.
3. **Dipendenza dichiarata**: senza MT-LS non ci sono statistiche (il path single-thread
   non passa dal nostro decider): il comportamento e' gracefully degradato a pesi
   statici (peso = BASE, mai toccato). Il banner lo dichiara.

Stream enum: TS_CHANGE, TS_SWAP, TS_PILLAR_CHANGE, TS_PILLAR_SWAP, ROOM_CHANGE,
ROOM_SWAP, OTHER (contato ma mai ripesato: mosse non classificate non entrano nel
learning).

## 4. I tre rischi del brief e le mitigazioni

1. **Feedback loop instabile** (peso crolla e non recupera): EPSILON-floor (0.25x base
   sempre), clamp [0.5, 2.5]x, segment reset (finestra scorrevole: un periodo sfortunato
   si dimentica in un segment), normalizzazione RELATIVA al best (mai assoluta: se tutti
   gli stream vanno male insieme i pesi non collassano).
2. **Varianza / non-riproducibilita'**: il gate e' run1+run2 appaiato con direzione
   CONCORDE (hard 0 binario + KPI in banda + medium miglior-o-neutro), run3 tiebreak se
   discordi. Il numero wall NON e' claim del gate (lezione 499): se il wall migliora,
   e' bonus da documentare, non promessa.
3. **Thread-safety con 8 thread MT-LS**: counters AtomicLongArray (bump lock-free);
   l'update del segment gira su UN thread (chi attraversa il confine via CAS su una
   flag AtomiceBoolean "updating"), pubblica i pesi su un volatile double[] scritto
   una volta per segment; le factory leggono il volatile. Nessuna struttura condivisa
   mutabile fuori da questi due punti.

## 5. Prototipo e gate

- Flag planner.ls.adaptive-weights.enabled DEFAULT OFF (opt-in, forma standard).
- Unit test L0: updateWeights(counters, config) tabellato (segment, floor, clamp,
  normalizzazione, sotto-MIN_EVALS) + classificatore (stub moves con valori Room vs
  Timeslot). Pura, no solver.
- A/B run1+run2 appaiati (baseline-golden.sh, golden default, multi-start + MT-LS +
  skip-doomed-room ON entrambe): unica differenza il -D. Gate: hard 0, KPI in banda,
  medium miglior-o-neutro CONCORDE 2/2; discordia -> run3.
- VERDICT con raccomandazione per maintainer.

## 6. Cosa NON e' questo prototipo

- Non e' nearby selection (TASK-499 chiuso NON adottare: il wall non replica).
- Non tocca i pesi BASE ratificati: l'adattivita' e' un moltiplicatore clamped sopra
  di essi; col flag OFF il comportamento e' byte-identico a oggi.
- Non copre swap/pillar filtering (TASK-501 SUBORDINATO, non toccato senza verdetto
  del 500, come da mandato).
