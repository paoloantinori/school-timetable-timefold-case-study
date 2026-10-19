---
id: TASK-636
title: >-
  TASK-636 - Pacing dense-first golden-aligned per i corsi lab dei docenti fissi
  (l'interim del -1 di G06, decisione A di maintainer)
status: Done
assignee: []
created_date: '2026-09-21 04:35'
updated_date: '2026-09-21 05:13'
labels:
  - claude-planned
  - construction
  - m-15
  - golden-aligned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decisione maintainer 21/09 mattina ("Credo A o comunque la risposta che piu' si allinea al Golden attuale") sull'interim del -1 di integrato-6: finche' il calendario lab non arriva come input dal workbook/web-app (la chiusura architetturale definitiva), la COSTRUZIONE esercita pressione di distribuzione allineata alla pratica golden.

IL FENOMENO (catena completa in integrato-6/nearmiss/ARBITRAGGIO.md): corso lab 60h (T01/Servizio tecnico/G06, steering THU golden-derived): il GOLDEN concentra 10 blocchi 6h entro il 25/02 (burst monografici autunno+inverno); il NOSTRO template spalma 9 blocchi + 6 ore sparse fino al 3/6. Il blocco pieno del 3/6 (6h su giorno a quota 4) rompe l'economia di giugno: il 9/6 resta vuoto (l'H34 -1 della consegna). M34/M35 cappano il soffitto settimanale (max 6 ok) ma non la diluizione (15 settimane attive vs 10 golden).

