# TASK-446 smoke L4 (staged solve, Alt-B): VERDICT 3/3 VERDE

**Data:** 2026-07-28. **Modalita':** UN smoke solve staged, livello trend (localized-testing-guidelines.md),
MAI evidenza di adozione. **Config:** profile production fresco (laptop-RUNBOOK), flag 435
constrained-teacher ON su riga java -jar (`-Dplanner.pre-assign.constrained-teacher.enabled=true`
via `PLANNER_JAVA_OPTS`, verificato sulla command line del processo JVM). JAR ricostruito con
Alt-B (marker `getRoomNullCount` nel fast-jar app confermato). laptop MISURA soltanto: nessuna
adozione, nessun default cambiato. Il gate di adozione L5 (435+446 congiunto) resta a maintainer.

**Rispetto al budget:** ciclo completo in ~32min di wall-clock (start 18:50, B-ROOM end 19:22),
dentro il paletto ~30-40min. Nessun hard cap forzato.

## Le TRE domande

### (a) La traiettoria B-TIMESLOT e' LIBERA? SI

La paralisi H16b (room-based hard) del baseline e' ASSENTE. La LS timeslot ottimizza pulita.

| Stadio | Durata | timeslot-tier hard | box | note |
|--------|--------|--------------------|-----|------|
| A-CONSTRUCTION | 5s | -188 (post-costruzione) | 65 | `roomNullAfterPlace 9339` (Alt-B: TUTTE non-pinnate room-null) |
| B-TIMESLOT-1 | **1073s (~18min)** | **-32** | 8 | vs baseline 60min @ -928 (H16b -963 dominante) |
| B-TIMESLOT-2 | 281s (~4.7min) | -30 | 6 | demotion round 1 |
| B-TIMESLOT-3 | 508s (~8.5min) | -28 | 4 | demotion round 2, "residual ACCEPTED as-is" |

Mid-B-TIMESLOT-1 (~6min) breakdown hard: **H17 -123 + H0 -65 boxed, ZERO H2/H16b/H-M2** (room-based
silenti). H1/H3/H5/H7 = 0 (zero conflitti timeslot). La traiettoria scende attivamente (-188 a -32)
e non si congela.

**Tempo-a-timeslot-hard-0 (confronto):**
- Baseline final-confirm-435: timeslot-hard-0 MAI raggiunto in B-TIMESLOT (H16b paralizzante a -928
  per 60min; -41 hard solo post-B-ROOM, e quel -41 era in parte room-hard).
- Alt-B smoke: timeslot-tier hard -32 in B-TIMESLOT-1 (18min); i conflitti timeslot (H1/H3/H5) sono 0
  fin da subito. Il residuo -28/-32 e' H17 (compaction gaps) + H0 (boxed), demandato agli stadi det + B-ROOM.

### (b) B-ROOM assorbe la griglia room-null INTERA? SI

9339 room-null in ingresso (da A-CONSTRUCTION) diventano **B-ROOM finished in 65s** con residuo
room-hard **0**.

Score finale: **-4hard**, e i -4hard sono **SOLO H0 (4 unassigned/boxed)**. **ZERO H2, ZERO H16b,
ZERO H-M2** (room-based). B-ROOM ha assegnato tutte le room sulla griglia completa senza introdurre
room-infeasibilita' (il rischio che Fedora aveva flaggato NON si e' materializzato in questo smoke).

### (c) Stadi deterministici senza sorprese? SI

Girati sullo stato room-null post-B-TIMESLOT, tutti conflict-free:
- **HoleRepair**: M20 day-fill cost 184000 a 184000 (gira, nessun conflitto introdotto).
- **AnchorRepair** (TASK-433): late days **52 a 15** (filled 48 leading slots; fully 37, partial 1,
  none 14); donor days emptied 14, **newly-late 0, conflicts added 0**.
- **SurplusRebalance** (TASK-422): 19 ore moved above-quota a deficit, M20 day-fill cost 209500 a 190000,
  deficit days 146 a 128, surplus days 76 a 61.

## KPI finali (post-B-ROOM)

| KPI | Alt-B smoke | riferimento |
|-----|-------------|-------------|
| score | **-4hard**/-920973medium/35513soft | baseline final-confirm-435 **-41hard** |
| full-day | **81.3%** (1947/2395) | golden 85.4%, run14 55.5% |
| day-start | **99.3%** (2191/2206) | golden ~99.3-100% |
| buchi interni | **0** giorni / 0 slot | n/a |

I -4hard (H0 boxed) sono il residual di box-the-residual accettato ("residual after 2 demotion
rounds ACCEPTED as-is"), coerente col design staged (i 4 boxed sono demandati a un ri-solve o
accettati come un ordinary solve). Nessun room-hard, nessun conflitto timeslot.

## Verdetto e prossimo passo

**Smoke L4 VERDE su tutte e 3 le domande.** Alt-B in produzione:
- disaccoppia room/timeslot (paralisi H16b -963 a 0, timeslot-hard -928 a -32 in 1/3 del tempo);
- B-ROOM assorbe le 9339 room-null mantenendo room-hard = 0 (nessuna room-infeasibilita' sistematica);
- KPI tenute (full-day 81.3%, day-start 99.3%, buchi 0) e migliori del baseline (-4hard vs -41).

**Proposta a maintainer (Fedora):** gate di adozione L5 congiunto 435+446 (solve completo di certificazione
sull'architettura veloce). Lo smoke e' misura-only: nessuna adozione, nessun default cambiato in questo run.

## Raw a supporto
- `phase-markers-log.txt` (traiettoria completa A, B-TIMESLOT x3, stadi det, B-ROOM).
- `score.txt`, `analysis.json` (breakdown per-constraint), `phases.json`.
- `export.json.gz` (protocollo TASK-401, presente stavolta), `lessons.txt`.
- KPI: `kpi-fill.txt`, `kpi-daystart.txt`, `kpi-holes.txt`, `kpi-resemblance.txt`, `kpi-cadence.txt`,
  `kpi-sharpness.txt`, `kpi-moda-knot.txt`, `teacher-fingerprint.txt`.
- `confronto-smoke-l4.html` (golden-vs-run, 14 gruppi x 172 giorni).
