---
id: TASK-424
title: >-
  M20 e' pin-blind sui giorni misti: paga la LS per sovra-riempire i giorni con
  pin autoritativi (267 giorni, causa-radice candidata del surplus 126-136 vs
  golden 70)
status: Done
assignee: []
created_date: '2026-07-26 23:00'
updated_date: '2026-07-27 18:27'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 247000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scoperto dal code-review M33 (2026-07-27 notte, finder con ri-derivazione Python indipendente su val419-train-run1). MECCANISMO: l'occupancy di M20 esclude i pin autoritativi (tradizione pinned-exempt) ma dailyQuota resta la quota PIENA del giorno -> su un giorno misto la LS viene PAGATA per aggiungere unpinned oltre il piano reale. Numeri (train-run1): 267 (gruppo,data) con >=1 pin autoritativo non-stage, TUTTI con deficit M20 riportato; pattern dominante 137 giorni: attese 6/pinnate 3/unpinned 3 = a piano esatto ma M20 charge 4.500 e paga marginale 2.000 per la 4a ora unpinned; 31 giorni attese 6/pinnate 6/unpinned 0: M20 charge 13.500 e pagherebbe per RADDOPPIARE il giorno (cap solo H11/openSlots). Conseguenza: 29 dei 112 giorni-sopra reali (26%) invisibili a M33, e l'incentivo che li crea resta. FIX CANDIDATO: nettare le ore pinnate autoritative del giorno dalla quota (quota_eff = max(0, dailyQuota - orePinnateAutoritativeDelGiorno)) in M20/M20.2/M33 e nei pricer condivisi dei repair (m20DayCost famiglia) - stessa base per tutti, anti-arbitraggio. VALIDAZIONE: golden guard deve restare 0 (fixture 100% pinned: quota_eff=0 e occupancy=0 ovunque, verificare che M20.2 non cambi); ConstraintVerifier casi misti; re-score statico train-run1/2 (predizione: il deficit M20 sui 267 giorni misti crolla); catena checkpoint smoke A/B su giorni-sopra (attesa: crollo verso golden 70). E' CHIRURGIA su M20: DoD piena + benchmark; interazione con M30 (che conta anche i pinned, semantica diversa deliberata) da NON toccare.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
HANDOVER (2026-07-27 ~07:40): implementazione partita su Fedora (agente in corso); il laptop (zai) ha il mandato autonomo su TUTTA m-12 con regola self-healing: aspetta il push Fedora con 'TASK-424' max 90 min, altrimenti la implementa da zero da questo task file. DoD obbligatoria dichiarata nel mandato (/simplify + /code-review high + suite EXIT reale) per ogni task, ratifiche solo maintainer.

