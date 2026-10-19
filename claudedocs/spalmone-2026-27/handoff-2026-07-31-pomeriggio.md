# HANDOFF pomeriggio 31/07 (zai, ~14:45) - coda: TASK-466(a) -> TASK-465 -> TASK-464

HEAD `feature/cartesian-block-entity`, pushato fino a `0483124e` (E3) + spike commit. Coda originale
(T19/E2/E3) CHIUSA; SPIKE co-presenza CHIUSO (gate TASK-464 superato). Questo handoff e'
autocontenuto per riprendere dopo /clear.

## FATTO e pushato (colonna 1)

- **(T19) A/B max-weekdays 1 vs 2 - NETTO NEGATIVO** (`t19-2026-07-31/VERDICT.md`). Full-solve
  staged x2, baseline riusato come baseline comune degli A/B (config default). max-weekdays=2
  peggiora medium -54k (-1025515 vs -971488), soft -12k, full-day -1,2pp, ricorrenza UNP -1,0pp;
  hard -42 migliore (-68 vs -110, room-null 92->42) ma hard gia' quasi-zero. Difetto-tripletta
  ASSENTE in entrambi i bracci (G06/Barberia 0/47, T19 0/128 non collocate): il solver colloca
  gia' T19 da solo. Scoperta: mwd=2 pinna il cluster lun+mar intero (T19 23 + T24 39 target).
  Soglia resta default 1, nessuna adozione.
- **(E2) A/B rimozione esenzione isG14 - KEEP** (`e2-isG14-2026-07-31/VERDICT.md`). Baseline
  riusato. Patch `isG14()->false`. medium MIGLIORA ~108k (-863692 vs -971488), ricorrenza pari,
  MA hard catastrofico -5826 (vs -110) per room-null 92->5808 sistemico. E' CONVERGENCE FAILURE
  (room-phase tagliato a 600s ancora attivo), NON infeasibility: l'esenzione e' un convergence-aid
  (G14 forzata densa satura le stanze). Spike S1 "no-op al construction checkpoint" corretico ma
  myopic (effetto emerge nel room-phase). Patch revertita, nessun code change.
- **(E3) A/B M20_PROGRESSION_STEP 500 vs 1500 - NEUTRO, KEEP 500** (`e3-m20step-2026-07-31/VERDICT.md`).
  Baseline riusato. Patch step 1500. n=2: run 1 CONVERGENCE FAILURE (-6117hard, firma tipo E2), run 2
  (relaunch post-reboot) CONVERSO PULITO a -111hard (= baseline -110). NEUTRO sul converto: hard -111
  vs -110, medium -971111 vs -971488 (rumore), room-null 93 vs 92, ricorrenza 75,4% vs 75,7%. Il
  gradiente M20 NON e' una leva sul finale (conferma spike S3 a full-solve). KEEP step 500. Lezione di
  metodo: il -6117 del run 1 avrebbe dato falso verdetto senza il relaunch. Patch revertita.
- **SPIKE room-null vs co-presenza golden - GATE TASK-464 SUPERATO**
  (`spike-room-null-copresenza-2026-07-31/{VERDICT,ANALYSIS}.md`). Read-only. Tutti i 92 room-null
  sono acconciatura, 100% cade su celle golden co-presenza 2+ gruppi (Mon AM + Thu). Solver capped
  lab a 1/slot (H2), golden ne mette 2-5 (Thu 08-12 depth 5 = 1/2/G10 + G12 + G14, verificato
  dal CSV). 95% ore acc golden e' co-presenza. Clearance: cap2=48, cap3=2, cap4=23, cap5=19 (full).
  Caveat: golden senza room -> non distingue "5 classi in 1 lab" da "alcune in aula normale" ->
  modellazione gated a referente. Audit inverso: tagging lab-obbligatorio corretto (difetto = cap H2).

## CODA RESIDUA (ordine concordato con maintainer)

### (1) TASK-466 parte (a): diff meccanico incarichi-vs-golden - READ-ONLY, NON toccare DataGenerator
Diff per (docente, gruppo, materia) fra `src/main/resources/seed/incarichi.csv` e il golden
`src/main/resources/seed/lessons.csv`, con le TRAPPOLE NOTE escluse: sabati, Fasullo (teacher/
subject, 112h al 2026-09-19), Stage off-grid. Alimenta il doc di notifica per referente (validazione a
posteriori: divergenza puo' essere vincolo che manca o errore umano). Direttiva "golden autoritativo
sugli input" ora in CLAUDE.md (ESTENSIONE AGLI INPUT, riga ~617). La parte (b) di normalizzazione seed
la gate-a maintainer sul diff. Tool riferimento: `scripts/analyze/derive-golden-*.py` (cross-check
indipendenti). Attenzione alle trappole conteggio ore (CLAUDE.md sezione KPI: parse_golden_csv non
filtra il weekday).

### (2) TASK-465: audit inverso tagging acconciatura - READ-ONLY misura sul golden
 gia' toccato dallo SPIKE (§5): il tagging lab-obbligatorio delle 4 materie acc (Barberia, Lavaggio,
Servizio stilistico, Servizio tecnico e stilistico) risulta CORRETTO (nessuna esiliata a cella
non-co-presenza). TASK-465 puo' approfondire / confermare su base golden.

