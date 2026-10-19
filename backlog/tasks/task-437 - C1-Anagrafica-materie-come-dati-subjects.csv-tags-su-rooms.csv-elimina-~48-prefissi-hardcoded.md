---
id: TASK-437
title: >-
  C1 Anagrafica materie come dati: subjects.csv + tags su rooms.csv (elimina ~48
  prefissi hardcoded)
status: In Progress
assignee: []
created_date: '2026-07-28 08:23'
updated_date: '2026-07-29 14:36'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
ordinal: 260000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C1. subjects.csv (subject, canonicalName, isLab, professionalizzanti, roomTags) + colonna tags in rooms.csv; via getRoomTags/isProfessionalizzante/ROOM_TAG_MAP/SubjectNormalization/M21_EXEMPT_SUBJECTS/SICUREZZA_SUBJECT/set T20/vocabolario pseudo. Verifica meccanica: flag derivati byte-identici sui 266 corsi PRIMA di cancellare i matcher. Fix incluso: stop al ricalcolo che sovrascrive le correzioni admin a ogni seed. Sforzo M, rischio basso-medio (solo seeding).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Piano di esecuzione (checkpoint-first, split in tappe)

**SCOPE PROTECTED-FILE (DataGenerator):** modifica autorizzata sotto TASK-437 / audit C1
(direttiva maintainer 2026-07-28). Tocca SOLO la sostituzione matcher->registry (getRoomTags/
isProfessionalizzante/ROOM_TAG_MAP). Le risposte referente (#1 de-tag Taglio, #2 R17 cap,
FASE C de-tag Progettazione/Tecnologia tessile) restano PRESERVATE, migrate nei dati
(subjects.csv / rooms.csv). Non si allarga lo scope referente.

**Split C1 in due direzioni indipendenti** (subject->tag vs roomName->tag):
- **Tappa 1 (lato MATERIA, ~48 prefissi):** subjects.csv (52 materie normalizzate, derivate
  meccanicamente dagli attuali matcher) + SubjectRegistry bean + sostituisce getRoomTags/
  isProfessionalizzante/isLabSubject in DataGenerator + fix "stop overwrite admin corrections"
  (rimuove force-update professionalizzanti a ogni startup; il flag si setta in generateCourses
  solo al seed fresco). subject_overrides.csv vuoto = meccanismo (gruppo,materia) LIVE ma inerte
  sul 2026/27.
- **Tappa 2 (lato STANZA):** colonna tags su rooms.csv (sostituisce ROOM_TAG_MAP, 7 entry).
- **Tappa 3 (solver-side):** M21_EXEMPT/SICUREZZA/T20 -> registry (static-map publish pre-solve).
- **Tappa 4: (gruppo,materia) overlay** gia' disegnato in Tappa 1 (registry + subject_overrides.csv).

**Verifica byte-identica:** script derivazione Python (/tmp/derive_subjects.py) genera subjects.csv
dagli attuali matcher -> il CSV e' derivato, non trascritto. SubjectRegistryTest asserisce le 52
materie + i casi non-ovvi (Informatica lab ma non-prof; Cad entrambi; Disegno Edile m21+prof).
DataGeneratorSeedTest (266 corsi, 18 stanze) resta il guard di integrita'.

**Requisito (gruppo,materia):** il registry espone flagsFor(subject, group) che applica overlay
da subject_overrides.csv. G14/Servizio stilistico si esprime come riga di override (force-empty
roomTags via sentinella NONE), inerte finche' non arriva la risposta scuola.

DoD: /simplify + /code-review high + test.sh exit code per ogni tappa. Commit+push a ogni tappa.

TAPPA 1 implementata + verificata (29/07 sera). subjects.csv 3 colonne (subject,roomTags,professionalizzanti) generato meccanicamente dagli attuali matcher (script /tmp -> 52 materie, invariante di gruppo OK). SubjectRegistry bean (load() public, @PostConstruct; flagsFor(subject) fail-loud, flagsFor(subject,group) overlay; SubjectFlags 2 campi + isLab(); putOverrideForTest). DataGenerator: inietta registry, generateCourses usa flagsFor(subject,group.name), RIMOSSO force-update professionalizzanti a ogni startup (fix overwrite-admin), RIMOSSI getRoomTags/isLabSubject/isProfessionalizzante (~48 prefissi). subject_overrides.csv header-only (meccanismo (gruppo,materia) LIVE, inerte 2026/27).

/simplify (4 agent) applicato: CHM->HashMap (no concorrenza post-load), normalize-once via baseFlags(), merge init()/load(). SKIP: PSEUDO_TEACHERS dedup (richiede promote const in DataGenerator protetto, fuori scope) -> follow-up. ALTITUDE #2 risolto NON trasportando le colonne solver-side (m21Exempt/sicurezza/t20): subjects.csv e' consumato solo per roomTags/professionalizzanti; le colonne solver-side le aggiunge Tappa 3 quando cabla il constraint provider (no seconda sorgente non-guardata).

ALTITUDE #1 (drift REALE pre-esistente, ora FIXATO): GoldenSolutionRegressionTest aveva una copia VERBATIM STALE di getRoomTags che classificava Progettazione Termoidraulica/Taglio Metalli/Tecnologia tessile come LAB, mentre produzione le aveva de-taggate (FASE C/referente TASK-436/447). Unificato: la copia stale -> 3 delegatori a SubjectRegistry (stessa sorgente di produzione). Verificato: suite piena 873 verde (golden resta feasible: lab->non-lab allenta i vincoli su orario valido). MonoRoomTagAllocatorPhaseTest.roomTagsFor era CURRENT (no drift): unificazione differita a follow-up.

DoD: #1 /simplify FATTO. #3 test exit code FATTO (873 verdi, exit 0, due run). #2 /code-review high in corso (3 agent correttezza). Commit+push dopo review.

FOLLOW-UP: (a) unificare MonoRoomTagAllocatorPhaseTest.roomTagsFor alla registry; (b) PSEUDO_TEACHERS -> promote a public in DataGenerator e dedup le 3 copie di test; (c) TAPPA 2 rooms.csv colonna tags (sostituisce ROOM_TAG_MAP); (d) TAPPA 3 solver-side M21_EXEMPT/SICUREZZA/T20 -> registry (aggiunge colonne a subjects.csv + static-map publish pre-solve).
<!-- SECTION:NOTES:END -->
