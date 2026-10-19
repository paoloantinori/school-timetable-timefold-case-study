# PROPOSTA TASK-481: cura H17 residui path input. Fork al decisore (Fedora/maintainer). 2026-08-02.

> Sessione alba5 (zai), ripresa da handoff omonimo. Verifica EF + diagnosi H17 + spike offline
> completate. **La diagnosi alba4 contiene un errore fattuale sulla config che invalida la cura lì
> proposta** ("aggiungi una metaeuristica"): il premise e' falso. La cura reale e' NON banale e
> richiede una scelta di direzione. Questo doc presenta l'evidenza e il fork. Nessun codice di cura
> scritto: direttiva "se il design si rivela grosso o ambiguo, fermati alla proposta".

## TL;DR (per il decisore)

1. **EF ratificato: in cassa e corretto.** Codice committed+pushed (`40d257ae`), opt-out idiom
   `isExactFinisherEnabled`, knob `DEFAULT_MAX_CANDIDATES=1000000`, test 963/0/16 (handoff). EF e'
   ORTOGONALE agli H17 (analysis.json bad-seed: `H17 -6hard/6matches` con H0=0, EF 18/18). Confermato.
2. **La diagnosi H17 alba4 ha un errore fattuale.** Dice "nessuna metaeuristica nel config, forager
   di default (hill-climbing), change+swap only, zero cartesian; cura = switch a tabu/late-acceptance".
   FALSO: `solverConfig.xml` Phase 2 ha `<acceptor><entityTabuSize>7>` (**tabu search attivo**, non
   hill-climbing), forager `acceptedCountLimit=1000`, e **quattro** selettori (`change`, `swap`,
   `pillarChange`, `pillarSwap`). Il solver staged B-TIMESLOT carica quell'XML
   (`ScheduleService.timeslotPhasesConfig` via `createFromXmlResource`). Tabu + pillar GIA' girano e
   lasciano comunque il buco. La cura "aggiungi metaeuristica" e' **doppiamente falsa**: (a) c'e'
   gia', (b) la strategia di accettazione non allarga il vicinato delle mosse, quindi non compone
   l'uscita a 3-mosse comunque.
3. **Spike offline (verificato): il buco @10 non e' chiudibile con 1- o 2-mosse nel bacino bad-seed.**
   Il test `H17HoleCloseDiscriminatorTest` (gated, su export bad-seed fedele: H1/H3=0, H17=-6 exact):
   1-move fill = 0/6, 2-move evict+fill = 0/6. La cella @10 del mercoledi' e' **saturata di docenti**
   per le materie d'aula di G11 (cross-check raw indipendente: i 10 docenti d'aula sono tutti
   occupati @10; i 3 docenti liberi @10 insegnano materie LAB/sparse con la cella @10 fuori VRP).
4. **La cura e' NON banale.** Ci sono 4 direzioni (A/B/C/D sotto), ciascuna lavoro reale con trade-off.
   **La scelta dipende da un requisito di prodotto: un singolo run deve essere 0-hard deterministico,
   o e' accettabile il multi-start?** Fork al decisore.

---

## 1. Catena causale VERIFICATA (causal-chain + adversarial-verification)

Ogni anello, stato e fonte:

| # | Anello | Stato | Fonte |
|---|--------|-------|-------|
| 1 | Il golden ha G11 mercoledi' COMPATTO (8-9-10-11-12), 0/34 mercoledi' con buchi | CONFIRMED | `seed/lessons.csv` (2026-11-11: T05@8-9, T23@10, T04@11, T11@12) |
| 2 | Anche la costruzione lo piazza compatto (composizione diversa, sempre compatta) | CONFIRMED | `target/construction-lessons.txt` (2026-11-11: T11@8-9, T04@10-11, T23@12) |
| 3 | Il finale bad-seed ha il buco @10 (occupied 8,9,11,12,13) | CONFIRMED | `kpi-holes.txt` + `lessons.txt` bad-seed (T08@8-9, T23@11, T02@12, T06@13) |
| 4 | H17 = -6hard/6matches, nessun altro hard residuo (H0=0 via EF) | CONFIRMED | `analysis.json` bad-seed (constraints[0] = H17 -6hard) |
| 5 | **EF ortogonale**: non crea/non corregge il buco | CONFIRMED | EF piazzando solo boxed in celle conflict-free; @10 e' H1-bloccata; meccanicamente prosciolto |
| 6 | **Il solver staged USA l'XML** (tabu + pillar + accepted-forager ATTIVI in Phase 2) | CONFIRMED | `ScheduleService.timeslotPhasesConfig:1108-1188` via `createFromXmlResource(solverConfigResource)`; `solverConfig.xml:120-130` (`<acceptor><entityTabuSize>7>`, `<forager><acceptedCountLimit>1000>`), `:97-118` (pillarChange/pillarSwap) |
| 7 | `StudentDayCompaction` chiude un buco SOLO spostando la lezione MAX-down; max=T06@13, saturo @10, break, nessun cambio composizione | CONFIRMED | `StudentDayCompaction.java:99-117` (max-only), `CompactionPhase.java:70` |
| 8 | **Nessuna 1- o 2-mossa chiude @10 nel bacino bad-seed** (cella @10 saturata di docenti d'aula) | CONFIRMED (spike + raw) | `H17HoleCloseDiscriminatorTest`: 1-move 0/6, 2-move 0/6; raw: 10 docenti d'aula tutti busy@10 |

**Cosa regge della diagnosi alba4:** l'attribuzione causale (artefatto del solver, non pratica della
scuola; EF ortogonale; golden 0/34; un arrangement compatto esiste). **Cosa NON regge:** la descrizione
della config ("no metaheuristic, hill-climbing, change+swap") e la cura derivata ("aggiungi tabu/LA").
Correzione fattuale da riportare in `CLAUDE.md` (sezione RATIFICA TASK-478) e nel doc diagnosi alba4
(vedi §5).