### (3) TASK-464: co-presenza ospite legale - design + prototipo flag OFF (GATE SUPERATO)
Lo spike conferma il 100% dei room-null su celle co-presenza golden, quindi il gate e' MET. MA:
- Model "cap 2" pulisce solo 48/92; clearance pieno richiede cap 5 (una stanza, 5 classi contemporanee).
- **Gated alla conferma di referente**: la scuola condivide davvero il lab acc fra 5 classi al giovedi',
  o alcune ore sono in aula normale? (golden senza room non lo dice; l'audit non ha trovato
  de-tagging, ma non puo' escluderlo definitivamente).
- Coppie ospiti candidate (dove il solver oggi sceglie male): G12 ospite di G02 Mon 08-12;
  G14+G12 ospiti del blocco 1/2/G10 Thu 08-13. Oggi il baseline lascia G12+G14 possedere
  il lab e starva G02 (70h room-null).
- Prototipo flag OFF (`planner.*.enabled` shape come M-LF/constrained-teacher), DoD piena se si
  implementa codice. Modella la co-presenza come cap-N per-cell (tag, datetime) - attento: e' uno
  spostamento di tier (H2 hard -> multi-occupancy), decisione di modello di maintainer.

## Stato ambiente
- HEAD `0483124e` pushato (E3 + spike su 0bccf287 di maintainer che ha TASK-464/465/466 + direttiva CLAUDE.md).
- **App UP su production col finale E3 relaunch** (step 1500, ma config reverito a 500 nel codice;
  il DB ha la solution E3-step1500 relaunch). Per run nuovi: stop+delete production, restart.
- **laptop RIAVVIATO ~13:33** (causa poco chiara: nessun .panic, pmset inconcludente; annotato senza
  indagine). Conseguenza operativa CRITICA: dopo un reboot, **`podman machine start` e' necessario**
  prima di ogni container (la VM Linux non si auto-avvia). Sintomo: `podman run` fallisce con
  "connection refused" / container "Exited ... 292 years ago" (timestamp sballato). Fix:
  `podman machine start`, poi `podman rm -f planner-pg` se exited, poi prod-start-and-wait.
- **GOTCHA prod-start-and-wait + timeout tool**: il `java -jar` raw con drop-and-create impiega
  ~70-84s la prima volta (DataGenerator seed). Il timeout di default 120s del Bash tool lo KILLAVA
  a meta' startup (l'app moriva subito dopo "started"). Usare SEMPRE `timeout: 300000` (5min) sul
  Bash tool per prod-start-and-wait. Per leggere una solution persistita SENZA wipe: PLANNER_JAVA_OPTS
  `-Dquarkus.hibernate-orm.schema-management.strategy=update` (preserva il volume).
- caffeinate attivo (background, lanciato via run_in_background per via dell'hook no-&).
- solverConfig = canonico (tabu7). Default omogenei: first-hour-slot ON + constrained-teacher ON.

## Lezioni di metodo (sessione)
- **n=1 inganna sui convergence-failure**: E3 run 1 (-6117) vs run 2 (-111) same code. Un A/B che
  mostra hard-catastrofe va confermato con un secondo run prima di verdictare (varianza di
  traiettoria, non effetto deterministico). Il relaunch di maintainer era la chiamata giusta.
- **isG14 e M20-step sono entrambi LS-only al construction-checkpoint ma movono il finale**: E2
  muove hard (convergence), E3 no. Lo spike S1/S3 "no-op al checkpoint" e' myopic per i parametri LS.
- **Subagent per analisi read-only pesanti**: lo SPIKE delegato a subagent ha tenuto pulito il
  contesto e tornato dati solidi (92 riprodotto, depth verificato). Verifica indipendente del claim
  piu' sorprendente (Thu depth-5) fatta dall'orchestratore direttamente sul CSV.

## Come riprende
- Questo handoff + `QUADRO-2026-07-30.md` (SINTESI RISVEGLIO + sezioni T19/E2/E3/SPIKE in colonna 1).
- Primo task: TASK-466 parte (a) diff incarichi-vs-golden (read-only).
- Riferimenti: VERDICT di ogni A/B + `spike-room-null-copresenza-2026-07-31/`.
- Se il contesto e' fresco e resta giornata: TASK-466(a) -> TASK-465 -> TASK-464 (flag OFF).
