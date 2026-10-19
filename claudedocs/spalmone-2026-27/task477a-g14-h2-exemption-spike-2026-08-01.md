# SPIKE TASK-477a (G14 esclusione H2 co-presenza) - zai, notte 2026-08-01

> Scouting notturno (regole di ingaggio: esplorare, flag OFF, NESSUNA adozione). HEAD
> `feature/cartesian-block-entity` `6a660194`. DB = run5. Scope: prototipo esclusione coppie G14
> dall'esenzione H2 di TASK-464 (referente Q19: G14 -> R10). ESITO: lo spike RIDEFINISCE il
> task - la (a) e' INERTE sul run corrente, la vera leva Q19 e' la (b) (de-tag). Nessun codice
> scritto (prototipo sarebbe no-op); follows-up: fix javadoc stale + inconsistenza depth-map.

## MECCANISMO (verificato dal codice)

- **H2 room conflict** (`BaseConstraintProvider.roomConflict`, linea 368-399): join Lesson x Lesson su
  stessa room, stessa data, slot sovrapposto, gruppi diversi. Filtro esenzione TASK-464 a linea 392:
  `.filter((a,b) -> !(coPresenceEnabled && isCoPresenceGuestPair(a,b)))`.
- **isCoPresenceGuestPair(a,b)** (linea 409): true sse `singleCoPresenceTag(a) == singleCoPresenceTag(b) != null`
  E la cella `(tag, weekday, slot)` e' golden-practiced (depth >= 2, `SchoolSchedule.isCoPresenceCell`).
- **singleCoPresenceTag(l)** (linea 425): il tag singolo di `course.requiredRoomTags` (esattamente 1
  tag), altrimenti null. Quindi l'esenzione e' chiavata sul TAG DEL CORSO a runtime, non sul gruppo.
- **loadGoldenCoPresenceDepth** (`ConstrainedTeacherAllocator` linea 341): costruisce la mappa depth
  per cella `(tag, weekday, slot)` = gruppi distinti co-presenti. **Chiavata per NOME MATERIA**
  (`subjectToTag.get(subject)`, buildSubjectToSingleTagMap), NON per (corso, gruppo). Pubblicata
  PRE-solve in `SchoolSchedule.goldenCoPresenceDepthStatic`.

## TROVATO 1 - javadoc STALE (loadGoldenCoPresenceDepth linea 309-310)

Il javadoc dice: *"On the 2026/27 seed the only tag with depth >= 2 is acconciatura"*. **FALSO.** La
mappa depth reale (calcolata dal golden via subjectToTag, il solo input del loader) ha **5 tag** con
celle depth >= 2:

| tag | celle depth>=2 |
|---|---|
| acconciatura | 30 |
| informatica | 24 |
| moda | 15 |
| meccanica | 14 |
| termoidraulica | 8 |

**Follow-up (cheap, correttezza): correggere il javadoc** (linea 309-310 e il commento H-COPRESENCE
in SchoolScheduleConstraintProvider linea 1924-1932 se echoing dello stesso claim). Nessun cambio
logico, solo verbo.

## TROVATO 2 - G14 E' in celle depth>=2, ma profilo runtime SPLITTATO

G14 nel golden (seed/lessons.csv, closed=False): Servizio stilistico (30h), Confezione (30h),
Competenze Digitali (20h), Comportamento Sociale (20h), Orientamento (15h).

