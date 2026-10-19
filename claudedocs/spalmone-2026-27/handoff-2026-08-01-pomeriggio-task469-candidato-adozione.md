# HANDOFF 2026-08-01 pomeriggio (zai) - TASK-469 CANDIDATO ADOZIONE + TASK-476 SKIPPATO + pivot hard-0

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `1a49f211` (3 commit
> pushati). Programma 3-part della sessione COMPLETATO: (1) A/B 469 fatto, (2) 476 diagnosticato
> + skipped per decisione maintainer, (3) doc work fatto. Pivot deciso: il lavoro si sposta sul
> bloccante hard-0 del gate m-13 (3 unassigned T01). Default solver: golden; source=input
> opt-in. Verdetti dai raw. Regole standing invariate.

## COSA E' STATO FATTO (3 commit pushati)

1. **TASK-469 (M-CPS) A/B full-solve AC#4 FATTO -> CANDIDATO ADOZIONE, resta SI maintainer (AC#5).**
   Run staged source=golden + combined flags ON + M-CPS ON vs baseline `task463-results/combined-run2`
   (M-CPS OFF). Capture `task469-results/ab-cps-on-run1/` + `VERDICT-2026-08-01.md`.
   - **Curriculum post-stage converge verso 6 (con cautela):** G10 12->9, G08 7->**6** (centra
     target), G11 24->21 (cautela: weak-weight non combatte il calendario del corso
     Personalizzazione 60h, come da design asimmetrico maintainer). Verificato independentemente da
     KPI script `curriculum-poststage-kpi.py` + query DB (9/6/21 concordano).
   - **Costo ZERO:** hard 0 (=baseline), full-day 90,8% (+0,4pp), ricorrenza unpinned 75,6% (FLAT),
     buchi 0, medium neutro (-963616 vs -994722, +31k entro varianza non claimato come miglioramento).
   - n=1: per adozione n>=2 solo per escludere varianza medium/full-day (effetto curriculum gia'
     meccanicamente confermato, non stocastico). Dichiarazione solo, nessuna adozione.

2. **TASK-476 T1 DIAGNOSTICATO + SKIPPATO (decisione maintainer 01/08 pm).** Diagnosi read-only (su
   profilo production mentre girava il 469): il target pulito non-circolare e' solo **T05**
   (Matematica 70h, "Venerdi libero a casa" = input dichiarato); **T24** e' Q22 golden-derived
   (circolare, rilassarlo replica il golden); i docenti top G04 ad alto monte-ore (T15
   lab 222h, T14 90h, T25 70h, T21 60h) sono **già disponibili il venerdi**.
   Rilassare T05 e' un controfattuale **school-gated** (la cura richiede che la scuola renda T05
   disponibile venerdi) E tocca solo il full-day, non il bloccante hard-0. Decisione maintainer: SKIP
   del run (~50min risparmiati). **T05-venerdi flaggato a referente come Q29** (`domande-brevi.md`:
   vincolo reale o preferenza?). AC#1 risolto come DIAGNOSTICATO. Dettaglio: commento #1 e #2 in
   TASK-476; doc diagnosi `task476-input-profile-diagnosis-run3-2026-08-01.md`.

3. **DOC WORK (tempi morti durante il 469).**
   - `tasks/lessons.md`: due lezioni nuove. (a) **JAR-stale banner**: prima di un solve misurato,
     rebuild SEMPRE + verifica che il banner dei flag attivi combaci CLAUDE.md (un JAR buildato
     prima di `db6f7b1f` riportava 463/464 default OFF; i flag espliciti del run precedente sono
     un tell). (b) **Verbatim byte-exact**: le quote verbatim vanno estratte byte-exact (hex-dump)
     al primo giro, mai da una mappa normalizzata (lezione TASK-474 skeptic).
   - `QUADRO-2026-07-30.md`: sezione **POMERIGGIO 01/08** (ratifica 463+464, 474 explainability,
     469 candidato, 452/473 stato, gate m-13 + skip 476).
   - Serie storica KPI NON aggiornata: il 469 e' n=1 candidato (non riferimento), non merita entry.

