---
id: TASK-390
title: Zero-slack weekly template — place tight groups (G05) by construction, not greedy first-fit
status: To Do
assignee: []
created_date: '2026-07-22 07:20'
labels:
  - claude-planned
dependencies: []
ordinal: 214000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Leva (c) del verdetto TASK-386: la via allo 0-hard AFFIDABILE (non solo il cluster buono ~-123). Il residuo di ogni run (12-17 + benchmark laptop) è la stessa coppia H3 di G05 (Scienze/T09 vs Matematica/T08 stacked). CAUSA RADICALE confermata (codice + log + `scripts/analyze/group-profile.sh "G05"`): G05 ha 990h domanda contro 29 slot/settimana = **34,1 settimane implicite** ≈ le ~34 didattiche reali → slack ≈ zero; e i suoi corsi sono tenuti da docenti condivisi con 10-14 altri gruppi (T21 13, T06 14, T08 10, T23 13, T02 11). Trovare una settimana in cui OGNI ora-corso di G05 ha una cella dove il suo docente è libero in tutti i gruppi è un matching stretto. `WeeklyTemplateBuilder.build` è greedy first-fit SENZA backtracking (righe 272-320): non riesce a piazzare un'ora-corso → `break`/leftover (riga 315) → round-robin+LS non trovano cella a slack-zero → stack H3. Che first-fit trovi il matching è FORTUNA (la varianza run-to-run), non costruzione — L2 lo affermava ma il greedy non lo garantisce. È lavoro di RICERCA (construction heuristic), modello INVARIATO (direttiva primaria).
<!-- SECTION:DESCRIPTION:END -->

## IMPLEMENTATO: compattazione leftward in costruzione (2026-07-22)

