# VERDICT via (b) - quota-tail moves-placed (TASK-533, 2026-08-20)

Esito condiviso con maintainer: la via (b) NON paga. Prototipo committed flag-OFF, NON armare.

## Cosa e' il prototipo

Commit `f0bccf1b` (branch `worktree-multithreaded-ls-experiment`, su `1226092e`):
`DayClusteredConstructionPhase.quotaTailMovesPlaced`, flag
`planner.construction.quota-tail-moves-placed.enabled` DEFAULT OFF. Pass a fine
A-CONSTRUCTION (dopo round-robin + compaction) che SPOSTA ore gia' piazzate dai
giorni surplus ai giorni sotto-quota dello stesso gruppo, bordo-consapevole alla
QFR: donatore solo ai bordi del run (coda prima, testa solo se slot > 0),
target solo estensione `max+1` (mai prima ora), gate H1/H3 live + VRP del corso,
donatore mai sotto la propria quota, esclusioni H32 (1/G08, G14).

Nota di posizionamento (costata una misura): il pass dentro lo stamp e' INERTE
(0 mosse) perche' lo stamp non accetta over-quota e i giorni surplus nascono
solo DOPO (coda round-robin + pin). Il punto-firma giusto e' fine fase.

## Firma misurata (checkpoint, real-seed construction test)

`WeeklyTemplateConstructionRealSeedTest` 13 run / 0 failures (EXIT 0):

| | celle sotto-quota fine A-CONSTRUCTION |
|---|---|
| flag OFF (baseline) | 41 |
| flag ON | 39 (4 mosse, 2 celle chiuse, 0 conflitti, ricorrenza 72,6% invariata) |

Rifiuti per causa (contatori nel log del pass): **VRP 223** (dominante),
conflitto H1/H3 65, no-target-slot 1, donor-tail pinned/pseudo 0.

## Perche' non paga

Il typing VRP delle ore in surplus (materia quota-bound H23-24, disponibilita'
docente H5, finestra calendario H20) vieta loro la data target: quelle ore
stanno sul giorno donatore PRECISAMENTE perche' non possono stare altrove. Le
mosse bordo-legali sono strutturalmente poche, e le 2 celle chiuse sono nel
bacino che il QFR esistente copre comunque (26 -> 8 celle misurate in vivo).

## Quadro per maintainer (decisione sua, invariata)

Le 8 celle residue (5x G10 + 3x G12, tutte ~1h, donori esausti a fine
ciclo) restano fra:
1. accettazione/classificazione;
2. maxAttempts multi-start piu' alti (la H17 e' bacino-dipendente ed e' la
   variabilita' dominante fra i run).

Strumento forte per l'H32: il QuotaFillRepair libero a due occasioni, gia' in
produzione. Il flag via (b) resta DEFAULT OFF.
