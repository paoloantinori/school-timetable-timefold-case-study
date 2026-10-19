---
id: TASK-560
title: >-
  TASK-560 - Cura residui H34: EmptyDayFinisher (caso a confermato dal probe:
  10/10 celle curabili)
status: Done
assignee: []
created_date: '2026-08-26 10:40'
labels:
  - claude-planned
  - task557-family
dependencies: []
priority: high
type: spike
ordinal: 318000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Comitato 2 (Fedora 26/08, dopo VERDICT H34): discriminante sulle 10 celle H34 residue della consegna dci-h34on. PROBE OFFLINE eseguito (script /tmp/h34-residual-probe.py, logica: donatore unpinned stesso gruppo, gate e floor/quota, finestra calendario, H1/H5/H3, room-safe; dichiarate le omissioni VRP-per-corso/H28/ripple): ESITO CASO (a): 10/10 celle hanno candidate legali (22-155 per cella) e 10/10 hanno candidate PRIMA-ORA H31-legali allo slot primo-aperto (12-129): PASSATA FINALE possibile, NON marker di bacino (caso b confutato). Da fare: EmptyDayFinisher prototipo (sorella QFR v2/ExactFinisher, occasione post-B-ROOM dopo QFR-2/RoomTail, improve-or-noop) dietro flag DEFAULT OFF, banco localizzato PRIMA con predizione, NON adottare. BANCO KEMPE ESITO (26/08 15:2x, mandato Fedora rilancio): KempeCellNeighborhoodProvider implementato (Move Streams preview, PreviewFeature.NEIGHBORHOODS, terminale pick/pick/asMove dal builtin letto nel sorgente; transazione di CELLA INTERA per la legge della valle H32: Kempe-2 monoria = -1 H34 vs +(quota-1) H32, mai preso da accettatore monotuno) + KempeCellNeighborhoodDiscriminatorTest (hill climbing acl=1 senza acceptor, 120s/45s, fuori dal decider MT-LS). MISURA sullo stato export h34on (10 celle): 0/10 chiuse, wall 46.3s, nessuna mossa accettata (ogni transazione pianificabile e' hard-peggiorativa al pricing o gate-e-bloccata). PREDIZIONE 2..6 SMENTITA, CLAUSOLA PRE-REGISTRATA SCATTATA: le celle sono territorio MULTI-START o KEMPE-CHAIN (TASK-482). EDF v0 dichiarato PROTOTIPO CHIUSO (il fix v0.1 slide-pre-estrazione non si fa: stessa geometria dimostrata morta). Caveat fixture: baseline -124.013hard != -14 della consegna (statiche non ripubblicate dal loadFromExport: la misura valida e' chiusura-celle e non-pegiorita' relativa; republish completo = futuro). Move Streams confermata come TECNOLOGIA giusta (API completa), la cura delle celle e' bacino/catena non grammatica.

VALUTAZIONE RIUSA-PRIMA (emendamento Fedora 26/08 + correttivo, /pa:reflect): ITEM 1 corretto: NESSUNA bocciatura storica misurata del cartesian in LS esiste; il non-ship e' DERIVA ARCHITETTURALE (pipeline staged rese secondario il tuning LS; unica reiezione tecnica documentata: pooledEntityPlacer CH). L'obiezione viva e' solo sulla FORMA config: cartesian due-entita' senza join ~1e14 coppie alla cieca = morto; la capacita' composite non e' mai stata bocciata e sopravvive ai quattro argini (pesi adattivi, single-writer, filtri entity-set, finestra dedicata). ITEM 2 verificato DAL SORGENTE AL TAG (sources-jar 2.3.0): Move Streams API ESISTE, preview dietro PreviewFeature.NEIGHBORHOODS; NeighborhoodProvider + MoveStreamFactory.forEach + join(BiNeighborhoodsJoiner) + Moves.compose = la mossa condizionale due-entita' esatta (Kempe-2); rischio integrazione col decider MT-LS reflection-swapped: finestra fuori dal decider. ITEM 3 DECISIONE: (b) strutturale Move Streams Kempe in finestra dedicata (sblocca TASK-482 piano B); (a) swap-compound solo ponte tattico. Criterio applicato: la superficie composita manca ovunque (shell-game iter-6/7, 93 FCC teacher-locked, TASK-482 aperto, M-TR). ITEM 4: predizione del banco Kempe scritta PRIMA nel registro (2..6 celle su 10, 0 hard aggiunti, finestra entro budget; se 0: territorio multi-start/chain). Flaw latente EDF v0 registrato (slide controllate pre-estratta: same-teacher falsamente rifiutato; non ha giocato nella traccia; fix v0.1 se il finisher sopravvive alla decisione).

TRACCIAMENTO 26/08 14:15 (mandato Fedora 13:25): DISCREPANZA RISOLTA, cache VRP REFUTATA. Nessuna tracciata muore su VRP (il confronto fresh-vs-cached non si innerva mai; getter diagnostico aggiunto in Lesson). Tre strati misurati: (1) probe v1 su DB live mutato dai save resume (1344 roomless = stato VERO dei checkpoint, sospetto save-bug RITIRATO); (2) probe v2 cieco alle stanze STAGE (11364 G08 in R08 a 04/19 h8): probe v3 (scripts/analyze/h34-residual-probe.py, occupanza Stage inclusa) = 9/10 celle con prima-ora legale; (3) COLLO DOMINANTE = SLIDE: le candidate superstiti (1824, 1834 su G13 04/19: ownLanding OK) muoiono sulla slide del donatore (10055/10050 slot5->4, H1 docente occupato sotto; SLIDES=30 in censimento). La grammatica single-batch non chiude in questo bacino. Direzioni v1 (decide maintainer): estrazione con cambio stanza, ripple multi-slot, multi-start. Infra fix odierni: grep -q/SIGPIPE in db-query.sh e prod-start-and-wait.sh (falsi "container not running" da quando i container hermes allungano l'output). Flag resta OFF.

ESITO BANCO LOCALIZZATO (26/08 13:08, resume dal POST_B_ROOM del ciclo h34on, contesto v2+H34+EDF ON): 0 mosse, 0 celle, 0 conflitti, nessun revert. Predizione 2..6 SMENTITA (caso rischio). Censimento rifiuti (property-gated nel codice): GATE-E=1518, ROOM=257, H1=172, VRP=49: i giorni donatori dichiarati non hanno surplus sopra max(quota, floor); i non dichiarati passano il gate ma le lezioni muoiono su stanza/docente/VRP al primo slot della finestra. DISCREPANZA APERTA col probe offline (12-129 candidate per cella con gate analoghi): prossima sessione = tracciamento per-lezione (5 candidate nominate attraverso i gate EDF; sospetti: cache VRP sul path resume, pin promossi residui). v0 committed DEFAULT OFF: prototipo + misura richiesti dal comitato, NON adozione. Banco sintetico TDD verde (5 mosse/1 cella/0 conflitti). Due lezioni di fixture del TDD: teacher id null nel banco = collasso H1; doppio blocker same-group = conflitto H3 del FIXTURE non del pass.

NOTA tecnica per il design: il fill parziale di un giorno vuoto PEGGIORA il quadro (H32 conta (quota-ore): 1 ora su quota 5 = +4 H32 vs -1 H34) - il finisher deve riempire a quota ATOMICAMENTE (gate c) oppure astenersi; la differenza da QFR v2 va trovata diagnosticando QUALE gate miete le candidate (qfr.v2.debug su resume).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->


ESITO FINALE (26/08 notturno): (1) EDF v0.1 (flaw fixato) sul VINCITORE del multi-start: 49 mosse, 4 celle chiuse, 0 conflitti, NESSUN revert (predizione 0..2 smentita in meglio): IL FINISHER TORNA CANDIDATO, flag resta OFF, decide maintainer. (2) Banco Kempe LS whole-cell: 0/10 (valle H32: le transazioni parziali mai accettate; registrata la legge). (3) Move Streams confermata idonea (provider committed come riferimento). (4) Le celle residue del vincitore: 12 = G09 (inriempibili: foglio 670h vs pool 666h) + G11 2 (corto 1h) + 7 riempibili in aritmetica esatta. La direzione per le ultime 7: catena-3 catalogata (soppressa stanotte con motivazione nel registro), pre-booking in costruzione (proposta registrata), risposta referente per G09.

## Final Summary

VERDETTO FINALE (26-27/08): EDF v0.1 (flaw slide-pre-estrazione fixato, 633ec106) CHIUDE 4/12 celle sul vincitore multi-start (49 mosse, 0 conflitti, nessun revert) e curva cross-bacino 4/0/3/2/1: l'impatto e' correlato alla qualita' del bacino. Banco Kempe Move Streams: 0/10 (valle H32: transazioni intere necessarie, legge registrata). PACK DI GATE pronto: task560-results/gate-adozione-edf-v01-2026-08-27.md (raccomandazione ADOTTARE, decide maintainer). Flag resta OFF. Commit chiave: 633ec106 (v0.1), f0fe5289 (provider).

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
