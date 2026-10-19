# HANDOFF per Fedora - turno Fedora-offline (zai, 2026-08-08)

Fedora e' crashata (~2026-08-07) e resta offline ~1 settimana. Questo turno ha tenuto avanti
la campagna dalla laptop locale (36 GB / 12 core arm64, stack planner gia' up). Tutto documentato
per la tua review keep/stop. Nessuna adozione solver in autonomia: i deliverable sono workbook
(471), analysis/policy (441), design (460), igiene (296), e deferral documentati (470/449).

## Come si lavora su questa laptop (metodologia, per quando serve)

- **La laptop regge i solve**: 36 GB / 12 core, profile dedicato `macbase` (non tocca `cert`),
  Quarkus su 8080, podman `planner-pg`. I solve staged girano qui come su Fedora (within-machine
  comparability; i tempi assoluti non sono cross-macchina, come insegna il laptop-RUNBOOK).
- **Driver baseline**: `scripts/benchmark/baseline-golden.sh` (commit 5059662f). Sibling di
  `cert-source-input.sh` ma source=golden (DEFAULT), il check veloce "questa macchina riproduce
  hard=0 + banda KPI?". Fresh profile -> import-calendario -> pre-assign -> solve-staged ->
  capture-run -> DONE marker. Lanciarlo con `caffeinate -is` (la laptop non deve addormentarsi).
- **Staged status reading** (memory `staged-solve-501`): `solverStatus=NOT_SOLVING` per tutto il
  ciclo staged (by construction); il segnale e' `stagedStage` (done quando null). hard sta
  congelato a boxed*2+room-null fino alla fase B-ROOM: NON interrompere per sospetto stallo.
- **-D per live solve sulla riga java -jar** (non MAVEN_OPTS): documentato in CLAUDE.md, valido
  anche qui (la laptop lancia via `planner.sh prod` = java -jar).

## Cosa e' STATO FATTO questo turno (commit, per review keep/stop)

| cosa | commit | tipo | verdetto per te |
|------|--------|------|-----------------|
| TASK-471 foglio workbook 22_TeoriaBlocco (teoria come input dichiarato) | cd70ccce + d8f86e47 | code+doc, DoD piena (/simplify, /code-review via requesting-code-review, test.sh 988/0/0) | DONE, ratificabile. Il mio "code-review high" iniziale era un review ad-hoc mal etichettato; ho poi eseguito `superpowers:requesting-code-review` (verdetto Ready-to-merge, 0 difetti, M1 applicato). `/code-review` slash command non e' invocabile da me (shadowed), usa lo skill. |
| TASK-296 chiuso (ACL rule-out) | (backlog move) | igiene | DONE. Già verdict 08-05. |
| TASK-441 policy anno-senza-golden + script M21 | e5caa3a5 | analysis+doc | Policy + M21 non-derivabilità. RESTA in 441: auto-disable loud (gated), confronto KPI end-to-end input-vs-golden, raccolta dichiarazioni workbook. |
| TASK-460 design ricombinazione orizzontale (AC#2) | 59daa4b1 | design doc | De-prioritizzato (asse esausto). AC#1 bad-seed trace + AC#3 tua approvazione restano. |
| 21_CadenzaAttesa workbook (cadenza come input dichiarato) | 965d1349 | code+doc, DoD piena | DONE. 245 pairs (M34/M35 reference, filtro total_hours>=10 match Java consumer). Condizionale. |
| Confronto KPI source=input vs source=golden (residuo TASK-441) | (vedi sotto) | analysis+solve | DONE. input viable: hard=0 entrambi, input -1,3pp ricorrenza / -2,4pp full-day (entro varianza). Path no-golden confermato. Doc `task441-input-vs-golden-2026-08-08.md`. |
| baseline-golden.sh driver | 5059662f | infra | DONE. |

## Cosa e' STATO DEFERRO (e perché - nessuno e' sprecato, sono bloccati su input esterni)

- **TASK-470** (cura M-TR construction-origin): la dedup e' **gia' committed default-OFF** nel
  WeeklyTemplateBuilder (`isTheorySpreadEnabled`/`subjectAlreadyOnDay`/`skipSubjectOnDay`). Il
  verdetto TASK-470 (2026-08-01) raccomanda di **NON adottarla** (lever parziali: 1h+dedup=1142,
  2,6x golden 440). Il suo valore e' bloccato sulla conferma referente di TASK-471 (la pratica
  dichiarata). Adottare ora e' prematuro. Riaprire se 471 confermata ridimensiona il gap.
- **TASK-449** (allocatore lab mono-stanza): le celle (weekday, slot, block) per (gruppo, tag)
  **non sono derivabili** e `LabRotation` e' VUOTO INTENZIONALMENTE nel 2026/27. Bloccato sulla
  popolazione del workbook (dichiarazione scuola). Si sblocca quando il workbook raccoglie le
  celle lab (colonna c del censimento).

## I tre trovare chiave per la tua review (pushback onesti)

1. **TASK-470 non e' il prossimo passo**, contrariamente a quanto il campaign-status-08-05
   lascia aperto: il verdetto 470 dice "non adottare", la dedup esiste gia', e 470 e' bloccato
   su referente. L'ho deferred invece di eseguirlo in autonomia.
2. **TASK-441 M21/T20 non sono golden-runtime**: sono costanti Java esposte come colonne
   workbook (08_Materie capOreGiorno/settimanaInizio). M21_EXEMPT non e' derivabile da
   subjects.csv (script: best rule 7,5% precision). La "riproducibilita'" no-golden e' la
   colonna dichiarata, non una derivazione.
3. **TASK-460 e' nell'asse esausto**: la campagna 08-05 ha dichiarato l'asse
   metaheuristic+costruzione ESAURITO (pivot al MODELLO). 460 e' li' dentro. De-prioritizzato.

## Il collo di bottiglia reale (non e' il compute)

L'asse MODELLO (il pivot dichiarato) ha i suoi task produttivi **bloccati su input esterni**:
- 470 -> conferma referente via workbook 471.
- 449 -> popolazione LabRotation (workbook). [cadenza 21_CadenzaAttesa DONE 965d1349; LabRotation DEFERRED vedi sotto]
- 441 -> dichiarazioni colonna-c (6 Q22, cadence DONE, M21/T20) + conferma scuola.
- m-13 -> decisione tua (accettare multi-start come chiusura procedurale del path input).

Quindi **il work autonomous locally e' limitato finché referente non risponde** al workbook. La cosa
più utile in autonomia (oltre a questo turno) e': (a) popolare il workbook TASK-445 con le
domande colonna-c [cadenza DONE; LabRotation DEFERRED: re-implementare buildMonoRoomCadence
(logica modale) in Python ha lo stesso rischio di divergenza che la cadenza I1 ha catchato
(JSON non filtrato vs consumer Java, 13 micro-pairs) ma peggiore; meglio emit Java-side dove
la logica vive (HybridPhase1aService.java:1164); le 6 domande Q22 restano da aggiungere a
06_DocentiDisponibilita], (b) il confronto KPI end-to-end source=input vs source=golden (441
residuo, finche' il golden c'e'), (c) tenere pulito il backlog. I solve pesanti non servono:
l'asse metaheuristic e' esausto.

## Baseline laptop (certificazione solve host) - PASS con caveat

Driver `baseline-golden.sh`, profile fresco `macbase`, source=golden (DEFAULT, confermato dal
banner: `grasp=false seed=0` attempt 1, constrained-teacher/first-hour-slot ON, multi-start ON
maxAttempts=3). Capture in `baseline-laptop-2026-08-08/`.

**Risultato (DONE 15:11, capture-run.sh):**
- **hard: 0** (target binario) - PASS
- **ricorrenza unpinned: 75,9%** (banda ratificata 75,6-75,9%, esattamente al top) - PASS
- **full-day: 86,8%** (ratificato ~87,5%, in banda; golden ~91,9%) - PASS
- **buchi interni: 0**, day-start scarti >=3 vs golden: none - PASS
- medium -1257k (sotto la banda -963k/-1047k; medium e' secondary + NON_REPRODUCIBLE, e la
  fase LS-Room ha pagato un piccolo costo medium per azzerare l'hard -9404 -> 0).

**Verdetto: la laptop e' un solve host valido.** Riproduce hard=0 + KPI ratificati (ricorrenza al
top della banda). Certificazione PASS.

**CAVEAT onesto (wall time + multi-start):** questa run ha preso **2 tentativi multi-start**
(109 min wall), non 1. Attempt 1 ha girato B-TIMESLOT-1 per il budget pieno (secondsSpentLimit
3600s = 60 min) e NON ha chiuso hard in B-ROOM, cosi' il multi-start ha ritentato (attempt 2,
B-TIMESLOT-1 converto in 15 min -> B-ROOM -> 0hard). source=golden e' normalmente good-seed
(single-attempt ~36-55 min, TASK-483); questa run era "bad-ish" (2 tentativi). Il multi-start ha
gestito la cosa come disegnato (pick-best -> 0hard). NON e' un difetto: e' la robustezza
ratificata (TASK-483) al lavoro. MA significa che **un run source=golden su questa laptop puo'
costare 36-109 min** (1-2 tentativi); per planificare i tempi di campagne A/B locali. Una 2a run
di baseline confermerebbe se 2-tentativi e' comune o un caso (pratica stability del laptop-RUNBOOK).

Tempo reale per la laptop come solve host: confermato. Per A/B future, conteggiare entro-machine
(non cross-Fedora, laptop-RUNBOOK ground rule).

## Backlog hygiene (collisioni ID - NON applicato in autonomia, deferto a te)

`backlog doctor` (preview read-only) rileva 3 gruppi di ID duplicati. NON ho applicato `--fix`
perche' il tool e' **all-or-nothing** e uno dei tre ha un'ambiguita' che richiede il tuo giudizio:

| collisione | file | doctor propone | problema |
|------------|------|----------------|----------|
| TASK-486 | 2 file stessa decisione ratificata (M-TC per-retry) | keep "terza-opzione-ratifica"=486, other -> 494 | basso: CLAUDE.md "RATIFICA TASK-486" resta valido (canonico mantenuto) |
| TASK-38 | 3 file vecchi branch-family (cartesian-shadow-lab-blocks / Shadow-Branch / .01) | keep cartesian-shadow-lab-blocks=38, Shadow-Branch -> 492 | basso: rfa vecchia, non in ratifiche attive |
| TASK-230 | completed (M20 sparse-day, STORICO) + active (Timeline-view UI) | keep active Timeline-view=230, completed M20 -> 493 | **ALTO**: il doctor riassegna NELLA DIREZIONE SBAGLIATA per la storia. CLAUDE.md "Sparse-day horizontal compaction (M20, task-230, 2026-03-14)" punta al completed M20: rinominarlo a 493 orfana il riferimento storico. Il canonico qui e' il completed M20 (originale), il colliding newcomer e' il Timeline-view che dovrebbe prendere un nuovo ID. |

`backlog doctor` e' safe (rename atomico frontmatter + report dei riferimenti ambigui, NON tocca
CLAUDE.md). Ma l'ASSEGNAZIONE canonica (specie TASK-230) e' una tua chiamata. Suggerimento:
applica --fix dopo aver deciso la direzione di TASK-230 (probabilmente invertirla: keep completed
M20=230, renumber active Timeline-view). I riferimenti ambigui da revieware sono elencati dal
doctor (task-028/task-046/task-038.01 citano TASK-38; CLAUDE.md cita task-230 per M20 e TASK-486).

TASK-296 e' stato CHIUSO (Done, gia' verdict 08-05).

## Stato git
- branch `feature/cartesian-block-entity`, pushato fino a 59daa4b1.
- commit del turno: 5059662f (driver), e5caa3a5 (441), 59daa4b1 (460) + cd70ccce/d8f86e47 (471).
- pre-existing untracked (task284/task296-results campaign dumps) non miei, non committati.
