---
id: TASK-419
title: >-
  Solve a stadi con promozione pinned del template + degrado selettivo a
  quasi-pinned (413 candidate 1, raffinato da maintainer 2026-07-25)
status: Done
assignee: []
created_date: '2026-07-25 12:24'
updated_date: '2026-07-27 18:27'
labels:
  - claude-planned
  - spalmone-2027
  - solver
dependencies:
  - TASK-408
priority: medium
ordinal: 100
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Proposta (maintainer, 2026-07-25, in sessione)

"Promuovere" i quasi-pinned (celle template M32) a PINNED veri per il solve, accettando il rischio di infeasibilità, con un workflow allungato: prima li considera completamente pinned, poi li DEGRADA a quasi-pinned (non a liberi!) se non trova soluzione valida. È il candidate 1 di TASK-413 ("il processo umano letterale") raffinato: al tempo del design il rilascio ricadeva su S13=2 (nulla); oggi il fallback è M32 graduato — i due meccanismi COMPONGONO.

## Perché ora (evidenza val408-run1, 2026-07-25)

L'attribuzione per fase (dump TASK-415, prima volta) mostra: la costruzione consegna ricorrenza 74,3%, la fase 2 (LS Compactness) erode a 52,8%, le fasi 3-6 non muovono nulla. ANCHE CON M32 l'erosione di fase 2 è −21,5pt: è il tetto del single-solve a penalità. Col template pinnato nello stage 1 la ricorrenza finale sarebbe ~74% BY CONSTRUCTION — il salto verso il golden (41,6% burst, cadenza costante) che nessun retuning di peso compra. Alternativa concorrente sullo stesso gap: benchmark mosse/accettore di fase 2 (più economico, meno deterministico) — se questo task parte, confrontarsi con quel filone, non duplireferente.

## Workflow (design concordato)

