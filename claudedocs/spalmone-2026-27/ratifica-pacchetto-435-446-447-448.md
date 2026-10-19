# Ratifica del pacchetto 435 / 446 / M37 / H28 / 447 - CHECKLIST ESEGUIBILE

Quando maintainer dice "si'" al pacchetto adottabile, questo documento e' la lista di azioni
eseguibili (non una narrazione). Obiettivo: la ratifica e' un comando solo, non una caccia
al tesoro. Stato alla certificazione 2026-07-29: **-195 hard** (189 ore H29 room-null + 3
lezioni H0 boxed, peso 2 = 6), KPI tutte tenute, nessuna adozione ancora formalizzata.

## STATO VERIFICATO (cosa e' gia' dentro, cosa no)

| Item | Nel codice? | Flag / peso | Dove |
|------|-------------|-------------|------|
| **TASK-435** ConstrainedTeacherAllocator | si', implementato | flag `planner.pre-assign.constrained-teacher.enabled` **default OFF** | `HybridPhase1aService.java:487` (`if (Boolean.getBoolean(CONSTRAINED_TEACHER_FLAG))`, costante a `:89`) |
| **TASK-446** room-decoupling (ROOM_TIER exclusion) | si', **sempre attivo** | nessun flag: `ROOM_TIER_HARD_FAMILIES = List.of("H2","H-M2","H29")` | `ScheduleService.java:766`, usato in `isRoomTierHardFamily()` `:1405` ed escluso da `timeslotTierHardScore()` `:1383` |
| **M37** room-change | si', **sempre attivo** | `ofMedium(readCadenceKnobProperty("planner.m37.weight", 100))` = medium 100 | `SchoolScheduleConstraintProvider.java:665` |
| **H28** (TASK-448) no teacher return | si', **sempre attivo** | `ONE_HARD` = hard 1 | `SchoolScheduleConstraintProvider.java:697` |
| **H29** (TASK-447) room required | si', **sempre attivo** | `ofHard(1)` = hard 1 (room-tier) | `SchoolScheduleConstraintProvider.java:722` |
| **H0** (TASK-447) unassigned | si', **sempre attivo** | `ofHard(2)` = hard 2 | `BaseConstraintProvider.java:528` |

CONSEGUENZA: H28/H29/H0/M37/ROOM_TIER sono gia' nel codice e il cert li ha usati (il -195 lo
dimostra). L'unica cosa con un flag e' **435** (default OFF; il cert lo accende da riga di
comando). Quindi la ratifica e' per lo piu' FORMALIZZAZIONE (chiudere task, aggiornare docs,
re-baseline) + una decisione su 435.

## A. CODE-CONFIG - l'unico cambio reale: 435

435 e' l'unico flag del pacchetto. Il cert lo passa gia' nello script
`scripts/benchmark/run-447-final-cert.sh:22`
(`export PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.enabled=true"`)
sulla riga `java -jar` (NON MAVEN_OPTS, vedi CLAUDE.md). Due opzioni,sceglierne UNA:

- **(consigliata) tieni il flag nello script cert**: il Phase-0 constrained-teacher e' lento
  (>900s osservati, `run-447-final-cert.sh:55`); tenere il default OFF evita che ogni solve
  dev/debug lo paghi. La ratifica = formalizzare `run-447-final-cert.sh` come procedura cert
  standard (sez. E). Nessun cambio al codice.
- **(alternativa) flip default a ON**: in `HybridPhase1aService.java:487` sostituisci
  `if (Boolean.getBoolean(CONSTRAINED_TEACHER_FLAG))` con un default-true (es.
  `if (!Boolean.getBoolean(CONSTRAINED_TEACHER_FLAG + ".disabled"))`). Pegno: ogni solve paga
  il Phase-0 lento.

FLAG DEL PACCHETTO CHE RESTANO OFF (misurati e bocciati, NON toccare in ratifica):
`planner.construction.slot0-balancing.enabled` (`WeeklyTemplateBuilder.java:277`,
bocciato gate2), `planner.construction.profile-following.enabled`
(`DayClusteredConstructionPhase.java:1125`, bocciato gate2). Documentarli come "misurato,
non adottato" perche' non vengano ri-accesi per sbaglio.

