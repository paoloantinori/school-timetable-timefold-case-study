---
id: TASK-645
title: >-
  Timefold flagship: ViolationScopedMoveProvider (constraint-match-guided move
  selection)
status: Done
assignee: []
created_date: '2026-09-23 04:52'
updated_date: '2026-09-23 09:22'
labels:
  - upstream
  - claude-planned
dependencies: []
references:
  - claudedocs/research_violation-directed-repair_2026-09-23.md
  - src/main/java/com/school/planner/solver/ViolationScopedSteepestFinisher.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
LA FLAGSHIP (proposta 3 di 3, la piu' grande: aprire DOPO che le due PR piccole hanno stabilito rapporto). CONTESTO: il team ha confermato che Timefold NON espone move selection guidata da constraint match (perche' per-proposal costa un score); noi abbiamo il riferimento funzionante: ViolationScopedSteepestFinisher, 0,05ms/eval incrementale (141k eval in 7,4s), chiude residui COMPOSITE_MISSING che la LS non chiude (A/B 5/5, catena cieco->meccanizzazione->adozione validata end-to-end). PROPOSTA: nuovo provider nella famiglia preview.api.move.builtin (forma ListChangeMoveProvider) che genera mosse solo attorno alle entita' giustificate dai match hard-negativi + le CONTESTO dei pattern (la scoperta del cieco: la cura della A..B..A sta sulla lezione di mezzo, mai un fatto del match). Nella stessa discussione valutare la forma custom phase config "violation-directed descent" (opinionata: lasciarla al dialogo). MATERIALI: report ricerca claudedocs/research_violation-directed-repair_2026-09-23.md (GLS, QuikFix, fix-and-optimize = precedenti), banco OffCapture638BenchTest, catalog sweep REPORT. Rapporto aperto via PR 2610 comment (public). Lingua inglese, /pstack:unslop, aspettarsi iterazione con triceo.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SETACCIO UPSTREAM 23/09: SPAZIO LIBERO + INVITO ESPLICITO. Issue #891 (Guided Local Search, aperta 2024): triceo 'an experiment that we want to do at one point. Unlikely in the near or medium term. PoC pull requests are welcome!'; ge0ffrey 'The Indictment API is a building block of building Guided Local Search some day'. Nessuna issue/PR esistente su violation-guided move selection (setaccio: constraint match 37 risultati tutti explainability, move selector 93 scanditi, indictment 12). VINCOLO DI FORMA: la preview Neighborhoods va verso iterator paradigm (#2582) e coarse-grained generic moves (#2610): la proposta si attesta su provider/iterator, NON move-selector XML. Ordine maintainer 23/09: discussion + codice esempio su branch del NOSTRO fork (maintainer/timefold-solver, esiste, stale marzo: branchare da upstream/main). PoC agent in volo (branch poc/violation-scoped-move-provider). Poi auto-adversarial review. NESSUN invio senza maintainer.

PoC ATERRATO 23/09: branch poc/violation-scoped-move-provider @ ec74460 sul fork (base upstream/main a283288c del 22/09), push verificato ls-remote, upstream con push URL disabilitato nel clone (impossibile l'accidentale). 3 file ~400 righe: ViolationScopedMoveSupport (risoluzione fatti via getConstraintMatchTotalMap, fail-loud se policy != ENABLED, 8 tipi score) + ViolationScopedChangeMoveProvider + ViolationScopedSwapMoveProvider (identici ai builtin + filtro violation-target). ./mvnw -pl core -am compile BUILD SUCCESS (Spotless li' ha riformattato allo stile loro). SCOPERTA STRUTTURALE per la discussion: la preview Neighborhoods NON espone NESSUN hook ai constraint match (SolutionView non vede lo score; l'Indicment API e' rimossa da OSS main; lo strato pubblico api/score/analysis e' documentato 'exclusive to Enterprise Edition'): il PoC aggancia il director dietro la SolutionView, la stessa via del nostro finisher. GAP censiti: (1) zero esecuzioni/test runtime, (2) target set senza invalidazione per-step, (3) raggiungibilita' di ENABLED su OSS durante LS = LA domanda per la discussione, (4) justification custom non mappate, (5) contesto pattern assente, (6) partner scoping assento. Adversarial review del PoC in volo; review 643 in volo.

ADVERSARIAL REVIEW del PoC 23/09: verdetto SERVE-TEST-MINIMO (la forma regge: A diff provider = solo filtro CONFIRMED, B API/tipizzazioni reggono senza reflection, D enterprise-only REFUTED come blocco: ENABLED raggiungibile su OSS via MonitoringConfig metriche CONSTRAINT_MATCH_* o environmentMode STEP_ASSERT+ + AbstractScoreDirectorBuilder.withConstraintMatchPolicy pubblico). TRAPPOLA TROVATA: DefaultMoveTester costruisce il director con policy DISABLED di default, quindi il fail-loud del PoC ucciderebbe il primo tentativo altrui. E un over-claim: il MOTIVATION javadoc cita i numeri di produzione (0,05ms/eval) come se li' avesse misurati il PoC. CURE DISPATCHATE (agente in volo): knob withConstraintMatchPolicy sul MoveTester + 1 NeighborhoodTester test produceAllOf/NoneOf + rietichetta numeri + percorso abilitazione nel javadoc. Stima review: ~1 giorno verso discussion difendibile. I gap cache per-step e justification mapping restano le DOMANDE da portare loro.

DIRETTIVA maintainer 23/09 sera (memoria open-core-posture-maintainer): CONTRIBUTI UPSTREAM ACCANTONATI prima dell'invio. Il branch PoC sul fork resta come artifact di conoscenza (la tecnica di aggancio al director dietro SolutionView e' NOSTRA e gia' in produzione nel 638); l'agente cure in volo viene lasciato finire perche' il test NeighborhoodTester dimostra la tecnica su main, poi FREEZE: niente investimenti ulteriori. La sostanza (violation-directed selection funziona nella forma provider+iterator, enablement via MonitoringConfig/environmentMode) confluisce in TASK-653 come capacita' nostra.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
