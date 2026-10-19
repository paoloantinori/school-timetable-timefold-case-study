---
id: TASK-477
title: >-
  G14 -> R10 (referente Q19): tracciare 2 rifiniture co-presenza + steering
  stanza
status: To Do
assignee: []
created_date: '2026-08-01 14:02'
updated_date: '2026-08-04 00:35'
labels:
  - claude-planned
  - golden-first
milestone: m-13
dependencies: []
references:
  - claudedocs/spalmone-2026-27/referente-autoverifica-domande-2026-08-01.md
  - claudedocs/spalmone-2026-27/task464-copresenza-ospite-design-2026-07-31.md
priority: low
type: enhancement
ordinal: 300000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
referente ha risposto alla Q19 (01/08 pomeriggio): "G14 andra' in R10" (stanza esistente nel seed tra le aule regolari). Corregge la nostra inferenza dai giorni-L ("ospite nel lab"): le ore co-schedulate di G14 si fanno in aula, non come ospite nel laboratorio. Sostanza occupancy invariata (G14 non conta come occupante del lab, gia' cosi' nel modello via de-tag in subject_overrides.csv); la semantica fisica ora e' decisa. Doc: claudedocs/spalmone-2026-27/referente-autoverifica-domande-2026-08-01.md (Q19 chiusa).

Due rifiniture candidate da TRACCIARE (NON implementare ora se non banali, decise da maintainer):

(a) ESCLUDERE le coppie G14 dall'esenzione H2 co-presenza (TASK-464). L'esenzione H2 pairwise (same single co-presenza tag su una cella (tag, weekday, slot) con golden depth >= 2) permette a un ospite di condividere la stanza di un titolare. Poiche' G14 non e' piu' un ospite nel lab (va in aula), l'esenzione non gli serve: le coppie G14 vanno escluse dal filtro dell'esenzione. Da verificare l'impatto: sul seed, G14 ha 50h L su 12 giovedi' e 30h di Servizio stilistico su giorni-L; quante coppie G14 sono oggi coperte dall'esenzione H2 e cosa cambia escludendole (occupancy invariata per costruzione, ma il filtro si semplifica).

(b) CANDIDATO steering stanza G14 -> R10. Le ore G14 dovrebbero preferibilmente andare in R10. E' una PREFERENZA (soft/medium), non un hard: valutare costo/beneficio (uno steering di stanza e' un vincolo di affinita' stanza-gruppo/materia, simile a S11/M-LF). Da misurare se il solver la onora senza degradare i KPI di guardia.

Entrambe NON banali e non urgenti (occupancy invariata, il modello e' gia' corretto sulla sostanza). Questo task e' solo TRACKING; l'implementazione e' gated maintainer + misura A/B. La riga Q19 in CLAUDE.md (sezione ratifica 463+464) va aggiornata con la correzione di referente (da "ospite nel lab" a "R10").
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Q19 referente risposta registrata (G14 -> R10) nel doc referente + riga CLAUDE.md corretta
- [x] #2 Refinement (a): misurato quante coppie G14 oggi coperte dall'esenzione H2; deciso (maintainer) se escluderle
- [x] #3 Refinement (b): valutato costo/beneficio dello steering stanza G14 -> R10 (A/B se non banale)
- [x] #4 Nessun cambiamento occupancy (G14 non conta come occupante lab, gia' vero): le rifiniture sono pulizia semantica, non correzione sostanza
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
--- Coda read-only 2026-08-04 (G14 golden fact-check + AC#2 scoping) ---

G14 nel golden (open rows, verificato): 115h, distribuzione Thu 73 / Fri 42, range 2027-01-28..2027-05-13 (29 giorni). Materie: Servizio stilistico 30, Confezione 30, Competenze Digitali 20, Comportamento Sociale 20, Orientamento 15. La cifra '30h Servizio stilistico' del task e' confermata. (Nota: G14 e' Thu/Fri, non Monday; il 'giorni-L' del task e' un'etichetta interna, non lunedi'.)

AC#2 scoping finding: 'contare le coppie G14 coperte dall'esenzione H2' NON e' misurabile dal solo lessons.csv (manca room/tag). Lezioni parallele allo stesso (date,slot) non sono co-presenza di stanza (sono classi parallele in aule diverse). L'esenzione H2 pairwise (TASK-464) keys off cellule (tag, weekday, slot) con golden co-presence depth >= 2, che richiede il tag di co-presenza + la depth map (loadGoldenCoPresenceDepth) -> misura runtime/DB, non script golden-CSV standalone. Quindi AC#2 e' bloccata sul meccanismo co-presenza (non banale, coerente col 'gated maintainer + misura A/B' del task).

Status: AC#1 gia' fatto (CLAUDE.md L452-457 ha la correzione Q19 G14->R10). AC#2 measurement bloccato sul meccanismo co-presenza (sopra). AC#3 steering e i refinements (a)/(b) restano gated maintainer. Nessuna adozione qui.

--- Valutazione read-only 2026-08-04 (VERDICT-477, raccomandazioni) ---

RACCOMANDAZIONE (a) H2 esenzione: NO ACTION. Il caso Q19 (Servizio stilistico, giorni-L) e' GIA' SODDISFATTO dal de-tag (subject_overrides.csv applicato via SubjectRegistry L76 -> G14 Servizio stilistico requiredRoomTags=NONE -> singleCoPresenceTag=null a runtime -> isCoPresenceGuestPair false -> esenzione NON si attiva). Misura depth-map (replica loadGoldenCoPresenceDepth L341 da lessons.csv): 91 celle depth>=2 golden-practiced totali; G14 Servizio stilistico 30h in cella depth-5 ma NON guest (de-tagged); G14 Confezione (moda) 30h in celle depth-2/4 guest candidate, ma Confezione e' il lab STESSO di G14 (richiede stanza moda), non il caso 'ospite in stanza altrui' di Q19 -> out of scope.

RACCOMANDAZIONE (b) steering G14->R10: DIFFERIRE (basso valore). S11 (L2194) e' room-STICKINESS (penalizza piu' stanze/giorno), NON room-SELECTION: da solo NON steera verso R10 specificamente. M3/M-LF non selezionano stanza. Meccanismo minimo = nuovo vincolo soft (preferred-room-per-group) -> GATED maintainer + A/B. Valore COSMETICO (referente Q19: occupancy invariata; G14 115h, R10 cap 21, nessun dilemma capacita'). Costo (nuovo vincolo+A/B) non giustificato senza ragione fisica di referente.

Doc-drift rilevato (notifica): DataGenerator L533 commenta 'subject_overrides.csv (empty on 2026/27)' - STALE, il file ha gli override G14/G12 (SubjectRegistry lo carica e applica correttamente, solo il commento e' stantio). Fix cosmetico gated maintainer (tocca DataGenerator protetto).

Esito AC: AC#1 fatto (CLAUDE.md L452-457). AC#2 valutato -> raccomandazione NO ACTION (de-tag gia' fatto il caso Q19). AC#3 valutato -> DIFFERIRE (S11 insufficiente, basso valore). AC#4 confermato (occupancy invariata, de-tag non la cambia). Nessuna adozione. Le rifiniture restano decisioni maintainer ((a) no-action, (b) defer).

Doc: claudocxs->claudedocs/spalmone-2026-27/task477-results/VERDICT-477-2026-08-04.md.
<!-- SECTION:NOTES:END -->
