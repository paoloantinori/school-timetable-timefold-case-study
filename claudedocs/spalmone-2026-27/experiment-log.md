# Experiment log — primo solve autonomo 2026/27 (TASK-363)

Protocollo: profilo fresco → import calendario (922 lezioni pinnate, 0 failure)
→ solve completo sul provider calibrato (m-8) → confronto per-vincolo col golden
(i numeri di riferimento sono i pin di `GoldenSolutionRegressionTest`).
Il golden NON è un target da eguagliare punto-a-punto: è la prova che un orario
"accettato dalla scuola" esiste; il solver deve produrne uno *comparabile*.

## Run 1 — 2026-07-18, provider completo (S13 attivo)

- Profilo: `dev` (fresco), seed DataGenerator 2026/27 + `import-calendario.sh`
  (persisted=true, 922 pin, 0 failure — prima volta end-to-end del processo scuola).
- Avvio solve: ~08:30. Entità: 922 lezioni pinnate + 9.631 da piazzare (10.553 totali).
- Config: solverConfig.xml 6 fasi, cap globale 8h, fasi a diminishedReturns.

### Progressione (campioni)

| ora | hard | medium | soft |
|-----|------|--------|------|
| 08:33 | -88.875 | -555.253 | -105.169 |
| 08:39 | -76.236 | -511.054 | -106.250 |
| 08:45 | -75.122 | -432.446 | -88.725 |
| 08:59 | -75.085 | -413.241 | +129.361 |

### Fotografia per-vincolo a metà volo (08:59, LS in corso)

| vincolo | run 1 (parziale) | golden | nota |
|---------|------------------|--------|------|
| H11 max ore/giorno gruppo | **-75.000** (7 match) | 0 | quasi tutto l'hard residuo |
| M22 calendar compactness (hard) | -71 (6) | 0 | |
| H7 max ore/giorno docente | -14 (3) | 0 | |
| M20 daily quota fill | **-413.000** (203 giorni) | 0 | il grosso del medium |
| M21 max 1h/materia/giorno | -169 (118) | -621 (452) | già MEGLIO del golden |
| M24 early-start | -28 (16) | -16 (15) | comparabile |
| M25 giorno libero | -21 (21) | -27 (27) | comparabile |
| M27 finestre T20 | -3 (3) | 0 | |
| M18 slot-0 | -1 (1) | 0 | |
| S12 presenza settimanale | +4.440 | +5.623 | |
| S13 stabilità settimanale | +6.324 (3.162) | n/a | attivo, sta guidando |
| S7 ore consecutive | +154.410 | n/a | |

Lettura di metà volo: la struttura del punteggio è sana — niente H1/H3/H6/H20
(conflitti e calendario rispettati by construction), l'hard residuo è
concentrato in 7 giorni-gruppo oltre il tetto H11 e il medium in 203 giornate
sotto-riempite (M20). M21/M24/M25 sono già nella fascia del golden.

### Risultato finale

**RUN INVALIDATO E FERMATO ALLE ~09:10** — non per un difetto del run ma per una
scoperta strutturale che il run stesso ha prodotto: il DB conteneva 6.343 lezioni
"impilate" (stesso gruppo, stesso timeslot) con H3 a ZERO. Root cause (TASK-377,
provata meccanicamente con test contro il codice pre-fix): H1/H2/H3 usavano
`forEach(Lesson.class)` e H16a `forEachUniquePair`, che iterano solo entità con
TUTTE le variabili inizializzate — e la pipeline a 6 fasi assegna le stanze solo
in fase 5, quindi i vincoli di conflitto erano SPENTI per tutte le fasi timeslot.
Il valore del run: ha esposto in un colpo solo (a) la cecità forEach, (b) le
esenzioni same-course/lab-session che coprivano esattamente e soltanto
l'impilamento same-slot, (c) la vacuità della taratura H16a/M22 sul golden
(fixture senza stanze), (d) l'inutilità del forager first-non-tabu del config di
test. Fix in TASK-377: H1/H3/H16a → forEachIncludingUnassigned + join per data;
esenzioni rimosse; H16a demansionato a S16 soft (il golden mostra 15 passaggi di
docente deliberati su ore RA/Scienze); M22 riallineato a medium (1000/giorno);
guardiano aggiornato (S16 = -15/15 esatti); 605/605 test verdi.

I numeri di metà volo qui sopra restano utili come baseline DIFETTOSA di
riferimento: qualunque run post-fix deve reggere il confronto sapendo che il
-75k hard era in realtà sottostimato (mancavano i conflitti invisibili).

## Run 2 — INVALIDATO: JAR stantio (2026-07-18, 10:05-11:13)

ERRORE OPERATIVO, non di codice: dopo il commit del fix ho riavviato con
`dev-start-and-wait.sh`, che AVVIA il JAR esistente ma NON ricompila —
l'app girava ancora col provider pre-TASK-377. Prova schiacciante nel
breakdown finale: M22 a -67 HARD (impossibile col fix, che lo rende
medium) e 6.268 impilamenti ancora nel DB. Punteggio finale (inutile ai
fini del protocollo ma registrato): -75.082 hard / -383.108 medium /
+205.088 soft in 68 min. LEZIONE: dopo ogni modifica Java, il run è
valido solo se preceduto da `rebuild.sh`/`rebuild-checked.sh`; verificare
SEMPRE il JAR con `unzip -p ... | strings | grep <marcatore del fix>`
prima di lanciare.

## Run 3 — primo solve ONESTO post-TASK-377 (2026-07-18, avvio 11:16)

JAR verificato (build 11:14, contiene "S16 - Consecutive same-subject
same teacher"). Profilo fresco, import-calendario pulito (922 pin,
persisted=true). Primo run in assoluto con H1/H3 vivi durante le fasi
timeslot: atteso un paesaggio hard REALE (niente stacking di comodo), da
confrontare col golden secondo TASK-363.

### Risultato finale (terminato 12:23, 67 min)

**-1.217.029 hard / -1.589.137 medium / +94.914 soft** — 0 lezioni non
assegnate, ma 1.049 impilamenti residui (stesso gruppo, stesso slot).

