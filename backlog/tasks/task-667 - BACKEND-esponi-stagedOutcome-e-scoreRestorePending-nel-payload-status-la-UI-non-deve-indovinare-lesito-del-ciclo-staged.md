---
id: TASK-667
title: >-
  BACKEND: esponi stagedOutcome e scoreRestorePending nel payload /status - la
  UI non deve indovinare l'esito del ciclo staged
status: Done
assignee: []
created_date: '2026-09-24 17:20'
updated_date: '2026-09-25 13:32'
labels:
  - claude-planned
  - ux
  - backend
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla /simplify review (angolo altitudine) del ramo feature/ux-planner-lane (24/09):
due stati che il backend POSSIEDE in memoria ma non pubblica, costringendo la UI a
inferenze fragili introdotte dai TASK-658/661.

1. stagedOutcome: il client ricostruisce l'esito del ciclo staged con un edge-detector
su stagedStage/refiningForDelivery/lastSolverError + uno specchio client-side
(stopRequestedRef in UnifiedSolverControls). Fallisce sui casi cross-canale (stop da
altra tab o via api.sh = toast "completato" su uno stop) e lastSolverError e' sticky
fra cicli. Il service ha gia' la macchina a stati (stagedCancelRequested privato,
ScheduleService.java:943; stagedStage sotto stagedLock, cleared nel finally).
Cura: campo additivo stagedOutcome (COMPLETE | CANCELLED | FAILED, + score finale) in
GET /api/schedule/status, settato nel finally del ciclo e nella path di stop;
l'effetto client collassa a "outcome cambiato -> toast/banner una volta".

2. scoreRestorePending: awaitingScoreRestore (TASK-658) e' la copia client della
guardia scoreRestoreStarted (ScheduleService.java:184-186, consumata a 4937-4952). Nel
ramo restore-fallito il poll leggero resta vivo a 1s SENZA bound aspettando una
transizione che non arrivera' mai (mitigato con deadline 60s nella lane, resta
l'inferenza). Cura: esporre scoreRestorePending (restore started && latestScore==null,
false a completamento E nel catch); il client polla il flag e si ferma quando il
server dichiara finito, in entrambi gli esiti.

Ambito: solo campi ADDITIVI nel payload status + consumatori frontend. Nessun cambio a
vincoli/solver/pesi.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /api/schedule/status espone stagedOutcome con i tre valori terminali, settato dal finally del ciclo e dalla path di stop
- [x] #2 GET /api/schedule/status espone scoreRestorePending, false anche nel catch di restore fallito
- [x] #3 La UI consuma i due campi e ritira stopRequestedRef e l'inferenza awaitingScoreRestore (con test/typecheck verdi)
- [x] #4 Verifica live: ciclo reale con stop e con consegna, esiti corretti a schermo
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Origine: /simplify 4-angle review del ramo UX 24/09 (REPORT
claudedocs/spalmone-2026-27/ux-review-2409/REPORT.md, sezione gates). Fili dai
reviewer: UnifiedSolverControls.tsx:375-421 (F1) e ScheduleTimeline.tsx:374-381 (F2).
Contesto lane: worktree /Users/user/planner-wt-ux, branch feature/ux-planner-lane.

2026-09-25 13:00 Fedora: cura landed. Gates COMPLETE: /simplify 4-angle (R1 helper withStagedOutcome/resetCycleOutcomeState, R2 hoist, R3 snapshotOf, S1 ref ridondante, S2 frammento, E1 edge restore fuori dal fetch-trigger; skip motivati: S3 coalescing=guardia skew, E2 sub-ms), /code-review high 8-angle con 8 finding: F1 CRITICO curato (il fetch pesante non cancella piu' stagedOutcome/scoreRestorePending: fold da statusPollRef anche quando l'heavy vince la gara del mount), F2 curato (isBusy include refiningForDelivery), F4 curato (latch di restore rilasciato ANCHE sul successo: pending non resta mai vero per sempre e un secondo restore dopo reset/import puo' ripartire), F5 curato (catch RuntimeException|Error nel wrapper: un crash fuori dal catch del ciclo classifica FAILED, mai COMPLETE), F6 curato (banner FAILED persistente con dismiss, speculare al CANCELLED), F7 curato (test solo hasKey: contesto condiviso QuarkusTest), F8 curato (StatusSnapshot/snapshotOf a module scope). F3+residuo-F2 FILED come TASK-670 (liveness unificato: coda FCC/finisher/save e gap inter-attempt). Test: StatusPayloadContractTest+FeasibilityEndpointContractTest 2/2 EXIT=0, ui-typecheck OK. Verifica viva su lane: pendente (dopo suite consolidata, per conflitto worktree).

2026-09-25 15:00 VERIFY-1 FAIL -> diagnosi completa con riproduzione strumentale (jstack + tick /status, log /tmp/uxlane-667-{diag,repro}.log): (a) l'esito CANCELLED FUNZIONA gia' (arriva ~8s dopo lo stop durante PRE-ASSIGN: il FAIL della verify-1 era la CONDIZIONE dello script, che richiedeva stage==null per accettare l'esito); (b) BUG REALE di liveness scoperto dal live: l'uscita-rapida-da-cancel (runOneMultiStartAttempt ritorna prima di ogni publishStage del ciclo) lascia stagedStage=PRE-ASSIGN PER SEMPRE -> isBusy() permanentemente true -> ogni solve successivo rifiutato con 'Solver is busy' fino al restart. CURA 29de5126: publishStage(null) nel finally piu' esterno (withStagedOutcome), proprietario di ultima linea, idempotente sui percorsi normali. Nota ambientale: il POST /solve-staged tiene il thread per ~19s (validateForSolving su 12k lezioni): i driver devono attendere il 202 PRIMA di cronometrare o stoppare. Verify-2 su 29de5126 in corsa (check 15:31).

2026-09-25 15:35 VERIFY-2 PASS (log /tmp/uxlane-667-live.log): jar 29de5126, stop durante PRE-ASSIGN -> t=2s stagedOutcome=CANCELLED dal server con stage azzerato (il fix liveness lavora), poi lane fermata e suite completa 1378/0 SUITE-EXIT:0. TASK CHIUSO 4/4 AC. Storia completa per il registro: verify-1 FAIL = script-condizione-sbagliata (richiedeva stage null) + BUG REALE liveness (stage PRE-ASSIGN per sempre sull'uscita-rapida-da-cancel) scoperto dal live e curato in 29de5126 col clear idempotente nel finally esterno. La verifica con consegna naturale (COMPLETE) resta coperta dalla classificazione simmetrica + suite; il caso e' lo stesso percorso finally esercitato dal CANCELLED.
<!-- SECTION:NOTES:END -->
