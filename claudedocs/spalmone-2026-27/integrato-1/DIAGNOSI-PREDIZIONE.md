# PREDIZIONE diagnosi H7 residuo (Fedora, pre-registrata; laptop la riporta PRIMA del banco)

> il census dira' DONOR-TOL o NO-CANDIDATE (nessun paio legale per quell'overcap
> su questo stato) e il banco NON chiude: la famiglia 620 ha una forma non coperta
> sul bacino integrato. In quel caso: dettaglia la forma (quale docente/giorno,
> quale gate, quale sarebbe la mossa mancante) nel VERDICT: e' la specifica del
> prossimo task.

Esito misurato: VEDI SOTTO.

## ESITO MISURATO (step41, replay sullo stato consegnato, heap 16g): PREDIZIONE CONFERMATA al ramo "banco NON chiude"

- Il banco riproduce il -4 e NON chiude: HRF re-run (tutte le braccia + h7cap
  default ON) targets=2 slides=0 moves=0, hard -4 -> -4.
- Census H7CAP: **TARGET T13 2027-01-20: 7 ore (cap 6)**;
  {HRF-H7CAP-SOURCE-YIELD=8, HRF-H7CAP-NO-CANDIDATE=1}: tutti gli 8 candidati
  export cadono sul SOURCE-YIELD, il resto e' NO-CANDIDATE.

## La forma non coperta (la specifica di TASK-621), dettagliata

Le 7 ore di T13 il 2027-01-20 (tutte unpinned, MISTE su TRE gruppi):
  8-9  Relazione con il cliente (G06)
  10   Comportamento Sociale (G01)
  11   Educazione Civica (G05)
  13   Comportamento Sociale (G01)
  14-15 Relazione con il cliente (G10)

PERCHE' il paio 620 non scatta: ogni candidato export cerca una CODA
stesso-gruppo che non sfori la quota del giorno di destinazione. Le ore di
T13 sono su G06 / G01 / G05 / G10: ogni giorno-sorgente e'
l'overcap-day stesso, e il gate SOURCE-YIELD (mid-day export richiede yield
tollerante del giorno sorgente) rifiuta: i giorni sorgente sono a quota piena
(dich 6 con 6 ore per G06/G05/G10 tipicamente) e NON possono cedere
senza donatore; ma la DONOR-TOL del paio e' SAME-GROUP sul gruppo del candidato
(esporta un'ora di G06 -> il donatore dev'essere di G06): la stessa
strettezza che il cieco aveva su A3 (i donatori tolleranti tail-only sono
rari nei gruppi a quota piena).

La MOSSA MANCANTE (non esprimibile dal paio 620): una delle 7 ore deve lasciare
il giorno VERSO UN ALTRO GRUPPO-day in coda, con il giorno sorgente riparato
da un donatore CROSS-GROUP o da uno slide interno che compatti senza toccare
la quota (per esempio: le 14-15 di G10 potrebbero scivolare su un altro
giorno di G10 con capienza; oppure le due ore NON-ADIACENTI di G01 @10 e
@13 con G05 @11 nel mezzo suggeriscono che la RICOMBINAZIONE INTRA-GIORNO
fra i tre gruppi e' la vera forma: T13 lavora 8-15 per tre classi in
parallelo, la scala del golden).
- Firma del bacino: e' ESATTAMENTE la firma del braccio A3/braccio B endgame
  (T26 7h su A3, T13 qui): overcap da TRASCINAMENTO PARALLELO
  multi-gruppo dello stesso docente-cerniera, non da un giorno sorgente mal
  piazzato.
- La specifica TASK-621: la famiglia che manca e' l'equivalente H7 del
  ROTATION-WITH-PERMISSION v0.6: una catena docente-cerniera che riordina le
  ORE PARALLELE del docente condiviso fra i gruppi NEL GIORNO (o il donatore
  CROSS-GROUP per la riparazione), non un semplice export+donatore same-group.

## VERDICT diagnosi (tre righe)

1. IPOTESI: census DONOR-TOL o NO-CANDIDATE, banco non chiude, forma non coperta.
2. MISURATO: confermato con census {SOURCE-YIELD=8, NO-CANDIDATE=1} su
   T13 2027-01-20 (7h/6 su TRE gruppi in parallelo 8-15): il paio
   same-group export+donatore non havie perche' ogni giorno sorgente e' a
   quota piena e i donatori tolleranti same-group non esistono su questo stato.
3. RACCOMANDAZIONE: TASK-621 = H7-parallel-relay: catena docente-cerniera per
   overcap multi-gruppo (la firma T26/T13), donatore cross-group o
   riordino intra-giorno. Il -4 dell'integrato e' FAMIGLIA (non innesco): il
   banco non chiude sullo stato consegnato.

## CODA: suite integrale su d6ab4fd5 (la certificazione finale della configurazione completa)

Predizione Fedora: 1345/0/0/84 EXIT 0.
Misurato: **Tests run: 1345, Failures: 0, Errors: 0, Skipped: 84, EXIT CODE 0,
BUILD SUCCESS** (heap 12g, finestra dedicata, log suite-d6ab4fd5.log.txt).
CONFORME alla predizione al numero esatto: la configurazione di produzione
completa (bundle + 619 + 620) e' CERTIFICATA.
