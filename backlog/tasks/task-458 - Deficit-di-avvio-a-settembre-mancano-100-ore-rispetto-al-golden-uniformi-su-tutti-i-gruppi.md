---
id: TASK-458
title: >-
  Deficit di avvio - a settembre mancano 100 ore rispetto al golden, uniformi su
  tutti i gruppi
status: Done
assignee: []
created_date: '2026-07-29 18:31'
updated_date: '2026-07-29 18:49'
labels: []
dependencies: []
ordinal: 281000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TROVATO di striscio nella misura dei tier (Fedora 2026-07-29). Settembre 2026: golden 992 ore ordinarie, run certificato 892, cioe' -100. Il deficit e' distribuito su TUTTI i 13 gruppi principali (-7/-10 ciascuno, G14 +5) e la data di prima lezione coincide (14/09 per tutti), quindi NON e' il calendario di un gruppo ne' un problema di start date (H14). Sospetti da verificare: rampa di avvio della costruzione, quote attese dei primi giorni, o interazione con i pin autoritativi. Misura riproducibile: teacher-tier-kpi.py stampa le ore per mese nelle sue tabelle, oppure conteggio diretto sul dump.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Identificata la causa delle 100 ore mancanti a settembre con catena verificata, non per correlazione
- [ ] #2 Stabilito se il deficit e' recuperato altrove nell'anno (traslazione) o perso (ore non piazzate)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO come artefatto di misura (Fedora 2026-07-29, doc claudedocs/spalmone-2026-27/task458-settembre-2026-07-29.md). Le 100 ore sono il SABATO 19/09/2026, 112 ore su tutti e 14 i gruppi, che il golden ha e la nostra griglia lunedi-venerdi non puo' contenere; il foglio autoritativo non ha nemmeno la riga per quella data. Escludendo i sabati: settembre golden 880 contro run 892, siamo AVANTI di 12; sull'anno lo scarto vero e' -44 ore su 10.542, lo 0,4%. Il golden ha in tutto 117 ore di sabato: ogni confronto sul monte-ore che non le esclude porta quel bias. Stessa famiglia dell'artefatto stage. Corretto anche un mio errore di calcolo intermedio: G04, G08 e G13 stanno in entrambi i fogli calendario e le loro ore attese venivano sommate due volte.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
