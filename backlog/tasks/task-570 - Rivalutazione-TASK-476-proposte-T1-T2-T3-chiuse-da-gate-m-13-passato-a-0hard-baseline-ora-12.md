---
id: TASK-570
title: >-
  Rivalutazione TASK-476: proposte T1/T2/T3 chiuse da gate m-13 passato a 0hard
  (baseline ora -12)
status: Done
assignee: []
created_date: '2026-08-29 11:41'
updated_date: '2026-08-29 12:10'
labels:
  - claude-planned
  - rivalutazione
milestone: m-13
dependencies: []
references:
  - >-
    backlog/completed/task-476 -
    Diagnosi-input-profile-run3-proposte-tuning-percorso-al-gate-di-parita-m-13.md
priority: medium
type: spike
ordinal: 322000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Creata dalla scansione TASK-567 (29/08). TASK-476 (diagnosi input-profile run3) chiuse le proposte di tuning T1 (disponibilita' venerdi), T2 (G10 -1h), T3 (profilo unassigned) con "gate m-13 APPROVATO (maintainer 2026-08-11, chiusura procedurale via multi-start, p(0-hard) procedurale = 32/32)": una condizione di superamento (-0hard) che al baseline attuale (-12hard deterministico, -69/-134 bacini GRASP) non vale piu'. Citazione verificata (riga 54 del task completato).

La rivalutazione: le tre proposte tornano candidate legittime di tuning del PERCORSO INPUT; decidere se il profilo run3 (32% bad-seed) merita T1/T2/T3 al nuovo baseline, con predizione prima e criteri verdict-check.

NOTA TASK-449 (classe b confermata ma MITIGATA, niente task dedicato): l'allocatore lab mono-stanza fu bocciato per "0hard -> -5hard" (baseline vecchio) + "non chiude collo" (questa regge); il suo bersaglio (coda H29 tag scarse) e' gia' chiuso dal RoomTailFinisher ratificato 25/08: rivalutare SOLO se la coda stanze si riapre.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Rivalutazione eseguita 29/08 ~14:20 (fonti: TASK-476 completato letto con definizioni T1/T2/T3 e AC; sanity/buste dei bracci A/B odierni; CLAUDE.md ratifiche TASK-497/550; stato delle domande referente pendenti). ESITO: le tre proposte sono OBSOLETE NELLA FORMA ORIGINALE, e le questioni sopravvissute sono gia' instradate: T1 (disponibilita' venerdi' docenti G04 per colmare 19 ven deep -2h) mirava a un sintomo che oggi non esiste piu' in quella forma: nei bracci A/B di oggi G04 legge il problema OPPOSTO (sovrabusta: run 6h vs busta [4..5] su ~106 giorni, full-day 80% vs golden 47%), e la domanda giorni-corti 1/G08 e' gia' pendente a referente (domande-brevi rev 14/08, citata nella ratifica TASK-497); eseguire un A/B di disponibilita' ora misurerebbe altro. T2 (-1h G10, trade-off concentrazione T01) e' una conferma/dichiarazione di pratica scolastica, non un run: resta domanda referente potenziale, subordinata alla stessa famiglia. T3 (3 unassigned = profilo constrained-teacher) e' posseduto dai meccanismi ratificati (source=input 16/08 + ExactFinisher + multi-start N=5). NESSUN BANCO, NESSUNA ADOPZIONE: la rivalutazione al baseline -12 chiude T1/T2/T3 come superate-dalla-forma, con le domande vive gia' nel canale referente/workbook. Condizione di riapertura: se referente risponde sulla busta G04 e la risposta implica rilassare disponibilita', T1 rivive in forma workbook (foglio disponibilita'), non come tuning solver.
<!-- SECTION:FINAL_SUMMARY:END -->