MECCANISMO CANDIDATO (da diagnosticare PRIMA di costruire, prassi localizzata): dove nasce la sparsezza: (a) il corso ha celle template settimanali e lo stamp le salta per quote, o (b) non ha celle e vengono piazzate dalla coda fill/round-robin contro M20 (ipotesi piu' probabile data la cadenza ogni-3-settimane). La cura cambia: (a) densita' dello stamp, (b) pacing DENSE-FIRST del fill (riempi le settimane al cap M34 prima di aprirne di nuove: massimizza le settimane a cap piu' alto, minimizza le settimane attive), forma data-driven generale (il cap viene dal golden-cadence-reference, nessun nome hardcodato).

VINCOLI DI CASA: stessa forma preferenza-mai-forza del 599 (pass di preferenza nel tryPlaceChunk con fallback identico); golden-parity check (TASK-517 placement-parity) perche' il meccanismo cambia un piano di piazzamento; banco differenziale su costruzione (firma: settimane attive per corso, max-ore/settimana distribuzione, blocchi pieni) prima di qualunque ciclo; ratifica maintainer col gate A/B.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
21/09 MATTINA, DIAGNOSI LIVELLO-1 (dump costruzione 18/09, zero-compute): a COSTRUZIONE il template piazza solo 16/60 ore di Servizio tecnico G06, tutte in CODA (i 4 ultimi giovedi' 20/5, 27/5, 3/6, 10/6, 4h ciascuno). Nel CONSEGNATO quelle 16 ore non ci sono piu' (i4 giovedi' finali sono vuoti di T01): la LS le ha ridistribuite sui giovedi' sparsi autunno-primavera. DUE LIVELLI divergono dal golden: (1) TEMPLATE: tail-drip (i giovedi' d'autunno pieni di altri corsi fanno rinviare le celle a fine anno), (2) LS: scatter (ridistribuisce in cadenza ogni-3-settimane). Il golden: burst frontale concentrato (10 giovedi' entro 25/02). LA CURA DENSE-FIRST AGISCE AL LIVELLO TEMPLATE (dare alla LS un punto di partenza concentrato: celle piene al cap M34 nelle settimane gia' attive del corso prima di aprirne di nuove); il livello LS si difende da solo meglio partendo concentrato (S13/M32). PROSSIMO: leggere il meccanismo di rinvio delle celle nello stamp (perche' i giovedi' pieni rinviano a fine anno invece che alla settimana libera successiva): il punto e' nello stamping/quota del DayClusteredConstructionPhase.

21/09 MATTINA, DIAGNOSI LIVELLO-2 (meccanismo TROVATO nel codice, lettura zero-compute): la cadenza ogni-3-settimane e' fabbricata dal BUDGET PACING (DayClusteredConstructionPhase ~1510-1551, TASK-542 cure-2 post-mortem iter-22): filtro Bresenham (placed+h)*W <= budget*(w+1) con W = totalWeeks (TUTTE le settimane del weekday). Intento dichiarato: 'a thin course spreads its hours evenly across the year instead of front-loading them' (cura per l'iter-22: drain delle code, 6664h coda vuota da gennaio, 21 giorni G08 vuoti in primavera). Per 60h su ~34 giovedi' ammette ~1 giovedi'/3.4 settimane: la cadenza osservata e' voluta. COLLISIONE: il golden front-loada i lab dei docenti fissi (burst 10 settimane), il pacing uniforma. CURA ALLINEATA GOLDEN (la forma richiesta da maintainer): W efficace per-corso = settimane attive del golden (golden-cadence-reference.json, gia' caricato per M34/M35): W_eff = min(totalWeeks, goldenActiveWeeks) nel filtro di pace: il corso con profil concentrato completa nella sua finestra golden; i corsi senza riferimento golden mantengono lo spread uniforme di oggi (fallback invariato). L'esistenza stessa del golden prova che l'aggregato non svuota la coda (il golden riempie i giorni di primavera con gli ALTRI corsi che pacano fino a fine anno). NON riaprire l'iter-22: il pacing resta, cambia solo W per i corsi con profil golden concentrato. PROSSIMO: implementazione (few lines nel pace filter + accessor al reference), banco differenziale costruzione (firma: settimane attive per corso, blocchi pieni, coda vuota da gennaio come guardia anti-iter-22), golden-parity TASK-517.

21/09, DIAGNOSI LIVELLO-3 (autocorrezione): il level-2 (budget pacing) era su percorso DORMIENTE (day-compact e' opt-in DEFAULT OFF e integrato-6 non lo armava: il mio primo edit tocca un ramo che non gira in produzione; tenuto come co-cura dichiarata per quando day-compact verra' mai adottato). Il PACING REALE e' WeeklyTemplatePlanner.buildWeeklyTargets: weekly target per-corso = ore/settimane-attive-del-GRUPPO (~30, stage-aware): Servizio tecnico = 60/30 = ~2h/settimana UNIFORME. La cadenza ogni-3-settimane osservata nasce SOTTO: conflitti della cella del giovedi' (stampSkipConflict: il giovedi' e' affollato dalle celle degli ALTRI corsi) + coda round-robin del repair + LS. LA CURA DENSE AL LIVELLO GIUSTO E' UN PROBLEMA DI DESIGN VERO: finestre temporali per-corso nel template (cella attiva per le N settimane golden poi CHIUSA, cosi' il target 6h/settimana esaurisce il budget senza deadloccare la cella per il resto dell'anno: la preoccupazione e' documentata nel codice stesso nella politica micro-course). PROSSIMO (banco-first, prassi localizzata): caratterizzare i giovedi' G06 dai contatori di costruzione GIÀ ESISTENTI (stampSkipsByGroup e' cause-indexed: conflict/exhausted/goal): il dump dice QUANTE celle salta e perche'; poi il design della finestra per-corso su quella base.

