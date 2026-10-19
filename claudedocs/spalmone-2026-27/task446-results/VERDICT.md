# TASK-446: Censimento blind-map (Alternativa B, room=null in costruzione)

**Data:** 2026-07-28. **Autore:** laptop (misura only). **Stato:** CENSIMENTO (pre-codice), per verifica Fedora.
**Scope:** per OGNI vincolo in `SchoolScheduleConstraintProvider` (SSCP) e `BaseConstraintProvider` (BCP),
classificare `forEach` vs `forEachIncludingUnassigned`, e il comportamento con `room=null` mid-solve,
INCLUSI H-D1 e i join secondari. Deliverable di validazione critica, vincolante per procedere con (b).

**Domanda portante dell'Alternativa B.** Il costruttore (`DayClusteredConstructionPhase`) lascia
`room=null` sui non-pinnati. Le fasi timeslot girano quindi su lezioni con timeslot assegnato ma room=null.
B-ROOM/CH-room (ultimo stage) assegna poi tutte le stanze. Funziona se e solo se:
(P1) i vincoli **room-based** diventano silenti su room=null (nessun fuoco spurio, nessuna NPE), e
(P2) i vincoli **timeslot** continuano a fuocheggiare su room=null (nessuna regressione della classe TASK-377).

---

## 1. Verdetto sintetico

| # | Questione | Esito |
|---|-----------|-------|
| P1 | Vincoli room-based silenti su room=null? | **SI.** I 7 room-touching hanno tutti guardia `room != null` esplicita; H2/H16b anche `forEach`/`forEachUniquePair` (initialized-only). |
| P2 | Vincoli timeslot continuano a fuocheggiare? | **SI.** Tutti su `forEachIncludingUnassigned`/`timeslottedLessons`/`precompute+forEachUnfiltered`. |
| H-D1 | Esiste come vincolo? | **NO.** Fantasma: nessun `.asConstraint` registrato. Sostituito dall'entita' `LabAssistant` + M-LA (vedi sezione 3). |
| Assunzione portante | Lezione timeslot-set + room-null esclusa da `forEach`/`forEachUniquePair`? | **CONF. ALTA** (modello TASK-377 + ancoraggio empirico produzione, sezione 4). Il checkpoint fine-costruzione dara' conferma definitiva. |
| Blocker per (b)? | | **NESSUNO.** Una guardia preventiva opzionale su H2 documentata come fallback (sezione 5). |

---

## 2. Censimento vincolo-per-vincolo

