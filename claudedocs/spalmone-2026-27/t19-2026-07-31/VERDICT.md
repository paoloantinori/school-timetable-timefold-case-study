# VERDETTO A/B T19: CONSTRAINED_TEACHER_MAX_WEEKDAYS 1 vs 2 (zai, 2026-07-31)

Golden-first pre-referente (referente irraggiungibile ~1 settimana; Q22 coperta solo leggendo il golden).
Full-solve **staged** entrambi i bracci, setup production fresco identico (drop-and-create + seed +
import-calendario + pre-assign), default omogenei (first-hour-slot ON, constrained-teacher ON).
HEAD `c6abbb16`, JAR rebuilt 10:24. SolverConfig canonico (tabu7). **Nessuna adozione** (misura).

## VERDETTO (3 righe, dai raw)
- **max-weekdays=2 e' NETTO NEGATIVO** sui KPI di qualita': medium -54k (-1025515 vs -971488),
  soft -12k (-5588 vs +6405), full-day -1,2pp (88,8% vs 90,0%), ricorrenza UNP -1,0pp (74,7% vs 75,7%).
  Controcupone: hard -42 migliore (-68 vs -110) perche' room-null non-esente crolla 92 -> 42.
- **Il difetto-tripletta NON c'e' in nessun braccio**: G06/Barberia 0/47 non collocate in ENTRAMBI,
  T19 0/128 non collocate in ENTRAMBI. Il solver colloca gia' tutto T19 e tutte le Barberia DA SOLO,
  senza pin. Il pin non cura un difetto (non c'e'): forza solo le celle specifiche lun+mar del golden.
- **Consequenza per Q22**: la risposta golden-first e' che il pattern T19-lun+mar e' RIPRODUCIBILE
  dalla ricerca senza vincolo (baseline lo fa). Se lun+mar e' vincolo reale, il costo e' ~54k medium +
  1pp ricorrenza/full-day; se e' preferenza organizzativa, il baseline (senza pin) da' un orario
  migliore. Hard gia' quasi-zero in entrambi (-110/-68) -> il guadagno hard e' marginale, la perdita
  medium/soft e' sostanziale. Direttiva primaria: la performance si attacca col solver, non annacquo
  il modello; qui pero' non si sta adottando, si sta misurando per Q22.

## Scoperta laterale (rilevante per la nota maintainer sui Q22)
max-weekdays=2 NON pinna solo T19: pinna **tutti i docenti <=2 weekday** del golden. Oltre a T19
(MON,TUE, 23 target) entra anche **T24 (MON,TUE, 39 target)**. T07 (1 weekday WED) e T01
(1 weekday THU) erano gia' vincolati al default 1. Quindi il braccio test misura l'effetto di pinmare
il CLUSTER lun+mar (T19+T24), non T19 isolato. Per un'adozione per-docente (estensione TASK-435
alla T01) servirebbe un selettore per-teacher, non la soglia globale.

## Tabella raw (verificata sui artifact: analysis.json + DB + KPI)

| metrica | baseline mwd=1 | test mwd=2 | delta |
|---|---|---|---|
| **score hard** | -110 | -68 | +42 migliore |
| **score medium** | -971488 | -1025515 | -54027 peggiore |
| **score soft** | +6405 | -5588 | -11993 peggiore |
| boxed (unassigned, H0) | 0 | 1 | +1 |
| room-null non-esente (H29) | 92 | 42 | -50 migliore |
| H17 holes (hard) | 18 | 24 | +6 peggiore |
| full-day rate | 90,0% (1762/1957) | 88,8% (1738/1957) | -1,2pp |
| full-day ex-G14 | 90,0% | 88,8% | -1,2pp |
| ricorrenza TOTALE | 76,7% (9580/12494) | 75,8% (9474/12493) | -0,9pp |
| ricorrenza UNPINNED | 75,7% (7120/9404) | 74,7% (6856/9175) | -1,0pp |
| ricorrenza PINNED | 79,6% (2460/3090) | 78,9% (2618/3318) | -0,7pp |
| G06/Barberia non collocate | 0/47 | 0/47 | invariato |
| T19 non collocate | 0/128 | 0/128 | invariato |
| total pinned | 3090 | 3318 | +228 (T19 128 + T24 ~39 + ripple) |
| solve wall-clock | ~29min | ~40min | +11min |

Decomposizione hard (TASK-447: hard = boxed*2 + room-null*1 + H17*1):
- baseline: 0*2 + 92 + 18 = -110 ✓
- test: 1*2 + 42 + 24 = -68 ✓

## Setup verificato (gate superato)
- baseline: Phase 0 log `max-weekdays=1`; Constrained T01(1wd THU,35)+T07(1wd WED,24);
  T01 231/231 pinned, T19 0/128.
- test: Phase 0 log `max-weekdays=2` (flag arrivato alla JVM via PLANNER_JAVA_OPTS java -jar);
  Constrained T01+T07+**T19(2wd MON,TUE,23)**+**T24(2wd MON,TUE,39)**; T01 231/231,
  T19 128/128 pinned. Gate (T19>0 pin) superato.

## Limiti
- n=1 per braccio. La varianza tra solve e' reale (baseline qui -110 vs 30d -173, stessa config):
  la DIREZIONE (medium/full-day/recurrence peggiorano) e' robusta, le magnitudini esatte no.
- Il braccio test include T24 oltre a T19 (soglia globale, non selettore per-teacher).
- Nessuna adozione: resta default max-weekdays=1. La decisione e' di maintainer dopo risposta scuola Q22.

## Artifact
- baseline: `t19-2026-07-31/baseline-mwd1/` (analysis.json, export.json.gz, lessons.txt, KPI, confronto HTML)
- test: `t19-2026-07-31/test-mwd2/` (idem)
- log app: `/tmp/planner-prod.log` (Phase 0 lines, banner)
