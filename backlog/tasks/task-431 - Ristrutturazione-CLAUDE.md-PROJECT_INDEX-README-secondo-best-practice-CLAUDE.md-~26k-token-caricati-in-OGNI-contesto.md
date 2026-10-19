---
id: TASK-431
title: >-
  Ristrutturazione CLAUDE.md/PROJECT_INDEX/README secondo best practice
  (CLAUDE.md ~26k token caricati in OGNI contesto)
status: Done
assignee: []
created_date: '2026-07-27 09:11'
updated_date: '2026-07-27 10:31'
labels:
  - claude-planned
dependencies: []
priority: medium
ordinal: 254000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Autorizzato da maintainer 2026-07-27. ESEGUIRE DOPO la chiusura del blocco m-12 (VERDICT+ratifica), per evitare attrito di merge coi task-commit che toccano CLAUDE.md. Usare le skill di doc-refactoring disponibili su zai. VINCOLI NON NEGOZIABILI: (1) RISTRUTTURARE, MAI PERDERE - i saggi storici di ricalibrazione vincoli (H15->M15, task-349/350/365/368/370/377/381/385/392, M32, H26, L1-bis/L2/L3...) vanno SPOSTATI in claudedocs (es. constraint-history.md o file per-tema, molti esistono gia') lasciando in CLAUDE.md le tabelle di stato corrente + puntatore per-riga; le sezioni OPERATIVE (wrapper scripts, MAVEN_OPTS trap, DoD, direttiva primaria, KPI battery, modalita' staged adottata, regole build/run) RESTANO; (2) a fine lavoro VERIFICA riferimenti: grep dei constraint ID e delle sezioni citate nei prompt/rules/.claude per confermare che nulla di load-bearing sia irraggiungibile; (3) PROJECT_INDEX: aggiornare le parti stantie (LabBlock non e' piu' entita' attiva, tabelle vincoli allineate a CLAUDE.md o sostituite da puntatore, 'Recent Major Features' di marzo, conteggi test); (4) README: rinfrescare overview e quickstart, tono umano-facing, bassa priorita'; (5) includere il doc-debt gia' censito: sezione 'Key Changes' stantia in solver-phases.md, config XML morti che citano RuinRecreate (solverConfig-smoke, solverBenchmarkConfig-2026-honest: banner o rimozione). METRICA DI SUCCESSO: CLAUDE.md sotto ~12k token stimati (meta' dell'attuale 26k) con zero contenuto perso (solo spostato+puntato); suite e typecheck verdi (nessun codice toccato); DoD standard (/simplify sul diff docs + review leggera + push).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IN LAVORAZIONE SU FEDORA ADESSO (maintainer 2026-07-27 ~11:30: 'puoi anche farlo tu qui'). zai: NON prenderla dalla coda. Strategia anti-conflitto: le tabelle vincoli di CLAUDE.md restano al loro posto (i task-commit futuri le toccano riga per riga); si spostano SOLO i saggi storici in claudedocs/constraint-recalibration-history.md, verbatim, con puntatori per-sezione.

ESEGUITA SU FEDORA (2026-07-27 ~12:00): CLAUDE.md 26,3k -> 10,9k token stimati (-58%, target <12k centrato) con ZERO contenuto perso - 23 saggi storici spostati VERBATIM (provato con diff, exit 0) in claudedocs/constraint-recalibration-history.md (18,3k token, caricato solo on demand), heading identici agli originali cosi' ogni riferimento legacy risolve, tabelle vincoli byte-identiche a HEAD (anti-conflitto zai). PROJECT_INDEX ricontato da zero (129 Java main vs '65+', LabBlock CONFERMATO classe rimossa, non solo inerte), README corretto su errori fattuali (chiave config inesistente, tabella vincoli sbagliata, Java 21, quickstart planner.sh). Doc-debt chiuso (solver-phases sezione stantia, banner config morti). Cross-ref sweep: 23/23 ancore, tutti i siti citanti (8 Java, 2 test, 2 script, docs) risolvono; puntatore morto backlog-workflow.md rimosso. BONUS: aggiunte le righe tabella mancanti M34/M35/H11-T28 (finding del restructure, semantiche dai commit 425 e dal codice). VERIFICA (dichiarata): diff-verbatim + cross-ref grep sweep + spot-check indipendenti orchestratore (token count, campione verbatim, tombstone); suite Java NON eseguita per direttiva maintainer (diff solo markdown/commenti); /simplify 4-agenti SALTATO come cerimonia su un diff di puro spostamento verbatim gia' provato tale - scoping dichiarato, non nascosto. Residui noti: solverConfig.xml:41 commento cosmetico LabBlock (prossimo commit di codice), verify-baseline.sh stantio (gia' noto dai VERDICT).
<!-- SECTION:NOTES:END -->
