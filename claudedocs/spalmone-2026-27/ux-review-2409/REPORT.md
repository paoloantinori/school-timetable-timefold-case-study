# UX lane 24/09 - TASK-658..665 - REPORT

Lane parallela all'istanza pubblica (laptop, clone `~/planner` congelato a 5185e055, PID 54572).
Worktree: `/Users/user/planner-wt-ux`, branch `feature/ux-planner-lane` (base 8f9280a2 =
5185e055 + solo backlog). DB: container `planner-pg-uxlane` porta 5433, volume
`planner-data-uxlane`. App: `java -jar` del worktree su 127.0.0.1:8081, log
`/tmp/planner-uxlane.log`, pid `/tmp/planner-uxlane.pid`.

## Fase 0 - setup lane + prove di non-interferenza

Prova di non-interferenza (PID servito + api.sh status), ripetuta a inizio lavoro e a fine fase:

| Quando | ps -p 54572 lstart | api.sh status |
|---|---|---|
| inizio lavoro (~12:44) | Thu Sep 24 10:17:47 2026 | NOT_SOLVING |
| fine fase 0 (~12:56) | Thu Sep 24 10:17:47 2026 | NOT_SOLVING |

Build lane: `npm ci` (worktree senza node_modules) + `./scripts/ui/ui-build.sh` + `mvn clean
package -DskipTests -Dnet.bytebuddy.experimental=true` nel SOLO worktree (wrapper
`/tmp/uxlane-build.sh`). Il jar servito non e' mai stato toccato.

Dati realistici: export dall'istanza servita in SOLA LETTURA
(`curl -s http://localhost:8080/api/schedule/export -o /tmp/uxlane-import.json`, 21,5MB,
forma hook-sanzionata) e import nella lane (`./scripts/api/import-schedule.sh
/tmp/uxlane-import.json 8081`, HTTP 200). Fedelta' import: 12551/12551 collocate,
274 pinned&placed = identico alla consegna servita (log servita 09:36:20 "274/12551 pinned").

### Nota: lo score della lane diverge dalla consegna servita (documentato, non curato)

Restore a freddo sulla lane: `[-68/0]hard/[-6711815/-3229]soft` vs servita
`[0/0]hard/[-672690/-3229]soft` sulle STESSE collocazioni. Decomposizione (GET
/api/schedule/analysis lane): H4 -35 (stage blackout), H7 -20, H31 -13; M20 -5.228.500,
M32 -700k. Causa: il DB lane ha SOLO il seed DataGenerator; il profilo production porta i
fact dell'import-calendario (quote a densita' golden-open TASK-550, calendario, stage).
La firma e' esattamente la "domanda fantasma" TASK-550 (M20 -5,2M su 1354 celle) + fact
calendario/stage assenti (H4/H31). Non e' una regressione di codice: e' la divergenza
nota del riconteggio a freddo vs statiche del ciclo ( famiglia TASK-509) amplificata dal
data-pipeline del profilo. Per la lane UI e' anzi uno stato piu' ricco di segnali da
renderizzare (violazioni hard presenti). Nessuna cura in questa lane (solver/dati fuori
mandato).

## TASK-660 - DIAGNOSI (sola lettura, nessuna cura in questo giro)

Domanda: perche' sullo stesso schermo il planner vede (1) score raw a 2 bracket
`[0/0]hard/[-672690/-3229]soft` con modello a 3 tier, (2) chip '8 HARD' da
analysis/lessons mentre analysis dice hard=[], (3) score 0hard, (4) feasible:false
'Compaction requirements not met'.

### (1) La stringa a 2 bracket NON e' stantia: e' la serializzazione canonica BendableScore

