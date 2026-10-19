# HANDOFF 2026-08-01 (zai) - sessione: RATIFICA 463+464 + programma maintainer 5/6 item

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` 5f22699a (6 commit puliti,
> tutti pushati). Default solver: golden; source=input opt-in; verdetti dai raw; -D su riga
> java -jar (PLANNER_JAVA_OPTS). Regole standing invariate.

## COSA E' STATO FATTO (6 item del programma maintainer, in ordine)

1. **flip m-13 → GATE DI PARITA' (NON ora).** run3 verificato dai raw (6/6 numeri: hard -6 = 3
   unassigned TASK-435, full-day 85,2%, ricorrenza 75,9%, 0 H20/H29/holes). Decisione delegata
   maintainer→Fedora: NON flippare (regredirebbe il riferimento, obiettivo ultimo = 0-hard). Gate
   dichiarato: flip quando un run protocollo-run3 chiude hard 0 E full-day nel range golden.
   Annotato in TASK-452 #5, census #1, VERDICT run3 (header supersession). [commit 42e6c3be]

2. **RATIFICA combined TASK-463+464 → DEFAULT ON.** lab-day-consolidation + co-presenza ospite
   adottati default ON con opt-out (`isCoPresenceEnabled`/`isLabDayConsolidationEnabled` forma
   opt-out, stessa di `isLabFallbackEnabled`). Evidenza: 464 A/B n=2 hard -112→-17/-18, combined
   run3 hard -6. Gate semantica-stanze chiuso da INPUT (Q20 G12 aula normale, Q19 G14
   ospite lab). DoD piena: /simplify clean (4 agent), /code-review high 2 skeptic Opus CONFIRMED
   (0 bug, 6 read-site via helper, default-ON inert a depth-map vuota; 2 commenti stale corretti;
   skeptic B flag → asserzione golden H-COPRESENCE aggiunta a GoldenSolutionRegressionTest),
   test.sh 934/0/0/16skip EXIT 0. CLAUDE.md: sezione RATIFICA + riga H-COPRESENZA.
   [commit db6f7b1f]

3. **DOPPIO R16 (TASK-475) → modellazione preparata.** Design doc + task. A1 patch preparata
   (LabAssistant(T20,"moda"), NON applicata). **CROSS-FINDING TASK-474 prep: l'extract verbatim
   (riga 96-97) CONFERMA A1 dal documento** (T14+T20 entrambi doppio moda, budget
   100/100/85) → non piu' pendente referente. A2 vincolo M budget (riusa join M-LA), A3 fallback.
   [commit 080111a1 + 6172618d]

4. **DIAGNOSI INPUT-PROFILE run3 (TASK-476).** Read-only (senza golden): monte-ore non manca
   (G04/G10 1980h), calendario attende 5h i ven ma solver colloca 3 (deficit solver-side).
   Ipotesi H1 (G04 ven deep = scarsita' docente/lab ven), H2 (G10 -1h = costo T01),
   H3 (3 unassigned = residuo TASK-435). Tuning T1 (rilassare disponibilita' ven G04) = lever
   input-side verso il gate di parita'. [commit 554ce256]

5. **TASK-474 EXPLAINABILITY → SESSIONE FRESCA (valutazione).** Verdetto: ~150-200 edit, formato
   Origine uniforme, DoD pesante. Prep fatto: formato 3-casi + mappa vincolo→fonte in 4 categorie
   (A verbatim con quote pronte nell'extract, B calendario, C golden, D strutturale) + workflow.
   [commit 6172618d]

6. **TASK-469 CURRICULUM post-stage → design + baseline + verbatim (prototipo build-ready).**
   maintainer ha DECISO la semantica (target ~6 asimmetrico: pieno sotto 6, debole oltre 6, cautela
   G11) → framing 'gated referente' superato. Baseline misurata: G10=6h esatto, G08=5h,
   G11=29h. Fonte verbatim extract riga 50. Prototipo deferito a sessione fresca per budget.
   [commit 5f22699a]

## CROSS-FINDING CHIAVE (dalla prep TASK-474, valsa per 2 task)

L'extract verbatim `vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` e' la miniera: quote
pronte per l'explainability di molti vincoli (H7 riga 108, M24/M25 T05 riga 45-46, M26 riga
42/92, M27 riga 98/101, M-TR riga 104-105, ecc.) E ha confermato 2 task:
- **TASK-475 A1**: riga 96-97 conferma T14+T20 doppio moda (budget 100/100/85).
- **TASK-469**: riga 50 e' la fonte esatta ("6 ore dopo stage per curriculum, ANCHE 2 CONSECUTIVVE").

## PROSSIMI PASSI (priorita prossima sessione)

1. **TASK-469 prototipo** (BUILD-READY, spec nel planSet): vincolo M asimmetrico flag default
   OFF, test localizzati, DoD. Primo item.
2. **TASK-474 explainability** (sessione fresca dedicata): eseguire il piano
   (task474-execution-plan-2026-08-01.md), mappa+scrivi ~150 edit.
3. **TASK-476 T1 run**: testare disponibilita' venerdi' G04 (lever verso il gate m-13).
4. (Opzionale) aggiornare il doc referente `domande-rotazione-e-tensioni-referente-2026-07-31.md` con le
   risoluzioni (autoverifica diceva di farlo; i finding sono gia' nei task/docs engineering).

## STATO RUN / APP
- Nessun solve lanciato questa sessione (tutto read-only + test.sh). HEAD 5f22699a pushato.
- Profilo `production` ha ancora la soluzione run3 persistita (per leggerla: modalita' update,
  NON `delete production`).
- test.sh verde: 934/0/0/16skip.

## PUNTI DI RIPRESA
- Questo handoff + TASK-469 planSet (prototipo) + TASK-474 execution plan + TASK-476 diagnosis.
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar,
  handoff+/clear a ~450k, mai fermo, DoD obbligatoria (simplify + code-review high + test EXIT).