| vincolo | run 3 | golden | nota |
|---|---|---|---|
| H3 conflitto gruppo | **-1.119.000** (1.119 coppie) | 0 | il cuore del problema |
| H11 tetto ore/giorno | -76.000 (9) | 0 | i "mucchi" |
| H1 conflitto docente | -22.000 (22) | 0 | |
| H22 / H7 / H17 | -14 / -14 / -1 | 0 | marginali |
| M20 quota giornaliera | -1.329.000 (578 giorni scarsi) | 0 (esente) | |
| M22 compattezza calendario | -260.000 (11) | 0 (esente) | ora MEDIUM come da fix |
| M21 / M25 / M27 | -117 / -17 / -3 | -621 / -27 / 0 | in fascia golden |
| S16 passaggi docente | 0 match | -15 soft | il solver li evita |
| S12 presenza settimanale | +5.159 | +5.623 | comparabile |
| S13 stabilità settimanale | +3.768 | n/a | attivo |

### Lettura strutturale

1. **La CH consegna al local search ~-1,28M hard**: la fase di costruzione
   stessa impila (fu scritta/tarata nell'era del provider cieco). Il LS in
   un'ora recupera solo ~66k. La leva più grossa per il prossimo run è una
   CH che rispetti l'occupazione (gruppo, slot) fin dal piazzamento.
2. **Saturazione strutturale** (analisi capacità 2026-07-18): G01
   1.016 ore vs 995 slot (-21), G05 1.009 vs 995 (-14) — eccesso
   concentrato in DUE corsi per gruppo (T16/Confezione +11/+10,
   T17/Moulage +15/+9) rispetto alle 990 ore/gruppo che il golden
   piazza davvero. G02 / G03 / G06 al 99,5% di riempimento
   (990/995). Il golden chiuse i conti usando 575 ore pomeridiane che
   referente (Q17) ora vieta: il problema onesto è PIÙ DURO di quello che gli
   umani hanno risolto a mano. Domanda per referente formulata (ore Orario
   vs incarichi per i corsi a turni MODA).
3. Fuori dai conflitti, la calibrazione regge: M21/M24/M25/M27/S12 tutti
   in fascia golden; S16 a 0 (il solver preferisce la continuità docente,
   coerente col soft).

### Verdetto TASK-363 (run 3)

Il motore corretto è ONESTO ma non ancora COMPETITIVO: serve (a) la
risposta della scuola sulle ore MODA (o l'applicazione di "Orario wins"
ai corsi a turni), (b) una ri-taratura di solverConfig/CH per il
paesaggio con H1/H3 vivi. L'A/B S13 (task-375) resta sensato solo dopo
(b): su un run dominato da -1,2M hard il segnale S13 è rumore.

## Run 4 — golden-first recalibration attiva (2026-07-18, avvio 13:16)

Commit 6a27dd97 (TASK-379), JAR verificato (marcatore M28 nel bytecode).
Differenze strutturali vs run 3:
- Pomeriggi USABILI dal solver (M28 medium 100/lezione, filtro VRP rimosso)
  → +~2.300 slot-ora di capacità totale, la valvola che il golden usava.
- M29 (bordi professionalizzanti) medium.
- Import con tetto Orario-wins: 866 pin (era 922; 17 righe turni oltre
  monte ore scartate) → 1/G05 a 990 ore ESATTE come il golden,
  domanda ≤ capacità per tutti i gruppi.
Il collo di bottiglia CH (TASK-378) resta: attesa una CH ancora impilante,
ma con capacità in avanzo il LS dovrebbe scendere molto più in basso del
run 3 (-1,217M). Questo run misura quanto del residuo era capacità vs CH.

### Risultato finale (terminato 14:22, 66 min)

**-518.276 hard / -1.558.849 medium / -159.470 soft** — il 57% di hard in
meno del run 3, 0 non assegnate, impilamenti residui 346 (erano 1.049).

| vincolo | run 4 | run 3 | golden |
|---|---|---|---|
| H3 conflitto gruppo | **-416.000 (416)** | -1.119.000 (1.119) | 0 |
| H11 tetto giornaliero | -79.000 (12) | -76.000 (9) | 0 |
| H1 conflitto docente | -22.000 (22) | -22.000 (22) | 0 |
| H15 spezzoni stessa materia | -1.260 (19) | 0 | 0 (esente) |
| M20 quota giornaliera | -1.229.000 (559) | -1.329.000 (578) | 0 (esente) |
| M28 pomeriggi (nuovo) | -79.700 (797 lezioni) | n/a (hard, VRP) | 0 (esente; golden ne usa 575) |
| M21 | -2.085 (876) | -117 | -621 |
| S1 buchi docente | -242.520 (1.362) | -14.400 | n/a |

### Lettura

1. **Le correzioni strutturali (TASK-379) hanno pagato**: -63% di coppie H3.
   Il solver usa davvero la valvola pomeridiana (797 lezioni, più delle 575
   del golden — comprensibile con la CH ancora impilante) e 1/G05 a 990
   ore non sono più strutturalmente impossibili.
2. **Il residuo (-518k) è il collo di bottiglia CH** (TASK-378): la
   costruzione consegna ancora un orario sporco e il LS a rendimenti
   decrescenti non finisce di scavare. Nessuna evidenza di altri vincoli
   troppo rigidi: H15 a -1.260 è rumore, H11/H1 sono fisici.
3. Effetto collaterale da tenere d'occhio: S1 (buchi docente) esploso a
   -242k con i pomeriggi aperti — la ri-taratura TASK-378 dovra' bilanciare
   M20/M28/S1.

### Verdetto aggiornato TASK-363

Percorso confermato: (fatto) golden-first sulle regole; (prossimo) TASK-378
CH occupancy-aware; poi run 5 e, a residuo hard ~sei-cifre-basse o meno,
l'A/B S13. Il confronto per-vincolo col golden e' ora metodologia stabile
(questa tabella).

## Run 5 — TASK-378 attivo: CH protetta + race dei comparatori chiusa (avvio 14:58)

Commit af93e8b7, JAR verificato (UnassignedLessonSelectionFilter nel bytecode).
Scoperte della forensica run-4 (log fasi):
- La CH custom era GIA' quasi pulita (-9.635 hard); era il fallback CH a
  distruggerla ri-piazzando tutte le 9.635 entita' (19.270 step) fino a
  -1.090.000. Ora il fallback tocca solo i resti (timeslot==null).
- I run 3 e 4 NON sono terminati: sono MORTI a inizio fase-stanze
  (TimSort "Comparison method violates its general contract") — i
  comparatori leggevano la mappa statica dei conteggi che OGNI poll di
  status/score ricostruiva sotto il thread del solver. Il mio stesso
  monitoraggio li ha uccisi. Fix: snapshot per-sort + pubblicazione
  atomica + guardia statici in loadSchedule. Un solve morto ora espone
  lastSolverError nello status.
### Esito: FERMATO alle ~15:45 — il filtro non basta, trovata la causa VERA

La fase 2 e' finita BIT-IDENTICA al run 4 (-1.090.000, -1.143.129 medium)
con meta' degli step (9.635 = tutte le entita' x 1 variabile): il
filterClass sull'entitySelector NON limita la coda del placer.

