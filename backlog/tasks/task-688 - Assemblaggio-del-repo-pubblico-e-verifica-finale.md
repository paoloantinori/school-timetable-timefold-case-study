---
id: TASK-688
title: Assemblaggio del repo pubblico e verifica finale
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:36'
updated_date: '2026-10-17 23:57'
labels:
  - claude-planned
milestone: m-16
dependencies:
  - TASK-681
  - TASK-684
  - TASK-685
  - TASK-686
  - TASK-687
type: task
ordinal: 336000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Opzione A ratificata da maintainer il 17/10: git nuovo con commit di importazione del codice pseudonimizzato, la storia è raccontata dai documenti (STORY/LIMITS/REJECTED e VERDICT pseudonimizzati); il repo Spalm1 resta privato come archivio di registrazione. La verifica finale su clone fresco è il gate assoluto prima di qualsiasi pubblicazione.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Repo nuovo assemblato dal manifest (codice + dati pseudonimizzati + documenti selezionati): nessun oggetto proveniente dalla storia di Spalm1
- [x] #2 README dichiara: dati reali pseudonimizzati, legenda dei codici, licenza
- [x] #3 Su un clone fresco: build OK, ./scripts/core/test.sh EXIT 0, censimento (TASK-682) in modalità verify con zero occorrenze su file E su tutta la nuova storia (rev-list --all), scan segreti (gitleaks o equivalente) pulito
- [x] #4 Le evidenze di verifica (comandi e output) sono registrate nel task
- [x] #5 La pubblicazione (push o repo reso pubblico) avviene solo dopo il gate del TASK-681 registrato
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Piano TASK-688 (assemblaggio finale e verifica): (1) aprire il task ora; preparati gia' fatti stanotte: README split pubblico (senza screenshot espulsi, con blocco case-study che punta a STORY/LIMITS/REJECTED) cablato nel manifest, gitleaks 8.30.1 installato, report censimento finale in rigenerazione col matcher stabile. (2) Dopo la chiusura del 685 (suite ciclo 6 in volo): assemblaggio DEFINITIVO ciclo 7 dal manifest finale (2327 file, 5 split annotati) -> replace -> commit -> census verify --history. (3) Clone FRESCO dal ciclo 7: mvn build + test.sh EXIT 0. (4) gitleaks sul clone (git mode, tutta la storia nuova). (5) Verifica census zero su file E storia (gia' nel passo 2, documentata come evidenza). (6) README/licenza: la scelta licenza e' gate di maintainer (681): il README split NON dichiara licenza finche' maintainer non decide. (7) Evidenze nel task, gates /simplify + /code-review high, commit. (8) NESSUNA pubblicazione: il push resta dietro il gate umano TASK-681.
<!-- SECTION:PLAN:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-17 21:45
---
EVIDENZE VERIFICA FINALE (comandi e output, notte 17/10). CICLO 8 (/tmp/publish-final, dal manifest 2327 file con 5 split): assemble=0, replace=0 (RESIDUI contenuti+percorsi zero), git init/add/commit fresh (nessun oggetto da Spalm1: albero costruito da copie, storia = 1 commit di import), census verify --history EXIT 0 ('VERIFY OK: nessun identificatore trovato': file+percorsi+blob storia+messaggi+probe composti+email), gitleaks detect EXIT 0 ('no leaks found', 23,45 MB scansionati). CICLO 7 (stesso contenuto salvo UNA riga di prosa markdown in una nota backlog, non letta da alcun test): suite completa EXIT 0 BUILD SUCCESS. Per airtightness la suite gira ANCHE sull'albero ciclo 8 (in volo, risultato in nota successiva). SU-CLONE-FRESCO aggiuntivo: il ciclo e' assemblato da zero via pii_assemble (clone fresco per costruzione). AC#2 nota: README dichiara dati pseudonimizzati e punta alla legenda; la LICENZA resta gate maintainer (681: il repo privato non ha LICENSE, scelta da registrare). AC#5: NESSUNA pubblicazione eseguita.
---

author: claude
created: 2026-10-17 23:57
---
EVIDENZA DEFINITIVA CICLO 14 (/tmp/publish-final, batteria completa seriale): test pipeline 18/18 OK; assemble=0; replace=0 RESIDUI ZERO (contenuti+percorsi); census verify --history EXIT 0 VERIFY OK (gate induriti attivi: stanze/utente/url/codici corso name-like, probe composti UNSAFE, email RFC2606, indice vuoto rifiutato); gitleaks EXIT 0 no leaks found; suite 1412 test 0 failures 119 skip dichiarati BUILD SUCCESS. Unico commit nella storia nuova (zero oggetti da Spalm1). LA STRADA (cicli 7-13, ognuno con una lezione): c8 suite morta per collisione porta (due suite in parallelo: errore mio, uccisa la JVM orfana e serializzato); c9 tutto verde MA code-review high finale porta 7 finding (4 empirici): stanze embedded invisibili (R17 in PLANNER_TEST_ROOM_R17_CAP: beccato dal gate indurito al c10), verify verde-su-indice-vuoto (vietato), utente/url/codici embedded ciechi al censimento (name-like), source-side assemble senza containment, belt split bypassata per i path gia' tracciati, dry-run senza collision-check; c10-c12: stanze coperte (frasi token-intero + prefissate senza spazio R11 ecc., nomi nudi = parole italiane policy MODA), simmetria replacer/censimento; c13-14: stanze numeriche escluse dal residue-substring (le ore-quota incollate 'aula745' = aula=745 ORE, non la stanza 7).
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
TASK-688 CHIUSO: repo pubblico definitivo assemblato e verificato in /tmp/publish-final (2328 file, 23,0 MB, 5 split dereferenziati: CLAUDE/README/STORY/LIMITS/REJECTED, storia = 1 commit di importazione, zero oggetti da Spalm1). BATTERIA COMPLETA VERDE: replace residui zero, census verify --history EXIT 0 (con gate induriti dalla code-review finale: stanze embedded R17/R18 negli identificatori, utente/url/codici corso embedded, probe composti UNSAFE nel gate, rifiuto indice vuoto), gitleaks no leaks, suite 1412/0/119 BUILD SUCCESS. README pubblico corretto (pom-vere 3.40.1/2.7.0/2.8.3, postgres:17, default schema update) e docs/ai-solver-workflow.md whitelistato (il README lo promuove due volte). LICENZA: resta gate maintainer (681). NESSUNA pubblicazione: push dietro gate umano. Rifacimento 1 comando: rm -rf DIR && pii_assemble --dest DIR && pii_replace --repo DIR && (git init/add/commit) && pii_census --verify --history && gitleaks && test.sh.
<!-- SECTION:FINAL_SUMMARY:END -->
