---
id: TASK-467
title: >-
  FASE 2: enumerazione delle rotazioni regolari senza input giorni-lab
  (multi-candidato, oltre l'euristica dello human planner)
status: Done
assignee: []
created_date: '2026-07-31 20:20'
updated_date: '2026-08-16 15:49'
labels:
  - claude-planned
  - phase-2
milestone: m-12
dependencies: []
priority: low
ordinal: 290000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Visione (maintainer, 2026-07-31 sera, doc modello-rotazione-acconciatura parte 4): il foglio giorni-lab (FA) e' l'euristica piu' recente dello human planner: fa confermare le date dei lab ai docenti coinvolti e poi risolve in un sottospazio semplificato. DEFAULT nostro: fare lo stesso (consumare l'input, semplificarci la vita). Questa task e' il SE-E-SOLO-SE: offrire un servizio migliore NON chiedendo i giorni-lab in input ma valutando tutte le combinazioni di assegnamenti REGOLARI (rotazioni legali sui vincoli docente), provando PIU' di una soluzione parziale invece dell'unica scelta arbitraria del planner umano, che e' ottimista senza certezza matematica. Base esistente da rivitalizzare: backtracking esaustivo 99197850, candidati (day x slot) come preferenze 0a842142, famiglia TASK-120 (RotationCandidateGenerator, RotationTestRunner early-abort, HybridPhase1aService), accantonata quando l'accordo divento' 'referente pre-alloca a mano'. Vincolo di regolarita' = struttura del doc rotazione (slot fissi per periodo, eredita' dello slot, priorita' docente mono-giorno). FASE 2 esplicita: NON iniziare finche' la fase 1 (input-driven) non e' consegnata e maintainer non la apre.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Design: enumeratore di rotazioni regolari (periodi, eredita' slot, vincoli/preferenze docente) con spazio candidati completo, nessuna esclusione a priori tra le legali
- [x] #2 Multi-candidato: k soluzioni parziali distinte provate (non una), con criterio di ranking e early-abort, integrate col ciclo staged
- [ ] #3 Confronto misurato vs input-driven: feasibility rate, qualita' KPI, costo computazionale
- [x] #4 Nessuna adozione senza maintainer; gated all'apertura esplicita della fase 2
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## FASE 2 DESIGN PRONTO (2026-08-01 notte, read-only) - implementazione resta GATED

Doc di design misurato: `claudedocs/spalmone-2026-27/task467-fase2-design-misurato-2026-08-01.md`.
Script di misura (riproducibile, dai file di input): `/tmp/measure_task467_candidatespace.py`.

### MISURA (numeri esatti, skeptic CONFIRMED)
- Menu celle annue (teacher,group,weekday) ammissibili = incarichi ∩ disponibilita' ∩ giorno-L:
  T01 4, T19 4, T18 11, T26 14 (totale 33).