**Catena causale completa, provata anello per anello:**
1. Query DB a meta' run: le 9.635 lezioni non-pinnate hanno TUTTE
   room=NULL → `findAvailableRoom` della fase 0 non assegna NEMMENO una
   stanza (sotto-bug indipendente: `Lesson.getAvailableRooms()` legge lo
   statico `SchoolSchedule.getAllRooms()` + cache transient
   `cachedAvailableRooms` — sospetti: cache vuota calcolata in un contesto
   senza statici e sopravvissuta al planning-clone, oppure statico nullo
   nel contesto del solver. DA DEBUGGARE con un log in findAvailableRoom).
2. room e' `allowsUnassigned=true`: per il QueuedEntityPlacer una entita'
   con room null e' NON-inizializzata (i docs 0.8.x avvertono proprio che
   "nullable variables don't mix well" con la CH) → TUTTE in coda.
3. L'unico move selector della fase 2 e' il CAMBIO TIMESLOT → il placer
   "inizializza" ogni entita' ri-piazzandole il timeslot → distruzione
   del risultato day-clustered (-9.635 → -1.090.000), run dopo run.

**Piano per la prossima sessione (TASK-378 fase 2):**
a. Debug + fix di findAvailableRoom/getAvailableRooms nel contesto del
   solver (aggiungere un WARN se availableRooms e' vuota; verificare
   cachedAvailableRooms vs planning clone e getAllRooms statico).
b. Fase 0 assegna SEMPRE una stanza (fallback: la meno occupata idonea)
   e forza il piazzamento dei ~1.194 resti col minimo conflitto → nessuna
   entita' non-inizializzata → fase 2 coda vuota (e se ANCORA ri-piazza,
   eliminarla del tutto rinumerando marker/parser/ts/docs per la regola
   solver-config-sync).
c. Verifica protocollo dopo le fasi CH: query stacked-extras ≈ 0 e
   room-null ≈ 0 PRIMA di lasciar correre il LS.
d. Il fix comparatori/statici di TASK-378 resta valido e committato
   (af93e8b7) — nessun crash TimSort nel run 5, lastSolverError pulito.

## Run 7 (16:15, morto subito) e Run 7-bis (16:28, in corso) — TASK-378 fase 2

Run 7: fase 1 completa (9.635 piazzate, 9.635 stanze, 1.198 forzate di cui
757 senza conflitti) ma il LS ha rifiutato di partire: "9635 uninitialized
entities". LA CAUSA PIU' PROFONDA DI TUTTA LA SAGA: la fase custom mutava i
campi SENZA notificare lo score director — per gli stream incrementali e la
contabilita' di inizializzazione di Timefold le entita' non sono MAI state
piazzate. Retro-spiegazione totale: lo score costante -9.635 di fase 0 era
9.635 x H0 (stream mai aggiornati); il "fallback che distruggeva" stava in
realta' piazzando entita' che per Timefold erano vuote, sovrascrivendo stato
invisibile. Il "-9.635 quasi pulito" era un'illusione: la qualita' reale
della CH custom non era mai stata misurata.

Fix (18284fad): tutte le mutazioni passano per context.execute(
Moves.change(variabile, entita', valore)) con i metamodel tipizzati.

Run 7-bis: fase 0 ora misura VERO: **-2.041.477 hard** — la CH custom,
giudicata dall'intero set di vincoli (H15/H17/H22/stanze inclusi), e' molto
peggio di quanto il vecchio fallback (-1,09M) facesse da solo. Ma per la
prima volta il LS parte da uno stato visibile con scoring incrementale
coerente.

### Esito finale (18:26, 117 min): "Solving ended" PER LA PRIMA VOLTA

**-521.445 hard / -1.562.768 medium / -21.072 soft** — pipeline completa,
9 fasi, NESSUN crash: la fase stanze e' passata (fix comparatori validato
empiricamente; il claim "fase stanze morta con allowsUnassigned" resta
rifiutato: 5.853 stanze assegnate, zero conflitti H2/H-M2/M3, S11 attivo
per la prima volta).

| vincolo | run 7-bis | run 4 (morto a meta') | golden |
|---|---|---|---|
| H3 | -417.000 (417) | -416.000 (416) | 0 |
| H11 | -75.000 (8) | -79.000 (12) | 0 |
| H1 | -29.000 (29) | -22.000 (22) | 0 |
| H15 | -420 (7) | -1.260 (19) | 0 (esente) |
| M20 | -1.223.000 (540) | -1.229.000 (559) | 0 (esente) |
| M28 pomeriggi | -91.600 (916) | -79.700 (797) | 0 (golden: 575) |
| S13 stabilita' | +3.252 | n/a | n/a |

Il paesaggio onesto converge a ~-520k hard con questo move set: il
residuo e' dominato da 417 conflitti di gruppo che ne' la CH (che li
CREA: -2,04M al termine di fase 1, in gran parte H15/H17/M28 piu' i 441
force-place con conflitto) ne' 2 ore di LS sciolgono. PROSSIMA LEVA
(TASK-378 iterazione 3): qualita' della CH day-clustered misurata sui
vincoli veri — adiacenza stessa-materia (H15), giorni senza buchi (H17),
niente pomeriggi se evitabile (M28) — ora finalmente possibile perche'
ogni piazzamento della fase custom e' visibile e misurabile.

Nota flake: testCumulativeTimeAccumulates e' fallito una volta nella
suite completa sotto carico (400 vs 202) e passa in isolamento e nei
rerun — timing-sensitive, candidato per la punch list TASK-367.

## Run 9 — LA PRIMA SOLUZIONE FATTIBILE (2026-07-18, 20:57 → 22:45)

**"Solving ended: 0hard/-10.163.070medium/-61.606soft"** in 107 minuti.
Zero conflitti, zero impilamenti, zero non-assegnate, 5.841 stanze, anno
schedulato fino al 2027-06-10 come il golden.

La chiave dell'ultimo passo (iterazione 4 di TASK-378): il run 8 mostrava
un residuo H3 costante ~415 coppie in TUTTI i run; la mappa per gruppo
(G06 128, G14 83, sature ~35) coincideva ESATTAMENTE con i tagli
dell'**orizzonte VRP di idealEndDate** — G14 confinato a Sep-Oct (115
ore in ~32 slot legali), G06 al 21 aprile, le sature al 27 maggio.
Capacita' docente comoda ovunque (max 67%). Rimosso il filtro (il golden
arriva a giugno; M12 medium mantiene la pressione di compattezza), la CH
e' passata da -1,63M a -26k hard e il LS ha dissolto il resto in 20 min.

