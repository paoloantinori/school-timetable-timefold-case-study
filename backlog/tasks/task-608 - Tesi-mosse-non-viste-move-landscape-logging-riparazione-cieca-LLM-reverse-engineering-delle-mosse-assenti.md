---
id: TASK-608
title: >-
  Tesi 'mosse non viste': move-landscape logging + riparazione cieca LLM +
  reverse engineering delle mosse assenti
status: Done
assignee: []
created_date: '2026-09-12 15:20'
updated_date: '2026-09-13 11:19'
labels:
  - claude-planned
  - esperimento
  - move-landscape
  - llm-blind-repair
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TESI (maintainer, 12/09): ci sta ancora sfuggendo qualcosa; probabilmente non stiamo consentendo agli LLM del coding agent di aiutarci a trovare la soluzione mancante perchÃ© non hanno i dati. Ipotesi specifica: ALCUNE MOSSE NON VENGONO VISTE O NON VENGONO PROVATE. Non si sa perchÃ©: forse sono mosse custom, forse i vincoli applicati alla mossa fanno scartare al solver, forse mancano proprio dal repertorio. Aspettativa: poco o niente si puÃ² fare in fase LS (metaeuristiche), molto di piÃ¹ in POST-LS: dopo che il solver consegna la sua soluzione parziale, un umano la guarda e dice "ma certo: sposta questa lÃ¬, scambia quelle tre in una mossa sola, shift orizzontale/verticale/custom". QUESTE mosse il solver non le fa.

PRECEDENTE INTERNO che rende la tesi plausibile: H28RotationFinisher (TASK-595) nacque esattamente cosÃ¬ (rotazione ovvista per un umano, assente dal comportamento del solver, meccanizzata come pass). E CompositionHoleCloser (TASK-481) documento' che la cura locale esiste ma lo shell-game la blocca: mosse singole che peggiorano l'intermedio sono il classico buco delle mosse composite.

DISegno SPERIMENTALE (tre fasi, tutto su SNAPSHOT, mai su ciclo completo):
FASE A "colpo d'occhio + move landscape": congelare la consegna braccio A del banco M39 (m39-off, [-11/0], export.json.gz + lessons.txt giÃ� catturati in task604-results/m39-off/) come dataset. Costruire il dossier: (1) stato sistema: celle H34 aperte, violazioni hard con contesto (griglia del giorno del gruppo E del docente coinvolto); (2) per ogni cella residua, ENUMERARE le mosse che i selettori attuali (change/swap/pillarChange/pillarSwap, filtri GapPreserving/Composite, VRP getAvailableTimeslots/getAvailableRooms, filtro unpinned/pinned) potrebbero produrre su quelle entitÃ , e tracciare il destino di ciascuna: mai generata / generata e filtrata (da chi) / valutata e rifiutata (delta score quanto) / accettata ma revertita da un oracle improve-or-noop. Questo risponde meccanicamente "viste? provate? scartate?".
FASE B "riparazione cieca LLM": dare a un LLM VERO ZERO-KNOWLEDGE del progetto (niente CLAUDE.md, niente nomi vincoli: solo lezioni, celle, e le violazioni espresse come dati: "questa cella dichiarata vuota ha 0 lezioni, queste sono le lezioni vicine") il dump della Fase A e chiedere mosse di scambio/spostamento per azzerare l'hard residuo. Ogni proposta va VALIDATA MECCANICAMENTE con l'evaluator offline (banco replay che ripubblica le statiche: infra esistente dei banchi live-state). Log: quali funzionano, quali no, cosa propone l'LLM che il solver non faceva.
FASE C "reverse engineering": per ogni mossa LLM vincente, classificare PERCHÃ‰ il solver non l'aveva fatta, tassonomia: (1) PRIMITIVE_PRESENT: mossa singola nei selettori, non trovata per budget/accettazione (perchÃ©?); (2) COMPOSITE_MISSING: servono 2+ mosse concatenate con intermedio peggiorativo (la classe sospetta principale: 3-cycle, rotazioni, swap multiplo); (3) FILTERED: filtrata da move filter / VRP / pinned; (4) PHASE_MISMATCH: il tipo di mossa esisteva solo in una fase gia' finita; (5) ORACLE_BLOCKED: un pass deterministico l'aveva ma un gate (improve-or-noop, sicurezza H17) l'ha revertita; (6) BUDGET: non raggiunta in tempo di LS. Consegna: tabella + verdetto sulla tesi (con smoking gun verbatim se confermata, confutazione onesta se no).

