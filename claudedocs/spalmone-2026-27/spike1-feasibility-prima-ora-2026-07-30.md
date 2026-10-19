# SPIKE 1 - Fattibilita' allocazione coordinata prima ora (2026-07-30 notte)

## Consegna (3 righe)
- **Ipotesi:** la cura profonda (allocazione anticipata coordinata della prima ora, TASK-451) e'
  un problema di ACCOPPIAMENTO, non di capacita': quindi fattibile in linea di principio.
- **Numero misurato:** matching bipartito (gruppo x docente-che-insegna-al-gruppo, disponibile a
  slot0) = **14/14 per tutti i 5 weekday, zero gruppi scoperti**. Vale anche come blocco slot0+slot1
  (docente disponibile a entrambi gli slot) e anche con soglia >=20h. Il golden ancora G11 ven con
  **T08** (la cella che il run perde).
- **Tenere: SI', il prototipo ha senso.** Non e' un vincolo di capacita' (a differenza di G06 /
  T01 che e' overdemand strutturale). CAVEAT decisivo: il matching e' necessario-non-sufficiente
  (non cattura la contenzione JOINT di slot1, che e' il vero meccanismo G11 ven); e il golden stesso
  ancora in modo stabile solo le celle dei gruppi primo-anno, non tutte.

## Metodo
Problema: per ogni weekday W, assegnare a ciascuno dei 14 gruppi un docente che (1) insegni a quel
gruppo (incarichi.csv, docenti reali, escludendo pseudo) e (2) sia disponibile a slot0 di W (regole
VACATION weekly di availability_rules.csv, che sono golden-derived Q22), con il vincolo che un docente
stia in un solo gruppo per weekday. Max matching bipartito (Kuhn). Tre varianti: topologia, +disponibilita',
+disponibilita'&>=20h. Ripetuto anche per blocco slot0+slot1. Dati: `data/incarichi.csv`,
`data/availability_rules.csv`, `data/teachers.csv`, golden `data/lessons.csv`. Read-only, zero codice modello.

## Risultato matching (zero scoperti ovunque)
| weekday | slot0 (+avail,>=20h) | blocco slot0+slot1 |
|---|---|---|
| lun | 14/14 | 14/14 |
| mar | 14/14 | 14/14 |
| mer | 14/14 | 14/14 |
| gio | 14/14 | 14/14 |
| ven | 14/14 | 14/14 |

Assegnamento concreto (ven, es.): G11=T08, G02=T21, G06=T23, G07=T12,
G12=T25, G13=T11, ecc. (tutti docenti distinti -> il vincolo "nessun docente in due posti"
e' rispettato). 26 docenti reali per 14 gruppi: la capacita' c'e', larga.

## Cosa fa il GOLDEN (mappa target docente x cella slot0)
Golden = la scuola CI RIESCE, e dice con quali docenti. Moda del docente slot0 per (gruppo, weekday):
- Gruppi primo anno (G01/G02/G03): ancora stabile ~96-100% quasi ovunque.
- Gruppi FC/split (G04/G08/G09/G07/G13): anchor parziale 70-82% (giorni lab/stage/split).
- **G11 ven = T08 76%** nel golden. Il run (30b) invece dà T08 a G02 ven (34 ven) e fa
  partire G11 alle 9 (Educazione Civica/T23): e' proprio lo scambio di ancora che la cura
  coordinata dovrebbe ripristinare.
- G14: anchor solo gio/ven (gruppo 5h, lun-mar-mer senza slot0, coerente col suo calendario).

CAVEAT fedelta': le % di copertura sono indicative (il denominatore "giorni di scuola" include giorni
di stage/special che non partono alle 8 per natura); per il conteggio autoritativo dei ritardi resta
il day-start-anatomy KPI (golden 4 ritardi / 1960, ~99,8%). La mappa docente-per-cella e' invece solida.

## Lettura per il prototipo (TASK-451)
1. La fattibilita' di capacita' c'e' (14/14): il prototipo NON e' destinato a fallire per mancanza di
   docenti. Passa il cancello che abbiamo imposto al 451.
2. Ma il matching statico non basta: il vincolo reale e' JOINT (slot1 conteso cross-gruppo, catena
   TASK-450). L'allocatore deve pre-assegnare il BLOCCO slot0-1 e gestire la contenzione residua, non
   solo slot0.
3. Il golden e' il TARGET, non un tetto da superare: la scuola ancora in modo stabile soprattutto i
   gruppi primo-anno e accetta anchor parziali sugli FC/split. La cura deve replicare la mappa
   golden (T08->G11 ven, ecc.), non esigere 70/70 celle stabili (il golden stesso non le ha).
4. Prossimo passo (costo): prototipo ConstrainedTeacherAllocator generalizzato a "slot prima-ora
   vincolato", pre-assegnamento cross-gruppo del blocco 2h usando la mappa golden come seed, flag
   default-OFF + checkpoint su ricorrenza/full-day/buchi (guardando i 6 buchi H17 che slot0-balancing
   ha aperto: il blocco contiguo li evita per costruzione).

## Domanda aperta (scritta, vado avanti)
Il matching e' cosi' largo (14/14 ovunque) che il vincolo stringente non e' mai la capacita' docente
allo slot0: e' sempre la contenzione JOINT di slot1. Conferma indiretta che il curse e' coordinare il
  blocco, non trovare un docente. Da tenere a mente quando si prototipa: un allocatore solo-slot0
  riprodurrebbe il difetto di slot0-balancing (che riserva slot0 ma lascia slot1 scoperto -> buco H17).