`DayClusteredConstructionPhase.compactStudentDays()` — dopo stamp/round-robin/force-place, per ogni (gruppo, data) con un buco interno tira a sinistra nel buco la lezione più a destra la cui mossa è **completamente legale**: gap slot nel VRP (`getAvailableTimeslots`) + `isSlotConflictFreeFor` (nessun'altra lezione al gap slot condivide teacher/H1 o gruppo/H3, controllato contro le lezioni REALI via indice autoritativo `byTs`, non i Set del phase che perdono molteplicità). Move solo-timeslot + room azzerata (riassegnata pulita dalla fase-4 room-CH). Pin mai mossi. Iterazione deterministica (nome gruppo, data). **INVARIANTE: 0 conflitti aggiunti** (`compactionAddedConflicts`), guardato dal test.

**Misura (`WeeklyTemplateConstructionRealSeedTest`, all-unpinned):** chiude 51 buchi (157→108), **0 conflitti aggiunti**, ricorrenza 77% (guard ≥50%), stampSkipConflict 0 invariato, 689 test verdi. Nel fixture all-unpinned solo 51/157 sono single-move-evitabili (placement più caotico); in produzione (R1: 6-10 buchi, ~85% evitabili con mossa singola) dovrebbe chiuderne quasi tutti. DoD: /simplify (3 fix applicati: javadoc, passata unica, occ→TreeSet; 3 skippati con motivazione), /code-review inline (conflict-free/room/determinismo/pin/cap/M28 verificati), test.sh exit 0 + ui non toccato. **Validazione produzione: il solve laptop di TASK-392 (HEAD) mostrerà il residuo H17 reale post-compattazione.**

Resta aperto: il ~15% di buchi FORZATI (conflitti cross-gruppo) + il template interlocking per il knot G05 (già risolto da R1 lato H3, ma la parte cross-gruppo di H17 è la stessa classe).

## DIAGNOSI H17 (2026-07-22, dopo TASK-392) — è COPERTURA di ricerca, non modello né blocco VRP

Dopo la demozione H15→M15 (TASK-392), il residuo hard che blocca lo 0-hard è **H17** (-6 run1 / -10 run2). Attribuito dai lessons.txt di R1 (`scripts/analyze` ad-hoc): **tutti buchi da 1 slot** in giornate da ~5 lezioni, sparsi su molti gruppi. **Lente golden: H17-hard è CORRETTO** (il golden ha 0 buchi, l'umano impacca stretto — a differenza di H15 che il golden viola 98x). Quindi H17 NON è un problema di modello: è un vero fallimento di compattazione del solver.

**Forced vs avoidable (dai lessons.txt):** run1 4/6 evitabili, run2 9/10 evitabili — cioè per la stragrande maggioranza esiste una lezione la cui mossa singola chiuderebbe il buco (docente libero al slot del buco). I forzati (~1/6) sono conflitti cross-gruppo (stessa classe del knot G05).

**Verifica empirica (b, richiesta utente) — `WeeklyTemplateConstructionRealSeedTest#h17AvoidableGaps_areVrpAvailableForTheMovableLesson`:** per 6 buchi reali di R1, controllato se lo slot del buco è nel `getAvailableTimeslots()` (VRP: calendario/quota/disponibilità/stage) della lezione movibile. RISULTATO: **6/6 VRP-available, 0 bloccati.** Combinato con docente-libero (no H1) e slot-vuoto (no H3): la mossa di riempimento è **completamente legale**. Meccanismo scoring verificato dal codice: `countBreakSlots = getLength()-1`, `ONE_HARD × slot-vuoti`, LINEARE-additivo (NON moltiplicativo; giorno vuoto = 0 penalità, non massima). Quindi NON è patologia di scoring NÉ blocco VRP → **pura copertura di ricerca**: la LS termina (unimproved 600s) prima di trovare queste ri-collocazioni singole nella coda.

**CONSEGUENZA per il fix:** compattare in COSTRUZIONE (impaccare stretto dall'inizio, così la LS non deve scovare questi spostamenti nella coda) è la via giusta ed empiricamente giustificata. I buchi evitabili (~85%) si chiudono con una compattazione greedy leftward in `DayClusteredConstructionPhase` (dopo lo stamp/round-robin: per ogni giornata con un buco, tira a sinistra la lezione la cui VRP+docente lo permette). I forzati (~15%) restano per il lavoro cross-gruppo (template interlocking / ruin mirato).

## RE-SCOPE (2026-07-22, dopo TASK-391)

TASK-391 (adozione ruin 5-20) ha **chiuso il knot H3 di G05** in modo affidabile (0/2 run R1; il knot appariva col ruin piccolo, control 1/2 e R2 1/2 — vedi experiment-log). Quindi la parte "G05 stacked H3" di questo task è RISOLTA da R1, NON serve la chirurgia sulla CH per quello.

Il target resta però lo **0-hard affidabile** (direttiva utente 2026-07-22: "se lo fa l'umano dobbiamo farlo anche noi, a costo di copiare ulteriori euristiche umane in fase di creazione di soluzione parziale"), e sotto R1 NON è raggiunto: il residuo persistente in TUTTI i 6 run è ora **H15 -60** (uno split same-day-same-subject non-consecutivo tra lezioni unpinned) + H17 piccolo. Questo task si RE-TARGETTA su quel residuo: portare lo 0-hard replicando in costruzione (WeeklyTemplateBuilder / DayClusteredConstructionPhase) l'euristica umana che evita lo split same-day (H15) e i micro-gap (H17). L'approccio incrementale sotto (MRV/forward-checking/backtracking gated ai gruppi tight, guard-protetto) resta valido come mezzo, ma il criterio di successo diventa "H15/H17 residuo → 0 in un solve reale", non più "coppia H3 di G05". Spike Step 0 fedele (con pin calendario) ancora utile per attribuire quale gruppo/materia genera l'H15 residuo.

## Proposed approach (incremental, gated, guard-protected)

**Gating:** applica la logica nuova SOLO ai gruppi tight (soglia su `demand / (weekly_slots * teaching_weeks)` vicino a 1, es. ≥0.97). Gli altri 13 gruppi restano sul greedy attuale (77% ricorrenza, full-day buono) INVARIATI.

**Step 0 — spike (non distruttivo):** PARZIALMENTE già fatto (2026-07-22). `WeeklyTemplateConstructionRealSeedTest` (all-unpinned, via test.sh) stampa: stamp pulito (skipConflict=0) ma **1354 leftover esauriti** che il round-robin/force-place tail infila creando **conflitti finali teacher=15, group=2** — evidenza DIRETTA che greedy+repair NON è un matching garantito, il meccanismo diagnosticato. NB: quel test è all-unpinned, mentre il knot di PRODUZIONE di G05 nasce coi pin del calendario (Confezione/Moulage pinnati → il resto a slack-zero). Uno spike FEDELE al knot di produzione richiede import-calendario + construction phase (non l'esistente test all-unpinned) → da fare qui SE si arriva al fallback CH, con attribuzione per-gruppo dei 2 group conflict.

**Step 1 — MRV ordering:** per i gruppi tight, ordinare le ore-corso per minimum-remaining-values (fewest feasible cells first) con forward-checking, invece di "biggest-course/hardest-teacher first". È la mossa CSP classica che fa riuscire il greedy sui problemi stretti; cambio chirurgico all'ordinamento in `build()`.

**Step 2 — misura:** se lo Step 1 azzera i leftover di G05 nel template costruito → basta. Altrimenti Step 3.

**Step 3 — bounded backtracking:** aggiungere backtracking limitato (budget di nodi) sui gruppi tight quando MRV+forward-checking non basta, per GARANTIRE il matching entro budget.

**Step 4 — verifica:** `WeeklyTemplateConstructionRealSeedTest` (ricorrenza ≥ banda 75-81%, stamp skipConflict=0 INVARIATI); nuova asserzione: G05 piazza tutte le 990h con 0 conflitti teacher/group nella settimana costruita; nessuna regressione sugli altri 13 gruppi; poi un solve reale per confermare che la coppia H3 di G05 non ricorre più (0 hard affidabile).

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
