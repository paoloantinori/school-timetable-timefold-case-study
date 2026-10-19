---
id: TASK-632
title: >-
  TASK-632 - Swap-for-overcap: la grammatica x-swap sui target H7 (dal blind
  puzzle T13)
status: Done
assignee: []
created_date: '2026-09-20 08:51'
updated_date: '2026-09-20 23:30'
labels:
  - claude-planned
  - h7-cap
  - blind-puzzle
  - m-15
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal blind puzzle sul near miss integrato-1 (-4, direttiva near-miss->blind-puzzle 20/09): T13 2027-01-20 ha 7h su cap 6 spalmate su 4 gruppi (G01 @10/@13, G05 @11, G06 @8/@9, G10 @14/@15) in una regione QUOTA-TIGHT (13-29/01: tutti i 4 gruppi a quota esatta ogni giorno, tolleranze 0, nessun surplus). Conseguenza strutturale: la famiglia export+donatore del braccio h7cap e' MORTA li' (nessun giorno donatore puo' cedere un'ora senza aprire il proprio H32). Il cieco zero-contesto ha trovato la chiusura LEGALE in UNA mossa: SWAP 1-per-1 con una lezione stesso-gruppo su altro giorno (G/Comportamento Sociale G01 20@13 <-> T20/Figurino G01 18@11), arbitrata PULITA sul dump completo (T20 libero a slot 13 il 20, cap 3/6, tuple H28 singleton, conteggi/contiguita'/quote invariati PER COSTRUZIONE: lo scambio di celle 1-per-1 lascia identici gli occupancy set). Famiglia mancante confermata: la grammatica x-swap applicata ai target OVERCAP (h7cap enumera, ma sa solo esportare+donatore).

PROTOTIPO: tryH7SwapPair in HoleRelayFinisher (flag opt-in planner.construction.hole-relay.h7swap.enabled, solo "true" arma, default OFF). Gate: VRP entrambi, H1 entrambi con esenzione partner, H7 arrivo entrambi (classe 616), H28 not-grow entrambe le tuple toccate (h28PairSafe riusato), stanze (primo atterraggio pre-check conservativo, secondo roomFor sulla cella liberata, sequenza x-swap), belt verifyNoNewHoles entrambi i giorni + verifyComposite. Liveness re-check ALREADY-CURED (lezione F6). Sequenza di scambio a 2 cicli nell'indice Live verificata mossa per mossa (put-sovrascrive + removeFrom netta, convergenza corretta in apply e revert).

BANCO: (a) inerzia su integrato-5 export (stato a 0 post-v4: nessun overcap, census ALREADY-CURED/NO-CANDIDATE, esito invariato); (b) misura reale sugli export m39-off/m39-on (scp da fedora-box): census H7SWAP + delta hard. Predizione Fedora: almeno un overcap chiude dove h7cap era morto per quota-tight.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Prototipo tryH7SwapPair committato dietro flag opt-in (default OFF), compile BUILD SUCCESS + StagedResumePlanTest
- [ ] #2 Banco inerzia: integrato-5 export con flag ON, stato a 0 -> nessun overcap enumerato, esito byte-invariato, census ALREADY-CURED o assente
- [ ] #3 Banco misura: export m39 (scp fedora-box->laptop) con terna+h7swap ON -> census HRF-H7SWAP-* e delta hard; predizione pre-registrata (almeno un overcap quota-tight chiude)
- [ ] #4 Gates /simplify + code-review high sul diff prima di ogni proposta di ratifica; il flip default resta decisione di maintainer
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
20/09 BANCO (laptop 021df3b9, su main 5f58e12d con override -Dtrio.export): AC#1 OK (compile+sanity 6/6 su a8132bf2, self-skip verificato), AC#2 OK (INERZIA CONFERMATA AL BYTE su integrato-5: terna v4 chiude la T08 -1 -> 0, h7swap ALREADY-CURED=1, zero azioni), AC#3 MISURATO con esito onesto: m39-off -18 -> -7 e m39-on -22 -> -11 (la terna vale -11 su basi pre-617/620, zero nuove violazioni), MA h7swap resta a ZERO istanze: nessuna delle due basi espone un overcap quota-tight donor-less (su off non c'e' overcap; su on l'overcap T21 2027-06-03, LA CELLA DEL BLIND-MENO1, viene chiuso da h7cap v4 col donatore Orientamento). VALORE NON PROVATO: il census h7swap non ha mai enumerato un rifiuto di coppia. RACCOMANDAZIONE (laptop+Fedora concordi): resta DEFAULT OFF, assicurazione non testata finche' non emerge una base con overcap senza donatori tolleranti (il caso T13/integrato-1 resta il candidato naturale, servira' base room-faithful o il ciclo integrato-6). BONUS della misura: SECONDA cella reale chiusa dalla catena 629 v1->v4 su dati mai visti (T08 + T21). Gates /simplify in corso su 7703872d..a8132bf2.

20/09 POMERIGGIO (banco 632b, fcef55e8 su wt/delivery-trio + fixture 22ffeffd/297e1a8c su main): fixture room-faithful di integrato-1 COSTRUITA (pass CH-only greedy deterministico, 3 cure documentate dall'agente: ordine scarcest-first, famiglie dei pin calcolate direttamente perche' il pipe non porta stanze, clean() pre-banco per statics fantasma; 12551 stanze, 0 room-null) e h7swap esercitato per la PRIMA VOLTA su una base con l'overcap T13: MIGLIAIA di coppie valutate, ZERO applicate, delta -52 -> -52. Census: CAND-H1=2152 PARTNER-H1=1389 (REALI, indipendenti dalle stanze), ROOM=1185 ROOM-CAND=537 (contaminati dalla qualita' greedy della fixture). PREDIZIONE SMENTITA; ATTRIBUZIONE ONESTA: grammatura-vs-fixture non discriminabile dal census aggregato. Follow-up immediato: debug per-coppia [HRF-H7SWAP-cand] committato (895f887d), rerun in volo che nomina il gate che uccide la coppia ESATTA del blind. Side-effect del ciclo: il laptop ha curato il main non-compilante (LocalDate::min inesistente introdotto da una mia cura /simplify copiata dal reviewer senza compilare: fix 4a9ae646) - la lezione mai-spedire-codice-non-compilato e' rafforzata.

