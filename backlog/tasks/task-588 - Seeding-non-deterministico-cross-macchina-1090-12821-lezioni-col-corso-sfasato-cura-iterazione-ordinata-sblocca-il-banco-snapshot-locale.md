---
id: TASK-588
title: >-
  Seeding non deterministico cross-macchina (1090/12821 lezioni col corso
  sfasato): cura = iterazione ordinata, sblocca il banco-snapshot locale
status: Done
assignee: []
created_date: '2026-09-06 11:53'
updated_date: '2026-09-06 11:54'
labels:
  - claude-planned
  - determinism
  - bridge-enabler
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
EVIDENZA (6/09, durante la costruzione del ponte banco-snapshot): due profile seminati con lo STESSO codice (laptop v123-6sep fresco di oggi, Fedora v123-bridge fresco di oggi) divergono nel mapping lezione->corso: 1090 lezioni su 12821 (blocco contiguo da id 11084, corsi adiacenti scambiati: es. lezione 12000 -> corso 273 sul laptop, 272 in locale). Le tabelle Course sono IDENTICHE su entrambi i lati (275 righe, diff vuota su id/subject/teacher/group), i range id coincidono (lezioni 1..12821, timeslot 1..1368): e' l'ORDINE DI GENERAZIONE delle lezioni che non e' stabile (ipotesi principe: un Course.listAll()/iterazione senza ORDER BY, o una collection non ordinata nel percorso DataGenerator->LessonGenerator). CONSEGUENZE: (1) il restore dei checkpoint per id e' INAFFIDABILE cross-macchina (il resume da snapshot funziona solo sul profilo che l'ha generato: il ponte banco-snapshot locale e' bloccato da questo); (2) ogni claim di riproducibilita' del seeding fra macchine e' indebolito (gli A/B cross-macchina finora hanno confrontato SCORE, non griglie: per quelli il danno e' nullo, ma il warm-start cross-macchina e le riconciliazioni per id richiedono la parita'). CURA DIREZIONE: ordinamento deterministico esplicito nell'iterazione che genera le lezioni (individuare il loop col blocco-11084 come marcatore; se il fix cade in bootstrap/DataGenerator.java serve il permesso esplicito di maintainer: file protetto; se cade in LessonGenerator/service non serve). VERIFICA MECCANICA del fix: semina fresca su DUE macchine + diff del mapping lezione->corso = vuoto. Verifica eseguita oggi: diff NON vuoto (1090 righe) = il bug e' vivo.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RETRAZIONE 6/09 (Fedora, stessa ora): il presunto nondeterminismo NON esiste. Il profilo locale era stato seminato con un jar STANTIO (target/quarkus-app del 21/08) mentre i seed CSV sono cambiati il 1/09 (ed6ed3f6): la divergenza dei 1090 era la mia mancata verifica del jar PRIMA di seminare (la lezione jar-stantio/banner-check applicata al rovescio: la memoria la imponeva anche per il seeding). Prova in corso: rebuild locale + profilo fresco + diff mapping vuoto atteso. Il task si chiude come RETRACTED con la nota di processo (verificare la freschezza del jar anche quando si SEMINA, non solo quando si misura). Se il diff post-rebuild restasse NON vuoto, il task riapre con la tesi originale.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
