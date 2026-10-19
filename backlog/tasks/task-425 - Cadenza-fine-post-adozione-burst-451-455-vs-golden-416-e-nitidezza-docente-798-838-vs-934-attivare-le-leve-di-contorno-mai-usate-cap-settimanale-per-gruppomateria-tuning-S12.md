---
id: TASK-425
title: >-
  Cadenza fine post-adozione: burst 45,1-45,5 vs golden 41,6 e nitidezza docente
  79,8-83,8 vs 93,4 - attivare le leve di contorno mai usate (cap settimanale
  per (gruppo,materia), tuning S12)
status: Done
assignee: []
created_date: '2026-07-27 04:45'
updated_date: '2026-07-27 18:27'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 248000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il gap di FORMA e' chiuso dall'adozione staged+treno-2 (ricorrenza 73,5-73,9, erosione 0); resta il gap di CADENZA FINE: burst>2 45,1-45,5% (golden 41,6), nitidezza pooled 79,8-83,8% (golden 93,4), gap-weeks ~1.720-1.830. Le leve candidate sono quelle censite a fine TASK-413 e mai attivate perche' il quasi-pinning (M32) e poi lo staged hanno preso la priorita': (a) cap settimanale per (gruppo, materia) tipo max-hours/week derivato dal template (anti-burst diretto sulle ultime settimane); (b) tuning S12 weekly presence; (c) eventuale reward di ripetizione cella multi-settimana oltre S13. Base di misura: kpi-cadence (burst, gap-weeks) e fingerprint-sharpness sui run adottati; riferimenti pinnati golden-cadence-reference.json. Catena: constraint/knob -> ConstraintVerifier -> re-score statico -> smoke A/B batteria -> laptop x2. Partire DOPO TASK-424 (che puo' muovere la base).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANATOMIA STRUTTURALE (viz+cadence su train2-run1 vs golden reference, 2026-07-27 ~07:00): DUE firme distinte, servono DUE leve. (1) BURST-heavy: il delta burst e' concentrato in 4 gruppi - G08 +24,3pt (61,1 vs 36,8), G07 +23,5, G03 +18,7, G12 +17,7 - e i pair peggiori sono materie MICRO (Sicurezza 12h/anno burst 12,67, Sviluppo Sostenibile 10h burst 11,40): i corsi piccoli vengono compressi in raffiche invece della cadenza golden -> leva = cap settimanale per (gruppo,materia) o quota-per-week dal template. (2) GAP-heavy: la famiglia MODA ha burst A PARITA' col golden ma gap-weeks doppi (G05 8,45 vs 3,85; G01 6,55 vs 2,85; G09 10,71 vs 6,10): settimane intere senza la materia -> leva = presenza settimanale (S12 rafforzato o punizione gap-week). NB: 5 gruppi sono GIA' a livello golden o MEGLIO (G04 -23,6pt!, G10 -10, G13 -7,2, FA/MODA-1-2 pari): le leve vanno mirate, non globali. Report HTML inviato a maintainer (confronto-golden-vs-train2.html).

VERIFICA + DoD laptop (2026-07-27, su implementazione opus-subagent):

L0 ConstraintVerifier: 7 nuovi test M34/M35 (at-cap/above/absent/pseudo; at-target/below/absent) PASS. Constraint count 52->54.

L1 golden guard: M34 0/0/0 (structural zero, structural per costruzione: il golden non bursts sopra il proprio max); M35 -50/1 RESIDUO CALIBRATO DOCUMENTATO (G04/Scienze: W24 oltre SCHOOL_YEAR_END, stessa classe del gap S12 5.623-vs-5.625). git diff: PURE ADDITIONS, NESSUN pin esistente cambiato.

Full suite orchestratore: test.sh EXIT=0 reale, 832 test / 0 fail / 0 errori / 11 skip.

Design verificato su real artifact: isoWeekKey (WeekFields.ISO) matcha il isocalendar del KPI; SubjectNormalization simmetrica load+lookup; golden-cadence-reference.json ha i campi max (M34) e active_weeks (M35).

/simplify (4 cleanup agent via 2 dispatch): NESSUN bug correttezza. Finding: #1 readCadenceKnobProperty duplica readM32KnobProperty (DEFER - drive-by su M32 adottato); #2 cast (int) ridondante in M34 (DEFER - cosmetico); #3 javadoc M35 drift 0/0 vs guard -50/1 (APPLICATO, doc-only); #4 citation M33 (DEFER); E2 re-parse 500KB JSON a ogni loadSchedule idle (DEFER - impatto solo UI, non solve: un read a inizio solve); A2 new File(path) fragile per java -jar non-repo-CWD (DEFER - runbook lancia da repo root, path risolve); N1/N2 doc/style (DEFER). Tutti i defer: cosmetici o non solve-critical su codice testato, costo ri-verifica non giustificato a meta-campaigna; #3 e E2 sono cleanup follow-up post-m-12.

/code-review high: 1 skeptic correttezza focalizzato in corso (math, anti-arbitraggio, robustezza file-path, ordering static). Live A/B x2 = gate di adozione (maintainer).

POST-MORTEM LIVE m12-run1 (dataviz+cadence per-gruppo, 2026-07-27 ~13:50): M34 ha colpito chirurgicamente i 4 gruppi burst-heavy dell'anatomia: G12 70,6->52,9 (= PARITA' col golden 52,9), G03 37,5->25,0, G07 52,9->41,2, G02 47,6->33,3. Residuo burst: G08 55,6 (golden 36,8) e UNA REGRESSIONE da watch: G11 47,1->52,9 (golden 41,2) - n=1, verificare su run2bis prima di indagare. M35 ha mosso TUTTI i gap-weeks MODA (G05 8,45->7,25, G01 6,55->5,6, G09 10,71->8,29; golden 3,85/2,85/6,10) ma solo ~30% del percorso: knob 50->100 candidato per il prossimo giro di benchmark. Side-effect virtuoso di M35: G09 +34 giorni full (93%, gap vs golden = 3 giorni) e G13 +14.

ADOTTATA ai pesi 100/50 (ratifica maintainer 2026-07-27 sera, n=2; watch G11 chiuso: run2bis 47,1 = rumore n=1).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO. M34 (weekly burst cap, peso 100, knob planner.m34.weight) + M35 (weekly spread deficit, peso 50, knob planner.m35.weight) aggiunti, self-gating sul reference per-(gruppo,materia) da golden-cadence-reference.json (max per M34, active_weeks per M35). DoD: L0 299/0 (7 nuovi test), L1 guard M34 0/0/0 structural + M35 -50/1 residuo calibrato documentato (G04/Scienze W24 oltre-orizzonte, stessa classe S12 5623-vs-5625), NESSUN pin esistente cambiato (pure additions), full suite orchestratore 832/0 EXIT 0. /simplify 4-agent: nessun bug, #3 javadoc drift applicato, altri defer con motivazione. /code-review skeptic: 6/6 claim CONFIRMED (math, anti-arbitraggio, robustezza, ordering); cattura che il cast (int) M34 e' RICHIESTO (Timefold sum->Long, non int - falso positivo del /simplify). Live A/B x2 = gate di adozione (maintainer).
<!-- SECTION:FINAL_SUMMARY:END -->
