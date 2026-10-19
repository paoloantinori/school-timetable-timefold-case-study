# VERDICT A/B chain TASK-364+467 (laptop, 2026-08-16 sera)

> Filone 1 del mandato. Control: entrambi i flag OFF (comportamento production,
> Phase 1 cortocircuitata, candidato unico). Treatment: entrambi ON (deriver
> popola le LabRotation, enumerator multi-candidato col ranking input-driven,
> primo feasible vince). Protocollo: baseline-golden.sh appaiato, source=input
> DEFAULT, maxAttempts=5 DEFAULT, tutto il resto ratificato ON. n=1 per braccio.

## COME SI E' ARRIVATI AL RUN UTILE: due bug latenti smascherati dallo smoke

Il round 1 (preservato in `round1-vacuous-isLabHour-empty/`) non misurava
nulla e la diagnosi del perche' e' meta' del valore del filone:

1. **Phase 3 INFEASIBLE con ProfessionalizzantiDay vuota.** Con le rotazioni
   derivate il cortocircuito count()==0 salta e Phase 3 torna a girare sul
   seed per la prima volta da mesi: nessun gruppo ha righe
   professionalizzantiday, getProfessionalDatesInRange torna vuoto PER DESIGN,
   ma il success-predicate contava quelle lezioni come candidate -> INFEASIBLE
   con nulla da fare. FIX (commit 36581b2b): no-profDay = expected-empty,
   stessa famiglia di noStagePattern.
2. **Popolazione Phase 1 VUOTA: isLabHour mai settato nel path regular.** Il
   path block del LessonGenerator (labBlockSize>1) ha sempre settato
   isLabHour=course.isLab; il path regular mai; sul seed OGNI corso lab ha
   labBlockSize=0 -> 2.836 ore lab con isLabHour=false e le query di sessione
   (isLabHour=true) vedevano ZERO lezioni a prescindere dalle rotazioni. FIX
   (commit 84da1595): lesson.isLabHour = course.isLab anche nel path regular.
   isLabHour e' letto SOLO dal lato pre-assign: score e production a flag
   spenti invariati (golden regression + real-seed construction VERDI).

Dopo i due fix, lo smoke della chain e' VIVO per la prima volta end-to-end:
428 lezioni unpinned acconciatura -> 9 session group; L'ENUMERATOR
MULTI-CANDIDATO INGAGGIATO: piano 1/3 provato e RIFIUTATO dal fail-fast
("Day restriction leaves 1 pair with no valid day: BlockKey[teacher=19,
group=2]" = la guard anti-shell-game al suo primo fuoco reale), piano 2/3
FEASIBLE e adottato; Phase 1 pinnata 398 entita', Phase 2 (moda/termoidraulica/
meccanica/informatica) altre 2.107. Il round 2 sotto gira su questo codice.

## ROUND 1 (vacuo, preservato): numeri e lettura

| | control (OFF/OFF) | treatment (ON/ON, delta ~0) |
|---|---|---|
| hard | 0 | 0 |
| medium | -1.031.076 | -1.031.729 |
| full-day | 87,7% | 88,1% |
| day-start | 99,4% | 99,5% |
| ricorrenza unpinned | 74,8% | 74,6% |
| BUCO-INTERNO | 0 | 0 |

Entrambi i banner verificati in entrambe le braccia (enabled=false/false nel
control, true/true nel treatment; deriver "derived 15 rows" solo nel
treatment). MA: Phase 1 girava con 0 lezioni (bug 2) e 0 piani enumerator:
il delta effettivo del treatment era nullo, i numeri sopra sono la banda di
rumore LS dello stesso solve. Gate di non-interferenza PASS, efficacia
NON MISURATA.

## ROUND 2 (utile, HEAD 71e0c30c): NEGATIVO DECISO, gate FALLITO sul binario hard-0

Banner verificati in entrambe le braccia (control: entrambi false; treatment:
entrambi true, deriver 15 righe/13 coppie, enumerator INGAGGIATO: piano 1/3
rifiutato dal fail-fast su una coppia contraddetta, piano 2/3 adottato; Phase 1
398 entita' pinnate + Phase g05/termoidraulica/meccanica/informatica 2.107).

| | control (OFF/OFF) | treatment (ON/ON) |
|---|---|---|
| hard | **0** (attempt 1) | **-64** (5/5 attempt, mai 0) |
| medium | -1.041.682 | -2.099.400 (analysis) |
| full-day | 87,4% | 75,1% |
| ricorrenza unpinned | 74,5% | 68,9% |
| BUCO-INTERNO | 0 | 0 |
| wall | 23 min | ~120 min (budget multi-start interamente consumato) |

Composizione dell'hard del treatment (analysis.json): **H20 -21** (lezioni
pinnate su slot CHIUSI dal calendario del gruppo), **H0 -28** (unassigned),
**H7 -15** (cap docente superato). Meccanismo radice IDENTIFICATO: il path di
pin di Phase 1/2 (GreedyRotationAssigner/AdaptiveSlotFinder) non ha NESSUN
check della finestra di calendario del gruppo nel suo census di skip (il log
elenca wrongDay/holiday/H5/stage/H11/used/idealEnd: niente H20), quindi i
blocchi derivati (fino a 8h, forme lette dal golden) vengono pinnati anche su
weekday la cui finestra e' piu' stretta del blocco, e il solver eredita
violazioni hard che la LS non puo' curare senza spostare i pin autoritativi.
H7: stesso buco lato cap docente.

## Raccomandazione per maintainer

