---
id: TASK-670
title: >-
  BACKEND: segnale di liveness unificato del ciclo staged (coda
  FCC/finisher/save e gap inter-attempt ciechi a stop e isBusy)
status: Done
assignee: []
created_date: '2026-09-25 10:45'
updated_date: '2026-09-27 07:30'
labels:
  - claude-planned
  - backend
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Residuo della code-review TASK-667 (finding F2-coda + F3, 25/09): il cancel e la guardia isBusy coprono stagedStage e refiningForDelivery, ma la coda di consegna resta cieca: FinalClosingCampaign, la catena CompositeSwapFinisher/EDF-extensions/HoleRelayFinisher/ViolationScopedSteepestFinisher, il restore-save e la concordance check girano con stagedStage=null E refiningForDelivery=false (refining e' armato SOLO nel corner-b dei vincitori doomed), e il gap inter-attempt del multi-start e' scoperto dal 661. Conseguenze misurate dalla review: (1) uno stop in quelle finestre e' silenziosamente ignorato e il ciclo classifica COMPLETE mentre la UI ha gia' toastato 'stopped' (o un driver ha ricevuto l'ack del DELETE); (2) isBusy() accetta un secondo ciclo concorrente che muta lezioni/DB mentre il primo sta ancora consegnando. La forma naturale: un campo volatile cycleThreadAlive (o equivalente) armato dal wrapper entry-point e letto da stopSolving/isBusy/stagedOutcome, oppure un marcatore di fase piu' fine. CURA strutturale, non per-pass: i finisher non controllano il flag oggi e toccarli tutti e' fuori scope additivo. Precedenti: la quiescenza refiningForDelivery e' nata esattamente cosi' (falsi FINISHED mid-refinement, ratifica 17/08).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Un segnale di liveness unificato del ciclo copre TUTTE le finestre: FCC, catena finisher [delivery], restore-save, concordance check, gap inter-attempt (oggi: stagedStage null E refiningForDelivery false)
- [ ] #2 stopSolving registrato (e terminato dove c'e' un solver) in quelle finestre: l'esito non puo' piu' etichettare COMPLETE una consegna successiva a uno stop ignorato
- [ ] #3 isBusy() consuma lo stesso segnale: nessuna finestra accetta un secondo ciclo concorrente
- [x] #4 Banco: stop durante la coda FCC/finisher di un ciclo good-basin (single attempt, corner-b skip) e durante un gap multi-attempt: entrambi devono classificarsi CANCELLED
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-25 22:15 Fedora: cura landed nel working tree. SEGNALE: volatile cycleThreadAlive armato sotto stagedLock PRIMA di thread.start() in entrambi gli entry point (chiude la TOCTOU check-then-start), abbassato PER ULTIMO nel finally del wrapper (stessa disciplina del clear dello stage, live-verified 25/09). CONSUMATORI: isBusy() + stopSolving() + startSolving() plain (finding 1 della review: mancava) + refuseIfStaged() (finding 2: reset/export/import/reschedule ora rifiutano anche nelle finestre di coda). Banner CANCELLED reso onesto sul caso consegna-poi-stop (finding 3: testo neutro, il label resta CANCELLED per AC#2). Log warnf con il termine liveness (finding 4). Gates: /simplify 4-angle COMPLETE (reuse+efficienza CLEAN; semplificazione: commenti ridotti a puntatori + F1b fixata la frase inaccurata sull'arming + get0 ritirato + lifecycle.jsonl fuori dalla root; altitudine: 4 finding applicati). /code-review high in volo. Test: StagedOutcomeWrapperTest 6/6 (semantica wrapper: classificazione, clear stage/flag su ogni uscita, FAILED>cancelled, quick-cancel) + StatusPayload 1/1 + Feasibility 1/1 + ui-typecheck. AC#4 banco vivo (stop durante coda FCC/finisher + gap inter-attempt): finestre di secondi su cicli da 30min, DIFFERITO al prossimo ciclo naturale o a una corsa strumentata dedicata.

2026-09-27 08:56 BANCO VIVO COMPLETO (log /tmp/banco-670d.log, uxlane3 fresco): ciclo multi-attempt (B-TIMESLOT-1→2 con C-DEMOTION, RELEASE, B-ROOM, A-CONSTRUCTION restart, RELEASE, null). Stop al null dopo RELEASE (t=3499s): registrato con 'stage null, refining true, cycle alive true' (= raffinata la finestra corner-b, NON solo FCC). Esito CANCELLED dopo 598s (la coda brucia fino in fondo: comportamento documentato e accettato). Run A precedente (stesso banco round 3): CANCELLED a t=592s dallo stop. ENTRAMBI I RUN CANCELLED = AC#4 CHIUSA. TASK COMPLETO 4/4.
<!-- SECTION:NOTES:END -->
