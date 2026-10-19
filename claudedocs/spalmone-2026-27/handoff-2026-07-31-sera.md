# HANDOFF sera 31/07 (zai, ~15:15) - TASK-466(a) + TASK-465 CHIUSI, TASK-464 design fatto

HEAD `feature/cartesian-block-entity`, pushato fino a `ebaa1b75` (TASK-465). Questo handoff e'
autocontenuto per riprendere dopo /clear. Coda originale 3 task: (1) e (2) CHIUSI, (3) design fatto
+ implementazione speccata per sessione fresca (non iniziata in codice, cambio load-bearing su H2).

## FATTO e pushato
- **TASK-466(a) diff incarichi-vs-golden CHIUSO** (`bd91715b`). Read-only, zero code/DataGenerator.
  FINDING SORPRENDENTE (verificato adversarial-verification, 2 volte indipendente): **diff triplo
  (docente,materia,gruppo) = 0 divergenze reali**; totale gruppo = 0 delta per 13/14 (G08 match
  esatto a 626h escluso pool RA). Unico delta numerico in tutto il dataset: size pool RA G08 40h
  (incarichi) vs 41h (golden), 1h, artefatto contabile noto (micro-corsi). TUTTE le divergenze
  documentate STALE/RISOLTE: T01/T28 244vs192 -> 231=231; G09 666vs640 -> 666=666; G04
  835vs750 risolta TASK-434/457 (835 capacita' griglia, 750 erogazione). Trappole (Fasullo 112h
  sabato, Stage 311h off-grid, Orientamento 5h sabato) tutte closed nel seed, escluse dal flag.
  IMPLICAZIONE: incarichi.csv gia' normalizzato al golden (TASK-371), parte (b) no-op sul lato
  incarichi; tabella discrepanze in `.claude/rules/datagenerator-protection.md` STALE (riportata).
  Artifact: `task466-incarichi-vs-golden-diff-2026-07-31.md` + `referente-notifica-divergenze-input-vs-golden-2026-07-31.md` (bozza, 1 riga da notificare: pool RA 40vs41).
- **TASK-465 audit tagging acconciatura CHIUSO** (`ebaa1b75`). No nuovo lavoro (formalizza lo spike).
  VERDETTO: KEEP tagging lab-obbligatorio delle 4 acc, difetto = cap H2 (1 lezione/slot), no
  de-tagging. Audit-inverse non trova esiliati; 95% ore acc in co-presenza, 100% room-null su celle
  co-presenza. Disegno meccanico/edile scagionati (excess ~0). AC#3 N/A (verdetto KEEP). La domanda
  fisica aula-normale (caveat spike) NON cambia il tagging, e' di TASK-464 -> doc TASK-466c.
