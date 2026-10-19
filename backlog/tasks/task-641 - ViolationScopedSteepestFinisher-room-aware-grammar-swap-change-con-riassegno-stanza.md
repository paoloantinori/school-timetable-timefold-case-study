---
id: TASK-641
title: >-
  ViolationScopedSteepestFinisher room-aware grammar (swap/change con riassegno
  stanza)
status: Done
assignee: []
created_date: '2026-09-23 04:51'
updated_date: '2026-09-23 10:29'
labels:
  - claude-planned
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task638-results/blind-and-moves/
  - claudedocs/spalmone-2026-27/integrato-1/export.json.gz
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DIAGNOSI NOTTURNA 857313d7: la H28 di integrato-1 e' composita sul tier stanze: la cura timeslot esiste (swap M-Lav@9<->M-Cad@12 arbitrata: la H28 chiude) ma le stanze viaggiano con le lezioni e nel pipe+CH-greedy di integrato-1 collidono (H2 -1000, hard -52->-1051). Analogo: 637-off il CHANGE del boxed Servizio tecnico atterra senza stanza (H29). Il 638 attuale non tocca mai room. CLASSE: candidati compositi = mossa timeslot (swap o change) + riassegno stanza via Moves.compose(timeslotMove, Moves.change(roomMetaModel, lesson, room)) - room e' planning variable (Lesson:129, VRP getAvailableRooms tag-filtrato). STRATEGIA: secondo passaggio PIGRO nella stessa iterazione, solo quando NESSUN candidato semplice migliora (superficie ratificata intatta nel percorso comune); enumerazione stanze da getAvailableRooms libere allo slot di destinazione (indice occupazione per-timeslot ricostruito per iterazione), ordinamento deterministico per room id; oracolo full-constraint decide (H2/H-M2/H29/M3/M-LF visibili). CROSS-PRODUCT con carried per le swap ({carried} U freeRooms per lato), skip la combinazione plain gia' valutata. BANCO: OffCapture638BenchTest mode=steepest su integrato-1 + 637-off + regressione sui 4 stati chiusi. Flag opt-in strict-literal planner.construction.violation-steepest.room-aware.enabled (solo "true" arma): il 638 e' ratificato DEFAULT ON, l'estensione grammaticale NON si attiva da sola. Ratifica delegata solo a evidenza completa.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] Banco integrato-1: la swap H28 M-Lav@9<->M-Cad@12 + riassegno stanza viene proposta e l'hard migliora (H28 chiuso senza H2)
- [x] #2 [ ] Banco 637-off: il CHANGE boxed Servizio tecnico atterra CON stanza (H29 curata)
- [x] #3 [ ] Regressione: i 4 stati gia' chiusi dal catalog sweep restano IDENTICI col flag OFF e col flag ON non peggiorano
- [x] #4 [ ] Flag opt-in strict-literal + census/banner, knobs.md aggiornato
- [ ] #5 [ ] Suite ./scripts/core/test.sh EXIT 0 con heap 3g
- [x] #6 [ ] Gates /simplify + code-review high prima di qualunque ratifica
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
23/09 mattina: IMPLEMENTATA e validata al banco 3/3 pre e post /simplify-refactor. 637-off (export): -3->0 (CHANGE + SWAP+ROOM R11 chiude l'H29 che lo sweep lasciava). integrato-1/run (pipe): -52->-41 in 7 mosse, inclusa la cura blind-puzzle 'SWAP+ROOM R07 Lavorazioni<->TecnicheSaldatura'. 636-off: IDENTICO flag ON (23337 eval, pass room mai invocato: forma pigra). /simplify 4-reviewer: cure applicate (steepest() unificato, direzione canonica anti-duplicazione eval, occupazione a Set per-slot con memo value-range, variableMetaModel generico, niente nullable threading, eccezione pigra dichiarata nel contratto di classe); finding跨-family (8 copie predicato stanza-libera) = TASK-652. STATO AL PUSH WIP: flag strict-literal default-OFF, suite delegata al laptop (fedora-box chiuso per spostamento), code-review high DA RIESEGUIE su Fedora al ritorno PRIMA della ratifica.

CODE-REVIEW HIGH completata (8 finder angle + 5 verifier, 23/09): 10 finding confermati/non-ridondanti. CURATI nel commit 234d8a94: F1 CRITICA (filtro direzione-canonica scartava l'unica direzione delle swap target-vs-non-target: dedup coppia non-ordinata con PairKey), keep-null arm per stati H29 (withCarried sentinel null), fairness quota per-plain + leftover (il prefisso swap-heavy non mangia il cap), javadoc enabled() stantio pre-ratifica, javadoc/call-site ScheduleService contraddittori (room-aware armato PUO' muovere stanze), census log due popolazioni, knobs.md rigenerato vero (footer 151, puntatori corretti). POSSEDUTI altrove: co-presenza H2 cieca in freeRoomsAt = TASK-652; metamodel generic = TASK-642. DEFERRED (cosmetici, non bloccanti): angle-E FQN/hoist/contatore, snapshot-shell banco su throw composito, double-memo sortedRoomsByLesson vs cachedAvailableRooms, vacating-only exemption genera varianti doomed in stati H2-wounded. BANCO 3/3 post-cure: 637-off -3->0, integrato-1 -52->-41 (blind-puzzle cure ora mossa #2), 636-off IDENTICO 23337 eval. Suite finale in volo; A/B ON arm su 234d8a94 sbloccato dopo il fermo programmato (OFF arm su b9113f2b, path OFF byte-identico dichiarato nel VERDICT).

RATIFICA 23/09 (delegata, evidenza completa): flip a DEFAULT ON opt-out standard nel commit di ratifica; voce CLAUDE.md aggiunta; knobs.md rigenerato (room-aware ora forma standard). A/B 5/5 con parita' dichiarata no-op legittimo + banco 3/3 doppio + code-review high con cure. CRITERIO #5 (suite EXIT 0 sullo stato ratificato): suite in volo, il commit di ratifica parte a suite verde. Deploy sito: la flip arriva sul sito pubblico alla prossima finestra di restart (la macchina e' di referente dopo referente-READY: niente rebuild mid-experiment, comportamento del sito invariato perche' la pigra forma spara solo su residui che la grammatica base non chiude).
<!-- SECTION:NOTES:END -->

2026-10-03 chiuso come FATTO (marcatura stantia): room-aware grammar RATIFICATO DEFAULT ON il 23/09 (delegata, "evidenza completa"): secondo passaggio pigro, oracolo full-constraint improve-or-noop, banco 3/3 doppio indipendente (637-off -3->0 con SWAP+ROOM che chiude l'H29; integrato-1 -52->-41; 636-off identico al pre), A/B 5/5 parita' 0-hard dichiarata no-op legittimo, suite 1370/0 x2, code-review high 8-angle con F1 curata. NOTA REGISTRO: questo file condivideva l'id TASK-641 con il task "Consolidare il resolver del metamodel timeslot" (file distinto, lavoro ancora aperto): quel file e' stato rinumerato TASK-676 per risolvere la collisione.