- **Stage 0**: costruzione come oggi (stamp template + repair tail + box-the-residual).
- **Stage 1 — PROMOZIONE**: ogni lezione unpinned piazzata su una cella template del suo corso (derivabile dalla mappa M32: cella (weekday,slot) ∈ getWeeklyTemplateCells(courseId)) diventa pinned=true; solve fasi 2-6. Lo spazio mosse si riduce a coda+box (~2-3k lezioni): fase 2 attesa PIÙ VELOCE, non più lenta.
- **Stage 2 — DEGRADO SELETTIVO**: se a fine stage 1 hard>0 o box>0: discriminatore VRP (BoxVrpFeasibilityDiscriminator, generalizzato) sul residuo → identifica gli occupanti-template bloccanti → degrada (unpin, restano quasi-pinned M32) SOLO quelli, scegliendo i MENO vincolati prima (1/|celle VRP|, stessa logica dell'eviction TASK-414 — il modello umano dai due lati) → re-solve breve. Massimo 1-2 iterazioni, poi si accetta il residuo come oggi.
- **Chiusura — SMARCATURA**: i pin promossi vanno rimossi a fine ciclo (pinned=false) senza toccare i pin AUTORITATIVI (golden/calendario/stage). SERVE UN MARCATORE PERSISTENTE che distingua le due classi (campo nuovo o registro degli id promossi): è il punto più rischioso — ricordare l'incidente TASK-383 (pin spariti) e il precedente rescheduleForTeacher (bulk SQL). MAI usare pinned=true nudo senza marcatore.

## Rischi noti da gestire

1. Box rigido: l'eviction NON può sfrattare pinned → un template pinnato può rendere il box insolubile nello stage 1 (è il caso che lo stage 2 esiste per sciogliere). Il discriminatore distingue A/B già oggi.
2. Contabilità pin (sopra). 3. M20/deficit: le celle pinnate non si adattano alla varianza per-data — la coda deve riempire attorno; verificare full-day non regredisca. 4. Doppio solve = orchestrazione ScheduleService/REST nuova (riusare i pattern reset/reschedule esistenti + il protocollo checkpoint per gli stati intermedi).

## Catena di test (localized-testing-guidelines §4)

Promozione/smarcatura: unit + real-seed (conservazione pin autoritativi: conteggi 1.785 stage/866 MODA INVARIATI prima/dopo un ciclo completo a vuoto). Stage 2: harness checkpoint (pattern BoxRepairCheckpointTest) su uno stato stage-1 catturato. Smoke locale ph2-da-snapshot per il trend. Gate di adozione: A/B laptop x2 vs baseline del treno 408 (attese: ricorrenza ~70%+, burst in discesa netta verso il golden, hard/box invariati, full-day non peggiorato, M32 residuo in crollo perché quasi tutto è on-template).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Promozione template->pinned con marcatore persistente distinto dai pin autoritativi; smarcatura a fine ciclo verificata (conteggi pin autoritativi invariati)
- [x] #2 Stage 2 degrado selettivo least-constrained-first guidato dal discriminatore, max 2 iterazioni, degrado a quasi-pinned M32 (mai a libero)
- [x] #3 Harness checkpoint per lo stage 2 + smoke ph2 locale prima del laptop
- [ ] #4 A/B laptop x2: ricorrenza finale >=70%, burst/gap-weeks in discesa verso golden, hard/box/full-day non peggiorati vs treno 408
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-25 ~15:20: SPIKE stage-1 avviato interamente in LOCALE sui checkpoint per-fase di val408 (pin on-template in harness + ph2-smoke 120s + discriminatore sul residuo) - il rischio piu' grosso (box rigido) si misura senza scrivere orchestrazione. In parallelo: anatomia dell'erosione (chi perde le celle in fase 2, verifica empirica della gradazione per scarsita') -> report task419-erosion-anatomy.

SPIKE E1 COMPLETATO IN LOCALE (2026-07-25 pomeriggio, harness Task419Stage1SpikeTest su ingresso-fase-2 val408-run1): 6.959 promossi (73% delle libere), LS 120s -> ZERO conflitti, ZERO unassigned, ZERO deadlock (ipotesi A totale: lo stage 2 su questa traiettoria non avrebbe lavoro), ricorrenza tenuta 73,8% vs tetto 52,8% del single-solve. CAVEAT DI DESIGN SCOPERTO (nuovo prerequisito): M20 e' pinned-exempt -> i promossi spariscono dal conteggio day-fill (M20 -3,23M vs -1,07M stesso grid unpinned) e la LS e' spinta a RI-riempire giorni gia' pieni; prima del laptop serve decidere la semantica di M20/M15/M18/M28 per i pin PROMOSSI (candidato: contarli come le ore Stage pinnate nel netting M20, o marcarli con flag distinto dal pinned autoritativo che le esenzioni consultano). Anatomia erosione (task419-erosion-anatomy): gradazione per-corso morde a meta' - T17 59,5% e T12 62,4% PEGGIO di tutti (corso flessibile ma docente scarso) -> secondo raffinamento candidato: peso M32 = f(scarsita' corso, scarsita' docente). Erosione sistematica (r=0,95 tra run).

BRACCIO W (notte 25/26, knob verificati attivi 300000/60/1500, M32/match x3,0 esatto): M32 TRIPLICATO non muove la ricorrenza (53,5/53,6% vs baseline 52,8/53,6%) - il tetto della penalita' e' reale e ben sotto il 73,8% del pinning (spike E1). CONCLUSIONE: la 419 serve INTERA (solve a stadi), nessuna scorciatoia di peso. Prerequisito di design invariato: semantica M20-family per i pin promossi.

2026-07-26 ~04:00: PASSO 1 avviato (Opus): registro statico promotedLessonIds + semantica conteggio/esenzione per ogni vincolo pinned-exempt (criterio: il conteggio di occupazione VEDE i promossi, l'esenzione dalla penalita' resta) + rilancio E1 come accettazione (atteso: M20 start ~-1,07M, zero deadlock, ricorrenza ~74%). Passo 2 (orchestrazione a stadi in ScheduleService + smarcatura + conservazione pin autoritativi) parte dopo. Gate finale: A/B laptop x2 con passeggero 403 (repair ph2/ph3).

PASSO 1 CONSEGNATO (2026-07-26 ~04:40, suite 764 EXIT=0, 24 verifier test nuovi): registro promotedLessonIdsStatic + gate unico isAuthoritativePin(l) = pinned && !isPromoted; regola di governo derivata dall'agente e adottata: lo score misura lo stesso orario nello stesso modo congelato o no (alla smarcatura nessun salto di punteggio) -> esteso anche a H23/H24/H25/H26 e alle esenzioni break-level M15/H17 (solo pin autoritativi). E1 post-fix: stato promosso IDENTICO cifra-per-cifra alla tabella E2 del grid non promosso (M20 -1,07M da -3,23M), zero deadlock (discriminatore vuoto), ricorrenza 73,1%, hard end -27. NOTA: hard start -148->-159 = 11 buchi veri prima nascosti dalle esenzioni sui promossi, ora onesti. Flag per il Passo 2 (dall'agente): (1) publish statics M32 PRIMA della promozione (il representative filter salta i pinned); (2) rebuild pinnedLessonTimeslotsByGroup post-promozione = scelta deliberata SI' (celle promosse escluse dal VRP della coda: sono occupate); (3) BoxRepair non evice promossi = il rischio che lo stage 2 esiste per sciogliere. NB: AC#2 spuntata parzialmente (logica degrado least-constrained e' del Passo 2; qui spuntiamo la parte semantica quasi-pinned M32-fallback gia' provata da W-arm+E1).

Correzione bookkeeping: AC#2 RIAPERTA - il degrado selettivo e' lavoro del Passo 2, non ancora implementato; la nota precedente la spuntava per errore.

PASSO 2 CONSEGNATO E REVIEWATO (2026-07-26 ~07:00): orchestrazione completa (stage A construction-only da XML reale, promozione on-template con colonna persistente promotedPin crash-safe + release al boot, stage B config esistente, stage C demozione least-constrained max 2 iter, smarcatura nel finally con assert sul SET di id autoritativi). Review 2 Opus: impianto confermato (7 punti obbligati puliti, ordering Timefold verificato nei sorgenti), 6 finding corretti - i 2 HIGH: colonna senza default DB (schema-update morto sui profili esistenti) e stagedStage invisibile a wait/capture + export a meta' ciclo che avrebbe trasformato promossi in pin autoritativi al re-import (ora 409). +unificazioni: conflicts() unico, isOnTemplateCell unico, plannableLessons() unico. Verifiche: suite 775 EXIT=0 x2 (agente+orchestratore in corso), harness stage-C ok, E1 M20 start -1,07M invariato. AC#1/2/3 spuntate (marcatore+smarcatura, degrado, harness+smoke locale); AC#4 = gate laptop. RESTA: smoke solve-staged su profilo fresco laptop (mai girato end-to-end su dati reali), poi A/B x2 vs val408 + passeggero 403.

SMOKE laptop (2026-07-26 ~11:50): FALLITO su qualita' MA con il risultato chiave della campagna dentro. VERDE: meccanica staged perfetta su dati reali (7.132 promossi, 4 demozioni C-DEMOTION-1, box 0 dopo B-SOLVE-2, autoritativi 2651->2651 intatti, promotedpin=0 a fine release), RICORRENZA UNPINNED 74,6% = TARGET >=70 CENTRATO (+22pt vs val408 52,8/53,6), full-day 79,9% NON crollato (il rischio celle-rigide non si e' materializzato). ROSSO: -897 hard dominato da H16b -895/895 (blocchi consecutivi stesso docente/materia in stanze DIVERSE): @PlanningPin congela l'ENTITA' room inclusa -> le stanze di stage A restano bloccate sui promossi e le fasi 5/6 armonizzano solo le libere (nei run normali la fase 6 porta H16b a 0). ATTRIBUZIONE: dagli artifact (box per fase ~0: le ipotesi ruin-recreate/parcheggio-null erano sbagliate; catena causale chiusa dai dump). FIX IN CORSO (locale): split dello stage B al confine timeslot/room - fasi 1-4 coi pin, smarcatura, poi fasi 5-6 (solo variabile room) senza pin: il template protegge i TIMESLOT, mai le stanze. Gate da riproporre a fix validato.

