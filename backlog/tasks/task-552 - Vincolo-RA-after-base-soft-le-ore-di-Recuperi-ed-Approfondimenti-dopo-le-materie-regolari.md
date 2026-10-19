---
id: TASK-552
title: >-
  Vincolo RA-after-base (soft): le ore di Recuperi ed Approfondimenti dopo le
  materie regolari
status: To Do
assignee: []
created_date: '2026-08-25 15:04'
labels:
  - claude-planned
dependencies: []
priority: medium
type: enhancement
ordinal: 312000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'ispezione visiva maintainer 25/08 sul ciclo finisher-ON, verificata col golden da Fedora: il golden colloca le ore di Recuperi ed Approfondimenti in modo BACK-LOADED (set-dic 33h, gen-giu 156h su 189 totali), il nostro solver le spalma uniformi (1-2h/settimana tutto l'anno). Conseguenza visibile: ideal end anticipato (le ore finiscono prima).

CANDIDATO: vincolo soft che spinga le ore RA DOPO la copertura delle materie regolari (forma da definire: reward per RA post-base-materia, o penalità su RA precoce, per (gruppo); NON hard - il golden stesso ha 33h in set-dic, quindi non e' zero-tolerance). Gates obbligatori prima di qualunque adozione: (1) risposta referente alla domanda rev 15 in domande-brevi.md ("le ore di recupero vengono esplicate dopo le regolari?"); (2) A/B appaiato con KPI di guardia (full-day, ricorrenza, buchi, medium) - forma standard opt-in default OFF; (3) golden-check: il vincolo non deve penalizzare il golden oltre il residuo praticato (33h early nel golden = soglia naturale); (4) placement-parity-check se introduce un piano. Riferimenti: registro iterations-542.md voce "ISPEZIONE VISIVA maintainer 25/08"; verifica golden di Fedora; confronto-ciclo-finisher-on.html vista Materie x settimana (Recuperi G concentrato in coda, N sparso).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