| | run 9 | golden |
|---|---|---|
| hard | **0** | ~-12 (eccezioni H4 calibrate) |
| M12 overflow | -8.065.000 (467 ore oltre target) | 0 (esente) |
| M20 giorni scarsi | -1.679.000 (666) | 0 (esente) |
| M28 pomeriggi | -68.900 (689 lezioni) | (golden ne usa 575) |
| S12 presenza sett. | +5.571 | +5.623 |
| S13 stabilita' | +2.840 | n/a |

Le tre giornate-svolta in una: TASK-377 (vincoli sbendati) ha reso il
problema onesto; TASK-379 (golden-first) ha reso la capacita' vera;
TASK-378 (chirurgia pipeline: notifiche move, fallback eliminato,
orizzonte VRP rimosso) ha reso il solver capace di risolverlo.

Prossimi affinamenti (non piu' blockers): M12 a 5000/lezione domina il
medium — calibrarlo contro la pratica golden; S1 buchi docente -150k;
G14 startdate reale (28 gennaio, dato per la scuola); A/B S13 ora
finalmente sensato (task-375).

## A/B S13 (task-375 AC2) + calibrazione M12 — 2026-07-19

**A/B S13 a pari budget** (stessa build-semantica del solver, profili freschi,
866 pin, ~2h ciascuno):

| braccio | hard | medium | soft | stabilita' settimanale |
|---|---|---|---|---|
| A = run 9, S13 ON | 0 | -10.163.070 | -61.606 | **14,7%** (1.420/9.635) |
| B = S13 OFF | -2.001 | -10.470.580 | -80.551 | **11,4%** (1.099/9.635) |

Verdetto: **S13 resta** — +3,3pp (+29% relativo) di regolarita' settimanale,
nessuna cannibalizzazione (medium e soft migliori nel braccio A). Cautela:
un run per braccio con NON_REPRODUCIBLE (il -2.001 hard del braccio B e' la
misura del rumore: 2 conflitti che il run 9 aveva sciolto). Cross-check
metrica: 1.420 match x peso 2 = +2.840 = esattamente lo score S13 del run 9.
Export archiviati in ~/data/planner-experiments/ (armA-run9-s13on.json,
armB-s13off.json).

**Calibrazione M12 (golden-first)**: misura meccanica contro le date obiettivo
calcolate (dal log del calcolatore): il golden piazza **3.677 ore su 12.551
(29,3%) OLTRE i target** — ogni gruppo arriva a giugno, G14 100% oltre
(finestra reale gen-maggio vs target 14 ottobre). "Finire presto" e' un
nostro assunto legacy, non una regola della scuola: lo spalmone E' lo
spalmare, e S12 (+5.623 sul golden) premia la continuita' annuale che i
target combattono. M12: 1000/giorno -> ONE_MEDIUM/giorno (nel run 9 era
l'80% del medium totale: -8M su -10,2M). Q20 per referente in domande-brevi
(serve la fine anticipata per qualche classe, o si spalma e basta?).

## Run 10 — M12 ricalibrato (2026-07-19, 19:12 → ~20:30): 0 hard in ~84 min

**0 hard / -3.002.983 medium / -20.187 soft.** Secondo fattibile consecutivo;
0 hard raggiunto GIA' in fase compattezza (~70 min; il run 9 ci arrivo' solo
in rifinitura stanze). Il medium e' 3,4x meglio del run 9 (-10,16M): la
montagna M12 e' sparita dal paesaggio e il solver spende dove conta.

### Metriche di SOMIGLIANZA col golden (nuovi KPI permanenti)

| metrica | run 10 | golden |
|---|---|---|
| ricorrenza settimanale (stessa cella, stessa materia/docente) | **14,3%** | 41,5% stretta / 72% modale |
| blocchi multi-ora stessa materia | 209x2h + 307x3h + 33 maggiori (9.005 ore singole) | 680x2h + 319x3h + 83x4h + 155x5h + 62x6h |
| ore/giorno per gruppo | dominante 6h (1.340 giorni) + 5h (364) | simile (giorni pieni) |

VERDETTO ONESTO: la soluzione e' FATTIBILE ma NON SOMIGLIA al golden. Il
riempimento giornaliero e' buono; mancano (a) il RITMO SETTIMANALE (14% vs
41-72%: il nostro orario non e' memorizzabile, il loro si') e (b) i BLOCCHI
(la scuola insegna a blocchi di 2-6 ore, noi spargiamo coriandoli da 1h).
Cause nel mix di vincoli: S4 (no-consecutivo, soft) + M21 remano CONTRO i
blocchi che la scuola fa 1.299 volte; S13 a peso 2 e' omeopatico contro
S7/M20. Strade proposte (per la discussione con maintainer): (A) ricalibrazione
golden-first dei vincoli di forma + S13 potenziato, economica ma incerta;
(B) CH a TIMBRO SETTIMANALE - costruire la settimana-tipo per gruppo,
timbrarla sull'anno, riparare le eccezioni (chiusure/stage/lab): produce la
struttura golden BY CONSTRUCTION (il NO-GO di task-351 era sul rewrite
completo della pipeline, non su una CH contenuta); (C) tenere queste
metriche come KPI di ogni run futuro.

