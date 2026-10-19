---
id: TASK-568
title: >-
  TASK-566 - Banco localizzato quasi-tier: proprieta' di attraversamento
  hard[0]/hard[1] (controllo all-hard vs trattamento quasi)
status: Done
assignee: []
created_date: '2026-08-29 10:56'
updated_date: '2026-08-29 11:38'
labels:
  - claude-planned
  - quasi-tier
  - banco
milestone: m-13
dependencies: []
priority: high
type: spike
ordinal: 320000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Banco localizzato quasi-tier (mandato Fedora 29/08 ~13:00, milestone-2 B, numero TASK-566 di Fedora). OBIETTIVO: dimostrare la proprieta' di attraversamento del tier: le violazioni delle famiglie quasi (H32 qui; H34+H32 su quasihard-b) devono poter stare su hard[1] con hard[0] pulito quando il tier e' armato, contro il muro all-hard quando non lo e'.

CONTESTO: l'infrastruttura BendableScore 2+2 completa (flag planner.constraints.quasi-tier.enabled DEFAULT OFF, H34+H32 su hard[1]) vive sul worktree quasihard-b di Fedora NON pushato. Il branch remoto raggiungibile piu' vicino e' worktree-bendable-538 (TASK-538 stadio 3): ha Score4 + 5 famiglie 537 su hard[1] INCONDIZIONALI (H11, H31, H32, H33, H-COPRESENCE), NON ha H34 (fork pre-26/08) ne' il flag. Il banco si costruisce quindi come test dedicato su branch side off bendable-538 in worktree /tmp (fuori dal flap del worktree principale).

PREDIZIONE PRIMA (registrata prima dell'implementazione, 29/08 ~13:30):
- BRACCIO CONTROLLO (routing all-hard, equivalente flag OFF di quasihard-b): le violazioni H32 delle 3 celle vuote dichiarate atterrano su hard[0] (livello 0), hard[1] = 0. E' il muro: ogni mossa che le tocca e' hard-worse, la LS non puo' attraversare.
- BRACCIO TRATTAMENTO (routing quasi, equivalente flag ON): le STESSE violazioni atterrano su hard[1], hard[0] = 0. La proprieta' del tier: hard[0] pulito = la LS puo' accettare scambi che peggiorano hard[1].
- La differenza hard[0] vs hard[0]+hard[1] fra i bracci e' la dimostrazione meccanica dell'attraversamento (la proprieta' del tier, come da mandato: non serve la LS per dimostrarla).
- NOTA H34: assente su questo branch (fork pre-H34); la gamba H34 del banco corre quando quasihard-b viene pushato.

SEMANTICA DEL FLAG NEL BANCO (divergenza dichiarata): qui assente = quasi (default del branch 538 invariato, cosi' i suoi test non si rompono), letterale "false" = all-hard (controllo). Su quasihard-b il flag reale e' DEFAULT OFF (all-hard) con ON = quasi: le due forme sono complementari, il banco misura la stessa proprieta' nei due versi.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Banco TASK-566 eseguito e VERDE (branch quasi-bench-566, pushato 46d9adbd, suite 1116/0 EXIT 0). La proprieta' di attraversamento del tier e' dimostrata meccanicamente: le STESSE violazioni (3 giorni sotto quota, deficit 2 per giorno = -6) atterrano su hard[0] (muro, controllo con routing all-hard) vs hard[1] con hard[0] PULITO (trattamento, tier armato). La gamba controllo copre TUTTE e 5 le famiglie 537 (H11/H31/H32/H33/H-COPRESENCE, fixture per-famiglia che sparano davvero) + H1 negativo immutato. PREDIZIONE PRIMA registrata e confermata braccio per braccio. NOTE PER QUASIHARD-B (differite, scritte nel javadoc): cattura duale solver-per-build vs director-snapshot-per-JVM (due bracci via app viva richiedono restart JVM), reporting quasi vacuo-verde sotto braccio controllo, suite girare al default non sotto -D. Gates: /simplify 4-agent (3 fix, 1 skip) + /code-review high 8 finding (5 fixati, 3 differiti documentati). H34: assente sul branch raggiungibile, la gamba corre quando quasihard-b viene pushato.
<!-- SECTION:FINAL_SUMMARY:END -->
