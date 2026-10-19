# TASK-456 lab-fallback: MISURA (in-harness, 2026-07-29)

Lezione 21 (maintainer 29/07): "i laboratori POSSONO ospitare anche lezioni normali, dando priorita'
alle attivita' di lab vere". Il motore lo vietava nel DOMINIO DEI VALORI (`Lesson.getAvailableRooms`
non-lab: solo stanze senza tag), non in un vincolo. TASK-456 apre quel dominio dietro flag
`planner.room.lab-fallback.enabled` (default OFF) + aggiunge M-LF (medium, knob
`planner.room.lab-fallback.weight`) che fa da "shelter di ultimo resort". MISURA PRIMA, nessuna
adozione: valore del peso e adozione restano a maintainer.

Tutto in-harness (`RoomPhaseSnapshotTest` export-mode sul cert `task447-results/cert/export.json.gz`,
all-groups, phase-5 LS 90s, override PRE-solve). Snapshot = trend su UNA traiettoria (il cert),
non adozione. File protetti (DataGenerator/seed CSV) intoccati: la regola e' in `Lesson.java` +
constraint provider.

## COME RIPRODURRE

```
PLANNER_TEST_ROOM_PHASE=1 \
PLANNER_TEST_ROOM_PHASE_EXPORT=claudedocs/spalmone-2026-27/task447-results/cert/export.json.gz \
PLANNER_TEST_ROOM_PHASE_GROUPS="" PLANNER_TEST_ROOM_PHASE_BUDGET=90 \
[PLANNER_TEST_ROOM_LAB_FALLBACK=1 PLANNER_TEST_ROOM_LAB_FALLBACK_WEIGHT=N] \
[PLANNER_TEST_ROOM_EXCLUDE="R18"] \
./scripts/core/test.sh -c RoomPhaseSnapshotTest
```

Cert: `task447-results/cert/export.json.gz` (29/07 09:32). REAL room-null baseline = 189
(7 non-lab + 182 lab), hard -195 = 189 + 3 boxed x2. Identico al doc `sensibilita-risposte-scuola-189h.md`.

## Scenario A: cert raw (baseline 189, 7 non-lab + 182 lab)

| config | non-lab rn (after) | LAB rn (after) | non-lab-in-lab (after) | hard |
|---|---|---|---|---|
| flag OFF (baseline) | 7 | 182 | 0 | -195 |
| flag ON, w=1 | **0** | 182 | 10 | -188 |
| flag ON, w=100 | **0** | 182 | 8 | -188 |
| flag ON, w=1000 | **0** | 182 | 7 | -188 |

- (a) room-null chiuse: 7/7 non-lab a OGNI peso (hard -195 -> -188, esattamente -7). La phase-5
  LS shelterizza tutte le room-null non-lab native in lab liberi.
- (c) LAB rn INVARIATO a 182 a ogni peso, anche w=1: i lab veri non vengono mai rubati. La
  priorita' ai lab veri regge a qualunque peso (H-M2 hard + il discouragement medium).
- (b) non-lab-in-lab: il peso controlla gli "extra" oltre lo shelter necessario. w=1 -> 10,
  w=100 -> 8, w=1000 -> 7 (= esattamente i 7 shelterizzati, nessun extra). Peso piu' alto =
  meno ordinarie parcheggiate nei lab. w=1000 e' il piu' "tirato".

## Scenario B: escludi R18 (204 lezioni displaced -> 211 non-lab rn)

La scuola vuole fuori la R18 (aula cap-0 fittizia). Senza lab-fallback era
catastrofico (la "189 -> 370" storica).

| config | non-lab rn (after) | LAB rn (after) | non-lab-in-lab (after) | hard |
|---|---|---|---|---|
| exclude POLIVALENTE, flag OFF | 75 | 182 | 0 | -263 |
| exclude POLIVALENTE, flag ON w=100 | **0** | 182 | 85 | -188 |

