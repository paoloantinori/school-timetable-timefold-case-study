# TASK-451 - Blocco prima-ora coordinato slot0-1 cross-gruppo (prototipo, flag OFF) - 2026-07-30/31 notte

Mandato (A): generalizza ConstrainedTeacherAllocator al blocco slot0-1 conteso cross-gruppo
(caso guida golden: T08 slot0-1 a G11 ven), flag default-OFF, cura di COSTRUZIONE
misurata al checkpoint (WeeklyTemplateConstructionRealSeedTest export-mode).

## Consegna (3 righe)
- **Ipotesi:** riservare il BLOCCO slot0+slot1 (non solo slot0 come slot0-balancing TASK-430,
  che lasciava il buco@slot1 = "sposta invece di coordinare"), col docente = anchor slot-0
  del golden, chiude il buco@slot1 a fine costruzione senza le regressioni di slot0-balancing.
- **Misura (costruzione, OFF vs ON, stessa popolazione):** hole-after-anchor (buco slot1)
  **53 (2,7%) -> 25 (1,3%)** (-53%); front-gap h9 **28 -> 19**; conflitti teacher/group
  **0/0 -> 0/0**; skipConflict **0 -> 0**; recurrence **76,5% -> 76,1%** (tiene guardia
  ~76-77%); stamp decline 22,1% -> 22,3% (rumore). Anchored@slot0 98,8% -> 99,2%.
  NON tocca front-gap>=2 h10+ (9 -> 9, modo B family, gestito da AnchorRepair: coerente
  con SPIKE 6).
- **Tenere: SI' (promettente al checkpoint), gate = full-solve A/B laptop x2.** Il blocco
  coordinato fa cio' che slot0-balancing non faceva (chiude il buco slot1 dimezzandolo) a
  costo zero (zero conflitti, skipConflict 0, recurrence tieni). NON elimina tutto (25
  hole residui: G08 7, G13 6, G02 3; e i 9 deep h10+ sono modo B). Un checkpoint di
  costruzione e' un TREND non adozione (LS stocastica): il verdetto finale vuole A/B laptop.
  Nessuna adozione: flag `planner.construction.first-hour-slot.enabled` default OFF.