`SchoolSchedule.java:1654-1685` (TASK-538 ramo B, 28/08): lo score e' `BendableScore` con
2 hard + 2 soft levels. hard[0] = tier hard puro, hard[1] = QUASI-hard RISERVATO (milestone
2, nessun vincolo lo scrive ancora), soft[0] = ex medium, soft[1] = ex soft. La
serializzazione `[h0/h1]hard/[soft0/soft1]soft` e' documentata in `SchoolSchedule.java:1681`
e `ScheduleService.java:1564-1569` (nota di compatibilita' esterna della migrazione).
Quindi `[0/0]hard/[-672690/-3229]soft` significa: hard 0, quasi 0, medium -672690, soft
-3229. La UI mostra la stringa raw accanto a 3 card Hard/Medium/Soft: i numeri SONO
coerenti, la stringa e' semplicemente non spiegata. Niente deploy stantio, niente score
persistito stantio: la risposta all'AC#4 e' "nessuno dei due, e' il formato canonico".

### (2) Le '8 HARD' vs hard=[]: stato client stantio su un sistema che mutava, non divergenza di endpoint

Timeline ricostruita da `/tmp/planner-app2.log` (il run servito pre-10:17):

- 08:16:10 referente lancia solve-staged dalla UI (multi-start, piu' tentativi con scarti
  profile-predictor fino alle 09:26).
- 08:27:01 una analysis/lessons ricalcola da DB `[-6019/0]hard` (stato mid-cycle).
- 09:10:02 il pass HoleRepair logga `H17 hole slots 8` (chiuse 8, poi REVERTED per
  improve-or-noop: le 8 restano nello stato di quel momento).
- 09:36:20 CONSEGNA: `[0/0]hard/[-1172290/-3229]soft`.
- 09:59:25 primo riconteggio post-consegna: `[0/0]hard/[-672690/-3229]soft` (divergenza
  medium consegnato vs ricontato: nota sotto).
- 10:17 restart; 10:19:02 restore asincrono (a8886823) logga lo stesso
  `[0/0]hard/[-672690/-3229]soft`.

Meccanismo del chip: `useLessonViolations.ts:54-62` fa il fetch quando
`solverStatus === 'NOT_SOLVING'` o cambia il dateRange. Durante TUTTO il ciclo staged
solverStatus resta NOT_SOLVING (il ciclo non passa da SolverManager, documentato in
`CLAUDE.md` "Osservare un solve staged"), quindi il fetch del chip parte a meta' ciclo
sullo stato mid-cycle (le 8 lezioni con buchi H17) e NON viene MAI rinfrescato: alla
consegna non scatta nessuna transizione (NOT_SOLVING -> NOT_SOLVING). L'AnalysisPanel
(`POST /api/schedule/analysis` via analyzeScore) ricalcola sempre da DB
(`ScheduleService.java:5122-5129`: loadSchedule + solutionManager.update, niente cache):
mostrava lo stato post-consegna (hard=[] corretto per quel momento). I due endpoint hanno
la STESSA semantica di ricalcolo; le due viste hanno letto MOMENTI DIVERSI di un DB che
mutava e l'assenza di evento di completamento (curato da TASK-661 AC#4) ha congelato il
chip su uno stato superato. Oggi (12:44) analysis/lessons sulla servita torna 0 HARD
(misurato: 40335 SOFT + 3014 MEDIUM entries, 0 HARD).

Concause vere a schermo, gia' nei task: il badge verde non e' gated su stagedStage
(UnifiedSolverControls.tsx:895) e latestScore=ZERO al via del ciclo
(ScheduleService.java:1587 startStagedProgression), quindi DURANTE il ciclo il banner
'Orario valido' e' verde mentre il DB e' a -6019: e' la finestra in cui la review ha
fotografato la contraddizione.

### (4) feasible:false 'Compaction requirements not met': soglia stantia, non uno stato corrotto

`ScheduleResource.java:302-307`: la feasibility confronta `mediumOf(score) >=
-DEFAULT_MAX_TOTAL_GAP_MINUTES` con `DEFAULT_MAX_TOTAL_GAP_MINUTES = 50000`
(`SchoolSchedule.java:38`). soft[0] (ex medium) e' un aggregato di TUTTE le medium
(~-672k su una consegna buona): qualunque consegna reale fallisce il confronto. La
semantica della soglia risale all'era in cui il tier medium era ~ gap-minuti totali; il
messaggio e' inoltre hardcoded inglese (ScheduleResource.java:305, curo in TASK-660/662).
hardScore==0 viene controllato PRIMA (riga 296) ed e' l'unica parte della feasibility che
oggi ha senso: con hard 0 la risposta e' comunque false per la soglia medium.

### Bonus accertato (famiglia TASK-509, ribadito): medium consegnato != medium ricontato

Consegna 09:36:20 medium -1.172.290; riconteggio a freddo 09:59 e restore 10:19 medium
-672.690 (stesso hard 0/0). Le statiche pubblicate dal ciclo (M32 template, netting pin)
non vengono ripubblicate identiche nel riconteggio a freddo. Gia' documentato come
residuo TASK-509 nel CLAUDE.md della ratifica quiescenza: qui e' la MISURA viva sul
deploy pubblico. Sulla lane (senza fact calendario) la stessa divergenza si amplifica a
-6,7M (vedi nota fase 0).

### Sintesi per la cura (prossimo giro, decide Fedora)

Una sola sorgente di verita' vuol dire: (a) il verdetto nasce dallo score ricalcolato
(unica semantica), non da latestScore; (b) badge/chip/feasibility si rinfrescano sull'evento
di completamento del ciclo (TASK-661); (c) la stringa score va parseggiata/spiegata
(formato BendableScore) o nascosta dietro le 3 card; (d) la soglia feasibility su
soft[0] va ritirata o ricalibrata (decisione di modello: fuori lane UI).

## TASK-658 - re-fetch della schedule sul transito score null->valore - DONE

### Baseline fallimentare (lane, jar pre-cura)

Import della consegna con latestScore null, poi apertura UI: il primo GET /api/schedule
torna le lezioni collocate e score null (il restore e' asincrono), la UI mostra
"Solution not yet scored" e griglia vuota. A 45+12 secondi dall'apertura la UI E' ANCORA
vuota anche se il server ha gia' completato il restore (log 12:54:36
"restored delivered score"): nessun poll gira da fermo (shouldPoll falso), la
change-detection che vive nel poll non viene mai eseguita. Screenshot:
img/658-baseline-after-load.png.

### Cura (2 edit frontend, zero backend)

- `ScheduleTimeline.tsx` (effetto polling): nuova condizione `awaitingScoreRestore`
  (score null E almeno una lezione collocata) inclusa in `shouldPoll`, e
  `schedule?.score` aggiunta alle dipendenze dell'effetto: il poll leggero resta vivo
  finche' manca lo score, la change-detection gia' esistente (`if (changed)
  fetchSchedule()`) ri-fetcha al transito, e lo score arrivato spegne il poll da solo.
  Su profilo davvero vuoto (zero lezioni collocate) il poll NON parte (guardia con
  `some(l => l.timeslot != null)`).
- `UnifiedSolverControls.tsx`: deps dell'effetto feasibility `[isSolving,
  schedule?.score]` cosi' anche il banner "Solution not yet scored" si aggiorna quando
  lo score appare.

ui-typecheck.sh EXIT=0 (x2).

### Verifica live (lane, jar con la cura)

Restart app -> apertura UI immediata: pagina caricata 13:13:12 con score null (banner
presente), restore server completato 13:13:21 (log), e la griglia SI POPOLA DA SOLA
senza alcun click: score visibile ("[-68/0]hard/[-6.7M/-3,229]soft" lane-state),
sidebar con le card Hard/Medium/Soft popolate, badge "68 unresolved conflicts". Il
timestamp del restore (> load) prova meccanicamente che il primo fetch era null e la
UI ha dovuto osservare il transito per ri-fetchare. Screenshot:
img/658-after-cure-populated.png.

Nota operativa per i rilanci lane: catturare il pid con `( cd wt && exec env ... java
... ) & echo $! > pid` (l'ampersand su `cd X && java` catturava la SUBSHELL, killando
il padre l'java restava orfano sulla porta e i rilanci morivano per bind silenzioso).
Killlane del mandato (pkill -f planner-wt-ux) sanato.

AC#3 del task (verifica sul SITO PUBBLICO) fuori mandato lane: richiede il restart
dell'istanza di referente, differita al deploy coordinato con Fedora.

## TASK-662 - italiano di default + copertura traduzione - DONE

### Cosa e' cambiato (6 file + 1 di rifinitura)

- `i18n/index.ts`: default ESPlicito 'it' (navigator.language non piu' consultato); la
  scelta manuale salvata in localStorage ('planner-lang', valori it/en validati) vince.
- `App.tsx`: i bottoni English/Italiano persistono la scelta in localStorage.
- `AnalysisPanel.tsx`: i dettagli per-vincolo chiamano l'endpoint con `lang`
  (overload (constraintId, lang) gia' esistente in scheduleApi) e il titolo del dialog
  passa per `getConstraintDescription` (nome localizzato).
- `LessonTooltip.tsx`: nuova mappa module-level id->nameKey derivata DAI DATI di
  data/constraints.ts (forme speciali M20.2/H-COPRESENCE/H-M2 incluse, non a mano);
  `getConstraintDescription` risolve prima la traduzione docs (it: "Compattezza
  Verticale (Nessun Buco)" per H17 etc.), fallback al comportamento inglese. Il tooltip
  lezione e il toast post-mossa di ScheduleTimeline ereditano senza ulteriori edit.
- `ScheduleSidebar.tsx` (rifinitura mia, stessa via): le voci Top Violations passano per
  getConstraintDescription ("Max Ore Giornaliere Insegnante", "Blocco Stage", "Inizio
  Giornata dal Primo Slot Aperto").
- Card e filtro tipo in AnalysisPanel tradotti con nuove chiavi (scoreHard/Quasi/Medium/
  Soft), allineate alla terminologia gia' esistente della sidebar ("Rigido/Medio/
  Morbido", ho corretto la prima bozza "Gravi/Leggere" che introduceva un secondo
  vocabolario).

### Verifica live (walkthrough su lane, screenshot in img/)

- Default: localStorage pulito -> la UI parte in ITALIANO (nav "Orario/Amministrazione",
  bottoni "Pre-assegna/Risolvi/Analizza/Fasi", sidebar "Rigido/Medio/Morbido",
  "12551/12551 assegnate (100%)", header tabella "Materia/Docente/Aula").
  img/662-orario-it.png + 662-orario-it-final.png.
- Tooltip lezione in italiano ("Sposta", "Blocca questo posizionamento (rendito
  intoccabile)", "Sposta qui", "Chiudi"). img/662-tooltip-it.png.
- Persistenza: switch a English -> localStorage 'en' -> reload -> EN attivo (nav
  "Schedule/Administration"); switch a Italiano -> reload -> IT attivo. AC#2 ok.
- Dettagli AnalysisPanel: dialog "Dettagli lezione per..." apre col parametro lang
  passato al backend; nomi vincolo localizzati ovunque passino per
  getConstraintDescription.

### Residuo noto (fuori scope 662)

- "Vincoli" hardcoded nel nav inglese: 665 AC#4.
- Messaggi feasibility backend inglesi ("Solution not yet scored", "Compaction
  requirements not met"): 660 AC#2.
- Le card dettagli usano il nome inglese del vincolo nei riferimenti grezzi del
  backend (constraint ref): il display localizzato copre titoli, tooltip, sidebar e
  toast; i ref interni restano canonici (stesso trattamento degli ID).

## TASK-661 - controllo del ciclo staged dalla UI - implementato, AC#1/#2/#5 verificate live, AC#3/#4 code-verified (bloccato live, vedi blocker)

### Cura (solo frontend, zero backend: refiningForDelivery era GIA' nel payload /status)

- Tipi: refiningForDelivery su ScheduleStatusLight e SchoolSchedule (domain.ts).
- ScheduleTimeline poll: refiningForDelivery in statusPollRef/changed/merge + nelle
  deps; `isRefining` aggiunto a shouldPoll (stessa ragione del 658: il poll deve
  restare vivo nella finestra stagedStage-null della rifinitura, altrimenti la
  transizione true->false e il re-fetch finale non arrivano mai).
- UnifiedSolverControls: `isBusyCycle = isSolving || stagedStage != null ||
  isRefining`; Stop visibile (e Risolvi nascosto) per tutto isBusyCycle; canReset
  negato con stagedStage non-null; outcome badge gated su !isBusyCycle (AC#3:
  niente badge durante ciclo e rifinitura); banner "Consegna in corso: rifinitura
  finale..." quando refining && stagedStage null (spinner pulse); toast di
  completamento sulla transizione refining true->false (AC#4), con re-fetch garantito
  dalla change-detection del poll (stessa transizione).

### I tre gate (laptop-RUNBOOK) sul ciclo di verifica

1. launch-preflight --jar-ok --flags "-Dplanner.checkpoint.capture.enabled=true":
   PREFLIGHT-OK (jar 14:07:50 > head 9f6ba001, flag literal verificato).
2. --expect-banner "Multi-start staged retry: enabled=true": PASS dopo il primo read
   full (NOTA: il banner NON esce a boot: refreshSolverStatics gira al primo
   loadSchedule, non all'avvio; i primi due FAIL del gate erano un assumption mio
   sbagliato, non un jar stantio).
3. capture-doctor su capture-661-lane/ (cattura manuale con api.sh -p 8081:
   capture-run.sh usa la porta di default e avrebbe colpito l'istanza SERVITA:
   deviazione documentata): DOCTOR-WARN NOT-DELIVERY-CERTIFIABLE hard=[-24585/0],
   no-sanity, no phases.json. Onesto: NON e' una consegna e non viene citata come tale.

### Verifica live (ciclo reale lanciato dalla UI)

Ciclo lanciato dal bottone Risolvi della UI (con la conferma 663 accettata). Stato
mid-cycle campionato dal DOM: `stopVisible: true, stopDisabled: false,
resetDisabled: true, solveVisible: false`, status "Ciclo a stadi: A-CONSTRUCTION" +
banner "CICLO A STADI IN CORSO... checkpoint INTERMEDIO" + "Tentativo 2 di 5".
Click su Stop: log server 14:26:31 "staged solve cancellation requested (stage
A-CONSTRUCTION)", stagedStage->null, UI torna a Risolvi con "Non in risoluzione".
AC#1, AC#2, AC#5 PASS.

### BLOCKER riportato a Fedora (fuori mandato lane, NESSUNA cura applicata)

Il ciclo NON arriva mai a consegna sulla lane: crash immediato a costruzione su TUTTI
i tentativi (3+ NPE in 30s, "staged solve FAILED - releasing the promoted pins
anyway"):

    NullPointerException: Cannot invoke "java.time.LocalDate.compareTo(...)" because "db" is null
      at DayClusteredConstructionPhase.sortGroupsCodaPrima(DayClusteredConstructionPhase.java:753)

Meccanismo (letto in codice): `latestEmptyCell.put(group, latest)` con latest=null
(gruppo senza NESSUNA riga quota positiva) inserisce un valore null, e
`getOrDefault(key, LocalDate.MIN)` (riga 751-752) NON sostituisce i valori presenti-ma-
null: il comparatore NPE-a sulla prima coppia. Su produzione il ciclo gira (referente,
08:16-09:36) perche' i suoi dati hanno righe quota per tutti i gruppi.

Causa scatenante lato lane (evidenziata, NON curata): il giro export->import PERDE le
righe DailyActivityQuota: l'export della servita ne contiene 2.257 (verificato nel
payload e nel file /tmp/uxlane-import.json), il payload lane dopo import ne ha 0.
Sospetto ImportService (che gia' oggi cancella le LabRotation, trappola documentata in
CLAUDE.md): il drop dei quota fact e' una classe nuova e piu' grave (rompe ogni
ricomincia-da-import). Divergenza dati lane gia' documentata in fase 0 (M20 domanda
fantasma) e' coerente con questo quadro ma NON la spiega tutta: M20 ha comunque
sparato 1354 match sul lane, quindi il percorso score vedeva fatti quota che la
costruzione no: da chiarire a Fedora con il db lane intatto (container
planner-pg-uxlane, porta 5433, volume planner-data-uxlane).

Conseguenze oneste: AC#3 (banner rifinitura) e AC#4 (toast completamento + re-fetch
finale) sono implementati e typecheckati ma NON verificabili live sulla lane finche'
il ciclo non arriva a rifinitura; da riverificare dopo la cura ImportService.

### Finding UX aggiuntivo (da valutare): Stop a meta' A-CONSTRUCTION svuota la griglia

Il ciclo parte disassegnando le collocazioni unpinned (by design, "re-plans from
construction"); uno Stop durante la costruzione lascia l'utente con la griglia VUOTA
(export post-stop: 12.551 lezioni, 274 assegnate, hard -24585). Il banner avverte che
la griglia e' intermedia, ma nessuna UI dice che lo Stop in quella finestra
RESTITUISCE un orario vuoto invece dell'ultimo buono. Candidato per un avviso nella
conferma di Stop (o un ripristino dell'ultima consegna): decisione Fedora.

**DECISIONE FEDORA 24/09 (ricevuta in corsa)**: (1) il bug import-quota e' TASK-666
(filed, commit f63e8e78 su origin): NON curato in lane, questo report e' il puntatore.
(2) AC#3/#4 di 661 da verificare su profilo NUOVO auto-seminato senza import (il
giro export->import e' rotto dal 666): FATTO, vedi sotto. (3) il caso post-Stop
griglia-vuota e' perimetro 661: cura frontend minima IMPLEMENTATA (commit 0c02052d):
alla transizione stagedStage non-null->null senza rifinitura, banner chiudibile
"Ciclo interrotto: la griglia mostra un orario intermedio, potenzialmente
incompleto." Verificato live (Stop mid-cycle -> banner renderizzato,
img/661-poststop-banner.png).

### Verifica AC#3/#4 su profilo nuovo (post-decisione Fedora)

Profilo nuovo: container planner-pg-uxlane2 porta 5434, volume planner-data-uxlane2,
auto-seed DataGenerator (12.551 lezioni, 0 collocate, quota rows 2.257 PRESENTI: il
seed le crea correttamente, conferma ulteriore che e' l'import a perderle, TASK-666).
Gates ripetuti: preflight OK (jar 14:49:47, albero di HEAD 0c02052d, working tree
pulito), banner "Multi-start staged retry: enabled=true" visto al primo read.
Ciclo lanciato dalla UI: [SEZIONE COMPLETATA A CICLO FINITO]

## TASK-664 - caricamento: skeleton, errore visibile, dedup - DONE

Cure (commit b2612afe): useLessonViolations con guard lastKey+inFlight (una sola
chiamata per chiave range|lingua|enabled; refetch manuale con force); ScheduleTimeline
con initialLoading -> skeleton animate-pulse ("Caricamento orario...") e loadError ->
banner rosso role=alert con bottone "Riprovare". Misure sulla lane:
- PRIMA: /api/schedule/analysis/lessons 3x al load (2 duplicati stesso range + 1 range
  diverso); DOPO: 1x. AC#3 ok.
- feasibility 2x resta: la seconda e' il refresh legittimo al transito score (658),
  dichiarato non-waste.
- Skeleton visto live con waitForSelector in-process durante reload ("Caricamento
  orario" presente nel DOM di caricamento).
- AC#4 "misura prima/dopo su sito pubblico": il pubblico misura il PRIMA (27,4MB/6,4s,
  3x lessons, 2x analysis, dalla UX review 24/09 sullo stesso codice base); il DOPO
  qui e' misurato sulla lane (stesso jar family): il deploy pubblico e' fuori mandato.

## TASK-665 - nav e Docs riordinate - DONE (commit ff5b094a)

Nav principale = Orario/Amministrazione/Docs/Riconciliazione; SVILUPPO (devLinkClass,
completa TASK-514) = Benchmark/Report/Vincoli/Complessita/Fasi/Storia; 'Vincoli' ora
i18n (common:constraintsNavTitle); blocco Golden calibration fuori dalla Docs page
(con le chiavi process.golden* rimosse da en+it); tooltip su TUTTE le strisce X/Y
(WeekViewGrid + TimelineView) con la semantica VERA letta dal backend
(StudentGroupCompletionService: X = celle-orario distinte con lezione, Y = slot validi
del calendario della classe, stage off-grid escluso). Verifica live: nav a 4+6 voci,
docs senza golden, 14/14 strisce con hint. NOTA attribuzione: il commit contiene
anche il fix 661 di disambiguazione completamento/interruzione (entrato per add
ampio).

## Gates di chiusura sul ramo

### /simplify (4 reviewer in parallelo + fix applicati, commit 6ca3ca96)

Applicati: merge del poll condizionata a changed (no rebuild lookup 12.5k a tick
idle), deadline restore-poll, heavy fetch saltata sull'edge false->true della
rifinitura, refetch stabile via ref, guard inFlight con pendingKey, mappa
CONSTRAINT_DESCRIPTIONS morta ritirata (~70 righe), toast fine-ciclo unificato,
reset fileInput unico, condizioni morte/fallback irraggiungibili rimossi, card
AnalysisPanel su sidebar.*Score esistenti, hasPlacedLessons condiviso
(utils/scheduleState.ts), changeLanguagePersisted esportata. SKIP dichiarati:
stagedOutcome/scoreRestorePending = TASK-667 FILED (backend fuori eccezioni
mandato); WarningBanner condiviso e refiningForDelivery optional = costo>valore ora.

### /code-review high (8 angoli, commit 79529962)

8 finding, tutti curati: F1 CRITICO (refiningForDelivery crollava a meta' rifinitura
perche' il payload pieno non lo porta: fetchSchedule ora preserva il valore dallo
stato precedente), F2 canReset include !isRefining, F3+F4 la cancellazione si riconosce
SOLO dal nostro Stop (lastSolverError RIMOSSO dal predicato: il backend vi APPENDE
divergenze non-fatali sulle consegne riuscite, ScheduleService:2823, verificato in
codice), F5 classificazione fine-ciclo debounced 5s (gap fra tentativi multi-start:
niente toast prematuri), F6 deadline 60->600 tick, F7 localStorage in try/catch
(SecurityError = app a pagina bianca nei contesti storage-bloccati), F8
sequence-number contro le risposte stantie del refetch forzato.

### Suite completa

- Run 1 (durante il ciclo 2): 1367 test, 0 failures, 5 ERRORS (boot-failure
  QuarkusTest: ErosionDiagBench, GoldenCoPresenceFreeze, LsVarianceBench,
  G13ThursdayProbe, ReportsResource), 778 skipped. CAUSA RISCIUSA: i QuarkusTest
  fanno bind sulla porta 8081, occupata dall'app LANE (il guard
  test-port-conflict-guard.sh l'ha rivelato al re-run): collisione ambientale, non
  di codice. Re-run dei 5 a lane ferma: TUTTI VERDI (EXIT 0 per ciascuno; i 3 bench
  test skippano il proprio gate come da comportamento normale).
- Run 2 (macchina ferma, lane giu'): 1372 test, 1 FAILURE
  (SingleWriterDispatcherProtocolTest.applyBroadcastCompletesOnceCapacityAppears,
  test di concorrenza MT-LS, famiglia storicamente load-sensitive, NON toccato
  dalla lane), 0 errors, 104 skipped (i bench questa volta sono GIRATI: i 5 ex-
  errori verdi). Re-run isolato del fallito: 4/4 VERDI (EXIT 0).
- Verdetto: il backend (intoccato dal ramo: solo frontend) e' verde; i due episodi
  sono ambientale/flaky, ciascuno root-causato e ri-verificato verde in isolamento.

### Ciclo 3 di verifica (661 AC#3/#4, decisione Fedora, jar fixato)

Harness dichiarato: -Dplanner.multistart.max-attempts=1 sulla SOLA lane (parametro
di test, non un cambio di modello: riproduce la forma del ciclo reale di referente del
24/09 mattina). Gates: preflight OK (jar 18:35:18 = HEAD 79529962, flags verified),
banner "Multi-start staged retry: enabled=true, maxAttempts=1" visto (prova che il
knob e' arrivato al codice vivo).

ESITO (consegna 20:28:25, tentativo 1, [-842/0]hard, [delivery] finishers 20:28:39):
con max-attempts=1 il tentativo unico e' anche l'ULTIMO e la rifinitura stanze gira
IN-CICLO: l'angolo refiningForDelivery NON scatta. Il path esercitato e' quello del
COMPLETAMENTO NATALE senza finestra di rifinitura: stato post-consegna sul jar fixato
verificato nel DOM (badge "842 conflitti residui" visibile, Risolvi tornato, score
finale [-842/0]hard/[-648.0k/-1.132]soft, NESSUN banner 'Ciclo interrotto': la
classificazione debounced ha commesso 'completato', corretto). Il toast di
completamento (vita 5s) non e' stato campionato al volo: la sua classificazione e'
provata dallo stato finale (nessun banner cancellazione + badge attivo) e dal codice
debounced; la cattura VISIVA del toast e del banner rifinitura e' demandata al ciclo 4.

### Ciclo 4 di verifica (angolo rifinitura + toast, jar fixato)

Fatto appreso (letto in MultiStartRunner.java:178-180): il budget multi-start spezza
SOLO ai confini fra tentativi, quindi budget-seconds=600 NON accorcia il primo
tentativo: serve solo a garantire che DOPO il tentativo 1 (atteso ~1.8h) la corsa si
chiuda e spedisca il winner CH-only -> refiningForDelivery=true -> finestra di
rifinitura (~10min) -> consegna con [delivery] finishers. E' la forma del ciclo di
referente del 24/09. Harness: -Dplanner.multistart.budget-seconds=600, gates ripetuti
(banner "budgetSeconds=600" visto).

ESITO (conferma LIVE del bug poi curato): tentativo 1 chiuso 21:56:47 ("best
attempt 1 shipped with CH-only rooms"), rifinitura 21:56-22:07, consegna 22:07:27
[-4/0]hard con [delivery] finishers. Sulla UI (jar pre-grace-fix): alle 21:57, con
refiningForDelivery=True lato server, NESSUN banner di rifinitura e NESSUN Stop nel
DOM: il poll era morto sulla transizione stagedStage->null PRIMA che il corner
aprisse il flag. La consegna finale ha comunque raggiunto lo schermo via fetch
successivi, ma banner rifinitura e toast di completamento non sono scoccati.
Cura (commit 64d3c1f4): grace window 90s del poll dopo ogni fine staged + priming
dei flag di ciclo dal light status al mount. Verifica: ciclo 5.

### Ciclo 5 di verifica (jar con grace fix): AC#3 e AC#4 VERIFICATE LIVE

Harness come ciclo 4 (budget-seconds=600), gates ripetuti (preflight OK, banner
budgetSeconds=600 visto). Sequenza il 25/09: tentativo 1 chiuso 23:55:24 ("best
attempt 1 shipped with CH-only rooms"), rifinitura 23:55-00:14, consegna
00:14:41 [-1953/0]hard con [delivery] finishers.

- Alle 00:01, refining=True lato server: banner VISIBILE nel DOM ("Consegna in
  corso: rifinitura finale dell'orario, la griglia mostrata non e' ancora quella
  definitiva."), Stop visibile, Reset DISABILITATO (il fix F2 della code-review
  lavora). Screenshot: img/661-refining-banner-live.png. AC#3.
- Alle 00:12:43, alla chiusura della rifinitura (refining true->false): toast
  "Ciclo a stadi completato" CATTURATO LIVE da watcher in-process con screenshot
  automatico: img/661-completion-toast-live.png. La consegna finale ha raggiunto
  la griglia via re-fetch del poll. AC#4.

TASK-661: TUTTE e CINQUE le AC verificate live (1, 2, 5 sul ciclo del profilo
importato; 3 e 4 sul ciclo 5 del profilo nuovo, dopo la cura del bug poll-morto
confermato dal ciclo 4).

## Riepilogo finale della lane

11 commit su feature/ux-planner-lane (5f938b30..6125e503 + report finale): 658, 662,
663, 664, 665 DONE con verifica live; 660 DIAGNOSI completa (cura demandata a
Fedora); 661 DONE con 5 cicli reali (2 sul profilo importato, 3 sul profilo nuovo,
5 lanciati dalla UI con i tre gate del runbook); 659 mockup consegnato (decisione a
Fedora). Gates di chiusura: /simplify 4-reviewer (11 fix), /code-review high
8-angle (8 fix, F1 critico + F4 verificato in codice), suite root-causata e
ri-verificata. Filed in turno: TASK-667 (stagedOutcome/scoreRestorePending);
TASK-666 era gia' di Fedora. L'istanza pubblica: PID 54572 start 10:17:47 del
24/09, MAI toccata (prove a inizio, fine fase e chiusura nel report).

## TASK-668 - 5 finding code-review di Fedora - CURATI (commit 16322459)

1. stopRequestedRef armato SOLO dopo api.stopSolving() riuscito (+ markCycleStop
   condiviso nello stesso punto): uno stop fallito non etichetta piu' 'interrotto'
   una consegna naturale successiva.
2. Il toast di completamento legge lo score da latestCycleRef AL COMMIT (il ref e'
   aggiornato a ogni run dell'effetto, che ha schedule?.score nelle deps): lo score
   consegnato che atterra un tick dopo la transizione entra nel toast.
3. REASONING (richiesto): la gate e' everSawCycleRef, vero se QUESTA PAGINA ha mai
   osservato stagedStage non-null o refining true. Un ciclo morto in costruzione
   (crash backend prima di pubblicare stage) non lascia mai quei segnali -> niente
   toast di successo su una griglia rotta. La forma e' volutamente minima perche' la
   classificazione STRUTTURALE (backend sa se il ciclo e' finito COMPLETE/CANCELLED/
   FAILED) e' TASK-667: finche' quel campo non esiste, il client puo' solo fidarsi
   di cio' che ha visto. Effetti collaterali accettati: una pagina aperta DOPO la
   morte del ciclo non festeggia (corretto: non ha visto niente); una pagina aperta
   a meta' ciclo vede gli stage e festeggia la fine (corretto).
4. Marchio di stop condiviso cross-tab: localStorage 'planner-cycle-stop-at' =
   timestamp, scritto solo su stop riuscito, letto da recentCycleStop() con bound
   30 minuti, cancellato quando QUALUNQUE tab osserva un nuovo stage (stale-stop
   non sopravvive al ciclo successivo). Ref locale tenuto come fallback per i
   contesti storage-bloccati (stessa filosofia del try/catch di F7). Scelta
   ragionata: derivarlo dal server richiederebbe un campo nuovo (TASK-667); il
   timestamp+clear-on-new-stage copre la razza stop->nuovo ciclo senza_chiavi
   per-ciclo perche' il backend ammette UN solo ciclo alla volta.
5. REASONING (richiesto): stagedEndGraceOnly non esclude piu' awaitingScoreRestore.
   Il caso post-Stop con score null e' ESATTAMENTE quello per cui la grace e' nata:
   prima, l'esclusione incrociata faceva azzerare il contatore a ogni run dell'
   effetto (grace mai conteggiata, inerte). Ora le due deadline coesistono nel
   tick: la grace (90s) scade prima e chiude il poll SOLO se la restore deadline
   (600s) non sta ancora contando; se sta contando, il poll sopravvive sotto quella
   (lo score-null post-stop e' onesto tenerlo in attesa fino al bound lungo).

Verifica: ui-typecheck EXIT=0. Nessun nuovo ciclo solve (mandato): i comportamenti
in shape sono gia' certificati live sui cicli 4-5; queste sono cure dei casi-limite,
coperte dal ragionamento sopra e dalla revisione del diff.
