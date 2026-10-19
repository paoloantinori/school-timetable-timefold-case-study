# TASK-450 prototipo: riserva-ancora nel template settimanale (direzione c) - SPEC

Stato: SPEC DA IMPLEMENTARE (sessione a contesto alto, handoff prima del codice). Mandato maintainer
2026-07-30 notte. Prototipo dietro flag, NON adozione (flag resta OFF, decisione di maintainer).

## ⚠️ TASK-430 GIÀ ESISTE (E FU BOCCIATA) - RICONCILIAZIONE PRIMA DI COSTRUIRE

La mappa Explore (vedi anchor sotto) ha trovato che **la direzione (c) è già implementata come
TASK-430**: `WeeklyTemplateBuilder.reserveSlot0Anchors()` (righe 351-442), gated da
`planner.construction.slot0-balancing.enabled` (riga 272-279), DEFAULT OFF. Riserva l'ancora
(slot 0, il primo slot aperto) per ogni (gruppo, weekday) distributivamente PRIMA del main
placement, poi il main placement contiguo (nextContiguousSlot scansiona da window[0]) riempie il
resto. E' la STESSA IDEA della direzione (c) ("riserva la cella ancora... non lasciarla orfana con
un buco subito sotto").

E CLAUDE.md (riga 370-371) ricorda che **TASK-430 fu BOCCIATA al gate2** ("-hard e -full-day"),
insieme a TASK-432 (profile-following). Quindi costruire un NUOVO flag per la direzione (c)
duplicherebbe una feature già scartata.

DECISIONE DA PRENDERE (scritta nel task 450, non bloccante su maintainer):
- **(A) RIMISURARE TASK-430 con la lente nuova**: il gate2 misurava -hard/-full-day al SOLVE
  INTERO. La lente di stanotte è il **buco-slot-1 a fine COSTRUZIONE** (il link dimostrato). Se
  TASK-430 ON fa sparire il buco-slot-1 a fine costruzione, la "cura" c'è già; resta da capire
  perché al solve intero peggiorava hard/full-day (forse il buco si chiude ma si riapre in LS, o
  la riserva slot0 sposta deficit altrove). Misura GRATUITA una volta passato il flag al test.
- **(B) SE la direzione (c) è DISTINTA (blocco contiguo 2h slot0+slot1, non solo slot0)**: allora
  è un nuovo angolo e vale un flag nuovo `planner.construction.anchor-reservation.enabled`. Ma va
  distinto esplicitamente da 430 (slot0-only).
- **(C) ACCETTARE la bociatura**: se 430 non uccide il buco-slot-1 a fine costruzione, e la
  variante contigua è infattibile (contentione slot1), la direzione (c) muore e 450 chiude come
  il task 457 (firma/documentato, niente fix).

OSTICOLO MISURA: `scripts/core/test.sh` RIFIUTATE i `-D` (case `-*` -> errore). Per passare il
flag al test JVM serve (1) un metodo di test che fa `System.setProperty` (pattern
profileFollowing riga 739-784 del test), o (2) raw mvn (bloccato dall'hook), o (3) enhancement di
test.sh con passthrough -D. Opzione (1) è la piu' pulita (10 righe, pattern esistente).

## MAPPA EXPLORE (anchor file:line)

**WeeklyTemplateBuilder.java** (scelta celle template):
- `tryPlaceChunk()` riga 463-540: decide dove piazzare un corso; `nextContiguousSlot()` riga
  555-569 scansiona da `window[0]` (primo slot aperto) verso l'alto, ritorna il primo libero
  contiguo. Quindi il main placement GIÀ' preferisce il primo slot.
- **TASK-430 gate riga 272-279**: `if (Boolean.getBoolean("planner.construction.slot0-balancing.enabled")) reserveSlot0Anchors(...)`. DEFAULT OFF, byte-identical quando spento (il metodo non fa nulla).
- `reserveSlot0Anchors()` riga 351-442: `anchorSlot = 0`, riserva slot0 per gruppo prima del main
  placement, gruppi piu' difficili-da-ancorare per primi (evita che un docente condiviso venga
  monopolizzato a slot0 dal primo gruppo).
