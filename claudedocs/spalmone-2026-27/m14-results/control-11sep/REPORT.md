> **AVVISO CONTAMINAZIONE (12/09, TASK-605)**: questo ciclo ha servito il JAR del
> worktree (build 29/08: pool G08 626, zero Recuperi = provato dalla discriminante
> RA). I dati restano validi COME STATO CONSEGNATO; NON valgono come misura del
> quadro attuale (RA-cure assente) e l'uscita di G08 dai sanity reds non e'
> attribuibile alla cura RA. Classificazione completa:
> ../AUDIT-CONTAMINAZIONE-2026-09-12.md

# REPORT: ciclo di controllo control-11sep (m-14, 11/09) - ANOMALIA IN TESTA

## ANOMALIA (riportata per prima, senza interpretazione, come da mandato)

**Consegna -26hard: PEGGIORE della soglia anomalia -20** (banda attesa
[-13..-17]). Vettore completo sotto. Il dottore ha validato la cattura
(DOCTOR-WARN, hard concorde -26): il numero e' reale, non un artefatto di
lettura. Dati grezzi per Fedora: la classificazione e' sua.

## Score di consegna

score.txt (DB): `-26hard/-1374377medium/-12390soft`
analysis.json (riconteggio): `-26hard/-873520medium/-12390soft`
(hard concorde fra le sorgenti; medium divergente TASK-509 noto)

## Vettore hard per famiglia (analysis.json)

| famiglia | hard | matches | control-10sep | slotretry-9sep |
|----------|------|---------|---------------|----------------|
| H34 Empty declared day | **-13** | 13 | -10 (10) | -3 (3) |
| H0 Unassigned lesson | -6 | 3 lez. | -6 (3 lez.) | -2 (1 lez.) |
| H7 Max teacher daily hours | **-3** | 3325 m | -1 | 0 |
| H33 Day floor | **-2** | 1 | 0 | -3 |
| H29 Room-null | **-1** | 1 | 0 | 0 |
| H31 Start-of-day | **-1** | 1 | 0 | 0 |
| H28 | 0 | - | 0 | -4 |
| H32 | 0 | - | 0 | -1 |
| TOTALE | **-26** | | **-17** | **-13** |

## Tentativi e durata

Multi-start 5/5, vincitore attempt-1 deterministico ("restoring best attempt
1 (last run was #5)" alle 19:02:38, validated 19:02:45). Lancio 11:41,
consegna 19:02, durata ~7h20m.

## Dottore

`DOCTOR-WARN sanity-reds=4 hard=[-26/0]` + WARN export 105MiB sopra cap
GitHub (resta sul laptop). EXIT 0. Hard score/analysis CONCORDI (-26).

## Sanity

NON CONFORME, 4 gruppi rossi: **G07** (28/05 x2, 09/06 x3), **G09**
(24/02, 21/04), **G10** (08/04 x2, 09/04, 30/04), **G14** (1 lezione
senza stanza). Full-day 89,6%.

## RISPOSTE ALLE DOMANDE PRE-REGISTRATE

(a) **Le 3 ore H0 G14 sono ancora non collocate?** H0=3 matches (-6pt,
    stesso conteggio del control-10sep): la struttura delle 3 lezioni
    unassigned e' invariata (identificazione per-gruppo nel dump: le
    sanity reds mostrano G14 con 1 lezione senza stanza, coerente col
    triangolo T16/G05/G14 noto).
(b) **Il rosso G08 si e' mosso col pool 667?** SI': G08 e' USCITO dai
    sanity reds (control-10sep: G07, G08, G09, G10; control-11sep:
    G07, G09, G10, G14). La cura input TASK-592 ha spostato il
    gruppo fuori dal rosso.
(c) **H34: quante e quali?** 13 celle (-13), in AUMENTO da 10 (control-10sep)
    e 3 (slotretry). L'elenco per-cella e' nel dump/confronto HTML:
    classificazione a Fedora.

## Checkpoint inventory (witness-work TASK-599)

staged-checkpoints/ con attempt dirs del ciclo (inventario sul disco).

## Confronto riassuntivo (quadro-vs-quadro)

control-11sep [-26] e' PEGGIORE di control-10sep [-17] su H34 (10->13),
H7 (1->3), e introduce H33 (0->2), H29 (0->1), H31 (0->1); H0 invariato
(3 lez). Migliora solo la voce sanity del G08 (uscito dal rosso col
pool 667). La cura input RA ha spostato il quadro: il come e' domanda
di Fedora, il dato e' qui.

## Export

export.json.gz (105 MiB) resta SOLO sul laptop in questo direttorio, non
committato (cap GitHub 100MB).
