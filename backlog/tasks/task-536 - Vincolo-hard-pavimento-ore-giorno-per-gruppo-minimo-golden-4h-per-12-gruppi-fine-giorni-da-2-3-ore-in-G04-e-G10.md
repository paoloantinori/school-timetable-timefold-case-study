---
id: TASK-536
title: >-
  Vincolo hard pavimento ore/giorno per-gruppo (minimo golden: 4h per 12
  gruppi): fine giorni da 2-3 ore in G04 e G10
status: Done
assignee: []
created_date: '2026-08-20 04:44'
updated_date: '2026-08-20 04:50'
labels:
  - claude-planned
  - hard-constraint
  - golden-first
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CORREZIONE maintainer 20/08 mattina: il pavimento si deriva MATEMATICAMENTE, il golden e' solo HINT ("se il golden non ce la faceva neanche lui, forse e' davvero difficile/impossibile").

Derivazione fatta (Fedora 20/08): pavimento aritmetico = floor(pool_non_stage / giorni_in_sessione_in_grid) = 5h/giorno per 12 gruppi (4h G04, 3h G14). Il golden NON lo raggiunge: min 4h per 12 gruppi, 2h per G08 e G09 (esistenza comprovata solo al minimo golden).

SPEC a due livelli:
1. HARD FLOOR al livello di esistenza comprovata: 4h/giorno per 12 gruppi, 2h per G08 e G09 (i giorni da 2h del golden su questi due gruppi vanno spiegati per causa reale: se la causa e' input-derivabile e curabile, il floor sale e la causa si modella; se e' resa strutturale, resta 2h dichiarato). NON e' "minimo golden" usato acriticamente: e' l'esistenza comprovata della scuola, sotto il pavimento aritmetico che l'aritmetica consente.
2. ASPIRAZIONE 5h (pavimento aritmetico) DOCUMENTATA, non vincolo: la distanza 4->5 e' domanda referente ("giorni da 4h voluti o subiti?"): se voluti, resta 4; se subiti, candidato innalzamento futuro.
Derivazione per-gruppo nel codice: pool da incarichi (escluse righe Stage E righe 'Totale ore corso'/subject None), giorni da calendario (A/L, in-grid). Cura i 12 giorni inaccettabili (G04 11 giorni sotto 4h con giorni da 2h; G10 13/05 da 3h).
Prevalidazione: golden-oracle (il golden passa al floor comprovato per costruzione); Test C (pool >= floor x giorni: verificato in tabella).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Golden-oracle test: il golden non viola mai il proprio pavimento per-gruppo (min derivato per-gruppo dal golden stesso: 4h per 12 gruppi, tolleranze dove il golden scende)
- [ ] #2 Vincolo hard con pavimento per-gruppo letto da mappa derivata golden (non hardcoded nome-gruppo: derivazione dal conteggio golden, forma input-first quando referente conferma)
- [ ] #3 Run di verifica: G04 giorni da 2h/3h = 0, G10 giorno da 3h = 0, 0hard end-to-end
- [ ] #4 Sanity gate TASK-535 mostra i rossi curetati
- [ ] #5 DoD completa
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
D
## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AMEND maintainer 20/08: derivazione matematica (pool/session-days = 5h per 12 gruppi), golden solo come hint di difficolta'; floor hard = livello esistenza comprovata (4h/2h), 5h aspirazione documentata come domanda referente.
<!-- SECTION:NOTES:END -->
<!-- SECTION:NOTES:END -->

