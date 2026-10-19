---
id: TASK-456
title: >-
  Lab-fallback: lezioni non-lab possono usare i laboratori liberi (dominio
  valori + penalita' medium, dietro flag) - MISURA PRIMA
status: Done
assignee: []
created_date: '2026-07-29 14:23'
updated_date: '2026-07-29 16:20'
labels:
  - claude-planned
dependencies: []
priority: high
ordinal: 279000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RISPOSTA SCUOLA (maintainer 29/07 sera): i laboratori POSSONO ospitare anche lezioni normali, dando priorita' alle attivita' di lab vere. Questo smentisce una regola cablata nel DOMINIO DEI VALORI, non in un vincolo: Lesson.getAvailableRooms (~riga 368-374) per una lezione non-lab filtra `r.tags == null || r.tags.isEmpty()` (solo stanze SENZA tag), quindi i laboratori sono strutturalmente preclusi anche quando liberi. E' piu' rigida della pratica (caso vietato dalla direttiva golden-first). Spiega anche il rompicapo R18: 10 aule ordinarie vs 66 slot con 11-13 classi fuori lab -> servono i lab come ripiego.

DISEGNO (misura prima, implementa dietro flag default-OFF):
1. DOMINIO VALORI: dietro flag, una non-lab vede ANCHE le stanze con tag (non solo quelle senza). Le lab restano come sono (H-M2 hard le tiene nel tag giusto). Punto: Lesson.getAvailableRooms ramo else (riga ~368).
2. PRIORITA' AI LAB VERI: nuova penalita' tier MEDIUM per non-lab collocata in stanza con tag (peso da calibrare, knob), cosi' il solver usa il lab come RIPARO non per comodita'. Senza questa, le ordinarie occupano l'officina e i lab veri restano senza stanza.
3. MISURA IN-HARNESS PRIMA DI ADOTTARE (RoomPhaseSnapshotTest export-mode sul cert, override flag+peso): (a) quante room-null si chiudono (baseline 185 post-correzioni 29/07), (b) quante non-lab finiscono in lab, (c) SE LE ORE LAB SENZA STANZA PEGGIORANO (se si, il peso e' sbagliato -> alzarlo). Report di almeno 2 valori a maintainer.
4. SOLO A MISURA VERDE: rimisurare se R18 puo' essere esclusa (referente) - prima era catastrofico (189->370).

REGOLE: dietro flag default-OFF (es planner.room.lab-fallback.enabled + .weight), checkpoint-first, NESSUN run live, NESSUNA adozione, DoD piena. Valore del peso e decisione di adottare = maintainer. File protetti (DataGenerator/seed CSV) NON toccati (la regola e' in Lesson.java dominio valori + constraint provider, non in seed).

NOTA HARNESS: l'override va aggiunto a RoomPhaseSnapshotTest (export-mode, setta il flag static + peso prima del solve). La cache getAvailableRooms e' vuota al primo solve in export-mode -> niente invalidazione. Baseline room-null = 185 (post #1 de-tag Taglio + #2 R17 cap 22 del 29/07).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTATO dietro flag default-OFF, MISURA-PRIMA, nessuna adozione. File protetti (DataGenerator/seed) intoccati: la regola e' in Lesson + constraint provider.

DOMINIO VALORI (Tappa 1): `Lesson.getAvailableRooms` ramo non-lab, dietro `planner.room.lab-fallback.enabled` (letto LIVE a cache-build via `SchoolSchedule.isLabFallbackEnabled()`, NON static-final: l'harness fa System.setProperty tra i run). Default OFF = value range byte-identical. Lab branch invariato (H-M2 hard).

M-LF (Tappa 1): nuovo vincolo medium in SchoolScheduleConstraintProvider, registrato in getFullSolvingConstraints. `ofMedium(planner.room.lab-fallback.weight, default 500)`, knob letto INLINE per-build (pattern M37). Filtro `enabled && room!=null && room.isTagged() && !isLab()`. Gated dallo stesso flag: a OFF il `enabled &&` short-circuit = 0 match = byte-identical (golden regression non drifta). Sibling di S10 (soft 50, sempre attivo, co-fira a flag ON). Nuovo `Room.isTagged()` (@JsonIgnore) shared.

HARNESS (Tappa 2): RoomPhaseSnapshotTest export-mode. Override `PLANNER_TEST_ROOM_LAB_FALLBACK=1` + `_WEIGHT=N` (bridge env->System.setProperty, helper `bridgeIntEnv` condiviso con M37). Count `countNonLabInTaggedRoom` (mirrors M-LF 1:1). Hook `PLANNER_TEST_ROOM_EXCLUDE` (punto 4 POLIVALENTE).

MISURA (Tappa 3): doc `task456-results/lab-fallback-measure-2026-07-29.md`. Cert `task447-results/cert/export.json.gz`. RAW: chiude 7/7 non-lab rn a ogni peso (w=1/100/1000), LAB rn INVARIATO a 182 anche a w=1 (priorita' ai lab veri regge). Peso controlla solo gli extra non-lab-in-lab (10/8/7). POLIVALENTE-exclude: flag OFF lascia 75 rn bloccati (-263); flag ON chiude tutte le 211 displaced (85 nei lab), LAB rn 182 invariato, hard -188 -> lab-fallback rende vitabile l'esclusione POLIVALENTE.

DoD: /simplify FATTO (applicati F1 predicato fuso, F2 bridgeIntEnv, Room.isTagged; skip F3 tail-hook + clone knob pre-esistente). /code-review high FATTO (4 agent: line-by-line/removed-behavior/cross-file/conventions): applicati 7 fix (@JsonIgnore isTagged =Jackson non serializza "tagged" su export.json/REST; riga M-LF in tabella CLAUDE.md; guard labFallback a exportMode =no stale-cache pipe; clear system props in clean() =no leak cross-test; nota pinned su EXCLUDE hook; count senza timeslot =mirror M-LF 1:1; javadoc M-LF "SAME population" corretto isLab vs requiredRoomTags). test.sh finale exit code in verifica.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-29 15:42
---
DOMANDA per maintainer/Fedora (misura VERDE, adozione gated a te): lab-fallback FUNZIONA (chiude 7/7 non-lab rn, LAB rn mai rubato a nessun peso). (1) PESO M-LF: misurati w=1/100/1000, tutti chiudono i 7 senza rubare lab; w=1000 e' il piu' tirato (0 extra ordinarie nei lab). Quale peso adottare se/quando si accende? (2) ADOZIONE: e' measure-first, flag resta OFF in produzione. Adottare ora o tenerlo strumentale? (3) POLIVALENTE: con lab-fallback ON l'esclusione di R18 (richiesta della scuola) diventa fattibile (75 rn bloccati -> 0, 85 ordinarie nei lab liberi, hard -188). Vuoi che proceda con exclude-POLIVALENTE + lab-fallback come prossima mossa? Io committo+pusho implementazione+misura (Tappa 1+2+3), adozione = te.
---

created: 2026-07-29 15:44
---
DoD CHIUSA: test.sh finale EXIT 0 (873 test, 0 failures, 0 errors, 14 skipped = harness env-gated). /simplify + /code-review high + exit code tutti verdi. Committo+pusho Tappa 1+2+3 (implementazione + harness + misura). Adozione/peso/POLIVALENTE = maintainer (vedi commento #1).
---

created: 2026-07-29 16:20
---
TAPPA B FATTA (pre-misura risposte attese, lab-fallback ON w=1000): hook nuovo PLANNER_TEST_ROOM_TAG_UNTAG_GROUP de-taggia per (gruppo,materia). Residuo LAB room-null GENUINO: baseline 182 -> (a) G14 154 -> (b) G12 144 -> (c) entrambi 120. Monotonico, stabile a 90s/180s. RICONCILIATO con l'inferenza golden di Fedora (commit 0ad379ea): G14 mai esclusiva (100% in contemporanea) -> conferma il mio 0 capacity-freed (de-taggare G14 non libera capacita' per gli altri, Fedora la considera risolta). G12: il golden dice solo 12h contese su 121 (giovedi T26), quindi il mio de-tag di 76h e' LIMITE SUPERIORE (29 liberati); la risoluzione realistica (12h) libererebbe molto meno. In entrambi i casi il residuo strutturale Lavaggio (~75, acconciatura genuina) domina. Doc: task456-results/lab-fallback-premisura-risposte-2026-07-29.md. Follow-up opzionale: misura 12h-only (hook gruppo+materia+timeslot).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
