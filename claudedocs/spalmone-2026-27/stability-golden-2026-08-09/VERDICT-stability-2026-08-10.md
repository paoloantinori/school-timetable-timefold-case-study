# VERDICT stability good-seed x3 (zai, 2026-08-10, Fedora-offline)

3 run source=golden (ratified config, tutti i flag DEFAULT ON) within-session, per
caratterizzare la varianza run-to-run del good-seed golden path (laptop-RUNBOOK: "stability
matters as much as the minimum"). master-batch-3, profile stab-1/2/3, capture canonica.

## Risultati

| run | hard | medium | ricorrenza unpinned | note |
|-----|------|--------|---------------------|------|
| 1 | **0hard** | -1.266.925 | 75,7% | |
| 2 | **0hard** | -1.238.947 | 75,5% | |
| 3 | **0hard** | -1.241.602 | 75,6% | |

(plus il baseline iniziale 0hard/75,9% = 4° dato source=golden coerente.)

## Verdetto: good-seed golden path STABLE

- **hard: 0hard 3/3** (affidabile). Nessun bad-seed H17 nelle 3 run. Il path golden consegna
  0hard riproducibilmente (il 2-attempt del baseline iniziale era il multi-start che cura un
  occasional bad-ish seed, ma l'esito finale 0hard e' certo).
- **ricorrenza: 75,5-75,7%** (spread 0,2pp). Stretto: la ricorrenza unpinned e' stabile.
- **medium: -1.238k/-1.267k** (spread ~28k, ~2%). Stabile.
- La variabilita' e' tutta entro la banda NON_REPRODUCIBLE LS attesa; nessuna run outlier.

## Per la campagna (Fedora/maintainer)

I numeri ratificati del golden path (hard=0, ricorrenza ~75,6%, full-day ~86-88%) sono
**riproducibili**: 3/3 run 0hard con KPI spread stretto. La confidenza nei numeri di riferimento
e' alta. La fonte di varianza residua (il bad-seed H17 probabilistico) e' nel path INPUT
(caratterizzato dal bad-seed N=16/N=32), non nel golden path che e' stabile.

Conferma indiretta del bad-seed N=16 (p=1,0): il golden path e' stabile a 0hard, e anche il
path input (con M-TC per-retry) e' robusto (16/16). La procedura multi-start e' affidabile su
entrambi i lati.
