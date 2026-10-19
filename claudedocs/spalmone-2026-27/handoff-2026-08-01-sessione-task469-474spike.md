# HANDOFF 2026-08-01 (zai, sessione 2) - TASK-469 CHIUSO + TASK-474 SPIKE

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` d3195956 (4 commit puliti,
> tutti pushati). Default solver: golden; source=input opt-in; verdetti dai raw; -D su riga
> java -jar (PLANNER_JAVA_OPTS). Regole standing invariate.

## COSA E' STATO FATTO

1. **TASK-469 PROTOTIPO M-CPS -> CHIUSO (DoD piena + misura).** 3 commit (51471b71 feat,
   fd814481 fix, 985fdb2a docs). Vincolo MEDIUM `M-CPS` flag
   `planner.constraints.curriculum-poststage.enabled` DEFAULT OFF, target 6h asimmetrico
   (deficit x full-weight 100, excess x weak-weight 1) sulle coorti finali (G10/Comportamento
   Sociale, G08/Competenze di cittadinanza + Educazione Civica, G11/Personalizzazione,
   T11). Stage-end = fatto per-gruppo INPUT (max DailyActivityQuota.stageHours>0, pubblicato
   PRE-solve in refreshSolverStatics via SchoolSchedule.lastStageDateFor). 13 test localizzati
   (CurriculumPostStageConstraintTest). DoD: /simplify 4 agent, /code-review high 2 skeptic Opus
   CONFIRMED, test.sh 947/0/0/16 EXIT 0.

   **DUE BUG TROVATI E FIXATI DURANTE LA DoD** (la ragione per cui la DoD paga):
   - **Blind-spot StagePattern** (trovato da /simplify): il join `Lesson x StagePattern` era
     cieco su G10 e G08 in produzione (il seed crea StagePattern solo per G07/G11/
     G12, vedi DataGenerator.generateStagePatterns riga 704). I test passavano perche'
     fornivano StagePattern di fixture. FIX: ridisegnato a static-map da INPUT (DailyActivityQuota).
   - **Case mismatch materia** (trovato dalla MISURA di conferma): i letterali perimetro erano
     lowercase ("Comportamento sociale", dall'incarichi.csv) ma a runtime i nomi materia sono
     Title Case ("Comportamento Sociale", "Educazione Civica") per via di
     SUBJECT_CASE_NORMALIZATION applicata in DataGenerator. Il vincolo era cieco su G10 (0h
     su 20h seed) e sull'Educazione Civica di G08. FIX: normalizzare entrambi i lati
     (precedente QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP): curriculumSubjectSet normalizza i
     letterali, il probe normalizza l.getSubject().

   **MISURA di conferma** (run3/combined-run2, NO new solve, script
   scripts/analyze/curriculum-poststage-kpi.py): post-fix total-curr=20/30/60 (combacia seed),
   post-stage G10 8/12, G08 7, G11 24. Tutti > target 6 (il solver senza M-CPS sparge
   curriculum post-stage oltre le 6h). M-CPS scoreggerebbe -21 (run3) / -25 (combined-run2)
   medium, tutto excess. Vincolo non vacuo sui run reali. PENDENTI: referente T4 (G08 5h vs 6h),
   A/B full-solve AC#4, adozione AC#5 (nessuna senza maintainer).

2. **TASK-474 EXPLAINABILITY -> SPIKE + ESEMPIO H7 (1 commit d3195956).** Primo lotto. Aggiunto
   campo `originKey?` a ConstraintInfo + render in ConstraintCard (blocco "Origine/Origin"
   sotto la descrizione, mirror di example). H7 lavorato end-to-end: origin verbatim
   verificato (extract riga 108). ui-typecheck + ui-build EXIT 0 (frontend-only).

## APPRENDIMENTI RIUTILIZZABILI PER IL FILL-IN TASK-474 (e oltre)

- **SUBJECT_CASE_NORMALIZATION** (SubjectNormalization.java): a runtime le materie sono Title
  Case ("Comportamento Sociale", "Educazione Civica", "Lingua Italiana", "Sviluppo Sostenibile")
  anche se l'incarichi.csv e' lowercase. Le quote verbatim vanno citate come da extract, ma
  quando si referring a materie nel javadoc usare la forma runtime. `normalize()` e' la funzione.
- **StagePattern copre solo 3 gruppi** (G07, G11, G12): per qualunque vincolo
  "post-stage" o "stage-window", NON usare StagePattern come sorgente universale; usare
  DailyActivityQuota.stageHours (copre tutti i 14 gruppi). Stage-end verificati da CSV seed
  (\r stripped): G10 2027-04-23, G08 2027-04-26, G11 2027-03-18.
- **calendario_*.csv hanno line-ending Windows (\r)**: awk `$4=="S"` fallisce se non si strip
  \r. Usare `tr -d '\r'`.
- **sum(l->1) in Timefold 2.3 torna Long** (verificato da skeptic via javap): serve
  `.intValue()` per passarlo a un helper int. count()/countBi() analoghi.
- **penalizesBy(int) in ConstraintVerifier = somma match-weight** (NON match-count): il test
  M-TR penalizesBy(2) su 1 match con weight 2 lo prova. La memory `timefold-penalizesby-
  matchcount` e' imprecisa per questa versione.

## PROSSIMI PASSI

1. **TASK-474 fill-in** (la sessione fresca, ~150 edit): per ogni vincolo (~50, meno H7 gia'
   fatto) aggiungere blocco Origine javadoc + riga constraints.ts originKey + locale en/it.
   Piano + mappa vincolo->fonte (4 categorie A/B/C/D) + formato uniforme in
   `task474-execution-plan-2026-08-01.md`. Procedere per categoria (A verbatim con quote gia'
   pronte nell'extract, B calendario, C golden, D strutturale), commit incrementali per lotto,
   DoD fine blocco: test.sh + ui-typecheck + ui-build + spot-check 5 vincoli. Spike campo
   `origin` + render GIA' fatto (d3195956): la sessione fresca e' fill-in puro.
2. **TASK-476 T1** (opzionale): testare disponibilita' venerdi' G04 (lever verso gate m-13).
   Diagnosi in `task476-diagnosi-input-profile-run3-2026-08-01.md`.
3. **TASK-475**: A1 (LabAssistant(T20,"moda")) confermata dal documento (extract riga 96-97),
   patch pronta nel task come diff; DataGenerator protetto, non applicare senza autorizzazione.

## STATO RUN / APP
- Nessun solve lanciato questa sessione (tutto test.sh + misura read-only). HEAD d3195956 pushato.
- Profilo `production` ha ancora la soluzione run3 persistita (per leggerla: modalita' update,
  NON `delete production`).
- test.sh verde: 947/0/0/16. ui-typecheck + ui-build verdi.

## PUNTI DI RIPRESA
- Questo handoff + TASK-474 execution plan + TASK-469 task (finalSummary).
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar,
  handoff+/clear a ~450k, mai fermo, DoD obbligatoria (simplify + code-review high + test EXIT).
