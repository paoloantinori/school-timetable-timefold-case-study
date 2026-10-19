---
id: TASK-669
title: >-
  BUG: sortGroupsCodaPrima ordina sulla PRIMA cella quota invece che sull'ULTIMA
  (TASK-590 intent inerte, 13/14 gruppi stessa chiave)
status: Done
assignee: []
created_date: '2026-09-25 05:32'
updated_date: '2026-09-26 18:18'
labels:
  - claude-planned
  - bug
  - solver
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding CONFIRMED dal code-review TASK-666 (25/09, verificato su codice + javadoc TASK-590 commit 28dbcea9): sortGroupsCodaPrima itera i timeslot e fa break alla PRIMA data con riga quota positiva, chiamandola 'latest': la semantica dichiarata (javadoc e intento TASK-590 'gruppi con celle di Giugno prima') e' l'ULTIMA cella dichiarata. Con la direzione sbagliata l'ordinamento most-constrained-first non discrimina: sul seed 2026/27 13 gruppi su 14 condividono la stessa chiave (la prima riga quota dell'anno, ~14/09), quindi la prioriteta' coda-prima e' di fatto inerte e l'ordine effettivo ricade quasi nell'ordine di input; la chiave varia inoltre con l'ordine non deterministico dello scan DB. Pre-esistente al TASK-666 (il diff di quel task aggiungeva solo il null guard nello stesso loop). CURA: iterare in senso inverso o tenere il max per data. ATTENZIONE: cambia l'ordine di costruzione = comportamento solver, serve banco A/B appaiato prima dell'adozione (regola casa), non un fix drive-by.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 La chiave di ordinamento e' la data dell'ULTIMA riga quota del gruppo (non la prima incontrata nello scan)
- [x] #2 Lo scan non dipende dall'ordine di ritorno non deterministico di Timeslot.listAll()
- [x] #3 Verifica su seed reale: le 14 chiavi non collassano su 2026-09-13/14 (oggi 13/14 gruppi condividono la stessa chiave)
- [x] #4 Banco A/B prima dell'adozione: la direzione corretta cambia l'ordine di costruzione, misurare celle/consegna prima di flippare
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-26 21:10 CURA LANDDED + BANCO COMPLETO (domanda maintainer: 'senza run full?' -> si', e fatto). FIX: scan max-date sulla griglia (restrizione a griglia load-bearing: righe oltre griglia non contano, semantica H32) + hoist per-group map (getExpectedHoursForGroup, review F5) + tie-break per id nel comparatore (F4: il blocco a 9 classi al 10/6 non ricade piu' sull'ordine HashMap). TEST: i 2 pin nuovi sono MUTATION-VERIFICATI (logica break-at-first ripristinata = 2 rossi; fix = 4/4; restore cmp byte-identical): i primi due pin che ho scritto erano vacui (date fuori griglia, trovato da /simplify con mutation test e da code-review F1/F2) e sono stati riscritti con date IN griglia e lista reversed. BANCO REAL-SEED A/B: OFF vs ON(tie-break) = dump construction-lessons.txt IDENTICI byte per byte (10438 righe, ricorrenza 75,3% entrambi, score costruzione identico). Il banco e' REALE (disambiguato F3: wireConstructionStatics pubblica le statiche quota da DB, SnapshotFixture:1084). CONCLUSIONE: la costruzione e' INSENSIBILE all'ordinamento dei gruppi su questo seed: la premessa di TASK-590 e' confutata, il suo miglioramento ratificato veniva da altro. Il flip e' igiene pura (direzione giusta + determinismo completo), adottabile sotto bounded-no-op con prova di no-op PIU' forte del solito (oracolo byte-identical). Nessun ciclo full necessario.
<!-- SECTION:NOTES:END -->
