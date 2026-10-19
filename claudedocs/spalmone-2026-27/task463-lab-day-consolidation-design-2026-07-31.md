# TASK-463 - Lab-day single-teacher contiguity (repair pass, flag OFF) - 2026-07-31

Mandato (maintainer, sign-off 2026-07-31): cura a valle dei 18 buchi H17 HARD mid-day lasciati
dall'adozione di TASK-451 (first-hour-slot, default ON). Diagnosi spike `8dc0d7f9`: i 18 gap
NON sono H17 sparso, sono UN SOLO pattern (G13 venerdi slot 11, lab meccanica 5h, 18
occorrenze). Il golden e il run 30d (OFF) fanno il blocco compatto mono-docente (T10
8-12); il run ON lo spezza su 2 docenti (T10 + T15) con gap a slot 11. E' artefatto
di ricerca (feasible: golden/30d lo fanno), curabile a valle.

Sign-off maintainer: (1) APPROVATO repair pass (a) come pattern di casa (sibling di
HoleRepair/AnchorRepair), agisce a valle indipendentemente dall'origine dello split, replica
la pratica golden. (2) APPROVATO framing di misura a tre livelli. (3) Cad-breaker: via
primaria consolidazione mono-docente same-day (AC#4); SE la misura mostra celle same-day
infeasible, fallback fill-the-gap-only SOLO per quelle celle come tier separato documentato,
senza rilassare AC#4 globalmente; cross-day upfront NO (escalation documentata, non default).

## CORREZIONE ROOT-CAUSE (verificata sui raw, 2026-07-31, post sign-off)

Lettura dei raw del run1 (`task451-results/ab-firsthour-on-run1/lessons.txt`) che corregge la
premessa meccanica del design iniziale. Il teacher NON e' una planning variable: e' fissato
per `Course` (`Lesson.getTeacher() == course.teacher`). Quindi "consolida il lab-day sul
docente anchor" NON e' una ri-assegnazione same-day. E' una **coordinazione cross-gruppo del
giorno del docente**.

Evidenza (run1, verificata su 2 venerdi + spoglio completo):
- **18 venerdi G13** hanno la shape `[8,9,10,12,13]` occupata con gap@11 (piu' 1 martedi'
  giorno corto, da cui 19 date con slot11 vuoto). Combacia con "18 occorrenze" dello spike.
- **Contrasto strutturale:** G13 lun-gio ha blocco **contiguo** `[8,9,10,11,12]` (o 8-13 gio),
  NESSUN gap. Solo il venerdi si spezza. Lo split non e' una prop. di G13, e' una prop. del
  **venerdi**.
- **Perche' venerdi:** entrambi i docenti lab meccanica sono saturati su ALTRI gruppi a slot
  11. Ven 2026-09-25 (idem 2026-09-18): **T10@11 = G04 (Tecnologia Meccanica)**,
  **T15@11 = G08 (Controllo numerico)**. Slot 11 ha 11 docenti occupati su 11 gruppi;
  per la quota lab G13 (lab_hours=5, aula_hours=0) l'unico docente che puo' coprire quegli
  slot e' T10 o T15, entrambi impegnati con gruppi FC. **Nessun fill semplice possibile.**

Conseguenza: il gap esiste perche' c'e' **scarsita' congiunta cross-gruppo del docente
meccanica il venerdi** (G13 + G04 + G08 si contendono T10/T15) e il run non la
coordina. E' esattamente il "coordinamento cross-gruppo del giorno del docente" della
descrizione TASK-463, scarsita' teacher-centric `(docente, giorno)`.

Sign-off maintainer sulla correzione: opzione (1) repair ridefinito come **COORDINATORE
CROSS-GRUPPO DEL GIORNO-DOCENTE**, post-HoleRepair. La scelta di alto livello (repair pass,
flag-OFF, misurato) REGGE; cambia il meccanismo (same-day -> cross-group). L'opzione vincolo
(2) e' **riabilitata** dalla correzione (il mio rifiuto iniziale poggiava sul modello
sbagliato: LS fallisce perche' NON ha il segnale che il blocco lab mono-docente conta), ma
resta follow-up NON ora. Opzione (3) pinning: NO.

## Scelta di design: repair pass (a), non medium (b), non costruzione (c)

La diagnosi stessa prescrive "un repair (o vincolo medium) che consolidi su un unico docente
in blocco contiguo" e NON elenca la riserva in costruzione, perche' l'autore aveva gia'
concluso che lo split e' artefatto LS che la costruzione non raggiunge.

- **(c) riserva in costruzione/template: SCARTATA.** Lo split lo crea la LS B-TIMESLOT dopo
  la costruzione (TASK-451 riserva slot0-1 correttamente; la LS rompe slot 10-13). Una
  riserva in costruzione viene disfatta dalla LS a meno che i pin non siano promossi
  (pesante, legge il golden). La diagnosi la esclude.
- **(b) vincolo medium: SCARTATA.** Il gap slot 11 e' una violazione H17 HARD. La LS ha gia'
  fallito a chiuderne 18 (strutturali, stabili n=2: -18/-18). Un medium sopra un gap hard che
  la LS ha gia' fallito di chiudere scambia (trade), non fissa, e rischia di aprire le guardie
  stesse del mandato (AC#3: i 16 late-start M18 di G11 ven). Inoltre e' ridondante con M31
  (lab-day single contiguous run, medium) che non ha impedito lo split.
- **(a) repair pass deterministico: SCELTA.** Porta il guard `assertImproveOrNoop` PER
  COSTRUZIONE (mirror di `runHoleRepair`, `ScheduleService.java:1302-1312`), quindi non puo'
  peggiorare il timeslot-tier hard score strutturalmente. E' il criterio del mandato ("chiude
  i 18 buchi SENZA aprire altro") soddisfatto dal meccanismo, non dalla speranza. Targettizza
  la radice nell'unico punto dove l'artefatto esiste (post-LS, pre-B-ROOM). Il docente di
  consolidamento e' il plurality holder da `incarichi.csv` (input), quindi nessuna lettura
  del golden.

## Meccanismo (coordinatore cross-gruppo del giorno-docente, corretto post root-cause)

**Classe** `LabDayConsolidation` (`solver/`, `public final class`, ctor privato, `static
Result repair(SchoolSchedule, BiConsumer<Lesson,Timeslot>, Consumer<Lesson>)`), stessa forma
di HoleRepair/AnchorRepair/BoxRepair/SurplusRebalance. Cablata nel ciclo staged in
`ScheduleService.java:903` (tra `runHoleRepair` e `runAnchorRepair`) tramite un nuovo
`runLabDayConsolidation(problem)` che copia il runner `runHoleRepair`: il guard
`assertImproveOrNoop` (righe 1366-1374) arriva gratis.

**Scope generico (scarsita' congiunta della spec, non mono-caso G13):** il meccanismo e'
scritto su `(tag lab, docente anchor, giorno)`, non hardcoded su G13. L'istanza G13-venerdi
e' il driver e la misura, ma il coordinatore e' l'espressione repair-time della scarsita'
teacher-centric `(docente, giorno)`, sibling teacher-della `M-TC`/`TASK-449` (che e' room-tag).
Puo' servire su altri lab dove lo stesso docente-anchor e' conteso cross-gruppo.

**Algoritmo (per (gruppo lab, data) con un gap H17 penalizzato nel lab-day):**
1. **Trigger:** un giorno con >=N ore lab di tag T (soglia ~4-5) E un gap H17 penalizzato nel
   lab-day (la shape G13-venerdi `[8,9,10,12,13]` gap@11).
2. **Anchor teacher:** il docente plurality del tag T per quel gruppo, dalle ore di incarichi
   (T10 110h >> T15 30h per LM G13). Derivato da input, NESSUNA lettura golden.
3. **Diagnosi del gap:** per ogni slot di gap, identifica CHI blocca il docente anchor li':
   la lezione di un ALTRO gruppo che occupa il docente anchor a quello slot (es. G04
   Tecnologia Meccanica@11 occupa T10). Quella e' la **lezione donatrice** da rilocalizzare.
4. **Rilocalizzazione cross-gruppo della donatrice (con guardia anti-shell-game, v. sotto):**
   sposta la donatrice a uno slot alternativo dove il gruppo donatore ha capacita', il docente
   e' libero, e 0-hard. Ogni candidato attraversa la catena di gate `BoxRepair.passesPlacementGates`
   (H14 + H11 + H7 + H17-safe arrival), `StudentDayCompaction.isSlotConflictFree`, VRP
   `getAvailableTimeslots().contains(target)`, e la guardia DONATORE M18/H17.
5. **Fill del gap con il corso anchor:** libero il docente anchor, importa una lezione del
   corso anchor (T10/LM) da un'altra data del gruppo nel gap slot, via
   `HoleRepair.commitCrossDayMove`, con gli stessi gate (incluso H17-safe departure
   all'origine).
6. **Skip-on-gate-fail:** ogni mossa che violerebbe un gate e' saltata; il pass ritorna il
   delta `conflictsAdded` e `assertImproveOrNoop` garantisce no-worsen. Se nessuna donatrice ha
   una rilocalizzazione 0-hard, il pass e' clean no-op e il gap persiste: RIPORTATO onestamente
   (la misura di fattibilita' decide se il meccanismo e' efficace o se serve il follow-up vincolo).

**Guardia anti-shell-game sul donatore (vincolo (ii) di maintainer, come AnchorRepair):** la
rilocalizzazione della donatrice non deve chiudere il gap G13 aprendo un late-start M18 o un
buco H17 nel gruppo donatore (G04). Mirror della `m18Guard`/`keepsDayAnchored` di
AnchorRepair/SurplusRebalance: la donatrice non puo' essere la prima lezione del suo giorno
(ne farebbe partire piu' tardi, M18), e la sua partenza deve essere H17-safe
(`BoxRepair.isDepartureSafe`). La guardia e' CONTEGGIATA (quante donatrici scartate per shell-game)
e ASSERTITA nel `assertImproveOrNoop` (no-worsen timeslot-tier hard sul donatore).

**Perche' H17/M15 sono sicuri:** M15 filtra `!lesson.isLab()`
(`SchoolScheduleConstraintProvider.java:633`), quindi M15 e' inerte sul lab-day LM. H17 e'
l'unico rischio hard-gap, gestito da `isH17Safe`/`isH17SafeDeparture` su entrambi i lati
(arrivo G13 + partenza donatrice + partenza origine import). M-LA e' ottimale quando T10
(assistente `meccanica`) e' il docente unico.

## Disciplinare del coordinatore + opzioni scartate (sign-off maintainer)

**Vincoli disciplinari (maintainer):**
- (i) Ogni mossa 0-hard verificata + improve-or-noop (`assertImproveOrNoop`, come sopra).
- (ii) Guardia anti-shell-game sul donatore M18/H17, CONTEGGIATA e ASSERTITA (v. meccanismo).
- (iii) Scope minimale: i 18 venerdi' del pattern; ma meccanismo generico su (tag, anchor, giorno).
- (iv) Full-solve A/B combinato con flag 464 ON (il gap@11 va misurato nel mondo co-presenza).

**Opzione (2) vincolo-segnale a LS: FOLLOW-UP candidato, NON ora.** Annotata: SE il repair
lascia residuo (la misura di fattibilita' mostra che le mosse legali per la donatrice non
esistono quasi mai), il vincolo che da' a LS il segnale "il blocco lab mono-docente conta" e'
il cambio di modello successivo. E' un cambio di modello, decisione maintainer separata.

**Opzione (3) pinning: NO.** Troppo rigido e mono-caso.

**Stop-condition (maintainer):** se il costo di implementazione del coordinatore esplode (le mosse
legali per la donatrice non esistono quasi mai), fermati alla MISURA DI FATTIBILITA' e riporta
i numeri invece di forzare.

## Framing di misura a tre livelli (e l'inversione della lettura ingenua di AC#2)

**I buchi sono visibili a fine costruzione? NO.** Sono artefatto LS strutturale (TASK-451 A/B
n=2: -18/-18 stabili; la diagnosi attribuisce lo split alla LS che colloca slot 10/12/13 dopo
che la costruzione ha riservato correttamente 8-9). Quindi il "checkpoint di costruzione" di
AC#2 NON li puo' vedere, come avvertito nel mandato e come QUADRO E2/E3 aveva gia' trovato
("no-op al construction checkpoint era corretto ma myopic").

**Inversione della lettura ingenua di AC#2 (documentata come chiesto):** AC#2 dice "misurato
al checkpoint di costruzione prima (ricorrenza, full-day, skipConflict non peggiorano), poi
full-solve A/B x2". La lettura ingenua e' "il checkpoint di costruzione misura la chiusura
dei buchi". E' FALSA per questo task: il repair agisce a valle della costruzione (nel ciclo
staged), quindi il checkpoint di costruzione misura SOLO che il flag e' inerte in
costruzione (nessuna regressione di ricorrenza/full-day/skipConflict), NON la chiusura dei
buchi. La chiusura si misura al boundary del repair (mezzo ciclo) e al full-solve A/B. I tre
livelli, onesti:

1. **Construction checkpoint** (`WeeklyTemplateConstructionRealSeedTest` export-mode, guard
   AC#2): conferma che il flag e' INERTE in costruzione (ricorrenza / full-day / skipConflict
   byte-identical OFF vs ON). E' un check di NESSUNA-regressione-in-costruzione, non di
   chiusura buchi. Documentato come tale.
2. **Repair-boundary measurement** (segnale reale, NUOVO): test real-seed che fa girare il
   ciclo staged e conta i buchi H17 + il pattern G13-venerdi-slot-11 post-HoleRepair (prima
   del nuovo pass) vs dopo. Questo e' il checkpoint onesto della cura, perche' i buchi
   esistono solo a meta' ciclo. Piu' unit test focalizzato: fixture G13-venerdi split
   (T10 8,9,12 + T15 10 Cad / 13 LM, gap @11), assert consolidamento su T10
   contiguo, 0 H17, byte-identical OFF.
3. **Full-solve A/B x2** (gate laptop, decisione AC#2/AC#5): bracci = {451 ON (default) +
   464 ON + 463 OFF} vs {451 ON + 464 ON + 463 ON}, batteria `capture-run.sh` completa.
   Misura il TRAGUARDO HARD 0 COMBINATO e ogni guardia, in particolare AC#3 (nessuna
   reintroduzione dei 16 late-start M18 di G11 ven che TASK-451 aveva chiuso).

## Debito golden-runtime: NESSUNO (differenza chiave vs TASK-451/464)

A differenza di TASK-451 (`loadGoldenSlot0Anchors`) e TASK-464
(`loadGoldenCoPresenceDepth`), entrambe letture runtime del golden censite in TASK-452,
TASK-463 NON legge il golden: il docente anchor e' il plurality holder derivato da
`incarichi.csv` (input). Il golden resta l'ORACOLO sui dati per validare che la scelta
plurality coincide con la pratica (T10), ma non e' letto a runtime. Nessuna voce in
TASK-452.

## Files previsti
- `solver/LabDayConsolidation.java`: nuovo repair pass (sibling di HoleRepair).
- `service/ScheduleService.java`: +`runLabDayConsolidation(problem)` (riga ~903),
  banner in `refreshSolverStatics`, accessor flag.
- `domain/SchoolSchedule.java`: +`isLabDayConsolidationEnabled()` (LIVE
  `planner.construction.lab-day-consolidation.enabled`, default false).
- `solver/LabDayConsolidationTest.java`: unit test fixture G13-venerdi + byte-identical OFF.
- `bootstrap/` o `solver/`: test real-seed boundary (H17 count pre/post repair).

## DoD / gate
Flag default OFF, NESSUNA adozione (AC#5, decisione maintainer). DoD piena per `CLAUDE.md`:
`/simplify` sul diff, `/code-review high` (finding applicati/scartati con motivo),
`./scripts/core/test.sh` con EXIT CODE. Commit+push, QUADRO aggiornato (sezione LA TRIPLETTA:
TASK-463 = scarsita' `(docente, giorno)` teacher-centric, sibling della scarsita' `(risorsa,
giorno)` room-centric di TASK-449/M-TC), verdetti 3 righe dai raw, handoff.

## Riferimenti
- Diagnosi: `spike-h17-holes-g13-friday-2026-07-31.md` (commit `8dc0d7f9`).
- Parent: `task451-first-hour-slot-2026-07-30.md` (A/B laptop n=2, i 18 buchi stabili).
- Mirror pattern flag/checkpoint: `task464-copresenza-ospit-design-2026-07-31.md`.
- Meccanismo staged cycle + repair contract: `ScheduleService.runStagedCycle` (829-937),
  runner `runHoleRepair` (1302-1312), guard `assertImproveOrNoop` (1366-1374).
- QUADRO "LA TRIPLETTA" (meccanismo comune di scarsita' coordinata).
