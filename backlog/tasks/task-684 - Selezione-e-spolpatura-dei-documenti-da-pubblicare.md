---
id: TASK-684
title: Selezione e spolpatura dei documenti da pubblicare
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:36'
updated_date: '2026-10-17 18:53'
labels:
  - claude-planned
milestone: m-16
dependencies:
  - TASK-682
type: docs
ordinal: 332000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
claudodocs pesa 3,4 GB di cui circa 1 GB tracciato, in gran parte dump di stato binari; il case study ha bisogno dei VERDICT/REPORT e dei KPI testuali, non degli stati. Inoltre CLAUDE.md mescola metodologia pubblicabile con ops workstation private. Serve un manifest esplicito e verificabile di cosa entra nel repo pubblico, prima di qualsiasi assemblaggio.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Manifest della pubblicazione: lista esplicita di ciò che si tiene (VERDICT/REPORT e KPI testuali, campaign-status, backlog come archivio, .claude senza settings.local.json) e ciò che si espelle (export.json.gz, checkpoint, PNG con nomi, directory con typo)
- [x] #2 CLAUDE.md è diviso: sezioni metodologiche (direttive, ratificazioni, workflow, tabelle vincoli) tenute; sezioni workstation/personali stripate (heap per macchina, bridge ccgram, nomi host, URL di deploy); PROJECT_INDEX.md allineato
- [x] #3 Nessun file del manifesto contiene dump binari di stato (i KPI testuali sì, gli export.gz no)
- [x] #4 Il manifesto è verificato contro la lista di espulsione del censimento (TASK-682): nessuna sovrapposizione
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Piano eseguito (registrato a posteriori rispetto all'implementazione, onestamente): generatore scripts/publish/pii_manifest.py con regole keep/expel esplicite e verifica incorporata (AC#4), output publish-prep/publish-manifest.txt (2327 file, 21,6 MB); CLAUDE.md split in publish-prep/splits/CLAUDE.md (due tagli: sezione heap workstation, blocco SITO PUBBLICO); PROJECT_INDEX.md verificato pulito e spedito as-is; gates /simplify (3 reviewer) + /code-review high prima del commit.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scoperte e decisioni (17/10): (1) dir AMBIGUE risolte: la dir docs reale e' claudedocs/ (7.497 tracciati), la typo e' claudodocs/ (470 tracciati, duplicata per errore di path): entrambe censite, la typo espulsa interamente. (2) Regole keep: src/** backlog/** scripts/** (escluso scripts/publish, tooling privata con dentro gli identificatori) .claude/** (esclusi settings.local.json, bak, settings.json.d, worktrees [non tracciati comunque], scheduler), claudedocs/**/*.md + claudedocs/spalmone-2026-27/data/** (reference golden testuali), file radice portanti (pom, planner.sh, solver.xsd, README, CONTRIBUTING, DESIGN-537, .gitignore, PROJECT_INDEX.md). Espulsi: dump ovunque (.gz/.png/>5MB), typo dir, robaccia legacy radice (backups/ exports/ tasks/ laptop.json package-lock.json radice PROJECT_INDEX.json stantio CLAUDE-WORKTREE-HANDOFF.md check-rendered-html.cjs dump.sh), local/ com/ io/ ai/ test-results/, docs/ (solo screenshot), publish-prep/ (mai). (3) .github/workflows/benchmark-regression.yml ESCLUSO con motivazione: reference a scripts/autotune/* INESISTENTI nel repo (workflow gia' rotto oggi, verificato con git ls-files); TASK-688 puo' introdurre una CI valida. (4) CLAUDE.md split generato in publish-prep/splits/CLAUDE.md: rimosse la sezione heap-3g-fedora-box (11 righe) e il blocco SITO PUBBLICO; 46 ratifiche intatte, zero residui edge-box/nginx/heap; il file passa dalla mappa pseudonimica come tutto il resto. (5) PROJECT_INDEX.md: verifica grep zero riferimenti workstation, spedito as-is. (6) MANIFEST: 2.327 file, 21,6 MB su 10.093 tracciati, verifica incorporata verde (nessuna sovrapposizione con le classi di espulsione del censimento). (7) VINCOLO SCOPERTO PER TASK-685: le 4 fixture .gz sotto src/test/resources (task542/holes*, task550/roomnull83) sono espulse dalla regola zero-binari, ma PureHoleRepairRegressionTest.inertWhenFlagOff e RoomTailFinisherRegressionTest le caricano SENZA guardia (NPE se assenti): il 685 dovra' aggiungere assume-if-missing ai due load() perche' la suite del clone resti verde.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-17 18:40
---
Gates /simplify round 1 (3 reviewer) applicati: (EFF) nessuna azione (gli 'unreachable branch' di expulsion_violation sono la cintura AC#4 per design, lasciati). (RIUSO) import da pii_census (REPO_ROOT, BIG_FILE_BYTES, tracked_files, fmt_mb, sh eliminata); DUMP_EXT locale PROMOSSO a BINARY_EXT importato (repo pubblico solo-testo: nessun binario ammesso, verificato che oggi zero file .ico/.pdf/.zip/.woff/.ttf sarebbero tenuti). (QUALITA') applicati: (a) registri metodologici piccoli TENUTI via whitelist (cure-registry.json 4,6K, series-families.json 3,2K, doom-prediction-registry/ 24K, post-ls-catalog/index.json 5,6K: citati dal CLAUDE.md kept come tooling vivente); (b) expulsi runbook orchestrazione privata (laptop-send-mandate.sh, master-batch-fedora-offline.sh, laptop-RUNBOOK.md, .claude/skills/laptop-test/) e gli 8 tmp-* di scripts/analyze; (c) PRIVATE_SCRIPTS unica costante (drift is_keep/EXPEL eliminato) + is_keep ora esclude ANCHE EXPEL_FILES/EXPEL_PREFIXES (prima la cintura scattava su file che il filtro ammetteva: runbook e tmp); (d) manifest scritto SOLO se zero violazioni (prima veniva scritto anche in exit-1); (e) check esistenza SPLIT_SOURCES fail-loud (lo split ESISTE: 169.879 byte, il glob del reviewer era flat); (f) header del manifest con grammatica annotazioni e contratto d'ordine pipeline (assembla -> replace con rename -> commit -> verify: i PATH con nomi vengono rinominati da pii_replace, il manifest non ha bisogno di grammatica di rename); (g) NUOVA categoria lan_privata nel censimento + regole lan nella mappa (10.20./30. -> 10.20./10.30., laptop.lan -> laptop.lan: i range LAN del deploy doc erano quasi-identificatori non coperti); (h) test test_lan_private aggiunto (14 test EXIT 0). NON applicati con motivazione: settings.json split aggiuntivo (gli hook .claude restano come esempi di disciplina agente, il rumore graphify-binario-mancante e' documentato non-fatale); scripts/analyze con default-path verso export espulsi (documentano il metodo, falliscono onestamente sull'input mancante); bench fixtures .gz restano espulse (auto-skip dei test self-gating, e sono i manufatti piu' densi di PII: 13k occorrenze per file).
---

author: claude
created: 2026-10-17 18:53
---
Code-review high DI CHIUSURA (7 finding, 4 provati empiricamente dal reviewer): tutti applicati. (F1) il gate finale era cieco sulle varianti t10/t10/T10_dump che il residue del replace cercava: name_form_specs ora genera TUTTE le name_variants e lo start-hump guarda anche '_' e '(' come confine; test aggiunti sulle tre probe del reviewer. (F2) verify ora include le email sconosciute fra gli hit (il sweep era report-only: un indirizzo non-nei-seed passava il gate). (F3) pickaxe_pattern: con git_ere=True salta le tuple (lookaround inespressibile in ERE + literal laptop sovra-contavano machine/macchina, numeri host della storia spazzatura in entrambe le direzioni; pattern vuoto -> pass saltata, colonna n/d); per i messaggi i literal sono ora bracket case-insensitive e la regex e' compilata SENZA re.I (re.I rendeva case-insensitive anche i lookahead: 'machine' matchava la forma caps, provato e curato). (F4) G14/G13 non generano piu' token famiglia (gruppi monoparola: la regola gruppo li copre, la family era morta per sort e il G-code non veniva mai usato); test pin. (F5) docstring doppia fusa. (F6) clausola morta rimossa. (F7) ManifestTest aggiunto (regole keep/expel + cintura AC#4 pinnate). Suite: 16 test EXIT 0. Map rigenerata (151 regole), manifest rigenerato (2.319 file). Report censimento in rigenerazione in background (matcher cambiato: varianti, lan_privata nuova, G14 out dalle famiglie, host corretto): atterra con un commit follow-up.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Consegnati: scripts/publish/pii_manifest.py (generatore con regole keep/expel esplicite e cintura AC#4 incorporata: su violazioni il manifest NON si scrive e exit 1), publish-prep/publish-manifest.txt (2.319 file, 21,6 MB su 10.093 tracciati: da 3,4 GB a 21,6 MB), publish-prep/splits/CLAUDE.md (variante pubblica: sezione heap-fedora-box e blocco SITO PUBBLICO rimossi, 46 ratifiche intatte, zero residui workstation), header del manifest con grammatica annotazioni (CLAUDE.md <- source:splits) e contratto d'ordine pipeline per il TASK-688 (assembla -> replace con rename -> commit -> census verify). PROJECT_INDEX.md verificato privo di riferimenti workstation e spedito as-is. Tenuti oltre ai sorgenti: backlog completo (733 file), 675 .md di claudedocs + data/ reference + 4 registri metodologici piccoli (cure-registry, series-families, doom-prediction-registry, post-ls-catalog/index). Espulsi: tutti i binari ovunque (BINARY_EXT del censimento, incluse le 4 fixture .gz dei bench: i due test senza guardia avranno assume-if-missing nel 685), la dir typo claudodocs/, 443 file legacy radice, runbook di orchestrazione privata, tmp-*, .github (workflow che referenzia script inesistenti). Fix trasversali della review di chiusura (artefatti 683 toccati con nota): matcher con tutte le varianti multi-parola, email sconosciute nel gate, pickaxe ERE-consistente + pattern messaggi case-corretto (re.I avvelenava i lookahead: 'machine' matchava), LAN privata come categoria, G14/G13 fuori dai token famiglia. Suite: 16 test EXIT 0 (nuovi: ManifestTest, varianti t10, lan_privata, pin G14). Gates: /simplify 3 reviewer + /code-review high 7 finding tutti applicati. Report censimento in rigenerazione (commit follow-up all'arrivo).
<!-- SECTION:FINAL_SUMMARY:END -->
