---
id: TASK-678
title: >-
  TASK-678-Braccio under-floor non-empty pass tra H34 e H32 la famiglia mancante
  H33 dei giorni sotto-pavimento con lezioni
status: Done
assignee: []
created_date: '2026-10-04 20:34'
updated_date: '2026-10-04 23:12'
labels:
  - claude-planned
  - m-15
  - near-miss
  - zero-hard
dependencies: []
priority: medium
type: enhancement
ordinal: 326000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Nato dalla round near-miss 04/10 (task677-results/nearmiss/REPORT.md, cieco G14 NO-SOLUTION locale). LA CELLA: G14 2027-05-07 consegna [-3/0] = 2 ore ordinarie alle 9-10 (H33 sotto pavimento 3 + H31 partenza alle 9). Il cieco (cost model reale, mondo chiuso 3 giorni) ha PROVATO che la parte H31 non e' curabile localmente (BFS esaustivo: le 4 lezioni che occupano lo slot 8 del venerdi appartengono a gruppi-quota congelati) e ha TROVATO la cura dell'H33 in 1 mossa: import Orientamento 05-06@12 -> 05-07@11 (donatore same-group edge-take, T14 libero). IL GAP: NESSUN pass in servizio mira ai giorni sotto-pavimento CON lezioni presenti: EDF mira ai giorni dichiarati vuoti (H34), QFR/H32 ai giorni sotto-quota, e i gruppi fuori-quota (G14, 1/G08) sono fuori da entrambi. L'H33 e' ASSENTE dal cure-registry. PROPOSTA: braccio EDF "under-floor non-empty" (o estensione QFR con scope floor): target = (gruppo, data) con lezioni presenti, ore non-Stage < DayFloorDeriver floor, gruppo qualsiasi con floor attivo; mossa = import same-group dal bordo di un giorno donatore sopra il pavimento (forma TASK-619, quota-free per i fuori-scope, H17-safe edge-take, H1 live). RESIDUO DICHIARATO: la meta' H31 del nodo resta non curabile localmente (cascata cross-date su altri gruppi, fuori grammatica: residuo -1 della classe congestione venerdi). EVIDENZA DISPONIBILE: banco naturale = le due capture in task677-results/nearmiss/ (pred-a3 -3 e adopt-final -2); la cura predetta dal cieco e' verificabile al banco prima di implementare (predizione pre-registrata: 1 mossa chiude H33, score -3 -> -2).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-04 21:09
---
ESITO 'prova con 678' (sera 04/10, banco UnderfloorBench678Test 3/3 verdi, BUILD SUCCESS): (1) L'BRACCIO ESISTEVA (TASK-577 H33_FLAG opt-in, bancato v131) ed era DOPPIAMENTE MORTO sugli stati consegnati: GATE-E asse-quota (donatori a quota tutti rifiutati) + stanza trascinata occupata. La descrizione di questo task ('famiglia mancante') era imprecisa: mancava la VITA dell'braccio, non l'braccio. (2) EMENDAMENTI 678 applicati: GATE-E floor-form per quota-esclusi (precedente 619 branch-aware), atterraggio room-aware con riassegno (grammatica 641) + gate sul value range del director, bordi candidati entrambi (lastKey+1 E firstKey-1: un giorno window-capped ha solo il bordo sotto), trail undo a 3 slot con restore stanza. (3) CONTROLLO POSITIVO SINTETICO (ferita su giovedi' G14 05-06, forma forced657): movesApplied=1 cellsClosed=1 conflictsAdded=0 -> l'braccio VIVE. (4) LA CELLA REALE E' STRUTTURALE: venerdi' G14 = slots 0-2 (GROUP_CALENDAR_PATTERNS), l'11 fuori range (la 'mezza cura' del cieco era artefatto dello spec senza calendario), l'unica terza cella e' lo slot 0 teacher-locked da 5 giorni saturi -> l'braccio enumera e correttamente non tocca (assertito nel banco). Il NO-SOLUTION del cieco era giusto nel modello reale. (5) RITROVATI: divergenza isSlotLegalFresh vs value range (Confezione/G14, regola quota: TASK separato da aprire) e bug sweep bordo-unico (fixato). Il flip del default resta a maintainer: ora esiste l'evidenza positiva sintetica + la prova del non-danno sulla cella strutturale.
---

