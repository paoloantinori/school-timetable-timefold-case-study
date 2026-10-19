# TASK-654 - Blind puzzle sul near-miss di integrato-7

Data: 23/09/2026. Input: `integrato-7/export.json.gz` (room-faithful, 12.551
lezioni, consegna `[-1/0]hard`). Residuo: UNA H17, buco interno 1h, gruppo 1
FC, venerdi' 2026-11-20 ora 11 (giorno 8,9,10,_,12,13).

## 1. Decomposizione

`residual_decomposition.py` non classifica la cella (emerge solo il proxy H7:
9 giorni da 8h di T01, cap 8 autoritativo, legittimi). La cella e'
stata estratta a mano da `lessons.txt` e ribadita dall'export.

## 2. Il puzzle

`PUZZLE.md` in questa directory: regole hard (H1/H5/H7/H17/H20/H23/H26/H31,
stanze), stato slot-per-slot del giorno e dei quattro docenti vicini, corsi del
gruppo, quote della settimana, stanze libere alle 11, goal = delta hard.

## 3. Esito cieco (passata separata sul solo testo del puzzle)

Non c'e' cura LOCALE: i quattro docenti confinanti (T25, T14,
T23, T06) sono tutti occupati alle 11, quindi ne' lo slide della
coda ne' una rotazione intra-giorno possono chiudere il buco. Qualsiasi cura
richiede un IMPORT cross-date (spostare nel buco una lezione G04 di un altro
giorno) o uno SWAP profondo. Cercando i donatori:

- **T15 / RA (Recuperi ed Approfondimenti)**: 26 donatori a bordo-superiore
  di giorno (es. mer 18/11 ora 13), docente libero alle 11 e a 3h/6. MA la
  materia e' quota-bound lab del gruppo e il venerdi' G04 ha quota lab = 0:
  H23 vieta TUTTE le import RA. (Nota: `course.isLab = false` nell'export, ma la
  classificazione H23 usa `QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP`, che per G04
  include RA: il proxy isLab era sbagliato, il sorgente ha deciso.)
- **T15 / Lavorazioni meccaniche o Cad**: alle 11 non e' libera NESSUNA
  stanza con tag meccanica: tutte le mosse LavMecc muoiono sulla stanza.
- **T05 / Matematica e T24 / Disegno**: docenti a 0 ore il 20/11, nessun tag,
  nessuna vacation. Donatori legali con esportazione a bordo-superiore che
  lascia il giorno d'origine contiguo e partente alle 8 (H31-safe):
  **11 donatori Disegno (lunedi' ora 12, giorno 8-12) e 1 Matematica**.

CURE TROVATA (1 mossa): **Disegno (T24), lunedi' 16/11 ora 12, R01 ->
venerdi' 20/11 ora 11**, stanza libera: R02 (cap 15, sotto i 18
alunni: costo M3 medium) oppure un lab libero (costo M-LF medium).

Delta del punteggio, contato slot-per-slot sul capture:
- H17 G04 20/11: -1 -> 0 (giorno 8-13 contiguo).
- H17/H31 donatore 16/11: il giorno passa da 8-12 a 8-11, contiguo, parte alle
  8: zero violazioni nuove. Il gruppo scende a 4h = pavimento H33 esatto.
- H1/H5/H7: T24 a 0h il 20/11, cap 6: pulito.
- H20/H23/H26: venerdi' aula aperta, Disegno non quota-bound, quota presente.
- Stanza: nessuna aula libera da 18 posti -> M3 medium (o M-LF su un lab).
  Zero hard nuovo.

**DELTA HARD = +1 (la violazione si chiude), a 1 mossa, con costo medium
piccolo. NO SOLUTION e' FALSO: la cura esiste.**

## 4. Arbitrato

