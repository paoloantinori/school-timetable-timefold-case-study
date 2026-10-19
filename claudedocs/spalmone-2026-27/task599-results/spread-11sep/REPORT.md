> **AVVISO CONTAMINAZIONE (12/09, TASK-605)**: questo ciclo ha servito il JAR del
> worktree (build 29/08: pool G08 626, zero Recuperi = provato dalla discriminante
> RA). Il delta [-26]->[-11] resta leggibile come evidenza di MECCANISMO (il lever
> era nel build 29/08) ma NON come gate sul quadro attuale: le gambe rimanenti della
> ratifica TASK-599 (banco locale Fedora, iter-11, regola del pianificatore umano)
> non sono toccate. Classificazione completa:
> ../../m14-results/AUDIT-CONTAMINAZIONE-2026-09-12.md

# REPORT: GATE DI ADOZIONE teacher-weekday-spread (spread-11sep, 11-12/09)

## ESITO IN TESTA (riportato, non interpretato, come da mandato)

**Consegna -11hard: NETTO MIGLIORAMENTO dal -26 del control-11sep**
(banda attesa derivata dal quadro: qualunque valore fosse uscito, andava
riportato). Il lever resta OPT-IN finche' Fedora non ratifica.

## Score di consegna

score.txt (DB): `-11hard/-1375421medium/-3572soft`
analysis.json (riconteggio): `-11hard/-868610medium/-3572soft`
(hard concorde; medium divergente TASK-509 noto)

## Vettore hard per famiglia (analysis.json)

| famiglia | hard | matches | control-11sep | delta |
|----------|------|---------|---------------|-------|
| H34 | **-10** | 10 | -13 (13) | migliora |
| H7 | **-1** | 3374 | -3 | migliora |
| H0 | **0** | 0 | -6 (3 lez G14) | **CHIUSE** |
| H33 | **0** | 0 | -2 | chiuse |
| H29 | **0** | 0 | -1 | chiuse |
| H31 | **0** | 0 | -1 | chiuse |
| TOTALE | **-11** | | **-26** | **+15** |

## Tentativi e durata

Multi-start 5/5, vincitore attempt-1 deterministico ("restoring best
attempt 1 (last run was #5)" alle 05:43:47, validated 05:43:53). Lancio
21:09, consegna 05:43, durata ~8h34m.

## Dottore

`DOCTOR-WARN sanity-reds=4 hard=[-11/0]` + WARN export 105MiB sopra cap
(resta sul laptop). EXIT 0. Hard CONCORDI (-11).

## RISPOSTE ALLE CINQUE DOMANDE PRE-REGISTRATE

(a) **Le 3 ore H0 G14 si collocano?** SI': H0=0 matches (-0 hard, da
    -6/3 lezioni del control). Il triangolo T16/G05/G14 si e'
    sciolto: coerente con la migrazione Confezione-G14 venerdi'->giovedi'
    misurata al banco (T16 al venerdi' non piu' satura).
(b) **Ricorrenza/full-day in banda?** SI': ricorrenza UNPINNED 70,3%
    (banda 76%+-2: LEGGERMENTE SOTTO, da segnalare), full-day 91,3%
    (banda 90%+-2: DENTRO).
(c) **Collaterale iter-11 H20-pin-fuori-finestra?** NON riappare: zero
    righe H20-pin nel log, zero H20 nel vettore hard.
(d) **Sotto-quota costruzione ~39?** Il guard non produce righe greppabili
    col pattern cercato nel log del ciclo (il banco lo misurava a 39 su
    costruzione isolata): dato NON estraiibile da questo log, dichiarato.
(e) **Sanity rossi vs i 4 del control?** 4 gruppi anche qui: G07,
    G08, G09, G10 (G14 e' USCITO dai rossi, G08 e' RIENTRATO:
    composizione diversa, non aggravio numerico). Zero lezioni senza stanza.

## NOTA BANNER (gap osservabilita')

Il flag `teacher-weekday-spread.enabled=true` e' verificato NEL PROCESSO
(ps alle 21:09) ma il banner "Construction/repair flags" NON lo echoa
(stessa famiglia del gap surplus-harvest del 5/09, cce67200): da censire
come banner-echo mancante se il lever viene ratificato.

## Checkpoint inventory

staged-checkpoints/ con attempt dirs del ciclo (inventario sul disco,
input witness-work TASK-599).

## Confronto riassuntivo

spread-11sep [-11] vs control-11sep [-26]: +15 hard. La componente
migliorativa domina: H0 G14 chiuse (la cura mirata funziona
end-to-end), H33/H29/H31 azzerate, H34 13->10, H7 3->1. La ricorrenza
unpinned 70,3% e' l'unico asse leggermente fuori banda (76%+-2). Il
lever resta opt-in: ratifica a Fedora/maintainer.

## Export

export.json.gz (105 MiB) resta SOLO sul laptop in questo direttorio, non
committato (cap GitHub 100MB).
