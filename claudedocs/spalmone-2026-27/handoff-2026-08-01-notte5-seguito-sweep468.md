# HANDOFF notte5-seguito 01/08 (zai) - TASK-468 SWEEP PESO: il peso e' INERTE (residuo fill-determinato)

## ⚠️ CORREZIONE DIAGNOSI (02:40, Fedora verification - SUPERSEDE M20-dominance sotto)

La diagnosi M20-dominance (gara di pesi) era INCOMPLETA. A peso 1000 (1000/ripetizione vs M20
marginale ~1000/ora) ci sarebbe stata way piu' varianza dell'1%; inerzia TOTALE a qualunque peso =
leva scollegata, non gara persa. Diagnosi read-only (causal-chain, senza altri solve):
- **Sorgente = COSTRUZIONE.** Residuo M-TR a fine construction = **1672** (target/construction-lessons.txt,
  WeeklyTemplateConstructionRealSeedTest; misura scripts/analyze/theory_adjacent_residual_dump.py validata a 1679 sul run dump
  = identico al vincolo) ~uguale al finale 1679. La LS eredita i blocchi 2h e li smonta appena (+7),
  NON li crea.
- **Blocchi UNPINNED al 98,6%** (1655/1679): NON e' la PROMOTION a pinnarli (sotto-ipotesi
  promotion-pinning CONFUTATA). La LS non smonta blocchi unpinned nemmeno a peso 1000 perche' la
  costruzione crea un ottimo locale di fill (M32+M20+M15 preservano; PLAUSIBILE non verificato, la
  cura non dipende da lui).
- **CURA PROPOSTA (TASK-470, gated maintainer, NON prototipo):** a monte nel WeeklyTemplateBuilder, dedup
  per-(gruppo, giorno, materia-teorica) nel placement greedy. NON un peso, NON un capping di length
  (M-TR fired su same-day same-subject anche non-contiguo). Rischio: potrebbe non tenere nella LS.
La headline peso-INERTE regge (1694->1679 ~1%); cambia il PERCHE' (sorgente construction, non gara di
pesi). Dettagli: `task468-results/VERDICT-sweep468-2026-08-01.md` sezione DIAGNOSI CORRETTA. La headline
+ sezioni sotto restano come record storico (M20-dominance e' la parte superata).

---

HEAD `feature/cartesian-block-entity` pushato fino a `b86eca5b` (verdetto sweep + canonici).
Sweep del peso M-TR COMPLETO. Programma in 3 parti tutto fatto: (1) sweep w1000 run+capture,
(2) doc updates durante il solve, (3) verdetto comparativo + QUADRO + CLAUDE.md + task + handoff.

**HEADLINE:** il peso M-TR e' **INERTE in [1,1000]**. Residuo 1694 (peso 1) -> 1679 (peso 1000),
~1% su 1000x peso, entrambi ~4x il golden 440. **M20 (fill, fino a 4500/ora sui giorni profondi)
domina strutturalmente anche a peso 1000**: il solver riempie i giorni ripetendo teoria perche'
l'ora scoperta costa di piu' (-M20) del repeat (-M-TR). Il residuo e' **fill-determinato, NON
accordabile col peso**. Costo KPI neutro-meglio (hard 0, full-day 90,2%). Bracketing deciso a 2
punti; peso 100 saltato (meccanicamente determinato + robusto all'esito). **NESSUNA adozione**
(flag 468 DEFAULT OFF). Il lever per riflettere il golden 440 e' strutturale (mix-distinto-per-
giorno / target M20), non il peso. Verdetto: `task468-results/VERDICT-sweep468-2026-08-01.md`.

## Commit (pushato in questo giro)
- `08c988cb` docs(task468): sweep avviato - AC2 nota peso + nomi materia DB + scheletro verdetto
- (questo giro) docs(task468): verdetto sweep - peso inerte, residuo fill-determinato + QUADRO + CLAUDE.md + handoff

## PARTE 1 - SWEEP w1000: DONE
Run ON peso 1000, stesso protocollo pulito di ab-on-run1: profilo production fresco
(drop-and-create + import-calendario + pre-assign, **3090 pinned verificati** = identici a
ab-on-run1), flags 463+464+468 ON, knob `-Dplanner.constraints.theory-adjacent.weight=1000` su
riga `java -jar` (PLANNER_JAVA_OPTS). Pre-solve verificato: M-TR -48000/24 match = esattamente
1000x il baseline peso-1 (-48/24), stesso setup pinned. solve-staged 01:48->02:21 (~33min),
hard 0 clean, capture `ab-w1000-run1/`.
- Falso stallo gestito: B-TIMESLOT-1 congelato ~13min (score quasi fermo) confrontato col
  reference ab-on-run1/phases.json (prima "2. LS - Compactness" = 12,3min, unimprovedLimit 600s):
  in linea, NON morte. Poi B-TIMESLOT -> B-ROOM (hard -1840 -> -8640 -> 0, room-null risolto).

## PARTE 2 - Doc updates (fatte durante il solve): DONE
- `AC2-design-2026-07-31.md`: nota peso (self-gating ~432/440 NON tiene a peso 1, confermato
  sweep), fix knob name (`planner.constraints.theory-adjacent.weight`, non `.theory-adjacent.`
  del draft), nomi materia DB autoritativi (`Metrologia e Tecnologia dei materiali`,
  `Ra lab aperto`) al posto shorthand inesistenti, sezione sweep.
- `VERDICT-sweep468-2026-08-01.md`: scheletro preparato durante il solve, poi riempito coi raw.

## PARTE 3 - Verdetto comparativo + canonici: DONE
- `VERDICT-sweep468-2026-08-01.md`: tabella peso1-vs-peso1000, diagnosi M20-dominance,
  bracketing deciso (skip peso100 trasparente), raccomandazione, verifica adversarial.
- `QUADRO-2026-07-30.md`: nuova sezione NOTTE5 01/08 (3 colonne) in cima.
- `CLAUDE.md`: riga M-TR aggiornata (clause "Peso debole (1)" -> "Peso INERTE nello sweep A/B",
  per non ingannare futuri lettori che alzeranno il peso pensando aiuti).
- Task #468: commento #4 sweep aggiunto (via backlog MCP). AC#4 resta spuntata (peso-1 A/B done);
  lo sweep e' il refinement.

## Bracketing: peso 100 saltato (perche')
Il programma autorizzava un run peso 100 "se residuo lontano da 440". Lo e', ma il run e'
inutile: (1) meccanicamente determinato (residuo cresce al calar del peso -> peso100 >= 1679,
atteso ~1685; predizione NON verificata empiricamente, LS stocastico, ma il verso e' garantito
dal meccanismo M20-dominante); (2) robusto all'esito (anche se sorprendesse a 800, la
raccomandazione non cambierebbe: ancora ~2x golden, gap resta strutturale). Il punto peso 100
non puo' ribaltare il verdetto. Se maintainer lo vuole empirico, un comando (vedi sotto).

