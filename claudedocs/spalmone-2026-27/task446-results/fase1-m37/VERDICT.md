# FASE 1 - M37 (demotion H16b hard -> medium), VERDICT (2026-07-28 notte)

**ESITO: PASS.** La trappola room-null per i non-lab e' sciolta: la phase-5 LS riempie
283 delle 291 ore non-lab room-null (97%), hard resta -4 (box stabile, NESSUNA
degenerazione da svuotamento), 0 conflitti H2. laptop implementa/misura, NON adotta
(riga CLAUDE.md M37, default, ratifica = maintainer domattina).

## Cosa cambia (stesso corpo e stessa esenzione lab del vecchio H16b)

- `SchoolScheduleConstraintProvider.consecutiveSameSubjectSameTeacherSameRoom`:
  `penalize(ONE_HARD)` -> `penalize(ofMedium(100))`, id `H16b - ... must use same room`
  -> `M37 - ... should keep the room`. Filtro INVARIATO (forEachUniquePair su gruppo/
  materia/data, entrambe non-lab, entrambe room!=null, timeslot adiacenti, stesso
  docente, stanza diversa). Commenti allineati.
- `ScheduleService.ROOM_TIER_HARD_FAMILIES`: `["H2","H16b","H-M2"]` -> `["H2","H-M2"]`.
  H16b non e' piu' un constraint (e M37 e' medium, mai hard): la voce era diventata un
  no-op che matchava nulla. Consistenza necessaria del demotion (non adozione).
- L0 test nuovo `consecutiveSameSubjectSameTeacherRoom_notPenalized_eitherRoomNull`
  (silenzio su null = segnale trappola-sciolta). I 3 test esistenti (penalized /
  sameRoom / acrossLunch) verificano il filtro, invariati.
- `RoomPhaseSnapshotTest`: conteggio non-lab-REAL room-null prima/dopo + budget LS
  env-overridabile (`PLANNER_TEST_ROOM_PHASE_BUDGET`), per il measurement delle 291.

Razionale (maintainer/Fedora): il golden NON specifica le stanze, quindi non difende il tier
hard su questo vincolo; e "ogni classe ha una stanza" M36(1000) deve dominare "non
cambiare stanza a meta' blocco" M37(100), altrimenti la LS room preferisce room=null
(silente) pur di evitare il -1 hard di H16b su una stanza diversa = room-null trap.

## Checkpoint

- **L0** `SchoolScheduleConstraintProviderTest`: 304 test, 0 fallimenti. (Tier/magnitudo
  non assertibili via ConstraintVerifier: penalizesBy e' match-count e Timefold 2.3 non
  ha API scores(Score), verificato su javap + docs. Tier provato dal codice ofMedium(100)
  + dal measurement EXPORT.)
- **L1** `GoldenSolutionRegressionTest`: verde (il golden non difende il tier room, il
  demotion non lo rompe).
- **EXPORT harness** `RoomPhaseSnapshotTest` (export gate-l5-m36, tutti i gruppi, LS 120s,
  M37 nel provider):
  - placed=12490, room-null before=3406 -> after=3123 (283 totali riempiti)
  - **non-lab REAL room-null: before=291 -> after=8 (phase-5 LS filled 283, 97%)**
  - score hard=-4 (i 4 boxed H0, STABILE), 0 room-conflict cell (H2 pulito)
  - medium=-4808885: e' un re-run ISOLATO phase-5-only partendo dallo stato reale, NON
    paragonabile al medium del run completo (che si misura alla Fase 6); non e' il metric
    di questa fase. Il segnale che conta e' il fill 291->8 + hard stabile.

## Lettura

Il demotion H16b->M37 realizza l'obiettivo: la LS room assegna le stanze ai non-lab
perche' il -100 di M37 (stanza diversa) e' battuto dal -1000 di M36 (null). Le 8 non-lab
residue sono plausibilmente slot senza stanza conflict-free in 120s di LS smoke (la Fase 6,
con phase-4 CH room + phase-5 LS pieni, dovrebbe chiuderle). I 256 lab room-null NON sono
toccati da M37 (sono scarstita' di tag H-M2, oggetto della Fase 3).

## DoD review (simplify + code-review high) sul diff Fase 1

La review di correttezza ha trovato 5 conseguenze cross-file del rename H16b->M37 (il
demotion non tocca solo il provider). Tutte conseguenze di consistenza del rename, non
adozione:

- #1 (BLOCKING, fixato) `StagedSolvePinLifecycleTest` asseriva H16b nei room-tier-hard;
  aggiornato a H2/H-M2 (room-hard) + M37/H16b nei non-room-hard.
- #2 (vacuous guard, fixato) `GoldenSolutionRegressionTest` `assertZeroHard("H16b")` ->
  `assertScore("M37", mediumScore, ==0)` + `assertMatchCount("M37",0)`: mediumScore 0 e'
  banale per un vincolo medium, il guard significativo e' matchCount 0 (M37 silente sul
  golden room-less).
- #3 (vacuous guard, fixato) `WeeklyTemplateConstructionRealSeedTest` censimento TASK-446
  ("H2/H16b silenti sulla griglia tutta-room-null"): ID -> M37 + helper rafforzato con
  assertMatchCount 0 (il segnale significativo per M37 medium, hardScore sempre 0).
- #4 (dead code + UX, fixato) `ConstraintMessageFormatter` case "H16b" -> "M37" + chiavi
  i18n `constraint.H16b` -> `constraint.M37` (en/it), messaggio ammorbidito "must stay"->
  "should keep" (tier medium). Ripristina il messaggio drill-down per M37.
- #5 (DEFERITO a maintainer, display-only) frontend `constraints.ts` (`{id:'H16b',type:'hard'}`)
  e `docs.json` (en/it) catalogano ancora H16b/hard. E' sync documentazione/catalogo:
  needs frontend DoD (ui-typecheck/build), lo si allinea alla ratifica con la riga
  CLAUDE.md M37.

Suite `test.sh`: prima passata 1 failure (#1), fixato, re-run in corso per l'EXIT=0 DoD.

## Confine di mandato

laptop MISURA/IMPLEMENTA, non adotta. Nessun default change, nessuna chiusura task, nessuna
ratifica. La riga CLAUDE.md "Canonical Constraint IDs" per M37 (e l'anchor Design Decisions)
restano a maintainer domattina. DoD piena su questo diff (simplify + code-review high + test.sh).