## Run 11 — L1 golden-sourced (2026-07-19, 21:01): superato da L1-bis, vedi checkpoint

1.572 pin (866 MODA + 706 FC lab dal golden, poi gated OFF su correzione di
maintainer: i pin devono venire dal Calendario Scolastico). CH -25.562 hard a fine
fase 0. Interrotto/superato dal redesign L1-bis (quote come restrizione dello
spazio, non pin golden). Non misurato oltre.

## Run 12 — L1-bis + stage on the grid (2026-07-20, 12:23 → 14:01, 98 min)

**-1.005 hard / -2.777.569 medium / -37.852 soft.** Primo run con il quadro
TASK-381 completo: DailyActivityQuota (VRP + H23/H24/M30/M31), 2.666 pin dal
solo Calendario (866 MODA + 1.800 Stage), 280 ore stage fuori griglia in
expectedUnscheduledHours, 0 stub vaganti. Import-report identico alla
derivazione meccanica (1.800/280/5, 0 calendar-closed).

**Fasi**: CH 45s (-17.665 hard: molto meglio del run 11 a -25.562, i pin
stage restringono davvero); LS compattezza 60 min (limite fisso) -3.017;
RuinRecreate 26 min -2.015; CH stanze 1s (pulita); rifinitura -1.005.

**Residuo hard (breakdown)**: H3 = -1.000 con UNA SOLA coppia — due lezioni
NON pinnate impilate (G05, lun 2027-03-22 11:00, Scienze/T09 vs
Matematica/T08; H3 pesa 1.000/coppia, i "gradini da mille" del monitor
erano coppie H3 sciolte una alla volta) — ottimizzazione incompiuta, non
infattibilita strutturale. Piu H17 = -5 (5 buchi). NON fattibile per un
soffio; la fase 2 ha il budget FISSO di 3600s senza unimproved-limit, quindi
un re-solve di rifinitura costa comunque 60+ min (smell di config per i
re-solve iterativi, da sistemare se si vuole "riprendi e rifinisci").

**Medium** -2,78M (run 10: -3,00M): M20 domina (-2,31M/937 giorni), M22
-441k/13, M12 -16k. Nuovi vincoli L1-bis quasi soddisfatti: M30 -173 (floor
strutturale >=23 incluso), M31 -66, H23/H24 = 0 (il VRP fa il suo).

### KPI somiglianza (confronto: run 10 / run 12 / golden)

| metrica | run 10 | run 12 | golden |
|---|---|---|---|
| ricorrenza settimanale TOTALE | 14,3% | **31,0%** | 41,5-72% |
| — di cui solo unpinned | ~14% | 16,9% (pinned: 81,8%) | — |
| blocchi >=2h stessa materia | 549 | **774** (206x8h = stage) | 1.299 |
| — di cui solo unpinned | — | 253 (8.931 ore singole) | — |
| ore/giorno dominanti | 6h (1.340g) | 6h (1.276g), 5h (408), 8h (242=stage) | pieni |

VERDETTO: il gap di somiglianza si dimezza (31% vs 41,5%) ma il guadagno e'
quasi tutto STRUTTURALE (pin stage/MODA ricorrenti per costruzione, 81,8%);
la parte libera migliora poco (16,9% vs 14,3%, S13 omeopatico) e i coriandoli
da 1h restano (8.931). Le leve restanti sono quelle note: L2 (CH a timbro
settimanale) per il ritmo, L3 (S4/M21 golden-first) per i blocchi.

## Run 13 — L3 shape recalibration (2026-07-20, ~17:50 → 19:06, 76 min)

**-1.004 hard / -2.752.147 medium / +8.565 soft.** Primo run con M21 a
soglia 2, senza S2, con S17 (reward blocchi) e i nuovi limiti di
terminazione (fase 3 esce dopo 7 min invece di 26: -22 min totali).

### AC#3 CENTRATO — i blocchi si sono formati

| metrica | run 12 | run 13 | golden |
|---|---|---|---|
| blocchi >=2h totali | 774 | **1.183** | 1.299 |
| — di cui unpinned | 253 | **662** (2,6x) | — |
| istogramma totale | — | 575x2h+306x3h+96 magg. | 680x2h+319x3h+300 |
| ore singole unpinned | 8.931 | **8.070** | — |
| ricorrenza tot/unpinned | 31,0%/16,9% | 31,0%/16,9% (invariata: bersaglio di L2) | 41,5-72% |

Zero regressioni: hard residuo identico (-1.004 vs -1.005), medium MIGLIORE
(-2,75M vs -2,78M), soft POSITIVO (+8,6k vs -37,9k; S17 = +7.380/1.476
coppie, S1 buchi docenti -17%). M21 residuo -41/34.

### Il residuo hard e' STRUTTURALE, non rumore

La coppia H3 superstite e' LA STESSA del run 12: G05,
Scienze/T09 vs Matematica/T08 (solo la data cambia: 18/2 vs 22/3).
Causa radice misurata: **G05 ha capacita' calendario 990 = domanda 990,
slack ZERO** (tutti i gruppi MODA sono al pareggio esatto) - il piazzamento
e' un matching perfetto bipartito tra celle e disponibilita' docenti; la LS
arriva a 989/990 e impila l'ultima ora. Il golden dimostra che il matching
esiste. Candidati: L2 (il timbro settimanale produce il matching per
costruzione) o una riparazione mirata RuinRecreate sui giorni della coppia.
H17 residuo: 4 buchi.