- **Servizio stilistico** -> mappa come acconciatura nel depth-map (subject-name keying: la materia
  e' acconciatura-tagged per i gruppi FA). G14 e' nelle celle acconciatura Thu 08-12 (depth 5:
  1/2/G10 + G12 + G14). **MA a runtime il corso G14 Servizio stilistico e' DE-TAGGED**
  (nessun requiredRoomTag: la query DB `course_requiredroomtags` per G14 restituisce solo
  Confezione=moda). Quindi `singleCoPresenceTag` = null per G14 Servizio stilistico -> l'esenzione
  H2 NON puo' mai fireare per quelle lezioni.
- **Confezione** -> moda tag, singolo, presente a runtime. G14 Confezione e' nelle celle moda Thu
  08-12 (depth 2-4 con 1/2/G09). **Questo e' l'unico path vivo** dove l'esenzione H2 PUO' fireare
  per G14 (Confezione moda condivisa con un host MODA).

## TROVATO 3 - CHECKPOINT run5: l'esenzione G14 NON E' USATA (INERTE)

Query DB sulla soluzione corrente (run5): nessuna lezione G14 condivide (room, timeslot) con un
gruppo diverso. L'esenzione e' DISPONIBILE per G14 Confezione (moda Thu) ma il solver NON la usa.

Dislocazione G14 in run5:
- **Confezione (moda, 30h): TUTTE 30 in R16** (lab moda), DA SOLA (nessun MODA ospitato insieme).
- Servizio stilistico (de-tagged, 30h): aule normali (R01..10, R10 3h).
- Competenze/Comportamento/Orientamento: aule normali.

**Conclusione checkpoint: l'esenzione H2 co-presenza per G14 e' INERTE sul run corrente.** Un
prototipo "escludi G14 da isCoPresenceGuestPair" sarebbe un NO-OP osservabile (difensivo: previene
solo un eventuale uso futuro, non cambia run5).

## TROVATO 4 - la VERA tensione Q19 e' la (b), non la (a)

referente Q19: G14 va in **R10** (aula normale, non ospite nel lab). Ma in run5 G14 Confezione
e' in **R16** (lab moda), perche' Confezione e' moda-TAGGED -> H-M2 la obbliga in una stanza
moda (R16 e' l'unica). R10 non e' moda-tagged, quindi Confezione NON puo' andarci sotto
H-M2.

**La tensione Q19 non si risolve con la (a) (esenzione H2), si risolve con la (b) (de-tag):** se
G14 Confezione fosse de-tagged (niente requiredRoomTag moda), potrebbe stare in R10 come
vuole referente. Ma cio' richiede una DECISIONE di referente: G14 Confezione e' un laboratorio hands-on
moda (serve R16) o si fa in aula? La (a) non sposta G14 da R16 ad R10 in nessun caso.

## TROVATO 5 - inconsistenza keying depth-map vs runtime

Il depth-map e' chiavato per NOME MATERIA, quindi conta il G14 Servizio stilistico (de-tagged a
runtime) come acconciatura, gonfiando le celle acconciatura Thu a depth 5. A runtime G14 non puo'
essere ospite acconciatura (null tag), quindi quel 5o slot non e' mai riempito: l'H-COPRESENCE cap
permette 5 co-presenti dove solo 4 gruppi possono davvero essere ospiti acconciatura. **Cap leggermente
lasco, non un bug di correttezza (il golden pratica 5), ma un'inconsistenza keying da registrare.**
Follow-up opzionale: far keyare il depth-map per (corso, gruppo) come il runtime, non per nome materia.

## SKETCH prototipo (a) - NON COSTRUITO (sarebbe no-op su run corrente)

Per memoria, se mai si vuol chiudere la (a) come lock-out difensivo: in `isCoPresenceGuestPair`
aggiungere, flag-gated default OFF (`-Dplanner.co-presenza.g14-exclude.enabled`, letto per-build
come `isCoPresenceEnabled`):
```java
if (G14_EXCLUDE && (isG14Group(a) || isG14Group(b))) return false;
```
(dove `isG14Group` e' un name-check "G14".equals(group.name); isG14 gia' esiste in
SchoolScheduleConstraintProvider:2349 ma isCoPresenceGuestPair e' in BaseConstraintProvider, servirebbe
un helper o un override). **Effetto atteso su run corrente: NULLO (checkpoint: esenzione inerte).**
DoD piena richiesta prima di qualsiasi merge (e' l'H2 stream, il piu' caldo).

## VERDETTO SPIKE (dai raw)

1. **NON costruisco il prototipo (a) stasera.** Sarebbe inert sul run corrente (esenzione G14 non
   usata, Trovato 3). Costruirlo come lock-out difensivo ha basso valore finche' non si decide la (b).
2. **La vera leva Q19 e' la (b) (de-tag G14 Confezione -> R10)**, che richiede una domanda a
   referente: G14 Confezione e' lab hands-on moda (serve R16) o si fa in aula? La (a) non la risolve.
3. **Follow-up cheap e legit (correttezza, non adozione):** fix del javadoc stale (Trovato 1) e
   registrazione dell'inconsistenza keying depth-map (Trovato 5).
4. **Adversarial-verification ha pagato:** il javadoc "acconciatura only" era false (5 tag); la mia
   prima lettura "G14 non ha acconciatura -> inert" era parziale (G14 ha Servizio stilistico che
   il depth-map conta come acconciatura, ma e' de-tagged a runtime). Solo il calcolo meccanico della
   mappa depth + il checkpoint DB hanno dato il quadro reale.

## PUNTI DI RIPRESA
- Questo doc + `BaseConstraintProvider.roomConflict`/`isCoPresenceGuestPair`/`singleCoPresenceTag`
  (linee 368-430) + `ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth` (341) + `SchoolSchedule`
  co-presenza statics (522-554).
- Query checkpoint (run5): join lesson x lesson su (room, timeslot) con gruppi diversi, filtro G14
  -> empty (esenzione non usata).
- Decisione pendente: referente su G14 Confezione (lab vs aula) - gate della (b).
