# AUDIT H5 (TASK-614 livello a): quali pass deterministici hanno landing gate ciechi su availability/VRP

Lettura-solo su HEAD d276912e (nessuna modifica a src/). Domanda: chi ha mosso
Lavorazioni meccaniche id=10060 (T10/G13, quota-bound lab) in 2027-06-10T11,
cella che il walk D1 ha misurato respinta SOLO da H5.1 (indisponibilita' docente)?

## VERDETTO: il mover e' FinalClosingCampaign, confermato dinamicamente

Catena di custodia dal log del braccio A (arm-a/app-crash.log, 12:57:27):

1. `,695` multi-start: attempt-2 predictor-discarded, winner = attempt-1 (doomed).
2. `,726` `[FinalClosingCampaign] TASK-537: true H17 holes closed=4, quasi paid 0/0`:
   il pass muove 4+ lezioni per chiudere buchi H17 di fine anno. Una delle sue
   mosse v2 (import cross-day dal DONORE all'ULTIMO slot del suo giorno) porta
   10060 da 2026-10-06T14:30 (ultima ora del suo giorno: firma da donore FCC)
   nel buco H17 di G13 il 2027-06-10 alle 11:00. Gate consultati da FCC:
   teacherBusy (H1 live-index), cella gruppo libera, finestra calendario (H20),
   H28, budget quasi H32/H33. **Availability/VRP MAI consultati**.
3. `,796` (+70ms) `FinalClosingCampaign FAILED ... IllegalStateException: The
   value (2027-06-10T11:00 ...) entity (Lavorazioni meccaniche(10060)) ... outside
   of the related value range`: il solver di rifinitura/consegna avviato dentro
   l'orbita dello stesso try asserta il VRP e muore sulla placement che FCC ha
   appena creato. Il catch "continuing with the pre-campaign winner" consegna
   comunque lo stato avvelenato; l'eccezione ri-emerge non catturata
   ("Exception in thread staged-solve") e il thread muore: NESSUNA consegna.

Tre aggravanti documentali:

- Il limite e' NOTO e DOCUMENTATO nella javadoc di FCC: "the VRP check
  (Lesson.getAvailableTimeslots) is not accessible cheaply from this static
  view, a known documented limit". Ma la mitigazione dichiarata e' FALSA sul
  seed corrente: "on the 2026/27 seed the quota-bound lab subjects are
  FC-only and those trades are already rare". Il walk D1 ha misurato
  `isQuotaBoundLabSubject(G13, Lavorazioni meccaniche)=true` (riga quota
  G13 06-10: aula=0 lab=4). G13 e' quota-bound pure lei: la famiglia
  esclusa a voce e' invece dentro il perimetro del buco.
- TASK-565 (28/08, n=1, stessa firma, stessa entity-family) e' ancora To Do:
  il crash di oggi e' la seconda occorrenza, e la prima con consegna morta
  (quella volta il catch salvava la consegna, oggi il assert e' arrivato
  a valle del catch).
- La conoscenza H5 ESISTE gia' nel codice condiviso: la catena
  `PureHoleRepair.Grid#cellOk` documentata come "(H1/H3/H5/VRP)" e usata da
  RoomTailFinisher copre H5 esplicitamente. FCC non la usa. La cura e' un
  rimando, non un porto.

## Tabella: landing gate per pass (assignment-site verificati, HEAD d276912e)

| Pass | VRP-fresh | H1 | H7 | room | H5 | Esito audit |
|---|---|---|---|---|---|---|
| FinalClosingCampaign | NO | SI (busy-index) | quasi-budi | null-room | NO | **MOVER CONFERMATO** (evidenza dinamica) |
| H17BlockSwap | NO | SI (occupancy) | no | no | NO | cieco come classe ma mosse solo intra-day: non puo' produrre la firma cross-mese |
| OneOffFinisher | SI (Fresh 165/394/503, isSlotLegalFresh 275/415) | SI | SI | SI (resolveRoom) | via VRP | coperto (ha gia' avuto il suo incidente: DIAG 10057 nel codice) |
| ExactFinisher | SI (catena BoxRepair, "VRP last") | SI | SI | null-room | via VRP | coperto |
| SurplusHarvest | SI (259/343/384/624/633, guard TASK-577 v6) | SI | SI | SI | via VRP | coperto |
| StudentDayCompaction | SI (isSlotLegalFresh al chooser) | SI | no | null-room | via VRP | coperto |
| BoxRepair | SI (passesPlacementGates + VRP) | SI | SI | null-room | via VRP | coperto |
| HoleRepair | SI (findImport: "VRP membership last") | SI | SI | SI | via VRP | coperto |
| PureHoleRepair | SI (Grid#cellOk) | SI | no | SI | **SI esplicito** | coperto, la catena di riferimento |
| RoomTailFinisher | SI (Grid#cellOk) | SI | no | SI (core) | **SI esplicito** | coperto + oracle full-hard |
| EmptyDayFinisher | SI (census VRP=12847, D3) | SI | no | SI | via VRP | coperto (oracle) |
| CompositeSwapFinisher | SI | SI | SI | SI | via VRP | coperto |
| HoleRelayFinisher | SI (rejectReason: isSlotLegalFresh) | SI | SI | SI | via VRP | coperto (census HRF) |
| SurplusRebalance | SI (isSlotLegalFresh "VRP last") | SI | no | no | via VRP | coperto |
| QuotaFillRepair | SI | SI | no | no | via VRP | coperto |
| LabDayConsolidation | SI (getAvailableTimeslotsFresh) | SI | no | no | via VRP | coperto + oracle |
| CompositionHoleCloser | SI (Fresh) | SI | no | no | via VRP | coperto (default OFF) |
| H28RotationFinisher | SI (javadoc: VRP via isSlotLegalFresh) | SI | no | no | via VRP | coperto |
| AnchorRepair | SI (delega a HoleRepair.findImport) | SI | SI | SI | via VRP | coperto |
| DeclaredDayPreBooker | SI + oracle | SI | SI | no | via VRP | coperto |
| GoldenSacrificeBudget | n/a | n/a | n/a | n/a | n/a | non muove nulla (solo derivation) |
| DayClusteredConstructionPhase | (esclusa dal mandato: T10 era legale a ottobre; il mover ha agito DOPO) | | | | | fuori perimetro |
| fasi LS (solver moves) | VRP-legale per costruzione (move selector sul dominio) | | | | | fuori perimetro |

## Sospetti in ordine di plausibilita' per la firma (06-10, ora 11, T10, G13)

1. **FinalClosingCampaign: CONFERMATO** (non piu' sospetto). Pass di chiusura
   anno sul winner, esattamente nella finestra temporale del crash (70ms prima),
   firma del donore corrispondente (ultima ora del giorno donore), blind spot
   documentato con mitigazione falsata dal dato G13.
2. H17BlockSwap: unico altro gate senza VRP-fresh, ma famiglia solo intra-day:
   escluso per la firma, resta un rischio di classe per mosse H5-violanti
   hard-neutral che l'oracle (se full-hard) revertirebbe comunque.

## Cura indicata (decisione a Fedora, non applicata)

Minimale: far passare il landing di FCC dalla stessa catena degli altri
(`isSlotLegalFresh(cand)` sul donore-prima-della-mossa; O(1), gia' usata da
tutti). Alternativa gia' pronta nel codice: la catena `Grid#cellOk` di
PureHoleRepair include H5. Il task di raccolta e' TASK-565 (da aggiornare con
questa conferma e l'escalation consegna-morta).