## B. BACKLOG TASKS DA CHIUDERE (con nota)

| Task | Stato ora | Azione | Nota di chiusura |
|------|-----------|--------|------------------|
| TASK-435 | Done | verificare DoD spuntata | "adottato via run-447-final-cert.sh (flag nello script); codice completo" |
| TASK-446 | To Do | chiudere | "room-decoupling adottato: ROOM_TIER_HARD_FAMILIES (H2/H-M2/H29) escluso da timeslotTierHardScore; il task-proposta timeslot-mode completa resta come follow-up, la parte load-bearing e' inside" |
| TASK-447 | To Do | chiudere | "adottato: H29 hard 1 + H0 hard 2 (room-null trap chiusa); BREAKS hard==boxed annotato in CLAUDE.md:392" |
| TASK-448 | To Do | chiudere | "adottato: H28 hard 1 (golden-first guard a delta zero, costo 0)" |

## C. CLAUDE.md / DOCS DA TOCCARE

1. `CLAUDE.md:392` (voce H29): la nota "**BREAKS the historical invariant `hard == boxed-count`...**
   **accepted by maintainer, see VERDICT**" -> riscrivi "accepted by maintainer" in "**ADOPTED** (ratifica
   <data>)". Il testo tecnico resta (documenta la conseguenza).
2. Voci H28 (`CLAUDE.md:391`), M37 (`:421`), H0 (`:366`): confermare che non recano piu' framing
   "misurato/To Do" dove implichi non-ancora; sono vincoli correnti.
3. Nuova voce di campaign-status: aggiungere `campaign-status-<data>-ratifica.md` (o appendere
   al `campaign-status-2026-07-29-risveglio.md`) con: "pacchetto ratificato, cert di riferimento
   = run-447-final-cert.sh, -195 hard e' il nuovo baseline".
4. Sezione "Modalita' di solve della campagna: STAGED" in CLAUDE.md: aggiungere nota che il cert
   di riferimento ora include il Phase-0 constrained-teacher (flag nello script).

## D. RIFERIMENTI METRICI DA AGGIORNARE (audit)

L'invariante storica **`hard == boxed-count` NON vale piu'**: ora `hard == boxed*2 + room-null*1`.
Audit da fare (grep `hard ==` / `boxed` / `getFeasibility` su claudedocs/ + codice):
- `ScheduleService.getFeasibility()` (legge `hardScore==0`): uno schedule lab-heavy con room-null
  ora risulta INFEASIBLE (non piu' feasible a parita' di boxed=0). Documentare il nuovo contrato.
- Ogni KPI/report che assume "hard == numero lezioni non piazzate": aggiornare alla formula nuova.
- Il "-195 hard" cert e' il nuovo riferimento hard; i confronti A/B future vanno letti con la
  formula `boxed*2 + room-null*1` (room-null e' il 189, boxed e' 3).
- claudedocs/spalmone-2026-27/task447-results/cert/VERDICT.md: confermare che cita -195 hard con
  la scomposizione 189+3 (gia' fatto in sede di cert).

## E. RE-BASELINE (il "comando solo")

La ratifica e' eseguibile come:
```
./scripts/benchmark/run-447-final-cert.sh   # gia' bakea 435 via PLANNER_JAVA_OPTS
./scripts/benchmark/capture-run.sh <outdir> # KPI canonici post-solve
```
dopo A-D. L'output di capture-run.sh e' il nuovo riferimento campagna (sostituisce il -195
cert come baseline adottato). Se al punto A si e' scelto il flip default-ON, il cert puo'
partire anche senza lo script, ma lo script resta la procedura documentata.

## NON FA PARTE DEL PACCHETTO (per chiarezza, non toccare in ratifica)

- TASK-449 / SPIKE 2 MonoRoomTagAllocator (flag `planner.pre-assign.mono-room-tag.enabled`,
  default OFF): prototipo valido ma il collo e' di capacita' (1 stanza acconciatura, 5 gruppi),
  non di search. Adozione separata, dopo A/B staged che misuri la reale riduzione room-null.
- Le 189h room-null residue: oggetto della TABELLA DI SENSIBILITA' alle risposte della scuola
  (documento separato), non chiudibili solo ratificando il pacchetto.