**NESSUNA adozione: entrambi i flag restano DEFAULT OFF** (il path production
e' intatto: il control ha riprodotto la banda attesa 0hard/-1,04M in 23 min).
Il verdetto tecnico e' NETTO e ha due meta' distinte:

1. **La meccanica multi-candidato e' PROSCIOLTA e funzionante**: per la prima
   volta l'enumerator ha provato piani distinti, il fail-fast ha rifiutato il
   piano contraddittorio, un piano feasible e' stato adottato. Il danno NON
   viene dalla scelta del candidato.
2. **Il danno viene dal PIAZZAMENTO dei pin** che la chain alimenta: il path
   Phase 1/2 pina senza validare H20 (finestra calendario) e H7 (cap docente).
   Curare = aggiungere quei due gate al piazzamento (o validare a monte nel
   deriver: seminare solo forme che entrano nella finestra del weekday e nel
   cap del docente). Solo DOPO quella cura ha senso un nuovo A/B.

Cosa resta guadagnato comunque da stasera: due bug latenti riparati (Phase 3
predicate, isLabHour path regular: il primo rende il pre-assign eseguibile,
il secondo ripopola la popolazione lab per qualunque uso futuro), la proof
che il meccanismo funziona, e la mappa esatta del prossimo passo.

## ROUND 3 (HEAD ceefa787, cura TASK-502 livelli a+b): la cura FUNZIONA sui propri bersagli, il gate FALLITO SI SPOSTA sul room-tier (H29), e due bug nuovi emergono

| | control (OFF/OFF) | treatment (ON/ON) |
|---|---|---|
| hard | **0** (attempt 1) | **-42** (score.txt) / **-7795** (analysis.json): 5/5 attempt falliti, best -7781 |
| medium | -1.045.035 | -2.050.380 (score.txt) / -1.709.829 (analysis.json) |
| full-day | 87,8% | 67,7% |
| ricorrenza unpinned | 74,1% | 69,5% |
| BUCO-INTERNO | 0 | 14 |
| wall | 23 min | ~126 min (budget 5 attempt intero) |

Composizione hard treatment (analysis.json): **H29 -7739** (room-null: dominante),
H0 -40, H17 -14, H7 -2.

### Cosa ha fatto la cura TASK-502 (verificato sui suoi bersagli)

- **H20: -21 -> 0.** Nessuna lezione pinnata fuori finestra calendario. Il census di
  skip del greedy mostra i rifiuti al lavoro (26 calWindow sui venerdi' stretti nello
  smoke, 28 nel run3).
- **H7: -15 -> -2.** Il gate teacherCap conta le ore piazzate e rifiuta i giorni saturi.
- Igiene a monte: 0 righe clipped/0 scartate (le 15 forme derivate entrano gia' nelle
  finestre e nei cap: la validazione calendario del deriver le aveva gia' filtrate).
- **AC#1 PASSA**: analysis a fine pre-assign nel run3 = H20 0, H7 0 sui pin (l'unico
  hard pre-solve e' H0 unassigned, fisiologico).
- **AC#2 PASSA**: l'enumerator resta ingaggiato (piano 1/3 rifiutato dal fail-fast,
  piano 2/3 adottato, identico al round 2: la cura non cortocircuita la scelta).

### Cosa NON funziona: il failure e' MIGRATO sul room-tier

Round 2 moriva su H0/H20/H7 (i pin). Round 3, con i pin puliti, muore su **H29 -7739**:
i blocchi che i gate ora RIFIUTANO (venerdi' fuori finestra ecc.) restano unpinned, e il
room-tier del solver non regge il carico: la B-ROOM CH assegna stanze ma la LS di
refinement lascia migliaia di room-null. Il meccanismo preciso (perche' il rifiuto
pre-assign peggiora il room-tier invece di lasciarlo invariato) NON e' stabilito:
diagnosi differita, e' la precondizione per un round 4.

### Bug nuovi emersi (documentati, NON risolti stanotte)

1. **NPE nella move thread al tentativo 5** (21:30:23):
   `SimpleFieldTracker.contains -> "existing is null"` dentro `Lesson.setRoom` richiamato
   dal move thread del MT-LS. E' il dirty-tracking del bytecode enhancement Hibernate
   che collide con la mutazione multi-thread dell'entita' arricchita. Tentativo 5
   abortito ("staged solve FAILED - releasing the promoted pins anyway"), score null.
   Race rara (solo dopo ~2h di room-LS pesante: il treatment ha molto piu' room-null
   da sistemare, quindi molta piu' pressione su quel codice).
2. **Percorso "best attempt shipped with CH-only rooms"** (nessun attempt a 0-hard):
   il wrapper spedisce il best con sole stanze CH. Coincide con:
3. **DISCREPANZA score.txt vs analysis.json, AMPIFICATA all'hard** (AC#4 del task):
   score.txt -42hard/-2.050.380 vs analysis.json -7795hard/-1.709.829 (delta hard
   -7753, delta medium -338k). Round 2: solo medium, -382k. SEMPRE treatment-specific
   (control run3: 0hard/-1.045.035 IDENTICO nelle due sorgenti). Meccanismo non
   stabilito: si collega al percorso no-0-hard e al fallito tentativo 5 (il dato
   caricato dal DB non coincide con lo score spedito).

### Raccomandazione per maintainer

**Nessuna adozione: i flag restano DEFAULT OFF** (production verificata dal control:
0hard in 23 min, KPI in banda). La cura TASK-502 e' confermata efficace sui propri
bersagli (H20/H7 azzerati) e va tenuta: e' il prerequisito di qualunque round futuro.
Ma la chain resta NON misurabile come beneficio: il failure migra sul room-tier e
due bug nuovi (NPE multi-thread sul dirty tracking, discrepanza score/analysis nel
percorso no-0-hard) vanno diagnosticati prima di un round 4. Prossimi passi proposti:
(a) diagnosi H29 (perche' i blocchi rifiutati dal pre-assign travolgono la B-ROOM),
(b) stack NPE SimpleFieldTracker: entita' detach/share fra move thread,
(c) il percorso ship-best-no-0hard e la sua discrepanza score vs DB.

## ROUND 3-DIAGNOSI (TASK-507, forensics read-only sui run3 artifact, 16/08 notte)

Catena causale dimostrata riga per riga dai log. Tre fatti la costruiscono.

### Fatto 1: il room CH e' INERTE su questo problema (entrambe le braccia)

Il CH della fase room NON assegna stanze: termina con lo score INVARIATO in entrambi
le braccia. Chi assegna tutte le stanze e' la LS di refinement.

- CONTROL (ab-off-run3/app.log): solver room parte da **-9404hard** (9404 room-null
  all'ingresso: tutte le stanze non-pinnate sono nulle), "Construction Heuristic
  phase (1) ended ... best score (-9404hard/-962166medium)" (19:14:09,687, CH 467ms,
  score invariato), poi "Local Search phase (3) ended ... best score
  (0hard/-1045035medium)" dopo 601s: la LS fa TUTTA l'assegnazione (9404 room-null
  -> 0 in 10 min).
- TREATMENT (ab-on-run3): identico andamento del CH (parte -7781, CH 563ms,
  invariato), ma la LS e' stata rimossa (vedi Fatto 2).

L'assunzione di design del TASK-498 ("the room CH still runs: every lesson gets a
room and the attempt's score stays comparable in the pick-best") e' FALSA su questo
problema: il CH lascia le stanze nulle, quindi un tentativo senza LS di refinement
consegna H29 = numero di lezioni non-pinnate.

### Fatto 2: TUTTI i tentativi treatment sono condannati a -40/-42 di timeslot-tier

Ogni tentativo logga "DOOMED at the post-RELEASE boundary (timeslot-tier hard
-42 < -2" (tentativi 1, 2, 4; il 3 a -40). Composizione: ExactFinisher residuo
"placed 6 of 26 boxed lesson(s)" = 20 boxed x peso H0 2 = **-40** + 1 buco H17 =
-2. Skip-doomed-room rimuove quindi la LS di refinement da OGNI tentativo, e il
tento spedisce H29 -7739 (7739 = lezioni non-pinnate senza stanza: 12494 - 4735
pinnate = 7759, meno 20 boxed senza timeslot = 7739, tornano i conti).

IL GAP REALE del treatment verso il control e' solo quel **-42 timeslot-tier**
(20 ore non piazzabili dalla costruzione + 1 buco): tutto il resto dell'hard del
run3 e' l'artefatto del Fatto 1 + Fatto 2.

### Fatto 3: la rifinitura di consegna (corner b) HA GIRATO ma e' stata uccisa dal driver

L'abort NPE del tentativo 5 (21:30:23) NON ha impedito il corner b: subito dopo,
"best attempt 1 shipped with CH-only rooms (no 0-hard attempt ran) - refining its
rooms before delivery" (21:30:23,375) lancia un solver completo (CH 350ms + LS,
"Multi-threaded LS phase started" 21:30:24,584). L'ipotesi Fedora (rifinitura non
partita dopo l'abort) e' CONFUTATA: la rifinitura parte. Ma:

- il driver considera il solve finito su NOT_SOLVING + stagedStage null (i due
  falsi segnali documentati): la rifinitura gira su un solver ad hoc che NON passa
  da solverManager e stagedStage e' gia' null, quindi il poll dichiara FINISHED
  mentre la LS sta ancora lavorando;
- capture-run legge il DB a 21:32:12, MID-rifinitura: analysis.json congela lo
  stato consegnato CH-only (**-7795hard**);
- score.txt viene letto 26s dopo (21:32:38) da latestScore, che nel frattempo la
  rifinitura in-memory aveva portato a **-42hard** (H29 quasi azzerato: la
  rifinitura FUNZIONA);
- il driver uccide l'app (21:36:59, "planner stopped") prima che la rifinitura
  finisca e salvi: lo stato -42hard NON e' mai stato persistito.

### Catena causale completa (verdetto TASK-507)

1. La costruzione del treatment lascia 20 boxed + 1 buco (timeslot-tier -42,
   sotto soglia -2): questa e' l'unica regressione REALE della chain 364+467
   rispetto al control in round 3.
2. Condannato il tentativo, skip-doomed-room rimuove l'LS di refinement.
3. Ma su questo problema la LS e' l'UNICA fonte di assegnazione stanze (il CH e'
   inerte): il tentativo spedisce H29 = tutte le lezioni non-pinnate (-7739).
4. Il corner-b di consegna rifina il vincitore e FUNZIONA (-42hard in-memory) ma
   il driver la uccide per i due falsi segnali di quiescenza: artifact incoerenti
   (analysis -7795 vs score -42) e potenziale reale del treatment NON misurato.

### Conseguenze operative (proposte, nessuna applicata)

- La soglia -2 del doomed-verdict condanna tentativi il cui gap timeslot e' -42:
  e' la soglia giusta (vogliamo 0-hard), ma la perdita di comparabilita' del
  pick-best per H29 rende il punteggio dei tentativi condannati inutilizzabile.
  Servira' o rendere il room CH funzionante, o far pesare H29 in modo comparabile,
  o ritardare il verdetto doomed.
- Il driver ha bisogno di un segnale di quiescenza che copra la rifinitura di
  consegna (il corner b): oggi la uccide dopo ~2 min.
- Il vero gap da curare per un round 4 e' il -42 della costruzione (20 boxed +
  1 buco), NON il room-tier.

## ROUND 3-BOXED (secondo mandato notturno): i 20 boxed sono UNA POPOLAZIONE omogenea, e la causa SONO i pin della chain

Identificazione (export.json.gz ab-on-run3, unassigned = timeslot null): esattamente
20 lezioni, tutte **G04 / Recuperi ed Approfondimenti / T15**. Nessun altro
gruppo o materia. In round 2 la stessa firma era gia' presente (14 unassigned, stessa
coppia): il fenomeno e' stabile nella chain, non rumore.

