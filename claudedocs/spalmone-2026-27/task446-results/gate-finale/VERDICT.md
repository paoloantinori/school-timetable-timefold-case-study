# FASE 6 - GATE UNICO FINALE (M37 + H28, run live), VERDICT (2026-07-29 notte)

**ESITO: run completa, ~40min. M37 HA RISOLTO la trappola room-null dei NON-LAB (291->3 a
produzione); il residuo 251 e' LAB capacity-constrained (dominio Fase 3). KPI tenute, H28
guard a 0, nessuna degenerazione. day-start G11 venerdi' residuo (Fase 4 defer). L'adozione
decide maintainer domattina; laptop ha MISURATO.**

## Run (staged, profile production fresco, flag 435 ON su riga java -jar, JAR M37+H28)

- Start solve ~00:18, fine 00:57 (~40min). B-TIMESLOT #1 967s (-31hard box9) -> #2 181s (-29
  box7) -> #3 (~7min, -27 box5, ACCEPTED as-is) -> RELEASE (7202 promoted, 3090 authoritative
  held) -> HoleRepair (H17 22->0) -> AnchorRepair (late 56->17, newly-late 0) -> SurplusRebalance
  (deficit 149->132) -> B-ROOM 605s.
- **Authoritative pins 3090 -> 3090 (preservati).** Validation newlyViolated=0, baseline=true.
- stagedStage CLEAR. Score finale: **-5hard/-1209153medium/24938soft.**

## Hard e decomposizione

- H0 (boxed) = **-5hard / 5 match** (genuine-unplaceable, STABILE: nessuna degenerazione da
  svuotamento, il nuovo abort criterion e' rispettato).
- room-hard H2 / H-M2 = **0** (GENUINI, 9376 room assegnate, non silent-null trap).
- **H28 (no teacher return) = 0 match** (il guard hard della Fase 2 tiene, l'invariante golden
  cristallizzata).
- M36 (room-null) = 0hard / -254000medium / 254 match.
- M37 (ex-H16b demoted) = -12400medium / 124 match (visibile ora come medium, prima hard-forced-0).
- M3 (capacita') = -641 / 641. S11 = -7220 soft.

## CRITERIO ROOM (547 -> 254: M37 ha risolto il non-lab)

Query unpinned-real-room-null (timeslot!=null && room==null && !pinned && !pseudo):
**254** (gate-l5-m36 era 547, -53%). Split:
- **non-lab: 291 -> 3** (M37 demotion: la phase-5 LS assegna le aule ai non-lab, come predetto
  dall'harness Fase 1 291->8; a produzione 291->3. La trappola room-null dei non-lab e' ESSENZIALMENTE
  RISOLTA.)
- **lab: 256 -> 251** (capacity-constrained al picco, dominio Fase 3: de-tag delle teoriche
  chiuderebbe ~meta', vedi PROPOSTA fase3-tag/VERDICT.md; il resto TASK-449 allocatore).

Quindi il criterio room (==0) NON e' chiuso (254), ma il componente non-lab e' risolto e il
residuo e' interamente LAB (Fase 3: capacita' docente/stanza al picco, non tag-error puro).
"Room-null real non-pinnato tende a 0": 547->254 e' un netto miglioramento, non ancora 0.

## KPI standard vs gate-l5-m36 e baseline final-confirm (-41)

| metrica | baseline -41 | gate-l5-m36 (M36) | gate-finale (M37+H28) |
|---|---|---|---|
| hard | -41 | -4 | -5 (box 5) |
| room-null real-unpinned | n/a (silent) | 547 (291 nl + 256 lab) | **254 (3 nl + 251 lab)** |
| room-hard H2/H-M2 | silent 0 | 0 genuine | 0 genuine |
| H28 no-return | - | - | **0 (nuovo guard)** |
| M37 (ex-H16b) | (hard) | (hard 0) | 124 medium |
| full-day | 77.2% | 80.9% | 80.9% (golden 85.4%) |
| day-start | 99.3% | 99.2% | 99.2% (soglia >=99) |
| ricorrenza unpinned | 72.7% | 74.9% | 74.2% (soglia >=72) |
| buchi interni | 0 | 0 | 0 |
| burst >2x | ~golden | ~golden | 36.1% (2.04) |
| runtime | ~2h | ~43min | ~40min |

KPI tutte VERDI e >= soglia (full-day 80.9 >= 78, day-start 99.2 >= 99, ricorrenza 74.2 >= 72,
buchi 0). Parita' o marginalmente sopra gate-l5-m36 (ricorrenza 74.9->74.2, noise).

## day-start-anatomy (Fase 5a, nuovo artefatto nella capture)

L'unica cella con scarto >= 3: **G11 venerdi run 16 | golden 0** (<<< SCARTO). Sono i 15-16
venerdi' sistematici (Fase 4: swap cross-group defer come shell-game-prone, vedi
fase4-anchor/VERDICT.md). day-start globale 99.2% (>= soglia), ma il pattern G11 venerdi'
resta: scope LS move-selector (TASK-292) o accettare il pattern (1 gruppo, -15 M18 medium).

## Tempo-a-hard-0

Hard non raggiunge 0 (5 boxed residual, come ogni run Alt-B). timeslot-tier hard convergia
~-27 a fine B-TIMESLOT-3 (~29min); hard finale -5 (box) a fine B-ROOM (~40min). vs baseline ~2h:
l'architettura veloce (Alt-B + M37) tiene.

## Conclusione e confine di mandato (FERMATI qui)

Il treno notturno M37 + H28 MIGLORA il gate: risolve la trappola room-null dei NON-LAB (291->3),
tiene le KPI, aggiunge il guard H28 (0 ritorni), nessuna degenerazione. Residui aperti per
maintainer: (1) 251 lab room-null -> Fase 3 PROPOSTA (de-tag teoriche) + TASK-449; (2) day-start 3
FB venerdi -> Fase 4 LS-scope; (3) TASK-447 (promote M36 hard + reweight H0) con censimento
pronto. laptop ha MISURATO, NON adotta: nessun default change, nessuna chiusura task, nessuna
ratifica. La decisione (cosa tenere: M37, H28, la PROPOSTA tag, l'allocatore 449, la 447) e'
di maintainer domattina leggendo i VERDICT di ogni fase.

## Cattura

`./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/task446-results/gate-finale`
artefatti: analysis.json, score.txt, KPI (fill/daystart/resemblance/cadence/holes/moda-knot/
sharpness + day-start-anatomy NEW Fase 5a), confronto-gate-finale.html, export.json.gz,
lessons.txt, phases.json.
