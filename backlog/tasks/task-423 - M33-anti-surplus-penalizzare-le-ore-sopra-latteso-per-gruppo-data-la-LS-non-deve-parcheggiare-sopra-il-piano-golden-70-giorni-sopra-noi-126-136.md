---
id: TASK-423
title: >-
  M33 anti-surplus: penalizzare le ore sopra l'atteso per (gruppo, data) - la LS
  non deve parcheggiare sopra il piano (golden 70 giorni sopra, noi 126-136)
status: Done
assignee: []
created_date: '2026-07-26 21:44'
updated_date: '2026-07-27 04:47'
labels:
  - claude-planned
dependencies: []
priority: medium
ordinal: 246000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Autorizzazione maintainer 2026-07-27 ~00:40: verificare coi checkpoint e infilare nel treno notturno (o successivo) SENZA input suo. Regola human-planner: mai sopra la capienza del giorno, resto in panchina - in costruzione gia' rispettata (stamp cap TASK-385 + box-the-residual), la viola la LS perche' niente penalizza il surplus. DESIGN: M33 medium, penalizza max(0, occupancy - dailyQuota(group,date)) per (gruppo, data); occupancy = stessa base di M20/horizontallyCompact (forEachIncludingUnassigned + timeslot!=null + !isAuthoritativePin + !G14), quindi VACUO sul golden guard 100%-pinned (pin zero-tolerance come M20); la calibrazione vs pratica golden (70 giorni sopra = livello lecito, mai piu' rigido) passa dal KPI daily-fill 'sopra', non dal guard. Peso: start low (candidati ONE_MEDIUM..100/ora eccesso; il trade tipico che deve tiltare: lezione su giorno pieno vs giorno in deficit legale gia' vale 1000+ di M20, quindi M33 serve sui casi dove M32/S-tier compensano - da misurare). CATENA VALIDAZIONE (tutta checkpoint): L0 ConstraintVerifier (giorno a quota+2 penalizza 2, a quota 0, pinned-exempt); golden guard INVARIATO (M33=0 strutturale); re-score statico train-run1/run2 (quanti dei 126-136 giorni sopra sono unpinned-driven = pressione reale); 2x Phase2Smoke 600s NON_REPRODUCIBLE vs i 3 P-run della batteria in /home/user/data/tmp/battery (metrica decisiva: giorni-sopra in calo, ricorrenza/M15/deficit-profondita' NON peggiorati). Se verde -> treno + run3 laptop (staged+422+M33); se murky -> evidenza pronta per il risveglio.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DoD OBBLIGATORIA anche qui (maintainer 2026-07-27: '/simplify e /code-review high sempre, impatto nettamente positivo'): dopo l'implementazione e PRIMA di dichiarare M33 nel treno: /simplify 4 angoli sul diff + code-review high (finder + verificatori, replicato a mano se lo skill non e' invocabile) + suite EXIT=0, oltre alla catena checkpoint gia' specificata.

WATCH-ITEMS dal round /simplify + code-review (2026-07-27), da rileggere sull'A/B live:
(a) SURPLUS FLAT vs PROGRESSIVO. Profondita' del surplus (quante ore sopra il piano per giorno-sopra): golden {1h:41, 2h:25, 3h:3, 4h+:1}, nostri train-run {1h:87-93, 2h:9-15, 3h:5, 4h+:10-11}. La coda 4h+ e' ~10x il golden. M33 e' oggi FLAT (100/ora): se la coda profonda sopravvive ai run con M33 attivo, il follow-up naturale e' una progressione sul surplus (stessa forma di m20DeficitCost sul deficit), non un aumento del peso piatto.
(b) M22 E' 10:1 SU M33. M22 costa 1000 per giorno-extra, M33 100 per ora-sopra: comprimere in meno giorni resta ~10x piu' conveniente che rispettare il piano giornaliero. Se nell'A/B live i giorni-sopra NON calano, il driver e' M22 e non il peso di M33: guardare li' prima di toccare 100.
(b2) I PRICER DEI REPAIR NON HANNO ANCORA IL TERMINE M33. La famiglia m20DayCost (m20MarginalRemovalCost/m20MarginalFillGain, usata da SurplusRebalance/BoxRepair/HoleRepair) prezza solo M20+M20.2, quindi un giorno sopra quota legge ancora costo 0. Direzione conservativa (ranking monotoni, delta sottostimati di 100/ora di surplus), quindi accettabile ad interim: fold del termine M33 nei pricer = follow-up gated sul benchmark, non un fix di correttezza. Premessa aggiornata nel javadoc di SurplusRebalance.
(c) M33 RESTRINGE LA POPOLAZIONE DI SurplusRebalance. Il pass TASK-422 cerca giorni-sopra da cui prestare ore: con M33 attivo la LS ne produce (auspicabilmente) meno, quindi i numeri del checkpoint 422 (35 mosse) vanno RIMISURATI post-M33 prima di giudicare quel pass.
(d) PIN-BLINDNESS: 29 dei 112 giorni-sopra di train-run1 sono INVISIBILI a M33, perche' la popolazione condivisa con M20 conta solo le ore non-pinnate (un giorno 3 pin + 5 libere e' realmente a 8 su quota 6, ma ne conta 5 e non paga). Non si corregge in M33 (deve contare esattamente cio' che conta M20, o le due facce dello stesso target divergono e il solver ci arbitraggia): causa radice = TASK-424. Semantica attuale PINNATA da un test dedicato (m33_notPenalized_mixedPinnedDay_documentedPinBlindness).
(e) Popolazione ora CONDIVISA (non piu' ristatuita): M20 e M33 leggono entrambi dailyLessonOccupancy(constraintFactory), quindi la divergenza e' strutturalmente impossibile e BAVET mantiene un solo nodo groupBy incrementale per entrambi. M33 registrato anche in getCompactionOnlyConstraints (completezza della famiglia day-fill in debug mode).
(f) STAGE-ONLY ROWS NON COPERTE DA H26: il gate quota>0 esenta anche i giorni con riga stage-only (aula+lab=0), dove H26 non garantisce nulla (la riga esiste, quindi isNoSessionDate e' false). Misurate 12 ore-lezione su 3 giorni in train-run1 = il conflitto documentato foglio-vs-golden del 2027-04-23/26. Piccolo e spiegato, ma e' li' che risalirebbe se la scuola risolvesse il conflitto al contrario.

LIVE x2 (train2): M33 69/77 match (-11,2k/-12k), giorni-sopra 111/118 (da 126-136), full-day +1,5-2pp vs treno-1, ricorrenza/M15/burst in banda: tutti i criteri A/B confermati live. Watch M22-driver: non necessario invoreferente, sopra E' calato. Coda +4h da rimisurare su questi run per la decisione progressione-surplus.

Adottata col treno-2 (ratifica piena 2026-07-27): live x2, DoD completa. Chiusa; la ricalibrazione peso/curva vive in TASK-427 (condizionale post-424).
<!-- SECTION:NOTES:END -->
