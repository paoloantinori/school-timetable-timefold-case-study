---
id: TASK-497
title: >-
  Cappare il target M20 al pool del gruppo: min(capienza, ore collocabili)
  (istanza TASK-461 confermata)
status: Done
assignee: []
created_date: '2026-08-14 10:29'
updated_date: '2026-08-14 11:12'
labels:
  - claude-planned
  - gated-maintainer
milestone: m-13
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spin-off dalla chiusura TASK-461 (istanza confermata: G04 85h, G08 24h, G09 ~4h di bersaglio M20 irraggiungibile; floor medium impossibile ~113-129k che distorce i compromessi). PROPOSTA GATED maintainer: cappare il target M20 al pool del gruppo. Due forme candidate (dal task 461): (a) scalare la quota giornaliera per obbligo/capienza; (b) cappare la quota giornaliera al residuo di monte-ore del gruppo. Composizione: il cap si applica DENTRO effectiveQuota (TASK-424, netto dei pin autoritativi) cosi' M20/M20.2/M33 e i pricer condivisi (BoxRepair/HoleRepair/SurplusRebalance) ereditano la forma corretta. Gate A/B standard: hard 0, KPI di guardia, medium atteso ~-113k o meglio (rimozione penalta' impossibile + meno distorsione). La pratica golden avalla la semantica (deficit sparso a -1h, obbligo come target). FLAG default-OFF, lettura LIVE come le altre. NOTA: e' una modifica di MODELLO: si giudica sulla correttezza logica (il target non puo' superare cio' che il gruppo puo' collocare), direttiva 2026-07-21.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
NUMERI VALIDATI CON SCRIPT (Fedora 14/08, ogni conto ricomputato dai sorgenti): G04 incarichi 750 (18 righe verificate), DAQ aula 623 + lab 212 = 835, stage 240 su 30 date (2 delle quali off-grid sabato non nel golden), griglia 1075 = 990 + 85. Golden: 750 reali (745 su date a quota + 5 su date a quota 0) + 224 stage su 28 date sempre 8h con zero lezioni sovrapposte, zero sabato/Fasullo/Esame. Gap golden: esattamente {1h: 90 giorni}, 90 - 5 = 85 = 835 - 750 = 1075 - 990 (identita' chiusa da entrambi i lati). Pratica golden = semantica del cap (obbligo come target). G08 690/666 = 24h.

DOMANDE referente REGISTRATE (domande-brevi.md revisione 14/08): (A SI/NO) giorni corti G04 voluti (90 x -1h); (A SI/NO) idem G08 (~24h); (C) le 5 ore golden su date a capacita' zero: reali o calendario mancante?

PIANO NOTTE 14/08: prototipo flag-gated planner.constraints.m20-cap-pool.enabled DEFAULT OFF + unit test; A/B domani (NON stanotte: il census flip#1 ha la priorita' sul solve host).
<!-- SECTION:NOTES:END -->


## Night implementation log (laptop, 2026-08-14)

PROTOTIPO IMPLEMENTATO su branch worktree `worktree-multithreaded-ls-experiment` (commit 190f2195, pushed). Flag `planner.constraints.m20-cap-pool.enabled` DEFAULT OFF, forma (b) esatta: per-(group,date) targets con SOMMA ANNUA = pool, pubblicati PRE-solve accanto al ratio TASK-461; capSupply (chokepoint unico sotto dailyQuota) usa il target esatto quando armed, byte-identical altrimenti.

SCOPERTA PRE-IMPLEMENTAZIONE (verificata su DB + banner live): il cap TASK-461 (forma a, ratio) e' LIVE dal 30/07 (commit 8e51babc) NONostante le note 461/497 dicano il contrario. Il suo difetto e' il motivo della forma (b): Math.round per giorno lascia il floor di G08 intatto (ratio 0.9399: nessun 6 scende a 5, le 42 giornate -1h del census baseline sono quel floor) e over-cutta G04 (690h target vs 750 pool, surplus forzato verso M33). I numeri del banner confermano: {G04=0.898=750/835, G08=0.9399=626/666, G09=0.994}. NOTA G08: il pool 626 esclude le 40h del corso RA (docente placeholder, DataGenerator non crea lezioni): il ratio e' coerente col pool collocabile REALE.

REVIEW (simplify 4-agent + code-review high): fixati hot-path flag capture (volatile once-per-setup), dup loop estratti in helper condivisi, floor 0 allocator (pool<giorni converge), clearStatics famiglia completa (incl. ratio TASK-461, gap pre-esistente), SnapshotFixture reset, banner armed=%b veritiero. DEFERITI al task: construction-phase bypass (expectedHoursCapFor non applica nessun cap: ereditato da TASK-461, se il prototipo passa il gate va unificato); distribuzione shortfall per gruppi non-G04 = euristica (domanda workbook se la scuola pratica altro).

DoD: /simplify + /code-review high FATTI; test.sh full + commit su branch principale = domani con l A/B (stanotte il census ha la CPU).

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
