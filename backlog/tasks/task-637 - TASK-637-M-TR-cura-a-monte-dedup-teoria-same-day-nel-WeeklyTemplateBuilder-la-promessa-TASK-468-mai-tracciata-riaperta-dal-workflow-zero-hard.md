---
id: TASK-637
title: >-
  TASK-637 - M-TR cura a monte: dedup teoria same-day nel WeeklyTemplateBuilder
  (la promessa TASK-468 mai tracciata, riaperta dal workflow zero-hard)
status: Done
assignee: []
created_date: '2026-09-21 12:47'
updated_date: '2026-09-22 18:07'
labels:
  - claude-planned
  - construction
  - golden-aligned
  - m-15
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La diagnosi TASK-468 (01/08, VERDICT sweep468) chiuse cosi': i blocchi 2h teoria NASCONO IN COSTRUZIONE (residuo fine-construction ~1672 ≈ finale 1679; la LS li eredita e non li crea) e sono UNPINNED al 98,6%. La cura indicata: DEDUP per-(gruppo, giorno, materia-teorica) nel placement greedy del WeeklyTemplateBuilder (NON un capping di length). NON e' mai stata tracciata come task: promessa senza proprietario dal 01/08 (audit-leak 21/09). IL WORKFLOW NUOVO LA REND ECONOMICA: il fenomeno e' misurabile da target/construction-lessons.txt (banco minuti), la cura e' una preferenza nel builder (forma 599/636: mai forza, opt-in default OFF fino a ratifica), validazione snapshot/banco senza cicli. Predicato isTheoryForRule gia' esiste nel provider (M-TR). Attento all'interazione M15 (forza blocco adiacente) e S17: la dedup devi solo NON-CREARE il secondo blocco, non spostare i presenti. Golden baseline ~440 runtime (self-gating).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Il fenomeno e' quantificato da costruzione (target/construction-lessons.txt o banco): ore teoriche in blocchi 2h+ same-day per (gruppo, materia) prima/dopo
- [ ] #2 La cura e' una preferenza-mai-forza nel WeeklyTemplateBuilder (dedup per (gruppo, giorno, materia-teorica)), flag opt-in default OFF fino a ratifica, con censore/banner
- [ ] #3 Banco differenziale 3-arm: OFF byte-identico, ON con firma residuo M-TR in calo e ricorrenza in banda, guardia anti-regressione fill/full-day
- [ ] #4 Blind puzzle di conferma: post-condizione delta punteggio reale (precedente 21/09), famiglie incluse
- [ ] #5 Gates DoD complete + ratifica maintainer prima di qualunque flip
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-09-22 18:07
---
PASSO 4 (22/09, laptop, engine rc-1): A/B adozione eseguito e consegnato, 5/5 criteri pre-registrati VERDI, NESSUN flip (decisione a maintainer). OFF [-3/0] vs ON [-1/0] hard; M-TR celle 2+ same-day 1606 -> 1402 (-12,7%, residuo 1662 -> 1455); ricorrenza unpinned 70,3 vs 70,0 (soglia 68); full-day 94,7 vs 94,6 (soglia 93); banner ON armed=true theoryHoldFills=139 alla prima costruzione e 111-155 nel burst GRASP. Consegne appaiate: entrambe dall'attempt-2 grasp seed 32. GAP dichiarati: checkpoint opt-in non passato (dir vuote, stati consegnati negli export locali over-cap); sanity rossi 2 (OFF) vs 1 (ON), famiglie note. Verdetto completo: claudedocs/spalmone-2026-27/task637-results/ab-adoption/VERDICT.md (commit 1aab4307).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
