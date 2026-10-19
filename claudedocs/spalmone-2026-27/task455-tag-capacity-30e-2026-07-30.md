# TASK-455: misura preliminare capacita' per-tag sul run 30e (2026-07-30)

**Mandato (maintainer, 2026-07-30):** prima di una riga di codice, misurare se le 169
ore assegnate senza aula (H29) sono concentrate su poche coppie (tag, weekday)
(attesa: giovedi' acconciatura) o diffuse. Se concentrate, il vincolo di
capienza per-tag del TASK-455 ha un bersaglio preciso; se diffuse, la leva e'
un'altra.

**Verdetto: CONCENTRATE. Il TASK-455 ha un bersaglio netto.** Due tag, entrambi
lab a stanza unica (cap 1), coprono 168/169 = 99% del deficit:
acconciatura 144 (85%) + termoidraulica 24 (14%) + meccanica 1. informatica
(cap 2) e saldatura (cap 1) hanno excess ZERO.

Strumento: `scripts/analyze/tag-capacity-excess-kpi.py` (riutilizzabile per il
confronto 30e vs 30f). Input: `certification-2026-07-30e/export.json.gz`.

## Chiave di cella e auto-controlli (nota di metodo)

- Cella = `(date, startDateTime)`. `timeslot` NON ha `slotIndex` in export.json
  (l'artefatto storico della spec sez. 2.3): l'ora di inizio e' la chiave.
- Tag richiesto = `lesson.course.requiredRoomTags` (campo runtime, zero drift).
- Capienza(tag) = #stanze assignable (cap>0) con quel tag. R18 cap-0
  esclusa (TASK-456).
- **Filtro H29 scoperto sul dato** (non assunto): placed AND room==null AND
  `not pinned` AND `not pseudo` = **169**, coincide col match count di H29 nello
  score. Tutte 169 sono LAB (requiredRoomTags non vuoto), 0 NON-LAB (come il
  VERDICT: "tutti lab capacity-starved, NON-LAB 0").
- **Causalita':** 0 su 169 H29 sta in una cella con capienza residua del suo tag.
  Tutte 169 sono in celle sovra-capienza. La room-null e' 100% contesa di
  capacita', mai "aula libera ma non assegnata". Riproduce e generalizza il claim
  della spec sez. 2.3 ("142 ore senza stanza, zero in celle col lab libero").
- **Riconciliazione congiunta:** excess totale del modello = 202 = 169 (unpinned
  = H29) + 33 (moda pinned, esente). I conti tornano all'unita'.

Cross-check indipendente (verifica-chiave, nota di metodo di maintainer): le 144
acconciatura si scompongono per (subject, teacher) in tre sotto-cluster puliti,
ciascuno col suo docente vincolato:
- Lavaggio / T18 68 (cluster giovedi')
- Servizio stilistico / T26 60 (cluster mercoledi'/giovedi')
- Barberia / T19 16 (cluster martedi', la **tripletta** "G06 / Barberia /
  T19" individuata indipendentemente dalla misura)

## Tavola (A): le 169 H29 per (tag richiesto, weekday)

```
tag                MON   TUE   WED   THU   FRI   TOT
acconciatura         1    17    52    72     2    144
termoidraulica       0    24     0     0     0     24
meccanica            0     0     1     0     0      1
TOT                  1    41    53    72     2    169
```

Acconciatura non e' solo giovedi': e' concentrata su mar/mer/gio (17/52/72),
picco giovedi'. Termoidraulica e' **tutta martedi'** (T03, G03).

## Tavola (B): modello capacita' (domanda = tutte le placed del tag, excess = domanda - cap)

```
tag            cap   MON  TUE  WED  THU  FRI  cells excess  worst-cell
acconciatura     1     1   17   52   70    2    142    144   2026-12-10 09:00 x2
moda             1     0    1    1   31    0     33     33   2026-09-17 11:00 x1
termoidraulica   1     0   24    0    0    0     24     24   2026-09-15 11:00 x1
meccanica        1     0    0    1    0    0      1      1   2026-11-04 13:00 x1
informatica      2     0    0    0    0    0      0      0   -
saldatura        1     0    0    0    0    0      0      0   -
TOT excess                        202 lesson-hours su 200 celle
```

**Wrinkle moda (importante per il design):** moda ha 33 celle sovra-capienza
(tutte giovedi') ma contribute 0 alle 169 H29. Sono tutte **pinned** (esenti).
Cioe' piu' gruppi hanno lezioni R16 pinnate nella stessa cella di giovedi':
e' un problema di coordinamento della pre-assegnazione (territorio TASK-449),
non TASK-455. Un vincolo pinned-exempt non lo tocca, correttamente.

## Implicazioni per il prototipo TASK-455 (tappa 2)

1. **Bersaglio preciso:** acconciatura (mar/mer/gio) + termoidraulica (mar). Il
   vincolo agisce su due tag a stanza unica, non su un insieme diffuso.
2. **Tier: MEDIUM-ALTO, motivato (non assunto).**
   - HARD sarebbe onesto (fisicamente due classi non stanno in un lab monostanza,
     conferma referente 29/07) MA (a) dichiarerebbe infeasible il golden stesso: la
     scuola accetta 44 sue violazioni acconciatura (30 G14 + G12 14, v.
     backlog TASK-455); (b) sul run 30e creerebbe 202 hard violations che il
     solver deve risolvere spargendo, irrigidendo la ricerca e rischiando di
     peggiorare M20/M15 (il meccanismo con cui la bilanciatura del primo slot ha
     fallito). La capacita' annuale c'e' (acconciatura 719h < 1368 slot), quindi
     lo spreading e' fattibile in principio, ma e' proprio il trade-off da
     misurare al checkpoint, non da assumere.
   - MEDIUM-ALTO (peso in famiglia con i "logical-hard medium" M20/M22 ~1000)
     discouraggia senza vietare: tollera le 44 del golden (pratica della scuola)
     e spinge il run a spargere. Prudente, e coerente col fatto che H29 stesso
     e' hard ma il difetto si manifesta come room-null, non come infeasibility.
3. **Pinned/pseudo-exempt**, come H17/M15/M18: il vincolo conta tutte le lezioni
   del tag nella cella (le pinned occupano fisicamente lo slot) ma penalizza solo
   le unpinned oltre capacita'. Cosi' mira esattamente alle 169 e non genera
   penalita' irrisolvibili sulle pinned (le 3G09).
4. **Flag DEFAULT OFF**, letto LIVE (stessa forma di M-LF / ripiego lab), banner
   in `ScheduleService.refreshSolverStatics`. Nessuna adozione: decisione di maintainer
   dopo il checkpoint.

## Riconciliazione con la storia del TASK-455

La task originale (29/07) temeva l'HARD che dichiara infeasible il golden (44
violazioni, gate risposte scuola su G14/G12). Il prototipo di oggi e'
MEDIUM dietro flag: aggira quel gate (niente infeasibility) e lascia misurare la
leva. Da notare che la misura 30e e' sul RUN (169, gruppi 2/G10 acconciatura +
G03 termoidraulica + Barberia/T19) mentre le 44 del golden sono G14 + 4
FD: stessi tag (acconciatura), gruppi diversi. Il vincolo sparera' su entrambi;
il medium lo tollera, l'hard no.

## Reframe (mandato maintainer, 2026-07-30): il vincolo e' un'istanza, non un meccanismo autonomo

Il M-TC non e' un vincolo isolato in piu': e' il **consumatore "ricerca locale"**
dell'osservazione generale "risorse fortemente vincolate per prime" che la spec
`docs/superpowers/specs/scarsity-aware-assignment.md` articola. Il **rilevatore
di scarsita'** (componente 4 della spec, calcolo pre-solve sugli input) misura a
monte la scarsita' congiunta (risorsa, weekday) e produce la classifica di
criticita'. Il M-TC e' cio' che il rilevatore misura, espresso a valle come
penalita' di ricerca: la cella (tag, data, ora) sovraffollata e' esattamente la
cella che il rilevatore dice critica. Un solo componente produce il segnale, due
consumatori lo consumano in forma diversa (costruzione = ORDINE, ricerca =
POLARIZZAZIONE/PENALITA'); M-TC e' il secondo. Non va letto come "un vincolo per
le aule" ma come l'istanza-di-ricerca del principio piu'-vincolato-per-primo.

### VRP dedicato vs vincolo: scelta motivata (non si prototipano entrambi)

maintainer chiede di valutare se un VRP dedicato sul tag scarso sia piu' pulito del
vincolo, o se serva la coppia. L'aut-aut in gioco e': "questa lezione di lab
occupa la stanza del suo tag in quell'ora, oppure non ci puo' stare".

**Un VRP non riesce a esprimerlo.** Il pattern di casa (VRP + vincolo gemello,
come stage e quote) restringe `Lesson.getAvailableTimeslots()` su fatti STATICI
per-entita': e' stage-day per questo gruppo? ha quota-lab questa data? Nessuno
dei due dipende da dove sono messe le ALTURE lezioni. La capienza di un tag in
una cella e' invece un **aggregato dinamico tra lezioni co-poste**: quante altre
lezioni dello stesso tag sono gia' li'. Un VRP (dominio dei valori fisso,
valutato per-entita) non vede le co-poste, quindi non puo' dire "la cella e'
piena". L'unica approssimazione VRP sarebbe un budget statico per-(lezion,
weekday) che PRE-DECIDE l'allocazione: e' brittle e toglie alla ricerca il
compito di trovarla, che e' proprio cio' che il medium lascia scoprire.

**Il vincolo e' il meccanismo giusto per la ricerca**, perche' la capienza del
tag e' un vincolo di capacita' aggregato, stessa famiglia di H1/H2/H3
(conflitti) e H7 (monte-ore giornaliero), non un fatto statico per-entita.

**Dove il VRP calza, ed e' un'altra via:** sul **consumatore-costruzione**
(spec componente 1, "piu'-vincolato-per-primo" come ORDINE). Pre-booking delle
celle scarse (stile TASK-435: pinnare prima i giovedi' di T01, poi i
giovedi' del laboratorio ereditano l'occupazione) restringe il dominio delle
lezioni RESTANTI. Quello e' un VRP-like deterministico, ma e' un consumatore
diverso (l'ordine di costruzione), gia' enumerato dalla spec, non e'
un'alternativa al M-TC nello stesso lavoro.

**Conclusione:** per la leva di ricerca si prototipa il vincolo (M-TC), non il
VRP. Non sono alternative per lo stesso job: il vincolo serve (l'aggregato e'
dinamico), il VRP completa solo in costruzione (via separata, TASK-435/449).
Questo si scrive, non si prototipa due volte.

## Chiave della risorsa scarsa: la COPPIA (tag, weekday), non la terna (mandato maintainer)

Misurato sul 30e (maintainer, non assunto): quale chiave spiega meglio le 169 ore
senza aula, copertura delle prime 3 celle:

| chiave | celle | copertura |
|---|---|---|
| tag + weekday | 7 | 88% |
| tag + docente | 6 | 85% |
| docente + weekday | 11 | 64% |
| tag + weekday + docente | 11 | 64% |

Aggiungere il docente FRAMMENTA il collo di bottiglia (88% -> 64%): il docente e'
la CAUSA (T01 spiega perche' il giovedi' sia saturo), non la chiave con cui
governare la risorsa. Il rilevatore, il vincolo e qualunque VRP usano la coppia
(tag, weekday). Il vincolo M-TC opera al livello fisico della cella oraria
(tag, startDateTime), piu' fine della coppia: e' l'unita' di capienza (una stanza
tiene una lezione per ora), e concentra il fuoco naturalmente sulle (tag,
weekday) che il rilevatore dice scarse. I due livelli sono compatibili.

## Mercoledi' acconciatura 52h: INDEPENDENTE, non rimbalzo (domanda chiusa)

Il mercoledi' e' il secondo collo (52h) e NON ha un docente vincolato che lo
spieghi (il giovedi' lo spiega T01 solo-giovedi'). Due ipotesi: (a) rimbalzo
del solver dal giovedi', (b) concentrazione indipendente. Misura sul 30e +
golden: **(b) indipendente**, tre evidenze:

1. **(gruppo, materia) condivisi mer-gio = 0.** Le due gole hanno popolazioni
   DISGIUNTE. Mercoledi' e' tutto non-T01 (G06 e G12 Servizio
   stilistico/T26; G10 e G02 Lavaggio/T18). Giovedi' e'
   dominato da T01 (Servizio tecnico e stilistico: G02 24, G10 24,
   G12 12, G06 9 = 69/142) piu' G06 Lavaggio/T18 51 e G14 22.
   Il rimbalzo vorrebbe le STESSE lezioni spostate: non ce n'e'.
2. **Il golden concentra acconciatura sul mercoledi' lui stesso** (166 lezioni:
   G10 100, G06 66). Il mercoledi' e' un giorno strutturale di acconciatura
   nella pratica della scuola, non un artefatto del solver.
3. Il mercoledi' e' risolvibile SPARGENDO entro il giorno: il golden colloca 166
   acconciatura di mercoledi' senza collisioni (ore non sovrapposte); domanda run
   200h < capienza mercoledi' 280h (35g x 8 slot).

**Conseguenza sulla calibrazione:** curare il giovedi' (T01) NON sistema il
mercoledi'. Ma non serve una calibrazione separata: il M-TC penalizza la
sovracapienza di cella oraria su TUTTI i weekday in modo uniforme, quindi agisce
sia su (acconciatura, giovedi') sia su (acconciatura, mercoledi') con lo stesso
peso. L'indipendenza anzi VALIDIDA la chiave (tag, weekday) del rilevatore: ogni
gola e' una coppia a se. Il vincolo medio spinge il run verso il pattern del
golden (ore non collidenti sul mercoledi'), e la capienza annuale c'e' (200<280):
e' un difetto di ricerca, non di modello, coerente con la scelta medium.

Dettaglio celle critiche (excess per coppia tag-weekday): acconciatura/giovedi'
72h, acconciatura/mercoledi' 52h, termoidraulica/martedi' 24h,
acconciatura/martedi' 17h, poi briciole.

### Mercoledi' acconciatura: composizione e causa (chiusura definitiva)

Composizione delle 52h di mercoledi' (lezioni in celle sovra-capienza, tutte
non-T01): G06 Servizio stilistico/T26 29, G10 Lavaggio/T18
27, G02 Lavaggio/T18 25, G12 Servizio stilistico/T26 22, G02
Servizio stilistico 1. Composizione delle 72h di giovedi': G06
Lavaggio/T18 51, T01 Servizio tecnico e stilistico (1+G10, G12,
G06 = 69), G14 Servizio stilistico/T26 22. (gruppo,materia) condivisi
mer-gio = 0.

Causa: NON c'e' un secondo docente vincolato. `availability_rules.csv` blocca
T01 su lun/mar/mer/ven (disponibile solo gio), ma T26 e T18 non
hanno NESSUNA regola di disponibilita' (liberi tutti i giorni). Quindi il
mercoledi' non e' diktat di un docente. E' il template/calendario di gruppo: il
golden stesso colloca G10 (100h) e G06 (66h) di acconciatura di mercoledi'.
Verdetto: ipotesi (b) CONCENTRAZIONE INDIPENDENTE, causa template/calendari
(non rimbalzo, non secondo docente). Curare il giovedi' non sistema il
mercoledi'; il M-TC uniforme li copre entrambi.

### Chiusura definitiva col test della cella template (la prova piu' diretta)

Il template settimanale (dump di fine costruzione, `target/construction-lessons.txt`)
PIAZZA LUI STESSO l'acconciatura di mercoledi' per piu' gruppi, ore di acconciatura
template per (gruppo, weekday):

```
group    MON TUE WED THU FRI
G02     67   0  52  62   1
G06      4  43  57  11  48
G10      2   2  50  65  51
G12    33  10  30  33   0
G14      0   0   0  30   0
```

Mercoledi': G02 52 + G06 57 + G10 50 + G12 30 = 189 lezioni template di
acconciatura. Il collo di mercoledi' nasce NEL TEMPLATE (ogni gruppo ha l'acconciatura
di mercoledi' nel suo template settimanale, coerente col golden), e le collisioni
sono piu' gruppi sullo stesso slot di mercoledi' con una stanza sola. Non e' il
solver che scarica dal giovedi': e' il template che ce la mette. Conferma (b)
INDEPENDENTE, e orienta la cura sul consumatore-costruzione (coordinare i template
cross-gruppo, TASK-449) oltre che sul vincolo di ricerca (M-TC).

### Perche' il template ammucchia 4 gruppi di acconciatura sullo stesso mercoledi'

Tre ipotesi (mandato maintainer), risposta sui dati + codice:

1. **Causa radice = il builder NON conta le stanze.** `grep -ic room
   WeeklyTemplateBuilder.java` = 0: il builder e' cieco alle aule e lavora
   PER-GRUPPO (ogni WeeklyTemplateBuilder usa un GroupWeeklyShape alla volta,
   nessuno stato cross-gruppo). Quindi non puo' evitare la collisione: ogni gruppo
   sceglie mercoledi' indipendentemente, senza sapere che gli altri ci sono gia'.
   E' la terza ipotesi di maintainer, confermata.
2. **Amplificatore = calendari, ma solo per meta'.** Ore attese aula+lab per
   weekday: G06 mer 6,9 (il piu' lungo), G10 mer 5,1 (il piu' lungo), ma G02
   mar/mer/gio pari (6,0) e G12 ha lun/gio piu' lunghi di mer (5,0 vs 4,7). Quindi
   il calendario spinge al mercoledi' G06 + G10, ma NON spiega G02 + G12.
3. **Margine COMODO, non stretto.** I docenti di acconciatura del mercoledi'
   (T18/Lavaggio, T26/Servizio stilistico) sono LIBERI tutti i giorni
   (nessuna regola di disponibilita', contro T01 solo-giovedi'). Quindi
   l'acconciatura PUO' spostarsi ad altri weekday appena il builder ne tiene conto.

**Conseguenza sul consumatore-costruzione della spec:** e' la terza ipotesi, quindi
"basta insegnare al builder a contare le stanze" (coordinate cross-gruppo contro la
capienza dell'unica stanza acconciatura), non serve ridisegnare i calendari. Un
vincolo a valle (M-TC) non disfa una concentrazione che il template ricrea ogni
settimana: per questo servono entrambi i consumatori (costruzione che non crea la
concentrazione + ricerca che sparge il residuo).

## Regola di metodo: checkpoint per MISURARE, DoD+solve per CONFERMARE

Il checkpoint (RoomPhaseSnapshotTest, solo fase aule) e' molto piu' veloce delle
varianti a solve completo, ma NON e' completo: un cambio di tagging tocca vincoli
(M21, M15, S10...) che la sola fase aule non valuta. La DoD su v1 ha trovato un
effetto M21 (golden -169 -> -187) che il checkpoint non vedeva. Regola:
**checkpoint per la MISURA e per scartare in fretta le varianti perdenti; DoD +
solve completo per la CONFERMA prima di adottare.** Non semplificare nella
direzione "il checkpoint basta": e' rapido ma parziale (solo fase aule).





