# HANDOFF 2026-07-29 (18:25, post TASK-456 Tappa A+B) - stato per sessione fresca

Scritto a fine turno (zai). SUPERA HANDOFF-2026-07-29.md e la versione 17:50 di questo
stesso file. TASK-456 e' ora IMPLEMENTATO + MISURATO + DECISO + PRE-MISURATO (Tappa 1+2+3+A+B),
tutto pushato. Leggi questo + il task TASK-456 + i doc `task456-results/lab-fallback-*.md`.

## AGGIORNAMENTO Tappa A + B (dopo la 17:50)

**Tappa A (decisioni ratifica, pushata 9b393d75):** Fedora per maintainer ha DECISO **peso M-LF
default 500 -> 1000** (a parita' di KPI minimizza le ordinarie nei lab, "lab = ultima risorsa",
nessuno svantaggio misurato; applicato al knob nel codice). **FLAG resta default-OFF** fino alla
ratifica formale di maintainer. **R18** nello stesso pacchetto di ratifica (non esclusa
nel codice). La ratifica di maintainer = SINGOLO COMANDO: (1) accendere il flag, (2) escludere
POLIVALENTE. Col ripiego ON l'esclusione costa ZERO (hard -188 = come tenerla; -263 senza
ripiego). Tutto in `lab-fallback-measure-2026-07-29.md` sezione RATIFICA.

**Tappa B (pre-misura risposte attese, pushata 13bfebb9/4bb7b825):** con lab-fallback ON w=1000,
hook nuovo `PLANNER_TEST_ROOM_TAG_UNTAG_GROUP` de-taggia per (gruppo,materia). Residuo LAB
room-null GENUINO: baseline 182 -> (a) G14 154 -> (b) G12 144 -> (c) entrambi 120
(monotonico, stabile 90s/180s). **RICONCILIATO con l'inferenza golden di Fedora (commit
0ad379ea):** G14 mai esclusiva (100% in contemporanea) -> conferma il mio 0 capacity-freed
(Fedora la considera risolta). G12: il golden dice solo 12h contese su 121 (giovedi'
T26), quindi il mio de-tag a 76h e' LIMITE SUPERIORE (29 liberati); la risoluzione
realistica (12h) libererebbe molto meno. Residuo strutturale Lavaggio (~75, acconciatura
genuina) domina in ogni caso: serve un'altra stanza, non una risposta di tagging. Doc:
`lab-fallback-premisura-risposte-2026-07-29.md`.

FOLLOW-UP OPZIONALE: misura "12h-only" G12 (hook gruppo+materia+timeslot/docente) per la
stima puntuale invece dell'upper bound a 76h. Solo se maintainer lo chiede.

---

## (Sotto: stato alla 17:50, Tappa 1+2+3, resta valido come dettaglio implementativo)

Scritto a fine turno (zai). SUPERA HANDOFF-2026-07-29.md (TASK-437 Tappa 1 + disegno
TASK-456): TASK-456 e' ora IMPLEMENTATO + MISURATO + PUSHATO (Tappa 1+2+3). Leggi questo +
il task TASK-456 + il doc di misura `task456-results/lab-fallback-measure-2026-07-29.md`.

## Dove sono (verde, pushato)

Branch `feature/cartesian-block-entity`, HEAD `c93018ed` (sync remote). Tutto pushato.
TASK-437 Tappa 1 (subject registry) resta CHIUSO come nel handoff precedente; Tappe 2-3 di
TASK-437 restano DEFERITE.

### TASK-456 lab-fallback (domanda 21) = Tappa 1+2+3 CHIUSE + pushate

Domanda 21 (maintainer): i lab possono ospitare lezioni normali, priorita' ai lab veri. Il motore
lo vietava nel DOMINIO DEI VALORI (`Lesson.getAvailableRooms` non-lab: solo stanze senza
tag). TASK-456 lo apre dietro flag default-OFF + M-LF (medium, knob) come shelter di ultimo
resort. MISURA-PRIMA: nessuna adozione, peso e adozione = maintainer.

