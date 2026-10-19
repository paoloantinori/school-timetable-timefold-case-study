---
id: TASK-407
title: >-
  Recurring-pattern heuristic places labs/lessons past the ideal end date (3
  FB, G04) — golden-faithful overflow or stamp gap?
status: Done
assignee: []
created_date: '2026-07-24 08:26'
updated_date: '2026-07-27 14:48'
labels:
  - claude-planned
  - research
  - construction
  - spalmone-2027
  - ideal-end
milestone: m-12
dependencies: []
priority: medium
ordinal: 231000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
EXPLORATIVE — Fedora to investigate (maintainer insight, 2026-07-24, observed during the TASK-406 run-1 solve).

OBSERVATION: G11 has labs placed ~2 days AFTER its ideal end date, following a recurring weekly pattern; G04 has MANY lessons past its ideal end. These placements appear to bring no value and arguably should not have been made at all.

ROOT CAUSE CANDIDATE (code-level, to verify): the weekly-template STAMP (DayClusteredConstructionPhase.stampWeeklyTemplate) runs across the FULL school year with NO getIdealEndDate cap BY DESIGN — its javadoc cites the TASK-379 golden-first rationale that the golden itself spreads ~29.3% of hours beyond computed target ends ("lo spalmone è lo spalmare"), with M12 (overflow beyond target end, ONE_MEDIUM/day since TASK-394 fact-based targets) as the calibrated counterweight. So the stamp stamps recurring cells all the way to the year's real end regardless of the group's ideal end.

THE QUESTION (golden comparison): is this golden-FAITHFUL (the golden also places G11 labs / G04 lessons past the ideal end) or a HEURISTIC GAP (the stamp extending recurring cells past the ideal end where the golden does NOT)? 
- If golden-faithful: the overflow is intended, M12 is the right lever, no construction change.
- If a gap: the stamp should respect the ideal end (confine recurring cells to [year start, ideal end]) and let M12/the LS handle genuine lateness, instead of construction placing recurring labs past the end. "treat this as something to mitigate in other ways" (maintainer).

NOTE the interaction with TASK-406: the deficit-aware box-repair (TASK-406) routes leftovers to M20-deficit days, which CAN include past-ideal-end days (a day past the ideal end can still have an expected-hours fact and thus a deficit), so it may slightly amplify past-ideal-end placements — but the bulk source is the full-year stamp, which TASK-406 did NOT change.

EVIDENCE: claudedocs/spalmone-2026-27/task406-results/val406-run{1,2}/lessons.txt (the live placements) vs the golden (lessons.csv / golden import). Compute per-group: lessons placed past ideal end, split by subject (labs vs aula), and compare the same count on the golden.

OUT OF SCOPE: changing the model without the golden comparison first (primary directive: a rule is judged on whether it reflects golden practice).
<!-- SECTION:DESCRIPTION:END -->

## CONFRONTO GOLDEN ESEGUITO (Fedora 2026-07-24) — VERDETTO: golden-faithful, premessa INVERTITA

Misura (lesson-hours OLTRE l'ideal end fact-based, golden lessons.csv vs val406-run1):
- **G04: golden ~306 (di cui 224 "Stage" da vagliare, ~82 non-stage) vs NOSTRE 62.** E per
  mese il golden SPALMA G04 fino a giugno (apr 125, mag 148, giu 69) mentre noi lo
  compattiamo (apr 70, mag 11, giu 9). Siamo GIÀ più compatti della scuola su G04.
- G08: golden 50 vs nostre 30. G14: golden 115 (tutta la sua domanda: il golden insegna
  G14 gen-mag, l'ideal 12/10 è "earliest possible", non pratica) vs nostre 83.
- **G11: golden 0 vs nostre 9** (7 Progettazione Termoidraulica) — l'UNICO caso in cui
  superiamo il golden, ed è marginale (9h, ideal 08/06 vs fine anno 10/06 = 2 giorni).

CONCLUSIONE (direttiva primaria): l'overflow oltre l'ideal end è pratica golden (la scuola ne
fa PIÙ di noi); M12 è la leva giusta e nessun cambio di costruzione è giustificato. L'ideal
end per i gruppi con domanda<piano (G04 750/990, G14 115/990, G08 926/~990) è il
"prima data possibile", non la fine pianificata — il marker UI può confondere: eventuale
follow-up UX (mostrare anche la fine-piano), non di modello. Residuo legittimo da tenere
d'occhio: le 9h di G11 (se ricompaiono più grandi in run futuri, riaprire).

**DOMANDA PER LA SCUOLA (collegata alla 408):** per i gruppi con slack (G04), preferite
compattare (finire ad aprile, giorni più pieni — quello che facciamo noi) o spalmare come il
vostro golden (32% di giorni pieni)? Le due cose sono in tensione diretta; il golden stesso è
incoerente tra gruppi (spalma G04, riempie G09 al 95%).

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO (report: claudedocs/spalmone-2026-27/task407-ideal-end-overflow-2026-07-27.md). READ-ONLY.

VERDICT: GOLDEN-FAITHFUL, NESSUN cambio costruzione. Il golden stesso overflow l'ideal-end per 3/4 gruppi citati (G04 322h past-end di cui 240 Stage, G08 10h, G14 120h). Il run e' PIU' COMPATTO del golden (G04 run 123h vs golden 322h; il run e' piu' rigoroso della pratica). G11 (run 9-14h vs golden 0h) e' l'unico gap candidato ma: limitato agli ultimi 2 giorni scolastici (2027-06-09/10, boundary orizzonte-anno), root = mismatch fact-sheet 966h-piano vs 990h-totalHours (ideal end 2027-06-08, 2 giorni validi residui), NON riproduce su m12-run1 (0h li').

MECCANISMO confermato: stampWeeklyTemplate gira full-year senza cap getIdealEndDate BY DESIGN (TASK-379 golden-first); il repair tail (findFirstAvailableDate) ha il cap; M12 (ONE_MEDIUM/day, fact-based TASK-394) e' il contrappeso calibrato.

RACCOMANDAZIONE: nessun cambio. Cappare lo stamp eliminerebbe il caso G11 (9-14h) ma ANCHE G04/G08/G14 dove il golden overflow (devierebbe dalla pratica golden, direttiva primaria). Se un run futuro vede G11 bleedare oltre la finestra 2-giorni (Mag/Giu), THEN e' gap genuino -> estendere il cap del repair tail allo stamp. Oggi non si raggiunge la soglia.

DoD N/A (indagine read-only, nessun codice).
<!-- SECTION:NOTES:END -->