FIX SPLIT TIMESLOT/ROOM CONSEGNATO (2026-07-26 ~13:15, locale): flusso = unassign -> A -> PROMOTION -> B-TIMESLOT(-n con C-DEMOTION max 2) -> RELEASE -> B-ROOM (solo variabile room, zero pin promossi) -> release idempotente di chiusura + assert. Ancora dello split = CompactionPhase (l'XML di produzione dichiara gia' 'nothing after this moves timeslots'); trigger stage C ripulito dal room-tier (H2*/H16b/H-M2* esclusi PER FAMIGLIA, test anti-trappola su H20/H23...); guardia runtime: B-ROOM si rifiuta di partire se un promosso e' ancora tenuto (il bug H16b non puo' tornare in forma silenziosa). Il ciclo staged non usa piu' solverManager (3 SolverFactory ad-hoc sincrone, cancel via riferimento). 780 test EXIT=0 (agente), suite orchestratore in corso. Nota performance da osservare al prossimo smoke: timeslotTierHardScore = analyzeScore completo max 3x/ciclo. PROSSIMO: commit+push, poi ri-proposta gate sul laptop (smoke-bis + A/B x2) quando la FASE 2 (403) libera la macchina.

METRICA AGGIUNTA AL GATE (2026-07-26 ~16:30, da osservazione visiva di maintainer sulla UI + anatomia verificata): il full-day binario nasconde la PROFONDITA' dei deficit. Golden: 135 parziali di cui 113 a -1h e solo 7 a -3h+; smoke2 staged: 193 di cui 63 a -3h+ (41 a -4h+). Segmentazione: 55/63 sono G14(28)+G04(27) = i due caveat dati noti dove il golden CONCENTRA (giorni pieni o vuoti) e noi SPALMIAMO - e' la domanda compattare-vs-spalmare gia' pendente con la scuola, ora con evidenza quantificata; residuo NON-caveat: 5 giorni vs ~1 golden (piccolo ma reale). NUOVO CRITERIO DI GATE: giorni profondi (-3h+) nei gruppi non-caveat, da confrontare con golden ~1 e val408 (~6: G09 3, G07 1, G08 2). NB: durante il ciclo staged la web UI mostra lo stato di fine-stage (in B-TIMESLOT = grid di costruzione): l'osservazione visiva di maintainer era su quello, ma ha comunque scoperto il blind-spot della metrica sul finale.

