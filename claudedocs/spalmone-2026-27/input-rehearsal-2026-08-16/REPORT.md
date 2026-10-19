# REPORT rehearsal input-path production run (laptop, 2026-08-16)

> Filone 1 del mandato Fedora 16/08. NON e' un A/B: e' la prima esercitazione
> del path che sara' quello di produzione quando il golden non ci sara' piu'
> (maintainer ha approvato l'accelerazione dell'autonomia dal golden). Obiettivo:
> osservare le frizioni che il golden-path maschera.

## Configurazione (certificata CI-robusta dal census n=19)

- Driver: `scripts/benchmark/baseline-golden.sh` (env-respecting) con
  `PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input
  -Dplanner.multistart.max-attempts=5"`, profilo fresco `inputrehearsal`,
  POLL_CAP=240.
- Nessun altro -D. Tutto il resto ai default ratificati ON, incluse le due
  ratifiche dello STESSO GIORNO (bbf89571, pre run): adaptive weights forma B
  DEFAULT ON e rimozione esenzione G14. Banner verificato nel log:
  `Multi-start staged retry: enabled=true, maxAttempts=5`,
  `Adaptive move weights (TASK-500): enabled=true` + arming del decider,
  `TASK-452 source=input: built input-derived profiles for 2 constrained teacher(s)`.
- Ciclo completo del driver: profilo fresco -> import-calendario -> pre-assign
  -> solve-staged -> capture-run.

## Esito

| metrica | valore |
|---|---|
| hard | **0** (buon bacino, tentativo 1/5, multi-start non e' mai entrato in retry) |
| medium | -1.044.443 |
| soft | 4.103 |
| wall | ~23 min (10:03:41 -> 10:26:10) |
| full-day | 87,5% (banda source=golden ~90,0; golden ~91,9 ex-G14) |
| day-start | 99,5% |
| ricorrenza unpinned | 74,2% (source=golden ~75,3) |
| BUCO-INTERNO | 0 |
| forma deficit | 180 giorni sotto quota, 152 a -1h (84%) |
| ERROR nel app.log | 0 |

Il gap input-vs-golden e' visibile (medium ~8% peggiore, full-day -2,5pp,
ricorrenza -1,1pp) e coerente col census: il path FUNZIONA end-to-end,
0-hard al primo tentativo, nessuna frizione bloccante FINCHE' il file golden
resta nel repo. Le frizioni sono sotto.

## Frizioni osservate (il deliverable della rehearsal)

### 1. STRUTTURALE, bloccante per l'era senza golden: due letture runtime del golden restano sul path di produzione

Il flag source=input cambia SOLO il canale del pre-assign constrained-teacher.
Due meccanismi ratificati leggono comunque `seed/lessons.csv` a runtime,
indipendentemente dal source (censite TASK-452 #6/#7; la rehearsal le rende
visibili nel log di produzione):

- **H-COPRESENCE depth map** (`ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth`,
  banner: `depth>=2 cells=91 (of 136 tallied)`). Senza il file golden la mappa
  e' vuota -> `SchoolSchedule.goldenCoPresenceDepth` ritorna 0 ovunque ->
  `isCoPresenceCell` sempre false -> **l'esenzione-ospite di H2 NON si sblocca
  piu'**: la co-presenza ospite diventa strutturalmente impossibile e tornano i
  room-null hard che TASK-464 aveva curato (94 acconciatura -> hard). NON e'
  degradazione graziosa: e' una regressione hard del tier stanze.
- **Slot-0 anchors del first-hour block**
  (`loadGoldenSlot0Anchors`, WeeklyTemplateBuilder.reserveFirstHourBlock).
  Senza il file: mappa vuota -> `want` nullo per ogni gruppo -> il blocco
  prima-ora si riserva comunque ma l'ordinamento perde la preferenza
  docente-ancora e cade sul solo semanticCourseKey. DEGRADAZIONE GRAZIOSA
  (qualita', non hard).

Conseguenza operativa: le due letture sono LE domande da chiudere nel workbook
(TASK-445) prima di spegnere il golden: (a) profondita' co-presenza praticata
per cella (tag, weekday, slot), (b) docente-ancora slot-0 per (gruppo,
weekday). La (a) e' bloccante, la (b) e' qualita'.

### 2. Cosmetica: la narrazione del driver mente sul source

`baseline-golden.sh` logga stringhe hardcoded "source=golden DEFAULT" mentre
gira input (e' il sibling thin di `cert-source-input.sh`, che ha la narrazione
onesta ma gli opts cablati senza il knob max-attempts). Chi legge il driver.log
senza guardare il PLANNER_JAVA_OPTS nella prima riga viene ingannato. Fix
possibile (non fatto qui): far rispettare PLANNER_JAVA_OPTS anche a
cert-source-input.sh e usarlo come veicolo canonico del path input.

### 3. Come previsto, nessuna sorpresa sul resto

- Phase 0 constrained-teacher da INPUT: profili derivati per 2 docenti
  (T01 THURSDAY, 35 pin targets): il canale input fa il suo lavoro.
- availabilita' golden-derived (Q22, 2/4 docenti rotazione): dormiente, le
  rotazioni non esistono (LabRotation.count()==0) e Phase 1/2/3 sono saltate.
- Banner tutti presenti (multi-start 5, adaptive armed con basi 5/3/2/1 + 2/1,
  enumerator OFF): NESSUN flag silently dropped. Indicatore attempt 1/5 visibile
  nei poll.
- import-calendario e pre-assign: senza errori; app.log pulito (0 ERROR).

## Conclusione

Il path input e' esercitabile end-to-end con la config CI-robusta e converge
0-hard al primo tentativo. L'autonomia dal golden e' oggi esattamente il
censito TASK-452: due letture runtime (una delle quali, la co-presenza depth,
provocherebbe una regressione hard quando il file sparisce). Nessun altro
attrito nuovo scoperto.

## Artefatti

Cattura canonica completa (capture-run.sh): score.txt, analysis.json,
export.json.gz, lessons.txt, KPI battery (fill/daystart/resemblance/holes/
cadence/sharpness/moda/anatomy), phases.json, viz HTML, app.log, driver.log.
