# HANDOFF mattina 01/08 (zai) - TASK-452/m-13: RUN VERIFICA source=input -> FLIP NON PRONTO

> **STATO (zai, 01/08 mattina): CURA DENTRO + DoD CLEAN (commit `4f5cd9d0`).** La cura scoped sotto
> e' implementata e verificata: `ConstrainedTeacherAllocator.buildInputDerivedProfile` cappa le h/data
> agli open-slot reali del GroupCalendarRule (knob `source=input`, default golden result-identical,
> skeptic Opus CONFIRMED); `/simplify` applicato + `/code-review high` clean + `./scripts/core/test.sh`
> 928/0/0; test di equivalenza RED->GREEN (G02 8h>6 open pre-cura). **NIENTE solve** (mandato): il
> re-run post-cura (n=2 source=input + un golden appaiato, stesso profilo fresco + config) e' la prova
> vera e si decide di giornata con maintainer. Punto di ripresa: questo handoff + VERDICT (blocco CURA
> IMPLEMENTATA) + census #1 + QUADRO.

> **AGGIORNAMENTO ~07:30 (dopo correzione Fedora + causal-chain): la diagnosi e' SISTEMATICA, non
> varianza.** Il framing "n=1 inconclusivo / banda varianza" qui sotto e' SUPERATO. Causa radice
> tracciata: la mappa statica GroupCalendarRule e' vuota a pre-assign (`setGroupCalendarRulesStatic`
> solo in refreshSolverStatics), quindi `openSlotsForGroupDay` (:1209) non filtra gli slot chiusi;
> il profilo input-derived (8h/data, h7Cap=8) ricade sui slot 6-7 chiusi di G06 giovedi' (GCR
> maxslot 4) -> 14 H20 pinned T01/G06 + 7 H0 T01/G02. Golden evade (blocchi <=6h).
> CURA: cappare h/data al window calendario REALE in `buildInputDerivedProfile`, knob `source=input`,
> default golden INVARIATO, + test "nessun pin su cella chiusa". Impl+DoD = sessione fresca; n=2 +
> golden appaiato DOPO la cura. Vedi VERDICT (sezione DIAGNOSI), census #1, QUADRO.

HEAD `feature/cartesian-block-entity` (push in coda a questo). App `source=input` ancora in
piedi (PID 26232) sul profilo `production` fresco. Default golden INVARIATO, nessuna adozione.

## HEADLINE

Il run di verifica del flip m-13 e' fatto (mandato di maintainer: non conferma ripetuta, ma la misura
che dis-riska la decisione). **Risultato: source=input NON confermato equivalente su n=1. Flip
NON pronto, default resta golden.** Il run non ha matchato la baseline pulita (hard -28 vs 0),
MA resta dentro la banda di varianza nota del golden (0..-169), quindi un run solo non separa
effetto-sorgente da varianza-solver. Il profilo e' sostituibile (ricorrenza/day-start flat); la
soluzione no.

## FATTO (verificato dai raw)

- **Protocollo combined-run2 + source=input.** Profilo `production` fresco (volume wipe ->
  DataGenerator reseed). App lanciata `java -jar` diretta (prod-start-and-wait.sh) con i `-D`
  **sulla riga java -jar** (lezione CLAUDE.md MAVEN_OPTS, non in MAVEN_OPTS):
  `-Dplanner.pre-assign.constrained-teacher.source=input -Dplanner.co-presenza.enabled=true
  -Dplanner.construction.lab-day-consolidation.enabled=true` (468 default OFF).
- **Knob arrivato end-to-end (verificato).** pgrep mostra i flag sul processo app vivo; banner
  `[HybridPhase1a] TASK-452 source=input: built input-derived profiles for 2 constrained
  teacher(s)` nel log (T01 Thu + T07 Wed) = prova che il path input e' GIRATO.
- **Import identico al baseline:** 2859 pin (866 MODA + 1993 stage), verify-baseline OK.
- **Solve staged ~34 min** (A-CONSTRUCTION -> PROMOTION -> B-TIMESLOT-1/2/3 -> B-ROOM), regolare
  (nessuno stallo: stagedStage progredito, hard converto nel finale). Capture completo.

## CONFRONTO source=input (run1) vs combined-run2 (source=golden)

| KPI | input | golden | |
|---|---|---|---|
| **hard** | **-28** (7 boxed + 14 H20) | **0** | peggio |
| medium | -1.017.099 | -994.722 | -22k |
| full-day | 87,1% | 90,4% | -3,3pp |
| ricorrenza unpinned | 75,7% | 75,8% | flat |
| day-start | 99,5% | 99,5% | flat |

Decomposizione hard: 7 lezioni non collocabili (H0) + 14 slot-chiusi (H20), entrambi assenti nel
golden. Dettaglio + KPI completi: `task452-results/verify-source-input-run1/VERDICT-2026-08-01.md`.

## VERDETTO (3 righe, per maintainer)

1. source=input **NON confermato equivalente su n=1** (hard -28 vs 0) -> **flip m-13 NON pronto,
   default resta golden.**
2. Non e' neppure provato rotto: -28 e' dentro la banda golden (0..-169); ricorrenza/day-start
   flat -> la sostituzione del profilo e' sana, il delta e' sui KPI sensibili alla varianza.
   Census #1 "date irrilevanti" **in dubbio, non rifutato**.
3. Per dis-riskare m-13: **n>=2 source=input + un golden appaiato** (stesso profilo fresco +
   config) per bound-are la varianza e separare l'effetto sorgente.

## COSA DECIDE maintainer

- **Flip del default golden->input?** NO su questa evidenza (n=1 inconclusivo). Serve il n=2.
- **Lanciare il n=2 / golden appaiato?** E' il prossimo passo se si vuole sbloccare m-13. Io mi
  fermo (FERMO DEFINITIVO): il prossimo input arriva da maintainer.

## STATO / COME RIPRENDERE

- App `source=input` in piedi (PID 26232) su profilo `production` fresco. Per un golden appaiato:
  kill-app, wipe profilo, rilanciare SENZA source=input (default golden) con stessi flag 463+464,
  stessa sequenza, capture in `task452-results/verify-golden-paired-run1/`.
- Punto di ripresa: questo handoff + `task452-results/verify-source-input-run1/VERDICT-2026-08-01.md`
  + census #1 (sezione "Verifica run source=input") + QUADRO (~06:30).

## REGOLE STANDING RISPETTATE

- Nessuna adozione, default golden invariato, verdetti dai raw (analysis.json/KPI/score.txt),
  tempi assoluti, -D sulla riga java -jar (verificato), banner source=input confermato.
- Read-only sul modello: nessun codice solver toccato, solo il run di verifica + doc.
