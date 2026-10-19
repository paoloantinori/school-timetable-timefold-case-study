---
id: TASK-460
title: >-
  Progettare l'intervento sulla ricombinazione orizzontale - brainstorming
  sospeso in attesa della prova per fase
status: Done
assignee: []
created_date: '2026-07-29 18:33'
updated_date: '2026-08-08 11:35'
labels: []
dependencies: []
ordinal: 283000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Deriva dal /brainstorming aperto da maintainer il 2026-07-29 sul problema 'uno slot resta vuoto perche' tutti i docenti utili sono gia' impegnati altrove', che lui ha scoperto essere il MECCANISMO GENERALE e non il caso singolo. Il metodo prescritto da maintainer e' il ragionamento a posteriori: guardare l'assegnamento del golden, il nostro parziale, e immaginare il set MINIMO di mosse che porti dall'uno all'altro.\n\nSTATO: il brainstorming e' fermo alla fase 'proporre 2-3 approcci' perche' le due ipotesi candidate sono state REFUTATE dalla misura, e progettare su una causa sbagliata sarebbe spreco. (a) Il prezzo non blocca lo swap: lo swap e' hard-illegale, -2000 H2 stanza e -1000 H1 docente, e M32 pesa solo -50 mentre M18 addirittura guadagna. (b) La penuria di aule non causa il ritardo: la fase orari gira prima dell'assegnazione stanze (TASK-446), quindi le aule non esistono ancora quando la giornata scivola. (c) Non e' scarsita' di docenti: nel golden ci sono in media 6,6 docenti del gruppo liberi per ogni ora insegnata e solo lo 0,1% di ore senza alternative, mentre nel nostro run a quei venerdi alle 8 i candidati conflict-free sono ZERO su 417. La saturazione e' PRODOTTA dal nostro piazzamento, non subita.\n\nCosa manca per progettare: il dump per fase (TASK-453, gia' deciso per la prossima certificazione) che dira' in quale momento esatto la giornata scivola. Il dossier delle evidenze e' TASK-459; la leva candidata di ordinamento in costruzione e' TASK-457.\n\nPista di maintainer tenuta viva e riformulata: i tier docenti non servono a bilanciare la concorrenza (non morde) ma a difendere un RITMO di consumo, vedi TASK-457.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Dump per fase letto e causa del difetto stabilita con catena verificata link per link
- [ ] #2 Proposti 2-3 approcci con compromessi e raccomandazione, sul meccanismo generale e non sul caso G11
- [ ] #3 Disegno approvato da maintainer e spec scritta in docs/superpowers/specs/
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
--- Coda 2026-08-04 (post TASK-472 AC#2): stale-blocker correction + approach seeds ---

Sblocco fattuale: TASK-453 e' DONE (2026-08-03), non piu' 'in attesa'. Il dump per-fase ESISTE e il verdetto (task453-results/VERDICT-erosione-2026-08-03.md) stabilisce: la fase LS-Compactness da sola sposta 1449/1744 = 83% delle celle sempre-mobili (Compaction 51, room phases 0); l'erosione e' dominata dalle ore teoriche generaliste MAI-PINNATE (Lingua/Matematica/Scienze/Recuperi); il template (74% resemblance) e' preservato dai PIN (promoted+authoritative), non dalle lezioni libere. Questa e' la 'prova per fase' che AC#1 aspettava: la giornata/cella scivola in LS-Compactness, non nelle fasi room o nel compaction.

Cosa resta per AC#1 (link-by-link): TASK-453 dice QUANDO e COSA (LS-Compactness rimescola le mai-pinnate), ma la catena specifica per la saturazione di SLOT-VUOTO (non cell-erosion) va ancora chiusa: intersecare il dump ID per-fase con gli slot bad-seed noti (G11 mercoledi ore 10, H17, TASK-478/481) per tracciare in quale cut i docenti utili di quella cella si saturano. Il tooling lo supporta (dump ID-tagged per-fase); e' analisi read-only sul dump esistente, non un nuovo run.

Approach SEEDS (dall'evidenza TASK-453 + refutazioni TASK-460; TUTTI gated maintainer AC#3, NESSUNA adozione):

A. Ridurre il rimescolamento delle mai-pinnate in LS-Compactness (rafforzare M32 quasi-pinning, o 'soft anchor' sulle celle teoriche mai-pinnate). Direttamente dall'insight TASK-453: la resemblance si alza riducendo il reshuffle delle mai-pinnate, non pinnando di piu'.

B. Costruzione: ordinamento/seed placement che eviti la configurazione saturante alle celle scarse (venerdi 8am). Da TASK-460: la saturazione e' PRODOTTA dal nostro piazzamento (run ha 0 candidati conflict-free li' vs golden 6.6 docenti liberi/ora). Leva candidata TASK-457 (ordinamento costruzione).

C. Filtro mosse LS 'saturation-aware' che declina una mossa che crea una cella 0-conflict-free-candidate (difende il bacino invece di curare il buco a valle; complementare a TASK-478 EF/480 multistart).

Prossimo passo (sessione dedicata, gated maintainer): chiudere AC#1 tracciando il bad-seed G11 Wed-10 sui cut per-fase, poi formalizzare 2-3 approcci per il gate di maintainer (AC#3). Qui solo seeding read-only, nessuna adozione.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: zai
created: 2026-08-08 11:35
---
AC#2 DESIGN DOC (2026-08-08, zai, Fedora offline): deliverable = `task460-horizontal-recombination-design-2026-08-08.md`. Formalizza i 3 approach seeds (A soft-anchor/M32-piu-forte, B construction seed placement TASK-457, C filtro mosse LS saturation-aware) con evidenza TASK-453 + refutazioni TASK-460 + compromessi.

RACCOMANDAZIONE ONESTA: TASK-460 e' nell'asse metaheuristic+costruzione, che la campagna 2026-08-05 ha dichiarato ESAURITA (pivot al MODELLO). De-prioritizzato: procedere ora significa rientrare nell'asse esausto. Se l'asse MODELLO stallasse e si tornasse qui, l'approccio piu' promettente e' il FILTRO MOSSE (C), non il peso (A: pesi medium flat) ne' la costruzione (B: lever parziali per TASK-470). C e' complementare a EF+multi-start.

AC#1 residuo (bad-seed G11 Wed-10 trace sui cut per-fase, dump ID-tagged pronto in task453-results/dumps): de-prioritizzato perche' chiusura analitica su un asse che non si intende inseguire. AC#3 (approvazione maintainer + spec) gated, NON procede in autonomia: il design e' esso stesso 'non inseguire ora', che e' un verdetto per Fedora. Nessuna adozione, nessun codice.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