- Senza lab-fallback: escludere POLIVALENTE lascia 75 non-lab rn (le aule sature non reggono
  l'overflow). hard -263.
- Con lab-fallback ON: TUTTE le 211 displaced si chiudono (85 nei lab + il resto in aula), LAB rn
  invariato a 182, hard -188. Il lab-fallback rende vitabile l'esclusione di R18
  (la richiesta della scuola prima bloccata).

## LETTURA

1. Il lab-fallback FUNZIONA come disegnato (a + c PASS): chiude le room-null non-lab e non ruba
   mai stanze ai lab veri, a qualunque peso. La priorita' ai lab veri e' strutturale (H-M2 hard) e
   il peso M-LF la rinforza.
2. Sul cert raw la leva e' piccola (+7): il residual room-null e' dominato da 182 LAB genuini
   (Lavaggio/acconciatura, capacity-bound: i lab specifici sono saturi). Il lab-fallback NON li
   tocca (servono piu' stanze-lab o riclassificazione, vedi le 82 residue del doc sensibilita').
   Apre invece le 7 non-lab native.
3. La leva GROSSA e' l'esclusione POLIVALENTE (Scenario B): lab-fallback ON la rende fattibile
   (0 rn residui, lab intoccati). E' la risposta operativa alla richiesta della scuola. Costo:
   ~85 lezioni ordinarie nei lab (a orari in cui quei lab sono liberi, nessuno spiazzamento).
   Trade-off operativo (una lezione di matematica nel laboratorio) = decisione di maintainer.
   **VERIFICATO DA FEDORA (risultato piu' forte del previsto): escludere R18 col
   ripiego ATTIVO costa ZERO.** hard -188 sia tenendola (Scenario A w=1000) sia escludendola
   (Scenario B) col ripiego ON; -263 se la escludi SENZA ripiego. La richiesta della scuola
   (togliere l'aula cap-0 fittizia) diventa gratis, ed era il nodo che ieri sembrava insolubile.
4. Limite dell'harness: questo misura solo la phase-5 (room LS) su placement timeslot FISSO. Il
   potenziale COMPLETO del lab-fallback (far piazzare piu' lezioni in costruzione usando i lab
   come overflow) richiede un solve intero, non misurabile qui (gated dall'adozione).
5. Peso: **DECISO 1000** (Fedora per maintainer, 2026-07-29). A parita' sugli altri KPI (0 non-lab rn,
   182 lab rn invariati a ogni peso) 1000 e' il peso che minimizza le ordinarie parcheggiate nei
   lab (7 vs 10 a w=1), esprime "il lab e' l'ultima risorsa" e non ha svantaggi misurati. Il
   default del knob e' ora 1000 nel codice.

## RATIFICA per maintainer (SINGOLO COMANDO)

Le decisioni operative sono PRESE (Fedora per maintainer, 2026-07-29); resta a maintainer solo la
**ratifica formale del default**, che e' un comando unico su due cose che stanno insieme:

1. **Accendere il flag** `planner.room.lab-fallback.enabled` (default OFF -> ON in produzione).
2. **Escludere R18** (la richiesta di referente: togliere l'aula cap-0 fittizia).
   Col ripiego ON l'esclusione costa ZERO (hard -188 = come tenerla, vedi LETTURA #3), quindi
   si fanno insieme.

GIÀ FATTO (non richiede ratifica, in attesa solo del flag): peso M-LF default = 1000 nel codice
(`SchoolScheduleConstraintProvider` + harness + CLAUDE.md). A flag OFF e' ininfluente (M-LF
inerte); quando maintainer accende il flag, il peso e' gia' quello deciso.

FINO ALLA RATIFICA: il flag resta default OFF, produzione byte-identical a pre-TASK-456. Nessun
run live, nessuna adozione.

## NOTE TECNICHE

- S10 co-firing: S10 "Discourage generic lessons in labs" (soft 50, gia' esistente) era INERTE per
  le non-lab (il dominio dei valori lo impediva). Aperto il dominio, S10 torna attivo sulla STESSA
  popolazione di M-LF. I due impilano (S10 soft + M-LF medium) quando il flag e' ON.
- Subtiglia de-tag: l'override `PLANNER_TEST_ROOM_TAG_UNTAG` svuota `requiredRoomTags` ma NON
  `course.isLab`. Quindi una lezione de-taggata resta `isLab()=true` ma si comporta da non-lab
  (dominio valori). M-LF filtra `!isLab()` per cui e' CIECO sulle de-taggate. Per questo la misura
  pulita usa il cert RAW (dove isLab() coincide con requiredRoomTags). Nel caso de-tag, M-LF e il
  count `nonLabInTaggedRoom` sono coerenti tra loro ma sottostimano le de-taggate. Non un bug
  (coerente), ma va tenuto a mente se si misura lab-fallback sopra un de-tag.
- R18 NON e' tagged (cap-0, nel dominio non-lab da sempre): il lab-fallback non cambia
  il suo stato. I 7 lab tagged diventano shelter di ultimo livello (dopo aule reali + POLIVALENTE
  cap-0).
- Byte-identical a flag OFF: M-LF e' inerte (predicato `enabled &&` short-circuit), la cache
  `getAvailableRooms` legge il flag live a cache-build. Produzione e golden regression invariate
  finche' il flag resta OFF.