ANATOMIA H17 COMPLETATA (2026-07-26 ~17:45, harness Task419H17GapAnatomyTest, fedele: 9 break/14 slot == analysis): meccanismo = bloccante CROSS-GROUP sul docente ex-promosso di un ALTRO gruppo (T15 in G08 blocca gli scivolamenti di G04; 7/9 gap), NON sandwich tra promossi. Tutti i 14 slot chiudibili: 5 gratis (import cross-day dalla coda libera, miss di ricerca), 2 col ramo min-slide mancante in StudentDayCompaction, 7 con 1 demozione l'uno (bill M32 901 = +0,7% del solo M32). DIFETTO STAGE C trovato: selectDemotions dichiara non-deadlock su cella conflict-free SENZA i gate caps/H17-safe di BoxRepair (la boxata A' aveva 1 sola cella conflict-free ma H17-velenosa e 49 celle H17-safe occupate solo da ex-promossi: 1 demozione la chiudeva). FIX DECISO: (1) pass hole-repair deterministico POST-smarcatura PRE-B-ROOM (room nullata come commitMove: niente pedaggio H2), politica candidati per costo: coda libera -> min-slide -> demozione; (2) gate stage C allineato a BoxRepair (conflict-free AND caps AND H17-safe). Atteso hard -15 -> 0. NOTA INFRA: export.json.gz di smoke2 CORROTTO (log al posto del JSON, stessa classe dei task406): capture-run deve validare l'export. Root-cause a monte annotato (template-builder cross-group contiguity) come eventuale lever futuro, NON necessario ora.

