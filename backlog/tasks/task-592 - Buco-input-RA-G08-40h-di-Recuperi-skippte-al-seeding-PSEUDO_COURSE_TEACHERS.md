---
id: TASK-592
title: >-
  Buco input RA G08: 40h di Recuperi skippte al seeding
  (PSEUDO_COURSE_TEACHERS)
status: Done
assignee: []
created_date: '2026-09-09 05:40'
updated_date: '2026-09-11 09:33'
labels:
  - claude-planned
  - input-divergence
  - golden-first
  - referente-question
milestone: m-14
dependencies: []
references:
  - >-
    claudedocs/spalmone-2026-27/task577-results/VERDICT-poolcapfill-retrazione-goldenmonthly-2026-09-09.md
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TROVATO 9/09 (meccanicamente, dal censimento TASK-577): l'unica riga incarichi con docente letterale 'RA' (Recuperi ed Approfondimenti, G08, 40h) e' SKIPPTA al seeding da PSEUDO_COURSE_TEACHERS (DataGenerator, decisione documentata: "no single teacher to attach to"). Conseguenza mai prezzata: il pool G08 e' 626h contro le 667 del golden (40h di skip + la nota 1h di arrotondamento RA 40vs41 gia' nota, che era la sottostima di questo stesso buco). Il DB non ha corsi RA per G08 (verificato su laptop); incarichi-vs-golden per materia e' perfetto sulle altre 18 materie. Le 10 celle dichiarate vuote di G08 a fine costruzione e parte del deficit M20/M33 derivano da questo buco di capacita'. La golden practice: quelle 41h le colloca con docente 'RA' (placeholder). DOMANDA per referente: chi tiene le 40h di Recuperi di G08 (per G04 e' T15 50h, per i MODA T02/T16/T17/T14)? Opzioni di modeling da valutare con la risposta: (a) lasciare (pool-cap gia' coerenizza il modello al pool 626), (b) pseudo-teacher RA come Stage (piazzabile senza H1/H5), (c) titolare nominato dall'input. NOTA: la riga nel datagenerator-protection rule sul "1h sheet rounding RA 40 vs 41" va aggiornata: il delta REALE input-vs-golden su questo corso e' 40h di skip + 1h di arrotondamento.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Domanda a referente formulata e inserita nel pacchetto (chi tiene le 40h RA di G08, esiste un titolare come T15 per G04)
- [ ] #2 Conseguenza misurata registrata: pool 626 vs golden 667, 10 celle dichiarate vuote a costruzione in parte da questo buco
- [ ] #3 Decisione presa sul modeling (lasciare / pseudo-teacher RA / titolare nominato) con evidenza, NON silenziosamente
- [ ] #4 The approved disposition accounts explicitly for all declared RA hours and reconciles any difference from golden practice.
- [ ] #5 No invented teacher or conflict-exempt placeholder is introduced solely to eliminate a deficit.
- [ ] #6 Any authorized modeling/input change passes seed, availability, inventory, and provider regression checks; affected baseline fingerprints are renewed.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SVOLTO golden-first l'11/09 (maintainer: "approvo tutto"): il golden risponde da solo alla domanda. Le 41h "RA" di G08 sono distribuite su 9 docenti (T23 19, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2, T03 1, T11 1; perlopiu' martedi', 25/41h). CURA APPLICATA: la riga `RA,Recuperi ed Approfondimenti,G08,40.0` e' sostituita da 9 righe per-teacher (41h, conto golden; il 40-vs-41 e' l'arrotondamento gia' notificato) in ENTRAMBE le copie di incarichi.csv (byte-identiche, CRLF preservati). La script di verifica deriva-golden-incarichi-corrections.py ora applica la rename RA->Recuperi (come GoldenScheduleImportService) e dà 0 divergenze su tutte le triple reali. PIN ripinnato col motivo: GoldenSolutionRegressionTest raCoursesCreated 1->0 (l'import trova i corsi reali distribuiti, nessun corso contabile). Notifica a referente: claudedocs/spalmone-2026-27/referente-notifica-cure-golden-first-2026-09-11.md. Effetto atteso: pool G08 626->667 (= golden), i giorni di G08 smettono di essere magri (sanity-rosso G08 del control-10sep). Chiusura dopo suite verde.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso con commit 128a5e29 (cura) + 11eb708c (review follow-up 1: gate workbook ribasato su 5 righe incluse 2 pre-esistenti della cura 27/08 mai ribasate) + 654c52e3 (review round 2: narrativa meccanismo corretta, vedi sotto). La risposta era nel golden: 41h "RA" di G08 distribuite su 9 docenti (T23 19, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2, T03 1, T11 1, perlopiu' martedi'). Riga RA sostituita dalle 9 righe per-teacher in entrambe le copie incarichi.csv (byte-identiche, CRLF); pool G08 626->667 (= golden). MECCANISMO (corretto dalla review round 2): le 41 righe golden passano SEMPRE dal fallback RA (normalize("RA") non ha alias); post-cura il find-or-create del fallback riusa i 9 corsi reali (raRowsImported=41, imported 10542). derive-golden-incarichi-corrections.py con rename RA: 0 divergenze. Pin ripinnati col motivo (28 pseudo/275 reali/667; raCoursesCreated 0; partial-coverage ripurposto su Esame; messaggio 31->41). Suite 1303/0 EXIT 0 + gate workbook EXIT 0. Notifica referente: referente-notifica-cure-golden-first-2026-09-11.md. KNOWN-RESIDUAL: D7 refresh riscrive totalHours dei corsi REALI al re-import (idempotente oggi; da guardia conscia se gli incarichi divergono dal golden); pseudo-teacher RA materializzato a zero corsi (inerto); javadoc DataGenerator 266 stantio (differita, file protetto). AC#5 onorata: nessun docente inventato.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Obtain explicit authorization for protected seed changes and any external communication.
- [ ] #2 Run /simplify and high-effort code review for any code changes; record an evidence-only exemption otherwise.
- [ ] #3 Run applicable main-session input-contract tests with exit codes and verify the recorded decision.
<!-- DOD:END -->
