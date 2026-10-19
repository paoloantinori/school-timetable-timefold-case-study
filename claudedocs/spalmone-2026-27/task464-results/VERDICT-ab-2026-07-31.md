# TASK-464 co-presenza ospite - VERDETTO full-solve A/B (zai, 2026-07-31 sera)

Gate AC#3 (full-solve A/B, n=2 ON + baseline OFF). HEAD `feature/cartesian-block-entity`,
JAR rebuilt col codice co-presenza (era stale: 12:30 vs sorgenti 15:48). Solve staged su
profilo production fresco (drop-and-create seed + import-calendario + pre-assign, identico ai
run di riferimento), flag passato via `PLANNER_JAVA_OPTS=-Dplanner.co-presenza.enabled=true`
sulla riga `java -jar` (MAI MAVEN_OPTS). Banner a solve-start confermato `enabled=true`
(ON1/ON2) e `enabled=false` (OFF). Nessuna adozione: flag resta OFF.

## VERDETTO (3 righe, dai raw)
1. **A/B netto e riproducibile: OFF -112hard -> ON -18/-17hard, delta +94/+95.** OFF = H29
   room-null -94 (94 lezioni acconciatura senza stanza: H2 vieta la co-locazione) + H17 -18; ON =
   H17 soli (17-18 buchi G13), **0 room-null**. I 94 room-null di OFF diventano ospiti co-locati
   in ON via esenzione H2. n=2 ON concordi (-18, -17): non e' un lucky basin (lezione E3 superata).
2. **Nessuna regressione.** full-day 89,5% -> 89,3% (varianza run-to-run, runbook documenta spread
   9x); ricorrenza unpinned 75,1% -> 75,5/75,6% (leggermente MIGLIORE); medium -963777 -> -963349/
   -956377 (MIGLIORE); buchi interni 18 -> 18/17 (stesso pattern G13 slot-11, care item TASK-451/
   463). Co-presenza e' un gain di hard PURO, a costo zero altrove.
3. **Co-presenza per-cell (target specifico): 89/92 celle co-locate, 178/184 lezioni ospiti-legali
   (~89-92 ospiti), TUTTE entro la depth golden (Mon cap2 onorato run-max=2, Thu entro cap5),
   0 over-depth (il cap H-COPRESENCE non fira mai: il solver co-loca esattamente fino alla
   pratica golden, mai oltre).** Replica fedele del checkpoint (92->0 sulla slice) su traiettoria
   intera.

## A/B table (raw dagli analysis.json + export.json + KPI)

| metric | OFF (flag off) | ON1 (flag on) | ON2 (flag on) |
|---|---|---|---|
| **score hard** | **-112** | **-18** | **-17** |
| score medium | -963777 | -963349 | -956377 |
| score soft | 5632 | 5610 | 8290 |
| H29 room-null (hard, match) | -94 (94) | 0 (0) | 0 (0) |
| H17 vertically compact (hard) | -18 (18) | -18 (18) | -17 (17) |
| H-COPRESENCE over-depth | inerte (flag off) | 0 match | 0 match |
| room-null DB (unpinned non-pseudo) | 94 | 0 | 0 |
| celle co-locate (>=2 acc same room) | 0 | 89 | 92 |
| lezioni ospiti-legali (in celle co-locate) | 0 | 178 | 184 |
| full-day rate | 89,5% | 89,3% | 89,3% |
| ricorrenza unpinned | 75,1% | 75,6% | 75,5% |
| buchi interni (tutti G13 slot-11) | 18 | 18 | 17 |
| H3 stacked cells | 0 | 0 | 0 |

## Catena causale (perche' il +94 e' del flag, non della varianza)
- OFF: 94 lezioni dei subject acconciatura-tagged (Barberia, Lavaggio, Servizio stilistico,
  Servizio tecnico e stilistico) restano senza stanza. H2 (room conflict, cap-1) vieta a due
  lezioni di condividere la stessa stanza, e ci sono piu' gruppi acconciatura che richiedono il
  lab nello stesso slot: H2 le blocca, H29 leConta come room-null (94). Co-locazione per-cell = 0.
- ON: l'esenzione pairwise di H2 (`isCoPresenceGuestPair`, gated dalla depth golden >=2 per cella)
  lascia co-locare gli ospiti golden-praticati; il cap H-COPRESENCE (hard, clone M-TC) impone il
  soffitto = depth golden di quella cella. Risultato: 89/92 celle co-locate, 0 room-null, 0 over-depth.
- I 94 room-null di OFF = i ~89-92 ospiti co-locati in ON (stessa popolazione, conteggio host/guest
  a parte). Il delta hard (+94/+95) coincide con i 94 room-null eliminati. Identico setup, JAR
  identico, solo il flag cambia: l'attribuzione e' pulita.

## "Hard piu' basso di sempre"
Floor storico dei run solver (campaign 2026/27, prima del flag): ~-108 hard (run 30d/first-hour,
room-null ~90 + H17 18). ON1/ON2: **-18/-17 hard** (solo H17 G13, room-null azzerato). E' il
run solver col hard piu' basso mai registrato, ~90 punti sotto il floor precedente. L'unico hard
residuo e' il care item G13 slot-11 (famiglia TASK-449/M-TC, consolidamento lab-day a docente
unico, DA COSTRUIRE). Il golden (soluzione valida della scuola) resta hard 0 di riferimento.

## Setup / riproducibilita'
Capture completo (capture-run.sh + export.json.gz + tutti i KPI) nei 3 outdir:
`claudedocs/spalmone-2026-27/task464-results/{off-baseline,on-run1,on-run2}/`.
Comando per braccio (ON; per OFF omettere PLANNER_JAVA_OPTS):
```
PLANNER_JAVA_OPTS="-Dplanner.co-presenza.enabled=true" ./scripts/core/prod-start-and-wait.sh 300
./scripts/api/import-calendario.sh && ./scripts/benchmark/verify-baseline.sh import
./scripts/api/api.sh pre-assign-reset && ./scripts/api/api.sh pre-assign   # poll COMPLETE
./scripts/api/api.sh solve-staged                                            # poll stagedStage
./scripts/benchmark/capture-run.sh <outdir>
```
Analisi per-cell (ospiti-legali): `python3 /tmp/cop464-colocation.py <export.json.gz>`.
NOTA verify-baseline.sh: il seed gate e' stale (aspetta 1376 timeslot, il seed ne ha 1368
post-TASK-408, corretto). Il gate import (1993/866/327) passa pulito sui 3 bracci.

## Decisione
**Nessuna adozione.** Flag `planner.co-presenza.enabled` resta DEFAULT OFF. L'A/B e' positivo
(gain hard +94 a costo zero, riproducibile) ed e' pronto per la decisione di maintainer. Il modello
NON e' gated a referente (golden autoritativo a livello orario); la domanda fisica "5 nel lab o
alcuni in aula normale?" resta nel doc di notifica referente (TASK-466c) senza bloccare.
