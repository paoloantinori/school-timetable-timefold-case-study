---
id: TASK-455
title: >-
  Vincolo hard capacita' per-tag lab: veto quasi caduto (44 viol, 30 G14+14
  G12), gate risposte scuola
status: Done
assignee: []
created_date: '2026-07-29 13:51'
updated_date: '2026-08-03 11:49'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 278000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Annotazione numeri (non implementare). Vincolo HARD di capacita' per-tag (due classi mai nello stesso lab, confermato da referente 29/07: 'una classe in laboratorio, le altre no, i laboratori sono uno per specialita''). Veto di Fedora era per 242 violazioni golden; con le riclassificazioni confermate (Taglio Metalli->aula, Progettazione Termoidraulica/Tecnologia tessile->aula) + capacita' vera (informatica ha DUE lab, R17 ora cap 22) le violazioni golden scendono a 44, tutte su ACCONCIATURA: 30 con G14 + 14 con G12. Il veto e' QUASI caduto. NON implementare l'hard finche' non arrivano le risposte su G14 e G12 (girate alla scuola 29/07, tardano): dichiarerebbe infeasible l'orario che la scuola considera valido. Quando arrivano le risposte: se G14 non occupa il lab (60h) e G12 lavora altrove per Servizio stilistico (76h), le 44 violazioni potrebbero azzerarsi e l'hard diventa fattibile. Pre-misura (coda HANDOFF-2026-07-29.md punto 3) dice esattamente quante cadono. Numeri in claudedocs/spalmone-2026-27/domande-brevi.md (discrepanza) e HANDOFF-2026-07-29.md.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-07-30 zai] MISURA PRELIMINARE 30e (mandato maintainer): deficit CONCENTRATO, il vincolo ha bersaglio netto. Due tag mono-stanza (cap 1) = 168/169 = 99%: acconciatura 144 (Lavaggio/T18 68, Servizio stilistico/T26 60, Barberia/T19 16) su mar/mer/gio, + termoidraulica 24 (T03, tutto mar) + meccanica 1. informatica(cap2)/saldatura: excess 0. Causalita': 0/169 H29 in cella con capienza residua (100% contesa). Riconciliazione: excess totale 202 = 169 unpinned(H29) + 3G09 pinned(esente). Script: scripts/analyze/tag-capacity-excess-kpi.py. Doc: claudedocs/spalmone-2026-27/task455-tag-capacity-30e-2026-07-30.md. Tappa 2: prototipo MEDIUM-ALTO (non hard: dichiarerebbe infeasible il golden, 44 viol scuola) dietro flag DEFAULT OFF, pinned/pseudo-exempt, mira alle 169.

[Nota tier] HARD onesto ma (a) infeasible sul golden (scuola accetta 44 sue viol), (b) 202 hardViol sul run da spreading che rischia M20/M15 come la bilanciatura primo-slot. MEDIUM-ALTO (~famiglia M20/M22) tollera le 44 del golden e spinge il run a spargere. Cap. annuale c'e' (719<1368) quindi spreading fattibile: il trade-off si MISURA al checkpoint, non si assume.

[2026-07-30 zai] PROTOTIPO M-TC costruito dietro flag DEFAULT OFF (planner.tag-capacity.enabled, opt-in, letto per-build come M-LF). Vincolo medium-high (peso planner.tag-capacity.weight default 1000): penalizza, per cella oraria (tag, startDateTime), le lezioni del tag oltre roomCapacityForTag (pubblicata pre-solve dalle stanze assignable). Conteggio H7-style (pinned contano nella domanda). Registrato in getFullSolvingConstraints, tabella CLAUDE.md aggiornata. Test LabTagCapacityConstraintTest 4/4 (OFF inerte, ON penalizza excess a peso 1000, within-cap 0, pinned conta). Code-review (skeptic Opus): CONFIRMED nessun bug. Doc: claudedocs/spalmone-2026-27/task455-tag-capacity-30e-2026-07-30.md. DoD: /simplify (diff minimale, idiomatico, niente da semplificare), code-review CONFIRMED, test.sh full in corso.

