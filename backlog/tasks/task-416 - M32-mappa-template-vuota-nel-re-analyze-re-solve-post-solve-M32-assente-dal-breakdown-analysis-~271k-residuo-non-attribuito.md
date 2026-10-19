---
id: TASK-416
title: >-
  M32: mappa template vuota nel re-analyze/re-solve post-solve (M32 assente dal
  breakdown analysis, ~271k residuo non attribuito)
status: Done
assignee: []
created_date: '2026-07-25 04:26'
updated_date: '2026-07-25 13:04'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
dependencies: []
priority: medium
ordinal: 240000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Trovato nella verifica val413 (2026-07-25 notte): analysis.json non contiene M32 e il suo medium totale differisce da score.txt esattamente del contributo M32 (~-271k, run1 270.976 / run2 271.572). IPOTESI PRINCIPALE (da verificare per prima): analyzeScore/loadSchedule(false) post-solve riesegue refreshSolverStatics -> WeeklyTemplatePlanner seleziona la popolazione con timeslot==null -> a solve finito popolazione vuota -> setWeeklyTemplateStatic pubblica mappa VUOTA -> M32=0 nella ri-analisi (spiega sia la riga mancante sia il totale piu' basso). IPOTESI ALTERNATIVA (zai, VERDICT val413 sez.2): gap di ripple nel drill-down per-constraint di ScheduleService (spiegherebbe solo la riga mancante, non il totale). CONSEGUENZE: (a) analysis non confrontabile con score a fine solve; (b) M32 match-count non osservabile da nessuna capture; (c) su RE-SOLVE di un profilo gia' risolto la mappa sarebbe vuota -> M32 inerte al secondo giro (semantica sbagliata: il template di riferimento deve restare stabile). DIREZIONE CANDIDATA: rendere la selezione della popolazione del planner assignment-agnostic per la MAPPA M32 (unpinned, indipendentemente dal timeslot corrente), mantenendo INVARIATA la semantica della fase CH (che deve continuare a templare solo le lezioni da piazzare) - attenzione a non rompere il byte-identico del real-seed guard. Catena di test per la matrice (claudedocs/localized-testing-guidelines.md par.4): vincolo esistente -> verifier + golden guard + score-a-snapshot (TASK-415, perfetto qui: deve mostrare M32 non-zero sullo snapshot finale di val413) + niente ciclo completo se il fix e' solo di ri-analisi.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 analysis.json post-solve include M32 con matches, e il medium dell'analysis coincide con score.txt
- [x] #2 re-solve su profilo risolto: la mappa template M32 non e' vuota (test dedicato)
- [x] #3 real-seed construction guard e golden guard invariati
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-25 review 415 (F1): ScoreASnapshotTest EREDITA questo bug silenziosamente - su qualunque snapshot finale la popolazione del WeeklyTemplatePlanner (filtro timeslot==null) e' vuota -> mappa template vuota -> M32~0 strutturale nel breakdown. Interim fix nel harness: riga esplicita 'M32: NON confrontabile su snapshot finale (TASK-416)'. Quando questo task e' risolto: (a) rimuovere quella riga, (b) l'AC #1 si verifica proprio con ScoreASnapshotTest su val413-run1 (atteso M32 ~ -271k, il residuo score-vs-analysis misurato nella verifica notturna).

IMPLEMENTATA 2026-07-25 (treno): WeeklyTemplatePlanner.buildReference (popolazione assignment-agnostic, occupazione solo pinned) usato da publishWeeklyTemplateStatics e SnapshotFixture. Test stabilita' reference==CH su stato fresco e template identico post-assegnazione; ScoreASnapshotTest su val413-run1 mostra M32 -272.697/2.092 match (banda attesa ~-271k: fix e harness si validano a vicenda). Guard invariati. Review treno (3 Opus): zero bug di correttezza; interplay con no-session 408 verificato pulito. AC#1 (analysis==score live) si prova al prossimo run laptop.

AC#1 PROVATA LIVE su val408 x2: analysis.json == score.txt AL CENTESIMO su entrambi i run (-1.904.823 e -1.909.720), M32 presente con matches (-279.751/2.106 e -284.914/2.136). In val413 divergevano di ~271k. Chiusa.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