Verifica meccanica completa sull'export (scan generalizzata: 40 candidati
passano H1/H5/H7/H17/H23/VRP, 12 sopravvivono anche al gate H31 sull'origine;
tutti Disegno/T05). La mossa 16/11@12 -> 20/11@11 e' la piu' vicina (3 giorni).
Grado di certezza: ALTO sui vincoli hard (aritmetica completa slot-per-slot dal
capture room-faithful); medio sul costo medium esatto (M3 vs M-LF, nessuno dei
due tocca l'hard).

## 5. VERDETTO DI CLASSE: (b) - cura dentro la grammatica HRF (619) che la
consegna ha mancato, con la causa probabile LOCALIZZATA nel SOURCE-GATE

La cura e' esattamente la forma dell'import cross-date dello stesso gruppo di
TASK-619 (braccio del HoleRelayFinisher, RATIFIED, default ON: nessun codice
nuovo da inventare). Perche' la consegna l'ha mancata:

- Il SOURCE-GATE del 619 (`HoleRelayFinisher`, righe ~1035-1086) valuta la
  giornata donatrice con `yieldLine = max(quota del foglio, floor H33)`;
  una giornata non-surplus richiede un backfill, e il backfill richiede a sua
  volta una giornata surplus (che per lo stesso gruppo non esiste).
- Per G04 questo e' STRUTTURALMENTE MORTO: nella consegna ciascuno dei 140
  giorni G04 ha ore <= max(quota, floor) (distribuzione ore-yieldLine:
  83 giorni a 0, 54 a -1, 3 a -2). ZERO giorni surplus. Quindi ogni candidato
  import muore a `HRF-IMP-SOURCE-GATE`, sempre, per questo gruppo.
- Eppure il gruppo e' POOL-CAP (TASK-497): il suo target NON e' la quota del
  foglio ma il pool (750h). H32 esclude esplicitamente G04/G08: lasciare il
  lunedi' a 4h non paga NESSUN hard, solo M20 medium. La linea di yield del
  SOURCE-GATE per i gruppi pool-cap dovrebbe essere il target per-data
  pool-cap (la stessa fonte di `expectedHoursCapFor`, TASK-577), non la quota
  del foglio. E' la STESSA classe del bug gia' curato dal 497/577 lato score:
  qui sopravvive nel gate del finisher.

Cura indicata (per il task di meccanizzazione, NON eseguita qui): nel
SOURCE-GATE del 619 sostituire la quota del foglio con il cap pool-cap
per-data (min gia' fatto da `expectedHoursCapFor`) per i gruppi nella mappa
M20-cap. Con quella linea, lunedi' 16/11 (5h) diventa donatore valido e
l'import a 1 mossa passa l'oracolo improve-or-noop full-hard.

Fiducia sulla causa esatta: ALTA ma non certificata dai counter. Il log
dell'app di integrato-7 (`/tmp/planner-prod.log` sul laptop) e' stato sovrascritto
dal restart delle 12:23 del 23/09 e `driver.sh` non ha copiato `app.log` nella
directory del capture: il censimento `HRF-IMP-SOURCE-GATE` della notte non e'
piu' leggibile. La catena aritmetica (zero giorni surplus -> gate morto -> cura
bloccata SOLO li') e' comunque completa sui dati. TENTATIVE il dettaglio che un
backfill alternativo non esista (irrilevante: il backfill dev'essere
stesso-gruppo, e nessun giorno stesso-gruppo e' surplus).

## 6. Nota di processo (side-finding)

Il log dell'app notturna e' peribile: `driver.sh` copia `/tmp/planner-prod.log`
in `$DIR/app.log` SOLO dopo il loop DELIVERED, e un restart dell'app (12:23)
sovrascrive il log prima. Vale la pena copiare il log nel capture SUBITO dopo
il solve, non dopo il loop del driver.

## 7. Prossimi passi proposti

1. Aprire il task di meccanizzazione: SOURCE-GATE 619 pool-cap-aware (la cura
   e' un cambio di fonte del target + banco sul replay di questo stato: la
   mossa e' pre-registrata qui).
2. Ripetere il check su G08 (l'altro gruppo pool-cap): anche li' il braccio
   import potrebbe essere morto con la stessa meccanica.
3. Side-fix: copia del log nel capture (driver.sh) per non perdere i census.

Nessuna modifica al codice di produzione in questo task (solo docs).

## CORREZIONE 23/09 pomeriggio (Federa, dopo il banco TASK-656): LA CURA ERA ILLEGALE

La cura pre-registrata (import Disegno T24 lun 16/11@12 -> ven 20/11@11) e' VRP-ILLEGALE:
T24 ha una VACATION su tutto il venerdi' 20/11 (lavora solo lunedi' e martedì, regola
ricorrente; verificato su teacherAvailabilities dell'export). L'arbitratore del cieco ha
saltato l'asse disponibilita' docente: la memoria blind-puzzle-full-constraints impone H5 e
non e' stato applicato (secondo incidente della famiglia dopo il precedente G13).
L'enumerazione completa post-correzione (H1 busy + H5 vacation + VRP): sopravvivono
solo 222 candidati, TUTTI T15 (unico docente G04 libero e disponibile quel
venerdi'), e i suoi soggetti muoiono tutti: RA quota-bound lab (venerdi' quota lab 0,
H23), Lavorazioni meccaniche e Cad senza stanza meccanica libera alle 11.

VERDETTO DI CLASSE CORRETTO: la cella e' STRUTTURALE per il braccio import in questo
stato (nessun docente legalmente disponibile con materia collocabile). La diagnosi del
SOURCE-GATE resta valida come bug di coerenza (yield-line quota-foglio vs pool-cap per
G04: zero giorni surplus su 140), curata come coherence fix (regression-identico su
m39-off -18->-7 e integrato-1), ma non dimostra positivo su QUESTA cella perche' la
cella non ha cure legali nel braccio. TASK-656 aggiornata di conseguenza.

LEZIONE DI PROCESSO: l'arbitratore dei blind puzzle DEVE verificare meccanicamente
teacherAvailabilities (VACATION) oltre a H1/H7/H17/H31/H33: e' il secondo caso in cui
l'arbitraggio manuale dichiara legale una mossa con docente non disponibile.

## VERDETTO v3 23/09 sera: CATENA TROVATA, CLASSE MANCANTE IDENTIFICATA

Il cieco v3 (puzzle completo: R0 membership + R3b gruppo + griglie integrali gio/ven)
ha trovato la catena a 3 mosse che l'arbitraggio conferma LEGALE con una correzione
di placement: (1) G03 Gestione azien (T06) ven@11 -> gio@13; (2) G03
Laboratorio Tecnologico (T22) gio@13 -> ven@11 [CORREZIONE: in LAB. TERMOID, non
R04: la materia e' tag-bound termoidraulica nel modello reale, e la stanza e' libera
tutto il venerdi'; il puzzle v3 non elencava il suo tag, il cieco l'aveva letta untagged];
(3) G04 Informatica (T06) ven@13 -> ven@11. Buco chiuso, zero hard nuove,
medium delta <= 0 (il doppione Informatica di T06 arretrato di un'ora).

LA CLASSE MANCANTE: catena di SBLOCCO CROSS-GROUP. La struttura: il tail-slide del
giorno ferito (Informatica @13->@11) e' bloccato solo dall'impegno @11 del suo docente
in UN ALTRO gruppo (G03); una swap CROSS-GIORNO dentro il gruppo di quell'impegno
(Gestione ven@11 <-> LabTe gio@13) libera il docente senza danni (conto invariato,
contiguita' preservata, docente dello sblocco libero entrambi i lati). NESSUN braccio
esistente la esprime: HRF/x-swap sono same-group; il 638 fa swap same-group su target
delle violazioni (la coppia G03 non e' target); CSF/EDF non incatenano cross-group.
Meccanizzazione: TASK-657 (banco-first, replay della mossa pre-registrata qui sopra).

Iterazioni del loop: v1 cieco illegale (T24 VACATION non nei dati) -> v2 regole
mancanti (membership/H3, mie) -> v3 completa. Ogni iterazione ha esposto esattamente
un difetto: il metodo paga in iterazioni cio' che risparmia in bias.

## CORREZIONE FINALE 23/09 sera: ANCHE LA CATENA v3 E' ILLEGALE (T22 VAC)

Il probe forced657 (modalita' banco che applica le 3 mosse raw e rilancia il recompute)
ha dato la prova meccanica: il value range della lezione LabTe di G03 ESCLUDE
ven@11 perche' T22 ha Vacation 08:00-16:30 su tutto il venerdi' 20/11 (pattern
lun/mar/gio, 70 VAC: 35 Wed + 35 Fri). Terza Vacation della stessa famiglia in tre
iterazioni (T24 v1, membership v2, T22 v3). La colpa della fetta mancante stavolta
e' di chi ha scritto il puzzle v3: la riga R2 dichiarava 'all other teachers listed: no
unavailability' SENZA verifica. VERDETTO DEFINITIVO: la cella e' STRUTTURALE con prova
meccanica: ogni catena sblocco muore sullo stesso muro (partner ven-disponibili tutti
occupati @11: T03/T10/T11/T15-altre-ore; partner liberi @11 tutti
ven-indisponibili: T24/T22/T05/T04 full-day, T11 occupato). Nessuna
classe di mosse la cura con questi input; la cura passa dalla scuola (altro docente
disponibile il venerdi' o cambio pattern), non dalla grammatica.

CHE CHIUDA IN POSITIVO: (1) il fix del resolver H17 (justification gruppo+data ->
target) resta e cura una cecita' reale del 638 (le ferite H17 erano INVISIBILI: zero
target, walk morto prima di valutare); (2) il braccio unblock 657 resta prototype
default-OFF: meccanicamente sano (576 candidati generati, oracle correttamente
esauriti su questo stato), nessun positivo dimostrato su questo bacino; (3) la regola
delle fette input e' ora rafforzata: MAI asserire 'nessuna indisponibilita' di default,
portare la disponibilita' dei docenti entro DUE hop dalla catena.

## VALIDAZIONE SINTETICA (TASK-657 unblock, 23/09 sera, laptop)

Capture sintetica `integrato-7-synth/export.json.gz` costruita dal export
integrato-7 (gzip+json via python heredoc): rimosse 35 righe da
teacherAvailabilities (T22 AND type==VACATION AND venerdi', weekday==4 da
startDateTime: NON committata, 93MB over-cap).

**PREDIZIONE PRE-REGISTRATA**: il finisher applica la catena UNBLOCK (swap
Gestione aziendale <-> Laboratorio Termoidraulico in G03 + slide Informatica
G04 ven@13 -> @11) e chiude -1 -> 0.

**ESITO: PREDIZIONE MANCATA.** Banco steepest exhaustivo (100000 candidates
cap): baseline -1 (H17 G04 2027-11-20, stessa cella di integrato-7), il
braccio unblock e' ARMATO e valuta 576 candidati unblock + 91873 room-variant
+ 7850 plain, ma NESSUNA catena migliora: applied=0, hard -1 -> -1 in 2357ms.

Censimento: zero=294, negative=7556, best plain delta 0hard/0quasi.

**CAUSE DA INDAGARE (per Fedora, non diagnosticate qui)**:
1. La cella 2026-11-20 e' un VENERDI': la rimozione delle VACATION di
   T22 venerdi' e' necessaria ma forse non sufficiente (altri docenti
   coinvolti nella catena potrebbero avere propri vincoli di disponibilita').
2. Il braccio unblock valuta 576 candidati: o la grammatica non esprime la
   forma della cura specifica (catena a piu' mosse?), o la cura esiste ma
   paga vincoli che l'oracolo full-hard vede come non-migliorativi.
3. Il banco usa stanze CH-only: se la cura richiede riassegni stanza
   specifici (la prediction dice "con eventuali riassegni stanza"), il
   greedy potrebbe non trovarli.

NON committata la capture (93MB). Il banco e' BUILD SUCCESS: l'infrastruttura
sintetica funziona, l'esito e' onesto zero-improvement.

## VALIDAZIONE SINTETICA COMPLETATA 23/09 sera: LA CATENA E' MECCANIZZATA E VALIDATA

Dopo il primo tentativo mancato del laptop (mutation dell'export JSON invisibile: il fixture
semina le disponibilita' dal SEED reale, non dall'export) e la seconda miss per le tre viste
disponibilita' incoerenti (VRP static / H5 fact-list / DB), la forma corretta del sintetico
e' il flag banco -Doffrep.synth.dropT22Fri=true che droppa le 35 VACATION di venerdi'
di T22 da TUTTE e TRE le viste (DB + static + facts list della soluzione).

RISULTATO (banco steepest, braccio unblock DEFAULT ON, codice con tutte le cure
/simplify+code-review): applied UNBLOCK chain: [Gestione aziendale <-> Laboratorio
Termoidraulico in G03] + [Informatica -> 2026-11-20], delta=1hard, hard -1 -> 0 in 21s.
ESATTAMENTE la catena pre-registrata del cieco v3, con il riassegno stanza (l'H2 R04
vs Scienze G10 curata dalla sonda del braccio: la correzione F1 della review e' cio'
che rende la catena applicabile). 676 candidati generati, la steepest seleziona la cura.

IL CERCHIO COMPLETO: cieco v3 trova la classe -> arbitrato scopre la Vacation T22
(strutturale sul bacino reale, prova meccanica) -> meccanizzazione -> tre tornate di cure
(/simplify 4-reviewer, code-review 8-finding) -> validazione sintetica con predizione
pre-registrata CENTRATA. La ratifica bounded-no-op ha ora il suo positivo dimostrato
(su input sintetico: il bacino reale resta strutturale per input, cura = scuola).

LEZIONE STRUMENTALE (banco): mutare l'input di un capture non si fa lato JSON (il fixture
riseeda dal seme reale) ne' lato DB solo (tre viste): la via canonica e' il flag banco con
le tre coerenze. Il costo odierno di questa scoperta: tre tornate, tutte registrate qui.
