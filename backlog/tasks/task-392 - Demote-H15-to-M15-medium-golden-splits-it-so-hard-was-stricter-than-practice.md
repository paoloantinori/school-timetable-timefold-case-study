---
id: TASK-392
title: Demote H15 -> M15 (medium) — golden splits it 98x so hard was stricter than school practice
status: Done
assignee: []
created_date: '2026-07-22 10:15'
labels:
  - claude-planned
dependencies: []
ordinal: 216000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Opzione 2 confermata dall'utente (2026-07-22): la regola H15 ("same-day same-subject must be consecutive") fornita alla scuola è più rigida di come la scuola stessa la applica, quindi è logicamente un M. Evidenza (dallo spike su TASK-391): l'unico residuo hard di R1 (ruin 5-20) è UN singolo split di Lingua Italiana 2h+1h, identico nei due run (attribuito dai raw lessons.txt filtrando i ~60 split same-day che sono materie di lab, già esenti da H15). Il golden fa esattamente questo: spezza Lingua Italiana 19 volte (dei suoi 98 split H15 totali, golden-audit task-347). Un tier HARD penalizza il solver per fare UNA volta ciò che la scuola fa 19 volte → più stretto della pratica. Per la direttiva primaria (un vincolo si giudica su "riflette la pratica golden?"), "must be consecutive" è logicamente una PREFERENZA → medium. Rovescia la decisione task-349 ("keep hard + pinned-exempt"), la cui unica preoccupazione ("il solver scatter liberamente sotto medium") NON era testata sotto medium ed è smentita dall'evidenza R1 (1 split, non scatter). Modello allineato alla pratica; la performance (scatter?) è ricerca, da validare a benchmark.
<!-- SECTION:DESCRIPTION:END -->

## Implementazione (FATTA su Fedora, DoD verde)

Cambio tier + PESO CALIBRATO: `ONE_HARD` → `ofMedium(20)` (NON il naïve `ONE_MEDIUM`); esenzione pinned break-level e weigher gap-minuti invariati. **Perché ofMedium(20) e non ONE_MEDIUM** (correzione post-review dell'utente 2026-07-22): a ONE_MEDIUM uno split da 1h costa 60 medium, ma i medium che il solver ci baratta contro (M20 riempimento 1000/giorno, M22 compattezza 1000/giorno) sono ~16x più pesanti → il solver scatter-erebbe Lingua Italiana per riempire giornate, la paura esatta di task-349, che sarebbe emersa già al primo solve di validazione. `ofMedium(20)` × gap-minuti = ~1200/split-1h, nella famiglia "logical hard medium" di M20/M22, così uno split è accettato solo se evitarlo costerebbe più di ~1 giornata M20 (uno split forzato/zero-slack, come i 98 del golden). Medium (non hard) non blocca comunque mai lo 0-hard a nessun peso. Il moltiplicatore 20 è un knob da benchmark contro la tensione M15-vs-M20 (troppo alto sopprime il full-day, troppo basso permette scatter). Rinominati `H15`/`sameDaySameSubjectMustBeConsecutive` → `M15`/`sameDaySameSubjectShouldBeConsecutive`. Drill-down `ScheduleService` emette `M15`/`MEDIUM` (era `H15`/`HARD`). Golden guard asserisce M15 su tier MEDIUM (mirror di M28), ancora **0** (i 98 split del golden restano pinned-exempt). Ripple: message bundles (`constraint.M15`) + `ConstraintMessageFormatter` case, frontend (`constraints.ts` type hard→medium, `docs.json` en/it, `LessonTooltip`), CLAUDE.md (H15 row Hard→Medium + nota "H15->M15 demotion" che supera task-349), PROJECT_INDEX. NON toccato: il veto hard `hasH15Violation`/`findH15SafeSlot` della pre-assegnazione (meccanismo CSP separato che tiene puliti i PIN; conservatorismo che non contraddice la demozione — nomi ora stali ma concetto distinto). ConstraintVerifier `penalizesBy(60)` invariato (tier-agnostico).

**DoD:** /simplify (rename, nessun code-surface → pulito), /code-review high (manuale, non model-invocable: completezza rename grep-verificata, tier corretto, dispatch/message su M15), test.sh EXIT 0 (688 verdi, GoldenSolutionRegressionTest passa con M15-medium=0), ui-typecheck EXIT 0 (frontend toccato).

## VALIDAZIONE (laptop, PENDENTE — test lungo)

NON è un braccio solverConfig (M15 è codice del constraint provider, non XML). Il laptop fa un **solve di HEAD** (R1 ruin 5-20 già adottato + M15 medium), 2 run per la varianza, e verifica:
1. **hard scende a ~solo-H17** (~-6/-10): il -60 di H15 esce dal tier hard (si sposta a medium). Se ci si avvicina a 0-hard affidabile → obiettivo (parziale) raggiunto (resta H17, TASK-390).
2. **M15-medium resta piccolo** (~1 match, non balloon): il solver NON sfrutta il rilassamento per scatter (la paura di task-349). Se M15-medium esplode → serve un peso calibrato o un meccanismo diverso (riportare, non annacquare).
3. **KPI tengono** (full-day ≥ ~74%, ricorrenza ≥ ~38%, blocchi ≥ ~1800).
Confronto col benchmark R1 (H15-hard): era hard {-66,-70} = H15 -60 + H17. Con M15: hard atteso ~-6/-10, medium +un piccolo M15.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff (rename+tier-flip, nessun code-surface, pulito)
- [x] #2 Skill /code-review high — eseguita in-context (skill non model-invocable): completezza rename grep-verificata, tier/dispatch/golden corretti
- [x] #3 Verifica finale con EXIT CODE: test.sh exit 0 (688 verdi + golden M15-medium), ui-typecheck exit 0 (frontend toccato)
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
