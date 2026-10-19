# Repeated planning e nullable variables (TASK-238)

Doc di riferimento tecnico per sviluppatori futuri. Stato: 2026-08-04. NESSUN codice
di real-time/repeated planning esiste nel progetto oggi: questo doc e' forward-looking
("se mai servisse"). Verificato contro `Lesson.java` e i docs Timefold (progetto su
2.3.0; il link /latest serve 2.4.0, contenuto byte-identico per questa feature), non
da memoria.

## TL;DR

Il modello usa **nullable planning variables** (`allowsUnassigned = true`) su entrambe
le variabili di `Lesson`. Questo e' voluto (overconstrained planning, il "box"), ma ha
un costo: **il repeated planning (real-time) non si sposa bene con le nullable
variables**, perche' ogni (re)start del solver o `ProblemFactChange` fa ripartire la
Construction Heuristic che prova a reinizializzare TUTTE le variabili null, sprecando
tempo. Oggi non usiamo repeated planning, quindi il problema e' latente. Se mai lo
aggiungessimo, le alternative sono: re-solve from scratch, pinning, oppure filtrare
l'entity selector del placer (la mitigazione documentata da Timefold).

## Le nullable variables nel nostro modello (ground truth)

`src/main/java/com/school/planner/domain/Lesson.java`:

- `timeslot` (riga 123): `@PlanningVariable(valueRangeProviderRefs = {"availableTimeslots"},
  comparatorFactoryClass = TimeslotStrengthComparatorFactory.class, allowsUnassigned = true)`.
  Il commento TASK-398 (riga 113) spiega il perche': `allowsUnassigned = true` abilita
  "the box", una lesson puo' restare con timeslot null invece di essere FORZATA in un
  hard conflict. La construction box-the-residual parcheggia il residuo non collocabile
  (H0 -2 cadauno) invece di scatterarlo in conflitti H3/H1.
- `room` (riga 129): `@PlanningVariable(valueRangeProviderRefs = {"availableRooms"},
  allowsUnassigned = true)`.
- `@PlanningPin isPinned()` (riga 108): supporto al pinning (lesson autoritative
  bloccate, non tocabili dal solver).

Entrambe le planning variables possono essere null. La score e' responsabile di
punire i null (H0 `unassignedLesson` tramite `forEachIncludingUnassigned`; i constraint
stream `forEach` filtrano di default le entita' con variabile null, per questo H0 usa
`forEachIncludingUnassigned`). Questo e' overconstrained planning standard Timefold.

## La limitazione (Timefold docs, verificata)

Dai docs Timefold 2.3.0 (domain-modeling, sezione "Allowing unassigned values"):

> Repeated planning (especially real-time planning) does not mix well with a planning
> variable that allows unassigned values. Every time the Solver starts or a problem
> fact change is made, the Construction Heuristics will try to initialize all the null
> variables again, which can be a huge waste of time.

Fonte: <https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems>
(sezione repeated planning; la stessa nota appare per le basic vars `allowsUnassigned`
e per le list vars).

Cio' significa per noi: se il solver venisse fatto ripartire su una soluzione gia'
costruita che contiene lesson boxed (timeslot null), la Construction Heuristic
riprovrebbe a inizializzare QUELLE lesson null, sprecando tempo e potenzialmente
disfacendo il box che la LS aveva gia' gestito. Piu' lesson nel box, piu' spreco a
ogni restart.

Nota terminologica: i docs piu' vecchi (0.8.x) usavano `nullable = true`; da Timefold
1.8.0+ il termine e' `allowsUnassigned = true`. Il progetto usa il termine corrente.
Semantica identica.

## Alternative (se mai il repeated planning servisse)

In ordine di semplicita' / allineamento col modello attuale:

