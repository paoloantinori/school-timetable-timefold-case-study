---
id: TASK-440
title: >-
  C4 Pulizia legacy: T28_TEACHER_ID x4 + H11-T28 via, createLabAssistants
  morto (M-LA mai attiva!), backup tracked, doc drift
status: Done
assignee: []
created_date: '2026-07-28 08:23'
updated_date: '2026-08-16 08:50'
labels:
  - claude-planned
milestone: m-13
dependencies: []
ordinal: 263000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C4+E: H11-T28 sostituito da Teacher.getEffectiveMaxDailyHours (gia' data-driven); createLabAssistants VERIFICATO morto -> LabAssistant sempre vuota -> M-LA non scatta mai (decidere se deve vivere: probabilmente seeding da CSV o rimozione della famiglia); rimuovere DataGenerator.java.hardcoded-backup e application.properties.bak (tracked) e la dir spuria src/main/webui/src/main/webui/; refuseIfSolverRunning doppio; 6 doc drift (H22/M29, 266 corsi, 8 Stage, rotazioni, messages H8 e M20).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
FINDING maintainer 2026-07-30 (verificato da lui sul seed attuale): il docente id 14 e' T09, non T01 (che e' id 18), e NESSUN docente ha oggi il flag external attivo. Quindi H11-T28 (il vecchio vincolo che concede 6h/giorno all'id 14 scritto a mano nel codice) punta oggi su una persona per cui quell'eccezione non e' mai stata pensata. Non e' urgente: il limite generale a 6h/giorno (H7, maxDailyHours default 6) morde prima dell'eventuale eccezione id-14, quindi il vincolo e' con ogni probabilita' inerte/mute. Resta un residuo legacy con il bersaglio sbagliato: quando si pulisce questa famiglia (H11-T28 via, T28_TEACHER_ID x4), verificare l'inertiezza e rimuovere, non ripuntare l'id.

## Esecuzione 16/08 (mandato Filone 3)

MECCANICO FATTO:

(1) H11-T28 rimosso: constraint + registrazione x2 (full + compaction-only), filtro di esclusione da H11 (le lezioni del docente 14 ora contano nel cap di gruppo), costanti T28 x4 classi (provider, HybridPhase1aService, ScheduleService, GreedyRotationAssigner), split T28 nella drill-down UI addH11Violations (passa unica, stessa popolazione di H11), javadoc SurplusRebalance (watch-item riscritto come storico), test obsoleto riscritto testH11_BlockValidation_NoTeacherException (6h su gruppo-5 ora REJECT). PROVA MECCANICA: GoldenSolutionRegressionTest assertZeroHard H11 PASSA (l'esclusione non era portante sul golden). Inerzia verificata sul seed: id 14 = T09, nessun flag external, H7 morde prima (finding maintainer 30/07 + census live labassistant=0).

(2) refuseIfSolverRunning duplicato in CalendarioImportService rimosso.

(3) Backup tracked rimossi: DataGenerator.java.hardcoded-backup, application.properties.bak + dir spuria src/main/webui/src/main/webui/ (2 file).

(4) Drift messages: caso H8 morto + builder rimossi; caso M20 ora legge SparseDayJustification (la justification REALE di M20; il vecchio cast a Lesson poteva solo throware -> catch -> messaggio null sempre); bundle en/it aggiornati alla semantica quota ({2} di {3} attese); javadoc SparseDayJustification svecchiato (non piu' 'meno di 4 ore').

(5) Drift CLAUDE.md: H22_RESTRICTED_DAYS_BY_GROUP -> M29_RESTRICTED_DAYS_BY_GROUP; sezione rotazioni ('seeds acconciatura rotations' -> generateLabRotations VUOTA, TASK-364, LabRotation.count()==0, Phase 1/2/3 skippate); riga H11-T28 spostata nella tabella Removed.

(6) Verificato NON-drift: '266 course entries' e' CORRETTO (DB fresco: 274 = 266 + 8 stage; incarichi.csv 295 righe -> 266 corsi dopo aggregazione).

DECISIONALE PER maintainer (NON fatto unilateralmente): createLabAssistants morto (definito, mai chiamato; labassistant = 0 righe sul DB live -> M-LA non scatta MAI). M-LA e' la sede canonica della semantica doppio/assistente (CLAUDE.md): rimuovere la famiglia e' un cambio di modello, seeding da CSV e' rfa workbook. Domanda: M-LA deve vivere? Se si' -> righe LabAssistant da input dichiarato (workbook); se no -> rimozione della famiglia in un task dedicato.

DoD: /simplify + /code-review high in corso; test focali verdi (golden regression + H11 10/10 + formatter 3/3); full suite EXIT 0 da confermare nel commit.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Esecuzione 2026-08-16 (mandato Filone 3), sweep meccanico COMPLETO con doppio round di review:

RIMOZIONI: H11-T28 (constraint + 2 registrazioni + filtro esclusione H11 + costanti x4 classi + split T28 drill-down UI + entry frontend constraints.ts + blocchi i18n h11_T28 en/it); refuseIfSolverRunning duplicato; backup tracked (DataGenerator.java.hardcoded-backup, application.properties.bak); dir spuria src/main/webui/src/main/webui/; caso/builder H8 morto nel formatter.

PROVE: (1) golden regression H11 resta 0 con le 555 lezioni T09 ora conteggiate (Opus: ricomputato da lessons.csv, nessun (gruppo,data) si capovolge; le 5 righe over-cap del sabato-Fasullo non diventano Lesson); (2) H11 strutturalmente inerte sul seed: finestre calendario <= cap per OGNI gruppo, nessun orario H3/H20-compatibile puo' violarla.

FIX POST-REVIEW (bloccante): corretto 'T28 e' id 18' (FALSO: id 18 = T01, che porta la VERA eccezione 8h; T28 non e' nel roster 2026/27) in CLAUDE.md, commento provider, javadoc test. Altri fix: riga REMOVED riposizionata in ordine ID, commenti T28 EXCEPTION orfani rimossi x4, parametro Teacher morto tolto dai 4 wouldExceedDailyLimit (+5 call site +4 test), formatter H7/H11 stessa marciume di M20 corretto (cast a Lesson -> justification records con dailyArgs condiviso; testi bundle en/it a 4 parametri; niente piu' 'group #N' doppio), javadoc SurplusRebalance ('All three').

VERIFICATO NON-DRIFT: '266 course entries' corretto (DB: 274 = 266 + 8 stage).

DECISIONALE PER maintainer (aperto, NON fatto): createLabAssistants morto (mai chiamato, labassistant=0 righe) -> M-LA non scatta mai. Se vive: righe da input workbook; se no: rimozione famiglia in task dedicato (M-LA = sede canonica semantica doppio, cambio modello = decisione maintainer).

DoD: /simplify (finding 1-5 applicati), /code-review high Opus ('correct and safe', block-on risolto), test.sh 1025/0 EXIT 0 + ui-typecheck EXIT 0 + ui-build EXIT 0 (frontend toccato).
<!-- SECTION:FINAL_SUMMARY:END -->
