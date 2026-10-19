# REPORT blind puzzle H28 + esplorazione classi-mossa (notte 23/09, laptop, rc-1)

Mandato: "quelli che non risolvono, passali per blind puzzle; forse esistono
classi di mosse mai considerate". Oggetto: la cella H28 G13 2027-02-12 dello
stato ab-adoption/on (637, export reale). Risultato in una riga: **la cura
esiste, e' UNA swap singola, e' stata trovata a mano dal puzzle e ARBITRATA
JAVA (-1 -> 0-hard sul capture reale); non manca una classe di MOSSE, manca
una classe di TARGET** (la lezione "c" di mezzo dell'A..B..A non e' mai
target). Le classi rc-1 mass/pillar sono censite dal jar: due sono gia'
usabili dal 638 cosi' com'e'.

## FILO 1 - il puzzle

### Costo reale (dai vincoli, non dai nomi)

- H28 (provider 1032-1059): dentro (gruppo, materia, data), coppia (a,b)
  STESSO docente + esiste c STESSA MATERIA strettamente tra loro con docente
  diverso. Il "B" di A..B..A deve essere stessa materia: una lezione di
  materia diversa in mezzo NON viola.
- H1: conflitto docente. H17: buchi interni. H31: inizio giorno al primo slot
  aperto (un buco di testa paga H31). H32: quota giorno >= dichiarata (5h per
  G13; l'eccedenza e' M33, non hard). H5: disponibilita' docente.

### I dati (dal capture on/, tutto verificabile su lessons.txt)

Giorno G13 12/02 (quota 5, modo L): @8 Lavorazioni-T10, @9
Lavorazioni-T15, @10 TecnicheSaldatura-T15, @11
Lavorazioni-T10, @12 TecnicheSaldatura-T15. La violazione: coppia
(D@8, D@11) con c = M-Lav@9 in mezzo.

Le occupazioni DOCENTE del giorno (finestra H1, il vincolo che uccide le swap
ovvie): T15 e' occupato @8 (G08 Controllo numerico), @9, @10, @11 (2
FC Controllo numerico), @12: LIBERO SOLO @13. T10 occupato @8, @9 (G08
Tecnologia Meccanica), @11: liberi @10, @12, @13.

Settimane estratte (08-19/02, tutti i gruppi): T15 insegna quasi ogni
giorno nei due FC (mattine piene) + G13; T10 ha G13 16/02 monografico
(Lav D @8,9,10,12 + Cad-M@11: la forma "giorno del docente" del golden) e
19/02 con la STESSA struttura del nostro giorno malato ma PULITA (vedi sotto).

### Perche' ogni mossa della grammatica attuale fa delta 0 o peggio

- Swap arbitrate dal cieco (D-Lav@8 <-> M-TS@10): M atterra su @8 dove insegna
  gia' G08: H28 +1, H1 -1 = 0. La collisione non era stata vista
  dall'arbitraggio umano.
- D-Lav@11 <-> M-TS@10 o @12: M atterra su @11 (G08): 0.
- D-Lav@8 <-> M-TS@12: M su @8: 0.
- CHANGE M-Lav@9 verso l'unico slot libero di M (@13): buco interno @9: H28
  +1, H17 -1 = 0.
- CHANGE D-Lav@8 -> @10 o @12 (D libero): pulisce H28 ma lascia il buco di
  testa @8: H28 +1, H31 -1 = 0.
- CHANGE D-Lav@11 -> @10: A..B..A resta (M-Lav@9 sempre tra @8 e @10). -> @12:
  resta (M-Lav@9 tra @8 e @12).
- Swap D-Lav@11 <-> M-Lav@9 (stessa materia): pulisce Lav ma... la coppia
  (D@8,D@9) dopo lo swap e' adiacente, pulita; pero' TS resta M@10,M@12 e non
  viola (il between deve essere stessa materia): QUESTA e' pulita? No: D
  atterra su @9 dove insegna G08: H1 -1 = 0.
- Verso altre date (finestra allargata): ogni esportazione d'ora lascia la
  quota del 12/02 sotto 5 (H32 -1) se non rimpiazzata: la lezione
  source-yield della famiglia 619.

### La cura (derivata, poi arbitrata)

**Swap M-Lavorazioni@9 <-> M-TecnicheSaldatura@12** (stesso docente, stesso
gruppo, stesso giorno: le due lezioni si scambiano gli slot). Dopo: @8 Lav-D,
@9 TS-M, @10 TS-M, @11 Lav-D, @12 Lav-M. La coppia (D@8,D@11) resta ma tra
loro ora ci sono SOLO TS: nessuna c di materia Lavorazioni -> H28 chiusa. TS:
M@9,M@10 adiacenti -> pulita. T15 insegna negli STESSI slot di prima
(@9,@12) e T10 e' intatto: H1/H5 identici. Il giorno resta 8-12 pieno:
H17/H32/H31 identici. Delta = +1 hard, costo medium -265 (rumore).