## Scelte di design (documentate, la strada piu' ragionevole)
1. **Meccanismo nel construction builder, non pre-assignment.** WeeklyTemplateConstruction
   RealSeedTest (l'harness del mandato) gira con pre-assignment OFF (CONSTRAINED_TEACHER_FLAG
   =false, "pin-OFF population"): per essere misurabile li', la cura agisce nel builder
   (WeeklyTemplateBuilder), sibling di reserveSlot0Anchors (TASK-430). E' il contrasto
   diretto col verdetto 30c ("slot0-balancing sposta invece di coordinare: qui si coordina
   PRIMA").
2. **Generalizza reserveSlot0Anchors (1h greedy) -> reserveFirstHourBlock (2h golden-matched).**
   (a) riserva slot0+slot1 (TemplateCell(course,0,2), lo stamp piazza slots 0-1 per ogni data
   del weekday); (b) docente = anchor slot-0 modale golden per (gruppo, weekday) via
   ConstrainedTeacherAllocator.loadGoldenSlot0Anchors (la generalizzazione dell'allocator);
   (c) matching cross-gruppo hardest-first con preferenza golden (le live map assicurano
   nessun docente double-booked). Commit nelle live occupancy map + weeklyTargets -= 2.
3. **Scope = replica la mappa golden dove stabile.** Non si esigono 70/70 celle (il golden
   stesso non le ha, spike1): si riserva il blocco solo dove (gruppo, weekday) ha finestra
   slot0-1 aperta, target>=2h, docente libero entrambi gli slot, H7/H11/quota consentono +2.
4. **Nessuna adozione.** Flag default OFF, opt-in. Banner in ScheduleService.refreshSolverStatics
   (`first-hour-slot=%b`). Misurato, non shipped.

## Files
- `service/ConstrainedTeacherAllocator.java`: +`loadGoldenSlot0Anchors()` (puro, (group,weekday)
  -> docente modale slot0 dal golden; generalizza l'allocator).
- `solver/WeeklyTemplateBuilder.java`: +`reserveFirstHourBlock()` + helper `teacherIdOf`/
  `teacherFreeBothSlots`/`wantMatches`, wiring flag `planner.construction.first-hour-slot.enabled`,
  import ConstrainedTeacherAllocator.
- `service/ScheduleService.java`: banner +`first-hour-slot=%b`.
- `bootstrap/WeeklyTemplateConstructionRealSeedTest.java`: +`firstHourSlotOn_measuresLate
  StartAtConstructionEnd` (mirror di slot0BalancingOn, stampa le 4 metriche + dump
  construction-lessons-firsthouron.txt).

## Debito golden-runtime (TASK-452, censito NON nascosto)
`ConstrainedTeacherAllocator.loadGoldenSlot0Anchors()` e' una **NUOVA lettura runtime di
`seed/lessons.csv`** (il golden = una soluzione) sul percorso di **costruzione**, quindi
potenzialmente di produzione quando il flag e' ON. Estrae una pratica della scuola NON
deducibile dagli input: il docente slot-0 modale per (gruppo, weekday). Questo e' il caso 2
della direttiva permanente "la soluzione gira sul SOLO INPUT, il golden e' temporaneo"
(maintainer 2026-07-30): cio' che il golden dice e non e' deducibile va CHIESTO e diventare input
dichiarato (workbook TASK-445), non assorbito a runtime. Il flag default-OFF NON esime: anche
il `MonoRoomTagAllocator` del TASK-449 e' flag-OFF/non adottato ed E' censito in TASK-452.
Aggiunta al ledger TASK-452 come punto #6 (con #1 ConstrainedTeacherAllocator DEFAULT ON e
#2 MonoRoomTagAllocator). Finestra di verifica (l'anchor map coincide con input esplicito?)
aperta finche' il golden esiste. Sollevato dal /code-review high (angle conventions).

## Misura dettagliata (per-gruppo hole-after-anchor, flag ON)
G01 0, G02 3, G03 0, G04 2, G05 0, G06 0, G07 0, G08 7, G09 3,
G10 0, G11 2, G12 2, G13 6, G14 3. Totale 25 (OFF era 53). Residuo concentrato
su G08 / G13 (gruppi con ancoraggio golden parziale, giorni lab/stage/split).

## Gate / prossimo passo (costo)
- **A/B full-solve laptop x2** (flag OFF vs ON, ciclo staged completo): il checkpoint di
  costruzione mostra il trend (-53% hole slot1), ma la LS e' stocastica e i 25 hole residui
  + l'interazione con AnchorRepair/HoleRepair si misurano solo sul solve intero. Solo quello
  decide l'adozione.
- **Domanda aperta (scritta, non blocca):** il front-gap>=2 h10+ (modo B, 9 giorni, G04 ven)
  NON si muove (come previsto: e' family distinto). Se l'A/B laptop mostrasse che i 25 hole
  residui si convertono in late-start al finale, serve combinare first-hour-block + la cura
  modo B (slot0 construction, non slot1 fill).

## A/B laptop run1 (ON) 2026-07-31 00:02 (flag ON, profilo production fresco, codice a92d4b9e)

Braccio OFF = 30d (stessi default, flag OFF inerte). Run ON: java -jar
`-Dplanner.construction.first-hour-slot.enabled=true`, ciclo staged completo (~45min, 23:17->00:02).
Capture: `task451-results/ab-firsthour-on-run1/`.

**Verdetto (3 righe, numeri dai raw):**
1. **(A) ON run1 batte 30d OFF su quasi tutto:** hard **-173->-108**, H0 boxed **3->0**, H29 room-null
   **167->90**, ricorrenza UNPINNED **74.3->75.6%** (totale 75.6->76.6%), full-day 89.8->89.7% (rumore),
   e **AZZERA il difetto primario day-start: G11 ven 16 late days -> 2** (G09 1 + G14 1,
   casi isolati), anchored rate 99.3->**99.9%**, istogramma prima ora {8:2178, 9:29}->{8:2192, 9:15}.
2. **UNICA regressione: 18 buchi H17 HARD mid-day (0->18).** (A) chiude i late-start M18 (medium,
   16->2) MA apre 18 holes H17 (hard): tier-regression locale (medium->hard). Decomposizione hard ON:
   -108 = H29 room-null -90 (90) + H17 holes -18 (18) + H0 boxed 0. Il netto hard resta molto
   migliore (-108 vs -173) perche' boxed e room-null migliorano piu' di quanto peggiorino gli holes.
3. **Numeri reggono (con caveat holes).** Gate = 2° run ON di conferma (stabilita' dei 18 holes:
   fissi o varianza stocastica?). Verdetto adozione (maintainer): se gli holes sono stabili al 2° run,
   (A) e' adottabile. Elimina il difetto cronico G11-ven al costo di 18 mid-day gap (meno visibili
   di un ritardo alla prima ora, ma sono tier HARD). Se gli holes sono varianza, (A) e' netto-positivo pulito.

Hard non = 0 in nessun braccio (30d -173 boxed+room-null, ON -108 room-null+holes): nessuno e'
feasible, ma ON e' piu' vicino a 0. Il room-null resta il difetto dominante (TASK-455/449 capacity).

## A/B laptop run2 (ON) - conferma stabilita' 2026-07-31 00:42

Run2 (profilo fresco, stesso setup, ciclo staged ~33min 00:08->00:41, zero C-DEMOTION iter).
Capture: `task451-results/ab-firsthour-on-run2/`.

**Run2 == run1 (n=2, i numeri SONO STABILI non varianza):**
- hard **-109** (run1 -108, delta 1 = rumore); breakdown **H29 room-null -91 (91) + H17 holes -18 (18) + H0 boxed 0** (run1: -90/90, -18/18, 0).
- **BUCO-INTERNO 18** (identico run1). day-start late **2** (identico), anchored 99.9%, scarti>=3 **none**.
- istogramma {8:2193, 9:15} (run1 {8:2192, 9:15}). full-day 89.5% (rumore).

**Verdetto finale (A), 3 righe (n=2 stabile):**
1. **(A) e' netto-positivo STABILE: hard -173->-108/-109 (-65), boxed 3->0, room-null 167->90/91,
   ricorrenza UNP 74.3->75.6%, e AZZERA il difetto cronico day-start G11 ven (16 late M18 -> 2).**
2. **CONTRO: 18 buchi H17 HARD mid-day (0->18), STRUTTURALI (deterministici, non varianza LS).** (A)
   riserva il blocco slot0-1 -> chiude i late-start MEDIUM ma il schedule risultante ha 18 gap mid-day
   che la LS non chiude (H17 hard). Trade-off stabile: 16 late-start MEDIUM (molto visibili: classe alle 9)
   vs 18 mid-day gap HARD (meno visibili ma tier hard).
3. **ADOZIONE = decisione di modello (maintainer).** Consiglierei ADOZIONE: netto hard molto migliore,
   difetto cronico genitore-visibile sparisce, 18 gap mid-day assorbibili (18 celle su ~10.542). MA il
   trade-off medium->hard e' reale: se la scuola preferisce zero gap mid-day al costo di 16 ritardi
   prima ora, (A) si butta. Nessuna adozione automatica: flag resta OFF, maintainer decide sul trade-off.

## DoD completata (2026-07-31 notte, contesto fresco)
/simplify + /code-review high multi-agente (3 finder: correttezza / cleanup
reuse+simplify+efficiency / convenzioni+altitude) sul diff 92df21e7 + verify.
**test.sh: BUILD SUCCESS, 886 test, 0 failure/0 error, 15 skip.** Verdetti:

**APPLICATI (3 fix codice + 1 debito):**
- **Corr1 (determinismo, sort riga 583):** il tiebreaker usava `course.id` raw,
  riintroducendo il bug di varianza run-to-run (75-81%) che la L2 review della classe
  (righe 236-247) aveva fissato con chiavi semantiche. Fix: tiebreaker su
  `semanticCourseKey(c)`. Load-bearing per l'A/B x2 (determinismo tra run).
- **Corr2 (sentinella -1, riga 531):** `dailyTargetHours(day) < 2` skip anche il
  sentinella -1 ("nessun fact"), mentre il sibling (408) gating su `==0` e placeChunkOnce
  (721) guarda `>=0`. -1 va trattato come "sconosciuto, procedi col cap finestra". Fix:
  `dayTarget >= 0 && dayTarget < 2` (hoistato, riutilizzato al cap riga 553).
- **Clean1 (DRY teacherIdOf):** migrate 4 copie inline (build 259, reserveSlot0Anchors
  409/452/465) al helper `teacherIdOf`. Byte-identico, single-sources la convenzione
  negative-id (il linchpin di correttezza: slotKeyId e' identita' -> nessun double-book).
- **C1 (debito golden):** loadGoldenSlot0Anchors censito in TASK-452 #6 (sopra).

**SCARTATI con motivazione:**
- **C2/Alt2/Clean2 (parameterizza reserveSlot0Anchors+reserveFirstHourBlock in helper
  unico):** SCARTATO. Prototipo flag-OFF non adottato; il diff specchia il sibling
  (commit dichiarato). Refactor di ~90 righe su codice certificato (TASK-430 + il nuovo)
  per un non-adopted e' rischio-non-adeguato; la generalizzazione vera si fa al gate
  adozione. Nota interazione: con entrambi i flag ON slot0-balancing ruba slot0 e
  first-hour-block va in no-op (mai in pratica: slot0-balancing REFUTED, entrambi OFF).
- **Clean3 (side-effect computeIfAbsent nel gather):** SCARTATO. Inerte (empty container
  == absent), pattern preesistente nel sibling; cambiarlo solo nel nuovo metodo li
  farebbe DIVERGERE (peggio).
- **Clean4 (doppio parse golden):** SCARTATO. Blast limitato (flag OFF); il raw-row load
  e' shareabile ma gli assi di grouping differiscono. Giusto censito come debito TASK-452
  (la doppia lettura e' la stessa radice del #6).
- **Clean5 (countInDay O(total) vs O(day)):** SCARTATO. Pattern preesistente
  (reserveSlot0Anchors, tryPlaceChunk); la costruzione e' veloce (49s tutto il test).
  Ottimizzazione differibile.

## Riferimenti
- TASK-450 (catena ritardo prima ora) + TASK-430 slot0-balancing (refuted): QUADRO colonna 1.
- spike1-feasibility-prima-ora (matching 14/14), spike6-modob-30c-phase2 (modo B family).
- localized-testing-guidelines: costruzione = L2 fixture + KPI dump, trend non adozione.
