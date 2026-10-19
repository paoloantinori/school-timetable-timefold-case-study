---
id: TASK-683
title: Mappa di pseudonimizzazione unica e script di sostituzione
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:35'
updated_date: '2026-10-17 13:29'
labels:
  - claude-planned
milestone: m-16
dependencies:
  - TASK-682
type: task
ordinal: 331000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La correttezza della de-identificazione è la coerenza: la stessa mappa applicata ovunque (seed, golden, DataGenerator, test, documenti, backlog) è ciò che tiene insieme la suite e la leggibilità della storia. Sostituzioni sparse rompono i test che pinnano i dati reali e lasciano la prosa senza referenti. Fonte della lista nomi: seed/teachers.csv (26 docenti) e l'inventario del TASK-682.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Una singola mappa (file dati + legenda) definisce: docenti T01..T26 (forme breve e nome completo), gruppi G01..G14, stanze R01..R18, dominio scuola sostituito, URL pubblico e host sostituiti con generici, maintainer sostituito con il maintainer, referente con il referente scuola; lo pseudo-docente Stage resta com'è
- [x] #2 Le date restano reali (decisione 17/10: si tolgono gli identificatori diretti, nessuno shift, nessun seed sintetico)
- [x] #3 Uno script applica la mappa ai file di testo tracciati con modalità dry-run e report di ciò che toccherebbe
- [x] #4 Lo script di censimento (TASK-682) in modalità verify su un albero sostituito riporta zero occorrenze per ogni identificatore originale
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Piano (post-ricerca sul codice reale, registrato prima dell'implementazione):

SCOPE DISCOVERY (probe del 17/10, decide il design): i cognomi esistono in tre forme che i word-boundary NON prendono: (1) camelCase in identificatori Java ("pinnedT01", "printT08", "corruptT11", ~35 occorrenze), (2) costanti snake ALL-CAPS ("T01_BUDGETS", "T20_EARLY_SUBJECTS": il carattere '_' e' un word-char quindi \b fallisce DENTRO l'identificatore), (3) forme danneggiate da edit precedenti ("baT01", "oxedT01"). Questo significa che sia il conteggio del censimento sia un replacer \b-only lascerebbero nomi reali nel clone con VERIFY OK falso. Il matcher va esteso e CONDIVISO fra censimento e replacer.

1. Matcher unico in pii_census.py: per le regole nome-like (docenti, legacy, persone) tre forme: standalone \b..\b case-insensitive; camel (?<=[a-z])S(?![a-z]) (evita "Serratura"); costante S_UPPER embedded ((?<=^|[_A-Z]) con lookahead [_A-Z(]|$). Per dominio/url/host/user restano le \b attuali. Il censimento guosta potere di rilevazione (i conteggi tree saliranno di poco; la storia pickaxe e' gia' a substring quindi non cambia).
2. Mappa publish-prep/pseudonym-map.json (privata, mai nel publish): assegnazione deterministica T01..T26 alfabetica per cognome breve, T27..T31 per i 5 legacy, G01..G14 nell'ordine del seed, codici famiglia FA..FG per i token standalone (FB/FA/FD/MODA/FC/G13/G14: appaiono in prosa e nei filename tipo calendario_fc.csv rinominati di conseguenza), R01..R18 stanze, C01..C14 codici corso MIUR B285-4-*, email -> t##@example.org, dominio -> example.org, URL pubblico -> https://schedule.example.org, host fedora-box->fedora-box / edge-box->edge-box / laptop->laptop, utente user->user / maintainer->maintainer-github / maintainer->(drop: cognome-> "the maintainer"), maintainer->maintainer, referente->referente (dopo fullname T06, ordine longest-first), "the school"->"the school", school->school (tema UI), frase composta prima delle regole parola. Stage e Fasullo restano. Date reali (nessuna regola).
3. scripts/publish/pii_replace.py: importa identificatori+matcher da pii_census; applica longest-first con le tre forme; rinomina i FILE il cui nome contiene identificatori (task backlog, calendario_fc.csv, golden-before-referente.py hook, calendario_moda.csv) aggiornando i riferimenti nel contenuto; modalita' --dry-run con report per regola (file, occorrenze, rename previsti); skip binari/>5MB/SELF_EXCLUDE; a fine applicazione lancia lo sweep residui substring case-insensitive con allowlist esplicita delle parole italiane legittime che contengono un cognome (Serratura e simili, censite dalla probe).
4. Legenda pubblica generata dalla mappa DROPPANDO gli originali (publish-prep/pseudonym-legend-public.md: solo codici e ruolo generico).
5. Prova su fixture dedicata con i casi ostili veri (camelCase, ALLCAPS snake, Serratura come parola legittima, fullname referente, email, URL, filename con nome): apply -> census --verify exit 0 + residui allowlist-only.
6. Rigenerare il census report (matcher esteso) e chiudere con gates (/simplify, /code-review high) + commit.
<!-- SECTION:PLAN:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-17 10:20
---
Avanzamento implementazione: (1) matcher multi-forma in pii_census (NAME_LIKE_CATS + name_form_specs: standalone ogni casing, camel (?<=[a-z_])Cap(?![a-z]), costante ALL-CAPS embedded, minuscolo embedded SOLO per cognomi distintivi: UNSAFE_EMBED = t20/t24/t28/t19/t05/t27/t29/t30/g14 perche' sottosequenze di parole legittime come serratura/difettosi/majority/t19; unit test 11/11 sui casi ostili veri). (2) pii_map_gen.py: 150 regole deterministiche (T01..T26 alfabetici, T27..T31 legacy, G01..G14, FA..FF famiglie escludendo MODA che e' parola italiana, R01..R18, C01..C14, statici). Policy nella mappa: date reali, moda standalone non mappata, spalmone tenuto, Stage/Fasullo tenuti. Legenda pubblica generata senza originali. (3) pii_replace.py: contenuto (UTF-8 rigoroso, CRLF preservati, non-UTF8 rifiatati e reportati), rename bottom-up con abort su collisioni, residue sweep substring con allowlist. (4) Fix asimmetria scoperta dal fixture: il gate --history NON usa piu' il pickaxe (substring senza boundary: 'difettosi' contiene 't24' e facebbe scattare il gate con albero pulito) ma scan_history_blobs con lo STESSO matcher dell'albero su tutti i blob via cat-file --batch. (5) Fixture ostile end-to-end VERDE: dry-run coerente, apply con rename (laptop-RUNBOOK->laptop-RUNBOOK con riferimenti contenuti allineati, calendario_fc->calendario_FC, golden-before-referente->golden-before-referente, task-T01->task-T01), residui zero (serratura/difettosi/majority preservate), census --verify --history EXIT 0. In volo: rigenerazione report censimento col matcher esteso + gates /simplify.
---

author: claude
created: 2026-10-17 10:28
---
Finding del reviewer RIUSO applicati (tutti verificati con fixture): (F1, CRITICO) la policy MODA viveva solo nella mappa: il censimento cercava ancora \bMODA\b e il gate --verify NON avrebbe MAI passato il clone (ogni 'moda' legittimo flaggato). Fix: FAMILY_EXCLUDED in pii_census (fonte unica importata da map_gen), gruppi_famiglia ora name-like con MODA esclusa. (F2+fixture) 'maintainer' minuscolo sfuggiva a replace (case-sensitive) E censimento: VERIFY OK falso col cognome dentro. Fix: regola case-insensitive ovunque (cognome distintivo, zero collisioni). Probe empirica: il residuo ha intercettato il buco, poi fix e retest verde. (F3/F4) consolidamento: LEGACY_TEACHERS, FAMILY_EXCLUDED, UNSAFE_EMBED/EMBED_ALLOWED_WORDS, SEED_DIR, SELF_EXCLUDE ora vivono in pii_census e sono importati (una sola fonte per 'cosa sono gli identificatori'); host laptop/laptop allineato fra censimento e replace; map_gen legge teachers.csv una volta sola. Curiosita' del percorso: l'hook golden-before-referente.py ha bloccato un comando perche' il fixture conteneva la frase 'Chiedere a referente' (pattern dell'hook); riformulata la riga del fixture, l'hook resta intatto.
---

author: claude
created: 2026-10-17 10:45
---
Finding del reviewer QUALITA' applicati (con istanze vive verificate dal reviewer nel repo): (HIGH-1) cognomi multi-parola senza varianti concatenate: 'FCT10'/'T10 PROFILE DUMP' (H17BlockSwapTest, ConstrainedTeacherInputSourceRealSeedTest, decine di log) passavano OGNI gate. Fix: name_form_specs genera varianti senza spazio (T10/T10) + name_variants per residue/gate/paths (anche _ e -). (HIGH-2) branch ALL-CAPS senza guardia corrompeva 'stage-MAJORITY' in 'MAT28TY' (WeeklyTemplatePlanner.java:480): fix con guardia anti-caps-run (?<![A-Z])UP(?![A-Z]) sui due lati, mantenendo T20_EARLY_SUBJECTS sostituito correttamente. (start-hump, scoperto dal mio fixture) token minuscolo a inizio identificatore seguito da maiuscola ('FCQuota', 'FCT10'): sfuggiva a sostituzione E censimento; aggiunta la forma (?<![A-Za-z0-9])lower(?=[A-Z]). (M3) replace_path ora usa le stesse forme del contenuto (il filename 'spike-g06-barberia-t19' non corrompe barberia) + nuovo path_residue post-rename. (M4) offset codici legacy derivato da len(teachers) (niente collisione T27 se il roster cresce); guardia sui fam_codes. (M5) scan_history_blobs: kill del processo git sugli errori (hang permanente altrimenti), returncode check; verify --history ora include ANCHE i messaggi commit (scan_commit_messages estratto e riusato). (M6) il target NON puo' contenere publish-prep/ o scripts/publish/ (hard fail nel replace, structural fail nel census verify su repo != privato): la mappa privata non puo' finire nel clone passando i gate in silenzio. (LOW) guardia commonpath sul --repo, t19/renna tolti dall'allowlist (voci morte), barberia AGGIUNTO (materia/parola italiana), allowlist per contenimento (stageMAJORITY contiene majority), assert build_rules nessun originale e' substring di una sostituzione, collisioni rename rilevate anche in dry-run, docstring su non-atomicita' e semantica exit del dry-run, G14 policy documentata (mappato standalone, 'g14' matita accettato come rumore), gruppi come name-like con varianti senza spazio ('g06' nei filename -> G06).
---

author: claude
created: 2026-10-17 10:59
---
Code-review high DI CHIUSURA (10 finding, 6 provati end-to-end dal reviewer su fixture-cloni): tutti applicati. (F1) '(?:laptop|laptop)' rompeva wrapped() e build_gate() (gate VERDE con 'laptop' nel clone, 'MacBook' contato come host): host ora come literal case-sensitive laptop/laptop/laptop + forme camel/caps esplicite, e wrapped()/build_gate() trattano i pattern che iniziano con '(' come regex verbatim. (F2) prescreen 'original not in low' saltava la regola sui file con solo varianti (T10 senza 'T10'): ora il prescreen usa TUTTE le varianti della regola. (F3) rename a singola fase: il file spostato nella dir trasformata faceva collidere il rename della dir (abort a meta' apply): rename_pass riscritto a DUE FASI (directory profondita'-prima sul basename, poi file sui percorsi risolti). (F4) verify --history usava il substring del pickaxe sui messaggi (clone pulito falliva per 'serratura' nel messaggio): nuova scan_commit_messages_matching col matcher condiviso. (F5) casing misti ('T01', 'T10') invisibili: standalone ora a bracket-expansion case-insensitive (una forma che copre tutti i casing). (F6) blob storici senza cap: chunked-drain oltre i 5MB + conteggio separato come structural failure. (F7) report stantio: rigenerazione definitiva in volo col matcher finale. (F8) CASE_SENSITIVE_KEYS morto rimosso. (F9) storia: reporting separato blob-contaminati vs commit-con-messaggi (era '401 commit' per 400 blob + 1 messaggio). (F10) rete di regressione COMMITTATA: scripts/publish/test_pii_pipeline.py (14 test: matcher forme ostili, gate superset, mappa deterministica, replace end-to-end con rename a due fasi, residue su leak piantato, host escluso dal substring, census verify verde/rosso su git repo vero) -> python3 scripts/publish/test_pii_pipeline.py EXIT 0.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Consegnati: publish-prep/pseudonym-map.json (150 regole deterministiche, PRIVATA), publish-prep/pseudonym-legend-public.md (senza originali, pubblicabile), scripts/publish/pii_map_gen.py (generatore), scripts/publish/pii_replace.py (applicatore: contenuto UTF-8 rigoroso con CRLF preservati, prescreen su varianti, rename a due fasi directory-poi-file, residui contenuti+percorsi con allowlist), matcher multi-forma condiviso in pii_census.py (standalone case-insensitive a bracket-expansion che copre i casing misti, camel, caps embedded con guardia anti-caps-run, start-hump, lower-embedded per soli cognomi distintivi, varianti multi-parola T10/T10/t10), gate superset, scan_history_blobs con cap+kill, verify storico su matcher condiviso (blob e messaggi separati). Rete di regressione COMMessa: scripts/publish/test_pii_pipeline.py, 14 test, EXIT 0 (matcher ostile, gate superset, mappa deterministica, replace end-to-end con dir rename, residue su leak, host escluso dal substring, census verify verde/rosso su git vero). Report censimento rigenerato col matcher finale: docenti 2.307 file/12,7M occorrenze, host 701 file (laptop/laptop/laptop), unione 2.525 commit contaminati. Gates: /simplify 3 reviewer (riuso/qualita'/efficienza: consolidamento fonti uniche, gate superset, alternanza residue) + /code-review high 10 finding TUTTI applicati (6 provati end-to-end dal reviewer: gate cieco su laptop, prescreen varianti, collisione rename dir/file, substring sui messaggi, casing misti, blob oversize). Policy dichiarate nella mappa: date reali, MODA non mappata standalone (parola italiana), spalmone/Stage/Fasullo tenuti, G14 mappato standalone col rumore 'g14=matita' accettato. Nota: 'laptop address' in testi di rete diventa 'laptop address' come rumore semantico accettato.
<!-- SECTION:FINAL_SUMMARY:END -->