20/09 RERUN DISCRIMINANTE (27d0d5b8): LA GRAMMATICA E' VIVA. Le righe per-coppia dimostrano che il braccio trova e valuta la coppia ESATTA del blind (G/Comportamento Sociale G01 20@wall13 <-> T20/Figurino G01 18@wall11; NB il debug stampa slot 0-based: @5 <-> @3): la coppia passa VRP, H1, H7-ARR e same-teacher, e muore SOLO su ROOM-CAND (nessuna stanza di famiglia libera alla cella di atterraggio sull'assegnazione greedy NON rifinita della fixture). Il collo e' la qualita' delle stanze della fixture, non la grammatura. PROSSIMA PROVA (differita): base con stanze rifinite (export vero se emergera', o pass CH+LS sulla fixture) dove la coppia e' attesa applichi e chiuda l'overcap T13; in alternativa il ciclo fresco integrato-6 col braccio armato per misura. Census aggregato invariato (CAND-H1 2152 / PARTNER-H1 1389 / ROOM 1185+537: il grosso H1 e' reale, il quadro ROOM e' fixture-limitato). Dichiarato: SAME-TEACHER senza riga per-coppia. Secondo fix di compilazione pagato al laptop (a972aa45: candSlot non dichiarato nella strumentazione; dopo LocalDate::min: la delega compile ha funzionato due volte, il processo di scrittura deve smettere di produrre non-compilanti).

21/09 NOTTE, FINALE (42f78130): la cella T13 CHIUDE H7 -1 -> 0 sulla base DAVVERO rifinita (medium -7,06M -> -2,48M in 600s), ma via H7D3 (braccio ratificato: export Relazione cliente @7 + partner Lingua Inglese), NON via h7swap (ALREADY-CURED x2, non enumera). Predizione h7swap-applied SUPERSEDED non smentita: il collo era la qualita' delle stanze, non la grammatura. SERIE 632 COMPLETA: (1) inerzia byte su integrato-5; (2) m39: terna -11/11, h7swap zero istanze (nessun overcap donor-less); (3) fixture greedy: migliaia di coppie valutate, la coppia esatta del blind passa VRP/H1/H7 e muore solo su ROOM-CAND; (4) base rifinita: la famiglia H7 si chiude con gli armi in servizio. CONCLUSIONE: h7swap = assicurazione marginale (vale solo su overcap dove h7cap/h7d3 non passano: nessun caso noto), resta DEFAULT OFF a tempo indeterminato. Due fix harness dichiarati (solutionClass sullo slice; solve() ritorna la clonata non muta il chiamante).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
