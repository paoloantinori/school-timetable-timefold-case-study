# SPIKE S1 - Esenzione isG14 ancora necessaria? (2026-07-30 notte)

**Domanda:** l'esenzione `isG14` (SSCP:1903, G14 exempt da day-fill M20/M33) e'
ancora necessaria? Era giustificata dal dato 990h (sbagliato); G14 ora ha 120h
reali (TASK-462). Medoto richiesto: togliere l'esenzione in-harness e misurare al
**checkpoint di costruzione** (WeeklyTemplateConstructionRealSeedTest).

## Misurazione (A/B, stessa macchina, ~1min/run)

| metrica (construction-end) | isG14 ON (baseline) | isG14 OFF (spike) |
|---|---|---|
| boxing "could not place" (linee) | 70 | **70 (identiche)** |
| G14 boxed | 30 | **30** |
| ricorrenza TASK-381 L2 | 76.1% | **76.1%** |
| full-day RAW (14 gruppi) | 74.7% | **74.7%** |
| full-day scoped (excl G14+stage) | 85.5% | **85.5%** |

Togliere l'esenzione e' un **NO-OP completo al checkpoint di costruzione**: boxing,
ricorrenza e full-day sono byte-identici. I conteggi per-gruppo del boxing coincidono
(G06 62, G10 61, G11 42, G13 39, G14 30, ...).

## Verdetto: INCONCLUENTE a questo checkpoint. L'esenzione e' LOCAL-SEARCH-only.

`isG14` governa `effectiveQuota`/`dailyQuota`/`NOT_G14` (M20/M33/cadenza), che
sono vincoli di **ricerca locale**, non di costruzione. Il WeeklyTemplateBuilder e'
puro-Java (senza accoppiamento Timefold): il suo target di fill per G14 NON passa
dall'esenzione, quindi la costruzione e' identica con o senza. Il checkpoint di
costruzione non esercita l'M20, quindi **non puo' rispondere** "l'esenzione serve ancora?".

**Tenerlo o buttarlo (per il QUADRO):** TENERE, ma la ragione va aggiornata. G14 e'
part-time (calendario: solo GIO 0-4, VEN 0-2; lun/mer/mar chiusi): i suoi giorni sono
sotto-riempiti **per natura**, e senza esenzione l'M20 li penalizzerebbe pesantemente
durante la LS, spingendo il solver a forzare/distorcere. Il dato 990h e' stale, ma la
natura part-time resta: l'esenzione fa ancora lavoro utile, per il motivo sbagliato
nel commento. La domanda reale (rimuoverla aiuta o guasta?) richiede un **A/B full-solve**
(gate del mattino con maintainer), NON questo checkpoint.

## Lezione di metodo (per S3 e futuri spike-al-checkpoint)
Un vincolo di ricerca locale (M20/M33/isG14) **non si vede al checkpoint di
costruzione**: il builder e' pre-score. Verificare al checkpoint un parametro che agisce
solo in LS produce un no-op silenzioso. S3 (M20_PROGRESSION_STEP=1500) ha lo stesso
rischio: probabilmente no-op al checkpoint per la stessa ragione (vedi doc S3).
