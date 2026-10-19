---
id: TASK-682
title: Censimento PII meccanico del repo (file tracciati + storia git)
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:35'
updated_date: '2026-10-17 10:09'
labels:
  - claude-planned
milestone: m-16
dependencies: []
type: task
ordinal: 330000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
L'analisi del 17/10 ha stimato le classi di PII ma una stima non è un inventario: prima di sostituire serve il censimento completo generato da macchina, con la lista di file per ogni classe e la lista di espulsione dei binari pesanti. Numeri di partenza (da confermare con lo script): nomi docenti in 2.149 file tracciati su 10.077 e in 236 messaggi di commit, 26 email reali in seed/teachers.csv, dominio scuola in 5 file, URL pubblico in 9 file, maintainer in 901 file, referente in 340.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Uno script sotto scripts/ censise le occorrenze su file tracciati E su tutta la storia git (git log e rev-list) per: nomi brevi e completi dei 26 docenti, le 26 email, dominio scuola, sigle gruppi, nomi stanze, URL pubblico, host fedora-box/laptop/edge-box, riferimenti alle persone maintainer e referente
- [x] #2 Il report elenca per categoria: file coinvolti, conteggi, e per la storia git il numero di commit contaminati
- [x] #3 La lista di espulsione copre almeno: i 266 export.json.gz tracciati, le directory di dump sotto claudedocs/spalmone-2026-27 (circa 1 GB tracciato), i 12 PNG, .claude/settings.local.json, e le dir non tracciate (clanner/, claudodocs/ con la typo, staged-checkpoints-*, lifecycle.jsonl)
- [x] #4 Lo script ha una modalità verify che esce non-zero se trova occorrenze, riusabile come gate finale del task di assemblaggio
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Piano (registrato prima dell'implementazione):
1. Script `scripts/publish/pii_census.py`: identificatori derivati a runtime dai seed CSV (fonte unica: teachers.csv per nomi brevi/completi/email, groups.csv per sigle gruppi + codici corso, rooms.csv per stanze) + elenco statico per dominio scuola, URL pubblico, host (fedora-box/edge-box/laptop maiuscolo, niente "laptop" nudo per i falsi positivi), utente maintainer (user/maintainer/maintainer), persone (maintainer; referente trattato a parte per l'overlap col docente T06).
2. Modo censimento: scan contenuti file tracciati (skip binari), scan percorsi tracciati (i nomi file dei task backlog contengono identificatori), messaggi commit, pickaxe per categoria sulla storia; inventario espulsione (json.gz, file >5MB, PNG, settings.local.json, non tracciati); sweep email sconosciute. Report markdown (-o file).
3. Modo verify: `--repo <target>` esce non-zero al primo hit (contenuti + percorsi; `--verify --history` aggiunge la storia); auto-esclusione di scripts/publish/ e publish-prep/ (il censimento non deve contare sé stesso).
4. Self-test su fixture in /tmp: file con identificatori piantati → exit 1; ripulito → exit 0.
5. Run sul repo, report salvato in `publish-prep/census-report-2026-10-17.md` (dir interna, da escludere sempre dal manifest del TASK-684).
6. Gates DoD (/simplify, /code-review high, evidenze) e commit.

Scoperte della ricognizione che allargano il perimetro della 683 (da annotare lì): i NOMI FILE dei task backlog contengono nomi docenti (es. task-245 "Model-T28-lab-exception") e sigle gruppi, quindi la mappa dovrà prevedere anche rename di file; groups.csv porta anche i codici corso MIUR (B285-4-*) da decidere se mappare.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementazione: scripts/publish/pii_census.py (censimento tree + inventario espulsione + sweep email + verify; storia via --history con pickaxe per categoria, ~50s a passata su questo repo). Report in publish-prep/census-report-2026-10-17.md. Auto-esclusione scripts/publish/ e publish-prep/; file >5MB saltati nel contenuto perche' espelli per regola al TASK-684.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-17 09:04
---
Scoperte del censimento tree (oltre le stime), rilevanti per TASK-683/684: (1) 443 file tracciati di robaccia legacy alla radice: backups/snapshot-1261hard.sql e exports/planner-solution-20260224-064019.sql contengono i nomi completi, tasks/ e' una copia vecchia della backlog, laptop.json e' un export 2025-26 da 9,2 MB: tutti candidati espulsione; (2) la dir typo claudodocs/ ha 470 file TRACCIATI (non solo non tracciati); (3) i nomi delle persone stanno DENTRO il codice Java di produzione: ScheduleService.java 29 occorrenze, SchoolScheduleConstraintProvider.java 25 (commenti 'maintainer authorization...'), piu' il filename dell'hook .claude/hooks/golden-before-referente.py; (4) il tema UI 'school' (src/main/webui/src/theme.tsx) e' un riferimento al nome della scuola: da rinominare; (5) i filename dei task backlog contengono nomi docenti e sigle gruppi (es. task-149 T26-T18, task-348 T01): la 683 dovra' prevedere RENAME di file; (6) nessuna email reale oltre le 26 note (lo sweep trovava solo falsi positivi module@version, regex corretta).
---

author: claude
created: 2026-10-17 09:04
---
Self-test verify su fixture /tmp/pii-fixture: albero contaminato -> exit 1 con dettaglio per categoria; albero ripulito -> exit 0 (evidenza nel transcript della sessione). Censimento tree completato: 9778 file letti, 278 binari, 21 file >5MB saltati nel contenuto (candidati espulsione per regola).
---

author: claude
created: 2026-10-17 09:43
---
Gates /simplify (3 reviewer) + /code-review high (6 finding, 3 provati empiricamente dal reviewer) completati. APPLICATI: (a) passegiata unica albero (sweep email fuso in scan_tree, una sola apertura per file, tracked_files una sola volta); (b) pickaxe parallelo (ThreadPool 4) con --regexp-ignore-case: la probe su fixture ha dimostrato che senza il flag la storia dei contenuti perdeva i case-mismatch (il gate sottostimava); (c) percorsi scanditi per TUTTI i file, non solo testo: il reviewer ha PROVATO che un dump >5MB o un PNG col nome nel filename passavano il verify in silenzio; (d) verify ora fallisce anche su file >5MB/binari/non leggibili (gate strutturale: contenuti invisibili al gate testuale non passano in silenzio); (e) status error distinto dai binari; (f) stima referente case-insensitive come la categoria persone; (g) flag --seeds + riga di provenienza in verify (lo script copiato nel clone pubblico non deriva piu' gli identificatori dai pseudonimi in silenzio); (h) .gitignore: staged-checkpoints-*/ (la regola esistente copriva solo il nome esatto), clanner/, lifecycle.jsonl. SCARTATI con motivazione: riuso dei loader scripts/workbook/sources.py (gate tool: lettore tollerante/BOM-safe e indipendenza dal modulo workbook; convenzione locale = lettore inline per script), streaming del log storia (~2MB, memoria non e' il problema), batch du (spazi nei path), flag scoped (?i:) (richiedono Python 3.11), cancellazione della robaccia non tracciata (dati non miei: gitignore, la cancellazione e' decisione del maintainer).
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Consegnato `scripts/publish/pii_census.py` + report definitivo `publish-prep/census-report-2026-10-17.md`. Numeri chiave del censimento (repo privato, 10.077 file tracciati, 9.778 testi scanditi): docenti_cognome 2.302 file / 12.708.519 occorrenze; gruppi 5.604 file; stanze 239; persone (maintainer+referente) 1.043 file, stima referente-referente 1.033 righe; dominio scuola 19 file; URL pubblico 9; host 416; utente maintainer 2.182; docenti legacy (T27/T28/T29/T30/T31, assenti dal roster ma vivi in docs e costanti Java) 155 file. Storia git: 2.567 commit contaminati su 3.941 (unione contenuti+messaggi, pickaxe --regexp-ignore-case). Espulsione censita: 273 .gz tracciati (707,7 MB), 21 file >5MB (790 MB), 12 PNG, .claude/settings.local.json, 443 file legacy alla radice (backups/, exports/, tasks/, laptop.json), 470 file tracciati nella dir typo claudodocs/, dir non tracciate ora in .gitignore. Verify mode provata su fixture: albero contaminato exit 1, pulito exit 0, storia-sola exit 1, big/PNG-con-nome exit 1 (finding F1 della review, provato empiricamente dal reviewer e curato). Equivalenza del refactoring dimostrata: diff dei report pre/post limitato alle sole sezioni attese (storia ignore-case, referente re.I, riga errori), colonne contenuto identiche. Gates: /simplify 3 reviewer + /code-review high 6 finding (5 applicati, 1 conservativo via .gitignore), py_compile OK, censimento EXIT 0; test.sh non applicabile (nessun codice Java/frontend toccato). NOTA per 683/684: i nomi docenti esistono in forma minuscola come identificatori Java in 10+ file (la mappa deve gestire il case), i filename dei task backlog contengono nomi (servono rename), il tema UI 'school' va rinominato, i codici corso MIUR B285-4-* da decidere.
<!-- SECTION:FINAL_SUMMARY:END -->