ARBITRATO JAVA (scratch test sul export reale, transcript nel commit message):
baseline hard -1 (H28 1 match) -> dopo la swap **hard 0**, cella residua
nessuna. Il witness di prassi scolastica: il giorno G13 19/02 dello stesso
capture ha la STESSA forma (Lav-M@8, Lav-D@9, TS-M@10, Lav-D@11, TS-M@12) ed
e' PULITO: la scuola stessa usa questa forma.

### Perche' il 638 non la trova: la classe mancante e' di TARGET, non di mosse

La mossa vincente e' una banale SwapMove entro la grammatica (same-group,
same-date, entrambe unpinned, VRP-legali). Ma il generatore muove solo i
TARGET, e i target dell'H28 sono i fatti della coppia (a,b) = le due lezioni
di T10: **la lezione "c" di mezzo (M-Lav@9) non e' mai target**, quindi la
swap che la muove non viene nemmeno generata. Cura indicata per il 638:
aggiungere ai target, per le famiglie pattern a tre (H28 oggi; stessa forma
potrebbe valere altrove), anche le lezioni CONTESTO del match (la "c"), oltre
ai fatti. E' un ampliamento di collectTargets, non una mossa nuova.

## FILO 2 - classi-mossa rc-1 (censimento dal jar 2.7.0-rc-1, javap)

Censimento completo `preview.api.move.builtin`: per BASIC variables esistono
Change/Swap/Assign/Unassign, **MassChange/MassAssign/MassUnassign**,
**PillarChange/PillarSwap/PillarUnassign**,
**SubPillarChange/SubPillarSwap/SubPillarUnassign**. Per LIST variables:
List*, SubList*, MassList*, **TwoOptListMoveProvider**,
**ListTailSwapMoveProvider**. Risposte alle tre domande:

1. **SubPillar su basic: SI'.** Il pillar e' definito dal VALORE assegnato
   (PillarSampler<Key_=Value_, Entity_>; per lo swap multi-variabile la chiave
   e' la lista dei valori): insiemi di entita' che condividono lo stesso
   (insieme di) valore. SubPillar = sottoinsieme del pillar: la mossa cambia/
   scambia il sottoinsieme tutto insieme. MA: esistono solo come PROVIDER del
   Neighborhood API (loop del solver), NON come factory one-shot `Moves.*`:
   integrarli nel 638 cosi' com'e' non si puo' senza attivare la preview
   Neighborhood (solver config + NeighborhoodProvider bean).
2. **Mass: cosa fanno.** `Moves.massChange(metaModel, Sample<Entity_>, Value_)`
   cambia la variabile di TUTTE le entita' del Sample al valore dato IN UNA
   SOLA mossa (il grande salto anti-optima-locale; MassAssign la variante che
   assegna entita' non assegnate). **Queste DUE sono factory one-shot e sono
   gia' usabili dal 638 direttamente**: `Sample.of(SequencedCollection)`
   costruisce il sottoinsieme, il resto e' identico a Moves.change/swap che
   il finisher gia' usa (firma verificata via javap). Applicabilita' al
   dominio: sì per basic; casi d'uso naturali da censire (svuotare/riempire
   celle, famiglie donatore/H34 dove servono spostamenti di massa).
3. **2opt / tail-swap: LIST-only, escluse.** TwoOptListMoveProvider e
   ListTailSwapMoveProvider esistono SOLO per planning list variable; il
   nostro Lesson.timeslot e' basic: esclusi dal dominio.

Quale API servirebbe per integrare nel 638: NESSUNA nuova per massChange e
pillarSwap one-shot (già nel Moves che importiamo); per i SubPillar servirebbe
attivare la preview Neighborhood API nel solver (config), che pero' non e' il
percorso del finisher: per il 638 la forma naturale e' generare SubPillar-like
a mano (gruppi di lezioni stesso valore -> Sample) e darli a
Moves.massChange/pillarSwap.

## Registro

- Arbitrato: scratch test (cancellato dopo l'uso, non committato) che carica
  l'export on/ e applica esattamente la swap: prima hard -1 (H28 1 match),
  dopo hard 0. Transcript nelle righe sopra e nel messaggio di commit.
- Nessun cambiamento di produzione: questo report e' l'unico artifact.
