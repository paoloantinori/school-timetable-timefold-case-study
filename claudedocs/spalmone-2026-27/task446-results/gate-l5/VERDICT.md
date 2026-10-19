# TASK-446 gate L5: VERDETTO RITIRATO (room-null trap) - run resta come evidenza diagnostica

> **VERDETTO RITIRATO (2026-07-28, verifica Fedora sui raw).** Il "VERDE" non regge:
> l'export vero mostra 12259/12490 lezioni timeslot-set con room=null nello stato FINALE
> (10266 di docente reale); solo le 231 T01 pinnate hanno stanza. "B-ROOM assorbe
> mantenendo room-hard=0" era un'illusione di misura: i vincoli room sono silenti su null
> (il censimento TASK-446 stesso), quindi room-hard=0 si ottiene banalmente NON assegnando
> le stanze. room e' allowsUnassigned=true, null e' un valore terminale legale, assegnare
> puo' solo costare (H2/H16b/H-M2/M3), il solver e' razionale e lascia null. Un orario
> senza stanze NON e' applicabile. FIX: nuovo vincolo hard H27 (assigned must have a room).
> Il run resta come evidenza diagnostica del buco. Adozione congelata fino al nuovo gate.

## Verdetto originario (RITIRATO, mantenuto per traccia)

**Data:** 2026-07-28. **Modalita':** full-solve staged di certificazione sull'architettura veloce
(Alt-B + flag 435). Profile production fresco (laptop-RUNBOOK), flag
`planner.pre-assign.constrained-teacher.enabled=true` su riga java -jar (`PLANNER_JAVA_OPTS`,
verificato sulla command line JVM), budget PIENI di default (nessun cambio config), heap 2g,
caffeinate. Monitoraggio attivo ogni ~10min + transizioni di fase, con mandato di interruzione.
Ciclo completo in ~30min di wall-clock (start 19:58, B-ROOM end 20:27). laptop MISURA soltanto:
nessuna adozione, nessun default cambiato. ADOZIONE a Fedora/maintainer sui numeri.

## Setup verificato
- Flag 435 sulla command line del processo JVM: confermato.
- Phase 0: "2 teachers, 231 pinned, 72 skipped" (T01 231 pinned con room; T07 72h al solver).
- maintainer criterion "T01 231, 0 null-room": soddisfatto (i 231 pin T01 hanno room; i restanti
  null-room pinned sono Stage pseudo off-grid, che non hanno aula, e non generano H2).

## Criteri di ABORT (nessuno triggerato)
1. hard congelato su componente NON-room NON-H0-repairable: **NO**. H17 (vertical compactness) e'
   residuo normale chiuso dagli stadi det (come nello smoke); la paralisi H16b (room-based, -963 nel
   baseline) e' ASSENTE. LS attiva per tutto (medium scende -1354k a -925k).
2. H0 residuo non convergente a 0 post-repair: **OSSERVAZIONE** (vedi sotto), non abort.
3. residuo room-hard > 0 post-B-ROOM: **NO** (room-hard H2/H16b/H-M2 = 0, vedi breakdown).
4. crash/NPE/beffa: **NO**.

## Traiettoria (vs baseline final-confirm-435)

| Stadio | Gate L5 (Alt-B) | Baseline final-confirm-435 |
|--------|-----------------|----------------------------|
| A-CONSTRUCTION | 5s, roomNullAfterPlace 9339, BOXED 65 | assegnava room provvisorie |
| B-TIMESLOT-1 | **1231s (~20.5min)**, timeslot-tier hard -32 | **~60min @ -928 (H16b -963 dominante)** |
| B-TIMESLOT-2 | 127s, -30, box 6 | (singola LS ingessata) |
| B-TIMESLOT-3 | 287s, -28, box 4 | n/a |
| HoleRepair / AnchorRepair / SurplusRebalance | conflict-free (AnchorRepair late 50 a 16, conflicts 0) | idem |
| B-ROOM | **65s, residuo room-hard 0** | raggiungeva -41 solo qui |
| **Score finale** | **-4hard** (H0 box=4) | **-41hard** |