INFRASTRUTTURA DA RIUSARE: snapshot/checkpoint capture (flag planner.checkpoint.capture.enabled, default ON nei cicli di misura: il braccio A ce l'ha), banchi live-state che caricano snapshot e ripubblicano le statiche, residual ledger TASK-539.01 (fixture v132 content-addressed), capture-doctor per l'integrita'. L'LLM cieco va interrogato in una sessione pulita (nessun leak di contesto progetto nel prompt).

VINCOLI: esperimento dei prossimi giorni, NON interrompe nulla: banco M39 in volo e m-14 hanno priorita'; nessun ciclo completo laptop per questo task; nessuna modifica a solverConfig/move selectors nel perimetro di questo task (le classi mancanti diventano task figli).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 FASE A: dossier residuo+move-landscape sullo snapshot braccio A (m39-off, -11): per OGNI cella/violazione hard residua, dump di (a) contesto (griglia giorno del gruppo e del docente), (b) enumera le mosse dei selettori che la toccano, (c) per ciascuna: generata? filtrata (da quale filtro/VRP/pin)? valutata (delta score)? accettata/rifiutata? mai vista?
- [ ] #2 FASE B: riparazione cieca: un LLM SENZA conoscenza del progetto riceve solo i dati (lezioni, celle, definizioni di violazione come dati) e propone mosse; ogni proposta validata meccanicamente con l'evaluator offline (score reale); log di quali azzerano/riducono l'hard
- [ ] #3 FASE C: reverse engineering: ogni mossa LLM vincente classificata nella tassonomia PRIMITIVE_PRESENT / COMPOSITE_MISSING / FILTERED (filtro, VRP, pin) / PHASE_MISMATCH / ORACLE_BLOCKED (pass deterministico che l'aveva ma improve-or-noop l'ha revertita) / BUDGET (non raggiunta in tempo); tabella + verdetto sulla tesi
- [ ] #4 Nessun ciclo completo laptop: tutto su snapshot/replay in banco locale (direttiva ITERAZIONE SU SNAPSHOT); zero interferenze con banco M39 in volo e con m-14
- [ ] #5 Se la tesi e' confermata: la classe di mosse mancanti viene tracciata come task figlio con il candidato meccanismo (mossa composita custom / pass / filtro da rivedere), senza implementarla in questo task
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
FASE A eseguita 12/09 sera (Fedora, dataset braccio A m39-off copiato da laptop): scomposizione residuo [-11/0] = H17x3 + H32x3 + H34x3 + H0x1(peso2). Tre H34 = G06 10/06, G09 03/06, G12 16/04 (+ G13 16/06 e' FANTOMA: dichiarata in calendario_fc.csv oltre la griglia 15/06, fuori scope ovunque; domanda input). H0 = ultima ora Servizio tecnico e stilistico/T01/G02 (59/60 collocate).

SMOKING GUN COMPOSITE_MISSING (H0): VRP della boxed = 162 celle TUTTE giovedi' 8-13; G02 occupa 162/162 (intersezione libera docente+gruppo = 0). NESSUNA mossa singola legale esiste: la riparazione richiede una SEQUENZA (sgomberare una lezione G02 da un giovedi' verso altro giorno, poi piazzare la boxed), col primo passo hard-peggiorativo: l'LS non la attraversa mai. Swap non applicabile (la boxed non ha valore da scambiare).

H34 strutturali ma riparabili a distanza: G06 collocato 990 = dichiarato 990 (surplus solo 2x1h entro 21gg), G12 675=675 (ZERO surplus entro 21gg), G09 666 vs 662 (+4). Donatori legali solo a lungo raggio (>21gg): serve lo spostamento orizzontale lungo, esattamente la classe ipotizzata. EDF era armato (banner enabled=true) ma non ha chiuso: FASE C da fare sui contatori in /tmp/planner-m39off-final.log (preservato sul laptop).

H32: G02 05-05 deficit 1h = ESATTAMENTE l'ora boxed (ma 05-05 mercoledi' fuori VRP della lab; servirebbe altra lezione); G13 14/12 e 21/01 deficit 1h senza materia dovente (donor surplus necessario).