## 2. Il meccanismo reale (perche' il buco resta)

Nel **bad seed**, la LS distribuisce i docenti in modo che la cella @10 del mercoledi' diventi un
**collo di bottiglia di capacita' docente** per G11: tutti i 10 docenti di materia d'aula che
potrebbero legalmente occupare @10 (VRP) sono gia' occupati @10 con un altro gruppo. I 3 docenti
liberi @10 (T12/T22 = LAB; T24 = Normativa sparse) hanno la cella @10 **fuori VRP** (giorni
lab / vincoli materia), quindi non possono collocarvisi. Risultato: per chiudere @10 servirebbe
**liberare un docente @10 spostando la lezione di un ALTRO gruppo** (mossa coordinata cross-gruppo a
3+ mosse), non una singola mossa. Tabu + pillar + change + swap non compongono quella cascata in uno
step (il vicinato e' a 1 mossa; pillar muove una colonna intera, non questa cascata mirata).
`StudentDayCompaction` e' max-only e non prova il cambio composizione. Nessun pass deterministico
esistente la cerca. Il bacino good-seed (run1/run3, 0 buchi) non cade in questa saturazione.

**Conclusione meccanica:** e' un problema di **vicinato di ricerca + bacino**, non di accettazione.
Aggiungere/ritoccare la metaeuristica (tabu/LA) NON risolve: la strategia di accettazione non genera
la mossa coordinata che manca.

## 3. Spike offline (de-risk, "repro prima del codice")

`src/test/java/com/school/planner/bootstrap/H17HoleCloseDiscriminatorTest.java` (gated
`PLANNER_TEST_H17_HOLE=1`, default OFF, come `LateStartSwapScoreTest`). Ricostruisce il finale bad-seed
dall'export (`SnapshotFixture.loadFromExport(..., includeQ22=true)` + `seedMediumTierFacts`),
verifica la fedelta' (H1/H3 conflicts=0, H17=-6 exact; il -114000 di hard e' solo H2 room-tier, rumor
costante che si cancella nel delta), poi per ognuno dei 6 mercoledi' prova:

- **1-move fill** (B -> @10, il giorno diventa 6 contigui): 0/6. Dei 527 G11 con @10 in VRP,
  NESSUNO ha docente libero @10 (gli unici liberi sono lab/sparse fuori VRP).
- **2-move evict+fill** (sposta una lezione del giorno nello slot vecchio di B, riempi @10 con B): 0/6.
  ~2050 pair respinti per B-teacher@hole (docente occupato @10), ~580 per A-teacher@B-old.

Cross-check raw (`lessons.txt`, indipendente dalla ricostruzione Java): i 10 docenti d'aula di G11
{T05, T04, T11, T08, T06, T25, T23, T21, T03, T02}
sono tutti occupati @10 ogni mercoledi' del buco. Conferma la saturazione.

**Esito: il buco e' hard-forced nel vicinato 1/2-mosse del bad-seed. La cura non puo' essere un
repair deterministico a profondita' 1 (a differenza di EF per H0).**

## 4. Il fork (4 direzioni, scelta del decisore)

La scelta dipende da: **un singolo run deve essere 0-hard deterministico, o il multi-start e'
accettabile come strategia H17?**

### Opzione A. Composition-aware hole-closer deterministico a profondita' limitata (RACCOMANDATA se si vuole 0-hard single-run)
Sibling di `ExactFinisher`/`StudentDayCompaction`, nuovo custom phase (o estensione di
`StudentDayCompaction`) nel ciclo staged (post-CompactionPhase/pre-B-ROOM). Per ogni buco H17:
cerca a profondita' 2-3 la cascata "libera un docente @cell spostando la lezione cross-gruppo + riempi
+ backfill", improve-or-noop su HARD (gate chain timeslot-tier). Cap dei candidati come EF
(`max-candidates`), revert su non-miglioramento. Flag-gated default OFF, A/B su bad seed.
- **Pro:** deterministico (garantisce 0-hard sul pattern noto, come EF per H0); search-side only
  (modello H17 non toccato, direttiva primaria rispettata); allineato alla filosofia "repair
  deterministico" del codebase; l'A/B e' pulito.
