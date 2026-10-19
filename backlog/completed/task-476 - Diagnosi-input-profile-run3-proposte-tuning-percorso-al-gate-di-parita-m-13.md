---
id: TASK-476
title: >-
  Diagnosi input-profile run3 + proposte tuning (percorso al gate di parita'
  m-13)
status: Done
assignee: []
created_date: '2026-08-01 09:29'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - golden-first
  - source-input
milestone: m-13
dependencies: []
references:
  - >-
    claudedocs/spalmone-2026-27/task476-input-profile-diagnosis-run3-2026-08-01.md
  - >-
    claudedocs/spalmone-2026-27/task452-results/verify-source-input-run3/VERDICT-2026-08-01.md
  - claudedocs/spalmone-2026-27/procedura-portfolio-path-input-2026-08-01.md
  - >-
    claudedocs/spalmone-2026-27/task452-results/verify-source-input-run6/VERDICT-2026-08-01.md
priority: medium
ordinal: 299000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Diagnosi read-only del perché run3 source=input lascia 3 unassigned e -5pp full-day (G04 54%, G10 30%), con proposte di tuning SENZA leggere il golden. E' il percorso verso il gate di parita' m-13 (TASK-452: flip quando hard 0 + full-day nel range golden).

Doc: claudedocs/spalmone-2026-27/task476-input-profile-diagnosis-run3-2026-08-01.md.

FATTI confermati (input-only): (1) monte-ore incarichi NON manca (G04 e G10 entrambi 1980h, il deficit e' solver-side non di domanda); (2) calendario G04 ATTENDE 5h i venerdi' (35/43 ven exp5+) ma il solver ne colloca 3 -> deficit e' collocabilita', non piano.

IPOTESI classificate (nessuna confermata da run mirato): H1 (G04 ven deep -2h = scarsita' docente/lab venerdi', da verificare disponibilita'), H2 (G10 -1h spread = costo concentrazione T01 giovedi, trade-off accettato), H3 (3 unassigned = residuo strutturale TASK-435, non fill).

PROPOSTE di tuning (no golden, ognuna testabile): T1 (alta priorita') controllare/rilassare disponibilita' venerdi' docenti top G04 -> run A/B, obiettivo colmare i 19 ven deep; T2 confermare -1h G10 traccia T01 (trade-off); T3 i 3 unassigned sono profilo constrained-teacher (compone con patch #1 TASK-452).

T1 e' il lever piu' promettente: input-side, niente codice, niente golden. Se colma i 19 ven deep il full-day si avvicina al range golden e il gate m-13 si riavvicina. Da testare con run mirato (stessa config run3, sola differenza = disponibilita' ven G04).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 T1 testato: run A/B run3-base vs run3 + disponibilita' venerdi' G04; i 19 ven deep colmati (o diagnosticato perche' no)
- [x] #2 Verificato se il deficit e' interamente solver-side (monte-ore e piano attesi corretti) o se c'e' un componente input nascosto
- [ ] #3 Nessun uso del golden per la diagnosi o il tuning (ripetibile nel regime post-golden)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
OBSOLETO/ASSORBITO: il gate m-13 e' APPROVATO (maintainer 2026-08-11, chiusura procedurale via multi-start, p(0-hard) procedurale = 32/32 verificato indipendentemente da Fedora 2026-08-13 sui raw). Il percorso al gate e' quindi chiuso. Nota: il censimento single-construction della notte 13/08 (p=0.667 GRASP) non riapre il gate (misura la coda GRASP, coperta dal multi-start ratificato). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
