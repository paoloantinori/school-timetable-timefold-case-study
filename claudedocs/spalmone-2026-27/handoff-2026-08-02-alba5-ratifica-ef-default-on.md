# HANDOFF 2026-08-02 alba5 (zai). RATIFICA ExactFinisher DEFAULT ON + knob 1M

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (commit di ratifica: vedi in
> fondo, push dopo questo handoff). **App UP** (prod, `java -jar`). **DB = A/B run2 finale
> (0hard/-1057977medium/8652soft)** su `planner-data-production`. Container `planner-pg` up
> (db-query.sh intermittente: al primo tentativo puo' dire "Container not running" anche se e' up;
> ritenta). Verdetti dai raw. Regole standing invariate. **maintainer sveglio/attivo** (ha detto SI' alla
> ratifica EF; direttiva addendum: 0-hard BINARIO e BLOCCANTE end-to-end).

## MANDATO - RATIFICA EF (maintainer: "maintainer HA DETTO SI") + ADDENDUM 0-HARD BINARIO. CHIUSO

### Flip del default (codice, TASK-478 AC#5)
- `SchoolSchedule.isExactFinisherEnabled()`: opt-out idiom `String v = ...; return v == null ||
  Boolean.parseBoolean(v);` (null/absent -> TRUE, solo `-Dplanner.exact-finisher.enabled=false`
  disattiva). Byte-identico alle ratifiche sorelle (isCoPresenceEnabled/isCurriculumPostStageEnabled/
  isLabDayConsolidationEnabled). Javadoc ratifica.
- `ExactFinisher.DEFAULT_MAX_CANDIDATES`: 100000 -> **1000000** (catena 10000->100000->1000000).
  Knob `getMaxCandidates()` LIVE invariato (floored a 1).
- `ScheduleService`: banner `refreshSolverStatics` + javadoc `runExactFinisher` + commento
  short-circuit (razionale invertita: l'early-return ora scatta solo con opt-out esplicito).
- Test `ExactFinisherTest`: rimossi 6 `FLAG="true"` ridondanti (default ON via @BeforeEach clear),
  `flagOffIsNoop` ora setta `FLAG="false"` (testa l'opt-out, non-vacuo). Discriminatore lasciato
  com'e' (FLAG="true" defensive, load-bearing).

### ADDENDUM maintainer (0-hard BINARIO e BLOCCANTE end-to-end) - REFRAMING FATTO
- Qualunque hard>0, non importa quanto piccolo (6 buchi / 12.494 lezioni = 0,048%), e' **difetto
  aperto BLOCCANTE** per il path input, **non trade-off accettabile**.
- I 6 H17 bad-seed restano **ORTOGONALI a EF** (attribuzione causale: EF non li crea/non li corregge)
  MA sono bloccanti end-to-end. Cura = **TASK-481** (DRAFT-006 consolidato, prioribilita' High).
- Conseguenza sul framing: EF ratificato chiude la coda boxed (H0) in modo deterministico, ma
  l'hard TOTALE del path input resta **seed-dipendente** (good seed 0, bad seed -6 H17). **Il path
  input NON e' hard-0 end-to-end** finché TASK-481 non cura gli H17. **m-13 resta bloccato
  end-to-end** (EF necessario ma non sufficiente). Corretto in CLAUDE.md RATIFICA, VERDICT A/B,
  QUADRO, diagnosi H17.

### NESSUN cambio al default source (direttiva maintainer esplicita)
- Default source **resta golden**. `source=input` resta opt-in (`-Dplanner.pre-assign.constrained-
  teacher.source=input`). Decisione m-13 (source=input default) ready-when-needed, ma ora bloccata
  anche da TASK-481 (non solo da EF).

## DoD (TASK-478 AC#5, codice solver adottato)
- **test.sh EXIT 0: BUILD SUCCESS, 963/0/16** (stessi conteggi di alba3; il flip default-ON non ha
  rotto nulla: flagOffIsNoop con FLAG=false passa, gli altri EF test passano con default ON, nessun
  test integration regredisce). Log: `tasks/b5abs6jc0.output`.
- **/simplify**: manuale (classifier Skill glm-5-turbo temporaneamente down), diff di 4 file ~94
  linee nette per il resto javadoc/commenti. Verdetto: **nessun finding** (idiom byte-identico alle
  ratifiche sorelle, costante minimale, commenti solo testo, test hygiene pulita).
- **/code-review high**: skeptic Opus CONFIRMED (4/4 sub-claim: idiom byte-identico, knob 1M fluisce
  in `getMaxCandidates`, flagOffIsNoop non-vacuo, 963/0/16). Nessun bug nel diff. **Coverage gap
  onesto (pre-esistente, non introdotto dal diff):** `grep -rln runStagedSolver src/test/` = ZERO,
  nessun test automatizzato esercita il ciclo staged. Quindi la safety production della default-ON
  EF si regge sull'**evidenza campagna** (A/B 2/2 hard-0 + probe 18/18 bad-seed + discriminatore
  uncapped 5/5), non sul test gate. Da tracciare come task di coverage (staged-cycle integration
  test) separato.

## VERDETTO (dai raw)
1. **EF RATIFICATO DEFAULT ON + knob 1M** (maintainer ha detto si'). Chiude la coda boxed (H0) in modo
   deterministico (probe 18/18 + discriminatore 5/5 + A/B 2/2). Opt-out esplicito, banner, test,
   sezione RATIFICA in CLAUDE.md.
2. **Path input NON hard-0 end-to-end**: -6 H17 bad-seed (G11 mercoledi ore 10) bloccanti,
   ortogonali a EF, cura TASK-481. 0-hard e' binario (addendum maintainer).
3. **m-13 resta bloccato** end-to-end (EF necessario, H17 mancano). Default source resta golden.

## STATO RUN / APP / INFRA
- **App UP** (prod, `java -jar`). Il jar in esecuzione e' ancora 15b2e0ec (EF ON via -D espliciti:
  `-Dplanner.exact-finisher.enabled=true -Dplanner.exact-finisher.max-candidates=1000000 +
  source=input`), quindi il comportamento running e' invariato dal flip del default (che conta solo
  senza -D). Per un run "pulito" col nuovo default: rebuild (`./scripts/core/rebuild.sh`) -> il jar
  avra' EF ON di default senza -D.
- **DB = A/B run2 finale (0hard)**. Container `planner-pg` up. caffeinate attivo (m12 wrapper).
- Backlog: TASK-478 (ratifica, da chiudere Done), **TASK-481** (cura H17 bloccante, To Do High,
  canonico: lo ha creato maintainer in 7ee440b2; rebase pulito, il mio task-482 duplicato e' stato
  rimosso e i doc ripuntati a TASK-481), DRAFT-006 rimosso (consolidato in TASK-481).

## PUNTI DI RIPRESA
- Questo handoff + `CLAUDE.md` (sezione RATIFICA TASK-478) + `task478-results/VERDICT-ab-ef-final-
  2026-08-02.md` + `diagnosi-h17-g11-mercoledi-2026-08-02.md` (entrambi riframati bloccante) +
  `QUADRO-2026-08-02.md`.
- Commit di ratifica: vedi `git log` (push dopo skeptic + questo handoff).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`
  (PLANNER_JAVA_OPTS), rebuild prima di run misurati, handoff+/clear a ~450k, mai fermo, DoD
  obbligatoria, **default source resta golden**.
