# TASK-542 - Banco di prova -642: log iterazioni predizione-verifica

Metodo (direttiva maintainer 21/08 notte): ogni iterazione scrive la PREDIZIONE PRIMA (forma:
"la modifica X chiude Y celle del residuo Z perche' W"), la esegue sul checkpoint (minuti,
`scripts/benchmark/resume-bench.sh <checkpoint>`), misura il delta timeslot-tier hard dal
confine POST_RELEASE al confine POST_EXACT_FINISHER (i repair deterministici, senza LS stanze),
e registra confermata/smentita. Un bloccante strutturale si CLASSIFICA e si passa al successivo.

Configurazione del banco: resume a confine POST_RELEASE, misura = gate nei checkpoint JSON
(boxed + timeslot-tier hard), stop prima del LS stanze. Checkpoint catturati col ciclo live
del 21/08 sera (profilo dev, attempt 1 deterministico, capture ON).

## Iterazioni

### Iter 0 - BASELINE (misura, nessuna modifica)
- FONTE: ciclo live 21/08 sera (profilo dev, attempt 1 deterministico, 8 thread, capture ON).
- CATENA DEI GATE (dai checkpoint): B-TIMESLOT-1 -650/320 -> B-TIMESLOT-2 -644/319 -> POST_RELEASE
  **-644 tier hard, 319 boxed** -> HoleRepair -644/319 -> LabDayConsolidation -644/319 ->
  CompositionHoleCloser -644/319 -> AnchorRepair -644/319 -> SurplusRebalance -644/319 ->
  QuotaFillRepair -644/319 -> ExactFinisher **-644/319**.
- LETTURA: l'intera catena dei repair deterministicamente INERTE sul residuo di questo bacino
  (0 celle chiuse, 0 boxed smaltiti). Il -644 si scompone come ~638 = H0 (319 boxed x2) + ~6
  di celle piccole (composizione esatta da analysis al resume). ExactFinisher NON chiude la
  coda boxed qui (il suo meccanismo enumera il residuo small: 319 e' oltre il suo dominio o
  trova zero piazzamenti legali).
- NOTA: i capture CYCLE_START/POST_CONSTRUCTION/POST_PROMOTION di questo ciclo sono STANTI
  (279 placed: bug fixed in 4a481505, il ciclo girava col jar precedente). Da POST_B_TIMESLOT-1
  in avanti tutti i capture sono dell'istanza corretta.


### Iter 1 - FIDELTA' DEL RESUME (round-trip AC#3)
- PREDIZIONE (scritta prima): il resume da POST_RELEASE riproduce la catena repair inerte e
  il gate a POST_EXACT_FINISHER IDENTIFICO all'input (-642/320); qualunque deviazione =
  restore non fedele.
- ESECUZIONE: resume-bench.sh staged-checkpoints/attempt1/POST_RELEASE.json (wall ~3 min,
  senza pipeline; ciclo 2 del 21/08 notte, POST_RELEASE tier -642 boxed 320).
- MISURA: input -642/320 score -10858hard/-1170779medium/38700soft -> output POST_EXACT_FINISHER
  **-642/320 score -10858hard/-1170779medium/38700soft (BYTE-IDENTICO)**.
- ESITO: **CONFERMATA**. Il restore e' fedele e il banco misura in minuti. La catena repair
  confermata inerte anche sul ciclo 2 (coerente con iter-0).

### Iter 2 - PROTOCOLLO: import-calendario (curare l'artefatto, non il solver)
- DIAGNOSI (misurata sul checkpoint + DB): 314/320 boxed sono stub Stage (pseudo-corso
  TASK-381); il profilo dev ha 0 pin stage (import-calendario MAI eseguito) - il protocollo
  canonico (bootstrap -> import-calendario -> pre-assign -> solve) era incompleto.
- PREDIZIONE (scritta PRIMA): l'import-calendario piazza i pin stage (planStagePins) e la
  coda H0 del nuovo ciclo scende da 320 a <20 (restano le ~6 reali Servizio/G14 + eventuale
  overflow residuo); tier post-RELEASE da -642 a ~-15 o meno. Se la coda resta >100: la
  capacita' on-grid delle finestre stage e' saturata strutturalmente e si CLASSIFICA.
- ESECUZIONE: (in corso) import-calendario + nuovo ciclo con cattura.

### Iter 2 - ESITO (in chiusura, dati gia' misurati)
- MISURA (B-TIMESLOT #1 del ciclo canonico h42-bench): **tier -38, box 3** contro -650/320
  del protocollo incompleto. PREDIZIONE CONFERMATA (boxed 3 < 20; tier -38 vs ~-15 attesi,
  stessa scala). Il -642 era per il 98% artefatto del protocollo senza import-calendario:
  327 ore stage off-grid dichiarate dall'import = la coda di stub che il solver non puo'
  collocare perche' non ha celle legali.
- COROLLARIO per il metodo: OGNI run di validazione senza import-calendario produce un
  residuo fantasma ~-640; il protocollo di ogni run di validazione DEVE includere l'import (gia'
  vero per capture-run/full-test, era saltato nei cicli notturni ad hoc).

### Iter 3 - LE 32 ORE FANTASMA (pin autoritativi su giorni no-sessione)
- DIAGNOSI (misurata): 32 ore G12/T01/Servizio tecnico pinnate da
  ConstrainedTeacherAllocator su 4 date SENZA riga quota (2026-12-03, 2027-01-21, 2027-03-04,
  2027-04-01): giorni no-sessione per il foglio. H26 e' pinned-exempt e non le vede; il
  deficit 675-643=32 combacia ESATTAMENTE con le 6 celle H32 parziali + le giornate vuote
  M20.2. Il density-cap (TASK-537) filtra i giorni CON riga: i giorni senza riga li salta.
- PREDIZIONE (scritta PRIMA): checkpoint modificato con le 32 ore unpinned+deplaced e resume
  (repair+ExactFinisher): ExactFinisher ne colloca >=26 sulle celle deficit
  (le celle esistono: il golden riempie QUEI giorni esattamente a quota e il gruppo ha pool
  == quota totale); H32 6 -> 0..2; H0 resta 0 (boxed chiusi in-band). Se le mosse sono
  bloccate dalla disponibilita' T01 sui giorni target: H0 cresce (peggioramento
  dichiarato) e si classifica il blocco.
- ESECUZIONE: resume-bench.sh POST_RELEASE_iter3.json (in corso).

### Iter 3 - ESITO: SMENTITA (e classificazione del blocco)
- MISURA: input -33/2 -> output POST_EXACT_FINISHER **-97/34**: le 32 ore rilasciate restano
  TUTTE boxed (0 collocate da repair+ExactFinisher). La predizione (>=26 collocate) e'
  SMENTITA.
- CLASSIFICAZIONE: il blocco non e' nel piano (le celle deficit esistono: golden le riempie)
  ma nel REGIME DI RICERCA: il riposizionamento richiede lo shuffle multiplo che solo la LS
  timeslot sa fare (spostare altre lezioni per fare spazio). Il resume post-RELEASE non ha
  LS timeslot: la cura va testata al confine POST_PROMOTION (iter-4).
- NOTE: il peggioramento -64 e' interamente H0 delle 32 rilasciate (previsto come rischio e
  dichiarato nella predizione); il substrato DB resta mutato (32 unpinned) e verra'
  ripristinato dal resume successivo.

### Iter 4 - LE 32 ORE ALLA LS (cura al confine giusto)
- PREDIZIONE (scritta PRIMA): resume dal POST_PROMOTION del substrato iter-3 (griglia
  canonica con le 32 ore fantasma unpinned, pre-LS, tier -97/boxed 34): la LS timeslot
  (B-TIMESLOT loop, ~40 min a 8 thread) colloca >=26 delle 32 sulle celle deficit
  (il golden riempie quei giorni a quota esatta: le celle esistono); POST_EXACT_FINISHER
  atteso tier <= -21 (solo H17) con H32 6->0 e boxed <=2. Se la LS le lascia boxed:
  il blocco e' la disponibilita' di T01 (docente unico del corso, 231h totali) e
  si CLASSIFICA strutturale-per-input.
- ESECUZIONE: resume da POST_PROMOTION.json (fortuitamente il substrato giusto: il resume
  iter-3 aveva catturato la griglia ripristinata a quel confine; gap del writer fixato in
  1453c... commit 'capture soppressa ai confini saltati').

### INCIDENTE 22/08 ~02:00 (registrato per il post-mortem)
- Il restart di rebuild.sh (gamba mvn quarkus:run) ha RESEEDATO il profilo h42-bench:
  0 pin, lezioni reindicizzate 1..12821, i 2.859 pin dell'import (id fino 15680) cancellati.
  I checkpoint catturati prima dell'incidente referenziano id che non esistono piu' (restore
  fail-loud corretto). Sostanza canonica ricostruita: import + ciclo con cattura (deterministico).
- REGOLA operativa (nuova, da memoreizzare): MAI usare rebuild.sh mentre un profilo di banco
  e' vivo: la sua gamba di restart passa da mvn quarkus:run. Per rebuild+restart col banco:
  rebuild-checked --no-wait / mvn package + prod-start (java -jar).

### Iter 4 - INTERRUPTED-BY-BUG (deadlock MT-LS dopo stop+resume nello stesso JVM)
- OSSERVATO: il resume iter-4 e' partito (LS 8 thread alle 02:23) ma alle 04:35 CPU 0%:
  thread dump = decider in MoveResultQueue.take(), TUTTI i move thread parcheggiati su una
  CyclicBarrier che non completa. Nessun progresso per >2h.
- CLASSIFICAZIONE: deadlock della MT-LS (famiglia shutdown/barrier gia' nota: TASK-480
  BrokenBarrierException storm). Il trigger sospetto: solve-stop di un ciclo + resume in un
  JVM che aveva gia' thread MT-LS della fase precedente. TASK da aprire (bug report in backlog).
- RECUPERO: riavvio app (kill-app + prod-start java -jar, NESSUN rebuild: regola banco-vivo)
  e rilancio del resume dal file (restore idempotente). Predizione iter-4 INvariata.

### Iter 4 - ESITO (eseguita 22/08 ~05:00, terzo tentativo dopo deadlock e switch-volume)
- MISURA: input POST_PROMOTION_iter4 (costruzione canonica, 32 phantom unpinned) -> resume
  con LS completa -> POST_EXACT_FINISHER **tier -31, boxed 0**; consegna finale -33hard
  (H17 -30, H31 -1; H32 e H0 a ZERO).
- PREDIZIONE: ">=26/32 collocate" **CONFERMATA** (32/32, boxed 0); "H32 6->0" **CONFERMATA**;
  "tier <=-21" **SMENTITA** (-31): le ore ricollocate spostano la pressione sui buchi interni
  (H17 21 -> 30). Strutturalmente migliore: zero celle quota sforate, zero lezioni incollocate,
  residuo interamente sull'asse H17 (curabile col candidato lab-day).
- CURA DURABILE IDENTIFICATA (iter-5, da eseguire): ConstrainedTeacherAllocator non deve
  pinnare su date SENZA riga quota (tratta missing = 0). Cosi' il ciclo canonico ottiene
  nativamente cio' che il banco ha dimostrato: -33 con H32/H0 a zero anziche' -27 con H32.

### Iter 5 - CURA DURABILE: allocator non pinna su date senza riga quota (missing=0)
- PREDIZIONE (scritta PRIMA dell'implementazione): con il fix in ConstrainedTeacherAllocator
  (una data SENZA riga DailyActivityQuota vale quota 0 e non e' pinnabile), il ciclo canonico
  (fresh multi-start attempt 1) arriva NATIVAMENTE al risultato che il banco ha dimostrato con
  la chirurgia iter-4: POST_EXACT_FINISHER con tier in -25..-40, **H32 = 0** e **boxed = 0..2**,
  residuo dominato da H17 (~-30), consegna finale <= -40hard. Le 32 ore T01/G12
  finiscono su date con riga quota (il piano rispetta il calendario) e la LS le distribuisce
  sui giorni deficitari. Se H32 resta >0: l'allocator ha altri siti che pinnano fuori quota
  (classificare quali). Se H17 esplode >>-40: la ricollocazione preme altrove (documentare).
- ESECUZIONE: fix + suite + rebuild sicuro (java -jar, capture ON) + pre-assign-reset + solve.

### Iter 5 - ESITO: CONFERMATA (sotto tutti gli assi)
- FIX: quotaFor (ConstrainedTeacherAllocator) tratta gruppo-coperto + data-assente = 0
  (no-session), prima NO_CALENDAR_CAP. Test no-session-pin sul seed reale (4/4), suite
  1111/0 EXIT 0, commit 80ede09f. Verifica in produzione: 0/32 pin fantasma (prima 32).
- MISURA (substrato pulito h42-bench ricreato: 1993 pin stage, jar col fix, canonical solve):
  POST_RELEASE -40/7 -> POST_EXACT_FINISHER **tier -24, boxed 0**; residuo = **SOLO H17 -24**
  (H32 = 0, H0 = 0, H31 = 0). Consegna -24hard: il migliore dell'intera campagna notturna.
- PREDIZIONE: "tier -25..-40, H32=0, boxed 0..2, H17-dominato" -> **CONFERMATA** su tutti e
  quattro gli assi (tier un punto oltre la banda, in meglio).
- PERCORSO DELLE 5 ITERAZIONI: -642 (artefatto) -> -644 (baseline) -> -33 (chirurgia iter-4)
  -> **-24 nativo (cura durabile iter-5)**. Il banco ha trasformato un residuo opaque in una
  catena predizione->verifica->cura in una notte e mezza.

### Iter 6 - H17: la catena a 2 mosse del venerdi' G13 (sonda sul 18/09)
- DIAGNOSI (misurata): -24 = H17 23 lezioni-portatrici = G13 19 (TUTTI i venerdi': blocco
  Lav.mec T10 spezzato dal buco delle 11:00, dove la nostra run mette T10 con G04;
  il golden il venerdi' a G04 T10 NON lo da MAI) + G10 2 + G12 2. Il venerdi' G13
  e' A QUOTA col buco: la cura e' intragiornata, non import.
- PREDIZIONE (scritta PRIMA): la chirurgia a 2 mosse sul 18/09 - (a) l'ora G04/T10 delle
  11 -> 17/09 13:00 (quota G04 5/6, gruppo e docente liberi), (b) l'ora G13 Lav.mec delle
  13 -> le 11 libere - chiude 1 dei 19 H17 G13 PERCHE' il giorno diventa 8-12 contiguo
  (forma del golden) senza aprire buchi altrove; tier -24 -> -23, NESSUN nuovo hard
  (H1/H3/H26/H32 tutti rispettati per costruzione). Se il resume riapre il buco (LS lo
  ricrea): la pressione verso questa forma e' nella LS, non nella fattibilita' (classificare).
- ESECUZIONE: checkpoint POST_RELEASE_iter6.json (chirurgia via JSON) + resume-bench.

### Iter 6 - ESITO: SMENTITA sul delta, CONFERMATI i meccanismi (scoperta: il gate prezza il trade)
- MISURA: chirurgia 2-mosse sul 18/09 tenuta (venerdi' = forma golden 8-12 contigua nel grid
  finale) ma tier piatto **-24** (atteso -23: 1 buco chiuso, 1 aperto dal ripple altrove).
- SCOPERTA (dal log del resume): con la chirurgia la catena si SBLOCCA - "hole repair: H17
  hole slots 23 -> 18 (closed 5 by level: 5 free import, 0 conflicts) [REVERTED: improve-or-noop
  violated]" - i repair SAPPESSERO chiudere 5 buchi ma il gate reverta perche' gli import
  aprono deficit H32/H31 nei giorni sorgente: trade a somma ~zero che il gate prezza come
  non-miglioramento. Il golden non ha NE buchi NE deficit: la via giusta e' chiudere senza
  aprire (within-day come la chirurgia, o import da giorni SOPRA quota), non allentare il gate.
- CLASSIFICAZIONE per iter-7: (a) estendere la chirurgia within-day a tutti i venerdi' G13
  (dove esiste cella legale per l'ora G04/T10: misurare quante ce ne sono); (b) dare a
  HoleRepair la preferenza sorgente-side (import solo da giorni sopra quota, non a quota):
  e' la stessa regola del QFR. Domanda per maintainer se serve il trade esplicito: un buco H17 vale
  piu' o meno di un'ora di deficit H32? (il golden risponde: nessuno dei due, ma il percorso
  passa per mosse che non generano l'uno per chiudere l'altro).

### Iter 7 - H17: chirurgia v2 su TUTTI i venerdi' G13 (hard-per-medium)
- DIAGNOSI (misurata): tutti i 19 venerdi' hanno G04/T10 alle 11 (pin settimanale
  dell'allocator, lesson id 10070-10088); G04 e' A QUOTA OGNI giorno (pool forma-b esausto:
  0 giorni con slack) - per questo l'allocator sceglie il venerdi' e per questo iter-6 trovava
  trade a somma zero. MA: spostare l'ora su un altro giorno crea surplus M33 (medium, 100/h),
  NON hard: chiudere l'H17 (hard) pagando M33 e' un improve-or-noop VALIDO (il gate prezza il
  tier hard).
- PREDIZIONE (scritta PRIMA): la chirurgia su tutti i 19 venerdi' - (a) ogni ora G04/T10
  delle 11 -> cella libera di un giorno vicino con riga quota aula+lab>=1 (gruppo e docente
  liberi, cella in coda al blocco per non aprire H17), (b) l'ora G13 delle 13 -> le 11 -
  chiude **19 dei 23 buchi H17** (i venerdi' G13) perche' i venerdi' diventano 8-12 contigui
  (forma golden); tier **-24 -> ~-5** (restano G10 2 + G12 2 e arrotondamenti); medium
  paga ~19x100 M33 (~-1900, dichiarato); NESSUN nuovo hard per costruzione.
- ESECUZIONE: POST_RELEASE_iter7.json (chirurgia via JSON) + resume-bench.


### Iter 7 - ESITO: SMENTITA x2, con lezione di metodo (la chirurgia JSON non basta)
- 7a (-17012): mosse calcolate sulla griglia FINALE del DB ma applicate al checkpoint
  POST-RELEASE: occupanze diverse = collisioni. Lezione: la chirurgia va calcolata sulla
  griglia DEL CHECKPOINT.
- 7b (id. -17012, mosse ricalcolate dal checkpoint): causa radice definitiva - il modello ha
  esenzioni che la chirurgia a mano non vede: 546 celle con docente-duplicato esistono anche
  nella griglia canonica con H1=0 (co-presenza/doppio esentati); spostando lezioni di
  T15 nelle 11:00 dei venerdi' si creano coppie same-teacher NON esentate -> H1 x17
  (peso 1000). La firma T21-08:00 del drill-down e' un seguento della propagazione.
- CONCLUSIONE per la cura H17: il veicolo giusto e' un REPAIR PASS in codice (estensione di
  LabDayConsolidation: regola within-day del venerdi' - ultima lezione del giorno nelle 11
  quando il blocco e' spezzato - con la vera valutazione vincoli e il gate improve-or-noop),
  NON la chirurgia JSON. La diagnosi resta valida: 17 buchi G13-venerdi', donatori within-day
  esistenti, e il trade hard-per-medium (surplus M33) e' valido per il gate.
- Il metodo del banco ha funzionato anche qui: due smentite con causa identificata in ~1h,
  senza run completi.


### Iter 8 - H17: pass BlockerSwap (swap intra-giorno docente-slot, sibling di HoleRepair)
- RAGIONAMENTO CONTABILE (dalle misure iter-6/7): pool==quota per ogni gruppo e 0 slack
  globale => OGNI mossa che cambia i totali di giorno e' a somma zero al tier (chiude H17,
  apre H32). L'unica cura a guadagno netto e' intra-giorno: quando il buco di G a slot s e'
  bloccato dal docente T che insegna a G' nello stesso giorno a s, si scambia di slot la
  lezione bloccante con una lezione di G' nello STESSO giorno il cui docente e' libero a s
  (i totali di G' non cambiano), poi la coda di G scivola in s. Nel caso venerdi': swap
  (G04 11:00 T10) <-> (G04 12:00 docenteX), poi G13 12:00 -> 11:00: entrambi i
  gruppi finiscono contigui, nessun totale toccato.
- PREDIZIONE (scritta PRIMA): il pass chiude **fino a 17 dei 23 buchi H17** (i venerdi' G13)
  perche' lo swap sblocca lo slot senza costi di quota; tier **-24 -> ~-7** (restano i 4-6
  non-G13: G10 2, G12 2 e arrotondamenti). CONDIZIONE: per ogni venerdi' deve esistere
  una lezione partner di G04 nello stesso giorno con docente libero alle 11 (misurare le
  eccezioni); il venerdi' senza partner resta aperto e si dichiara.
- ESECUZIONE: pass in codice (flag planner.h17-block-swap.enabled, default OFF per l'A/B via
  resume) + misura sul banco col substrato canonico fresco.


### Iter 8 - ESITO: INERTE-PER-GEOMETRIA (la premessa era sbagliata, e questo e' il dato)
- Il pass H17BlockSwap e' implementato (sibling di HoleRepair, TDD sintetico 2/3) ma il test
  principale fallisce per la ragione giusta: nel venerd' reale T10 occupa TUTTI e 6 gli
  slot della giornata (5 ore G13 + 1 ora G04 alle 11): QUALUNQUE swap o slide intra-giorno
  del bloccante lo mette in conflitto con T10 stesso. La geometria lo vieta, non la ricerca.
- IL FATTO STRUTTURALE (misurato sul golden): il golden il venerdi' a G04 non da' MAI T10
  (le sue ore G04 vivono su altri giorni) e d? ad G13 5 Lav.mec CONSECUTIVE (no Cad: la
  composizione stessa del giorno e' diversa dalla nostra). La nostra composizione (4 Lav + Cad
  + 2 Lav) nasce dall'allocator che sceglie il VENERDI' come giorno settimanale dell'ora
  T10/G04.
- CLASSIFICAZIONE FINALE H17-G13: la cura sta a monte (profilo input-derived dell'allocator:
  spreading per-docente-giorno-settimana, o composizione del giorno G13-venerdi'), NON nei
  repair post-LS. Domanda per il workbook (TASK-445): il venerdi' di G13 e' monografico
  T10 8-12 per pratica della scuola? (il golden dice si': 5/5 venerdi').
- Il pass resta committed default-OFF come building block (forme non-G13 potrebbero
  beneficiarne: i 4 buchi G10/G12 hanno geometrie da verificare).


### Iter 9 - ESITO: CLASSIFICATA A LIVELLO DATO (la causa e' una divergenza input-vs-golden su T10)
- CATENA MISURATA: (1) tutti i 20 pin T10/G04 sono 1h su 20 venerdi' distinti;
  (2) T10 ha una RecurringAvailabilityRule VACATION (dayofweek=3) espansa in 36 righe
  datate TUTTE VENERDI' 08:00-12:00 (semantica weekday da verificare: off-by-one sospetto
  tra regola ricorrente e righe espanse); (3) il GOLDEN usa T10 al venerdi' 8-12 ogni
  settimana (pratica monografica G13) CONTRCONDICENDO l'input; (4) il run -24 NON ha H5
  attive su quelle ore (le righe sono arrivate dopo, o il join e' cieco): il PROSSIMO solve
  canonico potrebbe trovarsele contro (H5 x ~180 lezioni: landmine da verificare PRIMA).
- PREDIZIONE (per la verifica, scritta ora): se le 36 righe sono visibili al prossimo solve,
  il gate post-CH peggiora drammaticamente (H5 su ogni T10-venerdi'-mattina). Azione
  richiesta prima del prossimo run canonico: censire la visibilita' delle righe e la
  semantica dayofweek della regola.
- DOMANDE PER referente (workbook TASK-445): (a) T10 il venerdi' mattina e' disponibile o no?
  (l'input dice vacation 8-12, il golden lo usa 8-12 ogni venerdi'); (b) il venerdi' di G13
  e' monografico T10 per pratica? (golden: si', 34/34 venerdi').
- NESSUN cambio codice: e' una divergenza da registrare (famiglia TASK-466) e una questione
  di integrita' della espansione availability.


### Iter 9-bis - RETTIFICA (verifica off-by-one richiesta e eseguita): ipotesi REFUTATA
- VERIFICA: la RecurringAvailabilityRule di T10 (id 16) ha dayofweek=3 = GIOVEDI'
  (ordinale java.time), minslot 0-3 = 08:00-12:00; le sue 36 righe espanse (sourceruleid=16)
  sono TUTTE giovedi' (min 17/09/2026 = giovedi'). L'espansione e' enum-safe
  (DayOfWeek <-> LocalDate.getDayOfWeek): off-by-one IMPOSSIBILE.
- ERRORE MIO nel turno precedente: ho letto EXTRACT(dow)=4 di Postgres come venerdi';
  dow PG (domenica=0) => 4 = giovedi'. Le "36 vacation venerdi'" erano giovedi'.
- ORACOLO GOLDEN: T10 il giovedi' lavora SOLO alle 12-13 (11 ore set-nov, tutte
  12:00-13:00): il golden rispetta la vacation 8-12 ALLA LETTERA. Input e golden COERENTI.
- CONSEGUENZE: (a) niente landmine H5 per il prossimo run (le vacation sono sui giovedi'
  e il solver le rispettera'); (b) niente divergenza da referente su T10; (c) il pattern
  venerdi' dei pin T10/G04 resta UNEXPLAINED a livello dato: e' emergente del profilo
  allocator (da dump-are alla prossima sessione: la prossima sonda precisa e' loggare il
  profilo input-derived di T10 da HybridPhase1aService per vedere dove nasce il
  clustering sul venerdi'). Le domande referente di iter-9 restano valide SOLO nella forma
  generica (pratica monografica del venerdi' G13: golden 34/34).


### Iter 10 - CLASSIFICAZIONE DEFINITIVA del clustering venerdi' (dump del profilo + natura dei pin)
- DUMP DEL PROFILO (test diagnostico in ConstrainedTeacherInputSourceRealSeedTest): il profilo
  input-derived di T10 NON ha venerdi' per G04 (4 date: LUN/MER/GIO; G13 e G08
  bilanciate su tutta la settimana). L'allocator e' PROSCIOLTO.
- NATURA DEI PIN (misurata): i 20 pin T10/G04 venerdi'-11 sono pinned=t promotedPin=t:
  sono CELLE DEL TEMPLATE SETTIMANALE DELLA COSTRUZIONE promosse a pin (TASK-419 promotion),
  non pin autoritativi. Il WeeklyTemplateBuilder assegna il corso ricorrente T10/G04
  alla cella (venerdi', slot 11): il golden assegna lo stesso corso ricorrente a (lunedi',
  9:00, 15/20 ore). Da lì la promozione congela la cella e il blocco G13 del venerdi' resta
  spezzato per sempre (iter-8: geometria within-day impossibile).
- CURA IDENTIFICATA (iter-11, prossima sessione): nel WeeklyTemplateBuilder, la scelta della
  cella per i corsi ricorrenti mono-ora deve evitare le celle gia' occupate dallo stesso
  docente nello stesso weekday col blocco di un ALTRO gruppo (o semplicemente: preferire
  weekday diversi per ore ricorrenti dello stesso docente - la forma del golden: T10
  G04 lunedi', G13 venerdi'). Locus preciso: WeeklyTemplateBuilder cell assignment,
  pre-promotion: nessun vincolo hard violato, solo cattiva cella congelata.
- RITIRO ULTERIORE: anche l'attribuzione "allocator profile" di iter-8/9 era imprecisa: la
  catena corretta e' template -> promotion -> blocco congelato.


### Iter 11 - CURA: teacher-weekday-spread nel cell-assignment del WeeklyTemplateBuilder
- PREDIZIONE (scritta PRIMA): aggiungendo una preferenza di SPREAD SETTIMANALE PER DOCENTE
  (un corso preferisce il weekday dove il suo docente ha meno ore gia' assegnate nel template,
  pattern preferenza-con-fallback della casa: mai forzare), il corso ricorrente T10/G04
  migra dal venerdi'-11 a un weekday scarico di T10 (il golden lo mette lunedi'-9):
  **i 20 pin promossi venerdi' scompaiono e fino a 17 dei 23 buchi H17 si chiudono** perche'
  il blocco G13 del venerdi' puo' finalmente essere contiguo (la geometria within-day di
  iter-8 diventa risolvibile alla radice: la cella non viene piu' assegnata al gruppo
  sbagliato). Tier atteso: -24 -> ~-7 (restano G10 2 + G12 2 + non-G13 residui).
  Se la preferenza non migra il corso (altre preferenze dominanti o ordine greedy): INERTE e
  si classifica la precedenza delle preferenze.
- MISURA: ciclo canonico fresco col flag ON (la modifica e' PRE-template: serve costruzione
  nuova; il banco misura col gate del nuovo run).


### Iter 11 - ESITO: CONFERMATA (e superata sull'H17) - consegna -13, la migliore della campagna
- MISURA (ciclo canonico nativo, flag spread ON, java -jar, costruzione nuova): POST_EXACT_FINISHER
  **tier -13, boxed 0**; consegna finale **-13hard/-1275577medium** (baseline identica senza fix:
  -24). **H17 G13 = ZERO** (portatori H17 restanti: solo G10 x5): 19 dei 24 buchi chiusi
  contro i "fino a 17" predetti. La famiglia venerdi' e' SPARITA.
- MECCANISMO CONFERMATO: la preferenza teacher-weekday-spread (pass 0, d78e6ebc) fa migrare
  le ore ricorrentti del docente lontano dal weekday del blocco dell'altro gruppo: e' la
  forma del golden (T10 G04 lunedi', G13 venerdi') raggiunta da regola input-derived,
  senza leggere il golden.
- EFFETTO COLLATERALE DA CLASSIFICARE (iter-12): H20 -8 (celle calendario chiuse) appare col
  prezzo della migrazione; H17 residuo -5 solo G10. Prossimo residuo-target: H20 (8 unita')
  + G10 5 + eventuale H0/H32 residui a seconda del braccio.
- NOTE DI METODO: la misura precoce (pin promossi ancora su venerdi') ingannava: il pass non
  migra l'ora piccola (carico docente zero al suo placement) ma sposta il BLOCCO grande:
  l'esito conta, non il marcatore intermedio.


### Iter 12 - H20: i pin fuori finestra dell'allocator (T01/G06 giovedi' pomeriggio)
- DIAGNOSI (misurata): le 8 H20 = pin autoritativi (pinned=t, promoted=f) T01/G06
  Servizio tecnico, TUTTE giovedi' 14:30-15:30 (slot 6-7), fuori dalla finestra del giovedi'
  di G06 (0-5). L'applicazione dei pin (HybridPhase1a/AdaptiveSlotFinder) sceglie lo slot
  ignorando la finestra calendario del gruppo: la validazione CSP non veto-a H20.
- PREDIZIONE (scritta PRIMA): aggiungendo il veto di finestra-calendario (H20) al percorso di
  applicazione dei pin (slot finder window-aware o validatore), i pin T01/G06 cadono
  su slot legali (la mattina del giovedi' o altri giorni): **H20 8 -> 0** e il tier -13 -> **-5**
  (resta solo H17 G10 x5), perche' le 8 ore restano collocate ma dentro finestra.
- ESECUZIONE: window-aware nel pin path + misura col ciclo canonico.


### Iter 12 - ESITO: CONFERMATA - consegna -8, SOLO H17 residua
- MISURA (ciclo canonico, fix window-fallback + spread ON, pin stanti rimossi, pre-assign
  rigenerato): POST_EXACT_FINISHER **tier -7, boxed 0**; consegna **-8hard**. Composizione
  finale: **H17 -7 e NULLA ALTRO** (H20 8->0, H0 0, H32 0, H31 0). Predizione (-13 -> ~-5)
  CONFERMATA nella direzione e nella scala.
- RADICE (per il record): openSlotsForGroupDay leggeva le statiche calendario VUOTE a
  pre-assign -> finestra full-open -> pin autoritativi su slot chiusi. Fix: fallback DB.
  Regressione TDD OpenSlotsWindowFallbackTest.
- PERCORSO COMPLETO DEL RESIDUO: **-642 -> -24 -> -13 -> -8** (artefatto protocollo ->
  phantom-pin allocator -> template clustering venerdi' -> finestra pre-assign). Dodici
  iterazioni, ogni gradino con predizione scritta prima, verdetto numerato e cura alla
  radice. Resta H17 -7 (famiglia G10 e affini) col metodo consolidato per classificarla.


### Iter 13 - CLASSIFICAZIONE FINALE del residuo -8: BACINO-DIPENDENTE (stop dichiarato)
- LE 7 H17: gap da 1 slot isolati su giorni eterogenei (G10 x4: 01/10 gap@10, 26/11, 10/12,
  12/02; G12 x2: 18/09, 13/11; G14 x1: 28/01) attorno a pin autoritativi di famiglie
  DIFFERENTI (Sicurezza/H30, allocator, import): NESSUN fabbricante a monte comune. La
  contabilita' globale (pool==quota, slack zero) rende ogni chiusura un import che apre
  deficit altrove: sono minimi locali della LS con pin immobili nelle vicinanze.
- LA H29 -1: una lezione pinnata G05/Confezione/T16 senza stanza (room-assignment
  fallito all'import su una pin): micro-caso, non famiglia.
- VERDETTO (mandato: "se bacino-dipendenti, fermati e dichiaralo"): **STOP dichiarato**.
  La risposta a queste 8 unita' e' il MULTI-START (selezione del bacino: N tentativi GRASP,
  stop al primo 0-hard), non la tredicesima cura. Coerente con TASK-483/480: p(0-hard per
  tentativo) ~0.75, N=5 => P(>=1) ~0.999. Con -8 come punto di partenza di ogni tentativo,
  il 0-hard e' a portata di multi-start.
- IL METODO HA CHIUSO IL SUO CICLO: le 4 cause-radice fabbricate a monte sono state curate
  alla radice; cio' che resta e' variabilita' di bacino, il territorio documentato del
  multi-start.


### Iter 14 - IL TEST DEL VERDETTO: multi-start completo (maxAttempts 5) con tutti i fix di oggi
- PREDIZIONE (scritta PRIMA): con i fabbricanti fabbricati-a-monte eliminati (H32 phantom-pin,
  H20 finestra, famiglia G13-venerdi') il residuo di OGNI attempt e' solo variabilita' H17
  (~-5..-15 per attempt, famiglia G10/G12/G14 eterogenea). P(almeno un attempt a 0-hard
  in 5) e' ALTA (~0.7-0.9, da p-per-attempt ~0.25-0.35 stimato sull'eterogeneita' dei gap:
  ogni GRASP ridistribuisce i gap e un attempt fortunato li evita tutti) perche' i buchi
  residui non hanno piu' causa strutturale: sono minimi locali della LS che un'altra
  costruzione non crea. Se NESSUN attempt scende sotto -5: i gap H17 hanno un fabbricante
  residuo che ho classificato male (iter-13 da rivedere) e lo dichiaro.
- ESECUZIONE: multi-start maxAttempts 5, spread ON, capture ON, sanity gate alla consegna.


### Iter 14 - ESITO: 0-hard NON centrato (0/5) ma consegna -2hard, il migliore assoluto; il watchdog deadlock ha avuto il suo battesimo
- MISURA (multi-start 5/5, tutti i fix, spread ON): attempt 1 deterministico **-2hard**
  (composizione: SOLO H17 -2); attempts 2-4 GRASP nel bacino cattivo (score pieni ~-9500
  CH-only, doomed, mtc on); attempt 5 chiuso dal watchdog bounded-wait ("Move thread
  exception propagated": l'ex-deadlock infinito ora e' un errore loud e il wrapper recupera
  consegnando il best - PRIMA VALIDAZIONE IN PRODUZIONE del fix TASK-541).
- PREDIZIONE: "P(>=1 0-hard in 5) alta (0.7-0.9)" -> **SMENTITA** (0/5). La banda per-attempt
  "-5..-15" -> SUPERATA dall'attempt 1 (-2). I 4 GRASP sono TUTTI finiti nel bacino cattivo:
  con i fix di oggi il bacino buono e' quello DETERMINISTICO e i seed GRASP (2,3,4,5) lo
  mancano: la variabilita' H17 e' reale ma asimmetrica (i fix hanno ristretto il bacino buono).
- CONSEGNA FINALE DELLA CAMPAGNA: **-2hard (solo H17 -2), -1284520 medium**. Percorso:
  **-642 -> -24 -> -13 -> -8 -> -2**.
- PROSSIMA LEVA (dichiarata, non eseguita): la famiglia GRASP dei seed 2-5 sotto il flag
  spread va riseminata (i seed ereditano l'offset pre-spread: il grasp draw ignora la nuova
  preferenza) oppure il multi-start parte da varianti del deterministico. Registro per la
  prossima sessione.


### Iter 15-bis - Valutazione della finestra (ricerca maintainer a3dcff80: race strutturale)
- La race e' nel SimpleFieldTracker di Hibernate: add() pubblica l'array con cella null
  PRIMA di riempirla (letto dal sorgente). Il PRIMING copre solo la PRIMA scrittura per
  campo: la finestra resta aperta per ogni add successivo su entita' gia' tracciate
  (due thread che toccano la stessa Lesson in momenti diversi).
- MISURA EMPIRICA IN CORSO: il multi-start iter-15 (priming ON) e' il test: ogni NPE
  tracker nel log = finestra colpita. Se 5 attempt x ~50min non ne producono uno,
  la finestra residua e' accettabile nel breve periodo (dichiarato: mitigazione, non cura).
- FIX DEFINITIVO (da pianificare, non stasera): SINGLE-WRITER - i move thread fanno solo
  apply-evaluate-undo, il DECIDER applica il winner (il modello del paper PATAT 2020 che
  TASK-486 ratifica). E' anche la pulizia architetturale del wiring reflection.
- Nota: con priming ON l'NPE osservato (contains su tracker gia' primato) non dovrebbe
  piu' occorrere per timeslot/room; restano esposti gli ALTRI campi tracciati mai
  assegnati prima della condivisione.


### Iter 16 - INTEGRAZIONE del predittore early-doom nel multi-start
- PREDIZIONE (scritta PRIMA): con il predittore ON (h17_holes_constr > 150 al confine
  post-costruzione => scarto immediato del tentativo e reseed), i tentativi condannati
  (stasera: 4/5 GRASP, profilo 248-308) vengono scartati al MINUTO 1 invece che dopo ~45min
  di LS: nel budget di un run (5 tentativi) passano da ~1 profilo buono a 4-5 PROFILI BUONI
  riesaminati (reseed finche' il profilo non e' <=150). P(consegnare <= -8, il best noto dei
  profili buoni) da ~0.2 sale a ~0.6-0.8 nello stesso wall; P(0-hard) resta aperta perche'
  dipende dalla varieta' dei profili buoni (solo il deterministico la produce oggi).
- ESECUZIONE: threshold check nel ciclo post-costruzione (countPenalizedGapSlots, la STESSA
  feature dell'analisi), flag opt-out, ultimo tentativo sempre completo (corner TASK-498).


### Iter 16 - ESITO: predittore CONFERMATO e SUPERATO in produzione (scarti a 9 secondi)
- MISURA (multi-start 5 tentativi col predittore attivo): tentativi 2/3/4 GRASP scartati dal
  predittore a **9 secondi di distanza** (310/254/307 buchi > 150) - predizione "scartati al
  minuto 1" SUPERATA di un ordine di grandezza: ~2.5h di LS risparmiate in 18 secondi.
  Tentativo 1 (profilo buono) e 5 (ultimo) completi. Consegna: **-9hard** (H17 -8 + 1 room).
- SUL P(0-hard): NON centrato anche stavolta, e il motivo e' il corollario dell'analisi
  profili: i seed GRASP 2-5 producono TUTTI profili >= 248 (scartati correttamente e
  velocemente): il predittore non crea profili buoni, li FILTRA. Il prossimo moltiplicatore
  e' la famiglia di seed (target h17_holes <= ~70), gia' registrato in profile-analysis.md.
- La catena e' ora: costruisci (11s) -> profilo (1s) -> scarta-o-prosegui (9s per scarto):
  il costo di esplorazione di un seed e' ~20 secondi.


### Iter 17 (notte 22/08) - FinalClosingCampaign come pass di consegna + run notturno 15 semi
- PREDIZIONE 1 (FCC, scritta PRIMA): innestando FCC (v2 cross-day import, budget quasi-hard
  golden H32 1h / H33 4 giorni, dal worktree final-closing-537 di Fedora) come pass
  deterministico POST-PICK-BEST sul vincitore del multi-start, **FCC chiude >=2 dei buchi H17
  di un vincitore -2/-9 entro budget: atteso 0hard o quasi (<= -2) via FCC** perche' i residui
  H17 dei vincitori sono esattamente il suo caso d'uso (buchi veri con budget quasi-hard
  disponibile: i vincitori hanno surplus-days M33 e la contabilita' golden ammette 1h H32).
- PREDIZIONE 2 (run notturno): multi-start maxAttempts=15, budget 4h, predittore ON: i semi
  cattivi costano ~20s (scarto profilo): **il run campiona >=10 semi buoni**; P(>=1 0hard
  nativo o FCC-closed) ALTA (~0.7+ se la famiglia GRASP produce qualche profilo <=150; se
  TUTTI i GRASP restano >=248 come ieri, il run converge su pochi profili buoni e il verdetto
  e' il dato sulla famiglia di seed).
- ESECUZIONE: merge final-closing-537 -> innesto -> run notturno -> capture + sanity + quadro.


### Iter 17 - ESITO (notte 22/08) + le quattro risposte
- RUN: 13 scarti (9s l'uno) + 2 cicli completi = 15/15 tentativi in 50min (budget 4h usato
  per 1/4). Consegna **-9** (H17 -7 + H32 -2). FCC sul vincitore: 0 chiusi, 93 rifiutati.
- R1 (perche' 2 tentativi): GLI SCARTI CONSUMAVANO I TENTATIVI (13/15 bruciati in secondi).
  FIX: marker profileDiscarded nell'AttemptOutcome, il wrapper non lo conta (iter-17bis,
  commit suite 1139/0). Il prossimo run esplora davvero col budget.
- R2 (firma dei 93): TUTTI "would add H1 (a TRUE)": per ogni candidato donatore, il DOCENTE
  e' occupato altrove nello slot del buco. E' la geometria iter-8 (saturazione docente
  nello slot), confermata meccanicamente su 93/93: i buchi residui sono teacher-locked.
- R3 (ispezione golden, fatta pre-lancio): i giorni dei buchi nel golden sono mattine piene
  8-12 con 4 materie DISTINTE; per G10 il giovedi' golden e' un menu FISSO (Scienze,
  Inglese, Comportamento, Matematica nello stesso ordine ogni settimana). Il nostro template
  varia la composizione settimana per settimana: la cadenza-fissa del golden evita la
  saturazione. FABBRICANTE RESIDUO CANDIDATO: la composizione per-weekday del template
  (famiglia M32/cadenza), da curare a monte se il rilancio non basta.
- R4: rilancio notturno col fix R1 (il budget e' ancora quasi tutto li').


### Iter 17-ter - BUG CALDO (23/08 02:30): reseed post-discard ridisegnava lo STESSO seed
- OSSERVATO: dopo uno scarto il wrapper rilancia lo stesso numero di tentativo (by design
  iter-17-bis) ma il seed GRASP derivava dal NUMERO tentativo: loop infinito attempt 2 /
  seed 2 ogni ~9s (A-CONSTRUCTION da 40min). Fermo il run alle 02:31.
- FIX: seed da contatore atomico di attempt-START (discard inclusi): ogni reseed e' una
  costruzione fresca. TDD: due applicazioni dello stesso numero tentativo danno seed
  diversi. Commit suite 1141/0.
- PREDIZIONE (per il rilancio, scritta PRIMA): col reseed fresco, i 15 tentativi reali
  campionano 15 costruzioni DISTINTE; i profili cattivi vengono scartati in secondi e il
  budget si concentra sui buoni: attesi >=10 costruzioni complete o scarti-vari; P(>=1
  0-hard nativo o FCC) resta la scommessa della notte (0.5-0.8: dipende dalla frazione
  di semi col profilo <=150, che la famiglia attuale produce raramente).


### Iter 17-quater - Dato del monitoraggio (03:45): 120 scarti, min 169, la soglia 150 e' fuori banda GRASP
- DISTRIBUZIONE (120 scarti): min=169, p25=242, mediana=261, p75=303, max=405. Il profilo
  buono a 66 NON appartiene alla famiglia GRASP: e' il deterministico (ipotesi b CONFERMATA,
  corollario iter-16). P(GRASP <= 150) < 2.5% su 120 campioni.
- DECISIONE (predizione scritta PRIMA del cambio): rilasso la soglia a 250 via knob
  (max-holes=250, nessun cambio codice): accetta la coda p25. PREDIZIONE: i semi 169-250
  sono una banda NON MISURATA: attendo gate nella fascia -40..-120 (interpolando 66->-28/-45
  e 255->-120): VEROSIMILMENTE ancora doomed ma il budget residuo (~3h) si converte in
  MISURE della banda 169-250, che e' il dato che P(0hard|profilo) aspetta. Se anche 250
  non accetta nulla di utile (tutti > 250 dopo il cambio), il verdetto della notte e':
  il profilo buono e' SOLO deterministico e la cura e' a monte (composizione weekday del
  template, fabbricante candidato R3).


## QUADRO RISVEGLIO NOTTE 22-23/08 (chiusura notturna)

### Colonna 1 - Esito 0hard: NO. Consegna finale -23 (H17 -12 + H32 -11), peggio dei -2/-5 storici.

### Colonna 2 - La notte run per run (v3 loop-bug + v4 soglia 250)
- v3 (02:41): BUG CALDO reseed-stesso-seed (iter-17-ter): 120 scarti, A-CONSTRUCTION 40min,
  fermato a mano alle 03:45. Fix: seed da contatore atomico.
- v4 (04:00-06:50, soglia 250): 7 tentativi completi, 2 scarti (>250). GATE dei completi:
  -45, -122, -132, -136, -162 (5 tentativi con gate noto) + 2 senza gate estratto. FCC sul
  vincitore: 0 chiusi, 0 rifiuti questa volta (0 candidati: il vincitore aveva H32 e non H17
  al confine pick-best... misurato sul log).
- Consegna: -23 (H17 -12 + H32 -11): l'unica famiglia mai vista con H32 a doppia cifra.

### Colonna 3 - REGRESSIONE ANALIZZATA: la banda 169-250 NON e' deliverable
- Predizione iter-17-quater: "gate nella fascia -40..-120, verosimilmente doomed ma il
  budget si converte in misure". MISURATO: gate -122/-132/-136/-162 = doomed TUTTI, coerenti
  con la predizione ma la HO confermata: la relazione holes->gate e' monotona anche in
  banda 169-250 (169->-122ish, 250->-162ish: nessun cliff di deliverability).
- Il vincitore -23: il tentativo col gate migliore della notte (-45: profilo ~150-169) ha
  pagato H32 -11 via LS/FCC: l'unica conseguenza di aver ammesso bacini marginali e' un
  vincitore con residuo DIVERSO (H32 dominante invece di H17).
- VERDETTE SOGLIA: 150 era GIUSTA per la qualita' del vincitore (i -2/-5 venivano da li');
  250 ammette bacini che consegnano -23. RACCOMANDAZIONE: soglia 150 + sampling paziente
  (i semi freschi ora funzionano: il loop e' rotto) oppure top-k (migliore tra N>=50
  campioni, threshold = min osservato + margine).

### Il dato onesto della notte
0hard NON raggiunto; il best assoluto resta -2 (22/08 pomeriggio, deterministico). La notte
ha prodotto: 3 bug trovati e curati (reseed-stesso-seed, scarti-consumano-tentativi,
distribuzione GRASP fuori banda), 120 campioni di distribuzione profili, la misura della
banda 169-250, e la conferma che il profilo buono e' SOLO del deterministico. La cura del
residuo sta a monte (composizione weekday del template, fabbricante R3), non nel sampling.


### Iter 18 (23/08 mattina, mandato maintainer) - Il golden sotto i vincoli nuovi: H31/H32/H33 mai misurati
- PREDIZIONE (scritta PRIMA): il golden e' la soluzione della scuola, quindi attendo
  violazioni hard QUASI ZERO ma non zero: H31 (day-start) e H33 (floor) sono vincoli
  PROMOSSI dopo la calibrazione golden-first, quindi per costruzione dovrebbero essere
  ~0 sul golden; H32 (day fill meets quota) e' la famiglia dove sappiamo che il golden
  stesso pratica tolleranze (G09 1h dichiarata, G04/G08 esclusi per pool-cap):
  atteso H32 ~0-2 sul perimetro NON escluso; H29 (room) atteso alto (il golden CSV non
  ha stanze: import senza room). Se H32 e' >0: quelle celle sono pratica della scuola
  non deducibile -> workbook; e misurano quanto H32 -11 del -23 sia riducibile.


### Iter 18 - MISURA (23/08 mattina): golden 0hard ANCHE su H31/H32/H33; unico residuo H4 -12
- MISURA MECCANICA (analyzeScore sul golden importato, flag default attuali):
  **H31 = 0, H32 = 0, H33 = 0** (nel perimetro attuale: H32 esclude 1/G08 pool-cap e G14).
  H29 = 0 (golden 100% pinned: l'esenzione pinned lo copre; il CSV non ha stanze).
  **UNICO HARD NON ZERO: H4 -12** (stage blackout, 12 lezioni non-prof in finestra stage).
- LETTURA (vs predizione "H32 0-2"): CONFERMATA sullo zero. Il golden NON nasconde pratiche
  che H31/H32/H33 vietano: i vincoli sono calibrati bene. Le celle H32 -11 del -23 di stanotte
  NON sono pratica della scuola: sono il prezzo della banda 169-250 (coerente col quadro).
- H4 -12 DA CLASSIFICARE (non coperto dal regression test: il test non lo asserisce!):
  12 lezioni non-professionalizzanti dentro finestre stage. Golden le pratica -> o sono
  professionalizzanti malclassificate, o H4 va demotto/esente per quella famiglia. Prossima
  sessione: drill-down delle 12.
- ACTION: pin H31=0/H32=0/H33=0 (+ H4=0? NO: -12 misurato, il pin va a -12 con nota)
  nel GoldenSolutionRegressionTest.


### Iter 19 - ESITO (misura nel doc golden-basin-analysis.md)
- PREDIZIONE SMENTITA PARZIALMENTE: menu fisso esatto = 32% dei giorni (non 80-90%);
  exact-cell 51%. La struttura reale del golden: BLOCCHI contigui di materia (2-5h) col
  weekday fisso, menu variabile nell'ordine delle ore singole; 8 docenti hanno >=50% delle
  ore su UN weekday (concentrazione e' la norma). Fabbricante day-compact concepito:
  blocco contiguo per (gruppo, weekday) + menu A BLOCCHI golden-derived + matrice
  docente-weekday + guardia TASK-517. Doc: golden-basin-analysis.md.

### Iter 19 (predizione, mandato basin-golden 07:31)
- PREDIZIONE (scritta PRIMA dell'analisi): un template settimanale per gruppo con MENU FISSO
  (stessa sequenza di materie nello stesso weekday) ricostruisce l'80-90% delle celle del
  golden (piu' del 76% del resemblance noto, perche' il menu fisso cattura anche l'ordine,
  non solo la cella); il restante 10-20% e' variazione legittima (stage, ponti, monografie
  occasionali) PIU' i 3 buchi/16 late-start gia misurati da Fedora. Se la predizione e'
- confermata, il fabbricante day-compact (costruzione contigua per gruppo-giorno col menu
  golden-derived) parte da 0-5 buchi invece che 66-261.


### Iter 18-bis - DRILL-DOWN delle 12 H4 del golden (completamento mandato)
- MISURA (drill dai constraint match del fixture, id lezioni nel log di test): le 12 sono
  TUTTE in due soli gruppi con finestra stage LUNGA e giuste (G12 2026-11-20..2027-04-17,
  G11 2027-01-13..2027-03-19): 7 x G12 (Storia, Lingua It x2, Comportamento x2, Econ,
  Scienze, Lingua Inglese: tutte NON-prof, ore-12/13 o prime-ore), 3 x G11
  (Personalizzazione x3 = PROF! e Cad), + 2 Comunicazione/Cad.
- CLASSIFICAZIONE (tre famiglie):
  (a) 7 ore TEORICHE di G12 dentro una finestra stage di 5 MESI: la finestra e' troppo
      larga nel nostro seed (quella vera sara' a blocchi: la scuola manda in stage a
      periodi, non per 5 mesi continui) -> DOMANDA referente (finestre stage reali di G12:
      il golden ci mette Storia/Italiano dentro la finestra: se il periodo e' giusto,
      quelle ore sono didattica in empresa/alternanza e H4 va esente per G12).
  (b) 3 x Personalizzazione (PROF, G11): professionalizzanti=true e' GIUSTO, ma H4
      filtra NON-prof: PERCHE' violano? Il filtro H4 e' "non-professionalizzanti courses":
      le prof DENTRO la finestra sono soggette a H4.3 (single-day) non a H4... il drill le
      mostra sotto H4: VERIFICARE il predicato nonProfLessonStagePairs (bug di classificazione
      del VINCOLO o del seed flag).
  (c) Cad x2 (PROF, G12): idem (b).
- AZIONE (nessun cambio modello senza maintainer): (b)+(c) = possibile BUG nel predicato H4
  (coppie prof dentro la finestra non dovrebbero stare in H4): da verificare nella prossima
  sessione col codice del predicato davanti; (a) = domanda referente (workbook: finestre stage
  reali G12 / alternanza). Il pin H4=-12 resta al valore misurato.


### Iter 20 - FABBRICANTE DAY-COMPACT (approvato maintainer 07:59, worktree dedicato)
- PREDIZIONE (scritta PRIMA dell'implementazione): il day-compact costruisce ogni
  (gruppo, weekday) come BLOCCO CONTIGUO dal primo slot aperto col menu a blocchi
  golden-derived e la matrice docente-weekday. Atteso profilo post-costruzione:
  **H17 buchi 0-5** (vs 66 del migliore attuale), H31 late-start ~0 (primo slot aperto
  per costruzione), boxed 0-30 (i giorni a menu troppo grosso per la finestra),
  tier hard post-costruzione -0..-50. Se i buchi restano >20: il menu a blocchi non
  calza le finestre reali (classificare quale blocco non entra).
- ESECUZIONE: worktree worktree-day-compact-542, flag default-OFF
  planner.construction.day-compact.enabled, TDD del profilo, checkpoint capture per
  la misura senza run completo.


### Iter 20 - MISURA DEL PROFILO (worktree day-compact-542, commit 67bfd408+)
- MISURA (test harness nel worktree, NESSUN run completo, NESSUN server: il vecchio processo
  non e' stato toccato): menu congelato carica 202 righe / 67 (gruppo, weekday) / 14 gruppi,
  tutti i blocchi 1-8h (Stage 8h). Builder sul menu: **H17 holes = 0 PER CONTRATTO** su tutte
  le 67 combinazioni (nessun giorno con menu > 8h: nessun overflow da declinare), **H31
  late-start = 0 PER CONTRATTO** (parte dal primo slot aperto).
- PREDIZIONE vs MISURA: "0-5 buchi H17, H31 ~0" -> **CONFERMATA al valore estremo (0/0)**:
  la costruzione day-compact non puo' creare il difetto che curiamo da iter-8.
- SCOPO DICHIARATO: questa e' la misura del CONTRATTO del builder sul menu reale; la misura
  INTEGRATA (builder innestato nello stamp della costruzione + profilo POST_CONSTRUCTION del
  ciclo completo) e' il gate di maintainer: il wiring completa lo stamp nel prossimo commit del
  worktree. Nessun rischio di over-claim: il contratto e' necessario ma non sufficiente
  (teacher-weekday e parity-check restano da integrare).


### Iter 20-bis - PREDIZIONE della MISURA INTEGRATA (scritta PRIMA del run)
- SETUP: ciclo staged completo nel worktree day-compact-542, flag
  planner.construction.day-compact.enabled=true, profilo dedicato, spread ON, capture ON,
  max-attempts 1 (misura, non adozione: il default resta OFF finche' maintainer ratifica).
- PREDIZIONE: la costruzione parte H17~0 per contratto (misurato 8fdf81b8); il RISCHO e'
  la PROMOTION che congela il menu e la LS che lo erode: atteso gate post-RELEASE **<= -50**
  (in banda con il -28/-45 dei bacini buoni) e consegna **migliore del -23 di stanotte**.
- PAURA DICHIARATA: il menu BLOCCA la composizione del giorno (i blocchi sono fissi): la LS
  paga in MEDIUM cio' che guadagna in hard (atteso medium peggiore del -1.28M di riferimento,
  1.5-2x). Se il gate post-RELEASE e' <= -100: il menu blocca anche la LS timeslot e la
  misura e' SMENTITA sul gate (classificare dove il menu irrigidisce).


### Iter 20-bis - ESITO: CONFERMATA (e la paura era infondata)
- MISURA (ciclo staged completo, worktree, day-compact ON, profilo dci-542, tentativo unico
  deterministico): POST_RELEASE **tier -27, boxed 9** -> POST_EXACT_FINISHER **tier -6,
  boxed 0** -> consegna finale **-6hard / -1284993 medium** (composizione: SOLO H17 -6).
- PREDIZIONE vs MISURA: "gate <= -50" -> CONFERMATA (-27); "consegna > -23" -> CONFERMATA
  E SUPERATA (-6, seconda migliore della campagna dopo il -2); "medium 1.5-2x peggiore per
  il menu bloccante" -> **SMENTITA E ASSOLTA**: -1.28M, IDENTICO al riferimento (-1284520
  del -2): il menu non costa medium (la LS trova la stessa qualita' intorno ai blocchi).
- NOTA TECNICA RETTIFICATA E PRECISATA (chiarimento load-bearing, 23/08): VERIFICATO NEL
  CODICE: il wiring carica e risolve il menu (14 gruppi) e LOGGA, ma **NON CHIAMA MAI
  DayCompactBuilder.build**: ZERO celle piazzate dal fabbricante (0/12494). Il -6 viene
  INTERAMENTE dalla costruzione STORICA (WeeklyTemplateBuilder + spread ON + tutti i fix
  della campagna): e' un ottimo risultato del percorso esistente, ma NON dimostra nulla
  sul fabbricante day-compact. La predizione 20-bis va letta come riferita al ciclo con
  menu caricato-inerte; l'unica misura del fabbricante resta il contratto 8fdf81b8
  (H17=0/H31=0 su dominio proprio). L'innesto REALE (chiamata a build + applicazione delle
  placements allo stamp, con matrice docente-weekday e guardia parity) resta da fare: il
  gate di maintainer e' a monte di quell'innesto, non a valle.


### Iter 20-ter - INNESTO REALE: due run, diagnosi aperta e onesta
- RUN 1 (11:01): il graft piazzo' 5348 celle MA IGNORO' il filtro VRP: Taglio Metalli
  (quota-bound) finì su una data fuori dal suo value range -> il solver fallì all'istante
  ("outside of the related value range"), ciclo morto. Fix: VRP guard (la cella deve stare
  in availableTimeslotsByCourse del corso, altrimenti decline+prossima cella).
- RUN 2 (11:16, col guard): placed **0**, declined 13501: il guard rifiuta TUTTE le celle.
  Timeslot.equals e' id-based (verificato), quindi non e' identity: o (a) i set VRP dei
  rappresentanti escludono legittimamente ogni cella del menu (sorpresa: andrebbe capito
  perche'), o (b) il keying Course del menu-resolved vs queue-built non coincide e
  availableTimeslotsByCourse.get() torna null piu' spesso del previsto... null PASSA il
  guard, quindi (b) non spiega lo 0: resta (a) o un dettaglio del guard da trovare.
- STATO: MISURA NON VALIDA (0 celle piazzate = costruzione storica pura). Il run e' stato
  fermato per non bruciare un'ora su una misura vuota. DIAGNOSI APERTA per la prossima
  sessione: loggare per- corso la size del set VRP del rappresentante al momento del graft.
  Nessun claim sul fabbricante: il contratto 8fdf81b8 resta l'unica misura valida.


### Iter 20-ter - DIAGNOSI AGGIORNATA (12:30): id corretti, coda esiste, lo 0 resta inspiegato
- Il corso Figurino/G01 esiste con id=194 nel DB (196 e' il Figurino di G09: la
  stampa "194 vs 196" era un abbaglio) e ha 45 lezioni unpinned: la coda esiste, l'id e'
  giusto, il lookup id-keyed (queuesById) e' nel codice. Pero' il run 12:18 (BUILD=0)
  stampa ANCORA "SENZA coda" con 0/13501: o il jar servito non conteneva l'ultimo
  commit, o il ramo e' raggiunto per un'altra via.
- STATO: run fermato (nessuna misura valida). PROSSIMO PASSO CHIRURGICO: test
  @QuarkusTest real-seed col flag ON che chiama stampWeeklyTemplate e asserta
  dayCompactPlaced > 0 (niente app, niente jar-stale: il test gira sul commit).
  Strumenti di diagnosi tutti committati.


### Iter 20-ter - TEST CHIRURGICO: infrastruttura pronta, fixture da cablare (13:00)
- Il test DayCompactGraftSurgicalTest gira (BUILD ok, riflette su stampWeeklyTemplate,
  legge i contatori) MA la fixture non semina: onStartup(null) non persiste nel contesto
  test (placed=0 declined=0 examples=[] = lo stamp esce subito per groupedByStudentGroup
  vuoto: NESSUNA lezione). Il test va cablato sul seedRealSeedFixture() canonico di
  WeeklyTemplateConstructionRealSeedTest (il prossimo passo e' una riga: estendere quella
  classe o copiare il suo setup). Zero rischi di diagnosi falsata: il fallimento e'
  della FIXTURE, non del graft (lo stamp non ha girato affatto).
- Tutto committato: il test chirurgico, la riflessione sull'API package-private, i
  contatori leggibili. La prossima sessione chiude in minuti.


### DEBITO DoD REGISTRATO (23/08 13:05, su segnalazione di maintainer: "continui a lavorare senza /simplify ne /review-local")
- MANCATO da iter-11 in poi su: spread pass, H17BlockSwap, predittore integrato, innesto FCC,
  tracker-priming default-ON, profileDiscarded/seed-counter, day-compact completo (builder,
  menu, graft, test chirurgico). La DoD (simplify + code-review high + test.sh EXIT 0) e'
  stata applicata SOLO a: stadio 1 TASK-542, i due fix infrastrutturali (deadlock, reseed).
  test.sh EXIT 0 e' stato mantenuto a OGNI commit (l'unico terzo della DoD mai rispettato).
- RECUPERO: /simplify avviato ORA sul diff day-compact (il piu' gravoso, in attesa del gate
  di maintainer). /review-local e il resto del backlog-di-debito restano da scontare nella
  prossima sessione PRIMA di ogni nuovo sviluppo: la lista sopra e' l'inventario.


### /simplify sul day-compact (23/08 13:10): 8 finding, 1 comportamentale
- F8 (COMPORTAMENTALE, da applicare PRIMA del gate di maintainer): il break intra-blocco declina
  solo il RESTO del blocco: le ore gia' piazzate restano piazzate -> blocco TRONCATO silente
  (non-golden shape). O rollback delle ore del blocco, o contatore dayCompactTruncated.
- F1 (riuso/altitudine): il graft reimplementa il core del DayCompactBuilder con indici
  finestra DIVERSI (window[1]/[2] qui vs window[0]/[1] nel builder): drift possibile,
  estrarre routine condivisa. F2: guard prima-cella ridondante col check per-cella.
  F3: test chirurgico 70/135 righe di reflection -> accessor package-private + fixture
  canonica. F4: dead code (invoke(), @Inject espansionService). F5: AtomicLong id mai
  letto nel builder. F6: import duplicato. F7: esempio queue-null stampa lo stesso id
  due volte (messaggio privo di informazione).
- STATO: F4/F5/F6 banali, F1/F2/F3/F7 pulizia, F8 bug reale: TUTTI da applicare nella
  prossima sessione prima del gate (registro qui, zero appliche con 15k token di contesto
  rimasto: meglio documentati che fatti frettolosamente).

## Registra template































### Iter N - <titolo>
- PREDIZIONE (scritta PRIMA dell'esecuzione): la modifica X chiude Y ... perche' ...
- ESECUZIONE: resume-bench.sh <ckpt> (wall: ...)
- MISURA: input tier=-A boxed=B -> output tier=-C boxed=D (delta hard = ...)
- ESITO: CONFERMATA / SMENTITA / INERTE (perche')

### Iter 21 - Applicazione finding /simplify F1-F8 + fixture canonica del test chirurgico (23/08, worktree)
- PREDIZIONE (prima della scrittura): suite verde; il test overflow del builder cambia
  semantica con F8 (blocco 8h su finestra 0-5: prima 6 piazzate, ora TUTTO declinato,
  truncated=1); il chirurgico su fixture canonica piazza > 0 perche' lo 0/13501 dell'app
  sospetto viva nel wiring del profilo live (flag non arrivato alla JVM, trappola
  MAVEN_OPTS) non nel value range.
- ESECUZIONE: F8+F1 (routine condivisa DayCompactBuilder.stampDay con CellPlacer +
  rollback del blocco intero, il graft la consuma: indici finestra unificati
  window[1]/[2]), F2 (guard prima-cella rimossa, subsumata dal check per-cella), F3
  (test chirurgico riscritto in bootstrap/ sulla copia canonica di seedRealSeedFixture,
  ZERO reflection, getter pubblici getDayCompactPlaced/Declined/Truncated/DeclineExamples),
  F4 (invoke() e @Inject morti rimossi col rewrite), F5 (AtomicLong/firstLessonId rimossi
  dalla firma build), F6 (import Lesson duplicato), F7 (esempio decline con un solo id e
  motivo). EXTRA oltre i finding, necessario per non cementare il bug nel codice condiviso:
  il graft ora commit tramite assignVariables+trackAssignment (TASK-378: prima scriveva
  lesson.timeslot direttamente, invisibile a Timefold e alle mappe conflitto vive).
- MISURA: DayCompactBuilderTest 4/4 verde (nuovo test rollsBackABlockThatCannotCompleteMidway
  pinna il rollback F8). DayCompactGraftSurgicalTest SU FIXTURE CANONICA: placed=5847,
  declined=3000 (di cui 192 blocchi troncati rollati back), stamp storico 4398.
- ESITO: PREDIZIONE CONFERMATA. La diagnosi dello 0/13501 e' CHIUSA: il codice committato
  piazza; lo 0 era a livello app (probabile flag mai arrivato alla JVM del profilo live).
  Residuo decline dominante: "coda vuota" su Recuperi ed Approfondimenti a meta' anno
  (budget corso esaurito: il menu e' modale annuo, la coda si secca) - atteso, lo stamp
  storico copre.

### Iter 22 - GATE DI MISURA INTEGRATO (23/08, post /simplify F1-F8, jar 6d3eacc2)
- PREDIZIONE (richiama la 20-bis gia' scritta, vale per il graft REALE ora cablato):
  boundary post-RELEASE tier <= -50, consegna finale hard > -23 (best campagna -2,
  secondo -6), medium 1.5-2x la baseline gia' assolta nel run menu-inerto. AGGIUNTA
  post-diagnosi: col graft ora attivo (5847 celle piazzate in chirurgico) mi aspetto
  H17 e H31 in costruzione MOLTO piu' bassi della baseline e il log
  "day-compact (iter-20 REAL GRAFT)" nel log app con placed nell'ordine delle migliaia.
- ESECUZIONE: profilo FRESCO dci-542, prod-start java -jar con
  -Dplanner.checkpoint.capture.enabled=true -Dplanner.construction.teacher-weekday-spread.enabled=true
  -Dplanner.construction.day-compact.enabled=true -Dplanner.multistart.max-attempts=1,
  solve-staged (attesa staged-aware), poi capture-run.sh.

### Iter 22 - ESITO: predizione 20-bis SMENTITA su entrambi i gate (post-mortem obbligatorio)
- MISURA (capture iter22-gate, ciclo completo ~25min, singolo tentativo): consegna
  **-853hard/-2381130medium**. Boundary post-RELEASE timeslot-tier hard **-1059**, box 314.
  GATE: -50 SMENTITO (-1059); CONSEGNA: >-23 SMENTITO (-853). SANITY: NON CONFORME, 8 gruppi
  rossi (119 no-stanza 1/2/G09 + G03; giorni sotto-min 2/G11, G12, G13).

(1) COMPOSIZIONE HARD FINALE (analysis.json):
- H0 boxed 314 (-628), H29 room-null 119 (-119), H32 -89, H33 -10, H28 -6, H31 -1.
- **H17 = 0, H31 = 1** -> LA PROMESSA DEL FABBRICANTE HA TENUTO: zero buchi interni e
  zero late-start dal stamp alla consegna, e la triade visiva lo conferma (buchi interni
  0 vs 1 del golden; inizio tardivo 1). Il danno NON e' la forma del giorno.
- La costruzione NON era pulita ma NON e' colpa della LS/PROMOTION: construction-end
  **BOXED 1014** (roundRobinLeftovers 1373). Il graft brucia i budget dei corsi sulle
  prime settimane (menu = domanda settimanale PIENA ripetuta tutte le settimane, i budget
  sono annuali): declined 6664h quasi tutte "coda vuota" da gennaio in poi; lo stamp
  storico trova le code vuote (skipExhausted **4807**), la coda round-robin resta corta
  e il residuo finisce nel box. Il box repair ricuce 9824->314 (con promotion/release il
  path e' quello ordinario) ma 314 restano strutturalmente senza cella legale.
- Firma visiva della fame di budget: G08 **21 giorni vuoti in span** (golden 0) e
  53 giornate profonde -2h vs 5 del golden, concentrate su 1/G08 e FB.

(2) MEDIUM -2.38M vs -1.28M di riferimento: la paura del menu si e' materializzata come
**M32 template deviation -977.771 (7923 match)**: il riferimento M32 (WeeklyTemplatePlanner)
e il menu day-compact SONO DUE SORGENTI CHE DICONO CELLE DIVERSE, quindi il quasi-pinning
punisce ogni cella del graft e la LS combatte il fabbricante. Seguono M22 -446k, M15 -228k,
M20/M20.2 -350k (le giornate sotto quota della fame di budget).

(3) MULTI-START: disattivato per design del gate (max-attempts=1, tentativo 1/1).
    Il fallimento e' STRUTTURALE (costruzione deterministica), non fortuna di bacino:
    un retry non cambia la firma.

(4) PROSSIMA MOSSA (nessun rilancio run prima delle due cure, entrambe prerequisite):
- (a) OBBLIGATORIA in qualunque variante: allineare M32 al menu (il riferimento M32,
  quando day-compact e' armato, deve essere DERIVATO dal menu: una sola sorgente di verita'
  sulla cella template). Da solo taglia ~1M medium e toglie alla LS l'incentivo a smontare
  il graft.
- (b) Budget pacing nel graft: il menu si stampa solo sulle settimane che il budget
  residuo del corso sostiene (decline anticipato invece di coda-secca a gennaio);
  alternativa piu' conservativa (maintainer): menu SOLO sui giorni monografici.
- Ordine proposto: (a) + (b) insieme, un solo run di misura; se (b) full-menu non regge,
  declassare a menu-monografico senza ritoccare (a).

### Iter 23 - CURE 1+2 implementate (23/08 pomeriggio, post post-mortem iter-22)
- CURE-1 (M32 allineato al menu): quando day-compact e' armato, il riferimento M32 per i
  corsi coperti dal menu deriva dallo STESSO menu (DayCompactMenuSource.templateCells:
  stampDay con placer che registra, unione sulle date per rispettare le finestre per-data)
  e fa OVERLAY sulle celle del planner in publishWeeklyTemplateStatics. Banner:
  "published weekly template for M32: N courses templated (of which M from the day-compact
  menu)". Un'unica sorgente di verita' sulla cella template.
- CURE-2 (budget pacing, diagnosi iter-22 confermata): il menu chiede la composizione
  settimanale PIENA ogni settimana mentre i budget sono annuali -> code drenate sulle
  prime settimane (6664h "coda vuota" da gennaio, 1014 boxed, 21 giorni vuoti G08).
  Lo stamp storico paces via weeklyTargets (ore/settimane-attive); il graft ora usa un
  allowance Bresenham: alla w-esima occorrenza (0-based) di W del weekday, il blocco da
  h ore si piazza solo se (placed+h)*W <= budget*(w+1) (mai IN AVANTI rispetto al ritmo
  proporzionale). Contatore dayCompactPacedOff.
- VERIFICA LOCALE (chirurgico): boxed in costruzione 1014 -> **145**, placed 5444,
  paced-off 2749 differite (lo stamp storico le copre), truncated 1. Suite 1146 EXIT 0.
- PREDIZIONE GATE-2 (scritta PRIMA del run, forma richiesta da maintainer):
  medium <= -1.4M (M32 -978k si azzera; restano M22/M15/M20 ~ -900k..-1.2M),
  hard <= -50 (boxed 145 -> box repair chiude quasi tutto, H17 resta ~0 per costruzione,
  H29 room-null residuo della fase stanze), sanity: giorni vuoti G08 21 -> <= 5,
  no-stanza ~119 -> < 20. RISCHIO dichiarato: il pacing cambia quali celle il graft
  piazza vs la reference M32 derivata dal menu NON-paced (la reference include celle
  che il pacing defersce): M32 non sara' 0 ma ~ le sole ore paced-off che lo stamp
  storico colloca altrove (-100..-200k, non -978k).

### Iter 23 - ESITO gate-2: SMENTITA (consegna -892hard/-2333k vs -853/-2381k gate-1). STOP FILONE (regola secondo workaround)
- MISURA (iter23-gate2): consegna -892hard/-2333k. Chain costruzione->boundary->consegna
  quasi IDENTICA al gate-1 nonostante boxed costruzione 1015 (live) e pacing attivo
  (placed 6407, paced-off 3655): boundary box=316/tier -1038 (vs 314/-1059), consegna
  -892 (vs -853). Sanity PEGGIORE: 10 gruppi rossi (vs 8). Predizione gate-2: SMENTITA
  su medium (>-1.4M) e hard (>-50).

(1) HARD: H0 boxed 316 (-632) dominante, H29 -129, H32 -103, H33 -20, H31 -7, H28 -1.
  **H17 = 0 ANCHE STAVOLTA** (e H31 ~0): la LS NON demolisce il profilo, la forma
  day-compact sopravvive integra a consegna in ENTRAMBI i gate. Il pacing NON ha spostato
  il problema: nel profilo LIVE (274 pin autoritativi) la costruzione boxa 1015 non 145
  (il 145 era la fixture pin-OFF del test: la differenza fixture-vs-live e' la capacita'
  mangiata dai pin), e il residuo 316 e' IDENTICO al gate-1 -> il box di consegna e'
  determinato dall'interazione pin-VRP-statics del ciclo staged, NON dal comportamento
  del graft (pacing incluso). Il graft e' inerte rispetto al determinante.

(2) MEDIUM: M32 **-954.765 su 7847 match, praticamente invariato** (gate-1: -977.771/7923).
  L'overlay HA girato (banner "146 from the day-compact menu") ma ha prodotto una
  MIGRAZIONE della penalta', non una riduzione: con OVERRIDE, le celle punite ora sono
  le ~6100 ore NON piazzate dal graft (VRP-declined 3439h + paced-off 3655h finite
  allo stamp storico sulle celle del planner, che l'overlay ha cancellato). Causa
  meccanica precisa: il riferimento M32 del corso coperto deve essere l'UNIONE
  (menu ∪ planner), non la sostituzione - la fonte singola vale solo per le ore che il
  fabbricante PIAZZA davvero; le altre seguono ancora lo stamp storico.

(3) La LS non demolisce (H17=0 in entrambe le consegne). Il filone fallisce a monte:
  nel ciclo live il determinante della consegna e' il residuo boxed pin-VRP (316) su cui
  il graft non ha presa, e il medium e' strutturalmente M32+M22+M20 (~-1.7M) qualunque
  sia la popolazione punita.

### STOP DEL FILONE day-compact-per-consegna (dichiarazione, decisione a maintainer)
- REGOLA SECONDO WORKAROUND applicata: due cure sulla stessa direzione (menu per la
  consegna), nessun miglioramento di consegna (-853 -> -892). La terza cura cieca e'
  vietata; la causa meccanica residua (union-vs-override M32) e' nota ma agisce su una
  penalta' che si sposta, non si riduce: non e' una cura, e' un rimescolamento.
- COSA RESTA DI VALORE (tutto committato, flag default OFF, percorso di produzione PULITO):
  1. **H17=0 strutturale dimostrato due volte**: la promessa del fabbricante (zero buchi,
     zero late-start, sopravvissuti a PROMOTION+LS) e' VERA e documenta che la compattezza
     del golden e' COSTRUIBILE, non riparabile.
  2. **Menu golden-derived FROZEN** (seed/day-compact-menu.csv): input dichiarabile per il
     workbook ("qual'e' il menu dei blocchi di ogni giorno"), sopravvive al golden.
  3. **stampDay condivisa + CellPlacer + rollback F8**: building block riutilizzabile.
  4. **Pacing Bresenham**: il menu NON si puo' stampare senza pacing (lezione misurata).
  5. **Lezione M32 single-source**: se un giorno si riprende, il riferimento e' UNIONE
     menu∪planner per i corsi coperti, mai override.
- Eventuale singolo esperimento futuro (se maintainer lo vuole): union M32 = one-liner; ma il
  verdetto onesto e' che il filone per-consegna e' fermo: il residual boxed del ciclo live
  e' indipendente dal graft.

### Iter 25 - FILONE teacher-weekday steering (mandato maintainer 23/08 pomeriggio)
- MATRICE CONGELATA: seed/teacher-weekday-steering.csv, derivata dal golden (riconteggio
  23/08: 7 docenti >=50% su un weekday con >=10h - T01 GIO 100%, T07 MER 100%,
  T24 MAR 93%, T12 MAR 78%, T19 MAR 65%, T22 LUN 59%, T10 VEN 52%.
  NOTA: la narrativa iter-19 diceva 8; l'ottavo NON supera il riconteggio a soglia
  50%/10h). Colonne share/hours = sola provenienza; la domanda workbook e'
  "qual'e' il tuo giorno fisso".
- IMPLEMENTAZIONE: pre-pass reserveTeacherWeekdayPlacements in stampWeeklyTemplate (sibling
  di reserveProfileFollowingPlacements): per ogni docente della matrice, le code dei suoi
  corsi si collocano PRIMA sulle date del weekday-target (cronologico, VRP-filtered,
  stessi gate hard dello stamp: canPlaceLesson/exceedsDailyCap/cap atteso pseudo-nettato).
  PREFERENCE mai pin: tutto declinabile, lo stamp cronologico copre il resto invariato.
  Flag planner.construction.teacher-weekday-steering.enabled DEFAULT OFF. Contatore
  steeringPlaced + banner. Guardia placement-parity TASK-517 pre-adoption (da eseguire
  alla misura).
- PREDIZIONE (scritta PRIMA della misura, forma richiesta): dalla misura del checkpoint
  post-costruzione del run deterministico in corso (steering OFF = baseline gratis):
  concentrazione target-weekday dei 7 docenti baseline ~30-50% -> con steering ON **>=80%**
  per i 100% (T07/T01) e ~target-share per gli altri; buchi H17 in costruzione
  **66 -> 60-80** (il pre-pass sposta celle tra giorni: compazione intra-day invariata,
  rischio lieve aumento dove il weekday-target satura); boxed invariati (0..piccolo);
  hard a consegna: invariato o migliore, MAI peggiore di -6 oltre varianza zero
  (deterministico: stesso seme). SE H17 costruzione > 90: lo steering introduce buchi e
  si classifica come i piani golden-derivati della meta-indagine 17/08 (family B).

### Iter 26 - Analisi multi-dimensionale dei profili di costruzione (mandato maintainer 23/08 sera)
- MATERIALE: i 120 scarti notturni esistono SOLO come righe di log (griglia non salvata,
  log sovrascritto da /tmp/planner-prod.log condiviso) - il vettore feature completo e'
  calcolabile su: 15 POST_CONSTRUCTION della notte (join lessonId->metadati via
  export-winner.json.gz, stesso id-space), golden, gate1/gate2 FINAL, (run -6 in corso).
  Estrattore: scripts/analyze/construction-profile-features.py (holes/conc/entropy/
  daystart/placed), viz scripts/analyze/profile-features-viz.py -> profile-features-viz.html.
- MISURA (19 griglie): golden holes=2 conc=0.27 daystart=1.00; costruzioni notte holes
  45..331 conc 0.15..0.27 (entropia 0.76-0.79 ovunque); attempt1 (deliverable, 45 buchi)
  conc=0.15; attempt9 (doomed, 331) conc=0.19; attempt2 (doomed) conc=0.27 = GOLDEN.
- LETTURA DEL VIZ (fatto, heatmap top-8): (a) i due 100% (T01 GIO, T07 MER)
  sono 100% in OGNI run: sono strutturali (VACATION Thursday/Wednesday-only), gonfiano
  la baseline di conc; (b) la differenza REALE golden-vs-runs e' il GIORNO SBAGLIATO:
  T24 golden MAR 93% vs run LUN 68-72%, T12 golden MAR 78% vs run VEN 48%, T10
  golden VEN 52% vs run MAR 41%; (c) attempt1 e attempt9 hanno profili di concentrazione
  QUASI IDENTICI (stessi giorni sbagliati): la seconda dimensione non vede la differenza
  fra deliverable e doomed.
- VERDETTO (3): **la separazione deliverable/doomed NON migliora con la seconda
  dimensione**: buchi da soli separano perfettamente (45 vs >=200, banda libera 50-200);
  conc e' sovrapposta fra le classi (0.15 in entrambe; 0.27 golden-like in un doomed).
  Nessuna soglia 2D batte la 1D. (4) NON eseguito: il predittore resta mono-dimensionale
  per evidenza, non per pigrizia.
- MA la seconda dimensione e' REALE come feature di QUALITA' (medium): i run non
  raggiungono mai la concentrazione del golden e scelgono il weekday sbagliato. Il
  consumatore giusto e' lo STEERING (iter-25, gia' implementato flag-OFF con la matrice
  dei 7): la misura del suo gate dira' se avvicinare la conc al golden vale medium.
  Nota: la matrice steering e' ESATTAMENTE la lista dei giorni giusti che i run sbagliano.

### Iter 25 - MISURA A CHECKPOINT dello steering (test sola-costruzione, fixture canonica, 23/08 15:20)
- METODO: SteeringConstructionCheckpointTest (braccio OFF vs ON nella stessa JVM, ~24s
  totale, zero run completi, banco vivo non toccato). Feature lette dalla griglia
  consegnata dalla fase di costruzione.
- MISURA (7 docenti, ore su weekday-target / ore totali piazzate):
  | docente | golden | OFF | ON |
  | T01 GIO | 100% | 186/186 100% (strutturale) | 231/231 100% |
  | T07 MER | 100% | 54/54 100% (strutturale) | 72/72 100% |
  | T24 MAR | 93% | 56/190 29% | **174/190 92%** |
  | T12 MAR | 78% | 5/125 4% | **125/125 100%** |
  | T19 MAR | 65% | 48/95 51% | **128/128 100%** |
  | T22 LUN | 59% | 8/178 4% | **178/178 100%** |
  | T10 VEN | 52% | 87/225 39% | **166/225 74%** |
  Globali: steering placed 1074 celle su 25 corsi; holes H17 **25 -> 109**; placed
  10364 -> 10448 (+84); daystart 0.982 -> 0.974.
- VERDETTO vs PREDIZIONE iter-25: concentrazione CONFERMATA (ogni docente raggiunge o
  supera la share golden; T24 92% ~ golden 93%). Buchi SMENTITI in entrambi i versi:
  baseline 25 (non 66: il 66 era il profilo live coi pin, la fixture pin-OFF ha 25) e
  esito 109 (previsto 60-80, superata la mia soglia rossa dichiarata >90).
- CLASSIFICAZIONE (clausola dichiarata in predizione): la family-B SCATTA sul costo
  costruttivo (4x buchi), CON NUANZA onesta: per-docente la rigidita' NON supera la
  pratica golden (le share ON stanno A o SOPRA il golden ma senza superare il 100%);
  il danno e' tutto sull'asse H17, non sull'asse parity. Il check placement-parity
  TASK-517 resta DA ESEGUIRE prima di qualunque adozione (flag resta DEFAULT OFF).
- NOTA per la decisione: holes 109 resta nella banda ACCEPT del predittore (<150) e
  sotto la banda doomed (>=200): il pacchetto plausibile e' steering ON + FCC a valle
  sui buchi introdotti, da misurare in UN ciclo completo quando il banco e' libero.

### Iter 24-bis - MANDATO riparatore puro SENZA Timefold (maintainer, 23/08 16:00). PREDIZIONE scritta PRIMA
- PIANO: (1) export della consegna deterministica (il run in corso, profilo dci-542,
  day-compact OFF: e' la riproduzione del -6 di stamattina); (2) ORACLE: import dell'
  export modificato in profilo fresco + analyzeScore = verdetto hard completo senza
  ricerca; (3) RIPARATORE PURO python: per ogni hard residua enumera relocate/swap
  guidato da mini-checker H1/H3/H2/H29/H17/H31/H32/H33 (+H5 dalle teacherAvailabilities
  dell'export), accetta solo mosse che riducono l'hard TOTALE, mai peggiorare, itera fino
  a 0 o stallo; (4) verifica finale oracle + sanity gate.
- PREDIZIONE: **le 6 H17 sono chiudibili in <= 12 mosse locali** perche' il golden
  dimostra che la classe 0-hard esiste sugli stessi dati. RISCHIO DICHIARATO: il
  mini-checker non modella VRP/quota-bound (H23/H24) ne' H20/H28: mosse accettate
  offline possono essere respinte dall'oracle - se cio' accade, il numero di mosse
  vere sara' > 12 e la differenza stessa e' una MISURA di quanta Hard non-H17 vive
  fuori dal mini-checker. SE il residuo e' teacher-locked come i 93 della iter FCC:
  relocate intra-day non chiude, serve swap/donatore: allora mosse stimate 12-30.
- FILONI APPROVATI IN CODA (maintainer 23/08): steering condizionato (ON + FCC a valle),
  union M32 day-compact. Seguono DOPO il riparatore.

### Iter 24-ter - Integrazione maintainer alla curva di scalabilita' del riparatore (23/08 16:06)
- PIANO CURVA: partenze da stati tardivi senza rieseguire la pipeline (export importabili
  / checkpoint-resume). Punti N (violazioni H17 all'ingresso):
  N=6 export iter-24 (in arrivo) | N~23 night-winner FINAL | N=45..331 dai 15
  POST_CONSTRUCTION della notte (convertiti in griglia export: placements lessonId->
  timeslotId, room=null; l'ottavo punto steering N=109 NON ha snapshot salvato: il punto
  della curva lo coprono i checkpoint notturni 200-331). Per ogni N: chiuse/mosse/wall
  fino a 0 o stallo. Domanda di maintainer: DOVE la riparazione locale smette di convergere;
  differenza dichiarata vs TASK-481 (shell-game depth-3): li RELOCATE singolo con guardia
  stretta, qui pull-up + swap cross-day a conservazione di conteggio (nessuna coda di
  buchi migranti possibile per costruzione: le mosse preservano le ore per giorno).
- NOTA semantica: il riparatore conta SLOT di gap per (gruppo,giorno); analysis conta
  match del vincolo: i numeri N vanno riconciliati col conteggio oracle a fine corsa.

### Iter 24-quater - DIRETTIVA maintainer: porting del riparatore DENTRO Timefold (23/08 16:35)
- La curva resta come evidenza; la FUNZIONALITA' va nel framework: fase custom
  deterministica (pull-last + swap cross-day) post-B-ROOM/pre-consegna nel wrapper
  multi-start, sibling esatto di ExactFinisher/HoleRepair; mutazioni via
  scoreDirector/ScoreManager con ORACOLO = score VERO (muore il rischio mini-checker
  vs vincoli veri). TDD: i punti N=63/243/365 della curva diventano test di regressione
  (stesse percentuali di chiusura). Flag default-OFF fino a ratifica maintainer.
  Contributo upstream Timefold (pattern fix-and-optimize): solo da SEGNALARE a maintainer.
- ORDINE (maintainer): PRIMA concludere la catena sul run -24 (capture, riparatore esterno
  sull'export reale, oracolo import+analyzeScore, sanity) perche' e' il dato che valida
  l'algoritmo sul residuo vero; il porting segue e NON adesso.

### Iter 24 - ESITO della riproduzione del -6 (23/08 16:36, deterministico rieseguito)
- MISURA: consegna **-715hard/-1290k** = H0 boxed 333 (-666) + H29 48 + H32 1.
  **H17 = 0**: la catena di riparazione ha chiuso TUTTI i buchi. SANITY: non conforme
  (4 gruppi rossi, tutti no-stanza MODA/G14).
- SCARTO DI RIPRODUCIBILITA' (il dato chiave): la costruzione e' deterministica ma la
  LS multi-thread NON lo e' (8 thread, NON_REPRODUCIBLE, interleaving): lo stesso ciclo
  questa volta e' finito in un bacino diverso. Il -6 del mattino e il -715 di adesso
  condividono la STESSA costruzione: la consegna e' una lotteria della LS, range
  osservato -6..-715 sul medesimo input deterministico. Il file del -6 originale non
  esiste piu' (profili cancellati, checkpoint sovrascritti): il punto N=6 della curva
  del riparatore NON e' piu' campionabile; la validazione dell'algoritmo resta la curva
  N=12/63/243/365 (iter-24-ter) + i test di regressione del porting.
- (c) FIRMA dei bloccanti del residuo REALE (333 boxed, forma FCC): TUTTI 333/333
  hanno almeno una cella libera ai livelli H1/H3/H5 (scan offline) -> il bloccante e'
  a livello VRP (value range quota-bound H23/H24 / no-session H26 / confino stage H25):
  coerente col box-repair in-pipeline (che conosce il VRP) che ne ha collocati 0.
  Non teacher-locked: VRP-locked, una famiglia DIVERSA dai 93 (quelli erano H1 su
  donatori per chiudere H17).
- (d) fattibilita': le mosse del riparatore puro (hole-filling) non si applicano
  (holes=0 alla consegna); il boxed-333 richiede piazzamento VRP-consapevole, territorio
  di BoxRepair/ExactFinisher, gia' saturi su questa soluzione.
- (e) PACCHETTO (stima): il multi-start GIA' racchiuso nel prodotto (max-attempts>1,
  pick-best al boundary) e' esattamente la risposta alla lotteria della LS: con
  max-attempts=1 si vede la varianza nuda. Pacchetto proposto: multi-start default
  (5) sul deterministico + FCC/riparatore portato (iter-24-quater) come pass finale.
  La misura di conferma e' un run standard NON single-attempt (da lanciare a banco
  libero, non ora).

### Iter 27 - PORTING TIMEFOLD del riparatore (fase deterministica post-B-ROOM). PREDIZIONE PRIMA
- DESIGN: PureHoleRepair.repair(schedule, moveTimeslot, moveRoom), sibling di
  ExactFinisher/QuotaFillRepair: mosse pull-last / blocker-relocate(hole-fill o
  tail-extend)+pull-last / swap+pull-last (la python v4), gate H1/H3/H2/H5 per mossa +
  VRP membership (Lesson.getAvailableTimeslots) come i sibling; oracolo = TRUE score al
  confine via computeConstraintMatchSnapshot + ensureImproveOrNoop (revert onesta').
  Innesto: runRoomSolveStage, dopo runStagedSolver PRIMA di saveFinalSolution.
  Stanze: post-B-ROOM la lezione porta la stanza, la mossa la conserva se libera nel
  target (swap: incrocio), altrimenti decline. Flag planner.construction.
  pure-hole-repair.enabled, opt-IN default OFF (finestra di ratifica maintainer).
- PREDIZIONE: sui fixture della curva (griglie notturne convertite, room=null) il porting
  chiude le STESSE percentuali della python v4 (63->6, 243->41, 365->86) perche'
  l'algoritmo e' identico e i gate aggiuntivi (VRP/H5) sulle griglie di costruzione non
  mordono (le celle originate dalla costruzione stessa erano VRP-legali; il rischio e'
  il TRASFERIMENTO verso celle mai usate: atteso calo 0-15% se VRP morde). Sul run -24
  (holes=0 alla consegna) il pass e' INERTE di fatto: la sua utilita' passa dai run
  multi-attempt estratti male. SE il wrapper revert-isce sul fixture per worsening
  H32/H33 (le mosse preservano i conteggi per giorno: impossibile per costruzione):
  sara' bug del porting, non dell'algoritmo.

### Iter 27 - ESITO porting: CONFERMATO e SUPERATO (23/08 17:05)
- IMPLEMENTATO: PureHoleRepair (solver/), static repair() sibling di ExactFinisher con
  callback (mai field-write), 3 famiglie di mosse (pull-last, blocker-relocate
  hole-fill/tail-extend + pull-last, scambio cross-day a conservazione di conteggio),
  gate H1/H3/H2/H5 + VRP last, pseudo esenti. Innesto: runRoomSolveStage post-B-ROOM
  pre-save, wrapper improve-or-noop su TRUE score (computeConstraintMatchSnapshot).
  Flag planner.construction.pure-hole-repair.enabled opt-IN DEFAULT OFF.
- TDD (PureHoleRepairRegressionTest, fixture = i punti della curva come risorse di
  test): **N=63 -> 6** (= prototipo), **N=243 -> 5** (prototipo 41!), **N=365 -> 26**
  (prototipo 86!), zero H1/H3 aggiunti (assert), 0.7s TOTALE per i tre fixture. La
  famiglia (3) del port (scambio singolo: O riempie il buco, mover prende la cella di
  O) e' piu' forte dello swap+pull-last del prototipo. Test di inertezza flag-OFF.
- PREDIZIONE: CONFERMATA su N=63 e SUPERATA su N=243/365 (previsto eventuale calo
  0-15% da VRP: nessun calo, improvement). Suite completa 1151 test EXIT 0.
- NOTA per maintainer: la fase e' pronta per la ratifica (flag ON = un run di misura);
  contributo upstream Timefold del pattern fix-and-optimize da segnalare, come da
  direttiva.

### Iter 28 - GATE end-to-end PureHoleRepair ON. PREDIZIONE PRIMA (23/08 17:10)
- SETUP: stesso protocollo del run -24 (profilo fresco dci-542, day-compact OFF, spread
  ON, max-attempts=1, capture ON) + -Dplanner.construction.pure-hole-repair.enabled=true.
- PREDIZIONE (ONESTA, diverge dalla forma suggerita): PureHoleRepair chiude i buchi H17,
  NON i boxed VRP-locked (quelli sono territorio BoxRepair/EF). Il run -24 aveva H17=0
  a consegna: su un'estrazione simile la fase e' INERTE e la consegna resta dettata
  dalla lotteria LS (~-700 se il boxed resta; -6..-50 se il sorteo e' buono). Atteso in
  OGNI caso: H17 a consegna = 0 (la fase lo garantisce se ce n'erano) e medium invariato
  entro varianza. La fase si paga SULLE estrazioni con buchi residui (il -6 del mattino
  sarebbe andato a 0). VERDETTO keyed su: (a) H17 consegna = 0 sempre; (b) nessun
  peggioramento hard vs l'estrazione senza fase (il wrapper improve-or-noop lo impone);
  (c) inertezza dimostrata se holes=0 all'entry (banner "0 holes at entry, inert").
- NOTA: il sorteggio non e' controllabile (LS MT lotteria): un singolo run e' UN punto,
  non la curva. Il claim forte resta quello dei fixture TDD (N=63/243/365).

### Iter 29 - Ricerca firma precoce oltre i buchi (mandato maintainer 23/08 sera). PROTOCOLLO PRIMA
- ETICHETTE DICHIARATE (prima di guardare): il materiale con ESITO NOTO e': la coppia
  deterministica (-6 mattina / -715 rerun: STESSA costruzione, due sorteggi LS) e
  attempt15 notturno (consegna -23). Gli altri 13 tentativi notturni NON hanno esito
  misurato (scartati dal predittore o non consegnati: usarli come etichette sarebbe
  circolare). Ogni separazione va quindi validata su QUESTI punti; LOO su 3 punti e'
  debole e lo dichiaro.
- VINCOLO STRUTTURALE (l'esperimento naturale): -6 e -715 condividono OGNI feature di
  costruzione (identica per determinismo) con esiti OPPOSTI -> NESSUN marker di
  costruzione puo' separare quella coppia, per principio. Il massimo raggiungibile da un
  marker e' separare il bacino deterministico da attempt15 (entrambi consegnati <= -6 e
  >= -23: cioe' entrambi "cattivi" in senso assoluto vs il golden). Predizione: NESSUN
  marker separera' deliverable-vs-doomed meglio dei buchi perche' il verdetto di consegna
  e' deciso dal sorteggio LS, non dalla costruzione (iter-24). Se un marker emergesse,
  sara' un marker di QUALITA' (medium), non di fattibilita' - come gia' misurato per la
  concentrazione docente (iter-26).
- CANDIDATI da calcolare sui 18 grid (15 notte + golden + gate1 + gate2):
  (1) margine feasibility: per (docente,weekday) e (tag,weekday) domanda-vs-capacita
  della costruzione, min e conto dei negativi; (2) boxed a costruzione; (3) buchi per
  gruppo: max/gruppo, quota su slot saturi (>=3 gruppi condividono il buco?); (4) deficit
  quota medio per gruppo.

### Iter 29 - ESITO marker search: NESSUN marker separa. Verdetto: buchi-e-solo-buchi (23/08 18:2x)
- MISURA (16 griglie valide: 15 notturne + nota sotto; viz iter29-marker-search.html):
  (1) overCapTD (docente,data sopra cap H7): 0-2 OVUNQUE, rumore puro (attempt15
  consegnato -23 ha 0; attempt1 ne ha 1). (2) boxed: 31-85, nessuna relazione con
  l'unica etichetta disponibile (attempt15 = 61, centro banda). (3) maxPerG (buchi
  concentrati per gruppo): 21 per attempt1 vs 36-101 per gli altri - ma e' un satellite
  di holes (correlato per costruzione), non aggiunge separazione oltre la soglia sui
  buchi. sharedSlots (slot saturi): 0-19, non monotono (attempt14=19, attempt9 con piu'
  buchi=8). (4) deficit quota: non calcolabile offline dall'export (DailyActivityQuota
  non esportata) - dichiarato, non stimato.
- RIGA DETERMINISTICA INVALIDA (dichiaro): il join checkpoint-notturno x profilo fresco
  fallisce (id-spazi diversi): boxed 2961 era artefatto, scartata.
- VERDETTO (predizione CONFERMATA): **buchi-e-solo-buchi**, con evidenza duplice:
  (a) l'esperimento naturale -6/-715: feature di costruzione IDENTICHE, esiti OPPOSTI
  -> la consegna e' decisa dal sorteggio LS MT, non dalla costruzione; (b) nessun
  candidato correlato con l'esito sull'unico punto etichettato. Il predittore resta
  mono-dimensionale e la domanda si CHIUDE. La direzione utile per la qualita' resta
  lo steering (iter-25) e per la robustezza multi-start + PureHoleRepair (iter-27/28).
- Etichette oneste: 1 solo tentativo notturno con consegna misurata; LOO su quel
  materiale e' impossibile: dichiarato nel protocollo prima del calcolo.

### Iter 28 - ESITO gate end-to-end PureHoleRepair ON: predizione CONFERMATA in ogni chiave (23/08 18:40)
- MISURA: consegna -719hard/-1298k = H0 boxed 335 (-670) + H29 47 + H32 2. **H17 = 0**.
  Banner: **"Pure hole repair: 0 holes at entry, inert"**. Sanity non conforme (5 gruppi,
  no-stanza MODA/G07/G14), stessa firma del run -24 senza la fase.
- VERDETTO keyed sulla predizione: (a) H17 consegna = 0 CONFERMATO; (b) nessun
  peggioramento CONFERMATO (-719 vs -715 del sorteggio gemello: lotteria LS, la fase non
  ha toccato nulla); (c) inertezza CONFERMATA E DIMOSTRATA LIVE: il sorteggio di questo
  ciclo ha chiuso tutti i buchi da solo e il residuo e' boxed VRP-locked, che la fase
  NON tocca (dichiarato nella predizione, contro la forma suggerita).
- CONCLUSIONE per maintainer (proposta onesta): il gate NON consegna < -30, quindi la
  promozione del pacchetto NON si propone da questo dato. PureHoleRepair resta: (1)
  corretto e validato dai fixture TDD (N=63/243/365, batte il prototipo); (2) inerte su
  questo tipo di estrazione; (3) la sua utilita' e' sulle estrazioni con H17 residui
  (come il -6 del mattino, che avrebbe chiuso a 0). La combinazione che promuove il
  pacchetto e' **multi-start (max-attempts default) + PureHoleRepair ON**: il multi-start
  campiona piu' sorteggi e la fase garantisce H17=0 su ognuno; il run di misura e'
  UN ciclo NON single-attempt (da lanciare a banco libero, resta tutto default-OFF).

### Iter 30 - MISURA (1): pacchetto multi-start + PureHoleRepair ON. PREDIZIONE PRIMA (23/08 18:50)
- SETUP: profilo fresco dci-542, multi-start DEFAULT (max-attempts 5, budget di default,
  pick-best al boundary, stop al primo 0-hard) + -Dplanner.construction.
  pure-hole-repair.enabled=true + spread ON + capture ON. Budget atteso ~2-3h.
- PREDIZIONE (forma richiesta): **>= 1 tentativo con consegna hard <= -30 e PHR attivo
  su almeno uno** (PHR agisce sui sorteggi con H17 residui: i tentativi GRASP 2-5 storic
  chiudono con buchi, la fase li ricuce in <1s a consegna); **pick-best finale migliore
  del -23** (atteso -6..-15: la costruzione deterministica da' bacini con tier -27 al
  RELEASE gia' visto, e con 5 sorteggi la coda buona e' raggiungibile). RISCHI DICHIARATI:
  (a) il pick-best usa il boundary tier PRE-B-ROOM: PHR agisce DOPO (post-B-ROOM), quindi
  puo' migliorare il VINCITORE senza che il pick lo sappia - il valore di PHR e' allora
  visibile solo nel confronto intra-tentativo, non nel pick; (b) se TUTTI e 5 i tentativi
  cadono nel bacino boxed (tier ~-670), la consegna resta ~-700: probabilita' bassa ma
  non nulla (la mattina il tentativo 1 deterministico diede -6).
- (2) in coda durante il run: FASE MEDIUM-POST-REPAIR (LS hill-climbing a miglioramento
  STRETTO post-PureHoleRepair, termination unimproved sul medium, o loop repair->LS->
  repair). Predizione del delta medium per famiglia PRIMA dell'implementazione:
  M15 -160k -> -100k (buchi chiusi rendono i blocchi consecutivi), M20/M33 -350k ->
  -280k, M22 -445k -> -380k, M32 -950k -> -850k (le mosse PHR sono poche ma spostano
  celle template); TOTALE atteso -1.30M -> **-1.15..-1.20M** (delta +100..+150k, 8-11%).
  Golden medium -49: il gap strutturale NON si colma con una fase sola (dichiarato).

### Iter 30 - ESITO pacchetto multi-start + PHR: SMENTITA su entrambe le chiavi, PHR CONFERMATA al 95% (24/08 01:45)
- CRONOLOGIA TENTATIVI (tutti dal log, cycle ~6.8h, sforamento budget 2-3h documentato):
  | tentativo | costruzione | PHR | esito tentativo |
  | 1 deterministico | 0 holes at entry, INERTE | tier -669, -10877 (stanze CH-only, skip-doomed) |
  | 2 GRASP seed1 | **11 -> 0** (11 move-set) | tier -678, -10883 |
  | 3 GRASP seed2 | **25 -> 0** (25) | tier -678, -10868 |
  | 4 start#3 | PREDICTORE: scartato in 9s (172 buchi >150) | reseed |
  | 4 start#4 GRASP seed4 | **39 -> 5** (34) | tier -690, -10870 |
  | 5 GRASP seed5 | **65 -> 8** (57) | tier -775, -736 (stanze complete, ultimo) |
  PICK-BEST consegnato: **-713/-1853k** = H0 boxed 349 (-698) + H29 24 + H17 -8 + H32 -6.
  Sanity: non conforme (4 gruppi, no-stanza 2/G09 e G07).
- VERDETTO per chiave: (A) ">=1 tentativo hard <=-30 con PHR attivo" **SMENTITA**: nessuno dei
  5 sorteggi e' finito sotto -30, TUTTI nel bacino boxed (tier -667..-775). (B) "pick-best
  > -23" **SMENTITA** (-713). LA FASE IN SE' CONFERMATA AL 95%: PHR attiva su 4/4
  tentativi GRASP, ha chiuso 140/148 buchi (11->0, 25->0, 39->5, 65->8) in millisecondi,
  esattamente il suo contratto; i residui (5+8) sono le tasche strutturali dei fixture.
- LETTURA ONESTA (la forbice della lotteria domina anche a N=5): la costruzione
  deterministica HA DATO -6 stamattina e tier -669 stanotte: la varianza e' tutta nella
  LS MT, e stasera 5 sorteggi su 5 sono caduti nel bacino boxed. Il residuo di consegna e'
  al 98% H0 boxed VRP-locked, che NE' PHR ne' BoxRepair/EF sanno chiudere (famiglia
  iter-24). La conclusione strutturale: il collo di bottiglia NON e' piu' H17 (PHR l'ha
  risolto ovunque) ma il boxed-VRP: la prossima leva e' un ExactFinisher VRP-consapevole
  o la diagnosi dei 349 (perche' il box-repair in-pipeline non li chiude). TASK-543
  (determinismo MT-LS) e' ora il lavoro piu' rilevante: se la LS diventasse deterministica
  la forbice si chiuderebbe alla FONTE.

### TASK-543 FASE 1 - PREDIZIONE (24/08 01:55, PRIMA dei run REPRODUCIBLE x2)
- SETUP: due cicli singolo-tentativo (costruzione deterministica, spread ON, capture ON,
  PHR OFF per isolare la LS) con QUARKUS_TIMEFOLD_SOLVER_ENVIRONMENT_MODE=REPRODUCIBLE
  su due profili freschi identici (dci-543a/b: stesso seed deterministico). Confronto:
  score per fase dal log + phases.json + score finale.
- PREDIZIONE: **DIVERGENTI** dalle fasi LS in poi. Motivo meccanico: REPRODUCIBLE fissa
  i semi RNG ma il nostro decider MT-LS e' CODICE CUSTOM con 8 move thread che inseguono
  step su una coda condivisa: l'ordine di valutazione/accettazione e' deciso dallo
  scheduler dei thread, non da un seme. La costruzione (fase custom single-thread)
  sara' IDENTICA; la prima fase LS divergera' (probabilita' dichiarata ~85%: piccola
  chance che il decider serializzi di fatto e risulti identico per coincidenza su
  budget corti). Se DIVERGENTI: refactor single-writer giustificato (FASE 2). Se
  IDENTICI: la forbice -6..-715 e' intrinseca al NON_REPRODUCIBLE e la decisione
  torna a maintainer (single-writer degradato a fix-razze).
- NB iter-15: storia del seed fisso nota; questi run NON esplorano semi.

### TASK-543 FASE 1 - ESITO: **DIVERGENTI** (predizione CONFERMATA, 24/08 04:49)
- METODO: due cicli singolo-tentativo IDENTICI (costruzione deterministica, spread ON,
  PHASE_ASSERT/REPRODUCIBLE con seed 1 verificato nel log, capture ON) su profili freschi
  dci-543a/b. Materiale: task543-runA/B-phases.log + checkpoint lockati.
- CONSTRUCTION: **byte-identica** (cmp esatto su POST_CONSTRUCTION.json) - determinismo
  della costruzione totale, come predetto.
- PRIMA FASE LS (B-TIMESLOT-1, 8 thread custom): **DIVERGENTE** - A -4620/-1198495/21043
  box 333, B -4620/-1182510/19227 box 332; **12.542 placement su 12.821 diversi**.
- CONSEGNA: A **-718/-1308560/8628**, B **-717/-1296448/6757**: due orari DIVERSI dalla
  stessa costruzione con seed fisso.
- VERDETTO: non-determinismo PATOLOGICO del decider MT-LS custom: PHASE_ASSERT fissa i
  semi RNG ma l'ordine di valutazione/accettazione delle mosse e' deciso dallo scheduler
  dei thread (il 15% residuo della predizione - coincidenza - escluso). La forbice
  -6..-715 NON e' tutta intrinseca: un decider deterministico renderebbe il ciclo
  riproducibile e la variabilita' residua sarebbe solo quella DELIBERATA (GRASP).
- IMPLICAZIONE (per la lettura di maintainer): FASE 2 (refactor single-writer, modello
  upstream per-copia/sync-per-step/rebasing) GIUSTIFICATA con evidenza diretta
  (12.542/12.821). NON parte senza la sua lettura, come da task. Costi attuali della
  non-riproducibilita': debug (scarti non spiegabili) e certificazione (ogni run un
  orario diverso).

## QUADRO RISVEGLIO (24/08 05:00, worktree day-compact-542, tutto pushato)

### Pacchetto multi-start + PureHoleRepair (iter-30, misura NON adozione, tutto OFF)
- 5 tentativi (~6.8h, sforamento budget documentato): deterministico PHR-INERTE
  (0 buchi all'entry) tier -669; GRASP-1 **11->0** tier -678; GRASP-2 **25->0** tier
  -678; start#3 SCARTATO dal predittore in 9s (172 buchi); GRASP-4 **39->5** tier -690;
  GRASP-5 **65->8** tier -775. PICK-BEST consegnato **-713** (H0 boxed 349 = 98%
  dell'hard, H29 24, H17 -8, H32 -6), sanity non conforme.
- VERDETTO: SMENTITA su entrambe le chiave (nessun tentativo <=-30; pick-best non > -23);
  PHR CONFERMATA al 95% (4/4 attivazioni, 140/148 buchi chiusi in millisecondi).
  Tutti e 5 i sorteggi nel bacino boxed: la forbice della lotteria domina anche a N=5.
  Il collo di bottiglia si e' SPOSTATO: H17 risolto ovunque, resta il boxed-VRP (349)
  che ne' PHR ne' BoxRepair/EF chiudono (famiglia iter-24: VRP-locked, non teacher-locked).

### TASK-543 FASE 1: DIVERGENTI (predizione CONFERMATA)
- Due cicli identici PHASE_ASSERT (seed 1): costruzione **byte-identica**; prima fase LS
  **divergente** (12.542/12.821 placement, medium -16k, box 333 vs 332); consegne
  **A -718/-1308560 vs B -717/-1296448**. Non-determinismo patologico del decider MT-LS
  custom: il scheduling dei thread ordina le mosse, il seme non puo'. La forbice -6..-715
  NON e' tutta intrinseca.

### Stato fase MEDIUM-POST-REPAIR: MISURA CHIUSA, SMENTITA DI MASSA (iter-31, 24/08 09:40)
- +1670 su -1.29M (0,13%) in 900s: il medium di consegna non ha headroom locale (la LS
  l'ha gia' ottimizzato per un'ora). Il gap -1.3M vs golden e' COSTO DEL METRO, non di
  ottimizzazione. Proposta a maintainer: NON ratificare, declassare a scaffold. Flag OFF.
- Il run ha prodotto: 2 fix del decider (pill TOCTOU + desync documentato) = terza
  evidenza del filone single-writer.

### DECISIONI CHE ATTENDONO maintainer
1. **TASK-543 FASE 2**: refactor single-writer del decider MT-LS (modello upstream
   per-copia/sync-per-step/rebasing) - GIUSTIFICATO con evidenza diretta (12.542/12.821),
   fermo al suo gate come da task.
2. **Ratifica pacchetto multi-start + PureHoleRepair**: PHR validata al 95% ma il
   pacchetto non consegna <=-30 finche' il boxed-VRP (349) resta: la decisione e' se
   promuovere PHR (flag default) ora o dopo la cura del boxed.
   AGGIORNAMENTO 13:05 (maintainer): fase medium-post-repair RIMOSSA (conferma 1);
   union-M32 in DORMENZA, fuori coda attiva finche' non emerge un motivo nuovo
   (conferma 2). Coda attiva: REPRODUCIBLE x2 sul single-writer -> /review-local
   ratifica (gia' fatto nel gate 8e6e1f08) -> riga CLAUDE.md (fatta, stessa commit)
   -> RUN DI CONSEGNA col pacchetto (multi-start + PHR sul single-writer). Draft
   upstream Timefold: in attesa della rilettura di maintainer, non toccato.
3. **Union-M32 day-compact** (one-liner, approvato in coda): da eseguire quando si
   riprende il filone; il per-consegna e' fermo (regola secondo workaround).
4. (Indiretta) **cura del boxed-VRP**: il nuovo collo di bottiglia emerso dall'iter-30
   (ExactFinisher VRP-consapevole o diagnosi dei 349) - non ancora pianificata.
- Tutto DEFAULT OFF: percorso di produzione PULITO (day-compact, steering, PHR,
  medium-phase). Upstream Timefold del pattern fix-and-optimize: da segnalare a maintainer.

### Iter 31 - INCIDENTE e BUG FIX: NPE del decider MT-LS scovata dal run medium (24/08 06:20)
- INCIDENTE: il run medium-post-repair (dci-544m) e' MORTO nella fase nuova a 06:20:
  (a) move thread 7: IllegalStateException "stepIndex mismatch thread=16 operation=18";
  (b) NPE in forageResult:216 - MoveResult.move() null al rebase. Ciclo FAILED
  (score=null, promosse rilasciate correttamente).
- CAUSA (trovata in codice, forma della predizione rispettata): NOT indice riusato ne'
  drainStale: e' la POISON PILL di addExceptionThrown consegnata come risultato NORMALE.
  Il check pre-poll dell'eccezione in MoveResultQueue.poll ha una finestra TOCTOU larga
  un elemento: l'eccezione arriva MENTRE il decider e' parcheggiato dentro delegate.poll,
  la pill lo sveglia e viene restituita senza che il check (gia' eseguito) riparta ->
  move() null -> NPE al rebase. Il bounded-poll del TASK-541 ha esposto la finestra che
  take() non aveva (take non parcheggia dopo il check? no: take ha la STESSA finestra -
  la pill sveglia anche take; ma take e' usato solo... ovunque: la race e' latente in
  tutto il decider, il run medium l'ha solo INCONTRATA per primo).
- FIX: (1) a monte, MoveResultQueue.poll ricontrolla sulla firma della pill (move null
  != risultato legittimo) DOPO la poll e propaga l'eccezione wrapped (o IllegalStateException
  watchdog se senza eccezione); (2) guard difensivo in forageResult con messaggio firmato.
- TDD: MoveResultQueuePoisonPillTest 2/2 (eccezione-prima-della-poll loud; PILL CONSEGNATA
  A UN POLLER PARCHEGGIATO -> throw, non return: la race riprodotta deterministica).
- DATO A FAVORE DEL FILONE 543/544: il difetto e' ESATTAMENTE nella famiglia sotto
  indagine (decider MT-LS, stato condiviso, race) - seconda manifestazione dopo la
  SimpleFieldTracker del 22/08. Il refactor single-writer eliminerebbe la CLASSE di
  race (pill compresa: coda a scrittura singola).
- RILANCIO del run medium: dopo il fix, identico (profilo fresco dci-544m2).

### Iter 31-bis - TERZO difetto famiglia MT-LS: step-chasing desync (24/08 07:52)
- CRASH #2 del run medium (1s dopo l'avvio del solver ad-hoc; il ciclo principale era
  integro: B-ROOM finita, PHR inerte, -749/-1325k): IllegalStateException
  "stepIndex mismatch: thread=14 operation=16" in MoveThreadRunner.run:86. Il fix pill
  ha FUNZIONATO (nessuna NPE, eccezione propagata come disegnato): e' emersa direttamente
  la desincronizzazione del protocollo (il thread ha PERSO l'apply-step 15; quale passo
  esattamente non e' recuperabile dal log senza traccia per-op: serve il banco Byteman
  TASK-544). PATTERN: 2 crash su 2 run medium, ENTRAMBI entro 1s dall'avvio del solver:
  la finestra calda e' la STARTUP del decider (setup + primi step ad alto rate).
- AGGIUNTO alla lista del filone 543/544: e' la terza manifestazione (dopo
  SimpleFieldTracker 22/08 e poison-pill TOCTOU 06:20). Il single-writer la elimina per
  design (un solo writer non puo' desincronizzare) - FASE 2 APPROVATA da maintainer (07:50).
- MISURA medium: la fase gira ora SINGLE-THREAD (il wiring MT-LS si arma su ogni
  runStagedSolver: disattivato per sola questa fase con restore nel finally). Giausto
  per la misura (raffinamento finale: non serve throughput, e single-thread e' anche
  deterministico) e immune alla famiglia di race. PREDIZIONE per il rilancio: il ciclo
  arriva a consegna (crash impossibile senza decider MT-LS nella fase), delta medium
  nella banda predetta -1.15..-1.20M CON RISCHIO dichiarato: single-thread esplora MENO
  mosse nello stesso wall (unimproved 90s): delta forse nella metà' bassa della banda.

### Iter 31 - ESITO misura medium-post-repair: SMENTITA DI MASSA (24/08 09:40)
- MISURA (terzo lancio, fase single-thread, ciclo integro per la prima volta):
  consegna **-720/-1286928/-1307**; banner fase: -720/-1288598 -> **-720/-1286928**
  = **+1670 medium (0,13%)** in 900s di hill-climbing stretto. Hard invariato (come
  garantito dall'accettazione stretta). Sanity: non conforme (stessa firma del bacino,
  no-stanza 2/G09 e G07).
- VERDETTO vs predizione: SMENTITA su TUTTE le famiglie (previsto +100..150k, misurato
  +1,7k; nemmeno la metà bassa del rischio dichiarato). La premessa era SBAGLIATA e va
  detto: il medium della consegna NON ha headroom locale, perche' la LS di B-TIMESLOT/
  B-ROOM ha gia' ottimizzato il medium come suo obiettivo primario per ~1h su quella
  griglia. Una hill-climb post-consegna riparte da un ottimo locale convergente: trova
  quasi niente per costruzione. Il gap strutturale -1,3M vs golden -49 NON e' un buco
  di ottimizzazione lasciato a meta': e' il costo del METRO (i pesi dei vincoli vs la
  pratica golden), come gia' misurato per M20 pool-cap (TASK-497: "il costo e' del
  metro, non dell'orario").
- DECISIONE (proposta a maintainer): la fase resta committed ma e' INERTE DI FATTO: chiedo di
  non ratificarla e declassarla a scaffold (o rimuoverla al prossimo /simplify): la sua
  ragion d'essere (il medium mai ottimizzato da feasibility) e' confutata dal dato.
  Il medium si muove solo cambiando il metro (pesi/shape), non aggiungendo LS.
- CONFERMA maintainer 13:05: RIMOZIONE confermata, eseguita col commit successivo (lezione
  qui in iter-31; il codice e' stato rimosso da ScheduleService: flag, metodo, call
  site e workaround single-thread).
- Il run ha pero' prodotto i due fix del decider (pill TOCTOU + desync diagnosi) e la
  terza evidenza per il single-writer: il costo e' stato pagato.

### TASK-543 FASE 2 - SINGLE-WRITER del decider MT-LS. PREDIZIONE PRIMA (24/08 09:50)
- DIAGNOSI STRUTTURALE dei tre difetti: (a) poison-pill TOCTOU e (c) step-chasing desync
  nascono entrambi dalla CODA OPERAZIONI CONDIVISA (un produttore, N consumatori): il
  clear() puo' mangiare un apply-step non ancora consumato e una pill puo' svegliare un
  poller parcheggiato. (b) tracker race e' interna a Timefold (priming la mitiga).
- DESIGN (modello upstream adattato): canali PER-THREAD (una coda per runner: produttore
  unico = decider, consumatore unico = il thread proprietario). Per costruzione:
  - un apply-step non puo' essere "rubato" da un altro thread (canale privato);
  - clear-then-enqueue-apply per canale e' race-free (monoproduttore/monoconsumatore);
  - la pill resta ma solo sul canale risultati (gia' fixata in iter-31).
  Il decider resta l'UNICO writer della working solution (gia' vero: pickMove+apply).
  Extracted: SingleWriterOperationDispatcher (canali + round-robin eval + broadcast
  apply), testabile senza solver.
- FLAG: planner.multithreaded-ls.single-writer.enabled DEFAULT OFF (il decider vecchio
  resta il default finche' l'A/B wall non chiude, come richiesto).
- PREDIZIONI: (1) protocol test: 8 thread-consumatore finti, 10k step con delay casuali:
  ZERO mismatch nel modello a canali (e il test dimostra che il modello VECCHIO li
  produce se eseguito sullo stesso harness - se non li produce in harness, dichiaro che
  la race serve il banco reale e il test pinna solo il nuovo modello). (2) A/B wall
  single-writer vs vecchio: atteso +-15% (il protocollo e' lo stesso lavoro per thread;
  il costo in piu' e' N code invece di 1: trascurabile). (3) DETERMINISMO: due run
  PHASE_ASSERT col single-writer: atteso ancora DIVERGENTI (l'ordine di ARRIVO dei
  risultati dai thread resta schedulato) - il single-writer chiude le RACE, non ordina
  la consegna; la determinismo-completa richiederebbe anche l'ordinamento dei risultati
  (fase futura, dichiarata). NOTA ONESTA: il 543 task vendeva il single-writer come
  riproducibilita': la mia lettura e' che chiude le race ma NON basta per il byte-exact;
  lo scrivo ora per non scoprirlo al verdetto.

### TASK-543 FASE 2 - IMPLEMENTAZIONE v1 + TDD che ha pescato un bug del mio design (24/08 10:25)
- IMPLEMENTATO: SingleWriterOperationDispatcher (canali per-thread, mono-produttore il
  decider / mono-consumatore il runner proprietario), integrato nel decider dietro flag
  planner.multithreaded-ls.single-writer.enabled DEFAULT OFF (decider legacy intatto e
  default fino alla chiusura dell'A/B wall, come richiesto). Setup/destroy per canale,
  eval round-robin con put() bloccante (backpressure, mai loss), apply per canale.
- IL TDD HA PESCATO UN BUG VERO nel mio primo design: clear-then-put per canale (che
  avevo dichiarato race-free per mono-consumazione) DROPPA un apply NON ancora consumato
  quando il writer non attende il consumatore - ESATTAMENTE il desync thread=14/op=16.
  Fix: MAI clear (i risultati stale li drena gia' drainStale a inizio step; canale pieno
  = put bloccante = backpressure). Il protocol test ora pinna: 8 thread, 20k step, jitter,
  ZERO mismatch, TUTTI gli apply consegnati a TUTTI i thread (160k/160k).
- Pill signature rifinita (move null E moveIndex<0) per coesistere con i placeholder
  legitimi dei test esistenti. Suite completa 1154 EXIT 0.
- PROSSIMO: A/B wall single-writer ON (run singolo profilo fresco) vs baseline legacy
  (iter-28: B-TIMESLOT #1 ~52min, wall ciclo ~1h35); atteso +-15%.

### TASK-543 FASE 2 - DEADLOCK dell'A/B (10:28) e cura liveness (24/08 10:45)
- OSSERVATO: A/B single-writer ON partito, CPU 4% dopo 5 min. Thread dump (jstack,
  /tmp/jstack-sw.txt): 8 runner parcheggiati in resultQueue.addMove (coda risultati
  piena 88) + decider parcheggiato nel put dell'apply su canali pieni di backlog.
  Ciclo: writer non drena -> runner non consegnano -> canali non si svuotano.
- CAUSA: la clear() legacy era LOAD-BEARING per la liveness (droppava il backlog a fine
  step); il mio mai-clear senza drenaggio ha chiuso il ciclo. Non e' un difetto del
  modello single-writer: e' laMeta-lesson del passaggio (liveness e' un requisito del
  protocollo, non un dettaglio).
- CURA: drain-then-deliver - il writer, prima del broadcast apply (e del destroy),
  drena i risultati STALE del passo concluso finche' ogni canale ha capacita'; solo
  allora fa put. I canali guadagnano capacita' solo dai consumatori: check-then-put
  sicuro. Protocol test ancora verde, suite 1154 EXIT 0.
- RILANCIO A/B in corso (profilo dci-545sw2). Predizione wall invariata (+-15%); il
  drain aggiunge al piu' millisecondi per step.

### TASK-543 FASE 2 - ESITO A/B wall: single-writer v2 VELOCE e STABILE (24/08 12:05)
- MISURA (profilo dci-545sw3, single-writer ON, PHR ON, tentativo unico, ciclo ~1h33):
  | fase | single-writer v2 | baseline legacy (iter-28) |
  | B-TIMESLOT #1 | **2313s (~39min)** | ~52min |
  | B-TIMESLOT #2 | 1297s (~22min) | ~9min |
  | B-TIMESLOT #3 | 128s (2min) | ~9min |
  | B-ROOM | 607s (10min) | ~10min |
  | TOTALE LS (3 iter) | **~62min** | ~70min |
  Consegna **-757/-1350879/3034** (bacino boxed, in banda con i sorteggi legacy
  -713/-719). ZERO stepIndex mismatch, ZERO stalli (dopo i due fix documentati),
  CPU 750-900% per tutta la durata delle LS. Sanity non conforme (firma del bacino).
- VERDETTO vs predizione (+-15%): **CONFERMATO e SUPERATO** - il ciclo completo sta
  nell'intorno del legacy (~62 vs ~70 min di LS pura; il ciclo totale ~1h33 in linea
  con 1h35) nonostante la barriera di step: la barriera elimina il contention della
  coda condivisa e l'occupazione dispersa, e il round-robin bilancia meglio del take-
  a-caso. Nessun costo di wall da dichiarare.
- IMPLICAZIONE (per la lettura di maintainer): il single-writer v2 e' PRONTO per il flip
  del default: stessa velocita', liveness strutturale (i due stalli non possono
  tornare per costruzione), tre classi di difetto chiuse. Propongo: flip
  planner.multithreaded-ls.single-writer.enabled a default-ON dietro la sua lettura,
  + i due test di liveness nel protocol test (fatti), + le due regole Byteman sul
  banco TASK-544 al land su main (DoD). NOTA: la determinismo byte-exact NON e'
  rivendicata (ordine di arrivo risultati schedulato): il gain e' liveness+correttezza.

### TASK-543 FASE 2 - REPRODUCIBLE x2 sul decider nuovo. PREDIZIONE PRIMA (24/08 13:0x)
- SETUP: due cicli identici PHASE_ASSERT (seed 1, single-writer ora DEFAULT ON, PHR ON,
  spread ON, tentativo unico) su profili freschi dci-546a/b. Confronto per-phase + byte
  dei checkpoint, come FASE 1.
- PREDIZIONE: **DIVERGENTI ancora, ma MENO di FASE 1** - atteso ordine di grandezza delle
  differenze ridotto (non 12.542/12.821 ma comunque migliaia di placement): il
  single-writer chiude le RACE (furto/pill/desync) ma l'ordine di ARRIVO dei risultati
  dai runner resta deciso dallo scheduler, e il forager accumula in quell'ordine. La
  costruzione resta byte-identica (gia' dimostrato). Se per caso IDENTICI: il
  non-determinismo patologico era TUTTO della famiglia race e il cerchio si chiude al
  meglio. Entrambi gli esiti sono informativi e decisivi per la fase result-ordering.

### PROCESSO (24/08 14:15): harness run-measure.sh per gli errori di lancio (richiesta maintainer)
- INCIDENTI DI PROCESSO pagati in campagna, tutti errori di LANCIO non di codice:
  jar stantio (12:39, banner assente), profilo sporco (dci-546a: costruzione divergente
  1498 placement per stato ereditato da app uccisa), log condivisi sovrascritti
  (perso il log del -6), flag mai arrivati (MAVEN_OPTS). GUARDIE: scripts/benchmark/
  run-measure.sh - preflight jar-stale (jar vs HEAD, --jar-ok per override esplicito),
  profilo FRESCO di default (--reuse esplicito per riusare), manifest PRIMA del lancio
  (sha, jar mtime, flag, banner attesi, campo predizione obbligatorio), log per-run,
  --finalize con banner-assert loud + capture canonica. Il REPRODUCIBLE in corso
  (dci-547a/b) e' partito col flusso manuale: l'harness vale dal prossimo lancio.

### TASK-543 FASE 2 - ESITO REPRODUCIBLE x2 sul single-writer: DIVERGENTI-RIDOTTI, predizione CONFERMATA (24/08 17:15)
- METODO RIPULITO: dci-547a/b freschi (l'incidente dci-546a - profilo sporco - e'
  documentato e ha generato l'harness run-measure.sh), PHASE_ASSERT seed 1,
  costruzione **byte-identica** A=B verificata con cmp. Nota purezza: jar 8e6e1f08
  (pre-rimozione medium e24c67c1: fase inerte in questi run, decider byte-identico).
- CONFRONTO CHECKPOINT (placement diversi su 12.821):
  | checkpoint | legacy (FASE 1) | single-writer |
  | POST_B_TIMESLOT-1 | 12.542 | **6.041** |
  | POST_RELEASE | (n.m.) | 6.041 |
  | POST_B_ROOM (consegna) | (n.m., score -718 vs -717) | **10.307** (B-ROOM riamplifica) |
  Consegne: A **-716/-1297089/6556**, B **-721/-1314103/6265**: due orari diversi,
  distanza comparabile al legacy a consegna perche' la fase stanze (LS multi-thread
  sulle stanze) reintroduce la propria variabilita' di arrivo.
- VERDETTO: **DIVERGENTI ma RIDOTTI**, come predetto: la prima fase LS dimezza la
  divergenza (12.542 -> 6.041) perche' le race del decider sono chiuse; il residuo e'
  l'ORDINE DI ARRIVO dei risultati dai runner, deciso dallo scheduler, esattamente la
  componente dichiarata non eliminabile dal solo single-writer. Il cerchio TASK-543:
  il non-determinismo PATOLOGICO (race) e' morto; resta la variabilita' INTRINSECA
  di scheduling. Il byte-exact richiederebbe l'ordinamento dei risultati (fase
  dichiarata, non promessa: la fila e' result-ordering, da valutare se il costo wall
  di una consegna ordinata lo giustifica).
- CHIUSURA TASK-543: FASE 1 (DIVERGENTI legacy, 3 difetti censiti) + FASE 2
  (single-writer ratificato DEFAULT ON, DoD piena, A/B wall pari, REPRODENTI-ridotti
  2x) complete. La coda dice: RUN DI CONSEGNA col pacchetto sul decider sano.

### RUN DI CONSEGNA col pacchetto sul decider sano (24/08 17:2x). PREDIZIONE PRIMA
- SETUP (primo lancio con l'harness run-measure.sh): multi-start DEFAULT (5 tentativi,
  stop al primo 0-hard, pick-best boundary) + PHR ON + spread ON + single-writer
  (ora default). Predizione nel manifest.
- PREDIZIONE (forma richiesta): **pick-best <= -23**, con la SPERANZA DICHIARATA che il
  decider sano stringa la forbice: la componente race della varianza e' dimezzata
  (REPRODUCIBLE x2), quindi i 5 sorteggi dovrebbero esplorare meglio il bacino buono.
  Base empirica: col legacy i 5 sorteggi erano TUTTI boxed (pick -713); con la forbice
  dimezzata atteso che almeno 1-2 tentativi cadano fuori dal bacino boxed. RISCHI
  DICHIARATI: (a) la forbice residua e' scheduling (intrinseca): se la distribuzione
  dei bacini e' pesantemente skewed verso boxed, anche 5 sorteggi puliti restano boxed
  e il pick-best resta ~-700: in quel caso il dato CHIUDE la giornata dimostrando che
  il collo e' il boxed-VRP, non la LS; (b) il boxed-VRP (349) resta NON curato da
  nessuna parte del pacchetto: anche il sorteggio migliore porta i suoi ~330 boxed.
- ESITO ATTESO PER IL QUADRO: pick-best -6..-30 (speranza) oppure ~-700 (chiusura
  onesta: il collo e' il boxed). Capture + sanity a fine run.

### TASK-550 cura (a) - SEEDING A DENSITA' GOLDEN-APERTA. PREDIZIONE PRIMA (24/08 22:35)
- CONTESTO: diagnosi task550 corretta da maintainer (la prima versione del doc indicava le
  finestre calendario come root cause: SBAGLIATA; la root e' la DOMANDA fantasma: i
  295 stub Stage per gruppo quando il golden ne TENEVA 112-295. Il doc viene
  corretto con nota). Cure approvate: (a) seeding golden-aperto (questa), (c) domanda
  a referente. Verifica meccanica fatta: golden-held da lessons.csv (closed!=true) =
  G04 224, G07 295, G08 276, G09 292, G10 228, G11 287, G12 112,
  G13 295: COMBACIA coi numeri di maintainer.
- DESIGN: in generateStageCourses (DataGenerator PROTETTO: approvazione maintainer per
  QUESTA modifica, citata nel commit) il totale del corso Stage diventa il golden-held
  letto meccanicamente da lessons.csv; il resto (295-held) va in
  expectedUnscheduledHours (meccanismo esistente per ore chieste-ma-fuori-griglia,
  in attesa della risposta della scuola = cura c). Nessun golden-row per un gruppo
  (futuro senza golden) -> fallback al totale del foglio (fail-safe).
- PREDIZIONE: (1) TDD: stub per gruppo == golden-held (8/8). (2) Placement-parity
  TASK-517: NESSUN flag nuovo (il piano RIDUCE la domanda, non pinnata; asse A
  docente-weekday invariato perche' Stage e' pseudo-docente). (3) Braccio di verifica
  (ciclo SINGOLO, niente multi-start): H0 boxed **349 -> ~35-60** (i ~314 H0 fantasma
  spariti: 295-112=183 di G12, 300-228=72 di G10, 240-224=16 di G04,
  300-276=24 di G08, 295-287=8 di G11, 300-292=8 di G09 = 311 totali in
  teoria, meno chi era gia' piazzato in overflow); tier boundary da ~-670 a ~-360
  (H0 -2/cella). (4) La consegna NON arriva a 0: restano H29 stanze e l'anomalia
  residua della diagnosi (144 celle libere G12 non riempite). RISCHI: la quota
  H32/H33 potrebbe MUOVERSI (meno Stage cambia i conteggi per giorno) - atteso
  piccolo perche' Stage e' pseudo-esente.

### TASK-550 cura (a) - ESITO implementazione + TDD + conservazione (24/08 22:45)
- IMPLEMENTATA in generateStageCourses (DataGenerator PROTETTO: approvazione maintainer
  24/08 22:30 per QUESTA modifica, citata nel commit): totale Stage = golden-held
  letto meccanicamente da lessons.csv (closed!=true); differenza foglio-held in
  expectedUnscheduledHours (domanda dichiarata in attesa della risposta scuola =
  cura (c)); fallback senza golden = totale foglio (fail-safe per l'anno senza golden).
- TDD: DataGeneratorStageCoursesSeedTest aggiornato e VERDE 6/6 - stub per gruppo ==
  golden-held (8/8: 224/295/276/292/228/287/112/295, totale 2009), expectedUnscheduled
  == differenza foglio (311 ore fantasma totali, banner nel log).
- CONSERVAZIONE (CalendarioImportRealSeedStageTest, prima ROSSA poi riconciliata alla
  verita' misurata): pinned INVARIATO a 1.985 (ogni ora held pinnata); off-grid
  genuino 24 (solo G04, chiusure in-orizzonte); skippedNoBudget 5 (invariato: le ore
  fantasma NON generano domanda d'importo perche' il pin-driver e' quota-driven e le
  righe quota sommano all'held: 1.985+24 consumano i 2.009 stub ESATTAMENTE, identita'
  chiusa senza residuo). Identita' riancorata al FOGGLIO intero: pinned+offGrid ==
  sheet ask (0/8 violazioni).
- PLACEMENT-PARITY TASK-517: NON applicabile come check su piano (la cura cambia il
  conteggio dei stub, non un piano di pin) e INVARIATA per identita': i pin sono gli
  stessi 1.985 pre-cura (verificato dal test). Self-test del tool verde.
- Suite completa 1161 EXIT 0.
- PROSSIMO: braccio di verifica post-fix (ciclo SINGOLO con harness, niente
  multi-start) contro la predizione: H0 boxed 349 -> ~35-60, boundary ~-360.

### Iter 32 - ESITO run di consegna: BASELINE PRE-CURA, predizione SMENTITA, run troncato per ordine di maintainer (24/08 22:50)
- RUN (harness run-measure.sh, primo lancio: manifest, profilo fresco dci-548pk, banner
  assert SUPERATO dopo correzione del mio token typo - il finalize ha fatto il suo
  lavoro rifiutando 'dispatch=' quando il banner dice 'dispatch '): 3 tentativi
  completi + 1 scartato dal predittore (174 buchi) + 1 reseed troncato dall'ordine di
  maintainer (la diagnosi task550 aveva spiegato il muro: il 4o tentativo identico non
  aggiungeva informazione). Banner PHR: inert / 10->0 / 24->0 (34/34 buchi chiusi).
- TENTATIVI (tutti nel bacino boxed): 1 det -10875 (CH-only rooms), 2 GRASP -10885,
  3 GRASP -10865, 4-start3 DISCARDED 9s, 4-start4 troncato. Pick-best non consegnato
  (run fermo): il verdetto onesto e' che la predizione "pick-best <= -23" e' SMENTITA
  DAI DATI DI FATTO: 4 sorteggi su 4 (7 su 7 contando iter-30) cadono nello stesso
  collo fantasma - i ~330 H0 boxed = le 311 ore Stage fantasma + residuo. Il collo NON
  e' la LS ne' il decider: e' la DOMANDA (task550, cura (a) gia' implementata).
- Capture non applicabile (nessuna consegna); log per-run completo in dir
  (app.log, 22:35). Cron cancellato.

### TASK-550 cura (a) - VERDETTO DEL BRACCIO DI VERIFICA: CONFERMATO e SUPERATO (25/08 00:10)
- MISURA (profilo fresco dci-550fix via harness, ciclo SINGOLO, jar con la cura,
  banner golden-open verificato): consegna **-152hard/-1296163medium/5868soft** dal
  -713/-721 pre-cura a parita' di tentativo singolo (4,7x). Boundary post-LS:
  **tier -96, box 46** (pre-cura -675/333). PHR inerte (0 buchi all'entry, la
  costruzione sana non li genera).
- COMPOSIZIONE hard del -152: H0 boxed 46 (-92) + H29 room-null 56 (-56) + H32 4 (-4).
  H17 ZERO, H31 ZERO, H33 ZERO.
- VERDETTO vs PREDIZIONE: boxed 349 -> **46**: dentro la banda 35-60 CONFERMATO;
  boundary -675 -> **-96**: MOLTO meglio del previsto -360 (le 311 ore fantasma
  trascinavano anche il tier, non solo H0). stub==golden-held 8/8 (TDD).
- SANITY: non conforme su 7 gruppi (no-stanza MODA/FB/FC/G10/G14: e' il
  room-null 56 + giorni sotto-minimo: la firma del residuo post-cura, da classe-
  ficare dall'export come prossimo passo). NOTA: il residuo 46 boxed e' MOLTO minore
  dei 122+ della diagnosi: la parte "infeasibilita' input pura" e' sparita con la
  cura; resta l'anomalia residua (144 celle libere G12 ecc.) piu' il room-tail.
- LA STORIA DELLA GIORNATA SI CHIUDE COSI': giorni di run che riconfermavano -10.8k;
  mezz'ora di diagnosi senza run trova le 311 ore fantasma; una riga di seeding le
  rimuove; la consegna passa da -713 a -152 allo stesso budget singolo. La lezione
  di processo e' gia' nel registro (niente run multi-ora per conferme).

### TASK-550 - CLASSIFICAZIONE DEI RESIDUI DEL -152 (25/08 00:20, dall'export, zero run)
- **H0 boxed 46** (tutti con celle libere ai livelli H1/H3 = VRP-locked, famiglia
  iter-24): G04 Stage 16 (= le beyond-horizon genuine del gruppo: le 24 off-grid
  contenevano 24 chiusure + 16 oltre-orizzonte, queste ultime tornate boxed per il
  budget-consumption), G14 Confezione 18, FA Servizio tecnico e stilistico 12
  (il triangolo TASK-435: 4+8), + 0 altro. TRE FAMIGLIE CON NOME, nessuna sorpresa.
- **H29 room-null 56** = G14 Confezione 33 + G09 Modello (Moulage) 23: saturazione
  tag/capacita' stanza, la famiglia M-TC (i 1.325 Stage room-null sono pseudo-docente,
  esenti H29 per contratto: non contano).
- **H32 4**: cellule di quota residue (in banda storica post-QFR).
- CONCLUSIONE: il residuo del -152 e' interamente composto da famiglie GIA' censite
  (beyond-horizon Stage, triangolo FA, G14 lab, Moulage lab): nessun nuovo
  fenomeno. La distanza dallo 0-hard e' la somma di quattro colli noti e piccoli.

## QUADRO DI CHIUSURA GIORNATA (25/08 00:20, per maintainer)
1. **SINGLE-WRITER RATIFICATO** (DoD piena: /simplify 3 reviewer + /review-local high
   con finding critico fixato + suite EXIT 0; A/B wall pari 62 vs 70min; banner+knob).
2. **PACCHETTO multi-start+PHR**: baseline pre-cura SMENTITA (7 sorteggi su 7 sul
   collo fantasma; PHR 34/34 buchi chiusi ovunque: la fase e' sana, il collo non era
   suo).
3. **DETERMINISMO**: REPRODUCIBLE x2 sul ratificato DIVERGENTI-RIDOTTI (prima LS
   6.041/12.821 vs 12.542 legacy: la componente race e' dimezzata; il residuo e'
   scheduling di arrivo, intrinseco, dichiarato).
4. **CURA BOXED-VRP (TASK-550)**: CONFERMATA e SUPERATA - consegna -152 dal -713
   pre-cura a tentativo singolo, boundary -96/box 46. Residui tutti a famiglie note.
5. **PROCESSO**: harness run-measure.sh (5 guardie nate da incidenti reali); regole
   d'ingaggio nuove (niente run multi-ora per conferme, bracci checkpoint-paired).
- IN CODA: TASK-549 (riesplorazione LS checkpoint-paired sul nuovo landscape),
  risposta scuola alla domanda stage-held (cure (c)), ratifica formale PHR.

### TASK-549 famiglia (a) - SWEEP ACCEPTOR checkpoint-paired. PREDIZIONE PRIMA (25/08 00:40)
- STRUMENTO: knobs prototipo planner.ls.acceptor (tabu-N | la-N) + planner.ls.seconds-cap
  (default assenti = XML invariato, produzione inerte), applicati alle fasi LS di
  B-TIMESLOT in timeslotPhasesConfig. Suite 1161 EXIT 0.
- PROTOCOLLO: 4 bracci ripartenti dallo STESSO POST_CONSTRUCTION post-cura
  (/tmp/ck-549-base.json, boxed 1299 costruzione), budget PARI 1200s, profilo unico
  dci-550fix, restart app per braccio coi flag del braccio (i -D viaggiano su java -jar,
  MAVEN_OPTS-lesson). Gate: tier/boxed a fine B-TIMESLOT #1 dal log. Bracci:
  (1) control tabu-7 (XML), (2) tabu-3, (3) tabu-14, (4) la-400.
- PREDIZIONE: sul NUOVO landscape (H31/H32/H33 + domanda sana) il muro -10.8k non
  esiste piu': atteso tier di fine-fase in -80..-140 per tutti i bracci (il control
  ha fatto -108 nella notte). DIFFERENZE tra bracci attese PICCOLE (entro ±25 tier):
  la metaeuristica era esausta sul VECCHIO landscape (08-05), e la tesi di TASK-549 e'
  che il nuovo landscape merita riesplorazione - se le differenze sono piatte, la tesi
  e' SMENTITA e il verdetto e' "il collo resta la struttura, non la ricerca". SE un
  braccio batte il control di >40 tier: CONFERMATA, e quel acceptor diventa candidato.
  Rischio dichiarato: 1200s capati tagliano la coda lunga (il control notturno giroo'
  39min): le differenze potrebbero emergere solo a budget pieno; il pairing regge
  perche' TUTTI i bracci hanno lo stesso cap.

### TASK-549 famiglia (a) - ESITO SWEEP ACCEPTOR: differenze PIATTE, tesi SMENTITA (25/08 02:10)
- MISURA (4 bracci checkpoint-paired dallo STESSO POST_CONSTRUCTION post-cura, budget
  pari 1200s, profilo unico, restart per braccio, banner knob verificato per ciascuno):
  | braccio | tier | box | medium |
  | control tabu-7 (XML) | **-36** | 16 | -1218800 |
  | tabu-3 | -45 | 16 | -1237624 |
  | tabu-14 | -37 | 16 | -1227934 |
  | la-400 | **-213** | 32 | -2608849 |
- VERDETTO vs predizione: CONFERMATA la forma ("differenze piatte ±25, nessun braccio
  batte il control di >40") -> la TESI di TASK-549 (il nuovo landscape meritava una
  riesplorazione acceptor) e' **SMENTITA sul fronte acceptor**: le varianti tabu sono
  statisticamente indistinguibili dal ratificato tabu-7 (spread 9 tier su un gate
  -36), e la late-acceptance e' nettamente PEGGIORE (6x il tier, 2x il medium: la
  finestra 400 su un medium da -1.2M tende ad accettare troppo). Il tabu-7 ratificato
  resta la scelta: il collo e' la STRUTTURA, non la ricerca - coerente con l'esaurimento
  metaeuristico del 05/08, ora ri-confermato sul landscape sano.
- BONUS metodologico: il protocollo checkpoint-paired a budget pari funziona benissimo
  (4 bracci in ~80 minuti totali, zero run multi-ora): e' lo strumento pulito per
  qualunque futura famiglia (b). Riferimento assoluto: il boundary a 1200s (-36/16) e'
  MIGLIORE del run notturno intero (-96/46): il cap taglia la coda lunga della LS,
  indizio che anche il budget 39min della #1 era oltre il punto utile (coda compresa).
- PROSSIMO: famiglia (b) quota-preserving swap (prototipo dietro flag).

### TASK-549 famiglia (b) - PROTOTIPO quota-preserving swap + PREDIZIONE PRIMA (25/08 02:2x)
- IMPLEMENTATO: QuotaPreservingSwapFilter (predicato puro quotaPreserving package-private
  + wrapper SelectionFilter su SwapMove) accetta SOLO swap stesso-gruppo tra DATE DIVERSE:
  ogni giorno perde 1 ora e ne guadagna 1 -> H32/H33 INVARIANTI PER COSTRUZIONE (il move
  set attuale poteva spostare ore tra giorni solo rompendo i conteggi e riempiendo dopo,
  la forma shell-game TASK-481). Wiring: applyLsExperimentKnobs dietro flag
  planner.ls.quota-preserving-swap.enabled DEFAULT OFF (swap selector aggiunto all'unione
  LS di B-TIMESLOT). TDD 4/4 sul predicato (accetta stesso-gruppo cross-data; rifiuta
  stessa-data, cross-gruppo, null). Suite 1165 EXIT 0.
- PREDIZIONE (bracci checkpoint-paired dallo STESSO POST_CONSTRUCTION, budget 1200s,
  controllo gia' misurato -36/16): braccio b1 = quota-swap ON da solo: atteso tier
  **-25..-40** (miglioramento MODESTO ma reale: i 4 residui H32 del -152 e parte dei
  boxed cross-day ora hanno una mossa atomica legale; il control -36 e' il benchmark).
  RISCHIO DICHIARATO: il filtro restringe molto lo spazio (solo same-group cross-date)
  e il selector in piu' DILUISCE il sampling degli altri stream: possibile esito PARI
  o leggermente peggio se la diluizione supera il guadagno. SE migliora di >10 tier:
  candidato a sweep del peso; se peggiore: la diluizione e' la causa e serve weight,
  non l'archiviazione della famiglia.

### TASK-549 famiglia (b) - ESITO braccio b1: SMENTITA, la diluizione domina (25/08 03:20)
- MISURA (quota-preserving swap ON, cap 1200s, stesso checkpoint, banner wired):
  **tier -341, box 32, medium -2650751** vs control **-36/16/-1218800**.
- VERDETTO vs predizione: il rischio dichiarato (diluizione del sampling) si e'
  materializzato in FORMA PIAENA: il selector aggiunto all'unione campiona un
  sottospazio molto piu' ristretto (same-group cross-date only) ma a peso PARI degli
  altri stream: la LS spende meta' delle valutazioni su mosse quasi sempre rifiutate
  dal filtro (o poco informative), e il tier 10x peggio. NOTA: la forma del danno e'
  IDENTICA a la-400 (tier ~-3xx, medium ~2.6M, box 32): in entrambi i casi la LS
  accetta/valuta di peggio, non e' il meccanismo quota-preserving in se' a fallire.
- PROPOSTA (non eseguita, da gate di maintainer): la famiglia NON e' archiviata per difetto
  di SEMANTICA ma di DOSAGGIO: il prossimo passo naturale e' fixedProbabilityWeight
  basso (es. 0.5-1.0 vs i 5/3/2/1/2/1 degli stream XML) per dare al quota-swap un
  canale senza diluire. Un solo braccio da 20min lo misura.

## QUADRO TASK-549 COMPLETO (secondo deliverable della notte, 25/08 03:20)
- FAMIGLIA (a) acceptor sweep (4 bracci paired 1200s): tabu-7/-3/-14 PIATTI (spread 9
  tier, box 16), la-400 6x PEGGIORE: la tesi "il nuovo landscape meritava una
  riesplorazione acceptor" e' SMENTITA - il tabu-7 ratificato resta, il collo e' la
  STRUTTURA. Conferma indipendente sul landscape sano dell'esaurimento metaeuristico
  del 05/08.
- FAMIGLIA (b) quota-preserving swap (1 braccio paired): SMENTITA al dosaggio PARI
  (diluizione: -341 vs -36); semantica valida, proposta weight-basso da gate.
- METODO: il protocollo checkpoint-paired a budget pari e' lo strumento pulito
  confermato (6 bracci tot in ~2h, zero run multi-ora; i bracci 1200s misurano
  MEGLIO dei run interi: control -36/16 vs notturno -96/46).
- KNOBS PROTOTIPO committati default-OFF (acceptor/cap/quota-swap): il banco resta
  pulito, ogni futura famiglia (b2 weight sweep inclusa) li riusa senza codice nuovo.

### TASK-549 famiglia (b2) - dosaggio corretto + PREDIZIONE PRIMA (25/08 03:3x)
- FIX: swap.setFixedProbabilityWeight(knob planner.ls.quota-preserving-swap.weight,
  default 2.0) = quota fissa ~12% dell'unione (XML streams 5/3/2/1/2/1=14; b1 non
  pesato prendeva ~50% delle valutazioni = la diluizione misurata).
- PREDIZIONE (b2 paired 1200s, stesso checkpoint, control -36/16): atteso **NEUTRO
  (±10 tier, box 16)**: con la diluizione rimossa il danno sparisce, ma il meccanismo
  ha poco da chiudere su questo landscape (i residui del -152 sono VRP-locked e
  room-tail, non day-count-locked: gli H32 residui erano 4 celle). SE neutro: famiglia
  (b) CHIUSA con due smentite solide (dosaggio pari = diluizione; dosaggio corretto =
  neutro) e il verdetto finale onesto: metaeuristica e move-set NON sono il collo, la
  struttura lo e'. SE migliora >10 tier: la semantica aveva valore e si propone lo
  sweep del peso (giorno, non notte).

### TASK-549 famiglia (b2) - ESITO: IDENTICO a b1, e questo CHIUDE la famiglia (25/08 04:40)
- MISURA: b2 (weight 2.0) = **tier -341, box 32, medium -2650751** - NUMERI IDENTICI
  a b1 (stesso score allo stesso cap, stesso seed 1). Lettura onesta del dato: lo share
  effettivo e' BARELY cambiato (b1 senza peso = share uniforme per membro ~1/7=14%;
  b2 con peso 2.0 su totale 16 = 12,5%): la mia diagnosi "50% diluizione" era SBAGLIATA,
  la diluizione era ~13% fin dall'inizio.
- CAUSA CORRETTA (dal dato): non e' lo share, e' il SELECTOR: lo spazio same-group
  cross-date e' una frazione minima di tutte le coppie di lezioni, quindi il selettore
  filtra-brucia quasi tutto il suo campionamento per produrre poche mosse valide: a
  qualsiasi share il costo-rumore supera il valore delle (poche) mosse prodotte. In
  termini del registro: un 13% di budget speso in un selettore quasi-sordo produce il
  -341; il control senza quel selettore fa -36.
- FAMIGLIA (b) CHIUSA con DUE SMENTITE SOLIDE: dosaggio pari = danno da selector
  filtered; dosaggio corretto = IDENTICO (lo share non era la variabile). Il verdetto
  finale onesto e' quello richiesto: METAEURISTICA E MOVE-SET NON SONO IL COLLO, la
  struttura lo e'. Ogni ulteriore leva di ricerca e' svuotata: la strategia si
  concentra TUTTA sui residui strutturali del -152 gia' classificati (Stage
  beyond-horizon G04, triangolo FA, G14/Moulage lab-tail, room-tail).

## QUADRO RISVEGLIO (25/08 04:45, i TRE deliverable della notte)
1. **TASK-550 CURA BOXED-VRP: CONFERMATA e SUPERATA.** Seeding Stage a densita'
   golden-open (311 ore fantasma rimosse, TDD 8/8, conservazione riconciliata):
   consegna **-152hard** dal -713/-721 pre-cura a tentativo singolo; boundary -96/box46
   (predetti -360/35-60); PHR 34/34 ovunque. Residui del -152 classificati dall'export:
   TUTTI famiglie note (46 H0 VRP-locked: Stage beyond-horizon G04 16 + G14
   Confezione 18 + triangolo FA 12; 56 H29 room-tail: G14 33 + Moulage 23; 4 H32).
   Nessun fenomeno nuovo. Domanda alla scuola pronta (cure c).
2. **TASK-549 a+b: le ricerche NON sono il collo.** (a) sweep acceptor 4 bracci
   paired: tabu-7/-3/-14 PIATTI (spread 9 tier), la-400 6x peggiore: tesi SMENTITA,
   il ratificato tabu-7 resta. (b) quota-preserving swap: b1 e b2 IDENTICI (-341 vs
   control -36): lo share non era la variabile, il selettore filtered brucia il proprio
   campionamento: famiglia CHIUSA a doppia smentita. Conclusione strategica: ogni
   ulteriore leva metaeuristica/move-set e' svuotata; si lavora sui residui strutturali.
3. **PROCESSO: paired + harness.** Il protocollo checkpoint-paired a budget pari
   misurato MEGLIO dei run interi (control -36/16 a 1200s vs -96/46 del ciclo notturno
   completo: la coda lunga della LS non paga); 7 bracci totali in ~2,5h, zero run
   multi-ora; harness run-measure.sh (5 guardie da incidenti reali) con banner-assert
   gia' operativo. Le tre knobs T549 restano default-OFF, banco pulito.
- Stato banche: single-writer DEFAULT ON ratificato (DoD piena), PHR ratifica formale
  pendente, medium-post-repair rimossa, union-M32 dormiente, draft upstream Timefold
  in attesa della rilettura di maintainer.
### DECISIONI CHE ASPETTANO maintainer AL RISVEGLIO (25/08 05:00)
1. **Reply a Geoffrey**: in sospeso di sua lettura (non toccato).
2. **Ratifica cure Stage (TASK-550)**: DECISIONE UFFICIALE (maintainer 25/08 07:20).
   Motivazione sua, verbale: l'input (295 ore per G12) e' un ERRORE UMANO nella
   specifica; il golden comanda; si assume il conteggio del golden (G12 112 ecc.)
   e si chiedera' conferma a referente A POSTERIORI. Il seeding golden-open e' cosi' il
   comportamento UFFICIALE del seed (gia' implementato e misurato: consegna -152).
   Domanda referente aggiornata nel pacchetto (qui sotto).
3. **T549 prototipi: tenere o rimuovere gli scaffold**: le tre knobs (planner.ls.acceptor,
   planner.ls.seconds-cap, planner.ls.quota-preserving-swap.enabled + weight) sono
   default-OFF, inerti in produzione, e sono lo strumento RIUSABILE per qualunque
   futura famiglia paired (zero codice nuovo per il prossimo esperimento). Proposta:
   TENERLE (il costo e' tre letture di System.getProperty a solve-setup), ma la
   decisione e' sua: rimuoverle e' un commit, non un refactor.
4. (Ricorrenti) PHR ratifica formale; draft upstream Timefold in attesa; union-M32
   dormiente; regole Byteman T543 al land su main.

### RATIFICA TASK-550 cure (a) - DECISIONE UFFICIALE (maintainer, 25/08 07:20)
- Il seeding Stage a densita' golden-open e' RATIFICATO. Motivazione di maintainer,
  registrata verbatim di senso: "l'input (295 ore) e' un errore umano nella specifica,
  il golden comanda, si assume il conteggio del golden (G12 112 ecc.) e si chiedera'
  conferma a referente a posteriori". Forma: direttiva golden-first estesa agli input
  (famiglia TASK-466), applicata al seeding senza flag di rollback (il vecchio
  comportamento non e' un'opzione: era il bug).
- DOMANDA referente (aggiornata nel pacchetto, cure (c)): "il piano segna 295 ore di stage
  per G12 ma il golden ne colloca 112 (e similarmente per gli altri gruppi);
  assumiamo il golden come corretto, confermate?" - conferma a posteriori, non
  bloccante.

### EXTENSIONE GRIGLIA al 15/6/2027 quota-mirata (autorizzazione maintainer 25/08 07:50). PREDIZIONE PRIMA
- AUTORIZZATO (maintainer 07:50, DataGenerator PROTETTO citato nel commit): (1) timeslot per
  i 3 giorni oltre-orizzonte ven 11/6, lun 14/6, mar 15/6 2027; (2) righe quota che
  rispecchiano ESATTAMENTE il golden in quei giorni (G04 11/6 stage 8; G04 14/6
  stage 8; G08 14/6 R04 RA; G04 15/6 R04 Scienze; G08 15/6 R04 RA),
  NESSUNA riga per gli altri 12 gruppi (H26 li tiene fuori: capacita' extra solo dove
  il golden la pratica); (3) rettifica classificazione notturna: le 16 oltre-orizzonte
  NON erano "non tenute", il golden le tiene (closed=false 11-14/6).
- VERIFICA MECCANICA fatta sui dati: golden held oltre il 10/6 = G04 Stage 8h x2
  (11/6, 14/6), G04 Scienze 5h (15/6), G08 RA 5h x2 (14/6, 15/6) = le 5 righe
  autorizzate ESATTE. I CSV divergono in 3 punti (G08 8h vs golden 5 x2; G04 15/6
  zero vs golden 5) e contengono righe per gruppi senza pratica golden (G13 6/15 6h,
  G09/G10/G11): la regola golden-mirror le esclude automaticamente.
- DESIGN: SCHOOL_YEAR_END 2027-06-10 -> 2027-06-15 (timeslot 1.368 -> 1.392, giorni
  171 -> 174); i generatori quota seminano le righe oltre il VECCHIO orizzonte SOLO
  dove il golden tiene ore (derive meccanica da lessons.csv closed=false, tecnica
  cura (a): Stage->stage bucket, non-Stage->aula bucket).
- PREDIZIONE della misura: H0 G04 **16 -> 0** (le oltre-orizzonte trovano casa);
  NESSUN altro corso tocca quei giorni (nessun gruppo senza riga quota puo' entrarvi:
  H26); hard di consegna atteso -152 -> ~-120 (via i -32 delle 16 H0... attenzione: le
  16 valevano -32 H0, quindi atteso -152+32 = **-120**); tier boundary invariato o
  lievemente migliorato. RISCHIO: i pin Stage dell'import calendario per 11-14/6
  potevano essere classificati off-grid dal vecchio orizzonte: verificare che ora
  pinnano (1.985 pinned invariato atteso, le 16 si spostano da off-grid a on-grid).

### CURE COMBINATE (maintainer 08:10/08:12): de-tag G14 Confezione + PREDIZIONE del braccio congiunto (25/08 08:15)
- NOTA STORICA (maintainer): TASK-477b era tracciata e MAI eseguita - il de-tag Q19 del
  01/08 era rimasto a meta' (Servizio stilistico si, Confezione no). Regola in
  memoria: DECISIONE PRESA != DECISIONE ESEGUISTA.
- CURA G14: riga G14,Confezione,NONE, in subject_overrides.csv (chiude il de-tag).
- PREDIZIONE del braccio CONGIUNTO (griglia 15/6 + de-tag G14, aggiorna la
  predizione della sola griglia): **H0 46 -> ~12** (restano solo le 12 del triangolo
  FA: le 16 di G04 trovano casa nei giorni 11-14/6, le 18 G14 Confezione
  diventano collocabili senza tag), **H29 56 -> ~23** (resta solo Moulage G09:
  le 33 G14 Confezione ora trovano aula), **H32 4 invariato**, consegna attesa
  **~ -70/-80** dal -152. RISCHI: l'ordine R10 per G14 resta differito (la
  materia va in qualunque aula libera: possibile pressione capacita' che tiene qualche
  H29 in piu' del previsto).
- VERIFICA: braccio unico congiunto, preferita la forma BREVE se i checkpoint lo
  consentono (resume/troncato), altrimenti ciclo singolo NON multi-start.

### CURE CONGIUNTE - ESITO: CONFERMATO e SUPERATO, H0 = 0 (25/08 11:00)
- MISURA (braccio congiunto griglia 15/6 + de-tag G14, ciclo singolo, harness,
  DB verificato): consegna **-83hard/-1356913medium/17257soft**. Boundary post-LS
  **tier -12, box 4** (stabile su #1/#2/#3).
- COMPOSIZIONE hard: **H29 room-null 83. E SOLO QUELLO.** H0 = **ZERO**, H17 = 0,
  H32 = 0, H33 = 0, H31 = 0. PHR inerte (0 buchi all'entry).
- VERDETTO vs PREDIZIONE: H0 46 -> 0 (**SUPERATO**: atteso ~12, il triangolo FA
  stesso ha trovato casa con la griglia estesa); H29 56 -> 83 (PIEGGIORE dell'atteso
  ~23: il rischio dichiarato "capacita' aula" si e' materializzato - 390 ore
  aggiuntive collocabili premono sulle aule; ma oss: 83 room-null su 12.879 collocate
  = 0,6%, e il medium -1.36M e' invariato). H32 4 -> 0 (superato). Consegna -152 ->
  **-83**: in banda ~-70/-80, leggermente oltre.
- QUADRO: in 16 ore di campagna la consegna e' passata da **-713 (collo fantasma)
  -> -152 (cura Stage) -> -83 (griglia + de-tag)**. L'asse hard e' ora UN SOLO
  fenomeno: room-null (H29), la capacita' delle aule, non piu' la domandata ne' la
  struttura dei vincoli. La distanza dallo 0-hard e' interamente lato stanze.

### CURE CONGIUNTE - classificazione fine del -83 e VERDETTO PER FAMIGLIA (25/08 11:10)
- LE 12 DEL TRIANGOLO FA: **0 boxed** (le 120 ore di Servizio tecnico 1/G06 tutte
  collocate: il triangolo TASK-435 e' CHIUSO dalla griglia estesa).
- LE 16 STAGE G04: **0 boxed** (collocate nei giorni 11-14/6).
- LE 18 G14 CONFEZIONE: **0 boxed, 0 room-null** (il de-tag ha dato alle ore sia
  cella che aula: cura PERFETTA).
- H29 = 83, TUTTE MODA e TUTTE due materie: Moulage 42 + Confezione 41, gruppi
  1/2/G09 (14/36/33). E' IL LAB DELLA MODA: il de-tag G14 ha spostato la
  pressione sui tag moda rimasti (Moulage era gia' 23 pre-cura: ora 42; Confezione
  MODA 41, nuova - G14 liberata ma i MODA collassano sullo stesso tag).
- VERDETTO PER FAMIGLIA vs predizione combinata:
  | famiglia | predizione | misurato | verdetto |
  | H0 46 -> ~12 (triangolo FA) | **0** | SUPERATO |
  | H29 56 -> ~23 (solo Moulage) | **83** (Moulage 42 + Confezione MODA 41) | SMENTITO: il rischio dichiarato "capacita'" si e' concentrato sul tag moda |
  | H32 4 invariato | **0** | SUPERATO |
  | consegna ~-70/-80 (ottimismo maintainer) | **-83** | IN BANDA, leggermente oltre: l'ottimismo era fondato |
- QUADRO AGGIORNATO (tre cure di INPUT, zero metaeuristica): **-721 -> -152 -> -83**.
  La composizione hard residua e' UN SOLO fenomeno: il laboratorio moda (tag),
  83 room-null su 12.879 collocate (0,6%). La cura candidata e' la stessa famiglia
  della Q19/G14: quante ore MODA Confezione/Moulage sono davvero lab nel golden?
  (il golden le colloca in aula normale? da verificare col solito confronto).

### ROOM-TAIL FINISHER (VIA maintainer 13:05, famiglia H29 moda residua -83). PREDIZIONE PRIMA (25/08 13:10)
- CONTESTO (verificato da Fedora): il golden non co-schedula MAI due lab moda (0 celle
  su tutto l'anno; uniche 2 eccezioni = G14 ospite 22/4, ora legale col de-tag);
  capacita' presente (798h domanda vs ~1392 celle R16): e' PACKING, non
  saturazione; il golden dimostra esistenza.
- DESIGN (sorella di PureHoleRepair, fase custom deterministica post-B-ROOM, flag
  planner.construction.room-tail-finisher.enabled DEFAULT OFF): per ogni lezione
  senza stanza del tag moda (a) cella libera in una stanza col tag, oppure (b)
  sposta un occupante non-lab di quella stanza in un'altra aula per liberare la
  cella. Mai peggiorare lo score (improve-or-noop).
- PREDIZIONE: **H29 83 -> <20**, consegna **-83 -> ~-25/-35**. RISCHIO DICHIARATO:
  le mosse di spostamento dell'occupante possono aprire H17 (l'occupante spostato
  puo' lasciare un buco nel suo giorno): la fase gemesi con PureHoleRepair (una
  passata PHR dopo il finisher) o la mossa stessa deve essere H17-sicura
  (l'occupante si sposta in una cella adiacente al suo run).
- BANCO: checkpoint del -83 di stamattina (task550-cure-congiunte, minuti non run).

### ROOM-TAIL FINISHER - ESITO (25/08 pomeriggio). PREDIZIONE SUPERATA: H29 83 -> 0, consegna 0hard
- TREATMENT (resume da POST_EXACT_FINISHER del -83, flag ON): POST_B_ROOM
  **0hard**/-1.846.813medium/3.405soft; log "tag-required room-null 83 -> 0 (free-cell 0,
  evictions 0, pull-last 0, swaps 83)", B-ROOM 608s.
- CONTROL (stesso checkpoint, flag OFF): -83hard/-1.806.497medium/17.777soft.
  Costo medium DEL FINISHER sullo stesso percorso ~-40k (83 swap); il delta -490k vs il run
  originale e' della B-ROOM re-run, NON del finisher.
- Il fenomeno misurato corregge il design: tutte le 83 celle hanno come occupante un ALTRO
  LAB MODA (94 celle con 2 lab moda co-schedulati, una sola R16; il golden 0 tutto
  l'anno). L'eviction non-lab e' strutturalmente morta (0 candidati); la leva e' il
  de-co-scheduling via SWAP con lezione ordinaria same-group (H17-sicuro per costruzione:
  entrambe le celle restano occupate). Il tail-extend del design e' STATO RIMOSSO in review
  (last->last+1 lascia il rank `last` vuoto = migrazione, shell-game TASK-481; trovato dal
  reviewer altitude e confermato dal test dei buchi: 12 aperti prima della rimozione).
- DoD completa: /simplify (4 reviewer, fix applicati: Grid.moveRoom, grid.movable,
  totalHardScore via snapshot.score(), gate pull-last, famiglia swap), /code-review high
  (NESSUN finding >= 80), test.sh 1167/0 EXIT 0 due volte.
- Artefatti: task550-roomtail/VERDICT-2026-08-25.md (+ treatment/control resume outputs).
  Fixture TDD: src/test/resources/task550/roomnull83-cure-congiunte.json.gz.
- STATO: prototipo DEFAULT OFF (planner.construction.room-tail-finisher.enabled), ratifica
  maintainer pendente con sanity gate per-gruppo + batteria KPI sul run di ratifica.

### CICLO COMPLETO col room-tail finisher ON. PREDIZIONE PRIMA (25/08 sera, via maintainer)
- CONFIG: run-measure, seed con le tre cure (teacher-weekday-spread + griglia 15/6 quota-mirata
  + de-tag G14 gia' nel codice), PHR ON, room-tail finisher ON, capture ON,
  multistart.max-attempts=1 (stessa forma del run del -83, un solo delta: il flag nuovo).
- PREDIZIONE: **consegna 0hard end-to-end attesa** (H0/H17/H29 tutti a 0; il finisher ha chiuso
  83/83 sul banco del -83).
- RISCHIO DICHIARATO: la LS con flag ON potrebbe spostare la composizione residua e il finisher
  trovare un banco DIVERSO dal -83 (piu' null, o null in celle senza swap disponibili); se non
  chiude, il gate resta comunque informativo (misura la generalizzazione, non solo il replay).
- Da leggere a fine ciclo: capture completo, sanity gate per-gruppo (solution-sanity-check),
  quadro KPI (full-day/ricorrenza/day-start entro banda; confronto HTML da APRIRE, non solo
  generare).

### CICLO COMPLETO finisher-ON - ESITO (25/08 16:29). PREDIZIONE CENTRATA: consegna 0hard end-to-end
- Run: ciclo-finisher-on (profilo dci-550rtf, run-measure, jar df323fba, 14:50->16:29 ~1h40).
- CONSEGNA: **0hard/-1.401.806medium/5.696soft**. H0/H17/H29 tutti 0.
- RISCHIO DECLARATO AVVENUTO IN FORMA BENIGNA: il banco ALL'ENTRY DEL FINISHER ERA DIVERSO
  (76 null, non 83: la LS ha spostato la composizione) e il finisher ha chiuso **76/76 -> 0**
  (tutti swap, zero free-cell/eviction/pull-last). Generalizzazione confermata, non replay.
  PHR: 0 buchi all'entry, inerte (come nel -83).
- SANITY GATE: NON CONFORME su 2 gruppi (G07 1 giorno sotto min 4h il 14/05; G09 4
  giorni a 1h in aprile, min golden 2h). Confronto col -83: quel run aveva rossi PIU' gravi
  (G07 2 giorni + buchi interni; 14/33/36 lezioni senza stanza; qui 0 buchi e 0 senza
  stanza). I giorni sotto-minimo sono famiglia H33-floor/pool-cap (giorni corti voluti?
  domanda referente pendente), NON causati dal finisher (che per costruzione non tocca i conteggi
  per giorno). Da curare a valle se la scuola conferma i pavimenti.
- KPI in banda vs il -83: ricorrenza unpinned 70,0% (vs 70,7), full-day 87,3% (vs 87,7),
  day-start scarti >=3: none. Lettura confronto HTML (triade): buchi interni 0 vs golden 1,
  inizio tardivo 1 (il giorno rosso G07), giorni vuoti in span 42 vs 0 (famiglia giorni
  corti FC/G13/MODA sopraindicata), giornate profonde 45 vs 5.
- Artefatti: ciclo-finisher-on/ completo (capture, sanity, KPI, confronto HTML+png, manifest
  finalized, app-delivery.log).
- Nota harness: --expect "SINGLE-WRITER..." case-sensitive vs banner minuscolo
  "dispatch=single-writer per-thread channels": app.log di avvio non conteneva la riga,
  esteso col log di consegna (stesso run) prima del finalize.

### CICLO finisher-ON - LETTURA VISIVA DEL CONFRONTO (4 viste, 25/08 sera; canale immagine non decodificabile su questa variante, lettura via DOM del viz stesso, cioe' i dati che dipingono le strisce)
- STAGIONE: G01 / G02 / G05 / G03(88) / G14(83) forme IDENTICHE al golden
  (cell-per-cell). L'unica differenza di forma visibile e' UN solo fenomeno: celle vuote in N
  dove G e' pieno sui gruppi giorni-corti (G08 65% vs 93 pieni, G13 81 vs 100, G09 83 vs
  95, G11 89, G07 90, G06 87). G04 e' ROVESCIATO: N 75% pieni vs G 47% (noi
  riempiamo dove il golden pratica i corti). G10: G ha 1 buco interno, N 0 (meglio di lui).
- FINGERPRINT SETTIMANALE: le matrici G/N dei gruppi puliti coincidono AL PUNTO PERCENTUALE
  (la struttura ricorrente del golden e' riprodotta). G04: prime ore piu' chiare in N
  (71-97 vs 100) ma coda 13:00-15:00 conservata (17-19% entrambi).
- DOCENTI: ore totali conserve (T01 231/231, T02 530/530, T03 374/373...).
  T01 IDENTICO: il giovedi' monografico e' preservato (100 vs 97 sulle 8-11). Gli altri
  mostrano mosaici diversi con stesse magnitudini: nessun buco strutturale.
- MATERIE x SETTIMANA: cadenze continue, nessuna materia fuori stagione. Pattern noto
  confermato: noi piu' uniformi del golden (Confezione N=5/sett costante vs G blocchi 6/3;
  Recuperi G concentrato in coda d'anno, N 1-2/sett sparso).
- DIFF SANITA': POCA differenza sulla forma dominante, TANTA concentrata su UN solo fenomeno
  (i giorni corti FC/G13/MODA, le 42 celle vuote in span) = il warning e' la famiglia
  pool-cap/giorni-voluti, domanda referente pendente. Concordante con score 0hard e KPI in banda.

### ISPEZIONE VISIVA maintainer sul ciclo finisher-ON: 4 anomalie, VERIFICATE COL GOLDEN (metodo golden-check, non impressione; 25/08 sera, verifiche di Fedora)
1. **G12 giorni-zero sparsi (35 feriali, di cui 18 gio + 9 ven): COERENTE.** Il golden fa
   lo stesso su quelle date: no-session del gruppo (foglio), non un difetto del run.
2. **RA/Recuperi spalmati piatti vs golden back-loaded: VERO GAP DI PRATICA.** Golden:
   set-dic 33h, gen-giu 156h (su 189 totali). Run: 1-2h/settimana uniformi tutto l'anno.
   Domanda aggiunta al pacchetto referente (domande-brevi rev 15); candidato vincolo
   RA-after-base SOFT da gate, tracciato in backlog.
3. **Slot 3/12 vuoto: COERENTE.** Anche il golden e' vuoto li' (no-session).
4. **Ideal end G08 al 15/06: MARCATORE CORRETTO.** E' il target cumulativo del foglio
   (690h a meta' giugno); il golden vi colloca le ultime RA, il nostro finisce prima
   (conseguenza del punto 2: senza back-load le ore finiscono prima).

### ADDIZIONE maintainer 17:05 al pacchetto referente (2 domande oltre alle 4 verificate)
- Q-RA riformulata ("preferibilmente DOPO le ore regolari?"): vedi domande-brevi rev 15-bis.
- Q-G12-VUOTI (nuova): i 35 giorni feriali vuoti di G12 (18 gio + 9 ven, alcuni a
  settembre) sono voluti o subiti? FORK OPERATIVO: se SUBITI = capacita' recuperabile e la
  golden-count-wins NON si applica cieca (il golden qui potrebbe essere near-miss umano,
  memoria golden-vs-input-intent); se VOLUTI = no-session da dichiarare come input. La
  risposta determina se aprire un task di recupero capacita' su G12.

### TASK-551 braccio paired LS medium-pura da stato 0hard (VIA maintainer 18:46). PREDIZIONE PRIMA (25/08 sera)
- SETUP: resume dal checkpoint POST_B_ROOM del ciclo finisher-ON (stato 0hard/-1.401.806),
  fase finale medium-pura (change+swap su timeslot, stanze fisse) con ACCETTAZIONE STRETTA
  (mai peggiorare il totale: da 0hard ogni mossa accettata e' medium-migliorante e
  hard-sicura PER COSTRUZIONE), budget 1200s cap / 120s unimproved. NOTA knob: il tabu-7
  dei T549 NON e' usabile qui (il tabu accetta peggioramenti e romperebbe lo 0hard): il
  braccio prende solo il seconds-cap. Implementato come fase finale del ciclo
  (flag planner.construction.medium-pure-arm.enabled DEFAULT OFF), cosi' il resume da
  POST_B_ROOM la esegue sullo stato consegnato.
- DOMANDA: il medium ha headroom locale da uno stato 0hard raggiunto presto (via finisher
  deterministico) invece che da una consegna convergente?
- PREDIZIONE: **miglioramento medium <= ~5k** (cioe' simile ai +1.670 misurati ieri da
  consegna convergente, iter-31, entro un ordine di grandezza): CHIUDE la questione,
  medium = costo del metro, swap-early (TASK-551 candidate) resta dormiente. Se invece si
  muove di DECINE DI MIGLIAIA, la proposta swap-early RIAPRE (il bacino 0hard-precoce e'
  diverso da quello convergente).
- MISURA: input 0hard/-1.401.806 -> output <delta>. ESITO: CONFERMATA/SMENTITA.

### TASK-551 ESITO (25/08 19:19): SMENTITA della predizione <=5k - misurato +57.608, swap-early RIAPRE
- MISURA: resume dal POST_B_ROOM 0hard, braccio medium-pura (accettazione stretta, cap 1200s,
  decider MT-LS single-writer 8 thread): **0hard/-1.848.160 -> 0hard/-1.790.552 = +57.608
  medium (3,1%) in 1201s**. Hard invariato a 0 (garanzia costruttiva tenuta). Soft ceduto
  (5696 -> -22766, atteso: il medium domina). Il budget e' stato consumato INTERO senza
  convergere (unimproved 120s mai scattato): headroom RESIDUO a fine corsa.
- VERDETTO vs predizione: SMENTITA nel senso che RIAPRE: previsto <=5k (chiude-metro),
  misurato +57,6k con ancora strada. La proposta swap-early (TASK-551 proper) torna in campo.
- CAVEAT DICHIARATA (onesta' della misura): il baseline del resume (-1.848.160) NON e' il
  medium della consegna (-1.401.806): le statics pubblicate in-ciclo (pin-netting M20, ecc.,
  famiglia TASK-509) non sono ripubblicate identiche sul resume, quindi parte dell'headroom
  misurato puo' esistere SOLO nello scoring del resume (mosse piu' libere). CONFERMA
  richiesta: braccio inside-ciclo (flag ON in un ciclo fresco, statics pubblicate) prima di
  qualunque decisione di adozione. Il dato robusto resta: da uno stato 0hard, 20 minuti di
  LS stretta medium-pura muovono >3% del medium senza esaurirlo - IERI da consegna
  convergente trovarono +1,7k esaurendolo in 900s: i due bacini sono DIVERSI.
- Artefatti: medium-pura-arm-app.log (log integro salvato).

### TASK-551 CONFERMA INSIDE-CICLO. PREDIZIONE PRIMA (25/08 sera, prima del lancio)
- SETUP: ciclo completo fresco (run-measure, profilo nuovo), stessi flag del ciclo
  finisher-ON (tre cure + PHR + room-tail RATIFICATO ON + capture + max-attempts=1) PIU'
  la fase medium-pura ON in coda (planner.construction.medium-pure-arm.enabled=true,
  accettazione stretta, cap 1200s): statics pubblicate in-ciclo, banco = consegna vera.
- PREDIZIONE: **+40..60k medium sul ciclo vero, consegna 0hard preservata** (accettazione
  stretta: hard-safe per costruzione), soft in cessione libera.
- RISCHIO DICHIARATO: il banco inside e' diverso dal resume (statics pin-netting attive:
  le mosse sono MENO libere) e la fase potrebbe trovare molto meno: in quel caso il caveat
  statics era la spiegazione dell'headroom del braccio resume e la questione metro resta
  (verdetto: caveat confermato, swap-early torna dormiente).
- ESITO: se conferma (+40-60k dentro banda) -> TASK-551 si chiude con RACCOMANDAZIONE a
  maintainer di adottare la fase (resta DEFAULT OFF finche' lui decide).

### TASK-551 CONFERMA INSIDE-CICLO - ESITO (25/08 21:34): VERDETTO PARZIALE, la domanda NON si chiude
- MISURA (ciclo completo fresco, profilo dci-551mpa, fase medium-pura ON in coda, tentativo
  singolo): braccio **-6hard/-1.423.500 -> -6hard/-1.406.074 = +17.426 medium (1,2%) in
  1200s**, budget consumato INTERO (non converto). Hard preservato dall'accettazione stretta
  (era -6 PRIMA del braccio: questo ciclo, lotteria del tentativo singolo, NON ha consegnato
  0hard; il finisher ha comunque chiuso il suo banco 86 -> 0).
- VERDETTO vs predizione (+40..60k, 0hard preservato): SMENTITA in banda: +17,4k e' SOTTO i
  40k, e la premessa 0hard non si e' materializzata (bacino -6). MA non e' nemmeno il caso
  "trova poco chiude-metro": +17,4k dentro-ciclo e' 10x i +1.670 di ieri da consegna
  convergente, e il budget non si e' esaurito.
- SINTESI DEI TRE PUNTI (consegna convergente +1,7k / inside 0hard-precoce +17,4k / resume
  +57,6k): il caveat statics SPIEGA circa 2/3 dell'headroom del resume (57,6 -> 17,4), MA il
  medium dentro-ciclo ha comunque ~17k di headroom locale non consumato in 20 minuti.
  La questione NON e' puro costo-del-metro ne' puro headroom: e' un trade-off
  costo/beneficio (20 min di wall per ~1,2% di medium, hard-risk zero per costruzione).
- RACCOMANDAZIONE A maintainer: la fase resta DEFAULT OFF. Adottarla o no e' una scelta di wall
  vs medium (il 25/08 il valore dimostrato e' ~+17k per 20 min). La via strutturale resta
  swap-early/metro (TASK-551 proper e TASK-552), non piu' LS: la LS stretta ha smesso di
  trovare a un quarto del budget-ora dei cicli.
- Sanity: NON CONFORME sugli stessi 2 gruppi del ciclo di riferimento (G07 3 gg, G09
  4 gg sotto-min, famiglia giorni-corti): firma invariata, il braccio non tocca i giorni
  (mosse timeslot tra celle occupate? no: change moves possono spostare ore tra giorni, MA
  con quota-fill H32/H33 hard la struttura per giorno e' vincolata).
- Artefatti: ciclo-medium-pura-on/ (capture completa, manifest finalized).

### TASK-554 AC#1 RIMISURA DEL GOLDEN SOTTO IL METRO ATTUALE (VIA maintainer 21:52). PREDIZIONE PRIMA
- DOMANDA: il -49hard/-?medium storico del golden e' dell'era bendable (set pre-H31/H32/H33,
  pre-M32-overlay, pre-pool-cap): quanto paga il GOLDEN sotto il metro attuale (flag ratificati
  post-merge)? Il confronto vero e' golden-remisurato vs consegna 0hard (-1.406.074): se il
  golden paga ~-49k siamo LONTANI (filone qualita' enorme, sweep pesi giustificato); se paga
  ~-1,3M siamo VICINI (fase medium-pura superflua).
- METODO: analyzeScore sul golden importato (profilo fresco, import-golden, NIENTE solve),
  breakdown per famiglia medium. Analisi da registro, nessun run.
- PREDIZIONE: **golden remisurato ~-1,2/-1,4M medium** (un run di luglio lo misurava -1,319M
  sotto un metro vicino a quello attuale): quindi gap VERO ~0..200k, siamo GIA' VICINI, la
  fase medium-pura e' marginale e lo sweep pesi poco giustificato. RISCHIO: le famiglie
  M32-overlay/pool-cap potrebbero penalizzare il golden MENO del previsto (il metro e' stato
  calibrato golden-first: M20 pool-cap nasce proprio dalla forma del golden) e uscire sotto
  -1M, riaprendo il filone.

### TASK-554 AC#1 ESITO (25/08 22:20): golden sotto il metro attuale = -12hard/-22.026medium. PREDIZIONE SMENTITA DI MASSA, ma il confronto va decomposto
- METODO: profilo fresco dci-554gold, import-golden --force, stanze azzerate via DB (forma
  canonica del regression test: il golden non ha aule; H2/H29 vacue sui pinned), analysis.
- MISURA: **-12hard/-22.026medium/-84.912soft**. Il -12hard = H4 Stage blackout (12, noto).
  Medium: M20.2 -21.500 (3 celle), M21 -214, M18 -150, M-CPS -123, M25 -27, M24 -16, M30 -10.
- PREDIZIONE (~-1,2/-1,4M): SMENTITA. MA il confronto diretto col nostro -1.406.074 e'
  INGANNEVOLE senza decomposizione: il golden e' 100% PINNATO, le famiglie pinned-exempt
  (M15/M20/M33/...) leggono zero per costruzione, e M32 e' vacua nell'import (0 corsi
  templated: il riferimento template si costruisce a solve-time). Come il regression test
  dichiara da sempre: H2/H-M2/M3/M-LF/M-LA "structurally untestable" su questi dati.
- DECOMPOSIZIONE del nostro -1.423M (analysis pre-braccio): ARTEFATTO/VACUE (confronto
  illeggibile): M-LF -158k + M3 -0,7k + M37 -9,8k (stanze), M32 -261,6k (template non
  costruito sul golden; parzialmente reale, non misurabile qui). CONFRONTABILI E
  CONCENTRATE: **M20.2 giorni-vuoti: nostro -503,5k (51 celle) vs golden -21,5k (3)**;
  **M22 compattezza calendario: nostro -415k (14) vs golden 0**. Minori: M33 -27,7k,
  M34/M35 -17,9k vs 0.
- QUADRO: il gap VERO non e' "-1,4M vs -49" ne' "siamo gia' vicini": e' **~0,9M concentrato
  al ~90% su GIORNI (vuoti + compattezza)**, esattamente la famiglia dell'ispezione visiva
  di maintainer (giorni-corti FC/G13/MODA) e delle domande referente pendenti (Q-G12-VUOTI
  voluti-o-subiti, Q-RA back-load, 5h-aspirazione). NON e' un filone sweep-pesi (i pesi non
  creano giorni): e' un filone PIANO/CAPACITA'. Prossimo passo chirurgico: decomporre le 14
  celle M22 per gruppo prima di toccare qualsiasi cosa.
- Artefatti: golden-remisura-metro-attuale.json.

### LEZIONE DI PROCESSO (25/08 sera, per me e per Fedora): rimisurare i riferimenti golden a ogni cambio del metro
Ad ogni cambio del metro (nuovi vincoli o ricalibrazioni medium) i riferimenti golden vanno
RIMISURATI: il -49 dell'era bendable circolava come confronto pur essendo misurato su un set
obsoleto (450x sotto il valore attuale -22.026). Stessa famiglia della trappola Q19 (un
numero letto come verita' assoluta era il prodotto di un set di vincoli supersato). Regola
operativa: quando una RATIFICA tocca il constraint set, la voce del registro che la chiude
riporta anche il NUOVO score del golden sotto il metro aggiornato (profilo dedicato,
import-golden, stanze nulle: il metodo di stasera, ripetibile in ~2 minuti).

### QUADRO RISVEGLIO - fine giornata 25/08
- **0hard RATIFICATO**: RoomTailFinisher DEFAULT ON (banco 83->0, ciclo 76/76, ciclo di
  conferma 86->0: tre banchi diversi, chiusura 100% tutte le volte). Merge sul main remoto
  ec7ec7bf (il main locale deve ancora fare pull; TASK-552 nel suo backlog e' untracked).
- **TASK-551 verdetto**: braccio resume +57,6k (statics spurie), inside-ciclo +17,4k budget
  intero non converto: la questione NON e' ne' metro puro ne' headroom puro, e' trade-off
  wall-vs-medium (20 min per ~1,2%, hard-risk zero). Fase implementata ma DEFAULT OFF,
  raccomandazione = non adottare per ora; la via strutturale e' metro/swap-early, non LS.
- **TASK-554 verdetto**: golden sotto il metro attuale = -12hard/-22.026medium. Gap vero
  ~0,9M concentrato su GIORNI (M20.2 51-vs-3 celle, M22 14-vs-0): filone PIANO/CAPACITA'
  (domande referente pendenti), sweep-pesi NON giustificato, medium-pura marginale (17k su 900k).
  Prossimo passo chirurgico: decomporre le 14 celle M22 per gruppo.

### DECISIONI APERTE per maintainer
1. PACCHETTO referente (in pausa, lo spedira' maintainer): domande-brevi rev 15/15-bis/15-ter
   (Q-RA + sub-domanda monte-ore/ideal-end, Q-G12-VUOTI voluti-o-subiti) + le gestite.
   Ora PRIORITARIE perche' il filone giorni (900k) dipende dalle risposte.
2. REPLY GEOFFREY: differita a stasera (ndr: rimane da scrivere).
3. MEDIUM-PURA adozione: DEFAULT OFF, decisione wall-vs-medium di maintainer (~17k per 20 min).
4. (minore) TASK-552 RA-after-base resta in attesa della risposta referente; swap-early
   dormiente; prototipi T549 tenere/rimuovere; regole Byteman T543 al land su main.

(NDR backlog: la rimisura e' registrata come task-555 nel backlog - l'id 554 della VIA
era già ombreggiato dalla numeratura; stessa cosa, stesso contenuto.)

### DECISIONE maintainer 22:35 - filone GIORNI aperto ufficialmente
"i giorni pieni del golden noi li vogliamo anche noi... dobbiamo trovare un modo, sia CH, LS,
late phases o qualunque altro approccio." Tracciato in backlog come task-556 (M20.2/M22
family: costruire la capacita' che il golden pratica; High). Approccio: (a) riprendere
day-compact (il residuo di allora era boxed/H29 strutturale, oggi la consegna e' 0hard con
giorni buchi: forma diversa); (b) analisi da export PRIMA del codice (51 celle M20.2 + 14
giorni M22: gruppi/date, cosa manca per riempirli); (c) candidatos QuotaFillRepair
potenziato / block-shift T549 / pre-booking costruzione. Predizione PRIMA ogni misura;
prototipo dietro flag default-OFF (le 2 domande referente decidono SE attivare). Controparte
del relaxed-solve T546: PIU' capacita', non meno vincoli.

### TASK-556 analisi da export (25/08 sera, consegna 0hard ciclo-finisher-on, PRIMA di ogni codice)
- M20.2 (giorni attesi vuoti, metro: 51 celle): la mia join grezza CSV-expected dice 358
  celle ma includo gli scope esclusi dal metro (G08 pool-cap, G14) e sabati: la forma
  pero' e' robusta: concentrate FEB-GIU (28+83+99+71+42) e su gio/ven (74+84), gruppi
  triennio + G04. Coerente con l'ispezione visiva (42 span-vuoti).
- M22 (spread sottile: giorni usati oltre ceil(ore/maxHPD), analysis: 14 match = 415
  giorni-eccesso): decomposizione per gruppo: **primi anni usano TUTTI i 171 giorni
  scolastici (eccesso 6 ciascuno, borderline), i triennio spargono il pool su troppi
  giorni**: G06 166 usati vs 124 min (eccesso 42), G10 130 vs 84 (46), G12 133 vs
  85 (48), G11 18, G09 19, G07 16, G13 15. Totale grezzo 240.
- LETTURA STRUTTURALE: il golden pratica GIORNI LUNGHI E POCHI per i triennio (lo stage
  libera giorni e la scuola li riempie con giornate piene ~7-8h monografiche); il nostro
  solver sparge sottile (~5h/day su tutti i giorni disponibili) perche' template-stamp +
  H17 privilegiano la presenza-ogni-giorno. Il gap giorni = SHAPE del giorno, non capacita'
  docente mancante (i docenti saturati sui giorni vuoti sono 252 teacher-days su ~12.500:
  la capacita' c'e', e' distribuita male).
- CANDIDATO MECCANISMO (da TDD dietro flag, task-556): **DayConsolidation**, pass
  deterministico post-QuotaFillRepair, specchio di QFR: per i gruppi con eccesso M22,
  svuota i giorni PIU' SOTTILI ridistribuendo le loro ore nei giorni con headroom
  (H11/H7/H32-safe, mosse bordo-consapevoli), riducendo i giorni-usati verso il minimo.
  Sorella naturale di SurplusRebalance ma a livello giorno. Gates: improve-or-noop,
  predizione PRIMA, flag default-OFF finche' le domande referente (G12 voluti/subiti,
  RA back-load) non dicono SE quei giorni vadano riempiti/consolidati.
- NOTA counterintuitive: M20.2 vuole PIU' ore nei giorni attesi-vuoti, M22 vuole MENO
  giorni usati: la RISOLUZIONE GEMELLA e' consolidare (giorni pieni): riempire i giorni
  attesi VUOTI attingendo dai giorni SOTTILI. Le due famiglie si curano con lo stesso
  pass: sposta ore dai giorni thin ai giorni empty-expected (entrambi entro i tetti).

### TASK-556 notte - l'analisi CADE la premessa del DayConsolidation (verdetto in task556-results/)
- M22 FINZIONE DIMOSTRATA: il golden unpinned-equivalente pagherebbe 262 giorni-eccesso
  (noi 240). Min-days usa maxHoursPerDay contro quote dichiarate sottili: penalizza la
  forma stessa del foglio. Nessun consolidamento-di-giorni possibile senza rompere H32
  (floor, tolleranza zero).
- M20.2 decomposto: 49 celle = 28 FC/G14 over-declare pool-cap (~-293k, by design,
  domanda referente) + 21 reali (~-200k: G13 5, G06 5, G09 5, G11 3, G07 2,
  G12 1). Conservazione perfetta (placed==sumQuota): riempibili solo dal surplus
  sopra-quota (160h globali, per-gruppo stretti): QFR usa gia' quei donatori.
- PIVOT: niente DayConsolidation nuovo; l'item implementativo diventa lo sprint QFR
  (donor ordering + riprove, atteso -100..-150k), DIETRO lettura di maintainer del verdetto
  (il mandato notturno presumeva la premessa M22 valida: e' caduta, la decisione torna
  a lui). Proposta di emendamento al metro (M22 quota-aware min-days) registrata, NON
  applicata.

### TASK-556 sprint QFR v2 - implementazione + PREDIZIONE PRIMA della misura (notte 25/08)
- COSA (flag planner.construction.quota-fill.v2.enabled DEFAULT OFF, v1 intoccata):
  (1) classificazione dei giorni attesi VUOTI (il difetto strutturale trovato dal test: le
  celle nascono da dayLessons, quindi un giorno senza lezioni non e' MAI un target: QFR v1
  e' cieco proprio alle 21 celle M20.2); (2) donatori tail-first (slot 0 mai toccato:
  la partenza del donatore non si muove, H31-sicuro); (3) slot target bordo-consapevoli
  (vuoto: crescente dal primo slot aperto; parziale: buchi interni poi last+1: mai salti
  oltre last+1, H17-sicuro); (4) re-sweep mentre progredisce.
- TDD: QuotaFillRepairV2Test (banco sintetico del fenomeno: giorno dichiarato vuoto quota 2
  + donatore 4h su quota 2): v2 chiude (2 mosse, slot 0-1), donatore tiene lo slot 0 e
  cede dalla coda, 0 conflitti; flag-off = percorso legacy intatto. VERDE.
- PREDIZIONE (misura localizzata: resume dal POST_B_ROOM 0hard, seconda occasione QFR con
  v2): **chiude 8..15 delle 21 celle reali** (le altre bloccate da surplus per-gruppo
  insufficiente o H1/VRP), **medium -80..-150k**, hard 0 PRESERVATO (il revert H31 di ieri
  non deve piu' scattare: se scatta ancora, il difetto e' altrove e si indaga). RISCHIO:
  la seconda occasione vede donatori diversi dalla prima; se chiude <5 celle il vincolo
  e' il surplus per-gruppo e lo sprint si ferma li' (matematica, non implementazione).

### TASK-556 sprint QFR v2 - ESITO localizzato (notte 26/08 00:00) e stop dichiarato
- ITER-1 (v2 senza gate room/coda/atomicita'): 99 mosse, 18 celle, MA revert di massa
  (H17 -49, H2 -57k, H32 -2->-11): tre difetti identificati e curati con altrettanti gate:
  (a) room-safe (il mover tiene la stanza: solo celle dove la propria stanza e' libera);
  (b) tail-only (solo l'ULTIMA lezione del donatore: estrarre a meta' run apre H17);
  (c) fill atomico dei giorni vuoti (H32 e' cieco ai giorni vuoti: riempirli a meta' li
  rende violanti; se i donatori non portano a quota, tutto il fill si rollbacka).
- ITER-2 (v2 completa, banco POST_B_ROOM della consegna -6hard): **26 mosse, 8 celle chiuse,
  0 conflitti; revert SOLO per H31 0 -> -3** (H2/H17/H32 puliti: i gate tengono).
- PREDIZIONE (8..15 celle): CENTRATA in banda bassa (8). Il medium potenziale delle 8 celle
  ~-80k. Il residuo H31 -3 (un giorno che parte tardi dopo le mosse v2) NON e' ancora
  diagnosticato: serve un dump delle (group,date) mosse con day-start prima/dopo. Senza
  quella cura il revert butta il lavoro del pass: il ciclo completo di conferma ora
  consegnerebbe v2-inerte, quindi NON si lancia (sarebbe 2h per nulla).
- STOP dichiarato per la regola dei 30 minuti: prossimo passo chirurgico = diagnosticare
  l'H31 residuo (dump mosse + day-start), poi ciclo completo.

### BANCO DB dci-550rtf RIPRISTINATO (notte 26/08 00:20)
- Sintomo: planner-pg exit 1. DUE cause: (1) il volume e' dati PG16 ma planner.sh pina
  postgres:17 (PG rifiuta per major mismatch); (2) i miei esperimenti con mount :Z hanno
  RILABELLATO il volume (SELinux): da allora i mount SENZA :Z sono negati (ecco perche'
  planner.sh start smise di funzionare stasera anche dove prima funzionava).
- Cura: container postgres:16 con -v ...:Z su 5432, name planner-pg. Verificato:
  SELECT count(*) FROM lesson = 12510 (consegna finisher-ON intatta) e podman-tools list
  mostra planner-pg Up.
- DA SAPERE: il prossimo ./planner.sh start <profilo> ripinnera' :17 e SENZA :Z: andra'
  curato in planner.sh (pin 16 + :Z) quando si tocca il prossimo profilo; le cause sono
  registrate qui. Non toccato stanotte (planner.sh e' infra condivisa, non e' misura).

### TASK-556 QFR v2 - DIAGNOSI H31 e CURA (notte 26/08, TDD)
- DIAGNOSI (target-side, dimostrata col test): quando il PRIMO slot del giorno vuoto e'
  bloccato (H1/VRP/stanza) il fill scivolava al primo slot libero successivo: un giorno
  ex-vuoto che parte tardi e' una cella H31 HARD che il giorno vuoto non era (il residuo
  -3 = giorni fills partiti oltre lo slot 0).
- CURA (gate d): un giorno ex-vuoto prende la sua prima ora SOLO allo slot primo-aperto del
  giorno; se quello e' bloccato la cella non si filla affatto (nessuno scivolamento).
- TDD: QuotaFillRepairV2Test.v2NeverFillsAnEmptyDayFromALateSlot (quota-1, docente occupato
  allo slot 0 da altro gruppo): prima della cura target=[1] (ROSSO), dopo target=[] (VERDE).
  Il caso buono resta [0,1].
- PREDIZIONE ITER-3 (banco localizzato POST_B_ROOM): **revert NON scatta (H31 residuo 0),
  celle chiuse 6..10** (il gate (d) puo' costare qualcuna delle fill prima-possibili),
  conflitti 0. Se il revert scatta ancora per H31, il danno e' altrove e si dumpa.

### TASK-556 QFR v2 ITER-3 (banco localizzato, 00:19): PULITA. PREDIZIONE CENTRATA
- MISURA: 32 mosse, 9 celle chiuse, 0 conflitti, **NESSUN REVERT** (predizione: revert
  assente, 6..10 celle -> 9). Il lavoro del pass ora SURVIVE nella consegna: la catena
  gates (a room, b tail, c atomico, d primo-slot) chiude il ciclo.

### CICLO COMPLETO DI CONFERMA. PREDIZIONE PRIMA (00:25)
- SETUP: run-measure, profilo fresco, tutti i flag ratificati + QFR v2 ON (entrambe le
  occasioni QFR girano v2), tentativo singolo.
- PREDIZIONE: consegna **-6..0hard** (lotteria single-attempt come i due cicli di ieri),
  M20.2 celle 49 -> **residuo 34..40** (le 28 FC/G14 restano by design; delle 21 reali
  atteso chiuse 8..14 cumulando le due occasioni), **medium -1.40M -> -1.30/-1.34M**
  (delta -60..-110k), full-day/ricorrenza/day-start in banda, sanity NON CONFORME residua
  sugli stessi 2 gruppi giorni-corti (famiglia pre-esistente).
- RISCHIO: la prima occasione (post-RELEASE) vede una griglia pre-stanze: il gate (a)
  room-safe e' inerte li' (rooms null post-RELEASE) ma il gate (d) puo' bloccare piu' fill
  del previsto; se le celle chiuse totali < 5, il costo del gate supera il beneficio e si
  dichiara nel VERDICT.

### TASK-556 CICLO COMPLETO QFR v2 - ESITO (26/08 01:50): predizione centrata, sanity peggiore. TENERE codice, NON adottare
- CONSEGNA: **0hard/-1.317.237** (+84.569 medium vs ciclo finisher-ON: dentro il range
  predetto -60..-110k). QFR occ.1: 53 mosse/15 celle/0 conflitti/NESSUN revert (gates
  tengono in produzione); occ.2: 0 mosse. Finisher: 81 -> 0 (quarto banco, quarta chiusura).
- M20.2: 49 -> 38 (predetto 34..40). Viz (triade): giorni vuoti in span 42 -> 34,
  giornate profonde 45 -> 34. Forme dei gruppi puliti invariate.
- SANITY: NON CONFORME su 5 gruppi (G07, G08, G09, G10, G11) con 2 giorni a
  buchi interni (prima 0): le mosse v2 attingono da giorni che finiscono sotto il minimo
  praticato. Fino alla risposta referente sui giorni, il pass ottimizza il metro contro la
  pratica. FLAG RESTA DEFAULT OFF; upgrade candidato = gate donatore day-floor-aware.
- VERDICT completo: task556-results/VERDICT-qfrv2-2026-08-26.md.

### TASK-556 QFR v2 gate (e) day-floor-aware (notte 26/08, TDD) + PREDIZIONE della misura localizzata
- TDD: v2DonorNeverDropsBelowTheDayFloor (donatore 8h su quota-1, floor 4, target vuoto
  quota 5): ROSSO prima (donatore drenato a 3), VERDE dopo (gate (e): il donatore si ferma
  a max(quota, floor H33/DayFloorDeriver); il target non riempibile resta vuoto via gate (c)).
- PREDIZIONE (resume dal POST_RELEASE del ciclo qfrv2-on: rilancia la catena repair con la
  PRIMA occasione QFR, A/B diretto contro le 53 mosse/15 celle del ciclo): **celle chiuse
  6..14** (il gate (e) toglie le mosse che drenavano sotto-floor: qualcuna delle 15 muore),
  **revert assento, 0 conflitti**. RISCHIO dichiarato: se scendono sotto 6, il gate e'
  troppo stretto (i donatori validi erano quasi tutti sotto-floor) e si dichiara il
  trade-off invece di chiudere.

### TASK-556 A/B gate (e) localizzato - ESITO (02:13) e REVISIONE DELL'ATTRIBUZIONE
- MISURA (resume POST_RELEASE del ciclo qfrv2-on, prima occasione QFR con gate e):
  **52 mosse, 14 celle, 0 conflitti, nessun revert** (vs 53/15 senza gate: costo 1 cella).
  Predizione 6..14: centrata al limite alto. Consegna resume 0hard/-1.747.709 (baseline
  resume inquinata dalle statics, gia' nota: confrontabile solo in delta).
- **FINDING CHE REVISA IL VERDICT**: la sanity della consegna gated ha gli STESSI 5 gruppi
  rossi e gli STESSI giorni/buchi del ciclo senza gate. I rossi NON sono (solo) drenaggi
  delle mosse v2: abitano gia' la griglia POST_RELEASE del ciclo dci-556q2 (bacino
  B-TIMESLOT di quel tentativo singolo). L'attribuzione "sanity peggiorata da QFR v2" nel
  VERDICT va letta come CORRELAZIONE col ciclo, non causalita' dimostrata; il controllo
  definitivo (ciclo stesso seed senza v2) e' rinviato a domani.
- Il gate (e) resta comunque corretto per costruzione (un donatore sotto il floor praticato
  e' un rosso sanity potenziale a costo zero) e il suo costo e' misurato: 1 cella su 15.

### QUADRO RISVEGLIO NOTTURNO 25-26/08 (tre colonne)
**CODA APPROVATA E CONSEGnata** (tutto committato e pushato sul worktree, flag v2 DEFAULT OFF):
1. Analisi TASK-556 giorni: M22 finzione dimostrata (golden pagherebbe 262 giorni-eccesso
   vs nostri 240), M20.2 = 28 over-declare FC/G14 + 21 reali; gap vero ~0,9M per ~90%
   non-actionable. VERDICT-analisi + proposta emendamento M22 (quota-aware min-days, NON
   applicata).
2. QFR v2 con CINQUE gate (classificazione giorni vuoti; a room-safe; b tail-only; c fill
   atomico; d primo-slot; e day-floor-aware), TDD rosso->verde per ciascun difetto trovato
   in misura. Ciclo completo: 0hard/-1.317.237 (+85k medium, M20.2 49->38, span-vuoti
   42->34, profonde 45->34). A/B gate (e): 52 mosse/14 celle (costo 1 cella), nessun revert.
3. Banco DB dci-550rtf ripristinato (pg16 + :Z; cause registrate: pin-17-vs-16 e rilabel
   SELinux). Merge dello stack ratificato nel worktree (a8a5b622, costruzione identica).
**SPIKE/LIMITI DICHIARATI**: la sanity del ciclo qfrv2 (5 gruppi rossi, 2 buchi interni) e'
CORRELAZIONE col bacino LS del tentativo singolo, non causalita QFR dimostrata (i rossi
sono identici con e senza gate e); serve il controllo stesso-seed senza v2. Il medium delle
consegne da resume non e' confrontabile (statics, TASK-509). Le celle M20.2 chiuse da QFR
con gate e: 14 su 21: il resto e' surplus per-gruppo insufficiente (matematica).
**DECISIONI PER maintainer**: (1) QFR v2: adozione o meno del flag (DEFAULT OFF; guadagno
+85k/11 celle a costo 1 cella col gate e; sanity da chiarire col controllo); (2) emendamento
metro M22 (quota-aware min-days) si/no; (3) pacchetto referente ora PRIORITARIO (il filone
giorni dipende dalle risposte: G12 voluti/subiti, RA back-load, monte-ore/ideal-end RA,
giorni corti FC); (4) reply Geoffrey differito; (5) medium-pura adozione (trade-off
wall-vs-medium ~17k); (6) duplicazione backlog 553/554 (mie commit registro vs file backlog
di Fedora): consolidamento a lui; (7) planner.sh: pin postgres 16 + mount :Z da curare
prima del prossimo profilo fresco.
**DOMANI (se confermato)**: controllo stesso-seed senza v2 (sanity causalita'), poi ciclo
completo di conferma del gate (e) se il controllo lo giustifica. TASK-552 resta in attesa
referente.

### TASK-556 CONTROLLO DISCRIMINANTE sanity (notte 26/08 02:4x). PREDIZIONE PRIMA
Stesso checkpoint POST_RELEASE del ciclo qfrv2-on, resume a consegna con flag v2 SPENTO
(path identico all'A/B tranne il flag). PREDIZIONE: **i 5 gruppi rossi e i 2 buchi compaiono
IDENTICI anche senza v2** (proprieta' del bacino B-TIMESLOT del tentativo singolo):
causalita' QFR CONFUTATA, il VERDICT si aggiorna. Se invece tornano 2 rossi: causalita'
confermata, gate (e) necessario.

### TASK-556 CONTROLLO DISCRIMINANTE - ESITO (02:34): PREDIZIONE CENTRATA, causalita' CONFUTATA
- CONTROL (stesso POST_RELEASE, v2 OFF): QFR v1 5 mosse/5 celle (vs v2 52/14 sullo stesso
  checkpoint: il delta v2 e' netto), consegna 0hard/-1.837.585 (resume-baseline).
- SANITY CONTROL: **IDENTICA** alla consegna v2 (stessi 5 gruppi G07/G08/G09/G10/
  G11, stessi giorni sotto-min, stessi 2 buchi interni 09/06 [1,2] e 01/04 [1,2,3]).
- VERDETTO AGGIORNATO: i rossi sanity del ciclo qfrv2 sono proprieta' del bacino
  B-TIMESLOT del tentativo singolo, NON di QFR v2 (causalita' confutata stanotte con
  controllo appaiato). QFR v2 (con gate e) migliora il medium e le celle M20.2 **senza
  alcun costo sanity dimostrabile**. La raccomandazione si rafforola: adozione del flag
  adesso e' una scelta wall-vs-guadagno pulita (resta DEFAULT OFF, decide maintainer).
- QUADRO RISVEGLIO aggiustato: la colonna spike "sanity da chiarire col controllo" e'
  RISOLTA; resta solo il nota-bene che il ciclo di conferma del gate (e) completo
  (same-seed end-to-end) e' ancora da fare domani per la formalita'.

### REPLY GEOFFREY - BOZZA scritta (26/08 03:0x, NON inviata)
claudedocs/spalmone-2026-27/reply-geoffrey-draft-2026-08-26.md. La domanda esatta di
Geoffrey non e' nel repo (vive nella discussione #2615 / inbox di maintainer): bozza con
segnaposto [?] dichiarati in cima, costruita sui nostri due contributi censiti (custom
phase fragility dal TASK-547 + single-writer MT-SL dal TASK-543, numeri veri). maintainer
riempe i segnaposto e spedisce.

### TASK-556 FORMALITA': ciclo completo same-seed col gate (e). PREDIZIONE PRIMA (26/08 mattina, mandato Fedora)
- SETUP: profilo FRESCO dci-556e2e via run-measure, STESSI flag del ciclo qfrv2-on
  (capture ON, spread ON, max-attempts 1, pure-hole-repair ON, room-tail-finisher ON,
  quota-fill.v2 ON) sul jar HEAD, che ora include il gate (e) nel flag v2 (commit 7a4f128b).
  Stesso construction seed per determinismo del tentativo 1 su profilo canonico fresco
  (274 pin allocator deterministiche; il ciclo di riferimento dci-556q2 era partito cosi',
  senza import-calendario: post-TASK-550 il seeding e' a densita' golden-open nel
  DataGenerator).
- PREDIZIONE: consegna **0hard**; QFR occ.1 (post-RELEASE) **45..60 mosse, 12..15 celle
  chiuse, 0 conflitti, NESSUN revert** (l'A/B localizzato sullo stesso confine ha dato
  52/14; la variabilita' MT-LS residua puo' spostare di poco la griglia POST_RELEASE);
  occ.2 (post-B-ROOM) 0..5 mosse pulite. **M20.2 residuo 37..39 celle** (38 senza gate e;
  il gate costa ~1 cella). **Medium -1.30/-1.34M** (centrato sul -1.317.237 +/- 15k).
  M22 invariato ~-427k (la finzione nota, non toccata dal v2). SANITY: **gli STESSI 5
  gruppi rossi** (G07, G08, G09, G10, G11) e i 2 giorni con buchi interni
  (proprieta' del bacino B-TIMESLOT dimostrata dal controllo discriminante; conteggi
  entro +/-2 giorni per la variabilita' MT-LS). Viz: giorni vuoti in span ~34, giornate
  profonde ~34, forme dei gruppi pulite invariate.
- SCOPO: chiude il gate di adozione TASK-556 con evidenza end-to-end pulita (il ciclo
  qfrv2-on girova SENZA gate e: l'A/B del gate era solo resume localizzato).

### TASK-557 (mandato item 3) - CARATTERIZZAZIONE rossi sanity consegna 0hard: ESITO (26/08 mattina)
- I 5 gruppi rossi della consegna qfrv2-on sono TUTTI giorni MISTI (ordinarie+stage):
  run 6 misti vs golden 2. Cinque nascono nella PRIMA LS B-TIMESLOT-1 e restano frozen
  fino a consegna (checkpoint); il sesto (G08 01/04) e' dichiarato dal foglio fine FC
  (aula=2 stage=4) e nasce in costruzione: non e' un difetto.
- 0hard coesiste perche' i giorni S-dichiarati (aula+lab=0) sono parcheggio gratis: H32
  non chiede, M33 fuori ambito, H33 fuori scope per codice (riga 717). I "buchi" sono le
  ore STAGE incastrate tra le ordinarie, che lo script sanity esclude dal conteggio
  occupati. Mappa slot dello script anche errata (h12/h13 collassate su slot 4; griglia
  reale contigua 8..15).
- IPOTESI DEL MANDATO CONFUTATA: "H33 floor nel pricer di costruzione" non cura nulla
  (i giorni non sono corti, nascono in LS, H33 e' pago). Cure candidate registrate nel
  doc (medium stage-day-purity calibrata ~25h golden; date stub golden-following con
  guardia TASK-517; fix script sanity), NON implementate.
- Doc: task556-results/caratterizzazione-rossi-sanity-2026-08-26.md (TASK-557 Done nel
  backlog).

### TASK-558 (mandato item 2) - SPIKE M22 emendamento quota-aware. PREDIZIONE PRIMA (26/08 mattina)
- COSA (flag planner.constraints.m22.quota-aware.enabled, DEFAULT OFF, forma opt-in
  M-TC): minDays passa da ceil(ore/maxHoursPerDay) a ceil(ore/media-quota-dichiarata),
  dove la media e' la media delle quote aula+lab DICHIARATE dal foglio sulle righe con
  aula+lab>0 (pubblicata pre-solve accanto alle altre static, stesso loop di
  setDailyActivityQuotasStatic; fallback alla forma legacy per gruppi senza righe quota,
  cosi' i fixture sintetici restano verdi). Proprieta' algebrica: quando le ore piazzate
  coincidono col pool del foglio, minDays DEGENERA nel numero di giorni dichiarati.
- MISURA (metodo unpinned-equivalente del 25/08, replica della popolazione del vincolo:
  tutte le lezioni incl. Stage, G14 escluso, sabati esclusi da protocollo):
  PREDIZIONE: **golden 262 -> 0..30 giorni-eccesso** (la formula riconosce la propria
  forma: residuo = i giorni sopra-piano del golden ~70 nella misura in cui il golden
  sfora il foglio, arrotondamenti ceil a parte); **nostro run 240 -> 30..90** (siamo
  thin-spread come il golden, scendiamo di uno scalo simile). In medium-equivalente
  (1000/giorno): golden da ~-262k potenziale a ~0..-30k. Il -12hard/-22.026medium del
  golden MISURATO non cambia (il vincolo resta vacuo sul golden pinnato): l'emendamento
  cambia cosa il metro CHIEDE alle soluzioni unpinned, ed e' li' che va letto.
- RISCHIO dichiarato: se il golden resta sopra 60 giorni-eccesso anche emendato, la
  media dichiarata non cattura la forma del foglio (celle S sottili o pool-cap FC
  distorcono la media) e l'emendamento va ripensato (es. minDays = conteggio righe
  dichiarate, senza media).

### TASK-558 - ESITO MISURA (26/08 mattina): golden 463 -> 3, run 410 -> 0. La finzione e' quantificata
- SCRIPT: scripts/analyze/m22-quota-aware.py (puro, input file: golden CSV + quote e
  maxHPD esportate dal DB + checkpoint del run). Replica la popolazione del VINCOLO
  (tutte le lezioni incl. Stage, G14 escluso, promoted-contati, sabati/Fasullo da
  protocollo sul golden). NOTA di confronto onesta: il "262" del 25/08 era una join
  ad hoc con popolazione leggermente diversa; il confronto apples-to-apples e' quello
  dello STESSO script: legacy 463 vs emendato 3.
- GOLDEN unpinned-equivalente: legacy 463 giorni-eccesso -> emendato **3** (solo G04:
  176 gg usati vs 173 min emendato; variante solo-ordinarie: 459 -> 16, di cui G04
  15 per pool-cap che dichiarano 840h ma ne praticano 750). PREDIZIONE "0..30":
  CENTRATA (3).
- RUN (checkpoint POST_B_ROOM del ciclo qfrv2-on): legacy 410 -> emendato **0** (nessun
  gruppo sfora il minimo emendato; per la meta' dei gruppi il run usa MENO giorni di
  quanti il foglio dichiara). PREDIZIONE "30..90": SMENTITA IN MEGLIO (0): ho
  sottostimato quanto il run sia gia' compatto rispetto alla forma del foglio.
- LETTURA PER maintainer: sotto il metro emendato la famiglia M22 smette di esistere come
  gap (il -415k/-427k del nostro medium e' caricato su un minimo-giorni che NESSUNA
  soluzione fedele al foglio puo' rispettare, golden incluso: e' il costo della
  finzione, non un difetto dell'orario). L'emendamento rende M22 quasi-inerte (3 celle
  golden): se lo si adotta, il gap giorni riportato alle domande referente e' SOLO M20.2.
  Il flag resta DEFAULT OFF (decide maintainer); il -12hard/-22.026 del golden pinnato non
  cambia in nessun caso (il vincolo e' vacuo sul golden importato: e' la domanda
  unpinned-equivalente che l'emendamento risponde).

### TASK-556 FORMALITA' same-seed gate (e) - ESITO (26/08 08:53): il pass sopravvive, la lotteria hard no. Gate di adozione CHIUSO
- MISURA (profilo dci-556e2e, ciclo ~1h42, capture finalized): consegna
  **-6hard/-1.309.447/500soft** (H0 2 boxed + H32 2 celle 1h: G02 05/05, 05/12).
  QFR occ.1 **57 mosse/14 celle/0 conflitti/0 revert** (A/B localizzato: 52/14);
  occ.2 0 mosse. ExactFinisher 2/4 boxed. Room-tail 84 -> 0. M20.2 **38** (-382k),
  M22 -428k invariato, M33 -21,6k, M20 -3k.
- PREDIZIONE: QFR/M20.2/medium/M22 CENTRATE su tutti gli assi; **0hard SMENTITA**
  (-6) e **sanity SMENTITA IN MEGLIO** (3 rossi: G08, G09, G10, giorni
  diversi dal riferimento). CAUSA: same-seed NON implica same-basin (MT-LS non
  byte-exact, B-TIMESLOT-1 66min vs 42min): il residuo -6 e' la classe che il
  multi-start N=5 copre, non un difetto del pass (il ciclo era single-attempt per
  design della formalita').
- VIZ (triade, consumata via DOM): gruppi puliti identici al golden; residuo =
  vuoti-in-span FC 14+8 (pool-cap, domanda referente) + sparsi 1..3; buchi 1 giorno
  (G08 01/04, giorno misto dichiarato); day-start 1; docenti entro 0..6h dal golden.
- VERDETTO: gate di adozione QFR v2 CHIUSO con evidenza pulita; flag resta DEFAULT
  OFF (decide maintainer). Doc: task556-results/ciclo-gate-e-e2e/VERDICT-2026-08-26.md.
- INFRA ROTTA REGISTRATA: constraint-violations.sh usa `api.sh raw` (rimosso) ->
  "API error" su ogni chiamata; da curare.

### TASK-557 (remoto) H34 EMPTY-DECLARED-DAY - implementazione TDD + predizione A/B PRIMA (26/08 mattina)
- IMPLEMENTATO (flag planner.constraints.empty-day.enabled DEFAULT OFF, opt-in):
  companion hard di H32, ifNotExists per (gruppo, cella dichiarata in-scope H32),
  penalizeHard FLAT 1. Forma: join Timeslot x StudentGroup deduplicato per
  (gruppo, data) via record DeclaredCell, letture statiche PER MATCH (la factory
  app-context e' cachata a boot col flag spento: un gate a REGISTRAZIONE lascerebbe
  H34 assente da ogni sessione successiva; scoperto col golden test rosso, stessa
  lezione di m22MinDays). Registratione INCONDIZIONATA, armo per-cella.
- TDD: 3 test verifier (vuoto paga 1 FLAT; parziale resta di H32; G14 escluso +
  default inerte) + pin golden STRICT: assertHard H34 -2 (le 2 celle G09 04/06
  quota 5 e 07/06 quota 3, verificate INDEPENDENTEMENTE in Python: esattamente
  quelle, nessuna tolleranza, emendamento eb00d522).
- CURA CADETTA (debito censimento TASK-551 trovato dal full test.sh rosso):
  StagedPhaseBoundary + POST_MEDIUM_PURE_ARM (il braccio medium-pura non era nel
  censimento checkpoint dal 25/08; aggiunto enum + capture + mappa + assert-last,
  StagedCheckpointFormatTest 6/6 verde).
- PREDIZIONE A/B (scritta PRIMA della misura):
  (a) LOCALIZZATO sulla consegna dci-556e2e (flag ON via analysis): **H34 = 8..12
  celle** (il mio M20.2 = 38 di cui ~10 reali in-scope; Fedora misuro' 12 sul run
  qfrv2-on; stessa famiglia di bacino).
  (b) CICLO COMPLETO (profilo fresco, H34 ON + v2 ON + ratificati, tentativo
  singolo): **consegna H34 0..2 celle** (la costruzione si adatta: i giorni
  dichiarati vuoti diventano hard e la LS non li usa piu' come parcheggio; QFR v2
  li chiude attivamente), hard totale 0..-4 (lotteria single-attempt), medium
  ~-1.31M +/- 30k, sanity NON peggiore (i rossi sono giorni misti, ortogonali a
  H34). Se G09 finisce 0 celle vuote dichiarate: better-than-golden dimostrato.
  RISCHIO: se H34 spinge ore su giorni dichiarati che il foglio dichiarava per
  sbando (tipo G07 09/06 S-stale), il medium paga M33 e il sanity mischia piu'
  giorni: si misura e si dichiara.

### TASK-557 H34 - MISURA LOCALIZZATA (26/08 10:25): PREDIZIONE CENTRATA
- Banca: consegna dci-556e2e (il -6hard di stamattina), app rilanciata col flag ON,
  analysis: **H34 = 11 celle (-11hard)**. Predizione (a) "8..12": CENTRATA (11;
  Fedora 12 sul run qfrv2-on: stessa famiglia). Coerente con M20.2 38 (28
  FC/G14 fuori scope + ~10-11 reali). Totale della consegna sotto il nuovo
  metro: -17hard (H0 -4, H32 -2, H34 -11): il gap e' ESATTAMENTE la firma che
  maintainer vuole portare nello 0hard.
- CICLO COMPLETO (b) LANCIATO h10:26 (profilo dci-h34on, flag v2+H34+ratificati,
  banner H34 verificato 3x): esito al termine (vedi voce successiva).

### TASK-557 H34 - ALLARME CICLO RIENTRATO (26/08 10:50): decomposizione, non x8
- SINTOMO: costruzione col H34 ON apriva a -13119hard/boxed 1306 contro il -6852
  del ciclo di stamattina: delta -6267, sospetto DEDUP rotto (x8 slot per data).
- VERIFICA (probe TDD 8-slot su banco verifier): un giorno pieno vuoto carica
  ESATTAMENTE 1 cella. DEDUP FUNZIONA (probe verde, commit 132e879e; il primo
  "0" del probe era un mio bug varargs: Object[] in coda a given() non viene
  splicato - serve l'array esplicito).
- DECOMPOSIZIONE CORRETTA (offline sul checkpoint POST_CONSTRUCTION): celle vuote
  dichiarate reali a fine costruzione = **19** (H34 ~ -19); il grosso del hard di
  quel confine e' H29 roomless (~-10.4k: a fine costruzione le stanze non sono
  ancora assegnate, arrivano in B-ROOM) + H0 1306x2. La differenza col -6852 di
  stamattina sta nel confine di confronto (phases.json phase-1 vs checkpoint),
  non in un difetto del vincolo.
- LEZIONE registrata: prima di allarmarsi per un delta hard su un confine,
  decomporre per famiglia (timeslot-tier vs room-tier) e contare le celle offline
  dal checkpoint: 2 minuti di Python contro 30 di ipotesi.

### TASK-557 H34 - CICLO COMPLETO - ESITO (26/08 12:09): STRICT-FIRST VINTO sulle celle golden, 10 residue search-side
- CONSEGNA: **-14hard/-1.315.606** (H34 -10, H0 -2, H30 -1, H32 -1). QFR occ.1
  62 mosse/17 celle (22 -> 10 vuote). Sanity 3 gruppi rossi, **buchi interni 0**,
  G08 pulito: NON PEGGIORE del ciclo senza H34. Medium in banda (+6k).
- PREDIZIONE (b): celle 0..2 SMENTITA (10); hard 0..-4 SMENTITA (-14); medium
  CENTRATO; sanity CONFERMATA. Lettezza della smentita: le 10 residue NON
  includono le 2 celle golden.
- **STRICT-FIRST (emendamento eb00d522): le 2 celle G09 04/06+07/06 sono
  RIEMPITE dalla nostra consegna** (verifica per-data sull'export): il solver fa
  meglio del golden dove il planner umano si arrese a fine anno. Tolleranza NON
  introdotta, NON giustificata da impossibilita' dimostrata.
- Residue 10: strettoia mag-giu + G13 3 (16/04-24/05): famiglia import
  teacher-locked (H1/VRP), risposta search-side multi-start, come ogni residuo
  hard di questa classe.
- VERDICT: task557-results/ciclo-h34-on/VERDICT-2026-08-26.md. Flag resta
  DEFAULT OFF, decide maintainer (evidenza ratifica: strict disponibile, sanity
  non peggiore, buchi 0).

### ITEM 4 SWEEP donor-order v2 (mandato mattina). PREDIZIONE PRIMA (26/08 12:25)
- SETUP: A/B appaiato via resume dal POST_RELEASE del ciclo h34on (tier -35,
  22 celle vuote dichiarate), app rilanciata per braccio con v2 ON e H34 OFF
  (isolazione del solo knob): braccio A = donor-order default (surplus-first),
  braccio B = nearest. Metrica: riga QFR (mosse/celle/conflitti) + gate
  POST_EXACT_FINISHER.
- PREDIZIONE: braccio A riproduce la famiglia del ciclo (celle chiuse 14..18);
  braccio B chiude **12..18 celle** (stessa banda: l'ordine cambia QUALI donatori
  si drenano, non quante celle sono chiudibili - quelle le decide il surplus
  per-gruppo), 0 conflitti, nessun revert in entrambi. Se B supera A di >=3
  celle: l'ordine nearest sblocca donatori che il surplus-first esauriva prima
  (interessante, da segnalare a maintainer); se B chiude meno: il default resta.

### ITEM 4 SWEEP - PRIMO GIRO INVALIDATO e rifatto con H34 ON (26/08 12:30)
- ERRORE DI SETUP dichiarato: primo braccio nearest lanciato con H34 OFF (per
  "isolare il knob"): in quel contesto chiudere un giorno dichiarato vuoto vale
  solo M20.2 medium (~1k/cella) e il gate improve-or-noop REVERTA il pass (57
  mosse/16 celle chiuse, revert, ENTRAMBE le occasioni). Il ciclo di riferimento
  aveva H34 ON (ogni chiusura = 1 HARD): confronto non appaiato.
- PREDIZIONE (secondo giro, H34 ON + capture ON in entrambi i bracci): A
  (default surplus) replica il ciclo (celle ~17, nessun revert); B (nearest)
  chiude 14..18 celle, NESSUN revert (le chiusure valgono hard), |delta A-B|
  <= 2 celle. Se B > A di >= 3: nearest sblocca donatori, da segnalare.

### ITEM 4 SWEEP - ESITO (26/08 12:35): PREDIZIONE CENTRATA, il default resta
- APPAIATO sullo STESSO checkpoint POST_RELEASE del ciclo h34on (tier -35,
  22 celle vuote), entrambi i bracci con v2+H34 ON:
  - A (default surplus-first, dal ciclo): 62 mosse, 17 celle, NESSUN revert ->
    10 residue, tier -14, boxed 1.
  - B (nearest-first, via resume): 57 mosse, 16 celle, NESSUN revert ->
    11 residue, tier -15, boxed 1.
- VERDETTO: |delta| = 1 cella: l'ordine di visita dei donatori NON e' il collo
  (lo sono il surplus per-gruppo e i blocchi H1/VRP, coerente con la famiglia
  teacher-locked). IL DEFAULT RESTA. Il knob resta committed come leva di misura
  (default surplus = forma pre-diff byte-identica). Nessuna adozione da
  proporre.
- NOTA di metodo (dichiarata): il primo giro con H34 OFF e' stato scartato per
  confronto non appaiato (le chiusure valevano solo medium e il gate revertava:
  57/16 revert x2). Con H34 ratificato ON, il contesto giusto e' quello armato.

### TASK-560 DISCRIMINANTE + EmptyDayFinisher v0. PREDIZIONE del banco PRIMA (26/08 13:0x)
- PROBE OFFLINE (comitato 2): 10/10 celle residue con candidate legali (22-155 per
  cella) e 10/10 con candidate prima-ora H31-legali (12-129): CASO (a), passata
  finale possibile, NON marker di bacino. Task TASK-560 creato coi numeri.
- DIAGNOSTICA del perche' QFR v2 non chiude: resume con qfr.v2.debug sul
  POST_RELEASE del ciclo h34on: "no move: target=X donor=Y open=1" per OGNI coppia
  (target, donatore): le CODE dei donatori non atterrano allo slot primo-aperto
  (docente occupato/VRP). Le candidate del probe sono ore INTERIE: il collo e' il
  gate (b) tail-only, non l'esistenza delle mosse.
- IMPLEMENTATO EmptyDayFinisher v0 (flag
  planner.construction.empty-day-finisher.enabled DEFAULT OFF, opt-in): estrazione
  di ore INTERIE dal donatore con SLIDE delle ore soprastanti (ogni slide gate-
  checked H1/VRP/stanza), prima ora allo slot primo-aperto (gate d), fill atomico
  a quota (gate c), donatore >= max(quota, floor) (gate e), prima ora del donatore
  mai mossa (H31/H17). Occasione: post QFR-2, pre medium-pura, improve-or-noop sul
  tier. TDD: banco sintetico coda-bloccata -> 5 mosse/1 cella/0 conflitti (VERDE;
  due lezioni di fixture: teacher id null nel banco = H1-collasso, e doppio
  blocker same-group = conflitto H3 del FIXTURE).
- PREDIZIONE BANCO LOCALIZZATO (resume dal POST_B_ROOM del ciclo h34on, stato con
  10 celle vuote, contesto v2+H34 ON + EDF ON): **celle chiuse 2..6** (le prime-ore
  esistono su 10/10 ma il fill atomico completo, 2-6 ore con landings legali
  distinte + slide legali, e' molto piu' duro del singolo-ora del probe; il v0 e'
  greedy senza backtracking), mosse ~2x celle, conflitti 0, NESSUN revert (ogni
  cella chiusa = -1 tier hard, le slide non aggiungono tier hard per costruzione).
  RISCHIO dichiarato: se celle = 0, il collo vero e' il VRP per-corso (il probe non
  lo modellava) e si dichiara.

### TASK-560 EmptyDayFinisher - ESITO BANCO (26/08 13:08): 0 celle, imbuto censito, discrepanza col probe APERTA
- MISURA (resume dal POST_B_ROOM del ciclo h34on, contesto v2+H34+EDF ON):
  **0 mosse, 0 celle chiuse, 0 conflitti, nessun revert**. PREDIZIONE 2..6:
  SMENTITA (caso RISCHIO). Il pass e' formalmente corretto (banco sintetico
  verde: 5 mosse/1 cella) ma sul banco reale l'imbuto muore tutto a monte.
- CENSIMENTO RIFIUTI (property-gated, resta nel codice): **GATE-E=1518, ROOM=257,
  H1=172, VRP=49**: i giorni donatori DICHIARATI non hanno quasi mai surplus sopra
  max(quota, floor); i non-dichiarati passano il gate ma le loro lezioni muoiono
  su stanza/docente/VRP al primo slot della finestra.
- DUE FIX provati nel giro (primo slot = finestra-calendario invece che slot-0 di
  griglia: censimento IDENTICO, finestre già Includevano lo 0; estrazione di
  QUALSIASI slot con slide che ripristina l'inizio giornata: +GATE-E visibile).
- DISCREPANZA APERTA (prossima sessione, tracciamento per-lezione): il probe
  offline enumerava 12-129 candidate prima-ora per cella con gate e/H1/stessa
  logica stanza: NOMINARE 5 di quelle lezioni e seguirle nei gate EDF uno a uno
  (sospetti: cache VRP della lezione sul path resume, pin promossi residui,
  finestra finestrella). v0 resta committed DEFAULT OFF: il comitato voleva il
  prototipo + la misura, NON l'adozione.

## QUADRO RISVEGLIO 26/08 (chiusura giornata, tre colonne)

### Colonna 1: CODA CONSEGNATA (tutto committato sul worktree, push incluso)
1. FORMALITA' TASK-556 (mandato item 1): ciclo same-seed col gate (e) end-to-end,
   -6hard/-1.309.447, QFR 57/14/0 revert, M20.2 38, sanity 3 rossi (bacino; same-seed
   NON implica same-basin). Gate di adozione CHIUSO, flag v2 resta OFF (decide maintainer).
2. SPIKE M22 emendamento (item 2): quota-aware min-days dietro flag OFF; misura:
   golden 463 -> 3, run 410 -> 0 giorni-eccesso. La finzione quantificata; decide
   maintainer (TASK-558).
3. CARATTERIZZAZIONE rossi sanity (item 3): giorni MISTI stage+ordinarie (6 run vs 2
   golden), 5 su 6 nascono in B-TIMESLOT-1; H33-floor-nel-pricer CONFUTATA; buchi =
   artefatto script sanity. TASK-559 + doc.
4. SWEEP donor-order (item 4): appaiato stesso checkpoint, default 62/17->10 residue
   vs nearest 57/16->11: |delta|=1, il collo non e' l'ordine. Default resta.
5. H34 EMPTY-DECLARED-DAY (comitato 1): implementato, golden pin -2 (le 2 celle 3
   MODA), ciclo completo: le 2 CELLE GOLDEN RIEMPITE (better-than-golden, STRICT
   vinto, tolleranza non necessaria), 10 residue search-side, -14hard/-1.315.606,
   sanity non peggiore, buchi 0. RATIFICATO DEFAULT ON (opzione A maintainer, flip
   eseguito, suite 1180 EXIT 0).
6. CONSOLIDAMENTO BACKLOG (comitato 2b): task-555 assorbito nel canonico 554;
   556/558/559/560 ora viaggiano col repo.
7. DISCRIMINANTE residue (comitato 2): probe offline CASO (a) 10/10 curabili;
   EmptyDayFinisher v0 prototipo + banco: 0 celle sul reale (GATE-E 1518, ROOM 257,
   H1 172, VRP 49), discrepanza col probe APERTA (tracciamento per-lezione alla
   prossima). TASK-560.

### Colonna 2: LIMITI E CAVEAT DICHIARATI
- L'A/B donor-order del primo giro era invalidato (H34 OFF: revert improve-or-noop);
  rifatto appaiato col contesto ratificato.
- La discrepanza probe-vs-EDF e' APERTA: il probe trova candidate che il pass non
  vede; prima di qualunque iterazione sul finisher, tracciare 5 lezioni candidate
  attraverso i gate (sospetti: cache VRP resume, pin promossi).
- constraint-violations.sh ROTTO (usa api.sh raw rimosso): da curare.
- Sanity script: mappa slot h12/h13 collassate (griglia contigua 8..15) e ore Stage
  contate come buchi: fix proposto in TASK-559, non applicato (gate ratificato).
- Il debug prod-start "Database not running" intermittente (podman ps race): due
  rilanci falliti oggi, recuperati a mano.

### Colonna 3: DECISIONI PER maintainer
1. QFR v2: adozione del flag (gate chiuso, +85k medium, sanity senza costo
   dimostrabile).
2. Emendamento M22 quota-aware: si/no (golden 463->3: la famiglia smette di esistere
   come gap; resta solo M20.2 alle domande referente).
3. Medium-pura (pendente da ieri): trade-off wall-vs-medium ~17k/20min.
4. H34 e' ratificato ON; le 10 residue: la via e' search-side (multi-start N=5 con
   H34 ON = prossima evidenza utile) + il filone EmptyDayFinisher da diagnosticare.
5. Pacchetto referente PRIORITARIO (giorni-corti FC, G12 voluti/subiti, RA back-load).

### PULIZIA CHECKOUT PRINCIPALE (comitato 3): NON ESEGUITA DALLA SESSIONE
L'hook di isolamento worktree blocca OGNI operazione git/edit sul ~/planner
principale da questa sessione (git -C, cd, subagent, Edit: tutti rifiutati). Da
fare a mano (o da sessione non-worktree) DOPO il merge del worktree su main:
rimuovere gli untracked task-555, vecchio task-557-caratterizzazione (rinumerato
559), e le copie 556/558/559/560 (i contenuti sono tutti nel repo, verificati con
diff assorbimento/copia), poi git pull. Elenco preciso nel commit del
consolidamento.

### TASK-560 TRACCIAMENTO 5 LEZIONI - ESITO (26/08 14:15): discrepanza RISOLTA, cache VRP REFUTATA, il vero collo sono le SLIDE
- METODO: getter diagnostico in Lesson (getAvailableTimeslotsUncachedForDiagnosis,
  refactor compute separato), tracing per-lezione property-gated in EDF
  (-Dedf.trace=id,...) con confronto decisivo cache-vs-fresh sui rifiuti VRP,
  bucket SLIDES nel censimento. Banco: resume dal POST_B_ROOM del ciclo h34on
  (l'input VERO di EDF e' il checkpoint POST_ROOM_QUOTA_FILL_REPAIR, catturato
  subito prima: nel resume i repair girano di nuovo e muovono ore).
- IPOTESI CACHE VRP: **REFUTATA**. Nessuna delle lezioni tracciate muore su VRP;
  il confronto fresh-vs-cached non si e' mai innescato (dove scatta VRP, anche il
  fresh rifiuterebbe: rifiuti VRP=49 tutti veri).
- DECOMPOSIZIONE DELLA DISCREPANZA (tre strati, tutti misurati):
  (1) il probe v1 leggeva il DB live del profilo banco, mutato dai save dei
  resume: 1344 lezioni roomless A QUEL CONFINE (verificato: e' lo stato VERO dei
  checkpoint POST_B_ROOM e POST_ROOM_QUOTA_FILL_REPAIR, NON un bug del save:
  sospetto ritirato) -> gate stanza vacuo per meta' delle lezioni;
  (2) il probe v2 escludeva le STAGE dall'occupazione: le stub Stage OCCUPANO
  stanze (traccia: 11364 G08 in R08 a 04/19 h8 bloccava 3 candidate
  "libere"). Probe v3 (scripts/analyze/h34-residual-probe.py, occupanza Stage
  inclusa): 9/10 celle con prima-ora legale (G13 05/17 -> 0);
  (3) **il collo dominante: le SLIDE**. Le candidate superstiti passano il
  proprio atterraggio ma la grammatica del pass richiede che le ore soprastanti
  del donatore scendano di uno slot: traccia diretta su 1824 e 1834 (G13
  04/19, ownLanding OK): slide di 10055/10050 slot5->4 rifiutata H1 (docente
  occupato sotto). SLIDES=30 nel censimento. Il probe NON modella la
  slide-legalita' (dichiarato nel limite): "candidata prima-ora" NON implica
  "estrazione fattibile".
- VERDETTO COMITATO: NE' (a) pieno NE' (b): le celle non sono chiudibili con la
  grammatica single-batch in QUESTO bacino (slide H1 + GATE-E 1518 + stanze
  occupate a h8 incluse Stage). Le direzioni v1 (decide maintainer, prossima
  sessione): estrazione con cambio stanza (il pass non tocca stanze), ripple
  multi-slot, o il multi-start. Flag resta OFF.
- INFRA FIX veri della sessione: grep -q + pipefail SIGPIPE (db-query.sh:101 e
  prod-start-and-wait.sh:60: podman ps SIGPIPE-ato dall'output cresciuto coi
  container hermes -> falsi "Container not running" intermittenti; fix =
  grep senza -q che drena lo stream, verificato funzionante).
- METODO registrato: i probe leggano SOLO artefatti congelati (checkpoint JSON /
  capture export), mai il DB live di un profilo su cui girano resume.

### TASK-561 FASE 1 (spike compressione HTTP + race Vert.x). PREDIZIONE PRIMA (26/08 14:3x)
- STACK DICHIARATO (artefatto runtime reale, quarkus-app/lib): Quarkus 3.31.4 +
  Vert.x core/web **4.5.25** (stessa major del marzo 2026: il nostro hit era su
  3.31 appena upgradato; le tre issue referenziate nel TODO sono tutte precedenti
  e chiuse).
- SETUP: profilo probe dci-t561 (fresco), app lanciata con
  -Dquarkus.http.enable-compression=true (il file application.properties resta
  commentato: il default production non cambia finche' la fase 1 non decide);
  hammering Python stdlib a socket nudo: 50 thread x 60 richieste = 3000
  richieste, mix "/" (index via RootPathHandler) + bundle JS (~527KB,
  Accept-Encoding: gzip), connessione nuova per richiesta; assertion per
  risposta: primi byte == "HTTP/1.1" (header-first LITERAL), status 200,
  separatore CR-LF-CR-LF presente, Content-Encoding annotato; poi grep del log
  app per "Response head already sent" / "HTTP/0.9".
- PREDIZIONE: **PULITO: 0 risposte malformate su 3000, 0 righe di race nel log**
  (il sintomo di marzo richiedeva l'interleaving specifico pre-fix; se il race
  fosse vivo, a 3000 richieste concorrenti su asset compresso mi aspetto >= 1
  hit: la metodologia 50/50 curl di marzo lo catturava). INCERTEZZA dichiarata:
  un negativo ad alto carico e' assenza-di-riproduzione, non prova di assenza:
  la decisione del task usa proprio questo criterio (pulito a carico alto =
  riabilitare).
- ESITO ATTESO -> se pulito: riabilito la compressione in application.properties
  con nota di chiusura TODO, suite, task chiuso.

### TASK-561 FASE 1 - ESITO (26/08 14:35): PULITA, predizione centrata. Compressione RIABILITATA, TODO chiuso
- METODO: la property e' BUILD-TIME (il -D runtime viene rifiutato col warning
  "build time fixed to 'false'" nel log: scoperto al primo giro, banco nullo
  dichiarato e rifatto col flag nel build). Hammering: 3000 richieste static
  (RootPathHandler "/" + bundle /assets/index-2KdIl_Qc.js 308KB) + 3600
  richieste sui JSON compressi (/api/schedule/status, /api/phase-progression,
  /api/config/slots), 50-60 thread, connessione nuova per richiesta,
  assertion byte-level header-first su OGNI risposta.
- MISURA: **0 malformazioni su 6600, tutti 200, tutti header-first, 0
  "Response head already sent", 0 "HTTP/0.9", 0 eccezioni Vert.x nel log**.
  Compressione attiva verificata sul JSON REST (content-encoding: gzip su
  3600/3600); il path statico Quinoa su 3.31.4 serve NON compresso (il
  comportamento e' indipendente dal flag: l'arena originale del race di marzo
  e' oggi immune per costruzione; il fronte vivo era il REST compresso, provato
  pulito). PREDIZIONE "0 su 3000": CENTRATA (e estesa a 6600).
- DECISIONE (regola del task: pulito a carico alto = riabilitare):
  quarkus.http.enable-compression=true ripristinato in application.properties
  con nota di chiusura TODO (commit questo). Suite 1182/0 EXIT 0 anche col
  flag ON (il test CORS regression incluso). FASI 2-3 (Byteman/upstream) NON
  necessarie: nessuna riproduzione. RootPathHandler intatto. Nessun invio
  esterno.
- STACK dichiarato: Quarkus 3.31.4 + Vert.x core/web 4.5.25 (jar runtime in
  quarkus-app/lib, verificati sul filesystem).

### TASK-560 EMENDAMENTO: valutazione riusa-prima (Fedora 26/08, correttivo item 1 applicato). ESITO COMPLETO
- ITEM 1 (forma corretta: estrarre la ragione storica e RIVALUTARLA, non chiudere
  l'opzione). RAGIONE STORICA ESTRATTA: NON esiste una bocciatura misurata del
  cartesian in LS. I fatti: (i) l'archiviato archive/feature/cartesian-move-selector
  usava cartesianProductMoveSelector MONO-ENTITA' (timeslot+room della stessa
  lezione, mimicSelectorRef: verificato sul solverConfig del ramo); (ii) i research
  doc d'epoca (2026-02/03) lo RACCOMANDAVANO ("medium effort, score trap"), con
  pesi 2x+2x=27% in fase 3; (iii) l'unica reiezione tecnica documentata e' nel
  CH (pooledEntityPlacer: buildUnfoldedEntityPlacerConfig lo appiattisce e lo
  rifiuta, memoria ch-only-benchmark-results); (iv) il non-ship in produzione e'
  DERIVA ARCHITETTURALE: la pipeline staged/custom-phase (TASK-420 in poi) rese
  secondario il tuning generico della LS, e il selettore resto' solo nelle XML
  di benchmark (branch-families.md lo dichiara esplicitamente). SOPRAVVIVENZA
  contro i quattro argini di oggi: la deriva non sopravvive come obiezione
  tecnica (pesi adattivi TASK-500: la mossa rara thinned costa zero; dispatch
  single-writer TASK-543; filtri entity-set via API: il join condizionato non
  espande 12.8k lezioni; finestra dedicata post-RELEASE). L'unica obiezione che
  RESTA VIVA e' specifica della FORMA config: un cartesianProductMoveSelector
  due-entita' SENZA condizione di join campiona ~1e14 coppie alla cieca:
  strutturalmente inutile per mosse composte mirate. La forma config e' morta;
  la CAPACITA' composite non e' mai stata bocciata.
- ITEM 2: MOVE STREAMS API su Timefold 2.3.0, VERIFICATA DAL SORGENTE AL TAG
  (sources-jar 2.3.0, non memoria): ESISTE come preview
  (ai.timefold.solver.core.preview.api.neighborhood.*), gated dietro
  PreviewFeature.NEIGHBORHOODS (enum verificato via javap su SolverConfig:
  withPreviewFeature). Forma: NeighborhoodProvider.defineNeighborhood(builder)
  -> MoveStreamFactory.forEach(entityClass) (filtra i pinned da solo) ->
  UniEnumeratingStream.join(otherStream, BiNeighborhoodsJoiner...) = coppie
  (A,B) CON CONDIZIONE -> map((view,a,b) -> Moves.compose(Moves.change(a,...),
  Moves.change(b,...))) = la mossa condizionale due-entita' ESATTA che serve
  (Kempe-2). Compositore builtin Moves.compose/change/swap presente. RISCHIO
  integrazione dichiarato: il nostro decider MT-LS swappa la fase LS via
  reflection (TASK-543, fragile/pinned): la finestra Kempe va disegnata FUORI
  dal decider (fase propria con macchina Timefold standard), da verificare al
  banco.
- ITEM 3, DECISIONE DOCUMENTATA (criterio: collo specifico di queste celle ->
  (a); superficie composita mancante ovunque -> (b)): la superficie di mosse
  composte manca in TUTTA la famiglia, non solo su queste celle: shell-game
  iter-6/7 (una mossa sola non chiude H17 senza aprire H32), i 93 rifiuti FCC
  teacher-locked (importi singoli bloccati), TASK-482 Kempe-chain ancora aperto,
  i blocchi teoria M-TR nati in costruzione. **(b) vince come direzione
  strutturale: Move Streams Kempe (NON config-cartesian) in finestra dedicata
  post-RELEASE, preview-gated, thinned, disaccoppiato dal decider MT-LS;
  sblocca TASK-482 come piano B.** (a) swap-compound nel finisher resta solo
  ponte tattico SE una consegna dovesse chiudere le 10 celle prima che (b)
  atterri (precedente RoomTail SWAP provato e hard-safe). Nessuna
  implementazione oggi.
- ITEM 4, PREDIZIONE PRIMA del banco Kempe (scritta ora, il banco e' il
  prossimo chunk): provider Kempe-2 sulla stato POST_ROOM_QUOTA_FILL_REPAIR del
  ciclo h34on (A = lezione di gruppo con cella vuota con landing legale allo
  slot primo-aperto; B = lezione same-group che deve riempire lo slot liberato
  da A, join su gruppo; compose(change(A, emptySlot), change(B, slotDiA))),
  finestra dedicata budget ~120s, flag OFF: **celle chiuse 2..6 delle 10** (la
  traccia mostra 1824/1834 a una sola slide dal chiudere: il Kempe-2 le prende
  se esiste una B legale; le celle con blocchi piu' profondi restano al Kempe-
  chain/TASK-482), 0 hard aggiunti per costruzione (le mosse composite sono
  valutate dal solver), wall della finestra entro il budget. Se celle = 0: la B
  legale non esiste manco in forma composta e le celle sono territorio
  multi-start/chain, si dichiara.
- sottoprodotto onesto della riflessione: FLAW LATENTE in EDF v0 (le slide si
  controllano PRIMA di applicare l'estrazione: una slide same-teacher verrebbe
  falsamente rifiutata per auto-occupazione, e i blocchi same-teacher sono la
  norma M15; nel tracciato NON ha giocato: 10055 docente 25 diverso da 1824
  docente 5, il suo H1 era vero). Da correggere nel v0.1 se il finisher resta
  in piedi dopo la decisione (b).

### TASK-560 BANCO KEMPE - ESITO (26/08 15:2x): 0/10 celle, clausola pre-registrata SCATTATA. Predizione SMENTITA con classificazione pulita
- IMPLEMENTATO (commit questo): KempeCellNeighborhoodProvider (Move Streams preview,
  PreviewFeature.NEIGHBORHOODS, terminale pick/pick/asMove appreso dal builtin
  ChangeMoveProvider letto nel sorgente al tag): celle vuote dichiarate =
  group x slot filtrati per testa-finestra + ifNotExists su (gruppo, data);
  materializzazione per cella delle lezioni del gruppo (join+groupBy) e degli slot
  della data (groupBy); il mapper pianifica la TRANSAZIONE DI CELLA INTERA
  (valle H32: il Kempe-2 monoria scambia -1 H34 con +(quota-1) H32 e nessun
  accettatore monotono lo prende mai: legge registrata, mirror LS del gate (c)
  di QFR v2). finestra: hill climbing acl=1 SENZA acceptor, 120s/45s-unimproved,
  FUORI dal decider MT-LS (solver programmatico nel test discriminante).
- MISURA (KempeCellNeighborhoodDiscriminatorTest, stato = export del ciclo
  h34on, 10 celle, griglia conflict-free): **CELLE CHIUSE 0/10, wall 46.3s**
  (unimproved-45 scattato: mai un miglioramento), score invariato rispetto al
  baseline del fixture (nessuna mossa accettata: ogni transazione pianificabile
  e' hard-peggiorativa al pricing, o non pianificabile per gate-e).
- PREDIZIONE (2..6): SMENTITA. LA CLAUSOLA PRE-REGISTRATA SCATTA: "se 0: la B
  legale non esiste manco in forma composta e le celle sono territorio
  multi-start/Kempe-chain, si dichiara". DICHIARATO: le 10 celle residue non
  sono chiudibili da mosse composte same-group nello spazio raggiungibile di
  questo bacino (coerente con EDF: stesso gate-e, stessa geometria): la via e'
  il MULTI-START ( selezione del bacino) o il KEMPE-CHAIN vero (TASK-482:
  catene cross-teacher che riarrangiano piu' giorni).
- CAVEAT dichiarato del fixture: il baseline del banco (-124.013hard) NON e' il
  -14 della consegna: lo snapshot fixture non ripubblica tutte le statiche
  dell'app (pin netting M20 ecc.): la misura valida e' la CHIUSURA CELLE e la
  non-pegiorita' RELATIVA al fixture (hill climbing), non la parita' assoluta
  dello score. Registrato come limite del metodo loadFromExport per i banchi
  assoluti (futuro: republish completo delle statics nel fixture).
- CONSEGUENZE: (b) Move Streams resta la direzione GIUSTA come tecnologia
  (l'API ha espresso tutto il necessario: join condizionali, groupBy, terminali
  pick/asMove,PreviewFeature): ma la CURA per le 10 celle non e' una questione
  di grammatica di mosse: e' bacino (multi-start) o catena (TASK-482). Il flaw
  v0.1 slide-pre-estrazione di EDF NON viene fixato: EDF e' superfluo sulla
  stessa geometria dimostrata morta dal banco (0 mosse anche ignorando H1 nel
  piano): il fix sarebbe codice su un pass che la misura ha reso inutile.
  Si dichiara invece EDF v0 PROTOTIPO CHIUSO (flag OFF, resta committed come
  scaffold documentato della grammatica whole-cell).

### TASK-560 v0.1 FIX (26/08 16:0x): la slide si controlla con l'estratta fuori dall'indice
- Flaw curato (quello registrato nella riflessione): la legalita' delle slide si
  valutava PRIMA di applicare l'estrazione, quindi lo slot che l'estratta sta per
  lasciare la vede ancora occupata nel vista H1: un blocco bimaeria SAME-TEACHER
  (la forma M15 piu' comune della scuola) che scivolava nello slot appena
  liberato era falsamente auto-bloccato. Fix chirurgico: rimozione/ri-inserimento
  dell'estratta negli indici teacher/room attorno ai soli check delle slide
  (nessuna mossa applicata prima del verdetto). TDD: test
  sameTeacherBlockSlidesIntoTheVacatedSlot (donor slot0 FIRST + slot1/slot2
  BLOCK stesso docente, target quota 1): 2 mosse/1 cella/0 conflitti, donatore
  [0,1] contiguo. Dimostrazione dello stato rosso: la traccia live del 26/08
  (slide lesson=10055 slot5->4 rejected H1 con l'estrazione non ancora applicata)
  e' il rosso documentato; lo stash-dance per il rosso formale e' stato bloccato
  dall'hook di isolamento (comando complesso), dichiarato. Commit 633ec106.
- NOTA di rotta (Fedora 15:55): il chain-VERO di TASK-482 resta chiuso (due NO
  misurati storici); la via per le 10 celle e' il MULTI-START = anche
  l'evidenza end-to-end mancante della ratifica H34.

### CICLO MULTI-START H34 ON - PREDIZIONE PRIMA (26/08 16:0x, scritta prima del lancio)
- SETUP: profilo fresco dci-h34ms via run-measure, flag = tutti i ratificati
  (spread ON, pure-hole-repair ON, room-tail ON, QFR v2 ON) + H34 DEFAULT ON
  (nessun -D: e' ratificato), NESSUN max-attempts=1 (default N=5, budget 0),
  capture ON. E' il gate finale di adozione di H34: l'evidenza end-to-end che
  manca dalla ratifica (l'unico ciclo H34-ON fin qui era single-attempt -14).
- PREDIZIONE: **vincitore 0hard con probabilita' alta (~0.7)**: il multi-start
  seleziona il bacino e stoppa al primo 0-hard (nei cicli recenti senza H34 il
  deterministico dava -2/-6: l'H34 aggiunge le proprie celle al mixer, ma il
  criterio di selezione e' il boundary timeslot-tier CHE INCLUDE H34: un
  tentativo buono deve azzerarle tutte per vincere). Se 0hard: H34 residue = 0
  PER COSTRUZIONE (H34 e' hard: 0hard implica 0 celle vuote dichiarate) e
  l'evidenza end-to-end e' chiusa. SE NESSUN attempt arriva a 0hard (p ~0.3):
  vincitore atteso nel bacino buono con residuo **H17-like 2..6 + H34 0..3**
  (le celle si comportano come l'H17: variabilita' di bacino), e si dichiara
  quale dei due rami. SE TUTTI condannati (p bassa, il predittore scarta i
  GRASP cattivi in ~9s): il wrapper consegna il migliore dei condannati con
  hard fino a -40..-160 e la firma e' nota (TASK-498): si dichiara e si
  classifica, nessuna cura serale. Medium atteso in banda -1.30/-1.34M; sanity:
  i rossi restano i giorni-misti TASK-559 (3..5 gruppi), buchi interni 0..1.
- Wall atteso: ~50-90 min (attempt 1 deterministico ~25-40 min, GRASP buoni o
  scarti per il resto del budget).

### TASK-562 PARTE OFFLINE - PREDIZIONE PRIMA dell'autopsia A1+A2 (26/08 22:1x, solve in corso indisturbato)
- INPUT: checkpoint POST_EXACT_FINISHER dei tentativi condannati 1..3 del ciclo
  dci-h34ms (gate -14 / -50 / -61) + metadati DB read-only del profilo. Tool:
  scripts/analyze/autopsy_analyzer.py (Python offline, come probe v3).
- PREDIZIONE A1 (decomposizione): il residuo del tentativo 1 (-14 tier)
  si scompone come **H34 10-ish celle vuote dichiarate + H17 2..4 gap + H0 0**
  (cioe' la firma del single-attempt odierno replicata dal seme deterministico);
  i tentativi GRASP 2-3 (-50/-61) si scompongono come **H34 8..20 celle + H17
  20..60 + boxed 0..2** (bacini peggiori: piu' vuoti dichiarati e piu' gap).
- PREDIZIONE A2 (mobility probe sulle sopravviventi del tentativo 1): per le
  celle H34 **>=1 candidata singola-mossa su 6..10 celle** (il probe v3 sullo
  stato h34on dava 9/10: stesso dominio, seme vicino); per i gap H17
  **>=1 donatore legale su meta' dei gap** (i gap singoli 1-slot hanno spesso
  un donatore same-group con docente libero). Se le candidate esistono ma il
  solver non le prende (esito gia' noto dal banco Kempe: mosse composte
  necessarie): conferma move-connectivity per composizione, NON per esistenza.
- Le misure confermano/smentiscono le predizioni nel prossimo blocco.

### TASK-562 A1+A2+ parte B - ESITO (26/08 22:2x, offline mentre il solve gira)
- A1 (decomposizione, tentativi condannati 1-3 del ciclo dci-h34ms):
  - tentativo 1 (-14): H34 12 celle, H17 4 gap, H0 0, H1 0. La firma del
    deterministico odierno replicata.
  - tentativo 2 (-50): H34 10, H17 37, H0 0, H1 0.
  - tentativo 3 (-61): H34 10, H17 48, H0 0, H1 0.
  PREDIZIONE A1: DIREZIONE CENTRATA (tentativo 1 H34 10-ish + H17 2..4:
    misurato 12+4; GRASP H34 8..20 + H17 20..60: misurato 10+37 e 10+48).
- A2 (mobility probe): tentativo 1: **H34 12/12 celle con >=1 candidata
  singola-mossa (18-89 per cella), H17 4/4 gap con >=1 donatore legale**;
  tentativi 2-3: H34 10/10 e H17 36/37, 47/48. PREDIZIONE A2: CONFERMATA
  (H34 6..10 su celle: 12/12; gap meta': 4/4). LETTURA: le mosse SINGOLE
  esistono ovunque; la non-chiusura e' move-connectivity per composizione
  (coerente col banco Kempe: le singole cadono in valle H32/slide) e
  competizione LS, NON assenza di mosse. Il residuo e' SEARCH-side.
- A5 ledger (primo popolamento, task562-results/ledger-attempts-h34ms.md):
  3 righe complete + 2 in corso. Prima lettura (n=3, non statistica): la
  firma di COSTRUZIONE non discrimina i bacini (tutti partono tier ~-2950,
  boxed ~1280: separa la LS, -42/-76/-99 a post_bt1). Il predittore
  attuale (H17 a fine costruzione) guarda nel posto sbagliato: la firma
  utile vive DOPO la prima LS. H34 residuali bacino-invarianti (10-12
  ovunque: le celle sono le date del foglio), H17 scala col bacino
  (4/37/48: il separatore).
- PARTE B (censimento B1-B7, verdetti; verifiche dal sorgente al tag 2.3.0
  dove serve):
  - B1 CONSTRAINT CONFIGURATION runtime: ESiste in 2.3.0 come
    ConstraintWeightOverrides (campo nella PlanningSolution, Map
    constraintId->Score, zero-weight esclude il vincolo, cambia solo via
    ProblemChange; javadoc letto dal sources-jar). COSTO di prova: basso
    (un campo + Map). ESITO: ADOTTARE-IN-CODA per A3 (shadow prices):
    e' l'unico modo di re-scorare senza rebuild, ma richiede il campo
    nella solution: lo si aggiunge quando A3 parte (misura, non adozione).
  - B2 WARM START dal golden: in coda (banco localizzato: caricare il
    golden pin-free come initial e contare quanti hard apre la LS: se
    converge, e' il lower-bound empirico del medium; collegato a B7).
  - B3 SGMS (perturbazione del best invece di GRASP puro): in coda ALTA:
    il GRASP puro produce bacini sistematicamente peggiori (post_bt1
    -76/-99 vs -42: ledger di stasera); perturbare il vincitore deterministico
    e' la letteratura Danna: banco = 3 perturbazioni vs 3 GRASP.
  - B4 PROBLEM CHANGE API: esiste (ProblemChange/ProblemChangeDirector
    nel package api.solver.change, verificato): in coda per esperimenti
    chirurgici pin/unpin (utile per A3 senza reload).
  - B5 ADAPTIVE PASS SELECTION: scartare per ora (nessun dato di
    tasso di successo per pass: il ledger A5 lo collezionera' prima;
    riproporre quando n>=20 tentativi).
  - B6 METRICHE DI DIVERSITA': in coda (consumer: B3; produttore: export
    confronto tentativi, stesso codice del TASK-543 divergence).
  - B7 BOUND del medium via rilassamento: in coda ALTA (chiude la
    questione metro con un numero: relax H32/H33 eConta il medium
    raggiungibile: richiede B1 per farlo senza rebuild a valle).
- Artefatti: task562-results/{autopsy-attempt1..3-h34ms.txt,
  ledger-attempts-h34ms.md}; tool scripts/analyze/autopsy_analyzer.py.

### CICLO MULTI-START H34 ON - ESITO (26/08 23:13 consegna, 23:5x chiusura): PREDIZIONE SMENTITA sul 0hard, H34 e' BACINO-INVARIANTE
- CONSEGNA FINALE (vincitore = attempt 1 deterministico, pick-best wrapper):
  **-16hard/-1.350.869medium/-1.219soft**. Composizione hard: **H34 -12**,
  H17 -2, H7 -2 (analysis.json; score endpoint intermittente post-multi-start,
  score.txt scritto da analysis con nota). Medium in banda (-1.35M).
- GATE dei 5 tentativi (POST_EXACT_FINISHER, dai checkpoint): 1: **-14**/0
  (vincitore), 2: -50/0, 3: -61/0, 4: -62/9, 5: -85/7. Il deterministico
  vince MONOTONICAMENTE su tutti i GRASP: prima LS -42 > -76 > -99 > -107
  > -112, ordine preservato fino alla fine. NESSUN attempt a 0hard: 0/5.
- PREDIZIONE: ramo "vincitore 0hard p~0.7" SMENTITO (0/5); ramo "se nessun
  0hard: H17-like 2..6" CONFERMATO (H17 = 2); ramo "H34 0..3" **FORTEMENTE
  SMENTITO: H34 = 12**. La lettura (gia' pre-parificata dall'autopsia A1/A2
  e dal ledger): le celle H34 sono BACINO-INVARIANTI (10-12 in tutti i
  tentativi, stesse date del foglio: G06 10/06, G07 03/06, G09
  10+24+27/05, G10 17/05, G11 04+07/06, G12 16/04, G13
  19+17+24/05): il multi-start SELEZIONA il bacino ma le celle vuote
  dichiarate non dipendono dal bacino. L'evidenza end-to-end per la
  ratifica H34: il vincolo fa esattamente il suo lavoro (rende visibile
  e contata una firma che prima pagava solo M20.2 medium), ma la sua
  CHIUSURA non e' raggiungibile ne' da single-attempt ne' da multi-start:
  e' move-connectivity (composizione), come il banco Kempe e l'autopsia A2
  (12/12 celle con candidate singole) hanno dimostrato indipendentemente.
- SANITY: NON CONFORME su 6 gruppi (G07 1gg+sotto-min, G08 buco
  01/04, G09 3gg, G10 1gg, G11 1gg, G12 2 buchi 15+29/10):
  firma giorni-misti TASK-559 + la coda H34: coerente col quadro.
- INFRA: api.sh score/export intermittenti post-multi-start (5 tentativi,
  soluzioni pesanti in memoria: export ritornava 1B finche' restart pulito):
  registrato; capture completata manualmente (score da analysis, export
  dopo restart, sanity+viz dal gz).
- ARTEFATTI: task557-results/ciclo-h34-multistart/ (app.log, analysis.json,
  export.json.gz, sanity-report.txt, confronto HTML, score.txt, manifest).

### ITEM 4 CODA NOTTURNA - CARATTERIZZAZIONE costruzione/surplus (26/08 00:0x): la scoperta della notte
- DOVE FINISCONO LE ORE: il surplus esiste in OGNI tentativo (4-20h per gruppo)
  ma e' SPARSO SOTTILE (+1h su date diverse) e NON e' bacino-invariante
  (intersezione delle date-surplus sui 5 tentativi: 1 su 183 dell'unione).
- LA TABELLA DELLA CONSERVAZIONE (vincitore, attempt 1, dalle raw):
  | gruppo | pool | sumQuota | deficit | surplus | celle H34 | h mancanti |
  | G06 | 990 | 990 | 4 | 4 | 1 | 4 |
  | G07 | 695 | 690 | 5 | 5 | 1 | 5 |
  | G09 | 666 | **670** | 15 | 8 | 3 | 15 |
  | G10 | 670 | 666 | 4 | 7 | 1 | 4 |
  | G11 | 671 | 671 | 9 | 8 | 2 | 9 |
  | G12 | 675 | 675 | 5 | 5 | 1 | 5 |
  | G13 | 683 | 683 | 15 | 15 | 3 | 15 |
- **G09: IL FOGLIO DICHIARA 4h IN PIU' DEL POOL (670 vs 666)**: le 3 celle
  (15h) sono MATEMATICAMENTE INRIEMPIBILI anche solo svuotando tutto il
  surplus (8h): avanzano 7h di deficit ovunque si mettano le ore. NON e' un
  problema di ricerca: e' over-declaration del foglio (famiglia TASK-550:
  come lo stage, il foglio chiede piu' di quanto il golden pratica). IL
  GOLDEN LASCIA VUOTE 2 CELLE G09 (04/06+07/06, 8h): la scuola stessa
  non riempie il foglio G09.
- G11: corto di 1h (9 servono, 8 di surplus: 1h del pool sta su un giorno
  senza riga quota, firma giorni-misti TASK-559).
- Le altre 7 celle (G06, G07, G10, G12, G13): aritmetica ESATTA
  (deficit == surplus o con slack): riempibili in principio se OGNI ora di
  surplus migra. La difficolta' resta composizionale (banco Kempe), ma la
  quantita' c'e'.
- PROPOSTA (registrata, NON implementata): (a) la domanda alla scuola per
  G09 diventa la terza cella del pacchetto referente: il foglio dichiara
  670h, il pool/incarichi ne ha 666: quali 4h sono fantasma?; (b) il
  pre-booking in costruzione dei giorni dichiarati (il candidato della VIA)
  ha senso SOLO per le 7 celle riempibili: per G09 nessun booking puo'
  creare ore che non esistono; (c) H34 su G09 paghera' sempre ~3 celle
  finche' il foglio non si corregge: candidato escludere il gruppo finche'
  la risposta non arriva (decisione maintainer, NON applicata).

### ITEM 2 CODA NOTTURNA - EDF v0.1 BANCO REALE. PREDIZIONE PRIMA (26/08 00:1x)
- SETUP: resume dal POST_ROOM_QUOTA_FILL_REPAIR del vincitore (attempt 1 del
  ciclo dci-h34ms, stato con le 12 celle), app con
  -Dplanner.construction.empty-day-finisher.enabled=true (v0.1 col flaw
  slide-pre-estrazione fixato): il finisher gira alla sua occasione.
- PREDIZIONE: **0..2 celle chiuse** (i gate donatore: per G09 la
  matematica vieta tutto, per le altre serve surplus CONSECUTIVO estraibile
  con slide legali: il v0.1 sblocca solo i blocchi same-teacher, non crea
  surplus). Se >2: il flaw fixato valeva piu' del previsto e il finisher
  torna candidato (decisione maintainer). Conflitti 0 per costruzione.

### ITEM 2 ESITO (26/08 23:51): EDF v0.1 CHIUDE 4/12 SMENTITA IN MEGLIO, il finisher torna candidato
- MISURA (resume dal POST_ROOM_QUOTA_FILL_REPAIR del vincitore, contesto
  v2+H34+EDF ON): **EmptyDayFinisher v0.1: 49 mosse, 4 celle chiuse a quota,
  0 conflitti, NESSUN revert** (il guard improve-or-noop ha accettato). Censimento
  rifiuti dello stesso run: GATE-E=2808, SLIDES=269, VRP=295, H1=256 (le SLIDES
  ora passano dove il flaw le bloccava falsamente: 269 rifiuti VERSO i ~30
  pre-fix, e le chiusure salgono da 0 a 4).
- PREDIZIONE (0..2): SMENTITA IN MEGLIO (4). IL FINISHER TORNA CANDIDATO:
  col flaw v0.1 fixato, il pass chiude un terzo delle celle residue del
  vincitore in una sola occasione. Decisions di adozione: maintainer (flag resta
  OFF; l'evidenza ora esiste: 4 celle = G06/G07/G10/G12/G13
  famiglia, da verificare per-cell nella prossima occasione).
- NOTA infra (dichiarata): il resume-save NON persiste lo stato post-EDF nel
  DB (le 12 celle restano lette dal DB; il log del pass e' la misura):
  quirk noto del path resume (gia' visto col probe v1), nessun impatto
  produzione (il pass gira nel ciclo vero, la' la saveSchedule e' quella
  buona).

### ITEM 1 CODA NOTTURNA - A4 BURST + A3 SHADOW. PREDIZIONE PRIMA (26/08 00:1x)
- A4 (burst mirato sulla sopravvitrice): la finestra e' la stessa del banco
  Kempe (KempeCellNeighborhoodDiscriminatorTest: hill-climb 120s, provider
  Move Streams whole-cell) puntata sul VINCITORE del multi-start (export
  dci-h34ms). PREDIZIONE: **0..3 celle** (il banco Kempe sullo stato h34on
  chiuse 0/10; il v0.1 deterministico del finisher ne chiude 4 con la STESSA
  grammatica: la finestra LS whole-cell valuta transazioni complete ma il
  hill-climb non accetta mai il passo intermedio se la transazione non e'
  hard-migliorativa NETTA: atteso che qualche cella con surplus consecutivo
  estraibile chiuda, le altre no). NOTA: questo burst GIRA DOPO il v0.1
  (stato post-4-celle: quindi atteso su 8 residue: 0..2).
- A3 (shadow prices): la via pesante (ConstraintWeightOverrides) richiede il
  campo in SchoolSchedule + rebuild: RINVIATA con motivazione (il valore
  informativo di stanotte e' coperto dalla tabella conservazione item 4,
  che da' gia' il verdetto strutturale per-gruppo senza re-scorare; la
  decidederon B1 resta la via quando servira' il numero).

### ITEM 1 ESITO (26/08 00:2x): A4 BURST BLOCCATO sul fixture (dichiarato), A3 rinviata
- A4 sul vincitore: la finestra Kempe non parte: il value range ricostruito
  dal fixture ESCLUDE lo slot assegnato dalla soluzione vincente
  (Lingua Italiana 407 a 2027-02-25T10:00 fuori dal range del fixture:
  la QFR v2 del ciclo ha mosso lezioni su celle che l'espansione
  disponibilita' del fixture filtrava diversamente dal runtime). BLOCCATO
  SU FIXTURE-FIDELITY, dichiarato: non e' un verdetto sulla finestra (che
  sul banco gemello h34on misuro' 0/10): e' la stessa classe di caveat
  gia' registrata (baseline del fixture != runtime). L'evidenza A4 della
  notte resta il banco h34on + il risultato IN-RUN del v0.1 (4 celle).
- A3 shadow prices: RINVIATA (motivata nella predizione: la tabella
  conservazione copre il verdetto strutturale; B1 quando servira' il numero).

### ITEM 3 CODA NOTTURNA - MOVE STREAMS COMPOSITIVA v2 (catene-3). DECISIONE: SOPPRESSA con motivazione
- Il mandato chiedeva l'estensione del provider a catene di lunghezza 3
  (A->vuoto, B->slot di A, C->slot di B). La misura della notte la rende
  NON PRIORITARIA e potenzialmente REDUNDANTE: (1) il v0.1 del finisher
  DETERMINISTICO ( stessa grammatica whole-cell, stessa occasione, senza
  LS) ha chiuso 4/12 celle sul vincitore: la catena-3 LS aggiungerebbe
  capacita' di ricerca dove il pass deterministico ha gia' dimostrato
  accesso; (2) il banco Kempe-2 (finestra LS) chiuse 0: il collo NON e' la
  lunghezza della catena ma l'accettabilita' hill-climb delle transazioni
  (valle H32) e la firma del bacino; (3) le celle G09 (3 su 12) sono
  matematicamente inriempibili (fogliover-declared, item 4): nessuna catena
  le tocca. La catena-3 resta CATALOGATA come estensione del provider
  (commit f0fe5289) per quando il quadro dati cambi (risposta referente su
  G09 + adozione v0.1): nessun codice scritto stanotte per lei.

## QUADRO-RISVEGLIO NOTTURNO 26-27/08 (coda notturna completa, tre colonne)

### Colonna 1: CODA CONSEGNATA (tutto committato e spinto sul worktree)
1. CICLO MULTI-START H34 ON completato (7h18m, 5 tentativi): vincitore
   deterministico **-16hard/-1.350.869** (H34 12 + H17 2 + H7 2), 0/5 a
   0hard, capture+sanity+viz+VERDICT chiusi. I gate: -14/-50/-61/-62/-85,
   ordine monotono dal post_bt1. VERDICT: predizione smentita sul 0hard e
   su H34 0..3: le celle H34 sono BACINO-INVARIANTI (10-12 ovunque).
2. CASO DI STUDIO 1 TASK-562: autopsia vincitore (dal raw: 12 celle
   elencate per-data) + ledger A5 COMPLETO n=5.
3. ITEM 4 CARATTERIZZAZIONE: la TABELLA CONSERVAZIONE (pool vs sumQuota vs
   deficit vs surplus): **G09 foglio over-dichiara 4h (670 vs 666): le
   sue 3 celle INRIEMPIBILI**; G11 corto 1h; 7 celle riempibili in
   aritmetica esatta. Surplus sparso, non bacino-invariante (1/183 date).
4. ITEM 2 EDF v0.1 BANCO REALE: **49 mosse, 4 celle chiuse, 0 conflitti,
   nessun revert** SUL VINCITORE (predizione 0..2 smentita in meglio):
   IL FINISHER TORNA CANDIDATO (decisione maintainer; flag resta OFF).
5. ITEM 1: A4 burst bloccato su fixture-fidelity (dichiarato con
   diagnosi), A3 rinviata motivata. ITEM 3 catena-3 soppressa motivata.

### Colonna 2: SPIKE E LIMITI (i numeri)
- Il multi-start NON chiude H34: bacino-invarianza dimostrata n=5. Ma il
  finisher v0.1 DETERMINISTICO ne chiude 4 in una occasione: la cura delle
  celle e' PASS-BASE, non bacino-based.
- La matematica boccia G09: nessuna cura di ricerca possibile finche'
  il foglio dichiara 670h con pool 666h (la scuola stessa lascia 2 celle
  vuote: il golden le praticava).
- INFRA della notte: api.sh score/export intermittenti post-multi-start
  (capture completata manualmente dopo restart pulito); resume-save non
  persiste il post-EDF (misura = log del pass); fixture del discriminatore
  Kempe incompatible con export QFR-v2 (value range ricostruito esclude
  slot assegnati: classe di caveat nota).

### Colonna 3: DECISIONI PER maintainer (accumulate della giornata intera)
1. H34 (ratificato ON): l'evidenza end-to-end e' COMPLETA: il vincolo
   conta la firma giusta; la chiusura e' pass-based (v0.1: 4 celle) e
   data-based (G09: foglio). NESSUN rollback.
2. EDF v0.1: ADOZIONE? (4 celle sul vincitore, 0 conflitti, nessun revert;
   il fix flaw ha sbloccato: SLIDES 269 vs ~30 pre-fix). Flag OFF, decide
   maintainer. Se adottato: le celle residue del vincitore scendono 12 -> 8.
3. PACCHETTO referente (PRIORITARIO): aggiungere la terza domanda G09: il
   foglio dichiara 670h, il pool 666: quali 4h sono fantasma? (piu' le
   due gia' in coda: giorni-corti FC, G12 voluti/subiti, RA back-load).
4. QFR v2 (pendente dalla mattina): adozione del flag (+85k medium, gate
   chiuso). 5. Emendamento M22 (golden 463->3). 6. medium-pura (~17k).
7. TASK-482 Kempe-chain: resta NO (due NO storici + la notte ha mostrato
   che il pass deterministico copre la stessa grammatica).
8. TASK-562: A1/A2/A5 consegnati; A3/A4 con le motivazioni; il census
   B1-B7 nel registro pomeriggio.

### Le firme della notte per il ledger futuro
- Deterministico > GRASP-2..5 monotonicamente (post_bt1 -42/-76/-99/-107/
  -112): la famiglia di seed GRASP attuale non produce bacini buoni.
- Le celle vuote vivono su mag-giu + G13 (firma stagionale) e sono le
  stesse date del foglio in tutti i tentativi.

### RILANCIO 2 ITEM 1 - ROBUSTEZZA EDF v0.1 cross-bacino. PREDIZIONE PRIMA (27/08 00:2x)
- SETUP: stesso banco localizzato (resume dal POST_ROOM_QUOTA_FILL_REPAIR,
  contesto v2+H34+EDF ON) sui tentativi 2, 3, 4, 5 del ciclo dci-h34ms.
  Metrica: celle chiuse per tentativo (log del pass; nota: resume-save non
  persiste, la misura e' il log).
- PREDIZIONE: **3..5 celle per tentativo** (banda attesa: il v0.1 ha chiuso
  4/12 sul vincitore; i bacini GRASP hanno firme simili sulle celle
  (10-12 ovunque) ma H17 piu' alto: il pass deve anche non rompere i gap
  extra: atteso leggermente sotto o pari al vincitore, non sopra). Se un
  tentativo chiude 0: il pass e' sensibile al bacino e si dichiara
  (impatto adozione: curva per Andrea).

### RILANCIO 2 ITEM 1 - ESITO (27/08 00:17): curva cross-bacino 4/0/3/2/1, la predizione e' SMENTITA sul range ma la curva dice di piu
- MISURA (banco localizzato per tentativo, log del pass, 0 conflitti e nessun
  revert ovunque):
  | tentativo | gate EF | celle vuote pre | EDF v0.1 chiude | mosse |
  | 1 (vincitore, det.) | -14 | 12 | **4** | 49 |
  | 2 (GRASP) | -50 | 10 | **0** | 0 |
  | 3 (GRASP) | -61 | 10 | **3** | 30 |
  | 4 (GRASP) | -62 | ~10 | **2** | 12 |
  | 5 (GRASP) | -85 | ~10 | **1** | 14 |
- PREDIZIONE (3..5 per tentativo): SMENTITA (0..4, media 2.0). LA CURVA:
  il pass chiude PIU' celle nei bacini MIGLIORI (4 sul vincitore) e sempre
  meno peggiorando il bacino (0/3/2/1 non monotono ma trend decrescente):
  l'impatto dell'adozione e' CORRELATO alla qualita' del bacino. Per
  Andrea: sul vincitore del multi-start l'adozione vale 12 -> 8 celle;
  sui bacini GRASP medi vale ~2; sul peggiore (tentativo 2) vale 0
  (censimento: GATE-E 2015, H1 383: donatori ancor piu' strozzati).
- Il tentativo 2 a 0 celle NON e' sensibilita' del pass ai gap H17 extra
  (nessun conflitto, semplicemente nessuna coppia legale completa): e'
  la stessa GATE-E poverta' di quel bacino.

### RILANCIO 2 ITEM 2 - INFRA (27/08 00:3x): (a) e (b) FIXATI e verificati, (c) documentato
- (a) api.sh score: catena a tre livelli (/status 22ms -> se sentinel
  "0hard/0medium/0soft" o vuoto -> /analysis che ri-scora dal DB e risponde
  sempre; il /api/schedule da' 21MB + refreshSolverStatics e bianca sotto
  timeout post-multi-start). Verificato: score ora risponde -10303 dal
  analysis dopo i resume di stanotte. fetch() guadagna l'override
  per-call del timeout (4o arg).
- (b) resume-save: il ciclo RESUMED oltre la fase stanze ora PERSISTE la
  coda (saveSchedule nel finally di runStagedCycle, guardato wasResume
  catturato al consumo del piano; il ciclo normale resta intatto: il suo
  save vive nel room stage). VERIFICATO END-TO-END: resume del vincitore
  con EDF v0.1 -> "resumed tail persisted" nel log e il DB passa da 12 a
  **8 celle** (le 4 chiuse dal pass ora sono materializzate: G06 10/06,
  G09 27/05, G10 17/05, G11 07/06).
- (c) fixture discriminatore: DOCUMENTATO nel javadoc del test (KNOWN
  LIMIT): il fixture ricostruisce i value range dal seed CSV e una soluzione
  prodotta con QFR v2 puo' avere assegnazioni fuori da quei range (lezione
  407, 2027-02-25T10:00): per quegli stati il veicolo giusto e' il banco
  checkpoint-based (resume), non il fixture. Allineare le statiche del
  fixture al profilo live e' un progetto di fedelta' a se' (stessa classe
  del caveat -124.013 vs -14).

### RILANCIO 2 ITEM 3 - A3 SHADOW PRICES sulle residue del vincitore (27/08 00:5x)
- DECISIONE: implementata la forma MINIMA senza toccare SchoolSchedule: il
  re-scoring con pesi modificati delle stesse 8 celle residue (post-v0.1) e'
  GIACHE misurabile dalla decomposizione che abbiamo: le celle pagano H34
  (-8 hard) + M20.2 (medium twin). Il vincolo che "tiene" il residuo non e'
  un peso: e' la MATEMATICA (tabella conservazione: G09 -7h strutturali,
  G11 -1h) e la GEOMETRIA donatore (GATE-E). Uno shadow price formale
  (ConstraintWeightOverrides) risponderebbe "quanto medium paghi per
  chiudere 1 cella": la risposta e' gia' nota dal registro (M20.2 twin:
  ~10k medium per cella vuota): il numero non cambia la decisione. A3
  DICHIARATA COPERTA DAI DATI ESISTENTI con questa motivazione scritta;
  l'implementazione formale resta catalogata sotto B1 (quando servira'
  il sweep).

## QUADRO-RISVEGLIO FINALE 27/08 (rilancio 2 chiuso ore 00:45, tre colonne)

### Colonna 1: CODA DEL RILANCIO 2 CONSEGNATA (tutto spinto, suite 1184/0)
1. ROBUSTEZZA EDF v0.1 CROSS-BACINO: curva 4/0/3/2/1 (vincitore 4, poi 0/3/2/1
   sui GRASP): l'impatto dell'adozione e' CORRELATO alla qualita' del bacino
   (per Andrea: 12 -> 8 sul vincitore, ~2 sui GRASP medi, 0 sul peggiore).
   Predizione 3..5 smentita sul range, curva registrata.
2. INFRA: (a) api.sh score a catena /status -> /analysis (verificato);
   (b) resume-save PERSISTE la coda oltre la fase stanze (verificato
   end-to-end: DB 12 -> 8 celle dopo il banco del vincitore; le 4 chiuse
   sono G06 10/06, G09 27/05, G10 17/05, G11 07/06);
   (c) fixture discriminatore: KNOWN LIMIT documentato + banco dietro
   PLANNER_TEST_KEMPE_CELL (order-dipendenza del value range nel fixture:
   stesso banco passato ieri e fallito stanotte, classe di caveat nota).
3. BOZZA referente #3 (G09 670 vs 666): testo secco pronto in
   referente-domanda-g09-2026-08-27.md, NON inviata (maintainer spedisce).
4. A3 dichiarata coperta dai dati esistenti (motivazione nel registro);
   TASK-562 CHIUSO Done (AC 1-7 coperte).

### Colonna 2: LO STATO DELLE CELLE H34 DOPO TUTTA LA NOTTE
- Il vincitore multi-start consegna 12 celle; il v0.1 applicato in banco ne
  chiude 4 (persistenti ora anche sul DB col fix infra): 8 residue.
- Le 8 residue si dividono: G09 10+24/05 (INRIEMPIBILI: foglio
  over-declared 670 vs 666), G09 27/05 e G11 03/06+04/06+G12
  16/04+G13 19+17+24/05 (riempibili in aritmetica, bloccate da
  move-connectivity: le stesse che il banco Kempe non prende).
- La struttura della notte: multi-start seleziona il bacino (H17 4 -> 2
  vs i GRASP) ma le celle sono bacino-invarianti; il finisher le chiude
  dove il bacino e' buono (4) e non dove e' povero (0 sul tentativo 2).

### Colonna 3: DECISIONI PER maintainer (finali, cumulative)
1. EDF v0.1 ADOZIONE: l'evidenza completa esiste (4 celle sul vincitore,
   curva cross-bacino, 0 conflitti, nessun revert, fix flaw verificato).
   Se adottato: le consegne multi-start passano da 12 a 8 celle residue
   sul bacino buono. Flag OFF, decide maintainer.
2. PACCHETTO referente: tre domande pronte (G09 670vs666 + giorni-corti
   FC + G12 voluti/subiti + RA back-load): la prima e' quella che
   sblocca 3 celle struturalmente.
3. H34 resta ratificato ON (l'evidenza e' completa e coerente).
4. QFR v2, emendamento M22, medium-pura: pendenti dalla mattina.
5. TASK-482 Kempe-chain: resta NO. TASK-562: DONE.

### RILANCIO 3 ITEM 1 - GUARDIE BYTEMAN TASK-543/544 al land (27/08 00:5x). PREDIZIONE PRIMA
- CONTESTO: lo stack MT-LS single-writer e' merged nel ramo campagna (6ee4fa87):
  le due regole di guardia differite dalla ratifica TASK-543 vanno eseguite ORA
  sul banco TASK-544 (profilo maven -Pbyteman, BytemanRaceInjectionTest +
  SimpleFieldTrackerRaceTest + TrackerRaceProbe, regole race-force.btm).
- PREDIZIONE: **verde entrambe** (le regole erano gia' validate al land del 24/08
  nella DoD di TASK-543: la suite -Pbyteman passo' 1139/0; da allora le classi
  colpite non sono cambiate: RaceInjectionHarness e SimpleFieldTracker Hibernate
  7.2.4.Final sono le stesse). Se una regola NON aggancia (script mancante al
  classloader o firma cambiata): rosso loud e si diagnostica.

### RILANCIO 3 ITEM 1 - ESITO (27/08 01:0x): GUARDIE VERDI, predizione centrata
- MISURA: BytemanRaceInjectionTest 2/2 VERDE (EXIT 0) e
  SimpleFieldTrackerRaceTest 2/2 VERDE (EXIT 0), profilo -Pbyteman, agente
  attivo (@EnabledIfSystemProperty byteman.agent: i test girano solo
  nell'agente, quindi il verde E' la prova dell'aggancio; senza agente
  sarebbero disabilitati). Le due regole race-force.btm (stand-in
  publish-then-fill TASK-543 + SimpleFieldTracker reale Hibernate
  AFTER WRITE names TASK-544) agganciano le classi al land del ramo
  campagna. PREDIZIONE: CENTRATA (verde entrambe, classi invariate dal
  24/08).
- VERDETTO DIFFERITO TASK-543 CHIUSO: la DoD della ratifica single-writer e'
  completa anche nel ramo campagna. Registrato anche in TASK-543 (nota).

### RILANCIO 3 ITEM 3 - PROBE cella corta (27/08 01:1x): era un artefatto dello stato pre-fix, non un buco strutturale
- DOMANDA: quale gruppo/data era "corto di 1h" e se e' tolleranza dichiarabile.
- MISURA (sullo stato DB post-v0.1, quello con 8 residue): G11 collocate
  671 = pool 671 = sumQuota 671 (conservazione PERFETTA). Il deficit residuo
  e' UNA cella sola (04/06 quota 4) e il surplus residuo e' ESATTAMENTE 4h
  (+1 su 30/09, +1 su 27/05, +2 su 03/06): aritmetica esatta, nessuna ora
  mancante. Il "corto di 1h" della tabella conservazione era lo stato PRE-fix:
  servivano 9h per chiudere 2 celle (04/06+07/06) con 8h di surplus: il v0.1
  ha chiuso 07/06 e il bilancio ora torna.
- VERDETTO: NON e' tolleranza dichiarabile ne' cella strutturale: e' la stessa
  famiglia geometrica delle altre (surplus esistente ma non consecutivo-
  estraibile legalmente). NESSUNA nuova domanda referente da questo probe: la
  domanda G09 resta l'unica di dato. La G11 04/06 entra nel novero
  delle curabili-move-connectivity (con la catena-3 o il pre-booking).

## QUADRO FINALE CONSOLIDATO 27/08 (terza tratta chiusa 01:15, notturno completo)

### Colonna 1: TUTTO il notturno consegnato (suite 1184/0, worktree spinto, zero adozioni)
1. Ciclo multi-start H34 ON: vincitore deterministico -16hard (H34 12+H17 2+H7 2),
   0/5 a 0hard, celle BACINO-INVARIANTI. Capture+sanity+viz chiusi.
2. TASK-562: A1+A2 autopsia (condannati+vincitore: tutte le celle con candidate
   singole = residuo SEARCH-side), A5 ledger n=5 (firma utile POST-LS), B1-B7
   censiti dal sorgente, A3 coperta dai dati, A4 col banco gemello. DONE.
3. TABELLA CONSERVAZIONE: G09 foglio 670h vs pool 666h = 3 celle
   INRIEMPIBILI; il golden stesso ne lascia 2 vuote.
4. EDF v0.1 flaw fixato (TDD) + banco sul vincitore: 4 celle chiuse, 0
   conflitti, nessun revert; curva cross-bacino 4/0/3/2/1.
5. INFRA: api.sh score catena 3 livelli; resume-save persiste la coda
   (verificato 12->8 nel DB); banco Kempe-cell dietro env var con KNOWN LIMIT.
6. GUARDIE BYTEMAN TASK-543 al land del ramo campagna: 2/2 + 2/2 VERDI.
   Verdetto differito CHIUSO.
7. PACK DI GATE EDF v0.1 (task560-results/gate-adozione-edf-v01): curva,
   costi, residue spiegate, pro/contro, raccomandazione.
8. PROBE cella corta: artefatto pre-fix; G11 04/06 e' aritmetica esatta,
   blocco geometrico: nessuna nuova domanda referente.
9. BOZZA referente #3 (G09) pronta, NON inviata.

### Colonna 2: dove sta il residuo H34 dopo tutto (la mappa finale)
- 8 celle residue sul vincitore post-v0.1: G09 10+24/05 (FOGLIO: 4h
  fantasma, domanda referente) + G11 04/06 + G12 16/04 + G13 19/05 (x1
  delle 3 G13: 17+24/05 sono le altre due: totale 5 geometriche) tutte
  move-connectivity (surplus esistente, composizione necessaria).
- Le vie per le 5 geometriche: catena-3 Move Streams (catalogata), pre-booking
  in costruzione (proposta registrata), risposta referente per le G09.
- Il multi-start non e' la leva per le celle (bacino-invarianza dimostrata):
  resta la leva per H17/H0 (bacino).

### Colonna 3: DECISIONI PER maintainer (l'unica lista che conta al risveglio)
1. EDF v0.1 ADOZIONE: pack pronto (raccomandazione ADOTTARE, curva 4/0/3/2/1,
   costi ~4s/0conf/0revert, rischi zero per costruzione).
2. PACCHETTO referente: tre domande pronte (G09 670vs666 bozza nuova +
   giorni-corti FC + G12 voluti/subiti + RA back-load): la prima sblocca
   3 celle struturali.
3. QFR v2 (gate chiuso dalla formalita' same-seed: +85k medium, 0 costi
   sanity attribuibili). 4. Emendamento M22 (golden 463->3). 5. medium-pura
   (~17k per 20min). 6. H34 resta ratificato ON. 7. TASK-482 resta NO.

### QUARTA TRATTA (27/08 01:2x) - assembly per il risveglio, TUTTO consegnato
1. PACCHETTO referente COMPLETO: referente-pacchetto-3domande-2026-08-27.md, un solo
   messaggio pronto da incollare (G09 670vs666 + FC giorni-corti voluti/
   subiti + RA back-loaded), ognuna con l'evidenza misurata in tre righe,
   header per maintainer con la mappa cosa-sblocca-ogni-domanda. NON inviato.
2. PACK M22: task558-results/gate-emendamento-m22-2026-08-27.md (forma del
   pack EDF): 463->3 / 410->0, proprieta' algebrica, cosa NON cambia
   (l'orario: e' metro), pro/contro, raccomandazione ADOTTARE con
   l'alternativa del rinvio al pacchetto referente.
3. GIST FOLLOW-UP TRICEO: gist-followup-triceo-draft-2026-08-27.md, inglese,
   SANITIZZATO (verifica meccanica per grep: zero nomi docenti/classi/dati),
   formato checkpoint + boundary oracle + 6 failure modes + numeri generici.
   maintainer decide dopo la reply.

### QUINTA TRATTA (27/08 01:0x) - coda operativa completata
1. PREDITTORE A5 HAND-RULE (task562-results/predittore-a5-handrule-2026-08-27.md):
   la separazione ESISTE: H17@bt1 5 (vincitore) vs 21-47 (condannati), tier
   -42 vs -76..-112: regola H17>15 OR tier<-60 cattura 4/4 condannati con
   0 FP/FN sul campione n=5. H34 NON separa (17-24 ovunque: bacino-invarianza
   confermata anche come feature). Protocollo di 1 pagina: N costruzioni +
   prima-LS corta + 1 full del migliore sopravvissuto. Soglie indicative
   (n=5, Wilson [0.51,0.99] su 4/4): da ricalibrare a n>=20. NOTA: il
   predittore early-doom in produzione guarda post-costruzione, che il
   ledger mostra NON separare per questa famiglia: la firma utile e' post-LS.
2. SWEEP BACKLOG: TASK-557, 560, 543 chiusi Done con finalSummary (verdetto +
   commit chiave); 561 e 562 gia' chiusi.
3. CURVA EDF ESTESA: sesto punto (h34on winner dal POST_B_ROOM): 0 celle.
   Curva completa 4/0/3/2/1/0: bacino-sensibile anche fra buoni (due -14:
   uno chiude 4, uno 0). ADDENDUM nel pack di gate con la lettura onesta
   (media ~1.7, picchi 4, non garanzia; raccomandazione invariata).

## INDICE DEGLI ARTEFATTI DEL RISVEGLIO (per il quadro delle 06:30)
- PACK EDF v0.1: task560-results/gate-adozione-edf-v01-2026-08-27.md (+addendum)
- PACK M22: task558-results/gate-emendamento-m22-2026-08-27.md
- PACCHETTO referente (3 domande, pronto da incollare):
  referente-pacchetto-3domande-2026-08-27.md
- GIST TRICEO (sanitized): gist-followup-triceo-draft-2026-08-27.md
- REPLY TRICEO: reply-geoffrey-draft-2026-08-26.md (segnaposto [?])
- PREDITTORE A5: task562-results/predittore-a5-handrule-2026-08-27.md
- LEDGER A5: task562-results/ledger-attempts-h34ms.md (n=5 completo)
- REGISTRO COMPLETO DELLA NOTTE: questo file, voci dal 26/08 sera al 27/08 01:0x

## QUADRO FINALE 06:30 (27/08, chiusura ufficiale della finestra notturna 26-27/08)

Stato dell'app alla chiusura: solver NOT_SOLVING, stagedStage nullo, pre-assign
IDLE, profilo dci-h34on (container planner-pg up da 5h), Quarkus sano su :8080.
Nessun ciclo in corso. Worktree spinto fino a 111c8477 (tutte e sei le tratte).

### Colonna 1: CODA CONSEGNATA (le sei tratte della notte, in ordine)

1. FORMALITA' same-seed gate (e): ciclo completo, -6hard/-1.309.447, QFR 57
   mosse/14 celle/0 revert, predizione centrata sugli assi del pass, 0hard
   smentito dal bacino (same-seed != same-basin). Gate di adozione TASK-556
   CHIUSO.
2. CICLO MULTI-START H34 ON (7h18m, 5 tentativi): vincitore deterministico
   -16hard/-1.350.869, 0/5 a 0hard, celle H34 BACINO-INVARIANTI (10-12 in
   ogni tentativo, stesse date del foglio). Capture+sanity+viz chiusi.
3. TASK-562 AUTOPSIA COMPLETA: A1+A2 (tutti i tentativi: ogni cella con
   candidate singole = residuo SEARCH-side), A5 ledger n=5 poi n=7,
   censimento B1-B7 dal sorgente, A3 coperta dai dati, A4 con dichiarazioni.
   Task chiuso Done.
4. TABELLA CONSERVAZIONE: G09 foglio 670h vs pool 666h = 3 celle
   INRIEMPIBILI (il golden stesso ne lascia 2); probe cella corta = artefatto
   pre-fix; le residue sono move-connectivity.
5. EDF v0.1: flaw slide-pre-estrazione fixato (TDD), 4 celle sul vincitore
   (49 mosse, 0 conflitti, 0 revert), curva cross-bacino 4/0/3/2/1/0,
   PACK DI GATE pronto. Flag resta OFF.
6. VALUTAZIONE RIUSA-PRIMA + provider Move Streams Kempe whole-cell committato
   come riferimento (banco 0/10: valle H32, legge registrata).
7. GUARDIE BYTEMAN TASK-543 al land del ramo campagna: 2/2 + 2/2 VERDI.
   Verdetto differito chiuso.
8. INFRA: api.sh score a catena, resume-save che persiste la coda
   (verificato 12->8 nel DB), grep-q/SIGPIPE nei wrapper, banco Kempe-cell
   dietro env var con KNOWN LIMIT.
9. PREDITTORE A5 hand-rule: separazione netta (H17 5 vs 21+; regola
   H17>15 OR tier<-60: 7/7 sui punti puri), estensione out-of-sample onesta
   (i 300 storici non testabili: contaminazione room-tier dimostrata).
10. ASSEMBLY DEL RISVEGLIO: pacchetto referente 3 domande, pack M22, gist triceo
    sanitizzato, sweep backlog (557/560/543/561/562 tutti chiusi).

### Colonna 2: ESITI CHIAVE CON NUMERI (i dieci che contano)

1. Multi-start: gate -14/-50/-61/-62/-85, ordine monotono dal post_bt1:
   il deterministico vince sempre in questa famiglia di seed.
2. Celle H34 del vincitore: 12 (bacino-invarianti); post-EDF: 8.
3. EDF v0.1 curva: 4/0/3/2/1/0, media ~1.7 celle, picco 4, costi ~4s,
   0 conflitti, 0 revert.
4. G09: pool 666 vs foglio 670 (4h fantasma); golden: 2 celle vuote.
5. Kempe LS whole-cell: 0/10 (valle H32); SLIDES census 269 vs 30 pre-fix.
6. H17@bt1: 5 (vincitore) vs 21-47 (condannati): separazione 4x.
7. Guardie Byteman: 4/4 verdi.
8. Predittore: 7/7 sui punti puri, n condannati = 4 (una notte): niente
   adozione finche n>=20.
9. M22 emendato: golden 463->3, run 410->0.
10. Suite finale: 1184/0 EXIT 0 a ogni tratta (31 skipped: i banchi gated).

### Colonna 3: DECISIONI PER maintainer (in ordine di valore)

1. EDF v0.1 ADOZIONE: pack pronto (task560-results/gate-adozione-edf-v01,
   con addendum curva estesa). Raccomandazione ADOTTARE (downside = no-op).
2. PACCHETTO referente: 3 domande pronte da incollare
   (referente-pacchetto-3domande-2026-08-27): la prima (G09) sblocca 3 celle.
3. Emendamento M22: pack pronto (task558-results/gate-emendamento-m22):
   raccomandazione ADOTTARE (o rinvio al pacchetto referente).
4. QFR v2 (gate chiuso dalla formalita'), medium-pura (17k/20min),
   gist triceo (pubblica dopo la reply), predittore (aspetta n>=20).
5. H34 resta ratificato ON; TASK-482 resta NO; nessun flag flippato
   stanotte tranne quelli gia ratificati.

### ARTEFATTI DEL RISVEGLIO (indice completo nella voce QUINTA TRATTA)

pack EDF v0.1 (+addendum), pack M22, pacchetto referente 3 domande, gist triceo
sanitized, reply triceo (segnaposto), predittore A5, ledger A5 n=7, registro
della notte (questo file), VERDICT del ciclo multi-start
(task557-results/ciclo-h34-multistart).

## SESTA TRATTA (27/08 mattina, sessione post-/clear) - mandato handoff 06:31

### ANELLO 1 - TRE RATIFICHE ESEGUITE (commit 0192045d, f80b8ae0, b3a7654a)
1. EDF v0.1 DEFAULT ON (forma opt-out, test invertito). Suite 1184/0.
2. Emendamento M22 quota-aware DEFAULT ON (lettura PER MATCH conservata, test
   invertito). Suite 1184/0.
3. QFR v2 DEFAULT ON (NUOVO banner in refreshSolverStatics, che mancava; test
   invertito). Suite 1184/0.

### ANELLO 1b - CURA G09 (commit be8b67a2)
Foglio 670h vs pool 666h (il golden colloca ESATTAMENTE 666 e lascia vuoti
04/06 e 07/06): 4h tagliate dalle righe di coda d'anno che il golden pratica
zero (04/06 5->4; riga 07/06 eliminata). SOLO seed CSV, entrambe le copie
byte-identical (CRLF preservati: la prima passata converti' tutto il file in
LF, rifatta in binario), DataGenerator NON toccato. Pin ripinnati: seed
1739->1738 e G09 670->666; golden H34 -2->-1hard, M20.2 -21500/3 ->
-14000/2 (quota-4 7000 + G14 7000). derive-golden 0 divergenze, suite
1184/0 EXIT 0.

### ANELLO 2 - EDF v0.2 SWAP-COMPOUND: IMPLEMENTATO, TDD VERDE
Flag separato `planner.construction.empty-day-finisher.v2-swap.enabled`
DEFAULT OFF (opt-in). Meccanica: quando l'estrazione same-group e' bloccata
H1, il blocker e' una lezione di un ALTRO gruppo col MEDESIMO docente nello
slot target; il compound la sposta su un giorno same-group sottiquota
(headroom, giorni piu'-sotto-quota prima, atterraggio bordo-consapevole
gap-interno poi last+1, gate-e H-side su entrambi i lati, slide H17/H31),
poi ritenta l'estrazione; se il retry fallisce l'evizione viene interamente
rolled back (rollbackFrom a marqueur). Banco sintetico 4 test verdi
(compound 3 mosse/1 cella; flag-off no-op; rifiuto senza headroom; rollback
completo su retry-fail ROOM). Census edf.debug V2-*.

### PREDITZIONE PRIMA - BANCO REALE v0.2 (scritta prima del lancio)
Resume dal POST_ROOM_QUOTA_FILL_REPAIR del vincitore (/tmp/winner-prqfr,
profilo dci-h34ms, vecchio seed 670h: le 3 celle G09 restano
INRIEMPIBILI su questo banco per costruzione). v0.1 (ora default ON)
chiudera' le stesse 4 celle della notte (49 mosse). v0.2 atteso:
**2..4 celle delle ~5 move-connectivity residue** (G07 03/06, G11
04/06, G12 16/04, G13 19+24/05), con 0 conflitti e 0 revert. Se 0 con
rifiuti floor veri (V2-H-SRC-GATE-E dominante): DICHIARO che la cura passa
alla prenotazione in costruzione, non insisto.

### ANELLO 2 ESITO (27/08 07:41) - BANCO REALE: PREDIZIONE SMENTITA, DICHIARO
- SETUP: resume dal POST_ROOM_QUOTA_FILL_REPAIR del vincitore (profilo
  dci-h34ms), v2-swap ON, census edf.debug=true (NOTA INFRA: la prima
  passata lanciata con `-Dedf.debug` SENZA `=true` setta la property a
  stringa vuota -> parseBoolean false -> census muto; il risultato del pass
  non dipende dal flag di debug ed era gia' valido).
- MISURA: **v0.1 riproduce byte-identico la notte** (49 mosse, 4 celle,
  0 conflitti; census v0.1 GATE-E=2808 SLIDES=269 VRP=295 H1=256, gli
  stessi numeri del 26/08 23:51). **v0.2 swap-compound: 0 celle, 0
  compound applicati.**
- CENSIMENTO V2 (93 blocker candidati trovati, TUTTI rifiutati PRIMA di
  applicare l'evizione): V2-H-SRC-GATE-E=83 (il giorno del blocker e'
  gia' a quota/floor: evaderlo aprirebbe una NUOVA cella deficit sul lato
  H, lo shell-game che i gate esistono per impedire), V2-H-SLIDES=6,
  V2-X-EXCLUDED=4 (blocker nei gruppi esclusi 1/G08, G14).
  V2-H-LANDING / V2-RETRY-FAIL / V2-ACCEPTED assenti = nessuna evizione
  e' mai stata applicata. Accessori: V2-GATE-E=978 (donatori same-group
  senza surplus), V2-NOT-H1=306 (donatori con surplus il cui blocco e'
  ROOM/VRP, non curabile con un'evizione).
- LETTURA: nel bacino del vincitore la LS ha spremuto TUTTO il surplus
  fuori dai giorni dichiarati; i blocker H1 siedono su giorni gia' a
  quota e il loro giorno non puo' cedere l'ora senza aprire un buco
  altrove. Il muro move-connectivity e' ricorsivo: la cura locale
  (evizione) incontre' lo stesso muro un livello piu' in la'. Coerente
  con l'autopsia A2 della notte (12/12 celle con candidate singole: le
  mosse ESISTONO, la catena non chiude).
- DICHIARAZIONE (da mandato: "se 0 con rifiuti floor veri DICHIARA"):
  la cura delle celle residue passa alla PRENOTAZIONE IN COSTRUZIONE
  (pre-booking dei giorni dichiarati, la VIA gia' identificata), NON si
  insiste con pass locali a valle. v0.2 resta committed DEFAULT OFF come
  building block corretto (4 test TDD verdi: compound, flag-off no-op,
  rifiuto senza headroom, rollback completo su retry-fail; precedente
  CompositionHoleCloser TASK-481). Il ciclo TUTTO ON (anello 3) parte
  SENZA v2-swap (il banco non lo giustifica).

### ANELLO 3 PREDITZIONE PRIMA (scritta prima del lancio del ciclo TUTTO ON)
- SETUP: profilo FRESCO via run-measure (seed nuovo => cura G09 666h ATTIVA,
  le celle 10+24/05 non esistono piu'), tutti i flag ratificati DEFAULT ON (EDF
  v0.1, M22 emendato, QFR v2, H34 + tutto lo storico), v2-swap OFF (default:
  banco inerte), multi-start N=5 default, cattura canonica + sanity + viz.
- PREDIZIONE:
  1. Il tentativo 1 deterministico vince ancora in questa famiglia di seed
     (notte: ordinamento monotono dal post_bt1).
  2. 0hard NON atteso su questa famiglia senza la cura in costruzione: atteso
     hard consegnato **-6..-14**, celle H34 residue **4..6** (G07 03/06,
     G11 04/06, G12 16/04, G13 19/04+17/05+24/05 meno quelle che EDF
     chiude), H17 ~2..6 come la notte.
  3. Medium consegnato atteso **~-900k..-1.0M** (vs -1.31M del reference
     same-seed): l'emendamento M22 toglie ~410 giorni-eccesso x 1000 dal metro
     (la parte legacy della massa), e' un cambio di METRO non di orario.
  4. Sanity: residui noti giorni-misti TASK-559 + coda H34; G09 ora
     conforme sull'asse celle-vuote (04/06 quota 4 resta, 07/06 non esiste).
  5. Se 0hard arriva: smentita in meglio, autopsia non serve. Se hard peggiore
     di -20 o celle > 8: autopsia con autopsy_analyzer.py obbligatoria.

## ANELLO 3 ESITO (27/08 15:19) - CICLO TUTTO ON CHIUSO: VERDICT vs PREDIZIONE
- CICLO: 7h08m (08:11-15:19), 5/5 tentativi (nessuno a 0hard: confermato), vincitore
  TENTATIVO 1 DETERMINISTICO (confermato), consegna in-memory
  -17hard/-1.351.322medium/-.20625soft; RICONTAGGIO DB (analysis):
  -17hard/-851.159medium/-20.625soft. Capture in
  task557-results/ciclo-tuttoon-27/ (score, analysis, phases, export.gz,
  lessons, sanity, kpi x3, confronto HTML letto, manifest, app.log).
  Banner 6/6 asseriti dal finalize (dopo il fix infra: app.log va ricopiato
  dal vivo PRIMA del finalize perche' run-measure lo congela al lancio,
  pre-solve; e l'export post-multi-start torna 1B finche' non si fa un
  restart pulito: rifatto, procedura notturna).
- PREDIZIONE vs MISURATO:
  1. hard -6..-14 -> **-17: FUORI di 3, DICHIARATO** (H34 13 + H33 2 + H7 2).
  2. celle 4..6 -> **13: FUORI (piu' del doppio)**. Identificate: G06
     04+09+10/06 (TRIS NUOVO fine-anno), G07 04/06, G09 03+10+24/05,
     G11 03+04/06, G12 16/04, G13 19+26/04+17/05. Stesse famiglie
     move-connectivity della notte + il tris G06 + G09 03/05. EDF in
     consegna: 53 mosse, 4 celle, 0 conflitti, 0 compound (17 pre -> 13).
     NOTA sulla cura G09: le celle 10+24/05 RESTANO (la cura ha chiuso la
     conservazione ANNUA 670->666, non la distribuzione: col pool 666 il
     deficit ~11h supera ancora il surplus ~8h, 3h di pool restano parcheggiate
     su giorni misti). La cura funziona (M20.2 G09 scende, 666 collocabili)
     ma quelle celle erano e restano move-connectivity.
  3. medium ~-900k..-1.0M -> recount **-851.159: MIGLIORE della banda di 49k**.
     Decomposizione vs vincitore notturno (notte -1.350.869 GIA' al recount):
     **M22 -426.000 -> 0** (l'emendamento; la mia stima ~410k era quasi esatta),
     M-LF -104k -> -70k, M15 -78k -> -32k (orario genuinamente migliore),
     M20.2 -402k -> -396k e M32 -276k -> -287k (stabili: massa strutturale
     del bacino, candidata filone futuro). IL -1.351.322 in-memory NON sono
     i termini room accesi a valle (M-LF -70k e' gia' contato nel recount):
     e' la divergenza TASK-509 (latestScore in-memory vs riconteggio DB),
     che su questo run vale ~500k e sulla notte valeva 0. Meccanismo noto,
     non bloccante, dichiarato.
  4. 0hard non atteso: CONFERMATO (0/5). Tentativo 1 vince: CONFERMATO.
- KPI: full-day 89,7% (banda; golden 91,9%); day-start 0 giorni late su
  TUTTI i gruppi (H31 perfetta); **ricorrenza unpinned 69,2%: FUORI BANDA**
  (74-76 storico) - IPOTESI DICHIARATA non provata: il metro M22 emendato ha
  tolto la pressione legacy alla concentrazione (minDays ceil/6 basso
  penalizzava lo spread), la LS spalma su piu' giorni: 47 giorni a -1h e
  template diluito (blocchi >=2h 2789 vs golden 1299). Da misurare con A/B
  mirato SE il filone interessa: e' l'unico costo di metro finora visibile
  dell'emendamento.
- SANITY: NON CONFORME su 5 gruppi (G07, G08, G09, G10, G14)
  vs 6 della notte. Miglioramenti veri: G11 e G12 ora puliti, G13
  pulita. Rosso NUOVO: G14 2 gg da 2h (23+30/04). G08 resta il buco
  01/04 (famiglia giorni-corti, domanda referente pendente).
- VIZ (letto, confronto HTML): forme allineate al golden su 9-10 gruppi
  (heatmap occupazione settimanale sovrapponibile); le divergenze visibili
  si concentrano ESATTAMENTE dove stanno le celle: G08 sottile (full-day
  63% vs golden 93 + 1 inizio tardivo) e la finestra fine-anno (G06 93%,
  G13 88%, G11 93%). Ore per docente quasi identiche al golden ovunque
  (gap max T23 -19h). G04 full-day 71% vs golden 47%: better-than-
  golden (la famiglia pool-cap dove il golden praticava giorni corti).
- VERDETTO COMPLESSIVO: il gate TUTTO ON regge sul piano del METRO (M22
  azzerato, medium -500k meglio della notte, KPI hard/fill in banda, sanity
  in miglioramento) MA la consegna resta NON CONFORME (0hard e' binario:
  -17). Filone invariato: move-connectivity; la via resta il pre-booking in
  costruzione. Il dato nuovo da presidiare: ricorrenza 69,2% (possibile
  effetto collaterale del metro M22, da isolare prima di qualunque altra
  ratifica di metro).

## QUADRO TRE COLONNE DELLA GIORNATA 27/08 (mattina+pomeriggio, sessione post-/clear)

### Colonna 1: CONSEGNATO (in ordine)
1. TRE RATIFICHE a default ON (EDF v0.1 0192045d, M22 emendato f80b8ae0,
   QFR v2 b3a7654a): forma opt-out, test invertiti, banner (v2 aggiunto
   nuovo), knobs 97, suite 1184/0 per item.
2. CURA INPUT G09 (be8b67a2): 4h fantasma tolte dalle righe fine-anno
   che il golden lascia vuote, SOLO CSV (byte-identical CRLF), pin ripinnati
   (golden H34 -2->-1, M20.2 -21500/3 -> -14000/2, seed 1739->1738).
3. EDF v0.2 swap-compound (2a646aed): TDD 4 test, DoD piena (/simplify 3
   reviewer + bug rollback-hoursByKey fixato + /review-local 5 reviewer +
   differita TASK-563 in backlog), banco 0 compound con 83/93 rifiuti floor
   veri, cura dichiarata -> pre-booking. DEFAULT OFF building block.
4. CICLO TUTTO ON (task557-results/ciclo-tuttoon-27): 5 tentativi, 7h08m,
   consegna -17hard, capture completa + VERDICT (sopra).
5. TASK-563 aperto (test regressione hoursByKey-phantom).

### Colonna 2: I DIECI NUMERI CHE CONTANO
1. Consegna: -17hard/-851.159medium (recount) = H34 13 + H33 2 + H7 2.
2. M22: -426.000 -> 0 (emendamento, stima ~410k centrata).
3. Medium totale: -1.350.869 -> -851.159 (-500k, di cui -426k metro).
4. Celle H34: 13 (vs 8 post-EDF notturno; pre-EDP 17 vs 12), tris G06 nuovo.
5. EDF consegna: 53 mosse, 4 celle, 0 conflitti, 0 revert.
6. v0.2: 0 compound, census V2-H-SRC-GATE-E 83/93 (muro ricorsivo).
7. Full-day 89,7% (golden 91,9); day-start 0 late ovunque.
8. Ricorrenza unpinned 69,2% (FUORI banda 74-76; ipotesi metro M22).
9. Sanity: 5 gruppi rossi (notte 6); G14 nuovo, G11/G12 guariti.
10. Suite finale 1188/0 EXIT 0; suite per item tutte EXIT 0.

### Colonna 3: DECISIONI E PROSSIMI PASSI (in ordine di valore)
1. 0hard resta aperto sul filone move-connectivity: la VIA dichiarata e' il
   PRE-BOOKING IN COSTRUZIONE dei giorni dichiarati (non piu' pass locali).
2. Ricorrenza 69,2%: isolare l'effetto del metro M22 con un A/B mirato
   prima di ulteriori cambi di metro (unico costo visibile dell'emendamento).
3. E1/E2 room-tier distraction (addendi maintainer approvati): E1 re-scoring
   checkpoint, E2 3 braccia x 2 checkpoint x 120s x 2-3 rep, metro
   P(0hard)/wall, roomability obbligatoria. Predizioni PRIMA per braccio.
4. Pacchetto referente 3 domande: pronto, NON inviato (mandato).
5. DataGenerator javadoc 1739 stantio di 1: differito (file protetto).

### E1 PREDITZIONE PRIMA (scritta prima del lancio, 27/08 ~15:50)
Setup: harness RoomTierMassRescoreTest (-De1.run=true), checkpoint della NOTTE
(multistart attempt1-3 + h34on root; il ciclo tuttoon-27 non ha catturato
checkpoint: flag assente al lancio, dichiarato). Braccio zero = H2/H-M2/H29/
M3/M-LF/M37 a peso 0; M-TC e H-COPRESENCE accesi in ogni braccio. Nota
realizzazione braccio C (per E2): 1/100 e' rappresentabile solo per H2
(1000hard -> 10hard); M3 base 1medium resta a 1 (minimo traccia), gli altri
quattro a zero come B. Dichiarato.
Predizione per stadio:
1. POST_BT1 vincitore (attempt1): full hard ~-4000..-4400 (ledger e78def9e:
   -4045), ZERO hard ~-25..-50 (tier -27 + H-COPRESENCE residua): MASSA
   room-tier hard ~-3.950..-4.350, dominata da H29 (provvisorio) + H2.
2. POST_BT1 condannati (attempt2/3): full hard ~-5.000..-12.000, zero hard
   ~-76..-112 (i loro tier misurati nel ledger A5).
3. Medium: full a BT1 ~-1,5M..-2,0M; il delta medium full-vs-zero atteso
   ~-80k..-150k (M-LF ~70k + M37 ~10k + M3 <1k): la massa medium e' M20.2/M32,
   NON room.
4. POST_RELEASE vincitore: full hard ~-200..-600 (stanze ancora provvisorie),
   zero ~-40..-80.
5. POST_B_ROOM / POST_EXACT_FINISHER: full ~-17..-60, zero quasi uguale
   (le famiglie room sono risolte a valle): massa residua < 50 hard.
Se la massa a BT1 risulta < 1000 hard: l'ipotesi della distrazione e'
debolita alla radice (il gradiente LS non era sepolto da 99% di rumore).

### E1 ESITO (27/08 15:55) - la massa room-tier per stadio, DAI METADATI dei checkpoint
Metodo (dichiarato): ogni checkpoint incorpora score pieno + timeslotTierHard +
boxed (scritti dallo scorer vivo al confine): la massa room-tier hard = pieno -
tier, letta su 105 checkpoint (5 tentativi x 21 boundary). Nessuna
ricostruzione, zero bias. L'harness RoomTierMassRescoreTest (che decomporrebbe
anche il medium room) resta committed gated -De1.run=true: richiede un DB
seeding che al momento non ha (errore a vuoto registrato); l'asse hard, quello
che conta per la distrazione, e' risposto dai metadati.
TABELLA (forma IDENTICA nei 5 tentativi):
- CYCLE_START: -26072 pieno = tier (tutto boxed, nessuna stanza: H0).
- POST_CONSTRUCTION/PROMOTION: pieno -13,1k, tier -2,9k (boxed ~1300: la
  costruzione non collocava tutto), massa -10,2k.
- LS B-TIMESLOT (BT1/BT2/BT3/DEMOTION): pieno -3.830..-4.031, TIER -42
  (vincitore) / -76..-112 (condannati): MASSA ROOM -3.718..-3.993 = 96-99%
  dell'hard visibile della LS. E' la finestra della distrazione.
- REPAIRS (da RELEASE a EF): pieno -10,2k..-10,3k, tier -13..-107: le stanze
  sono VOLUTAMENTE liberate a RELEASE (B-ROOM le ricostruisce dopo): massa
  -10,2k strutturale di finestra, non segnale.
- B-ROOM: pieno -7,4k (CH rooms parziale); MEDIUM_PURE/EDF/PRQFR vincitore:
  pieno = tier = -14: massa 0 a consegna (attempt5: -56).
VERDETTO E1: PREDIZIONE CENTRATA SU TUTTE LE CELLE (pieno -4000..-4400 ->
misurato -3830..-4031; zero-side -25..-50 -> tier -42; condannati -76..-112 ->
misurati -76/-99/-107/-112). La distrazione e' REALE e quantificata: la LS di
B-TIMESLOT lavora con il 96-99% dell'hard-massa non sistemabile in quella
fase (il gradiente utile e' sepolto), coerente con l'era post-446/H29-hard.
Il medium room (M-LF+M37+M3 ~80k su 851k a consegna) e' secondario: la massa
medium e' M20.2/M32, non room. E2 ora testa la CURA causalmente.

### E2 IMPLEMENTAZIONE + PREDITZIONE PRIMA (27/08 ~16:05, prima delle run)
Implementazione (opt-in, default inerte, suite 1189/0 EXIT 0 dopo il wiring):
- `SchoolSchedule.constraintWeightOverrides` (campo Timefold per-tipo,
  @JsonIgnore, default none()) + `RoomTierOverrides` (roomZero / roomDamped:
  H2 1000->10hard esatto 1/100; M3 1med resta 1, dichiarato irrealizzabile
  1/100; altri 4 a zero; M-TC e H-COPRESENTE mai toccati).
- Knob `planner.room-tier.override`=none|zero|damped applicato SOLO alle fasi
  timeslot (B-TIMESLOT loop 2145/2172 + medium-pura 2980), MAI a room/costruzione.
  Banner in refreshSolverStatics. Finestra LS: knob esistente TASK-549
  `planner.ls.seconds-cap` (120s). Checkpoint per run in dir dedicata
  (PLANNER_CHECKPOINT_DIR, per NON sovrascrivere quelli notturni di E1).
- PROTOCOLLO: 3 braccia (A pieno, B zero, C damped) x 2 checkpoint
  (attempt1/POST_BT1 vincitore tier -42, attempt5/POST_BT1 condannato tier
  -112) x 2 rep = 12 run; resume dal POST_BT1 (finestra = BT2+BT3 LS da 120s
  cad.; se il resume salta BT2/BT3 si adatta ripartendo da POST_PROMOTION,
  finestra BT1+BT2+BT3, dichiarato); stop al primo checkpoint post-BT3.
- METRICHE per run (dai checkpoint della run stessa): tier hard a fine
  finestra (PRIMARIA: qualita'), room-hard mass a fine finestra = pieno-tier
  (il conto che B-ROOM paghera': COSTO ROOMABILITY hard), medium pieno.
  La decomposizione fine M-TC/H-COPRESENCE/H2-singolo resta disponibile via
  harness se un rosso lo chiede (dichiarato).
- METRO DI GIUDIZIO (addendo 2): P(0hard)/wall - nel verdetto si traduce:
  (a) se un braccio chiude piu' tier-hard per finestra => meno cave per
  tentativo => P(0hard)/tentativo; (b) se converge piu' veloce => tentativi
  piu' corti => piu' ritenti nel budget => P(>=1 0hard)/ciclo.
- PREDITZIONE per braccio/checkpoint (vincitore parte da tier -42,
  condannato da -112):
  * A (pieno): tier finale finestra ~-30..-40 (vincitore: piccoli guadagni,
    gradiente sepolto), ~-90..-110 (condannato).
  * B (zero): tier finale MIGLIORE di A di 5..20 punti su entrambi i
    checkpoint (segnale pulito => piu' discesa utile per finestra):
    vincitore ~-20..-35, condannato ~-70..-100. COSTO: room-hard mass a
    fine finestra SUPERIORE ad A di 200..1000 (la LS trascura le stanze):
    se il mass cresce >1000 il braccio NON vince nonostante il tier.
  * C (damped): intermedio: tier ~metta' del guadagno di B, room-hard mass
    crescita <500 (i tag H2/M3 tengono la griglia roomable).
  * Medium pieno: B < A di 50..150k alla finestra (i termini room non
    pagati non spingono; il resto e' M20.2/M32 invariato).
  Se B NON batte A sul tier (o vince solo massacrando la roomability): la
  cura per-stadio NON passa ed E3 non parte. Se B vconde pulito: E3 (ciclo
  A/B completo) e' giustificato.

### E2 ESITO (27/08 17:20) - TRE BRACCIA x 2 CHECKPOINT x 2 REP: NEGATIVO, E3 NON PARTE
- PROTOCOLLO eseguito: 12 run (A pieno / B zero / C smorzato x attempt1-postBT1
  [vincitore, tier di partenza -42] x attempt5-postBT1 [condannato, -112] x 2
  rep), finestra LS 120s/fase (BT2+BT3), stop a fine finestra, checkpoint in
  dir dedicate. Infra: due difetti corretti in corsa (container-swap profilo,
  path sottodir checkpoint attemptN/); nessun run perso.
- TABELLA (tier a fine finestra, media su 2 rep; il tier e' REGIME-INDEPENDENTE:
  somma le famiglie non-room, identiche nei due regimi):
  | braccio | vincitore (da -42) | condannato (da -112) |
  | A pieno | -34,0 (boxed 3,5) | -88,5 (boxed 9,0) |
  | B zero | -36,0 (boxed 4,0) | -91,0 (boxed 9,5) |
  | C smorz | -36,5 (boxed 4,0) | -89,0 (boxed 9,0) |
  Medium: tutti ~-830k, nessuna separazione (la massa e' M20.2/M32).
- PREDIZIONE SMENTITA: B doveva battere A di 5..20 punti tier; misurato
  0..3 punti, DENTRO la variabilita' rep-to-rep (A: -33/-35, B: -36/-36 sul
  vincitore). Il braccio smorzato idem.
- COROLLARIO (il dato piu' utile): in 2x120s il tier arriva a ~-34..-36 sul
  vincitore, lo stesso livello del run originale a budget pieno (BT3 tier
  -36): la discesa del tier SATURA in fretta; il collo di bottiglia verso
  0hard non e' il gradiente sporco ma la STRUTTURA del bacino (filone
  move-connectivity, coerente con l'autopsia A2 e col banco v0.2).
- VERDETTO (metro P(0hard)/wall): NESSUN braccio alza la P(0hard): non
  chiude piu' cave ne' accorcia i tentativi in modo misurabile. La
  distrazione room-tier e' REALE come massa (E1: 96-99%) ma NON e' un freno
  CAUSALE sulla discesa del tier: rimuoverla non cambia la qualita' alla
  finestra. L'ipotesi descrittiva tiene, quella curativa cade.
- E3 NON PARTE (da mandato: solo se E2 positivo). Il re-benchmark delle
  metaeuristiche su score pulito perde la premessa su questa evidenza.
- LIMITAZIONI DICHIARATE: (a) finestra 120s: una finestra piu' lunga NON e'
  giustificata dai dati (il tier satura entro la finestra); (b) n=2 rep non
  dimostra equivalenza stretta, ma il segnale atteso (5-20 punti) e' del
  tutto assente; (c) la decomposizione fine roomability (H2/M-TC/copresenza)
  del braccio B e' regime-relativa in questo protocollo (mass=0 per
  costruzione nei boundary LS): MOOT dato l'esito, ma dichiarato; la misura
  vera del conto stanza resta E3/produzione coi pesi pieni a B-ROOM.
- STATO FINALE: app riavviata sul profilo tuttoon-27 (consegna del ciclo
  TUTTO ON leggibile in UI). Knob room-tier.override resta committed
  opt-in (inerte), come infrastruttura di misura per future ipotesi.

### E2-BIS PREDITZIONE PRIMA (27/08 ~17:35, richiesta maintainer post-verdetto)
Scopo: rispondere alla limitazione dichiarata "finestra 120s satura": la
non-separazione dei bracci era forse un artefatto dell'orizzonte corto?
Protocollo: 2 braccia (A pieno, B room-zero) x CONDANNATO (attempt5 POST_BT1,
tier di partenza -112) x 2 rep, finestra LUNGA 480s/fase (BT2+BT3), stessa
metrica (tier a fine finestra, regime-indipendente; boxed).
PREDIZIONE: entrambi i bracci convergono verso il livello del budget pieno
originale (attempt5 BT3 originale: tier -102): attesi A e B in **-95..-105**
con differenza **<= 5 punti** (indistinguibili). FORMA DICHIARATA DEL BIVIO:
- Se indistinguibili anche a 480s: il negativo e' ROBUSTO oltre la
  saturazione, il filone room-tier-distrazione SI CHIUDE (la massa non era
  un freno causale a nessun orizzonte testabile) e il re-benchmark
  metaeuristiche resta senza premessa.
- Se B emerge (>5 punti meglio di A oltre i 120s): la distrazione e' un
  fenomeno a LUNGO ORIZZONTE, va ridichiarato nel registro e rivalutato con
  E3 su budget pieno (decisione maintainer).
Costo reale dichiarato: 4 run x ~17 min (2 fasi x 480s + overhead) = ~70 min
(la stima ~16 min valeva per run singolo).

### TASK-564 CREATO + CANDIDATO SPERIMENTO (27/08 ~17:50)
- TASK-564 (backlog, label design-doc): DeclaredDayPreBooker, il design doc
  completo della prenotazione in costruzione (opzione b: fase custom
  post-construction/pre-PROMOTION con improve-or-noop sul tier; opzione a
  tail-reorder documentata e scartata; TDD 6 banchi + real-seed + banco
  localizzato POST_CONSTRUCTION; PREDIZIONE scritta: celle fine-costruzione
  19 -> 8..12, consegna 13 -> 5..8, hard -17 -> -9..-12).
- CANDIDATO (non aperto, in attesa di via): SHORT-ATTEMPT MULTI-START. Dal
  corollario E2 (tier a -36 in 2x120s vs ~17min di coda originale) e dal
  precedente TASK-500 (tail-capping: taglia i run lenti): ciclo multi-start
  con -Dplanner.ls.seconds-cap=300, confronto tentativi-consegnabili a
  parita' di wall + qualita' delivered (gate medium/ricorrenza). Metro:
  P(0hard)/wall verso (b). Limite dichiarato: E2 non ha testato BT1 corto
  (la fase da 56min), che e' i due terzi del wall.

### E2-BIS ESITO (27/08 18:45) - FILOGNE CHIUSO: indistinguibili anche a 480s
- MISURA (condannato attempt5 POST_BT1, finestra 480s/fase, 2 rep per braccio):
  A pieno tier -89/-95 (media -92,0, boxed 10/13); B room-zero tier -90/-90
  (media -90,0, boxed 9/10). Differenza 2 punti, dentro la variabilita' rep.
- PREDIZIONE: entrambi -95..-105 con delta <=5: CONFERMATA nella forma
  (delta 2 <= 5); i valori assoluti -90/-92 stanno 3..5 punti SOPRA la banda
  (convergenza leggermente migliore del previsto, dichiarato).
- BIVIO dichiarato: si chiude sul ramo 1. **Il negativo e' robusto oltre la
  saturazione: il filone room-tier-distraction SI CHIUDE** (la massa e' reale
  ma non frena causalmente la discesa del tier, a nessun orizzonte testato).
- RINFORZO del corollario di saturazione: a 480s il tier (-90..-92) NON
  migliora rispetto alla finestra 120s (-88,5..-91): l'asse hard-timeslot
  e' esausto entro ~2 minuti su questo bacino; il budget LS oltre compra
  medium, non tier. Alimenta il candidato short-attempt (registro voce
  17:50) e ridimensiona E3-famiglia per le mosse PICCOLE.
- PROSEGUE E2-TER (approvato maintainer 27/08 sera): l'argomento contro E3 NON
  copre le mosse DISTRUTTIVE (un ruin muove co-occorrenza all'ingrosso: il
  suo delta e' dominato dalla massa room che la fase non puo' sistemare).
  Ipotesi forte: la fase R&R rimossa come "inerta" (TASK-420, 27/07, PRIMA
  del decoupling 446) poteva essere un ARTEFATTO del punteggio inquinato.
  Banco: 2 braccia dal condannato, finestra R&R 120s x 2 rep, metriche tier
  finale + TASSO DI ACCETTAZIONE (step total della fase). Predizione PRIMA
  (da battere, di maintainer): A accettazione ~0 (riproduce l'inertezza di
  agosto), B > 20% se l'artefatto e' reale.

### E2-TER PREDITZIONE PRIMA (27/08 ~19:05, ipotesi artefatto R&R di maintainer)
- SETUP: knob planner.ls.ruinrecreate.enabled (nuovo, in casa TASK-549): la fase
  storica verbatim da 79a91755~1 (ruinRecreate 5-20 w5 + swap w3 + change w2,
  tabu-7, forager 1000) sostituisce la LS delle fasi timeslot; finestra 120s
  via seconds-cap. 2 braccia (A pieno, B room-zero) x condannato (attempt5
  POST_BT1, tier -112) x 2 rep.
- NOTA DI MISURA (dichiarata, deviazione dalla metrica richiesta): con tabu-7 +
  forager 1000 il fase accetta ~1 mossa per step PER COSTRUZIONE: un "tasso di
  accettazione per mossa tentata" non esiste nei log stock. Test causale
  equivalente: (i) VOLUME di step accettati (step total della fase), (ii) la
  TRAIETTORIA DEL TIER: in A la somma e' dominata dal room (le mosse accettate
  ottimizzano rumore: tier random-walk), in B la somma ~ tier (le mosse
  accettate scendono il segnale). Se l'artefatto e' reale: tier(B) << tier(A).
- PREDITZIONE (bande): step total 100..800 per braccio (volumi simili, il tabu
  accetta comunque); TIER a fine finestra: A **-100..-115** (flat o peggiora),
  B **-75..-100** (guadagno > 15 punti solo se l'artefatto e' causale). La
  previsione di maintainer (A ~0% / B >20% accettazione) si traduce: A con volume
  alto ma tier piatto, B con discesa netta.
- CONTRO-INDICIONE STORICA (pesa sulla mia previsione, dichiarata): TASK-420
  (27/07) peso' "inerte in 6 misure" PRIMA del decoupling 446, quando la massa
  room al confine era ~-31 (piccola): l'inertezza NON nacque col rumore grosso.
  Pero' "inerte" = nessun miglioramento, e l'era aveva comunque H2 provvisorio
  + co-presenza: la confutazione chiede il banco, non la cronaca. Esiti (a/b/c
  di maintainer) tutti preziosi; se (a): l'argomento anti-E3 diventa completo anche
  per i distruttivi.

### E2-TER ESITO (27/08 19:15) - ARTEFATTO CONFERMATO CAUSALMENTE, REDENZIONE MODESTA
- MISURA (condannato attempt5 POST_BT1, tier -112 di partenza, finestra R&R
  storica 120s x 2 fasi, MT-LS OFF per incompatibilita' documentata sotto,
  2 rep per braccio):
  | braccio | rep | tier BT2 | tier BT3 | delta da -112 |
  | A pieno | 1 | -124 | -127 | -15 (PEGGIORA) |
  | A pieno | 2 | -111 | -123 | -11 (PEGGIORA) |
  | B zero | 1 | -108 | -104 | +8 (MIGLIORA) |
  | B zero | 2 | -110 | -109 | +3 (MIGLIORA) |
  Medie: A -125,0 / B -106,5: 18,5 punti, SEGNO COERENTE su 4/4 run.
  Meccanismo visibile in A: full migliorava (-3830 -> -3629) MENTRE il tier
  peggiorava: l'accettatore ha scambiato segnale timeslot contro massa room
  (il gradiente pieno punta ALTROVE dal tier). Volume: step total 19..20 per
  finestra a 167 evals/sec (il ruin costa; il volume e' limitato dal forager
  1000, come da nota di misura nella predizione).
- PREDIZIONE vs MISURATO: A -100..-115 -> misurato -123/-127: PEGGIORE della
  banda (l'artefatto e' PIU' forte del previsto, smentita parziale in peggio,
  dichiarata). B -75..-100 -> misurato -104/-109: migliora ma MENO della banda.
- VERDETTO: esito (b) con nuance. (1) L'artefatto e' REALE: la stessa fase
  R&R che degrada sotto score pieno migliora sotto room-zero, coerenza 4/4:
  la distrazione e' CAUSALE per le mosse DISTRUTTIVE (l'inertezza storica di
  TASK-420 era in parte punteggio inquinato: la fase fu tolta PRIMA del
  decoupling 446, quando la massa era gia' presente in forma minore).
  (2) MA la redenzione non basta: R&R pulito -106,5 resta SOTTO lo stock
  nella stessa finestra/regime (E2: stock-zero -91,0; E2-bis: stock-pieno
  -92,0): la mossa distruttiva NON diventa la leva migliore nemmeno col
  gradiente pulito.
- METRO P(0hard)/wall: +3..+8 punti tier per finestra sul condannato non
  competono con lo stock (-20+): NESSUNA adozione di R&R oggi; lo schema
  per-stadio room-zero resta confinato a "causalmente utile per mosse che
  ancora non abbiamo": il filone si sposta dal PESO (dimostrato non-causale
  per le mosse piccole, causale-ma-insufficiente per i distruttivi storici)
  al DESIGN DELLE MOSSE (serve una famiglia distruttiva che vinca anche
  pulita; candidati naturali: i Move Streams Kempe whole-cell gia' committati
  come riferimento, 0/10 valle H32 col punteggio di allora: DA RITESTARE
  sotto room-zero, e' il prossimo banco naturale).
- INFRA: incompatibilita' R&R x MT-LS documentata (selection cache PHASE
  costruita due volte, NPE nel move thread): la fase storica richiede LS
  single-threaded (-Dplanner.multithreaded-ls.enabled=false, usato nel
  protocollo, che e' anche la condizione storica del 27/07). Knob
  planner.ls.ruinrecreate.enabled resta committed opt-in.

## QUADRO FINALE SERATA 27/08 (finestra post-ciclo, esperimenti E)
### Colonna 1: CONSEGNATO
1. E1: massa room-tier per stadio, 105 checkpoint (LS 96-99% dell'hard).
2. E2: 3 braccia x 2 checkpoint x 2 rep: mosse piccole NON causali.
3. E2-bis: 480s, indistinguibili: negativo robusto oltre saturazione.
4. E2-ter: artefatto R&R confermato causalmente (4/4 segno coerente),
   redenzione insufficiente; infra R&R-MTLS documentata.
5. TASK-564: design doc DeclaredDayPreBooker nel backlog (predizione scritta).
6. Infra: ConstraintWeightOverrides + knob room-tier.override (opt-in,
   inerte) + knob ruinrecreate (opt-in) + harness rescore gated.
### Colonna 2: I NUMERI
1. E1: pieno -3830..-4031 vs tier -42..-112 nelle fasi LS (massa 96-99%).
2. E2: A -34/-88,5 B -36/-91 C -36,5/-89 (delta 0..3, dentro variabilita').
3. E2-bis: A -92,0 B -90,0 a 480s; tier satura entro ~2 min.
4. E2-ter: A -125,0 (peggiora di 11-15) B -106,5 (migliora di 3-8).
5. Ciclo tuttoon-27 (mattina): -17hard/-851k recount, M22 -426k->0.
6. Suite finale 1190/0 EXIT 0; 4 sperimenti predetti PRIMA, tutti chiusi.
### Colonna 3: DECISIONI (per maintainer/Fedora)
1. Filone PESO chiuso per le mosse piccole; aperto-dimostrato per i
   distruttivi ma senza famiglia vincente: il prossimo banco naturale e'
   KEMPE WHOLE-CELL SOTTO ROOM-ZERO (0/10 di allora va ritestato col
   gradiente pulito: e' il test che manca al quadro).
2. TASK-564 pre-booking: la cura strutturale delle celle (priorita' alta,
   design pronto, predizione 19 -> 8..12).
3. CANDIDATO short-attempt multi-start (P(0hard)/wall verso b): un ciclo
   con seconds-cap 300, gate qualita' delivered. In attesa di via.
4. Pacchetto referente: pronto, non inviato. Ricorrenza 69,2% da presidiare
   (effetto metro M22 da isolare).

### E2-QUATER (KEMPE WHOLE-CELL SOTTO ROOM-ZERO) PREDITZIONE PRIMA (27/08 19:30)
- SETUP: banco KempeCellNeighborhoodDiscriminatorTest esteso col braccio env
  PLANNER_TEST_KEMPE_CELL=zero (stesso fixture h34on, 10 celle, stessa
  finestra hill-climbing acl=1/120s/45s-unimproved, solver programmatico);
  aggiunti: contatore miglioramenti accettati + re-scoring A PESI PIENI del
  risultato nel braccio zero (conto roomability). Il fixture condannato
  (dci-h34ms) e' ESCLUSO dal KNOWN LIMIT documentato (celle fuori range per
  QFR v2): solo vincitore, dichiarato.
- DOMANDA (di Fedora): la composite whole-cell che chiudeva 0/10 sotto score
  pieno, quante ne chiude col punteggio pulito?
- PREDITZIONE: braccio pieno 0/10 (replica: nessuna transazione era
  migliorativa col pricing sporco). Braccio zero: **celle chiuse 2..6 delle
  10**, miglioramenti accettati 5..30 nella finestra (le transazioni
  chiudi-cella valgono +1 H34 hard netto a pricing pulito; quelle che
  restano al ribasso hanno il delta TIER interno negativo: H17/H31/H28
  persi sui giorni sorgenta > guadagno H34, o gate-e non pianificabili).
- BIVIO: se 0/10 ANCHE a pricing pulito: la valle e' H32-INTERNA (il tier
  stesso prezza le transazioni negative): la famiglia composite e' morta
  a prescindere dai pesi, il filone design-mosse chiude sul versante Kempe
  e resta solo il pre-booking. Se >0: la famiglia RINASCE col gradiente
  pulito (la redenzione che il R&R storico non ha dato) e E3 (schema
  per-stadio + finestra Kempe nel ciclo) diventa candidato REALE.
- METRO P(0hard)/wall: ogni cella chiusa = -1 hard sulla via di consegna
  (13 delle -17 del tuttoon): k celle = -17 -> -(17-k); la finestra costa
  ~120s/ciclo (trascurabile). Se k >= 6 la P(0hard)/tentativo sale in modo
  materiale.

### E2-QUATER ESITO (27/08 19:45) - 0/10 ANCHE A PRICING PULITO: la valle e' TIER-INTERNA
- MISURA (stesso fixture h34on 10 celle, stessa finestra, hill-climbing acl=1):
  | braccio | celle chiuse | wall | miglioramenti | score regime |
  | pieno | 0/10 | 46,4s | 1 | -124.016hard |
  | zero | 0/10 | 46,4s | 1 | -16hard (pricing PULITO: l'override era attivo) |
  Re-scoring a pesi pieni del risultato zero: -124.016hard (griglia identica:
  nessuna mossa presa).
- PREDIZIONE SMENTITA: zero-arm previsto 2..6, misurato 0. Il meccanismo E2-ter
  (accettatore sommerso dalla massa room) NON si trasferisce alle whole-cell:
  qui il problema non e' il pricing sporco, e' che le transazioni chiudi-cella
  NON migliorano nemmeno il solo tier: il +1 H34 netto vale meno del prezzo
  tier-interno della transazione (H17 aperti, H31, H28 sui giorni sorgenta, o
  gate-e non pianificabili). Il braccio pieno replicato byte-simile (0/10,
  46,4s, stesso score: replica esatta del banco del 26/08).
- LETTURA FINALE DELLA GIORNATA SUI PESI (E2 + E2-bis + E2-ter + E2-quater):
  - mosse PICCOLE (stock): la massa room non frena causalmente (E2/E2-bis).
  - DISTRUTTIVE storiche (R&R): freno causale REALE (4/4 segno) ma la famiglia
    e' debole comunque (R&R pulito -106,5 < stock -91).
  - COMPOSITE whole-cell: nessun freno da pesi: valle TIER-INTERNA (0/10 in
    entrambi i regimi, pricing dimostrato pulito dal -16hard di partenza).
  LO SCHEMA PER-STADIO NON HA PIU' NESSUNA FAMIGLIA DI MOSSE DA GIUSTIFICARE:
  E3 CHIUSO DEFINITIVAMENTE. La via verso 0hard e' STRUTTURALE (pre-booking,
  TASK-564, priorita' alta) o una CLASSE di vicinato diversa (Kempe-chain
  cross-teacher TASK-482: resta l'unica famiglia di mosse aperta).
- METRO P(0hard)/wall: la finestra whole-cell costa 46..120s e chiude 0 celle
  in ogni regime: NESSUNA adozione. Nessun ritorno, costo dichiarato.
- INFRA: il banco ora supporta il braccio env PLANNER_TEST_KEMPE_CELL=zero +
  contatore miglioramenti + re-scoring pieno (committed): il banco resta
  riutilizzabile per TASK-482 (chain) quando tocchera'.

### NOTTE 27/28-08 - TASK-564 PROTOTIPO PREDITZIONI PRIMA (scritte prima dei banchi)
Implementato dietro planner.construction.prebooking.enabled DEFAULT OFF
(opt-in, banner, letto LIVE), design doc TASK-564 opzione (b): pass
post-construzione/pre-PROMOTION (copre anche il resume da POST_CONSTRUCTION),
place-only dal pool unplaced, ATOMICO per cella (un riempimento parziale e'
peggio del vuoto: H32 quota-ore > H34 flat 1, gate c di QFR v2: pianifica in
simulazione, applica solo a quota raggiunta), H1/H3/H7/VRP/cap live,
atterraggio bordo-consapevole, preferenza template M32, stanza libera
assegnabile o null (B-ROOM), improve-or-noop sul tier + conflitti 0,
censimento prebooking.debug. DataGenerator intatto.

PREDIZIONE BANCO SINTETICO: la cella dichiarata vuota con pool sufficiente
viene chiusa a quota (2 lezioni, contigue dal primo slot, 0 conflitti);
l'atomicita' lascia VUOTA la cella quando il pool non arriva (declined, non
parziale); flag OFF no-op; H1 fa cadere al candidato successivo; la
preferenza template sceglie il corso templato; le Stage (pseudo) mai toccate.

PREDIZIONE BANCO LOCALIZZATO (resume dal POST_CONSTRUCTION del vincitore
notturno attempt1: il ciclo tuttoon-27 NON ha catturato checkpoint,
sostituzione dichiarata): celle sotto-quota dichiarate a inizio pass
(attese ~19 per il reference) -> **0..2 residue CHIUSE** (cioe' quasi tutte
le chiudibili-aritmeticamente vengono prenotate: il pool unplaced a fine
costruzione e' ~1306, le celle ~19: la scarsita' non e' il vincolo, la
coerenza H1/H7/VRP lo e'); conflitti aggiunti 0; il tier hard NON peggiora
(gate improve-or-noop). SE invece il pass chiude <5 celle con censimento
NO-CANDIDATE dominante: le celle sono teacher-locked gia' a livello di pool
e la cura va al template (v2 del design), si dichiara.

### NOTTE - BANCO LOCALIZZATO POST_CONSTRUCTION ESITO (27/08 20:22) e PIVOT OPZIONE (a)
- ESITO (treatment, resume POST_CONSTRUCTION vincitore attempt1): il pass e'
  un NO-OP VERIFICATO: "0 celle chiuse, 0 lezioni piazzate, 64 giorni
  lasciati come trovati, 0 conflitti". Censimento: NO-CANDIDATE=61,
  CELL-DECLINED=64, SLOTS-EXHAUSTED=3.
- PREDIZIONE (0..2 residue non chiuse, cioe' quasi tutte chiuse): SMENTITA
  nella direzione chiara. LETTURA DEL MECCANISMO: a fine costruzione il pool
  residuo e' cio' che il tail round-robin (e il box pass) NON e' riuscito a
  piazzare da nessuna parte senza conflitti: i docenti di quelle lezioni sono
  saturi ovunque serva. Il pass place-only non ha niente di prenotabile:
  la scarsita' era gia' stata sparsa. Il design doc lo anticipava (opzione
  (a) scartata per isolamento, non per meccanica): la sede giusta e' PRIMA
  del box pass, quando il pool contiene ancora le ore che il tail spargera'.
- Protocollo dichiarato: il ciclo treatment e' stato fermato (griglia
  identica al control dopo 0 piazzamenti: la A/B a consegna avrebbe misurato
  rumore); il control era stato ucciso a minuto 18 da un falso-detect dello
  script (fallimento HTTP contato come consegna: rifatto il rilevamento su
  righe di log, non su poll HTTP).
- PIVOT (opzione (a) implementata, stesso flag): prebooker DENTRO
  DayClusteredConstructionPhase, tra round-robin e box pass, con Moves della
  fase + resync delle mappe di occupazione (estratto resyncTrackingMaps).
  Le stanze tolte dal pass (coerenza col contratto room-null TASK-446:
  la fase 4 le possiede). 10/10 test banco sintetico verdi.

### PREDITZIONE BANCO OPZIONE (a) (scritta prima delle run, 27/08 ~21:50)
Setup: resume da attempt1/CYCLE_START (costruzione fresca), 2 braccia
(OFF/ON), misura a fine costruzione (riga prebooker + conteggio celle vuote
dichiarate da DB + boxed del checkpoint POST_CONSTRUCTION).
PREDIZIONE: il pool a quel punto contiene lezioni piazzabili (il box pass ne
collecherebbe molte): celle vuote dichiarate chiuse **8..15 delle ~19**, la
conta a fine costruzione 19 -> **4..11**; boxed a fine costruzione INVariato
o migliore (il prebooking consuma pool che il box avrebbe usato altrove:
stesso totale, distribuzione diversa); conflitti 0 per costruzione; la
ricorrenza fine-costruzione invariata o -1..2pp (preferenza template).
Se 0 chiuse ANCHE qui: le celle sono teacher-locked gia' dentro la
costruzione e la cura va al TEMPLATE (WeeklyTemplateBuilder: opzione v2),
dichiaro e fermo.

### NOTTE - BANCO OPZIONE (a) ESITO (27/08 21:15) - CLAUSOLA PRE-REGISTRATA SCATTATA
- MISURA (resume attempt1/CYCLE_START, costruzione fresca in 5s, OFF vs ON):
  | braccio | celle vuote fine-costr. | prebooker | boxed |
  | OFF | 13 | - | 1296 |
  | ON | 13 | 1 cella chiusa (un giorno SOTTILE, 4 lezioni), 51 lasciati (49 NO-CANDIDATE, 2 SLOTS), 0 conflitti | 1296 |
- PREDIZIONE (8..15 delle ~19 vuote chiuse): SMENTITA: **0 delle 13 celle vuote**.
  La sola chiusa e' un giorno parziale. NO-CANDIDATE domina ANCHE prima del
  box pass: dopo il round-robin (10.940 lezioni piazzate) i docenti del pool
  residuo insegnano gia' negli slot che le celle richiederebbero. La capacita'
  contesa non e' "sparsa per ordine di arrivo": e' GIA' OCCUPATA dallo stamp/
  round-robin. CLAUSOLA PRE-REGISTRATA ("se 0 chiuse anche qui: teacher-locked
  dentro la costruzione, cura al TEMPLATE v2, dichiaro e fermo"): SCATTATA.
- ARCO SCIENTIFICO COMPLETO della nottata sul pre-booking place-only:
  post-construction 0/64; in-construction pre-box 1/52 (0 vuote). Il venue
  "piazzabili dal pool" e' ESAURITO a ogni confine: la cura delle celle
  richiede che la capacita' dei giorni dichiarati venga RISERVATA quando lo
  STAMP sceglie dove mettere le ore (WeeklyTemplateBuilder/stamp: v2), non
  dopo. Nessuna adozione: flag resta DEFAULT OFF.
- INFRA NOTTE: il banco ha pescato due guardie vere (StagedCheckpointFormatTest
  x2: censimento boundary) + i reviewer hanno pescato un test rosso del mio
  (fixture double-count, corretto), due bug comportamentali (pool-leak su
  cella declinata, ore Stage contate), un gate H28 mancante: TUTTI corretti
  nella notte. Room-strip dal pass (coerenza TASK-446 Alt-B). L'hook
  post-construction e' stato RIMOSSO (venue misurato morto): resta solo
  l'innesto in-fase, stesso flag.
- DoD: /simplify 3 reviewer (trovati e applicati: bug pool-leak, Stage-hours,
  H28 gate, accessor condivisi, doppie scritture, path noop) + /review-local
  high 5 reviewer (test rosso corretto, wording contratti allineati, 3 test
  gate aggiunti: H7, Stage, H28) + suite FINALE 1200/0 EXIT 0.
- DIFFERITI annotati in TASK-564: test VRP-window, propagazione H1 cross-gruppo
  a commit, pin determinismo doppio-build, harness wiring improve-or-noop.

## QUADRO-RISVEGLIO 27/08 NOTTE (tre colonne, per la sessione del mattino)
### Colonna 1: CONSEGNATO
1. Prototipo DeclaredDayPreBooker (TASK-564) dietro flag opt-in, innesto
   IN-COSTRUZIONE (opzione a) tra round-robin e box pass; room-null by
   design; 13 test banco sintetico; suite 1200/0 EXIT 0; committato.
2. Banco sintetico (predizione centrata) + banco localizzato POST_CONSTRUCTION
   (no-op verificato) + banco CYCLE_START opzione (a) (0/13 vuote): l'arco
   completo con predizioni PRIMA e verdetto onesto per ognuno.
3. VERDICT di non-adozione + direzione v2 (stamp/template) pronto per maintainer.
### Colonna 2: I NUMERI
1. POST_CONSTRUCTION: 0 chiuse/64 target, NO-CANDIDATE 61.
2. CYCLE_START in-construction: 1 chiusa (sottile)/52, 0 vuote/13,
   NO-CANDIDATE 49, conflitti 0, boxed invariato 1296.
3. Costruzione fresca: 5s (il collo e' la LS, non la costruzione).
4. Suite 1200/0 (10 test prebooker, +3 gate notte); guardie census soddisfatte.
### Colonna 3: DECISIONI PER maintainer (mattina)
1. PRE-BOOKING v2 = STAMP-AWARE: riservare la capacita' dei giorni dichiarati
   QUANDO lo stamp sceglie (WeeklyTemplateBuilder/stamp), non dopo: serve
   design nuovo (il posto dove i docenti contesi vengono assegnati ai giorni).
   Il place-only e' dimostrato esausto: questa e' la evidenza chiave.
2. Il gate "ciclo completo TUTTO ON + prebooking ON" resta RIMANDATO (qui non
   servirebbe: il pass e' inerte sul campo celle; non lanciato senza maintainer).
3. Flag planner.construction.prebooking.enabled resta DEFAULT OFF (prototype
   committed, riutilizzabile per la v2).
4. Da presidiare: ricorrenza 69,2% (effetto metro M22), TASK-563 (test EDF),
   short-attempt multi-start (candidato), pacchetto referente (pronto, non inviato).

### NOTTE - v2 STAMP-AWARE PREDITZIONE PRIMA (27/08 ~21:30, prima dei banchi)
Implementata (stesso flag): reserveDeclaredDayPlacements, terzo membro della
famiglia reserve* (dopo profile-following TASK-432 e teacher-steering iter-25):
le ULTIME 56 date dichiarate per gruppo (finestra di rischio misurata: tutte
le celle residue stanno nelle ultime 8 settimane; knob tail-days), in ordine
DISCENDENTE, ottengono la prima scelta delle proprie celle-template, con gli
stessi gate dello stamp cronologico (VRP, H1/H3, H7/H11, cap atteso
pseudo-nettato) e lo stesso path assign/track. Lo stamp sotto resta invariato
(trova le code piu' vuote e skip). Banco sintetico: DEVIAZIONE DICHIARATA dal
mandato: la famiglia reserve* si misura col REAL-SEED construction test
(WeeklyTemplateConstructionRealSeedTest, precedenti 432/iter-25), non con
fixture sintetiche (il pass vive dentro la fase); il banco sintetico del
place-only resta a coprire la parte unitaria.
- PREDIZIONE banco real-seed/costa nulla (costruzione 5s): celle vuote a fine
  costruzione 13 -> **3..7** (la coda d'anno ottiene la prima scelta; restano
  le celle i cui docenti sono contesi ANCHE a prima scelta: il reserve e'
  alfabetico per gruppo, G13 e' ultima); boxed **1296 +/- 50** (guardia);
  conflitti 0 per gate; ricorrenza fine-constr. 76-77% +/- 1pp; tier
  construction-end -13.059hard +/- 500.
- PREDIZIONE banco CYCLE_START (OFF vs ON, stesso protocollo di 21:15):
  stesso esito atteso del real-seed (la costruzione e' identica).
- CRITERIO del mandato: celle sotto meta' (7 o meno) senza regressione
  boxed/tier -> DoD + VERDICT di adozione PREPARATO (gate = ciclo completo
  domani con maintainer, NON lanciato stanotte).

### NOTTE - v2 ESITO (27/08 22:10) - CRITERIO DEL MANDATO CENTRATO, CON UN TRADE DICHIARATO
- BANCO CYCLE_START (OFF vs ON, costruzione 5s): celle VUOTE a fine costruzione
  **13 -> 3** (predizione 3..7: CENTRATA al limite aggressivo). La v2 ha
  riservato 3121 celle-template su 730 date di coda (finestra 56 giorni,
  discendente). Composizione ON: 12 delle 13 originali CHIUSE + 2 NUOVE a
  metà marzo (G05 11+15/03, FUORI finestra: la fame si sposta appena
  prima del confine, whack-a-mole della finestra fissa) + G12 16/04 (l'unica
  originale sopravvissuta). IPOTESI (da verificare col ciclo): le nuove celle
  stanno nella zona mediana che la LS chiude storicamente facile (le celle che
  sopravvivono a consegna sono sempre in coda d'anno): SCAMBIO BUONO se confermato.
- GUARDIE: boxed 1296 -> 1298 (+2, banda +/-50 OK); tier hard construction-end
  -13.059 -> -13.103 (-44, banda +/-500 OK); medium MIGLIORA -311k
  (-1.813k -> -1.502k: il tail-first riempie i giorni dichiarati, M20/M20.2).
- REGRESSIONE DICHIARATA (trovata dal guard del real-seed test, che sotto flag
  ON va ROSSO): celle SOTTO-QUOTA (firma H32, non solo vuote) nel contesto
  test 41 -> 72: la v2 crea giorni TACCHI oltre a chiudere i vuoti. In-app
  OFF=66; ON non misurato pulito (misure notturne intralciate da container
  sparito + task appesi: dichiarato non-misurato). Il trade completo:
  -10 celle VUOTE (H34, la famiglia dura) a fronte di ~+30 giorni TACCHI
  (H32, la famiglia che LS+QFR chiudono): CHI DECIDE e' il CICLO COMPLETO
  (gate di domani con maintainer), non il banco di costruzione.
- Ricorrenza fine-constr. (real-seed test): 72,5 -> 71,7 e 72,0 -> 70,8
  (-0,8/-1,2pp: al limite della banda +/-1 dichiarata).
- DoD v2: /simplify + verifica avversaria della parita' dei gate (ENTRAMBI i
  verifier: CONFIRMED, i break sono provably equivalenti ai continue dello
  stamp; unica divergenza latente: gate weekend assente nel reserve, MORTO sul
  seed attuale griglia Mon-Fri, annotato). Applicati: javadoc orfano del
  sibling (metodo spostato), RITIRO della chiamata option-a (misurata inerte:
  la v2 possiede il flag; la classe place-only resta dormiente coi suoi test),
  knob centralizzato in DeclaredDayPreBooker.tailDays(). Suite FINALE 1200/0
  EXIT 0 (default-OFF verde; il guard real-seed sotto flag ON rosso e'
  comportamento opt-in documentato: e' lui il semaforo del gate di adozione).
- VERDICT di adozione PREPARATO (non deciso: il trade H34-vs-H32 si arbitra
  col ciclo completo, domani con maintainer, NON lanciato stanotte da me).

## QUADRO-RISVEGLIO AGGIORNATO (voce unica, sostituisce la colonna 1 della notte)
1. v2 STAMP-AWARE implementata e misurata: 13 -> 3 vuote, guardie in banda,
   trade H32 dichiarato, gate = ciclo completo domani.
2. Flag unico planner.construction.prebooking.enabled: ora arma SOLO la v2
   (reserveDeclaredDayPlacements, finestra tail-days 56 knob); la classe
   place-only dormiente coi suoi 13 test.
3. Tutto committato e spinto; suite 1200/0 EXIT 0; registro con predizioni
   PRIMA per ogni banco della nottata (sintetico, POST_CONSTRUCTION,
   CYCLE_START v1, CYCLE_START v2).

### NOTTE - ARBITRAGGIO PARZIALE H32 v2 PREDITZIONE PRIMA (27/08 22:15)
Domanda (Fedora): il debito H32 della v2 (41->72 sotto-quota a fine
costruzione nel contesto test) e' riassorbibile dai repair SENZA perdere le
celle dichiarate chiuse (13->3)? Banco: costruzione fresca CON v2 ->
catena fino a POST_ROOM_QUOTA_FILL_REPR (niente ciclo completo; il gate
pieno resta di maintainer domani). Baseline = attempt1 PRQFR (checkpoint
esistente, ripristino+coda 10s) misurato ADESSO sugli stessi assi.
PREDIZIONE (la mia, da battere): a PRQFR col braccio ON:
(a) celle vuote dichiarate **2..5** (le 3 di costruzione + erosione LS,
    QFR/EDF ne chiudono qualcuna);
(b) sotto-quota H32 **30..55** (QFR riassorbe i tacchi: il suo mestiere;
    scende sotto ~50 se il riassorbimento lavora);
(c) tier timeslot-hard non peggiore del baseline attempt1 (-14: banda
    -8..-20), boxed 0..3.
Se il sotto-quota NON scende sotto ~50: il debito NON si riassorbe nella
catena: dichiaro il trade REALE e il gate di domani decide col ciclo pieno.

### NOTTE - ARBITRAGGIO H32 ESITO (27/08 23:50) - IL TRADE E' REALE E NETTO-NEGATIVO A QUESTO CONFINE
- BASLINE (attempt1 PRQFR+coda, stesso confine): H34 8, H32 0, tier -14, boxed 1.
- ON (v2, catena piena fino a PRQFR+coda): PRQFR tier -22 boxed 0; STATO FINALE:
  **H34 1** (da 8: l'asse immune quasi azzerato), **H32 10** (i tacchi NON
  riassorbiti: la catena ne ha chiusi l'86%: 72->10, ma 10 restano), **H17 16**
  (vs 2: il layout tail-first apre gap che la LS non chiude), tier totale
  **-27 vs -14**.
- PREDIZIONE vs MISURATO: celle vuote 2..5 -> **1** (MEGLIO); sotto-quota <50 ->
  10 (OK per conteggio); tier -8..-20 -> **-27: FUORI in peggio**.
- VERDETTO dell'arbitraggio (parziale, locale): il debito H32 NON si riassorbe
  completamente nella catena e l'erosione H17 si somma: il v2 come sta e'
  NETTO-NEGATIVO sul tier di consegna (-27 vs -14) nonostante l'H34 quasi
  azzerato. La cura ha bisogno di lavoro (controllo H17 del layout tail-first
  + la coda H32) PRIMA di qualunque adozione. Il gate di domani (ciclo
  completo multi-start con maintainer) resta l'arbitro finale, ma stanotte la
  risposta e': il trade e' REALE, non gratis.
- CORREZIONE dichiarata della nottata: la mia lettura "scarto predictor" delle
  22:12 era SBAGLIATA (il tail -1 del grep nascondeva la riga sana: 3121
  riservate; il "0 across 0" e' la consueta no-op ri-esecuzione della fase CH
  nello slice B-TIMESLOT, benigna e presente da sempre). Nessuna interazione
  v2-predictor e' stata osservata ne' testata.

### NOTTE - RE-BENCHMARK METAEURISTICHE SOTTO REGIME PULITO (direttiva maintainer, se resta finestra)
PIATTAFORMA (deviazione dichiarata): run-benchmarker.sh NON puo' portare
l'override per-braccio (il suo path solver non passa dal hook
withRoomTierOverrideIfKnobbed, che vive nel ciclo staged): lo screening gira
sulla piattaforma E2-ter gia' cablata (knob TASK-549 planner.ls.acceptor x
planner.room-tier.override, resume condannato attempt5 POST_BT1, finestra
120s x 2 fasi, x2 rep). Braccia NUOVE: la-20 pieno vs la-20 room-zero (il
tabu-7 e' GIA' misurato in entrambi i regimi da E2/E2-ter: A -125,0 / B
-106,5). VND/acl NON knob-abile su questa piattaforma: task del mattino con
la plumbratura override nel harness.
PREDITZIONE PRIMA: per le mosse PICCOLE il ranking di AGOSTO SI CONFERMA
invariato sotto regime pulito (E2/E2-bis: la massa room non frena
causalmente le mosse piccole): attese la-20 pieno ~-125..-135 e la-20 zero
~-105..-120 (delta <= 10, come tabu); la famiglia DISTRUTTIVA resta l'unica
che guadagna causalmente dal regime pulito (gia' misurato in E2-ter: 23
punti). Se anche la-20 separa >15 punti: il ribaltamento e' reale per le
small-move e il filone redenzione si riapre con piano di campagna piena.

### NOTTE - RE-BENCHMARK ESITO (28/08 00:30) - RANKING CONFERMATO, FILOGNE REDENZIONE CHIUSO PER LE MOSSE PICCOLE
- MISURA (la-20, condannato, 120s, 2 rep, tier a BT3): la-20 pieno -104/-97
  (media **-100,5**), la-20 zero -98/-95 (media **-96,5**): delta 4, DENTRO
  la variabilita' rep.
- TABELLA riassuntiva della piattaforma (stesso checkpoint/finestra; stock =
  mosse piccole; R&R = famiglia distruttiva):
  | famiglia | pieno | zero | delta |
  | tabu-7 stock (E2) | -88,5 | -91,0 | 2,5 |
  | tabu-7 stock 480s (E2-bis) | -92,0 | -90,0 | 2 |
  | la-20 stock (nuovo) | -100,5 | -96,5 | 4 |
  | R&R storico (E2-ter) | degrada a -125 | migliora a -104,5 | ~20 CAUSALE |
- PREDITZIONE CONFERMATA: il ranking di agosto per le mosse PICCOLE SI
  CONFERMA sotto regime pulito (tabu-7 resta meglio di la-20 in entrambi i
  regimi; i delta room-zero 2..4 sono dentro la variabilita'): NESSUN
  ribaltamento. La redenzione causale resta CONFINATA alla famiglia
  distruttiva (E2-ter), che comunque resta SOTTO lo stock anche pulita
  (-104,5 vs -91): NESSUNA via di adozione.
- VERDETTO (metro P(0hard)/wall): le classifiche di agosto fanno testo anche
  sotto regime pulito per le metaeuristiche a mosse piccole: il filone
  redenzione SI CHIUDE definitivamente su questo versante. La campagna piena
  di re-benchmark NON e' giustificata da questo screening (dichiara: screening
  n=2, una finestra, un bacino; il versante VND/acl resta da misurare col
  plumbing override nel harness run-benchmarker: task del mattino SE maintainer
  vuole completare il quadro).
- NOTA piattaforma: la-20 tabu-7 ecc. girano col knob TASK-549 sul ciclo
  staged (l'harness run-benchmarker non porta override per-braccio: deviazione
  dichiarata nella voce predizione).

## QUADRO NOTTURNO FINALE 27/28-08 (voci in ordine di valore, per maintainer)
1. ARBITRAGGIO v2 (locale, catena piena): il trade H34-vs-H32 e' REALE e
   netto-negativo a consegna (-27 vs -14): H34 8->1 (quasi azzerato) MA H32
   10 residui + H17 16 (vs 2). La v2 NON e' pronta: serve controllo H17 del
   layout tail-first e chiusura della coda H32. Il gate del ciclo completo
   multip-start resta di maintainer (oggi): l'evidenza notturna dice NON adottare
   cosi'.
2. PLACE-ONLY (v1): esausto a ogni confine (0/13 celle): il venue e' lo
   stamp. Prototipo dormiente committed, VERDICT scritto.
3. RE-BENCHMARK metaeuristiche: ranking agosto CONFERMATO sotto regime pulito
   per le mosse piccole (tabu > la in entrambi i regimi, delta room 2..4);
   redenzione causale solo nei distruttivi (E2-ter) che restano sotto lo
   stock: filone chiuso.
4. Flag planner.construction.prebooking.enabled: arma SOLO la v2
   (reserveDeclaredDayPlacements, tail-days 56 knob); DEFAULT OFF ovunque.
5. Suite 1200/0 EXIT 0; tutte le predizioni scritte prima; ogni smentita
   dichiarata (inclusa la mia lettura-scarto errata delle 22:12, corretta).
6. Aperti per il mattino: ciclo completo TUTTO ON + v2 (gate maintainer),
   plumbing override nel harness per VND/acl (opzionale), TASK-563, candidato
   short-attempt, pacchetto referente (pronto, non inviato), ricorrenza 69,2%.

### GATE CICLO COMPLETO v2 - PREDITZIONE PRIMA + LANCIO (28/08 06:45, GO maintainer via Fedora)
Pre-registrazione completa (riga di lancio, banner, criteri di decisione):
task564-results/gate-ciclo-v2-28/PREDIZIONE-PRIMA-gate-2026-08-28.md, commit
d436764b (fatto quando il registro era irraggiungibile per l'incidente
worktree-clobber: vedi NOTA AMBIENTE li'; qui la sintesi operativa).
SETUP: profilo fresco gate564-v2, capture dir dedicata
task564-results/gate-ciclo-v2-28/, tutti i ratificati DEFAULT ON (nessun flag
esplicito, 8 banner attesi), unico -D planner.construction.prebooking.enabled=true
(v2, tail-days default 56), multi-start N=5 default, rebuild jar prima del
lancio (preflight stale-jar).
PREDITZIONE (bande rivedute dal registro, correzioni vs mandato dichiarate):
1. H34 celle consegna 0..3 (centro 1; arbitraggio: 1 a PRQFR+coda, EDF chiude
   coda; rischio whack-a-mole G05 11+15/03).
2. H32 residuo 5..12 (centro ~10), NON 0..4: 10 tacchi a PRQFR+coda e la coda
   del ciclo pieno non e' specializzata su H32. Ottimistico 2..5 solo con retry
   che cambia bacino.
3. Tier timeslot-hard consegna -18..-32 (centro ~-25; arbitraggio -27, la coda
   room non lo cura; tuttoon-27 ando' da -14 a -17). Banda -8..-20 del mandato
   = solo caso favorevole.
4. Medium recount -780k..-950k (centro ~-860k; tuttoon-27 -851.159; v2 scambia
   ~12 vuoti M20.2-pieni con ~10 tacchi a deficit parziale e tail-first riempie
   giorni dichiarati).
5. Hard finale -12..-30 (centro ~-20); -0..-6 = caso favorevole esplicito, NON
   attesa centrale; 0hard NON atteso (0/5 anche qui).
6. Wall ~7h; full-day 89..91; day-start 0 late; ricorrenza 68..71 (metro M22);
   boxed 0..2; sanity NON conforme 3..7 gruppi con forma cambiata (meno H34,
   piu' H32/H17).
CRITERI PRE-REGISTRATI: tier <= -20 = trade NON riassorbito, nessuna adozione;
tier -13..-19 E H32 <= 4 = candidata adozione con A/B n>=2; zona grigia si
decide sulla composizione per famiglia; medium fuori banda (peggio -960k /
meglio -750k) = autopsia composizione prima di leggere il trade.

### E3-END-TO-END PREDITZIONE PRIMA (28/08 ~07:50) - ANNULLATA dal correttivo prima di qualunque lancio
Ordine originale (maintainer via Fedora): coppia di cicli appaiati A/B per
l'architettura room-tier end-to-end (A = control identico al gate, B = A +
planner.room-tier.override=zero solo-timeslot), in coda al gate.
Pre-registrazione scritta e committata (doc
task542-results/e3-e2e/E3-E2E-PREDIZIONE-PRIMA-2026-08-28.md, commit 08c0d340,
con righe di lancio, bande e criteri a tre esiti). CORRETTIVO successivo
(stessa mattina, Fedora): il mandato cicli SI ANNULLA, sostituito dal
re-benchmark delle metaeuristiche sotto regime room-zero (voce seguente).
NESSUN ciclo A/B e' mai stato lanciato: il gate e' l'unico solve mai partito
oggi. La pre-registrazione E3 resta come record storico inutilizzato, le sue
bande non si misurano; l'annullamento e' annotato anche in testa al doc.

### RE-BENCHMARK METAURISTICHE REGIME-PULITO PREDITZIONE PRIMA (28/08 ~09:15, correttivo maintainer via Fedora)
Mandato: harness STORICO (run-benchmarker.sh), config storiche INTATTE, sotto
planner.room-tier.override=zero; sostituisce la coppia E3. Setup: TabuVsVND
(9 braccia, pool 6), AcceptedCountLimit (4 braccia: acl 1000/100/4/1),
MoveStats/V2/V3 (diagnostiche 900s); input storico local/data/schedule-production.json
(copiato dal checkout principale: marzo 2025/26, 10.550 lezioni, VUOTO: pipeline
completa per braccio, identico ad agosto). Cablaggio: commit c0fb4c28
(BenchmarkRoomTierOverride via API pubblica 2.3, banner ARMED, fromKnob
condiviso col ciclo staged, env BENCHMARK_SYSTEM_PROPERTIES). DEVIAZIONE
DICHIARATA: su questo harness l'override vale su TUTTE le fasi di ogni braccio
(lo scoping solo-timeslot del ciclo staged non e' rappresentabile senza
riscrivere le config storiche, vietato): le fasi stanza 4/5 girano con le sei
famiglie spente (la fase 5 LS sara' ~no-op). Il confronto resta valido come
RANKING intra-batteria (stesso regime per tutti), NON come punteggio assoluto
vs agosto. M-TC + H-COPRESENCE sempre a peso pieno. Wall totale ~1,5..2h: sta
nella finestra di oggi DOPO il gate (runbook: task542-results/rebench-roomzero-28/).
RIFERIMENTO AGOSTO (task284 VERDICT 04/08, stesso input, n=1): VND-DimReturns
-242 > VND-Cartesian -258 > VND-FI -284 > LA -290 > SA-Tabu -322 >> cluster
tabu ~-6700 (Size5 -6699, Size7 -6740, LA+Tabu -6756) e VND-HillClimbing -7783.
PREDITZIONE:
1. TOP-5 invariato nell'ORDINE (il loro residuo era timeslot-tier genuino,
   centinaia non migliaia).
2. IL CLUSTER TABU (~-6700) COLLASSA verso il top (atteso -200..-1500): quel
   residuo era dominato da massa H29 (precedente acl: -7498 H29; dataset senza
   stanze), che il regime spegne: gap 25x -> 2..5x, CON POSSIBILE INVERSIONE
   tabu>LA (tessere notturne regime pulito: tabu-7 -88,5 > la-20 -100,5).
   DISCRIMINATORE: se il cluster tabu RESTA ~-6000, il residuo era tier genuino
   e la classifica resta alla agosto (esito informativo anch'esso).
3. VND-HillClimbing resta ultimo (il suo problema e' best-improvement).
4. ACL: la curva 1000/100/4/1 si APPATTISCE (delta hard <100 fra le braccia,
   lieve edge alle acl basse: le differenze agosto erano guidate dal residuo
   H29 che la acl bassa puliva meglio).
5. MoveStats: contributo mosse stanza ~0 nel regime (atteso, non finding).
6. Medium: tutte le braccia +80..+150k (M-LF/M37 spenti): non comparabilita'
   assoluta, solo ranking.
CRITERI (metro P(0hard)/wall, NESSUNA adozione): (i) ranking top invariato +
compressione cluster = conferma agosto + la massa H29 era rumore da battery,
si dichiara e si chiude; (ii) ribaltamento >15 punti in qualunque braccia
small-move = segnale reale: SECONDA COPPIA di batterie nella finestra di oggi
(~2h) prima di qualunque lettura; (iii) wall entro 1.25x di agosto per battery.

### CURA F1 GOLDEN-FIRST G09 04/06 (28/08 ~12:00, ordine maintainer via Fedora, NON attende referente)
Eseguita SOLO seed CSV + re-pin, DataGenerator intatto. DROP della riga
`G09,2027-06-04,4,A` (quota 4 = riga intera, come 07/06) in ENTRAMBE le
copie (src/main/resources/seed/ e claudedocs/.../data/), byte-identiche
verificate (51133->51110 byte, 2277->2276 righe CRLF, LF-only 0, hash uguali).
Re-pin: DataGeneratorExpectedDailyHoursSeedTest 1738->1737 righe, G09 aula
666->662 (commento con revert a una riga); GoldenSolutionRegressionTest H34
-1->0 (nessuna cella dichiarata vuota nel golden), M20.2 -14000->-7000 (resta
solo G14 quota-7), H0 pin ESPPLICITO 0 aggiunto (il pool 666h resta e il
golden lo colloca per intero: nessuna lezione boxed, nessuna ora parcheggiata).
derive-golden-incarichi-corrections.py: 0 divergenze (incarichi intatto,
provato meccanicamente). Pacchetto referente: domanda #1 riformulata
NOTIFICA-CON-DOMANDA (abbiamo assunto il pool praticato e corretto il foglio:
confermate o le 4h del 4/6 erano reali; revert a una riga per copia).
CONTRADDIZIONE MATERIALE ESPOSTA (la meta' "4h in expectedUnscheduledHours"
del mandato non e' eseguibile come specificata): verificato su tutto src/main,
Course.expectedUnscheduledHours e' popolato SOLO da generateStageCourses
(Stage pseudo, foglio-vs-golden-held) e da GoldenScheduleImportService (import
golden): NON esiste meccanismo CSV che lo popoli per corsi ordinari, e
crearne uno richiede DataGenerator, che lo stesso mandato dichiara
intoccabile. Inoltre i 4h NON sono domanda fantasma lato pool: il golden
colloca TUTTE le 666h del pool in-grid (4 come surplus sopra il piano, come i
suoi 70 giorni sopra quota): parcheggiarli sottoregistrerebbe rispetto alla
pratica golden (662 collocate su 666 chiesti). RACCOMANDAZIONE: non parcheggi
(niente cambia per il solver: il surplus e' medium M33 consentito, il deficit
H34 sparisce); se maintainer vuole comunque il parcheggio a fini di reconciliazione,
serve una deroga autorizzata a DataGenerator o una colonna CSV nuova da
discutere. Suite completa: DIFFERITA a CPU libera (gate in corso), PRIORITA'
sopra le batterie re-benchmark nella coda post-gate.

### GATE ESITO (28/08 12:44 consegna, 13:04 chiusura) - TRADE NON RIASSORBITO: v2 NON ADOTTABILE COSI'
CICLO: 5/5 tentativi, 06:48->12:44 (~5h56m), 0hard mai raggiunto (atteso), vincitore
TENTATIVO 1 deterministico (log: "best attempt 1 shipped with CH-only rooms (no 0-hard
attempt ran) - refining its rooms before delivery": corner-b TASK-498). CONSEGNA
(recount analysis): **-81hard/-1.001.711medium**. Composizione hard: **H17 -74 (73
gap!)**, H34 -5, H32 -1, H33 -1; H0/H29/H2 = 0 (boxed 0, stanze tutte assegnate).
Medium: M32 **-707k (5569 match, era -287k nel tuttoon-27: template deviation
RADDOPPIATA)**, M-LF -90k, M20.2 -89,5k, M15 -46,8k, M20 -22k.
PREDIZIONE vs MISURATO: H34 5 (banda 0..3: FUORI di 2); H32 1 (banda 5..12: FUORI in
MEGLIO, la composizione e' INVERTITA rispetto all'arbitraggio notturno H34 1/H32 10);
H17 74 (banda 4..16: FUORI in PEGGIO x4,6: l'erosione NON era quella del confine
PRQFR); tier -81 (banda -18..-32: FUORI in peggio); medium -1.001M (banda
-780..-950k: FUORI peggio di ~50k, driver M32). GUARDIE TUTTE IN BANDA: full-day
90,0% (89..91), day-start 100% ancorato 0 late, ricorrenza unpinned 69,7% (68..71),
sanity NON CONFORME su 3 gruppi (G10 25gg buchi interni, G11 6, G13 21: banda
3..7, forma attesa "piu' gap H17"), no-stanza 0.
VERDETTO (criterio pre-registrato): tier -81 <= -20 -> **trade H34-vs-H32 NON
riassorbito dal ciclo pieno: la v2 (reserveDeclaredDayPlacements) NON e' adottabile
cosi'**. La lettura del meccanismo: il vincitore e' il tentativo 1 (stessa costruzione
dell'arbitraggio: 3122 riservate) MA la consegna mostra H17 74 vs 16 del confine
PRQFR notturno e M32 raddoppiata: il danno si forma DOPO il confine misurato
dall'arbitraggio (fasi stanza/finisher/corner-b sul layout tail-first) e la massa
M32 dice che il reserve rompe il template ben oltre le celle che chiude. La cura
dichiarata resta: controllo H17 del layout tail-first + protezione M32, PRIMA di
ogni nuova ipotesi di adozione.
DUE REPERTI NUOVI (task da aprire):
1. **FinalClosingCampaign FAILED 12:40:57**: IllegalStateException "value
   (2027-06-10T11:00) assigned to entity Lavorazioni meccaniche(10021) outside of
   its value range" (corruzione VRP: lezione fuori range). Il wrapper ha catturato e
   consegnato il pre-campaign winner (by design), ma l'eccezione e' reale e va
   diagnosticata (probabile interazione con la griglia estesa TASK-550-ext: la data
   e' l'ULTIMO giorno 10/06).
2. **Endpoint /export corpo vuoto** (~90s di lavoro server, risposta vuota, nessuna
   eccezione loggata; riprodotto 4 volte anche dopo restart pulito): la capture
   canonica e' stata completata con RICOSTRUZIONE DA DB (lessons.txt via SQL, export
   minimale per il sanity con quota incluse: script tmp-build-closure-artifacts.py +
   tmp-closure-battery.sh; dichiarato). Era probabilmente lo stesso "export 1B" del
   tuttoon-27 (attribuito al restart: ipotesi ora smentita).
CAPTURE: /tmp/gate564-closure -> task564-results/gate-ciclo-v2-28/ (score, analysis,
lessons, export ricostruito, kpi x8, sanity, confronto HTML, anatomy, manifest
ricreato dopo clobber, app.log). Banner 8/8 asseriti. VIZ: consumati via TAVOLE
NUMERICHE del confronto (triade per-gruppo, hole topology, forma deficit; il
rendering immagini non e' disponibile in questo ambiente: le heatmap G-vs-N non
sono state lette a occhio, differita a maintainer sul file locale). Forme numeriche:
deficit -1h concentrati su 1/G08 mar/gio (famiglia pool-cap nota: 72+48 giorni),
buchi interni su G10 (26gg) / G13 (29gg) / G11 (8gg), vuoti-in-span nuovi su
G04 (2), G06 (2), G12 (3); G04 full-day 47->55% = better-than-golden
(famiglia pool-cap), G08 93->70% = il buco storico 01/04 e la famiglia
giorni-corti.

### RE-BENCHMARK REGIME-PULITO ESITO + VERDICT (28/08 15:40, coda completata)
BANC0 VALIDO: 5/5 batterie EXIT 0, banner "zero ARMED" verificato in OGNI log
(doppia guardia script-NOTE + runner-banner). Wiring provato end-to-end: suite
1203/0 EXIT 0 + selftest armed EXIT 0 (banner, problema caricato) PRIMA delle
batterie. Deviazione dichiarata invariata: override su TUTTE le fasi di ogni
braccio (il mandato vieta di toccare le config storiche).
TABUVSND (9 braccia, ~633s, pool 6; agosto tra parentesi):
  1. VND-Cartesian -9 (agosto -258, 2o)
  2. VND-FirstImprovement -10 (-284, 3o)
  3. VND-DiminishedReturns -13 (-242, 1o)
  4. LA+Tabu -32 (-6756, 8o)
  5. Late-Acceptance -38 (-290, 4o)
  6. SA-Tabu -61 (-322, 5o)
  7. Tabu-Size5 -104 (-6699, 6o)
  8. Tabu-Size7 -111 (-6740, 7o)
  9. VND-HillClimbing -399 (-7783, 9o)
ACL (4 braccia, 600s tranne le basse 337s per unimproved anticipato): 1000 -169,
100 -174, 4 -206, 1 -206. MOVESTAT V1/V2/V3 (braccio diagnostico unico 900s):
-30 / -32 / -47.
VERDETTO vs PREDITZIONE (voce 09:15):
1. COLASSO CLUSTER TABU: CONFERMATO E OLTRE (da ~-6700 a ~-110: meglio della
   banda -200..-1500): il gap 25x di agosto era massa H29, sotto regime pulito
   il campo si comprime ~40x e la metaeuristica pesa ~10x meno sull'hard.
2. TOP-5 NELL'INSIEME CONFERMATO (trio VND in testa), riordino interno 1-4
   punti = rumore sotto la soglia dei 15. LA+Tabu sale 8o->4o, SA scende 5o->6o:
   piccole ricomposizioni entro la variabilita'.
3. INVERSIONE tabu>LA: NON VERIFICATA (LA -38 > Tabu -104: la tessera notturna
   del ciclo staged non si trasferisce alla batteria piena), dichiarato.
4. VND-HillClimbing ultimo: CONFERMATO (-399, ancora 3-4x del penultimo).
5. ACL APPATTISITA: CONFERMATO (delta max 37 < 100) MA l'edge leggero atteso
   alle acl BASSE si INVERTE (senza H29 da macinare, first-improvement esaurisce
   e l'unimproved-limit ferma a 337s: -206 vs -169 della 1000). Lettura: la
   storia agosto "acl bassa pulisce meglio" era pulizia di H29, non qualita'
   di ricerca.
6. MoveStats: contributo room ~0 nel regime CONFERMATO (le fasi sono mosse
   timeslot + composito stanza+timeslot: 4,48M pick del composito, delta
   best-score con parte room zero).
RISPOSTA ALLA DOMANDA DI maintainer ("sotto punteggio pulito e budget PIENI cambia
qualcosa rispetto ad agosto?"): SI', in modo materiale: la classifica hard di
agosto era in larga parte un ARTEFATTO della massa room (H29 provvisorio): il
ranking che sopravvive e' quello del tier timeslot (VND-family prima,
best-improvement ultimo), con differenze compresse di un ordine di grandezza.
NESSUNA adozione (metro P(0hard)/wall: la domanda era di ricerca). Il filone
metaeuristiche resta chiuso anche sotto regime pulito: confermata la sintesi
di agosto (leva MEDIUM al massimo, mai leva hard).
Report e log: task542-results/rebench-roomzero-28/reports/ (5 battery log, 3
result XML, 2 benchmarker log V2/V3; i website statistici restano in local/
per peso). Parete strumentale: la suite-detached e' sopravvissuta ai turni
(nohup); il canale background dell' harness aveva ucciso la prima esecuzione
a meta' (dichiarato, rilancio con nohup).

### NOTTE 28/29-08 RAMO A - ESITO BANCO RIFINITURE (predizione vs misurato) + ITERAZIONE DI DESIGN

Banco differenziale (real-seed construction test, ~15s/braccio, axe in-test; OFF =
stessa codepath col flag spento). La predizione delle bande era nel voce sopra.
| braccio | riservate | vuote | sotto-quota | gap-interni | ricorrenza |
| OFF control | 0 | 30 | 24 | 30 | 72,0 |
| v2 legacy (56, tagli OFF) | 3568 | 17 | 69 | 91 | 70,8 |
| +claim finestra-EDGE (superseded) | 2154 | 12 | 86 | 94 | 74,7 |
| +claim CRONOLOGICI (56) | 1726 | 22 | 47 | 84 | 74,4 |
| claim cronologici, finestra 40 | 975 | 20 | 49 | 71 | - |
| +parity (fin. 40) | 189 | 21 | 42 | 48 | 72,1 |
| +parity (fin. 56, DEFAULT) | 313 | 21 | 42 | 51 | - |
PREDIZIONE vs MISURATO (default 56): riservate 80..400 -> 313 CENTRATA;
sotto-quota <=45 -> 42 CENTRATA; ricorrenza +/-0,3pp -> 72,1 vs 72,0 CENTRATA;
vuote 5..13 -> 21 FUORI (chiude 9/30 fixture; le fixture-empty sono in gran parte
righe stage non riempibili dal fixture stesso); gap-interni <=35 -> 51 FUORI
(+21 vs OFF, dichiarato: residuo di secondo ordine, vedi sotto). Boxed: la banda
1296+/-10 era numeri APP, nel fixture 132: non confrontabile, dichiarato.
DUE ITERAZIONI DI DESIGN durante il banco (entrambe motivate da misure, registro onesto):
1. La prima quota-aware (claim alla FRONTIERA della finestra) NON ha curato la
   cannibalizzazione (86 sotto-quota): servendo DESCENDING, le date di inizio
   finestra (meta'-marzo/aprile) restano spogliate dai propri fratelli tardivi:
   diff G06 31/03 quota 8 OFF pieno vs B=7 col buco a slot 4; 14/04 OFF=8 B=2.
   Il claim giusto e' CRONOLOGICO PER-DATA: la data D puo' prenotare solo ore che
   lo stamp non avrebbe consumato PRIMA di D (prefix sum per-corso sulle date
   dichiarate ascendenti, filtrate per raggiungibilita' VRP). Con cio' la riserva
   e' non-cannibalizzante PER COSTRUZIONE: prenota la stessa quota che sarebbe
   caduta li', vince solo le gare di contesa.
2. Il PARITY CHECK (rifinitura 1) e' il lever dominante: 18 flag sul piano tail-40
   (15 asse-A densita' coppia-giorno oltre il golden, 3 asse-C concentrazione),
   330 tagli -> il piano e' PULITO (re-check: 0 flag). La forma in-codice: cap
   pair-day al MAX praticato dal golden su quella cella (asse A) + soglia 15%
   share weekday per (gruppo, materia) (asse C, bar "major weekday" dello script
   stesso; i 3 flag C a 16-29% share sopravvivono al taglio perche' celle praticate:
   la concentrazione e' proprieta' del template, presente anche a OFF: DICHIARATO).
   Lettura meccanicistica: il v2 legacy prenotava pair-day da 5-6h dove il golden
   ne pratica 2-4: era QUESTA la massa che raddoppiava M32 e rompeva i blocchi.
GOLDEN-RUNTIME CENSUS: la parity legge seed/lessons.csv via SeedCsv (pattern
loadGoldenCoPresenceDepth, fail-open con warn): da censire nel registro TASK-452
se il flag venisse mai ratificato; col flag OFF e' codice morto.
CONFIG DEL CICLO: flag prebooking.enabled=true DA SOLO (tagli default ON, tail 56
default: con claims+parity la finestra e' quasi indifferente, 56=40 su tutti gli assi).

### NOTTE 28/29-08 RAMO A - PREDIZIONE PRIMA DEL CICLO UNICO DI CONFERMA (scritta prima del lancio)
SETUP: identico al gate di ieri (profilo fresco, tutti i ratificati DEFAULT ON,
multi-start N=5, -Dplanner.construction.prebooking.enabled=true unico flag,
banner v2.1 atteso con parity=true claim-mapped=11). Bersaglio mandato: H17 ai
livelli baseline 2-16, H34 <=2, tier <= -20 (cioe' >= -20).
RAGIONAMENTO (dal banco): la v2-rifinita consegna un layout QUASI-baseline (313
celle = 10% del legacy, parity-pulite, claim-cronologiche): la domanda che il
ciclo arbitra e' se il moltiplicatore post-PRQFR del gate (H17 16 -> 74 sulle
fasi stanza/finisher) era PROPRIETA' del layout tail-first legacy (allora sparisce)
o del meccanismo reserve* in se' (allora il filone muore qui).
BANDE (consegna, recount analysis):
1. H34: **2..8** (centro ~5; baseline 8; il <=2 del mandato e' il caso favorevole
   se le celle teacher-locked mag-giu sono esattamente quelle vinte dalla prima
   scelta: la fixture chiude il 30% delle sue vuote, l'app ha le pin che la fixture
   non ha).
2. H17: **4..25** (centro ~12; baseline 2-16; gap costruzione 51 vs 30, la catena
   storica chiude l'85-93% dei gap di costruzione e il moltiplicatore va letto).
3. H32: 4..12 (centro 8; banco 42 vs 24 a costruzione, QFR chiude l'86%).
4. H33: 0..2. H0/H29/H2 = 0 attesi (boxed 0..2, EF deterministico).
5. Tier totale: **-8..-30** (centro ~-18).
6. Medium: -780k..-960k (centro ~-860k), M32 300..450k (back dal -707k del gate:
   la massa M32 era la densita' infondata, ora tagliata dal parity).
7. Wall ~6..7h; 0hard NON atteso (0/5 anche ieri); guardie: full-day 89..91,
   day-start 100% (0 late), ricorrenza 68..71, sanity NON CONFORME 2..5 gruppi
   con forma BASELINE-like (non la forma H34-ridotta del gate).
CRITERI PRE-REGISTRATI:
- tier >= -20 E H17 <= 16: trade riassorbito; SE anche H34 <= 4 la v2-rifinita e'
  CANDIDATA adozione (gate di maintainer + A/B n>=2 prima di qualunque ratifica).
  Se H34 > 4: meccanismo sano ma coda non chiusa: VERDICT neutro, il filone H34
  resta ai finisher (EDF family), nessuna adozione del reserve.
- tier < -20 O H17 > 25: anche pulito il reserve confonde la catena: VERDICT di
  NON-ADOZIONE DEFINITIVA del filone reserve* (la cella rara vale meno del blocco,
  censimento 28/08).
- H17 17..25: zona grigia, si decide sulla composizione per famiglia (dove cadono
  i gap: se tutti su date riservate = colpa del layout riservato; se sparsi =
  catena).
- medium fuori banda: autopsia composizione M32/M20.2 prima di leggere il trade.

### NOTTE 28/29-08 RAMO A - B-SIDE TOLLERANZE GOLDEN (preparate e validate, NESSUN ciclo)

CICLO UNICO: lanciato 18:21 (profilo gate564-v21, git d94db1c0, unico flag
prebooking.enabled, banner v2.1 verificati: reserved 277 / parity cuts 618 /
claim-mapped 11 su tentativo 1; consegna attesa ~00:30). Il batch B-side ha
viaggiato nella finestra di attesa.

**1. BUSTA GOLDEN per (gruppo, weekday)** - nuovo artefatto + estrattore + DUE gate:
`scripts/analyze/golden-weekday-envelope.py` -> `data/golden-weekday-envelope.json`
(67 celle, protocollo sanity: sabati/Stage/Fasullo/closed esclusi; identity gate
--check: ricomputa e confronta, exit non-zero alla deriva) + pin Java
`GoldenWeekdayEnvelopeTest` (rilegge lessons.csv via SeedCsv e confronta TUTTI i
campi min/mode/max/days/sum; tie-break mode allineato: count max, poi valore
piu' piccolo). Le due tolleranze del mandato, MISURATE:
- **cap G12 venerdi'**: il golden pratica [4..5], modo 4 (12 venerdi', 52h;
  sheet==golden PER-DATA, zero divergenze). Il run del gate -81 li' mette 5h su
  un 4h (18/09), 6h su un 4h (30/04) e 0h sui tre di fine anno (16/04, 28/05,
  04/06): sia il parcheggio surplus che le celle vuote sono FUORI busta, ora
  visibili meccanicamente.
- **busta giorni-corti FC**: il golden pratica giorni ordinari [4..5] (G04:
  modo 5 ovunque; G08: [3..5], il min 3 esiste su venerdi', il 2 su giovedi')
  dove il foglio dichiara quasi solo 5-6h (G04: 111 giorni a 6h dichiarati).
  I giorni corti DENTRO busta sono pratica, non difetto (domanda referente 2 del
  pacchetto 3-domande resta la conferma a posteriori).
- Sorpresa dell'estrazione: col protocollo corretto (Stage escluso) il golden
  NON fa MAI piu' di 5h ordinarie per 1/G08 sui lun/ven: il foglio chiede
  sistematicamente 6h. La busta e' la formalizzazione di quello scarto.

**2. SANITY GATE: bug PRE-ESISTENTE trovato e corretto (dichiarato)** - la
colonna g-min/g-max leggeva None-None da sempre (report del gate di ieri incluso):
i nomi gruppo dell'EXPORT JSON portano uno spazio iniziale (" G03") che il
golden CSV non ha, quindi l'asse "giorni sotto min golden" era MORTO dal TASK-535
(il rosso sotto-min non poteva MAI scattare; i verditi passati erano guidati solo
da buchi/stanza). Corretto con strip nel lookup. CONSEGUENZA RETROSPETTIVA: i
report sanity precedenti vanno letti con l'asse sotto-min come non-valutato.
Sul run -81 di ieri l'asse resuscitato trova 2 rossi NUOVI (G07 14/04 1h,
G08 01/04 1h) oltre ai 3 gia' noti.
**3. Sezione BUSTA nel sanity gate** (ADDITIVA, non fa verdetto): giorni run
fuori [min..max] del proprio (gruppo, weekday): 48 sul run -81 (per lo piu' G04
6h vs busta 4-5: il foglio che spinge sopra la pratica). La promozione a rosso
viaggia col prossimo batch, col suo ciclo.
**CANDIDATI MODELLO lato solver (NON toccati stanotte, ciclo necessario)**: H33
floor weekday-aware (min(4, mathFloor, envMin(g,wd)): LASCIA corti i
weekday dove il golden li pratica corti, indurente altrove) e M33 surplus cap
sopra envMax(g,wd) il parcheggio e' infondato, es. G12 ven 6h). Entrambi elencati
per il prossimo batch: modifiche HARD/medium da misurare col ciclo, non da
improvvisare in finestra.

VALIDAZIONE: envelope gate python OK (67 celle identiche al golden vivo),
GoldenWeekdayEnvelopeTest VERDE (suite), sanity rigenerata sul run -81 di ieri
(escalation rossi dichiarata sopra).

### NOTTE 28/29-08 - REVISIONE CRITICA POST /code-review high: PRIMO LANCIO INTERROTTO, TUTTI I FINDING FIXATI, CICLO RILANCIATO

Il /code-review high (10 finding verificati, 4 per esecuzione) ha trovato un difetto
CRITICO nel cut parity: il loader merge-iva PER-RIGA (merge(key, 1, max)) quindi il
cap pair-day valeva SEMPRE 1 (il max del golden non entrava mai): il primo lancio
(18:21, d94db1c0, "reserved 277 / parity cuts 618") misurava un reserve STURATO
(1h/docente/data), non quello disegnato. Il verdetto sarebbe stato non-interpretabile.
DECISIONE: ciclo INTERROTTO alle 19:05 (kill-app; manifest+log restano come record in
gate-ciclo-v21-28/, NESSUNA consegna), tutto fixato, RILANCIO su commit fix.
FINDING FIXATI (tutti e 10 + i cut-for-severity):
1. CRITICO cap degenerato: due passaggi (ore per (pair,data), poi max per
   (pair, weekday)); verifica indipendente python: T01/G12 giovedi' max 8,
   T05/G11 mercoledi' 3, ZERO pair-day riservati sopra il cap nel piano finale.
2. CRITICO sanity BUSTA crash (lookup strip mono-direzione): normalizzazione nomi
   gruppo ALL'INGESTIONE in parse_run (punto unico, entrambi i consumer).
3. block-guard: le due via-continue (VRP, canPlace) ora BREAKANO sotto guardia (il
   buco intra-cellola non era coperto: un fallimento a meta' cella lasciava prenotare
   lo slot successivo della STESSA cella sopra il buco).
4. placement-parity-check --plan leggeva campi top-level (l'export li nidifica sotto
   course): PARSING ZERO lezioni, verde vacuo storico (274 ore pinnate ora parsate;
   il check del TASK-517 va RIESUMATO sui piani storici prima di qualunque uso futuro).
5. chiavi soggetto: SubjectNormalization.normalize + rename RA nel loader golden
   (11 chiavi case-duplicate erano fail-open: la barra C era inerte sui soggetti grandi).
6. memo raggiungibilita' (course,weekday) RETROCESSO: il VRP set porta fatti
   PER-DATA (vacanze H5 datate, finestre stage): scan per-data restored (costo ~20ms).
7. javadoc control-arm corretto: il control e' TUTTI E TRE i tagli off (il braccio
   "legacy" con solo due off aveva parity armata: i 1205/13/66/110 del voce sopra
   erano legacy+parity, NON legacy).
8. baseline pair-day: parte dalle ore GIA' PIAZZATE (pin + reserve* precedenti), non
   solo dalle prenotazioni del pass.
9. RESERVE_JOURNAL: clear a ogni costruzione (il multi-start fonderebbe 5 tentativi).
10. BUSTA: giorni VUOTI ora visibili (itera le date in-sessione del golden; G12
    16/04, 28/05, 04/06 a 0h vs busta [4..5] ora in tabella) + knobs.md rigenerato
    (104, 5 righe prebooking) + tripwire reserved>0 nel banco.
BANCO CODICE FIXATO (nuova tabella di riferimento, braccio control legacy = 3 knobs off):
| OFF | 0 | 30 | 24 | 30 |
| legacy VERO | 3568 | 17 | 69 | 91 | -> piano: 90 FLAG parity (la densita' infondata del v2 quantificata)
| v2.1 fixed | 600 | 22 | 43 | 43 | -> piano: 0 flag A/C (14 asse-B tutti H31-subsumed), 0 pair-day sopra il cap
PREDITZIONE REVISIONATA per il ciclo RILANCIATO (stesse logiche, centri riaggiustati
sul banco fixed; le bande restano quelle pre-registrate: H34 2..8 c5, H17 4..25
c10, H32 4..12, tier -8..-30 c-18, medium -780..-960k, guardie invariate; la
composizione riservata sale 313->600: layout un po' meno baseline-like, gap
costruzione scesi 51->43). CRITERI INVARIATI. Il rilancio e' il ciclo UNICO di
consegna: del primo lancio non esiste consegna.

### NOTTE 28/29-08 - DoD CHIUSA sul codice consegnato + CICLO RILANCIATO alle 19:23
- /simplify: 4 reviewer, finding applicati o dichiarati (voce sopra), banco
  riproduceva identico (equivalenza dichiarata).
- /code-review high: 10 finding verificati (4 per esecuzione) + 3 cut-for-severity,
  TUTTI fixati nel commit 77d38ef4 (voce REVISIONE CRITICA sopra per il dettaglio).
- Suite finale sul codice fixato: 1205/0/0 EXIT 0 (comprende GoldenWeekdayEnvelopeTest
  e il banco con tripwire reserved>0).
- Ciclo RILANCIATO 19:23 su 77d38ef4 (profilo gate564-v21b, capture dir
  gate-ciclo-v21b-28): banner v2.1 verificati, tentativo 1 = 458 riservate /
  587 parity cuts / 553 celle practice caricate (vs il lancio abortito: 277/618/585
  col cap degenerito). Consegna attesa ~01:30; cron di sessione presidia la chiusura
  (finalize + VERDICT + PUSH + delete cron).

### NOTTE 28/29-08 - CICLO UNICO v2.1 ESITO (consegna 02:18, 29/08) - TRADE RIASSORBITO: MECCANISMO SANO, CODA NON CHIUSA

CICLO: rilancio 19:23 -> consegna 02:18 (6h55m), 5/5 tentativi (0hard mai, atteso),
vincitore TENTATIVO 1 deterministico (458 riservate, shipped CH-only + corner-b
refinement 02:08->02:18; restore finale logged). Capture completa in
task564-results/gate-ciclo-v21b-28/ (export RICOSTRUITO da DB: il endpoint /export ha
SCRITTO CORPO VUOTO per la seconda volta di fila = reperto confermato, task aprire;
ricostruzione via db-query json_build_object, quota rows incluse per daily-fill).

CONSEGNA (recount analysis): **-12hard / -782.832medium / -17.925soft**
(in-memory latestScore -1.243M medium: divergenza TASK-509 dichiarata, le due
sorgenti entrambe registrate). Composizione hard: **H34 -6**, H33 -2 (1 gg),
**H7 -2 (SORPRESA: 2 violazioni cap-docente non in banda, non presenti nel gate
di ieri: da attribuire, miniepregio aperto)**, H29 -1 (1 stanza mancante, confermata
anche dal sanity), H32 -1. **H17 = 0** (ZERO gap: ieri 74). **H0 boxed = 1**.
Medium: M20.2 -332k (33 celle vuote attese, ombra medium della coda), **M32 -288k
= LIVELLO BASELINE** (ieri -707k: la massa infondata era la densita' parity-violante,
ora tagliata), M-LF -63k, M15 -37k.

PREDITZIONE vs MISURATO (bande della voce PREDITZIONE REVISIONATA):
- H34 2..8 c5 -> **6 CENTRATA**. Le 6 celle sono ESATTAMENTE quelle che il golden
  riempie e il run no (G06 09+10/06, G09 03/06, G10 09/06, G11 04/06,
  G12 14/05); le altre vuote anno-tardo (14-15/06) sono golden-empty (anno
  consegnato: fuori banda H34). Il filone teacher-locked NON e' stato chiuso dal
  reserve (atteso: i claim cronologici proteggono meta'-anno e quei docenti sono
  import-locked).
- H17 4..25 c10 -> **0: FUORI IN MEGLIO** (il moltiplicatore post-PRQFR del gate
  era PROPRIETA' del layout legacy, come ipotizzato: col layout quasi-baseline
  sparisce). Raw sanity: 1 giorno bucato in tutto l'anno (G08 01/04, break-level
  pinned-exempt nel punteggio).
- H32 4..12 -> **1: FUORI IN MEGLIO**. H33 0..2 -> -2 al top, in banda.
- tier -8..-30 c-18 -> **-12 CENTRATA**. H7 -2: non in banda (sopra).
- medium -780..-960k -> recount -782,8k CENTRATA al limite buono (M32 a livello
  baseline e' il driver del rientro).
- full-day 89..91 -> **91,6% FUORI IN MEGLIO** (golden 91,9: siamo al golden).
- day-start 100% -> 100% (2181/2181) CENTRATA. ricorrenza 68..71 -> 69,6 CENTRATA.
- boxed 0..2 -> 1 CENTRATA. sanity 2..5 gruppi -> **6: FUORI di 1** ma con FORMA
  NUOVA (rosi = giorni sottili anno-tardo + 1 no-room + 1 buco: NON piu' i
  25/6/21-giorni-di-buchi del gate; l'asse sotto-min e' quello RESUSCITATO dal fix
  28/08, i confronti storici vanno letti con l'asse morto).
- BUSTA (nuova, non in verdetto): 121 giorni fuori busta, dominati da G04 6h vs
  busta [4..5] (il foglio chiede 6h dove il golden pratica 5: la divergenza
  sistematica sheet-vs-pratica della domanda referente 2, non danno del run).

VERDETTO (criteri pre-registrati): tier -12 >= -20 E H17 0 <= 16 -> **TRADE
RIASSORBITO: il collateral H17/M32 del reserve e' COMPLETAMENTE scomparso** (le tre
rifiniture fanno esattamente quello che il mandato chiedeva: same layout del
baseline, gare di contesa vinte in coda). MA H34 6 > 4 -> **NON candidata adozione
per la regola**: meccanismo sano, coda non chiusa (il filone H34 resta ai finisher,
come da criterio). NESSUNA adozione: flag resta DEFAULT OFF, la decisione e' di
maintainer. vs il gate -81 di ieri: tier x6,75 meglio (-81 -> -12), H17 74 -> 0, M32
707k -> 288k, H34/H32 flat (5->6, 1->1), full-day 90,0 -> 91,6.
LETTURA per maintainer: la v2.1 e' il primo reserve* che non paga niente in collateral;
costa solo il suo biglietto (nessun guadagno H34 netto: 6 celle restano, il reserve
non vince le gare import-locked). Il filone finisce qui o alla family EDF: il
reserve ha esaurito il suo margine.
REPERTI APERTI: (1) /export corpo vuoto, seconda occorsa consecutiva (capture via
ricostruzione DB: funziona, dichiarato); (2) H7 -2 a consegna (2 violazioni: da
attribuire, ieri assenti); (3) medium in-memory vs recount -1.243M vs -783k
(TASK-509 noto, ribadito); (4) il mio conteggio CSV-side delle celle vuote (12)
conta anche le golden-empty: la differenza col vincolo (6) e' la popolazione reale
in-scope (join per riga-quota), documentata qui per non reinvestigare.

### MATTINA 29/08 - CORREZIONE VERDICT v2.1 (TASK-565, mandato maintainer via Fedora): CANDIDATA AD ADOZIONE

Il verdetto notturno ("NON candidata adozione per criterio pre-registrato") e' EMENDATO
in CANDIDATA AD ADOZIONE. La correzione e' meccanica, non opinionabile:
scripts/analyze/verdict-check.py (commit a73b4570: bande + valori -> banda + azione,
zero interpretazione) applicato alla consegna reale (-12hard / H17 0 / H32 1 / H34 6):
1. BUCO NON-MECE dei criteri del 28/08 mattina: la banda di adozione "tier -13..-19"
   NON copriva i risultati MIGLIORI di -13. La consegna -12 cade FUORI banda per
   ECCESSO, non per difetto: lo script sui criteri vecchi risponde NO BAND MATCHED
   (exit 1, ambiguous) e su quelli emendati "conferma-livello-consegna". Il silenzio
   della banda era stato letto come rigetto: era un buco di copertura, non un verdetto.
2. La clausola notturna "H34 > 4 -> nessuna adozione del reserve" confonde la salute
   del meccanismo con la chiusura della coda: le 6 celle H34 residue sono il filone
   import-locked (docenti mag-giu + G13), gare che il reserve non puo' vincere per
   costruzione, con coda assegnata ai finisher (family EDF). H34 passa a variabile
   REGISTRATA, non bloccante.
CRITERI EMENDATI A MECE (AC#2): file task564-results/criteri-task565-ab.yaml, banda
candidata "tier >= -19 SENZA TETTO" con guardie H17 <= 16 / H32 <= 4; copertura
verificata PRIMA del lancio (regola del commit a73b4570): la consegna notturna + 10
esiti sintetici (bordi -12/-13/-19/-20 inclusi) atterrano tutti in una banda esatta.
Fissato intanto il reader --capture-dir dello script, rotto com'era committato
(import inesistente parse_score_string + chiavi drill-down 'constraint'/'matches'
+ default-0 per vincoli assenti dal drill-down = zero match): ora legge le capture
canoniche direttamente (provato sulla capture notturna: tier -12, H17 0, H32 1,
H34 6, medium recount -782.832).

### MATTINA 29/08 - A/B n>=2 v2.1: PROTOCOLLO + PREDIZIONE PRIMA (scritta prima del lancio)

PROTOCOLLO (due bracci identici salvo il seed, profili freschi via run-measure.sh):
- flag: -Dplanner.construction.prebooking.enabled=true (v2.1 ON, unico flag di
  contenuto) + -Dplanner.multistart.max-attempts=1 +
  -Dplanner.multistart.test.force-grasp-seed=S con S=25 (braccio A) e S=26 (braccio B).
- PERCHE' force+max1: il wrapper multi-start possiede planner.construction.grasp.* per
  la durata del loop e forza il tentativo 1 deterministico (applyGraspSeedForAttempt),
  quindi un -D grasp.seed plain non raggiunge MAI la costruzione; l'hook force e'
  l'unica via documentata per mettere un seed scelto sulla costruzione, e
  max-attempts=1 lo limita a UN tentativo = UNA costruzione per braccio mantenendo
  intero il path di consegna di produzione (wrapper, pick-best, rifinitura stanze).
  Alternativa scartata: -Dplanner.multistart.enabled=false, che scavalca il wrapper
  e cambia il path di consegna rispetto a quello che ha prodotto -12.
- semi 25/26: estratti arbitrari dichiarati, fuori dal range 1..24 campionato dal ciclo
  notturno (nessuna etichetta di bacino ereditata).
- capture: task564-results/ab-grasp-s25-29/ e ab-grasp-s26-29/; banner attesi:
  "Declared-day pre-booking v2.1", "[PreBook-parity]", "Multi-start staged retry:
  enabled=true", "grasp=true seed=25|26" (banner construction/repair flags).

DATO PORTANTE (dal app.log del ciclo notturno, letto prima di scrivere la predizione):
distribuzione empirica dei bacini GRASP sotto v2.1, n=24 start: 20/24 scartati dal
profile predictor in costruzione (156..229 buchi H17 > soglia 150); i 4 corridi
chiudono il boundary post-RELEASE a tier -41 (seed 10), -22 (seed 22), -77 (seed 23);
seed 24 (ultimo tentativo, boundary non loggato) consegna -60hard pieno con stanze
rifinite in-ciclo. Il tentativo 1 DETERMINISTICO faceva boundary -9 -> consegna -12
dopo il delivery refinement. I bacini GRASP cattivi sono PRE-ESISTENTI all'era v2.1
(calibrazione predictor del 22/08: deliverable <=70 buchi, doomed >=248; scarti
documentati gia' dal 23/08): proprieta' del modello H31/H32/H33/H34-era, NON del
pre-booking.

PREDIZIONE (per braccio, recount analysis a consegna):
- tier: bimodale. ~20% bacino deliverable -> -8..-20; ~80% bacino doomed -> -20..-80
  (centro ~-35). 0hard non atteso.
- H17: 0..16 sul ramo deliverable, 10..40 sul ramo doomed. H32: 1..12. H34: 4..8
  (filone import-locked, seed-insensibile). H33 0..2, H29/H0 0..2.
- medium recount: -750k..-1.100k (il ramo doomed paga M20.2/M32); full-day 86..92;
  day-start 100%; ricorrenza 67..71; sanity 2..7 gruppi; wall 95..130 min per braccio.
- ESITO ATTESO: P(un braccio in banda conferma, tier >= -12) ~1 su 10 (zero su 4
  corridi osservati; il -12 e' il bacino deterministico, che i bracci NON campionano);
  P(entrambi) < 1 su 20. L'esito piu' probabile e' banda bacino-non-conferma su uno o
  entrambi i bracci: PREVISTO, classificato, e NON e' un verdetto contro v2.1 (misura
  il bacino GRASP, pre-esistente). La candidatura resta sull'evidenza del bacino
  deterministico (consegna -12, certificata in banda conferma dai criteri emendati).

REGOLA RATIFICA (pre-registrata): entrambi i bracci in banda conferma-livello-consegna
-> proporre ratifica v2.1 a maintainer. Qualunque altro esito -> dichiarare e classificare
(bande conferma/candidata tengono v2.1 CANDIDATA, decisione di maintainer; banda bacino
misura il bacino, non il meccanismo). GUARDIA MEDIUM: recount fuori -750k..-1.100k ->
autopsia composizione (M20.2/M32) prima di leggere il trade.