- `GroupWeeklyShape.windowFor(day)` riga 131-135: ritorna `[minSlot, maxSlot]`; `window[0]` è il
  primo slot aperto (da GroupCalendarRule). NON è sempre 0 (G14 Thu-Fri, MODA venerdi corti).

**DayClusteredConstructionPhase.java** (stamp):
- `stampWeeklyTemplate()` riga 1052-1192: build template (riga 1086 via WeeklyTemplatePlanner.build)
  poi stamp su tutte le date (riga 1129).
- contatori decline riga 106-110: `stampPlaced`, `stampSkipConflict` (H1/H3/H7/H11, incrementato
  riga 1167), `stampSkipIllegalOrClosed` (VRP reject), `stampSkipExhausted`, `stampSkipExpectedHoursGoal`.

**Flag pattern**:
- ScheduleService.java riga 1707-1710: banner "Construction/repair flags:" (aggiungere qui il
  nuovo flag per auto-doc).
- HybridPhase1aService.java riga 89: `CONSTRAINED_TEACHER_FLAG` costante; riga 484: `Boolean.getBoolean(FLAG)` gate.
- Convenzione nome: `planner.construction.*.enabled`.

**First-open-slot**: GroupCalendarRule.java riga 44-48 (`minSlot`/`maxSlot`);
SchoolSchedule.getGroupCalendarWindow() riga 665-673 ritorna `[openSlotCount, minOpenSlot, maxOpenSlot]`.

**WeeklyTemplateConstructionRealSeedTest.java**:
- test principale riga 224-293 (@Timeout 400); `buildAndSolveConstruction()` riga 489-543
  (construction-only solve); dump scritto riga 456 -> `writeConstructionLessonsDump()` riga 472-478
  (SnapshotFixture.writePlacedGrid -> target/construction-lessons.txt).
- pattern flag-ON nel test: `profileFollowing_concentratesT01OnMonographicThursdays` riga
  739-784 usa `System.setProperty(...,"true")` nel try, `System.clearProperty` nel finally. IL FLAG
  RAGGIUNGE IL TEST JVM via System.setProperty (lo stesso Boolean.getBoolean legge).

**Byte-identical-when-off**: il gate riga 272-279 (o uno nuovo analogo) corto-circuita a OFF.
Landmine: contenzione docente slot1 (COMPITO 1) puo' far declinare la cella riservata dallo stamp
(stampSkipConflict riga 1167).

## OBIETTIVO
Il costruttore del template settimanale (WeeklyTemplateBuilder) riserva la cella dell'ancora: per
ogni (gruppo, giorno-settimana) riserva un BLOCCO CONTIGUO che parte dal primo slot aperto del
calendario di gruppo. Cosi' lo stamp non lascia "slot 0 pieno + slot 1 vuoto" (il buco interno che
violerebbe H17 e che la prima LS chiude traslando il blocco a destra, causando i late-start di
TASK-450). Misura se questo fa sparire il buco allo slot 1 a fine costruzione.

