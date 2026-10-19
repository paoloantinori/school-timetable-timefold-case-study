# TASK-449 A/B verdict - FINALE: 449 RULED OUT (arm ON confirmed negative) (zai, 2026-08-09/10)

Live staged A/B (Fedora-offline compute window): 449 OFF (control) vs ON (treatment),
source=golden, within-session, ab-task449-mono-room.sh. L'esperimento che mancareva (Final
Summary TASK-449: "Adozione = maintainer dopo A/B staged"). Re-run pulito (POLL_CAP=240) ha
CONFIRMATO il segnale negativo.

## Setup
- Arm OFF: `baseline-golden.sh` profile task449-off, 449 default OFF.
- Arm ON: `baseline-golden.sh` profile task449-on, `-Dplanner.pre-assign.mono-room-tag.enabled=true`.
- Entrambi source=golden, staged cycle, multi-start maxAttempts=3.

## Risultati

| arm | score (best completed) | ricorrenza | full-day | tentativi | wall | capture |
|-----|------------------------|------------|----------|-----------|------|---------|
| OFF (control) | **0hard**/-1281kmedium | 75,8% | 85,9% | 2 -> 0hard | 112 min | clean |
| ON (treatment) | **-19hard**/-1205kmedium (attempt 2) | n/a | n/a | 3, nessuno a 0hard | >150 min (cap-cut) | FAILED |

**Arm OFF: VALID.** 0hard via multi-start (2 tentativi), 75,8% ricorrenza, 85,9% full-day.
Controllo coerente col baseline ratificato.

**Arm ON: INCONCLUSIVE.** Il poll-loop di baseline-golden.sh aveva cap 150 min; l'arm ON
ha girato 3 tentativi multi-start (NONE a 0hard: attempt 2 best = **-19hard**, attempt 3
interrotto a meta' B-TIMESLOT dal cap). Il capture e' FALLITO ("Export failed": lo schedule
era mid-solve; analysis.json e' lo stato mid-attempt-3 -2221hard, NON il pick-best). Il
master batch poi ha killato l'app (partito su bad-seed).

## Segnale (onesto, ma da confermare con re-run)

**449 ON NON ha raggiunto 0hard in 2 tentativi completi** (best attempt 2 = -19hard), mentre
OFF ha raggiunto 0hard in 2. Legge suggestiva: **449 ON degrada l'hard** (il pre-booking
over-constrain il bacino: attempt 2 -19hard e' peggio del bad-seed H17 -6 del OFF, e il
multi-start non lo cura in 3 tentativi). Coerente col caveat della Final Summary (l'allocatore
non aiuta) MA qui il segnale e' piu' forte: non solo non aiuta, **danneggia** (introduce hard
che il multi-start non cura). Adozione leans NEGATIVE.

Caveat: attempt 3 e' stato tagliato; un re-run pulito (cap piu' lungo) potrebbe mostrare che
attempt 3 raggiunge 0hard (allora 449 ON = piu' lento ma funziona) o confermare il -19hard
(allora 449 ON danneggia). Il -19hard e' il best COMPLETATO, quindi il segnale negativo ha
peso, ma non e' conclusivo.

## Difetto harness (fixato)

baseline-golden.sh poll cap era hardcodato 150 min: troppo corto per un arm 3-tentativi (449
ON degrada il bacino -> piu' tentativi). FIXATO: POLL_CAP ora e' env-configurable
(`${POLL_CAP:-150}`), default 150. Il re-run ON usa POLL_CAP=240.

## Re-run CONFIRMED negative (2026-08-10, master-batch-2, POLL_CAP=240)

Re-run pulito dell'arm ON: 3 tentativi completi (179 min wall), capture clean. **Best = -5hard
(NON 0hard)** anche con 3 tentativi. Quindi: OFF 0hard (2 tentativi, 75,8% ricorrenza, 85,9%
full-day); ON -5hard (3 tentativi, NESSUNO a 0hard, 73,1% ricorrenza, 88,0% full-day, 179 min).

**VERDETTO FINALE: 449 RULED OUT.** 449 ON non raggiunge 0hard neanche con 3 tentativi
completi (best -5hard) vs OFF 0hard in 2. Il pre-booking mono-room-tag OVER-CONSTRAIN il
bacino: introduce un residuo hard (-5hard) che il multi-start non cura. Confermato su run
pulita (non artefatto cap). La soluzione strutturale resta capacita' (piu' stanze/tag) o
de-tag, non l'allocatore. Prototipo resta committed default-OFF. NESSUNA adozione.

## Re-run queued (STORICO, sostituito sopra)

master-batch-2 aspetta il bad-seed (master-done) poi re-runna l'arm ON pulito (POLL_CAP=240,
capture clean). Risultato: conferma NEGATIVA (449 ON non 0hard) o MISTA (449 ON 0hard ma
lento). Verdetto finale dopo il re-run.

## Per Fedora (adozione)
- NON adottare 449 ora: il segnale e' suggestivo-negativo (-19hard best, non 0hard in 2
  tentativi) + la Final Summary diceva non-aiuta. Un re-run pulito confermera'.
- Se il re-run conferma -19hard/non-0hard: 449 Ruled OUT (danneggia). Se 0hard-lento: 449
  neutro (non vale il wall). Entrambi -> non adozione.
