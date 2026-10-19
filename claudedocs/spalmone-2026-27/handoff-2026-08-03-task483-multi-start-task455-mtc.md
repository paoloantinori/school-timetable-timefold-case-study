# HANDOFF 2026-08-03 (zai). TASK-483 multi-start DONE + TASK-455 M-TC A/B verdict.

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity`. Tutto pushato. Regole
> standing invariate: verdetti dai raw, commit+push, tempi assoluti, handoff+/clear, mai fermo,
> NESSUNA adozione (source resta golden, M-TC default-OFF, multi-start opt-in OFF).

## TASK-483 (multi-start retry incapsulato) - DONE

Il multi-start e' dentro il prodotto dietro flag DEFAULT-OFF (`planner.multistart.enabled`, opt-in
come GRASP). Un solo comando Risolvi: se il ciclo staged finisce con hard residuo ritenta con
costruzione GRASP randomizzata (tentativo 1 deterministico, >=2 seeded), pick-best, stop a 0-hard.
Core puro `MultiStartRunner` (sibling PortfolioPickBest, testabile senza solve). Stato osservabile
`stagedAttempt`/`stagedMaxAttempts`/`stagedBudgetSeconds` + UI "Tentativo X di Y" + badge esito
(Orario valido / N conflitti) + hint durata (fino a ~Z min dal budget). Zero regressione quando OFF
(path singolo ciclo byte-identical; gate esterno = gate singolo).

Review: /simplify (4 agent) + /code-review high (Opus) -> 1 Important (busy-guard gap in
construction dei tentativi 2+: stagedStage null -> isBusy false + stopSolving no-op; fix:
runStagedCycle re-pubblica STAGE_CONSTRUCTION all'entry) + 1 Minor (restore saveSchedule non
wrapped; fix: try/catch + lastSolverError). 979 test pass exit 0 + ui-typecheck + ui-build.

Commit: 2927979b (core) + questo handoff. NESSUNA adozione: la ratifica default-ON e' di maintainer
(numeri: p(0-hard)=0.750 strict, N=3 -> P(>=1)>0.95, costo quasi nullo sui bacini buoni).

## TASK-455 (M-TC) A/B controllato seed-6 - VERDETTO (b), cura con prezzo = penale falsa

Prima misura di M-TC su un solve VERO con caso-cattivo noto e riproducibile (seed-6, source=input,
stesso seme dell'overnight badseed-frequency; unica variabile `-Dplanner.tag-capacity.enabled=true`).
Banner verificato (`M-TC lab tag-capacity: enabled=true`, rooms-per-tag meccanica=1).

Tre risposte (raw, non SUMMARY):
- **(a) room-null sparito, 0-hard.** OFF -1hard (H29, lezione meccanica G04 T15) -> ON 0hard.
  M-TC ha RELIEF-ATO la collisione: overflow meccanica 0 a fine run (1 in OFF). La LS, spinta dalla
  penalta', ha spostato la lezione fuori dalla cella satura.
- **(b) costo medium -204.714** di cui **-120.000 = penalta' M-TC stessa** (120 match x 1000). KPI:
  full-day -0,8pp, ricorrenza unpinned flat (75,5%), buchi interni 0, fine-anticipata +42, wall +10%.
  Scomposizione dei 120 match per tag (replicando il groupBy sull'export): **acconciatura 107 +
  moda 13 + meccanica 0 + altri 0**.
- **(c) esito (b): cura che chiude l'hard ma con prezzo = 100% penale FALSA su co-presenza PRATICATA
  dal golden** (acconciatura/moda) che M-TC non esenta (a differenza di H2/H-COPRESENCE depth>=2).
  NON e' un problema di peso (trappola M-TR: overflow praticati non scarsi). La leva giusta e'
  l'ESENZIONE co-presenza praticata (mirror H-COPRESENCE). Con esenzione, su seed-6 M-TC sparerebbe
  0 match -> cura pulita (0-hard, medium ~come OFF). Tracciato in TASK-485 (con key follow-up:
  practiced-status della coppia meccanica G04+G13 da verificare sul golden depth map).

NESSUNA adozione: M-TC resta default-OFF. Ratifica a maintainer solo dopo che TASK-485 (esenzione) e'
misurata pulita.

## PUNTATORI

- TASK-483 task: backlog (Done, AC tutte checked, DoD checked).
- TASK-455 verdetto: `task455-results/VERDICT-ab-mtc-seed6-2026-08-03.md` + raw `task455-results/ab-mtc-seed6/`.
- Baseline OFF: `task480-results/badseed-frequency/grasp-seed-6/`.
- Script M-TC seed-6: `scripts/benchmark/task455-mtc-seed6.sh`.
- TASK-485 (esenzione co-presenza M-TC): backlog, To Do, milestone m-12.
- Commit core TASK-483: 2927979b (+ questo handoff e il verdetto).

## APP / DB

App DOWN (lo script M-TC fa kill-app a fine capture). DB `multistart` ancora up (volume persistente,
M-TC final grid dentro). Per ripristinare production: `./planner.sh stop && ./planner.sh start production`.

## AGGIORNAMENTO: esenzione co-presenza M-TC (TASK-485) DONE

A/B di conferma (seed-6, M-TC ON + esenzione celle practiced depth>=2, mirror H-COPRESENCE
riusando isCoPresenceCell, stesso bridge di coPresenceCap). Pre-condizione verificata col numero:
`meccanica|WEDNESDAY|13:00` golden depth=1 (NON praticata) -> esenzione lascia fuori il target.

Esito: **0-hard** (cura preservata), **M-TC match 120->0** (penalita' falsa -120.000 SPARITA),
medium -1.464.102 -> **-1.353.502**. MA medium NON tornato a OFF (-1.259.388): -94.114 residuo e'
**displacement reale** (M20 -53k, M15 -31k: la LS sposta lezioni per dare stanza alla meccanica),
non penalita' falsa. Full-day -0,8pp identico con/senza esenzione -> l'esenzione ha rimosso SOLO
la parte falsa, il costo reale del cure resta.

L'esenzione e' il fix corretto del problema false-penalty. Il cure ha un costo reale ~-94k medium
(non nullo). KEY OPEN QUESTION per la ratifica di maintainer: il -94k e' costo del cure su seed-6 o
M-TC perturba la LS anche sui bacini buoni? A/B good-seed non fatto. M-TC resta DEFAULT-OFF.

Raw ora versionati: analysis.json + score.txt + kpi-*.txt per ab-mtc-seed6, ab-mtc-seed6-exemption,
grasp-seed-6 (baseline). Verdetto completo: task455-results/VERDICT-ab-mtc-seed6-2026-08-03.md.
TASK-485 Done. Commit: questo push.