A/B STAGED x2 POST-FIX H16b VERIFICATO SUI RAW (2026-07-26 sera, VERDICT zai 6a2dfb62 coincide): profilo STABILE su 3 run consecutivi - ricorrenza 74,1/73,8/73,8, burst 43,0/43,4/44,3 (golden 41,6), day-start ~97, H16b ZERO ovunque. Residui = esattamente cio' che il treno locale cura: H17 9 gap/run (HoleRepair: 14->0 VERIFICATO sull'harness smoke2, livelli 6 free+5 min-slide+3 on-template), H0 2-9 boxate (gate stage-C fixato), full-day 76,8/77,1 in calo vs baseline 79,9 (i run laptop giravano col M20 FLAT: il progressivo del treno e' la cura candidata). GATE FINALE = staged + treno completo x2, attese: hard ~0, buchi interni 0, ricorrenza ~74, burst ~43, full-day risalito, giorni profondi non-caveat ~golden.

DEFERRED da /simplify+review DoD (2026-07-26, quattro angoli su tutto il lavoro del giorno; i finding applicabili sono stati applicati la sera stessa):
- A2 (altitude): estrarre una casa pubblica 'PlacementGates/GridIndex' per le primitive condivise oggi ospitate in StudentDayCompaction (occupancy/conflicts) e BoxRepair (gate H7/H11/H17, h17DayPenalty); StagedPinPolicy pesca in entrambe e slideMinIntoGap e' parcheggiato nella compattazione. Puro spostamento (~2h), da fare a gate adottato, non prima.
- A4 (altitude, MODELLO vs RICERCA): M20 e' progressivo (marginale 1000->4500) ma i repair sono shape-blind: BoxRepair.findDirectCell prende il PRIMO giorno con deficit invece del piu' profondo; HoleRepair.findImport prezza solo su M32 (max 500) mentre l'import puo' approfondire un deficit da 4500 e dopo HoleRepair nessuna LS ri-ottimizza. Fix: helper marginalDeficitCost(group,date) come chiave di ranking in entrambi (~30 righe, solo ordinamento). CAMBIA il comportamento di ricerca: merita validazione checkpoint propria, non slip-in pre-gate.
- A5 (altitude): estrazione StagedSolveOrchestrator da ScheduleService (~900 righe): solo se lo staged viene adottato.
- E5 (efficiency, minore): de-hoisting teacherId/groupId in conflicts() condiviso; trade con leggibilita', il meno importante.

GATE SERALE 2026-07-26 (val419-staged smoke2+run1+run2, HEAD efd79a21 SENZA il treno M20prog+HoleRepair) - CIFRE VERIFICATE SUI RAW (score.txt, analysis.json, KPI rilanciati localmente su lessons.txt, count pinned nei dump):
- H16b = 0 in tutti e 3 i run (fix split timeslot/room CONFERMATO live).
- Ricorrenza unpinned 74,1 / 73,8 / 73,8% (target >=70 CENTRATO; val408 52,8/53,6). Erosione template ~0: phase-2-entry 75,8% -> finale 75,4% (val408 perde 17pp).
- Burst>2: 43,0/43,4/44,3% (golden 41,6; val408 ~80). Day-start 96,7-96,9. Sharpness 83,4-83,8. Gap-weeks 1.721-1.772 (val408 ~2.350).
- Controprezzi: hard -15/-16/-22 = H17 -14/-14/-13 (9 match) + H0 -1/-2/-9; buchi interni 9gg/14-14-13 slot (coincidono 1:1 con l'hard H17); full-day 76,8-77,6 (val408 79,9/80,0).
- Meccanica: 7.132 promossi, 0 demozioni, autoritativi 2.651 conservati (contati nei dump finali), promotedpin rilasciati.
LETTURA: i controprezzi sono ESATTAMENTE i bersagli del treno gia' validato in checkpoint (HoleRepair 14->0 su smoke2; M20 progressivo per la profondita'). Batch notturno col treno completo x2 = condizione per la raccomandazione di adozione (vedi campaign-status-2026-07-26-gate419.md, sezione Raccomandazione). Ratifica adozione: maintainer.

CODE-REVIEW HIGH (2026-07-26 sera, DoD sul diff del treno completo): 6 confermati, 5 FIXATI subito (runHoleRepair ora fail-closed con invariante score-based timeslot-tier che ABORTISCE il ciclo se l'hard peggiora; HoleRepair.repair asserisce registry promozioni vuoto all'ingresso; javadoc marginale max corretto - 4500 e' la prima ora del giorno atteso VUOTO quota-8, il parziale 7-short vale 4000; le 2 copie private isOnTemplate/weightOf del test-anatomia delegano ai predicati reali; Timeslot a mano in HoleRepairTest -> TestDataBuilder). Il 6o confermato (4 mappe live parallele in commitImport tenute coerenti per convenzione, non per struttura - il fix profondo e' un RepairContext/DayIndex unico) e' lo STESSO meccanismo del deferred A2/PlacementGates gia' annotato sopra: il review lo CONFERMA e alza la priorita' di A2 a primo refactor post-adozione. 4 candidati refutati con motivazione (overflow m20DeficitCost impossibile per il min-chain openSlots<=8; re-drain donatori non puo' alzare H17; staleness republish non sfruttabile oggi; stagedStage frontend correttamente cablato).

