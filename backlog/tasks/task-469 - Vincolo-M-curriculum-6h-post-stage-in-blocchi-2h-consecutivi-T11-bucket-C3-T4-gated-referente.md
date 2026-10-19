---
id: TASK-469
title: >-
  Vincolo M 'curriculum 6h post-stage in blocchi 2h consecutivi' (T11, bucket
  C3/T4) - gated referente
status: Done
assignee: []
created_date: '2026-07-31 22:37'
updated_date: '2026-08-01 14:16'
labels:
  - claude-planned
milestone: m-12
dependencies: []
references:
  - claudedocs/spalmone-2026-27/vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt
priority: medium
type: enhancement
ordinal: 292000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fonte: bucket C3 / T4 (doc misure-golden-bucket-c-2026-07-31.md + referente T4). La nota T11
chiede "6 ore dopo stage per curriculum (G08, 3 OIT, G10, anche 2 consecutive)". Misura
golden: G10 = ESATTAMENTE 6h post-stage di Comportamento sociale, in 3 blocchi di 2h
consecutivi (gio 29/04, 06/05, 13/05, stage finisce 2027-04-23) -> modello pulito che combacia
alla lettera "anche 2 consecutive". G08 = 5h (Competenze di cittadinanza, off-by-one). G11
= 29h ma di Personalizzazione (materia pratica, scope diverso).

PERCHE' GATED E NON PROTOTIPO SUBITO (scelta motivata parte 4, 2026-08-01): la regola NON e'
rispettata al 100% dal golden. Un vincolo "6h post-stage di curriculum" penalizzerebbe il golden
su G08 (5h < 6h) -> violerebbe golden-first finche' referente non chiarisce lo scope. Il design ha
domande aperte che un prototipo ora congelerebbe come ipotesi. Il prototipo flag-OFF si fa DOPO
la risposta referente (T4), col design chiuso.

DOMANDE referente (T4, gia' nel doc domande-rotazione-e-tensioni-referente):
1. il curriculum e' Comportamento sociale / Educazione civica / Competenze di cittadinanza (3
   FA), e vale per G08 / G11 / G10 ogni anno?
2. G08 e' 5h o 6h (off-by-one reale o arrotondamento di foglio)?
3. G11: Personalizzazione e' curriculum (scope diverso) o la regola non si applica?
4. la forma "2h consecutivi" e' parte vincolante o solo descrittiva?