### Perche' restano boxed: il docente e' occupato dai PIN della chain sui giorni giusti

G04 ha 79 giorni attesi con quota-spare (94 pieni su 173) e lo spare esiste
tipicamente in fasce da 4h su mercoledi'/giovedi'. Ma T15 in quelli stessi
giorni e' interamente occupato da blocchi lab PINNATI dalla Phase 2 della chain
(verificato cella per cella sui primi 6 giorni spare: 2026-11-04/11-18 Controllo
numerico G08 08:00-13:00 PIN; 2026-12-02/09/16 Cad G08 08:00-13:00 PIN; ecc.):

| soggetto | giorno | ore pinned (trattamento) |
|---|---|---|
| Cad G08 | mercoledi' | 30 |
| Controllo numerico G08 | mercoledi' | 64 |
| Lavorazioni meccaniche G08 | martedi'/mercoledi'/giovedi' | 42+42+26 |
| Lavorazioni meccaniche G13 | mercoledi'/giovedi' | 24+6 |
| Cad G13 | martedi'/mercoledi' | 18+12 |
| Cad G04 | mercoledi'/giovedi' | 2+18 |
| Lavorazioni meccaniche G04 | giovedi' | 152 |

Totale T15 lab PINNED nel treatment: 436 su 476 (il 92%). Nel control: 0
pinned su 476, e il solver distribuisce T15 su TUTTI e cinque i weekday
(lun 100, mar 51, mer 123, gio 120, ven 132) piazzando le 50h di RA G04
interamente su giovedi' (la scelta del solver libero).

Nel treatment T15 sta solo su mar 86 / mer 204 / gio 202 / ven 14: lunedi e
venerdi sono quasi vuoti MA G04 in quelli ha la finestra/giornata piena di quota
(weekend pattern FC: lun-gio aula+lab 8h piene, il venerdi' con stage): le 20 ore
non hanno alcuna cella VRP-compatibile dove gruppo-spare e docente-libero si
intersechino. ExactFinisher chiude 6 dei 26 boxed iniziali (le celle residue),
le 20 finali sono strutturalmente bloccate.

### Risposta alla domanda guida (esplicita)

**SI': la causa sono i pin della chain.** Meccanicamente: il fix isLabHour
(84da1595) ha ripopolato la popolazione lab di Phase 2 (meccanica/informatica), e
Phase 2 pina i blocchi lab di T15 (Cad/Controllo numerico/Lavorazioni
meccaniche) sui giorni-L di 1/G08 e G13, che sono esattamente i giorni dove
G04 ha quota-spare per la RA dello stesso docente. Il conflitto e' Docente-1
(H1): il docente e' lo stesso. Non e' finestra calendario, non e' capacita'
gruppo (lo spare c'e'), e' disponibilita' incrociata gruppo-spare x docente
consumato dai pin.

### Conseguenza per il round 4 (la forma)

Il -42 non e' un problema di ricerca da curare con retry: e' un conflitto di
ALLOCAZIONE introdotto dal pre-assign. Forme candidate (da valutare col prossimo
mandato): (a) Phase 2 evita di pinnare un docente oltre la capacita' residua
della SUA popolazione non-lab (guardia docente-centrica sullo stesso stile di
teacherCap ma annuale, non giornaliero); (b) il deriver/Phase 2 lascia libero un
buffer di weekday per i docenti con corsi non-lab sulla stessa classe (T15
ha 50h RA su G04 oltre ai lab); (c) accettare il conflitto e lasciare le 20h al
solver: cio' richiede che il tentativo NON sia condannato (la soglia -42 < -2 e'
esatta) quindi il vero cambiamento e' nella mappatura pick-best/condanna, non
nella costruzione. La mia lettura: (a) e' la piu' coerente col principio
"il pin aiuta solo se non crea overdemand a valle".

## ROUND 4-PREVIEW (terzo mandato notturno, HEAD 44d25b7f): il guard forma (a) NON cambia l'esito (aggregato annuale troppo grossolano), il marker di quiescenza FUNZIONA

| | control (chain ON, guard OFF) | treatment (chain ON, guard ON) |
|---|---|---|
| hard | **-42** | **-42** |
| boxed | 20 (G04 / RA / T15) | 20 (identici) |
| full-day | 74,7% | 73,9% |
| ricorrenza unpinned | 69,1% | 69,0% |
| BUCO-INTERNO | 0 | 0 |
| wall | ~2h20 (5 attempt) | ~2h18 (5 attempt) |

