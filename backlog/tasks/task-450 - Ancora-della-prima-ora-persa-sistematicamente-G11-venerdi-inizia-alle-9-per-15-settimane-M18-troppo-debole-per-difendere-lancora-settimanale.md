---
id: TASK-450
title: >-
  Ancora della prima ora persa sistematicamente: G11 venerdi inizia alle 9
  per 15 settimane (M18 troppo debole per difendere l'ancora settimanale)
status: Done
assignee: []
created_date: '2026-07-28 20:27'
updated_date: '2026-07-30 03:39'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 273000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
EVIDENZA (Fedora, run gate-l5-m36, verificata sui raw + nuovo scripts/analyze/day-start-anatomy.py): 18 giornate iniziano dopo le 8 contro le 4 del golden, ma NON sono sparse: 15 su 18 sono G11, sempre di VENERDI, dal 18/09 all 08/01, 15 settimane consecutive. Il tasso globale (99.2% ancorate) nasconde il difetto perche' e' una percentuale su 1954 giorni-gruppo: la forma settimanale e' invisibile alla metrica aggregata. ANATOMIA: nel golden ogni venerdi di G11 apre con Matematica/T08 alle 8 (26 venerdi, ancora fissa); nel run T08 alle 8 del venerdi insegna a G02 (34 venerdi) e G11 parte alle 9 con Educazione Civica/T23. NON E INFATTIBILITA: su tutti e 15 i venerdi almeno 4 docenti di G11 (T03, T04, T05, T24) risultano LIBERI alle 8; e' una scelta di allocazione globale della risorsa scarsa prima-ora-del-docente, cioe un fallimento di ricerca. COSTO NEL MODELLO: M18 pesa ofMedium(50) x offset, quindi la giornata in ritardo costa 50 mentre M20 (riempimento) pesa 1000/ora: nessun incentivo a spostare. AnchorRepair chiude i casi importabili (54 -> 18 in questo run) ma non questo, che richiederebbe uno scambio di ancora tra gruppi. DIREZIONI (da valutare, checkpoint-first): (a) rialzo mirato del peso M18 sul PRIMO slot (offset 1) o gradiente non lineare; (b) estensione di AnchorRepair a mosse di SCAMBIO cross-gruppo sull ancora, non solo import; (c) ancora settimanale nel template di costruzione (WeeklyTemplateBuilder deve riservare la cella (weekday, slot 0) per ogni gruppo, cosi M32 la difende); (d) allocatore per-risorsa dell ora 8 come TASK-449 fa per le stanze-tag. Il pattern e human-faithful: lo human planner fissa l ancora settimanale per ogni gruppo. METRICA: day-start-anatomy.py va aggiunto alla batteria KPI standard (capture-run.sh) perche il tasso globale da solo non e sufficiente a certificare l applicabilita.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Nessuna cella (gruppo, giorno-settimana) con scarto >= 3 giornate in ritardo rispetto al golden
- [ ] #2 G11 venerdi: 0 giornate in ritardo (golden 0)
- [ ] #3 day-start-anatomy.py nella batteria di capture-run.sh
- [ ] #4 Totale giornate in ritardo <= golden + 2
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-28 22:28
---
CORREZIONE FEDORA alla root-cause della Fase 4 (verificata sull'export gate-l5-m36, 2026-07-29 00:35): il VERDICT fase4-anchor dice 'tutti e 4 i docenti di G11 sono saturi allo slot-0 del venerdi', ma ha guardato solo i docenti GIA' schedulati quel giorno (T23, T21, T06, T12). Il gruppo ne ha altri: T03 (20h), T04 (60h), T05 (116h), T24 (20h), TUTTE non pinnate, e nessuno di loro insegna MAI a G11 di venerdi (0 date venerdi su 20/47/40/20 date distinte); il conteggio dei busy su tutti i gruppi li da' liberi allo slot 0 in tutti e 15 i venerdi. Quindi NON e' saturazione della capacita' docente: e' che l'import di AnchorRepair non trova un donatore LEGALE, il che e' un vincolo diverso (il donatore deve poter lasciare la sua giornata senza aprire un buco H17, cioe' essere una coda di giornata, e non deve creare un nuovo ritardo sulla data di origine). Il DEFER resta corretto come decisione (mandato: documenta e passa oltre, niente fix creativi), ma la direzione futura va ripensata su questa base: il candidato naturale e' un import cross-date da CODA di giornata con docente libero al venerdi slot 0 (T05/T04 hanno decine di date), non uno swap cross-gruppo. Da rivalutare anche la direzione (c) ancora settimanale nel template di costruzione, che eviterebbe il problema a monte.
---

created: 2026-07-29 07:20
---
--- FASE D (29/07, checkpoint, direzione (d) NON REGGE) --- Direzione 'donatore coda-di-giornata + docente libero + guard M18' testata con SpikeApplicabilityAnatomyTest (harness deterministico sul repair) sullo snapshot gate-finale: G11 venerdi UNCLOSABLE. Breakdown pool donatori G11 (666): teacher-busy-at-slot=396, departure-unsafe=159, VRP=111. La direzione descrive il gate attuale (isDepartureSafe=coda, conflict-free=docente libero, M18 guard) e il blocco reale NON e' l'H17-departure: e' teacher-busy (396) + VRP (111). I 111 donatori coda+liberi sono lezioni LAB escluse dal VRP della lab-quota (G11 ven ha labHours=0, H23 corretto - non un bug). Verdetto: BUTTARE (d); G11 venerdi va risolto a livello di COSTRUZIONE (direzione c: ancora settimanale nel template WeeklyTemplateBuilder, candidata SPIKE 3 a focus fresco). NESSUN codice repair modificato (mandato: documenta e fermati). VERDICT: claudedocs/spalmone-2026-27/task447-results/faseD-450/VERDICT.md --- AC #3 (day-start-anatomy nella batteria capture-run): GIA' SODDISFATTA, capture-run.sh riga 54 esegue gia' scripts/analyze/day-start-anatomy.py (tee kpi-daystart-anatomy.txt + echo scarti riga 88). --- AC #1/#2 (G11 ven 0, no cell scarto>=3): NON raggiunte da (d); restano aperte su (c).
---

created: 2026-07-29 08:46
---
CAUSA TRACCIATA (Fedora, 29/07, dopo SPIKE 3 del laptop): il ritardo NON nasce da una regola permissiva ne' dal repair, nasce da un BUCO INTERNO che la costruzione lascia allo slot 1. Catena, anello per anello, sui dump: (1) a fine costruzione G11 ha 33 giornate con slot 0 PIENO e slot 1 VUOTO, contro un massimo di 6 per ogni altro gruppo; concentrate su mercoledi (13 su 24) e venerdi (17 su 26), mentre lunedi/martedi/giovedi hanno lo slot 1 quasi sempre pieno. (2) Quel buco e' una violazione HARD di H17 (buco interno), quindi la LS deve chiuderlo. (3) La riparazione piu' economica e' una singola mossa: far scivolare la lezione dello slot 0 nello slot 1. Chiude il buco hard, lascia la giornata contigua e centra esattamente le ore attese. Le alternative sono peggiori o impossibili: riempire lo slot 1 da un'altra data non ha donatori legali (misurato in FASE D: 396 docente-occupato, 159 partenza non sicura, 111 esclusi dal VRP correttamente), togliere una lezione apre un deficit da 1000. (4) L'unica cosa che si oppone a quella mossa e' M18, che vale 50. (5) Una volta li', nessuna mossa singola puo' riportare indietro il blocco perche' tutti e quattro i docenti di quella giornata sono occupati allo slot 0 con altri gruppi: serve uno spostamento coordinato di 5 lezioni che il move set non ha. CONSEGUENZA sulla direzione del fix (maintainer: niente regole dedicate, capire il perche'): il punto di intervento e' a MONTE, nella costruzione. Regola generale, non speciale: una riga di template stampata non deve mai lasciare un buco interno, l'ammanco va in CODA alla giornata (fine anticipata, legale) e mai in mezzo (buco, hard). E' anche la forma del golden. ANELLO NON VERIFICATO, da chiudere prima di implementare: se lo slot 1 resta vuoto perche' la riga di template non ha una cella li', oppure perche' lo stamp la declina per conflitto o per cap (DayClusteredConstructionPhase:1166, contatore stampSkipConflict). Le due cause chiedono fix diversi (costruire la riga contigua contro compattare dopo lo skip). Discriminante: leggere il riferimento di template pubblicato pre-solve, o i contatori stamp del log di costruzione.
---

created: 2026-07-29 09:23
---
SWEEP PESO M18, primo giro (Fedora in locale, 29/07 11:04-11:16, mentre il laptop era senza quota). Strumento: Phase2SmokeSnapshotTest da snapshot di fine costruzione, budget 300s, NON_REPRODUCIBLE, un braccio per peso; manopola nuova planner.m18.weight (default 50, commit dedicato). Verificato che la proprieta' raggiunga la JVM forkata: 'Picked up JDK_JAVA_OPTIONS: -Dplanner.m18.weight=250' nei log (la trappola storica dei -D che restano nella JVM di Maven). TREND ONLY, fixture senza stage ne' pin: valgono solo i differenziali.

| metrica | peso 50 | peso 250 |
|---|---|---|
| giornate in ritardo | 176 | 149 (-27, -15%) |
| ricorrenza | 66,0% | 67,5% (+1,5pp) |
| full-day senza G14 | 67,5% | 66,2% (-1,3pp) |
| giornate profonde (>=2h) | 319 | 353 (+34) |

LETTURA: il compromesso registrato nel TASK-405 ('250 alza il day-start ma fa scendere la ricorrenza') NON si riproduce sul modello di oggi: a 250 la ricorrenza non peggiora, semmai migliora, coerente col fatto che nel frattempo e' difesa da M32 e dal template, assenti allora. Il prezzo esiste ma si e' spostato: si scambiano 27 inizi tardivi in meno contro 34 giornate profonde in piu', cioe' due difetti di applicabilita' l'uno contro l'altro, e la giornata profonda e' quella che il golden evita con piu' cura (82% del suo deficit sta a -1h). Quindi NON e' un miglioramento netto e la decisione non e' automatica.

CAUTELE: n=1 per braccio contro una dispersione interna storica di ~0,3pp sulla ricorrenza (batteria M15 del 26/07), quindi l'1,5pp e' piu' grande del rumore atteso ma non ancora provato; replicato in corso. La fixture non ha stage ne' pin, quindi i valori assoluti non sono confrontabili con la produzione.
---

created: 2026-07-29 09:37
---
SWEEP M18 CONSOLIDATO a n=2 per braccio (Fedora, 29/07 fino alle 11:35). Tabella completa (Phase2Smoke da snapshot, 300s, NON_REPRODUCIBLE, fixture senza stage/pin: solo differenziali):

| metrica | 50 | 50b | 250 | 250b |
|---|---|---|---|---|
| giornate in ritardo | 176 | 180 | 149 | 164 |
| ricorrenza | 66,0 | 66,2 | 67,5 | 65,4 |
| full-day senza G14 | 67,5 | 67,1 | 66,2 | 68,3 |
| giornate profonde | 319 | 325 | 353 | 320 |

LETTURA CORRETTA (il primo giro a n=1 diceva altro, e va corretto):
1. CONFERMATO: a 250 le giornate in ritardo calano davvero. I due bracci NON si sovrappongono (peggior 250 = 164 contro miglior 50 = 176), circa -22 giornate, -12%.
2. NON CONFERMATO: il guadagno di ricorrenza (+1,5pp del primo giro) era rumore. Il braccio 250 ha dispersione interna 2,1pp (67,5 contro 65,4) che si mangia la differenza; le medie sono 66,1 contro 66,5, cioe' pari.
3. NON CONFERMATO: il costo in giornate profonde (+34 del primo giro) era rumore: 319/325 contro 353/320, bracci sovrapposti.
4. REFUTATO con n=2 il compromesso storico del TASK-405: a 250 la ricorrenza NON scende. Su questo modello quel prezzo non si paga piu' (plausibile: nel frattempo la ricorrenza e' difesa da M32 e dal template).
5. OSSERVAZIONE, non finding: il braccio 250 ha varianza molto piu' alta su ricorrenza, full-day e profonde. Con n=2 e' un indizio (peso alto = piu' rimescolamento) non una conclusione.

VERDETTO: alzare M18 riduce gli inizi tardivi senza un costo misurabile, ma il campione e' troppo piccolo per escludere un costo e la varianza cresce. NON basta per adottare (l'adozione passa comunque dall'A/B staged completo). E resta il punto di merito: un peso sposta il difetto, non risolve la contesa sulla prima ora; la strada strutturale e' allocare in anticipo la risorsa scarsa, come il TASK-435 ha fatto per il docente vincolato.
---

---
COMPITO 1 + 2 (zai/laptop, 29/07, checkpoint, verifica indipendente della catena). Metodo: dump del template pubblicato (diagnostica temp in DayClusteredConstructionPhase subito dopo WeeklyTemplatePlanner.build, poi rimossa) + verifica cross-gruppo sul dump di costruzione (WeeklyTemplateConstructionRealSeedTest flag-OFF, /tmp/spike3/baseline-construction.txt).

COMPITO 1 - DOMANDA RESIDUA CHIUSA (misurato, non dal codice): il template del VENERDI di G11 e' SOLO `[0+1 Matematica]` (1 cella, slot 0). NON c'e' cella template a slot 1, quindi NESSUNO dei 4 stamp-skip si applica a slot 1 (non e' uno stamp-decline; skipConflict/cap=0 conferma). Le 6 lezioni extra del venerdi (h10-h15) arrivano dal REPAIR TAIL (`placeLessonsRoundRobin`), non dallo stamp. Riconcilia con la nota "template ha la cella, si riempie da aprile con Disegno": quel riempimento e' il repair tail che riempie slot 1 piu' tardi nell'anno (contenzione docente allentata), NON una cella template. Quindi "quale scarto tiene vuota slot 1" e' posta male: non c'e' cella da saltare.
COMPITO 1 - CAusa VERIFICATA del buco a slot 1 (contenzione risorsa scarsa prima ora): su 2026-09-18 (ven) a h9/slot1, TUTTI e 5 i docenti del venerdi di G11 insegnano ad ALTRI gruppi - T08->G02, T12->G07, T23->G01, T06->G09, T25->G04 (estratto dal dump di costruzione, non dal commento). Nessuna lezione di G11 puo' atterrare a slot 1: ogni docente candidato e' preso. Conferma indipendente del punto 2 di Fedora.

COMPITO 2 - VERDETTO: la regola generale "ammanto in coda, mai in mezzo" NON chiude il buco di G11 ven. Non e' un artefatto di ordine di piazzamento: e' contenzione HARD di docenti sulla prima ora (5 docenti su 5 presi a slot 1 da altri gruppi). Nessuna mossa singola ne' compattazione riempie slot 1. Gia' provato dalla compattazione TASK-390 esistente (buchi 158->101, questo persiste). La variante "a monte nel template" incontra la STESSA contenzione (template per-gruppo; la contenzione slot-1 cross-gruppo non e' coordinata). Quindi NON implemento una compattazione gia' dimostrata insufficiente.
SPIKE PROPOSTO (direzione di merito, da APPROVARE): allocazione ANTICIPATA della prima ora, 435-style. Generalizzare ConstrainedTeacherAllocator dal "docente vincolato" allo "slot prima-ora vincolato": pre-assegnare deterministicamente, cross-gruppo, quale gruppo ha quale docente a slot 0-1 (block 2h contiguo; il golden dà Matematica/T08 slot 0-1 a G11 ven, il run lo spezza dando T08 slot1 a G02). Risolve la contesa alla radice invece di spostare il difetto con un peso. Flag default-OFF, verdetto a checkpoint (slot0-pieno-slot1-vuoto da 46/33; guard ricorrenza 76,3% e full-day 85,5%). Coerente con SPIKE 3 (leva costruzione, non solver) e M18-sweep (peso debole: -12%).
---

created: 2026-07-30 01:06
---
ANELLO P1-vs-P2 CHIUSO AL 100% (zai, 30/07 notte). Verso dimostrato: la costruzione crea il presupposto, la prima LS (P2) esegue lo slide. Metodo: ./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest scrive target/construction-lessons.txt (fine-costruzione, seed reale). CAUTELA DICHIARATA: e' un'ALTRA ISTANZA (costruzione propria sul seed reale, NON il run 29b), quindi si confronta la FORMA (giorno-settimana, ancoraggio, presenza buco slot1) non i numeri assoluti.

G11, CONSTRUCTION-END vs P2(LS-Compactness del 29b), forma per giorno-settimana:

| wd | constr@8 | constr@9 | constr>=10 | buco@slot1 (slot0 pieno+slot1 vuoto) | P2@8 | P2@9 | P2>=10 |
|---|---|---|---|---|---|---|---|
| lun | 30/33 | 3 | 0 | 1 | 32 | 1 | 0 |
| mar | 25/25 | 0 | 0 | 1 | 33 | 0 | 0 |
| mer | 21/24 | 0 | 3 | 12 | 16 | 7 | 11 (18 late) |
| gio | 25/25 | 0 | 0 | 0 | 35 | 0 | 0 |
| ven | 26/26 | 0 | 0 | 17 | 32 | 1 | 1 |

FINDING DECISIVO: a fine costruzione i giorni difettosi (mer, ven) sono ANCORATI ALLE 8 (mer 21/24, ven 26/26 a slot0) e NON sono gia' in ritardo (constr@9=0 su mer/ven). MA hanno il BUCO allo slot 1 (slot0 pieno + slot1 vuoto): mer 12/24, ven 17/26. E' lo smoking gun: il buco@slot1 compare ESATTAMENTE sui giorni che poi vanno in ritardo, e coincide con la contenzione docente slot-1 del COMPITO 1 (tutti i docenti del giorno presi altrove). La P2 (LS Compactness) chiude quel buco HARD H17 con la mossa piu' economica, far scivolare il blocco slot0->slot1, e la giornata va a 9.

CATENA COMPLETA: costruzione -> piazza slot0 ma non slot1 (docente conteso) -> buco H17 -> P2 chiude per traslazione -> giorno slitta alle 9. La costruzione NON lascia gia' il giorno in ritardo: crea il PRESUPPOSTO (ancorato + buco), la prima LS esegue lo slittamento. Questo conferma il punto d'intervento a monte (direzione c / SPIKE): lo stamp non deve lasciare buco interno, l'ammanco va in coda alla giornata (fine anticipata, legale) non in mezzo (buco, hard).

NUANCE ONESTA (non indebolisce il verso): il buco@slot1 e' NECESSARIO ma non SUFFICIENTE. ven ha 17 buchi a fine costruzione ma in 29b P2 slitta solo 1 giorno (il difetto 29b e' su mercoledi, 18): quindi P2 dove PUO' riempie slot1, dove NON puo' (mer, contenzione piu' dura) slitta. Il verso P1->P2 regge ugualmente: il presupposto e' di costruzione, l'esecuzione e' di P2, e whether-slitta dipende dalla contenzione residua per giorno-settimana. NESSUN codice modificato (mandato documenta-e-passa).
---

created: 2026-07-30 01:07
---
REFINEMENT fedelta' (zai): nel commento #7 la tabella mostra constr@>=10=3 su mer (3 giorni GIA' in ritardo profondo a fine costruzione, front-gap di 2 slot: slot0+slot1 vuoti), mentre il testo dice 'NON sono gia' in ritardo'. Correzione: il pattern 'ancorato@8 + buco@slot1 -> P2 fa scivolare' vale per la MAGGIORANZA (mer 21/24 ancorate@8, di cui 12 col buco), ma c'e' una MINORANZA (mer 3/24) gia' in ritardo profondo a fine costruzione (>=10). Quindi la costruzione ha DUE modi di fallire la prima ora, entrambi radicati nella contenzione docente slot-0/1: (A) maggioranza: piazza slot0 ma non slot1 (buco singolo), P2 fa scivolare; (B) minoranza: non piazza neanche slot0 (front-gap 2 slot), gia' in ritardo in costruzione. Il VERSO P1->P2 (costruzione crea il presupposto, P2 esegue lo slide) resta dimostrato per il modo A, dominante. Il modo B e' causa piu' a monte (la costruzione stessa non ancora) e andrebbe tenuto distinto come caso: cambia dove agisce la leva (costruzione dello slot0, non solo chiusura del buco slot1).
---

created: 2026-07-30 02:22
---
PASSO 1 MISURA (zai, 30/07 notte, opzione A): TASK-430 slot0-balancing rimisurato ON vs OFF al CHECKPOINT di fine costruzione (WeeklyTemplateConstructionRealSeedTest), NON al solve intero come il gate2 che la bocciò. Commit cb17a2f5. Metodo: nuovo test slot0BalancingOn_measuresSlot1HoleAtConstructionEnd (flag via System.setProperty, pattern profile-following) + diagnostica measureAnchorHole eseguita sia ON che OFF. 4 numeri (seed reale H2, numeri identici pre/post refactor, ricorrenza cross-check resemblance-kpi.py):

| metrica | OFF | ON | delta |
|---|---|---|---|
| ancorate (non-G14) | 1905/1928 = 98.8% | 1899/1920 = 98.9% | piatto |
| buco@slot1 (non-G14, DECISIVA) | 53 (with-block 45) | 25 (with-block 21) | -53% |
| ricorrenza | 76.6% | 76.8% | piatto |
| stamp decline | 21.9% (skipConflict 0) | 22.0% (skipConflict 0) | piatto, non esplode |

VERDETTO = RAMO A. Il buco@slot1 CALA MOLTO (si dimezza) con 430 ON, quindi la cura c'è già e NON si prototipa un meccanismo contiguo nuovo che duplicherebbe 430 (che riserva slot0-only 1h, non blocco slot0+slot1). CAVEAT DECISIVO (fedeltà): i buchi NON spariscono, si SPOSTANO. Breakdown per gruppo (with-block): G11 31->1 (il difetto provato dell'handoff mer 12/24 + ven 17/26, UCCISO), MA G09 0->9 e G12 1->8 (nuove concentrazioni); migliorano G04 6->0, G06 4->2, G08 1->0. E' la conferma quantitativa del COMPITO 2 del commento #6 ("template per-gruppo; contenzione slot-1 cross-gruppo non coordinata"): 430 è uno scambio a SOMMA ZERO della cella slot0 tra gruppi che condividono docenti, non una cura coordinata.

PROSSIMO PASSO (mandato ramo A): rimisurare 430 sul SOLVE INTERO nello stato ATTUALE (oggi M-LF/M37/H29/AnchorRepair adoptati che al gate2 non c'erano) per testare l'ipotesi che la regressione gate2 (-hard, -full-day) fosse un conto netto negativo ALLORA ma non più. La metrica chiave: il GUADAGNO G11 controbilancia la PERDITA G09/G12 su hard/full-day/M18 finale? Se sì, 430 si ratifica (default ON); se no, la cura profonda resta TASK-451 (allocazione anticipata cross-gruppo 435-style, che coordina la prima ora invece di spostarla).

DoD (sul test, zero codice produzione): /simplify fatto (4 finding applicati), /code-review high CLEAN (zero bug correttezza, 3 minor cleanups), full-class EXIT=0 (9 test, 0 fail, 2 skip), byte-identical a flag OFF (gate riga 277 short-circuita, numeri OFF invariati).
---

created: 2026-07-30 03:39
---
PASSO 2 - SOLVE INTERO (zai, 30/07 notte, ramo A): TASK-430 slot0-balancing rimisurato sul solve intero nello stato ATTUALE (M-LF/M37/H29/H28/constrained-teacher adottati, assenti al gate2). Run 30c vs 30b, stessa JAR HEAD cb17a2f5, stesso input (fresh seed + import-calendario 866/1993 + pre-assign 231 T01), UNICA differenza slot0-balancing ON. Banner verificato pre-run (slot0-balancing=true constrained-teacher=true), 3 flag via PLANNER_JAVA_OPTS, dump per-fase presenti. Solve staged ~39min. VERDICT + tabella completa: claudedocs/spalmone-2026-27/certification-2026-07-30c/VERDICT.md.

RISPOSTA ALL'IPOTESI: REFUTATA. Il netto NON e' positivo sulle metriche di applicabilita'. I 4 AC day-start SONO soddisfatti (30c: ancoraggio 100.0%, G11 ven 0 ritardi vs 15, 0 scarto>=3, 1 ritardo totale <= golden+2): la cura day-start e' reale e pulita, il caveat zero-sum del PASSO 1 (G09/G12) era fenomeno di costruzione che le LS risolvono (al finale 0 ritardi). MA i costi squalificano l'adozione:
1. REGRESSIONE DI TIER: i 6 buchi interni (H17 HARD, -6) sono TUTTI G11 MERCOLEDI' slot1 (ore [8,10,11,12] buco [9]). Ha curato il late-start ven (M18 medium) creando un internal-hole mer (H17 hard): spostato il difetto di G11 da medium a HARD. Golden 0 buchi, 30b 0, 30c 6.
2. M15 contiguita' same-day SPEZZETTATA: 4 match -> 36 (-81600, il costo medium dominante, non anticipato al gate2). Lo shuffle slot0 non preserva l'adiacienza materia.
3. full-day ex-G14 -3.1pp (87.5% -> 84.4%), M20 -32000 (ipotesi cell-cost confirmata), M32 -20931, medium net -153880, soft -62524.
L'hard migliora (+62, -169->-107) ma INTERAMENTE per H29 room-null che crolla (163->89, effetto collaterale del ripiego stanze, NON il meccanismo slot0), offset da H0 (3->6 boxed) e H17 (0->6). CAVETE n=1 NON_REPRODUCIBLE: delta room-null/hard puo' includere varianza; M15/buchi/full-day attribuibili meccanicisticamente.

VERDETTO: NON RATIFICARE default ON (tenere OFF). Gli AC day-start passano ma il bilancio complessivo e' negativo (tier + M15 + full-day). La cura profonda resta TASK-451 (allocazione anticipata coordinata cross-gruppo 435-style). Decisione di ratifica = maintainer; adozione eventuale non sotto n>=3 run.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO PER MISURA (29/07), senza scrivere codice di fix. La diagnosi e' passata per tre ipotesi, tutte cadute con evidenza: (1) NON e' il repair, il donatore coda-di-giornata non esiste (396 docente-occupato, 159 partenza non sicura, 111 esclusi correttamente dal VRP perche' lab su giorno senza quota lab); (2) NON e' la costruzione, l'ancora c'e' gia' a fine costruzione (Matematica/T08 alle 8 su 5 venerdi verificati) ed e' la ricerca a eroderla; (3) NON e' il peso, lo sweep di M18 a n=2 per braccio mostra che 250 riduce i ritardi del 12% ma sposta il difetto e non lo risolve, e il compromesso storico del TASK-405 non si riproduce piu' (la ricorrenza non peggiora). CAUSA VERA: contesa sulla prima ora, con tutti i docenti della giornata occupati altrove; nessuna mossa singola ne' catena corta puo' uscirne (vedi TASK-451, 0/15 a profondita' 2 e 3). Lascito utile: knob planner.m18.weight (default 50, invariato), scripts/analyze/day-start-anatomy.py nella batteria di cattura, e la metrica per (gruppo, giorno-settimana) che ha reso visibile il difetto che il tasso globale al 99,2% nascondeva.
<!-- SECTION:FINAL_SUMMARY:END -->