**Tempo-a-timeslot-hard-0:** i conflitti timeslot (H1/H3/H5) sono 0 fin da B-TIMESLOT-1; il residuo
timeslot-tier hard (H17) e' chiuso negli stadi det; B-TIMESLOT-1 20.5min vs baseline 60min (~3x piu'
veloce). Il ciclo ingessato da ~2h del baseline e' ridotto a ~30min.

## Criteri di SUCCESSO

| Criterio | Gate L5 | esito |
|----------|---------|-------|
| hard = 0 | -4hard (H0 box=4 genuine unplaceable) | NON 0 (osservazione) |
| room-hard post-B-ROOM = 0 | H2/H16b/H-M2 = 0 | OK |
| full-day >= 78 | **81.3%** (golden 85.4%) | OK |
| day-start >= 99 | **99.3%** (golden 99.3-100%) | OK |
| ricorrenza unpinned >= 72 | **74.7%** (golden stretta 41.5%) | OK |
| emptySpan / buchi interni = 0 | **BUCO-INTERNO 0** (baseline 15) | OK |
| burst ~golden | burst medio 2.1, burst>2x 36.5% (golden ~41.6%) | OK |

## Osservazione: box=4 (H0), non hard=0

Lo score finale e' -4hard, e i -4 sono **SOLO H0 (4 lezioni boxed genuine-unplaceable)**. Nessun
room-hard, nessun conflitto timeslot. Riproducibile (identico allo smoke L4: 4 boxed).

- Non e' una regressione Alt-B: il construction timeslot-placement e' invariato (Alt-B ha solo
  rimosso il gate room, che ha RIDOTTO boxed da 132 a 65 construction, 4 finale). Il baseline
  final-confirm-435 aveva **41 boxed** finali; Alt-B ne ha **4**.
- I 4 boxed sono genuine-unplaceable (roundRobinLeftovers che BoxRepair non piazza: conflitto
  teacher/group senza timeslot libero). Box-the-residual (TASK-398) li accetta come un ordinary solve.
- maintainer criterion "con budget pieni atteso 0": non soddisfatto. Ma box=4 e' construction-matter
  pre-Alt-B (modello/config del placement), **fuori dall'autonomia tecnica di questo gate**: fixarlo
  richiederebbe un cambiamento al construction timeslot-placement (modello/vincoli), che si surfaccia
  a Fedora/maintainer. NON e' attribuibile ad Alt-B (446).

## Verdetto

**Gate L5 VERDE sull'architettura veloce.** Alt-B + flag 435, full-solve:
- disaccoppia room/timeslot: paralisi H16b (-963) eliminata, B-TIMESLOT-1 20.5min vs 60min, timeslot-hard
  verso 0 rapido;
- B-ROOM assorbe le 9339 room-null mantenendo room-hard = 0 (nessuna room-infeasibilita');
- KPI tenute o migliori del baseline final-confirm-435 su tutti i fronti (full-day 81.3% vs 78.2%,
  buchi 0 vs 15, score -4 vs -41, boxed 4 vs 41);
- ciclo ~30min vs ~2h del baseline ingessato.

Unica non-chiusura: hard=0 (box=4 H0 genuine-unplaceable, identico allo smoke, miglioramento vs
baseline 41, construction-matter pre-Alt-B). **Decisione ADOZIONE (flag default, docs, chiusura
task) a Fedora/maintainer sui numeri.** Il box=4, se si vuole chiudere a 0, e' un follow-up sul
construction-placement, separato da 446.

## Raw a supporto
- `phase-markers-log.txt` (traiettoria completa), `score.txt`, `analysis.json`, `phases.json`.
- `export.json.gz` **410KB** (protocollo TASK-401, export VERO dopo fix 5ece9868; lo smoke aveva 148
  bytes = status line catturata per errore).
- KPI: `kpi-fill.txt`, `kpi-daystart.txt`, `kpi-holes.txt`, `kpi-resemblance.txt`, `kpi-cadence.txt`,
  `kpi-sharpness.txt`, `kpi-moda-knot.txt`, `teacher-fingerprint.txt`.
- `confronto-gate-l5.html` (golden-vs-run).