Legenda colonna `Source`:
- `IEU` = `forEachIncludingUnassigned(Lesson)` (diretto o via helper `timeslottedLessons`/`realTeacherTimeslottedLessons`)
- `FE`  = `forEach(Lesson)` (initialized-only, esclude room-null)
- `FUP` = `forEachUniquePair(Lesson)` (initialized-only, esclude room-null)
- `PC`  = `precompute(...forEachUnfiltered(Lesson))` (tutte le entita', poi filtro)
- `FEQ` = `forEach(DailyActivityQuota)` (non-Lezione)

`Room?`: tocca `lesson.room`? (enumerato da `grep room` sui due provider)

### 2a. BaseConstraintProvider (24 vincoli registrati)

| ID | Source | Room? | room=null mid-solve | Verdetto |
|----|--------|-------|---------------------|----------|
| H1 Teacher conflict (352) | IEU (`realTeacherTimeslottedLessons`) | no | fuocheggia (timeslot) | P2 ok |
| **H2 Room conflict (378)** | **FE** (365) | **si** | **silente**: FE esclude room-null; joiner `equal(room)` richiede room non-null dal lato FROM | P1 ok (assunzione sez. 4) |
| H3 Student group conflict (405) | IEU (`timeslottedLessons`) | no | fuocheggia | P2 ok |
| H4 Stage blackout (433) | PC (`nonProfLessonStagePairs`, filtro `timeslot!=null` 426) | no | fuocheggia | P2 ok |
| H4.3 Prof single-day during stage (462) | IEU + join(ProfessionalizzantiDay, StagePattern) | no | fuocheggia | P2 ok |
| H5 Teacher unavailability (481) | IEU + join(TeacherAvailability) | no | fuocheggia | P2 ok |
| H6 School holiday (492) | IEU + join(SchoolHoliday) | no | fuocheggia | P2 ok |
| H14 Student group start date (505) | IEU | no | fuocheggia | P2 ok |
| H0 Unassigned lesson (518) | IEU | no | fuocheggia | P2 ok |
| **H-M2 Room suitability (530)** | IEU (525) | **si** | **silente**: filtro `l.room != null && ...` | P1 ok |
| H20 Group calendar closed slot (544) | IEU | no | fuocheggia | P2 ok |
| H23 Quota-bound lab on zero-lab-quota day (573) | IEU | no | fuocheggia | P2 ok |
| H24 Aula on zero-aula-quota day (590) | IEU | no | fuocheggia | P2 ok |
| H25 Stage outside stage days (619) | IEU | no | fuocheggia | P2 ok |
| H26 No-session day (651) | IEU | no | fuocheggia | P2 ok |
| M28 Afternoon reserved for stage (688) | IEU (filtro `slotIndex >= AFTERNOON_START`) | no | fuocheggia (room-indipendente) | P2 ok |
| **M-LA Lab assistant conflict (713)** | IEU (700) + join(Lesson, `equal(timeslot)`) | **si** (lato lab) | **silente sul lato room**: filtro `labLesson.room != null && tags.contains(tag)` (709); il lato assistente e' timeslot-puro | P1 ok |
| **M3 Room capacity (725)** | IEU (720) | **si** | **silente**: filtro `lesson.room != null && capacity < size` | P1 ok |
| M5 Informatica after stage (739) | PC (`informaticaStagePairs`) | no | fuocheggia | P2 ok |
| M26 Sicurezza before stage (774) | IEU | no | fuocheggia | P2 ok |
| M30.1 Lab day quota fill, match (824) | FEQ + join(`timeslottedLessons`) | no | fuocheggia | P2 ok |
| M30.2 Lab day quota fill, no match (836) | FEQ | no | fuocheggia | P2 ok |
| M31 Lab day single contiguous run (871) | IEU | no | fuocheggia | P2 ok |
| M12 Overflow beyond target end (953) | IEU | no | fuocheggia | P2 ok |

### 2b. SchoolScheduleConstraintProvider (31 vincoli registrati)

| ID | Source | Room? | room=null mid-solve | Verdetto |
|----|--------|-------|---------------------|----------|
| H7 Max teacher daily hours (333) | IEU (313) | no | fuocheggia | P2 ok |
| H11 Max student group daily hours (367) | IEU (344) | no | fuocheggia | P2 ok |
| H11-T28 T28 6h/day (399) | IEU (374) | no | fuocheggia | P2 ok |
| M18 Day start from first open slot (479) | IEU (462) | no | fuocheggia | P2 ok |
| M15 Same-day same-subject consecutive (572) | IEU via `adjacentSameSubjectPairs`(`nonLabTimeslottedLessons`) | no | fuocheggia | P2 ok |
| S16 Consecutive same-subject same teacher (623) | IEU via `adjacentSameSubjectPairs`(`timeslottedLessons`) | no | fuocheggia | P2 ok |
| **H16b Consecutive same-subject-teacher same room (642)** | **FUP** (631) | **si** | **silente** (TRIPLO): FUP initialized-only + filtro `a.room!=null && b.room!=null` (636) + `!equal(room)` (640). E' la sorgente della paralisi B-TIMESLOT attuale (-963hard). | P1 ok |
| S17 Reward same-subject block pair (682) | IEU via `adjacentSameSubjectPairs`(`realTeacherTimeslottedLessons`) | no | fuocheggia | P2 ok |
| M29 Professionalizzanti at day edges (774) | IEU (759) + join(Lesson) | no | fuocheggia | P2 ok |
| M23 Block lesson pair togetherness (819) | IEU (810) + join(Lesson) | no | fuocheggia | P2 ok |
| H17 Vertically compact (855) | IEU (842) | no | fuocheggia | P2 ok |
| S1 Minimize teacher gaps (874) | IEU (865) + join(TeacherAvailability) | no | fuocheggia | P2 ok |
| S3 Teacher preference (886) | IEU (878) | no | fuocheggia | P2 ok |
| M21 Max 2h per subject per day (932) | IEU (918) | no | fuocheggia | P2 ok |
| M32 Template deviation (1002) | IEU (994) | no | fuocheggia | P2 ok |
| M27 Subject start-period preference (1075) | IEU (1056) | no | fuocheggia | P2 ok |
| M24 At most one early start day/week (1132) | IEU (1107) | no | fuocheggia | P2 ok |
| M25 Required free day/week (1164) | IEU (1183) + join(IEU) | no | fuocheggia | P2 ok |
| S7 Reward consecutive student lessons (1192) | IEU (1183) + join(IEU) | no | fuocheggia | P2 ok |
| S8 Isolated student lesson (1205) | IEU (1197) + ifNotExists(IEU) | no | fuocheggia | P2 ok |
| S9 Dilute hours (1227) | IEU (1216) | no | fuocheggia (DISABILITATO per CLAUDE.md, ma registrato) | P2 ok |
| S12 Weekly subject presence (1289) | IEU (1282) | no | fuocheggia | P2 ok |
| M34 Weekly subject hours above golden cadence (1405) | IEU (1384) | no | fuocheggia | P2 ok |
| M35 Weekly subject spread below golden cadence (1464) | IEU (1445) | no | fuocheggia | P2 ok |
| S13 Weekly stability (1548) | IEU (1535) + join(IEU) | no | fuocheggia | P2 ok |
| **S10 Discourage generic in labs (1564)** | IEU (1554) | **si** | **silente**: filtro `lesson.room != null && tags...` (1555) | P1 ok |
| **S11 Student group room affinity (1589)** | IEU (1571) | **si** | **silente**: filtro `lesson.room != null` (1574) + `countDistinct(room)` | P1 ok |
| M20 Daily quota fill (1961) | FEQ (1998) + join(`timeslottedLessons`) | no | fuocheggia | P2 ok |
| M20.2 Empty expected day (2016) | FEQ | no | fuocheggia | P2 ok |
| M33 Surplus beyond expected day (2113) | IEU (2154) | no | fuocheggia | P2 ok |
| M22 Calendar compactness (2178) | IEU | no | fuocheggia | P2 ok |

**Totale: 55 vincoli registrati (24 BCP + 31 SSCP; coincide uno-a-uno con i 55 `.asConstraint` da grep). 7 room-touching (tutti silenti su room=null). 48 room-indipendenti (tutti continuano a fuocheggiare).**

---

## 3. H-D1 (doppio / co-teaching): FANTASMA

- `grep -rn 'asConstraint' src/main` per `d1|doppio|assistant` torna **un solo hit**: `"M-LA - Lab assistant conflict"` (BCP:713).
- Nessun `.asConstraint("H-D1...")` o `.asConstraint("...D1 -...")` esiste in `src/main/java`.
- `LabAssistant.java` javadoc (riga 13-19): il doppio e' definito teacher->room, e la pratica attuale e'
  **M-LA (MEDIUM)** che penalizza schedulazioni dell'assistente in conflitto con un lab nella sua stanza,
  "rather than requiring attendance at all sessions (old HARD H-D1 approach)".
- I riferimenti a "H-D1" sopravvivono **solo in commenti** (`HoleRepair.java:98-103`, `SurplusRebalance.java:152-154`)
  e nella **riga stale di CLAUDE.md** (tabella Canonical Constraint IDs elenca H-D1 / H-D1.1-4, da correggere a tempo).
- **Impatto su Alt-B: nessuno.** Non essendo registrato, non ha superficie room da blindare. L'eventuale
  semantica doppio vive interamente in M-LA, gia' classificata silente sul lato room (sezione 2a).

---

## 4. Assunzione portante + ancoraggio empirico

**Assunzione A.** Una `Lesson` con `timeslot` assegnato e `room=null` NON e' "fully initialized" per
`forEach(Lesson)` / `forEachUniquePair(Lesson)`. Da A segue che H2 (FE) e H16b (FUP) escludono le room-null
senza filtri aggiuntivi.

**A e' un fatto EMPIRICO di questa versione di Timefold, NON una conseguenza del modello documentale**
(verifica Fedora). `Lesson.room` e' `@PlanningVariable(allowsUnassigned = true)` (`Lesson.java:132`), e la
semantica documentale di Timefold ammette che un variable `allowsUnassigned` con valore null conti come
assegnato ai fini di `forEach`: in quel caso A sarebbe FALSA. Pero' in questa versione A osservabilmente
REGGE, confermata due volte:

1. **TASK-377 (storico).** H1/H3/H16a costruiti su `forEach(Lesson)` erano CIECHI sulle room-null durante
   le fasi timeslot: l'evidenza originale che una room-null non entra in `forEach`. La fix allora fu
   passare a `forEachIncludingUnassigned` + filtro timeslot (javadoc `BCP:110-119` documenta quel modello,
   ma e' descrizione del comportamento, non la base logica di A).
2. **final-confirm-435 mid-solve.** Sulla griglia mid-solve, con lezioni room-null prodotte dalla
   compattazione (`DayClusteredConstructionPhase:283` null-a le room), H2 risultava ESATTAMENTE 0 in
   `analysis.json`. Se le room-null entrassero in `forEach` e il joiner `equal(room)` le accoppiasse
   null-null, H2 sparerebbe o NPE-rebbe su `a.room.id` (`BCP:374`). Non lo fa: A regge a scala di
   produzione.
3. **Fixture room-less nei test.** Il commento S16 (`SSCP:579`) cita "the room-less golden guard fixture":
   `GoldenSolutionRegressionTest` gira sull'orario golden senza room, e il constraint set lo tollera a scala.

**Differenza Alt-B vs oggi.** Oggi l'insieme room-null post-costruzione e' "i pochi non collocabili";
Alt-B lo porta a "tutti i non-pinnati". A e' una proprieta' **per-entita'**, non una soglia di conteggio:
se regge per i pochi, regge per tutti.

**CONSEGUENZA VINCOLANTE (verifica Fedora).** Il checkpoint fine-costruzione deve ASSERIRE esplicitamente
`H2 == 0` E `H16b == 0` sulla griglia tutta-room-null (non solo stamparli), cosicche' se un upgrade di
Timefold cambiasse la semantica di `forEach` / `allowsUnassigned`, il guard-test lo urla immediatamente.
Insieme si asserisce, a livello schedule, che NESSUNA non-pinnata porta room
(`nonPinnedRoomSet == 0`) e che il canary `roomNullCount == non-pinned-placed` (phase
counter che vive dentro `assignVariables`, il commit point room=null). Il log di fine
CH non stampa piu' `roomsAssigned` (contatore morto rimosso in /simplify: l'invariante
"0 room assegnate in costruzione" e' coperta a livello schedule, non da un contatore
sempre-zero).
Se A cedesse (H2 o H16b != 0, o NPE), scatta il fallback sezione 5 (non e' un blocker di design).

---

## 5. Guardia fallback (opzionale, solo se il checkpoint la richiede)

**Un solo sito a rischio se A cedesse: H2** (`BCP:364-378`), per via del `a.room.id` in giustificazione (374).
H16b e' gia' triplemente blindato (FUP + 2 filtri). Se il checkpoint mostrasse H2 sensibile a room-null,
la guardia minima (pattern identico a H16b:636) sarebbe:

```java
return constraintFactory.forEach(Lesson.class)
        .join(Lesson.class,
                Joiners.equal(l -> l.room),
                Joiners.equal(LESSON_DATE),
                Joiners.lessThan(l -> l.id))
        .filter((a, b) -> a.room != null && b.room != null)   // guardia fallback
        .filter((a, b) -> !a.getStudentGroup().equals(b.getStudentGroup()))
        ...
```

**Raccomandazione: NON aggiungerla preventivamente** (principio smallest-change; l'ancoraggio empirico sez. 4
dice che e' superflua). Aggiungerla solo se il checkpoint la richiede. Documentata qui per tracciabilita'.

---

## 6. Superficie room della fase di costruzione (nota per step b)

`DayClusteredConstructionPhase` ha 4 punti di contatto con room che l'implementazione Alt-B (step b) dovra' indirizzare:

| Riga | Cosa | Impatto Alt-B |
|------|------|---------------|
| 202-207 `assignVariables(l, ts, room)` | assegna room solo se `room != null` | passare `null` (o non chiamare `findAvailableRoom`) |
| 367-404 `canPlaceLesson` | **gates** il placement timeslot sulla disponibilita' room (H2, 387-401) | **DECISIONE Fedora (verifica census): RIMUOVERE il gate sotto Alt-B.** L'intero punto dell'alternativa e' disaccoppiare timeslot da room; un gate room in costruzione e' esattamente l'accoppiamento che stiamo togliendo; B-ROOM lavora sulla griglia completa. Contrappeso nel checkpoint: confrontare boxed/H0 fine-costruzione vs baseline (il gate oggi puo' mascherare o causare boxed) + residuo hard post-B-ROOM nell'harness staged breve. |
| 438-443 `trackAssignment` | traccia `roomOccupiedTimeslots` | morto sotto Alt-B (room=null) |
| 451+ `findAvailableRoom` | ritorna room o null | non chiamato sotto Alt-B |
| 283 compaction | gia' null-a le room | coerente con Alt-B (precedente esistente) |

**Nota.** Decisione presa in verifica Fedora: il gate room in `canPlaceLesson` (387-401) e' RIMOSSO sotto
Alt-B. Se dall'harness emergesse room-infeasibilita' sistematica (residuo hard post-B-ROOM), se ne riparla
con evidenza.

---

## 7. Conclusioni

- **P1 (room-based silenti): confermata.** 7 vincoli room-touching, tutti con guardia `room != null`;
  H2/H16b anche via `forEach`/`forEachUniquePair` initialized-only.
- **P2 (timeslot continuano): confermata.** 51 vincoli su `forEachIncludingUnassigned`/helper/`precompute`.
  Nessuna regressione TASK-377: l'unico `forEach(Lesson)` (H2) e l'unico `forEachUniquePair(Lesson)` (H16b)
  sono entrambi room-based, esattamente i vincoli che Alt-B VUOLE blindare.
- **H-D1: fantasma**, nulla da blindare.
- **Join secondari: nessuno introduce dipendenza room** oltre ai 7 (enumerati da `grep room`).
- **Blocker per procedere con (b): NESSUNO.**
- **Checkpoint fine-costruzione obbligatorio** (sezioni 4 e 6) con ASSERZIONI esplicite: `H2 == 0`,
  `H16b == 0`, `nonPinnedRoomSet == 0` (schedule), `roomNullCount == N-non-pinned-placed`
  (canary in assignVariables), e metriche timeslot
  (boxed/ricorrenza/day-start) INVARIATE vs baseline. L'asserzione H2/H16b==0 protegge da un eventuale
  cambio di semantica `forEach`/`allowsUnassigned` in un upgrade Timefold (A e' empirico, sezione 4).
- **Gate room in canPlaceLesson RIMOSSO** sotto Alt-B (verifica Fedora); contrappeso = confronto boxed/H0
  vs baseline + residuo hard post-B-ROOM.

**Prossimo passo: commit + push di questo census per verifica Fedora. Implementazione (b) solo dopo OK Fedora e solo se il census non rivela blocker (non ne rivela).**
