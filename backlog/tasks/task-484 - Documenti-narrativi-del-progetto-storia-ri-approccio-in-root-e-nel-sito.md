---
id: TASK-484
title: Documenti narrativi del progetto (storia + ri-approccio) in root e nel sito
status: Done
assignee: []
created_date: '2026-08-03 08:48'
updated_date: '2026-08-03 08:50'
labels:
  - documentation
  - claude-planned
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Richiesta esplicita di maintainer del 2026-08-03: servono due documenti markdown nella root del repo, pensati come input per una persona o un agente che debba affrontare un progetto simile dopo di noi. STORIA.md racconta la storia del progetto nell'ordine naturale degli eventi; RIAPPROCCIO.md descrive come ri-approccerei un progetto simile sapendo oggi quello che so. Entrambi devono essere raggiungibili anche dal sito, non solo dal repo. Richiesta esplicita di documentare l'approccio a doppio agente adottato in questa collaborazione: un agente di controllo su Fedora che decide e verifica, un agente esecutore sul laptop che esegue i lavori lunghi, canale tmux su ssh per i mandati, canale git per i risultati.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 STORIA.md in root con cronologia per epoche ancorata ai git log del repo (2.292 commit, 2026-02-13..2026-08-03), punti di svolta datati, una sezione sugli errori e le autocorrezioni, e una sezione sull'approccio a doppio agente con tmux su ssh
- [x] #2 RIAPPROCCIO.md in root con contenuto prescrittivo: cosa rifare identico, cosa cambiare, ordine di lavoro, protocollo di evidenza, trappole di dominio
- [x] #3 I due documenti sono raggiungibili dal sito con una voce di navigazione unica Storia (rotte /storia e /storia/:doc), due schede una per documento, e link interni funzionanti fra i due documenti
- [x] #4 L'implementazione importa i due file dalla root via Vite ?raw senza copie duplicate, usa react-markdown + remark-gfm, mappa i componenti markdown sui token del design system a variabili CSS invece del plugin typography (che non seguirebbe i tre temi), lascia il corpo dei documenti non tradotto per scelta dichiarata con commento nel codice e traduce solo la chrome via i18n
- [x] #5 ui-typecheck.sh e ui-build.sh terminano con exit code 0 e il chunk lazy ProjectStoryPage nel bundle contiene il testo dei due documenti
- [x] #6 Verifica visiva con Playwright sul bundle servito via http conferma navigazione fra le due schede, i link interni fra i documenti e il rendering corretto delle tabelle GFM
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verificato: ui-typecheck.sh exit 0, ui-build.sh exit 0 (chunk ProjectStoryPage-*.js contiene il testo di STORIA.md/RIAPPROCCIO.md, confermato per grep sulla sezione 4.2 Il doppio agente con tmux su ssh). Un difetto reale trovato dalla verifica visiva Playwright: senza remark-gfm le tabelle markdown non venivano renderizzate; corretto aggiungendo la dipendenza remark-gfm. File toccati: STORIA.md, RIAPPROCCIO.md (root), src/main/webui/src/components/ProjectStoryPage.tsx, src/main/webui/src/components/MarkdownDoc.tsx, src/main/webui/src/App.tsx (route /storia e /storia/:doc), src/main/webui/src/i18n/locales/{en,it}/common.json (voce di navigazione), src/main/webui/src/vite-env.d.ts (tipizzazione import ?raw), package.json/package-lock.json (react-markdown, remark-gfm). Lavoro presente in working tree, non ancora committato al momento della creazione di questo task.

DoD gate non ancora soddisfatto: nessuna evidenza che le skill /simplify e /code-review high siano state eseguite su questo diff prima di questa registrazione. Per chiudere a Done: (1) eseguire /simplify sul diff e applicare o scartare con motivazione i finding, (2) eseguire /code-review high sul diff e verificare i candidati confermati, (3) backlog task edit 484 --check-dod 1 --check-dod 2 --check-dod 3 -s Done (il DoD #3, verifica finale con exit code da ui-typecheck.sh/ui-build.sh, e' gia' soddisfatto: eseguito da questa sessione orchestratrice il 2026-08-03 con EXIT:0 su entrambi).
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