- **TASK-464 co-presenza ospite: DESIGN (AC#1) fatto**, implementazione NON iniziata in codice.
  Reason: cambio load-bearing su H2 + loader intricato + DoD piena meritano contesto fresco; flag
  OFF default = produzione sicura anche rinviando. Design + piano file-per-file nei commenti del
  task e in `task464-copresenza-ospite-design-2026-07-31.md`.

## COME RIPRENDE TASK-464 (sessione fresca)
Design chosen: **esenzione pairwise mirata di H2 + nuovo count-cap H-COPRESENCE (hard) per
(tag, weekday, slot), entrambi flag default OFF, depth/coppie SOLO da golden**. DECISIONE maintainer
(questa sessione, anti-attesa): modellazione NON gated a referente. Piano file-per-file nei commenti
TASK-464 #2 (+ righe precise):
1. `SchoolSchedule.java`: `isCoPresenceEnabled()` (template `isTagCapacityEnabled`:372, default FALSE
   LIVE) + static map depth (key `tag|weekday|slotIndex`) + getter/setter.
2. `ConstrainedTeacherAllocator.java`: `loadGoldenCoPresenceDepth(Map<String,String> subjectToTag)`
   mirror `loadGoldenSlot0Anchors`:248 (SeedCsv, skip closed, subject->tag, conta gruppi distinti
   per (tag, weekday, slotIndex); slotIndex da start-hour via `TimeslotSequence.SLOT_START_TIMES`).
3. `ScheduleService.refreshSolverStatics` (~1794, dopo M-TC): subjectToTag derivato dai `Course`
   (subject + requiredRoomTags, autorevole, NO duplicazione SubjectRegistry), loader, pubblica +
   banner.
4. `BaseConstraintProvider.roomConflict` (364-379): aggiungere
   `.filter((a,b) -> !(SchoolSchedule.isCoPresenceEnabled() && isCoPresenceGuestPair(a,b)))`.
   OFF -> byte-identico.
5. `SchoolScheduleConstraintProvider`: nuovo `coPresenceCap` (clone M-TC 1790-1809, penalizeHard
   count > depth). `H-COPRESENCE - ...`.
6. `ScheduleService.ROOM_TIER_HARD_FAMILIES` (768): aggiungere `"H-COPRESENCE"`.
7. Test ConstraintVerifier: H2 flag OFF == byte-identico; flag ON coppia acc in cella depth>=2 non
   penalizza H2, coPresenceCap penalizza oltre depth.
DoD: byte-identico OFF (AC#2) + checkpoint stanze (room-null 92->0 guest, AC#3) + full-solve A/B
n>=2 se checkpoint regge + /simplify + /code-review high + test.sh EXIT 0. Clearance atteso dallo
spike: cap2=48 cap3=2 cap4=23 cap5=19; depth Mon 08-12=2, Thu 08-12=5, Thu13=4, Thu14-15=2.

## Stato ambiente
- HEAD `ebaa1b75` pushato. App: era UP su production col finale E3 relaunch (step1500, config
  reverito a 500 nel codice). Per run nuovi: stop+delete production, restart. laptop post-reboot
  (~13:33): **`podman machine start`** prima di ogni container; `prod-start-and-wait` con
  **timeout 300000** sul Bash tool (5min, il default 120s killa a meta' startup).
- `claudodocs/` (typo dir, non mia) untracked: lasciarla o cancellarla, non committarla.
- solverConfig canonico (tabu7). Default omogenei: first-hour-slot ON + constrained-teacher ON.

## Lezioni di metodo (sessione)
- **"incarichi-vs-golden" pulito per costruzione (TASK-371)**: quando un diff richiesto viene 0,
  prima ipotesi "non ho capito la trasformazione" (CLAUDE.md). Qui incarichi era GIA' corretto al
  golden, le divergenze note vivevano in spalmone.csv/DataGenerator/fogli. Verificato
  adversarialmente prima di asserire 0.
- **Trappole golden = righe closed nel seed**: non serve filtrare il weekday, il flag closed le
  prende tutte (Fasullo/Stage/Orientamento sabato). Caveat CLAUDE.md vale solo per un sabato
  marcato open (qui non si verifica).
- **Design vs implementazione su H2**: il design (parte difficile) consegnato; l'implementazione
  load-bearing rinviata a contesto fresco invece di accanirsi in coda. Allineato a "PLZ Don't Make
  Errors" + flag-OFF = zero rischio produzione nel rinvio.

## Regole standing (invariate)
Nessuna adozione (tutto 2026/27 sperimentale resta non-adozione salvo maintainer). commit+push a ogni
chiusura. verdetti 3 righe dai raw. QUADRO da aggiornare (sezione TASK-466a/465/464-design).
handoff+/clear a ~450k.

## Riferimenti rapidi
- `task466-incarichi-vs-golden-diff-2026-07-31.md` + `referente-notifica-divergenze-input-vs-golden-2026-07-31.md`
- `spike-room-null-copresenza-2026-07-31/VERDICT.md` (TASK-465 + TASK-464 evidence)
- `task464-copresenza-ospite-design-2026-07-31.md` + commenti task TASK-464 #1/#2/#3
- `QUADRO-2026-07-30.md` (SINTESI RISVEGLIO + sezioni T19/E2/E3/SPIKE in colonna 1)
