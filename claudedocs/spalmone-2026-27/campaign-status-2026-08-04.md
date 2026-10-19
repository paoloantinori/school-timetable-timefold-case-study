# Campaign status, 2026-08-04 (dopo la notte read-only)

Doc di orientamento mattutino. Sostituisce `campaign-status-2026-07-29-risveglio.md` (stale: elencava
il pacchetto 435+446+M37+H28 come "attende decisione", ma e' tutto ADOPTED DEFAULT ON ora). Stato
ADOZIONI/KPI da `CLAUDE.md` (autorita' delle ratifiche); verdetti di stanotte da
`handoff-notte-2026-08-04.md` (commit `5cbc8189`) e dai VERDICT doc citati, verificati e non da memoria.

## Stato attuale: cosa e' ADOPTED DEFAULT ON

Il ciclo staged (`POST /api/schedule/solve-staged`) e' la modalita' di campagna. Catena di ratifiche
DEFAULT ON (tutte opt-out, lette LIVE per-build, banner in `ScheduleService.refreshSolverStatics`):

- **2026-07-27**: staged cycle + M20/M33 pin-netting (TASK-424) + A4 ranking + TASK-422 SurplusRebalance
  + TASK-423 M33 anti-surplus + TASK-425 (M34/M35 cadence cap).
- **TASK-433** AnchorRepair (deterministico, post-HoleRepair/pre-SurplusRebalance).
- **TASK-435** constrained-teacher pre-assignment pin (2026-07-30).
- **TASK-451** first-hour-slot coordinato slot0+1 (2026-07-31).
- **TASK-463** lab-day-consolidation + **TASK-464** co-presenza ospite + **H-COPRESENCE** cap (2026-08-01).
- **TASK-469** M-CPS curriculum post-stage target 6h asimmetrico (2026-08-01).
- **TASK-478** ExactFinisher (completamento esatto del residuo boxed, 2026-08-02).
- **TASK-483** multi-start retry incapsulato (2026-08-03): pick-best su (hard, medium), stop al primo 0-hard.
- **M-LF** lab-fallback (TASK-456, 2026-07-29). **H29** promoted hard (TASK-447).

**Source DEFAULT = golden.** `source=input` e' opt-in (`-Dplanner.pre-assign.constrained-teacher.source=input`,
decisione ready-when-needed). **GRASP** (costruzione randomizzata) e' opt-in OFF
(`-Dplanner.construction.grasp.enabled`+`.seed`); il multi-start lo usa internamente per i tentativi >=2.

## Dove siamo vs il golden (KPI da evidenza ratificata)

| metrica | run corrente (ratificato) | golden | soglia |
|---------|---------------------------|--------|--------|
| hard | **0** su good seed (golden path); bad-seed -6 H17 (source=input path) | 0 | 0 (binario) |
| full-day | 87,5% (TASK-451, 88,6% col conteggio KPI corretto) | 85,4% | 78 |
| day-start | 99,9% | ~99,9% | - |
| ricorrenza unpinned | 75,6-75,9% (TASK-463 combined run3 / TASK-469 A/B) | ~golden cadence | 72 |
| buchi interni | 0 (good seed); 18 H17 accettati come costo TASK-451 | 0 | 0 |
| medium | -963k/-1047k (good seed, banda) | - | - |

Fonti: `CLAUDE.md` ratifiche TASK-451/463/469/478; `task463-results/combined-run3` (source=input, hard -6,
ricorrenza 75,9%); `task469-results/ab-cps-on-run1` (source=golden, hard 0, ricorrenza 75,6%). Numeri su
run ratificati, non medie; la LS e' NON_REPRODUCIBLE quindi varianza run-to-run.

## Il blocco corrente: m-13, source=input H17 bad-seed (0-hard binario)

Il path **source=input** (quello dell'esercizio reale, senza golden) e' **seed-dipendente**: good seed
0-hard, **bad seed -6 H17** (G11 mercoledi ore 10). 0-hard e' BINARIO e BLOCCANTE end-to-end
(direttiva maintainer 2026-08-02), quindi il gate **m-13 (genericita'/riuso multi-anno) resta bloccato** finche'
la varianza H17 non e' assorbita (`CLAUDE.md` L493-496).

Stato della cura:
- **TASK-481 CHIUSO** (esito onesto): la cura LOCALE del buco H17 e' uno SHELL-GAME (depth-3: ricollocare
  il bloccante @10 apre un gap sul suo gruppo, il buco migra). `CompositionHoleCloser` resta committed
  DEFAULT-OFF come building block corretto (gate `isDepartureSafe` rifiuta lo shell-game).
- **TASK-480/483 multi-start** e' la via PRINCIPALE per H17: selezione del bacino, non chiusura locale.
  p(0-hard) per singola costruzione = 0,750 (n=8, Wilson95 [0,41,0,93]); N=3 → P(>=1 0-hard)>0,95; wall
  medio 28 min/run. Costo zero sui good basin (loop si ferma al primo 0-hard).
- **TASK-478 ExactFinisher** chiude la coda boxed H0 deterministicamente (good seed), ortogonale al
  multi-start (che seleziona il bacino).

## Cosa la notte read-only ha chiarito (orientamento; elenco + commit nell'handoff)

Elenco completo dei 16 verdetti (con commit hash) in `handoff-notte-2026-08-04.md`. Qui solo cio' che
cambia lo stato della campagna:

- **TASK-486 M-TC per-retry VALIDATO** (prototipo opt-in, DEFAULT OFF): zero-cost sui good basin (NON 2x),
  cura il bad basin room-tier (AC#4 in-vivo). Pronto come safety-net opt-in; **ratifica a maintainer**.
- **TASK-453 erosione MISURATA** (read-only): cell-retention MOBILE construction→finale = 16,6%, concentrata
  in LS-Compactness, template preservato dai PIN non dalle lezioni libere. **Sblocca TASK-460** (blocker
  erosione rimosso).
- **Negativi puliti** (nessuna azione): 236 (HashMap non e' varianza), 339 (pin causano zero hard), 417
  (CH-room inert), 293 (shadow vars daily non warrantate), 488 (3o-anno = stage-shift, ignore), 482
  (Kempe-chain NO).
- **DEFER** (aperti): 487 (G04 friday), 477 (G14 b), 412 (T12), 158 (incremental score, gate
  profilazione non fatto).

## Cosa resta aperto / prossimo intervento (gated maintainer)

1. **TASK-486 ratifica M-TC per-retry** (validato, manca il via di maintainer sul default).
2. **TASK-460 brainstorming ricombinazione orizzontale** (blocker TASK-453 rimosso; evidenza erosione
   disponibile per seminare l'approccio).
3. **TASK-470/471** workbook teoria-spread (gated maintainer; veicolo solo-input).
4. **TASK-449** allocatore pre-assegnazione lab mono-stanza.
5. **m-13 gate**: bloccato sul source=input H17 bad-seed; il multi-start (483) lo agglutina ma non lo
   cura in-locus (481 shell-game). Decidere se accettare la procedura multi-start come chiusura o
   inseguire una cura strutturale.

## Dove guardare

- Orientamento nottata: `claudedocs/spalmone-2026-27/handoff-notte-2026-08-04.md`.
- Ratifiche e KPI: `CLAUDE.md` (sezioni "RATIFICA TASK-*" + tabelle vincoli).
- Evidenza 486: `task486-results/VERDICT-mtc-per-retry-2026-08-03.md`. Erosione 453:
  `task453-results/VERDICT-erosione-2026-08-03.md`.
- Pipeline solver: `claudedocs/solver-phases.md`; config knob: `claudedocs/solver-configurations.md`.
