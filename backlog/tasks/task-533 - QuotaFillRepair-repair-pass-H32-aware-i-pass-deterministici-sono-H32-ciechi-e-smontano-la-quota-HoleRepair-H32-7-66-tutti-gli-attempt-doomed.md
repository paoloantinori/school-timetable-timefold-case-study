---
id: TASK-533
title: >-
  QuotaFillRepair + repair pass H32-aware: i pass deterministici sono H32-ciechi
  e smontano la quota (HoleRepair H32 -7->-66, tutti gli attempt doomed)
status: Done
assignee: []
created_date: '2026-08-19 18:25'
updated_date: '2026-08-20 04:11'
labels:
  - claude-planned
  - hard-constraint
  - search-side
  - repair-pass
dependencies: []
references:
  - /tmp/h32b-run.log (laptop)
  - claudedocs/spalmone-2026-27/task532-results/h32-run1/
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Evidenza run h32b del 19/08 sera (log /tmp/h32b-run.log sul laptop): dopo la costruzione quota-aware (afb24485), TUTTI gli attempt 1-4 del multi-start sono DOOMED al gate post-RELEASE (timeslot-tier hard -29/-156/-85/-128, soglia -2). Cause-radice dal log: (1) i repair pass deterministici sono H32-CIECHI e costruiti pre-H32: HoleRepair REVERTITO per improve-or-noop con H32 -7 -> -66, AnchorRepair REVERTITO con H32 -14 -> -21 (e H31 0 -> -26/-15 in altri revert); (2) la firma attesa della costruzione quota-aware (fine A con H32~0) NON arriva: stage A chiude a ~-9700hard e in B-TIMESLOT l'H32 residuo all'ingresso repair resta -7/-14/-23/-25; (3) B-TIMESLOT LS non chiude da sola. AMEND (reflect 19/08, regola del secondo-cerotto): due fix falliti sullo stesso sintomo = si debugga sistematicamente, non si commissionano tre cure in parallelo.

FASE 0 OBBLIGATORIA (gate, prima di qualunque codice): decomposizione dello score di fine A-CONSTRUCTION per famiglia di vincolo (H0/H17/H31/H32/altro) sul construction-lessons.txt del run h32b + il dump di un attempt del multi-start. Dice QUALE famiglia tiene il -9700. Le componenti sotto si costruiscono SOLO sulle famiglie colpevoli dalla fase 0. Questo e' anche il test di firma che diventa checkpoint (vedi AC#1).