Trappola encoding scattata e corretta: nomi materia differiscono per casing fra incarichi.csv ('Lingua italiana') e lessons.txt ('Lingua Italiana'); join casefold + check su campioni (80/80, 59/60 verificati). memoria discovery-bias applicata.

FASE B lanciata: pacchetto sterilizzato /tmp/m39off/blind-packet.txt (regole R1-R7 neutrali + 4 problemi: 3 celle vuote con griglie 26gg + problema SPECIALE con griglia giovedi' G02/T01); agente cieco in volo; la validazione meccanica delle sue mosse segue a consegna (pre-check conflitti docente/gruppo/contiguita' in python; evaluator Java in Fase C).

FASE B completata 12/09 sera: agente cieco (solo pacchetto dati neutrali, zero contesto progetto) ha consegnato 16 mosse. Derivazioni indipendenti notevoli: (a) disciplina estrazione-bordo (= H17-safety) da solo; (b) strategia surplus-first (= gate E di EDF) da solo; (c) per la SPECIALE ha DERIVATO E DICHIARATO la catena di 2 mosse ('CATENA DICHIARATA: questa mossa esiste SOLO per rendere possibile la 16').

VALIDAZIONE MECCANICA (validate_blind.py, semantica vera non-Stage): tutte le 15 MOVE esistono, nessuna pinnata, ZERO violazioni H1/H3/H7 introdotte. LA CATENA SPECIALE E' VALIDA E HARD-MIGLIORATIVA (-2): H0 risolta, nessun conflitto. SMOKING GUN CONFERMATO: riparazione composita che il solver non puo' fare (nessuna mossa singola migliorativa esiste) trovata dall'LLM cieco in un passo ragionato.

CONFUTAZIONE PARZIALE onesta (le 3 celle H34): i fill dell'agente sono LEGALI ma hard-PEGGIORI sotto i pesi veri: H34 e' FLAT 1/cella mentre H32 e' 1/ora-deficit, quindi riempire una cella 4h con donatori a-quota costa +4 H32 per -1 H34 (e un fill PARZIALE da 1 ora paga comunque il deficit delle restanti: +3 -1 = peggio). Il framing del pacchetto ('giorno a zero = errore peggiore') ha guidato la strategia: colpa del pacchetto, non dell'agente. Conseguenza strutturale: le celle H34 sono quasi-strutturali SOTTO I PESI ATTUALI: l'unico fill hard-migliorativo e' quello COMPLETO da donatori SURPLUS (4/5/5 ore a lungo raggio, esistono per tutti e tre i grupri). Il solver che le lascia vuote e' miopicamente CORRETTO: non e' un fallimento del repertorio mosse, e' logistica surplus a lungo raggio (territorio EDF) + eventuale questione di disegno pesi (H34 flat vs H32 per-ora, voluto da TASK-557).

Limiti dichiarati del validatore python: non netta i pin autoritativi nella quota (surplus conteggiati G13 12-21/12-22: overcount del before-state, 5 vs 3 del solver; delta sui gruppi toccati validi), non verifica stanze (H2/H-M2/H-COPRESENCE) ne' H5 dated: la SPECIALE e' un LAB tag acconciatura (R16 unica): la verifica stanza resta alla Fase C con l'evaluator Java.