Entrambe le braccia convergono sullo STESSO esito del round 3: il -42 hard e i 20
boxed persistono esattamente. Il guard NON ha declinato NESSUNA coppia (zero righe
DECLINED nei log) e la ragione e' aritmetica, misurata sul run: la capacita' annua
libera di G04 dopo il carico pinnato e' ~646 slot (finestra 8 - carico/34
settimane per weekday), il pool lab di T15 su G04 e' ~172h, il non-lab
(RA) 50h: 50 <= 646-172, il pin passa. L'aggregato ANNUALE non vede che i 646 slot
liberi stanno nei weekday SBAGLIATI (lun/ven, dove T15 resta libero ma G04
non ha quota-spare per la RA; il golden stesso mette la RA di giovedi'). La forma
(a) come implementata e' troppo grossolana: servirebbe l'intersezione
per-weekday (quota-spare del gruppo ∩ disponibilita' residua del docente), non la
somma annua. Quest'altra forma resta da valutare.

### La buona notizia del run: il marker refiningForDelivery e la convergenza spontanea

1. **Il quinto tentativo raggiunge -42hard da solo in ENTRAMBE le braccia** (non
   doomed: B-ROOM con rifinitura completa da 603s, -7795 -> -42). Il budget
   maxAttempts=5 e' ESATTAMENTE sufficiente: tentativi 1-4 condannati a -42/-70/-43/-66
   timeslot-tier, il quinto la spunta. NESSUN intervento del corner-b (best = ultimo
   tentativo, niente restore).
2. **score.txt e analysis.json concordano sull'HARD** (-42 = -42, in entrambe le
   braccia; era il punto del marker TASK-509). Il medium resta divergente
   (-1704k vs -2106k): score.txt riporta l'ultimo best in-memory del solver,
   analysis riconteggia il DB salvato; e' il meccanismo TASK-509 gia' mappato
   (latestScore narrativa vs DB verita'), adesso isolato al solo medium perche'
   la rifinitura non viene piu' uccisa a meta'.
3. Nessuna NPE del tracker in ~4h30 di MT-LS cumulata (2 braccia x 5 tentativi):
   coerente con la race rara del disassembly (finestra di pochi bytecode).

### Sintesi per maintainer (al mattino)

- La chain 364+467 con maxAttempts=5 converge a -42hard DETERMINISTICAMENTE (4
  run: round 2 -64, round 3 -42, run4 entrambe le braccia -42). Il residuo e'
  UNA popolazione: 20 ore di RA G04/T15, conflitto H1 docente-con-se-stesso
  dai pin Phase 2.
- Il guard forma (a) annuale NON e' la cura (aggregato troppo grosso, zero declini).
  La direzione giusta e' l'intersezione per-weekday, oppure intervenire sulla
  condanna (forma c, semantica TASK-498, decisione di maintainer) o sul curriculum
  (lasciare al solver le 20 ore: tentativo 5 le lascia effettivamente al solver,
  arrivando a -42 che e' esattamente 20 boxed x 2 + 1 H17 + 1 H7... da
  verificare la composizione residua: qui -42 = H0 -40 (20 boxed x peso 2) + H7 -2).
- RATIFICA della catena cura+guard: NO, il guard non aggiunge nulla; la catena
  deriver+enumerator arriva da sola a -42 col quinto tentativo. La questione
  per maintainer e' se -42 (20 ore RA strutturalmente bloccate dai pin) sia accettabile
  o se si voglia la forma (c) / l'intersezione per-weekday.

## ROUND 5-PREVIEW (quarto mandato notturno, run5 in corso, guard raffinato con bug poi fissato)

### Cronaca

1. **Filone 1 (completato, 1f273456)**: guard raffinato alla forma INTERSEZIONE
   per-weekday (stesso flag): il caller passa per ogni weekday
   min(spare gruppo, residuo docente dopo il pin) scalato annuo, la regola declina
   quando la somma dei minimi non copre il pool non-lab. Test del caso run4 con i
   numeri MISURATI pinna l'esito onesto: NEANCHE l'intersezione per-weekday declina
   T15 (107+92+0+2+144 = 345 >= 50): la famiglia dei test di capacita'
   aggregata e' REFUTA come cura dei 20 boxed, il blocco vive a livello di
   piazzamento esatto. Test di controllo: la regola PUO' declinare (docente saturo
   su 4 weekday su 5). Catena localizzata verde.
2. **Run5 (lanciato 04:52, in corso)**: singolo braccio chain + guard ON. Il run ha
   smascherato un BUG nel raffinamento: `teacherPinnedByWeekday` fondeva i pin di
   OGNI docente in una mappa unica, quindi ogni coppia sottraeva dal proprio residuo
   il carico di TUTTA la scuola (~2500h/weekday) e l'intersezione collassava a 0:
   il guard ha declinato tutti e 6 i pair acconciatura rimasti al Phase 2
   (T18/1-2-G10, T26/1-2-G10). FIX committato (0e7a7818, filtro per
   teacherId) MA il JAR in volo e' quello pre-fix.

### Cosa misura quindi il run5 (onesto)

Con il bug, il run5 e' un'ABLAZIONE PARZIALE: Phase 1 acconciatura pinna solo 86
entita' (54 sessioni overflow declinate), mentre i pin che la diagnosi ROUND 3-BOXED
indica come causa (i blocchi meccanica/informatica di T15 su 1/G08 e G13)
sono INTATTI (meccanica 402, informatica 594, termoidraulica 313 pinnate). Prevista
quindi la stessa firma -42 con i 20 boxed. Se invece cambia qualcosa, e' isolazione
ulteriore. Il run con il guard CORRETTO (post-0e7a7818) e' il follow-up del mattino.

### Run5 completato (07:05, ~134 min): -42hard, i 20 boxed IDENTICI, ablation confermata

| | run5 (guard buggy: acconciatura declinata) | run4 (guard OFF) |
|---|---|---|
| hard | **-42** (H0 -40 + H7 -2) | -42 |
| boxed | 20 (G04 / RA / T15) | 20 |
| full-day | 76,3% | 74,7% |
| ricorrenza unpinned | 70,5% | 69,1% |
| buchi | 0 | 0 |
| wall | ~134 min | ~140 min |
| tentativi 1-4 | -8027/-8033 (CH-only, condannati) | -7781/-7793 |

Lettura: anche togliendo dal pre-assign TUTTI i pin acconciatura (l'over-decline del
bug: Phase 1 pinna solo 86 entita' contro le 332 di run4) il residuo hard resta
ESATTAMENTE -42 con gli stessi 20 boxed, e il quinto tentativo li risolve da solo
come in run4. Combinato con la diagnosi ROUND 3-BOXED e l'esito onesto del test di
intersezione (345 >= 50), il quadro e' ora completo e coerente:

1. i 20 boxed NON dipendono dai pin acconciatura (run5 li toglie, nulla cambia);
2. dipendono dai pin meccanica/informatica/termoidraulica di T15 (RUN4 li ha
   tutti, run5 PURE: meccanica 402, informatica 594, termoidraulica 313) sui
   giorni-L di 1/G08 e G13;
3. NESSUN test di capacita' aggregata (annuale round 4, per-weekday intersezione qui)
   li prevede: il blocco e' a livello di piazzamento esatto (VRP/M20/H7).

**Stato al mattino per maintainer**: il guard raffinato (intersezione per-weekday, con il
fix del filtro per-docente 0e7a7818) e' committato e testato ma il run con il guard
CORRETTO non e' stato eseguito (il run5 e' partito col JAR pre-fix). Dato che
l'intersezione onesta NON declina il caso (345 >= 50), il run col guard corretto
atteso e' identico a run4 (-42): il follow-up utile NON e' un altro run del guard
ma un probe di piazzamento esatto (perche' ExactFinisher depth<=1 non trova cella
per la RA di T15 nei lun/ven liberi dove G04 ha quota-spare).

## ROUND 4-PROBE (TASK-510, laptop 17/08 mattina): perche' la RA di T15 non ha cella

Domanda del mandato: perche' ExactFinisher depth<=1 non trova cella per le 20 ore RA
(Recuperi ed Approfondimenti, G04, T15) nei lun/ven liberi dove G04 ha
quota-spare. Metodo: ricostruzione delle condizioni di fine costruzione run4 dal suo
export finale (ab-on-run4/export.json.gz) + lettura dei filtri reali (VRP
`Lesson.getAvailableTimeslots`, catena gate EF H14/H11/H7/H17/H3), piu' il probe
live (profilo probe510, deriver 15 righe + enumerator 2/3 FEASIBLE + Phase 1/2
COMPLETE, fermato a fine pre-assign: l'UNICO run autorizzato). Test di supporto:
`BoxedRaPlacementProbeTest` (la variante H2 non ha seed reale: documentata, non eseguita).

### Il fatto decisivo: la RA di G04 e' una materia LAB quota-bound

`DailyActivityQuota.QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP` mette "Recuperi ed
Approfondimenti" nel bound-set di G04 (insieme a Lavorazioni meccaniche e Cad).
Il filtro VRP chiama `permits(quota, quotaBoundLabSubject)` che per le materie
quota-bound lab richiede `labHours > 0`: le celle di lun/ven sono AMMESSE solo se
la riga quota del giorno ha ore lab. Sul run4: le righe G04 di lun/ven sono tutte
(aula=6, lab=0) o (0,0). **ZERO celle VRP esistono per la RA di lun/ven**, per
costruzione del modello, non per sfortuna della ricerca. Il golden conferma la
classificazione: colloca la RA di G04 SOLO su mercoledi' (26h) e giovedi' (24h),
mai su lun/ven. La domanda del mandato porta quindi un presupposto falso: lo
"spare" di lun/ven e' spare AULA, e la RA non puo' consumarlo.

### Le due assi del tentativo residuo, entrambe chiuse

| asse | stato nel run4 finale | verdetto |
|---|---|---|
| lun/ven (spare aula) | righe quota (6,0): VRP rifiuta la materia lab quota-bound | STRUTTURALE (VRP) |
| giovedi' (26 giorni-L) | residuo gruppo = 0 (capacita' pienamente consumata) | STRUTTURALE (riempimento) |
| mercoledi' (27 giorni-L) | 34 celle ora-level con stanza gruppo libera E T15 slot-libero (14:30/15:30 su date ott-dic) | **STRUTTURALE (H7)**: T15 e' a ESATTAMENTE 6h (cap) su OGNI mercoledi' |

La riga mercoledi' e' il punto che inganna: le 34 celle sono libere a livello di
SLOT, ma H7 conta le ore DOCENTE del giorno. Misurato sull'export run4: T15
ha 6h su tutti i 34 mercoledi' (174h PINNATE: Lavorazioni meccaniche 66, Controllo
numerico 64, Cad 44; + 30h RA gia' collocate). Ogni piazzamento RA in quelle celle
sfora il cap giornaliero del docente: `teacherDaily >= teacherCap` rifiuta. Il
gate EF fa la cosa giusta e "placed 0 of 20" e' il verdetto CORRETTO, non un bug
di copertura.

### AC#3: STRUTTURALE, non COPERTURA

Non esiste cella legale in NESSUN piano compatibile con i pin della chain: lun/ven
vietati dal VRP (quota-bound lab), giovedi' saturo, mercoledi' sature le 6h/giorno
del docente. La causa e' a monte: il deriver/enumerator pinna le materie
meccanica/CN/Cad di T15 riempiendo TUTTO il budget H7 dei suoi giorni-L, e
lascia zero headroom per l'altra materia quota-bound dello stesso docente (RA 50h,
di cui 30 collocate dalla LS negli interstizi e 20 boxed). La domanda giusta non
e' "perche' EF non trova la cella" ma "perche' i pin consumano il budget del
docente su ogni giorno-L prima che la RA entri".