## LA CATENA DIMOSTRATA (perche' questa cura e non un'altra)
Costruzione -> piazza slot0 ma non slot1 (docente conteso, vedi COMPITO 1 del task 450: tutti i
docenti del giorno presi altrove) -> buco H17 -> P2 (LS Compactness) chiude nel modo piu'
economico, traslando slot0->slot1 -> giornata slitta alle 9. Nessuna fase dopo recupera (P3
repair inerte, P4 ri-ancora solo 4/18 per caso). Quindi la cura non e' alzare M18 (sposta il
difetto) ne' un repair (inseguire): e' NON CREARE IL BUCO. Verificato link-per-link questa notte
(commenti #7/#8 del task 450 + doc task457).

## FLAG
- `Boolean.getBoolean("planner.construction.anchor-reservation.enabled")` (nome da confermare
  con la mappa Explore: seguire la convenzione `planner.construction.*` di slot0-balancing /
  profile-following).
- DEFAULT R15 IDENTICA ai flag di costruzione esistenti (TASK-430/432/435): costante
  stringa, `Boolean.getBoolean` nel punto di costruzione.
- BYTE-IDENTICO a flag spento: il gate corto-circuita al comportamento attuale. (Verifica: il dump
  di fine costruzione con flag OFF e' byte-identico a oggi.)
- Aggiungere il flag al banner "Construction/repair flags:" in ScheduleService (~riga 1707) per
  auto-documentazione (come slot0/profile-following/constrained-teacher).

## PUNTO DI CAMBIO (da riempire con la mappa Explore)
- candidato: WeeklyTemplateBuilder, nel metodo che sceglie le celle (weekday, slot) per gruppo.
  Quando il flag e' ON, per ogni (gruppo, weekday) con celle template, garantire che la prima
  cella sia il primo slot aperto del calendario di gruppo (anchor) E che sia un blocco contiguo
  (slot0+slot1 entrambi template, non slot0 da solo). Se il template attuale ha solo slot0,
  estendere a 2h contigue dall'ancora.
- ANCHOR DA ESPLORARE: WeeklyTemplateBuilder (scelta slot), DayClusteredConstructionPhase (stamp
  + contatore stampSkipConflict ~riga 1166), SchoolSchedule.getGroupCalendarWindow /
  GroupCalendarRule (primo slot aperto per gruppo/weekday).
- LANDMINA NOTA (COMPITO 1): slot1 puo' essere non riempibile per contenzione docente (tutti i
  docenti del giorno occupati altrove). Se riservare l'ancora fa DECLINARE tante celle dallo stamp
  (stampSkipConflict alto), il template diventa infattibile altrove e il prototipo si BUTTA. Il
  % stamp-decline e' uno dei 4 numeri da riportare proprio per questo.

## MISURAZIONE (CHECKPOINT, non solve intero)
Via `./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest` che scrive
`target/construction-lessons.txt` (dump di fine costruzione). I flag -D raggiungono la JVM del
test via surefire (verificare con la mappa). Confrontare flag OFF vs flag ON su 4 numeri:

1. **giornate ancorate al primo slot aperto** (per (gruppo, weekday), prima lezione = primo slot
   aperto del calendario). Obiettivo: aumenta con flag ON.
2. **giornate con buco allo slot 1** (slot0 pieno + slot1 vuoto, popolazione non-G14).
   Obiettivo: SI AZZERA (o crolla) con flag ON. E' il segnale che la LS non ha piu' motivo di
   traslare.
3. **ricorrenza di fine costruzione** (oggi ~76-77%, vedi resemblance-kpi.py sul dump). Guard:
   NON deve crollare (se riservare l'ancora distrugge la ricorrenza, il costo e' troppo alto).
4. **% celle template declinate dallo stamp** (stampSkipConflict / tot celle stampate). Guard:
   se schizza (template infattibile), BUTTARE.

Strumento di misura: gli script 1/2 si calcolano dal dump (group|date|hour|...), 3 via
resemblance-kpi.py, 4 dai contatori stamp del log di costruzione (o da una diagnostica nel test).

## PASS/FAIL
- PASS (prototivo promettente, va a A/B staged): buco-slot1 crolla (ideale a 0), ancorate
  aumentano, ricorrenza tiene (>= ~74%, entro il rumore di ~76-77%), stamp-decline non esplode.
- FAIL (butta, doc il perche'): buco-slot1 non cala (la contenzione docente prevale sul reserved)
  OPPURE ricorrenza crolla OPPURE stamp-decline esplode (template infattibile).

## DOD (sull'incremento di codice)
1. /simplify sul diff.
2. /code-review high: candidati verificati (Opus), tutti i confermati corretti.
3. Verifica finale con EXIT CODE (./scripts/core/test.sh) dall'orchestratore.
4. byte-identico a flag OFF verificato (dump OFF == dump di oggi).
File protetti (DataGenerator) NON toccati.

## STATO IMPLEMENTAZIONE
- [ ] mappa Explore (anchor file:line)
- [ ] spec completato con anchor
- [ ] flag + gate (byte-identico OFF)
- [ ] misura OFF vs ON (4 numeri)
- [ ] DoD
- [ ] commit + push, verdetto PASS/FAIL nel task 450
