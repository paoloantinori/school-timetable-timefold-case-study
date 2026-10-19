---
id: TASK-411
title: >-
  Rigidità del template settimanale — fingerprint golden quasi fisso vs nostro
  diffuso (52-89%)
status: Done
assignee: []
created_date: '2026-07-24 11:40'
updated_date: '2026-07-25 06:21'
labels:
  - claude-planned
  - research
  - search
  - spalmone-2027
dependencies:
  - TASK-409
  - TASK-410
priority: medium
ordinal: 235000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Seconda differenza rivelata dal fingerprint visivo (TASK-408, maintainer 2026-07-24): la
settimana golden è un template quasi RIGIDO (colonne al 97-100%, blocchi pomeridiani piccoli
e netti — es. G04 lun/mar 18% = pattern lab periodico), la nostra è DIFFUSA (52-89%
ovunque, nessuna struttura netta). Il KPI numerico di ricorrenza (~36%) sottorappresenta
quanto la struttura sia visivamente diversa: il golden non "ricorre al 36%", È un template
con eccezioni.

NOTA (2026-07-24, dopo il riquadro di maintainer in TASK-413): il MECCANISMO per questa task è
la semantica "quasi-pinned" del template descritta in TASK-413 (solve a stadi con template
pinnato + release selettivo, o penalità di deviazione dal template fisso) — questa task
resta come MISURA/GATE (metrica di nitidezza fingerprint) delle opzioni là elencate.

EXPLORATIVE: quali leve avvicinano il fingerprint al golden senza pagare hard/box?
Candidate (da misurare, non tutte insieme):
1. Peso S13 (weekly stability, oggi soft 2 — "start low" mai ricalibrato dopo il template
   stamp): un A/B con S13 sensibilmente più alto (es. 20-50) è l'esperimento più economico.
2. Mosse LS template-preserving: oggi la LS può rompere celle-template liberamente; una
   variante che preferisce mosse INTRA-cella settimanale (stesso weekday+slot di settimana
   diversa) preserverebbe la struttura — richiede custom move selector (ricerca Timefold).
3. "Template lock" morbido post-costruzione: pinnare-soft (non pinned reale) le celle
   template ad alta confidenza e lasciare libere solo le code — rischioso per la
   feasibility, ultima opzione.
GATED da 409+410: prima chiudere i buchi di fill (che forzano deviazioni dal template),
poi misurare quanta diffusione RESTA — parte della diffusione attuale è probabilmente
conseguenza, non causa. Metrica: il fingerprint stesso (% celle di gruppo con occupazione
≥90% o ≤10%, "nitidezza") aggiunta a golden-vs-run-viz o script sibling, con soglia dal
golden.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore + A/B laptop con metrica di nitidezza fingerprint
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Giro pulizia 2026-07-25: SCOPE RIDOTTO - il gate sostanziale e' gia' stato esercitato nell'A/B val413 con proxy equivalenti (burst ratio, gap-weeks, ricorrenza, viste fingerprint della viz). Resta SOLO la metrica numerica di nitidezza dedicata (% celle >=90/<=10) se la si vuole come KPI pinnato accanto a cadence-kpi.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
