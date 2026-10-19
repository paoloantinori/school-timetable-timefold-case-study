---
id: TASK-686
title: 'STORY.md in inglese: la narrazione del caso'
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:36'
updated_date: '2026-10-17 20:58'
labels:
  - claude-planned
milestone: m-16
dependencies:
  - TASK-683
  - TASK-684
type: docs
ordinal: 334000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il valore del repo come esempio per altri utenti o agenti è la metodologia, ed è già documentata al 90% (ratificazioni in CLAUDE.md, VERDICT per task, campaign-status, skill zero-hard-loop). Serve la distillazione per un esterno, in inglese (decisione di maintainer, 17/10), con la legenda dei pseudonimi in testa. Lingua inglese con citazioni italiane marcate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 STORY.md in inglese copre i cinque archi: problema e golden come oracolo; calibrazione golden-first; pipeline a 5 fasi e catena di consegna fino a 0-hard; metodologia di lavoro agente+umano (snapshot-first, A/B con criteri pre-registrati, puzzle ciechi con arbitrato Java, ratifiche con flag default-ON, bounded-no-op, sanity gate); fallimenti onesti (shell-game, evidenza evaporata, predictor disarmato)
- [x] #2 La legenda dei pseudonimi (T##/G##/R##) apre il documento e nessun nome reale compare
- [x] #3 Le citazioni dalle fonti italiane sono tradotte o marcate come tali
- [x] #4 Ogni capitolo linka i documenti pseudonimizzati del set pubblicato come evidenza
- [x] #5 Verifica zero-PII del censimento (TASK-682) sul documento: pulita
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
STORY.md consegnato in publish-prep/splits/STORY.md (path pubblico nuovo cablato nel manifest via annotazione source:). Cinque archi coperti e substantivi (verdetto reviewer coverage). Legenda pseudonimi in testa con riga spalmone-kept e codici verificati contro la mappa. Correzioni fattuali applicate: H29 'soft'->medium M36 (WRONG del fact-check), 10.542 ore con clausola trappole calendricali, attribuzione round puzzle corretta, 'Twice'->'Repeatedly', GRASP espansa, glosse boxed/basin/sigla, '[-3 hard]' in prosa, CompositionHoleCloser allineato a REJECTED (default-off per mesi POI adottato default-on: contraddizione cross-doc trovata dal reviewer duplicazione e curata), H16->M37/S16 allineato, virgola spuria. 7 link evidenza aggiunti nel cap. 4-5 (PREDIZIONE/VERDICT 613, round-3 report, near-miss 677, sanity script, knobs, skill zero-hard-loop). Gates: /simplify (fact-check 3-angle + round finale 3-angle: 18 fatti duplicati 17 concordi 1 contraddizione curata; sweep finale: 17 link STORY tutti nel manifest, zero PII doppia passata, zero em-dash) + code-review high di chiusura. Zero-PII matcher: NESSUN hit.
<!-- SECTION:FINAL_SUMMARY:END -->
