# TASK-455 POINT 3: checkpoint M-TC sopra v1 (30g) - 2026-07-30

**Mandato (maintainer, 2026-07-30):** misurare il vincolo M-TC (tag-capacity, prototipo dietro
flag DEFAULT OFF) sopra la certificazione v1 (30g). Piano confermato: (1) bridge
`PLANNER_TEST_ROOM_TAG_CAPACITY` in `RoomPhaseSnapshotTest`, (2) misura al checkpoint di
room-null per (tag, weekday) M-TC ON vs OFF, (3) solo se il checkpoint promette, solve
intero. DoD sul bridge. Nessuna adozione.

**Esito: il checkpoint PROMETTE, ma non attraverso il bridge.** Due difetti strutturali
rendono il confronto ON/OFF room-null del bridge meccanicamente NULLO. Il checkpoint onesto
e' la diagnostica read-only sull'export (quella che la spec prescrive e che 30e ha usato),
che ho girato su 30g. La conferma DEVE essere un solve intero: e' l'unico luogo dove M-TC
prende un gradiente reale.

## 1. Il bridge e' lo strumento SBAGLIATO per room-null M-TC (verificato sul codice)

M-TC non e' un vincolo di assegnazione stanza: e' un vincolo di DOMANDA AGGREGATA.
`SchoolScheduleConstraintProvider.labTagCapacity` fa `groupBy((tag, startDateTime), count())`
sulle lezioni placed del tag, poi penalizza `count - roomCapacityForTag(tag)` per le celle
sovra-capienza. Il conteggio dipende solo da QUALI lezioni stanno a quel datetime con quel
tag, non da quale stanza hanno. Due conseguenze, entrambe letali per il piano confermato:

**(a) Gradiente zero in fase solo-aule.** La harness `RoomPhaseSnapshotTest.roomPhasesOnly`
ha mosse SOLO sulla variabile `room` (changeMove + swapMove, `withVariableName("room")`,
nessun timeslot). A timeslot fissi la domanda per (tag, startDateTime) e' FISSA, quindi la
penalita' M-TC e' COSTANTE rispetto a ogni mossa di stanza. Una penalita' costante da'
gradiente zero alla LS: room-null e ordinarie ON vs OFF sono identiche a meno del rumore
tabu. La fase aule e' strutturalmente impotente sulla scarsita' di tag (le 3 lezioni di
acconciatura che contendono l'unica stanza non si risolvono spostando stanze, si risolvono
spostando 2 lezioni ad altre date, che la fase aule non puo' fare).

**(b) La capacity non e' pubblicata nella harness.** `SchoolSchedule.roomCapacityForTag`
legge `roomCapacityByTagStatic`, pubblicata in produzione SOLO da
`ScheduleService.refreshSolverStatics` (riga 1730-1739, conta le stanze assignable per tag).
La harness costruisce il Solver via `SolverFactory` bypassando `ScheduleService`, chiama
`setAllRoomsStatic(rooms)` ma MAI `setRoomCapacityByTagStatic`. `SnapshotFixture.resetStatics`
non la resetta neanche. Risultato: il campo resta al default `new HashMap<>()`, quindi
`roomCapacityForTag` ritorna 0 per ogni tag. Con capacity 0 il filtro `count > 0` scatta su
OGNI cella con almeno una lezione e la penalita' diventa `count * peso` su TUTTE le lezioni
lab, non solo quelle scarse. M-TC al checkpoint del bridge leggerebbe garbage. (Il test
unitario dedicato `LabTagCapacityConstraintTest` pubblica la mappa esplicitamente per
test, e li' M-TC funziona: il difetto e' solo della harness room-phase.)

Il bridge (flag + peso, specchiato sul lab-fallback) e' meccanicamente corretto per cio'
che fa, ma "stessa meccanica del lab-fallback" vale per il toggle del flag, NON per la
capacity: M-LF non legge una mappa statica (e' un filtro per-lezione), M-TC si'. La
publicazione della capacity e' specifica di M-TC e manca. Anche aggiunta, resterebbe il
difetto (a): gradiente zero. La DoD su un measurement nullo e' moot.

## 2. Il checkpoint onesto: diagnostica read-only su export 30g (PROMETTE)

Strumento: `scripts/analyze/tag-capacity-excess-kpi.py` (la diagnosi che la spec
`task455-tag-capacity-30e` prescrive: "checkpoint per MISURARE e scartare in fretta").
Cella = (date, startDateTime). Capacity(tag) = #stanze assignable con quel tag. H29 =
placed AND room==null AND not pinned AND not pseudo (filtro scoperto sul dato, coincide col
match count dello score). Nessun re-solve, nessun flag.

```
30g (v1: Servizio stilistico de-tag G14 + G12)
H29 = 119   excess_total = 149   causality_spare=0  causality_contended=119  (100% contesa)

(A) H29 room-null per (tag richiesto, weekday)
tag                MON   TUE   WED   THU   FRI   TOT
acconciatura         1    13    31    50     0    95
termoidraulica       0    24     0     0     0    24
TOT                  1    37    31    50     0   119

(B) modello capacita': excess (domanda - cap) per (tag, weekday)
tag             cap   MON  TUE  WED  THU  FRI  excess
acconciatura      1     1   13   31   50    0    95
termoidraulica    1     0   24    0    0    0    24
moda              1     0    0    0   30    0    30   (pinned, immovabile, v. nota)
informatica       2     0    0    0    0    0     0
```

**Lettura.** Le 119 residuali sono 100% contesa (0 in cella con capienza residua): ogni
room-null e' una cella dove domanda > capacita', mai "aula libera ma non assegnata". Il
99% (118/119) e' su DUE lab a stanza unica: acconciatura (cap 1) 95, di cui THU 50 + WED 31
+ TUE 13 (picco giovedi', coerente col mandato "quasi tutte acconciatura gio/mer"), e
termoidraulica (cap 1) 24, tutta martedi' (T03 / G03), invariata da 30e. M-TC ha un
bersaglio netto e affilato su v1.

**Confronto 30e -> 30g (effetto del de-tag v1 sulla scarsita').**

| metrica | 30e | 30g (v1) | delta |
|---|---|---|---|
| H29 room-null | 169 | 119 | -50 (chiuse, coincide col VERDICT 30g) |
| excess_total | 202 | 149 | -53 |
| acconciatura H29 | 144 | 95 | -49 |
| termoidraulica H29 | 24 | 24 | 0 |
| overcap_cells | 200 | 149 | -51 |

Il de-tag v1 ha chiuso 49 acconciatura (144->95); il residuo resta concentrato sugli stessi
due lab monostanza. La cura M-TC mira esattamente alle 95 acconciatura (THU/WED/TUE) + 24
termoidraulica (TUE).

## 3. Percorso: la conferma DEVE essere un solve intero

Il checkpoint (diagnostica) promette. Ma "M-TC chiude room-null" si puo' misurare SOLO in un
solve completo, dove i timeslot si muovono e M-TC prende un gradiente reale (spostare le
lezioni unpinned fuori dalle datetime contese riduce sia la penalita' M-TC sia le room-null
H29). La fase aule non puo', per costruzione (sez. 1a). Regola della spec confermata:
checkpoint per MISURARE e scartare in fretta, DoD + solve completo per CONFERMARE prima di
adottare.

Setup del solve di conferma (capture 30h):
- M-TC ON via `-Dplanner.tag-capacity.enabled=true` sulla riga `java -jar` (NON MAVEN_OPTS:
  lesson di CLAUDE.md, il fork dell'app non vede i -D di Maven). Capacita' pubblicata in
  produzione da `ScheduleService` automaticamente, quindi qui M-TC legge le capacity reali.
- profilo fresco: la baseline G14 e' ora quella nuova del TASK-462 (120h, non piu' 990h).
  **Il confronto 30h-vs-30g NON e' omogeneo su G14 e va dichiarato nel VERDICT 30h**
  (mandato esplicito di maintainer). La scarsita' acconciatura e' dominata da 2/G10 + G12,
  non da G14 (su 30g il Servizio stilistico G14 e' de-taggato), quindi il confound e'
  atteso minore sul bersaglio principale ma va dichiarato onestamente.
- solve-staged (modo campagna), capture `scripts/benchmark/capture-run.sh`.
- confronto vs 30g sui 3 KPI di guardia + room-null. Base 30g: buchi H17 0, ricorrenza
  unpinned 74,5%, full-day ex-G14 88,1% (159 sotto, 151 a -1h, 8 profondi), room-null 119,
  score -125h/-995929m/+18175s.
- Precedente di cautela (QUADRO): il REFUTO di slot0-balancing (30c) mostra che "spostare la
  risorsa scarsa invece di coordinarla" degrada M20/M15 e crea buchi H17 hard. M-TC e' una
  leva di SPARGIMENTO della stessa famiglia: il medium-alto (non hard) e' scelto proprio per
  misurare questo trade-off senza irrigidire. Il solve 30h e' la misura di quel trade-off.

**Nessuna adozione.** M-TC resta DEFAULT OFF. La decisione e' di maintainer sui numeri del 30h.

## 4. Fate del bridge

Il bridge (+18 righe, uncommitted, scritto dalla sessione precedente) e' lo strumento
sbagliato per room-null M-TC (sez. 1). Tre opzioni oneste:
1. **Revertirlo** (consigliato): e' una trappola di measurement nullo per la sessione
   successiva. La diagnostica dà gia' il segnale che il bridge potrebbe dare (score delta),
   read-only e senza capacity da pubblicare.
2. **Completarlo** (pubblicare `roomCapacityByTagStatic` dai rooms) + tenerlo solo come
   cross-check ridondante dello score delta M-TC (149k su 30g). Ma resterebbe gradiente-zero
   su room-null, quindi ingannevole se letto per quello scopo.
3. Lasciarlo uncommitted come sta: M-TC leggerebbe capacity 0 se mai attivato, garbage.

Lasco la decisione a maintainer. Nel frattempo il working tree ha il bridge uncommitted; non
tocca il codice di produzione e non influisce sul solve 30h (che usa i path di produzione).

## 5. Nota secondaria: discrepanza spec vs codice sul pinned (da riconciliare)

La spec `task455-tag-capacity-30e` punto 3 dice "penalizza solo le unpinned oltre capacita'".
Il codice penalizza `lessonCount - capacity` dove `lessonCount` INCLUDE le pinned (groupBy su
`forEachIncludingUnassigned` filtrato `timeslot!=null && size==1 && !pseudo`, nessun
`!pinned`). Il commento in codice (riga 1720-1731) e' invece esplicito e autoconsistente:
conta tutte (mirror di H7), e le celle sovraccaricate SOLO da pinned (moda/R16 giovedi',
30h su 30g) "carry a constant penalty the search cannot move". Quindi il codice penalizza le
30 moda pinned come costo permanente immovabile. Conseguenza per il 30h: M-TC ON aggiunge
almeno -30.000 (moda pinned immovabile) + il nucleo mobile (acconciatura 95 + termoidraulica
24 = le 119 H29) che il solver puo' ridurre spargendo. La spec e il codice vanno riconciliati
(la spec e' imprecisa o aspirazionale; il codice e' l'autorita' e il suo commento e' coerente).
Non blocca il 30h, ma il punteggio medium non va letto senza togliere le 30 moda pinned dal
"costo mobile".
