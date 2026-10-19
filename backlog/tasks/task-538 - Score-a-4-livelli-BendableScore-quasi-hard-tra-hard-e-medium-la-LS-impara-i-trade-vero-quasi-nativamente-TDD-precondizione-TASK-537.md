---
id: TASK-538
title: >-
  Score a 4 livelli (BendableScore): quasi-hard tra hard e medium - la LS impara
  i trade vero/quasi nativamente (TDD, precondizione TASK-537)
status: Done
assignee: []
created_date: '2026-08-20 11:39'
updated_date: '2026-09-02 08:59'
labels:
  - claude-planned
  - architecture
  - score
  - design
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Idea maintainer 20/08 ("pazza idea"): score a 4 LIVELLI con il QUASI-HARD tra hard e medium, per dare al solver la gerarchia vero/quasi che il golden pratica (TASK-537 per la tabella). Candidato nativo: BendableScore di Timefold (livelli lessicografici configurabili), da verificare sui docs per ergonomia coi constraint stream; alternativa CustomScore se bendable non copre. COSA COMPRA: la LS accetterebbe NATIVAMENTE i trade al margine (chiudere un vero-hard pagando un quasi entro budget golden) che oggi i guard improve-or-noop impediscono; gate e pick-best diventano onesti per costruzione (stop al primo 0-veri-e-quasi<=budget); badge UI onesto. COSTO: il refactor piu' invasivo dai TASK-228/267: score type in solverConfig + 62+ constraint declarations + timeslotTierHardScore + gate + pick-best + test + UI + KPI parser. APPROCCIO: TDD (direttiva maintainer): ogni consumatore parte dal test che pinna il comportamento a 4 livelli. PRECONDIZIONE: semantica validata da TASK-537 sui run reali.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Verifica API sui docs Timefold correnti: BendableScore con 2 hard-level (veri, quasi) + 2 soft-level (medium, soft): forma dei penalize nei constraint stream, supporto config XML, benchmarking
- [ ] #2 Design: migrazione di tutti i consumatori dello score (62+ constraint declarations, solverConfig, timeslotTierHardScore/ROOM_TIER_HARD_FAMILIES, gate staged, pick-best, threshold doom, test golden-oracle, drill-down UI, KPI parser) con piano per stadio
- [ ] #3 Implementazione TDD OBBLIGATORIA (direttiva maintainer 20/08): ogni consumatore migrato parte dal test che pinna il comportamento atteso a 4 livelli, poi l'implementazione; nessun consumatore migrato senza il suo test rosso-verde
- [ ] #4 Gate di partenza: la semantica e' validata prima da TASK-537 (campagna): se i trade non funzionano o i budget golden non bastano, il refactor non parte
- [ ] #5 Prototipo in worktree usa-e-getta prima del merge: suite completa verde + checkpoint replay QFR dimostra il trade 5xH32-vs-1xH17 accettato (la mossa revertita il 19/08)
- [ ] #6 Decisione finale: maintainer
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DOD esplicitata (maintainer 20/08 sera): /simplify e /code-review high PER STADIO (non solo finale), verifica EXIT 0 dell'orchestratore per ogni stadio dichiarato completo; TDD gia' in corpo. Ordine di merge col fix H2 del laptop: prima H2 (piccolo), poi 538 (ScheduleService e' la superficie condivisa).

STADI 1-2 COMPLETI (20-21/08 notte, worktree-bendable-538, ultimo commit 41cc5c6b): score 4 livelli (Score4), 64 dichiarazioni migrate (livelli verificati uno auno dalla review), gate/pick-best/multi-start a semantica [veri,quasi,medium,soft] con delivery gate sul budget (stop a 0-VERI E quasi<=floor), floor quasi derivato dallo SCORE REALE del golden (-49, era fantasma -43049 per H11), payload status dichiara quasiWithinBudget, UI AnalysisPanel tipo QUASI. DoD per-stadio piena: TDD (test nuovi pinnano semantica), simplify 8 finding (6 applicati + 2 skip motivati), code-review high 3 finding confermati corretti, suite completa EXIT 0 verificata dall'orchestratore 3 volte (1103 -> 1109 -> 1112). RESTO (stadio 3): parser KPI esterni (check-regression.py, capture-run.sh, api.sh, compare-runs.py) da aggiornare al formato [-a/b]hard/[c/d]soft PRIMA di qualunque A/B; solverBenchmarkConfig score string; badge UI completi; derivazione H-COPRESENCE (TODO 537). Merge order: dopo il fix H2 del laptop (ScheduleService superficie condivisa).

STADIO 3 COMPLETO (21/08, commit 182270a8, verificato dall'orchestratore): parse_score_4 single-source fail-loud in shared.py, tutti i parser vivi a 4 componenti (check-regression, compare-runs, badseed, score-stream, btimeslot, spalmone-report, multistart-ab pick-best lessicografico, api.sh score), 20 literal XML benchmark convertiti (BendableScore.parseScore verificato sul jar), UI parseScore4 + badge '0 veri / N quasi entro|OLTRE budget golden' + i18n. Test parse e check-regression verificati da Fedora (fail-loud pinnato). NOTA preesistente: solverBenchmarkConfig-pinned-remaining.xml malformato PRIMA della migrazione (extra content a fine file, da ripulire). Mancano al merge: merge H2 (fatto sul main da zai) -> merge bendable -> run 4-livelli.

VERDETTO: CHIUSO NEGATIVO (maintainer, 2/09 mattina, decisione formale all'intervista del mattino). IL QUINTO LIVELLO NON SERVE: (1) l'analisi del muro H32/H34 (research 1/09) ha mostrato che il passaggio intermedio e' peggiorante in somma a qualunque livello: il landscape e' monotono per-ora, il collo non e' la gerarchia ma il CAMPIONAMENTO (mossa 4x concatenata: 15Mx il budget LS: misurato); (2) la cura che funziona e' la mossa DETERMINISTICA lunga-raggio (SurplusHarvest, TASK-577), non un tier che insegni il trade; (3) il quasi-tier non ha mai girato in produzione (DEFAULT OFF da sempre) e le superfici disallineate sono state gia' riparate. Cosa resta: il codice BendableScore spento resta nel repo (nessun collasso immediato); il COLlassO A 3 LIVELLI e' pulizia futura a piacere (non urgente, candidata a una task di cleanup generica). CHIUDE IN CASCATA: TASK-564 (milestone-2 H34/H32 su hard[1]: la premessa muore col verdetto) e il punto 1 di TASK-572 (/feasibility: nessun quasi-tier da rappresentare: il metro pieno resta l'unico). TASK-537 (campagna chiusura con gerarchia vero/quasi): la gerarchia-quasi muore, il problema di sfondo (guard improve-or-noop bloccano trade al margine) resta aperto: da riformulare senza quasi-tier se emergera'.
<!-- SECTION:NOTES:END -->
