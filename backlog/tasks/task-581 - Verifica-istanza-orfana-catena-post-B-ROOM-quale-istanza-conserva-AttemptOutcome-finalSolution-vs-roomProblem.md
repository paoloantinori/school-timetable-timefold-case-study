---
id: TASK-581
title: >-
  Verifica istanza orfana catena post-B-ROOM: quale istanza conserva
  AttemptOutcome (finalSolution vs roomProblem)
status: Done
assignee: []
created_date: '2026-09-05 06:01'
labels:
  - claude-planned
  - solver
  - persistence
  - verification
milestone: m-13
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
VERIFICA (non implementazione), da fare PRIMA di ogni ciclo SurplusHarvest successivo. Scoperta 4/09 mattina (vedi session-checkpoint-2026-09-01-notte, aggiornamento "4/09 MATTINA TARDIVA").

IPOTESI: `runRoomSolveStage` ritorna una NUOVA istanza (`finalSolution`), ma la catena post-B-ROOM (QFR-2 TASK-533, EDF TASK-560, SurplusHarvest prima-occasione, riga ~1384) gira su `roomProblem` (istanza vecchia pre-room). I saveSchedule censiti sono: 640 (solver-event, salva solved), 1420 (tail, finalSolution), 1464 (resume), 2618 (wrapper-restore), 1597 (post-construction). NESSUNO salva roomProblem dopo la catena.

CONSEGUENZA SE CONFERMATO: le mosse della catena post-B-ROOM non persistono (istanza orfana). Spiegherebbe perche' il residuo H34 resta nelle consegne nonostante EDF/SurplusHarvest chiudano celle nei log. Le due passate SurplusHarvest identiche del ciclo v11 (40/8 alle 06:41 e 06:44) sono la prova indiziaria: la tail avrebbe trovato le celle gia' chiuse se vedesse le mosse del chain.

ANTITESI (da non ignorare): il gate di ratifica EDF (task560-results/gate-adozione-edf-v01 riga 39) dichiara le celle chiuse VERIFICATE NEL DB post-fix-infra. Quindi in quel ciclo le mosse della catena arrivavano al DB. IPOTESI DELLA VIA MANCANTE: il wrapper MultiStartRunner conserva come AttemptOutcome.solution() l'istanza roomProblem MUTATA dalla catena (non solved), e il restore del winner (saveSchedule 2618) la risalva: la catena persiste SOLO se quel tentativo vince.

DA VERIFICARE, in ordine: (1) quale istanza conserva AttemptOutcome (finalSolution o roomProblem?), leggere MultiStartRunner.runOneMultiStartAttempt; (2) se roomProblem: la catena NON e' orfana sul winner (bug inesistente), ma le due passate identiche restano da spiegare; (3) se finalSolution: il gate EDF aveva un'altra via (guardare cosa fu il 'fix-infra' del 26/08). NON lanciare cicli finche' questa verifica non e' chiusa: decide dove deve girare la tail di SurplusHarvest.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lettura di MultiStartRunner.runOneMultiStartAttempt: documentato se AttemptOutcome conserva finalSolution o roomProblem, con riga di codice citata
- [ ] #2 Censimento dei saveSchedule nella catena post-B-ROOM (QFR-2, EDF, SurplusHarvest): esiste o manca un save dopo la catena
- [ ] #3 Spiegata perche' le due passate SurplusHarvest del ciclo v11 erano byte-identiche
- [ ] #4 VERDETTO scritto in claudedocs/spalmone-2026-27/ che dichiara se il bug esiste o e' un falso allarme, con evidenza
- [ ] #5 Se il bug esiste: aperto un task di cura (far girare la catena su finalSolution o salvare roomProblem dopo la catena)
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
