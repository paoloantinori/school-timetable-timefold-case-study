# HANDOFF 2026-07-29 EOD (20:00) - stato per sessione fresca

Scritto a fine turno (zai). SUPERA tutti gli handoff precedenti della giornata
(HANDOFF-2026-07-29.md TASK-437, HANDOFF-2026-07-29-labfallback.md TASK-456). Tutto pushato.
Sessione molto densa: TASK-456 completato in tutte le tappe + 2 analisi G11 late-start.
Leggi questo + i task citati + i doc `task456-results/*.md`.

## Dove sono (verde, pushato)

Branch `feature/cartesian-block-entity`, HEAD `00ad29f4` (sync remote). Tutto pushato.

### TASK-456 lab-fallback = COMPLETO (Tappe 1/2/3/A/B), ratifica pending maintainer
- Implementato dietro flag `planner.room.lab-fallback.enabled` (default OFF, letto LIVE) +
  vincolo M-LF medium (knob `planner.room.lab-fallback.weight`, **default 1000 DECISO** da
  Fedora). Byte-identical a flag OFF. File protetti intoccati.
- MISURA (doc `lab-fallback-measure-2026-07-29.md`): chiude 7/7 non-lab room-null senza mai
  rubare lab a nessun peso; **escludere R18 col ripiego ON costa ZERO** (hard
  -188 = come tenerla; -263 senza). S10 co-fira.
- Pre-misura risposte attese (doc `lab-fallback-premisura-risposte-2026-07-29.md`): residuo
  LAB rn 182->154(G14)->144(G12)->120(entrambi). G14 mai esclusiva (0 capacity-freed,
  conferma golden Fedora); G12 solo 12h contese su 121 (il de-tag a 76h e' upper bound).
  Residuo Lavaggio ~75 strutturale.
- **RATIFICA maintainer = singolo comando**: (1) accendere il flag + (2) escludere R18.
  Peso 1000 gia' nel codice. Fino ad allora flag OFF, produzione invariata.

### G11 late-start: DUE analisi fatte, IPOTESI SUCCESSIVE REFUTATE
1. **Swap delta** (harness `LateStartSwapScoreTest`, doc `late-start-swap-score-2026-07-29.md`):
   l'ipotesi "il punteggio M32 rifiuta lo swap" e' REFUTATA. Lo swap e' HARD-illegale: 417
   candidati value-range-legali a slot0 ma 0 conflict-free (tutti collidono H2 room/H1 teacher).
   Delta: hard -3000 (H2 -2000 + H1 -1000), M32 solo -50, M18 GUADAGNA +50. Il candidato
   T04 di Fedora e' fuori value-range a slot0. Il fix non e' swapMove (slot0 vuoto)
   ma 2 changeMove.
2. **Convergenza aule/late-start** (doc `late-start-convergence-2026-07-29.md`, poi CORRETTO):
   REFUTATA per motivo strutturale. La fase degli ORARI nel ciclo a stadi gira PRIMA
   dell'assegnazione stanze (disaccoppiamento TASK-446), la fase stanze non sposta lezioni
   nel tempo: quindi quando G11 e' spinto alle 9 le aule non esistono ancora. Conferma
   indiretta: a fine costruzione inizia alle 8, nel run finale alle 9. **Causa reale (da
   stamattina): la costruzione lascia un buco alla seconda ora (H17 hard, 33 giornate/3
   FB, 17 ven), riparato per traslazione.** Prova per-fase mancante = TASK-453 (prossima
   certificazione, era gia' deciso).
   - Numeri di capienza (validi come dato, NON spiegano il ritardo): 15 late-start flag-OFF
     (tutti G11 ven, golden 0); a slot0 aule saturate 8-10/11 ma 5.2 lab liberi/7. La
     contesa aule e' reale e il lab-fallback la risolve, ma e' ORTOGONALE al late-start.
   - **NETTO**: lab-fallback NON chiude il late-start (nasce prima delle stanze). Non e'
     ne' prezzo (M32) ne' capacita' (aula/lab): e' costruzione/riparazione (H17 hole).

## NEXT (ordine di priorita', Fedora 2026-07-29 sera)

1. **TASK-445** (workbook Excel input scuola). Proposta `claudedocs/proposta-workbook-input-2026-07-28.md`
   + audit `claudedocs/audit-genericita-multi-anno-2026-07-28.md` + task `backlog/tasks/task-445...md`.
   E' il lavoro PIU' GROSSO non iniziato, non dipende da risposte in sospeso: template dei
   fogli (MODA/calendario/vincoli docenti/elenchi, 1 riga=1 fatto, nomi canonici) + importatore
   del nuovo formato + validazione all'inserimento. Checkpoint-first, DoD piena. NON iniziato
   in questa sessione perche' troppo grosso per il contesto residuo: attacreferente a contesto fresco.
2. Se 445 resta troppo grosso: **m-13 rimaste**. TASK-438 (calendario scolastico parametrico) o
   TASK-454 (export/import round-trip: aggiungere GroupCalendarRule, split TASK-436/6).
3. **TASK-453** (dump per-fase) solo alla prossima certificazione: dara' il momento esatto in
   cui la giornata G11 scivola dalle 8 alle 9. Era gia' deciso, non serve pre-work.

## Numeri load-bearing (verificati questo turno)
- G11 late-start flag-OFF = 15 giornate (tutte venerdi, golden 0), via day-start-anatomy.
- slot0 (08:00) dei 16 ven late-start: gruppi@8 11-13, aule 8-10/11 saturi, lab 4-6/7 liberi.
- Swap G11 (caso 2026-11-20): hard -3000 (H2 -2000 + H1 -1000), M32 -50, M18 +50; 0/417
  conflict-free. Caso 2026-09-18: hard -2000, stessa firma.
- lab-fallback: 7/7 non-lab rn chiusi a ogni peso, LAB rn 182 invariato; POLIVALENTE-exclude
  free col ripiego ON.

## Cose NOTABILI per la sessione fresca
- Regole invariate: checkpoint-first, NESSUN run live, NESSUNA adozione, file protetti
  (DataGenerator/seed) fermi allo scope autorizzato, domanda->scrivila e vai avanti.
- Lab-fallback e' measure-first: flag OFF in produzione finche' maintainer ratifica (flag ON +
  exclude POLIVALENTE). Non confondere: risolve la PENURIA DI AULE, NON il late-start G11.
- M32 NON e' il blocco del late-start (refutato) ne' causa di difetti qui (cella davvero
  cambiata negli swap misurati).
- Harness diagnostici pushati (env-gated, skippati nel suite di default):
  `LateStartSwapScoreTest` (delta swap), estensioni `RoomPhaseSnapshotTest`
  (lab-fallback override + PLANNER_TEST_ROOM_EXCLUDE + PLANNER_TEST_ROOM_TAG_UNTAG_GROUP).
- Per produrre dump flag-ON serve un solve offline con `-Dplanner.room.lab-fallback.enabled=true`
  sulla riga `java -jar` (NON MAVEN_OPTS, vedi CLAUDE.md), poi day-start-anatomy + capture-run.
- Fedor ha pushato in parallelo (commit 0ad379ea inferenza golden 44 sovrapposizioni,
  84a5c4eb riallineamento backlog): rebase pulito quando serve.