## PIVOT: bloccante hard-0 del gate m-13 (prossimo focus, NON ancora iniziato)

Il gate m-13 (TASK-452 flip source=input) richiede **hard 0 + full-day nel range golden**. run3
source=input = hard -6 + full-day 85,2%. **Il bloccante hard e' identificato con precisione:**
i 3 unassigned sono **G10 / T01 / "Servizio tecnico e stilistico"** (unpinned, timeslot
null, verificato dal run3 export). E' il residuo atteso del constrained-teacher TASK-435 ("ne
restano 3"): le ore di T01 solo-giovedi non trovano cella legale sotto source=input.
**G10 e' doppiamente colpito da T01**: i 3 unassigned (hard) E il -1h full-day spread
(H2 medium, costo concentrazione giovedi). Entrambi risalgono al vincolo solo-giovedi.

Cura candidata (da investigare, NON banale): profilo T01 constrained-teacher meno aggressivo
(distribuire le 231h sui giovedi disponibili cosi tutte collocano), area TASK-435 / TASK-452
patch #1 (input-derived profile). La patch #1 di TASK-452 (source=input knob, default golden) e'
gia' pronta e byte-identica al default: il profilo input-derived front-loada 8h/giovedi e potrebbe
essere la leva (o parte del problema). Da verificare con run source=input post-patch.

## STATO RUN / APP
- App UP su profilo `production`, java -jar con flag espliciti `-Dplanner.constraints.curriculum-
  poststage.enabled=true -Dplanner.co-presenza.enabled=true -Dplanner.construction.lab-day-
  consolidation.enabled=true` (PID nel log /tmp/planner-prod.log). La soluzione in DB e' il run
  469 (source=golden, hard 0, M-CPS ON) NON run3 (run3 source=input e' stato sovrascritto).
- Per leggere run3 source=input: capture in `task452-results/verify-source-input-run3/` (export,
    lessons, analysis, VERDICT). Per un run source=input nuovo: stop+delete production, restart con
    `PLANNER_JAVA_OPTS` incluso `-Dplanner.pre-assign.constrained-teacher.source=input`.
- test.sh NON rilanciato questa sessione (nessun codice toccato: solo flag + dati + docs).

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **Hard-0 m-13 (FOCUS)**: chuidere i 3 unassigned G10/T01. Prima diagnosi: perche'
   source=input lascia 3 ore T01 senza cella (profilo input-derived front-load 8h/giovedi vs
   golden 6h sparsi? slot/calendario G10 giovedi?). Poi cura nel profilo constrained-teacher
   (TASK-435/452 patch #1). Gate = hard 0 su source=input.
2. **TASK-469 adozione**: n>=2 M-CPS ON per escludere varianza medium/full-day, poi SI maintainer per
   flip default OFF->ON.
3. **TASK-475**: attendere risposta referente (T20 secondo assistente moda: A1/A1+A2/A3).
4. **Q29 T05-venerdi**: attendere risposta referente (vincolo reale o preferenza?).

## PUNTI DI RIPRESA
- Questo handoff + `task469-results/ab-cps-on-run1/VERDICT-2026-08-01.md` (469) + commenti TASK-476
  (476 skip) + `QUADRO-2026-07-30.md` sezione POMERIGGIO 01/08.
- Per l'hard-0: run3 capture `task452-results/verify-source-input-run3/` (i 3 unassigned =
  G10/T01/Servizio tecnico e stilistico), TASK-452 patch #1 (notes) + TASK-435 (constrained-
  teacher), diagnosi TASK-476 H3.
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS, non MAVEN_OPTS) + rebuild prima di run misurati (lezione JAR-stale), handoff+
  /clear a ~450k, mai fermo, DoD obbligatoria su codice (questa sessione: solo flag+dati+docs, niente
  DoD codice).
