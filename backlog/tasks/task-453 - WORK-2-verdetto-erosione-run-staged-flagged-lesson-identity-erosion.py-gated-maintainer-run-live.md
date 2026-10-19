---
id: TASK-453
title: >-
  WORK 2 verdetto erosione: run staged flagged + lesson-identity-erosion.py
  (gated maintainer, run live)
status: Done
assignee: []
created_date: '2026-07-29 12:37'
updated_date: '2026-08-03 21:49'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 276000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PRODUZIONE del verdetto WORK 2 (tooling gia' consegnato commit f0448e9e). Il tool lesson-identity-erosion.py e il dump ID-tagged per-fase (PhaseTransitionMarker.writeTransitionDumpWithIds) sono pronti e DoD'd. Manca solo PRODURRE i numeri: serve UN solve staged con -Dplanner.phase-transition-dump.enabled=true (via java -jar con la flag sulla riga di comando, NON MAVEN_OPTS) che generi i dump ID per-fase (costruzione -> finale, stessa popolazione), poi `python3 scripts/analyze/lesson-identity-erosion.py --dir target/phase-transitions` dà il verdetto: quali gruppi/materie perdono la cella settimanale e in quale fase, composition-controlled (set MOBILE = unpinned a entrambi i cut, split authoritative vs template-promoted). E' un RUN LIVE quindi GATE maintainer (regola: nessun run live senza via esplicito). Alternativa checkpoint: estendere WeeklyTemplateConstructionRealSeedTest a solve staged completo su real seed in H2 (incremento piu' pesante, non fatto). Dettagli: claudedocs/spalmone-2026-27/task447-results/spikes/SPIKES-RAW.md sezione 'WORK 2 - TOOLING erosione'.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DECISIONE maintainer/Fedora 29/07: il run live NON si fa ora da solo. Si attacca il flag planner.phase-transition-dump.enabled alla PROSSIMA certificazione (che serve comunque quando maintainer ratifica il pacchetto 435/446/M37/H28/447). Un run paga due debiti (verdetto erosione + cert) invece di bruciarne due. IN ATTESA, non e' lavoro corrente: il tool e' gia' consegnato (commit f0448e9e + ffd8b64f), manca solo produrre i numeri al prossimo cert flagged.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
WORK 2 CHIUSO (2026-08-03, run live GATE maintainer via esplicita). Dump-enabled staged solve (-Dplanner.phase-transition-dump.enabled=true sulla riga java -jar via PLANNER_JAVA_OPTS, NON MAVEN_OPTS; early-verify ha confermato flag raggiunto). Solve clean 0hard/-1.032.457medium, 4 phase-transition cut (construction -> finale). VERDETTO (lesson-identity-erosion.py --dir, composition-controlled): cell-retention MOBILE (1752 lezioni unpinned a entrambi i cut) = 16.6% kept, 83.4% moved. Erosione concentrata nella fase LS-Compactness (1449/1744 = 83% delle sempre-mobili; Compaction 51, room phases 0). Worst per-(gruppo,materia): teoriche generaliste mai-pinnate (G09 Lingua Italiana 3.6% kept, G12 Scienze 0%). INSIGHT: il template (74% resemblance) e' preservato dai PIN (promoted+authoritative), non dalle lezioni libere (mai-pinnate = 83% rimescolate ma minoranza); la resemblance si alza riducendo il rimescolamento delle mai-pinnate (M32), non pinnando di piu'. Misura diagnostica read-only, NESSUN cambio al modello o adozione. DoD: /simplify self-review (bug score-after-kill fissato), /code-review high (Ready: Yes, 3 Minor fissati: n_dumps || true, comment timing finestra 180s, dump-dir esplicito), test.sh N/A (diff bash-only no Java). Doc: task453-results/VERDICT-erosione-2026-08-03.md. Script: scripts/benchmark/task453-phase-dump-erosion.sh.
<!-- SECTION:FINAL_SUMMARY:END -->