### AC#4: forma del round 4

**RACCOMANDAZIONE: budget-reservation lato deriver/enumerator.** Prima di pinnare
un blocco su (docente, giorno-L), il pre-assign deve sottrarre dal budget giornaliero
del docente la domanda lab quota-bound NON coperta da rotazioni (per T15 su
G04: 50h RA su ~53 giorni-L ≈ 1h/giorno). Implementazione naturale: nel calcolo
della capienza del docente per il piazzamento (`applyMtcForAttempt`-style, o il
budget del deriver), riservare `quotaBoundLabDemandNonEnumerated(teacher, group) /
labDays` ore per giorno. E' la stessa famiglia logica del TASK-502 guard ma sull'asse
GIUSTO (capacita' giornaliera del docente su giorni-L, non aggregato annuale ne'
intersezione aula per-weekday) e agisce PRIMA del pin, non dopo.

Alternative scartate, con motivo:
- **Altri run del guard TASK-502 (asse aggregato/intersezione)**: REFUTATO dai run.
  La capacita' aggregata passa (345 >= 50) mentre il piazzamento esatto falla:
  l'asse aggregato non vede H7-per-giorno.
- **Slegare la RA dal bound-set lab di G04**: cambio di MODELLO che contraddice
  l'input (il golden pratica la RA solo su giorni-L; la direttiva vincola il modello
  alla correttezza logica, e qui la classificazione e' input-fedele).
- **Accettare -42 come esito deterministico N=5**: fallback dichiarato se maintainer
  ritiene il valore della chain altrove superiore al costo; ma H0 -40 e' binario e
  blocca il gate m-13 del path input, quindi resta l'opzione di ripiego, non la via.
- **Piu' tentativi multi-start / EF depth>1**: inutile, il bacino e' deterministico
  dato i pin (run4 = run5 identici nei boxed) e nessuna cella legale esiste.

Probe live (profilo probe510): costruzione pre-assign riprodotta onestamente con
deriver+enumerator ON (15 righe derivate, 2/3 piani enumerator FEASIBLE, Phase 1
332 + Phase 2 402 pinnate, pipeline COMPLETE), fermato PRIMA del solve. Il profilo
resta sui disco (nessuna distruzione); l'app del probe e' stata terminata.

## ROUND 6 (TASK-516, laptop 17/08 pomeriggio): budget-reservation A/B run6 - GATE FALLITO

Implementazione (1c646a1c + fix 3e08d4e6): reservation = ceil(domanda lab-bound non
enumerata del pair / giorni-L del gruppo), gate H7 del greedy netto della reservation
(floor 1 solo se reservation > 0), flag
`planner.pre-assign.budget-reservation.enabled` DEFAULT OFF, banner verificato nei
log. Unit test del caso probe 5/5, suite completa 1063/0.

Protocollo: `scripts/benchmark/ab-task516-reservation.sh`, baseline-golden appaiato,
maxAttempts=5 default. Entrambi i bracci sullo stesso JAR (build 1c646a1c; i fix di
review eb73654e sono successivi e semanticamente neutri sul seed).

### Risultati

| | control (chain ON, reservation OFF) | treatment (chain + reservation ON) |
|---|---|---|
| hard | **-42** (H0 -40 = 20 boxed + H7 -2) | **-70** (H0 -44 = 22 boxed + H29 -24 + H7 -2) |
| boxed | 20 (tutti RA G04) | 22: RA G04 13 + **Lavorazioni meccaniche G04 9** |
| room-null (H29) | 0 | 24 |
| full-day | 74,6% | 75,8% |
| ricorrenza unpinned | 68,9% | 69,7% |
| buchi interni | 0 | 0 |
| wall | ~129 min | ~182 min |