author: claude
created: 2026-10-04 21:54
---
CODE-REVIEW HIGH 3-angle (04/10 notte) + CURE APPLICATE. CONFIRMATO E CORRETTO: F1 rollback corrompeva l'indice stanze (restore della stanza PRIMA del de-index: occupante fantasma sulla nuova + marchio cancellato sul bloccante della vecchia -> H2 potenziale a meta' pass; percorso VIVO per H34/H32 ratificate con need>=2): fix = de-index prima con ts/room correnti, restore dopo. F3 il ramo ROOM-deferred non valutava mai il fresh check (landingRejectReason usciva sulla ROOM): aggiunto esplicito. F6/M3 la scan prendeva la stanza PIU' PICCOLA libera (ordinamento ascending): ora PREFERISCE capacity>=size con fallback (M3 medium accettabile per chiudere l'hard: la dottrina). F5 la premessa del floor-form era FALSA (M20/M33 LEGGONO G14: l'esenzione TASK-496 fu RIMOSSA 16/08): commenti corretti col TRADE DICHIARATO (~1000 medium per ora di deficit sui donatori a quota, belt-invisibile, da misurare nell'A/B H33 prima di qualunque flip). F7: census ristorato nel ramo reject dell'estrazione, commento sortedness sul binarySearch, Pass.rooms morto rimosso, javadoc orfano rimosso, commento both-edges che dichiara l'allargamento alle famiglie ratificate. R2-F1: asserts conflictsAdded+hard-total nel banco. R2-F3: fixture v2SwapRollsBack pubblica setAllRoomsStatic(vuoto) (indipendenza dall'ordine). VERIFICATI PULITI: H-COPRESENCE room-blind (fonte), H2/index corretti nell'apply, both-edges non puo' overfillare (need-bounded), in-scope gruppi invariati. RESIDUI REGISTRATI: H28/H30 non guardati dall'atterraggio EDF (classe pre-esistente, allargata: TASK dedicato da aprire al prossimo giro); M37 possibile sull'atterraggio (100/pair, bounded); il test di regressione del rollback-F1 (need>=2 con riassegno) differito; la preferenza donatori surplus-first non implementata (il trade e' dichiarato). NONDETERMINISMA osservata e dichiarata nel banco (rifiuto pulito / net-zero / chiusura: invarianti assertite, esatto esito non pinnato fino a 679).
---

author: claude
created: 2026-10-04 23:12
---
RATIFICA (maintainer, 04/10 notte, 'accetta e deploya'): braccio H33 DEFAULT ON (84c8ce77 + fix post-review), forma opt-out standard, extensionsArmed corretto (F1: il delivery gate non vedeva h33), banchi congelati pinnati H33=false (il finding della /code-review finale: replay609/DoomTrio/DeliveryTrio/LsVariance), javadoc TASK-612+678, registry allineato. BLAST RADIUS verificata contro il seed: ramo H33 strutturalmente morto per in-scope (nessuna quota < floor), popolazione viva = sotto-pavimento non-vuoto dei soli esclusi (G14 input-locked): atteso enumera-e-non-chiude. GATE SALTATO (ordine esplicito): A/B pre-flip, trade M20 alla prossima campagna. DEPLOY eseguito (runbook, L1+L2 verdi, banner verificati in produzione).
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Braccio H33 under-floor RATIFICATO DEFAULT ON e DEPLOYATO in produzione (runbook L1+L2 verdi, banner H33/threshold/predictor nel log). Emendamenti 678 + flip + extensionsArmed + banchi congelati pinnati. Residui: TASK-679 (deriva fresh/range con predizione pre-registrata), gambe H28/H30 EDF, test regressione rollback, trade M20 da leggere.
<!-- SECTION:FINAL_SUMMARY:END -->
