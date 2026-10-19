# Storico: trade-off VRP memory ed entita' planning multiple (Shadow/Block -> Lesson singola)

**Task:** TASK-235 (documentazione storica, read-only)
**Data:** 2026-08-04
**Scopo:** preservare la conoscenza di un trade-off pagato a caro prezzo. Prima del TASK-228/267
(rimozione LabBlock) c'erano esperimenti su entita' planning multiple (famiglie Shadow e Block) con
un trade-off di memoria/expressivita' sui ValueRangeProvider (VRP). Quelle entita' sono state rimosse
ma il trade-off imparato non era documentato. Questo doc lo registra.

> NESSUNA adozione, NESSUN cambio al codice. Solo documentazione storica, ogni claim e' primary-source
> (file:line, commit SHA, doc storici). CH = Construction Heuristic, LS = Local Search (glossario in CLAUDE.md).

## TL;DR

**Fatto che inganna piu' spesso: la raccomandazione del research doc `research-value-range-provider-2026-02-27.md`
("NON aggiungere VRP entity-level per `Lesson.room`") e' SUPERATA, e lo e' stata quasi subito.** Il
TASK-153 (commit `c79c6752`, 2026-03-04, **5 giorni dopo** il research doc) ha aggiunto proprio quel
VRP entity-level (`Lesson.getAvailableRooms()`, `Lesson.java:344`), rimuovendo il VRP solution-level
su `SchoolSchedule.rooms`. Il TASK-456 (lab-fallback, 2026-07-29, `c93018ed`/`5ee5171f`) ha poi
**allargato il filtro** della stessa funzione (non-lab vede anche le stanze tagged), non creato il
VRP. Oggi `Lesson` ha due VRP entity-level (timeslot e room). Chi legge il research doc del 2026-02-27
e lo prende per buono si sbaglia: vedi sezione 4.

Sotto al fatto sopra, due assi di trade-off distinti (spesso confusi):

- **Asse A, entity-level vs solution-level VRP.** Un `@ValueRangeProvider` sull'entita' consuma piu'
  memoria e disabilita ottimizzazioni automatiche (cloning piu' pesante), ma permette filtering
  dinamico per-entita'. Scelta VIVA oggi su `Lesson`. Dettaglio e costi: sezione 2.
- **Asse B, singola vs multiple entita' planning.** Avere `LabBlock`/`LabFollower` come entita'
  aggiuntive rendeva il dominio piu' espressivo (mosse atomiche di blocco, contiguita' strutturale)
  a costo di copie-VRP per tipo, 9 set di vincoli split (L-L/L-B/B-B), piu' fasi e validazione
  runtime (H9/M4/M17/M18). RIMOSSO (TASK-228/267). Perche' e numeri: sezioni 3 e 5.

## 1. Cosa si provava (le tre famiglie di branch)

Tre famiglie architetturali parallele, stesso dominio/seed/frontend/API, diversa modellazione dei
lab. Riferimento vivo e autorevole: `claudedocs/branch-families.md` (con reality note TASK-322).

