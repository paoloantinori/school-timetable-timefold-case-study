# HANDOFF 2026-08-01 (zai, sessione 3) - TASK-474 CHIUSO (explainability vincoli completa)

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` ffc326f6 (5 commit TASK-474,
> tutti pushati). TASK-474 DONE con DoD piena. Default solver: golden; source=input opt-in;
> verdetti dai raw; -D su riga java -jar (PLANNER_JAVA_OPTS). Regole standing invariate.

## COSA E' STATO FATTO

1. **TASK-474 EXPLAINABILITY VINCOLI -> CHIUSO (DoD piena).** Per ognuno dei 62 vincoli registrati
   (`.asConstraint`) aggiunto blocco **Origine** uniforme in 3 artefatti: javadoc IT sul metodo
   builder (SchoolScheduleConstraintProvider + BaseConstraintProvider), `originKey` in
   constraints.ts, campo `origin` in en/it docs.json. 7 nuove card UI per i vincoli recenti non
   ancora sul DocsPage (H-COPRESENCE, M34, M35, M-LF, M-TC, M-TR, M-CPS). Origini classificate
   onestamente in 4 categorie: A workbook verbatim, B calendario per-gruppo, C pratica golden con
   doc di misura, D strutturale. 5 commit: c5cab783 (hard) + 97b0fbab (medium) + 610188b8 (soft) +
   a3adfab5 (fix uniformita' H5) + ffc326f6 (fix fedelta' verbatim).

   **DoD:**
   - /simplify 4 agent (reuse/simplification/efficiency/altitude): 1 finding reale (H5 citava il
     workbook come "school workbook" invece dello standard "Spalmone constraints workbook"),
     fissato. Resto clean/uniforme.
   - /code-review high 2 skeptic Opus: behavior CONFIRMED (0 righe di codice Java modificate,
     provato con comment-strip residue diff); content REFUTED -> **3 bug verbatim reali** che la
     mappa aveva normalizzato e gli agent propagato, fissati e riverificati byte-per-byte contro
     il sorgente (T10 `Govedi'`->`Govedi'` accento + doppi spazi; T20 doppi spazi; T05
     doppio spazio). 1 falso positivo (M-TR `piu'` nell'origin) dismissato con evidenza: l'origin
     M-TR aveva gia' `piu'` corretto, il `piu'` residuo e' nella descrizione prose + un commento
     `//` pre-esistente TASK-468.
   - test.sh 947/0/0/16 EXIT 0 + ui-typecheck EXIT 0 + ui-build EXIT 0 (sullo stato finale).

   **APPRENDIMENTO RIUTILIZZABILE:** le quote verbatim vanno estratte BYTE-EXACT dal sorgente
   fin dal primo giro (la mappa le aveva in parte normalizzate: accenti -> apostrofi, doppi spazi
   -> singoli). Un agente skeptic con hex-dump e' cio' che li ha catturati. Mappa origine (spec
   di trascrizione + spot-check) in `task474-origin-map-2026-08-01.md`, ora autoritativa e corretta.

2. **TASK-475 A1 (patch T20 moda) depositata come diff nel task.** La patch preparata (aggiungere
   `LabAssistant(T20,"moda")` dopo DataGenerator.java:1071) e' ora in `implementationNotes` di
   TASK-475 come diff unificato contestualizzato (prima era solo nel design doc). NON applicata:
   DataGenerator protetto, serve OK di maintainer. Trigger: risposta referente.

3. **TASK-476 T1 -> skippato (nessun tuning flag-gated banale).** T1 e' input-side (rilassare
   disponibilita' venerdi' docenti top G04), richiede una run A/B staged (~49 min) + modifica ai
   dati di disponibilita'. Non esiste un flag banale da flippar(e). Rimane uno spike con i suoi AC.

## STATO RUN / APP
- Nessun solve lanciato questa sessione (tutto test.sh + ui-typecheck/build read-only + review).
- HEAD ffc326f6 pushato. Profilo `production` ha ancora la soluzione run3 persistita (per
  leggerla: modalita' update, NON `delete production`).
- test.sh verde: 947/0/0/16. ui-typecheck + ui-build verdi.

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **TASK-469 (M-CPS) A/B full-solve AC#4 + adozione AC#5** (prototipo flag OFF, misura conferma
   sensata gia' fatta; serve run A/B e poi ratifica maintainer).
2. **TASK-475**: attendere risposta referente (T20 secondo assistente moda: A1 / A1+A2 / A3).
3. **TASK-476 T1**: run A/B run3-base vs run3 + disponibilita' ven G04 (percorso al gate m-13).
4. **Gates milestone**: m-12 (TASK-424 capofila) e m-13 (TASK-452 flip source=input).

## PUNTI DI RIPRESA
- Questo handoff + TASK-474 (completed, finalSummary) + mappa origine
  (`task474-origin-map-2026-08-01.md`).
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar,
  handoff+/clear a ~450k, mai fermo, DoD obbligatoria (simplify + code-review high + test EXIT).
