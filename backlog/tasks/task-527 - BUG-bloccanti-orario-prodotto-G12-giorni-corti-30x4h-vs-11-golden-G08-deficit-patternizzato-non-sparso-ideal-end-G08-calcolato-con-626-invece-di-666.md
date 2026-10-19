---
id: TASK-527
title: >-
  BUG bloccanti orario prodotto: G12 giorni corti (30x4h vs 11 golden), G08
  deficit patternizzato (non sparso), ideal end G08 calcolato con 626 invece
  di 666
status: To Do
assignee: []
created_date: '2026-08-19 06:16'
updated_date: '2026-08-19 10:51'
labels:
  - claude-planned
  - solver
  - bug
  - model
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Segnalati da maintainer 19/08 in ispezione del run golden-free 0hard, verificati meccanicamente da Fedora sui dati. BUG A: G12 run 30x4h+4x3h vs golden 11x4h+117x5h. BUG B: G08 deficit patternizzato settimanalmente (lun 5h, mar-ven 4h) invece di sparso come golden e come la forma ratificata TASK-497. BUG C: ideal end G08 marcato al 21/05 (soglia 626 ore cumulate) mentre pool reale 666 -> 7 giugno, e il golden colloca 667 ore con 50 oltre il 21/05.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 BUG A (G12 giorni corti): il run produce 30 giorni da 4h e 4 da 3h dove golden ne fa 11 (da 5h: golden 117 giorni). Verificare perche' il target M20/pool-cap per G12 non insegue le 5h/giorno del calendario: e' deficit del fill o soglia errata? Correggere finche' la distribuzione non e' golden-like
- [ ] #2 BUG B (G08 deficit patternizzato): il run alterna lunedi' 5h + mar-ven 4h ogni settimana (pattern del template) invece di spargere ~24h di deficit come golden (5h quasi ovunque). La forma del pool-cap (TASK-497) e' 'deficit sparso': correggere la distribuzione nella costruzione/LS
- [ ] #3 BUG C (ideal end G08 = 21 maggio): il marker usa 626 ore cumulate ma il pool reale e' 666 (TASK-497) e il golden colloca 667 con 50 ore dopo il 21/05. Identificare donde viene 626 (somma parziale corsi?) e correggere il totalHours del calcolatore
- [ ] #4 Tutte e tre: verifica golden-vs-run post-fix col dataviz (regola consumare: pattern visivi, non solo medie)
- [ ] #5 Collegamento: BUG A e B sono la causa dei 'giorni pieni 72-78%' gia' riportati nei KPI del 19/08 senza indagine: lezione registrata
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ESTENSIONE BUG B (scan Fedora 19/08 su tutto il run): il deficit patternizzato non e solo G08. G04: 21 giorni da 3h, 19 VENERDI (run ven 3,8h vs golden 5,0; golden non ha mai giorni <4h). G06: 23 giorni da 4h di cui 19 venerdi. G12: ven 3,9h vs golden 4,3h (attributo del BUG A). FILO COMUNE: il pool-cap patternizza il deficit sul venerdi invece di spargerlo. Gruppi PULITI (distribuzione identica al golden): 1/G05, G02, G03, G13. La cura del BUG B va verificata su tutti i gruppi over-capacity, non solo G08.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 06:29
---
BUG C FIXATO (commit 58a9a728, suite 1073/0, test nuovo 3/3): il crossamento usava il pool GREZZO dei corsi (G08 926 = 626 reali + 300 Stage pseudo) contro il cumulato di piano che contiene solo ore IN-GRIGLIA: le 183+24 ore off-grid (sabati/chiusure) non consumano piano e il marker scattava al 626esimo giorno-ora = 21/05. Ora la soglia e' il pool IN-GRIGLIA (totalHours - expectedUnscheduled; G08 902) e la divergenza piano-vs-pool (666 vs 626 per G08) e' WARNata come dato di INPUT da riportare, non assorbito.
---