BATTERIA M15 LOCALE COMPLETA (2026-07-26 ~22:30, 3x600s progressivo vs 3x600s flat, NON_REPRODUCIBLE, da fine-costruzione): (1) scatter M15 REFUTATO su entrambi i bracci (split non-lab P={1,3,1} F={4,0,1}; hard -155->0 sempre); (2) la curva progressiva appiattisce la PROFONDITA' a parita' di deficit totale (giorni 3..7h-short: 221 vs 277, -20%) pagando ~5pp di full-day rate (62,6 vs 67,7) e ~2pp di ricorrenza - comportamento DI PROGETTO, golden-coerente (residuo golden = solo giorni -1); (3) REVISIONE attesa gate: full-day potrebbe non recuperare/scendere, le lenti giuste del verdetto sono buchi-interni=0 + giorni-profondi in calo + deficit totale + M15/ricorrenza tenuti (dettaglio in campaign-status-2026-07-26-gate419.md, sezione Batteria). Artifact: /home/user/data/tmp/battery/{P1..P3,F1b..F3b}.{txt,log}.

BATCH NOTTURNO TRENO x2 VERIFICATO SUI RAW (2026-07-27 00:30, VERDICT 928e889b fedele su ogni riga controllata): hard 0 (run2, PRIMO zero assoluto della campagna) / -3 solo-H0 (run1); H17=0 e H16b=0 entrambi; BUCHI INTERNI 0/0 entrambi (HoleRepair live 2->0 e 4->0, zero conflitti, zero min-slide); ricorrenza unpinned 73,9/73,4 TENUTA; burst 45,9/45,9 (+2pp vs flat, watch minore); M15 11/32 (golden ~98, non esplode); M32 787/803 stabile; autoritativi 2.651 intatti (contati nei dump); demozioni 2giri/4pin (run1) vs 0 (run2), varianza non sistematica. Controprezzi: full-day 75,0-75,5 (-1,5pp), day-start -1pp, sharpness -3pp - la predizione REVISIONATA dalla batteria (profondita' giu', non full-day su) e' confermata run-per-run: giorni profondi 251->235, giorni -1 86->162. RACCOMANDAZIONE: adozione staged+treno senza riserve tecniche (scorecard sez. 1bis); ratifica a maintainer. Lever di recupero full-day in coda: 422 (DoD in corso stanotte) + vincolo anti-surplus (golden 70 vs nostri 126-136).

