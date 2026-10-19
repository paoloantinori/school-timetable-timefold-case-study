---
id: TASK-575
title: >-
  Stabilita' coppia (docente,gruppo): celle fisse ed esaurimento materie a
  blocchi (prassi referente)
status: Done
assignee: []
created_date: '2026-08-30 19:26'
updated_date: '2026-08-31 19:50'
labels:
  - claude-planned
  - golden-first
  - construction
milestone: m-13
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla prassi descritta da referente (30/08) e verificata nel golden con KPI+viz (scripts/analyze/pair-stability-kpi.py, pair-stability-golden-vs-baseline.html, commit 8fcd5d21):

LA PRASSI: per una coppia (docente, gruppo) le celle (weekday, slot) restano fisse tutto l'anno e le materie si alternano A BLOCCHI (prima si esaurisce un corso, poi il prossimo parte negli stessi slot). Nel golden e' piena su 17/169 coppie, dominate dai docenti di LAB (T16 xG01: 179 lezioni 1 switch; T10 x G13: celle 100% stabili; T07, T01, T02); i teorici intrecciano (T20 128 switch, T09 117). Quindi: tendenza reale del ramo lab, non legge universale: HARD vietato (violerebbe il golden sugli intrecciatori).

IL GAP NOSTRO: i run erodono la prassi MOLTO oltre il golden: T16 xG01 switch 1->14, T10 x G13 celle 100%->0%, T03 xG03 weekday 94%->49%. Il template M32 e' chiave (gruppo, materia) e non preserva ne' l'ancoraggio weekday della coppia ne' la sequenzialita' a blocchi dei corsi.

OPZIONI DI MODELLO (decisione maintainer):
A. MEDIUM self-gating: reward per (docente,gruppo) che mantiene le celle fisse e le materie a blocchi, attivo solo dove il golden stesso pratica (self-gating M34-style per coppia: il reference dice quali coppie sono 'da laboratorio').
B. COSTRUZIONE: il WeeklyTemplateBuilder alloca celle per coppia (docente,gruppo) e sequenzia le materie dentro (esaurimento a blocchi): la prassi diventa forma del template, non penalita'.
C. KPI-only: tenere la metrica come guardia di qualita' nei capture (banda per run) senza toccare il modello.

Riferimenti: la tabla KPI nel commit; il viz a piccoli multipli (bande solide golden vs confetto run).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DECISIONE maintainer (31/08 22:00): B+C approvati, A differito. B = costruzione pair-cells prosegue con F5 (cura dello stamp, prototipo flag-OFF, giudicato dal banco con la regola template-holes+costruzione ~0 prima di qualsiasi ciclo); C = KPI pair-stability entra nei capture; A = medium self-gattering differito a F5 consegnato (rischi condivisi: conflitto con M32 finché la costruzione non crea la forma, lever inerte sul passato M-TR, debito golden-runtime del self-gating). Evidenza che ha informato la decisione: F3 chiuso (precision 80,7% vs 38,8%), template pair più compatto (14-25 buchi vs 83), ciclo F4 morto nello stamp (skip-exhausted 3945 vs 3032, buchi 114 vs 43 slot a parità di fixture).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
