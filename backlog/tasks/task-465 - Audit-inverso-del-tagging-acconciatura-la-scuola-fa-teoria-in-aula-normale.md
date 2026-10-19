---
id: TASK-465
title: 'Audit inverso del tagging acconciatura: la scuola fa teoria in aula normale?'
status: Done
assignee: []
created_date: '2026-07-31 10:53'
updated_date: '2026-07-31 13:04'
labels:
  - claude-planned
  - analysis
  - golden-first
milestone: m-12
dependencies: []
priority: medium
ordinal: 288000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Direttiva maintainer 2026-07-31 pomeriggio: agire sulle piste di re-interpretazione dei ~92 room-null lab (tag acconciatura 85%, termoidraulica 14%). Scoperta chiave: le 44 violazioni tag-capacity del GOLDEN sono G14 + G12 su acconciatura, e le inferenze Q19/Q20 (domande-brevi.md) mostrano che nel golden quei gruppi stanno nel laboratorio DA OSPITI in contemporanea con una classe titolare (G14 30h tutte di giovedi', G12 12h giovedi'). Il nostro H2 (room conflict hard) vieta la co-presenza, quindi il motore lascia l'ospite room-null dove la scuola lo mette dentro.

Obiettivo: verificare sul golden se le materie acconciatura che trattiamo come lab-obbligatorie (requiredRoomTags -> H-M2) compaiono in slot dove il laboratorio e' certamente occupato da un'altra classe titolare, cioe' se la scuola le fa anche in aula normale (quota teoria). Se si', valutare il rilassamento del requisito di tag per la quota teoria; la forma e' da decidere in fase di analisi (ore non-lab del corso gia' modellate? split del corso?). Approccio read-only prima (misura sul golden), poi eventuale prototipo dietro flag default-OFF; nessuna adozione senza maintainer (golden-first, CLAUDE.md sezione 'Golden-first recalibration').

GATED dall'esito dello spike read-only gia' in coda su zai (anatomia room-null vs celle di co-presenza golden). Correlato ma NON duplicato di TASK-449/455 (scarsita' tag): questo mette in dubbio il tagging stesso, non la gestione della scarsita'. Riferimenti: claudedocs/spalmone-2026-27/task455-tag-capacity-30e-2026-07-30.md (anatomia 169: Lavaggio/T18 68 gio, Servizio stilistico/T26 60 mer/gio, Barberia/T19 16 mar, termoidraulica 24 mar).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Misura sul golden con numeri per (materia, gruppo): quante ore di materie tag-acconciatura cadono in slot dove il lab e' certamente occupato da un'altra classe titolare
- [x] #2 Verdetto in 3 righe: tenere il tagging com'e' oppure rilassare per la quota teoria
- [ ] #3 Se il verdetto e' rilassare: proposta di forma documentata SENZA implementazione (la decisione resta a maintainer)
- [x] #4 Nota esplicita che Disegno meccanico e Disegno edile ed impiantistico sono gia' scagionati (excess 1 e 0)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-31 13:04
---
VERDETTO (3 righe, dai raw, zai 2026-07-31): (1) KEEP tagging lab-obbligatorio delle 4 materie acconciatura (Barberia, Lavaggio, Servizio stilistico, Servizio tecnico e stilistico) com'e'. L'audit inverso dello spike (VERDICT.md) non trova NESSUNA materia esiliata fuori dalle celle di co-presenza golden: il tagging e' CORRETTO. (2) Il difetto dei ~92 room-null e' il CAP H2 (1 lezione/slot per stanza), non il tagging: il golden co-loca 2-5 gruppi (95% delle ore acc 683/719 in co-presenza 2+; 100% dei 92 room-null su celle co-presenza), il solver capped a 1 non puo' ospitare. Nessun de-tagging. (3) Disegno meccanico e Disegno edile/impiantistico gia' scagionati: excess ~0 nel modello tag-capacity (task455 Tavola B: l'excess e' concentrato in acconciatura 144 + termoidraulica 24, le materie teoriche non saturano).
---

created: 2026-07-31 13:04
---
AC#3 (proposta forma di rilassamento) è N/A: il verdetto e' KEEP, non rilassare. La domanda fisica '5 classi in 1 lab vs alcune in aula normale?' (caveat dello spike) NON cambia il verdetto sul tagging perche' l'audit-inverse ha gia' verificato che tutte le ore acc cadono in celle di co-presenza (se la scuola facesse teoria in aula normale, quelle ore apparirebbero fuori co-presenza, e non ci sono). Quella domanda fisica e' di modeling della co-presenza (TASK-464) e va nel doc di notifica referente (TASK-466c), senza bloccare il tagging.
---

created: 2026-07-31 13:04
---
Riferimenti (no nuovo lavoro): spike-room-null-copresenza-2026-07-31/VERDICT.md (audit-inverse, anatomia room-null per (gruppo,materia), depth Thu verificata dal CSV); task455-tag-capacity-30e-2026-07-30.md (Tavola B excess per tag-weekday, Disegno scagionati). DoD: read-only analysis, zero code, nessun diff da /simplify o /code-review.
---
<!-- COMMENTS:END -->