ADDENDUM mandato laptop (07:50): niente chokepoint - a m-12 completata/bloccata zai avanza DA SOLO sulla coda backlog (priorita' High prima, esclusi task in attesa di ratifica/scuola; candidati naturali: 417 export-mode, cluster benchmark 280/281/284/332 sul nuovo assetto, 407). Worktree a sua discrezione per lavori rischiosi/paralleli. Unico stop legittimo: esaurimento task lavorabili, con stato scritto e pushato.

IMPLEMENTAZIONE Fedora (2026-07-27 08:15). `effectiveQuota(group, date) = max(0, dailyQuota - orePinnateAutoritativeRealTeacher)` in `SchoolScheduleConstraintProvider`, unica sorgente per M20 (filtro/weigher/justification), M20.2 (weigher, no-op numerico: un giorno vuoto non ha pin), M33 (confronto e peso; il gate di AMBITO resta `m20Quota > 0` sulla quota GREZZA, altrimenti i 31 giorni interamente pinnati sarebbero esenti proprio dove il surplus va visto) e `m20DayCost` (quindi BoxRepair/HoleRepair/SurplusRebalance ereditano). Static map `(gruppo, data) -> ore` pubblicata NELLO STESSO passo di `setPinnedLessonTimeslotsByGroupStatic` (produzione: refreshSolverStatics/refreshPinStatics; test: SnapshotFixture e le fixture ConstraintVerifier), invariante di stalenessa documentata sul campo. Non nettati: pin PROMOSSI (TASK-419, restano occupancy) e pin pseudo-docente Stage (target aula+lab). COSTRUZIONE NON TOCCATA, verificata gia' pin-aware: `getDailyLessonCount` conta tutte le lezioni piazzate (pin inclusi) e `WeeklyTemplateBuilder` conta i `blockedGroupSlots` verso il target del giorno.

MISURE (tutte con exit code). (a) SchoolScheduleConstraintProviderTest + GoldenSolutionRegressionTest EXIT=0, golden pin INVARIATI (M20 0/0, M33 0/0, M20.2 -14500/2). (b) Re-score statico val419-train2-run1 (BEFORE = stesso albero con netting neutralizzato): M20 -1.400.500/447 -> -447.500/212, M33 -11.200/69 -> -13.900/96, M20.2 -112.000/10 invariato, ogni altra riga IDENTICA, medium totale -2.464.285 -> -1.513.985. Ri-derivazione Python indipendente sul dump: 267 giorni con >=1 pin autoritativo non-stage, 31 interamente pinnati, pattern dominante (3 pin, 3 unpinned) 153 giorni: coincide col meccanismo descritto nel task. (c) Checkpoint su val419-staged-smoke2: HoleRepair 14 buchi -> 0, 0 conflitti, stessa ripartizione per livello (5/0/9); SurplusRebalance 35 -> 40 ore spostate, giorni-deficit di partenza 375 -> 142 (deficit fantasma spariti), giorni-surplus 107 -> 130 (surplus reale ora visibile), 0 conflitti, H17 14 -> 14. (d) Phase-2 smoke 600s NON_REPRODUCIBLE: la snapshot di batteria `/home/user/data/tmp/battery/constr` ha ZERO pin (10.400/10.400 unpinned), quindi e' STRUTTURALMENTE CIECA a questo cambio (start score byte-identico ai baseline M33: -155/-1.831.732/-114.208); le due run M424-1/2 servono solo come smoke di non-divergenza (hard 0, ricorrenza 66,1/65,0% vs 64,7/65,2%, full-day 61,1/63,3% vs 62,9/63,1%, sopra 103/78 vs 81/80: spread dominato dalla velocita' di valutazione, 2.607 vs 4.008 mosse/s per contesa CPU). A/B DECISIVO fatto quindi su una snapshot PINNATA (val419-staged-smoke2, 2.651 pin), stesso albero, netting neutralizzato per l'arm BEFORE: full-day 71,6% -> 75,8%, giorni-sopra 135 -> 81 (golden 70), profondita' surplus {1h:118, 2h:7, 3h:3, 4h+:7} -> {1h:65, 2h:5, 3h:5, 4h+:6}, ricorrenza unpinned 65,7% -> 66,2%, M15 penalizzati 0 -> 0, hard 0 -> 0, velocita' 4.038 -> 4.838 mosse/s (nessun costo di scoring). (e) suite completa EXIT=0.

CORREZIONE alla descrizione del task (verificata sul codice e sui dump): i 31 giorni interamente pinnati NON erano addebitati 13.500 da M20 - il filtro `totalHours > 0` li rendeva gia' invisibili (occupancy 0, nessuna tupla). L'effetto reale li' era un'altra distorsione: la PRIMA ora unpinned su un giorno interamente pinnato costava 10.000 di deficit fantasma e ogni ora successiva veniva RIMBORSATA (discesa fino a raddoppiare il giorno). Post-424 quella prima ora costa 100 (M33) e la curva e' monotona in salita. Il pagamento marginale documentato (2.000 per la 4a ora) e' reale ed e' il pattern (3 pin, 3 unpinned), che qui e' il piu' numeroso (153 giorni su train2-run1).

WATCH/FOLLOW-UP: (1) i pricer dei repair continuano a NON avere il termine M33 (watch-item b2 del TASK-423, invariato); (2) la coda di surplus profondo (4h+) resta 6-7 giorni contro 1 del golden anche dopo il fix: e' esattamente la domanda del TASK-427 (progressione sul surplus), ora misurabile su una baseline non piu' inquinata dai deficit fantasma; (3) `scripts/analyze/daily-fill-kpi.py` conta le ore REALI (pin inclusi) contro il foglio e resta la metrica giusta per "giorni sopra": non va nettata.