## Raccomandazione (adozione = maintainer)
- Nessuna adozione per sterzare: nessun peso in [1,1000] avvicina al golden 440. Flag 468 OFF.
- Se intento = "niente 2h teoria" weak-best-effort: peso 1 ok, documentando che non muove il
  needle (run resta ~1694). E' una preferenza dichiarata, non un vincolo effettivo.
- Se intento = riflettere il golden 440: lever strutturale, non peso. Investigare il
  mix-distinto-per-giorno del golden vs run, o la forma del target M20. Tracciare come lavoro
  a se' (golden come oracolo per scoprire quale pratica produce il 440; direttiva solo-input).

## Come riprendere (slot aperti, opzionali, solo se maintainer li vuole)
- **Peso 100 empirico** (conferma bracket): identico protocollo, knob
  `-Dplanner.constraints.theory-adjacent.weight=100`, capture `task468-results/ab-w100-run1/`.
  Atteso ~1685 (meccanicamente determinato).
- **Probe peso 5000** (dove M-TR repeat finally batte M20 max sul giorno piu' profondo):
  knob weight=5000. Attenuto: potrebbe ridurre il residuo ma a costo di buchi/hard (il solver
  preferisce lasciare scoperto invece di ripetere). Da misurare.
- Entrambi facoltativi: il verdetto e' deciso senza di loro.

## Verifica (adversarial-verification, numeri dai raw)
- Weight-inert (1679≈1694): CONFIRMED. Attribuzione pulita (stesso setup, solo peso differisce;
  pre-solve 1000x conferma knob arrivato). Entrambi convergi normally. Scoped [1,1000].
- Baseline 432: CONFIRMED (riprodotto dal source `theory_adjacent_residual.py`). 440 runtime =
  432+8 (RA->Recuperi rename), re-measure documentato sessione precedente.
- Skip peso100: PLAUSIBLE (determinato + robusto), predizione label non-verificata.

## Regole standing rispettate
- Flag 463/464/468 tutti DEFAULT OFF, nessuna adozione.
- commit+push a ogni chiusura (08c988cb + questo giro).
- Verdetti dai raw (analysis.json diretto, non summary).
- Tempi assoluti (solve 01:48->02:21, capture 02:21).
- DoD: nessun codice toccato (solo docs + measurement), non applicabile /simplify+/code-review.
