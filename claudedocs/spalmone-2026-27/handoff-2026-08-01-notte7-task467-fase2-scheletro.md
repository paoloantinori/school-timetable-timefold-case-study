# HANDOFF 2026-08-01 notte7 (zai) - SPIKE TASK-467 FASE 2: scheletro enumeratore rotazioni (flag OFF)

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `da5b0e00` (notte 01/08 pushato:
> scheletro `RotationEnumerator` pure-Java dietro flag OFF + test localizzato). **App DOWN invariata**
> (ereditata dalla sessione precedente: orphan "prod fresh reseed source=input" killed; NON tocca il DB).
> **DB corrente = run5 (hard -38) INTATTO** (12494 lessons, 16 unassigned, 2882 pinned). Questo spike NON
> ha usato l'app ne' il DB (lavorato su test/fixture in Java puro). PRIMO STEP ripresa app: `java -jar`
> con `-Dplanner.pre-assign.constrained-teacher.source=input` (legge run5 dal DB, NON fresh reseed).

## COSA E' STATO FATTO (mandato notturno: SPIKE TASK-467 fase 2, l'ultimo del mandato)

**Scheletro enumeratore rotazioni consegnato, dietro flag default OFF, NON cablato in produzione.**
- Codice: `src/main/java/com/school/planner/solver/RotationEnumerator.java` (pure Java, no JPA/DB,
  no Timefold, pattern `WeeklyTemplateBuilder`). Riattiva la FORMA di
  `DayAssignmentCsp.enumeratePairsRecursive` (DFS + MCV + early-abort `MAX_CANDIDATES=10000`) a livello
  per-docente e congiunto, PIU' lo strato timeline (sequenza di periodi con propagazione del budget).
  Lo strato timeline e' il "layer nuovo" del design: l'enumeratore per-periodo viene chiamato
  ricorsivamente sui periodi. (In produzione propagherebbe anche lo stato 0hard via
  `PreAssignmentContext.track/untrack`; quel wiring e' assente: e' scouting, non adozione.)
- Flag: `planner.rotation.enumerator.enabled`, opt-in DEFAULT OFF
  (`Boolean.parseBoolean(System.getProperty(FLAG,"false"))`, pattern DEBUG_MODE/COMPACTION_ONLY righe
  170/180, NON l'opt-out M-LF). `isEnabled()` definito. NON cablato in `HybridPhase1aService` o altrove
  (grep conferma: solo il test lo esercita). Nessuna adozione.
- Test: `src/test/java/com/school/planner/solver/RotationEnumeratorTest.java` (plain JUnit, no
  `@QuarkusTest`, no DB, no solve). Fixture dai soli input reali (incarichi + lab-days + availability +
  schema slot). **7/7 VERIFICATI con exit code:**
  `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS` (0.039s).

### CONSEGNA IN 3 RIGHE (ipotesi / misura / tieni-butta)
1. **Ipotesi (design misurato):** lo spazio delle rotazioni regolari di acconciatura e' enumerabile
   per-periodo (branching per-docente <= 120 < 10^4), riattivando `DayAssignmentCsp` + un layer
   timeline; il gate e' riprodurre dal SOLO INPUT la rotazione strutturale nota (T01 Thu) e
   riportare i candidati per periodo.
2. **Misura (dai raw, jshell su classe compilata + test):** branching per-docente per-periodo
   riprodotto ESATTAMENTE da solo-input = **T01 4 / T19 4 / T18 36 / T26 120**
   (coincide col design sez. 1.3); frammento T01-Thu presente tra i candidati per-periodo (4
   candidati = i 4 gruppi eleggibili); strato timeline riproduce rotazioni valide (budget coperto).
   Congiunto {T01,T19} = 16 (prodotto, nessun weekday condiviso); bound congiunto 4 docenti =
   69120. Prune H3 cross-docente verificato (synthetic: 4 -> 2). `MAX_CANDIDATES` NON fires a livello
   per-docente (max 120). Timeline T01: >=1000 rotazioni valide (`MAX_TIMELINES=1000` cap raggiunto,
   dichiarato non silente).
3. **Tieni/butta:** **TIENI come scheletro flag-OFF** (non adozione). La forma enumeratore e' sound e
   trattabile, il layer timeline riproduce rotazioni valide. ATTENZIONE onesta: la distribuzione
   by-length timeline `{5=2,6=27,7=171,8=800}` e' ARTEFATTO dell'ordinamento DFS (e del cap), NON la
   distribuzione reale; il conteggio timeline e' parametrico (weeksPerPeriod/maxPeriods) e non e' il
   gate metric (il gate e' il branching per-periodo, esatto). La timeline congiunta a 4 docenti e'
   target di produzione (richiede propagazione 0hard `PreAssignmentContext`, out of scope qui).
   **Adozione resta gated** sul go esplicito di maintainer (fase 1 input-driven prima; design sect. 4).

## STATO RUN / APP / INFRA
- **App DOWN invariata** (ereditata). Questo spike non la tocca: lavorato su test/fixture Java puro,
  buildato solo `target/classes` per la misura jshell. Container `planner-pg` PG17 up. DB = run5
  (hard -38) INTATTO (12494 lessons, 16 unassigned, 2882 pinned).
- **PRIMO STEP ripresa app** (invariato dal handoff notte6): `java -jar` con
  `-Dplanner.pre-assign.constrained-teacher.source=input` (legge run5 dal DB esistente; NON serve
  fresh reseed). In alternativa `./planner.sh prod production` (java -jar diretto, PLANNER_JAVA_OPTS
  per il flag, NON MAVEN_OPTS). Container PG17 a regime.

## PROSSIMI PASSI (lavoro aperto, non bloccato; dal handoff notte6, invariati)
1. **run6 portfolio (discriminazione H1/H2)** quando il flip m-13 diventa imminente o in sessione
   dedicata. ~40-60min.
2. **Follow-up cheap (correttezza):** fix javadoc stale `loadGoldenCoPresenceDepth` (309-310) + echo
   H-COPRESENCE (`SchoolScheduleConstraintProvider` 1924-1932). Solo verbo.
3. **Inconsistenza keying depth-map** (spike Trovato 5).
4. **TASK-477 (b) G14 Confezione de-tag -> R10:** dopo chiarimento referente (lab vs aula).
5. **Notifiche referente (TASK-472):** R2 Sicurezza 1a/2gg, R3 T21 mid-day, R1 termo 08-12 vs 8-13.
   Q22 T05-venerdi. Q19 G14 Confezione.

## PUNTI DI RIPRESA
- Questo handoff + `task467-fase2-design-misurato-2026-08-01.md` (design) + QUADRO-2026-07-30.md
  (sezione NOTTE 01-02/08 aggiornata col gate fase2).
- Codice: `src/main/java/com/school/planner/solver/RotationEnumerator.java` +
  `src/test/java/com/school/planner/solver/RotationEnumeratorTest.java`.
- Commit notturno: `da5b0e00` (pushato). Misure raw: jshell su `target/classes`.
- **DoD pesante (`/simplify` + `/code-review high`) NON eseguita su questo scheletro** perche' e'
  flag-OFF non cablato (scouting puro, regole notturne); la verifica meccanica e' il gate test (7/7,
  exit 0). Il DoD pesante e' il gate pre-adozione, da fare SE/QUANDO si cabla in `HybridPhase1aService`.
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su java -jar (PLANNER_JAVA_OPTS),
  poll a finestre (no background pipe), DoD obbligatoria su codice solver adottato, handoff+/clear a ~450k.