NOTTE 26->27/07, sequenza completa in autonomia (mandato maintainer): (1) gate treno x2 VERDE verificato sui raw (hard 0/-3, buchi 0/0, raccomandazione adozione senza riserve in scorecard 1bis); (2) TASK-422 SurplusRebalance implementata+DoD completa (checkpoint 35 mosse/-47.500 neutrale al byte, suite 814) committata 2f7ecb12; (3) TASK-423 M33 anti-surplus implementata+DoD completa (A/B checkpoint: giorni-sopra -7,8%, ricorrenza in tolleranza; identita' score provata; suite 820) committata d8cda4c3+a71f77b5; (4) TASK-424 creata (M20 pin-blind sui giorni misti, causa-radice candidata del surplus, priorita' alta); (5) run3/train2 (staged+422+M33 x2) DISPATCHATO al laptop ~01:20 CEST su HEAD a71f77b5, VERDICT-train2 atteso in mattinata, cron durable d2e5e959 di guardia (3-10 al minuto :37). RATIFICHE PER maintainer al risveglio: adozione staged+treno(-2), perimetro (con o senza 422/M33 in base a train2), TASK-420 rimozione fase 3, TASK-424 come prossima priorita' della campagna.

TRAIN2 x2 VERIFICATO SUI RAW (2026-07-27 05:10, VERDICT 9217cc61 fedele): hard 0 E 0 (prima doppietta), buchi 0/0, full-day 77,0/76,6 (RECUPERATO il costo del treno-1, parita' col flat), sopra 111/118 (da 126-136 verso golden 70), ricorrenza 73,5/73,7, burst 45,1/45,5 (giu'), M15 22/26, M32 786/793, M33 attivo 69/77 match, rebalance 16h/33h mosse 0 abort 0 de-anchor, autoritativi 2.651. PACCHETTO RATIFICA in scorecard: adozione staged+treno-2 completo senza riserve tecniche; poi TASK-420 e TASK-424 (causa-radice del residuo).

RATIFICA maintainer (2026-07-27 mattina, intervista): ADOZIONE PIENA di staged + treno-2 completo (419 + M20 progressivo + HoleRepair + A4 + 422 SurplusRebalance + 423 M33) come modalita' di solve della campagna. Argomenti del perimetro ridotto presentati e superati (improve-or-noop fail-closed = rischio asimmetrico verso il basso; benefici additivi misurati su 2 run; ricalibrazione post-424 = manutenzione ordinaria pesi). Contestualmente ratificati: TASK-420 GO (eseguita, DoD in corso), TASK-424 GO subito (golden come riferimento, senza attendere la scuola). Prossimi gap dichiarati: full-day via 424, poi cadenza fine (burst/nitidezza, leve contorno 413), domande scuola con evidenza quantificata.

ADOTTATO IN PIENO (ratifica maintainer 2026-07-27 mattina). Chiuso.

n=2 CONFERMATO (run2bis verificato sui raw laptop pre-push, 18:50): hard 0 fresco, full-day 78,5, sopra 83, burst 41,8 (~golden), ricorrenza 72,8. Pacchetto ratifica pronto: claudedocs/spalmone-2026-27/ratifica-m12-2026-07-27.md (424 adozione, 425 adozione pesi attuali, day-start residuo noto).

RATIFICA SERALE (maintainer, 2026-07-27 ~20:15): ADOTTATI anche 424 e 425 ai pesi attuali (evidenza n=2, pacchetto ratifica-m12-2026-07-27.md). Flag 430 resta OFF fino al prossimo ciclo live; 432 via libera; prossimo gate = 430-ON + 432 (+ eventuale M35=100) in UN solo A/B x2.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
