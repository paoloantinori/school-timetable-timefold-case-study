# HANDOFF 2026-08-01 notte6 (zai) - DIAGNOSI run4/run5 varianza + DoD perimetro + SPIKE TASK-477a

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `9c199cc0` (notte 01/08 pushato:
> code `scripts/analyze/perimetro_residuo_report.py` DoD; docs diagnosis run4/run5, spike 477a, QUADRO
> NOTTE 01-02/08, questo handoff). **App DOWN** (un orphan background "prod fresh reseed source=input"
> di una sessione precedente e' stato killed e ha lasciato l'app giu'; NON tocca il DB). **DB corrente
> = run5 (hard -38) INTATTO** (verificato: 12494 lessons, 16 unassigned, 2882 pinned). PRIMO STEP
> ripresa: riavviare l'app `java -jar` con `-Dplanner.pre-assign.constrained-teacher.source=input`
> (legge run5 dal DB, NON fresh reseed - il volume production e' PG17 a regime e i dati ci sono).
> Default solver: golden; source=input opt-in. Verdetti dai raw. Regole standing invariate.

## COSA E' STATO FATTO (mandato serale 5 punti: 4 CHIUSI + 1 DEFERRED)

1. **(1) DIAGNOSI run4(0) vs run5(-38), catena causale CONFIRMEDA** ->
   `task452-results/run4-run5-variance-diagnosis-2026-08-01.md`. Le 16 H0 = T08/G03/Matematica
   (11) + T19/G06/Barberia (5); 6 H17 gap. **Collocabili** (run4 li ha piazzati: lessons.txt diff
   80->69 e 47->42 = 11 e 5 esatti) -> NON strutturale, e' varianza LS. Divergenza al B-TIMESLOT #1
   (box0 vs box16); demozione max 2 round ACCEPTED as-is. Causa diretta: non-determinismo LS (no
   setRandomSeed sul path produzione, NON_REPRODUCIBLE, `ScheduleService:1302-1331`). Cura esclusa,
   modello ok. Soft link PLAUSIBLE: pre-solve byte-identico (non re-verificato, non blocca).

2. **(2) PROPOSTA (non prototipo, non c'e' cura piccola)**. Leva repair-side medium/low-EV; leva coerente
   = **portfolio multi-start DRAFT-005** (gia' costruito, `-Dplanner.portfolio.size`). Nessun cambio
   modello/cura. **run6 discriminatorio (H1 varianza vs H2 tight-edge) PROGETTATO, NON lanciato**
   (path non bloccante, golden default hard-0, portfolio e' draft da vetare).

3. **(3) DoD recovery `scripts/analyze/perimetro_residuo_report.py` CHIUSA**. /simplify 3 agent ->
   import single-source da `theory_adjacent_residual` (via sys.path.insert come i sibling), rule5
   `tier_line()`, dead accent entry. **Idempotente + behavior-preserving** (md5 identica, diff vs
   committed = solo blocco TIER rule5, R1-R5 byte-exact 78/4/31/43/27). F4 closed-filter inert
   (428 closed sono tutti Fasullo). File modificato: `scripts/analyze/perimetro_residuo_report.py`.

4. **(4a) SPIKE TASK-477a RIDEFINITO (non prototipato)** -> `task477a-g14-h2-exemption-spike-2026-08-01.md`.
   javadoc `loadGoldenCoPresenceDepth` "acconciatura only" e' **STALE** (5 tag depth>=2). G14 profilo
   split: Servizio stilistico (detagged, esenzione non firea) + Confezione (moda, path vivo). **Checkpoint
   run5: esenzione G14 NON USATA** (G14 Confezione sola in R16). **(a) INERTE** sul run corrente;
   la VERA leva Q19 e' la **(b)** (de-tag G14 Confezione -> R10), gate = chiarimento referente
   (lab hands-on vs aula). Inconsistenza keying depth-map registrata.

5. **(4b) TASK-467 fase2 DEFERRED** ("solo se la coda si svuota"): coda non svuotata (4a ricca + chiusura
   obbligatoria). Scheletro enumeratore fase 2 resta per sessione dedicata.

## VERDETTO NOTTE (dai raw)
1. **Il residuo hard del path input e' varianza LS, non modello/cura.** Confermato meccanicamente
   (placement-diff). Leone della notte = diagnosi onesta che sgancia il problema dalla cura e lo
   attribuisce alla ricerca.
2. **Adversarial-verification ha pagato 2 volte:** la placement-diff (run4 piazza i 16) e lo spike 477a
   (javadoc stale + checkpoint esenzione-inerte). Nessuna delle due conclusioni era ovvia a lettura.
3. **DoD debito saldato** (perimetro script). Spike onesto: (a) inert, reindirizza a (b) + referente.

## STATO RUN / APP / INFRA
- **App DOWN** al termine sessione: un orphan background "prod fresh reseed source=input" di una
  sessione precedente e' stato killed e ha lasciato l'app giu'. **Il DB e' INTATTO** (verificato:
  12494 lessons, 16 unassigned, 2882 pinned = run5). PRIMO STEP ripresa: riavviare l'app `java -jar`
  con `-Dplanner.pre-assign.constrained-teacher.source=input` (legge run5 dal DB esistente; NON serve
  fresh reseed, il volume production e' PG17 a regime). In alternativa `./planner.sh prod production`,
  ma e' java -jar diretto (PLANNER_JAVA_OPTS, non MAVEN_OPTS, per il flag source=input).
  Container `planner-pg` PG17 up. DB = run5 (hard -38), 16 unassigned (11 T08/G03/Math + 5
  T19/G06/Barberia).
- Per run nuovo: reset + re-import (2859 pin) + pre-assign source=input (+23 = 2882) + solve-staged.
  Fresh-reseed non necessario (PG17 a regime, seed deterministico).
- `api.sh wait <sec>` staged-aware (esce su stagedStage null); timeout posizionale (`api.sh wait 590`).
  No background pipe con `api.sh wait`.

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **run6 portfolio (discriminazione H1/H2)** quando il flip m-13 diventa imminente o in sessione
   dedicata. ~40-60min. Avvio: rebuild + app `java -jar` con
   `-Dplanner.portfolio.size=2 -Dplanner.pre-assign.constrained-teacher.source=input` + reset +
   import-calendario + pre-assign + solve-staged + capture-run.sh.
2. **Follow-up cheap (correttezza):** fix javadoc stale `loadGoldenCoPresenceDepth` (linea 309-310) +
   echo H-COPRESENCE (`SchoolScheduleConstraintProvider` 1924-1932). Solo verbo, nessun cambio logico.
3. **Inconsistenza keying depth-map** (spike Trovato 5): valutare far keyare per (corso, gruppo) come
   il runtime, non per nome materia.
4. **TASK-477 (b) G14 Confezione de-tag -> R10:** dopo chiarimento referente (lab vs aula). E' il
   vero lever Q19; la (a) non lo risolve.
5. **TASK-467 fase2 (4b deferred):** scheletro enumeratore, gate = riprodurre una rotazione valida.
6. **Notifiche referente (TASK-472):** R2 Sicurezza 1a/2gg, R3 T21 mid-day, R1 termo 08-12 vs 8-13.
   Q22 T05-venerdi. Q19 G14 Confezione (nuova da questo spike).

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/run4-run5-variance-diagnosis-2026-08-01.md` (diagnosi) +
  `task477a-g14-h2-exemption-spike-2026-08-01.md` (spike) + QUADRO sezione NOTTE 01-02/08.
- Perimetro DoD: `scripts/analyze/perimetro_residuo_report.py` (import theory_adjacent_residual).
- Commit notturno: vedi `git log` (perimetro + docs). Pushato.
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS, non MAVEN_OPTS), poll a finestre (no background pipe), DoD obbligatoria su codice
  solver, handoff+/clear a ~450k.