[Mandato maintainer] (1) Chiave risorsa scarsa = COPPIA (tag, weekday) MISURATA non la terna: aggiungere il docente frammenta (copertura 88%->64%). Il docente e' la CAUSA non la chiave. Il vincolo opera a cella oraria (unita' fisica), compatibile con la coppia di governo. (2) Mercoledi' acconciatura 52h = INDEPENDENTE non rimbalzo: (gruppo,materia) condivisi mer-gio = 0, il golden concentra acconciatura su mercoledi' lui stesso (166h: G10 100, G06 66). Curare il giovedi' non sistema il mercoledi', ma il M-TC uniforme li copre entrambi: nessuna calibrazione separata. (3) VRP dedicato non sostituisce il vincolo (la capienza del tag e' aggregato dinamico tra lezioni co-poste, non fatto statico per-entita); il VRP calza solo sul consumatore-costruzione (TASK-435/449, via separata).

[Sharp edge operativa] Il flag e' catturato al BUILD (non per-evaluation, come M-LF) e soggetto al gotcha MAVEN_OPTS: -Dplanner.tag-capacity.enabled raggiunge l'app JVM SOLO sulla riga java -jar, NON via mvn quarkus:run (dev-start-and-wait). Il banner LOG 'M-TC lab tag-capacity: enabled=%b' lo rende osservabile. Il checkpoint/certificazione con M-TC ON va lanciato via java -jar (come gia' richiesto da maintainer per la certificazione).

[2026-07-30 zai] PASSO B/C (tagging) misurati al CHECKPOINT con RoomPhaseSnapshotTest export-reload (M-TC off, un fattore alla volta), NON a solve completo. v0/v1/v2 sul export 30e, room-null dopo fase-5 LS: v0 lab169/nonlab0/hard-177; v1 (G14+G12 de-tag) lab125/nonlab1/hard-134 (44 lab chiuse, ~0 ordinario peggiorato, VITTORIA PULITA); v2 (tutti 5 gruppi) lab75/nonlab89/hard-172 (94 lab chiuse ma 89 ore ordinarie allagano le aule = PAREGGIO). UNTAG lascia isLab=true e gonfia il conteggio: v2 onesta misurata via UNTAG_GROUP su 5 gruppi. PASSO C RACCOMANDAZIONE: v1 (evidence-based, clean). v2 allaga aule (capacita' aula strutturale). Caveat: checkpoint fissa placement, v2 potrebbe migliorare col spreading in solve completo, ma v1 gia' pulita al checkpoint = scelta sicura. Doc: task455-tagging-evidence-2026-07-30.md.

[METODO] Scorso l'harness di checkpoint (RoomPhaseSnapshotTest, leva UNTAG/UNTAG_GROUP) che misurava le varianti in minuti: perso tempo su 2 setup di solve completo falliti prima di tornarci. claudedocs/localized-testing-guidelines.md va consultato PRIMA di lanciare solve lunghi.

[2026-08-03 zai] PRIMA MISURA su solve vero con caso-cattivo noto (seed-6, source=input, stesso seme dell'overnight badseed-frequency). A/B controllato: OFF (-1hard H29, la lezione meccanica G04 T15) vs ON (M-TC enabled). ESITO: 0-hard, la collisione meccanica e' RELIEF-ATA (M-TC ha spinto la LS a spostare la lezione fuori dalla cella: overflow meccanica 0 vs 1 dell'OFF). MA costo medium -204.714 di cui -120.000 e' penalita' M-TC stessa su 120 match. Scomposizione per tag dei 120 match (replicando il groupBy sull'export): acconciatura 107 + moda 13 + meccanica 0 + altri 0. QUINDI il 100% del costo e' co-presenza PRATICATA dal golden (acconciatura/moda) che M-TC non esenta (a differenza di H2/H-COPRESENCE depth>=2). Diagnosi: NON e' un problema di peso (trappola M-TR: overflow praticati non scarsi), la leva giusta e' l'ESENZIONE co-presenza praticata (mirror H-COPRESENCE). Con esenzione, su seed-6 M-TC sparerebbe 0 match -> cura pulita (0-hard, medium ~come OFF), mantenendo il relief meccanica se la coppia G04+G13 non e' praticata (KEY FOLLOW-UP da verificare sul golden depth map). Verdetto: task455-results/VERDICT-ab-mtc-seed6-2026-08-03.md. Script: scripts/benchmark/task455-mtc-seed6.sh. NESSUNA adozione: M-TC resta default-OFF.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