created: 2026-08-19 06:29
---
BUG A DIAGNOSI MECCANICA (non e' deficit di fill, e' PATTERN): G12 pool 675 = quota 675, TUTTO collocato (0 unplaced), ma la distribuzione e' 91x5h + 28 giorni sotto quota di cui 26 = MARTEDI' a 4h con quota 5 (sample 15/22/29/09, 6/13/20/10: tutti martedi'). E' la firma dello STAMP del template settimanale (WeeklyTemplateBuilder): una cella corta su un weekday si ripete tutto l'anno. Il golden spalma (117x5+11x4). Cura candidata: il lever TASK-497 forma b (pool-cap, deficit sparso, PROTOTYPE flag OFF) oppure un post-pass del builder che riallinea la cella corta; entrambi vogliono il gate A/B di maintainer (nessun default flip).
---

created: 2026-08-19 06:29
---
BUG B DIAGNOSI DOPPIA: (1) INPUT: G08 piano-calendar 666h aula+lab vs pool incarichi 626h: 40h di divergenza (il golden colloca 667): nessuna distribuzione puo' fare 5h ovunque con 626h su 130 giorni; domanda referente gia' in classe (divergenze input-vs-golden). (2) PATTERN: il residuo e' patternizzato (lun 5h, mar-ven 4h) = stessa firma template di BUG A; stessa cura (TASK-497 lever o post-pass builder).
---

created: 2026-08-19 06:29
---
AC#5 lezione registrata: i full-day 72-78% dei KPI 19/08 erano la firma visibile di A/B (giorni 4h patternizzati): d'ora in poi un full-day sotto banda con dist pattern-per-weekday scatta l'analisi della forma, non solo della media.
---

created: 2026-08-19 06:29
---
Verifica dataviz (AC#4): differita al run post-fix (il re-solve servito per l'ispezione di maintainer usa ancora il codice pre-fix; non si riavvia l'app finche' maintainer ispeziona).
---

created: 2026-08-19 08:22
---
CURA A/B - analisi completata fino al meccanismo esatto (rilancio 19/08 10:18): lo stamp (DayClusteredConstructionPhase.stampWeeklyTemplate) piazza per (data, gruppo) le celle del template di QUEL weekday, con cap expectedHoursCap. La firma patternizzata nasce cosi': per i gruppi pool<capienza (1/G08) la coda del corso in esaurimento (stampSkipExhausted) lascia corto SEMPRE il weekday che ospita le sue celle; per G12 (pool=quota=675) sono i totali-per-weekday del template a non allinearsi alla quota (cella corta fissa). NOTA: il lever TASK-497 pool-cap e' GIA' DEFAULT ON (ratificato 15/08, forma opt-out) e i target M20 sono gia' sparsi: il pattern sopravvive perche' lo stamp e' la forma e M32 blocca le mosse (ogni spostamento costa deviazione).
---

created: 2026-08-19 08:22
---
DESIGN della cura (da implementare, flag DEFAULT OFF planner.construction.short-day-scatter.enabled): post-pass deterministico nel phase, sibling di profile-following: per (gruppo, settimana ISO) con weekday W a quota-1 e un weekday V a quota, sposta una lezione spostabile da V a W facendo ciclare W settimana per settimana (gate: canPlaceLesson/exceedsDailyCap; accettare il costo M32 della cella persa). Trade-off da misurare nel run: M32 deviazione vs allineamento M20/M33 e distribuzione golden-like.
---

created: 2026-08-19 08:22
---
STOP onesto: l'implementazione + test + run cap-240 non stanno nel contesto residuo di questo turno. La diagnosi e' completa al livello di riga di codice (meccanismo sopra), il design e' pronto, nessuna decisione di maintainer richiesta per il flag OFF: prossimo turno implementa e lancia il run di misura.
---

created: 2026-08-19 09:24
---
CURA A/B IMPLEMENTATA (5692316c + fix CME): post-pass shortDayScatter nel DayClusteredConstructionPhase, flag planner.construction.short-day-scatter.enabled DEFAULT OFF (opt-in standard), sibling di profile-following. Per (gruppo, settimana ISO): weekday corto (cap-1) e weekday pieno (>=cap), sposta UNA lezione spostabile (non-pinned, non-pseudo) dal pieno al corto con sorgente CICLICO per settimana, gate canPlaceLesson/expectedHoursCap/VRP, untrack/track simmetrici (helper decrement nuovi). Test ShortDayScatterTest 3/3 sulle firme storiche; suite 1076/0.
---

created: 2026-08-19 09:24
---
RUN scatter-519 (flag ON, capture canonica scatter-519-2026-08-19, commit 78709fb2): primo lancio CME (sort mid-iteration, fix immediato), rilancio pulito. Pass fired: 180 mosse in costruzione. ESITO: **0hard TENUTO** (tentativo 1, ~33 min), medium -996.680 MIGLIORE del control verify-519 (-1.013.671).
---

created: 2026-08-19 09:24
---
DISTRIBUZIONI (la misura chiave, vs control): G08 short-days SPARSI Gio 11/Mer 10/Ven 9 (era pattern lun5/mar-ven4): BUG B curato sull'asse pattern (il conto 40h resta: e l'input 666-vs-626, domanda referente). G12 shorts Mar 16/Ven 11/Gio 7 (era 26 martedi' fissi): BUG A curato sulla forma. RESIDUI: G04 (17 shorts su 18 venerdi') e G06 (19 su 23 venerdi') NON sparsi: da verificare se la quota CALENDAR del venerdi' e' genuinamente corta per quei gruppi (input-following corretto) o se serve redistrib (DB giu' al momento della misura: check differito).
---

created: 2026-08-19 09:24
---
Stato: prototipo DEFAULT OFF, adozione a maintainer dopo il ricontrollo distribuzioni di Fedora.
---

created: 2026-08-19 10:22
---
TURN0-3 (19/08 11:46): raffinamento implementato (8d29b3a1, suite 1077/0, test 4/4): (a) deficit QUALSIASI sotto la quota dichiarata riempito (il vecchio test cap-1 mancava il caso G04 ven 3h vs 5h dichiarate); (b) sorgenti fallback: TUTTI i weekdays pieni provati in ordine ciclico (la fonte singola spesso non aveva lezioni legalmente spostabili: i 16 martedi' G12 residui del primo run).
---

created: 2026-08-19 10:22
---
RUN scatter2-519 (e830e6ca): 513 mosse in costruzione (vs 180), **0hard TENUTO** (tentativo 1), medium -986.411 (migliore ancora: control -1013k, scatter1 -996k). I DUE RESIDUI: G12 martedi' 19/33 a 5h (era 0/33: la meta' curata, 14 a 4h residui = gate legalita' H1/VRP sulle fonti disponibili quelle settimane); G04 venerdi' 18 a 5h COME DICHIARATO (era 0: curato) + 9 a 3h e 6 a 4h residui (stesso limite: nessuna lezione spostabile legale verso quei venerdi').
---

created: 2026-08-19 10:22
---
Letture per maintainer: la cura e' DIREZIONALEMENTE confermata su entrambi i residui (0 -> 19 e 0 -> 18 giorni a quota dichiarata) con hard 0 e medium migliorato; il residuo restante e' vincolo di legalita' per-mossa (H1 teacher gia' occupato / VRP del corso non contiene slot del weekday corto), non pattern dello stamp: spingerlo oltre richiederebbe mosse a catena (swap due lezioni), famiglia Kempe/TASK-482. Prototipo resta DEFAULT OFF.
---

created: 2026-08-19 10:46
---
PREVALIDAZIONE vincolo quota-fill (mandato cambio-approccio, doc task527-quota-constraint-prevalidation-2026-08-19.md, commit 2698ee7e): TEST A (golden-oracle) NEGATIVO -> STOP prescritto eseguito, NESSUNA implementazione. Il golden viola la quota dichiarata su 31/2009 giorni (G03 21, di cui 20 LUNEDI' a +1h; G09 5; G14 5; margine +1h x26, +3h x5; 155 giorni sotto quota come noto). Un hard quota-fill dichiarerebbe infeasibile la soluzione della scuola: golden-first lo vieta. Le 31 divergenze sono pratiche non dichiarate (famiglia referente): la via corretta e' la domanda workbook, e hard selettivo SOPO la conferma delle quote da parte della scuola.
---

created: 2026-08-19 10:46
---
TEST B differito (path export indicato non raggiungibile; detection comunque nota positiva dai numeri di Fedora). TEST C differito (sum incarichi.csv artefatto 1980 uniforme: colonne da chiarire; pool autoritativi dal DB, container down). Nessun ID vincolo registrato perche' nulla e' stato implementato: la famiglia resta da aprire se/quando la scuola conferma le quote.
---

created: 2026-08-19 10:49
---
PREVALIDAZIONE COMPLETA A+B+C (turno 12:50, doc aggiornata c8600034). TEST B POSITIVO: la misura sull'export vivo scatter2 vede esattamente i residui known (G12 mar 14x4h, G04 ven 9x3h+6x4h; tot run 240 giorni sotto quota, margine quasi sempre 1h): come RILEVATORE il vincolo e' valido. TEST C NEGATIVO sulla forma proposta, con pool AUTORITATIVI dall'export: G12 pool 675 < quota-sum 695 (i suoi martedi' corti sono matematicamente non riempibili da NESSUN orario), G04 -240, G08 -103, G09/FA/FB/G13 -16..-28; hard fill possibile SOLO per i 4 grupzi puliti (1/G05, 1/G06, delta 0) e con slack per G03 (+21: e il golden riempie proprio quell'extra nei 21 lunedi' +1h, coerente con TEST A).
---

created: 2026-08-19 10:49
---
VERDETTO FINALE: NESSUNA implementazione del vincolo. Hard fill: impossibile dove ci sono i difetti, inutile dove e' possibile, contrario alla pratica dove c'e' slack (G03). Hard surplus: golden lo viola 31 volte (TEST A). La quota-dichiarata resta target MEDIUM (M20 pool-cap ratificato) + scatter flag OFF come leva misurata (0->19 e 0->18 giorni a quota). La radice e' INPUT: le divergenze pool-vs-calendario e i lunedi' G03 vanno a referente/workbook (la pagina /constraints le mostra in da-confermare). Nessun ID vincolo registrato.
---

created: 2026-08-19 10:51
---
TEST A RAFFINATO (protocollo near-miss di maintainer, doc cd0b0658): PROTOCOLLO REGISTRATO (0 viol -> procedi; violazioni classificate per entita+sistematicita; near-miss <=1-2h non sistematico = vincolo valido, il golden e' il planner umano che si e' arreso, il run col vincolo deve batterlo su quelle celle; >2h o sistematico = fermati). MISURATO: NESSUN gruppo e' near-miss-puro. G04: 116/171 giorni sistematici, -1x90 MA ANCHE -6x25 (grandi). G08: -1x7 ma anche -8/-5/-4 (grandi). G09/G13/G10/G11/G12: poche violazioni ma tutte GRANDI (-3..-8h = giorni interi). G14/G03: solo SURPLUS (+3/+1: pratica dello slack pool, non viola un hard fill). Il caso 'planner arreso' NON si presenta: le violazioni grandi coincidono con pool<quota-sum (TEST C). Verdetto STOP confermato.
---

created: 2026-08-19 10:51
---
DOTTRINA REGISTRATA: golden-first per le PRATICHE, input-first per le DICHIARAZIONI (l'intento dichiarato vince sul golden, ma solo dove la matematica del pool lo consente: TEST C resta il gate). L'hard fill attende correzione/conferma delle quote dichiarate; fino ad allora il target e' il MEDIUM M20 pool-cap ratificato + scatter flag OFF.
---
<!-- COMMENTS:END -->

### VERDETTI maintainer 19/08 sui candidati dello scan Fedora

- Candidato 1 (G04 venerdi 3h): BUG CONFERMATO (famiglia BUG B, template stamp: la diagnosi meccanica di zai conferma la firma dello stamp settimanale).
- Candidato 2 (G06 4h regolari): NON bug, possibile preferenza qualitativa (la regolarita' potrebbe essere migliore del golden). Domanda referente P1 aggiunta.
- Candidato 4 (G07 5/6h): possibile preferenza qualitativa. Domanda referente P2.
- Candidato 5 (G10 20 giorni a 7h vs 1 golden): BUG. Il singolo 7h del golden e' un'eccezione non risolta, non prassi: con numeri cosi' piccoli non si inferisce pratica accettata. Da aggiungere alla cura del template stamp (il 7h e' la faccia speculare del weekday-corto).
- Candidato 6 (G14 2 giorni 4h): discorso qualitativo. Domanda referente P3.
- NOTA TASK-526: verdetto no-bug di zai (le 2859 senza stanza sono tutte pin/pseudo esenti per progetto, DB e in-memory coincidono). Il difetto reale e' la UI che calcola H29 sul click SENZA l'esenzione pin/pseudo: e' il finding (c) del TASK-525.