21/09, DIAGNOSI LIVELLO-4 (chiusura catena, dal log del ciclo): 'stamp skips G06' ha ZERO occorrenze in tutto il log di integrato-6: il corso non salta celle perche' NON HA celle template. Il weekly target di Servizio tecnico (quota settimanale del gruppo ~25h divisa fra TUTTI i corsi via allocateLargestRemainder: quota 60/1980) arrotonda a ~0-1: la politica del codice (documentata in buildWeeklyTargets) manda un corso senza cella DIRETTAMENTE nella coda round-robin del repair tail. E' LA CODA a piazzare le 60h (firma coerente: le 16h del dump in coda d'anno, distribuzione da geografia-quota non da cadenza). I salti dominanti dello stamp sono di ALTRI corsi (exhausted 2552). LA CATENA COMPLETA: corso template-less -> coda round-robin spalma per quota -> LS ridistribuisce -> cadenza ogni-3-settimane osservata. Il design della cura (finestra temporale per-corso) ora ha tre punti d'attacco possibili: (a) dare la cella al corso con finestra golden (template con target alto su N settimane poi chiuso), (b) preferenza di cadenza nella coda round-robin (il tail preferisce date vicine a ore gia' piazzate dello stesso corso quando il profilo golden e' concentrato), (c) la co-cura W_eff nel ramo day-compact (gia' committata). Il punto (b) e' il meno invasivo (la coda esiste gia', preferenza-mai-forza). PROSSIMO: design detail + banco.

21/09 POMERIGGIO, IMPLEMENTATA + PRIMO BANCO (livello-5, autocorrezione dell'attacco): l'attacco (b) round-robin era SBAGLIATO - il banco fixture dimostra che placeLessonsRoundRobin e' MORTO nel real-seed senza pin (0 giorni per gruppo, lo stamp piazza tutto). Il pass che fabbrica il drip e' la QUOTA-AWARE FILL TAIL DENTRO LO STAMP (TASK-532): itera i giorni cronologicamente e riempie i deficit di quota con le code dei corsi in ordine-di-mappa, senza senso di cadenza. CURA IMPLEMENTATA: preferenza di pace (stesso confronto cumulativa-golden, helper unico goldenCumulativeExpected per i due siti) nella selezione-corso della fill tail + boost di priorita' nel round-robin (inerte nel fixture ma vivo in produzione dove il tail lavora). Flag `planner.construction.cadence-pace.enabled` OPT-IN DEFAULT OFF (la direttiva bounded-no-op esclude esplicitamente la costruzione; flip a opt-out standard SOLO in ratifica, precedente 599). Pubblicazione: terza mappa pace nel setCadenceReferenceStatic (chiave ISO week TimeslotSequence.isoWeekKey, parsing "2026-W39" con WeekFields.ISO), test-side mirror in SnapshotFixture per i banchi. BANCO (constructionEndRecurrence, 3 arm): OFF = byte-identico al pre-cura (inerzia provata); ON = paceBoostApplications=2089, dump DIVERGE, Servizio tecnico G06: 16h tutte in coda maggio-giugno -> 37h distribuite feb-giu (direzione golden: piu' denso e piu' presto), ricorrenza 72.6 -> 75.0, +45 lezioni piazzate in assoluto, guardia anti-iter-22 VERDE (giugno identico 215/215, nessun mese in regresso). GAP ONESTI: la finestra golden set-ott resta vuota (la fill tail puo' solo ridirigere i deficit esistenti, non spostare i corsi altri: la forma piena richiede il livello template/steering), giugno tiene ancora 8h (la cella H34 non muore a costruzione). UNIT: CadencePaceStaticsTest pinna la curva pubblicata contro la serie reale (6/30/30/30/60/60/0 + sentinella). PROSSIMO: suite completa + /simplify + code-review + decisione se il residuo giu-giu giustifichi anche il livello template o si ratifica cosi'.

<!-- SECTION:NOTES:END -->

## A/B IN CORSO + NEAR-MISS TENTATIVO-1 BRACCIO OFF (21/09 sera)

Braccio OFF (controllo): tentativo 1 consegnato [-2/0]. Decomposizione: le due
celle sono H7 (T21 2027-01-27 a 7h cap 6; T23 2026-12-02 a 7h cap
6), NON H32: il radar residual_decomposition aveva un BUG per i gruppi FCQuota
(max fra calendario_fc fine e la copia grezza class_days fabbricava quota 6
fantasma per G13 21-22/12 dove fc dice 5 e il golden e' esatto a 5): corretto
(nella stessa serata, script ora usa SOLO la fonte fc per i gruppi FCQuota).
Famiglia H7 con donatore assente: la terna h7cap ratificata non ha chiuso.
Blind puzzle neutro partito (entrambe le celle: ogni line-day coinvolto e'
ESATTAMENTE a quota, quindi la mossa nuda apre H32: il puzzle testa la
raggiungibilita' di delta<0). Tentativo 2/5 del braccio OFF in volo.

## ARBITRAGGIO NEAR-MISS TENTATIVO-1 (21/09 sera, chiusura)

Blind puzzle: la coppia mutua proposta muore su H1 da entrambi i lati (T23 @10 occupato il 27/01, T21 @10 occupata il 02/12); il fallback @14 muore su H28 (T21 doppio Inglese su G10 02/12 via @10 esistente). MA la ricerca ESAUSTIVA sul dump (H1+H7+H28+pin, conteggi invarianti per costruzione dello swap) trova 16.701 swap legali per T23 e 11.661 per T21: il -2 e' CURABILE, classe COMPOSITE_MISSING (mosse che nessun brancio della terna esprime). Il braccio che le esiste: h7swap (TASK-632, grammatica 1-per-1, valida su T13 ma DEFAULT OFF in questo run: la terna del banco lo lascia al flag di lancio). PROSSIMO: replay del trio con h7swap ARMATO sul capture off/ (DeliveryTrioBenchTest, trio.export=ab-adoption/off/export.json.gz) DOPO che l'A/B libera il laptop. Lezione blind-puzzle: per celle H7 la finestra del puzzle va sul mondo intero (le soluzioni vivono a mesi di distanza), o si va diretti all'esaustivo.

## DOMANDA maintainer 21/09 sera: perche' la LS non prende 3.373 swap legali? + i due banchi accodati

Dato chiave dal log OFF: B-ROOM finisce a **-13**, la consegna e' **-2** (la coda wrapper cura 11 punti; i 2 superstiti sono le H7). L'esaustivo full-family (H1/H7/H17/H28/H31/VRP/pin) conta **3.373 swap legali** sul consegnato: non strutturale. Banci ACCODATI sul capture off/ (dopo il braccio B): (a) replay terna con h7swap ARMATO (cura deterministica, predizione: chiude); (b) pass LS corto sul consegnato (predizione di maintainer: chiude in tempo zero; misura il soffitto non-deterministico). Il quando-nasce del tentativo-1 OFF e' perso (checkpoint sovrascritti dal braccio B): se una cella H7 sopravvive nel braccio B, la tracciatura sui SUOI checkpoint dira' se nasce in LS-era (filtri swap primi sospetti: GapPreserving/Composite) o nella coda deterministica.

## TERZO BANCO dalla domanda maintainer (21/09): micro-pass LS scoped+seeded

Chiarito il meccanismo: le PROPOSTE Timefold sono cieche (selettori su entita'/valori, non violation-directed; nessun mode' nativo community 'parti dalle celle in violazione'), l'ATTESAZIONE e' punteggio-diretta: con 3.373 swap vincenti su ~78M coppie, un LS corto li incontra in secondi (statistica, non mira). La mira esiste gia' nella terna (enumerazione target dai match). IDEA maintainer formalizzata: micro-pass LS a delivery con entita' = lezioni giustificate dai match hard residui (selettore filtrato), EnvironmentMode REPRODUCIBLE + seed fisso = riproducibile (la mia 'non-determinismo per costruzione' era sbagliata: e' non-oracolo, non non-deterministico), tempo-bounded. Scala banchi sul capture off/: (a) h7swap armato, (b) LS corto non-scoped, (c) LS scoped+seeded. Predizioni: tutti e tre chiudono il -2; (c) e' il candidato design se conferma (mira+seme+tetto).

## RATIFICATO (maintainer 21/09 sera): DEFAULT ON, opt-out standard

Flip eseguito: accessor null/absent -> ON. Inerzia post-flip verificata: default arm
armed=true paceBoost=1974 ricorrenza 74,0; -D=false arm BYTE-IDENTICAL al pre-ratifica
(dump coincidente). Voce CLAUDE.md scritta. Residui aperti che NON sono di questo task:
la finestra golden set-ott resta vuota (serve livello template/steering: eventuale 432),
il -2 del braccio OFF e' COMPOSITE_MISSING (TASK-638), la notifica referente G06 giugno e'
pronta (referente-notifica-g06-giugno-4h-2026-09-21.md).