1. **Re-solve from scratch (cio' che facciamo oggi).** Ogni solve parte da una
   soluzione vuota/fresca (la costruzione ricrea il template + box + repair). Nessun
   repeated planning, nessun problema di reinizializzazione. E' l'approccio robusto
   per come e' strutturato il solver oggi (ciclo staged, multi-start, pinning del
   template). Costo: un solve completo (~28 min/run) invece di un aggiornamento
   incrementale. Accettabile perche' l'uso operativo e' "lancio notturno/manuale su
   input annuale", non editing interattivo in tempo reale.

2. **Pinning (`@PlanningPin`).** Gia' presente nel modello. Una lesson pinned non e'
   tocata dal solver. Per un "repeated planning" leggero (es. rifinire solo un
   sottoinsieme dopo un piccolo cambiamento), si possono pinnare tutte le lesson
   tranne quelle impattate dal cambiamento, e rilanciare il solver: la CH non
   reinizializzera' le pinnate (il pin le esclude dalla placement). E' la via piu'
   coerente col nostro modello (il pinning e' gia' la meccanica di template/anchor/
   box repair). Caveat: il pin va usato con attenzione, pinnare tutto tranne poche
   lesson restringe molto lo spazio di ricerca.

3. **Filtrare l'entity selector del placer nel CH (mitigazione documentata Timefold).**
   E' il rimedio che i docs indicano per le nullable + repeated planning: filtrare
   l'entity selector nel `<constructionHeuristic>` cosicche' la CH non riprovi a
   inizializzare le entita' gia' assegnate, evitando lo spreco. Esempio dai docs:
   ```xml
   <constructionHeuristic>
     <queuedEntityPlacer>
       <entitySelector id="entitySelector1">
         <filterClass>...SkipAlreadyAssignedEntityFilter...</filterClass>
       </entitySelector>
     </queuedEntityPlacer>
     ...
   </constructionHeuristic>
   ```
   Richiede un `EntityFilter` che escluda le lesson gia' collocate (timeslot != null),
   cosicche' la CH reinizializzi solo le null (il box residuo). E' la soluzione
   tecnica corretta se si vuole vero repeated planning mantenendo le nullable, ma e'
   configurazione non banale e non e' mai stata necessaria finora.

## Quando usare repeated planning vs re-solve (guida)

- **Usa repeated planning** solo se: l'uso diventa interattivo (un operatore modifica
  poche lesson e vuole una rifinitura veloce), o gli input cambiano di frequente a
  run caldo, e il costo di un solve completo e' inaccettabile. In quel caso valuta
  prima il pinning (alternativa 2), poi il filtro del CH placer (alternativa 3).
- **Usa re-solve from scratch** (default attuale) se: il solve e' un batch periodico
  su input annuali, la qualita' prevale sulla latenza, e il modello nullable + box
  + staged e' la struttura con cui si lavora. E' la situazione odierna.

## Cosa NON fare

- Non aggiungere repeated planning senza prima aver letto questo doc e considerato il
  costo di reinizializzazione dei null. Un `SolverManager.solve` su una soluzione
  boxed esistente senza filtro CH e' uno spreco silente.
- Non confondere `allowsUnassigned` (overconstrained, il nostro caso, gestito dalla
  score) con un "bug" (i null nel box sono voluti, sono il segnale di residuo non
  collocabile che la LS chiude).
- I constraint stream `forEach` saltano le entita' con var null per default: quando
  si ragiona sul box (contare/penalizzare i null) serve `forEachIncludingUnassigned`
  (gia' usato da H0). Aggiungere repeated planning non cambia questo, ma e' facile
  scordarselo scrivendo nuovi constraint.

## Riferimenti

- Timefold docs (progetto su 2.3.0; /latest = 2.4.0, feature invariata dall'1.8.0),
  domain modeling "Allowing unassigned values" + "Repeated planning":
  <https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems>
- Codice: `src/main/java/com/school/planner/domain/Lesson.java` (righe 108-131).
- Contesto box/overconstrained: TASK-398, TASK-447 (H0 hard), `CLAUDE.md` sezione
  vincoli H0/H29 e "box-the-residual".
