# REPORT: ciclo di controllo control-10sep (m-14, 10-11/09)

Ciclo sui DEFAULT ratificati a HEAD df331488 (10 commit oltre la slotretry-9sep,
incluse RATIFICA pool-cap-fill 567ffff0 e rimozione golden-monthly 1e994d83):
primo delivery sul HEAD corrente. Profilo fresco control-10sep, costruzione
deterministica, checkpoint capture attiva, NESSUN flag extra oltre il
checkpoint (si misurano i default). Lancio 23:25, consegna 06:40, durata ~7h15m.

## Score di consegna

score.txt (DB): `-17hard/-1363239medium/-17568soft`
analysis.json (riconteggio): `-17hard/-858969medium/-17568soft`
(hard concorde fra le sorgenti; il medium divergente e' TASK-509 noto)

## Vettore hard per famiglia (analysis.json)

| famiglia | hard | matches | vs slotretry-9sep [-13/0] |
|----------|------|---------|---------------------------|
| H34 Empty declared day | -10 | 10 | 3 -> 10 (peggiore) |
| H0 Unassigned lesson | -6 | 3 | 1 lez. -> 3 lez. (peggiore) |
| H7 Max teacher daily hours | -1 | 3310 match-count | 0 -> 1 (nuovo) |
| H28 | 0 | - | 4 -> 0 (migliore) |
| H33 | 0 | - | 3 -> 0 (migliore) |
| H32 | 0 | - | 1 -> 0 (migliore) |
| TOTALE | **-17** | | -13 -> -17 |

## Tentativi

Multi-start 5/5 (tentativo 1 deterministico = VINCITORE, "restoring best
attempt 1"), nessun 0-hard fra i tentativi. Reseed GRASP: 15+ attempt dirs
nei checkpoint.

## Dottore (TASK-579.01 dogfood)

`DOCTOR-WARN sanity-reds=4 hard=[-17/0]` + WARN export 106MiB sopra il cap
GitHub (resta sul laptop, path sotto). EXIT 0. Hard score.txt vs analysis:
CONCORDI (-17).

## Sanity

NON CONFORME, 4 gruppi rossi: G07 (09/06 x3 sotto-min + buco interno),
G08, G09 (bucо interno 01/04), G10 (19/03, 08/04). Zero lezioni senza
stanza. Full-day 90,4%.

## Checkpoint inventory (input del witness-work)

staged-checkpoints/ contiene attempt1..attempt15+ (ogni tentativo multi-start
con la sua serie di confine): l'inventario completo e' sul disco, parte
dell'artefatto del ciclo.

## Confronto riga per riga vs slotretry-9sep

| asse | slotretry-9sep | control-10sep | delta |
|------|---------------|---------------|-------|
| hard totale | -13 | -17 | -4 (peggiore) |
| H28 | 4 | 0 | chiusa |
| H33 | 3 | 0 | chiusa |
| H34 | 3 | 10 | +7 (peggiore) |
| H0 | 2pt/1 lez. | 6pt/3 lez. | +2 lez. (peggiore) |
| H32 | 1 | 0 | chiusa |
| H7 | 0 | 1 | nuovo |

NOTA di contesto: slotretry-9sep girava su 8a2c5287 con surplus-harvest
ARMAРO esplicitamente; control-10sep gira sui DEFAULT ratificati (SH ON per
default ora, pool-cap-fill ON, golden-monthly rimosso): il confronto e'
il QUADRO-vs-QUADRO, non una singola variabile. Le famiglie che migliorano
(H28/H33/H32) e le nuove criticita' (H34 10, H0 3, H7 1) sono il vettore
per la prioritizzazione di Fedora.

## Export

export.json.gz (106 MiB) resta SOLO sul laptop in questo direttorio, non
committato (cap GitHub 100MB, prassi).
