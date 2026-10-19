# HANDOFF mattina 01/08 (zai) - TASK-470 gate FALLITO: cura builder della teoria chiusa, la distribuzione e' input

HEAD `feature/cartesian-block-entity`, pushato a `73a6e05b` (TASK-470 `b093dce9` + bucket-B
`73a6e05b`). Tutto flag DEFAULT OFF, nessuna adozione. DoD completa (/simplify + /code-review high
+ test EXIT 0). Verdetti dai raw.

## HEADLINE
Il prototipo TASK-470 (theory-spread nel WeeklyTemplateBuilder, lever = scelta del weekday,
preferenza-con-fallback) e' stato implementato, gate-ato e **CONFUTATO con numeri**: il residuo
M-TR resta PIATTO (1669 -> 1675). Uno spike diagnostico (theory-as-1h+dedup, revertito) scende a
1142 (-31%) MA plateau a 2,6x golden 440. **Conclusione: i lever di costruzione sono parziali; il
gap verso golden e' a livello di INPUT.** La cura builder della teoria e' chiusa. Aperto TASK-471
(dichiarare la distribuzione teoria nel workbook, dal golden, conferma scuola).

## FATTO STANOTTE (numeri verificati dai raw)
- **TASK-470 prototipo** flag `planner.construction.theory-spread.enabled` DEFAULT OFF. Gate
  `WeeklyTemplateConstructionRealSeedTest#theorySpreadOn_...` EXIT 0, guardie VERDI (zero H1/H3,
  stampSkipConflict=0 invariato, ricorrenza 77,6%). Residuo **OFF 1669 -> ON 1675 giorni-2+**
  (piatto). Il prototipo FUNZIONA (units 1750->1713, Lingua Italiana 353->326) ma il lever weekday
  e' insufficiente: ogni chunk 2h di teoria e' un giorno-2+.
- **SPIKE 1h+dedup (throwaway revertito): 1142** (-31%). Conferma chunk-size come lever reale, MA
  plateau a 2,6x golden. Residuo restante = ripetizioni vincolate (ore settimanali > weekday
  disponibili). Golden 432/6742 = 6,4% (scuola piazza ~94% teoria a 1h/giorno).
- **Refactor isTheoryForRule single-source in SubjectNormalization** (M-TR delega; il builder
  riutilizza lo stesso predicato + grouping key `theoryRuleGroupingKey` con l'alias T06).
  Single-source COMPLETO (classification + grouping insieme). `TheoryAdjacentConstraintTest` 10/10,
  construction gate EXIT 0 numeri identici al pre-fix (behavior preservato).
- **TASK-471 aperto** (workbook: distribuzione teoria come input dichiarato). Famiglia TASK-445/466.
- **QUADRO** aggiornato con sezione MATTINA 01/08 (3 colonne + menu a/b/c/d).

## COSA DECIDE maintainer (menu nel QUADRO)
a) Adozione combined 463+464 (gated Q20 referente).
b) TASK-468 M-TR post-470: formulazione proposta = tienilo a peso 1 DEFAULT OFF come MISURATORE
   onesto (conta le violazioni della pratica teoria-1h/giorno; non sterza, la cura builder e'
   chiusa, gap e' input-level -> TASK-471 workbook). Nessun peso >1.
c) Invio pacchetto referente (rotazione/tensioni + bucket-C T1-T8 + Q20).
d) TASK-469/470: 469 (T11 6h, T4) resta To Do gated referente; 470 gate-failed -> PROPOSTA flag OFF.

## STATO / COME RIPRENDERE
- HEAD pushato. Flag 463/464/468/theory-spread tutti DEFAULT OFF. Nessuna adozione.
- Verdetto TASK-470: `task470-results/VERDICT-construction-gate-2026-08-01.md`.
- TASK-471 (workbook teoria) e' il prossimo passo verso il golden 440; non bloccante.
- Il prototipo weekday-only resta committed come artifact + building block (la dedup per-giorno e'
  componente di qualunque cura 1h futura, se il workbook ridimensiona il gap).

## REGOLE STANDING RISPETTATE
- Flag OFF, nessuna adozione, verdetti dai raw, tempi assoluti, DoD completa, commit+push a chiusura.
- Spike diagnostico revertito (chunk-length era escluso dal programma; usato solo per verificare
  il meccanismo, non committed).