created: 2026-08-20 07:15
---
TASK-536 IMPLEMENTATO (288073c7) con i 4 CHECKPOINT del protocollo, tutti misurati. FORMA (correzione maintainer 20/08): pavimento a due livelli - math floor(pool_non_stage/giorni_sessione_alua+lab) = 5h x 12 gruppi / 4h G04 / 3h G14 e' l'ASPIRAZIONE DOCUMENTATA (domanda referente, non vincolo); HARD floor = min(4, mathFloor, goldenExistenceMin) = 4h x 12 / 2h (G08, G09) / 3h G14, derivato in DayFloorDeriver (mappa, zero nomi hardcoded; il golden entra SOLO come CAP di esistenza, garantisce l'oracle). Trappola parsing confermata e gestita: le righe 'Totale ore corso' stanno nella colonna TEACHER (subject='None') e raddoppiano i pool. CP1 VERDE: DayFloorHardConstraintTest 3/3 (tabella derivata indipendente = tabella spec; golden mai sotto il proprio pavimento). CP2 MISURA: 45 giorni sotto-pavimento a fine stage A (costruzione H33-cieca; dettaglio per gruppo nel log; coda Maggio-Giugno + venerdi' aprile G12). CP3 SONDA su export qfr3: 12/12 giorni violanti (G04 x11, G10 13/05) hanno DONATORE statico legale (docente libero, slot contiguo, lezione unpinned) -> NESSUNA cella pre-classificata, nessuna domanda referente forzata. CP4 REPLAY QFR su griglia congelata: sotto-quota 9->8 (1 mossa, lower-bound pin-noti), sotto-pavimento 12->12: il QFR NON cura l'H33 (G04 e' QFR-excluded by design) -> la cura deve venire dal solver che vede H33 hard. Suite completa 1088/0. Flag opt-out planner.constraints.day-floor.enabled (null->ON), banner in refreshSolverStatics.
---
<!-- COMMENTS:END -->

created: 2026-08-20 09:50
---
RUN DI VERIFICA h33-536 COMPLETO (947f0053, capture task536-results/h33-run1): multi-start 5 attempt, tutti doomed al gate post-RELEASE H17-dominante, pick-best = attempt 5. RISULTATO: -143hard = H17 -126 (124 celle, bacino-dipendente) + H32 -15 + H33 -2 (UNA cella: G14 07/05 ore 1 vs floor 3) + H29 ZERO (room phase completa). TARGET H33 CONSEGNAATO: giorni sotto pavimento 12 -> 1 (G14); G04 x11 e G10 x1 delle attese TUTTE chiuse; guardie improve-or-noop revertiscono correttamente i pass che peggiorerebbero H33 (HoleRepair 0->-12 revertito, nei log). Sanity 535: NON CONFORME (rossi: buchi H17 su 5 gruppi 91 gg, MODA 72 pin roomless = il backfill 530-ext non era nel jar di questo run, G14 1 giorno). Full-day 87,5. Leve 2+3 del pacchetto combinato (maxAttempts 8, portfolio.size=2) pronte come -D; il run combinato resta BLOCCATO dal gate della coda floor-aware (45->24, vedi task-533) salvo waivo di maintainer.
---
<!-- COMMENTS:END -->