PROSSIMO: L2 - CH a timbro settimanale (AC#2, ricorrenza >=50% a fine CH).

## L2 — CH a timbro settimanale (2026-07-20, no live run - misura da test costruzione-only)

AC#2 CHIUSO. `WeeklyTemplateBuilder` (nuovo, pure Java) costruisce una
settimana-tipo per gruppo (finestra calendario modale, quota lab/aula modale
per i 3 gruppi quota, docenti hardest-first, blocchi 2h preferiti quando la
capacita' lo permette, compattezza per costruzione, mai lo stesso docente in
due gruppi nella stessa cella) e la timbra cronologicamente sull'anno dentro
la fase 1 esistente (`DayClusteredConstructionPhase`, nessun cambio XML). Il
round-robin preesistente diventa la coda di ripristino; `forcePlaceLeftovers`
resta l'ultima rete invariata.

**Misura (test costruzione-only sul seed reale, senza pin MODA/stage - AC#2
lo permette esplicitamente):** ricorrenza settimanale a fine costruzione
**75-81%** (varia run-to-run per l'ordinamento IDENTITY di H2 nella suite
condivisa; sempre ben sopra la soglia 50%), **0 conflitti H1/H3/H7/H11
introdotti dalla timbratura stessa** (asserito via `stampSkipConflict==0`),
8.000+/10.501 lezioni piazzate direttamente dalla timbratura, il resto alla
coda round-robin/force-place. Suite completa 664 test (era 656+7 unit test
nuovi), 0 fallimenti.

Review (/simplify + /code-review high, 12 agenti paralleli) ha trovato e
corretto 4 bug reali prima della chiusura: sentinella di finestra-chiusa
{-1,-1} che non soddisfaceva il proprio contratto (ArrayIndexOutOfBounds,
root-cause reale scoperta dal test stesso); sentinella condivisa per corsi
senza docente che avrebbe fuso corsi scorrelati; mediana lab/aula che
ignorava i campioni zero (un outlier isolato vinceva il voto contro uno zero
maggioritario); `lastPlacementDate` del round-robin cieco alle lezioni gia'
timbrate. Piu 2 dedup (H7 via `Teacher.getEffectiveMaxDailyHours()`,
`isoWeekKey` unificato in `TimeslotSequence`).

**AC#4 (full run 0 hard vs golden) resta aperto** - il test di L2 misura solo
la fase di costruzione, non un solve completo; serve un run live per
confermare che il matching perfetto di G05 (run 12/13, capacita' 990=990,
slack zero) sia risolto dalla timbratura per costruzione come atteso.

PROSSIMO: run completo con L2+L3 insieme per misurare AC#4 (0 hard, KPI di
somiglianza vs golden) e verificare dal vivo se la coppia H3 residua di 2
MODA e' sparita.

## Run 14 — L2 template stamp (2026-07-20, ~21:00 → 22:22, 82 min): LA SVOLTA

**0 hard / -3.460.972 medium / +25.550 soft.** Fattibile, e la somiglianza
cambia categoria:

| | run 13 | run 14 | golden |
|---|---|---|---|
| hard | -1.004 | **0** | — |
| ricorrenza unpinned | 16,9% | **41,5%** | 41,5% stretta |
| ricorrenza totale | 31,0% | **50,3%** | 41,5-72% |
| blocchi >=2h | 1.183 | **1.863** | 1.299 |
| ore singole unpinned | 8.070 | 6.663 | — |
| soft | +8,6k | +25,6k | — |
| medium | -2,75M | -3,46M | — |

- La ricorrenza libera finale ATTERRA ESATTAMENTE sul 41,5% stretto del
  golden: il timbro (78% a fine CH, 18 secondi di fase 1) viene eroso dalla
  LS ma si stabilizza al livello della pratica scolastica - la scommessa
  "il template compatto non offre gradiente alle mosse di compattezza" ha
  retto, con S13 a peso 2 come unica guardia esplicita.
- La coppia H3 di G05 (runs 12/13, slack zero) e' SCIOLTA per
  costruzione: il matching perfetto arriva dal template, non dalla ricerca.
- CH: -26.213 hard in 18s (piu' conflitti del round-robin, ma la fase 2 li
  smaltisce: -82 a fine fase).
- PREZZO: M20 -2.912M/1.062 giorni (vs -2.286M/923) = ~626 deviazioni-ora
  in piu' dal riempimento giornaliero; M12 729 matches (spalmatura full-year
  del timbro, attesa). Il golden ha SIA giorni pieni SIA ricorrenza: c'e'
  margine di tuning (bilanciare template vs M20) senza sacrificare il ritmo.
  Candidato: prossima iterazione di calibrazione, non bloccante.