FASE C da fare: (1) contatori EDF in /tmp/planner-m39off-final.log (perche' non ha chiuso le 3 celle: gates? surplus lontani non visti? trade flat-vs-ora rifiutato da improve-or-noop?); (2) enumerazione surplus-donor completi (4/5/5 ore a lungo raggio) per le 3 celle = candidate mosse hard-migliorative SINGLE da dare all'evaluator; (3) validazione Java (stanze incluse) della catena SPECIALE; (4) classificazione finale nella tassonomia.

RUN-2 cieco (auto-rigenerato dallo stesso agente): strategia MINIMALE (5 MOVE + 1 PLACE, surplus-first rigoroso + un solo trade dichiarato per G12). Validato: tutte legali, 0 doppie-prenotazioni gruppo, destinazione sgombero (G02 10/06 h14) e' giorno dichiarato (6h) con cella libera e giorno contiguo 8-14. Catena SPECIALE riderivata in variante (sgombero 15/10 h12 -> 10/06 h14, PLACE a 15/10 h12): di nuovo COMPOSITE_MISSING trovato dal cieco. Contabilita' hard dei fill run-2: PEGGIORE di run-1 (fill parziali pagano deficit: G06 06-10 +2, G09 06-03 +3, G12 04-16 +4 e 04-06 +1): conferma che sotto i pesi veri l'unico fill redditizio e' quello COMPLETO da surplus year-wide.

FINDING qualitativo run-2: l'agente ha fatto DA SOLO l'aritmetica del deficit strutturale di finestra ('80 attivita' vs 82 dichiarate') e ha CHIESTO esplicitamente i dati mancanti (pool non piazzato, giorni oltre la finestra, occupazione docenti su giorni non-target): e' la dimostrazione from-the-inside della tesi di maintainer 'gli LLM non ci aiutano perche' non hanno i dati'. La lista delle sue richieste = la lista telemetria per il prossimo pacchetto.

Residuo validatore: la riga di conteggio doppie-prenotazioni DOCENTE e' stata rimossa per errore nel cleanup del run-2 (le doppie GRUPPO sono 0; i docenti target erano liberi per verifica pacchetto + spot-check): l'evaluator Java della Fase C resta l'autorita' finale su H1/H2/H5.

FASE C progress (notte 13/09): contatori EDF dal log braccio A (/tmp/planner-m39off-final.log): tentativo vincitore = QFR#1 14 mosse/3 celle + EDF '18 moves, 1 cell(s) closed' (13:16:19) + QFR#2 0/0. Nessuna riga REVERTED per EDF => NON oracle-blocked. Delivered H34=3 => 4 celle esistevano pre-EDF, EDF ne ha chiusa 1 e lasciato 3.

CORREZIONE dell'enumerazione (importante, onesta): la prima enumerazione surplus_fill.py permetteva prelievo ILLIMITATO dal donatore (bug: prendevo 2 ore da giorni con surplus 1, aprendo H32). Enumerazione CORRETTA surplus_fill2.py col gate E vero: yield(giorno) = collocato_nonStage - max(quota, golden_nonStage), cap per-donatore rispettato. RISULTATO: fill completo CHIUDIBILE per tutte e 3 le celle (yield year-wide legale 20/30/24 >= 4/5/5), donatori anche a lunga distanza (es. G12: donatori 05-14, 06-04, 12-10, 10-29, 10-22 verso il 04-16). La conclusione regge, i move-list della prima versione erano sbagliati, quelli della seconda sono i candidati.

VERDETTO FASE C (parziale, manca solo il perche' interno di EDF): le 3 H34 = COMPOSITE_MISSING con valle (ogni singola mossa del fill e' hard-peggiore fino all'ultima: valle +2/+1/0/-1), materiale legale PRESENTE sotto i gate di EDF stesso, EDF non oracle-blocked ma ha chiuso solo 1/4. Sospetti residui sul perche' (da risolvere col flag -Dedf.debug esistente nel codice): ordine di scansione target/donori, consumo del materiale sulla cella chiusa, gate di atterraggio interattivi, H33-family/v2-swap OFF.

Residuo -11 TOTALE classificato: H0 catena composita (-2, validata), 3 H34 fill compositi con valle (-3, materiale enumerato), 3 H32 deficit single-move da donatori surplus (da enumerare), 3 H17 gap (single-move slide, da verificare blocco docente). Pavvero teorico raggiungibile col solo repair: vicino allo zero. La validazione Java (stanze/H5/H-COPRESENCE) resta il passo autoritativo, delegata al task figlio.

FASE B-bis (13/09 pomeriggio, mandato vocale di maintainer: 'dai il puzzle completo a un LLM cieco, poi reverse engineering'): COSTRUITO E GIRATO. Riconoscimento onesto registrato: il pacchetto Fase B di ieri era stati ritagliato dalle mie primitive (le finestre NON contenevano entrambe le celle della catena vincente: 02-25 e 05-05). B-bis: puzzle COMPLETO (1084 righe: stato annuale di 6 classi, dich inline, giorni S come sessione, celle ammesse della SPECIALE, occupazione aule ACC, i 9 difetti come fatti) + verificatore meccanico con dati pieni (tutte le 14 classi per R1, stanze, VRP), baseline calibrato = ESATTAMENTE i 9 difetti + il decimo (G02 05-05) che l'agente ha SCOPERTO DA SOLO baselineando il file vuoto.

ESITO ROUND 1-2: TUTTO PULITO in 2 round, 29 mosse, TUTTI i 10 difetti chiusi, CON le danze multi-salto esattamente come previsto (3-mosse per la SPECIALE: T21->05-05@13, T26->10-23@10, SPECIALE->09-24@12; swap didattici per le H17; import incrociati per le H32). Il metodo dell'agente: deriva da solo il modello di resa ('le code rimovibili sono esattamente le ore massime dei giorni surplus') e l'ordinamento delle mosse.

ARBITRATO JAVA (step6 del banco): 28 mosse su 29 LEGALI; l'unica illegale esponeva un BUCO DI FEDELTA' DEL MIO PUZZLE, non un errore dell'agente: le attivita' CONFINATE (VRP mono-weekday) non erano rappresentate (solo la SPECIALE aveva le celle). Estratte dall'export: 8 confinate (lab T01 solo giovedi' per 1/2/G10 e G12; Tecnologia e creativita' T07 solo mercoledi' MODA; Stage G12 solo venerdi'). IMPORTANTE per l'onesta' dei verdetti Java: la mia scansione Java CONTROLLAVA il VRP (i miei donatori non includevano l'ora di T01): il too-permissive era il checker python, NON un bug del banco. Puzzle v2 con R10 + sezione confinate; checker v2; round 3 affidato allo stesso agente: chiudere il G12 senza ore confinate. Se lo dichiara impossibile = conferma indipendente dei miei verdetti strutturali; se lo chiude con un'altra danza = i verdetti cadono.

WORKFLOW CONSEGNATO (il vero deliverable di B-bis): puzzle-completo -> LLM cieco -> verificatore meccanico -> ARBITRATO JAVA (ground truth) -> correzione della fedelta' del puzzle -> iterazione. Ogni ciclo: minuti. E' la risposta operativa alla domanda di maintainer.

ROUND 3 (13/09 pomeriggio): TUTTO PULITO anche sotto R10, 30 mosse: l'agente ha chiuso il G12 con una DANZA A VINCOLO DI GIORNO (la confinata di T01 non puo' lasciare il giovedi' -> RIMBALZA su un altro giovedi' ammesso 10-15@12, liberando la cella ponte per T11 verso 04-16@10). Arbitrato Java: NUOVA respinta (Lingua Italiana G12 a 04-16@8: fuori range) che ha aperto la TERZA cipolla di fedelta': FINESTRE PER-CORSO (insiemi di date per attivita' dalle disponibilita' ricorrenti dei docenti: T04 non copre il venerdi'; T10 per G13 solo mar/ven, 47 giorni). Estratti i VRP COMPLETI di 275 corsi dall'export (vrp_by_course.json, prodotto date x ore); checker R11 con feedback azionabile (lista giorni se <=60, pattern weekday altrimenti); moves-3 sotto R11: 10 mosse violano (le gambe delle danze che usavano corsi fuori finestra). ROUND 4 in volo.