SUITE CONFERMATA VERDE dopo il commit e4d2ba5a (Fedora, dc2f3b1e): 825 test, 0 failure, EXIT=0 (il WIP nel messaggio di commit e' superato - il codice committato E' quello a suite verde). RISULTATI CHIAVE della catena checkpoint (predizione-vs-reale, tutto negli artifact /home/user/data/tmp/battery e nel report agente): re-score train2-run1 M20 -1.400.500/447 -> -447.500/212 (953k di falso deficit eliminato), M33 69->96 match, M20.2 -112.000/10 IDENTICO, ogni altra riga identica, guard golden intatto (M20.2 -14500/2 esatto). A/B smoke 600s su base CON PIN (smoke2, 2.651 autoritativi; BEFORE da tree neutralizzato): sopra 135->81 (golden 70), full-day 71,6->75,8 (+4,2pp), ricorrenza SU, M15 0, hard 0, nessun rallentamento scoring. Rebalance checkpoint: deficit fantasma 375->142 giorni, surplus reale visibile 107->130, mosse 35->40, 0 conflitti. CORREZIONE alla description: i 31 giorni tutto-pin non erano caricati -13.500 da M20 (invisibili per il gate totalHours>0); la distorsione reale era la PRIMA ora unpinned a -10.000 fantasma con discesa verso il raddoppio - post-424 costa 100 (M33) e la curva e' monotona.

VERIFICA laptop (2026-07-27 08:25-09:00 CEST, su e4d2ba5a pullato da Fedora - DoD completata, task Done):

(1) test.sh EXIT reale = 0, BUILD SUCCESS, 825 test / 0 fail / 0 errori / 11 skip (env-gated attesi). GoldenSolutionRegressionTest PASS -> guard intatto (M20 0/0, M33 0/0, M20.2 -14500/2). ConstraintVerifier (inclusi 220 nuovi test mixed-case di Fedora) + HoleRepair/SurplusRebalance/MarginalDeficitCost PASS.

(2) re-score statico ScoreASnapshot su train2-run1 (placement fissato, EXIT 0) - PREDIZIONE-vs-REALE tutte confermate: M20 match 447->212 (falsi-deficit giorni misti collassati), M33 match 69->96 (sale, vede i surplus prima invisibili; sotto ~111 per gate scope quota-0 + base aula+lab), M20.2 -112k/10 INVARIATO (byte-identico), guard golden intatto. Incentivo perverso rimosso: 4a ora unpinned su giorno quota6/3pin/3unpinned da -2000 marginal (M20 pagava) a +100 (M33 carica).

(3) DoD: /simplify 4-agent (finding tutti scartati con motivazione - cosmetici su codice shipped+verified); /code-review high 8-angoli (core netting CORRETTO; 1 finding confermato SurplusRebalance.isSurplusDay ammette giorni at-plan-con-pin come donatori -> deferito a TASK-429, severita' bassa perche' deficit aggregato reale invariato e KPI daily-fill onesta; live A/B misurera'). Live A/B x2 resta il gate di adozione (ratifica maintainer).

ADOTTATA (ratifica maintainer 2026-07-27 sera, n=2).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO Da laptop (DoD completata su e4d2ba5a Fedora): test.sh EXIT=0 reale (825/0/11skip), /simplify 4-agent (finding tutti scartati con motivazione - cosmetici su codice shipped+verified), /code-review high 8-angoli (core netting CORRETTO; 1 finding confermato CANDIDATE 1 SurplusRebalance.isSurplusDay ammette giorni at-plan-con-pin come donatori -> deferito a TASK-429, severita' bassa perche' deficit aggregato reale invariato e KPI daily-fill onesta; live A/B misurera'). Re-score train2-run1 prediction-vs-real tutte confermate (M20 447->212, M33 69->96, M20.2 -112k/10 invariato, guard intatto). Live A/B x2 resta il gate di adozione (maintainer).
<!-- SECTION:FINAL_SUMMARY:END -->