CURA (sibling pattern, non LS e non modello), ORDINATA E CONDIZIONATA alla fase 0:
1. QuotaFillRepair deterministico post-RELEASE (ordinamento per giorno piu' sotto quota): ripara i giorni sotto-quota pescando ore dai giorni in surplus (fonte coerente con M33/SurplusRebalance), improve-or-noop valutato su TUTTI i timeslot-tier hard (H32/H31/H17/H28 inclusi). SI costruisce solo se la fase 0 mostra residuo H32 a fine costruzione o all'ingresso repair.
2. Quota-consapevolezza dei pass esistenti (Hole/Anchor/LDC/SurplusRebalance): le guardie improve-or-noop GIA' revertiscono (il danno e' impedito; il costo e' che i pass restano inerti e non riparano i loro buchi H17). Renderli H32-aware serve a renderli UTILI, non a fermarli: priorita' inferiore al punto 1, farlo solo se dopo il punto 1 i pass ancora si annullano sistematicamente.
3. Se resta residuo dopo 1+2 su piu' attempt: lista celle residue per (gruppo, data) portata a Fedora per classificazione (conflitto reale vs search), NON si tocca il modello (direttiva correttezza-vs-costo: H32 resta hard, golden-clean, tolleranza zero).

APPROCCIO A CHECKPOINT (ridurre il ciclo di validazione da ~40 min/run a secondi): i repair pass sono pass puri su una griglia congelata: si possono validare in-harness caricando l'EXPORT del run h32b all'ingresso dei pass (gia' catturato in task532-results/h32-run1/export.json.gz, o nuovo export pre-RELEASE dal vivo) e rieseguendo SOLO i pass (pattern dei *CheckpointTest esistenti, claudedocs/localized-testing-guidelines.md). Ogni iterazione su QuotaFillRepair/guardie si misura in secondi senza rilanciare il ciclo. La firma di costruzione si misura gia' a fine stage A (3s) senza aspettare B-TIMESLOT: il fallimento e' visibile al minuto 1, non al minuto 40.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Firma fine-construction: H32 residuo ≤ 5 (idealmente ~0) sul construction-lessons.txt
- [ ] #2 Repair pass esistenti (Hole/Anchor/LDC/SurplusRebalance) non peggiorano H32 nelle loro mosse (0 revert improve-or-noop dovuti a H32)
- [ ] #3 Run di verifica multi-start: 0hard end-to-end (H32 0, H31 0, H17 0)
- [ ] #4 Under-quota giorni (gruppi in scope) 63 → 0; G12 giorni ≤3h = 0
- [ ] #5 Fill-parity post-run in task532-results per confronto prima/dopo
- [ ] #6 DoD completa: /simplify, /code-review high, test.sh EXIT 0
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AMEND 19/08 sera (reflect /pa:reflect max, approvato da maintainer): aggiunta FASE 0 (decomposizione score fine-construction per famiglia) come gate obbligatorio prima di qualunque cura; componenti 1 e 2 ordinate e condizionate alla fase 0; aggiunto approccio checkpoint (export congelato + replay dei soli pass in-harness) per validare in secondi.

ADD 19/08 sera (maintainer): attivare il constraint profiling Enterprise-DIY (DRAFT-005 AC#3, harness esistente leave-one-out: ConstraintProfilingLeaveOneOutTest + filtro diag per-ID, inerte senza property) come passo di MISURA del TASK-533, dopo la fase 0 e prima del run lungo: (a) replay leave-one-out sui repair pass col checkpoint congelato (K replay in secondi, ranking costo-tempo per famiglia: se H32 domina la valutazione mosse orienta l'implementazione di QuotaFillRepair); (b) ranking tempi aggiornato H31+H32 su un run breve (la consegna DRAFT-005 AC#3 non rifatta dopo le nuove promozioni hard). Zero nuovo codice: solo property diagnostica sui run giusti.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 19:07
---
FASE 0 COMPLETA (laptop 19/08 notte): (1) DECOMPOSIZIONE fine A-CONSTRUCTION (log h32b): stage A chiude a -9731/-9937/-9829/-9834hard sui 5 attempt: la componente dominante e' la stanza non assegnata (CH-room e' inerte per disegno: ogni lezione non-pinnata e' room-null fino a B-ROOM: ~9.400 H29 + residuo timeslot-tier piccolo). La firma richiesta (H32~0 a fine A) NON e' misurabile dal solo score totale perche' H29 lo sommerge: il gate giusto e' il timeslot-tier all'ingresso repair: -29/-156/-85/-128/-? e la decomposizione dei revert dice H32 -7/-14/-21/-23/-25 e H31 -15/-26. FAMILIGLIE COLPEVOLI a valle della costruzione: H32 (residuo construction), H31 (anchor-revert collateral), H28 (-3 in un revert), H17 (i repair si annullano e non chiudono i loro buchi).
---

created: 2026-08-19 19:07
---
(2) LE 5 CELLE G10 CLASSIFICATE (H1-verifica fatta): 17/12 (6 vs 7), 14/01 (7 vs 8), 20/05 (7 vs 8), 26/05 (4 vs 5), 03/06 (7 vs 8). In TUTTE e CINQUE lo slot mancante ESISTE e 8+ docenti del gruppo sono LIBERI in quello slot (nessun blocco H1). Unplaced nel finale = 0: il fill tail non puo' agire perche' tutte le lezioni G10 sono gia' collocate ALTROVE: le ore mancanti stanno su 7 giorni in SURPLUS (17/09, 08/10, 29/10, 10/12, 18/02, 29/04: tutti 8h collocati vs quota 4-5). VERDETTO: le 5 celle sono SEARCH-SIDE pura (distribzione), nessuna da portare a Fedora: la cura e' il component 1 (QuotaFillRepair: sposta ore dai giorni surplus ai giorni sotto-quota), esattamente il caso d'uso previsto dal task.
---

created: 2026-08-19 19:07
---
(3) H17 -99: i repair pass che lo chiuderebbero (HoleRepair ha chiuso 149->88 in un attempt) si ANNULLANO per improve-or-noop che pesa H32/H31: component 2 (H32-awareness delle guardie) e' indicato DOPO il 1 perche' con QuotaFillRepair attivo il residuo H32 all'ingresso repair scende e le guardie smettono di revertire da sole.
---

created: 2026-08-19 19:07
---
PROSSIMO PASSO (componente 1): QuotaFillRepair deterministico post-RELEASE, validato col replay checkpoint (export /tmp/h32b-export.gz = griglia congelata): per (gruppo, giorno sotto-quota), sposta una lezione spostabile da un giorno surplus dello stesso gruppo nello slot mancante (gate H1/H3/H7 + VRP), improve-or-noop su TUTTI i timeslot-tier hard.
---

created: 2026-08-19 19:30
---
COMPONENTE 1 IMPLEMENTATO (3d25d6d5 + d93b485a, suite 1083/0): QuotaFillRepair deterministico post-RELEASE in solver/, innestato nel ciclo staged post-SurplusRebalance/pre-ExactFinisher con improve-or-noop su TUTTO il timeslot-tier (stessa forma dei sibling). Contratto: celle (gruppo, data) sotto quota ordinata per deficit piu' grande, donori = giorni surplus dello STESSO gruppo, gate H1 live su entrambi i lati della mossa, mosse timeslot-only, cap 500. Checkpoint test QuotaFillRepairCheckpointTest (PLANNER_TEST_QUOTAFILL=path export): replay in 4s.
---

created: 2026-08-19 19:30
---
REPLAY SULLA GRIGLIA CONGELATA (firma PARZIALE, con causa identificata): 2/7 celle chiuse, 0 conflitti. CAUSA RADICE della parzialita': l'export h32b e' lo stato CONSEGNATO, dove i pin PROMOSSI dal ciclo staged sono ancora flagged pinned=true: i 7 giorni donori di G10 sono demoliti a tutti-movability-zero dal filtro pinned del pass (corretto in vivo, dove il pass gira POST-RELEASE con la promozione rilasciata). La firma 2/7 e' quindi un LOWER BOUND del replay: la validazione vera richiede una cattura PRE-RELEASE (post-smarcatura) o il run vivo.
---

created: 2026-08-19 19:30
---
Bug trovati e curati durante il replay (il metodo checkpoint ha pagato): (1) chiave giorno groupId@date non group|date (0 mosse silenti); (2) buildGroupDaySlots mappa slot->isAuthoritativePin: la PRESENZA della chiave e' occupazione, il booleano no (2 conflitti H3 G09); (3) conteggio celle deve escludere le ore pseudo-Stage (coerenza con H32). Ogni fix validato in secondi col replay.
---

created: 2026-08-19 19:30
---
PROSSIMI PASSI: (a) cattura pre-RELEASE per la firma piena del replay (o direttamente il run vivo col pass innestato: e' gia' nel ciclo); (b) leave-one-out DRAFT-005 sul replay; (c) run lungo finale 0hard.
---

created: 2026-08-19 21:35
---
RUN VIVO qfr-run1 (1fe90cc0): il pass ha girato pulito in tutti gli attempt (prima firing live: 17 mosse/13 celle, 0 conflitti; attempt successivi 4-15 mosse, sempre 0 conflitti, mai revertito). ESITO FINALE: -38hard (H32 -31 su 26 celle, H17 -5, H28 -1, H29 -1), full-day 88,0 (record per un run con vincoli attivi). BUG live curato in corsa (14c8d9f3): la prima firing moveva su un timeslot fuori value-range del corso (IllegalStateException Timefold): ora ogni coppia (lezione, slot) e' gated su getAvailableTimeslots + H1 live.
---

created: 2026-08-19 21:35
---
CELLE RESIDUE CLASSIFICATE (26, lista a Fedora per la decisione): G12 13 giorni (10 a 4h vs 5, 2 a 7 vs 8, 1 a 6 vs 8) + G13 7 giorni (4 a 3h vs 5, 3 a 4h vs 5) + G10 5 giorni (tutti 7 vs 8). ARITMETICA G12: pool 675 == quota-sum in-grid 675 e TUTTO collocato: le 13 celle sono distribuzione pura (le ore stanno su altri giorni): search-side, curabili in linea di principio. G13: placed 978 vs quota-sum 819: il gruppo ha 159h PIU' del dichiarato: le celle 3h sono il contraltare di giorni a 6h: DISTRIBUTIONE, ma qui la quota-sum non quadra per ECCESSO (segnale input: G13 ha piu' ore del piano, coerente col task466: possibile domanda referente). G10 7-vs-8: stesso pattern distributivo.
---

created: 2026-08-19 21:35
---
NON 0hard: il residuo H32 -31 e' distribuzione search-side su 3 gruppi con donori ESAURITI o mal distribuiti al momento del pass (il pass corre UNA volta post-RELEASE per attempt). Le vie: (a) fare girare il pass anche a fine ciclo (post-B-ROOM, seconda occasione), (b) estendere il fill tail della costruzione a spostare (non solo piazzare unplaced), (c) accettare e classificare. La firma replay 2/7 e' confermata lower-bound: in vivo il pass ha chiuso 13 celle in un colpo.
---

created: 2026-08-19 21:35
---
Prossimi passi rimasti: leave-one-out sul materiale del run, fill-parity page, decisione Fedora sulle 26 celle.
---

created: 2026-08-19 21:46
---
CORREZIONE (Fedora, verifica indipendente): la mia classificazione G13 '(c) quota-sum squilibrata per ECCESSO 978 vs 819' era SBAGLIATA: includevo le ore Stage del foglio (S=300). Base corretta aula+lab (come H32): quota-sum 678 vs 679 collocate = distribuzione pura, NON contraddizione input. Le celle G13 in-scope sono 6, tutte LUNEDI' di maggio-giugno con run=0 (pool collocato in anticipo), ~31h di surplus su altri giorni donabili. Classificazione corretta: search-side distributiva anche per G13, nessuna domanda referente necessaria.
---

created: 2026-08-19 22:38
---
CALIBRAZIONE CHECKPOINT COMPLETATA (386fbbd8, doc calibrazione-checkpoint-2026-08-20.md): replay su export finale qfr-run1 replica il live 1:1 (17 mosse / 13 celle / 0 conflitti identici). Autoverifica a due vie: counter replay 33->20 e' forma NO-tolleranza (include 7 celle G09 da 1h che H32 tollera): quadra col 26 in-scope live al netto della tolleranza. REGOLA PERMANENTE REGISTRATA: pass terminale post-B-ROOM = replay valido sull'export finale DA FARE PRIMA del run (secondi vs ~2h), con avvertenza: dove i donori sono pin-promossi il replay sottostima (lower bound, causa individuabile meccanicamente).
---

created: 2026-08-19 22:38
---
CORREZIONE CLASSIFICAZIONE G13 applicata (Fedora): base aula+lab 678 quota vs 679 collocate = distribuzione pura; il mio 978 includeva le S=300. Nessuna domanda referente per G13.
---

created: 2026-08-19 22:38
---
OPZIONE (a) IMPLEMENTATA (0bb9def1, suite 1083/0): seconda occasione QuotaFillRepair post-B-ROOM (donori ridistribuiti dalla fase stanze; improve-or-noop guarda l'hard pieno incluse H2: un pass room-collidente si autosopprime). Run vivo qfr2b-519 lanciato (notte): prima firing attempt 1 = 16 mosse/11 celle/0 conflitti; run in corso.
---

created: 2026-08-19 22:38
---
CALIBRAZIONE STRUMENTO (da usare sul run di stanotte prima di qualunque decisione): replay sull'export finale del run dice in secondi se la seconda occasione post-B-ROOM chiude le residue.
---

created: 2026-08-20 00:20
---
RUN qfr3 FINALE (494e4978): -108hard (H17 -100 su 99 celle + H32 -8 su 8 celle), full-day 84,6. LA SECONDA OCCASIONE POST-B-ROOM FUNZIONA: H32 scende da 26 (qfr-run1, pass singolo) a 8: TUTTE le 7 celle G13 chiuse e 10 delle 13 di G12. Residue 8: 5x G10 + 3x G12 (tutte aprile-maggio, quasi tutte 7-vs-8 di 1h).
---

created: 2026-08-20 00:20
---
CONFRONTO -38 (qfr1) vs -108 (qfr3) per famiglia: H32 26 -> 8 (MIGLIORA con la seconda occasione, stabile come direzione); H17 5 -> 99 (PEGGIORA: bacino-dipendente: l'attempt 5 non-doomed di qfr3 porta una griglia con piu' buchi che il tentativo fortunato di qfr1). CONCLUSIONE: le componenti H32 del residuo sono STABILI (8 celle persistenti fra i run: sono i donori esauriti); la H17 e' VARIANZA di bacino, non regressione causale della seconda occasione (il pass e' timeslot-only con improve-or-noop: non puo' creare buchi).
---

created: 2026-08-20 00:20
---
Lista celle-con-bloccante FINALE (8, tutte distribuzione con donori-esausti a fine ciclo): G10 17/12 6/7, 07/01 7/8, 13/05 3/4, 26/05 4/5, 03/06 7/8; G12 15/04 7/8, 22/04 7/8, 29/04 7/8. Tutte con slot mancante esistente e contiguo (le celle usate sono 08:00 in poi): il bloccante e' l'assenza di donori surplus legali al momento della seconda occasione.
---

created: 2026-08-20 00:20
---
REBUILD FINALE eseguito (bundle index-Coz_khLa.js): fix UI 525 round2 + stanze + STAGE + CORS verificati live (200 con Origin pubblica). App fermata a fine turno. Stato: 0hard NON raggiunto; il quadro per maintainer: H32 quasi chiuso (8 celle stabili), H17 resta il nemico principale (99 in questo bacino, 5 nel precedente: bacino-dipendente), il multi-start sceglie il bacino e la delta H17 fra i run e' la variabilita' residua.
---

created: 2026-08-20 01:56
---
SPIKE PROFILING LEAVE-ONE-OUT COMPLETATO (notte 20/08, 60 braccia x 20s = 21min, checkpoint h32b congelato). RANKING (baseline 14.313 calcs/s; S9 il pavimento no-op +145): top-10 = S3 +1698, S13 +1551, S7 +1434, H28 +1301, S8 +1141, S1 +1055, H17 +1026, S16 +941, H1 +907, M12 +841. LE NUOVE HARD: H31 +675 (rank 19, 4,7% del cardio), H32 +490 (rank 27, 3,4%).
---

created: 2026-08-20 01:56
---
TRE RIGHE: IPOTESI (se H32 dominava la valutazione mosse, il QuotaFillRepair era la cura giusta da deterministic-repair) / RISULTATO (H32 costa 3,4% sopra pavimento: NON domina; H17 costa il triplo di H32 e' il vero costo hard, e i soft S3/S13/S7 pesano piu' di ogni hard) / TENERE-BUTTARE (TENERE il QuotaFillRepair: la sua giustificazione resta la chiusura delle CELLE (26->8 misurato), non il costo; BUTTARE l'idea di ottimizzare H31/H32 per performance: insieme sono ~8% del cardio, qualunque micro-ottimizzazione li' e' sposta di pgna rilevanza; il collo resta la triade soft S3/S13/S7 se un giorno servira' velocita').
---

created: 2026-08-20 03:11
---
MATTINA 20/08 FINALE: rebuild completo eseguito (l'rc=1 del rebuild.sh era il falso-negativo PID noto: BUILD SUCCESS e app HEALTHY verificati). Scoperto e corretto un disallineamento profilo: il run 'qfr3' girava in realta' sul profilo qfr2b-519 (la dir qfr3-run1 e' solo la capture): riavviato sul profilo GIUSTO, 12.494/12.494 collocate servite. Screenshots mattino (timeline + week, distinti verificati via md5) + week-round2 + compare-runs nel repo (9b18f827). App LASCESA ACCESA col run servito per la verifica visiva di maintainer. TASK-534 DoD completa di ieri (94a6c640): chiuso Done.
---

author: zai
created: 2026-08-20 04:11
---
VIA (b) IMPLEMENTATA COME PROTOTIPOPO FLAG-OFF (f0bccf1b, branch worktree-multithreaded-ls-experiment): pass quota-tail moves-placed a fine A-CONSTRUCTION (dopo round-robin + compaction, il punto-firma giusto: dentro lo stamp era inerte perche' lo stamp non accetta over-quota e i surplus nascono dopo), bordo-consapevole: donatore solo ai bordi del run (coda prima, testa solo se slot>0), target solo estensione max+1 (mai prima ora), gate H1/H3 live + VRP del corso, donatore mai sotto la propria quota, esclusioni H32 (1/G08, G14). Flag planner.construction.quota-tail-moves-placed.enabled DEFAULT OFF.
---

author: zai
created: 2026-08-20 04:11
---
TRE RIGHE. IPOTESI: spostando ore gia' piazzate alla firma (fine A) le celle sotto-quota H32 si chiudono in costruzione dove il QFR post-RELEASE arriva tardi. RISULTATO: firma OFF 41 -> ON 39 (4 mosse, 2 celle chiuse, 0 conflitti, ricorrenza 72,6% invariata): la copertura e' quasi nulla perche' il bloccante dominante e' il VRP (223 rifiuti vs 65 conflitti): le ore in surplus stanno sul giorno donatore PERCHE' il loro typing (materia quota-bound, disponibilita' docente, finestra calendario) vieta la data target. RACCOMANDAZIONE: NON armare il flag: la via (b) con regole bordo-consapevoli e' strutturalmente debole (le mosse legali sono poche e le 2 celle chiuse le chiuderebbe anche il QFR esistente); lo strumento forte per le 8 celle residue resta il QFR libero a due occasioni (gia' in produzione) + accettazione/classificazione, decisione di maintainer invariata.
---

author: zai
created: 2026-08-20 04:11
---
VALIDAZIONE (solo checkpoint come da mandato, NESSUN run live, app qfr3 non toccata): WeeklyTemplateConstructionRealSeedTest 13 run / 0 failures (flag-OFF inerte verificato: baseline stampa 41 e gli altri 12 test indenni); metrica a due vie: contatore celle sotto-quota H32 nel test (OFF 41 / ON 39) = contatore interno del pass (2 celle chiuse su griglia pre-pass 41). Dump target/construction-lessons-quotatailon.txt per KPI cross-check.
---
<!-- COMMENTS:END -->

created: 2026-08-20 09:40
---
PACCHETTO COMBINATO - GATE BLOCCANTE REGISTRATO (4bd6baff). (1) CODA FLOOR-AWARE (planner.construction.floor-aware-tail.enabled, DEFAULT OFF): secondo sweep a fine stamp che riprova ogni (gruppo, giorno) sotto-pavimento col pool ancora unplaced; recupera il meccanismo che la coda cronologica perde (le date il cui weekday non ha celle template saltano l'intera coda). Firma misurata (checkpoint, 3s): 45 -> 24 giorni sotto-floor a fine costruzione (36 piazzati, 10 celle chiuse). LE 24 RESIDUE CLASSIFICATE: 9 distribuzione-pura (gruppi con ZERO unplaced: G04 17/09-ish, G07, G10, G11, G13: le ore stanno su altri giorni, cura = movimento, fuori dal perimetro della coda by design) + 15 bloccate da VRP/day-typing col materiale in mano (G12: 40 unplaced professionalizzanti mer-gio-only non riempiono i venerdi' aprile; G05 finestre calendario; G14). GATE ~0 NON RAGGIUNTO: per protocollo promozioni NESSUN run combinato finche' il gate non e' verde o maintainer non lo waiva. (2) maxAttempts 8 e (3) portfolio.size=2 restano pronti come -D al run (wiring portfolio: size letto via Integer.getInteger nel banner, armabile senza codice). Run h33-536 attempt-1..5 (solo H33, senza coda): tutti doomed H17-dominante al gate post-RELEASE (H33 0 dopo B-TIMESLOT; le guardie revertiscono i pass che lo peggiorano, es. HoleRepair H33 0->-12 revertito).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