LETTURA STRUTTURALE che emerge: le finestre per-corso sono ESATTAMENTE cio' che bloccava i miei fill Java (che le rispettavano). Il loop puzzle->cieco->verificatore->Java sta CONVERGENDO verso la verita' constraint-set completa: ogni iterazione aggiunge un vincolo reale che le analisi precedenti avevano. Il round 4 decide: se le danze riescono a infilarsi nelle finestre per-corso, i difetti sono chiudibili e i miei verdetti strutturali cadono; se l'agente dichiara l'impossibile con le catene di finestre, ottengo la conferma indipendente.

RISULTATO FINALE (13/09 sera): **-11 -> 0 HARD sul solver vero**. moves-5.txt (40 mosse dell'agente cieco) arbitrate dal banco Java (step6, evaluator completo: co-presenza depth armata, stanze, VRP, cap): hard azzerato, medium -798025 -> -770810 (migliora), test VERDE. La tesi di maintainer e' provata nella forma finale: la miglior consegna pulita del quadro ratificato e' interamente riparabile a zero da un LLM che non sa NIENTE del progetto, alimentato dal puzzle completo + verificatore meccanico iterativo.

PERCORSO COMPLETO (5 round, ~90 min di agente + 5 arbitrati Java): R1-2 (29 mosse, tutti i difetti) -> Java: guscio 1, confinamenti weekday (R10) -> R3 (30 mosse, danza del rimbalzo sul giovedi') -> Java: guscio 2, finestre per-corso (R11, 10 mosse) -> R4 (36 mosse, 'nulla e' impossibile', timeslot-hard AZZERATO, resta H2 bookkeeping) -> mio pass stanze (guscio 3: famiglie aule scarse, R8b, 3 mosse) -> R5 (40 mosse, staffetta attraverso classe NON-target G04). Ogni guscio era un vincolo REALE non rappresentato nei miei dati: il loop cieco+verificatore+Java e' un RIVELATORE DI VINCOLI oltre che di soluzioni.

CADUTA DEI MIEI VERDETTI 'STRUTTURALI' (H34 DFS-esaurito, H17 teacher-locked, H32 no-donor): erano impossibilita' inferite dal pattern DENTRO la mia classe di mosse. L'agente li ha attraversati con danze multi-salto (relay a 3, sfratto cross-classe con scivolamenti a cascata, staffette di famiglie d'aula). La sua lezione di metodo, verbatim: 'sotto vincoli a piu' livelli, l'impossibilita' va dimostrata sul dato meccanico, mai sul pattern osservato'. REGOLA PERMANENTE per TASK-609 e per ogni futura analisi di raggiungibilita'.

ARTEFATTI PRESERVATI: claudedocs/spalmone-2026-27/task608-results/blind-puzzle/ (puzzle-full.txt con tutte le regole R1-R11 + tabella famiglie aule, check_moves.py verificatore completo, moves-2..5.txt la serie, vrp_by_course/confined/rooms/export_lessons/boxed_vrp i dati estratti). Il banco CompositeRepairReplay609Test step6 legge PLANNER_TEST_MOVES (default /tmp/m39off/moves-5.txt).

CONSEGUENZE IMMEDIATE: (1) TASK-609: il CompositeSwapFinisher ha un bersaglio provato: le 40 mosse SONO la specifica (le danze = le primitive: relay multi-salto, sfratto cross-classe, staffetta famiglie); reverse engineering da qui. (2) m-14: la consegna zero-hard ESISTE come '-11 + 40 mosse composite': il percorso alla milestone e' mechanizing queste danze. (3) il workflow puzzle->cieco->verificatore->Java e' promosso a STRUMENTO STANDARD per esplorare la raggiungibilita' di ogni futuro residuo.
<!-- SECTION:NOTES:END -->