**Cosa e' stato fatto (dietro flag default-OFF, file protetti intoccati):**
- **Dominio valori**: `Lesson.getAvailableRooms` ramo non-lab vede anche le stanze tagged dietro
  `planner.room.lab-fallback.enabled` (letto LIVE a cache-build via `SchoolSchedule.isLabFallbackEnabled()`,
  NON static-final: l'harness fa System.setProperty tra i run). Default OFF = byte-identical.
- **M-LF** (nuovo medium in `SchoolScheduleConstraintProvider`, registrato in getFullSolvingConstraints):
  `ofMedium(planner.room.lab-fallback.weight, default 500)`, knob inline per-build (pattern M37),
  filtro `enabled && room!=null && room.isTagged() && !isLab()`. A OFF il `enabled &&` short-circuit
  = 0 match = byte-identical (golden regression non drifta). Sibling di S10 (soft 50, co-fira a ON).
- **Room.isTagged()** (@JsonIgnore): shared helper.
- **Harness** (`RoomPhaseSnapshotTest`): override `PLANNER_TEST_ROOM_LAB_FALLBACK=1` + `_WEIGHT=N`,
  count `countNonLabInTaggedRoom`, hook `PLANNER_TEST_ROOM_EXCLUDE` (punto 4 POLIVALENTE), helper
  `bridgeIntEnv` condiviso con M37, guard exportMode (no stale-cache pipe), clear system props in
  clean() (no leak cross-test).
- **CLAUDE.md**: riga M-LF in tabella Medium Constraints.

**DoD piena**: /simplify (4 agent: applicati F1 predicato fuso + F2 bridgeIntEnv + Room.isTagged;
skip F3 tail-hook + clone knob pre-esistente) + /code-review high (4 agent line-by-line/removed-behavior/
cross-file/conventions: 7 fix applicati, vedi commento task #2) + test.sh EXIT 0 (873 test, 0 fail,
14 skipped = harness env-gated).

### MISURA (doc `task456-results/lab-fallback-measure-2026-07-29.md`, cert `task447-results/cert/export.json.gz`)

**Scenario A, cert RAW (baseline 189 = 7 non-lab + 182 lab, hard -195):**
| config | non-lab rn after | LAB rn after | non-lab-in-lab | hard |
|---|---|---|---|---|
| flag OFF | 7 | 182 | 0 | -195 |
| flag ON w=1 | 0 | 182 | 10 | -188 |
| flag ON w=100 | 0 | 182 | 8 | -188 |
| flag ON w=1000 | 0 | 182 | 7 | -188 |

- (a) chiude 7/7 non-lab rn a OGNI peso (hard -195 -> -188).
- (c) LAB rn INVARIATO a 182 a ogni peso, anche w=1: i lab veri non vengono mai rubati.
- (b) peso controlla solo gli extra non-lab-in-lab: w=1 -> 10, w=100 -> 8, w=1000 -> 7 (tirato).

**Scenario B, escludi R18 (204 displaced -> 211 non-lab rn):**
| config | non-lab rn after | LAB rn after | non-lab-in-lab | hard |
|---|---|---|---|---|
| exclude, flag OFF | 75 | 182 | 0 | -263 |
| exclude, flag ON w=100 | 0 | 182 | 85 | -188 |

-> **lab-fallback rende vitabile l'esclusione R18** (richiesta della scuola prima
bloccata: 75 rn bloccati -> 0, 85 ordinarie nei lab liberi, LAB rn invariato).

**Lettura**: il lab-fallback FUNZIONA come disegnato (a + c PASS). Sul cert RAW la leva e' piccola
(+7: il residual e' dominato da 182 LAB genuini capacity-bound, che il lab-fallback non tocca;
servono piu' stanze-lab). La leva GROSSA e' l'esclusione POLIVALENTE (Scenario B). Limite
dell'harness: misura solo phase-5 (room LS) su placement timeslot fissato; il potenziale completo
(costruzione) richiede un solve intero, gated dall'adozione.

## NEXT / DECISIONI IN ATTESA (maintainer/Fedora)

**Domanda scritta nel task TASK-456 (commento #1), VADO AVANTI senza fermarmi:**
1. PESO M-LF: misurati w=1/100/1000, tutti chiudono i 7 senza rubare lab; w=1000 e' il piu' tirato.
   Quale peso adottare se/quando si accende?
2. ADOZIONE: flag resta OFF in produzione. Adottare ora o tenerlo strumentale?
3. POLIVALENTE: procedere con exclude-POLIVALENTE + lab-fallback come prossima mossa?

**Coda originale (maintainer session-start):**
- **Pre-misura risposte attese (G14 non occupa il lab / G12 altrove / entrambe)**: NON FATTA.
  Va rifatta nel quadro nuovo del lab-fallback ON perche' la dinamica cambia (vedi
  `sensibilita-risposte-scuola-189h.md`, baseline ora 185). G14 = 28 rn tutte Servizio stilistico;
  G12 = domanda 20 (76h Servizio stilistico+tecnico che contendono il lab, 14 sovrapposizioni).
- **TASK-445** (workbook Excel input): NON iniziato, solo se la coda si svuota.

## Cose NOTABILI per la sessione fresca
- Lab-fallback e' MISURA-PRIMA: il flag `planner.room.lab-fallback.enabled` resta OFF in produzione
  finche' maintainer non decide. Non c'e' adozione nello commit `c93018ed`.
- **Subtiglia isLab/requiredRoomTags**: `Course.isLab` e `Course.requiredRoomTags` sono campi
  SEPARATI. L'override harness `PLANNER_TEST_ROOM_TAG_UNTAG` svuota `requiredRoomTags` ma NON
  `course.isLab`, quindi una lezione de-taggata resta `isLab()=true` ma si comporta da non-lab.
  M-LF filtra `!isLab()` -> e' CIECO sulle de-taggate. Per questo la misura pulita usa il cert RAW
  (dove i due coincidono). Non un bug (M-LF e il count sono coerenti tra loro), ma da tenere a mente.
- **S10 co-firing**: S10 (soft 50) era inerte per le non-lab (dominio chiuso); aperto il dominio,
  co-fira con M-LF sulla stessa popolazione. Documentato.
- **R18 NON e' tagged** (cap-0, nel dominio non-lab da sempre): il lab-fallback non
  cambia il suo stato. I 7 lab tagged (R14/R15/R11/LAB.INFO 1-2/
  R16/R17) diventano shelter di ultimo livello dopo aule reali + POLIVALENTE cap-0.
- Come riprodurre la misura: vedi doc, oppure `room-tag-sensitivity.sh` come template (cert
  `task447-results/cert/export.json.gz`, all-groups, phase-5 LS 90s, override PRE-solve).
- skill gates: /simplify + /code-review high + test.sh exit code su ogni incremento di codice
  (fatti su `c93018ed`). DataGenerator PROTTETTO: non toccato (la regola e' in Lesson + provider).