DESIGN (post-referente, draft): vincolo MEDIUM su (gruppo, materia-curriculum, periodo-post-stage)
che premia/penalizza verso 6h totali post-stage in blocchi 2h consecutivi. Tier MEDIUM calibrato
(quasi-gratis su G10 se il design e' fedele). Pattern: target-based come M12/M20 (overflow
sotto/oltre il target 6h) + forma a blocchi (M15-style consecutivita'). Da prototipare dietro
flag planner.constraints.curriculum-poststage.enabled DEFAULT OFF + A/B.

DIPENDENZE: risposta referente T4. Nessuna adozione senza maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Risposta referente T4 ricevuta (scope curriculum, G08 5h vs 6h, G11 Personalizzazione, forma 2h consecutivi)
- [ ] #2 #2 Design chiuso (forma, peso, set curriculum) downstream della risposta referente
- [ ] #3 #3 Prototipo flag default-OFF byte-identico a OFF + unit test
- [x] #4 #4 A/B full-solve con KPI standard, nessuna regressione strutturale
- [ ] #5 #5 Nessuna adozione senza maintainer
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
DESIGN + BASELINE (zai, 2026-08-01, prototipo deferito a sessione fresca con spec pronta).

FONTE VERBATIM (cross-finding prep TASK-474): vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt, tab "Vincoli Docenti", riga 50: "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)". T11 e' il docente (riga 49: "T11 | 9-14 sempre e un giorno alle 8.00"). Target ~6 documentale, blocco 2h consecutivo.

BASELINE GOLDEN misurata (seed/lessons.csv, post stage-end per gruppo):
- G10 (stage end 2027-04-23): Comportamento sociale POST = 6h esatti (14h pre, corso 20h). PRATICA PULITA, target centrato.
- G08 (stage end 2027-04-26): Competenze di cittadinanza POST = 5h (+ 5 pre = 10h); Educazione civica 20h tutte pre. Tot curriculum post = 5h. CASO DEFICIT (-1 vs 6).
- G11 (stage end 2027-03-19): Personalizzazione POST = 29h (31h pre, corso 60h). CASO CAUTELA: continua post-stage per volumi propri, l'eccesso NON va compresso.
Conferma il target ~6 e l'asimmetria di maintainer.

DESIGN vincolo M asimmetrico (prototipo, flag planner.constraints.curriculum-poststage.enabled DEFAULT OFF):
- Perimetro: {(G10, Comportamento sociale), (G08, Competenze di cittadinanza + Educazione civica), (G11, Personalizzazione)}, docente T11, post-stage = oltre lo stage-end del gruppo.
- Conta ore curriculum post-stage in blocchi 2h consecutivi (verbatim "ANCHE 2 CONSECUTIVE").
- ASIMMETRIA: sotto 6 -> penalita PIENA (6 - hours) x W_full; oltre 6 -> penalita DEBOLE (hours - 6) x W_weak (W_weak << W_full).
- CAUTELA G11: Personalizzazione 29h post-stage per volumi propri. La W_weak sull'eccesso deve restare bassa abbastanza da non combattere calendario/fill (23h di eccesso x W_weak: se W_weak=1 -> 23 medium, da misurare che non distorca). Alternativa: esenzione/cap dell'eccesso dove la materia prosegue come corso pieno (Personalizzazione 60h non e' solo curriculum).
- Tier MEDIUM (mai hard: il golden stesso ha G08=5 <6 e G11=29 >>6, hard vieterebbe il golden).
- Misura residuo run vs baseline (G10 6, G08 5, G11 29) come target.

PROTOTIPO (sessione fresca): nuovo constraint in SchoolScheduleConstraintProvider (template M-TR/M34-style, self-gating flag default OFF), test localizzati (ConstraintVerifier: deficit pena pieno, eccesso pena debole, G11 cautela), DoD (simplify + code-review high + test.sh). Stage-end per gruppo da StagePattern (non lettura golden a runtime: lo stage-end e' input).
<!-- SECTION:PLAN:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-01 09:33
---
[zai 2026-08-01] AGGIORNAMENTO STATO: maintainer (01/08 mattina, programma sessione) ha DECISO la semantica (target ~6 ASIMMETRICO: pieno sotto 6, debole oltre 6, cautela G11 Personalizzazione 29h) -> il framing 'gated referente / T4' della Description e' SUPERATO. Il prototipo e' BUILD-READY, non bloccato da referente. La baseline golden misurata conferma il target (G10=6 esatto, G08=5 deficit, G11=29 cautela) e la fonte verbatim e' l'extract riga 50. Implementazione deferita a SESSIONE FRESCA per budget (DoD piena su un nuovo constraint merita contesto fresco); spec completa nel planSet (design + baseline + perimetro + AC). Primo item prossima sessione.
---

created: 2026-08-01 13:19
---
[zai 2026-08-01 pomeriggio] A/B full-solve AC#4 FATTO (capture task469-results/ab-cps-on-run1/ + VERDICT). Run staged source=golden + M-CPS ON vs baseline combined-run2 (M-CPS OFF).

ESITO: hard 0 (=baseline), full-day 90,8% (+0,4pp), ricorrenza unpinned 75.6% (FLAT -0.2pp), buchi 0, medium -963616 (vs -994722, +31k entro varianza, neutro). NESSUNA regressione strutturale.

CURRICULUM POST-STAGE (converge verso 6, con cautela): G10 12->9, G08 7->6 (CENTRA target), G11 24->21 (cautela: weak-weight non combatte il calendario del corso Personalizzazione 60h, come da design asimmetrico maintainer). Verificato independentemente da KPI script + query DB (9/6/21 concordano).

VERDETTO: M-CPS realizza lo scopo a COSTO ZERO, candidato adozione, resta SI maintainer (AC#5). n=1: per adozione n>=2 per escludere varianza sul medium/full-day (effetto curriculum gia' meccanicamente confermato). AnchorRepair REVERTED in questo run (H28 0->-1, safety-net 473), ortogonale a M-CPS. Dichiarazione solo, nessuna adozione.
---

created: 2026-08-01 14:03
---
[zai 2026-08-01 sera] RATIFICA IN CORSO (maintainer ha confermato l'adozione). Modifiche fatte (working tree, non ancora committate): (1) SchoolSchedule.isCurriculumPostStageEnabled() flippato a DEFAULT ON opt-out (v==null||parseBoolean, identico a isCoPresenceEnabled/isLabDayConsolidationEnabled); (2) commenti/javadoc in SchoolScheduleConstraintProvider (build-array, method header, Origine+ADOZIONE) aggiornati DEFAULT OFF->ON con evidenza; (3) CurriculumPostStageConstraintTest: build(false) ora setProperty("false") (clearProperty risolverebbe a ON), test rinominato optedOut_isInertEvenAtDeficit, javadoc classe aggiornato; (4) CLAUDE.md: riga M-CPS nella tabella Medium + blocco RATIFICA TASK-469. DoD: /simplify clean (self-review, diff meccanico mirror di 5 ratifiche precedenti), test.sh EXIT 0 (947/0/0/16 skipped), /code-review high (skeptic Opus) in corso. Banner esiste gia' in ScheduleService.refreshSolverStatics (linea 2075, legge isCurriculumPostStageEnabled -> ora true di default). Nessun altro test/caller rompe (grep: solo CurriculumPostStageConstraintTest referenziava il flag; i test snapshot/golden non asseriscono totali ne' M-CPS, e M-CPS e' inerte senza stageEndByGroup pubblicato). Commit + chiusura task dopo code-review clean.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RATIFICATO DEFAULT ON (maintainer, 2026-08-01 sera; commit abbd7f91). M-CPS curriculum post-stage target 6h asimmetrico adottato con opt-out esplicito (-Dplanner.constraints.curriculum-poststage.enabled=false), stessa forma delle ratifiche precedenti. SchoolSchedule.isCurriculumPostStageEnabled ora null/absent->true. Evidenza A/B ab-cps-on-run1 (ON vs combined-run2 OFF): hard 0, curriculum converge verso 6 (G10 12->9, G08 7->6 centra target, G11 24->21 cautela), costo zero (full-day +0,4pp, ricorrenza flat, holes 0, medium neutro -994722->-963616 entro varianza). DoD completa: /simplify clean, /code-review high (skeptic Opus) 4/4 claim CONFIRMED + 2 finding non-bloccanti fixed (golden guard M-CPS medium=-123 matchCount=3 in GoldenSolutionRegressionTest, banner conferma enabled=true stage-end 8 gruppi; frontend docs.json en/it 'prototype off'->'ratified on'), test.sh 947/0/0/16 EXIT 0, ui-build EXIT 0. Banner pre-esistente in ScheduleService.refreshSolverStatics (linea 2075). Q19 G14->R10 (referente) corretta in CLAUDE.md; rifiniture G14 tracciate in TASK-477.
<!-- SECTION:FINAL_SUMMARY:END -->
