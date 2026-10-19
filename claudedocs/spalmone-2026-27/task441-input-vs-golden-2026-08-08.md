# TASK-441 residuo - Confronto KPI end-to-end source=input vs source=golden (zai, 2026-08-08)

Chiude il residuo TASK-441 "confronto derivazione input-only vs golden non ancora fatto"
(policy doc `task441-no-golden-policy-2026-08-08.md`). La domanda della direttiva
solo-input, verificabile solo finche' il golden esiste: il path **source=input** (quello
dell'esercizio reale, senza golden) produce un orario comparabile al path **source=golden**?

**Risposta: SI'.** I due path sono comparabili: entrambi hard=0 (via multi-start pick-best),
KPI strutturali identici (0 buchi, day-start clean), source=input leggermente indietro sui
medium (coerente col quadro: il path input e' il piu' duro, bad-seed H17 + constrained-teacher
allocator lavora di piu'). Il path no-golden e' viable.

## Setup

Entrambi i solve su questa laptop (Fedora offline), stessi driver ratificati, cattura canonica
(`capture-run.sh`). Within-machine comparability (laptop-RUNBOOK: i tempi assoluti non sono
cross-macchina).

- **source=golden** (golden path): `baseline-golden.sh`, profile `macbase`, capture in
  `baseline-laptop-2026-08-08/`.
- **source=input** (no-golden path): `cert-source-input.sh` (`-Dplanner.pre-assign.
  constrained-teacher.source=input`), profile `cert`, capture in
  `cert-input-vs-golden-2026-08-08/`.

Entrambi: staged cycle, multi-start ON (maxAttempts=3), M-TC per-retry ON, repair ON, tutti
i flag ratificati DEFAULT. environment NON_REPRODUCIBLE (singolo run per lato, varianza LS).

## Confronto KPI

| KPI | source=golden | source=input | delta | read |
|-----|---------------|--------------|-------|------|
| **hard** | **0** | **0** | = | entrambi 0 via multi-start pick-best |
| ricorrenza unpinned | 75,9% | 74,6% | -1,3pp | input leggermente piu' disperso |
| full-day | 86,8% | 84,4% | -2,4pp | input leggermente meno compatto |
| buchi interni | 0 | 0 | = | identico (strutturale) |
| day-start (scarti >=3) | none | none | = | identico (strutturale) |
| medium | -1.257k | -1.323k | -66k | input leggermente peggio |
| multi-start attempts | 2 | 2 | = | entrambi bad-ish seed, curati dal retry |
| wall | ~109 min | ~112 min | ~ = | confrontabile |

## Lettura

1. **Hard binario identico (0 = 0).** Entrambi i path raggiungono 0hard via multi-start
   (TASK-483): il source=input ha il bad-seed H17 (G11 mercoredi' ore 10) che il primo
   tentativo non chiude, ma il retry (GRASP) lo cura a 0hard. Stesso meccanismo del golden
   path su questa laptop. La robustezza ratificata (multi-start) tiene su entrambi i lati.
2. **KPI strutturali identici** (0 buchi, day-start clean): la struttura dell'orario e'
   la stessa. Le differenze sono nei medium di_qualita' (ricorrenza, full-day), dove input
   paga ~1-2,4pp. Coerente: il path input ha il constrained-teacher allocator che lavora
   senza il golden (source=input) e il bad-seed richiede il retry.
3. **Nessuna regressione strutturale**: il path no-golden non perde ne' hard ne' forma.
   La differenza medium e' entro la banda di varianza run-to-run (NON_REPRODUCIBLE) e nello
   spirito del "path input e' leggermente piu' duro" del quadro (campaign-status-2026-08-05,
   TASK-435 ratifica).

## Verdetto per TASK-441 (direttiva solo-input)

**Il path source=input (no-golden) e' viable: produce un orario hard=0 strutturalmente
identico al golden path, con KPI medium entro ~1-2,4pp.** La direttiva e' rispettata: quando
il golden sparira', il path input consegna un orario valido. Il debito golden-runtime
(TASK-452, #1 ConstrainedTeacherAllocator DEFAULT ON) e' confermato non-bloccante per la
qualita' dell'orario (lavora su source=input e produce 0hard).

Questo CHIUDE il residuo "confronto end-to-end" della policy TASK-441. Le verifiche
"input-vs-golden" della finestra sono ora tutte fatte: #1 docente vincolato (censimento),
M21 (script), cadenza (censimento), e questo confronto solve end-to-end.

## Limiti
- Singolo run per lato (NON_REPRODUCIBLE): i -1,3/-2,4pp hanno varianza. La FORMA (hard=0
  entrambi, strutturali identici, input leggermente indietro) e' il segnale robusto.
- source=input su questa laptop ha preso 2 tentativi (112 min): il bad-seed H17 e' quello del
  m-13 block. Il multi-start lo cura (0hard), ma il wall e' 2x il good-seed. Per la campagna
  locale, conteggiare 1-3 tentativi per i solve source=input.