- **Contro:** LAVORO MEDIO-GRANDE. L'albero a profondita' 3 puo' essere grande; serve bounding
  intelligente (come EF). Non garantito a chiudere se la cascata richiesta e' piu' profonda di 3.
- **Stima:** ~1 giornata design+impl + catena localizzata + A/B (2-3 solve ~50min).

### Opzione B. Selettore di mosse coordinato (2-Kempe-chain / cartesian mirato)
Aggiungere un `moveSelector` che proponga la cascata cross-gruppo come singola mossa composita, cosi'
tabu la puo' esplorare. Es: swap a catena tra gruppi che libera una cella.
- **Pro:** lascia che la metaeuristica scopra la via; piu' "elegante" concettualmente; generalizza
  ad altri buchi.
- **Contro:** LAVORO MEDIO. Il design della mossa e' sottile (correttezza H1/H3 nella catena);
  impatto performance LS; NON deterministico (potrebbe non chiudere entro budget). Difficile da A/B
  isolare.
- **Stima:** ~1-1.5 giornate, rischio alto di non tenere in LS.

### Opzione C. Defer a multi-start (TASK-480) come strategia H17
I good seed (run1/run3) hanno 0 buchi. TASK-480 (multi-start vero da costruzioni indipendenti) dice
esplicitamente: "se il finisher chiude il worst case da solo, multi-start = cintura+bretelle; se no,
e' la via principale". EF NON chiude H17, quindi multi-start E' la via principale per H17.
- **Pro:** niente nuova code_surface di search; usa quel che TASK-480 costruisce; coerente con la
  natura di bacino del problema.
- **Contro:** NON deterministico single-run (un run puo' ancora cadere nel bacino bad); richiede che
  TASK-480 sia adottato; 0-hard "binario e bloccante" diventerebbe "0-hard in attesa di un buon
  bacino" (tempi wall-clock ~Nx).
- **Stima:** 0 su TASK-481 (lavoro in TASK-480). TASK-481 si chiude come "superseded/deferred a 480".

### Opzione D. Protezione della composizione compatta in costruzione (sconsigliata)
Pinnare/promuovere la composizione compatta G11 mercoledi' prima della LS.
- **Contro:** la LS distrugge quella composizione per risolvere H1 globale; pinnarla re-introdurrebbe
  quei conflitti H1. Il bacino bad-seed e' proprio quello dove la composizione compatta e' in tensione
  con il resto. Rischia infeasibilita'. Sconsigliata.

## 5. Correzioni di record da fare (a chiusura, ovunque si vada)

Indipendentemente dal fork, l'errore fattuale va corretto (e' load-bearing e TASK-481 stesso lo
eredita nei Implementation Notes):
- `CLAUDE.md`, sezione **RATIFICA TASK-478**: la frase "cura search-side (metaeuristica LS
  hill-climbing default -> tabu/late-acceptance ...)" va corretta in "tabu+pillar+accepted-forager
  SONO gia' attivi in Phase 2; la cura non e' la metaeuristica (non allarga il vicinato) ma un
  composition-aware hole-closer a profondita' limitata (Opz A) o multi-start (Opz C)".
- `task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md` sezione "La LS non scappa": same correzione.
- `backlog/tasks/task-481` Implementation Notes: aggiornare con la catena verificata + spike + fork.

## 6. Raccomandazione

- **Se il requisito e' "singolo run 0-hard deterministico"** (coerente con "0-hard e' binario e
  bloccante"): **Opzione A**, con A/B su bad seed come gate di adozione. E' la via che rispetta la
  direttiva primaria (search-side only) e il precedente EF (chiusura deterministica).
- **Se il multi-start e' accettabile**: **Opzione C** (chiudi TASK-481 come deferred a TASK-480),
  perche' il problema e' strutturalmente di bacino.

**Fermo in attesa di decisione (Fedora/maintainer).** Non forzo nessuna direzione: le stime sono reali e
le opzioni si escludono a monte (A/B toccano la search-side in modo diverso da C). Ritorno al
secondario TASK-479 (scrittura, non ricerca) se la coda si svuota in attesa del verdetto.

## Puntatori
- Spike: `src/test/java/com/school/planner/bootstrap/H17HoleCloseDiscriminatorTest.java` (run:
  `PLANNER_TEST_H17_HOLE=1 ./scripts/core/test.sh -c H17HoleCloseDiscriminatorTest`)
- Catena causale: questo doc sezione 1; diagnosi alba4 (con correzione sezione 5)
- Config: `src/main/resources/solverConfig.xml:46-131` (Phase 2 LS), `ScheduleService.java:1108-1196`
- Raw bad-seed: `task478-results/ab-ef-probe-1m-run2/{analysis.json,kpi-holes.txt,lessons.txt,export.json.gz}`