- Branching PER-PERIODO PER-TEACHER (unita' di enumerazione): 4 / 4 / 36 / 120. **MAX = 120 < 10^4**.
- Verdetto: enumerazione esaustiva FATTIBILE a granularita' periodo; esplode solo per-settimana
  (10^70+), vietata dal modello di regolarita' (depth = #periodi ~7, non #settimane ~36).
- Potature (tutte input-driven): regolarita', trimestralita' giorni-L, budget-feasibility, 0hard,
  mono-giorno. Golden solo tie-break strutturale dichiarato.

### DESIGN (riusa, non reinventare)
- Riattiva `DayAssignmentCsp.enumeratePairsRecursive` (production, `MAX_CANDIDATES=10000`,
  `DayAssignmentCsp.java:252`) come skeleton esaustivo; layer timeline (sequenza periodi) NUOVO sopra.
- `GreedyRotationAssigner` (path fase-1, accetta PARZIALE) resta default; fase-2 = superamento
  ottimismo planner (k soluzioni complete 0hard, non una parziale).
- Accettazione: `PreAssignmentValidator.canPinHardConstraints` (`:600`, hard-only).
- Ranking input-driven (budget, scheletro giorni-L, eredita' slot/mono-giorno); golden tie-break solo.
- `RotationTestRunner`/`RotationCandidate` MORTI (test-only): non resuscitare.

### ONESTA' (da verifica avversariale)
- 2/4 docenti (T01 Thu, T19 Mon/Tue) hanno availability golden-derived Q22: il verdetto di
  fattibilita' NON dipende da quelli (max = T26, nessuna regola), ma la provenienza va dichiarata.
- Giorni-L trimestrali (non anno-pieno): G12 forzato in Q3; per-trimestre il menu e' < dell'annuo.
- Il count di candidati 0hard-legali post-pruning e' STIMA (10^3-10^6), non misurato: si misura
  all'implementazione. Misurato/verificato e' solo il branching per-periodo <10^4.

### SCOPE NEGATIVO (anti-overfitting)
NON copia l'istanza golden cella-per-cella; NON cambia la semantica fase-1; NON sostituisce il
canale input-workbook (il backtracking e' RIPIEGO se l'input non arriva); NON promuove a hard; NON
esce dall'acconciatura.

**STATO: AC #1 (design) COPERTO. AC #2/#3/#4 restano GATED all'apertura esplicita di maintainer
(fase 1 input-driven consegnata prima). Nessun codice scritto, nessun solve, nessuna adozione.

## AC#4 WIRING consegnato (16/08, mandato Filone 1): DEFAULT OFF, gate non-interferenza PASS, efficacia VACUA sul seed corrente

Wiring: HybridPhase1aService.runPhase1 chiama assignWithEnumeratorPlans quando planner.pre-assign.rotation-enumerator.enabled=true (opt-in, default false, banner in refreshSolverStatics con enabled+topk+weeks-per-period+max-periods). k piani ranked provati in ordine come restrizione per-PAIR Map<BlockKey,Set<DayOfWeek>> del backtracking greedy INVARIATO (6-arg overload; 5-arg delega con null); primo fattibile vince, tutti-falliscono = fallback greedy. Validazione 0hard (PreAssignmentValidator) IDENTICA.

Due round di review con fix: (R1 critico) restrizione union-based era meccanicamente inerte (unione weekday invariante fra candidati) -> per-PAIR dal mapping cella->gruppo; (R1) pair-dropping silente -> restrictValidDays ritorna le coppie contraddette e il piano FALLISCE, non skippa; (R2) 'mai peggio del greedy' vale solo per il fallback: un piano adottato cavalca la partial-success semantics (PRECONDIZIONE del futuro gate A/B: confrontare anche le sessioni pinnate). Dedup restrizioni identiche, blame deterministico, wording neutro assigner, walk daysByGroup condiviso con weekdayEconomy.

A/B non-interferenza (n=1/braccio, baseline-golden, tutto ratificato ON): hard 0/0, medium -966278/-970299, full-day 90,0/89,6, ricorrenza unpinned 75,3/75,7, BUCO-INTERNO 0/0, wall 23,6/20,7 min. MA VACUO come misura di efficacia: LabRotation.count()==0 (TASK-364) salta Phase 1/2/3 PRIMA del wiring in ENTRAMBI i run (verificato nei log). Il gate prova che il flag non sborda, non che il ranking aiuti.

DoD: /simplify 4-agent applicato (unique groupNamesById walk + WARN collisione, daysByGroup hoistato, restrizioni precompute, no buildFailureAnalysis sul fail-fast, ecc.); /code-review high (Opus) round 2: nessun critical/major, 2 minor corretti + 2 minor e 1 efficienza documentati come differiti con motivo scritto; test battery 79/79 (wiring 8, enumerator 11, greedy/hybrid 60).

Docs: claudedocs/spalmone-2026-27/task467-results/ab-2026-08-16/VERDICT.md (completo: findings, A/B, debito differito, precondizioni gate). Commit: b9a2ad91 (wiring) + fix post-review.

STATO: AC#4 'gated all'apertura' COPERTO come meccanismo (flag OFF, non-adozione raccomandata e confermata). AC#3 (confronto misurato vs input-driven) resta aperto: IMPOSSIBILE sul seed corrente (lever irraggiungibile), richiede TASK-364 o probe con rotazioni sintetiche.

## ENABLER per AC#3 (16/08 pomeriggio, Filone 2 mandato): LabRotationDeriver

Il follow-up TASK-364 (claudedocs/spalmone-2026-27/task364-followup-labrotation-deriver-2026-08-16.md) rende il lever RAGGIUNGIBILE: LabRotationDeriver (flag planner.pre-assign.labrotation.derive-from-golden.enabled DEFAULT OFF, hook pre-cortocircuito in HybridPhase1aService) deriva le righe acconciatura dal golden (weekday modale >=20% quota, shape run modale PER WEEKDAY) e le valida contro i fogli calendario (solo giorni-L seminati). Popolazione PRODUZIONE = filtro Phase 1 (de-tag overrides compresi): 13 coppie -> 15 righe, rotating T18 x2. Review Opus completa: HIGH (census 15/18 del test vs produzione 13/15) corretto, MEDIUM (per-weekday shapes, no-op senza golden) corretti, LOW tutti corretti o annotati. Quando armato, l'A/B di efficacia TASK-467 AC#3 diventa MISURABILE (era impossibile: LabRotation.count()==0 saltava Phase 1/2/3 prima del wiring). Precondizione dal VERDICT TASK-467 resta: il gate dovra' confrontare anche le sessioni pinnate (partial-success semantics).

Test: LabRotationDeriverRealSeedCensusTest 6/6 + ZeroRotationTest 2/2 (flag-clear aggiunto).

## A/B CHAIN 364+467 (16/08 sera, mandato Filone 1): NEGATIVO DECISO, gate FALLITO, meccanica prosciolta

Round 1 (preservato round1-vacuous-isLabHour-empty/): delta treatment ~0. Due bug latenti trovati e riparati per rendere la chain eseguibile: (1) Phase 3 INFEASIBLE con professionalizzantiday vuota (predicate corretto: no-profDay = expected-empty, commit 36581b2b); (2) lesson.isLabHour MAI settato nel path regular del LessonGenerator: sul seed 2.836 ore lab con isLabHour=false e le query di sessione Phase 1/2 vedevano popolazione VUOTA (fix 84da1595; isLabHour e' letto solo dal pre-assign: score e production a flag spenti invariati, golden regression verde).

Round 2 (HEAD 71e0c30c): control 0hard/-1.041.682, 23 min, attempt 1. Treatment (chain VIVA: deriver 15 righe, enumerator piano-1 rifiutato dal fail-fast + piano-2 adottato, Phase 1 398 + Phase 2 2.107 entita' pinnate): **-64hard in 5/5 attempt**, medium -2.099.400, full-day 75,1 vs 87,4, ricorrenza 68,9 vs 74,5, wall ~120 min. Composizione hard: H20 -21 (pin su slot CHIUSI dal calendario gruppo), H0 -28, H7 -15.

MECCANISMO RADICE: il path di pin Phase 1/2 non valida H20 (finestra calendario) ne' H7 (cap docente): il census di skip del greedy elenca wrongDay/holiday/H5/stage/H11/used/idealEnd, niente calendar-window. I blocchi derivati (fino a 8h) finiscono su weekday con finestra piu' stretta del blocco e il solver eredita hard che la LS non puo' curare senza spostare pin autoritativi.

RACCOMANDAZIONE: NESSUNA adozione (entrambi i flag DEFAULT OFF, production intatta). Meccanica multi-candidato PROSCIOLTA (primo fuoco reale del fail-fast). Cura identificata: gate H20+H7 nel piazzamento (o seminare solo forme che entrano nella finestra/cap a monte nel deriver), poi nuovo A/B. VERDICT: task467-results/ab-chain-2026-08-16/VERDICT.md.
<!-- SECTION:NOTES:END -->

### AC#2 CORE consegnato (laptop, notte 15-16/08, spike filone 4)

rankTopK/enumerateTopK in RotationEnumerator: ranking input-driven a tre termini
(budget tightness, lab-day alignment sul calendario dichiarato, weekday economy
mono-day), distinctness per firma strutturale, tie-break deterministico, early-abort
propagato. Test 4 nuovi (top-k distinti, allineamento preferito, mono-day preferito,
determinismo contro input order) + fix Cell-not-Comparable nella firma: 11/11.
L'INTEGRAZIONE col ciclo staged resta GATED su maintainer (come da AC#4): nessun wiring,
nessun default flip. Nota design: pesi tutti a 1 (il doc 2.5 non ha pesi misurati:
inventarli senza gate run e' la trappola del tuning statico chiusa dalla campagna).

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
