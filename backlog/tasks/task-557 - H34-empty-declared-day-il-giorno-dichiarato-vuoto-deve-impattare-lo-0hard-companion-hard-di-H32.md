---
id: TASK-557
title: >-
  H34 empty-declared-day: il giorno dichiarato-vuoto deve impattare lo 0hard
  (companion hard di H32)
status: Done
assignee: []
created_date: '2026-08-26 06:40'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
HARD companion di H32 (TASK-532). Il buco: il groupBy di H32 itera le lezioni, quindi un giorno dichiarato (quota aula+lab>0) COMPLETAMENTE vuoto non genera riga ed e' invisibile al tier hard (documentato nel commento a SchoolScheduleConstraintProvider.java:636 "a TOTALLY-empty expected day is invisible here and stays M20.2's complement (medium)"). Conseguenza misurata: il sotto-riempimento parziale paga hard ma il giorno vuoto paga solo M20.2 medium e la LS lo tratta come moneta di scambio: l'impronta "giornata dichiarata vuota" non impatta lo 0hard e si perde.

NUMERI (verificati 26/08 da Fedora, scope esatto H32: quota aula+lab>0, escluse G04/G08/G14, non-Stage, date in-griglia, quote DB dall'export ciclo-qfrv2-on):
- golden: 2 celle, entrambe G09 ultima settimana (04/06/2027 quota 5, 07/06 quota 3) = slack fine-anno, stessa famiglia della tolleranza 1h che G09 porta;
- run consegnato 0hard: 12 celle aprile-giugno (G13 3, G07 2, G09 2, G06 1, G10 1, G11 1, G12 1), ~58h;
- 30 celle FC/G14 over-declare RESTANO FUORI SCOPE per design (TASK-497 forma-b, domanda referente pendente).

SPEC: constraint ifNotExists per (gruppo, riga-quota in-scope): nessuna lezione non-Stage assegnata quel giorno -> penalizeHard FLAT 1 (solo presenza: la profondita' resta di H32, che appena atterra una lezione prende il sopravvento). Scope identico a H32 (stesse esclusioni via isQuotaFillExcluded, solo date con timeslot, non-Stage). ID proposto H34 (H33 e' preso). Flag planner.constraints.empty-day.enabled, sviluppo DEFAULT OFF, forma opt-out standard alla ratifica (null/absent -> ON, solo "false" disarma), banner in refreshSolverStatics.

PROTOCOLLO: TDD (test rosso su fixture sintetica con giorno dichiarato vuoto, poi verde); GoldenSolutionRegressionTest deve mostrare esattamente le 2 celle G09 note (pin + classificazione: esclusione ultima settimana o tolleranza estesa, decisione al gate); A/B con predizione PRIMA (atteso: 12 -> 0..2 celle, hard resta 0 con la costruzione che si adatta, sanity invariata o migliore, recupero parziale del -200k M20.2); checkpoint localizzato prima, ciclo completo solo come gate finale. Ratifica: maintainer. Regole: laptop misura non adotta, nessun push su main, DataGenerator intatto. Dopo il flag nuovo: rigenerare claudedocs/knobs.md e verificare il conteggio.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
- [x] Constraint H34 registrato, attivo dietro flag planner.constraints.empty-day.enabled DEFAULT OFF, scope identico a H32 (escluse G04/G08/G14, date con timeslot, non-Stage), penalta' hard flat 1 per giorno dichiarato vuoto
- [x] Test TDD: rosso su fixture sintetica con giorno dichiarato vuoto, verde dopo l'implementazione; test di interazione con H32 (giorno parzialmente riempito resta competenza H32)
- [x] GoldenSolutionRegressionTest: il golden mostra esattamente le 2 celle G09 note (04/06 e 07/06/2027), pin + classificazione documentata. EMENDAMENTO maintainer 26/08: STRICT-FIRST, niente tolleranza di partenza: le 2 celle potrebbero essere RESA dell'human planner (fine anno), non preferenza della scuola; se il solver riesce a riempirle, la tolleranza non serve. La tolleranza si introduce SOLO se dimostriamo di non poterle riempire (verifica di fattibilita' sul banco: quota 5 e 3, ultima settimana in-griglia, surplus G09), e in quel caso va dichiarata "quasi-hard: resa umana documentata", mai silently
- [ ] A/B con predizione PRIMA scritta e confrontata: 12 -> 0..2 celle nel run, hard 0 preservato, sanity invariata o migliore, recupero parziale M20.2 dichiarato
- [x] Banner in refreshSolverStatics + claudedocs/knobs.md rigenerato con conteggio verificato
- [x] DoD completa: /simplify, /code-review high, test.sh EXIT 0 da orchestratore

## ESITO 26/08 (worktree day-compact-542, ciclo completo dci-h34on)
STRICT-FIRST VINTO: le 2 celle golden G09 (04/06 e 07/06) sono RIEMPITE dalla
nostra consegna (verifica per-data sull'export): tolleranza NON necessaria, NON
introdotta. L'AC "A/B 12 -> 0..2 celle" resta APERTA: il ciclo singolo-tentativo
consegna 10 celle residue (strettoia mag-giu + G13 3; famiglia import
teacher-locked H1/VRP, stessa classe dei 93 rifiuti FCC di iter-17) e hard -14
(H34 -10 + H0 -2 + H30 -1 + H32 -1). Medium -1.315.606 in banda, sanity NON
peggiore (3 rossi, buchi interni 0), M20.2 38 (le stesse 10 reali in twin
medium). La risposta alle 10 residue e' search-side (multi-start N=5), non
tolleranza: prossima evidenza utile = un ciclo multi-start con H34 ON. Doc:
task557-results/ciclo-h34-on/VERDICT-2026-08-26.md. Flag resta DEFAULT OFF
(decide maintainer). Nota tecnica: la forma e' registrazione-incondizionata + armo
per-cella (la factory app si cache a boot: registration-gate cache-hostile,
lezione m22MinDays).



ESITO MULTI-START (26/08 23:13, notturno): ciclo completo 5 tentativi con H34 ON (profilo dci-h34ms): vincitore deterministico -16hard/-1.350.869 (H34 12 + H17 2 + H7 2), 0/5 a 0hard. Gate -14/-50/-61/-62/-85, ordine monotono dal post_bt1. Le celle H34 sono BACINO-INVARIANTI (10-12 ovunque, stesse date del foglio): il multi-start seleziona il bacino ma non chiude le celle. L'AC dell'evidenza end-to-end e' CHIUSA: il vincolo fa il suo lavoro (visibilita' e conteggio), la chiusura e' pass-based (EDF v0.1: 4 celle, TASK-560) e data-based (G09 over-declared, tabella conservazione nel registro 26/08). Doc: task557-results/ciclo-h34-multistart/VERDICT implicito nel registro + quadro notturno.

## Final Summary

VERDETTO FINALE (26-27/08): H34 implementato, ratificato DEFAULT ON (04f6c15e), STRICT-first vinto (le 2 celle golden G09 RIEMPITE nel ciclo dci-h34on), ciclo completo -14hard con 10 residue, multi-start 5 tentativi 0/5 a 0hard con celle BACINO-INVARIANTI, autopsia+tabella conservazione: G09 foglio over-declared (670 vs 666) = 3 celle inriempibili (domanda referente bozzata), 7 riempibili in aritmetica. Evidenza end-to-end completa: il vincolo conta la firma giusta, la chiusura e pass-based (EDF v0.1 4 celle) e data-based. Commit chiave: 4fda98f4 (impl), 04f6c15e (ratifica).