created: 2026-08-20 15:40
---
RUN COMBINATO h33b-536 (72ee3f07, capture h33b-run1): floor-tail ON + maxAttempts 8 + portfolio 2, 6 tentativi (budget 4h), tutti doomed al gate (-33/-164/-93/null/-124/-133), best = tentativo 1 ripristinato. TRE RIGHE. IPOTESI: le tre leve insieme chiudono pavimento e migliorano il bacino H17. RISULTATO: PAVIMENTO 0 GIORNI SU 14 GRUPPI (target 12->0 CONSEGNAto, anche G14); H17 -22 (22 celle: G11 10/02+07/05, G13 20 venerdi' slot3 - pattern lab-day noto); H32 -11; full-day 92,5 (SOPRA il golden ex-G14 91,9); floor-tail attivo nel vivo (82/96/54/42/41 ore per costruzione). MA 0hard NON raggiunto per un DIFETTO DI WIRING, non di ricerca: -2.380.033hard = H2 room conflict 2380 coppie (CH-only rooms): il ramo TASK-498 'refining its rooms before delivery' NON e' scattato (nessun log) nell'unica configurazione che lo richiede (all-doomed + portfolio; il run h33-536 senza portfolio lo aveva eseguito, H29/H2 0). RACCOMANDAZIONE: la qualita' timeslot del combinato e' la migliore mai misurata; PRIMA di qualunque altro run serve il fix del delivery-refinement (task nuovo, riproducibile in harness dal log) e l'estensione del sanity gate a H2 (i conflitti stanza oggi non sono nel gate). Decisioni a maintainer: accettabilita' dei residui (22 buchi + 11 quota), promozione 537.
---
<!-- COMMENTS:END -->

created: 2026-08-20 15:35
---
VERDETTO FINALE - TABELLA CELLE RESIDUE DEL VINCITORE h33b-run1 (due vie: sonda statica python sull'export + replay QuotaFillRepairCheckpointTest, concordi: replay 12->11, la sonda 11/11 bloccate; TUTTI i pin dell'export sono autoritativi, 3.090, nessun promosso: il lower-bound-artifact e' escluso). H33 PAVIMENTO: 0 celle su 14 gruppi (target consegna). H17: 3 celle effettive - G11 10/02 slot2 e 07/05 slot3, G13 pattern 20 venerdi' slot3 (famiglia lab-day, cure tracciate). H32: 11 CELLE, TUTTE CON LO STESSO BLOCCANTE STRUTTURALE: le ore di riempimento stanno su giorni donatori la cui popolazione e' INTERAMENTE pin autoritativi (lab rotations / pre-assignment / calendario): nessuna ora spostabile, QFR inerte per design (filtro pinned). Tabella: G10 17/12 (6/7), 13/01 (7/8), 03/02 (7/8), 20/05 (7/8), 03/06 (7/8); G12 13/10 (4/5), 07/01 (4/5), 15/04 (7/8), 22/04 (7/8), 29/04 (7/8), 06/05 (7/8). Classificazione: DISTRIBUZIONE VINCOLATA AI PIN, non conflitto docente ne' VRP ne' budget. Le strade possibili (tutte decisione maintainer): accettazione (1h/cella, 10 celle su ~2.200 giorni-sessione); derubricare i pin lab a spostabili su queste finestre; far girare la costruzione riservando quota prima del pinning. FINE TURNO: nessuna cura avviata.
---
<!-- COMMENTS:END -->

created: 2026-08-20 21:05
---
STOP SAFETY (update corporate laptop offline ~30min, ordine maintainer 21:03). Stato turno zai post-reboot sul fix H2 delivery-refinement (WIP 9cb5d32e): (1) il difetto e' confermato essere la REFUSIONE INERTE, non il wiring (il javadoc dell'harness RoomRefinementCheckpointTest, scritto pre-reboot, corregge il verdetto h33b: la refinement RUNNO' 605s col banner nel log; il room CH fa 0 step perche' tutte le stanze sono gia' assegnate e la LS non digerisce 2380 conflitti H2). Il fix (free delle stanze non autoritative prima della refinement) e' nel WIP commit ed e' coerente. (2) Harness avviato ma NON completato: braccio control su export h33b-run1 interrotto dallo stop (primo tentativo fallito per trappola nota: SnapshotFixture.parseExport richiede .gz, l'export nel worktree e' plain json; soluzione pronta: gzip in /tmp/h33b-export.json.gz e PLANNER_TEST_ROOMREF su quello). DA FARE alla ripresa: braccio control + braccio PLANNER_TEST_ROOMREF_FREE=1, poi DoD (simplify+review), rebuild, run di verifica su profilo dedicato (floor-tail ON + maxAttempts 8 + portfolio 2, attese H32 11->0/quasi, H33 0, H17 3, 0hard, sanity 535 da DB), capture + VERDICT. Podman machine gia' avviata post-reboot. Nessun file modificato oltre questa nota (WIP gia' committato).
---
<!-- COMMENTS: END -->

created: 2026-08-21 00:00
---
HARNESS DUE BRACCI COMPLETATI (ripartenza post-update). Braccio 1 (control, export h33b-run1 .gz, niente FREE): BEFORE -2523033hard (H2 2523 match), CH room "step total (0)" (inerente, diagnosi confermata), LS 1496s/48750 step recuperano solo 143 conflitti -> AFTER -2380135 (H2 2380): RIPRODUCE il difetto live al numero esatto (2380 = la consegna h33b). Braccio 2 (FREE non-pinned, il fix WIP 9cb5d32e): FREED 9404, CH 9404 step (1248ms) MA AFTER H2 = 2380 INVARIATO: il WIP e' FALSIFICATO, tutti i conflitti residui stanno sulle stanze delle lezioni PINNATE (3090 autoritative), immovibili per @PlanningPin. Correzione: liberare TUTTE le stanze (il pin fissa il timeslot, non la stanza; il mirror corretto del released problem del B-ROOM ordinario, dove la costruzione null-a ogni stanza, pinned incluse). Fix corretto applicato a ScheduleService + variante harness aggiornata; braccio 3 (FREE=all) in corso per la prova. Entrambi i bracci 1-2 hanno chiuso al @Timeout(1500) (la LS macina su H2 immovibili): atteso che il braccio 3 chiuda molto prima (H2~0 -> unimproved 120s).
---
<!-- COMMENTS: END -->

created: 2026-08-21 00:40
---
ROOT CAUSE H2 TROVATO E FIXCATO. I 2380 H2 dell'export h33b sono quasi tutti (547 celle, 1993 lezioni) la PILA DI STUB STAGE in R01 (room id 1): `GoldenScheduleImportService.findDisplayRoomFor`, col fallback tag-less aggiunto il 20/08 per i 72 pin aula MODA (TASK-530 ext), NON esclude i corsi pseudo e assegna a ogni stub Stage la stessa prima aula generica. Pin congelati -> conflitto incurabile dalle fasi room (il javadoc TASK-530b diceva "Pseudo-Stage pins keep room=null" ma il codice lo violava). Catena: CalendarioImportService:176/183 planStagePins -> persistPinnedLesson -> findDisplayRoomFor(Stage)=R01. FIX a monte: findDisplayRoomFor ritorna null per teacher.pseudo (contratto 530b onorato; i pin aula MODA reali mantengono il fallback; sanity 535 esclude gia' Stage dalla colonna no-stanza, verificato). Il delivery-refinement torna alla forma "free NON-pinned" (la forma WIP era GIUSTA: arm2 non la falsificava, il residuo 2380 era il difetto a monte; arm3 free-ALL e' la forma respinta: azzera H2 ma spoglia anche le stanze legitime dei pin lab, R16 794 + R11 231). Test aggiunto: CalendarioImportServiceTest Stage -> null. Prossimo: DoD (simplify+review) + test.sh EXIT 0, rebuild, run verifica.
---
<!-- COMMENTS: END -->

created: 2026-08-21 09:15
---
CORNER-B TASK-498 CONFESSO E FIXCATO (segnalazione Fedora, verificata sul log h36-verify). Meccanismo: con skip-doomed-room ON, i tentativi condannati not-last consegnano stanze CH-only (~9.400 H29 room-tier di rumore nello score pieno) mentre l'ULTIMO tentativo tiene la refusione LS completa -> il pick-best su score pieno, in un run all-doomed, sceglie SEMPRE l'ultimo per costruzione. Notte h36-verify: gate per tentativo = a1 -23 (MIGLIORE), a2 -164, a4 -84, a6 -97, a7 -155; a8 (ultimo, non-giudicabile dal gate) rifinito -> full -118 batte il full -9451 di a1 SOLO per il rumore di stanze. Valgono ~90 punti hard nelle consegne all-doomed. FIX: chiave di ranking PRIMARIA = timeslot-tier hard al confine post-RELEASE (comparabile tra tentativi, calcolato gia' dal gate per ogni tentativo), tie-break sullo score pieno (latest-wins invariato); AttemptOutcome guadagna timeslotTierHard, stoccato in ScheduleService.lastAttemptBoundaryTierHard (reset a MIN_VALUE a inizio tentativo). Con il fix, in un all-doomed il pick sceglie il miglior bacino timeslot e il corner-b esistente ne raffina le stanze (con il TASK-536 free-non-pinned). Test: MultiStartRunnerTest +2 (shape h36-verify: a1 tier -23 batte a8 tier -2681 nonostante il full; equal-tier -> full-score latest-wins preservato), 8/8 verdi.
---
<!-- COMMENTS: END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