Il control replica ESATTAMENTE run4/run5 (-42, stessi 20 boxed): il bacino e'
deterministico, terza conferma. Il treatment e' PEGGIORE: -70 hard, i 20 boxed RA
NON curati (13 residui) e 9 ore di Lavorazioni meccaniche NUOVAMENTE boxed, piu'
24 room-null.

Nota di protocollo: la prima cattura del treatment (ab-on-run6) e' STATO DI VOLO
(driver: "WARN capture-run non-zero", analysis -2526 con H29 -2080): il ciclo era
ancora in B-ROOM del tentativo 5 quando il poll ha letto stagedStage nullo tra due
stadi. La cattura valida e' `ab-on-run6-final` (score -70, stagedStage davvero
nullo, polling live fino a quiescenza). Il falsissimo-segnale e' esattamente quello
documentato in CLAUDE.md ("osservare un solve staged").

### Diagnosi del fallimento (perche' la cura flat non funziona)

La reservation taglia il cap GIORNALIERO, ma il greedy pinna BLOCCHI interi (4-6h).
Con cap 6 - 1 = 5, un blocco da 6h non entra PIU' in nessun giorno: il gate rifiuta
il blocco ovunque e l'INTERO pair overflowa al solver. Misurato sull'export finale:
i mercoledi' di T15 passano da 174 ore PINNATE (control) a **0 pinned**
(150 ore collocate dal solver, tutte unpinned), e 9 ore di meccaniche restano boxed.
Inoltre la reservation per (T15, G08) e' scattata a 5h/giorno (log
BudgetReservation: {group 13=1, group 4=1, group 8=5}): il numeratore conta il
corso "Lavorazioni meccaniche" di G08 (110h) come domanda non-enumerata perche'
quel pair in fase meccanica pinnava altri soggetti, e ceil(110/labDays) stritola il
cap a 1. La regola e' troppo grezza su due assi contemporaneamente: granularita'
del blocco e provenienza del numeratore.

### Esito

GATE FALLITO. Il prototipo resta DEFAULT OFF, nessuna bozza di ratifica (AC#5 non
scatta). La direzione del probe TASK-510 resta valida (il blocco e' a monte, nel
consumo del budget H7 dei pin), ma la forma della cura deve rispettare la
granularita' del blocco: candidato naturale per un round 7, CLIP del blocco al cap
nettato (la stessa shape-hygiene del deriver, TASK-502: clip finestra/cap docente)
invece del rifiuto intero, e numeratore limitato ai corsi che NESSUNA fase pinnara'
(materie lab-bound senza tag di rotazione). Entrambe le modifiche sono piccole e
localizzate; nessuna evidenza giustifica altri run prima di entrambe.

## ROUND 7 (TASK-516 riforma golden-guided, laptop 17/08 sera): GATE FALLITO ma il bersaglio RA e' CURATO