- **Baseline**: solo `Lesson` (un'ora), `timeslot`+`room` planning variable; contiguita' lab via soft.
- **Block**: `Lesson` + `LabBlock` (entita' separata, `startTimeslot`+`room`+`blockSize`, mosse
  atomiche di blocco); vincoli split L-L/L-B/B-B (H1.1/H1.2/H1.3).
- **Shadow**: `Lesson` + `LabFollower` (leader-follower, `timeslot` shadow dal leader); split L-L/L-F/F-F.

L'argomento di questo doc ha bisogno solo di un fatto: **Block e Shadow aggiungevano ciascuna un
secondo tipo di entita' planning** (`LabBlock` / `LabFollower`). La modellazione per-famiglia sta in
`branch-families.md`. Oggi tutte DORMANT (sotto `archive/*`) tranne `feature/cartesian-block-entity`,
il cui nome e' storico: non c'e' piu' alcun LabBlock.

## 2. Il trade-off

### Asse A, entity-level VRP (fonte: research doc 2026-02-27, doc Timefold quotato)

Dal doc Timefold (riportato nel research doc L75, attribuito):

> "A ValueRangeProvider on the planning entity consumes more memory than ValueRangeProvider on the
> Solution and disables certain automatic performance optimizations."

I costi che pesarono sulla decisione: (1) memory overhead, ogni entita' conserva la propria lista;
(2) ottimizzazioni automatiche disattivate; (3) solution cloning overhead (significativo su dataset
grandi). L'inventario completo (inclusi chained variables e local-optima caveat) sta nel research doc
L77-108. Mitigazione indicata da Timefold: VRP **parameter-based** (il metodo dell'entita' riceve la
solution come parametro: meno memoria, meno cloning, filtering per-entita' comunque); **non adottata**
perche' l'entita' non ha accesso alla solution nella codebase attiva (research doc L99).

### Asse B, multiple entita' planning (fonte: TASK-228 finalSummary; design doc per la proiezione pre-implementation)

**Numeri MISURATI (TASK-228 finalSummary, non la proiezione del design doc):**

| Metrica | Prima (Block) | Dopo (denormalizzato) | Fonte |
|---------|---------------|----------------------|-------|
| Entita' `Lesson` | ~7.000 (+ ~200 LabBlock) | **10.550** (lab hours denormalizzati in lesson pinnate, +50%) | finalSummary |
| Entita' `LabBlock` | ~200 | 0 | finalSummary |
| Set di vincoli conflitto | 9 (L-L/L-B/B-B) | 3 (L-L) | design doc |
| Vincoli totali | ~40 | ~28 | finalSummary |
| Fasi solver | 8 | 6 (oggi 5, TASK-420) | design doc / solverConfig |
| Medium score | -387M | **-219M (43% meglio)**, hard 0 | finalSummary |

> **Avvertenza accuratezza:** il design doc `denormalize-labblocks-to-lessons.md` (status Planning)
> proiettava ~7.600 lesson (+5,5%): una stima pre-implementation che sottostimava la crescita.
> L'attuale e' 10.550 (finalSummary); il seed 2026/27 odierno e' ~10.542. Si cita la finalSummary,
> non la proiezione.

**Cosa e' misurato e cosa no:** il trade-off Asse B e' misurato su **performance solver** (medium
-387M -> -219M, hard 0). Il **costo di memoria** (Asse A) e' **qualitativo**, fondato sui doc Timefold
("consumes more memory"), NON esiste una misurazione in MB/heap nei doc di campagna. Non si riporta
quindi un numero di memoria (sarebbe inventato). Il giudizio e' caduto sulla semplificazione solver
(meno vincoli, meno fasi, validazione H9/M4/M17/M18 spostata alla pre-assegnazione), misurata e
positiva, non sulla memoria da sola.

## 3. Perche' abbiamo scelto Lesson singola (Asse B, TASK-228 + TASK-267)

- **TASK-228** (2026-03-13, Done): collassare `LabBlock` in `Lesson` pinnate (`isLabHour=true`).
  Risultato (finalSummary): 0 LabBlock in solving, medium 43% migliore, constraint count ~40 -> ~28.
- **TASK-267** (2026-03-17, Done, commit `0a91e975`): cleanup del codice deprecato post-migrazione
  (`LabBlock.java`, `LabBlockDifficultyComparator`, caricamento, riferimenti TS). La pre-assegnazione
  usa il wrapper `LabSession`, non entita' LabBlock.

Il giudizio e' caduto sul bilancio complessivo: vincere 15-25% di tempo (design doc) e 43% di medium,
eliminare 6 set di vincoli split e 2 fasi, spostare la validazione (H9/M4/M17/M18) dal solver alla
pre-assegnazione deterministica, ha controbilanciato la perdita di expressivita' (mosse atomiche di
blocco). La contiguita' dei lab, che `LabBlock` enforceva strutturalmente, e' oggi garantita da
`Lesson` pinnate + vincoli medium (M15 same-day same-subject consecutive, M31 lab-day single run,
famiglia lab-day consolidation TASK-463).

Un "costo pagato" correlato: il **VRP identity bug** (`timefold-bug-report-value-range-identity-bug.md`,
2026-03-06, Timefold 1.31.0) bloccava l'init del solver per una contraddizione `IdentityHashSet` (==)
vs `HashedValueRange` (.equals()); risolto upstream in 1.32.0, obsoleto su 2.3.0. Esempio del principio
"piu' entita' e piu' VRP = piu' superficie per questa classe di bug".

## 4. Stato corrente (Asse A: la raccomandazione 2026-02-27 e' SUPERATA)

(Elabora il fatto in apertura al TL;DR.) Il research doc `research-value-range-provider-2026-02-27.md`
(task-031) raccomandava di **non** aggiungere VRP entity-level per `Lesson.room` (motivo: ~7.000
entita' x cached list = overhead non-triviale; ~20 stanze; H-M2 hard impara in fretta; la LS vuole
flessibilita'). All'epoca `Lesson.room` usava il VRP solution-level.

**Superata in due tempi.**
1. **TASK-153** (commit `c79c6752`, 2026-03-04, 5 giorni dopo il research doc): ha AGGIUNTO il VRP
   entity-level `Lesson.getAvailableRooms()` (`Lesson.java:344`) e rimosso il VRP solution-level su
   `SchoolSchedule.rooms` (il commento `SchoolSchedule.java:1337` "Room VRP moved to entity-level"
   corrobora). Gia' qui la raccomandazione letterale "non aggiungere il VRP" era overturn. Il filtro
   iniziale manteneva lo spirito del research doc: le non-lab escludevano le stanze tagged
   (`r.tags == null || r.tags.isEmpty()`), cioe' non potevano rifugiarsi nei lab.
2. **TASK-456** (lab-fallback, ratifica 2026-07-29, `c93018ed`+`5ee5171f`): ha cambiato solo il
   PREDICATO di filtro della stessa funzione (`labFallback || !r.isTagged()`): con lab-fallback ON
   (default), le non-lab vedono anche le stanze tagged. Questo ribalta lo SPIRITO del research doc
   (non-lab nei lab) ma non crea il VRP, che esisteva da 5 mesi. Introdotto M-LF (medium: non-lab in
   stanza tagged). Il beneficio (chiudere i 94 room-null acconciatura in ospite-legale, hard -112 ->
   -17/-18, vedi ratifica combined 463+464 in CLAUDE.md) ha superato il costo.

Stato corrente verificato (grep + git, 2026-08-04): `Lesson.java:171` VRP entity-level
`availableTimeslots` (filtering targetEndDate/pinned); `Lesson.java:344` VRP entity-level
`availableRooms` (aggiunto TASK-153, filtro allargato TASK-456); `SchoolSchedule.java:1332` VRP
solution-level `timeslots` (l'unico residuo: il VRP `rooms` solution-level e' stato rimosso a
TASK-153). Mix deliberato: entity-level su `Lesson` (filtering dinamico, costo memoria accettato) +
solution-level `timeslots` su `SchoolSchedule`. La lezione del 2026-02-27 (il costo c'e') resta
vera; la decisione specifica "non mettere il VRP room su Lesson" e' stata ribaltata dal TASK-153
(5 giorni dopo) e poi approfondita dal TASK-456. Coerente con la direttiva primaria: il modello si
giudica sulla correttezza logica, il costo computazionale si assorbe lato ricerca.

## 5. Cosa significherebbe riattivare Asse B (LabBlock/LabFollower)

1. Reintrodurre `LabBlock`/`LabFollower` come `@PlanningEntity` con planning variable e VRP entity-level
   propri (timeslot inizio + room + blockSize).
2. Re-split dei vincoli di conflitto in L-L/L-B/B-B (9 set), H9 (lab entity valid placement) tornato
   runtime invece che pre-validato.
3. CH in due fasi (LabBlock prima), fasi solver in piu'.
4. Copia-VRP moltiplicata per il nuovo tipo (costo Asse A su un tipo in piu'), cloning piu' pesante,
   superficie per bug classe identity-bug.
5. Riscrivere la pre-assegnazione (oggi wrapper `LabSession` su `Lesson` pinnate) per emettere di
   nuovo entita' LabBlock.

**Quando varrebbe la pena:** solo se la contiguita' dei lab non si reggesse piu' con il modello
attuale (`Lesson` pinnate + M15/M31 + lab-day consolidation TASK-463), o se le mosse atomiche di
blocco sbloccassero qualita' di ricerca non raggiungibile altrimenti. **Oggi NON e' warrantato:** la
campagna mostra che la contiguita' tiene (ricorrenza unpinned ~75%, lab-day consolidation TASK-463
default ON, hard-0 sul good seed). Riattivare per "performance" contraddice TASK-228 (che sulle
performance ha vinto 15-25%) e la direttiva primaria.

**Se invece la domanda e' ridurre il costo di memoria dei VRP entity-level attuali (Asse A):** la via
Timefold e' il VRP parameter-based (l'entita' riceve la solution), non adottato perche' l'entita' non
ha accesso alla solution (vedi sezione 2). Cambio architetturale minore ma non banale, da valutare
solo se la memoria diventasse un collo misurato (oggi non lo e').

## Fonti (primary)

- `claudedocs/branch-families.md` (famiglie Baseline/Block/Shadow, reality note TASK-322).
- `claudedocs/design/denormalize-labblocks-to-lessons.md` (TASK-228 design; tabella PROIEZIONE pre-impl).
- `claudedocs/research-value-range-provider-2026-02-27.md` (task-031, trade-off entity vs solution VRP,
  raccomandazione poi superata da TASK-456).
- `backlog/tasks/task-228` (Done 2026-03-13, finalSummary con misure reali).
- `backlog/completed/task-267` (Done 2026-03-17, cleanup).
- `claudedocs/timefold-bug-report-value-range-identity-bug.md` (2026-03-06, VRP identity bug).
- CLAUDE.md sez "Branch Families" + ratifiche combined 463+464 + M-LF (TASK-456).
- Codice: `Lesson.java:171/344` (VRP entity-level), `SchoolSchedule.java:1332` (VRP solution-level).
- Commit: `c79c6752` (TASK-153, aggiunta VRP entity-level room su Lesson), `0a91e975` (TASK-267
  LabBlock removal), `c93018ed`+`5ee5171f` (TASK-456 lab-fallback, allarga il filtro room VRP).

## Scope note (TASK-235)

Doc read-only di storia/decisioni (reframing di maintainer via Fedora: il task originale puntava a
MEMORY.md; il saggio storico sta in `claudedocs/`). AC#2 del task originale (annotare `Lesson.java`
con un javadoc-pointer a questo doc) e' un tocco di codice lasciato gated da maintainer; sicuro e banale
se lo si vuole (one-liner su `getAvailableRooms`/`getAvailableTimeslots`). AC#3 (alternative) e AC#4
(guidance entity-level vs solution-level) sono coperti in sezioni 2 e 5.