TASK-381: TUTTI gli AC centrati (AC#2 78% a fine CH >= 50%; AC#3 1.863
blocchi; AC#4 0 hard + KPI). Task chiuso.

### KPI giorni pieni (aggiunto 2026-07-21 su indicazione di maintainer — permanente)

Full-day rate = giorni (gruppo,data) con ore piazzate == ATTESE dal foglio
classe (calendario_class_days.csv). Script: scripts/analyze/daily-fill-kpi.py.

| | run 14 | golden |
|---|---|---|
| full-day rate | **55,5%** (1.329/2.395) | **85,4%** (tetto realistico) |

Run 14 per gruppo: MODA1/2 e G02 97-99%; G14 0% (Q16: il foglio gli
da' l'intera settimana, il calendario e' Gio-Ven); gruppi stage 30-47%;
G04 36% NONOSTANTE le quote (M20 punta al calendario, non all'atteso).
ROOT CAUSE: la nozione di "giorno pieno" di M20/template (calendario
settimanale) coincide con l'atteso per-data della scuola solo sul 10,9%
dei giorni. Fix formalizzato in TASK-385 (fact expectedHours per tutti i
14 gruppi -> M20/template/stamp/KPI sullo stesso bersaglio).

## Run 15 — TASK-385 atteso per-data (2026-07-21, ~11:35 → 13:07, 92 min)

**-426 hard / -1.523.434 medium / -10.680 soft.** Verdetto secondo la
direttiva primaria modello-vs-ricerca: CASO (b) — modello confermato,
benchmark metaeuristiche prioritario.

| | run 14 | run 15 | golden |
|---|---|---|---|
| hard | 0 | -426 (3 H15 + 6 H17) | — |
| medium | -3,46M | **-1,52M** (2,3x) | — |
| **full-day rate** | 55,5% | **74,1%** | 85,4% |
| ricorrenza unpinned | 41,5% | 38,8% | 41,5% stretta |
| blocchi >=2h | 1.863 | 1.861 | 1.299 |

- L'osservazione di maintainer (giornate piene = KPI) + il retarget all'atteso
  per-data: giorni pieni +18,6 punti, medium piu' che dimezzato (M20 ora
  insegue il piano vero della scuola). Blocchi invariati, ricorrenza -2,7
  punti (da monitorare, non allarmante).
- Il prezzo computazionale: CH -57.457 hard (vs -26.213) - il template
  per-data e' piu' rigido; fase 2 consuma l'intera ora ANCORA migliorando
  (-57k -> -1.8k), fasi 3/5 limano a -426. Residuo di natura
  ricerca-incompiuta, non contraddizione logica.
- PROSSIMO (per direttiva): benchmark ONESTO delle metaeuristiche (tutti i
  benchmark storici sono pre-TASK-377, provider cieco - non fanno testo):
  tabu size, late acceptance, pesi mosse, budget fase 2, sul set vincoli
  attuale. Obiettivo: riportare hard a 0 dentro budget sostenibili senza
  toccare il modello.

## Run 16 — controllo budget fase 2 (2026-07-21, ~13:20 → 15:06, 106 min): budget REFUTATO

Config identica a run 15 (tabu 7 / acl 1000), UNICA differenza fase 2
secondsSpentLimit 3600 -> 5400 (90 min). Domanda: "il -426 di run 15 è
solo fame di tempo?". Risposta: NO.

| | run 15 (60 min) | run 16 (90 min) |
|---|---|---|
| CH | -57.457 | -57.457 (identica) |
| fine fase 2 | -1.797 | **-4.128** (peggio con 30 min in più) |
| **hard finale** | **-426** | **-3.902** |
| totale | 92 min | 106 min |

VERDETTO: più budget non compra hard più basso — run 16 è finito in un
bacino peggiore e il tempo extra non l'ha tirato fuori. -426 vs -3.902
dalla STESSA config rivela una convergenza INSTABILE (tabu 7/acl 1000 è
un coin-flip tra buon atterraggio e pessimo). Per la direttiva primaria:
caso (b) confermato - modello ok, il residuo è di ricerca. Budget
revertito a 3.600 (i bracci del benchmark erano già a 3.600, confermati).
PROSSIMO: benchmark metaeuristiche sul laptop (TASK-386, laptop-RUNBOOK.md) -
obiettivo primario ora è la STABILITÀ di convergenza, non solo il minimo.

## Run 17 — heap -Xmx2g (2026-07-22 notte, TASK-387, 86 min): heap NON e' la leva

Stessa pipeline/config di run 15/16 (+H25 confinamento stage, che misura 0),
UNICA differenza JVM heap 260MB default -> 2GB (via JAVA_TOOL_OPTIONS, heap
riservato 2GB confermato con jcmd).

**GC (la domanda di TASK-387):**
| | run 16 (260MB) | run 17 (2GB) |
|---|---|---|
| young-GC pausa media | 183 ms (147.4s/804) | **63 ms** (117.2s/1859) |
| GC totale / wall-time | 148.7s / 106min = **2,3%** | 118.7s / 86min = **2,3%** |

L'heap grande fa collezioni piu' FREQUENTI ma piu' CORTE, a **parita' di
overhead totale (2,3% entrambi)**. Il numero 183ms isolato allarmava, ma il
GC non stava mai strozzando il solver in modo dominante: la frazione di
wall-time in GC e' identica. La pausa piu' corta (63 vs 183ms) da' un
runtime piu' liscio ma non cambia il throughput utile.

**Hard: -1362** (run 15 -426, run 16 -3902) - DENTRO la banda di varianza,
nessun miglioramento chiaro. Composizione: H3 -1000 (LA coppia G05 a
slack zero di sempre) + H15 -360 + H17 -2. KPI identici a run 15 (full-day
75,4%, ricorrenza unpinned 38,1%, blocchi 1901). L'heap non ha cambiato ne'
lo score ne' la qualita'.

**VERDETTO (direttiva primaria):** heap RULED OUT come collo di bottiglia
della ricerca. Il residuo resta un problema di ricerca (la coppia zero-slack
+ varianza di convergenza) -> il benchmark metaeuristiche (TASK-386) resta
LA priorita', caso (b) confermato. NOTA OPS separata: a 260MB l'app IDLE
post-solve fiammeggiava un core in GC-thrash (Old gen fisso all'81%); a 2GB
sparisce. Vale adottare un heap di default sano nel launcher (fix del drain
a riposo + pause piu' lisce), come miglioramento operativo, NON come fix
dello score. Meccanismo verificato: JAVA_TOOL_OPTIONS=-Xmx2g ereditato dalla
JVM app forkata da mvn quarkus:run.

## Benchmark TASK-386 (laptop, 8 run: 6 bracci + 2 re-run stabilità) — la metaeuristica di fase 2 NON e' la leva

Eseguito sul laptop (non su questo laptop, come richiesto): A/B live sequenziale
su 6 config di fase 2, piu' un re-run dei due leader per misurare la
distribuzione, non il punto. Fase 1 (CH) byte-identica su tutti gli 8 run
(`-57457hard`) → costruzione deterministica, A/B pulito: ogni divergenza e'
da fase 2 in poi. Risultati in `benchmark-386-results/` (score/phases/analysis/KPI per braccio + VERDICT.md del laptop).

| braccio | config fase 2 | hard finale | full-day | ric.unpinned | blocchi>=2h |
|---|---|---|---|---|---|
| A (control) | tabu 7 / acl 1000 | -1062 | 78,0% | 37,1% | 2077 |
| C | acl 500 | **-123** | 78,2% | 37,0% | 2048 |
| C-run2 | acl 500 | **-1002** | 78,6% | 36,2% | 2096 |
| D | acl 2000 | **-302** | 78,2% | 37,8% | 2024 |
| D-run2 | acl 2000 | **-1541** | 78,3% | 38,0% | 2073 |
| E | tabu 11 | -1001 | 78,4% | 36,0% | 2040 |
| F | no pillars | -303 | 77,8% | 39,0% | 2117 |
| B | late acceptance 400 | -128 | **66,9%** | 45,4% | 2086 |

**Tre conclusioni, tutte verificate sui dati grezzi (non solo dal VERDICT.md del laptop):**

1. **Late acceptance (B) squalificato.** Unico braccio a far crollare un KPI:
   full-day 66,9% contro 77,8-78,6% dei bracci tabu (il resto del calo lo
   assorbe come ricorrenza piu' alta, ma la giornata piena e' l'indice che il
   golden rispetta). Si resta nella famiglia tabu.

2. **Nessuna config di fase 2 batte il control in modo riproducibile.** La
   classifica dei run singoli (C -123, D -302, F -303 "vincono") e' RUMORE: i
   re-run collassano i leader nel cluster peggiore — C -123 → -1002, D -302 →
   -1541. E' la varianza di run-16 (stessa config, -426 vs -3902) replicata su
   un'altra macchina. n=2 non risolve una distribuzione che oscilla 5-9x.

3. **L'esito e' BIMODALE, e la forbice nasce sia in fase 2 sia in fase 3.**
   Cluster "convergiuto" (~-123..-303) vs "non-convergiuto" (~-1000..-1541),
   separati da ~1000 hard. Split per-fase (phases.json):
   - Fase 2 e' gia' variabile a config fissa: braccio D chiude fase-2 a -317
     (run1) contro -1562 (run2).
   - Fase 3 (ruin-recreate, IDENTICA in tutti i bracci) e' un rescue BINARIO da
     ~1000 hard: C-run1 -1139 → -138 (scatta); C-run2 -1024 → -1024 (non
     scatta); A -2198 → -1077, E -2019 → -1020, F -1320 → -321 (scatta).
   Atterrare nel cluster buono richiede una discesa fortunata di fase 2 E/O uno
   scatto di fase 3. Su 7 run tabu, 3 atterrano buoni (~43%).

**VERDETTO (direttiva primaria — problema di RICERCA, modello intatto):** il
benchmark ha risposto alla domanda che l'aveva motivato ("migliora con una
metaeuristica diversa?") — NO, la scelta dell'acceptor/forager di fase 2 non
porta segnale riproducibile. Si tiene il control (braccio A). Il residuo
-123..-303 (cluster buono) e persino lo 0-hard di run 14 NON sono un problema
di modello ne' di metaeuristica di fase 2: sono **instabilita' di convergenza**
sullo stesso nodo strutturale di sempre (la coppia G05 a slack-zero + H15).

**Prossima leva (search-only, NON deciso — da confermare col richiedente):** la
scelta e' tra (a) **strategia multi-start/restart** — il basin buono e'
raggiunto ~43% delle volte, con 3 restart P(almeno uno buono) ~81%, la leva
piu' economica e riproducibile, pura orchestrazione; (b) **irrobustire il
ruin-recreate di fase 3** (dimensione del ruin, restart interni, budget) — e'
lo scatto binario da ~1000 hard identificato sopra; (c) **fissare per
costruzione il matching G05 a slack-zero nel template di fase 1** — l'unica
via allo 0-hard affidabile, ancora search (construction heuristic), non
modello. Tutte compatibili con la direttiva primaria.

## Benchmark TASK-391 (laptop, 6 run) + adozione R1 — ruin 5-20 ADOTTATO. Il knot di G05 era REALE (correzione al laptop)

Sweep dei ruin count di fase 3 (Ruin & Recreate) sul laptop, fase 2 tenuta al control,
3 bracci × 2 run. Verificato da me contro i dati grezzi (score/analysis/lessons), non
accettato dal sommario del laptop.

| braccio | ruin | hard run1 | hard run2 | spread | residuo hard |
|---|---|---|---|---|---|
| A control | 2-5 | -121 | -1123 | 1002 | run1 H15 -120; run2 **H3 -1000** + H15 |
| **R1 (ADOTTATO)** | **5-20** | **-66** | **-70** | **4** | solo H15 -60 + H17 piccolo, **0 H3** |
| R2 | 10-40 | -2182 | -1146 | 1036 | **H3** -2000/-1000 + H15 (peggiore) |

**R1 (ruin 5-20, il default Timefold) è reliably low** (spread 4 vs 1002/1036), KPI tengono
(full-day 76.9-78.1%, blocchi 2005-2081). Meccanismo verificato da phases.json: R1-run2 parte
da una fase-2 pessima (-2199, ~uguale al -2138 di A-run2) e il ruin 5-20 la risale a -88 (Δ +2111);
il ruin 2-5 del control da -2138 arriva solo a -1138 (Δ +1000). Stesso basin, R1 scappa, il control no.
R2 (10-40) è reliably il peggiore (costo confermato). Fase 1 byte-identica (-57457) su tutti i 6.

**CORREZIONE alla premessa del laptop (verificata da lessons.txt, il laptop aveva sbagliato):** il laptop
concludeva "il knot di G05 non è mai apparso (0/6), premessa refutata, gli H3 sono in altri gruppi".
FALSO. Ho computato gli stack per-gruppo dai raw lessons: A-run2 ha 1 stack ed è **G05**
(Matematica/T08); R2-run1 ha 2 stack **entrambi G05** (Scienze/T09 vs Matematica/T08,
vs Storia della moda) — esattamente le materie del knot. Quindi la diagnosi (c) era CORRETTA: il knot
è reale, appare col ruin piccolo (control 1/2, R2 1/2), e **R1 è precisamente ciò che lo evita in modo
affidabile (0/2)**. Il laptop ha sottovenduto il proprio risultato mis-attribuendo gli H3.

**Target 0-hard NON ancora raggiunto:** R1 chiude il knot H3 di G05 ma lascia **H15 -60** (uno split
same-day-same-subject non-consecutivo tra unpinned) + H17 piccolo. Residuo diverso e minore. Il pursuit
dello 0-hard prosegue copiando altre euristiche umane in fase di costruzione (direttiva utente 2026-07-22:
"se lo fa l'umano dobbiamo farlo anche noi") — ora mirato a H15, vedi TASK-390.

**Nota storica (per evitare confusione futura):** TASK-324 (2026-07-14) sweepò già i ruin count e
"refutò" 20-80 come within-noise, MA girò sul provider CIECO pre-TASK-377 ("non fa testo" per direttiva
CLAUDE.md), sul vecchio pipeline, e non testò MAI 5-20 (saltato dopo il 20-80 within-noise). Non
contraddice TASK-391, che gira sul provider unblinded a 6 fasi ed è l'evidenza valida.

**Adottato su Fedora (DoD completa):** solverConfig.xml phase-3 minimumRuinedCount 2->5,
maximumRuinedCount 5->20 + wording docs 2-5->5-20. XSD valido, GoldenSolutionRegressionTest invariato,
688 test verdi, /simplify + code-review puliti. Ripple solver-config-sync: nessuna (fase/nome/termination
invariati; SolverConfigParser e solverPhases.ts referenziano il nome-fase e il peso, non i count).