Implementazione (58fabfbd + fix clip 1c0a497e): numeratore limitato ai corsi
quota-bound NON-lab (nessuna fase li pinna: il falso G08 del round 6 e' escluso
per costruzione); ritmo DERIVATO dal golden (reservation = cap meno il carico
TOTALE medio del docente nei giorni in cui il golden colloca la materia; banner
dichiarato: "reserved 2h/day (golden avg load 4h on its Recuperi e
Approfondimenti days)", la misura di Fedora replicata dal codice); CLIP invece
del rifiuto (il blocco che non entra nel cap nettato viene pinnato nel prefisso
che entra, log CLIPPED 4-of-6 sui giovedi', coda al solver). Suite 1065/0.
Nota operativa: il primo treatment e' crashato per un bug di integrazione clip
(createTempSession iterava la sessione PIENA contro gli slot clippati,
IndexOutOfBoundsException; run archiviato ab-on-run7-invalid1) e il rerun e'
stato catturato con polling live fino a quiescenza reale (ab-on-run7-final; la
cattura automatica del driver era di nuovo leggermente in anticipo).

### Risultati

| | control (chain ON) | treatment (chain + reservation golden + clip) |
|---|---|---|
| hard | **-42** (H0 -40 = 20 boxed RA + H7 -2) | **-53** (H0 -50 = 25 boxed + H7 -3) |
| boxed RA G04 | 20 | **0** (RA interamente collocata, 29h su mercoledi') |
| boxed altri | 0 | **25 Lavorazioni meccaniche G04** (le code del clip) |
| mercoledi' T15 | 174h pinned (cap 6 saturo) | 174h pinned + RA 29h unpinned (6h su 33 giorni, 7h su 1) |
| full-day | 74,6% (riferimento run6-control, config identica; run7 control non rileggibile per l'FS flaky) | 74,1% |
| ricorrenza unpinned | 68,9% (idem run6-control) | 69,1% |
| buchi interni | 0 | 0 |
| wall | ~123 min | ~150 min |

### Diagnosi onesta

La cura FUNZIONA sul bersaglio del probe TASK-510: la RA non e' piu' boxed (0
contro 20), collocata esattamente dove il golden la pratica (mercoledi'/giovedi',
nelle ore riservate). Ma il budget total non torna: le CODE dei blocchi clippati
(25 ore di Lavorazioni meccaniche) non hanno casa, perche' anche loro sono
quota-bound lab (stesso bound-set di G04) e i giorni-L sono ora pieni (33
mercoledi' a 6h, 1 a 7h = anche 3 violazioni H7). Il box si e' SPOSTATO dalla RA
alle code: -53 contro -42. Non e' shell-game verso un gruppo estraneo (tutto
interno alla stessa coppia docente-gruppo), ma e' comunque un trasferimento, e la
causa e' aritmetica: la domanda TOTALE lab-bound di T15 su G04 sui
giorni-L (mec+CN+Cad+RA) supera la capacita' (cap x giorni-L) se i pin consumano
il ritmo golden SOLO in parte (il clip ha ridotto i giovedi' ma i mercoledi' sono
rimasti a 174h pinned, ritmo 5,1h/giorno: la reservation non ha presidiato il
ritmo su TUTTI i giorni-L, solo dove il blocco non entrava).

### Esito

GATE FALLITO (hard non 0, -53 < -42). Prototipo resta DEFAULT OFF, nessuna bozza
di ratifica. Il round 7 pero' CHIUDE la diagnosi con un dato positivo: la
convivenza golden RA+lab sullo stesso giorno e' RIPRODUCIBILE (0 boxed RA) quando
il headroom esiste; quello che manca e' il vincolo di budget TOTALE del pair sui
giorni-L (somma pin + domanda non-enumerata <= cap x giorni-L, distribuita), non
piu' un problema di singola cella. Il candidato round 8, se maintainer lo vuole: gate
di budget AGGREGATO del pair applicato PRIMA del pinning dei blocchi (declina o
rimappa il blocco in eccesso finche' la somma quadr), la stessa aritmetica del
TASK-502 ma per-pair per-weekday e con il ritmo golden come target, non come
soglia binaria.

## ROUND 8 (TASK-518 gate aggregato + reservation teacher-wide, laptop 18/08 notte): -42 -> -3, H0 a ZERO, gate sfiorato

Implementazione (1bbe4d9a, suite 1068/0): (a) la reservation e' TEACHER-wide
(max sulle pair del docente): l'headroom guadagnato dalla domanda non-enumerata
di UNA pair netta il cap del docente per TUTTI i suoi pin, cosi' i pin degli
altri gruppi non mangiano l'headroom (il difetto round 7); (b) plan sizing
PRIMA del greedy: se il piano della pair supera il budget netto
((cap - reservation) x giorni-L) le sessioni in CODA tornano al solver (mai
azzeramento round-6, mai code orfane round-7); (c) unit test 10/10 con i tre
casi storici richiesti (RA round 3, code clip round 7, azzeramento round 6
vietato). Stesso flag, DEFAULT OFF.

### Risultati

| | control (chain ON) | treatment (chain + round-8 gate) |
|---|---|---|
| hard | **-42** (H0 -40 = 20 boxed + H7 -2) | **-3** (H7 -2 su 3.140 match + H17 -1) |
| H0 boxed | 20 (tutti RA G04) | **0** (DB verificato: zero lezioni non collocate) |
| H29 room-null | 0 | 0 |
| mercoledi' T15 | saturi a 6h | 13 giorni a 4h (ritmo golden), 20 a 6h, 1 a 2h |
| pin T15 | ~380h | 294h pin + 232h al solver (tutte collocate) |
| full-day | 74,6% | 77,5% |
| ricorrenza unpinned | 68,9% | 68,6% |
| buchi interni | 0 | 1 slot (G11 2026-11-06) |
| wall | ~124 min | ~150 min |
| placement-parity (TASK-517) | 23 flag | **20 flag, il flag T15/G04 e' SCOMPARSO** |

Il control replica per la QUINTA volta il bacino -42 (deterministico). Il
treatment porta hard da -42 a -3: H0 zero (RA E code tutte collocate), H29
zero, e il residuo e' H7 -2 + H17 -1, la stessa famiglia di residuo LS-varianza
dei bad-seed storici (TASK-478/481), non piu' un blocco strutturale. Il ritmo
golden e' ora visibile nel piano: i 13 mercoledi' con RA stanno a 4h come il
golden pratica.

### Verdetto

GATE SFIORATO, NON PASSATO: hard 0 e' binario e -3 non e' 0. Nessuna bozza di
ratifica (la regola del mandato e' netta). MA il quadro e' cambiato
qualitativamente: il -42 strutturale della chain e' CURATO (era tutto H0),
restano 3 punti di residuo che la letteratura dei task precedenti attribuisce
alla varianza della LS, non al piano. Il passo naturale (per maintainer, al risveglio):
un round 9 con lo stesso treatment su un SEED DIVERSO (o N=2-3) per stabilire
se -3 e' il pavimento del treatment o un dato del singolo run; in parallelo la
placement-parity mostra che restano 20 flag della famiglia B (T06,
T18,T22: la stessa cura applicata alle altre coppie e' il seguito
naturale del TASK-518, non un nuovo meccanismo).

Nota di protocollo: il driver ha catturato a 03:01 col cap dei 150 minuti
mentre l'ultimo stadio girava; la cattura autorevole e' ab-on-run8-final,
presa a quiescenza confermata (due letture stabili a un minuto di distanza,
stagedStage nullo, score -3hard/-1553970medium; DB: 0 non collocate).

## ROUND 9 (TASK-518, laptop 18/08): CENSORED - il cap dei 150 minuti taglia il treatment prima della quiescenza

Domanda del mandato: -3hard (round 8) e' pavimento della cura o singolo-run
luck? Metodo: treatment identico al round 8 (chain + budget-reservation col
gate) su 3 costruzioni GRASP indipendenti, seed 11/12/13.

Nota tecnica (perche' seed 11-13 e non 1-3): il plain
-Dplanner.construction.grasp.seed viene NORMALIZZATO dal wrapper multi-start
ratificato (TASK-483 forza il tentativo 1 deterministico e reseeda i retry con
seed=tentativo). Prova live: banner grasp=false col -D presente, jcmd mostra la
proprieta' riscritta nel JVM. Il veicolo corretto e' l'hook
-Dplanner.multistart.test.force-grasp-seed=N (verificato: grasp.enabled=true).
Il primo tentativo con seed=1 e' archiviato come ab-on-run9-seed1-invalid.

### Risultati: tre osservazioni CENSORED, nessun finale pulito

| seed | DONE del driver (min 150) | stato persistito alla kill | lettura |
|---|---|---|---|
| 11 | -2118hard | 0 boxed; cattura con H29 -2080 (B-ROOM appena iniziato) | best-so-far timeslot-tier ~-650: bacino peggiore |
| 12 | -2hard | 1 boxed, 10.759 senza stanza | best-so-far -2 MA il solve girava ancora (flush DB precoce) |
| 13 | -2hard | 0 boxed, 2.859 senza stanza | best-so-far -2 MA B-ROOM attempt 5/5 in corso alla kill |

Meccanica del censuramento: il cap di 150 minuti del driver scatta mentre il
quinto tentativo e' in B-ROOM; la score del DONE e' il best-so-far IN MEMORIA
(non lo stato persistito, che resta a un flush precedente); il recovery via
riavvio del profilo in update-mode recupera solo lo stato persistito, non il
finale mai raggiunto. Inoltre col seed forzato TUTTI i tentativi condividono la
stessa costruzione (il wrapper forza lo stesso seed a ogni attempt), quindi il
multi-start perde la diversificazione dei retry e i run durano di piu' del
round 8 (~150+ min contro 124 del control).

### Risposta onesta alla domanda del mandato

NON RISPENDIBILE con questi dati: nessun seed ha raggiunto la quiescenza. I
due best-so-far -2hard (seed 12/13) e il finale pulito -3hard del round 8 sono
compatibili con un pavillo ~-2/-3, ma il censuramento vieta di chiamarlo
pavillo. Il seed 11 mostra che la distribuzione dei bacini NON e' un punto:
esiste almeno una costruzione con residual timeslot-tier ~-650.

### Stato e fermata (regola del mandato: gate fallito = documento e mi fermo)

FILONI 2 e 3 NON avviati. Il follow-up naturale per maintainer: round 9-bis con cap
240 minuti e seed diversi da 11 (o n=2), oppure accettare l'evidenza mista
round8+round9 (tre best-so-far in banda -2/-3 su quattro osservazioni) e
decidere se -3 e' abbastanza per procedere all'estensione del gate sulle altre
coppie (asse A della placement-parity, 11 flag). Review findings 1a/1b/2
applicati nel frattempo (6bbbcbe6); finding differito documentato (asimmetria
pair-view del plan sizing vs teacher-view del greedy, candidato round 10).

## GOLDEN-FREE REHEARSAL (TASK-523, laptop 18/08 sera): la chain sopravvive senza golden

Condizione production ricreata: seed/lessons.csv ASSENTE dal JAR, rotazioni del
deriver CONGELATE come input statico (le 15 righe del run8 inserite a mano),
deriver OFF, enumerator + budget-reservation ON, profilo fresco gf-rehearsal.

### Due fragilita' scoperte (e curate, commit 88604ee2)

Senza golden l'app NON PARTIVA: loadGoldenCoPresenceDepth lanciava RuntimeException
e /api/schedule rispondeva 500. Ora tutti e tre i loader golden
(CoPresenceDepth, Slot0Anchors, BudgetReservation) degradano con warn forte.
Conseguenze dichiarate nei warn: esenzione-ospite H2 INERTE (regressione hard
nota, domanda workbook: profondita' praticata per cella) e ancore slot-0 ridotte
a qualita'.

### L1 piano senza golden: VERDE

4.663 pin, 0 pinnate senza slot, H7 pulito sui pin (solo pseudo-Stage oltre 6,
esente by design). Banner fallback verificato: reservation 1h/giorno (spread
50h/53 giorni-L, clamp del fix review non necessite ma armato). Parity asse A:
21 flag, stessa famiglia del run8 (20), il flag T15/G04 ASSENTE: la cura
sopravvive senza golden.

### L2 distanza piani (golden-free vs golden-live run8)

Tutte le coppie non-T15: pin IDENTICI (stesse ore per coppia). T15:
+28h (G04) +32h (G08) +10h (G13); giorni a 5h contro 4h del run8: la
distanza e' ESATTAMENTE il delta della reservation (fallback 1h vs ritmo golden
2h). Congelare anche il ritmo nel workbook chiuderebbe il gap.

### L3 full solve senza golden: -27hard (controllo run8: -3)

| | run8 (golden-live) | golden-free |
|---|---|---|
| hard | -3 (H7 -2 + H17 -1) | -27 (H29 -15 + H7 -5 + H17 -1 + H0 -6) |
| boxed | 0 | 3 (RA G04) |
| roomless (H29, unpinned) | 0 | 15 |
| full-day | 77,5% | 76,9% |
| ricorrenza unpinned | 68,6% | 68,9% |
| buchi | 1 slot | 1 slot |

Lettura: la sopravvivenza e' REALE (tutto in banda, KPI quasi identici), il
costo del golden mancante e' concentrato e previsto: H29 -15 (l'esenzione-ospite
inerte, la regressione documentata), H7 -5 e 3 RA boxed (il ritmo 1h invece di
2h). Nessuna nuova frizione oltre il censito TASK-452.

### Proposta workbook (per maintainer, NON applicata)

1. Congelare le ROTAZIONI derivate (15 righe, foglio dedicato): sostituiscono la
   lettura runtime del golden nel deriver.
2. Congelare IL RITMO per (docente, gruppo) con domanda lab-bound non-pinnata:
   il carico totale praticato dal golden sui giorni di quella materia (per
   T15/G04: 4h). Chiude il delta L2 e le 3 RA boxed.
3. La profondita' co-presenza praticata per cella (tag, weekday, slot): blocca
   la regressione H29 (giu' censita come domanda bloccante del TASK-452).

## EPILOGO (maintainer, 18/08 notte): la chain NON FRUITBILE come miglioramento; il control resta il campione

Decisione dopo il verdetto golden-free (-27): il control path (production di
oggi) resta il campione; la meccanica chain resta committed flag OFF come
capacita' per il giorno in cui la scuola dichiarera' rotazioni nel workbook.
Nessun altro round.

### La serie completa, onesta

| run | configurazione | hard |
|---|---|---|
| control con golden (x5 repliche) | production | **0** (deterministico, tentativo 1) |
| chain r3-r5 (bacino) | deriver+enumerator | -42 (20 boxed RA, deterministico) |
| chain r6 | + reservation flat | -70 |
| chain r7 | + ritmo golden + clip | -53 |
| chain r8 | + teacher-wide + plan sizing | **-3** (H0 zero) |
| chain golden-free (rehearsal) | r8 senza golden (rotazioni congelate) | -27 |
| control SENZA golden (m-13, solo mappa co-presenza congelata) | production senza chain | **-25** (H29 termoidraulica, 0 boxed) |

Conclusione: in NESSUN round la chain ha battuto il control. La famiglia B
(piani piu' rigidi del golden) e' confermata dal parity check (TASK-517) ed e'
la causa comune dei fallimenti r3-r7; la cura golden-guided (r8) ha chiuso H0
ma non raggiunto lo 0hard binario.

### La prova m-13 (filone 1 del mandato di chiusura)

Il control SENZA golden, con la SOLA mappa co-presenza congelata come input
statico (seed/golden-copresence-depth.csv, generata dal loader reale con
identity gate): **NON arriva a 0hard**. Best -25hard su 5 tentativi (122 min
contro i 23 min dello 0hard deterministico col golden). Composizione: H0 ZERO
(0 boxed), H29 -25 = Laboratorio Termoidraulico (G11 14 + G03 11),
saturazione della singola stanza termoidraulica. La mappa congelata HA fatto il
suo lavoro: acconciatura room-null ZERO (l'esenzione-ospite funziona col
congelamento; era la regressione -15 della rehearsal chain). Cosa manca oltre
alla mappa: NON slot0 (degrada a qualita'), NON constrained-teacher (gia'
source=input di default); manca la traiettoria di qualita' che il golden
insensitive-guidava (il termine noto: full-day 71,0% contro 87,5%). Il residuo
room-tier e' variabilita' di ricerca che il golden-live evitava per indirizzo,
non per vincolo. Non si itera oltre (decisione).

## M13-FINAL (laptop, notte 18/19 agosto): 0-HARD SENZA GOLDEN. m-13 CHIUSO POSITIVAMENTE

Obiettivo notturno di maintainer: "fino a che non abbiamo 0hard senza golden non
abbiamo ottenuto niente". Ottenuto.

### Censimento dei lettori golden runtime (filone 0)

| lettore | path control? | stato |
|---|---|---|
| loadAllGoldenProfiles (constrained-teacher) | si', ma source=input e' il DEFAULT | INERTE |
| loadGoldenSlot0Anchors | vivo | **CONGELATO** (questa notte) |
| loadGoldenCoPresenceDepth | vivo | **CONGELATO** (notte 18) |
| buildMonoRoomCadence (TASK-449) | flag OFF | INERTE |
| BudgetReservation.loadGoldenLoad / LabRotationDeriver | chain-only, flag OFF | INERTE |
| GoldenScheduleImportService | endpoint esplicito di import | non e' path runtime |

Nessun lettore non censito vivo sul control: le due letture censite dal TASK-452
erano davvero tutte.

### Il run decisivo (profilo m13-gf2, golden FISICAMENTE assente dal JAR)

Configurazione: control path standard (zero flag chain), co-presenza congelata
(136 celle) + ancore slot-0 congelate (67 righe, 14 gruppi), banner FROZEN
verificati per entrambi, identity gate verdi.

| | control col golden | control golden-free (M13-FINAL) |
|---|---|---|
| hard | 0 (deterministico, tentativo 1, x5 repliche) | **0 (deterministico, tentativo 1)** |
| medium | ~-1,03M | -1.028.908 |
| full-day | 87,5% | **87,8%** |
| ricorrenza unpinned | 74,2% | 74,2% |
| buchi interni | 0 | 0 |
| wall | ~23 min | ~15 min (tentativo 1 solo) |

Il golden-free e' INDISTINGUIBILE dal golden-live: l'ipotesi della notte era
esatta (le ancore erano l'ultimo indirizzo che mancava; il -25 H29 di ieri sera
era la traiettoria di ricerca senza bussola, non un vincolo).

### I congelamenti workbook DEFINITIVI per il path golden-free

1. Profondita' co-presenza per cella (tag, weekday, slot): seed/golden-copresence-depth.csv,
   136 celle (sblocca l'esenzione-ospite H2).
2. Ancora slot-0 per (gruppo, weekday): seed/golden-slot0-anchors.csv, 67 righe
   (docente modale della prima ora; ordina il blocco prima-ora).

Entrambi generati dal loader reale con test di identita' (rigenerarli dal golden
finche' esiste; dopo la scomparsa del file restano input dichiarati, forma
workbook, da far confermare alla scuola).

Nota tecnica: con il golden presente i due loader leggono il CSV e i file
congelati restano dormienti (il fallback scatta solo se il CSV manca): percio'
il path di produzione di OGGI e' byte-identico a ieri, e il path senza golden
e' ora verificato end-to-end.
