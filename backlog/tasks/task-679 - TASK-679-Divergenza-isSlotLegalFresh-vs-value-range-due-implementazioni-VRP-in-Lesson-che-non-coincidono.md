---
id: TASK-679
title: >-
  TASK-679-Divergenza isSlotLegalFresh vs value range due implementazioni VRP in
  Lesson che non coincidono
status: Done
assignee: []
created_date: '2026-10-04 21:09'
updated_date: '2026-10-05 06:05'
labels:
  - claude-planned
  - bug
  - solver
  - vrp
dependencies: []
priority: high
type: bug
ordinal: 327000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Ritrovato dal banco TASK-678 (04/10 sera): Lesson ha DUE implementazioni della legalita' VRP che DIVERGONO. isSlotLegalFresh (usato dai pass deterministici come gate O(1) anti stale-cache, TASK-578) ha fatto passare l'atterraggio di Confezione(8263, G14) su 2027-05-07@11 mentre il value range del director (getAvailableTimeslots, stessa classe) lo esclude: il director ha lanciato IllegalStateException in computeInitializationStatistics ('assigned outside the related value range'). CAUSA PROBABILE: le due implementazioni hanno predicati di scope diversi sulla regola quota (il fresh usa !dailyQuotasByDate.isEmpty() && !pseudo, il cached usa un gate quotaGroup; e la forma di permits e' consultata con argomenti diversi: Lesson.java ~272 vs ~324 vs ~395). IMPATTO: qualunque pass che fidisi di isSlotLegalFresh puo' piazzare lezioni in celle che il solver rifiutera' al prossimo avvio (la classe di crash SurplusHarvest, rinascente). LAVORO: allineare le due implementazioni su un unico predicato (o far delegare il fresh al range con cache pulita), con un test di equivalenza su un campione di lezioni x slot (la forma test: per N lezioni e M slot, assert fresh(lesson, slot) == range.contains(slot)); il banco 678 tiene il caso Confezione come regression pin (il gate VRP-RANGE aggiunto in EDF e' il ripiego locale, da rimuovere quando il difetto radice e' curato).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-04 21:31
---
NOTA aggiuntiva (04/10 notte, dal revert dell'hoist): spostare il gate VRP-RANGE da extractionFor a landingRejectReason (strictly-conservative in teoria) ha fatto crashare il banco via una CASCATA: le famiglie relay/compound, private dei loro movimenti marginali, sono scivolate su un percorso di mosse NON guardato dal range (le SLIDE del donatore / i refill del relay applicano applyMove con controlli propri H1-only). Quindi esiste almeno un percorso di mosse del pass che bypassa sia legalLanding che il range: rientra esattamente in questo task (allineare o guardare anche quel percorso). Il gate resta INLINE al sito extraction (known-good, banco 3/3 + unit 9/9).
---

author: claude
created: 2026-10-05 05:44
---
DIAGNOSTICA ESEGUITA (05/10, VrpConsistency679Test, env PLANNER_TEST_679): (1) AC1 EQUIVALENZA VERDE: 7.229.376 controlli (tutte le lezioni x tutti gli slot di lun+ven+primi 5 giorni), ZERO disaccordi fra isSlotLegalFresh e getAvailableTimeslotsFresh: le due catene di filtri sono PREDICATO-IDENTICHE. La tesi originale del task (divergenza di predicati sulla regola quota) e' DISPROVATA. (2) ROOT-CAUSE PROBE VACUO: sul ciclo di vita pulito (seed + statics pubblicate + cache computata dopo) NESSUNA lezione ha uno slot fresh-legale fuori dal range cachato: la classe 'cache stantia' non e' riproducibile senza mutazione/statics-repubblica in mezzo. (3) REINTERPRETAZIONE del crash Confezione della run-2 del banco: quasi certamente INQUINAMENTO CROSS-TEST nello stesso JVM (mutazioni in-memory su entita' gestite + flush accidentale durante la transazione di analyzeScore del test successivo), non un difetto di Lesson: le run successive con il gate range + restore-per-id completo sono stabili 3x3/3 e il crash non e' mai piu' comparso. STATO: il gate VRP-RANGE in EDF resta come cintura (economico, corretto, coerente col director); il test AC1 resta come PIN DI REGRESSIONE permanente (7.2M controlli); il task si chiude con la tesi ridotta: nessun difetto dimostrabile in Lesson, il residuo possibile (cache-through-mutation) e' coperto dal pattern banco restore-per-id + gate.
---

author: claude
created: 2026-10-05 06:05
---
CORREZIONE POST-REVIEW (05/10, /code-review high: 8 finding, tutti applicati): (1) AC1 esteso alla GRIGLIA COMPLETA con HashSet per lezione: 17.470.992 controlli, ZERO disaccordi; aggiunto il pin delle statics vive (allTimeslots == Timeslot.count()) perche' l'equivalenza certificata dev'essere contro il DB corrente, non un set stantio lasciato da un test precedente. (2) IL ROOT-CAUSE NON E' VACUO, E' RIPRODOTTO: il probe riscritto (cache computata, lezione mossa fuori range, lettura della cache) dimostra la meccanica esatta del crash: la clausola grandfather del range si lega allo slot AL MOMENTO DEL CACHING, la lezione mossa cade fuori dal PROPRIO value range e la cache non si auto-sana -> il prossimo update() del director lancia. Quindi: PREDICATI IDENTICI (17M), CRASH = CACHE-STALENESS-THROUGH-MUTATION. (3) Commenti EDF riallineati (il gate VRP-RANGE e' cintura PERMANENTE, la condizione di rimozione e' ritirata); (4) il percorso slide/refill non guardato ha ora il suo owner: TASK-680; (5) DoD spuntate (la /code-review skill e' girata davvero, evidenza nel transcript).
---
<!-- COMMENTS:END -->
