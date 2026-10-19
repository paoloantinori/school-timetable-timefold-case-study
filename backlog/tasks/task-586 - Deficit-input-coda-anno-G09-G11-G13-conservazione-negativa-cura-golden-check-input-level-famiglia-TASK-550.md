---
id: TASK-586
title: >-
  Deficit input coda-anno: G09/G11/G13 conservazione negativa (cura
  golden-check input-level, famiglia TASK-550)
status: To Do
assignee: []
created_date: '2026-09-05 10:17'
updated_date: '2026-09-06 06:35'
labels:
  - claude-planned
  - input-first
  - golden-check
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task577-results/notte-4sep/
  - >-
    backlog/tasks/task-577 -
    Approfondimento-15hard-teacher-locked-finestre-mag-giu-censimento-dalle-capture-esistenti-e-classificazione-multi-start-vs-strutturale.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Diagnosi dal consegnato notte-4sep (5/09, messa a fuoco durante la v12 SurplusHarvest): le celle residuali non sono tutte uguali. Tre gruppi hanno CONSERVAZIONE NEGATIVA fra ore collocate e calendario dichiarato in-grid (G09 -14h: 664 vs 678; G11 -8h: 671 vs 679; G13 -6h: 683 vs 689): il pool non copre il calendario, NESSUN pass di move puo' chiudere tutte le celle, e le celle a conservazione zero (G06 x2, G12 x1, delta=0 con giorni over-quota esistenti) SONO invece curabili da pass (v12 in volo). Direttiva applicabile: la famiglia TASK-550 (quando un numero di input eccede cio' che il golden pratica, si assume il golden e si corregge l'input). Prima cura OBBLIGATORIA: chiedere al golden che cosa fa su quelle date (fill vs vuoto), NON al solver. Celle coinvolte (dal consegnato): G09 03/06 quota 5 vuota; G11 04/06 quota 4 vuota; G13 16/12 quota 5 (piazzate 4); + il residuo sanity dei giorni sotto-min. Verifica meccanica: script analitico fornito nella diagnosi (conservazione per gruppo = somma piazzate vs somma quote in-grid).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Conto golden per ogni cella del filone (l'unico esito onesto: golden riempie o non riempie)
- [ ] #2 Cura applicata SOLO dove il golden la pratica (input corretto o domanda a referente aperta)
- [ ] #3 Il check di derivazione golden gira verde
- [ ] #4 Se il golden lascia la cella vuota: riga quota corretta in input o dichiarazione esplicita (mai solver vs impossibilita' matematica)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CONTO GOLDEN (5/09, criterio AC#1 CHIUSO): il golden RIEMPIE tutte le celle del filone a quota esatta (G09 03/06=5h, G11 04/06=4h, G13 16/12=5h, G06 09+10/06=4h, G12 07/05=4h; ex-sabati/Fasullo/Stage). Totale per gruppo in-grid (quota dichiarata vs golden vs run notte-4sep): G01 990/990/990, G02 990/990/990, G03 969/990/990 (quota SOTTO golden di 21), G05 990/990/990, G06 990/990/990, G07 690/695/695, G09 678/666/666 (quota SOPRA golden di 12), G10 674/670/670 (+4), G11 679/671/671 (+8), G12 675/675/675, G13 689/683/683 (+6). LETTURA: (a) run == golden su OGNI gruppo (il pool e' golden-mirato, seeding TASK-550); (b) i gruppi deficitari (G09 -12, G10 -4, G11 -8, G13 -6) hanno input che dichiara oltre la pratica golden: cura input (ritagliare le righe quota alle date che il golden lascia vuote, forma CURA G09 27/08), NON solver; (c) 1/G07 dichiarano MENO del golden (surplus over-declared-plan: H32 penalizza solo deficit, M33 medium il surplus: nessuna azione hard); (d) le celle G06 x2 e G12 (conservazione 0 con giorni over-quota esistenti, golden li riempie) restano territory del pass: v12 SurplusHarvest in volo le prende.

CORREZIONE 6/09 mattina (la premessa del deficit e' CONFUTATA dal conto per-data): il presunto deficit (-12/-4/-8/-6) era un ARTEFATTO delle righe quota dietro la griglia (G09 14+15/06 quota 16h, G10 15/06 8h, G11 15/06 8h, G13 15/06 6h: date SENZA timeslot per quei gruppi, fuori scope H32/H34 ma dentro la somma). In-scope la quota dichiara ESATTAMENTE il golden per G10 (666), G11 (671) e G13 (683); G09 sta 4h SOTTO il golden (662 vs 666: il foglio sotto-dichiara, eccezione nota 01/10 6-vs-5 inclusa). CONSEGUENZE: (a) NESSUN taglio input per le celle vuote: tutte (G06 x2, G09 03/06, G11 04/06, G12 07/05) sono a conservazione zero = territory del pass SurplusHarvest; (b) verificata la fattibilita' combinatoria sul consegnato: greedy con capacita' donatori chiude TUTTE e 5 le celle (2-5 docenti alternativi per slot, capacita' 19-36h vs quote 4-5): se il pass v12 non le chiude il collo e' la sua ricerca, non la disponibilita'; (c) il residuo del task diventa IGiene input: le righe 14-15/06 senza griglia inquinano le riconciliazioni (questa stessa lettura sbagliata ne e' la prova): valutare la rimozione o la marcatura, famiglia input-hygiene non cura; (d) la cella H7 del consegnato e' T26 30/09 7h>6 (una sola, famiglia separata, curabile con micro-riparo o donazione).
<!-- SECTION:NOTES:END -->
